package com.mayo.deviceregistry.controller;

import com.mayo.deviceregistry.dto.*;
import com.mayo.deviceregistry.service.DeviceService;
import com.mayo.common.core.enums.DeviceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for device operations
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Device Management", description = "APIs for managing hospital device registration and pairing")
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * Register a new device (standard registration with pairing)
     */
    public ResponseEntity<DeviceDto> registerDevice(
            @Valid @RequestBody DeviceRegistrationRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        DeviceDto device = deviceService.registerDevice(request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(device);
    }

    /**
     * Register a new device by admin (direct activation)
     * This endpoint is for admin-only device registration
     */
    @PostMapping("/admin/register")
    @Operation(summary = "Register a new device by admin (admin only)")
    public ResponseEntity<DeviceDto> registerDeviceByAdmin(
            @Valid @RequestBody AdminDeviceRegistrationRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        
        UUID adminHospitalId = null;
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            // We need JwtTokenProvider here, but it's in common-security.
            // Ideally validation should be in Service, but Controller has easy access to Header/Principal.
            // Let's pass the token to service? Or better, inject token provider?
            // Since JwtTokenProvider is a component, we can inject it.
        }
        // Actually, better to handle this in Service if we pass the token or extracted ID.
        // Let's rely on the service to extract it if we pass the token, or better, 
        // if we change the signature of service method.
        // For now, let's keep controller simple and handle logic in service if possible, 
        // OR inject JwtTokenProvider here.
        
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.registerDeviceByAdmin(request, token));
    }

    /**
     * Check if a device is registered (public endpoint - no auth required)
     * URL: /devices/public/check/{deviceId}
     * Gateway: /api/devices/public/check/{deviceId}
     */
    @GetMapping("/public/check/{deviceId}")
    @Operation(summary = "Check if device is registered (public endpoint)")
    public ResponseEntity<DeviceCheckResponse> checkDeviceRegistration(@PathVariable String deviceId) {
        // This endpoint MUST be public in SecurityConfig
        DeviceCheckResponse response = deviceService.checkDeviceRegistration(deviceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Pair device using pairing code
     */
    @PostMapping("/pair")
    @Operation(summary = "Pair device using pairing code")
    public ResponseEntity<DeviceDto> pairDevice(@Valid @RequestBody DevicePairingRequest request) {
        DeviceDto device = deviceService.pairDevice(request);
        return ResponseEntity.ok(device);
    }

    /**
     * Update device status
     */
    public ResponseEntity<DeviceDto> updateDeviceStatus(
            @PathVariable UUID id, 
            @RequestParam DeviceStatus status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        DeviceDto device = deviceService.updateDeviceStatus(id, status, token);
        return ResponseEntity.ok(device);
    }

    /**
     * Send heartbeat for device
     */
    @PostMapping("/{deviceId}/heartbeat")
    @Operation(summary = "Send heartbeat for device")
    public ResponseEntity<Void> sendHeartbeat(@PathVariable String deviceId) {
        deviceService.sendHeartbeat(deviceId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get all devices (Scoped by Hospital Admin)
     */
    @GetMapping
    @Operation(summary = "Get all devices")
    public ResponseEntity<List<DeviceDto>> getAllDevices(@RequestHeader(value = "Authorization", required = false) String token) {
        List<DeviceDto> devices = deviceService.getAllDevices(token);
        return ResponseEntity.ok(devices);
    }

    /**
     * Get device by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get device by ID")
    public ResponseEntity<DeviceDto> getDeviceById(@PathVariable UUID id) {
        DeviceDto device = deviceService.getDeviceById(id);
        return ResponseEntity.ok(device);
    }

    /**
     * Get devices by hospital ID
     */
    public ResponseEntity<List<DeviceDto>> getDevicesByHospitalId(
            @PathVariable UUID hospitalId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        List<DeviceDto> devices = deviceService.getDevicesByHospitalId(hospitalId, token);
        return ResponseEntity.ok(devices);
    }

    /**
     * Get device by device ID string (for authentication service)
     */
    @GetMapping("/by-device-id/{deviceId}")
    @Operation(summary = "Get device by device ID string")
    public ResponseEntity<DeviceDto> getDeviceByDeviceId(@PathVariable String deviceId) {
        DeviceDto device = deviceService.getDeviceByDeviceId(deviceId);
        return ResponseEntity.ok(device);
    }

    /**
     * Get all active device IDs (for authentication service)
     */
    @GetMapping("/active")
    @Operation(summary = "Get all active device IDs")
    public ResponseEntity<List<String>> getActiveDeviceIds() {
        List<String> deviceIds = deviceService.getActiveDeviceIds();
        return ResponseEntity.ok(deviceIds);
    }
    /**
     * Delete device
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete device")
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID id, @RequestHeader(value = "Authorization", required = false) String token) {
        deviceService.deleteDevice(id, token);
        return ResponseEntity.noContent().build();
    }
}
