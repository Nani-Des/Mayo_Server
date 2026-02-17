package com.mayo.patientrecord.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "allergies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "patient_record_id", nullable = false)
    private UUID patientRecordId;

    @Column(name = "allergen", nullable = false)
    private String allergen;

    @Enumerated(EnumType.STRING)
    @Column(name = "allergen_type")
    private AllergenType allergenType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_severity")
    private ReactionSeverity reactionSeverity;

    @Column(name = "reaction_description")
    private String reactionDescription;

    @Column(name = "symptoms")
    private String symptoms;

    @Column(name = "onset_date")
    private LocalDateTime onsetDate;

    @Column(name = "reported_date")
    private LocalDateTime reportedDate;

    @Column(name = "reported_by")
    private String reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AllergyStatus status;

    @Column(name = "verification_status")
    private String verificationStatus;

    @Column(name = "verification_date")
    private LocalDateTime verificationDate;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "treatment")
    private String treatment;

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

    public enum AllergenType {
        DRUG,
        FOOD,
        ENVIRONMENTAL,
        LATEX,
        INSECT,
        OTHER
    }

    public enum ReactionSeverity {
        MILD,
        MODERATE,
        SEVERE,
        LIFE_THREATENING
    }

    public enum AllergyStatus {
        ACTIVE,
        INACTIVE,
        RESOLVED,
        UNCONFIRMED
    }
}