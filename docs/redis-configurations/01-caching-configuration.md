# Redis Caching Configuration

## Use Case Overview

This Redis configuration is optimized for **caching flight and hotel data** in a high-traffic travel analytics system. The primary goal is to reduce database load by storing frequently accessed data with automatic eviction of less-used entries.

## Key Configuration Details

### Memory Management

```conf
maxmemory 2gb
maxmemory-policy allkeys-lru
maxmemory-samples 5
```

**Why these settings?**

- **maxmemory 2gb**: Limits Redis memory usage to 2GB, preventing OOM issues
- **allkeys-lru**: Uses Least Recently Used algorithm across ALL keys (not just those with TTL). This is ideal for pure caching where any key can be evicted
- **maxmemory-samples 5**: LRU is approximate; Redis samples 5 keys and evicts the least recently used. Higher values = more accurate but slower

### Persistence - Disabled

```conf
save ""
appendonly no
```

**Why disable persistence?**

- Cache data is **ephemeral** - it can be regenerated from the source database
- Disabling persistence improves performance (no disk I/O overhead)
- Faster restarts (no need to load RDB/AOF files)
- If Redis crashes, data is lost but can be rebuilt from the primary database

### Lazy Freeing

```conf
lazyfree-lazy-eviction yes
lazyfree-lazy-expire yes
lazyfree-lazy-server-del yes
```

**Why lazy freeing?**

- Memory deallocation happens in background threads
- Prevents blocking the main Redis thread when evicting large keys
- Critical for maintaining low latency in high-throughput caching scenarios

### Performance Tuning

```conf
hz 10
slowlog-log-slower-than 10000
```

- **hz 10**: Redis checks for expired keys 10 times per second (default). Good balance for cache cleanup
- **slowlog**: Logs commands taking >10ms, useful for identifying slow cache operations

## Typical Use Cases in the System

### 1. Popular Flight Routes Cache

```java
// Cache structure: "flights:route:NYC-LAX" -> JSON
String cacheKey = "flights:route:" + origin + "-" + destination;
redisTemplate.opsForValue().set(cacheKey, flightData, 15, TimeUnit.MINUTES);
```

### 2. Hotel Search Results

```java
// Cache expensive search queries
String cacheKey = "hotels:search:" + cityId + ":" + checkIn + ":" + checkOut;
redisTemplate.opsForValue().set(cacheKey, hotelResults, 30, TimeUnit.MINUTES);
```

### 3. Price Aggregations

```java
// Cache computed price statistics
String cacheKey = "analytics:prices:avg:" + routeId + ":" + date;
redisTemplate.opsForValue().set(cacheKey, avgPrice, 1, TimeUnit.HOURS);
```

## Performance Characteristics

| Metric | Expected Value |
|--------|----------------|
| Read latency | < 1ms |
| Write latency | < 1ms |
| Throughput | 100k+ ops/sec |
| Memory efficiency | ~85% effective usage |
| Cache hit ratio | Target: >80% |

## Monitoring Recommendations

```bash
# Check memory usage
redis-cli INFO memory | grep used_memory_human

# Monitor hit rate
redis-cli INFO stats | grep keyspace_hits
redis-cli INFO stats | grep keyspace_misses

# Check evictions
redis-cli INFO stats | grep evicted_keys

# Slow queries
redis-cli SLOWLOG GET 10
```

## Best Practices

1. **Set TTLs appropriately**
   - Flight prices: 10-15 minutes (dynamic pricing)
   - Hotel availability: 5-10 minutes (high volatility)
   - Static data (airport info): 24 hours

2. **Use consistent key naming**
   - Pattern: `{service}:{entity}:{identifier}`
   - Example: `flights:route:NYC-LAX`, `hotels:property:12345`

3. **Monitor cache hit ratio**
   - Target >80% hit rate
   - If lower, increase cache size or adjust TTLs

4. **Handle cache misses gracefully**
   - Always have fallback to database
   - Implement cache warming for critical data

## Configuration Trade-offs

| Setting | Pro | Con |
|---------|-----|-----|
| No persistence | Fast, no I/O overhead | Data lost on restart |
| allkeys-lru | Automatic eviction | May evict important keys |
| Lazy freeing | Non-blocking eviction | Slightly higher memory usage |

## When to Use This Configuration

✅ **Good for:**
- Read-heavy workloads
- Data that can be regenerated
- Acceptable to lose all data on restart
- Need for high throughput and low latency

❌ **Not suitable for:**
- Critical data that must persist
- Write-heavy workloads
- Data that's expensive to regenerate
- Need for strict consistency guarantees
