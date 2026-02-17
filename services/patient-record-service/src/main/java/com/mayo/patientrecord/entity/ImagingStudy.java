package com.mayo.patientrecord.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "imaging_studies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImagingStudy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "patient_record_id", nullable = false)
    private UUID patientRecordId;

    @Column(name = "study_type", nullable = false)
    private String studyType;

    @Column(name = "modality")
    private String modality;

    @Column(name = "body_part")
    private String bodyPart;

    @Column(name = "study_description")
    private String studyDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StudyStatus status;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "performing_facility")
    private String performingFacility;

    @Column(name = "ordering_provider")
    private String orderingProvider;

    @Column(name = "interpreting_provider")
    private String interpretingProvider;

    @Column(name = "findings")
    private String findings;

    @Column(name = "impression")
    private String impression;

    @Column(name = "recommendations")
    private String recommendations;

    @Column(name = "accession_number")
    private String accessionNumber;

    @Column(name = "study_instance_uid")
    private String studyInstanceUid;

    @Column(name = "image_count")
    private Integer imageCount;

    @Column(name = "radiation_dose")
    private String radiationDose;

    @Column(name = "contrast_used")
    private Boolean contrastUsed;

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

    public enum StudyStatus {
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        PRELIMINARY,
        FINAL
    }
}