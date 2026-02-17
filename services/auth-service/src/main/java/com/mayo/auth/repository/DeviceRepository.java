package com.mayo.auth.repository;

import com.mayo.auth.entity.Device;
import com.mayo.common.core.enums.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Device entity
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    /**
     * Find device by device ID
     */
    Optional<Device> findByDeviceId(String deviceId);

    /**
     * Find devices by user ID
     */
    List<Device> findByUserId(UUID userId);

    /**
     * Find devices by user ID and status
     */
    List<Device> findByUserIdAndStatus(UUID userId, DeviceStatus status);

    /**
     * Check if device ID exists
     */
    boolean existsByDeviceId(String deviceId);

    /**
     * Find active devices by user ID
     */
    @Query("SELECT d FROM Device d WHERE d.userId = :userId AND d.status = 'ACTIVE'")
    List<Device> findActiveDevicesByUserId(@Param("userId") UUID userId);

    /**
     * Count devices by user ID
     */
    long countByUserId(UUID userId);

    /**
     * Find all devices by hospital ID
     */
    List<Device> findByHospitalId(UUID hospitalId);
}