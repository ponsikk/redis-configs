package com.analytics.aggregation.listener;

import com.analytics.aggregation.service.AnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Event subscriber listening to Redis Pub/Sub
 *
 * Listens to events from flight-service and booking-service
 * and updates analytics in real-time
 */
@Component
@Slf4j
public class EventSubscriber implements MessageListener {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            log.debug("Received event from channel: {}", channel);

            Map<String, Object> event = objectMapper.readValue(body, Map.class);

            // Handle different event types
            switch (channel) {
                case "events:booking:created":
                    handleBookingCreated(event);
                    break;

                case "events:booking:confirmed":
                    handleBookingConfirmed(event);
                    break;

                case "events:flight:updated":
                    handleFlightUpdated(event);
                    break;

                default:
                    log.debug("Unhandled event channel: {}", channel);
            }

        } catch (Exception e) {
            log.error("Failed to process event", e);
        }
    }

    private void handleBookingCreated(Map<String, Object> event) {
        Long flightId = ((Number) event.get("flightId")).longValue();
        String userId = (String) event.get("userId");
        Double amount = ((Number) event.get("amount")).doubleValue();

        // Extract route from flightId (in real app, would query flight-service)
        String routeId = "NYC-LAX"; // Placeholder

        analyticsService.recordBooking(flightId, routeId, userId, amount);
        log.info("Processed booking created event: flightId={}", flightId);
    }

    private void handleBookingConfirmed(Map<String, Object> event) {
        String bookingReference = (String) event.get("bookingReference");
        log.info("Booking confirmed: {}", bookingReference);
        // Additional analytics if needed
    }

    private void handleFlightUpdated(Map<String, Object> event) {
        Long flightId = ((Number) event.get("flightId")).longValue();
        String updateType = (String) event.get("updateType");
        log.info("Flight updated: flightId={}, type={}", flightId, updateType);
    }
}
