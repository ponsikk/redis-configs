# Redis Distributed Lock Configuration

## Use Case Overview

This Redis instance is configured for implementing **distributed locks** using the Redlock algorithm. It ensures mutual exclusion across multiple microservices for critical operations like payment processing, booking confirmation, and inventory management.

## Key Configuration Details

### Memory Management

```conf
maxmemory 512mb
maxmemory-policy volatile-ttl
```

**Why these settings?**

- **maxmemory 512mb**: Lock keys are tiny (few bytes), minimal memory needed
- **volatile-ttl**: Evicts keys with shortest TTL first - perfect for locks that should expire

**Lock memory footprint:**
- Typical lock key: ~100 bytes
- 512MB can handle millions of concurrent locks

### Persistence - AOF with `appendfsync always`

```conf
appendonly yes
appendfilename "locks.aof"
appendfsync always
```

**Why `appendfsync always`?**

This is the **most controversial setting** but critical for lock safety:

- **appendfsync always**: Every write is synced to disk immediately
- **Guarantees durability**: If Redis crashes, locks are recovered
- **Performance cost**: ~100x slower than `everysec`

**Trade-off analysis:**

| Setting | Safety | Performance |
|---------|--------|-------------|
| appendfsync always | Highest (our choice) | ~1k ops/sec |
| appendfsync everysec | Medium | ~100k ops/sec |
| appendfsync no | Lowest | ~200k ops/sec |

**Why we chose `always`:**
- Losing locks can cause **data corruption**
- Example: Two services both think they own the lock, both modify same booking
- For locks, correctness > performance

### Lazy Freeing - Disabled

```conf
lazyfree-lazy-eviction no
lazyfree-lazy-expire no
lazyfree-lazy-server-del no
```

**Why disable lazy freeing?**

- **Consistency over performance**: Lock operations must be synchronous
- Lazy freeing delays deletion to background threads
- For locks, we need immediate, deterministic behavior

### High-Frequency Expiration

```conf
hz 20
```

- **hz 20**: Checks for expired keys 20 times/sec (vs default 10)
- Critical for locks: faster release of expired locks
- Prevents lock starvation

### Keyspace Notifications

```conf
notify-keyspace-events Ex
```

- **Ex**: Notifies when keys expire
- Useful for detecting lock timeouts
- Applications can react to lock expiration

## Distributed Lock Algorithms

### 1. Simple SET NX (Not Recommended for Production)

Basic lock implementation using SET with NX (Not eXists) flag.

```java
@Service
public class SimpleLockService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public boolean acquireLock(String lockKey, String lockValue, long ttlSeconds) {
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseLock(String lockKey, String lockValue) {
        // Only delete if we own the lock
        String script =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

        redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(lockKey),
            lockValue
        );
    }
}
```

**Redis Commands:**
```bash
# Acquire lock
SET lock:booking:123 uuid-value-456 NX EX 30

# Check lock
GET lock:booking:123

# Release lock (with Lua for atomicity)
EVAL "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end" 1 lock:booking:123 uuid-value-456
```

**Problems with simple approach:**
- Single point of failure
- No automatic retry
- Clock skew issues
- No lease renewal

### 2. Redlock Algorithm (Recommended)

Industry-standard distributed lock algorithm by Redis creator.

**Requirements:**
- 3+ independent Redis instances (odd number)
- Quorum-based: Must acquire lock on majority (N/2 + 1)

