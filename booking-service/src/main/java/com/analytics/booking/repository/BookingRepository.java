package com.analytics.booking.repository;

import com.analytics.booking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByUserId(String userId);

    List<Booking> findByFlightId(Long flightId);

    List<Booking> findByStatus(String status);

    boolean existsByFlightIdAndSeatNumberAndStatus(Long flightId, String seatNumber, String status);
}
