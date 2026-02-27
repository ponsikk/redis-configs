package com.analytics.flight.controller;

import com.analytics.flight.model.Flight;
import com.analytics.flight.service.FlightService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flight Controller - thin REST API layer
 *
 * Delegates all business logic to FlightService
 */
@RestController
@RequestMapping("/api/flights")
@Slf4j
public class FlightController {

    @Autowired
    private FlightService flightService;

    @GetMapping("/search")
    public ResponseEntity<List<Flight>> searchFlights(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        List<Flight> flights = flightService.searchFlights(origin, destination, startDate, endDate);
        return ResponseEntity.ok(flights);
    }

    @GetMapping("/{flightId}")
    public ResponseEntity<Flight> getFlight(@PathVariable Long flightId) {
        Flight flight = flightService.getFlight(flightId);
        return ResponseEntity.ok(flight);
    }

    @GetMapping
    public ResponseEntity<List<Flight>> getAllFlights() {
        List<Flight> flights = flightService.getAllFlights();
        return ResponseEntity.ok(flights);
    }

    @PostMapping
    public ResponseEntity<Flight> createFlight(@RequestBody Flight flight) {
        Flight created = flightService.createFlight(flight);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{flightId}")
    public ResponseEntity<Flight> updateFlight(
            @PathVariable Long flightId,
            @RequestBody Flight updates) {

        Flight updated = flightService.updateFlight(flightId, updates);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{flightId}")
    public ResponseEntity<Void> deleteFlight(@PathVariable Long flightId) {
        flightService.deleteFlight(flightId);
        return ResponseEntity.noContent().build();
    }
}
