# Redis Rate Limiting Configuration

## Use Case Overview

This Redis instance is optimized for **API rate limiting** and throttling in the API Gateway. It tracks request counts per user/IP with sliding window algorithms to prevent abuse and ensure fair resource usage across all clients.

## Key Configuration Details

### Memory Management

```conf
maxmemory 512mb
maxmemory-policy volatile-ttl
```

**Why these settings?**

- **maxmemory 512mb**: Rate limit counters are tiny (few bytes per key), 512MB is sufficient for millions of rate limit entries
- **volatile-ttl**: Evicts keys with **shortest TTL first**. Perfect for rate limiting where older counters should expire first

### Persistence - Disabled

```conf
save ""
appendonly no
stop-writes-on-bgsave-error no
```

**Why no persistence?**

- Rate limit data is **highly transient** (expires in seconds/minutes)
- If Redis crashes, counters reset - users get fresh limits (acceptable trade-off)
- Maximum performance: no disk I/O overhead
- Faster operations critical for every API request

### High-Frequency Key Expiration

```conf
hz 20
```

**Why hz 20?**

- Default hz is 10 (10 checks/second for expired keys)
- **hz 20** = 20 checks/second, doubles the rate of expired key cleanup
- Critical for rate limiting: faster cleanup of expired counters saves memory
- Small CPU trade-off for better memory efficiency

### Performance Optimization

```conf
maxclients 20000
slowlog-log-slower-than 5000
```

- **maxclients 20000**: API Gateway needs high connection count (many concurrent API users)
- **slowlog 5000**: Very sensitive slow query logging (>5ms). Rate limiting must be ultra-fast

### Lazy Freeing

```conf
lazyfree-lazy-eviction yes
lazyfree-lazy-expire yes
```

- Background deletion of expired counters
- Prevents blocking the main thread when millions of counters expire

## Rate Limiting Algorithms

### 1. Fixed Window Counter

Simple counter that resets every N seconds.

```java
@Service
public class RateLimitService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public boolean allowRequest(String userId, int maxRequests, int windowSeconds) {
        String key = "ratelimit:fixed:" + userId + ":" + getCurrentWindow(windowSeconds);

        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount == 1) {
            // First request in this window, set expiration
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        return currentCount <= maxRequests;
    }

    private long getCurrentWindow(int windowSeconds) {
        return System.currentTimeMillis() / 1000 / windowSeconds;
    }
}
```

**Example**: 100 requests per minute
- Key: `ratelimit:fixed:user123:28506720` (minute window)
- Counter increments with each request
- Resets when new minute starts

**Pros**: Simple, efficient
**Cons**: Burst at window boundaries (100 requests at :59, 100 at :00 = 200 in 2 seconds)

### 2. Sliding Window Log

Stores timestamp of each request.

```java
public boolean allowRequest(String userId, int maxRequests, int windowSeconds) {
    String key = "ratelimit:log:" + userId;
    long now = System.currentTimeMillis();
    long windowStart = now - (windowSeconds * 1000);

    // Remove old entries
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

    // Check count
    Long count = redisTemplate.opsForZSet().count(key, windowStart, now);

    if (count < maxRequests) {
        // Add current request timestamp
        redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
        redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        return true;
    }

    return false;
}
```

**Example**: Last 100 requests in 60 seconds
- Key: `ratelimit:log:user123`
- Sorted Set with scores = timestamps
- Counts entries in sliding window

**Pros**: Smooth rate limiting, no bursts
**Cons**: Higher memory usage (stores all timestamps)

### 3. Sliding Window Counter (Hybrid)

Combines fixed window efficiency with sliding window smoothness.

```java
public boolean allowRequest(String userId, int maxRequests, int windowSeconds) {
    long now = System.currentTimeMillis() / 1000;
    long currentWindow = now / windowSeconds;
    long previousWindow = currentWindow - 1;

    String currentKey = "ratelimit:sliding:" + userId + ":" + currentWindow;
    String previousKey = "ratelimit:sliding:" + userId + ":" + previousWindow;

    Long currentCount = redisTemplate.opsForValue().increment(currentKey);
    if (currentCount == 1) {
        redisTemplate.expire(currentKey, windowSeconds * 2, TimeUnit.SECONDS);
    }

    Long previousCount = Optional.ofNullable(
        redisTemplate.opsForValue().get(previousKey)
    ).map(Long::parseLong).orElse(0L);

    // Calculate sliding window weight
    double percentageInCurrent = (now % windowSeconds) / (double) windowSeconds;
    double estimatedCount = previousCount * (1 - percentageInCurrent) + currentCount;

    return estimatedCount <= maxRequests;
}
```

**Pros**: Balance of accuracy and efficiency
**Cons**: Slightly more complex

### 4. Token Bucket (Burst Handling)

Allows controlled bursts above the rate limit.

```java
public boolean allowRequest(String userId, int tokensPerSecond, int bucketSize) {
    String key = "ratelimit:bucket:" + userId;

    Map<Object, Object> bucket = redisTemplate.opsForHash().entries(key);

    long now = System.currentTimeMillis();
    long lastRefill = Long.parseLong(bucket.getOrDefault("lastRefill", "0").toString());
    double tokens = Double.parseDouble(bucket.getOrDefault("tokens", String.valueOf(bucketSize)).toString());

    // Refill tokens based on time passed
    if (lastRefill > 0) {
        double secondsPassed = (now - lastRefill) / 1000.0;
        tokens = Math.min(bucketSize, tokens + secondsPassed * tokensPerSecond);
    }

    if (tokens >= 1) {
        tokens -= 1;
        redisTemplate.opsForHash().put(key, "tokens", String.valueOf(tokens));
        redisTemplate.opsForHash().put(key, "lastRefill", String.valueOf(now));
        redisTemplate.expire(key, 3600, TimeUnit.SECONDS);
        return true;
    }

    return false;
}
```

