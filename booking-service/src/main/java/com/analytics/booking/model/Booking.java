package com.analytics.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingReference;

    @Column(nullable = false)
    private Long flightId;  // Reference to Flight in flight-service

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String passengerName;

    @Column(nullable = false)
    private String passengerEmail;

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String status; // PENDING, CONFIRMED, CANCELLED

    private String paymentId;

    private LocalDateTime bookingDate;

    private LocalDateTime confirmedAt;

    private LocalDateTime cancelledAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        bookingDate = LocalDateTime.now();
    }
}
