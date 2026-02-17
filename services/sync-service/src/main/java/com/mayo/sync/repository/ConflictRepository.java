package com.mayo.sync.repository;

import com.mayo.sync.entity.Conflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Conflict entity
 */
@Repository
public interface ConflictRepository extends JpaRepository<Conflict, UUID> {

    List<Conflict> findByUserIdAndResolutionStatus(UUID userId, Conflict.ResolutionStatus resolutionStatus);

    List<Conflict> findByUserId(UUID userId);

    @Query("SELECT c FROM Conflict c WHERE c.userId = :userId AND c.resolutionStatus = 'PENDING' ORDER BY c.createdAt DESC")
    List<Conflict> findPendingConflictsByUser(@Param("userId") UUID userId);

    @Query("SELECT c FROM Conflict c WHERE c.recordId = :recordId AND c.resolutionStatus = 'PENDING'")
    List<Conflict> findPendingConflictsByRecordId(@Param("recordId") String recordId);

    long countByUserIdAndResolutionStatus(UUID userId, Conflict.ResolutionStatus resolutionStatus);
}