```java
@Service
public class RedlockService {

    private final List<RedisTemplate<String, String>> redisInstances;
    private static final int QUORUM = 2; // For 3 instances

    public RedlockService(List<RedisTemplate<String, String>> instances) {
        this.redisInstances = instances;
    }

    public RedLock acquireRedlock(String resource, String value, long ttlMillis) {
        int acquired = 0;
        long startTime = System.currentTimeMillis();
        long drift = (long) (ttlMillis * 0.01) + 2; // Clock drift

        List<RedisTemplate<String, String>> acquiredInstances = new ArrayList<>();

        // Try to acquire lock on all instances
        for (RedisTemplate<String, String> redis : redisInstances) {
            try {
                Boolean success = redis.opsForValue()
                    .setIfAbsent(resource, value, ttlMillis, TimeUnit.MILLISECONDS);

                if (Boolean.TRUE.equals(success)) {
                    acquired++;
                    acquiredInstances.add(redis);
                }
            } catch (Exception e) {
                // Instance unavailable, continue
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        long validityTime = ttlMillis - elapsedTime - drift;

        // Check if we acquired quorum and lock is still valid
        if (acquired >= QUORUM && validityTime > 0) {
            return new RedLock(resource, value, validityTime, acquiredInstances);
        } else {
            // Failed to acquire, release all locks
            releaseRedlock(resource, value, acquiredInstances);
            return null;
        }
    }

    public void releaseRedlock(String resource, String value, List<RedisTemplate<String, String>> instances) {
        String script =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

        for (RedisTemplate<String, String> redis : instances) {
            try {
                redis.execute(
                    new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(resource),
                    value
                );
            } catch (Exception e) {
                // Best effort release
            }
        }
    }
}

@Data
@AllArgsConstructor
public class RedLock {
    private String resource;
    private String value;
    private long validityTime;
    private List<RedisTemplate<String, String>> acquiredInstances;
}
```

**Redlock guarantees:**
1. **Mutual exclusion**: At most one client holds lock at any time
2. **Deadlock free**: Eventually possible to acquire lock (with TTL)
3. **Fault tolerance**: Works with N/2 instances down

### 3. Redisson Library (Production Ready)

Redisson provides battle-tested distributed lock implementation.

**Maven dependency:**
```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.24.3</version>
</dependency>
```

**Configuration:**
```java
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://localhost:6384")
            .setConnectionPoolSize(64)
            .setConnectionMinimumIdleSize(10);

        return Redisson.create(config);
    }
}
```

**Usage:**
```java
@Service
public class BookingService {

    @Autowired
    private RedissonClient redissonClient;

    public void processBooking(String bookingId) {
        String lockKey = "lock:booking:" + bookingId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Try to acquire lock, wait up to 10s, auto-release after 30s
            boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (acquired) {
                try {
                    // Critical section
                    Booking booking = bookingRepository.findById(bookingId);
                    booking.setStatus("CONFIRMED");
                    bookingRepository.save(booking);

                    // Deduct inventory
                    inventoryService.decrementAvailability(booking.getFlightId());

                    // Process payment
                    paymentService.charge(booking);

                } finally {
                    lock.unlock();
                }
            } else {
                throw new LockAcquisitionException("Could not acquire lock for booking " + bookingId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
```

**Redisson features:**
- Automatic lock renewal (watchdog)
- Fair locks (FIFO)
- Read/write locks
- MultiLock (lock multiple resources atomically)

## Typical Use Cases

### 1. Prevent Double Booking

```java
@Service
public class FlightBookingService {

    public BookingResult createBooking(String flightId, String seatNumber, String userId) {
        String lockKey = "lock:seat:" + flightId + ":" + seatNumber;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    // Check availability
                    Seat seat = seatRepository.findByFlightAndSeat(flightId, seatNumber);
                    if (seat.isAvailable()) {
                        seat.setAvailable(false);
                        seat.setUserId(userId);
                        seatRepository.save(seat);

                        return BookingResult.success();
                    } else {
                        return BookingResult.alreadyBooked();
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                return BookingResult.lockTimeout();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BookingResult.error(e);
        }
    }
}
```

### 2. Payment Processing

```java
@Service
public class PaymentService {

    public void processPayment(String orderId, double amount) {
        String lockKey = "lock:payment:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(10, 60, TimeUnit.SECONDS)) {
                try {
                    Payment payment = paymentRepository.findByOrderId(orderId);

                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        // Call payment gateway
                        PaymentResult result = paymentGateway.charge(amount);

                        if (result.isSuccess()) {
                            payment.setStatus(PaymentStatus.COMPLETED);
                            payment.setTransactionId(result.getTransactionId());
                        } else {
                            payment.setStatus(PaymentStatus.FAILED);
                        }

                        paymentRepository.save(payment);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                throw new PaymentException("Could not acquire payment lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
```

