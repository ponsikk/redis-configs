package com.analytics.shared.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Shared event publisher using Redis Pub/Sub instance (port 6383)
 *
 * Redis Configuration: no persistence, pub/sub optimized
 *
 * Purpose:
 * - Event-driven communication between microservices
 * - Publish events to Redis channels
 * - Decoupling services through async messaging
 */
@Service
@Slf4j
public class EventPublisher {

    private final RedisTemplate<String, Object> pubSubRedisTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(@Qualifier("pubSubRedisTemplate") RedisTemplate<String, Object> pubSubRedisTemplate,
                         ObjectMapper objectMapper) {
        this.pubSubRedisTemplate = pubSubRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish event to Redis channel
     */
    public void publish(String channel, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            pubSubRedisTemplate.convertAndSend(channel, json);
            log.debug("Published event to channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to publish event to channel: {}", channel, e);
        }
    }
}
