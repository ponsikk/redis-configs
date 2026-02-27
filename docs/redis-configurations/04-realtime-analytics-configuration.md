# Redis Real-time Analytics Configuration

## Use Case Overview

This Redis instance is optimized for **real-time analytics** on flight bookings, hotel reservations, and travel trends. It uses advanced data structures (Sorted Sets, HyperLogLog, Counters) to provide instant insights without querying the main database.

## Key Configuration Details

### Memory Management - LFU Algorithm

```conf
maxmemory 4gb
maxmemory-policy allkeys-lfu
lfu-log-factor 10
lfu-decay-time 1
```

**Why LFU (Least Frequently Used)?**

- **allkeys-lfu**: Evicts keys accessed **least frequently**, not least recently
- Perfect for analytics: popular routes/hotels stay in memory, rare ones get evicted
- **LFU vs LRU**:
  - LRU evicts old data even if frequently accessed
  - LFU keeps frequently queried analytics (e.g., "top 10 routes") even if not accessed recently

**LFU Tuning Parameters:**

- **lfu-log-factor 10**: Controls counter increment sensitivity
  - Higher value = slower counter growth = harder to stay in cache
  - Default: 10 (good for most cases)

- **lfu-decay-time 1**: Time in minutes for counter decay
  - Counter decreases if key not accessed
  - 1 minute = aggressive decay, keeps cache fresh

### Hybrid Persistence

```conf
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec
auto-aof-rewrite-min-size 128mb
```

**Why both RDB and AOF?**

- **RDB snapshots**: Fast backups, good for disaster recovery
  - `save 60 10000`: If 10k writes in 60s, create snapshot

- **AOF**: Durability for analytics data
  - `appendfsync everysec`: Balance of durability and performance

- **Hybrid approach**: Best of both worlds
  - RDB for fast restarts
  - AOF for minimal data loss

### Active Defragmentation

```conf
activedefrag yes
active-defrag-ignore-bytes 100mb
active-defrag-threshold-lower 10
active-defrag-threshold-upper 25
```

**Why defragmentation?**

- Analytics data has many updates (counters, sorted sets)
- Memory fragmentation builds up over time
- Active defrag runs in background to reclaim fragmented memory
- **Thresholds**: Starts at 10% fragmentation, aggressive at 25%

## Analytics Data Structures

### 1. Counters - Simple Metrics

**Use case**: Total bookings, revenue, page views

```java
@Service
public class AnalyticsService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Increment booking counter
    public void recordBooking(String flightId) {
        String key = "analytics:bookings:" + flightId;
        redisTemplate.opsForValue().increment(key);
    }

    // Get total bookings
    public Long getTotalBookings(String flightId) {
        String key = "analytics:bookings:" + flightId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    // Daily revenue tracking
    public void recordRevenue(String date, double amount) {
        String key = "analytics:revenue:" + date;
        redisTemplate.opsForValue().increment(key, amount);
    }
}
```

**Redis Commands:**
```bash
INCR analytics:bookings:flight123
INCRBY analytics:revenue:2024-03-15 250
GET analytics:bookings:flight123
```

### 2. Sorted Sets - Rankings & Leaderboards

**Use case**: Top routes, popular hotels, trending destinations

```java
// Track flight popularity by booking count
public void recordFlightBooking(String routeId, String flightId) {
    String key = "analytics:popular:routes:" + routeId;
    redisTemplate.opsForZSet().incrementScore(key, flightId, 1);
}

// Get top 10 flights for a route
public Set<ZSetOperations.TypedTuple<String>> getTopFlights(String routeId, int limit) {
    String key = "analytics:popular:routes:" + routeId;
    return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);
}

// Get rank of a specific flight
public Long getFlightRank(String routeId, String flightId) {
    String key = "analytics:popular:routes:" + routeId;
    return redisTemplate.opsForZSet().reverseRank(key, flightId);
}

// Get flights in a price range
public Set<String> getFlightsInPriceRange(double minPrice, double maxPrice) {
    String key = "analytics:prices:all";
    return redisTemplate.opsForZSet().rangeByScore(key, minPrice, maxPrice);
}
```

