package com.mayo.deviceregistry.repository;

import com.mayo.deviceregistry.entity.Device;
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
     * Find devices by hospital ID
     */
    List<Device> findByHospitalId(UUID hospitalId);

    /**
     * Find devices by hospital ID and status
     */
    List<Device> findByHospitalIdAndStatus(UUID hospitalId, DeviceStatus status);

    /**
     * Find device by pairing code
     */
    Optional<Device> findByPairingCode(String pairingCode);

    /**
     * Check if device ID exists
     */
    boolean existsByDeviceId(String deviceId);

    /**
     * Check if pairing code exists
     */
    boolean existsByPairingCode(String pairingCode);

    /**
     * Find devices by status
     */
    List<Device> findByStatus(DeviceStatus status);

    /**
     * Find active devices by hospital ID
     */
    @Query("SELECT d FROM Device d WHERE d.hospitalId = :hospitalId AND d.status = 'ACTIVE'")
    List<Device> findActiveDevicesByHospitalId(@Param("hospitalId") UUID hospitalId);

    /**
     * Count devices by hospital ID
     */
    long countByHospitalId(UUID hospitalId);
}