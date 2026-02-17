package com.mayo.auth.controller;

import com.mayo.auth.dto.DeviceLoginRequest;
import com.mayo.auth.dto.LoginRequest;
import com.mayo.auth.dto.LoginResponse;
import com.mayo.auth.dto.RegisterRequest;
import com.mayo.auth.entity.User;
import com.mayo.auth.service.AuthService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "User registered successfully"));
    }

    /**
     * Login user
     */
    @PostMapping("/login")
    @Operation(summary = "Login user")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /**
     * Device-specific login
     */
    @PostMapping("/device-login")
    @Operation(summary = "Login with device authentication")
    public ResponseEntity<ApiResponse<LoginResponse>> deviceLogin(@Valid @RequestBody DeviceLoginRequest request) {
        LoginResponse response = authService.deviceLogin(
                new LoginRequest(request.getUsernameOrEmail(), request.getPassword()),
                request.getDeviceId()
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Device login successful"));
    }

    /**
     * Refresh token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody com.mayo.auth.dto.RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("X-User-Id") java.util.UUID userId,
            @Valid @RequestBody com.mayo.auth.dto.ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}