**Redis Commands:**
```bash
ZINCRBY analytics:popular:routes:NYC-LAX 1 flight123
ZREVRANGE analytics:popular:routes:NYC-LAX 0 9 WITHSCORES
ZREVRANK analytics:popular:routes:NYC-LAX flight123
ZRANGEBYSCORE analytics:prices:all 200 500
```

**Example Data:**
```
analytics:popular:routes:NYC-LAX
  flight123: 1542 bookings
  flight456: 1203 bookings
  flight789: 987 bookings
```

### 3. HyperLogLog - Unique Visitors

**Use case**: Unique users viewing a flight, unique IPs, daily active users

```java
// Track unique visitors to a flight page
public void recordFlightView(String flightId, String userId) {
    String key = "analytics:unique:views:" + flightId;
    redisTemplate.opsForHyperLogLog().add(key, userId);
}

// Get approximate unique visitor count
public Long getUniqueViewers(String flightId) {
    String key = "analytics:unique:views:" + flightId;
    return redisTemplate.opsForHyperLogLog().size(key);
}

// Daily active users
public void recordUserActivity(String date, String userId) {
    String key = "analytics:dau:" + date;
    redisTemplate.opsForHyperLogLog().add(key, userId);
}

public Long getDailyActiveUsers(String date) {
    String key = "analytics:dau:" + date;
    return redisTemplate.opsForHyperLogLog().size(key);
}
```

**Why HyperLogLog?**

- Stores ~12KB per key regardless of cardinality
- Can count billions of unique items
- 0.81% standard error (very accurate)
- Perfect for "approximately unique" counts

**Redis Commands:**
```bash
PFADD analytics:unique:views:flight123 user1 user2 user3
PFCOUNT analytics:unique:views:flight123
PFMERGE analytics:unique:views:all flight123 flight456 flight789
```

### 4. Hashes - Multi-dimensional Metrics

**Use case**: Aggregate statistics, grouped metrics

```java
// Store flight statistics
public void updateFlightStats(String flightId, Map<String, String> stats) {
    String key = "analytics:stats:flight:" + flightId;
    redisTemplate.opsForHash().putAll(key, stats);
}

// Example stats map:
Map<String, String> stats = Map.of(
    "totalBookings", "1542",
    "totalRevenue", "385000",
    "avgPrice", "250",
    "cancelRate", "0.05"
);

// Get specific metric
public String getMetric(String flightId, String metric) {
    String key = "analytics:stats:flight:" + flightId;
    return (String) redisTemplate.opsForHash().get(key, metric);
}

// Increment hash field
public void incrementCancellation(String flightId) {
    String key = "analytics:stats:flight:" + flightId;
    redisTemplate.opsForHash().increment(key, "cancellations", 1);
}
```

**Redis Commands:**
```bash
HSET analytics:stats:flight:123 totalBookings 1542 totalRevenue 385000
HGET analytics:stats:flight:123 avgPrice
HINCRBY analytics:stats:flight:123 cancellations 1
HGETALL analytics:stats:flight:123
```

### 5. Time-Series Data with Sorted Sets

**Use case**: Bookings over time, price history

```java
// Record booking with timestamp
public void recordBookingTimestamp(String flightId, String bookingId) {
    String key = "analytics:timeseries:bookings:" + flightId;
    long timestamp = System.currentTimeMillis();
    redisTemplate.opsForZSet().add(key, bookingId, timestamp);
}

// Get bookings in last hour
public Set<String> getRecentBookings(String flightId, int minutes) {
    String key = "analytics:timeseries:bookings:" + flightId;
    long now = System.currentTimeMillis();
    long cutoff = now - (minutes * 60 * 1000);
    return redisTemplate.opsForZSet().rangeByScore(key, cutoff, now);
}

// Count bookings per hour
public Long getBookingsInHour(String flightId, long hourTimestamp) {
    String key = "analytics:timeseries:bookings:" + flightId;
    long hourStart = hourTimestamp;
    long hourEnd = hourTimestamp + 3600000;
    return redisTemplate.opsForZSet().count(key, hourStart, hourEnd);
}
```

