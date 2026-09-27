package com.example.cinema;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class NotificationSender {
    void send(UUID id, String message) {
        // Local delivery adapter: the worker publishes to the customer's persisted inbox and this
        // log.
        LoggerFactory.getLogger(NotificationSender.class).info("Notification {}: {}", id, message);
    }
}
