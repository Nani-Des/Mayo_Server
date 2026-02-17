package com.mayo.patientrecord.dto;

import com.mayo.patientrecord.entity.Medication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationDto {

    private UUID id;
    private UUID patientRecordId;

    @NotBlank(message = "Medication name is required")
    private String medicationName;

    private String genericName;
    private String brandName;
    private String strength;
    private String form;
    private String dosage;
    private String frequency;
    private String route;

    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @Min(value = 0, message = "Refills cannot be negative")
    private Integer refills;

    private String prescribingProvider;
    private LocalDateTime prescribedAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Medication.MedicationStatus status;
    private String indication;
    private String instructions;
    private String sideEffects;
    private String interactions;

    @DecimalMin(value = "0.0", message = "Cost must be non-negative")
    private BigDecimal cost;

    private Boolean insuranceCovered;
    private String pharmacy;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}