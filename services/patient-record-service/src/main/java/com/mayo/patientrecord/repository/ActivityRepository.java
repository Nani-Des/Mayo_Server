package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.Activity;
import com.mayo.patientrecord.entity.RecordType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findByPatientId(UUID patientId);

    List<Activity> findByRecordId(UUID recordId);

    List<Activity> findByRecordType(RecordType recordType);

    List<Activity> findByUserId(String userId);

    List<Activity> findByDeviceId(UUID deviceId);

    List<Activity> findByHospitalId(UUID hospitalId);

    @Query("SELECT a FROM Activity a WHERE a.patientId = :patientId AND a.recordType IN :recordTypes ORDER BY a.timestamp DESC")
    List<Activity> findByPatientIdAndRecordTypeIn(@Param("patientId") UUID patientId, @Param("recordTypes") List<RecordType> recordTypes);

    @Query("SELECT a FROM Activity a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<Activity> findByTimestampBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT a FROM Activity a WHERE a.patientId = :patientId AND a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<Activity> findByPatientIdAndTimestampBetween(@Param("patientId") UUID patientId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}