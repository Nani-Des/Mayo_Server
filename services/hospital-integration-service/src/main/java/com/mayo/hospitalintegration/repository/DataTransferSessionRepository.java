package com.mayo.hospitalintegration.repository;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataTransferSessionRepository extends JpaRepository<DataTransferSession, UUID> {

    Optional<DataTransferSession> findBySessionId(String sessionId);

    List<DataTransferSession> findByHospital(Hospital hospital);

    List<DataTransferSession> findByDevice(HospitalDevice device);

    List<DataTransferSession> findByStatus(DataTransferSession.TransferStatus status);

    Page<DataTransferSession> findByHospitalAndStatusOrderByCreatedAtDesc(Hospital hospital,
            DataTransferSession.TransferStatus status, Pageable pageable);

    List<DataTransferSession> findByHospitalAndCreatedAtBetween(Hospital hospital, LocalDateTime startDate,
            LocalDateTime endDate);

    @Query("SELECT s FROM DataTransferSession s WHERE s.status IN :statuses ORDER BY s.createdAt DESC")
    List<DataTransferSession> findActiveSessions(@Param("statuses") List<DataTransferSession.TransferStatus> statuses);

    @Query("SELECT s FROM DataTransferSession s WHERE s.hospital = :hospital AND s.status IN :statuses")
    List<DataTransferSession> findActiveSessionsByHospital(@Param("hospital") Hospital hospital,
            @Param("statuses") List<DataTransferSession.TransferStatus> statuses);

    @Query("SELECT COUNT(s) FROM DataTransferSession s WHERE s.hospital = :hospital AND s.status = :status AND s.createdAt >= :since")
    long countSessionsByHospitalAndStatusSince(@Param("hospital") Hospital hospital,
            @Param("status") DataTransferSession.TransferStatus status, @Param("since") LocalDateTime since);

    @Query("SELECT SUM(s.dataSizeBytes) FROM DataTransferSession s WHERE s.hospital = :hospital AND s.status = 'COMPLETED' AND s.completedAt >= :since")
    Long getTotalDataTransferredByHospitalSince(@Param("hospital") Hospital hospital,
            @Param("since") LocalDateTime since);

    List<DataTransferSession> findByHospital_IdOrderByCreatedAtDesc(UUID hospitalId);

    boolean existsBySessionId(String sessionId);
}