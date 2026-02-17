package com.mayo.auth.controller;

import com.mayo.auth.dto.*;
import com.mayo.auth.service.CertificateSyncService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for certificate synchronization operations
 */
@RestController
@RequestMapping("/api/v1/auth/certificates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Certificate Sync", description = "Certificate synchronization APIs for offline authentication")
public class CertificateSyncController {

    private final CertificateSyncService certificateSyncService;

    /**
     * Get CA public key and certificate
     */
    @GetMapping("/ca")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get CA certificate and public key")
    public ResponseEntity<ApiResponse<CaCertificateDto>> getCaCertificate(Authentication authentication) {
        log.info("CA certificate request from user: {}", authentication.getName());

        CaCertificateDto caCertificate = certificateSyncService.getCaCertificate();
        return ResponseEntity.ok(ApiResponse.success(caCertificate, "CA certificate retrieved successfully"));
    }

    /**
     * Get list of active device certificates with pagination
     */
    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get paginated list of device certificates")
    public ResponseEntity<ApiResponse<DeviceCertificatesResponse>> getDeviceCertificates(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {

        log.info("Device certificates request from user: {} - page: {}, size: {}", authentication.getName(), page,
                size);

        DeviceCertificatesResponse response = certificateSyncService.getDeviceCertificates(page, size);
        return ResponseEntity.ok(ApiResponse.success(response, "Device certificates retrieved successfully"));
    }

    /**
     * Get specific device certificate
     */
    @GetMapping("/devices/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get specific device certificate")
    public ResponseEntity<ApiResponse<CertificateSyncDto>> getDeviceCertificate(
            @Parameter(description = "Device ID") @PathVariable String deviceId,
            Authentication authentication) {

        log.info("Device certificate request for device: {} from user: {}", deviceId, authentication.getName());

        CertificateSyncDto certificate = certificateSyncService.getDeviceCertificate(deviceId);
        return ResponseEntity.ok(ApiResponse.success(certificate, "Device certificate retrieved successfully"));
    }

    /**
     * Get certificate revocation list
     */
    @GetMapping("/revocations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get certificate revocation list")
    public ResponseEntity<ApiResponse<CertificateRevocationListDto>> getCertificateRevocationList(
            Authentication authentication) {

        log.info("Certificate revocation list request from user: {}", authentication.getName());

        CertificateRevocationListDto crl = certificateSyncService.getCertificateRevocationList();
        return ResponseEntity.ok(ApiResponse.success(crl, "Certificate revocation list retrieved successfully"));
    }

    /**
     * Get certificate updates since timestamp
     */
    @GetMapping("/updates")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get certificate updates since timestamp")
    public ResponseEntity<ApiResponse<CertificateUpdatesDto>> getCertificateUpdates(
            @Parameter(description = "Timestamp since when to get updates (ISO 8601 format)") @RequestParam Instant since,
            Authentication authentication) {

        log.info("Certificate updates request since: {} from user: {}", since, authentication.getName());

        CertificateUpdatesDto updates = certificateSyncService.getCertificateUpdates(since);
        return ResponseEntity.ok(ApiResponse.success(updates, "Certificate updates retrieved successfully"));
    }
}