package com.analytics.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.Tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Analytics Redis instance (Port 6382)
 *
 * Configuration:
 * - maxmemory: 4gb
 * - maxmemory-policy: allkeys-lfu
 * - Hybrid persistence (RDB + AOF)
 * - Active defragmentation enabled
 *
 * Use case: Real-time metrics with Sorted Sets, HyperLogLog, Counters, Hashes
 */
class AnalyticsRedisTest {

    private Jedis jedis;
    private static final String REDIS_HOST = "localhost";
    private static final int ANALYTICS_PORT = 6382;

    @BeforeEach
    void setUp() {
        jedis = new Jedis(REDIS_HOST, ANALYTICS_PORT);
        jedis.flushDB();
    }

    @AfterEach
    void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void testCounterIncrement() {
        String key = "analytics:bookings:total";

        jedis.incr(key);
        jedis.incr(key);
        jedis.incr(key);

        assertEquals("3", jedis.get(key));
    }

    @Test
    void testSortedSetForRankings() {
        String key = "analytics:popular:routes";

        jedis.zadd(key, 543, "NYC-LAX");
        jedis.zadd(key, 421, "LAX-SFO");
        jedis.zadd(key, 389, "JFK-MIA");
        jedis.zadd(key, 234, "BOS-CHI");

        // Get top 3 routes
        List<Tuple> top3 = jedis.zrevrangeWithScores(key, 0, 2);

        assertEquals(3, top3.size());
        assertEquals("NYC-LAX", top3.get(0).getElement());
        assertEquals(543.0, top3.get(0).getScore());
    }

    @Test
    void testSortedSetIncrementScore() {
        String key = "analytics:popular:routes";

        jedis.zadd(key, 100, "NYC-LAX");

        jedis.zincrby(key, 1, "NYC-LAX");
        jedis.zincrby(key, 1, "NYC-LAX");

        Double score = jedis.zscore(key, "NYC-LAX");
        assertEquals(102.0, score);
    }

    @Test
    void testHyperLogLogUniqueUsers() {
        String today = LocalDate.now().toString();
        String key = "analytics:unique:users:" + today;

        jedis.pfadd(key, "user1", "user2", "user3");
        jedis.pfadd(key, "user2", "user3", "user4");

        long uniqueCount = jedis.pfcount(key);
        assertEquals(4, uniqueCount);
    }

    @Test
    void testHashForFlightStats() {
        String key = "analytics:stats:flight:123";

        jedis.hset(key, "totalBookings", "1542");
        jedis.hset(key, "totalRevenue", "385000");
        jedis.hset(key, "avgPrice", "249.67");

        String bookings = jedis.hget(key, "totalBookings");
        assertEquals("1542", bookings);

        assertEquals(3, jedis.hgetAll(key).size());
    }

    @Test
    void testDailyRevenueTracking() {
        String today = LocalDate.now().toString();
        String key = "analytics:revenue:daily:" + today;

        jedis.incrByFloat(key, 250.00);
        jedis.incrByFloat(key, 350.50);
        jedis.incrByFloat(key, 199.99);

        Double total = Double.parseDouble(jedis.get(key));
        assertEquals(800.49, total, 0.01);
    }

    @Test
    void testLFUPolicy() {
        String policy = jedis.configGet("maxmemory-policy").get(1);
        assertEquals("allkeys-lfu", policy);
    }

    @Test
    void testHybridPersistence() {
        String aofEnabled = jedis.configGet("appendonly").get(1);
        String saveConfig = jedis.configGet("save").get(1);

        assertEquals("yes", aofEnabled);
        assertFalse(saveConfig.isEmpty());
    }

    @Test
    void testActiveDefragmentation() {
        String activeDefrag = jedis.configGet("activedefrag").get(1);
        assertEquals("yes", activeDefrag);
    }

    @Test
    void testComplexAnalyticsWorkflow() {
        // Simulate booking created event processing
        String bookingId = "BK123";
        String flightId = "FL456";
        String route = "NYC-LAX";
        String userId = "user789";
        String today = LocalDate.now().toString();
        double amount = 250.00;

        // Increment total bookings counter
        jedis.incr("analytics:bookings:total");

        // Update popular routes sorted set
        jedis.zincrby("analytics:popular:routes", 1, route);

        // Update popular flights sorted set
        jedis.zincrby("analytics:popular:flights", 1, flightId);

        // Add unique user to HyperLogLog
        jedis.pfadd("analytics:unique:users:" + today, userId);

        // Update daily revenue
        jedis.incrByFloat("analytics:revenue:daily:" + today, amount);

        // Update flight stats hash
        jedis.hincrBy("analytics:stats:flight:" + flightId, "totalBookings", 1);
        jedis.hincrByFloat("analytics:stats:flight:" + flightId, "totalRevenue", amount);

        // Verify all operations
        assertEquals("1", jedis.get("analytics:bookings:total"));
        assertEquals(1.0, jedis.zscore("analytics:popular:routes", route));
        assertEquals(1, jedis.pfcount("analytics:unique:users:" + today));
    }
}
