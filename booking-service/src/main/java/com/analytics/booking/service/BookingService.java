package com.analytics.booking.service;

import com.analytics.booking.model.Booking;
import com.analytics.booking.repository.BookingRepository;
import com.analytics.shared.cache.CacheService;
import com.analytics.shared.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Booking service - business logic for bookings
 *
 * Responsibilities:
 * - Create/confirm/cancel bookings
 * - Distributed locking for seat reservation
 * - Publish events to other services
 */
@Service
@Slf4j
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingLockService lockService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private EventPublisher eventPublisher;

    private static final String BOOKING_CACHE_PREFIX = "bookings:user:";
    private static final String BOOKING_CREATED_CHANNEL = "events:booking:created";
    private static final String BOOKING_CONFIRMED_CHANNEL = "events:booking:confirmed";
    private static final String BOOKING_CANCELLED_CHANNEL = "events:booking:cancelled";

    /**
     * Create booking with distributed lock
     */
    @Transactional
    public Booking createBooking(Long flightId, String userId, String passengerName,
                                 String passengerEmail, String seatNumber, BigDecimal price) {

        return lockService.executeWithSeatLock(flightId, seatNumber, () -> {
            // Check if seat already booked
            if (bookingRepository.existsByFlightIdAndSeatNumberAndStatus(flightId, seatNumber, "CONFIRMED")) {
                throw new RuntimeException("Seat " + seatNumber + " is already booked");
            }

            // Create booking
            Booking booking = new Booking();
            booking.setBookingReference(generateBookingReference());
            booking.setFlightId(flightId);
            booking.setUserId(userId);
            booking.setPassengerName(passengerName);
            booking.setPassengerEmail(passengerEmail);
            booking.setSeatNumber(seatNumber);
            booking.setPrice(price);
            booking.setStatus("PENDING");
            booking.setBookingDate(LocalDateTime.now());

            Booking saved = bookingRepository.save(booking);

            // Invalidate user bookings cache
            cacheService.invalidate(BOOKING_CACHE_PREFIX + userId);

            // Publish event
            Map<String, Object> event = new HashMap<>();
            event.put("bookingId", saved.getId());
            event.put("bookingReference", saved.getBookingReference());
            event.put("flightId", flightId);
            event.put("userId", userId);
            event.put("amount", price.doubleValue());

            eventPublisher.publish(BOOKING_CREATED_CHANNEL, event);

            log.info("Booking created: {}", saved.getBookingReference());
            return saved;
        });
    }

    /**
     * Confirm booking after payment
     */
    @Transactional
    public Booking confirmBooking(String bookingReference, String paymentId) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!"PENDING".equals(booking.getStatus())) {
            throw new RuntimeException("Booking is not in PENDING status");
        }

        booking.setStatus("CONFIRMED");
        booking.setPaymentId(paymentId);
        booking.setConfirmedAt(LocalDateTime.now());

        Booking confirmed = bookingRepository.save(booking);

        // Invalidate cache
        cacheService.invalidate(BOOKING_CACHE_PREFIX + booking.getUserId());

        // Publish event
        Map<String, Object> event = new HashMap<>();
        event.put("bookingReference", bookingReference);
        event.put("flightId", booking.getFlightId());
        event.put("userId", booking.getUserId());

        eventPublisher.publish(BOOKING_CONFIRMED_CHANNEL, event);

        log.info("Booking confirmed: {}", bookingReference);
        return confirmed;
    }

    /**
     * Cancel booking
     */
    @Transactional
    public void cancelBooking(String bookingReference, String reason) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setStatus("CANCELLED");
        booking.setCancelledAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // Invalidate cache
        cacheService.invalidate(BOOKING_CACHE_PREFIX + booking.getUserId());

        // Publish event
        Map<String, Object> event = new HashMap<>();
        event.put("bookingReference", bookingReference);
        event.put("userId", booking.getUserId());
        event.put("reason", reason);

        eventPublisher.publish(BOOKING_CANCELLED_CHANNEL, event);

        log.info("Booking cancelled: {}", bookingReference);
    }

    /**
     * Get user bookings with caching
     */
    public List<Booking> getUserBookings(String userId) {
        String cacheKey = BOOKING_CACHE_PREFIX + userId;

        List<Booking> cached = cacheService.getList(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Booking> bookings = bookingRepository.findByUserId(userId);
        cacheService.cache(cacheKey, bookings, 300); // 5 minutes

        return bookings;
    }

    /**
     * Get booking by reference
     */
    public Booking getBooking(String bookingReference) {
        return bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
    }

    private String generateBookingReference() {
        return "BK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
