package com.mayo.patientrecord.dto;

import com.mayo.patientrecord.entity.VitalSigns;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalSignsDto {

    private UUID id;
    private UUID patientRecordId;

    @NotNull(message = "Recorded date/time is required")
    private LocalDateTime recordedAt;

    private String recordedBy;
    private String location;

    @DecimalMin(value = "30.0", message = "Temperature must be at least 30°C")
    @DecimalMax(value = "45.0", message = "Temperature cannot exceed 45°C")
    private BigDecimal temperature;

    private VitalSigns.TemperatureUnit temperatureUnit;

    @Min(value = 30, message = "Heart rate must be at least 30 bpm")
    @Max(value = 250, message = "Heart rate cannot exceed 250 bpm")
    private Integer heartRate;

    @Min(value = 6, message = "Respiratory rate must be at least 6 breaths/min")
    @Max(value = 60, message = "Respiratory rate cannot exceed 60 breaths/min")
    private Integer respiratoryRate;

    @Min(value = 60, message = "Systolic BP must be at least 60 mmHg")
    @Max(value = 300, message = "Systolic BP cannot exceed 300 mmHg")
    private Integer systolicBp;

    @Min(value = 30, message = "Diastolic BP must be at least 30 mmHg")
    @Max(value = 200, message = "Diastolic BP cannot exceed 200 mmHg")
    private Integer diastolicBp;

    @DecimalMin(value = "70.0", message = "Oxygen saturation must be at least 70%")
    @DecimalMax(value = "100.0", message = "Oxygen saturation cannot exceed 100%")
    private BigDecimal oxygenSaturation;

    private Boolean oxygenSupplement;

    @DecimalMin(value = "0.5", message = "Weight must be at least 0.5 kg")
    @DecimalMax(value = "500.0", message = "Weight cannot exceed 500 kg")
    private BigDecimal weightKg;

    @DecimalMin(value = "20.0", message = "Height must be at least 20 cm")
    @DecimalMax(value = "250.0", message = "Height cannot exceed 250 cm")
    private BigDecimal heightCm;

    @DecimalMin(value = "10.0", message = "BMI must be at least 10")
    @DecimalMax(value = "80.0", message = "BMI cannot exceed 80")
    private BigDecimal bmi;

    @Min(value = 0, message = "Pain scale must be 0-10")
    @Max(value = 10, message = "Pain scale must be 0-10")
    private Integer painScale;

    @Min(value = 20, message = "Glucose must be at least 20 mg/dL")
    @Max(value = 1000, message = "Glucose cannot exceed 1000 mg/dL")
    private Integer glucoseMgDl;

    private VitalSigns.PatientPosition position;
    private String method;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}