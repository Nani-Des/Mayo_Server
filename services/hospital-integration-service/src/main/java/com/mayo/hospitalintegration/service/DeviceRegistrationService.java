package com.mayo.hospitalintegration.service;

import com.mayo.common.core.enums.DeviceType;
import com.mayo.hospitalintegration.dto.HospitalDeviceDto;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.repository.HospitalDeviceRepository;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceRegistrationService {

    private final HospitalDeviceRepository deviceRepository;
    private final HospitalRepository hospitalRepository;
    private final ActivityTrackingService activityTrackingService;

    @Transactional
    public HospitalDeviceDto registerDevice(HospitalDeviceDto deviceDto) {
        log.info("Registering device with deviceId={}, hospitalId={}", deviceDto.getDeviceId(), deviceDto.getHospitalId());
        
        // Validate hospital exists
        Hospital hospital = hospitalRepository.findById(deviceDto.getHospitalId())
                .orElseThrow(() -> {
                    log.error("Hospital not found with id: {}", deviceDto.getHospitalId());
                    return new IllegalArgumentException("Hospital not found: " + deviceDto.getHospitalId());
                });

        // Check if device already exists
        if (deviceRepository.existsByDeviceId(deviceDto.getDeviceId())) {
            log.warn("Device already registered with deviceId: {}", deviceDto.getDeviceId());
            throw new IllegalArgumentException("Device already registered: " + deviceDto.getDeviceId());
        }

        HospitalDevice device = mapToEntity(deviceDto);
        device.setHospital(hospital);
        device.setStatus(HospitalDevice.DeviceStatus.REGISTERED);
        device = deviceRepository.save(device);

        // Track activity
        activityTrackingService.trackActivity(
                hospital,
                device,
                ActivityTrackingService.ActivityType.DEVICE_REGISTRATION,
                "Device registered: " + device.getDeviceName(),
                null, null, null, null, null
        );

        log.info("Registered new device: {} for hospital: {}", device.getDeviceId(), hospital.getHospitalId());
        return mapToDto(device);
    }

    @Transactional
    public Optional<HospitalDeviceDto> updateDeviceStatus(String deviceId, HospitalDevice.DeviceStatus status) {
        return deviceRepository.findByDeviceId(deviceId)
                .map(device -> {
                    HospitalDevice.DeviceStatus oldStatus = device.getStatus();
                    device.setStatus(status);
                    device.setLastActiveAt(LocalDateTime.now());
                    device = deviceRepository.save(device);

                    // Track status change
                    activityTrackingService.trackActivity(
                            device.getHospital(),
                            device,
                            ActivityTrackingService.ActivityType.DEVICE_STATUS_CHANGE,
                            "Device status changed from " + oldStatus + " to " + status,
                            null, null, null, null, null
                    );

                    log.info("Updated device {} status to {}", deviceId, status);
                    return mapToDto(device);
                });
    }

    @Transactional
    public void updateDeviceHeartbeat(String deviceId) {
        deviceRepository.findByDeviceId(deviceId)
                .ifPresent(device -> {
                    device.setLastHeartbeat(LocalDateTime.now());
                    device.setLastActiveAt(LocalDateTime.now());
                    deviceRepository.save(device);
                });
    }

    @Transactional(readOnly = true)
    public Optional<HospitalDeviceDto> getDeviceById(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public List<HospitalDeviceDto> getDevicesByHospitalId(UUID hospitalId) {
        return deviceRepository.findByHospitalId(hospitalId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private HospitalDeviceDto mapToDto(HospitalDevice device) {
        HospitalDeviceDto dto = new HospitalDeviceDto();
        dto.setId(device.getId());
        dto.setDeviceId(device.getDeviceId());
        dto.setHospitalId(device.getHospital().getId());
        dto.setHospitalName(device.getHospital().getName());
        dto.setDeviceType(device.getDeviceType());
        dto.setDeviceName(device.getDeviceName());
        dto.setManufacturer(device.getManufacturer());
        dto.setModel(device.getModel());
        dto.setSerialNumber(device.getSerialNumber());
        dto.setFirmwareVersion(device.getFirmwareVersion());
        dto.setIpAddress(device.getIpAddress());
        dto.setMacAddress(device.getMacAddress());
        dto.setConnectionType(device.getConnectionType());
        dto.setStatus(device.getStatus());
        dto.setSupportedProtocols(device.getSupportedProtocols());
        dto.setSupportedDataFormats(device.getSupportedDataFormats());
        dto.setLastHeartbeat(device.getLastHeartbeat());
        dto.setRegisteredAt(device.getRegisteredAt());
        dto.setLastActiveAt(device.getLastActiveAt());
        dto.setCreatedAt(device.getCreatedAt());
        dto.setUpdatedAt(device.getUpdatedAt());
        return dto;
    }

    private HospitalDevice mapToEntity(HospitalDeviceDto dto) {
        HospitalDevice device = new HospitalDevice();
        device.setDeviceId(dto.getDeviceId());
        device.setDeviceType(dto.getDeviceType());
        device.setDeviceName(dto.getDeviceName());
        device.setManufacturer(dto.getManufacturer());
        device.setModel(dto.getModel());
        device.setSerialNumber(dto.getSerialNumber());
        device.setFirmwareVersion(dto.getFirmwareVersion());
        device.setIpAddress(dto.getIpAddress());
        device.setMacAddress(dto.getMacAddress());
        device.setConnectionType(dto.getConnectionType());
        device.setSupportedProtocols(dto.getSupportedProtocols());
        device.setSupportedDataFormats(dto.getSupportedDataFormats());
        return device;
    }
}