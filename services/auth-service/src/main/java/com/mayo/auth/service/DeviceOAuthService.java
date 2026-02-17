package com.mayo.auth.service;

import com.mayo.auth.dto.DeviceAuthorizationResponse;
import com.mayo.auth.dto.DeviceTokenRequest;
import com.mayo.auth.dto.LoginResponse;
import com.mayo.auth.entity.DeviceAuthorizationCode;
import com.mayo.auth.repository.DeviceAuthorizationCodeRepository;
import com.mayo.common.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for OAuth 2.0 Device Flow authentication for hospital devices
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceOAuthService {

    private final DeviceAuthorizationCodeRepository authorizationCodeRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.oauth.device.verification-uri:http://localhost:8080/device/verify}")
    private String verificationUri;

    @Value("${auth.oauth.device.code-expiry-minutes:15}")
    private int codeExpiryMinutes;

    @Value("${auth.oauth.device.polling-interval-seconds:5}")
    private int pollingIntervalSeconds;

    /**
     * Initiate device authorization (Step 1: Device requests codes)
     */
    @Transactional
    public DeviceAuthorizationResponse requestDeviceAuthorization(String deviceId, String hospitalId) {
        log.info("Requesting device authorization for device: {} in hospital: {}", deviceId, hospitalId);

        // Generate unique device code and user code
        String deviceCode = generateDeviceCode();
        String userCode = generateUserCode();

        // Create authorization code entity
        DeviceAuthorizationCode authCode = DeviceAuthorizationCode.builder()
                .deviceCode(deviceCode)
                .userCode(userCode)
                .deviceId(deviceId)
                .hospitalId(java.util.UUID.fromString(hospitalId))
                .status(DeviceAuthorizationCode.Status.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(codeExpiryMinutes))
                .intervalSeconds(pollingIntervalSeconds)
                .build();

        authorizationCodeRepository.save(authCode);

        log.info("Device authorization codes generated for device: {}", deviceId);

        return DeviceAuthorizationResponse.builder()
                .deviceCode(deviceCode)
                .userCode(userCode)
                .verificationUri(verificationUri)
                .verificationUriComplete(verificationUri + "?user_code=" + userCode)
                .expiresIn(codeExpiryMinutes * 60)
                .interval(pollingIntervalSeconds)
                .build();
    }

    /**
     * Approve device authorization (Step 2: User approves via web interface)
     */
    @Transactional
    public void approveDeviceAuthorization(String userCode, String approvedByUserId) {
        log.info("Approving device authorization for user code: {}", userCode);

        DeviceAuthorizationCode authCode = authorizationCodeRepository.findByUserCode(userCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user code"));

        if (!authCode.isPending()) {
            throw new IllegalArgumentException("Authorization code is not in pending state");
        }

        authCode.setStatus(DeviceAuthorizationCode.Status.APPROVED);
        authorizationCodeRepository.save(authCode);

        log.info("Device authorization approved for device: {}", authCode.getDeviceId());
    }

    /**
     * Deny device authorization
     */
    @Transactional
    public void denyDeviceAuthorization(String userCode) {
        log.info("Denying device authorization for user code: {}", userCode);

        DeviceAuthorizationCode authCode = authorizationCodeRepository.findByUserCode(userCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user code"));

        authCode.setStatus(DeviceAuthorizationCode.Status.DENIED);
        authorizationCodeRepository.save(authCode);

        log.info("Device authorization denied for device: {}", authCode.getDeviceId());
    }

    /**
     * Poll for token (Step 3: Device polls for token)
     */
    public LoginResponse pollForToken(DeviceTokenRequest request) {
        log.debug("Polling for token with device code: {}", maskDeviceCode(request.getDeviceCode()));

        Optional<DeviceAuthorizationCode> authCodeOpt = authorizationCodeRepository
                .findByDeviceCode(request.getDeviceCode());

        if (authCodeOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid device code");
        }

        DeviceAuthorizationCode authCode = authCodeOpt.get();

        // Update last polled time
        authCode.setLastPolledAt(LocalDateTime.now());
        authorizationCodeRepository.save(authCode);

        if (authCode.isExpired()) {
            authCode.setStatus(DeviceAuthorizationCode.Status.EXPIRED);
            authorizationCodeRepository.save(authCode);
            throw new IllegalArgumentException("Device code expired");
        }

        if (authCode.getStatus() == DeviceAuthorizationCode.Status.DENIED) {
            throw new IllegalArgumentException("Authorization denied by user");
        }

        if (authCode.getStatus() != DeviceAuthorizationCode.Status.APPROVED) {
            // Still pending, return slow down response
            throw new IllegalArgumentException("authorization_pending");
        }

        // Generate tokens for the approved device
        String accessToken = jwtTokenProvider.generateDeviceAccessToken(
                java.util.UUID.randomUUID(), // Anonymous user for device
                "device-" + authCode.getDeviceId(),
                com.mayo.common.core.enums.UserType.HOSPITAL_ADMIN, // Device gets hospital admin permissions
                java.util.Set.of(), // No specific permissions for now
                authCode.getDeviceId(),
                authCode.getHospitalId());

        String refreshToken = jwtTokenProvider.generateDeviceRefreshToken(
                java.util.UUID.randomUUID(),
                authCode.getDeviceId());

        // Clean up the authorization code
        authorizationCodeRepository.delete(authCode);

        log.info("Token issued for device: {}", authCode.getDeviceId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900L) // 15 minutes
                .build();
    }

    /**
     * Get authorization code status for verification page
     */
    public DeviceAuthorizationCode getAuthorizationStatus(String userCode) {
        return authorizationCodeRepository.findByUserCode(userCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user code"));
    }

    /**
     * Scheduled task to clean up expired authorization codes
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void cleanupExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        var expiredCodes = authorizationCodeRepository.findExpiredCodes(now);

        for (DeviceAuthorizationCode code : expiredCodes) {
            code.setStatus(DeviceAuthorizationCode.Status.EXPIRED);
            authorizationCodeRepository.save(code);
        }

        if (!expiredCodes.isEmpty()) {
            log.info("Expired {} device authorization codes", expiredCodes.size());
        }
    }

    /**
     * Generate secure device code
     */
    private String generateDeviceCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generate user-friendly user code (8 characters)
     */
    private String generateUserCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        String userCode = code.toString();

        // Ensure uniqueness
        while (authorizationCodeRepository.existsByUserCode(userCode)) {
            userCode = generateUserCode();
        }

        return userCode;
    }

    /**
     * Mask device code for logging
     */
    private String maskDeviceCode(String deviceCode) {
        if (deviceCode == null || deviceCode.length() < 8) {
            return "****";
        }
        return deviceCode.substring(0, 4) + "****" + deviceCode.substring(deviceCode.length() - 4);
    }
}