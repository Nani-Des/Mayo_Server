package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.ImagingStudy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ImagingStudyRepository extends JpaRepository<ImagingStudy, UUID> {

    List<ImagingStudy> findByPatientRecordIdInOrderByPerformedAtDesc(List<UUID> patientRecordIds);

    List<ImagingStudy> findByPatientRecordId(UUID patientRecordId);

    List<ImagingStudy> findByPatientRecordIdOrderByPerformedAtDesc(UUID patientRecordId);

    @Query("SELECT is FROM ImagingStudy is WHERE is.patientRecordId = :patientRecordId AND is.studyType = :studyType ORDER BY is.performedAt DESC")
    List<ImagingStudy> findByPatientRecordIdAndStudyTypeOrderByPerformedAtDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("studyType") String studyType);

    @Query("SELECT is FROM ImagingStudy is WHERE is.patientRecordId = :patientRecordId AND is.performedAt BETWEEN :startDate AND :endDate ORDER BY is.performedAt DESC")
    List<ImagingStudy> findByPatientRecordIdAndPerformedAtBetween(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<ImagingStudy> findByStatus(ImagingStudy.StudyStatus status);

    @Query("SELECT is FROM ImagingStudy is WHERE is.patientRecordId = :patientRecordId AND is.status = :status ORDER BY is.performedAt DESC")
    List<ImagingStudy> findByPatientRecordIdAndStatusOrderByPerformedAtDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("status") ImagingStudy.StudyStatus status);

    List<ImagingStudy> findByAccessionNumber(String accessionNumber);
}