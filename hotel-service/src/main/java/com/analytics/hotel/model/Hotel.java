package com.analytics.hotel.model;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "hotels")
@Data
public class Hotel implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private String address;
    private Integer stars;
    private BigDecimal pricePerNight;
    private Integer totalRooms;
    private Integer availableRooms;
    private String status;
}
