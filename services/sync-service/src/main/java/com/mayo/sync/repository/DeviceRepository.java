package com.mayo.sync.repository;

import com.mayo.sync.entity.Device;
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

    Optional<Device> findByDeviceId(String deviceId);

    List<Device> findByUserIdAndStatus(UUID userId, Device.DeviceStatus status);

    List<Device> findByUserId(UUID userId);

    @Query("SELECT d FROM Device d WHERE d.userId = :userId AND d.status IN :statuses")
    List<Device> findByUserIdAndStatuses(@Param("userId") UUID userId, @Param("statuses") List<Device.DeviceStatus> statuses);

    boolean existsByDeviceIdAndUserId(String deviceId, UUID userId);
}