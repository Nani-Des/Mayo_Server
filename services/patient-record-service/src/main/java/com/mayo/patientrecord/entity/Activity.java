package com.mayo.patientrecord.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "activities", indexes = {
        @Index(name = "idx_activity_patient_record", columnList = "patient_id, record_id"),
        @Index(name = "idx_activity_record_type", columnList = "record_type"),
        @Index(name = "idx_activity_action", columnList = "action"),
        @Index(name = "idx_activity_timestamp", columnList = "timestamp"),
        @Index(name = "idx_activity_user", columnList = "user_id"),
        @Index(name = "idx_activity_device", columnList = "device_id"),
        @Index(name = "idx_activity_hospital", columnList = "hospital_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    private RecordType recordType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private Action action;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "hospital_id")
    private UUID hospitalId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = createdAt;
        }
    }

    public enum Action {
        CREATE,
        UPDATE,
        VIEW,
        DELETE
    }
}