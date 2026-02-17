package com.mayo.hospitalintegration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "activities", indexes = {
    @Index(name = "idx_activity_hospital_device", columnList = "hospital_id, device_id"),
    @Index(name = "idx_activity_type_timestamp", columnList = "activity_type, timestamp"),
    @Index(name = "idx_activity_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false)
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private HospitalDevice device;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Column(name = "activity_description", nullable = false)
    private String activityDescription;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "record_id")
    private String recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type")
    private RecordType recordType;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON metadata

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = createdAt;
        }
    }

    public enum ActivityType {
        // Device activities
        DEVICE_REGISTRATION,
        DEVICE_HEARTBEAT,
        DEVICE_STATUS_CHANGE,
        DEVICE_MAINTENANCE,

        // Data transfer activities
        DATA_TRANSFER_INITIATED,
        DATA_TRANSFER_COMPLETED,
        DATA_TRANSFER_FAILED,
        DATA_TRANSFER_CANCELLED,

        // Access activities
        ACCESS_REQUESTED,
        ACCESS_GRANTED,
        ACCESS_DENIED,
        ACCESS_REVOKED,

        // Prescription activities
        PRESCRIPTION_CREATED,
        PRESCRIPTION_UPDATED,
        PRESCRIPTION_DELETED,
        PRESCRIPTION_ACCESSED,

        // Diagnosis activities
        DIAGNOSIS_CREATED,
        DIAGNOSIS_UPDATED,
        DIAGNOSIS_DELETED,
        DIAGNOSIS_ACCESSED,

        // Authentication activities
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        LOGOUT,
        SESSION_EXPIRED,

        // System activities
        SYSTEM_MAINTENANCE,
        CONFIGURATION_CHANGE,
        ERROR_OCCURRED
    }

    public enum RecordType {
        PRESCRIPTION,
        DIAGNOSIS,
        LAB_RESULT,
        IMAGING,
        TREATMENT,
        MEDICATION,
        ALLERGY,
        VITAL_SIGN,
        PATIENT_RECORD
    }
}