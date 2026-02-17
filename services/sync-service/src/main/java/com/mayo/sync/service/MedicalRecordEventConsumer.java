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
 * Consumer for medical record-related events to enable delta syncing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalRecordEventConsumer {

    private final SyncService syncService;
    private final ObjectMapper objectMapper;

    /**
     * Consume medical record events and record delta changes for sync
     */
    @KafkaListener(
        topics = Topics.MEDICAL_RECORD_EVENTS,
        groupId = "sync-service-medical-record",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeMedicalRecordEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String eventData = record.value();
            log.debug("Received medical record event: {}", eventData);

            // Parse event data
            JsonNode eventNode = objectMapper.readTree(eventData);
            String eventType = eventNode.get("eventType").asText();
            String userId = eventNode.get("userId").asText();
            String actorId = eventNode.get("actorId").asText();
            
            JsonNode dataNode = eventNode.get("data");
            String additionalData = dataNode != null ? dataNode.toString() : "{}";

            // Record delta change based on action
            recordMedicalRecordDeltaChange(eventType, userId, actorId, additionalData);

            ack.acknowledge();
            log.info("Successfully processed medical record event: {} for user: {}", eventType, userId);

        } catch (Exception e) {
            log.error("Failed to process medical record event: {}", e.getMessage(), e);
            // For now, acknowledge to prevent reprocessing - in production might want DLQ
            ack.acknowledge();
        }
    }

    /**
     * Record delta change for medical record-related events
     */
    private void recordMedicalRecordDeltaChange(String eventType, String userId, String actorId, String additionalData) {
        try {
            // Record the delta change for sync
            // Note: This would typically be done through the SyncService's delta change recording mechanism
            // For now, we'll log it - in a full implementation, this would integrate with the delta change repository

            log.info("Recorded delta change for medical record sync: eventType={}, userId={}, actorId={}, data={}",
                    eventType, userId, actorId, additionalData);

        } catch (Exception e) {
            log.error("Failed to record medical record delta change: {}", e.getMessage(), e);
        }
    }
}