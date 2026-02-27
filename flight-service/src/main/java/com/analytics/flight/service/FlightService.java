package com.analytics.flight.service;

import com.analytics.flight.model.Flight;
import com.analytics.flight.repository.FlightRepository;
import com.analytics.shared.cache.CacheService;
import com.analytics.shared.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flight service - business logic for flight operations
 *
 * Responsibilities:
 * - CRUD operations for flights
 * - Search and filtering
 * - Uses shared CacheService and EventPublisher
 */
@Service
@Slf4j
public class FlightService {

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private EventPublisher eventPublisher;

    @Value("${cache.ttl.flights:900}")
    private long flightCacheTtl;

    private static final String FLIGHT_CACHE_PREFIX = "flights:flight:";
    private static final String ROUTE_CACHE_PREFIX = "flights:route:";
    private static final String FLIGHT_UPDATED_CHANNEL = "events:flight:updated";

    /**
     * Get flight by ID with caching
     */
    public Flight getFlight(Long flightId) {
        // Try cache first
        String cacheKey = FLIGHT_CACHE_PREFIX + flightId;
        Flight cached = cacheService.get(cacheKey, Flight.class);
        if (cached != null) {
            return cached;
        }

        // Cache miss - get from database
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found"));

        // Cache for future requests
        cacheService.cache(cacheKey, flight, flightCacheTtl);

        return flight;
    }

    /**
     * Search flights by route with caching
     */
    public List<Flight> searchFlights(String origin, String destination,
                                      LocalDateTime startDate, LocalDateTime endDate) {

        // Try cache first
        String cacheKey = ROUTE_CACHE_PREFIX + origin + "-" + destination;
        List<Flight> cached = cacheService.getList(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // Cache miss - query database
        List<Flight> flights = flightRepository.findAvailableFlights(origin, destination, startDate, endDate);

        // Cache results
        cacheService.cache(cacheKey, flights, flightCacheTtl);

        return flights;
    }

    /**
     * Get all flights
     */
    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    /**
     * Create new flight
     */
    public Flight createFlight(Flight flight) {
        flight.setStatus("SCHEDULED");
        Flight saved = flightRepository.save(flight);

        log.info("Flight created: {}", saved.getFlightNumber());
        return saved;
    }

    /**
     * Update flight (invalidates cache and publishes event)
     */
    public Flight updateFlight(Long flightId, Flight updates) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found"));

        // Update fields
        if (updates.getDepartureTime() != null) {
            flight.setDepartureTime(updates.getDepartureTime());
        }
        if (updates.getArrivalTime() != null) {
            flight.setArrivalTime(updates.getArrivalTime());
        }
        if (updates.getBasePrice() != null) {
            flight.setBasePrice(updates.getBasePrice());
        }
        if (updates.getStatus() != null) {
            flight.setStatus(updates.getStatus());
        }

        Flight updated = flightRepository.save(flight);

        // Invalidate cache
        String cacheKey = FLIGHT_CACHE_PREFIX + flightId;
        cacheService.invalidate(cacheKey);

        String routeCacheKey = ROUTE_CACHE_PREFIX + flight.getOrigin() + "-" + flight.getDestination();
        cacheService.invalidate(routeCacheKey);

        // Publish update event for other services
        eventPublisher.publish(FLIGHT_UPDATED_CHANNEL, Map.of(
                "flightId", flightId,
                "updateType", "DETAILS_UPDATED"
        ));

        log.info("Flight updated: {}", flight.getFlightNumber());
        return updated;
    }

    /**
     * Delete flight
     */
    public void deleteFlight(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found"));

        flightRepository.delete(flight);

        // Invalidate cache
        cacheService.invalidate(FLIGHT_CACHE_PREFIX + flightId);
        cacheService.invalidate(ROUTE_CACHE_PREFIX + flight.getOrigin() + "-" + flight.getDestination());

        log.info("Flight deleted: {}", flightId);
    }
}
