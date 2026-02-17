package com.mayo.patientrecord.controller;

import com.mayo.patientrecord.dto.InitiateOwnershipTransferRequest;
import com.mayo.patientrecord.dto.OwnershipTransferDto;
import com.mayo.patientrecord.dto.OwnershipTransferHistoryDto;
import com.mayo.patientrecord.dto.PatientDto;
import com.mayo.patientrecord.service.PatientService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for patient operations
 */
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Patient Management", description = "Patient management APIs")
public class PatientController {

    private final PatientService patientService;

    /**
     * Create a new patient
     */
    @PostMapping
    @Operation(summary = "Create a new patient")
    @PreAuthorize("hasAuthority('RECEPTIONIST_REGISTER_PATIENTS') or hasAuthority('PROVIDER_WRITE_PATIENT_RECORDS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS') or hasAuthority('SUPER_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<PatientDto>> createPatient(@Valid @RequestBody PatientDto patientDto) {
        PatientDto createdPatient = patientService.createPatient(patientDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdPatient, "Patient created successfully"));
    }

    /**
     * Get patient by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get patient by ID")
    @PreAuthorize("hasAuthority('PROVIDER_READ_PATIENT_RECORDS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS') or hasAuthority('SUPER_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<PatientDto>> getPatientById(@PathVariable UUID id) {
        return patientService.getPatientById(id)
                .map(patient -> ResponseEntity.ok(ApiResponse.success(patient, "Patient retrieved successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all patients
     */
    @GetMapping
    @Operation(summary = "Get all patients")
    @PreAuthorize("hasAuthority('PROVIDER_VIEW_ALL_PATIENTS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS') or hasAuthority('SUPER_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<List<PatientDto>>> getAllPatients() {
        List<PatientDto> patients = patientService.getAllPatients();
        return ResponseEntity.ok(ApiResponse.success(patients, "Patients retrieved successfully"));
    }

    /**
     * Get patients by user ID
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get patients by user ID")
    @PreAuthorize("hasAuthority('PROVIDER_READ_PATIENT_RECORDS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS') or hasAuthority('SUPER_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<List<PatientDto>>> getPatientsByUserId(@PathVariable UUID userId) {
        List<PatientDto> patients = patientService.getPatientsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(patients, "Patients retrieved successfully"));
    }

    /**
     * Get patient by family member ID
     */
    @GetMapping("/family-member/{familyMemberId}")
    @Operation(summary = "Get patient by family member ID")
    @PreAuthorize("hasAuthority('PROVIDER_READ_PATIENT_RECORDS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS') or hasAuthority('SUPER_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<PatientDto>> getPatientByFamilyMemberId(@PathVariable UUID familyMemberId) {
        return patientService.getPatientByFamilyMemberId(familyMemberId)
                .map(patient -> ResponseEntity.ok(ApiResponse.success(patient, "Patient retrieved successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update patient
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update patient")
    @PreAuthorize("hasAuthority('PROVIDER_WRITE_PATIENT_RECORDS') or hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS') or hasAuthority('SUPER_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<PatientDto>> updatePatient(@PathVariable UUID id, @Valid @RequestBody PatientDto patientDto) {
        return patientService.updatePatient(id, patientDto)
                .map(updatedPatient -> ResponseEntity.ok(ApiResponse.success(updatedPatient, "Patient updated successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete patient
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete patient")
    @PreAuthorize("hasAuthority('HOSPITAL_ADMIN_ALL_ACCESS') or hasAuthority('SUPER_ADMIN_ALL_ACCESS')")
    public ResponseEntity<ApiResponse<Void>> deletePatient(@PathVariable UUID id) {
        boolean deleted = patientService.deletePatient(id);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success(null, "Patient deleted successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Initiate ownership transfer for a patient
     */
    @PostMapping("/{patientId}/transfer-ownership")
    @Operation(summary = "Initiate ownership transfer for a patient")
    public ResponseEntity<ApiResponse<OwnershipTransferDto>> initiateOwnershipTransfer(
            @PathVariable UUID patientId,
            @Valid @RequestBody InitiateOwnershipTransferRequest request) {
        OwnershipTransferDto transfer = patientService.initiateOwnershipTransfer(patientId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(transfer, "Ownership transfer initiated successfully"));
    }

    /**
     * Confirm ownership transfer
     */
    @PostMapping("/transfers/{transferId}/confirm")
    @Operation(summary = "Confirm ownership transfer")
    public ResponseEntity<ApiResponse<OwnershipTransferDto>> confirmOwnershipTransfer(@PathVariable UUID transferId) {
        OwnershipTransferDto transfer = patientService.confirmOwnershipTransfer(transferId);
        return ResponseEntity.ok(ApiResponse.success(transfer, "Ownership transfer confirmed successfully"));
    }

    /**
     * Reject ownership transfer
     */
    @PostMapping("/transfers/{transferId}/reject")
    @Operation(summary = "Reject ownership transfer")
    public ResponseEntity<ApiResponse<OwnershipTransferDto>> rejectOwnershipTransfer(@PathVariable UUID transferId) {
        OwnershipTransferDto transfer = patientService.rejectOwnershipTransfer(transferId);
        return ResponseEntity.ok(ApiResponse.success(transfer, "Ownership transfer rejected successfully"));
    }

    /**
     * Get ownership transfer history for a patient
     */
    @GetMapping("/{patientId}/ownership-history")
    @Operation(summary = "Get ownership transfer history for a patient")
    public ResponseEntity<ApiResponse<List<OwnershipTransferHistoryDto>>> getOwnershipTransferHistory(@PathVariable UUID patientId) {
        List<OwnershipTransferHistoryDto> history = patientService.getOwnershipTransferHistory(patientId);
        return ResponseEntity.ok(ApiResponse.success(history, "Ownership transfer history retrieved successfully"));
    }
}