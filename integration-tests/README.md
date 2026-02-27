# Integration Tests

Comprehensive integration tests for all 6 Redis configurations used in the Travel Analytics System.

## Test Coverage

| Test Class | Redis Instance | Port | Configuration Tested |
|------------|---------------|------|---------------------|
| **CacheRedisTest** | Cache | 6379 | allkeys-lru, no persistence, TTL expiration |
| **SessionRedisTest** | Session Store | 6380 | volatile-lru, AOF persistence, sliding window |
| **RateLimitRedisTest** | Rate Limiting | 6381 | volatile-ttl, Lua scripts, hz=20 |
| **AnalyticsRedisTest** | Analytics | 6382 | allkeys-lfu, Sorted Sets, HyperLogLog, Hashes |
| **PubSubRedisTest** | Pub/Sub | 6383 | noeviction, pattern subscriptions, event flow |
| **DistributedLockRedisTest** | Locks | 6384 | appendfsync always, Redlock, lock contention |

---

## Prerequisites

Before running the tests, ensure all 6 Redis instances are running:

```bash
# Start all Redis instances
redis-server redis-configs/caching/redis.conf
redis-server redis-configs/session-store/redis.conf
redis-server redis-configs/rate-limiting/redis.conf
redis-server redis-configs/realtime-analytics/redis.conf
redis-server redis-configs/pubsub/redis.conf
redis-server redis-configs/distributed-lock/redis.conf
```

Verify all instances are running:

```bash
redis-cli -p 6379 PING  # Cache
redis-cli -p 6380 PING  # Session
redis-cli -p 6381 PING  # Rate Limit
redis-cli -p 6382 PING  # Analytics
redis-cli -p 6383 PING  # Pub/Sub
redis-cli -p 6384 PING  # Locks
```

---

## Running Tests

### Run All Tests

```bash
cd integration-tests
mvn test
```

### Run Specific Test

```bash
mvn test -Dtest=CacheRedisTest
mvn test -Dtest=SessionRedisTest
mvn test -Dtest=RateLimitRedisTest
mvn test -Dtest=AnalyticsRedisTest
mvn test -Dtest=PubSubRedisTest
mvn test -Dtest=DistributedLockRedisTest
```

---

## Test Details

### 1. CacheRedisTest (Port 6379)

Tests caching with LRU eviction:

- ✅ Set and get with TTL
- ✅ Automatic expiration
- ✅ LRU eviction policy verification
- ✅ No persistence configuration
- ✅ Pattern-based key filtering

**Key Operations:**
```java
jedis.setex("flights:route:NYC-LAX", 900, flightJson);
jedis.get("flights:route:NYC-LAX");
jedis.keys("flights:*");
```

---

### 2. SessionRedisTest (Port 6380)

Tests session management with AOF persistence:

- ✅ Create session with JSON serialization
- ✅ Session expiration (30 min TTL)
- ✅ Sliding window renewal
- ✅ volatile-lru policy
- ✅ AOF with everysec fsync
- ✅ Keyspace notifications (Ex)

**Key Operations:**
```java
jedis.setex("session:" + sessionId, 1800, sessionJson);
jedis.expire("session:" + sessionId, 1800); // Renew
```

---

### 3. RateLimitRedisTest (Port 6381)

Tests rate limiting with Lua scripts:

- ✅ Fixed window rate limiting
- ✅ Lua script atomic operations
- ✅ volatile-ttl eviction
- ✅ High frequency expiration (hz=20)
- ✅ No persistence
- ✅ Multiple user limits

**Key Operations:**
```java
jedis.incr("ratelimit:user:123:window");
jedis.expire("ratelimit:user:123:window", 60);
jedis.eval(luaScript, keys, args);
```

---

### 4. AnalyticsRedisTest (Port 6382)

Tests real-time analytics data structures:

- ✅ Counters (INCR)
- ✅ Sorted Sets for rankings (ZADD, ZINCRBY)
- ✅ HyperLogLog for unique counts (PFADD, PFCOUNT)
- ✅ Hashes for complex stats (HSET, HINCRBY)
- ✅ allkeys-lfu policy
- ✅ Hybrid persistence (RDB + AOF)
- ✅ Active defragmentation
- ✅ Complex workflow simulation

**Key Operations:**
```java
jedis.incr("analytics:bookings:total");
jedis.zincrby("analytics:popular:routes", 1, "NYC-LAX");
jedis.pfadd("analytics:unique:users:2024-03-15", "user123");
jedis.hset("analytics:stats:flight:123", "totalBookings", "1542");
```

---

### 5. PubSubRedisTest (Port 6383)

Tests event-driven messaging:

- ✅ Basic publish/subscribe
- ✅ JSON event serialization
- ✅ Pattern subscriptions (events:booking:*)
- ✅ noeviction policy
- ✅ No persistence
- ✅ Pub/Sub buffer limits

**Key Operations:**
```java
publisher.publish("events:booking:created", eventJson);
subscriber.subscribe(pubsub, "events:booking:created");
subscriber.psubscribe(pubsub, "events:booking:*");
```

---

### 6. DistributedLockRedisTest (Port 6384)

Tests distributed locking with Redisson:

- ✅ Basic lock acquisition/release
- ✅ Prevent double booking scenario
- ✅ Auto-expiration (30 sec TTL)
- ✅ Multiple independent locks
- ✅ Lock contention handling
- ✅ appendfsync always
- ✅ AOF enabled
- ✅ volatile-ttl policy

**Key Operations:**
```java
RLock lock = redissonClient.getLock("lock:seat:flight123:12A");
lock.tryLock(5, 30, TimeUnit.SECONDS);
lock.unlock();
```

---

## Understanding Test Results

### Successful Test Output

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.analytics.tests.CacheRedisTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.analytics.tests.SessionRedisTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Common Issues

**Connection Refused:**
```
java.net.ConnectException: Connection refused
```
Solution: Start the corresponding Redis instance

**Wrong Configuration:**
```
Expected: "allkeys-lru", Actual: "noeviction"
```
Solution: Verify you're using the correct redis.conf file

---

## What Each Test Validates

### Configuration Validation
- Eviction policies (LRU, LFU, TTL)
- Persistence settings (RDB, AOF, appendfsync)
- Memory limits
- Expiration frequency (hz)
- Keyspace notifications

### Functional Validation
- Data structures work correctly
- TTL expiration behaves as expected
- Distributed locks prevent race conditions
- Pub/Sub delivers messages reliably
- Analytics aggregations are accurate

### Performance Validation
- Lock contention handling
- Multiple concurrent operations
- Pattern subscriptions scale correctly

---

## Integration with Microservices

These tests validate the Redis configurations that microservices depend on:

- **flight-service** → Cache (6379) + Pub/Sub (6383)
- **hotel-service** → Cache (6379) + Pub/Sub (6383)
- **booking-service** → Locks (6384) + Cache (6379) + Pub/Sub (6383)
- **analytics-service** → Analytics (6382) + Pub/Sub (6383)
- **api-gateway** → Rate Limit (6381) + Session (6380)

---

## Next Steps

After running integration tests:

1. Start all microservices
2. Test end-to-end workflows
3. Monitor Redis instances with `redis-cli --stat`
4. Review configuration documentation in `docs/redis-configurations/`
