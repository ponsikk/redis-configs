package com.analytics.hotel.repository;

import com.analytics.hotel.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    @Query("SELECT h FROM Hotel h WHERE h.city = :city AND h.status = 'ACTIVE' AND h.availableRooms > 0")
    List<Hotel> findAvailableHotelsByCity(@Param("city") String city);

    @Query("SELECT h FROM Hotel h WHERE h.city = :city AND h.stars >= :minStars AND h.status = 'ACTIVE' AND h.availableRooms > 0")
    List<Hotel> findAvailableHotelsByCityAndStars(@Param("city") String city, @Param("minStars") Integer minStars);

    List<Hotel> findByStatus(String status);
}
