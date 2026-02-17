package com.mayo.auth.controller;

import com.mayo.auth.dto.RegisterRequest;
import com.mayo.auth.dto.StaffResponse;
import com.mayo.auth.entity.User;
import com.mayo.auth.service.AuthService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.stream.Collectors;

/**
 * Controller for managing hospital staff
 */
@RestController
@RequestMapping("/api/v1/auth/staff")
@RequiredArgsConstructor
@Tag(name = "Staff Management", description = "APIs for managing hospital staff (Doctors, Nurses)")
public class StaffController {

    private final AuthService authService;

    /**
     * Register a new staff member (Admin, Hospital Admin, or Super Admin Only)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Register a new staff member")
    public ResponseEntity<ApiResponse<StaffResponse>> registerStaff(
            @Valid @RequestBody RegisterRequest request,
            Authentication authentication) {
        
        UUID adminId = UUID.fromString(authentication.getName());
        User adminUser = authService.getUserById(adminId);
        
        User createdUser = authService.registerStaff(request, adminUser);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(convertToResponse(createdUser), "Staff member registered successfully"));
    }

    /**
     * List staff for a hospital (Admin, Hospital Admin, or Super Admin Only)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List staff members")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> listStaff(
            @RequestParam(required = false) UUID hospitalId,
            Authentication authentication) {
        
        UUID adminId = UUID.fromString(authentication.getName());
        User adminUser = authService.getUserById(adminId);
        
        // If Hospital Admin, force their hospital ID
        UUID targetHospitalId = hospitalId;
        if (adminUser.getHospitalId() != null) {
            targetHospitalId = adminUser.getHospitalId();
        } else {
            // Super Admin must provide hospital ID or we could return all?
            // For now, let's require it for fetching specific hospital staff
            if (targetHospitalId == null) {
                // Determine behavior: return empty or throw? 
                // Let's return empty list for now if no hospital specified for super admin
                 return ResponseEntity.ok(ApiResponse.success(java.util.Collections.emptyList(), "Hospital ID required"));
            }
        }
        
        List<User> staff = authService.getStaffByHospital(targetHospitalId);
        List<StaffResponse> response = staff.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(response, "Staff list retrieved successfully"));
    }

    /**
     * Deactivate a staff member (Admin, Hospital Admin, or Super Admin Only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Deactivate a staff member")
    public ResponseEntity<ApiResponse<Void>> deactivateStaff(
            @PathVariable UUID id,
            Authentication authentication) {
        
        UUID adminId = UUID.fromString(authentication.getName());
        User adminUser = authService.getUserById(adminId);
        
        authService.deactivateStaff(id, adminUser);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Staff member deactivated successfully"));
    }

    // Helper to convert User to StaffResponse
    private StaffResponse convertToResponse(User user) {
        return StaffResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .userType(user.getUserType())
                .hospitalId(user.getHospitalId())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .ghanaCardId(user.getGhanaCardId())
                .build();
    }
}
