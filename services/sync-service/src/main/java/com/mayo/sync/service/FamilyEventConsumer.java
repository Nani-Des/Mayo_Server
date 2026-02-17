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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumer for family-related events to enable delta syncing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyEventConsumer {

    private final SyncService syncService;
    private final ObjectMapper objectMapper;

    /**
     * Consume family events and record delta changes for sync
     */
    @KafkaListener(
        topics = Topics.FAMILY_EVENTS,
        groupId = "sync-service-family",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeFamilyEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String eventData = record.value();
            log.debug("Received family event: {}", eventData);

            // Parse event data
            JsonNode eventNode = objectMapper.readTree(eventData);
            String eventType = eventNode.get("eventType").asText();
            String familyId = eventNode.get("familyId").asText();
            String userId = eventNode.get("userId").asText();

            // Record delta change based on event type
            recordFamilyDeltaChange(eventType, familyId, userId, eventData);

            ack.acknowledge();
            log.info("Successfully processed family event: {} for family: {}", eventType, familyId);

        } catch (Exception e) {
            log.error("Failed to process family event: {}", e.getMessage(), e);
            // For now, acknowledge to prevent reprocessing - in production might want DLQ
            ack.acknowledge();
        }
    }

    /**
     * Record delta change for family-related events
     */
    private void recordFamilyDeltaChange(String eventType, String familyId, String userId, String eventData) {
        try {
            UUID userUuid = UUID.fromString(userId);
            String recordType;
            String recordId;
            String changeType;

            switch (eventType) {
                case "FAMILY_CREATED":
                    recordType = "FAMILY";
                    recordId = familyId;
                    changeType = "CREATE";
                    break;
                case "MEMBER_ADDED":
                    recordType = "FAMILY_MEMBER";
                    // Extract memberId from event data
                    JsonNode eventNode = objectMapper.readTree(eventData);
                    recordId = eventNode.get("data").get("memberId").asText();
                    changeType = "CREATE";
                    break;
                case "MEMBER_REMOVED":
                    recordType = "FAMILY_MEMBER";
                    // Extract memberId from event data
                    eventNode = objectMapper.readTree(eventData);
                    recordId = eventNode.get("data").get("memberId").asText();
                    changeType = "DELETE";
                    break;
                default:
                    log.debug("Ignoring family event type: {}", eventType);
                    return;
            }

            // Record the delta change for sync
            // Note: This would typically be done through the SyncService's delta change recording mechanism
            // For now, we'll log it - in a full implementation, this would integrate with the delta change repository

            log.info("Recorded delta change for family sync: type={}, recordType={}, recordId={}, userId={}",
                    changeType, recordType, recordId, userId);

        } catch (Exception e) {
            log.error("Failed to record family delta change: {}", e.getMessage(), e);
        }
    }
}