### 6. Bitmap - Boolean Analytics

**Use case**: Daily active users, feature usage tracking

```java
// Mark user as active on a given day
public void markUserActive(String date, long userId) {
    String key = "analytics:active:" + date;
    redisTemplate.opsForValue().setBit(key, userId, true);
}

// Check if user was active
public Boolean wasUserActive(String date, long userId) {
    String key = "analytics:active:" + date;
    return redisTemplate.opsForValue().getBit(key, userId);
}

// Count active users (requires Redis 7+)
public Long countActiveUsers(String date) {
    String key = "analytics:active:" + date;
    return redisTemplate.execute((RedisCallback<Long>) connection ->
        connection.bitCount(key.getBytes())
    );
}
```

## Typical Use Cases

### 1. Real-time Dashboard - Top Routes

```java
@RestController
public class AnalyticsDashboardController {

    @GetMapping("/analytics/top-routes")
    public List<RouteStats> getTopRoutes(@RequestParam(defaultValue = "10") int limit) {
        Set<ZSetOperations.TypedTuple<String>> topRoutes =
            redisTemplate.opsForZSet().reverseRangeWithScores("analytics:routes:bookings", 0, limit - 1);

        return topRoutes.stream()
            .map(tuple -> new RouteStats(
                tuple.getValue(),
                tuple.getScore().longValue()
            ))
            .collect(Collectors.toList());
    }
}
```

### 2. Search Trends

```java
// Track search queries
public void recordSearch(String query) {
    String key = "analytics:searches:trends";
    redisTemplate.opsForZSet().incrementScore(key, query.toLowerCase(), 1);

    // Also track by time for trending
    String timeKey = "analytics:searches:recent:" + getCurrentHour();
    redisTemplate.opsForZSet().incrementScore(timeKey, query.toLowerCase(), 1);
    redisTemplate.expire(timeKey, 24, TimeUnit.HOURS);
}

// Get trending searches in last hour
public Set<String> getTrendingSearches() {
    String key = "analytics:searches:recent:" + getCurrentHour();
    return redisTemplate.opsForZSet().reverseRange(key, 0, 9);
}
```

### 3. Conversion Funnel

```java
// Track funnel stages
public void recordFunnelStep(String userId, String step) {
    String dateKey = LocalDate.now().toString();
    redisTemplate.opsForHyperLogLog().add("analytics:funnel:" + step + ":" + dateKey, userId);
}

// Calculate conversion rate
public Map<String, Long> getFunnelMetrics(String date) {
    Map<String, Long> metrics = new HashMap<>();
    metrics.put("viewed", redisTemplate.opsForHyperLogLog().size("analytics:funnel:view:" + date));
    metrics.put("searched", redisTemplate.opsForHyperLogLog().size("analytics:funnel:search:" + date));
    metrics.put("booked", redisTemplate.opsForHyperLogLog().size("analytics:funnel:book:" + date));
    return metrics;
}
```

## Performance Characteristics

| Operation | Data Structure | Complexity | Use Case |
|-----------|----------------|------------|----------|
| Counter increment | String | O(1) | Total bookings |
| Top N ranking | Sorted Set | O(log N) | Popular flights |
| Add to ranking | Sorted Set | O(log N) | Track popularity |
| Unique count | HyperLogLog | O(1) | Unique visitors |
| Range query | Sorted Set | O(log N + M) | Price range |
| Multi-field stats | Hash | O(1) per field | Flight metrics |

