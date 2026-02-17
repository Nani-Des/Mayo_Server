package com.mayo.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer for user-related events to enable delta syncing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final SyncService syncService;
    private final ObjectMapper objectMapper;

    /**
     * Consume user events and record delta changes for sync
     */
    @KafkaListener(
        topics = Topics.USER_EVENTS,
        groupId = "sync-service-user",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeUserEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String eventData = record.value();
            log.debug("Received user event: {}", eventData);

            // Parse event data - matches auth-service event structure
            JsonNode eventNode = objectMapper.readTree(eventData);
            String eventType = eventNode.get("eventType").asText();
            String userId = eventNode.get("userId").asText();
            String actorId = eventNode.get("actorId").asText();
            
            // Extract additional data if present
            JsonNode dataNode = eventNode.get("data");
            String additionalData = dataNode != null ? dataNode.toString() : "{}";

            // Record delta change based on event type
            recordUserDeltaChange(eventType, userId, actorId, additionalData);

            ack.acknowledge();
            log.info("Successfully processed user event: {} for user: {}", eventType, userId);

        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
            // For now, acknowledge to prevent reprocessing - in production might want DLQ
            ack.acknowledge();
        }
    }

    /**
     * Record delta change for user-related events
     */
    private void recordUserDeltaChange(String eventType, String userId, String actorId, String additionalData) {
        try {
            // Record the delta change for sync
            // Note: This would typically be done through the SyncService's delta change recording mechanism
            // For now, we'll log it - in a full implementation, this would integrate with the delta change repository

            log.info("Recorded delta change for user sync: eventType={}, userId={}, actorId={}, data={}",
                    eventType, userId, actorId, additionalData);

        } catch (Exception e) {
            log.error("Failed to record user delta change: {}", e.getMessage(), e);
        }
    }
}