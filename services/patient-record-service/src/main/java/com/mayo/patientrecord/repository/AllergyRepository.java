package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.Allergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, UUID> {

    List<Allergy> findByPatientRecordIdInOrderByReportedDateDesc(List<UUID> patientRecordIds);

    List<Allergy> findByPatientRecordId(UUID patientRecordId);

    List<Allergy> findByPatientRecordIdOrderByReportedDateDesc(UUID patientRecordId);

    @Query("SELECT a FROM Allergy a WHERE a.patientRecordId = :patientRecordId AND a.status = :status ORDER BY a.reportedDate DESC")
    List<Allergy> findByPatientRecordIdAndStatusOrderByReportedDateDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("status") Allergy.AllergyStatus status);

    @Query("SELECT a FROM Allergy a WHERE a.patientRecordId = :patientRecordId AND a.allergenType = :allergenType ORDER BY a.reportedDate DESC")
    List<Allergy> findByPatientRecordIdAndAllergenTypeOrderByReportedDateDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("allergenType") Allergy.AllergenType allergenType);

    @Query("SELECT a FROM Allergy a WHERE a.patientRecordId = :patientRecordId AND a.reactionSeverity = :severity ORDER BY a.reportedDate DESC")
    List<Allergy> findByPatientRecordIdAndReactionSeverityOrderByReportedDateDesc(
            @Param("patientRecordId") UUID patientRecordId,
            @Param("severity") Allergy.ReactionSeverity severity);

    @Query("SELECT a FROM Allergy a WHERE a.patientRecordId = :patientRecordId AND a.status = 'ACTIVE' ORDER BY a.reportedDate DESC")
    List<Allergy> findActiveAllergiesByPatientRecordId(@Param("patientRecordId") UUID patientRecordId);

    List<Allergy> findByStatus(Allergy.AllergyStatus status);
}