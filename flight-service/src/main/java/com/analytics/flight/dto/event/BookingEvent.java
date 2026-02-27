package com.analytics.flight.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent implements Serializable {
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private Long bookingId;
    private String bookingReference;
    private Long flightId;
    private String userId;
    private Double amount;
}
