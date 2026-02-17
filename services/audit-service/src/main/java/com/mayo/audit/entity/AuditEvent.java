package com.mayo.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for audit events
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_events_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_events_patient_id", columnList = "patient_id"),
    @Index(name = "idx_audit_events_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_events_action", columnList = "action"),
    @Index(name = "idx_audit_events_resource_type", columnList = "resource_type"),
    @Index(name = "idx_audit_events_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_audit_events_originating_device_id", columnList = "originating_device_id"),
    @Index(name = "idx_audit_events_doctor_user_id", columnList = "doctor_user_id"),
    @Index(name = "idx_audit_events_version_number", columnList = "version_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "hospital_id")
    private UUID hospitalId;

    @Column(name = "session_id")
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "location")
    private String location;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON string

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "compliance_flags", columnDefinition = "TEXT")
    private String complianceFlags; // JSON string

    @Column(name = "hash_value")
    private String hashValue;

    @Column(name = "previous_event_hash")
    private String previousEventHash;

    @Column(name = "digital_signature")
    private String digitalSignature;

    // Versioning metadata fields
    @Column(name = "originating_device_id")
    private UUID originatingDeviceId;

    @Column(name = "doctor_user_id")
    private UUID doctorUserId;

    @Column(name = "version_number")
    private Long versionNumber;

    @Column(name = "parent_version_hash")
    private String parentVersionHash;

    @Column(name = "versioning_digital_signature")
    private String versioningDigitalSignature;

    @Column(name = "conflict_resolution_metadata", columnDefinition = "TEXT")
    private String conflictResolutionMetadata; // JSON string

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AuditAction {
        CREATE,
        READ,
        UPDATE,
        DELETE,
        LOGIN,
        LOGOUT,
        REGISTER,
        PASSWORD_RESET,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        PASSWORD_CHANGED,
        PERMISSION_GRANTED,
        PERMISSION_REVOKED,
        ROLE_ASSIGNED,
        ROLE_REMOVED,
        ACCESS_DENIED,
        UNAUTHORIZED_ACCESS_ATTEMPT,
        WAF_BLOCKED_REQUEST,
        RATE_LIMIT_EXCEEDED,
        BRUTE_FORCE_DETECTED,
        SUSPICIOUS_ACTIVITY_DETECTED,
        MALWARE_DETECTED,
        TLS_HANDSHAKE_FAILED,
        INVALID_CERTIFICATE,
        SSL_PROTOCOL_VIOLATION,
        RECORD_ACCESSED,
        PHI_ACCESSED,
        RECORD_MODIFIED,
        RECORD_CREATED,
        RECORD_DELETED,
        SENSITIVE_DATA_ACCESSED,
        USER_CREATED,
        USER_DELETED,
        USER_MODIFIED,
        STAFF_ADDED,
        STAFF_REMOVED,
        CONFIGURATION_CHANGED,
        SECURITY_POLICY_UPDATED,
        COMPLIANCE_CHECK_PASSED,
        COMPLIANCE_CHECK_FAILED,
        EXPORT,
        IMPORT,
        SEARCH,
        AUDIT_VIEW,
        COMPLIANCE_CHECK,
        BACKUP,
        RESTORE,
        CONFIG_CHANGE,
        USER_MANAGEMENT,
        DEVICE_REGISTRATION,
        SYNC_OPERATION,
        PATIENT_ACCESS,
        MEDICAL_RECORD_ACCESS,
        PRESCRIPTION_ACCESS,
        APPOINTMENT_ACCESS,
        VITAL_SIGNS_ACCESS,
        LAB_RESULTS_ACCESS,
        IMAGING_ACCESS,
        REPORT_GENERATION,
        DATA_EXPORT,
        SYSTEM_MAINTENANCE
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL,
        INFO,
        WARN,
        ERROR
    }
}