package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Patient entity
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    List<Patient> findByOwnerId(UUID ownerId);
    Optional<Patient> findByFamilyMemberId(UUID familyMemberId);
    Optional<Patient> findByMedicalRecordNumber(String medicalRecordNumber);
}