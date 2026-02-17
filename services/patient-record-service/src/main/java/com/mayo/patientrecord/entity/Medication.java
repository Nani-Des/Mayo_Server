package com.mayo.patientrecord.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "medications")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "patient_record_id", nullable = false)
    private UUID patientRecordId;

    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @Column(name = "generic_name")
    private String genericName;

    @Column(name = "brand_name")
    private String brandName;

    @Column(name = "strength")
    private String strength;

    @Column(name = "form")
    private String form;

    @Column(name = "dosage")
    private String dosage;

    @Column(name = "frequency")
    private String frequency;

    @Column(name = "route")
    private String route;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "refills")
    private Integer refills;

    @Column(name = "prescribing_provider")
    private String prescribingProvider;

    @Column(name = "prescribed_at")
    private LocalDateTime prescribedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MedicationStatus status;

    @Column(name = "indication")
    private String indication;

    @Column(name = "instructions")
    private String instructions;

    @Column(name = "side_effects")
    private String sideEffects;

    @Column(name = "interactions")
    private String interactions;

    @Column(name = "cost", precision = 8, scale = 2)
    private BigDecimal cost;

    @Column(name = "insurance_covered")
    private Boolean insuranceCovered;

    @Column(name = "pharmacy")
    private String pharmacy;

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

    public enum MedicationStatus {
        PRESCRIBED,
        ACTIVE,
        ON_HOLD,
        DISCONTINUED,
        COMPLETED,
        CANCELLED
    }
}