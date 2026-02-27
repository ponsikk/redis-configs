package com.analytics.flight.repository;

import com.analytics.flight.model.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    List<Flight> findByOriginAndDestination(String origin, String destination);

    @Query("SELECT f FROM Flight f WHERE f.origin = :origin AND f.destination = :destination " +
           "AND f.departureTime BETWEEN :startDate AND :endDate AND f.status = 'SCHEDULED'")
    List<Flight> findAvailableFlights(String origin, String destination,
                                       LocalDateTime startDate, LocalDateTime endDate);

    List<Flight> findByStatus(String status);

    @Query("SELECT DISTINCT f.origin FROM Flight f")
    List<String> findAllOrigins();

    @Query("SELECT DISTINCT f.destination FROM Flight f")
    List<String> findAllDestinations();
}
