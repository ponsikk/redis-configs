package com.analytics.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Rate limiting service using Redis Rate Limit instance (port 6381)
 *
 * Redis Configuration: volatile-ttl, no persistence, high frequency expiration
 *
 * Purpose:
 * - Protect backend services from abuse
 * - Per-user and per-IP rate limiting
 * - Different limits per endpoint
 */
@Service
@Slf4j
public class RateLimitService {

    @Autowired
    @Qualifier("rateLimitRedisTemplate")
    private StringRedisTemplate rateLimitRedisTemplate;

    // Lua script for atomic rate limit check
    private static final String RATE_LIMIT_SCRIPT =
            "local key = KEYS[1] " +
            "local limit = tonumber(ARGV[1]) " +
            "local ttl = tonumber(ARGV[2]) " +
            "local current = redis.call('INCR', key) " +
            "if current == 1 then " +
            "    redis.call('EXPIRE', key, ttl) " +
            "end " +
            "if current > limit then " +
            "    return 0 " +
            "end " +
            "return 1";

    /**
     * Check if request is allowed
     */
    public boolean allowRequest(String identifier, int maxRequests, int windowSeconds) {
        long currentWindow = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = "ratelimit:" + identifier + ":" + currentWindow;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
        Long result = rateLimitRedisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(maxRequests),
                String.valueOf(windowSeconds)
        );

        boolean allowed = result != null && result == 1;

        if (!allowed) {
            log.warn("Rate limit exceeded for: {} (limit: {}/{}s)", identifier, maxRequests, windowSeconds);
        }

        return allowed;
    }

    /**
     * Get remaining requests
     */
    public int getRemainingRequests(String identifier, int maxRequests, int windowSeconds) {
        long currentWindow = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = "ratelimit:" + identifier + ":" + currentWindow;

        String value = rateLimitRedisTemplate.opsForValue().get(key);
        int current = value != null ? Integer.parseInt(value) : 0;

        return Math.max(0, maxRequests - current);
    }
}
