package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    List<Medication> findByPatientRecordIdInOrderByPrescribedAtDesc(List<UUID> patientRecordIds);

    List<Medication> findByPatientRecordId(UUID patientRecordId);

    List<Medication> findByPatientRecordIdOrderByPrescribedAtDesc(UUID patientRecordId);

    @Query("SELECT m FROM Medication m WHERE m.patientRecordId = :patientRecordId AND m.status = :status ORDER BY m.prescribedAt DESC")
    List<Medication> findByPatientRecordIdAndStatusOrderByPrescribedAtDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("status") Medication.MedicationStatus status);

    @Query("SELECT m FROM Medication m WHERE m.patientRecordId = :patientRecordId AND m.medicationName LIKE %:medicationName% ORDER BY m.prescribedAt DESC")
    List<Medication> findByPatientRecordIdAndMedicationNameContainingOrderByPrescribedAtDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("medicationName") String medicationName);

    @Query("SELECT m FROM Medication m WHERE m.patientRecordId = :patientRecordId AND m.status IN ('ACTIVE', 'PRESCRIBED') AND (m.endedAt IS NULL OR m.endedAt > :currentDate) ORDER BY m.startedAt DESC")
    List<Medication> findActiveMedicationsByPatientRecordId(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("currentDate") LocalDateTime currentDate);

    List<Medication> findByStatus(Medication.MedicationStatus status);
}