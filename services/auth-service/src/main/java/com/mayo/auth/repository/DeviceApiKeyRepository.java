package com.mayo.auth.repository;

import com.mayo.auth.entity.DeviceApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for DeviceApiKey entity
 */
@Repository
public interface DeviceApiKeyRepository extends JpaRepository<DeviceApiKey, UUID> {

    /**
     * Find API key by key value
     */
    Optional<DeviceApiKey> findByApiKey(String apiKey);

    /**
     * Find API keys by device ID
     */
    List<DeviceApiKey> findByDeviceId(String deviceId);

    /**
     * Find API keys by hospital ID
     */
    List<DeviceApiKey> findByHospitalId(UUID hospitalId);

    /**
     * Find active API keys by device ID
     */
    @Query("SELECT k FROM DeviceApiKey k WHERE k.deviceId = :deviceId AND k.status = 'ACTIVE' AND (k.expiresAt IS NULL OR k.expiresAt > :now)")
    List<DeviceApiKey> findActiveKeysByDeviceId(@Param("deviceId") String deviceId, @Param("now") LocalDateTime now);

    /**
     * Find expired API keys
     */
    @Query("SELECT k FROM DeviceApiKey k WHERE k.expiresAt IS NOT NULL AND k.expiresAt <= :now AND k.status = 'ACTIVE'")
    List<DeviceApiKey> findExpiredKeys(@Param("now") LocalDateTime now);

    /**
     * Check if API key exists
     */
    boolean existsByApiKey(String apiKey);

    /**
     * Count API keys by hospital ID
     */
    long countByHospitalId(UUID hospitalId);
}