package com.analytics.hotel.controller;

import com.analytics.hotel.model.Hotel;
import com.analytics.hotel.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    /**
     * Search hotels by city
     * GET /api/hotels/search?city=Paris&minStars=4
     */
    @GetMapping("/search")
    public ResponseEntity<List<Hotel>> searchHotels(
            @RequestParam String city,
            @RequestParam(required = false) Integer minStars) {
        return ResponseEntity.ok(hotelService.searchHotelsByCity(city, minStars));
    }

    /**
     * Get hotel by ID
     * GET /api/hotels/1
     */
    @GetMapping("/{id}")
    public ResponseEntity<Hotel> getHotelById(@PathVariable Long id) {
        return ResponseEntity.ok(hotelService.getHotelById(id));
    }

    /**
     * Create new hotel
     * POST /api/hotels
     */
    @PostMapping
    public ResponseEntity<Hotel> createHotel(@RequestBody Hotel hotel) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(hotelService.createHotel(hotel));
    }

    /**
     * Update hotel
     * PUT /api/hotels/1
     */
    @PutMapping("/{id}")
    public ResponseEntity<Hotel> updateHotel(
            @PathVariable Long id,
            @RequestBody Hotel hotel) {
        return ResponseEntity.ok(hotelService.updateHotel(id, hotel));
    }

    /**
     * Update room availability (internal endpoint)
     * PATCH /api/hotels/1/availability?delta=-1
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<Void> updateAvailability(
            @PathVariable Long id,
            @RequestParam int delta) {
        hotelService.updateAvailability(id, delta);
        return ResponseEntity.noContent().build();
    }
}
