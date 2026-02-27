package com.analytics.shared.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared caching service using Redis Caching instance (port 6379)
 *
 * Redis Configuration: allkeys-lru, no persistence, optimized for speed
 *
 * Purpose:
 * - Generic caching layer for all microservices
 * - Reduce database load with LRU eviction
 * - Configurable TTLs per entity type
 */
@Service
@Slf4j
public class CacheService {

    private final RedisTemplate<String, Object> cacheRedisTemplate;

    public CacheService(@Qualifier("cacheRedisTemplate") RedisTemplate<String, Object> cacheRedisTemplate) {
        this.cacheRedisTemplate = cacheRedisTemplate;
    }

    public void cache(String key, Object value, long ttlSeconds) {
        cacheRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Cached key: {} with TTL: {}s", key, ttlSeconds);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = cacheRedisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("Cache HIT for key: {}", key);
            return (T) value;
        }
        log.debug("Cache MISS for key: {}", key);
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key) {
        return (List<T>) cacheRedisTemplate.opsForValue().get(key);
    }

    public void invalidate(String key) {
        cacheRedisTemplate.delete(key);
        log.info("Invalidated cache key: {}", key);
    }

    public void invalidatePattern(String pattern) {
        var keys = cacheRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            cacheRedisTemplate.delete(keys);
            log.info("Invalidated {} cache keys matching pattern: {}", keys.size(), pattern);
        }
    }
}
