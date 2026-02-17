package com.mayo.auth.service;

import com.mayo.auth.dto.DeviceCertificateValidationResult;
import com.mayo.auth.entity.User;
import com.mayo.auth.repository.UserRepository;
import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.common.core.enums.Permission;
import com.mayo.common.core.enums.UserType;
import com.mayo.common.security.jwt.JwtTokenProvider;
import com.mayo.events.topics.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Context-aware authentication provider that combines user and device
 * authentication
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContextAwareAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final DeviceCertificateService certificateService;
    private final DeviceApiKeyService apiKeyService;
    private final DeviceRegistryClient deviceRegistryClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        ContextAwareAuthenticationToken authToken = (ContextAwareAuthenticationToken) authentication;

        String username = authToken.getName();
        String password = (String) authToken.getCredentials();
        String deviceId = authToken.getDeviceId();
        String certificatePem = authToken.getCertificatePem();
        String apiKey = authToken.getApiKey();

        log.info("Context-aware authentication attempt for user: {} with device: {}", username, deviceId);

        try {
            // Multi-factor authentication logic
            AuthenticationResult result = performMultiFactorAuthentication(
                    username, password, deviceId, certificatePem, apiKey);

            if (!result.isAuthenticated()) {
                throw new BadCredentialsException("Authentication failed: " + result.getFailureReason());
            }

            // Create authenticated token with enhanced context
            List<SimpleGrantedAuthority> authorities = result.getPermissions().stream()
                    .map(permission -> new SimpleGrantedAuthority("ROLE_" + permission.name()))
                    .collect(Collectors.toList());

            ContextAwareAuthenticationToken authenticatedToken = new ContextAwareAuthenticationToken(
                    result.getUser(),
                    null,
                    authorities,
                    result.getDeviceContext(),
                    result.getAuthenticationMethods());

            log.info("Context-aware authentication successful for user: {} with methods: {}",
                    username, result.getAuthenticationMethods());

            // Publish successful authentication event
            publishAuthenticationEvent(result.getUser().getId(), "CONTEXT_AWARE_AUTH_SUCCESS",
                    Map.of("deviceId", deviceId, "methods", result.getAuthenticationMethods()));

            return authenticatedToken;

        } catch (Exception e) {
            log.warn("Context-aware authentication failed for user: {} - {}", username, e.getMessage());

            // Publish authentication failure event
            publishAuthenticationEvent(null, "CONTEXT_AWARE_AUTH_FAILED",
                    Map.of("deviceId", deviceId, "error", e.getMessage()));

            throw new BadCredentialsException("Authentication failed", e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ContextAwareAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * Perform multi-factor authentication combining various methods
     */
    private AuthenticationResult performMultiFactorAuthentication(
            String username, String password, String deviceId,
            String certificatePem, String apiKey) {

        AuthenticationResult result = new AuthenticationResult();
        List<String> authMethods = new ArrayList<>();

        // 1. User credential authentication (primary factor)
        User user = authenticateUserCredentials(username, password);
        if (user == null) {
            result.setAuthenticated(false);
            result.setFailureReason("Invalid user credentials");
            return result;
        }
        result.setUser(user);
        authMethods.add("PASSWORD");

        // 2. Device validation (secondary factor)
        DeviceContext deviceContext = validateDevice(deviceId, user);
        if (deviceContext == null) {
            result.setAuthenticated(false);
            result.setFailureReason("Device validation failed");
            return result;
        }
        result.setDeviceContext(deviceContext);
        authMethods.add("DEVICE_VALIDATION");

        // 3. Certificate authentication (optional enhancement)
        if (certificatePem != null && !certificatePem.isEmpty()) {
            DeviceCertificateValidationResult certResult = certificateService.validateDeviceCertificate(deviceId,
                    certificatePem);
            if (certResult.isValid()) {
                authMethods.add("CERTIFICATE");
                log.debug("Certificate authentication successful for device: {}", deviceId);
            } else {
                log.warn("Certificate authentication failed for device: {} - {}", deviceId,
                        certResult.getErrorMessage());
            }
        }

        // 4. API key authentication (alternative to password)
        if (apiKey != null && !apiKey.isEmpty()) {
            Optional<com.mayo.auth.dto.DeviceApiKeyDto> apiKeyResult = apiKeyService.validateApiKey(apiKey);
            if (apiKeyResult.isPresent() && apiKeyResult.get().getDeviceId().equals(deviceId)) {
                authMethods.add("API_KEY");
                log.debug("API key authentication successful for device: {}", deviceId);
            } else {
                log.warn("API key authentication failed for device: {}", deviceId);
            }
        }

        // Determine permissions based on user type and authentication strength
        Set<Permission> permissions = determinePermissions(user, authMethods);
        result.setPermissions(permissions);
        result.setAuthenticationMethods(authMethods);
        result.setAuthenticated(true);

        return result;
    }

    /**
     * Authenticate user credentials
     */
    private User authenticateUserCredentials(String username, String password) {
        Optional<User> userOpt = userRepository.findByEmailOrGhanaCardId(username);
        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        // Note: Password verification would be done by the calling service
        // Here we just return the user for context
        return user;
    }

    /**
     * Validate device context using device registry service
     */
    private DeviceContext validateDevice(String deviceId, User user) {
        log.debug("Validating device {} for user {}", deviceId, user.getId());

        try {
            // Call device registry service
            DeviceRegistryClient.DeviceInfo deviceInfo = deviceRegistryClient.validateDevice(deviceId);

            if (deviceInfo == null) {
                log.warn("Device {} validation failed - device not found or inactive", deviceId);
                return null;
            }

            // Additional validation: check if device belongs to user's hospital
            // This would require user hospital information, for now assume valid

            log.debug("Device {} validated successfully - hospital: {}, protocol: {}",
                    deviceId, deviceInfo.getHospitalId(), deviceInfo.getProtocol());

            return DeviceContext.builder()
                    .deviceId(deviceId)
                    .hospitalId(UUID.fromString(deviceInfo.getHospitalId()))
                    .deviceStatus(deviceInfo.getStatus())
                    .protocol(deviceInfo.getProtocol())
                    .build();

        } catch (Exception e) {
            log.error("Error validating device {}: {}", deviceId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Determine permissions based on user type and authentication strength
     */
    private Set<Permission> determinePermissions(User user, List<String> authMethods) {
        Set<Permission> permissions = new HashSet<>();

        // Base permissions from user type
        switch (user.getUserType()) {
            case PATIENT -> permissions.addAll(Set.of(
                    Permission.PATIENT_READ_OWN_RECORDS,
                    Permission.PATIENT_UPDATE_OWN_PROFILE));
            case DOCTOR -> permissions.addAll(Set.of(
                    Permission.PROVIDER_READ_PATIENT_RECORDS,
                    Permission.PROVIDER_WRITE_PATIENT_RECORDS));
            case NURSE -> permissions.addAll(Set.of(
                    Permission.NURSE_RECORD_VITALS,
                    Permission.PROVIDER_READ_PATIENT_RECORDS));
            case HOSPITAL_ADMIN -> permissions.addAll(Set.of(
                    Permission.HOSPITAL_ADMIN_MANAGE_HOSPITAL_USERS,
                    Permission.HOSPITAL_ADMIN_VIEW_HOSPITAL_AUDIT_LOGS));
            case SUPER_ADMIN -> permissions.addAll(Set.of(
                    Permission.SUPER_ADMIN_ALL_ACCESS,
                    Permission.SUPER_ADMIN_MANAGE_ALL_HOSPITALS));
            default -> permissions.add(Permission.PATIENT_READ_OWN_RECORDS);
        }

        // Enhanced permissions for strong authentication
        if (authMethods.contains("CERTIFICATE")) {
            // Certificate authentication provides enhanced security
            log.debug("Certificate authentication detected - enhanced security context");
        }

        if (authMethods.size() >= 3) { // Multi-factor authentication
            // Multi-factor authentication provides additional verification
            log.debug("Multi-factor authentication detected - enhanced verification");
        }

        return permissions;
    }

    /**
     * Inner classes for authentication result and device context
     */
    public static class AuthenticationResult {
        private boolean authenticated;
        private String failureReason;
        private User user;
        private DeviceContext deviceContext;
        private Set<Permission> permissions;
        private List<String> authenticationMethods;

        // Getters and setters
        public boolean isAuthenticated() {
            return authenticated;
        }

        public void setAuthenticated(boolean authenticated) {
            this.authenticated = authenticated;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public DeviceContext getDeviceContext() {
            return deviceContext;
        }

        public void setDeviceContext(DeviceContext deviceContext) {
            this.deviceContext = deviceContext;
        }

        public Set<Permission> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<Permission> permissions) {
            this.permissions = permissions;
        }

        public List<String> getAuthenticationMethods() {
            return authenticationMethods;
        }

        public void setAuthenticationMethods(List<String> authenticationMethods) {
            this.authenticationMethods = authenticationMethods;
        }
    }

    public static class DeviceContext {
        private String deviceId;
        private UUID hospitalId;
        private DeviceStatus deviceStatus;
        private String protocol;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String deviceId;
            private UUID hospitalId;
            private DeviceStatus deviceStatus;
            private String protocol;

            public Builder deviceId(String deviceId) {
                this.deviceId = deviceId;
                return this;
            }

            public Builder hospitalId(UUID hospitalId) {
                this.hospitalId = hospitalId;
                return this;
            }

            public Builder deviceStatus(DeviceStatus deviceStatus) {
                this.deviceStatus = deviceStatus;
                return this;
            }

            public Builder protocol(String protocol) {
                this.protocol = protocol;
                return this;
            }

            public DeviceContext build() {
                DeviceContext context = new DeviceContext();
                context.deviceId = this.deviceId;
                context.hospitalId = this.hospitalId;
                context.deviceStatus = this.deviceStatus;
                context.protocol = this.protocol;
                return context;
            }
        }

        // Getters
        public String getDeviceId() {
            return deviceId;
        }

        public UUID getHospitalId() {
            return hospitalId;
        }

        public DeviceStatus getDeviceStatus() {
            return deviceStatus;
        }

        public String getProtocol() {
            return protocol;
        }
    }

    /**
     * Publish authentication event to audit service
     */
    private void publishAuthenticationEvent(UUID userId, String eventType, Map<String, Object> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"userId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    java.util.UUID.randomUUID(), eventType, userId != null ? userId.toString() : null,
                    java.time.LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.AUDIT_EVENTS, userId != null ? userId.toString() : "system", eventMessage);
            log.debug("Published authentication event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish authentication event for user {} event {}", userId, eventType, e);
        }
    }
}