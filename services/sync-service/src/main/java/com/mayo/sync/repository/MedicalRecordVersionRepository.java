package com.mayo.sync.repository;

import com.mayo.sync.entity.MedicalRecordVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalRecordVersionRepository extends JpaRepository<MedicalRecordVersion, UUID> {

    // Find latest version for a record
    @Query("SELECT v FROM MedicalRecordVersion v WHERE v.userId = :userId AND v.recordType = :recordType AND v.recordId = :recordId AND v.isLatest = true")
    Optional<MedicalRecordVersion> findLatestVersion(@Param("userId") UUID userId, @Param("recordType") String recordType, @Param("recordId") String recordId);

    // Find all versions for a record ordered by version desc
    @Query("SELECT v FROM MedicalRecordVersion v WHERE v.userId = :userId AND v.recordType = :recordType AND v.recordId = :recordId ORDER BY v.version DESC")
    List<MedicalRecordVersion> findVersionsByRecord(@Param("userId") UUID userId, @Param("recordType") String recordType, @Param("recordId") String recordId);

    // Find versions after a specific version number
    @Query("SELECT v FROM MedicalRecordVersion v WHERE v.userId = :userId AND v.version > :version ORDER BY v.version ASC")
    List<MedicalRecordVersion> findVersionsAfter(@Param("userId") UUID userId, @Param("version") Long version);

    // Find max version for user
    @Query("SELECT MAX(v.version) FROM MedicalRecordVersion v WHERE v.userId = :userId")
    Long findMaxVersionByUserId(@Param("userId") UUID userId);

    // Find versions by device for P2P sync
    @Query("SELECT v FROM MedicalRecordVersion v WHERE v.originatingDeviceId = :deviceId AND v.version > :lastSeenVersion ORDER BY v.version ASC")
    List<MedicalRecordVersion> findVersionsByDeviceAfterVersion(@Param("deviceId") String deviceId, @Param("lastSeenVersion") Long lastSeenVersion);

    // Count versions for integrity checks
    @Query("SELECT COUNT(v) FROM MedicalRecordVersion v WHERE v.userId = :userId AND v.recordType = :recordType AND v.recordId = :recordId")
    Long countVersionsForRecord(@Param("userId") UUID userId, @Param("recordType") String recordType, @Param("recordId") String recordId);

    // Find versions with broken chains (for integrity validation)
    @Query("SELECT v FROM MedicalRecordVersion v WHERE v.userId = :userId AND v.recordType = :recordType AND v.recordId = :recordId AND v.parentVersionId IS NOT NULL AND NOT EXISTS (SELECT p FROM MedicalRecordVersion p WHERE p.id = v.parentVersionId)")
    List<MedicalRecordVersion> findVersionsWithBrokenParentChains(@Param("userId") UUID userId, @Param("recordType") String recordType, @Param("recordId") String recordId);

    // Update isLatest flag (should be done in service with transaction)
    @Query("UPDATE MedicalRecordVersion v SET v.isLatest = false WHERE v.userId = :userId AND v.recordType = :recordType AND v.recordId = :recordId AND v.id != :newLatestId")
    void markPreviousVersionsAsNotLatest(@Param("userId") UUID userId, @Param("recordType") String recordType, @Param("recordId") String recordId, @Param("newLatestId") UUID newLatestId);
}