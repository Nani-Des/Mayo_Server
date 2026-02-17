package com.mayo.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.audit.entity.ComplianceRule;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending alerts and notifications for compliance violations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // System admin user ID for system alerts (should be configured)
    private static final UUID SYSTEM_ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * Send an alert with specified severity and details
     */
    public void sendAlert(String severity, String title, String message, String eventId) {
        log.warn("ALERT [{}] {}: {} (Event: {})", severity, title, message, eventId);

        // Implementation would integrate with:
        // - Email notifications
        // - SMS alerts
        // - Dashboard alerts
        // - External monitoring systems (PagerDuty, etc.)

        switch (severity.toUpperCase()) {
            case "CRITICAL":
                sendCriticalAlert(title, message, eventId);
                break;
            case "HIGH":
                sendHighPriorityAlert(title, message, eventId);
                break;
            case "MEDIUM":
                sendMediumPriorityAlert(title, message, eventId);
                break;
            case "LOW":
            default:
                sendLowPriorityAlert(title, message, eventId);
                break;
        }
    }

    /**
     * Send compliance violation alert with automated response
     */
    public void sendComplianceViolationAlert(ComplianceRule rule, UUID eventId, String violationDetails) {
        String title = String.format("Compliance Violation: %s (%s)",
                                   rule.getName(), rule.getComplianceFramework());
        String message = String.format("Violation detected for rule '%s': %s. Framework: %s, Severity: %s",
                                     rule.getName(), violationDetails,
                                     rule.getComplianceFramework(), rule.getSeverity());

        sendAlert(rule.getSeverity().name(), title, message, eventId.toString());

        // Trigger automated responses based on rule severity and framework
        executeAutomatedResponse(rule, eventId, violationDetails);
    }

    /**
     * Execute automated responses for compliance violations
     */
    private void executeAutomatedResponse(ComplianceRule rule, UUID eventId, String violationDetails) {
        switch (rule.getSeverity()) {
            case CRITICAL:
                // Immediate actions for critical violations
                quarantineData(eventId);
                notifyComplianceOfficer(rule.getComplianceFramework());
                escalateToSecurityTeam(rule, eventId);
                break;
            case HIGH:
                // High priority automated responses
                logSecurityIncident(rule, eventId, violationDetails);
                notifySupervisors(rule.getComplianceFramework());
                break;
            case MEDIUM:
                // Medium priority responses
                createComplianceTicket(rule, eventId, violationDetails);
                break;
            case LOW:
                // Low priority - just logging
                log.info("Low priority compliance violation logged: {}", rule.getName());
                break;
        }
    }

    private void sendCriticalAlert(String title, String message, String eventId) {
        // Immediate notification to security team
        log.error("CRITICAL ALERT: {} - {}", title, message);

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("eventId", eventId);
            metadata.put("severity", "CRITICAL");
            metadata.put("timestamp", Instant.now().toString());

            Map<String, String> notificationEvent = new HashMap<>();
            notificationEvent.put("userId", SYSTEM_ADMIN_USER_ID.toString());
            notificationEvent.put("type", "SECURITY_ALERT");
            notificationEvent.put("title", title);
            notificationEvent.put("message", message);
            notificationEvent.put("priority", "CRITICAL");
            notificationEvent.put("channels", "[\"PUSH\", \"EMAIL\", \"SMS\"]");
            notificationEvent.put("metadata", objectMapper.writeValueAsString(metadata));

            String eventJson = objectMapper.writeValueAsString(notificationEvent);
            kafkaTemplate.send(Topics.NOTIFICATION_EVENTS, SYSTEM_ADMIN_USER_ID.toString(), eventJson);

            log.info("Sent critical alert notification event for event: {}", eventId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize critical alert notification event: {}", e.getMessage(), e);
        }
    }

    private void sendHighPriorityAlert(String title, String message, String eventId) {
        // High priority notification
        log.warn("HIGH PRIORITY ALERT: {} - {}", title, message);

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("eventId", eventId);
            metadata.put("severity", "HIGH");
            metadata.put("timestamp", Instant.now().toString());

            Map<String, String> notificationEvent = new HashMap<>();
            notificationEvent.put("userId", SYSTEM_ADMIN_USER_ID.toString());
            notificationEvent.put("type", "SECURITY_ALERT");
            notificationEvent.put("title", title);
            notificationEvent.put("message", message);
            notificationEvent.put("priority", "HIGH");
            notificationEvent.put("channels", "[\"PUSH\", \"EMAIL\"]");
            notificationEvent.put("metadata", objectMapper.writeValueAsString(metadata));

            String eventJson = objectMapper.writeValueAsString(notificationEvent);
            kafkaTemplate.send(Topics.NOTIFICATION_EVENTS, SYSTEM_ADMIN_USER_ID.toString(), eventJson);

            log.info("Sent high priority alert notification event for event: {}", eventId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize high priority alert notification event: {}", e.getMessage(), e);
        }
    }

    private void sendMediumPriorityAlert(String title, String message, String eventId) {
        // Medium priority notification
        log.info("MEDIUM PRIORITY ALERT: {} - {}", title, message);

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("eventId", eventId);
            metadata.put("severity", "MEDIUM");
            metadata.put("timestamp", Instant.now().toString());

            Map<String, String> notificationEvent = new HashMap<>();
            notificationEvent.put("userId", SYSTEM_ADMIN_USER_ID.toString());
            notificationEvent.put("type", "SECURITY_ALERT");
            notificationEvent.put("title", title);
            notificationEvent.put("message", message);
            notificationEvent.put("priority", "MEDIUM");
            notificationEvent.put("channels", "[\"EMAIL\"]");
            notificationEvent.put("metadata", objectMapper.writeValueAsString(metadata));

            String eventJson = objectMapper.writeValueAsString(notificationEvent);
            kafkaTemplate.send(Topics.NOTIFICATION_EVENTS, SYSTEM_ADMIN_USER_ID.toString(), eventJson);

            log.info("Sent medium priority alert notification event for event: {}", eventId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize medium priority alert notification event: {}", e.getMessage(), e);
        }
    }

    private void sendLowPriorityAlert(String title, String message, String eventId) {
        // Low priority notification
        log.info("LOW PRIORITY ALERT: {} - {}", title, message);

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("eventId", eventId);
            metadata.put("severity", "LOW");
            metadata.put("timestamp", Instant.now().toString());

            Map<String, String> notificationEvent = new HashMap<>();
            notificationEvent.put("userId", SYSTEM_ADMIN_USER_ID.toString());
            notificationEvent.put("type", "SECURITY_ALERT");
            notificationEvent.put("title", title);
            notificationEvent.put("message", message);
            notificationEvent.put("priority", "LOW");
            notificationEvent.put("channels", "[\"EMAIL\"]");
            notificationEvent.put("metadata", objectMapper.writeValueAsString(metadata));

            String eventJson = objectMapper.writeValueAsString(notificationEvent);
            kafkaTemplate.send(Topics.NOTIFICATION_EVENTS, SYSTEM_ADMIN_USER_ID.toString(), eventJson);

            log.info("Sent low priority alert notification event for event: {}", eventId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize low priority alert notification event: {}", e.getMessage(), e);
        }
    }

    private void quarantineData(UUID eventId) {
        log.error("CRITICAL: Initiating data quarantine for event {}", eventId);
        // Implementation would integrate with data management service
        // to quarantine affected data and prevent further access
    }

    private void notifyComplianceOfficer(ComplianceRule.ComplianceFramework framework) {
        log.error("Notifying compliance officer for {} framework violation", framework);
        // Implementation would send immediate notification to compliance officers
    }

    private void escalateToSecurityTeam(ComplianceRule rule, UUID eventId) {
        log.error("SECURITY ESCALATION: {} violation in event {}", rule.getName(), eventId);
        // Implementation would escalate to security incident response team
    }

    private void logSecurityIncident(ComplianceRule rule, UUID eventId, String details) {
        log.warn("Security incident logged: Rule={}, Event={}, Details={}", rule.getName(), eventId, details);
        // Implementation would create security incident record
    }

    private void notifySupervisors(ComplianceRule.ComplianceFramework framework) {
        log.warn("Notifying supervisors about {} compliance violation", framework);
        // Implementation would notify relevant supervisors
    }

    private void createComplianceTicket(ComplianceRule rule, UUID eventId, String details) {
        log.info("Creating compliance ticket for rule {} and event {}", rule.getName(), eventId);
        // Implementation would create ticket in compliance tracking system
    }
}