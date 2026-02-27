package com.analytics.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import redis.clients.jedis.Jedis;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Distributed Lock Redis instance (Port 6384)
 *
 * Configuration:
 * - maxmemory: 512mb
 * - maxmemory-policy: volatile-ttl
 * - AOF with appendfsync always (maximum durability)
 * - High frequency expiration (hz 20)
 *
 * Use case: Distributed locking to prevent race conditions (e.g., double booking)
 */
class DistributedLockRedisTest {

    private RedissonClient redissonClient;
    private Jedis jedis;
    private static final String REDIS_HOST = "localhost";
    private static final int LOCK_PORT = 6384;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + REDIS_HOST + ":" + LOCK_PORT);
        redissonClient = Redisson.create(config);

        jedis = new Jedis(REDIS_HOST, LOCK_PORT);
        jedis.flushDB();
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void testBasicLockAcquisition() throws InterruptedException {
        String lockKey = "lock:test:basic";
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
        assertTrue(acquired);
        assertTrue(lock.isLocked());

        lock.unlock();
        assertFalse(lock.isLocked());
    }

    @Test
    void testLockPreventsDoubleBooking() throws InterruptedException {
        String seatLockKey = "lock:seat:flight123:12A";
        AtomicInteger bookingsCreated = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        // Simulate two users trying to book same seat simultaneously
        Runnable bookingTask = () -> {
            RLock lock = redissonClient.getLock(seatLockKey);
            try {
                if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                    try {
                        // Simulate booking creation
                        Thread.sleep(100);
                        bookingsCreated.incrementAndGet();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };

        Thread user1 = new Thread(bookingTask);
        Thread user2 = new Thread(bookingTask);

        user1.start();
        user2.start();

        latch.await();

        // Only one booking should be created due to lock
        assertEquals(1, bookingsCreated.get());
    }

    @Test
    void testLockAutoExpiration() throws InterruptedException {
        String lockKey = "lock:test:expiring";
        RLock lock = redissonClient.getLock(lockKey);

        lock.tryLock(5, 2, TimeUnit.SECONDS);
        assertTrue(lock.isLocked());

        Thread.sleep(3000);

        // Lock should auto-expire after 2 seconds
        assertFalse(lock.isLocked());
    }

    @Test
    void testMultipleLocks() throws InterruptedException {
        String lock1Key = "lock:seat:flight100:1A";
        String lock2Key = "lock:seat:flight100:2B";

        RLock lock1 = redissonClient.getLock(lock1Key);
        RLock lock2 = redissonClient.getLock(lock2Key);

        assertTrue(lock1.tryLock(5, 30, TimeUnit.SECONDS));
        assertTrue(lock2.tryLock(5, 30, TimeUnit.SECONDS));

        assertTrue(lock1.isLocked());
        assertTrue(lock2.isLocked());

        lock1.unlock();
        lock2.unlock();
    }

    @Test
    void testLockContention() throws InterruptedException {
        String lockKey = "lock:test:contention";
        int threads = 10;
        AtomicInteger successfulAcquisitions = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                RLock lock = redissonClient.getLock(lockKey);
                try {
                    if (lock.tryLock(1, 10, TimeUnit.SECONDS)) {
                        try {
                            successfulAcquisitions.incrementAndGet();
                            Thread.sleep(50);
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        // All threads should eventually acquire the lock
        assertEquals(threads, successfulAcquisitions.get());
    }

    @Test
    void testAppendFsyncAlways() {
        String appendfsync = jedis.configGet("appendfsync").get(1);
        assertEquals("always", appendfsync);
    }

    @Test
    void testAOFEnabled() {
        String aofEnabled = jedis.configGet("appendonly").get(1);
        assertEquals("yes", aofEnabled);
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
    void testLockKeyPattern() throws InterruptedException {
        RLock lock1 = redissonClient.getLock("lock:seat:flight123:12A");
        RLock lock2 = redissonClient.getLock("lock:seat:flight456:5C");

        lock1.tryLock(5, 30, TimeUnit.SECONDS);
        lock2.tryLock(5, 30, TimeUnit.SECONDS);

        assertEquals(2, jedis.keys("lock:*").size());

        lock1.unlock();
        lock2.unlock();
    }
}
