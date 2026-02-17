package com.mayo.patientrecord.dto;

import com.mayo.patientrecord.entity.ImagingStudy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImagingStudyDto {

    private UUID id;
    private UUID patientRecordId;

    @NotBlank(message = "Study type is required")
    private String studyType;

    private String modality;
    private String bodyPart;
    private String studyDescription;
    private ImagingStudy.StudyStatus status;
    private LocalDateTime performedAt;
    private LocalDateTime reportedAt;
    private String performingFacility;
    private String orderingProvider;
    private String interpretingProvider;
    private String findings;
    private String impression;
    private String recommendations;
    private String accessionNumber;
    private String studyInstanceUid;

    @Min(value = 0, message = "Image count must be non-negative")
    private Integer imageCount;

    private String radiationDose;
    private Boolean contrastUsed;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}