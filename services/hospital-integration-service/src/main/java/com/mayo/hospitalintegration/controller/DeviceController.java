package com.mayo.hospitalintegration.controller;

import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.security.certificate.DeviceCertificateValidator;
import com.mayo.hospitalintegration.dto.HospitalDeviceDto;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.service.DeviceRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Management", description = "Hospital device registration and management endpoints")
public class DeviceController {

    private final DeviceRegistrationService deviceRegistrationService;
    private final DeviceCertificateValidator deviceCertificateValidator;

    @PostMapping("/register")
    @Operation(summary = "Register a new hospital device")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN') or hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<HospitalDeviceDto>> registerDevice(@RequestBody HospitalDeviceDto deviceDto) {
        log.info("Received device registration request: deviceId={}, hospitalId={}, userId={}", 
                deviceDto.getDeviceId(), deviceDto.getHospitalId(), 
                SecurityContextHolder.getContext().getAuthentication() != null ? 
                    SecurityContextHolder.getContext().getAuthentication().getName() : "null");
        log.debug("Request body: {}", deviceDto);
        try {
            // Validate device certificate if provided
            if (deviceDto.getCertificate() != null && !deviceDto.getCertificate().isEmpty()) {
                boolean certValid = deviceCertificateValidator.validateDeviceCertificate(
                    deviceDto.getCertificate(), deviceDto.getDeviceId());
                if (!certValid) {
                    log.warn("Device certificate validation failed for device: {}", deviceDto.getDeviceId());
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Device certificate validation failed"));
                }
                log.info("Device certificate validated successfully for device: {}", deviceDto.getDeviceId());
            }

            HospitalDeviceDto registered = deviceRegistrationService.registerDevice(deviceDto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(registered));
        } catch (IllegalArgumentException e) {
            log.error("Failed to register device {}: {}", deviceDto.getDeviceId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error registering device {}: {}", deviceDto.getDeviceId(), e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Device registration failed: " + e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}")
    @Operation(summary = "Get device by device ID")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN') or hasAuthority('SYNC_READ_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<HospitalDeviceDto>> getDevice(@PathVariable String deviceId) {
        return deviceRegistrationService.getDeviceById(deviceId)
                .map(device -> ResponseEntity.ok(ApiResponse.success(device)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{deviceId}/status")
    @Operation(summary = "Update device status")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN') or hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<HospitalDeviceDto>> updateDeviceStatus(
            @PathVariable String deviceId,
            @RequestParam HospitalDevice.DeviceStatus status) {
        return deviceRegistrationService.updateDeviceStatus(deviceId, status)
                .map(updated -> ResponseEntity.ok(ApiResponse.success(updated)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{deviceId}/heartbeat")
    @Operation(summary = "Update device heartbeat")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN') or hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<Void>> updateHeartbeat(@PathVariable String deviceId) {
        deviceRegistrationService.updateDeviceHeartbeat(deviceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}