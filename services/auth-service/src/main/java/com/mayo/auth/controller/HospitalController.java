package com.mayo.auth.controller;

import com.mayo.common.core.dto.ApiResponse;
import com.mayo.auth.dto.CreateHospitalRequest;
import com.mayo.auth.dto.HospitalResponse;
import com.mayo.auth.dto.UpdateHospitalRequest;
import com.mayo.auth.service.HospitalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hospitals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Hospital Management", description = "Endpoints for managing hospitals (Super Admin only)")
public class HospitalController {

    private final HospitalService hospitalService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS')")
    @Operation(summary = "Create a new hospital")
    public ResponseEntity<ApiResponse<HospitalResponse>> createHospital(@Valid @RequestBody CreateHospitalRequest request) {
        HospitalResponse response = hospitalService.createHospital(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Hospital created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')") 
    @Operation(summary = "Get all hospitals")
    public ResponseEntity<ApiResponse<List<HospitalResponse>>> getAllHospitals() {
        List<HospitalResponse> response = hospitalService.getAllHospitals();
        return ResponseEntity.ok(ApiResponse.success(response, "Hospitals retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get hospital by ID")
    public ResponseEntity<ApiResponse<HospitalResponse>> getHospital(@PathVariable UUID id) {
        HospitalResponse response = hospitalService.getHospital(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Hospital retrieved successfully"));
    }
    
    @GetMapping("/by-hospital-id/{hospitalId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get hospital by Hospital ID String")
    public ResponseEntity<ApiResponse<HospitalResponse>> getHospitalByHospitalId(@PathVariable String hospitalId) {
        HospitalResponse response = hospitalService.getHospitalByHospitalId(hospitalId);
        return ResponseEntity.ok(ApiResponse.success(response, "Hospital retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS')")
    @Operation(summary = "Update hospital details")
    public ResponseEntity<ApiResponse<HospitalResponse>> updateHospital(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHospitalRequest request) {
        HospitalResponse response = hospitalService.updateHospital(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Hospital updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete a hospital")
    public ResponseEntity<ApiResponse<Void>> deleteHospital(@PathVariable UUID id) {
        hospitalService.deleteHospital(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Hospital deleted successfully"));
    }
}
