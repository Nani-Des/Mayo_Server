package com.mayo.auth.controller;

import com.mayo.auth.dto.AdminUserResponse;
import com.mayo.auth.dto.CreateHospitalAdminRequest;
import com.mayo.auth.dto.SystemStatsResponse;
import com.mayo.auth.entity.User;
import com.mayo.auth.repository.UserRepository;
import com.mayo.auth.service.AuthService;
import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.core.enums.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for Super Admin operations (system-wide)
 * This controller provides system-wide admin functionality
 */
@RestController
@RequestMapping("/api/v1/auth/super-admin")
@RequiredArgsConstructor
@Tag(name = "Super Admin Management", description = "APIs for system-wide administration (Super Admin only)")
@Slf4j
public class SuperAdminController {

    private final UserRepository userRepository;
    private final AuthService authService;

    /**
     * Create a Hospital Admin account (Super Admin only)
     */
    @PostMapping("/hospital-admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a Hospital Admin account")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createHospitalAdmin(
            @Valid @RequestBody CreateHospitalAdminRequest request) {
        log.info("Creating hospital admin for hospital: {}", request.getHospitalId());
        
        User createdAdmin = authService.createHospitalAdmin(request);
        AdminUserResponse response = convertToAdminResponse(createdAdmin);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Hospital Admin created successfully"));
    }

    /**
     * Get system statistics (Super Admin only)
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get system-wide statistics")
    public ResponseEntity<ApiResponse<SystemStatsResponse>> getSystemStats(Authentication authentication) {
        UUID superAdminId = UUID.fromString(authentication.getName());
        log.info("Super admin {} requested system statistics", superAdminId);
        
        List<User> allUsers = userRepository.findAll();
        
        long totalUsers = allUsers.size();
        long totalDoctors = allUsers.stream().filter(u -> u.getUserType() == UserType.DOCTOR).count();
        long totalNurses = allUsers.stream().filter(u -> u.getUserType() == UserType.NURSE).count();
        long totalPatients = allUsers.stream().filter(u -> u.getUserType() == UserType.PATIENT).count();
        long totalAdmins = allUsers.stream()
                .filter(u -> u.getUserType().isAdmin())
                .count();
        long totalHospitals = allUsers.stream()
                .map(User::getHospitalId)
                .filter(h -> h != null)
                .distinct()
                .count();
        
        SystemStatsResponse stats = SystemStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalHospitals(totalHospitals)
                .totalDoctors(totalDoctors)
                .totalNurses(totalNurses)
                .totalPatients(totalPatients)
                .totalAdmins(totalAdmins)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(stats, "System statistics retrieved successfully"));
    }

    /**
     * List all Hospital Admins (Super Admin only)
     */
    @GetMapping("/hospital-admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all Hospital Admins")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> listHospitalAdmins() {
        List<AdminUserResponse> admins = userRepository.findAll().stream()
                .filter(u -> u.getUserType() == UserType.HOSPITAL_ADMIN)
                .map(this::convertToAdminResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(admins, "Hospital Admins retrieved successfully"));
    }

    /**
     * Activate/Deactivate a user (Super Admin only)
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all users in the system")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> listAllUsers(
            @RequestParam(required = false) UserType userType,
            @RequestParam(required = false) Boolean isActive) {
        
        List<User> users = userRepository.findAll();
        
        if (userType != null) {
            users = users.stream()
                    .filter(u -> u.getUserType() == userType)
                    .collect(Collectors.toList());
        }
        
        if (isActive != null) {
            users = users.stream()
                    .filter(u -> Boolean.TRUE.equals(u.getIsActive()) == Boolean.TRUE.equals(isActive))
                    .collect(Collectors.toList());
        }
        
        List<AdminUserResponse> response = users.stream()
                .map(this::convertToAdminResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(response, "Users retrieved successfully"));
    }

    private AdminUserResponse convertToAdminResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .userType(user.getUserType())
                .hospitalId(user.getHospitalId())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .build();
    }
}
