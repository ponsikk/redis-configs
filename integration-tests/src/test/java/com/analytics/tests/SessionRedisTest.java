package com.analytics.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Session Store Redis instance (Port 6380)
 *
 * Configuration:
 * - maxmemory: 1gb
 * - maxmemory-policy: volatile-lru
 * - AOF persistence (appendonly yes, appendfsync everysec)
 * - Keyspace notifications (notify-keyspace-events Ex)
 *
 * Use case: User session persistence across restarts
 */
class SessionRedisTest {

    private Jedis jedis;
    private ObjectMapper objectMapper;
    private static final String REDIS_HOST = "localhost";
    private static final int SESSION_PORT = 6380;

    @BeforeEach
    void setUp() {
        jedis = new Jedis(REDIS_HOST, SESSION_PORT);
        jedis.flushDB();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void testCreateSession() throws Exception {
        String sessionId = "550e8400-e29b-41d4-a716-446655440000";
        String key = "session:" + sessionId;

        UserSession session = new UserSession(
            sessionId,
            "user123",
            "192.168.1.1",
            Instant.now(),
            Instant.now()
        );

        String json = objectMapper.writeValueAsString(session);
        jedis.setex(key, 1800, json);

        String retrieved = jedis.get(key);
        assertNotNull(retrieved);

        UserSession deserialized = objectMapper.readValue(retrieved, UserSession.class);
        assertEquals("user123", deserialized.getUserId());
        assertEquals("192.168.1.1", deserialized.getIpAddress());
    }

    @Test
    void testSessionExpiration() throws InterruptedException {
        String key = "session:test-expiry";
        jedis.setex(key, 2, "{\"userId\":\"test\"}");

        assertTrue(jedis.exists(key));

        Thread.sleep(3000);

        assertFalse(jedis.exists(key));
    }

    @Test
    void testSlidingWindowRenewal() {
        String key = "session:sliding";
        jedis.setex(key, 1800, "{\"userId\":\"user123\"}");

        long ttl1 = jedis.ttl(key);

        // Simulate activity - renew TTL
        jedis.expire(key, 1800);

        long ttl2 = jedis.ttl(key);

        assertTrue(ttl2 >= ttl1);
    }

    @Test
    void testVolatileLRUPolicy() {
        String policy = jedis.configGet("maxmemory-policy").get(1);
        assertEquals("volatile-lru", policy);
    }

    @Test
    void testAOFPersistence() {
        String aofEnabled = jedis.configGet("appendonly").get(1);
        String appendfsync = jedis.configGet("appendfsync").get(1);

        assertEquals("yes", aofEnabled);
        assertEquals("everysec", appendfsync);
    }

    @Test
    void testKeyspaceNotifications() {
        String notifyConfig = jedis.configGet("notify-keyspace-events").get(1);
        assertTrue(notifyConfig.contains("E") && notifyConfig.contains("x"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UserSession {
        private String sessionId;
        private String userId;
        private String ipAddress;
        private Instant createdAt;
        private Instant lastAccessedAt;
    }
}
