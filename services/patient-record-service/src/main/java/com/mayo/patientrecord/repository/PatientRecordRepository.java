package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.PatientRecord;
import com.mayo.patientrecord.entity.RecordType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientRecordRepository extends JpaRepository<PatientRecord, UUID> {

    List<PatientRecord> findByPatientId(UUID patientId);

    List<PatientRecord> findByPatientIdAndRecordType(UUID patientId, RecordType recordType);

    List<PatientRecord> findByRecordType(RecordType recordType);

    List<PatientRecord> findByCreatedBy(String createdBy);
}