### 3. Inventory Management

```java
@Service
public class InventoryService {

    public boolean decrementAvailability(String flightId, int quantity) {
        String lockKey = "lock:inventory:" + flightId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    Inventory inventory = inventoryRepository.findByFlightId(flightId);

                    if (inventory.getAvailableSeats() >= quantity) {
                        inventory.setAvailableSeats(inventory.getAvailableSeats() - quantity);
                        inventoryRepository.save(inventory);
                        return true;
                    } else {
                        return false; // Not enough seats
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return false;
    }
}
```

### 4. Scheduled Job (Only One Instance Runs)

```java
@Service
public class DailyReportGenerator {

    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void generateDailyReports() {
        String lockKey = "lock:job:daily-report";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Only one instance in the cluster will acquire this lock
            if (lock.tryLock(0, 3600, TimeUnit.SECONDS)) {
                try {
                    logger.info("This instance acquired the lock, generating reports...");
                    generateReports();
                } finally {
                    lock.unlock();
                }
            } else {
                logger.info("Another instance is generating reports, skipping...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 5. Multi-Resource Lock

```java
@Service
public class MultiBookingService {

    // Book flight + hotel atomically
    public void bookPackage(String flightId, String hotelId, String userId) {
        String flightLock = "lock:flight:" + flightId;
        String hotelLock = "lock:hotel:" + hotelId;

        RLock lock1 = redissonClient.getLock(flightLock);
        RLock lock2 = redissonClient.getLock(hotelLock);

        // MultiLock ensures both are acquired or none
        RLock multiLock = redissonClient.getMultiLock(lock1, lock2);

        try {
            if (multiLock.tryLock(10, 60, TimeUnit.SECONDS)) {
                try {
                    // Both resources locked, proceed with booking
                    bookFlight(flightId, userId);
                    bookHotel(hotelId, userId);

                    // Commit transaction
                    transactionManager.commit();
                } finally {
                    multiLock.unlock();
                }
            } else {
                throw new BookingException("Could not acquire locks for package booking");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
```

## Best Practices

### 1. Always Use Try-Finally

```java
// BAD: Lock not released on exception
if (lock.tryLock()) {
    processBooking();
    lock.unlock();
}

// GOOD: Lock always released
if (lock.tryLock()) {
    try {
        processBooking();
    } finally {
        lock.unlock();
    }
}
```

### 2. Set Appropriate TTL

```java
// TOO SHORT: Might expire before operation completes
lock.tryLock(0, 5, TimeUnit.SECONDS); // 5s
processLongRunningTask(); // Takes 10s → Lock expires!

// TOO LONG: Deadlock risk if process crashes
lock.tryLock(0, 3600, TimeUnit.SECONDS); // 1 hour

// GOOD: Slightly longer than expected operation time
lock.tryLock(0, 30, TimeUnit.SECONDS); // 30s for 10s operation
```

### 3. Use Unique Lock Values

```java
// Store instance ID + thread ID + random UUID
String lockValue = hostName + ":" + threadId + ":" + UUID.randomUUID();
lock.setIfAbsent(lockKey, lockValue, ttl);

// Ensures only the lock owner can release it
```

### 4. Handle Lock Acquisition Failure

```java
if (!lock.tryLock(10, 30, TimeUnit.SECONDS)) {
    // Don't silently fail!
    logger.error("Failed to acquire lock for {}", lockKey);

    // Options:
    // 1. Retry with exponential backoff
    // 2. Return error to client
    // 3. Queue for later processing
    // 4. Alert monitoring system

    throw new LockAcquisitionException("Could not acquire lock");
}
```

### 5. Monitor Lock Expiration

```java
@Service
public class LockMonitor {

    @Bean
    RedisMessageListenerContainer lockExpirationListener(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        container.addMessageListener(
            new MessageListenerAdapter(this, "onLockExpired"),
            new PatternTopic("__keyevent@0__:expired")
        );

        return container;
    }

    public void onLockExpired(String expiredKey) {
        if (expiredKey.startsWith("lock:")) {
            logger.warn("Lock expired: {}", expiredKey);
            alertOps("Lock timeout: " + expiredKey);
        }
    }
}
```

## Performance Characteristics

| Metric | Value (appendfsync always) | Value (everysec) |
|--------|----------------------------|------------------|
| Lock acquisition | ~5ms | ~0.5ms |
| Throughput | ~1k locks/sec | ~100k locks/sec |
| Durability | Highest | Medium |
| Use case | Critical operations | High throughput |

## Common Pitfalls

### 1. Lock Leak (Not Releasing)

```java
// BAD: Exception causes lock to never release
RLock lock = redissonClient.getLock(lockKey);
lock.lock();
processBooking(); // Throws exception
lock.unlock(); // Never reached!

// GOOD: Use try-finally
lock.lock();
try {
    processBooking();
} finally {
    lock.unlock();
}
```

### 2. Releasing Someone Else's Lock

```java
// BAD: Delete lock without checking ownership
redisTemplate.delete(lockKey);

// GOOD: Check ownership before deleting
String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);
```

### 3. Clock Skew Between Services

**Problem**: Service A sets lock with TTL 30s, but its clock is 5s fast

**Solution**: Use Redlock with multiple independent instances

### 4. Lock Not Expiring (Deadlock)

**Problem**: Service crashes while holding lock, lock never released

**Solution**: Always set TTL (Redisson's watchdog pattern extends if needed)

## Monitoring Recommendations

```bash
# Count active locks
redis-cli --scan --pattern "lock:*" | wc -l

# Find locks without TTL (dangerous!)
redis-cli --scan --pattern "lock:*" | xargs -L1 redis-cli TTL | grep -c -- "-1"

# Monitor lock expirations
redis-cli INFO stats | grep expired_keys

# Check for lock contention (many clients waiting)
redis-cli CLIENT LIST | grep -c "cmd=blpop"

# Slow lock operations
redis-cli SLOWLOG GET 10
```

## Configuration Trade-offs

| Setting | Pro | Con |
|---------|-----|-----|
| appendfsync always | Max safety | Slow (~1k ops/sec) |
| No lazy freeing | Deterministic | Slightly slower |
| hz 20 | Fast expiration | More CPU |
| volatile-ttl | Evicts old locks | Must set TTL |

## When to Use This Configuration

✅ **Good for:**
- Payment processing
- Inventory management
- Preventing race conditions
- Ensuring exactly-once execution
- Critical data modification

❌ **Not suitable for:**
- High-throughput operations (>10k locks/sec)
- Non-critical operations (use optimistic locking)
- Long-running tasks (>5 minutes)

## Alternatives

### When NOT to use distributed locks:

1. **Optimistic Locking** (database-level)
   ```sql
   UPDATE bookings SET status = 'CONFIRMED', version = version + 1
   WHERE id = ? AND version = ?
   ```

2. **Idempotency Keys** (for payments)
   ```java
   @Transactional
   public void processPayment(String idempotencyKey, Payment payment) {
       if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
           return; // Already processed
       }
       // Process payment
   }
   ```

3. **Message Queue with Consumer Groups** (Kafka, RabbitMQ)
   - Partitioning ensures only one consumer per message

4. **Database Constraints** (unique indexes)
   ```sql
   CREATE UNIQUE INDEX idx_seat ON bookings(flight_id, seat_number)
   WHERE status = 'CONFIRMED';
   ```

## Redlock Setup for Production

```conf
# Run 3+ Redis instances on different servers
# Instance 1: redis://server1:6384
# Instance 2: redis://server2:6384
# Instance 3: redis://server3:6384

# Each with this configuration
# Must be independent (no replication between them!)
```

```java
@Bean
public RedissonClient redissonMultiInstance() {
    Config config = new Config();
    config.useReplicatedServers()
        .addNodeAddress(
            "redis://server1:6384",
            "redis://server2:6384",
            "redis://server3:6384"
        );

    return Redisson.create(config);
}
```
