package com.mayo.sync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conflicts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conflict {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "record_id", nullable = false)
    private String recordId;

    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Column(name = "local_version", nullable = false)
    private Long localVersion;

    @Column(name = "server_version", nullable = false)
    private Long serverVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_type", nullable = false)
    private ConflictType conflictType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false)
    private ResolutionStatus resolutionStatus;

    @Column(name = "local_data", columnDefinition = "TEXT")
    private String localData;

    @Column(name = "server_data", columnDefinition = "TEXT")
    private String serverData;

    @Column(name = "resolved_data", columnDefinition = "TEXT")
    private String resolvedData;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

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

    public enum ConflictType {
        VERSION_CONFLICT,
        DATA_CONFLICT,
        DELETION_CONFLICT
    }

    public enum ResolutionStatus {
        PENDING,
        RESOLVED_LOCAL_WINS,
        RESOLVED_SERVER_WINS,
        RESOLVED_MERGED,
        RESOLVED_MANUAL
    }
}