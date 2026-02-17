package com.mayo.auth.service;

import com.mayo.auth.dto.DeviceApiKeyDto;
import com.mayo.auth.dto.DeviceApiKeyRequest;
import com.mayo.auth.dto.DeviceAuthorizationResponse;
import com.mayo.auth.dto.DeviceCertificateValidationResult;
import com.mayo.auth.dto.DeviceCheckResponse;
import com.mayo.auth.dto.DeviceDto;
import com.mayo.auth.dto.DeviceRegistrationRequest;
import com.mayo.auth.dto.DeviceTokenRequest;
import com.mayo.auth.dto.LoginRequest;
import com.mayo.auth.dto.LoginResponse;
import com.mayo.auth.dto.RegisterRequest;
import com.mayo.auth.entity.Device;
import com.mayo.auth.entity.User;
import com.mayo.auth.repository.DeviceRepository;
import com.mayo.auth.repository.UserRepository;
import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.common.core.enums.Permission;
import com.mayo.common.core.enums.UserType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.common.core.exception.ResourceNotFoundException;
import com.mayo.common.core.exception.UnauthorizedException;
import com.mayo.common.security.jwt.JwtTokenProvider;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for authentication operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DeviceCertificateService certificateService;
    private final DeviceApiKeyService apiKeyService;
    private final DeviceOAuthService oauthService;
    private final ContextAwareAuthenticationProvider contextAwareProvider;

    /**
     * Register a new user
     */
    @Transactional
    public User register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Check if Ghana Card ID already exists (if provided)
        if (request.getGhanaCardId() != null && userRepository.existsByGhanaCardId(request.getGhanaCardId())) {
            throw new IllegalArgumentException("Ghana Card ID already exists");
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .ghanaCardId(request.getGhanaCardId())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .userType(request.getUserType())
                .deviceType(request.getDeviceType())
                .hospitalId(request.getHospitalId())
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        // Publish user registration event
        publishUserEvent(savedUser.getId(), "USER_REGISTERED", savedUser.getId(),
                Map.of("email", savedUser.getEmail(), "userType", savedUser.getUserType().name()));

        return savedUser;
    }

    /**
     * Get default permissions based on user type
     */
    private Set<Permission> getDefaultPermissions(UserType userType) {
        return Permission.getPermissionsForUserType(userType);
    }

    /**
     * Authenticate user and generate tokens
     */
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for: {}", request.getUsernameOrEmail());

        // Find user by email or Ghana Card ID
        Optional<User> userOptional = userRepository.findByEmailOrGhanaCardId(request.getUsernameOrEmail());

        if (userOptional.isEmpty()) {
            throw UnauthorizedException.invalidCredentials();
        }

        User user = userOptional.get();

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw UnauthorizedException.invalidCredentials();
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getUserType(),
                getDefaultPermissions(user.getUserType()),
                user.getHospitalId(),
                user.isSuperAdmin(),
                null);  // roleId - User entity doesn't have this field yet

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        log.info("User logged in successfully: {}", user.getEmail());

        // Publish user login event
        publishUserEvent(user.getId(), "USER_LOGGED_IN", user.getId(),
                Map.of("loginMethod", "password"));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900L) // 15 minutes
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .userType(user.getUserType().name())
                        .hospitalId(user.getHospitalId())
                        .build())
                .build();
    }

    /**
     * Device-specific login and generate device tokens
     */
    public LoginResponse deviceLogin(LoginRequest request, String deviceId) {
        log.info("Device login attempt for: {} with device: {}", request.getUsernameOrEmail(), deviceId);

        // Find user by email or Ghana Card ID
        Optional<User> userOptional = userRepository.findByEmailOrGhanaCardId(request.getUsernameOrEmail());

        if (userOptional.isEmpty()) {
            throw UnauthorizedException.invalidCredentials();
        }

        User user = userOptional.get();

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw UnauthorizedException.invalidCredentials();
        }

        // Verify device exists and is active
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        if (!device.getStatus().isActive()) {
            throw new IllegalArgumentException("Device is not active");
        }

        // Staff Login Logic
        if (user.getUserType() == UserType.HOSPITAL_ADMIN || user.getUserType() == UserType.SUPER_ADMIN) {
             // Admins can log into devices in their hospital
             if (user.getHospitalId() != null && device.getHospitalId() != null &&
                 user.getHospitalId().equals(device.getHospitalId())) {
                 // Allow
             } else if (user.isSuperAdmin()) {
                 // Super admin can log into any device
             } else {
                 if (!device.getUserId().equals(user.getId())) {
                     throw UnauthorizedException.invalidCredentials();
                 }
             }
        } else {
            // Patients etc. must own the device
            if (!device.getUserId().equals(user.getId())) {
                throw UnauthorizedException.invalidCredentials();
            }
        }

        // Generate device-specific tokens
        String accessToken = jwtTokenProvider.generateDeviceAccessToken(
                user.getId(),
                user.getEmail(),
                user.getUserType(),
                getDefaultPermissions(user.getUserType()),
                deviceId,
                user.getHospitalId(),
                user.isSuperAdmin(),
                null);  // roleId - User entity doesn't have this field yet

        String refreshToken = jwtTokenProvider.generateDeviceRefreshToken(user.getId(), deviceId);

        log.info("User logged in successfully with device: {}", user.getEmail());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900L) // 15 minutes
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .userType(user.getUserType().name())
                        .hospitalId(user.getHospitalId())
                        .build())
                .build();
    }

    /**
     * Register a new device for a user
     */
    @Transactional
    public DeviceDto registerDevice(UUID userId, DeviceRegistrationRequest request) {
        log.info("Registering device: {} for user: {}", request.getDeviceId(), userId);

        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if device ID already exists
        if (deviceRepository.existsByDeviceId(request.getDeviceId())) {
            throw new IllegalArgumentException("Device ID already exists");
        }

        // Create new device
        Device device = Device.builder()
                .deviceId(request.getDeviceId())
                .deviceType(request.getDeviceType())
                .publicKey(request.getPublicKey())
                .status(DeviceStatus.ACTIVE)
                .userId(userId)
                .hospitalId(user.getHospitalId())
                .build();

        Device savedDevice = deviceRepository.save(device);
        log.info("Device registered successfully with ID: {}", savedDevice.getId());

        // Publish device registration event
        publishUserEvent(userId, "DEVICE_REGISTERED", userId,
                Map.of("deviceId", savedDevice.getDeviceId(), "deviceType", savedDevice.getDeviceType().name()));

        return convertToDto(savedDevice);
    }

    /**
     * Check if device is registered
     */
    public boolean isDeviceRegistered(String deviceId) {
        return deviceRepository.existsByDeviceId(deviceId);
    }

    /**
     * Get all devices (Admin only)
     */
    public List<DeviceDto> getAllDevices(UUID hospitalId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return deviceRepository.findAll().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } else if (hospitalId != null) {
            return deviceRepository.findByHospitalId(hospitalId).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } else {
            // Should not happen for Admin roles, but return empty to be safe
            return java.util.Collections.emptyList();
        }
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
     * Get devices by user ID
     */
    public List<DeviceDto> getDevicesByUserId(UUID userId) {
        List<Device> devices = deviceRepository.findByUserId(userId);
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
     * Update device status
     */
    @Transactional
    public DeviceDto updateDeviceStatus(UUID deviceId, DeviceStatus status) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        device.setStatus(status);
        Device updatedDevice = deviceRepository.save(device);

        log.info("Device status updated: {} to {}", deviceId, status);
        return convertToDto(updatedDevice);
    }

    /**
     * Convert Device entity to DeviceDto
     */
    private DeviceDto convertToDto(Device device) {
        return DeviceDto.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .deviceType(device.getDeviceType())
                .publicKey(device.getPublicKey())
                .status(device.getStatus())
                .registeredAt(device.getRegisteredAt())
                .userId(device.getUserId())
                .build();
    }

    /**
     * Device Certificate Authentication
     */
    public DeviceCertificateValidationResult authenticateWithCertificate(String deviceId, String certificatePem) {
        log.info("Certificate authentication request for device: {}", deviceId);
        return certificateService.validateDeviceCertificate(deviceId, certificatePem);
    }

    /**
     * Device API Key Authentication
     */
    public Optional<DeviceApiKeyDto> authenticateWithApiKey(String apiKey) {
        log.info("API key authentication request");
        return apiKeyService.validateApiKey(apiKey);
    }

    /**
     * OAuth 2.0 Device Flow - Request authorization
     */
    public DeviceAuthorizationResponse requestDeviceAuthorization(String deviceId, String hospitalId) {
        log.info("OAuth device authorization request for device: {} in hospital: {}", deviceId, hospitalId);
        return oauthService.requestDeviceAuthorization(deviceId, hospitalId);
    }

    /**
     * OAuth 2.0 Device Flow - Approve authorization
     */
    public void approveDeviceAuthorization(String userCode, String approvedByUserId) {
        log.info("OAuth device authorization approval for code: {} by user: {}", userCode, approvedByUserId);
        oauthService.approveDeviceAuthorization(userCode, approvedByUserId);
    }

    /**
     * OAuth 2.0 Device Flow - Poll for token
     */
    public LoginResponse pollForDeviceToken(DeviceTokenRequest request) {
        log.info("OAuth device token poll for device code: {}", request.getDeviceCode());
        return oauthService.pollForToken(request);
    }

    /**
     * Context-Aware Authentication
     */
    public LoginResponse contextAwareLogin(String username, String password, String deviceId,
            String certificatePem, String apiKey) {
        log.info("Context-aware authentication request for user: {} with device: {}", username, deviceId);

        try {
            // Create context-aware authentication token
            ContextAwareAuthenticationToken authToken = new ContextAwareAuthenticationToken(
                    username, password, deviceId, certificatePem, apiKey);

            // Authenticate using context-aware provider
            ContextAwareAuthenticationToken authenticated = (ContextAwareAuthenticationToken) contextAwareProvider
                    .authenticate(authToken);

            // Generate JWT token based on authentication result
            User authenticatedUser = (User) authenticated.getPrincipal();
            String accessToken = jwtTokenProvider.generateAccessToken(
                    authenticatedUser.getId(),
                    authenticatedUser.getEmail(),
                    authenticatedUser.getUserType(),
                    getDefaultPermissions(authenticatedUser.getUserType()),
                    authenticatedUser.getHospitalId(),
                    authenticatedUser.isSuperAdmin(),
                    null);  // roleId - User entity doesn't have this field yet

            String refreshToken = jwtTokenProvider.generateRefreshToken(((User) authenticated.getPrincipal()).getId());

            log.info("Context-aware authentication successful for user: {}", username);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(900L)
                    .user(LoginResponse.UserInfo.builder()
                            .id(((User) authenticated.getPrincipal()).getId())
                            .email(((User) authenticated.getPrincipal()).getEmail())
                            .fullName(((User) authenticated.getPrincipal()).getFullName())
                            .userType(((User) authenticated.getPrincipal()).getUserType().name())
                            .hospitalId(((User) authenticated.getPrincipal()).getHospitalId())
                            .build())
                    .build();

        } catch (Exception e) {
            log.warn("Context-aware authentication failed for user: {} - {}", username, e.getMessage());
            throw e;
        }
    }

    /**
     * Generate API key for device
     */
    public DeviceApiKeyDto generateDeviceApiKey(DeviceApiKeyRequest request) {
        log.info("Generating API key for device: {}", request.getDeviceId());
        return apiKeyService.generateApiKey(request);
    }

    /**
     * Get API keys for device
     */
    public List<DeviceApiKeyDto> getDeviceApiKeys(String deviceId) {
        log.info("Retrieving API keys for device: {}", deviceId);
        return apiKeyService.getApiKeysForDevice(deviceId);
    }

    /**
     * Revoke device API key
     */
    public void revokeDeviceApiKey(UUID apiKeyId) {
        log.info("Revoking API key: {}", apiKeyId);
        apiKeyService.revokeApiKey(apiKeyId);
    }

    /**
     * Get user by ID
     */
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Update user profile
     */
    @Transactional
    public User updateUser(UUID userId, com.mayo.auth.dto.UpdateUserRequest request) {
        User user = getUserById(userId);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        User updatedUser = userRepository.save(user);
        log.info("User profile updated: {}", userId);
        
        publishUserEvent(userId, "USER_UPDATED", userId, Map.of("updatedFields", "profile"));
        
        return updatedUser;
    }

    /**
     * Change password
     */
    @Transactional
    public void changePassword(UUID userId, com.mayo.auth.dto.ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid old password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", userId);

        publishUserEvent(userId, "PASSWORD_CHANGED", userId, Map.of());
    }

    /**
     * Register a staff member (Doctor/Nurse) by an Admin
     */
    @Transactional
    public User registerStaff(RegisterRequest request, User adminUser) {
        log.info("Staff registration attempt by admin: {} for email: {}", adminUser.getId(), request.getEmail());

        if (!adminUser.isAdmin()) {
            throw new UnauthorizedException("Only Administrators can register staff");
        }

        if (adminUser.getHospitalId() != null) {
            if (request.getHospitalId() != null && !request.getHospitalId().equals(adminUser.getHospitalId())) {
                throw new com.mayo.common.core.exception.ForbiddenException("You can only register staff for your own hospital");
            }
            request.setHospitalId(adminUser.getHospitalId());
        } else {
            if (request.getHospitalId() == null) {
                throw new IllegalArgumentException("Hospital ID is required for staff registration");
            }
        }

        if (request.getUserType().isAdmin() || request.getUserType() == UserType.PATIENT) {
             throw new IllegalArgumentException("Invalid staff role.");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
             request.setPassword("Staff@123");
             log.info("Generated default password for staff");
        }

        return register(request);
    }

    /**
     * Get staff members by hospital ID
     */
    public List<User> getStaffByHospital(UUID hospitalId) {
        List<User> users = userRepository.findByHospitalId(hospitalId);
        return users.stream()
                .filter(u -> u.getUserType() == UserType.DOCTOR || 
                             u.getUserType() == UserType.NURSE || 
                             u.getUserType() == UserType.RECEPTIONIST)
                .collect(Collectors.toList());
    }

    /**
     * Deactivate staff member
     */
    @Transactional
    public void deactivateStaff(UUID staffId, User adminUser) {
        User staff = getUserById(staffId);

        if (adminUser.getHospitalId() != null && !adminUser.getHospitalId().equals(staff.getHospitalId())) {
             throw new com.mayo.common.core.exception.ForbiddenException("Cannot manage staff from another hospital");
        }

        staff.setIsActive(false);
        userRepository.save(staff);
        log.info("Staff deactivated: {}", staffId);
        
        publishUserEvent(staffId, "STAFF_DEACTIVATED", adminUser.getId(), Map.of());
    }

    /**
     * Create a Hospital Admin account (Super Admin only)
     */
    @Transactional
    public User createHospitalAdmin(com.mayo.auth.dto.CreateHospitalAdminRequest request) {
        log.info("Creating hospital admin for hospital: {} with email: {}", request.getHospitalId(), request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        String password = request.getInitialPassword();
        if (password == null || password.isBlank()) {
            password = "Admin@" + UUID.randomUUID().toString().substring(0, 8);
            log.info("Generated random password for hospital admin");
        }

        User hospitalAdmin = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(password))
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .userType(UserType.HOSPITAL_ADMIN)
                .hospitalId(request.getHospitalId())
                .isActive(true)
                .emailVerified(true)
                .build();

        User savedUser = userRepository.save(hospitalAdmin);
        log.info("Hospital Admin created successfully with ID: {}", savedUser.getId());

        publishUserEvent(savedUser.getId(), "HOSPITAL_ADMIN_CREATED", savedUser.getId(),
                Map.of("email", savedUser.getEmail(), "hospitalId", savedUser.getHospitalId().toString()));

        return savedUser;
    }

    /**
     * Refresh access token using refresh token
     */
    public LoginResponse refreshToken(com.mayo.auth.dto.RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        log.info("Token refresh request received");

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw UnauthorizedException.invalidToken();
        }

        // Check if token type is REFRESH or DEVICE_REFRESH
        boolean isDeviceToken = jwtTokenProvider.isDeviceRefreshToken(refreshToken);
        boolean isUserToken = jwtTokenProvider.isRefreshToken(refreshToken);

        if (!isDeviceToken && !isUserToken) {
            throw new IllegalArgumentException("Invalid token type. Expected refresh token.");
        }

        // Extract user ID
        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = getUserById(userId);

        // Security check: Verify user is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("User account is inactive");
        }

        String newAccessToken;
        String newRefreshToken = refreshToken; // Reuse old refresh token by default to support rotation policies later

        if (isDeviceToken) {
            String deviceId = jwtTokenProvider.getDeviceIdFromToken(refreshToken);
            
            // If device ID is missing in token, try request body
            if (deviceId == null && request.getDeviceId() != null) {
                deviceId = request.getDeviceId();
            }

            if (deviceId == null) {
                throw new IllegalArgumentException("Device ID not found for device token refresh");
            }

            // Verify device exists and is active
            DeviceDto device = getDeviceByDeviceId(deviceId);
            if (device.getStatus() != DeviceStatus.ACTIVE) {
                throw new UnauthorizedException("Device is not active");
            }

            // Verify device belongs to user (or appropriate admin logic)
            // Simplified check: owner or exact match
            // Note: In detailed implementation, re-use deviceLogin constraints
            
            newAccessToken = jwtTokenProvider.generateDeviceAccessToken(
                    user.getId(),
                    user.getEmail(),
                    user.getUserType(),
                    getDefaultPermissions(user.getUserType()),
                    deviceId,
                    user.getHospitalId(),
                    user.isSuperAdmin(),
                    null
            );
            
            // Optionally rotate refresh token
            // newRefreshToken = jwtTokenProvider.generateDeviceRefreshToken(userId, deviceId);

        } else {
            // Standard User Token Refresh
            newAccessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(),
                    user.getEmail(),
                    user.getUserType(),
                    getDefaultPermissions(user.getUserType()),
                    user.getHospitalId(),
                    user.isSuperAdmin(),
                    null
            );
            
            // Optionally rotate refresh token
            // newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        }

        log.info("Token refreshed successfully for user: {}", user.getEmail());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900L) // 15 minutes
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .userType(user.getUserType().name())
                        .hospitalId(user.getHospitalId())
                        .build())
                .build();
    }

    /**
     * Publish user event to Kafka
     */
    private void publishUserEvent(UUID userId, String eventType, UUID actorId, Map<String, ?> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"userId\":\"%s\",\"actorId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    UUID.randomUUID(), eventType, userId, actorId, java.time.LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.USER_EVENTS, userId.toString(), eventMessage);
            log.debug("Published user event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish user event for user {} event {}", userId, eventType, e);
        }
    }
}
