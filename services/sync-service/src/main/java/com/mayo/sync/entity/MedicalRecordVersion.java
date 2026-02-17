package com.mayo.sync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "medical_record_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "record_type", nullable = false)
    private String recordType; // LAB_RESULT, MEDICATION, etc.

    @Column(name = "record_id", nullable = false)
    private String recordId;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "parent_version_id")
    private UUID parentVersionId;

    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private String content;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "originating_device_id", nullable = false)
    private String originatingDeviceId;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "cryptographic_signature")
    private String cryptographicSignature;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest;

    @Column(name = "change_type", nullable = false)
    private String changeType; // CREATE, UPDATE, DELETE, MERGE

    @Column(name = "doctor_user_id")
    private UUID doctorUserId;

    @Column(name = "conflict_resolution_status")
    private String conflictResolutionStatus; // none, resolved, pending

    // Version chain integrity fields
    @Column(name = "chain_depth")
    private Integer chainDepth;

    @Column(name = "parent_hash")
    private String parentHash;

    @Column(name = "is_valid")
    private Boolean isValid;

    @Column(name = "last_verified")
    private LocalDateTime lastVerified;
}