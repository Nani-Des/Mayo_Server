package com.mayo.sync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delta_changes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeltaChange {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "record_id", nullable = false)
    private String recordId;

    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    // CRDT support fields
    @Column(name = "document_id")
    private String documentId;

    @Column(name = "crdt_state", columnDefinition = "BYTEA")
    private byte[] crdtState;

    @Column(name = "is_crdt_enabled", nullable = false)
    private Boolean isCrdtEnabled = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "entity_version")
    private Integer entityVersion;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = createdAt;
        }
    }

    public enum ChangeType {
        CREATE,
        UPDATE,
        DELETE
    }
}