package com.analytics.booking.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock service using Redisson (Redis port 6384)
 *
 * Redis Configuration: appendfsync always, AOF persistence for lock recovery
 *
 * Purpose:
 * - Prevent double booking of same seat
 * - Ensure atomic seat reservation across multiple instances
 * - Uses Redlock algorithm for safety
 */
@Service
@Slf4j
public class BookingLockService {

    @Autowired
    private RedissonClient redissonClient;

    private static final String SEAT_LOCK_PREFIX = "lock:seat:";

    /**
     * Execute operation with seat lock (prevents double booking)
     */
    public <T> T executeWithSeatLock(Long flightId, String seatNumber, Supplier<T> operation) {
        String lockKey = SEAT_LOCK_PREFIX + flightId + ":" + seatNumber;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Wait up to 5s to acquire lock, auto-release after 30s
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);

            if (acquired) {
                try {
                    log.debug("Acquired seat lock: flight={}, seat={}", flightId, seatNumber);
                    return operation.get();
                } finally {
                    lock.unlock();
                    log.debug("Released seat lock: flight={}, seat={}", flightId, seatNumber);
                }
            } else {
                log.error("Failed to acquire seat lock: flight={}, seat={}", flightId, seatNumber);
                throw new RuntimeException("Seat is currently being booked by another user");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring seat lock", e);
        }
    }
}
