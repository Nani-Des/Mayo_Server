package com.mayo.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.audit.entity.AuditEvent;
import com.mayo.audit.entity.ComplianceRule;
import com.mayo.audit.repository.AuditEventRepository;
import com.mayo.audit.repository.ComplianceRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Engine for evaluating compliance rules against audit events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceRuleEngine {

    private final ComplianceRuleRepository complianceRuleRepository;
    private final AuditEventRepository auditEventRepository;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    /**
     * Evaluate an audit event against all active compliance rules
     */
    public void evaluateEvent(AuditEvent event) {
        List<ComplianceRule> activeRules = complianceRuleRepository.findActiveRulesOrderedByPriority();

        for (ComplianceRule rule : activeRules) {
            try {
                boolean triggered = evaluateRule(rule, event);
                if (triggered) {
                    executeRuleActions(rule, event);
                    logComplianceViolation(rule, event);
                }
            } catch (Exception e) {
                log.error("Failed to evaluate rule {} against event {}: {}",
                        rule.getId(), event.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Evaluate an audit event against rules for a specific compliance framework
     */
    public void evaluateEventForFramework(AuditEvent event, ComplianceRule.ComplianceFramework framework) {
        List<ComplianceRule> frameworkRules = complianceRuleRepository.findActiveRulesByFramework(framework);

        for (ComplianceRule rule : frameworkRules) {
            try {
                boolean triggered = evaluateRule(rule, event);
                if (triggered) {
                    executeRuleActions(rule, event);
                    logComplianceViolation(rule, event);
                }
            } catch (Exception e) {
                log.error("Failed to evaluate rule {} for framework {} against event {}: {}",
                        rule.getId(), framework, event.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Evaluate a specific compliance rule against an audit event
     */
    private boolean evaluateRule(ComplianceRule rule, AuditEvent event) {
        JsonNode conditions = rule.getConditions();

        switch (rule.getRuleType()) {
            case THRESHOLD:
                return evaluateThresholdRule(conditions, event);
            case PATTERN:
                return evaluatePatternRule(conditions, event);
            case TIME_BASED:
                return evaluateTimeBasedRule(conditions, event);
            case AGGREGATION:
                return evaluateAggregationRule(conditions, event);
            default:
                log.warn("Unknown rule type: {}", rule.getRuleType());
                return false;
        }
    }

    private boolean evaluateThresholdRule(JsonNode conditions, AuditEvent event) {
        // Example: Check if failed login attempts exceed threshold within time window
        if (conditions.has("action") && "LOGIN".equals(conditions.get("action").asText())) {
            if (event.getAction() == AuditEvent.AuditAction.LOGIN) {
                // Check metadata for login success/failure
                JsonNode metadata = parseJsonString(event.getMetadata());
                if (metadata != null && metadata.has("success") && !metadata.get("success").asBoolean()) {
                    // Count failed logins for this user in the time window
                    LocalDateTime windowStart = LocalDateTime.now().minusMinutes(
                            conditions.get("timeWindowMinutes").asInt(60));

                    long failedLogins = auditEventRepository.countActionsSince(
                            AuditEvent.AuditAction.LOGIN, windowStart);

                    int threshold = conditions.get("threshold").asInt(5);
                    return failedLogins >= threshold;
                }
            }
        }
        return false;
    }

    private boolean evaluatePatternRule(JsonNode conditions, AuditEvent event) {
        // Example: Check for suspicious access patterns
        if (conditions.has("suspiciousActions")) {
            for (JsonNode action : conditions.get("suspiciousActions")) {
                if (event.getAction().name().equals(action.asText())) {
                    // Additional pattern checks could be implemented here
                    return true;
                }
            }
        }
        return false;
    }

    private boolean evaluateTimeBasedRule(JsonNode conditions, AuditEvent event) {
        // Example: Check for access outside business hours
        if (conditions.has("businessHoursOnly") && conditions.get("businessHoursOnly").asBoolean()) {
            int hour = event.getTimestamp().getHour();
            // Assuming business hours 8 AM - 6 PM
            return hour < 8 || hour > 18;
        }
        return false;
    }

    private boolean evaluateAggregationRule(JsonNode conditions, AuditEvent event) {
        // Example: Check for unusual data access patterns
        if (conditions.has("maxRecordsPerHour")) {
            LocalDateTime hourStart = event.getTimestamp().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
            long recordsAccessed = auditEventRepository.countActionsSince(
                    AuditEvent.AuditAction.RECORD_ACCESSED, hourStart);

            int maxRecords = conditions.get("maxRecordsPerHour").asInt(100);
            return recordsAccessed > maxRecords;
        }
        return false;
    }

    /**
     * Execute actions defined in the compliance rule
     */
    private void executeRuleActions(ComplianceRule rule, AuditEvent event) {
        JsonNode actions = rule.getActions();

        if (actions.isArray()) {
            for (JsonNode action : actions) {
                executeAction(action, rule, event);
            }
        }
    }

    private void executeAction(JsonNode action, ComplianceRule rule, AuditEvent event) {
        String actionType = action.get("type").asText();

        switch (actionType) {
            case "ALERT":
                sendAlert(action, rule, event);
                break;
            case "BLOCK_USER":
                blockUser(action, event);
                break;
            case "NOTIFY_ADMIN":
                notifyAdmin(action, rule, event);
                break;
            case "LOG_VIOLATION":
                // Violation logging is handled separately
                break;
            default:
                log.warn("Unknown action type: {}", actionType);
        }
    }

    private void sendAlert(JsonNode action, ComplianceRule rule, AuditEvent event) {
        String message = action.get("message").asText();
        String violationDetails = String.format("%s - Event: %s, User: %s, Action: %s",
                message, event.getEventId(),
                event.getUserId(), event.getAction());

        alertService.sendComplianceViolationAlert(rule, event.getId(), violationDetails);
    }

    private void blockUser(JsonNode action, AuditEvent event) {
        if (event.getUserId() != null) {
            int durationMinutes = action.get("duration").asInt(15);
            // Implementation would integrate with user management service
            log.info("Blocking user {} for {} minutes due to compliance violation",
                    event.getUserId(), durationMinutes);
        }
    }

    private void notifyAdmin(JsonNode action, ComplianceRule rule, AuditEvent event) {
        // Implementation would send notifications to administrators
        log.info("Notifying administrators about compliance violation: {}", rule.getName());
    }

    private void logComplianceViolation(ComplianceRule rule, AuditEvent event) {
        // Implementation would create a compliance violation record
        log.warn("Compliance violation detected: Rule={}, Event={}, Severity={}",
                rule.getName(), event.getEventId(), rule.getSeverity());
    }

    /**
     * Scheduled evaluation of time-based and aggregation rules
     */
    @Scheduled(fixedRateString = "${audit.compliance.rules.evaluation-interval:30000}")
    public void evaluateScheduledRules() {
        // Evaluate rules that require periodic checking
        log.debug("Running scheduled compliance rule evaluation");
    }

    /**
     * Parse JSON string to JsonNode
     */
    private JsonNode parseJsonString(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            log.warn("Failed to parse JSON string: {}", jsonString);
            return null;
        }
    }
}