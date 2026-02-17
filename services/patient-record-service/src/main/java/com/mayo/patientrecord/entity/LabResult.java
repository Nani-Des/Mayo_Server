package com.mayo.patientrecord.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "patient_record_id", nullable = false)
    private UUID patientRecordId;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Column(name = "test_code")
    private String testCode;

    @Column(name = "category")
    private String category;

    @Column(name = "value", precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "unit")
    private String unit;

    @Column(name = "reference_range")
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private LabStatus status;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "performing_lab")
    private String performingLab;

    @Column(name = "ordering_provider")
    private String orderingProvider;

    @Column(name = "interpretation")
    private String interpretation;

    @Column(name = "notes")
    private String notes;

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

    public enum LabStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        CORRECTED
    }
}