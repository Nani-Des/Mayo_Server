package com.mayo.patientrecord.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDto {

    private UUID id;
    private String medicalRecordNumber;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;
    private String contactInfo;
    private UUID ownerId; // User ID who owns this patient record
    private UUID familyMemberId; // Optional link to family member
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}