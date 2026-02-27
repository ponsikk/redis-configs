package com.analytics.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Rate Limiting Redis instance (Port 6381)
 *
 * Configuration:
 * - maxmemory: 512mb
 * - maxmemory-policy: volatile-ttl
 * - High frequency expiration (hz 20)
 * - No persistence (appendonly no)
 *
 * Use case: API throttling and DDoS protection
 */
class RateLimitRedisTest {

    private Jedis jedis;
    private static final String REDIS_HOST = "localhost";
    private static final int RATE_LIMIT_PORT = 6381;

    @BeforeEach
    void setUp() {
        jedis = new Jedis(REDIS_HOST, RATE_LIMIT_PORT);
        jedis.flushDB();
    }

    @AfterEach
    void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void testFixedWindowRateLimit() {
        String userId = "user123";
        long currentWindow = Instant.now().getEpochSecond() / 60;
        String key = "ratelimit:user:" + userId + ":" + currentWindow;

        int maxRequests = 100;
        int windowSeconds = 60;

        for (int i = 0; i < maxRequests; i++) {
            Long count = jedis.incr(key);
            if (count == 1) {
                jedis.expire(key, windowSeconds);
            }
            assertTrue(count <= maxRequests);
        }

        // 101st request should exceed limit
        Long count = jedis.incr(key);
        assertTrue(count > maxRequests);
    }

    @Test
    void testLuaScriptAtomicity() {
        String script = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
                return 0
            end
            return 1
            """;

        String key = "ratelimit:test:lua";
        int ttl = 60;
        int limit = 10;

        for (int i = 0; i < limit; i++) {
            Object result = jedis.eval(script, 1, key, String.valueOf(ttl), String.valueOf(limit));
            assertEquals(1L, result);
        }

        // 11th request should fail
        Object result = jedis.eval(script, 1, key, String.valueOf(ttl), String.valueOf(limit));
        assertEquals(0L, result);
    }

    @Test
    void testVolatileTTLPolicy() {
        String policy = jedis.configGet("maxmemory-policy").get(1);
        assertEquals("volatile-ttl", policy);
    }

    @Test
    void testHighFrequencyExpiration() {
        String hz = jedis.configGet("hz").get(1);
        assertEquals("20", hz);
    }

    @Test
    void testNoPersistence() {
        String aofEnabled = jedis.configGet("appendonly").get(1);
        assertEquals("no", aofEnabled);
    }

    @Test
    void testMultipleUserRateLimits() {
        long window = Instant.now().getEpochSecond() / 60;

        for (int userId = 1; userId <= 5; userId++) {
            String key = "ratelimit:user:" + userId + ":" + window;
            jedis.setex(key, 60, String.valueOf(userId * 10));
        }

        assertEquals(5, jedis.keys("ratelimit:*").size());
    }

    @Test
    void testRateLimitExpiration() throws InterruptedException {
        String key = "ratelimit:test:expiring";
        jedis.setex(key, 2, "1");

        assertTrue(jedis.exists(key));

        Thread.sleep(3000);

        assertFalse(jedis.exists(key));
    }
}
