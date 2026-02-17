package com.mayo.hospitalintegration.controller;

import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.core.enums.Permission;
import com.mayo.hospitalintegration.dto.HospitalDto;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.service.HospitalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hospitals")
@RequiredArgsConstructor
@Tag(name = "Hospital Management", description = "Hospital registration and management endpoints")
public class HospitalController {

    private final HospitalService hospitalService;

    @GetMapping
    @Operation(summary = "Get all hospitals")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<List<HospitalDto>>> getAllHospitals() {
        List<HospitalDto> hospitals = hospitalService.getAllHospitals();
        return ResponseEntity.ok(ApiResponse.success(hospitals));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get hospital by ID")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<HospitalDto>> getHospital(@PathVariable UUID id) {
        return hospitalService.getHospitalById(id)
                .map(hospital -> ResponseEntity.ok(ApiResponse.success(hospital)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-hospital-id/{hospitalId}")
    @Operation(summary = "Get hospital by hospital ID")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<HospitalDto>> getHospitalByHospitalId(@PathVariable String hospitalId) {
        return hospitalService.getHospitalByHospitalId(hospitalId)
                .map(hospital -> ResponseEntity.ok(ApiResponse.success(hospital)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new hospital")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS')")
    public ResponseEntity<ApiResponse<HospitalDto>> createHospital(@RequestBody HospitalDto hospitalDto) {
        try {
            HospitalDto created = hospitalService.createHospital(hospitalDto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update hospital")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS')")
    public ResponseEntity<ApiResponse<HospitalDto>> updateHospital(@PathVariable UUID id, @RequestBody HospitalDto hospitalDto) {
        return hospitalService.updateHospital(id, hospitalDto)
                .map(updated -> ResponseEntity.ok(ApiResponse.success(updated)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete hospital")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS')")
    public ResponseEntity<ApiResponse<Void>> deleteHospital(@PathVariable UUID id) {
        if (hospitalService.deleteHospital(id)) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get hospitals by status")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<List<HospitalDto>>> getHospitalsByStatus(@PathVariable Hospital.HospitalStatus status) {
        List<HospitalDto> hospitals = hospitalService.getHospitalsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(hospitals));
    }

    @GetMapping("/integration-enabled")
    @Operation(summary = "Get hospitals with integration enabled")
    @PreAuthorize("hasAuthority('SUPER_ADMIN_MANAGE_ALL_HOSPITALS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<List<HospitalDto>>> getIntegrationEnabledHospitals() {
        List<HospitalDto> hospitals = hospitalService.getIntegrationEnabledHospitals();
        return ResponseEntity.ok(ApiResponse.success(hospitals));
    }
}