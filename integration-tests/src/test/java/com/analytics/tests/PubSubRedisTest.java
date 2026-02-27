package com.analytics.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Pub/Sub Redis instance (Port 6383)
 *
 * Configuration:
 * - maxmemory: 256mb
 * - maxmemory-policy: noeviction
 * - No persistence (save "", appendonly no)
 * - Pub/Sub buffer limits configured
 *
 * Use case: Event-driven communication between microservices
 */
class PubSubRedisTest {

    private Jedis publisher;
    private Jedis subscriber;
    private ObjectMapper objectMapper;
    private static final String REDIS_HOST = "localhost";
    private static final int PUBSUB_PORT = 6383;

    @BeforeEach
    void setUp() {
        publisher = new Jedis(REDIS_HOST, PUBSUB_PORT);
        subscriber = new Jedis(REDIS_HOST, PUBSUB_PORT);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (publisher != null) {
            publisher.close();
        }
        if (subscriber != null) {
            subscriber.close();
        }
    }

    @Test
    void testBasicPubSub() throws InterruptedException {
        String channel = "events:test";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        Thread subscriberThread = new Thread(() -> {
            subscriber.subscribe(new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    receivedMessage.set(message);
                    latch.countDown();
                    unsubscribe();
                }
            }, channel);
        });

        subscriberThread.start();
        Thread.sleep(100);

        publisher.publish(channel, "Hello Pub/Sub");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("Hello Pub/Sub", receivedMessage.get());
    }

    @Test
    void testBookingEventPublish() throws Exception {
        String channel = "events:booking:created";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BookingEvent> receivedEvent = new AtomicReference<>();

        Thread subscriberThread = new Thread(() -> {
            subscriber.subscribe(new JedisPubSub() {
                @Override
                public void onMessage(String ch, String message) {
                    try {
                        BookingEvent event = objectMapper.readValue(message, BookingEvent.class);
                        receivedEvent.set(event);
                        latch.countDown();
                        unsubscribe();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, channel);
        });

        subscriberThread.start();
        Thread.sleep(100);

        BookingEvent event = new BookingEvent(
            "evt-123",
            "BOOKING_CREATED",
            Instant.now(),
            1L,
            "BK123456",
            100L,
            "user789",
            250.00
        );

        String json = objectMapper.writeValueAsString(event);
        publisher.publish(channel, json);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(receivedEvent.get());
        assertEquals("BK123456", receivedEvent.get().getBookingReference());
        assertEquals(250.00, receivedEvent.get().getAmount());
    }

    @Test
    void testPatternSubscription() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> channel1 = new AtomicReference<>();
        AtomicReference<String> channel2 = new AtomicReference<>();

        Thread subscriberThread = new Thread(() -> {
            subscriber.psubscribe(new JedisPubSub() {
                @Override
                public void onPMessage(String pattern, String channel, String message) {
                    if (channel1.get() == null) {
                        channel1.set(channel);
                    } else {
                        channel2.set(channel);
                    }
                    latch.countDown();
                    if (latch.getCount() == 0) {
                        punsubscribe();
                    }
                }
            }, "events:booking:*");
        });

        subscriberThread.start();
        Thread.sleep(100);

        publisher.publish("events:booking:created", "event1");
        publisher.publish("events:booking:cancelled", "event2");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("events:booking:created", channel1.get());
        assertEquals("events:booking:cancelled", channel2.get());
    }

    @Test
    void testNoEvictionPolicy() {
        String policy = publisher.configGet("maxmemory-policy").get(1);
        assertEquals("noeviction", policy);
    }

    @Test
    void testNoPersistence() {
        String aofEnabled = publisher.configGet("appendonly").get(1);
        assertEquals("no", aofEnabled);
    }

    @Test
    void testPubSubBufferLimits() {
        String bufferConfig = publisher.configGet("client-output-buffer-limit").get(1);
        assertTrue(bufferConfig.contains("pubsub"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class BookingEvent {
        private String eventId;
        private String eventType;
        private Instant timestamp;
        private Long bookingId;
        private String bookingReference;
        private Long flightId;
        private String userId;
        private Double amount;
    }
}
