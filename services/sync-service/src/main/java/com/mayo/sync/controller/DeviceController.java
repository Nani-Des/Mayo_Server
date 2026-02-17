package com.mayo.sync.controller;

import com.mayo.sync.dto.DevicePairRequest;
import com.mayo.sync.entity.Device;
import com.mayo.sync.repository.DeviceRepository;
import com.mayo.sync.service.DevicePairingService;
import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.core.enums.Permission;
import com.mayo.common.security.certificate.DeviceCertificateValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for device operations
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Management", description = "Device pairing and management APIs")
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final DeviceCertificateValidator deviceCertificateValidator;
    private final DevicePairingService devicePairingService;

    /**
     * Initiate device pairing with challenge-response
     */
    @PostMapping("/pair/initiate")
    @Operation(summary = "Initiate device pairing with challenge-response authentication")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<DevicePairingService.DevicePairingChallenge>> initiatePairing(@Valid @RequestBody DevicePairRequest request) {
        try {
            // Get current user ID (would come from security context in real implementation)
            UUID userId = UUID.randomUUID(); // Placeholder

            DevicePairingService.DevicePairingChallenge challenge = devicePairingService.initiatePairing(request, userId);
            return ResponseEntity.ok(ApiResponse.success(challenge, "Pairing initiated successfully"));
        } catch (Exception e) {
            log.error("Failed to initiate pairing for device {}: {}", request.getDeviceId(), e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to initiate pairing: " + e.getMessage()));
        }
    }

    /**
     * Complete device pairing with signed challenge
     */
    @PostMapping("/pair/complete")
    @Operation(summary = "Complete device pairing with signed challenge")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<Device>> completePairing(
            @RequestParam String challengeId,
            @RequestParam String signedChallenge) {
        try {
            // Get current user ID (would come from security context in real implementation)
            UUID userId = UUID.randomUUID(); // Placeholder

            Device device = devicePairingService.completePairing(challengeId, signedChallenge, userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(device, "Device paired successfully"));
        } catch (Exception e) {
            log.error("Failed to complete pairing for challenge {}: {}", challengeId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to complete pairing: " + e.getMessage()));
        }
    }

    /**
     * Pair device via Bluetooth
     */
    @PostMapping("/pair/bluetooth")
    @Operation(summary = "Pair device via Bluetooth")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<Device>> pairViaBluetooth(
            @RequestParam String deviceId,
            @RequestParam String bluetoothAddress) {
        try {
            // Get current user ID (would come from security context in real implementation)
            UUID userId = UUID.randomUUID(); // Placeholder

            Device device = devicePairingService.pairViaBluetooth(deviceId, bluetoothAddress, userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(device, "Device paired via Bluetooth successfully"));
        } catch (Exception e) {
            log.error("Failed to pair device {} via Bluetooth: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Bluetooth pairing failed: " + e.getMessage()));
        }
    }

    /**
     * Pair device via WiFi with QR code
     */
    @PostMapping("/pair/wifi")
    @Operation(summary = "Pair device via WiFi with QR code")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<Device>> pairViaWifi(
            @RequestParam String deviceId,
            @RequestParam String qrCodeData) {
        try {
            // Get current user ID (would come from security context in real implementation)
            UUID userId = UUID.randomUUID(); // Placeholder

            Device device = devicePairingService.pairViaWifi(deviceId, qrCodeData, userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(device, "Device paired via WiFi successfully"));
        } catch (Exception e) {
            log.error("Failed to pair device {} via WiFi: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("WiFi pairing failed: " + e.getMessage()));
        }
    }

    /**
     * Pair device via USB
     */
    @PostMapping("/pair/usb")
    @Operation(summary = "Pair device via USB")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<Device>> pairViaUsb(@RequestParam String deviceId) {
        try {
            // Get current user ID (would come from security context in real implementation)
            UUID userId = UUID.randomUUID(); // Placeholder

            Device device = devicePairingService.pairViaUsb(deviceId, userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(device, "Device paired via USB successfully"));
        } catch (Exception e) {
            log.error("Failed to pair device {} via USB: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("USB pairing failed: " + e.getMessage()));
        }
    }

    /**
     * Unpair device and revoke authentication
     */
    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Unpair device and revoke authentication")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<Void>> unpairDevice(@PathVariable String deviceId) {
        try {
            devicePairingService.unpairDevice(deviceId);
            return ResponseEntity.ok(ApiResponse.success(null, "Device unpaired successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Device not found: {}", deviceId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to unpair device {}: {}", deviceId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Failed to unpair device: " + e.getMessage()));
        }
    }

    /**
     * List paired devices
     */
    @GetMapping
    @Operation(summary = "List paired devices")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<List<Device>>> listDevices() {
        List<Device> devices = deviceRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(devices, "Devices retrieved successfully"));
    }
}