package com.mayo.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.events.topics.Topics;
import com.mayo.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalRecordEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationProcessor notificationProcessor;

    @KafkaListener(
        topics = Topics.MEDICAL_RECORD_EVENTS,
        groupId = "notification-service",
        concurrency = "#{${notification.kafka.consumer.concurrency:3}}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMedicalRecordEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received medical record event: key={}, topic={}, partition={}, offset={}",
                     key, topic, partition, offset);

            // Parse the medical record event JSON
            JsonNode jsonNode = objectMapper.readTree(message);

            // Extract fields
            UUID userId = null;
            if (jsonNode.has("userId") && !jsonNode.get("userId").isNull()) {
                userId = UUID.fromString(jsonNode.get("userId").asText());
            }

            String action = jsonNode.has("action") ? jsonNode.get("action").asText() : null;
            String details = jsonNode.has("details") ? jsonNode.get("details").asText() : null;
            String resourceType = jsonNode.has("resourceType") ? jsonNode.get("resourceType").asText() : null;
            String resourceId = jsonNode.has("resourceId") ? jsonNode.get("resourceId").asText() : null;

            // Create NotificationEvent
            NotificationEvent notificationEvent = NotificationEvent.builder()
                .userId(userId)
                .type(action)
                .message(details)
                .title(generateTitle(action, resourceType))
                .priority("NORMAL")
                .channels(new String[]{"PUSH", "EMAIL"})
                .build();

            // Process the notification event
            notificationProcessor.processNotificationEvent(notificationEvent);

            acknowledgment.acknowledge();
            log.info("Successfully processed medical record event for user: {}", userId);

        } catch (Exception e) {
            log.error("Failed to process medical record event: {}", message, e);
            acknowledgment.acknowledge(); // Acknowledge to avoid infinite retries
        }
    }

    private String generateTitle(String action, String resourceType) {
        if (action != null && resourceType != null) {
            return action + " on " + resourceType;
        } else if (action != null) {
            return action;
        } else {
            return "Medical Record Event";
        }
    }
}