package com.mayo.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.audit.entity.AuditEvent;
import com.mayo.audit.entity.ElasticsearchAuditEvent;
import com.mayo.audit.repository.AuditEventRepository;
import com.mayo.audit.repository.elasticsearch.ElasticsearchAuditEventRepository;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka consumer for user events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final AuditEventRepository auditEventRepository;
    private final ElasticsearchAuditEventRepository elasticsearchAuditEventRepository;
    private final ComplianceRuleEngine complianceRuleEngine;
    private final AuditIntegrityService integrityService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = Topics.USER_EVENTS, groupId = "audit-service", concurrency = "#{${audit.kafka.consumer.concurrency:3}}", containerFactory = "auditKafkaListenerContainerFactory")
    @Transactional
    public void consumeUserEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) throws Exception {

        try {
            log.debug("Received user event: key={}, offset={}", key, offset);

            // Parse the user event
            AuditEvent auditEvent = parseEvent(message, key);

            // Validate and enrich the event
            enrichAuditEvent(auditEvent);

            // Generate integrity hash
            String hashValue = integrityService.generateEventHash(auditEvent);
            auditEvent.setHashValue(hashValue);

            // Set previous event hash for chain integrity
            String previousHash = integrityService.getLatestEventHash();
            auditEvent.setPreviousEventHash(previousHash);

            // Handle versioning metadata
            handleVersioningMetadata(auditEvent);

            // Save the audit event
            AuditEvent savedEvent = auditEventRepository.save(auditEvent);

            // Index in Elasticsearch
            try {
                ElasticsearchAuditEvent esEvent = new ElasticsearchAuditEvent(savedEvent);
                elasticsearchAuditEventRepository.save(esEvent);
                log.debug("Indexed user event in Elasticsearch: {}", savedEvent.getEventId());
            } catch (Exception e) {
                log.error("Failed to index user event in Elasticsearch: {}", savedEvent.getEventId(), e);
                // Don't fail the whole process for indexing issues
            }

            // Evaluate compliance rules
            complianceRuleEngine.evaluateEvent(savedEvent);

            // Acknowledge successful processing
            acknowledgment.acknowledge();

            log.info("Successfully processed user event: {}", savedEvent.getEventId());

        } catch (Exception e) {
            log.error("Failed to process user event: key={}, error={}", key, e.getMessage(), e);
            // In a production system, you might want to send to a dead letter queue
            // or implement retry logic here
            throw e; // Re-throw to trigger retry or dead letter handling
        }
    }

    private AuditEvent parseEvent(String message, String key) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(message);

        AuditEvent event = new AuditEvent();
        event.setEventId(key != null ? key : UUID.randomUUID().toString());
        event.setTimestamp(LocalDateTime.now()); // Use current time if not provided

        // Parse common fields
        if (jsonNode.has("userId")) {
            event.setUserId(UUID.fromString(jsonNode.get("userId").asText()));
        }
        if (jsonNode.has("deviceId")) {
            event.setDeviceId(UUID.fromString(jsonNode.get("deviceId").asText()));
        }
        if (jsonNode.has("hospitalId")) {
            event.setHospitalId(UUID.fromString(jsonNode.get("hospitalId").asText()));
        }
        if (jsonNode.has("sessionId")) {
            event.setSessionId(jsonNode.get("sessionId").asText());
        }
        if (jsonNode.has("patientId")) {
            event.setPatientId(UUID.fromString(jsonNode.get("patientId").asText()));
        }
        if (jsonNode.has("resourceType")) {
            event.setResourceType(jsonNode.get("resourceType").asText());
        }
        if (jsonNode.has("resourceId")) {
            event.setResourceId(jsonNode.get("resourceId").asText());
        }

        // Parse action - try to map to enum
        if (jsonNode.has("action")) {
            String actionStr = jsonNode.get("action").asText();
            try {
                event.setAction(AuditEvent.AuditAction.valueOf(actionStr));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown audit action: {}, defaulting to RECORD_ACCESSED", actionStr);
                event.setAction(AuditEvent.AuditAction.RECORD_ACCESSED);
            }
        } else {
            // If action is missing, set a default
            log.warn("Action field missing in user event, defaulting to RECORD_ACCESSED");
            event.setAction(AuditEvent.AuditAction.RECORD_ACCESSED);
        }
        log.debug("Parsed action: {}", event.getAction());

        // Parse metadata
        if (jsonNode.has("metadata")) {
            event.setMetadata(jsonNode.get("metadata").toString());
        }

        // Parse location data
        if (jsonNode.has("location")) {
            event.setLocation(jsonNode.get("location").toString());
        }

        // Parse IP address
        if (jsonNode.has("ipAddress")) {
            try {
                event.setIpAddress(InetAddress.getByName(jsonNode.get("ipAddress").asText()));
            } catch (UnknownHostException e) {
                log.warn("Invalid IP address in user event: {}", jsonNode.get("ipAddress").asText());
            }
        }

        // Parse user agent
        if (jsonNode.has("userAgent")) {
            event.setUserAgent(jsonNode.get("userAgent").asText());
        }

        // Parse versioning metadata fields
        if (jsonNode.has("originatingDeviceId")) {
            event.setOriginatingDeviceId(UUID.fromString(jsonNode.get("originatingDeviceId").asText()));
        }
        if (jsonNode.has("doctorUserId")) {
            event.setDoctorUserId(UUID.fromString(jsonNode.get("doctorUserId").asText()));
        }
        if (jsonNode.has("versionNumber")) {
            event.setVersionNumber(jsonNode.get("versionNumber").asLong());
        }
        if (jsonNode.has("parentVersionHash")) {
            event.setParentVersionHash(jsonNode.get("parentVersionHash").asText());
        }
        if (jsonNode.has("versioningDigitalSignature")) {
            event.setVersioningDigitalSignature(jsonNode.get("versioningDigitalSignature").asText());
        }
        if (jsonNode.has("conflictResolutionMetadata")) {
            event.setConflictResolutionMetadata(jsonNode.get("conflictResolutionMetadata").toString());
        }

        return event;
    }

    private void enrichAuditEvent(AuditEvent event) {
        // Set default severity based on action
        if (event.getSeverity() == null) {
            event.setSeverity(determineSeverity(event.getAction()));
        }

        // Add compliance flags based on action and context
        String complianceFlags = determineComplianceFlags(event);
        event.setComplianceFlags(complianceFlags);

        // Enrich versioning metadata
        enrichVersioningMetadata(event);
    }

    private void enrichVersioningMetadata(AuditEvent event) {
        // If originating device is not set, use the deviceId
        if (event.getOriginatingDeviceId() == null) {
            event.setOriginatingDeviceId(event.getDeviceId());
        }

        // If doctor user is not set and user is a doctor, set it
        // This would require additional logic to determine user role
        // For now, if not set, leave as null or set to userId if applicable

        // If version number is not set, increment from the latest version
        if (event.getVersionNumber() == null) {
            Long maxVersion = auditEventRepository.findMaxVersionNumberForResource(
                    event.getResourceType(), event.getResourceId());
            event.setVersionNumber(maxVersion != null ? maxVersion + 1 : 1L);
        }

        // If parent version hash is not set, get the latest version hash for this resource
        if (event.getParentVersionHash() == null) {
            String latestVersionHash = integrityService.getLatestVersionHashForResource(
                    event.getResourceType(), event.getResourceId());
            event.setParentVersionHash(latestVersionHash);
        }

        // Generate versioning digital signature if not provided
        if (event.getVersioningDigitalSignature() == null) {
            String versionSignature = integrityService.generateVersioningSignature(event);
            event.setVersioningDigitalSignature(versionSignature);
        }
    }

    private void handleVersioningMetadata(AuditEvent event) {
        // Additional versioning logic can be added here
        // For example, conflict resolution handling
        if (event.getConflictResolutionMetadata() != null) {
            // Process conflict resolution metadata
            log.info("Processing conflict resolution for event: {}", event.getEventId());
        }
    }

    private AuditEvent.Severity determineSeverity(AuditEvent.AuditAction action) {
        // Null safety - should never happen after fix above, but defensive programming
        if (action == null) {
            log.error("Action is null in determineSeverity, returning INFO severity");
            return AuditEvent.Severity.INFO;
        }
        
        switch (action) {
            // Authentication events
            case LOGIN, LOGOUT, REGISTER, PASSWORD_RESET:
                return AuditEvent.Severity.INFO;
            case ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, PASSWORD_CHANGED:
                return AuditEvent.Severity.WARN;

            // Authorization events
            case PERMISSION_GRANTED, PERMISSION_REVOKED, ROLE_ASSIGNED, ROLE_REMOVED:
                return AuditEvent.Severity.WARN;
            case ACCESS_DENIED, UNAUTHORIZED_ACCESS_ATTEMPT:
                return AuditEvent.Severity.ERROR;

            // Security events
            case WAF_BLOCKED_REQUEST, RATE_LIMIT_EXCEEDED, BRUTE_FORCE_DETECTED:
                return AuditEvent.Severity.WARN;
            case SUSPICIOUS_ACTIVITY_DETECTED, MALWARE_DETECTED, TLS_HANDSHAKE_FAILED:
                return AuditEvent.Severity.ERROR;
            case INVALID_CERTIFICATE, SSL_PROTOCOL_VIOLATION:
                return AuditEvent.Severity.CRITICAL;

            // Data access events
            case RECORD_ACCESSED, PHI_ACCESSED:
                return AuditEvent.Severity.INFO;
            case RECORD_MODIFIED, RECORD_CREATED, RECORD_DELETED, SENSITIVE_DATA_ACCESSED:
                return AuditEvent.Severity.WARN;

            // Administrative events
            case USER_CREATED, USER_DELETED, USER_MODIFIED, STAFF_ADDED, STAFF_REMOVED:
                return AuditEvent.Severity.WARN;
            case CONFIGURATION_CHANGED, SECURITY_POLICY_UPDATED:
                return AuditEvent.Severity.ERROR;

            // Compliance events
            case COMPLIANCE_CHECK_PASSED:
                return AuditEvent.Severity.INFO;
            case COMPLIANCE_CHECK_FAILED:
                return AuditEvent.Severity.CRITICAL;

            default:
                return AuditEvent.Severity.INFO;
        }
    }

    private String determineComplianceFlags(AuditEvent event) {
        // This would contain logic to determine which compliance frameworks apply
        // For example: HIPAA, GDPR, etc.
        // Implementation would depend on the specific compliance requirements
        try {
            return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("hipaa", true)
                    .put("gdpr", event.getPatientId() != null)
                    .put("audit_required", true));
        } catch (Exception e) {
            log.warn("Failed to serialize compliance flags", e);
            return "{}";
        }
    }
}