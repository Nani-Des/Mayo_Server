package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.VitalSigns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface VitalSignsRepository extends JpaRepository<VitalSigns, UUID> {

    List<VitalSigns> findByPatientRecordIdInOrderByRecordedAtDesc(List<UUID> patientRecordIds);

    List<VitalSigns> findByPatientRecordId(UUID patientRecordId);

    List<VitalSigns> findByPatientRecordIdOrderByRecordedAtDesc(UUID patientRecordId);

    @Query("SELECT vs FROM VitalSigns vs WHERE vs.patientRecordId = :patientRecordId AND vs.recordedAt BETWEEN :startDate AND :endDate ORDER BY vs.recordedAt DESC")
    List<VitalSigns> findByPatientRecordIdAndRecordedAtBetween(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT vs FROM VitalSigns vs WHERE vs.patientRecordId = :patientRecordId ORDER BY vs.recordedAt DESC LIMIT 1")
    VitalSigns findLatestByPatientRecordId(@Param("patientRecordId") UUID patientRecordId);

    @Query("SELECT vs FROM VitalSigns vs WHERE vs.patientRecordId = :patientRecordId AND vs.recordedAt >= :since ORDER BY vs.recordedAt DESC")
    List<VitalSigns> findByPatientRecordIdAndRecordedAtAfter(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("since") LocalDateTime since);
}