package com.analytics.aggregation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Analytics service using Redis Analytics instance (port 6382)
 *
 * Redis Configuration: allkeys-lfu, hybrid persistence (RDB+AOF)
 *
 * Purpose:
 * - Real-time analytics using Sorted Sets, HyperLogLog, Counters
 * - Track booking metrics, popular routes, revenue
 * - Event-driven data collection from flight/booking services
 *
 * Redis Data Structures:
 * 1. Counters (INCR) - total bookings, revenue
 * 2. Sorted Sets (ZADD) - popular routes, top flights
 * 3. HyperLogLog (PFADD) - unique users count
 * 4. Hashes (HSET) - flight statistics
 */
@Service
@Slf4j
public class AnalyticsService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Keys
    private static final String TOTAL_BOOKINGS = "analytics:bookings:total";
    private static final String FLIGHT_BOOKINGS = "analytics:bookings:flight:";
    private static final String ROUTE_BOOKINGS = "analytics:bookings:route:";
    private static final String POPULAR_ROUTES = "analytics:popular:routes";
    private static final String POPULAR_FLIGHTS = "analytics:popular:flights";
    private static final String DAILY_REVENUE = "analytics:revenue:daily:";
    private static final String UNIQUE_USERS = "analytics:unique:users:";
    private static final String FLIGHT_VIEWS = "analytics:views:flight:";

    /**
     * Record booking (called from event listener)
     */
    public void recordBooking(Long flightId, String routeId, String userId, Double amount) {
        // 1. Increment total bookings counter
        redisTemplate.opsForValue().increment(TOTAL_BOOKINGS);

        // 2. Increment flight-specific bookings
        redisTemplate.opsForValue().increment(FLIGHT_BOOKINGS + flightId);

        // 3. Increment route-specific bookings
        redisTemplate.opsForValue().increment(ROUTE_BOOKINGS + routeId);

        // 4. Update popular routes ranking (Sorted Set)
        redisTemplate.opsForZSet().incrementScore(POPULAR_ROUTES, routeId, 1);

        // 5. Update popular flights ranking
        redisTemplate.opsForZSet().incrementScore(POPULAR_FLIGHTS, flightId.toString(), 1);

        // 6. Track daily revenue
        String today = LocalDate.now().toString();
        redisTemplate.opsForValue().increment(DAILY_REVENUE + today, amount);

        // 7. Track unique users with HyperLogLog
        redisTemplate.opsForHyperLogLog().add(UNIQUE_USERS + today, userId);

        log.info("Recorded booking analytics: flight={}, route={}, amount={}", flightId, routeId, amount);
    }

    /**
     * Record flight view
     */
    public void recordFlightView(Long flightId, String userId) {
        String today = LocalDate.now().toString();
        redisTemplate.opsForHyperLogLog().add(FLIGHT_VIEWS + flightId + ":" + today, userId);
        log.debug("Recorded flight view: flight={}, user={}", flightId, userId);
    }

    /**
     * Get total bookings
     */
    public Long getTotalBookings() {
        String value = (String) redisTemplate.opsForValue().get(TOTAL_BOOKINGS);
        return value != null ? Long.parseLong(value) : 0L;
    }

    /**
     * Get bookings for specific flight
     */
    public Long getFlightBookings(Long flightId) {
        String value = (String) redisTemplate.opsForValue().get(FLIGHT_BOOKINGS + flightId);
        return value != null ? Long.parseLong(value) : 0L;
    }

    /**
     * Get top N popular routes (Sorted Set)
     */
    public Set<ZSetOperations.TypedTuple<Object>> getTopRoutes(int limit) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(POPULAR_ROUTES, 0, limit - 1);
    }

    /**
     * Get top N popular flights
     */
    public Set<ZSetOperations.TypedTuple<Object>> getTopFlights(int limit) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(POPULAR_FLIGHTS, 0, limit - 1);
    }

    /**
     * Get daily revenue
     */
    public Double getDailyRevenue(LocalDate date) {
        String value = (String) redisTemplate.opsForValue().get(DAILY_REVENUE + date.toString());
        return value != null ? Double.parseDouble(value) : 0.0;
    }

    /**
     * Get unique users count for a day (HyperLogLog)
     */
    public Long getUniqueUsers(LocalDate date) {
        return redisTemplate.opsForHyperLogLog().size(UNIQUE_USERS + date.toString());
    }

    /**
     * Get dashboard stats
     */
    public Map<String, Object> getDashboardStats() {
        LocalDate today = LocalDate.now();
        
        return Map.of(
                "totalBookings", getTotalBookings(),
                "todayRevenue", getDailyRevenue(today),
                "todayUniqueUsers", getUniqueUsers(today),
                "topRoutes", getTopRoutes(10),
                "topFlights", getTopFlights(10)
        );
    }
}
