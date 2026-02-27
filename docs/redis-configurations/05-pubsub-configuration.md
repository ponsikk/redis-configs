# Redis Pub/Sub Configuration

## Use Case Overview

This Redis instance is configured for **publish/subscribe messaging** to enable real-time notifications across microservices. It handles booking confirmations, price alerts, system events, and inter-service communication without data persistence.

## Key Configuration Details

### Memory Management

```conf
maxmemory 256mb
maxmemory-policy noeviction
```

**Why these settings?**

- **maxmemory 256mb**: Pub/Sub doesn't store messages (fire-and-forget), so minimal memory needed
- **noeviction**: If memory fills, return errors instead of evicting. Pub/Sub shouldn't evict anything

**How Pub/Sub Uses Memory:**

1. **Connection buffers**: Each subscriber has an output buffer
2. **Channel metadata**: Minimal overhead for channel names
3. **No message storage**: Messages not persisted, only in-flight

### Client Output Buffer Limits

```conf
client-output-buffer-limit pubsub 32mb 8mb 60
```

**Critical for Pub/Sub stability!**

This setting has 3 values: `hard_limit soft_limit soft_seconds`

- **Hard limit (32mb)**: If client buffer exceeds 32MB, disconnect immediately
- **Soft limit (8mb)**: If buffer exceeds 8MB for 60 seconds, disconnect
- **Protects against slow subscribers**: Prevents one slow client from blocking Redis

**Example scenario:**
- Publisher sends 1000 msg/sec
- Slow subscriber can only process 100 msg/sec
- Buffer grows → hits soft limit → disconnected after 60s

### Persistence - Disabled

```conf
save ""
appendonly no
```

**Why no persistence?**

- Pub/Sub is **ephemeral messaging**
- Messages are not stored, only delivered to active subscribers
- If a subscriber is offline, messages are lost (by design)
- No point in persistence

### Keyspace Notifications

```conf
notify-keyspace-events KEA
```

**Why enable notifications?**

- **K**: Keyspace events (key-level events)
- **E**: Keyevent events (event-level events)
- **A**: All events (sets, lists, expires, etc.)

Allows applications to subscribe to key changes:
```
SUBSCRIBE __keyspace@0__:booking:*
SUBSCRIBE __keyevent@0__:expired
```

### High Connection Limit

```conf
maxclients 15000
tcp-keepalive 60
```

- **maxclients 15000**: Many microservices subscribing to channels
- **tcp-keepalive 60**: Check connection health every 60 seconds

## Pub/Sub Patterns

### 1. Simple Pub/Sub

Publisher sends to a channel, subscribers receive.

```java
@Service
public class BookingEventPublisher {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void publishBookingCreated(Booking booking) {
        BookingCreatedEvent event = new BookingCreatedEvent(
            booking.getId(),
            booking.getUserId(),
            booking.getFlightId(),
            booking.getAmount(),
            Instant.now()
        );

        redisTemplate.convertAndSend("events:booking:created", event);
    }
}

@Service
public class BookingEventSubscriber {

    @Bean
    MessageListenerAdapter messageListener() {
        return new MessageListenerAdapter(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                BookingCreatedEvent event = deserialize(message.getBody());
                handleBookingCreated(event);
            }
        });
    }

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic("events:booking:*"));
        return container;
    }

    private void handleBookingCreated(BookingCreatedEvent event) {
        // Send confirmation email
        // Update analytics
        // Trigger loyalty points calculation
    }
}
```

**Redis Commands:**
```bash
# Publisher
PUBLISH events:booking:created '{"bookingId":"123","userId":"user456",...}'

# Subscriber
SUBSCRIBE events:booking:created
PSUBSCRIBE events:booking:*  # Pattern matching
```

### 2. Pattern Subscription

Subscribe to multiple channels with wildcards.

```java
@Configuration
public class EventSubscriberConfig {

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        // Subscribe to all booking events
        container.addMessageListener(
            bookingListener(),
            new PatternTopic("events:booking:*")
        );

        // Subscribe to all payment events
        container.addMessageListener(
            paymentListener(),
            new PatternTopic("events:payment:*")
        );

        // Subscribe to all price alerts
        container.addMessageListener(
            priceAlertListener(),
            new PatternTopic("alerts:price:*")
        );

        return container;
    }
}
```

**Pattern Examples:**
```
events:booking:*        → events:booking:created, events:booking:cancelled
events:*:user123        → events:booking:user123, events:payment:user123
alerts:*                → alerts:price:*, alerts:system:*
```

### 3. Keyspace Notifications

React to Redis key changes (with `notify-keyspace-events KEA` enabled).

```java
@Service
public class SessionExpirationHandler {

    @Bean
    RedisMessageListenerContainer expirationContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        // Listen for expired keys
        container.addMessageListener(
            new MessageListenerAdapter(this, "handleExpiration"),
            new PatternTopic("__keyevent@0__:expired")
        );

        return container;
    }

    public void handleExpiration(String expiredKey) {
        if (expiredKey.startsWith("session:")) {
            String sessionId = expiredKey.substring(8);
            logSessionExpired(sessionId);
            cleanupUserData(sessionId);
        }
    }
}
```

