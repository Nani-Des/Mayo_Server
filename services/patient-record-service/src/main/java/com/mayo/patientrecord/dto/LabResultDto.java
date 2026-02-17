package com.mayo.patientrecord.dto;

import com.mayo.patientrecord.entity.LabResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabResultDto {

    private UUID id;
    private UUID patientRecordId;

    @NotBlank(message = "Test name is required")
    private String testName;

    private String testCode;
    private String category;

    @DecimalMin(value = "0.0", message = "Value must be positive")
    @DecimalMax(value = "999999.99", message = "Value is too large")
    private BigDecimal value;

    private String unit;
    private String referenceRange;
    private LabResult.LabStatus status;
    private LocalDateTime performedAt;
    private LocalDateTime reportedAt;
    private String performingLab;
    private String orderingProvider;
    private String interpretation;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}