## Monitoring Recommendations

```bash
# Check analytics data size
redis-cli --bigkeys

# Monitor sorted set operations
redis-cli SLOWLOG GET 10

# Check memory fragmentation
redis-cli INFO memory | grep fragmentation

# Monitor LFU evictions
redis-cli INFO stats | grep evicted_keys

# Check persistence status
redis-cli INFO persistence

# Analyze data types
redis-cli --scan --pattern "analytics:*" | head -100
```

## Best Practices

### 1. Use Key Expiration for Time-based Data

```java
// Hourly metrics expire after 7 days
String key = "analytics:hourly:" + hour;
redisTemplate.expire(key, 7, TimeUnit.DAYS);

// Daily metrics expire after 90 days
String dailyKey = "analytics:daily:" + date;
redisTemplate.expire(dailyKey, 90, TimeUnit.DAYS);
```

### 2. Aggregate Periodically

```java
@Scheduled(cron = "0 0 * * * *") // Every hour
public void aggregateHourlyToDaily() {
    String currentHour = getCurrentHour();
    String dailyKey = "analytics:daily:" + getCurrentDate();

    // Aggregate all hourly counters into daily
    for (int i = 0; i < 24; i++) {
        String hourKey = "analytics:hourly:" + getCurrentDate() + ":" + i;
        Long hourlyCount = getCount(hourKey);
        redisTemplate.opsForValue().increment(dailyKey, hourlyCount);
    }
}
```

### 3. Use Pipelines for Batch Operations

```java
public void recordBatchBookings(List<Booking> bookings) {
    redisTemplate.executePipelined(new SessionCallback<Object>() {
        @Override
        public Object execute(RedisOperations operations) {
            for (Booking booking : bookings) {
                operations.opsForValue().increment("analytics:total");
                operations.opsForZSet().incrementScore(
                    "analytics:routes:" + booking.getRouteId(),
                    booking.getFlightId(),
                    1
                );
                operations.opsForHyperLogLog().add(
                    "analytics:unique:users",
                    booking.getUserId()
                );
            }
            return null;
        }
    });
}
```

### 4. Naming Conventions

```
analytics:{metric}:{dimension}:{identifier}:{timeframe}

Examples:
analytics:bookings:flight:123:2024-03-15
analytics:revenue:route:NYC-LAX:2024-03
analytics:popular:hotels:city:NYC
analytics:unique:visitors:hotel:456
```

## Configuration Trade-offs

| Setting | Pro | Con |
|---------|-----|-----|
| LFU eviction | Keeps popular data | Complex tuning |
| Hybrid persistence | Fast + durable | More disk I/O |
| Active defrag | Reclaims memory | CPU overhead |
| 4GB memory | More analytics | Higher cost |

## When to Use This Configuration

✅ **Good for:**
- Real-time dashboards
- Leaderboards and rankings
- Trend analysis
- Unique visitor counting
- Time-series metrics
- Search analytics

❌ **Not suitable for:**
- Historical data (use time-series DB)
- Complex aggregations (use data warehouse)
- OLAP queries (use ClickHouse, BigQuery)
- Long-term storage (use S3 + Parquet)

## Scaling Considerations

### Current Capacity

- **4GB memory**: ~10M keys (depending on data structure)
- **Sorted Sets**: Efficient up to millions of elements per key
- **HyperLogLog**: Can count billions with 12KB

### When to Scale

1. **Memory pressure**: Upgrade to 8GB/16GB
2. **High write throughput**: Use Redis Cluster with sharding
3. **Complex queries**: Offload to ClickHouse/BigQuery
4. **Long-term trends**: Stream to data warehouse (Kafka → S3 → Athena)

### Hybrid Architecture

```
Real-time (Redis) → Batch (Spark) → Warehouse (Redshift)
   ↓                     ↓                    ↓
 Last hour          Last 7 days         Historical
 Sub-second         Minute delay        Query on demand
```
