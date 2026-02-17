package com.mayo.auth.controller;

import com.mayo.auth.dto.DeviceDto;
import com.mayo.auth.dto.DeviceRegistrationRequest;
import com.mayo.auth.service.AuthService;
import com.mayo.auth.entity.User;
import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.core.enums.DeviceStatus;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for device management operations
 */
@RestController
@RequestMapping("/api/v1/auth/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Management", description = "Device registration and management APIs")
public class DeviceController {

    private final AuthService authService;

    /**
     * Register a new device for the authenticated user
     */
    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Register a new device")
    public ResponseEntity<ApiResponse<DeviceDto>> registerDevice(
            @Valid @RequestBody DeviceRegistrationRequest request,
            Authentication authentication) {

        // Extract user ID from authentication (assuming it's stored in principal)
        UUID userId = UUID.fromString(authentication.getName());

        DeviceDto device = authService.registerDevice(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(device, "Device registered successfully"));
    }

    /**
     * Get device by ID
     */
    @GetMapping("/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get device by ID")
    public ResponseEntity<ApiResponse<DeviceDto>> getDevice(
            @Parameter(description = "Device ID") @PathVariable UUID deviceId) {

        DeviceDto device = authService.getDeviceById(deviceId);
        return ResponseEntity.ok(ApiResponse.success(device, "Device retrieved successfully"));
    }

    /**
     * Get device by device ID string
     */
    @GetMapping("/by-device-id/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get device by device ID string")
    public ResponseEntity<ApiResponse<DeviceDto>> getDeviceByDeviceId(
            @Parameter(description = "Device ID string") @PathVariable String deviceId) {

        DeviceDto device = authService.getDeviceByDeviceId(deviceId);
        return ResponseEntity.ok(ApiResponse.success(device, "Device retrieved successfully"));
    }

    /**
     * Get all devices (Admin only)
     */
    public ResponseEntity<ApiResponse<List<DeviceDto>>> getAllDevices(Authentication authentication) {
        
        UUID adminHospitalId = null;
        boolean isSuperAdmin = false;
        
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            adminHospitalId = user.getHospitalId();
            isSuperAdmin = user.isSuperAdmin();
        } else if (authentication != null) {
            // Fallback for token-based auth without full principal
            // In a real scenario, should extract hospitalId from token claims
            // But let's check if the Principal is a User first
            log.warn("Principal is not a User object: {}", authentication.getPrincipal());
        }

        List<DeviceDto> devices = authService.getAllDevices(adminHospitalId, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success(devices, "All devices retrieved successfully"));
    }

    /**
     * Get all devices for the authenticated user
     */
    @GetMapping("/my-devices")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all devices for current user")
    public ResponseEntity<ApiResponse<List<DeviceDto>>> getMyDevices(Authentication authentication) {
        
        System.out.println("DEBUG: getMyDevices called");
        System.out.println("DEBUG: Authentication: " + authentication);
        if (authentication != null) {
            System.out.println("DEBUG: Name: " + authentication.getName());
            System.out.println("DEBUG: Principal: " + authentication.getPrincipal());
            System.out.println("DEBUG: Authorities: " + authentication.getAuthorities());
        }

        UUID userId = UUID.fromString(authentication.getName());
        List<DeviceDto> devices = authService.getDevicesByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(devices, "Devices retrieved successfully"));
    }

    /**
     * Update device status
     */
    @PatchMapping("/{deviceId}/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update device status")
    public ResponseEntity<ApiResponse<DeviceDto>> updateDeviceStatus(
            @Parameter(description = "Device ID") @PathVariable UUID deviceId,
            @Parameter(description = "New device status") @RequestParam DeviceStatus status) {

        DeviceDto device = authService.updateDeviceStatus(deviceId, status);
        return ResponseEntity.ok(ApiResponse.success(device, "Device status updated successfully"));
    }
}