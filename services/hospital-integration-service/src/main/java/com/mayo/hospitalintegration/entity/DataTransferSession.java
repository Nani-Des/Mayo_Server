package com.mayo.hospitalintegration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "data_transfer_sessions", indexes = {
    @Index(name = "idx_transfer_hospital_device", columnList = "hospital_id, device_id"),
    @Index(name = "idx_transfer_status_timestamp", columnList = "status, created_at"),
    @Index(name = "idx_transfer_session_status", columnList = "session_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataTransferSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "session_id", unique = true, nullable = false)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false)
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private HospitalDevice device;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false)
    private TransferType transferType;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false)
    private TransferProtocol protocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransferStatus status;

    @Column(name = "total_records")
    private Long totalRecords;

    @Column(name = "processed_records")
    private Long processedRecords = 0L;

    @Column(name = "failed_records")
    private Long failedRecords = 0L;

    @Column(name = "data_size_bytes")
    private Long dataSizeBytes;

    @Column(name = "transferred_bytes")
    private Long transferredBytes = 0L;

    @Column(name = "source_endpoint")
    private String sourceEndpoint;

    @Column(name = "destination_endpoint")
    private String destinationEndpoint;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON metadata

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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
        if (status == null) {
            status = TransferStatus.INITIATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TransferType {
        PULL,  // Device pulling data from hospital
        PUSH,  // Hospital pushing data to device
        SYNC   // Bidirectional synchronization
    }

    public enum DataType {
        PRESCRIPTION,
        DIAGNOSIS,
        LAB_RESULT,
        IMAGING,
        TREATMENT,
        MEDICATION,
        ALLERGY,
        VITAL_SIGN,
        PATIENT_RECORD,
        BULK_DATA
    }

    public enum TransferProtocol {
        HL7,
        FHIR,
        DICOM,
        REST_API,
        SOAP,
        SFTP,
        HTTPS,
        WEBSOCKET
    }

    public enum TransferStatus {
        INITIATED,
        CONNECTING,
        TRANSFERRING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }
}