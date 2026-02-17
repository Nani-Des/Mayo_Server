package com.mayo.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for audit event data transfer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDto {

    private UUID id;
    private String eventId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    private UUID userId;
    private UUID deviceId;
    private UUID hospitalId;
    private String sessionId;
    private String action;
    private String resourceType;
    private String resourceId;
    private UUID patientId;
    private InetAddress ipAddress;
    private String userAgent;
    private String location;
    private String metadata;
    private String severity;
    private String complianceFlags;
    private String hashValue;

    // Versioning metadata fields
    private UUID originatingDeviceId;
    private UUID doctorUserId;
    private Long versionNumber;
    private String parentVersionHash;
    private String versioningDigitalSignature;
    private String conflictResolutionMetadata;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime updatedAt;
}