**Available Events:**
```
__keyevent@0__:expired     # Key expired
__keyevent@0__:set         # SET command executed
__keyevent@0__:del         # DEL command executed
__keyspace@0__:mykey       # Any operation on "mykey"
```

## Typical Use Cases

### 1. Real-time Booking Notifications

```java
// Microservice: Flight Service
@Service
public class FlightBookingService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void confirmBooking(Booking booking) {
        // Save booking to database
        bookingRepository.save(booking);

        // Publish event to other services
        BookingConfirmedEvent event = new BookingConfirmedEvent(booking);
        redisTemplate.convertAndSend("events:booking:confirmed", event);
    }
}

// Microservice: Email Service
@Service
public class EmailNotificationService {

    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        sendConfirmationEmail(event.getUserEmail(), event.getBookingDetails());
    }
}

// Microservice: Analytics Service
@Service
public class AnalyticsEventHandler {

    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        incrementBookingCounter(event.getFlightId());
        updateRevenueMetrics(event.getAmount());
    }
}
```

### 2. Price Alerts

```java
@Service
public class PriceMonitorService {

    @Scheduled(fixedRate = 60000) // Every minute
    public void checkPriceChanges() {
        List<PriceChange> changes = detectPriceChanges();

        for (PriceChange change : changes) {
            // Publish alert for users subscribed to this route
            PriceAlertEvent alert = new PriceAlertEvent(
                change.getRouteId(),
                change.getOldPrice(),
                change.getNewPrice(),
                change.getPercentageChange()
            );

            String channel = "alerts:price:route:" + change.getRouteId();
            redisTemplate.convertAndSend(channel, alert);
        }
    }
}

// User notification service subscribes per-user
@Service
public class UserNotificationService {

    public void subscribeToPriceAlerts(String userId, String routeId) {
        // Each user has a listener for their watched routes
        String channel = "alerts:price:route:" + routeId;
        subscribeUser(userId, channel);
    }
}
```

### 3. Inter-service Communication

```java
// API Gateway broadcasts rate limit warnings
@Service
public class RateLimitWarningService {

    public void warnAboutRateLimit(String userId, int remainingRequests) {
        if (remainingRequests < 10) {
            RateLimitWarning warning = new RateLimitWarning(userId, remainingRequests);
            redisTemplate.convertAndSend("system:ratelimit:warning", warning);
        }
    }
}

// All services receive and log warnings
@Service
public class SystemMonitor {

    @EventListener
    public void onRateLimitWarning(RateLimitWarning warning) {
        logger.warn("User {} approaching rate limit: {} requests remaining",
            warning.getUserId(), warning.getRemainingRequests());

        // Maybe notify user in UI via WebSocket
        webSocketService.sendToUser(warning.getUserId(), "rate_limit_warning", warning);
    }
}
```

### 4. Cache Invalidation

```java
@Service
public class CacheInvalidationService {

    // When flight data changes, notify all services to invalidate cache
    public void invalidateFlightCache(String flightId) {
        CacheInvalidationEvent event = new CacheInvalidationEvent("flight", flightId);
        redisTemplate.convertAndSend("cache:invalidate:flight", event);
    }
}

// All services listen and clear their local caches
@Service
public class LocalCacheManager {

    @Autowired
    private Cache localCache;

    @EventListener
    public void onCacheInvalidation(CacheInvalidationEvent event) {
        if ("flight".equals(event.getEntityType())) {
            localCache.evict("flight:" + event.getEntityId());
        }
    }
}
```

### 5. System Health Monitoring

```java
@Service
public class HealthCheckBroadcaster {

    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void broadcastHealth() {
        HealthStatus status = new HealthStatus(
            serviceName,
            cpuUsage,
            memoryUsage,
            activeConnections,
            Instant.now()
        );

        redisTemplate.convertAndSend("system:health:" + serviceName, status);
    }
}

// Monitoring dashboard subscribes to all health channels
@Service
public class MonitoringDashboard {

    @Bean
    RedisMessageListenerContainer healthMonitor(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.addMessageListener(
            healthListener(),
            new PatternTopic("system:health:*")
        );
        return container;
    }

    public void onHealthStatus(HealthStatus status) {
        updateDashboard(status);
        if (status.getCpuUsage() > 80) {
            alertOps("High CPU on " + status.getServiceName());
        }
    }
}
```

## Performance Characteristics

| Metric | Expected Value |
|--------|----------------|
| Publish latency | < 1ms |
| Delivery latency | < 5ms |
| Throughput | 1M+ msg/sec |
| Max channels | Millions |
| Max subscribers per channel | Thousands |

## Limitations & Considerations

