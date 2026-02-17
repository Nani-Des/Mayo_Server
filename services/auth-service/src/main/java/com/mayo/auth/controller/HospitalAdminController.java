package com.mayo.auth.controller;

import com.mayo.auth.dto.AdminUserResponse;
import com.mayo.auth.dto.HospitalStatsResponse;
import com.mayo.auth.entity.User;
import com.mayo.auth.repository.UserRepository;
import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.core.enums.UserType;
import com.mayo.common.security.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for Hospital Admin operations (hospital-wide)
 * Provides hospital-level administration functionality
 */
@RestController
@RequestMapping("/api/v1/auth/hospital-admin")
@RequiredArgsConstructor
@Tag(name = "Hospital Admin Management", description = "APIs for hospital-wide administration (Hospital Admin only)")
@Slf4j
public class HospitalAdminController {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Get hospital statistics (Hospital Admin only)
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Get hospital statistics")
    public ResponseEntity<ApiResponse<HospitalStatsResponse>> getHospitalStats(Authentication authentication) {
        UUID hospitalAdminId = UUID.fromString(authentication.getName());
        
        User hospitalAdmin = userRepository.findById(hospitalAdminId)
                .orElseThrow(() -> new IllegalStateException("Hospital admin not found"));
        
        UUID hospitalId = hospitalAdmin.getHospitalId();
        if (hospitalId == null) {
            throw new IllegalStateException("Hospital ID not set for hospital admin");
        }
        
        List<User> hospitalUsers = userRepository.findByHospitalId(hospitalId);
        
        long totalUsers = hospitalUsers.size();
        long totalDoctors = hospitalUsers.stream().filter(u -> u.getUserType() == UserType.DOCTOR).count();
        long totalNurses = hospitalUsers.stream().filter(u -> u.getUserType() == UserType.NURSE).count();
        long totalReceptionists = hospitalUsers.stream().filter(u -> u.getUserType() == UserType.RECEPTIONIST).count();
        long totalPatients = hospitalUsers.stream().filter(u -> u.getUserType() == UserType.PATIENT).count();
        
        HospitalStatsResponse stats = HospitalStatsResponse.builder()
                .hospitalId(hospitalId)
                .totalUsers(totalUsers)
                .totalDoctors(totalDoctors)
                .totalNurses(totalNurses)
                .totalReceptionists(totalReceptionists)
                .totalPatients(totalPatients)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(stats, "Hospital statistics retrieved successfully"));
    }

    /**
     * List all staff in the hospital (Hospital Admin only)
     */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "List all staff in the hospital")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> listHospitalStaff(
            @RequestParam(required = false) UserType userType,
            Authentication authentication) {
        
        UUID hospitalAdminId = UUID.fromString(authentication.getName());
        
        User hospitalAdmin = userRepository.findById(hospitalAdminId)
                .orElseThrow(() -> new IllegalStateException("Hospital admin not found"));
        
        UUID hospitalId = hospitalAdmin.getHospitalId();
        if (hospitalId == null) {
            throw new IllegalStateException("Hospital ID not set for hospital admin");
        }
        
        List<User> hospitalUsers = userRepository.findByHospitalId(hospitalId);
        
        // Filter by user type if provided
        if (userType != null) {
            hospitalUsers = hospitalUsers.stream()
                    .filter(u -> u.getUserType() == userType)
                    .collect(Collectors.toList());
        }
        
        List<AdminUserResponse> response = hospitalUsers.stream()
                .map(this::convertToAdminResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(response, "Hospital staff retrieved successfully"));
    }

    /**
     * List all patients in the hospital (Hospital Admin only)
     */
    @GetMapping("/patients")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "List all patients in the hospital")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> listHospitalPatients(Authentication authentication) {
        
        UUID hospitalAdminId = UUID.fromString(authentication.getName());
        
        User hospitalAdmin = userRepository.findById(hospitalAdminId)
                .orElseThrow(() -> new IllegalStateException("Hospital admin not found"));
        
        UUID hospitalId = hospitalAdmin.getHospitalId();
        if (hospitalId == null) {
            throw new IllegalStateException("Hospital ID not set for hospital admin");
        }
        
        List<User> patients = userRepository.findByHospitalIdAndUserType(hospitalId, UserType.PATIENT);
        
        List<AdminUserResponse> response = patients.stream()
                .map(this::convertToAdminResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(response, "Patients retrieved successfully"));
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
