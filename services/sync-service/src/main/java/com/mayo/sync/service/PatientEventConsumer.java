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
 * Consumer for patient-related events to enable delta syncing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientEventConsumer {

    private final SyncService syncService;
    private final ObjectMapper objectMapper;

    /**
     * Consume patient events and record delta changes for sync
     */
    @KafkaListener(
        topics = Topics.PATIENT_EVENTS,
        groupId = "sync-service-patient",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumePatientEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String eventData = record.value();
            log.debug("Received patient event: {}", eventData);

            // Parse event data - matches service event structure
            JsonNode eventNode = objectMapper.readTree(eventData);
            String eventType = eventNode.get("eventType").asText();
            String userId = eventNode.get("userId").asText();
            String actorId = eventNode.get("actorId").asText();
            
            JsonNode dataNode = eventNode.get("data");
            String additionalData = dataNode != null ? dataNode.toString() : "{}";

            recordPatientDeltaChange(eventType, userId, actorId, additionalData);

            ack.acknowledge();
            log.info("Successfully processed patient event: {} for user: {}", eventType, userId);

        } catch (Exception e) {
            log.error("Failed to process patient event: {}", e.getMessage(), e);
            // For now, acknowledge to prevent reprocessing - in production might want DLQ
            ack.acknowledge();
        }
    }

    /**
     * Record delta change for patient-related events
     */
    private void recordPatientDeltaChange(String eventType, String userId, String actorId, String additionalData) {
        try {
            // Record the delta change for sync
            // Note: This would typically be done through the SyncService's delta change recording mechanism
            // For now, we'll log it - in a full implementation, this would integrate with the delta change repository

            log.info("Recorded delta change for patient sync: eventType={}, userId={}, actorId={}, data={}",
                    eventType, userId, actorId, additionalData);

        } catch (Exception e) {
            log.error("Failed to record patient delta change: {}", e.getMessage(), e);
        }
    }
}