package com.analytics.aggregation.controller;

import com.analytics.aggregation.service.AnalyticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Analytics Controller - REST API for metrics
 */
@RestController
@RequestMapping("/api/analytics")
@Slf4j
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> stats = analyticsService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/bookings/total")
    public ResponseEntity<Long> getTotalBookings() {
        return ResponseEntity.ok(analyticsService.getTotalBookings());
    }

    @GetMapping("/routes/top")
    public ResponseEntity<?> getTopRoutes(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTopRoutes(limit));
    }

    @GetMapping("/flights/top")
    public ResponseEntity<?> getTopFlights(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTopFlights(limit));
    }

    @GetMapping("/revenue/daily")
    public ResponseEntity<Double> getDailyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(analyticsService.getDailyRevenue(date));
    }

    @GetMapping("/users/unique")
    public ResponseEntity<Long> getUniqueUsers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(analyticsService.getUniqueUsers(date));
    }
}
