package com.analytics.gateway.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Session service using Redis Session Store instance (port 6380)
 *
 * Redis Configuration: volatile-lru, AOF persistence
 *
 * Purpose:
 * - Store user sessions across multiple gateway instances
 * - Session expiration and sliding window
 * - Survive gateway restarts
 */
@Service
@Slf4j
public class SessionService {

    @Autowired
    @Qualifier("sessionRedisTemplate")
    private RedisTemplate<String, Object> sessionRedisTemplate;

    private static final String SESSION_PREFIX = "session:";
    private static final long SESSION_TTL_MINUTES = 30;

    /**
     * Create new session
     */
    public String createSession(String userId, String ipAddress) {
        String sessionId = UUID.randomUUID().toString();
        String key = SESSION_PREFIX + sessionId;

        UserSession session = new UserSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setIpAddress(ipAddress);
        session.setCreatedAt(Instant.now());
        session.setLastAccessedAt(Instant.now());

        sessionRedisTemplate.opsForValue().set(key, session, SESSION_TTL_MINUTES, TimeUnit.MINUTES);

        log.info("Created session: {} for user: {}", sessionId, userId);
        return sessionId;
    }

    /**
     * Get session and extend TTL (sliding window)
     */
    public UserSession getSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        UserSession session = (UserSession) sessionRedisTemplate.opsForValue().get(key);

        if (session != null) {
            // Extend session TTL (sliding window)
            session.setLastAccessedAt(Instant.now());
            sessionRedisTemplate.opsForValue().set(key, session, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("Extended session: {}", sessionId);
        } else {
            log.warn("Session not found or expired: {}", sessionId);
        }

        return session;
    }

    /**
     * Invalidate session (logout)
     */
    public void invalidateSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        sessionRedisTemplate.delete(key);
        log.info("Invalidated session: {}", sessionId);
    }

    /**
     * Check if session exists
     */
    public boolean isValidSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        return Boolean.TRUE.equals(sessionRedisTemplate.hasKey(key));
    }

    @Data
    public static class UserSession implements Serializable {
        private String sessionId;
        private String userId;
        private String ipAddress;
        private Instant createdAt;
        private Instant lastAccessedAt;
    }
}
