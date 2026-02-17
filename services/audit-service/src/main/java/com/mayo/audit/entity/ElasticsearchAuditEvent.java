package com.mayo.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.audit.entity.AuditEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Elasticsearch document for audit events
 */
@Document(indexName = "audit_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ElasticsearchAuditEvent {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String eventId;

    @Field(type = FieldType.Date)
    private LocalDateTime timestamp;

    @Field(type = FieldType.Keyword)
    private UUID userId;

    @Field(type = FieldType.Keyword)
    private UUID deviceId;

    @Field(type = FieldType.Keyword)
    private UUID hospitalId;

    @Field(type = FieldType.Keyword)
    private String sessionId;

    @Field(type = FieldType.Keyword)
    private String action;

    @Field(type = FieldType.Keyword)
    private String resourceType;

    @Field(type = FieldType.Keyword)
    private UUID resourceId;

    @Field(type = FieldType.Keyword)
    private UUID patientId;

    @Field(type = FieldType.Ip)
    private String ipAddress;

    @Field(type = FieldType.Text)
    private String userAgent;

    @Field(type = FieldType.Object)
    private JsonNode location;

    @Field(type = FieldType.Object)
    private JsonNode metadata;

    @Field(type = FieldType.Keyword)
    private String severity;

    @Field(type = FieldType.Object)
    private JsonNode complianceFlags;

    @Field(type = FieldType.Keyword)
    private String hashValue;

    @Field(type = FieldType.Keyword)
    private String previousEventHash;

    @Field(type = FieldType.Text)
    private String digitalSignature;

    // Versioning metadata fields
    @Field(type = FieldType.Keyword)
    private UUID originatingDeviceId;

    @Field(type = FieldType.Keyword)
    private UUID doctorUserId;

    @Field(type = FieldType.Long)
    private Long versionNumber;

    @Field(type = FieldType.Keyword)
    private String parentVersionHash;

    @Field(type = FieldType.Text)
    private String versioningDigitalSignature;

    @Field(type = FieldType.Object)
    private JsonNode conflictResolutionMetadata;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;

    @Field(type = FieldType.Integer)
    private Integer version;

    // Constructor to convert from JPA ey
    public ElasticsearchAuditEvent(AuditEvent auditEvent) {
        this.id = auditEvent.getId().toString();
        this.eventId = auditEvent.getEventId();
        this.timestamp = auditEvent.getTimestamp();
        this.userId = auditEvent.getUserId(); // UUID to UUID is fine
        this.deviceId = auditEvent.getDeviceId();
        this.hospitalId = auditEvent.getHospitalId();
        this.sessionId = auditEvent.getSessionId();
        this.action = auditEvent.getAction() != null ? auditEvent.getAction().name() : null;
        this.resourceType = auditEvent.getResourceType();
        this.resourceId = auditEvent.getResourceId() != null ? UUID.fromString(auditEvent.getResourceId()) : null;
        this.patientId = auditEvent.getPatientId();
        this.ipAddress = auditEvent.getIpAddress() != null ? auditEvent.getIpAddress().getHostAddress() : null;
        this.userAgent = auditEvent.getUserAgent();
        this.location = parseJsonString(auditEvent.getLocation());
        this.metadata = parseJsonString(auditEvent.getMetadata());
        this.severity = auditEvent.getSeverity() != null ? auditEvent.getSeverity().name() : null;
        this.complianceFlags = parseJsonString(auditEvent.getComplianceFlags());
        this.hashValue = auditEvent.getHashValue();
        this.previousEventHash = auditEvent.getPreviousEventHash();
        this.digitalSignature = auditEvent.getDigitalSignature();

        // Versioning fields
        this.originatingDeviceId = auditEvent.getOriginatingDeviceId();
        this.doctorUserId = auditEvent.getDoctorUserId();
        this.versionNumber = auditEvent.getVersionNumber();
        this.parentVersionHash = auditEvent.getParentVersionHash();
        this.versioningDigitalSignature = auditEvent.getVersioningDigitalSignature();
        this.conflictResolutionMetadata = parseJsonString(auditEvent.getConflictResolutionMetadata());

        this.createdAt = auditEvent.getCreatedAt();
        this.updatedAt = auditEvent.getUpdatedAt();
        this.version = auditEvent.getVersion();
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static JsonNode parseJsonString(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            return null;
        }
    }
}