**Pros**: Allows bursts, gradual token refill
**Cons**: More complex state management

## Typical Use Cases

### 1. API Gateway Rate Limiting

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = extractUserId(request);
        String endpoint = request.getRequestURI();

        // Different limits for different endpoints
        RateLimit limit = getRateLimitForEndpoint(endpoint);

        if (!rateLimitService.allowRequest(userId, limit.maxRequests, limit.windowSeconds)) {
            response.setStatus(429); // Too Many Requests
            response.setHeader("X-RateLimit-Retry-After", String.valueOf(limit.windowSeconds));
            return false;
        }

        return true;
    }
}
```

### 2. Per-User Limits

```
ratelimit:user:{userId}:minute       // 100 requests/minute
ratelimit:user:{userId}:hour         // 1000 requests/hour
ratelimit:user:{userId}:day          // 10000 requests/day
```

### 3. Per-IP Limits (DDoS Protection)

```java
String ipAddress = request.getRemoteAddr();
String key = "ratelimit:ip:" + ipAddress;

if (!rateLimitService.allowRequest(key, 1000, 60)) {
    // Block this IP temporarily
    redisTemplate.opsForValue().set("blocked:ip:" + ipAddress, "1", 1, TimeUnit.HOURS);
    throw new TooManyRequestsException();
}
```

### 4. Tiered Rate Limits

```java
public class TieredRateLimitService {

    private static final Map<UserTier, RateLimit> TIER_LIMITS = Map.of(
        UserTier.FREE, new RateLimit(100, 3600),      // 100/hour
        UserTier.BASIC, new RateLimit(1000, 3600),    // 1k/hour
        UserTier.PREMIUM, new RateLimit(10000, 3600), // 10k/hour
        UserTier.ENTERPRISE, new RateLimit(100000, 3600) // 100k/hour
    );

    public boolean allowRequest(User user) {
        RateLimit limit = TIER_LIMITS.get(user.getTier());
        return allowRequest(user.getId(), limit.maxRequests, limit.windowSeconds);
    }
}
```

## Performance Characteristics

| Metric | Expected Value |
|--------|----------------|
| Latency | < 1ms (critical) |
| Throughput | 200k+ checks/sec |
| Memory per user | 50-500 bytes |
| Check frequency | Every API request |

## Monitoring Recommendations

```bash
# Monitor rate limit hits
redis-cli --scan --pattern "ratelimit:*" | wc -l

# Check memory usage
redis-cli INFO memory | grep used_memory_human

# Find top rate-limited users
redis-cli --scan --pattern "ratelimit:*" --count 1000 | xargs redis-cli MGET | sort | uniq -c | sort -rn | head -20

# Monitor command latency
redis-cli --latency

# Check expired keys cleanup
redis-cli INFO stats | grep expired_keys
```

## Redis Commands Used

| Command | Use Case | Performance |
|---------|----------|-------------|
| INCR | Fixed window counter | O(1) - fastest |
| ZADD | Sliding window log | O(log N) |
| ZREMRANGEBYSCORE | Remove old timestamps | O(log N + M) |
| ZCOUNT | Count in time range | O(log N) |
| GET/SET | Token bucket state | O(1) |
| EXPIRE | Auto cleanup | O(1) |

## Best Practices

### 1. Return Rate Limit Headers

```java
response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
```

### 2. Graceful Degradation

```java
public boolean allowRequest(String userId, int maxRequests, int windowSeconds) {
    try {
        return rateLimitService.allowRequest(userId, maxRequests, windowSeconds);
    } catch (RedisConnectionFailure e) {
        // If Redis is down, allow requests (fail open)
        logger.error("Redis unavailable, bypassing rate limit", e);
        return true;
    }
}
```

### 3. Use Lua Scripts for Atomicity

```lua
-- Fixed window with atomic increment and expire
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

local current = redis.call('INCR', key)
if current == 1 then
    redis.call('EXPIRE', key, ttl)
end

if current > limit then
    return 0
end
return 1
```

```java
// Execute Lua script
Boolean allowed = redisTemplate.execute(
    rateLimitScript,
    Collections.singletonList(key),
    String.valueOf(maxRequests),
    String.valueOf(windowSeconds)
);
```

## Configuration Trade-offs

| Setting | Pro | Con |
|---------|-----|-----|
| No persistence | Ultra-fast | Lose limits on restart |
| volatile-ttl | Efficient cleanup | Must set TTL on all keys |
| hz 20 | Faster expiration | Slightly more CPU |
| High maxclients | Handles many connections | More memory overhead |

## When to Use This Configuration

✅ **Good for:**
- API gateway throttling
- DDoS protection
- Fair resource allocation
- Preventing abuse
- SLA enforcement (tiered limits)

❌ **Not suitable for:**
- Strict distributed rate limiting (single instance has race conditions)
- Financial transactions (need ACID guarantees)
- Cases where losing limits on restart is unacceptable

## Scaling Considerations

### Current Limitation

Single Redis instance has race conditions in distributed systems:
- Service A and Service B might both increment counter simultaneously
- Could allow slightly more requests than limit

### Solutions

1. **Redis Cluster with hash tags**
   ```java
   String key = "{user:" + userId + "}:ratelimit";
   // Ensures all keys for a user go to same shard
   ```

2. **Use INCR + Lua for atomicity**
   - INCR is atomic even in distributed systems

3. **Redis Cell Module**
   - Native token bucket implementation
   - `CL.THROTTLE user123 15 30 60` - 15 requests per 30 seconds, burst of 60
