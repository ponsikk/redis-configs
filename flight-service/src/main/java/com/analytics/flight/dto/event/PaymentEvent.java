package com.analytics.flight.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent implements Serializable {
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String orderId;
    private String transactionId;
    private Double amount;
    private String status;
}
