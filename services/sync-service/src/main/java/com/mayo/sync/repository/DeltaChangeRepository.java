package com.mayo.sync.repository;

import com.mayo.sync.entity.DeltaChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for DeltaChange entity
 */
@Repository
public interface DeltaChangeRepository extends JpaRepository<DeltaChange, UUID> {

    List<DeltaChange> findByUserIdAndVersionGreaterThanOrderByVersionAsc(UUID userId, Long version);

    List<DeltaChange> findByUserIdAndRecordTypeAndVersionGreaterThanOrderByVersionAsc(UUID userId, String recordType, Long version);

    @Query("SELECT MAX(d.version) FROM DeltaChange d WHERE d.userId = :userId")
    Long findMaxVersionByUserId(@Param("userId") UUID userId);

    @Query("SELECT d FROM DeltaChange d WHERE d.userId = :userId AND d.version > :lastVersion ORDER BY d.version ASC")
    List<DeltaChange> findChangesAfterVersion(@Param("userId") UUID userId, @Param("lastVersion") Long lastVersion);

    @Query("SELECT d FROM DeltaChange d WHERE d.userId = :userId AND d.timestamp > :since ORDER BY d.timestamp ASC")
    List<DeltaChange> findChangesSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    List<DeltaChange> findByRecordIdAndRecordTypeOrderByVersionDesc(String recordId, String recordType);

    // CRDT-related queries
    List<DeltaChange> findByDocumentIdAndIsCrdtEnabledTrueOrderByVersionAsc(String documentId);

    @Query("SELECT d FROM DeltaChange d WHERE d.documentId = :documentId AND d.isCrdtEnabled = true AND d.version > :lastVersion ORDER BY d.version ASC")
    List<DeltaChange> findCrdtChangesAfterVersion(@Param("documentId") String documentId, @Param("lastVersion") Long lastVersion);

    @Query("SELECT MAX(d.version) FROM DeltaChange d WHERE d.documentId = :documentId AND d.isCrdtEnabled = true")
    Long findMaxCrdtVersionByDocumentId(@Param("documentId") String documentId);

    List<DeltaChange> findByIsCrdtEnabledTrueAndUserIdOrderByVersionAsc(UUID userId);
}