### 1. No Message Persistence

**Problem**: Messages are lost if no subscribers are connected

**Solution**: Use Redis Streams for persistent messaging
```java
// Redis Streams alternative
redisTemplate.opsForStream().add("stream:bookings", event);
```

### 2. No Message Acknowledgment

**Problem**: Can't confirm subscriber received the message

**Solution**: Implement application-level ACKs
```java
@EventListener
public void onBooking(BookingEvent event) {
    try {
        processBooking(event);
        redisTemplate.convertAndSend("acks:booking:" + event.getId(), "OK");
    } catch (Exception e) {
        redisTemplate.convertAndSend("acks:booking:" + event.getId(), "ERROR");
    }
}
```

### 3. Slow Subscriber Backpressure

**Problem**: Slow subscriber buffers grow, hits `client-output-buffer-limit`, gets disconnected

**Solution**:
- Increase buffer limits
- Make subscribers faster (async processing)
- Use Redis Streams with consumer groups

```java
// Async processing to avoid slow subscriber
@EventListener
@Async
public void onBooking(BookingEvent event) {
    processBookingAsync(event);
}
```

### 4. Pattern Matching Performance

**Problem**: `PSUBSCRIBE` is slower than `SUBSCRIBE`

**Solution**: Use exact channel names when possible
```java
// Slower
PSUBSCRIBE events:*

// Faster
SUBSCRIBE events:booking:created
SUBSCRIBE events:payment:completed
```

## Monitoring Recommendations

```bash
# Active channels
redis-cli PUBSUB CHANNELS

# Count subscribers per channel
redis-cli PUBSUB NUMSUB events:booking:created

# Count pattern subscribers
redis-cli PUBSUB NUMPAT

# Monitor published messages
redis-cli --csv MONITOR | grep PUBLISH

# Check client output buffers
redis-cli CLIENT LIST | grep -i pubsub
```

## Best Practices

### 1. Channel Naming Convention

```
{domain}:{entity}:{action}:{identifier}

Examples:
events:booking:created
events:payment:completed
alerts:price:changed:route123
system:health:flight-service
cache:invalidate:hotel:456
```

### 2. Use Structured Messages (JSON)

```java
@Data
public class BookingEvent {
    private String eventId = UUID.randomUUID().toString();
    private String eventType = "BookingCreated";
    private Instant timestamp = Instant.now();
    private String bookingId;
    private String userId;
    private Map<String, Object> payload;
}

// Publish
redisTemplate.convertAndSend("events:booking:created", new ObjectMapper().writeValueAsString(event));

// Subscribe
ObjectMapper mapper = new ObjectMapper();
BookingEvent event = mapper.readValue(message.getBody(), BookingEvent.class);
```

### 3. Idempotent Subscribers

```java
// Store processed event IDs to avoid duplicate processing
@EventListener
public void onBooking(BookingEvent event) {
    String eventId = event.getEventId();

    // Check if already processed
    if (redisTemplate.opsForSet().isMember("processed:events", eventId)) {
        return; // Skip duplicate
    }

    processBooking(event);

    // Mark as processed
    redisTemplate.opsForSet().add("processed:events", eventId);
    redisTemplate.expire("processed:events", 24, TimeUnit.HOURS);
}
```

### 4. Graceful Disconnection Handling

```java
@Configuration
public class ResilientSubscriberConfig {

    @Bean
    RedisMessageListenerContainer resilientContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        // Auto-reconnect on connection loss
        container.setRecoveryInterval(5000L); // Retry every 5 seconds

        container.addMessageListener(
            bookingListener(),
            new PatternTopic("events:booking:*")
        );

        return container;
    }
}
```

## Configuration Trade-offs

| Setting | Pro | Con |
|---------|-----|-----|
| No persistence | Ultra-fast | Messages can be lost |
| High buffer limit | Handles bursts | More memory per client |
| Keyspace notifications | React to key changes | Small CPU overhead |

## When to Use This Configuration

✅ **Good for:**
- Real-time notifications
- Event broadcasting
- Microservice communication
- Live updates (dashboards, chat)
- Cache invalidation signals
- System monitoring

❌ **Not suitable for:**
- Guaranteed message delivery
- Message persistence (use Streams)
- Complex routing (use RabbitMQ/Kafka)
- Long-term event storage
- Transactional messaging

## Pub/Sub vs Redis Streams

| Feature | Pub/Sub | Streams |
|---------|---------|---------|
| Persistence | No | Yes |
| Message history | No | Yes |
| Consumer groups | No | Yes |
| Acknowledgments | No | Yes |
| Performance | Faster | Slightly slower |
| Use case | Fire-and-forget | Reliable messaging |

**When to switch to Streams:**
```java
// Use Streams when you need reliability
redisTemplate.opsForStream().add(
    StreamRecords.newRecord()
        .ofObject(event)
        .withStreamKey("stream:bookings")
);

// Consumer groups for guaranteed processing
```
