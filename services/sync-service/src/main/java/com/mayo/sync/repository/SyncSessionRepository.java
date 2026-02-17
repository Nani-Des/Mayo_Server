package com.mayo.sync.repository;

import com.mayo.sync.entity.SyncSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for SyncSession entity
 */
@Repository
public interface SyncSessionRepository extends JpaRepository<SyncSession, UUID> {

    List<SyncSession> findByUserIdAndDeviceIdOrderBySessionStartDesc(UUID userId, String deviceId);

    Optional<SyncSession> findByUserIdAndDeviceIdAndStatus(UUID userId, String deviceId, SyncSession.SyncStatus status);

    @Query("SELECT s FROM SyncSession s WHERE s.userId = :userId AND s.status = :status ORDER BY s.sessionStart DESC")
    List<SyncSession> findActiveSessionsByUser(@Param("userId") UUID userId, @Param("status") SyncSession.SyncStatus status);
}