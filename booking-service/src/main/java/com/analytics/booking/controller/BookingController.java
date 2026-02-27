package com.analytics.booking.controller;

import com.analytics.booking.model.Booking;
import com.analytics.booking.service.BookingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Booking Controller - thin REST API layer
 */
@RestController
@RequestMapping("/api/bookings")
@Slf4j
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestBody CreateBookingRequest request,
            @RequestHeader("X-User-Id") String userId) {

        Booking booking = bookingService.createBooking(
                request.getFlightId(),
                userId,
                request.getPassengerName(),
                request.getPassengerEmail(),
                request.getSeatNumber(),
                request.getPrice()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @PostMapping("/{bookingReference}/confirm")
    public ResponseEntity<Booking> confirmBooking(
            @PathVariable String bookingReference,
            @RequestBody ConfirmBookingRequest request) {

        Booking confirmed = bookingService.confirmBooking(bookingReference, request.getPaymentId());
        return ResponseEntity.ok(confirmed);
    }

    @PostMapping("/{bookingReference}/cancel")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable String bookingReference,
            @RequestBody CancelBookingRequest request) {

        bookingService.cancelBooking(bookingReference, request.getReason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Booking>> getUserBookings(@PathVariable String userId) {
        List<Booking> bookings = bookingService.getUserBookings(userId);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{bookingReference}")
    public ResponseEntity<Booking> getBooking(@PathVariable String bookingReference) {
        Booking booking = bookingService.getBooking(bookingReference);
        return ResponseEntity.ok(booking);
    }

    @Data
    public static class CreateBookingRequest {
        private Long flightId;
        private String passengerName;
        private String passengerEmail;
        private String seatNumber;
        private BigDecimal price;
    }

    @Data
    public static class ConfirmBookingRequest {
        private String paymentId;
    }

    @Data
    public static class CancelBookingRequest {
        private String reason;
    }
}
