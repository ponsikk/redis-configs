package com.analytics.hotel.service;

import com.analytics.hotel.model.Hotel;
import com.analytics.hotel.repository.HotelRepository;
import com.analytics.shared.cache.CacheService;
import com.analytics.shared.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelService {

    private final HotelRepository hotelRepository;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;

    private static final String CITY_CACHE_PREFIX = "hotels:city:";
    private static final String HOTEL_CACHE_PREFIX = "hotels:id:";
    private static final String HOTEL_UPDATED_CHANNEL = "events:hotel:updated";

    @Value("${cache.ttl.hotel-search:900}")
    private long hotelCacheTtl;

    /**
     * Search hotels by city with caching
     */
    @Transactional(readOnly = true)
    public List<Hotel> searchHotelsByCity(String city, Integer minStars) {
        String cacheKey = minStars != null
            ? CITY_CACHE_PREFIX + city + ":" + minStars
            : CITY_CACHE_PREFIX + city;

        List<Hotel> cached = cacheService.getList(cacheKey);
        if (cached != null) {
            log.info("Cache HIT for hotels in city: {}", city);
            return cached;
        }

        log.info("Cache MISS for hotels in city: {}", city);
        List<Hotel> hotels = minStars != null
            ? hotelRepository.findAvailableHotelsByCityAndStars(city, minStars)
            : hotelRepository.findAvailableHotelsByCity(city);

        cacheService.cache(cacheKey, hotels, hotelCacheTtl);
        return hotels;
    }

    /**
     * Get hotel by ID with caching
     */
    @Transactional(readOnly = true)
    public Hotel getHotelById(Long id) {
        String cacheKey = HOTEL_CACHE_PREFIX + id;

        Hotel cached = cacheService.get(cacheKey, Hotel.class);
        if (cached != null) {
            log.info("Cache HIT for hotel ID: {}", id);
            return cached;
        }

        log.info("Cache MISS for hotel ID: {}", id);
        Hotel hotel = hotelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Hotel not found: " + id));

        cacheService.cache(cacheKey, hotel, hotelCacheTtl);
        return hotel;
    }

    /**
     * Create new hotel
     */
    @Transactional
    public Hotel createHotel(Hotel hotel) {
        hotel.setStatus("ACTIVE");
        Hotel saved = hotelRepository.save(hotel);
        log.info("Created hotel: {}", saved.getName());
        return saved;
    }

    /**
     * Update hotel and invalidate cache
     */
    @Transactional
    public Hotel updateHotel(Long id, Hotel hotelUpdate) {
        Hotel existing = hotelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Hotel not found: " + id));

        existing.setName(hotelUpdate.getName());
        existing.setCity(hotelUpdate.getCity());
        existing.setAddress(hotelUpdate.getAddress());
        existing.setStars(hotelUpdate.getStars());
        existing.setPricePerNight(hotelUpdate.getPricePerNight());
        existing.setTotalRooms(hotelUpdate.getTotalRooms());
        existing.setAvailableRooms(hotelUpdate.getAvailableRooms());
        existing.setStatus(hotelUpdate.getStatus());

        Hotel updated = hotelRepository.save(existing);

        // Invalidate cache
        cacheService.invalidate(HOTEL_CACHE_PREFIX + id);
        cacheService.invalidatePattern(CITY_CACHE_PREFIX + updated.getCity() + "*");

        // Publish event
        eventPublisher.publish(HOTEL_UPDATED_CHANNEL, new HotelUpdatedEvent(
            UUID.randomUUID().toString(),
            "HOTEL_UPDATED",
            Instant.now(),
            updated.getId(),
            updated.getName(),
            updated.getCity()
        ));

        log.info("Updated hotel: {} and invalidated cache", updated.getName());
        return updated;
    }

    /**
     * Update room availability (called after booking)
     */
    @Transactional
    public void updateAvailability(Long hotelId, int roomsDelta) {
        Hotel hotel = hotelRepository.findById(hotelId)
            .orElseThrow(() -> new RuntimeException("Hotel not found: " + hotelId));

        int newAvailable = hotel.getAvailableRooms() + roomsDelta;
        if (newAvailable < 0 || newAvailable > hotel.getTotalRooms()) {
            throw new RuntimeException("Invalid room availability");
        }

        hotel.setAvailableRooms(newAvailable);
        hotelRepository.save(hotel);

        // Invalidate cache
        cacheService.invalidate(HOTEL_CACHE_PREFIX + hotelId);
        cacheService.invalidatePattern(CITY_CACHE_PREFIX + hotel.getCity() + "*");

        log.info("Updated availability for hotel {}: {} rooms available", hotelId, newAvailable);
    }

    /**
     * Event DTO for hotel updates
     */
    public record HotelUpdatedEvent(
        String eventId,
        String eventType,
        Instant timestamp,
        Long hotelId,
        String hotelName,
        String city
    ) {}
}
