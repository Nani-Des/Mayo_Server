package com.mayo.audit.repository;

import com.mayo.audit.entity.AuditEvent;
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

/**
 * Repository for audit events
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Optional<AuditEvent> findByEventId(String eventId);

    Page<AuditEvent> findByUserId(UUID userId, Pageable pageable);

    Page<AuditEvent> findByPatientId(UUID patientId, Pageable pageable);

    Page<AuditEvent> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.timestamp >= :since")
    long countEventsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.action = :action AND a.timestamp >= :since")
    long countActionsSince(@Param("action") AuditEvent.AuditAction action, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.severity = :severity AND a.timestamp >= :since")
    long countBySeverity(@Param("severity") AuditEvent.Severity severity, @Param("since") LocalDateTime since);

    @Query("SELECT a.resourceType, COUNT(a) FROM AuditEvent a WHERE a.timestamp >= :since GROUP BY a.resourceType")
    List<Object[]> countByResourceTypeSince(@Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT a.userId FROM AuditEvent a WHERE a.timestamp >= :since AND a.userId IS NOT NULL")
    List<UUID> findActiveUsersSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT a.patientId) FROM AuditEvent a WHERE a.timestamp >= :since AND a.patientId IS NOT NULL")
    long countDistinctPatientsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT a.deviceId) FROM AuditEvent a WHERE a.timestamp >= :since AND a.deviceId IS NOT NULL")
    long countDistinctDevicesSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.timestamp >= :since AND a.severity IN ('HIGH', 'CRITICAL')")
    long countViolationsSince(@Param("since") LocalDateTime since);

    @Query("SELECT MAX(a.timestamp) FROM AuditEvent a")
    LocalDateTime findLatestEventTimestamp();

    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:patientId IS NULL OR a.patientId = :patientId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditEvent> findAuditEventsWithFilters(@Param("userId") UUID userId,
                                                @Param("patientId") UUID patientId,
                                                @Param("action") AuditEvent.AuditAction action,
                                                @Param("resourceType") String resourceType,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate,
                                                Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.timestamp < :cutoffDate ORDER BY a.timestamp DESC")
    Page<AuditEvent> findEventsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);

    void deleteByIdIn(List<UUID> ids);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.action = :action AND a.timestamp >= :since")
    long countEventsByActionSince(@Param("action") String action, @Param("since") LocalDateTime since);

    // Versioning-related queries
    @Query("SELECT a FROM AuditEvent a WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId " +
           "ORDER BY a.versionNumber DESC LIMIT 1")
    Optional<AuditEvent> findLatestVersionForResource(@Param("resourceType") String resourceType,
                                                     @Param("resourceId") String resourceId);

    @Query("SELECT a FROM AuditEvent a WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId " +
           "ORDER BY a.versionNumber ASC")
    List<AuditEvent> findVersionChainForResource(@Param("resourceType") String resourceType,
                                                @Param("resourceId") String resourceId);

    @Query("SELECT MAX(a.versionNumber) FROM AuditEvent a WHERE a.resourceType = :resourceType AND a.resourceId = :resourceId")
    Long findMaxVersionNumberForResource(@Param("resourceType") String resourceType,
                                        @Param("resourceId") String resourceId);
}