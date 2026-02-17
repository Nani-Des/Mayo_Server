package com.mayo.auth.controller;

import com.mayo.auth.entity.User;
import com.mayo.auth.service.AuthService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User management APIs")
public class UserController {

    private final AuthService authService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable UUID userId) {
        User user = authService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user profile")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable UUID userId, @RequestBody com.mayo.auth.dto.UpdateUserRequest request) {
        User user = authService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(user, "User profile updated successfully"));
    }
}