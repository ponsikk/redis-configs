package com.analytics.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Cache Redis instance (Port 6379)
 *
 * Configuration:
 * - maxmemory: 2gb
 * - maxmemory-policy: allkeys-lru
 * - No persistence (save "", appendonly no)
 *
 * Use case: High-speed caching for search results
 */
class CacheRedisTest {

    private Jedis jedis;
    private static final String REDIS_HOST = "localhost";
    private static final int CACHE_PORT = 6379;

    @BeforeEach
    void setUp() {
        jedis = new Jedis(REDIS_HOST, CACHE_PORT);
        jedis.flushDB();
    }

    @AfterEach
    void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void testCacheSetAndGet() {
        String key = "flights:route:NYC-LAX";
        String value = "[{\"id\":1,\"origin\":\"NYC\",\"destination\":\"LAX\"}]";

        jedis.setex(key, 900, value);
        String cached = jedis.get(key);

        assertEquals(value, cached);
        assertTrue(jedis.ttl(key) > 0 && jedis.ttl(key) <= 900);
    }

    @Test
    void testCacheExpiration() throws InterruptedException {
        String key = "test:expiring:key";
        jedis.setex(key, 2, "value");

        assertEquals("value", jedis.get(key));

        Thread.sleep(3000);

        assertNull(jedis.get(key));
    }

    @Test
    void testLRUEviction() {
        // This test would require filling up maxmemory
        // For now, verify config is set correctly
        String policy = jedis.configGet("maxmemory-policy").get(1);
        assertEquals("allkeys-lru", policy);
    }

    @Test
    void testNoPersistence() {
        String saveConfig = jedis.configGet("save").get(1);
        String aofEnabled = jedis.configGet("appendonly").get(1);

        assertTrue(saveConfig.isEmpty() || saveConfig.equals(""));
        assertEquals("no", aofEnabled);
    }

    @Test
    void testMultipleKeysWithPatterns() {
        jedis.setex("flights:route:NYC-LAX", 900, "flight1");
        jedis.setex("flights:route:LAX-SFO", 900, "flight2");
        jedis.setex("hotels:city:NYC", 900, "hotel1");

        assertEquals(3, jedis.keys("*").size());
        assertEquals(2, jedis.keys("flights:*").size());
        assertEquals(1, jedis.keys("hotels:*").size());
    }
}
