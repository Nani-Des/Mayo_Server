package com.mayo.patientrecord.dto;

import com.mayo.patientrecord.entity.Allergy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllergyDto {

    private UUID id;
    private UUID patientRecordId;

    @NotBlank(message = "Allergen is required")
    private String allergen;

    private Allergy.AllergenType allergenType;
    private Allergy.ReactionSeverity reactionSeverity;
    private String reactionDescription;
    private String symptoms;
    private LocalDateTime onsetDate;
    private LocalDateTime reportedDate;
    private String reportedBy;
    private Allergy.AllergyStatus status;
    private String verificationStatus;
    private LocalDateTime verificationDate;
    private String verifiedBy;
    private String treatment;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}