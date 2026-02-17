package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, UUID> {

    List<LabResult> findByPatientRecordIdInOrderByPerformedAtDesc(List<UUID> patientRecordIds);

    List<LabResult> findByPatientRecordId(UUID patientRecordId);

    List<LabResult> findByPatientRecordIdOrderByPerformedAtDesc(UUID patientRecordId);

    @Query("SELECT lr FROM LabResult lr WHERE lr.patientRecordId = :patientRecordId AND lr.testName = :testName ORDER BY lr.performedAt DESC")
    List<LabResult> findByPatientRecordIdAndTestNameOrderByPerformedAtDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("testName") String testName);

    @Query("SELECT lr FROM LabResult lr WHERE lr.patientRecordId = :patientRecordId AND lr.performedAt BETWEEN :startDate AND :endDate ORDER BY lr.performedAt DESC")
    List<LabResult> findByPatientRecordIdAndPerformedAtBetween(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<LabResult> findByStatus(LabResult.LabStatus status);

    @Query("SELECT lr FROM LabResult lr WHERE lr.patientRecordId = :patientRecordId AND lr.status = :status ORDER BY lr.performedAt DESC")
    List<LabResult> findByPatientRecordIdAndStatusOrderByPerformedAtDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("status") LabResult.LabStatus status);
}