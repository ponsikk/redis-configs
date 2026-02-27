package com.analytics.flight.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancellationEvent implements Serializable {
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String bookingReference;
    private String userId;
    private String reason;
}
