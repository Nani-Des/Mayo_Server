package com.mayo.deviceregistry.service;

import com.mayo.deviceregistry.dto.*;
import com.mayo.deviceregistry.entity.Device;
import com.mayo.deviceregistry.repository.DeviceRepository;
import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.common.core.enums.DeviceType;
import com.mayo.common.core.exception.ResourceNotFoundException;
import com.mayo.events.topics.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for device registration and pairing operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final com.mayo.common.security.jwt.JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new device
     */
    @Transactional
    public DeviceDto registerDevice(DeviceRegistrationRequest request, String token) {
        log.info("Registering device: {} for hospital: {}", request.getDeviceId(), request.getHospitalId());

        // Check for Hospital Admin restriction
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            UUID adminHospitalId = jwtTokenProvider.getHospitalIdFromToken(jwt);
            
            if (adminHospitalId != null) {
                if (!adminHospitalId.equals(request.getHospitalId())) {
                    log.error("Hospital Admin {} attempted to register device for different hospital: {}", adminHospitalId, request.getHospitalId());
                    throw new com.mayo.common.core.exception.UnauthorizedException("You can only register devices for your own hospital");
                }
            }
        }

        // Check if device ID already exists
        if (deviceRepository.existsByDeviceId(request.getDeviceId())) {
            throw new IllegalArgumentException("Device ID already exists");
        }

        // Generate pairing code
        String pairingCode = generatePairingCode();

        // Create new device
        Device device = Device.builder()
                .deviceId(request.getDeviceId())
                .deviceType(request.getDeviceType())
                .hospitalId(request.getHospitalId())
                .status(DeviceStatus.PENDING)
                .protocol(request.getProtocol())
                .pairingCode(pairingCode)
                .build();

        Device savedDevice = deviceRepository.save(device);
        log.info("Device registered successfully with ID: {}", savedDevice.getId());

        // Publish device registered event
        publishDeviceEvent(savedDevice, "DEVICE_REGISTERED");

        return convertToDto(savedDevice);
    }

    /**
     * Register a new device by admin (directly activates the device)
     */
    @Transactional
    public DeviceDto registerDeviceByAdmin(AdminDeviceRegistrationRequest request, String token) {
        log.info("Admin registering device: {} for hospital: {}", request.getDeviceId(), request.getHospitalId());

        // Check for Hospital Admin restriction
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            UUID adminHospitalId = jwtTokenProvider.getHospitalIdFromToken(jwt);
            
            if (adminHospitalId != null) {
                // This is a Hospital Admin
                if (!adminHospitalId.toString().equals(request.getHospitalId())) {
                    log.error("Hospital Admin {} attempted to register device for different hospital: {}", adminHospitalId, request.getHospitalId());
                    throw new com.mayo.common.core.exception.UnauthorizedException("You can only register devices for your own hospital");
                }
                log.info("Hospital Admin validated for hospital: {}", adminHospitalId);
            } else {
                // This is a Super Admin (no hospitalId in token)
                log.info("Super Admin validated (no hospital restriction)");
            }
        }

        // Check if device ID already exists
        if (deviceRepository.existsByDeviceId(request.getDeviceId())) {
            throw new IllegalArgumentException("Device ID already exists");
        }

        UUID hospitalUuid = UUID.fromString(request.getHospitalId());
        UUID adminUserUuid = UUID.fromString(request.getAdminUserId());

        // Create new device directly in ACTIVE status
        Device device = Device.builder()
                .deviceId(request.getDeviceId())
                .deviceType(DeviceType.DESKTOP) // Default for desktop app
                .hospitalId(hospitalUuid)
                .status(DeviceStatus.ACTIVE)
                .protocol("local") // Default protocol for direct registration
                .deviceName(request.getDeviceName())
                .registeredBy(adminUserUuid)
                .build();

        Device savedDevice = deviceRepository.save(device);
        log.info("Device registered by admin successfully: {} by admin: {}", savedDevice.getDeviceId(), adminUserUuid);

        // Publish device registered event
        publishDeviceEvent(savedDevice, "DEVICE_REGISTERED_BY_ADMIN");

        return convertToDto(savedDevice);
    }

    /**
     * Check if a device is registered (public endpoint)
     */
    public DeviceCheckResponse checkDeviceRegistration(String deviceId) {
        log.debug("Checking device registration for: {}", deviceId);

        return deviceRepository.findByDeviceId(deviceId)
                .map(device -> DeviceCheckResponse.builder()
                        .registered(true)
                        .deviceId(device.getDeviceId())
                        .hospitalId(device.getHospitalId().toString())
                        .deviceName(device.getDeviceName())
                        .status(device.getStatus().name())
                        .registeredAt(device.getRegisteredAt() != null 
                            ? device.getRegisteredAt().format(DateTimeFormatter.ISO_DATE_TIME) 
                            : null)
                        .build())
                .orElse(DeviceCheckResponse.builder()
                        .registered(false)
                        .build());
    }

    /**
     * Pair device using pairing code
     */
    @Transactional
    public DeviceDto pairDevice(DevicePairingRequest request) {
        log.info("Pairing device with code: {}", request.getPairingCode());

        Device device = deviceRepository.findByPairingCode(request.getPairingCode())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid pairing code"));

        if (device.getStatus() != DeviceStatus.PENDING) {
            throw new IllegalArgumentException("Device is not in pending state");
        }

        device.setStatus(DeviceStatus.ACTIVE);
        device.setPairingCode(null); // Clear pairing code after successful pairing
        Device pairedDevice = deviceRepository.save(device);

        log.info("Device paired successfully: {}", pairedDevice.getDeviceId());

        // Publish device paired event
        publishDeviceEvent(pairedDevice, "DEVICE_PAIRED");

        return convertToDto(pairedDevice);
    }

    /**
     * Update device status
     */
    @Transactional
    public DeviceDto updateDeviceStatus(UUID deviceId, DeviceStatus status, String token) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        // Enforce Scope
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            UUID adminHospitalId = jwtTokenProvider.getHospitalIdFromToken(jwt);
            
            if (adminHospitalId != null) {
                if (!device.getHospitalId().equals(adminHospitalId)) {
                    throw new com.mayo.common.core.exception.UnauthorizedException("Cannot update device status for another hospital");
                }
            }
        }

        device.setStatus(status);
        Device updatedDevice = deviceRepository.save(device);

        log.info("Device status updated: {} to {}", deviceId, status);

        return convertToDto(updatedDevice);
    }

    /**
     * Send heartbeat for device
     */
    @Transactional
    public void sendHeartbeat(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        device.setLastHeartbeat(LocalDateTime.now());
        deviceRepository.save(device);

        log.debug("Heartbeat received for device: {}", deviceId);
    }

    /**
     * Get device by ID
     */
    public DeviceDto getDeviceById(UUID deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
        return convertToDto(device);
    }

    /**
     * Get devices by hospital ID
     */
    public List<DeviceDto> getDevicesByHospitalId(UUID hospitalId, String token) {
        // Enforce Scope
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            UUID adminHospitalId = jwtTokenProvider.getHospitalIdFromToken(jwt);
            
            if (adminHospitalId != null) {
                if (!hospitalId.equals(adminHospitalId)) {
                    throw new com.mayo.common.core.exception.UnauthorizedException("Cannot view devices for another hospital");
                }
            }
        }

        List<Device> devices = deviceRepository.findByHospitalId(hospitalId);
        return devices.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all devices (Filtered by Hospital Admin scope)
     */
    public List<DeviceDto> getAllDevices(String token) {
        UUID adminHospitalId = null;
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            adminHospitalId = jwtTokenProvider.getHospitalIdFromToken(jwt);
        }

        List<Device> devices;
        if (adminHospitalId != null) {
            // Hospital Admin - only see their own devices
            devices = deviceRepository.findByHospitalId(adminHospitalId);
        } else {
            // Super Admin - see all
            devices = deviceRepository.findAll();
        }

        return devices.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get device by device ID string
     */
    public DeviceDto getDeviceByDeviceId(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
        return convertToDto(device);
    }

    /**
     * Get all active device IDs
     */
    public List<String> getActiveDeviceIds() {
        List<Device> activeDevices = deviceRepository.findByStatus(DeviceStatus.ACTIVE);
        return activeDevices.stream()
                .map(Device::getDeviceId)
                .collect(Collectors.toList());
    }

    /**
     * Generate unique pairing code
     */
    private String generatePairingCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (deviceRepository.existsByPairingCode(code));
        return code;
    }

    /**
     * Publish device event to Kafka
     */
    private void publishDeviceEvent(Device device, String eventType) {
        try {
            var eventData = new java.util.HashMap<String, Object>();
            eventData.put("eventId", UUID.randomUUID().toString());
            eventData.put("eventType", eventType);
            eventData.put("deviceId", device.getDeviceId());
            eventData.put("hospitalId", device.getHospitalId().toString());
            eventData.put("deviceType", device.getDeviceType().name());
            eventData.put("protocol", device.getProtocol());
            eventData.put("status", device.getStatus().name());
            eventData.put("timestamp", LocalDateTime.now().toString());

            String eventJson = objectMapper.writeValueAsString(eventData);
            kafkaTemplate.send(Topics.DEVICE_EVENTS, device.getDeviceId(), eventJson);
            
            log.debug("Published device event: {} for device: {}", eventType, device.getDeviceId());
        } catch (Exception e) {
            log.error("Failed to publish device event: {} for device: {}", eventType, device.getDeviceId(), e);
        }
    }

    /**
     * Convert Device entity to DeviceDto
     */
    private DeviceDto convertToDto(Device device) {
        return DeviceDto.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .deviceType(device.getDeviceType())
                .hospitalId(device.getHospitalId())
                .status(device.getStatus())
                .protocol(device.getProtocol())
                .deviceName(device.getDeviceName())
                .lastHeartbeat(device.getLastHeartbeat())
                .pairingCode(device.getPairingCode())
                .registeredBy(device.getRegisteredBy())
                .registeredAt(device.getRegisteredAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
    /**
     * Delete device by ID (Enforce Hospital Scope)
     */
    @Transactional
    public void deleteDevice(UUID deviceId, String token) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        // Enforce Scope
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            UUID adminHospitalId = jwtTokenProvider.getHospitalIdFromToken(jwt);
            
            if (adminHospitalId != null) {
                if (!device.getHospitalId().equals(adminHospitalId)) {
                    throw new com.mayo.common.core.exception.UnauthorizedException("Cannot delete device from another hospital");
                }
            }
        }

        deviceRepository.deleteById(deviceId);
        log.info("Device deleted successfully: {}", deviceId);
    }
}
