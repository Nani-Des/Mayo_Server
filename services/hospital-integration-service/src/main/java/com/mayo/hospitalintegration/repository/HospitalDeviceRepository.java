package com.mayo.hospitalintegration.repository;

import com.mayo.common.core.enums.DeviceType;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HospitalDeviceRepository extends JpaRepository<HospitalDevice, UUID> {

    Optional<HospitalDevice> findByDeviceId(String deviceId);

    List<HospitalDevice> findByHospital(Hospital hospital);

    List<HospitalDevice> findByHospitalId(UUID hospitalId);

    List<HospitalDevice> findByDeviceType(DeviceType deviceType);

    List<HospitalDevice> findByStatus(HospitalDevice.DeviceStatus status);

    List<HospitalDevice> findByHospitalAndStatus(Hospital hospital, HospitalDevice.DeviceStatus status);

    @Query("SELECT d FROM HospitalDevice d WHERE d.lastHeartbeat < :threshold")
    List<HospitalDevice> findInactiveDevices(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT d FROM HospitalDevice d WHERE d.ipAddress = :ipAddress")
    Optional<HospitalDevice> findByIpAddress(@Param("ipAddress") String ipAddress);

    boolean existsByDeviceId(String deviceId);

    long countByHospitalAndStatus(Hospital hospital, HospitalDevice.DeviceStatus status);
}