package com.mayo.common.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.common.core.enums.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Security audit service for logging security events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityAuditService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String AUDIT_EVENTS_TOPIC = "audit.events";

    /**
     * Log authentication event
     */
    public void logAuthenticationEvent(UUID userId, String email, String action,
                                     boolean success, String ipAddress, String userAgent) {
        Map<String, Object> event = createBaseEvent(userId, email, action, "AUTHENTICATION");
        event.put("success", success);
        event.put("ipAddress", ipAddress);
        event.put("userAgent", userAgent);

        if (!success) {
            event.put("severity", "WARN");
        }

        publishEvent(event);
    }

    /**
     * Log authorization event
     */
    public void logAuthorizationEvent(UUID userId, String email, String action,
                                    Permission permission, boolean granted, String resource) {
        Map<String, Object> event = createBaseEvent(userId, email, action, "AUTHORIZATION");
        event.put("permission", permission.name());
        event.put("granted", granted);
        event.put("resource", resource);

        if (!granted) {
            event.put("severity", "ERROR");
        }

        publishEvent(event);
    }

    /**
     * Log security violation
     */
    public void logSecurityViolation(String action, String details, String ipAddress,
                                   String userAgent, String severity) {
        Map<String, Object> event = createBaseEvent(null, null, action, "SECURITY_VIOLATION");
        event.put("details", details);
        event.put("ipAddress", ipAddress);
        event.put("userAgent", userAgent);
        event.put("severity", severity);

        publishEvent(event);
    }

    /**
     * Log data access event
     */
    public void logDataAccessEvent(UUID userId, String email, String action,
                                 String resourceType, UUID resourceId, boolean sensitive) {
        Map<String, Object> event = createBaseEvent(userId, email, action, resourceType);
        event.put("resourceId", resourceId.toString());
        event.put("sensitive", sensitive);

        if (sensitive) {
            event.put("severity", "WARN");
        }

        publishEvent(event);
    }

    /**
     * Log configuration change
     */
    public void logConfigurationChange(UUID userId, String email, String component,
                                     String setting, String oldValue, String newValue) {
        Map<String, Object> event = createBaseEvent(userId, email, "CONFIGURATION_CHANGED", "SYSTEM");
        event.put("component", component);
        event.put("setting", setting);
        event.put("oldValue", oldValue);
        event.put("newValue", newValue);
        event.put("severity", "ERROR");

        publishEvent(event);
    }

    /**
     * Log certificate event
     */
    public void logCertificateEvent(String action, String certificateType,
                                  String subject, LocalDateTime expiry) {
        Map<String, Object> event = createBaseEvent(null, null, action, "CERTIFICATE");
        event.put("certificateType", certificateType);
        event.put("subject", subject);
        event.put("expiry", expiry.toString());
        event.put("severity", "WARN");

        publishEvent(event);
    }

    private Map<String, Object> createBaseEvent(UUID userId, String email, String action, String resourceType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("action", action);
        event.put("resourceType", resourceType);
        event.put("severity", "INFO");

        if (userId != null) {
            event.put("userId", userId.toString());
        }
        if (email != null) {
            event.put("email", email);
        }

        return event;
    }

    private void publishEvent(Map<String, Object> event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(AUDIT_EVENTS_TOPIC, event.get("eventId").toString(), message);
            log.debug("Published security audit event: {}", event.get("action"));
        } catch (Exception e) {
            log.error("Failed to publish security audit event", e);
        }
    }
}