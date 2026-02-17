package com.mayo.patientrecord.controller;

import com.mayo.patientrecord.dto.*;
import com.mayo.patientrecord.service.PatientService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient-records")
@RequiredArgsConstructor
@Tag(name = "Patient Record Management", description = "Patient record management APIs")
public class PatientRecordController {

    private final PatientService patientService;

    @PostMapping
    @Operation(summary = "Create a new patient record")
    public ResponseEntity<ApiResponse<PatientRecordDto>> createPatientRecord(@Valid @RequestBody PatientRecordDto recordDto) {
        PatientRecordDto createdRecord = patientService.createPatientRecord(recordDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdRecord, "Patient record created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get patient record by ID")
    public ResponseEntity<ApiResponse<PatientRecordDto>> getPatientRecordById(@PathVariable UUID id) {
        return patientService.getPatientRecordById(id)
                .map(record -> ResponseEntity.ok(ApiResponse.success(record, "Patient record retrieved successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get patient records by patient ID")
    public ResponseEntity<ApiResponse<List<PatientRecordDto>>> getPatientRecordsByPatientId(@PathVariable UUID patientId) {
        List<PatientRecordDto> records = patientService.getPatientRecordsByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(records, "Patient records retrieved successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update patient record")
    public ResponseEntity<ApiResponse<PatientRecordDto>> updatePatientRecord(@PathVariable UUID id, @Valid @RequestBody PatientRecordDto recordDto) {
        return patientService.updatePatientRecord(id, recordDto)
                .map(updatedRecord -> ResponseEntity.ok(ApiResponse.success(updatedRecord, "Patient record updated successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete patient record")
    public ResponseEntity<ApiResponse<Void>> deletePatientRecord(@PathVariable UUID id) {
        boolean deleted = patientService.deletePatientRecord(id);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success(null, "Patient record deleted successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Lab Result endpoints

    @PostMapping("/lab-results")
    @Operation(summary = "Create a new lab result")
    public ResponseEntity<ApiResponse<LabResultDto>> createLabResult(@Valid @RequestBody LabResultDto labResultDto) {
        LabResultDto createdLabResult = patientService.createLabResult(labResultDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdLabResult, "Lab result created successfully"));
    }

    @GetMapping("/lab-results/patient-record/{patientRecordId}")
    @Operation(summary = "Get lab results by patient record ID")
    public ResponseEntity<ApiResponse<List<LabResultDto>>> getLabResultsByPatientRecordId(@PathVariable UUID patientRecordId) {
        List<LabResultDto> labResults = patientService.getLabResultsByPatientRecordId(patientRecordId);
        return ResponseEntity.ok(ApiResponse.success(labResults, "Lab results retrieved successfully"));
    }

    @GetMapping("/lab-results/patient/{patientId}")
    @Operation(summary = "Get lab results by patient ID")
    public ResponseEntity<ApiResponse<List<LabResultDto>>> getLabResultsByPatientId(@PathVariable UUID patientId) {
        List<LabResultDto> labResults = patientService.getLabResultsByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(labResults, "Lab results retrieved successfully"));
    }

    @GetMapping("/lab-results/{id}")
    @Operation(summary = "Get lab result by ID")
    public ResponseEntity<ApiResponse<LabResultDto>> getLabResultById(@PathVariable UUID id) {
        return patientService.getLabResultById(id)
                .map(labResult -> ResponseEntity.ok(ApiResponse.success(labResult, "Lab result retrieved successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/lab-results/{id}")
    @Operation(summary = "Update lab result")
    public ResponseEntity<ApiResponse<LabResultDto>> updateLabResult(@PathVariable UUID id, @Valid @RequestBody LabResultDto labResultDto) {
        return patientService.updateLabResult(id, labResultDto)
                .map(updatedLabResult -> ResponseEntity.ok(ApiResponse.success(updatedLabResult, "Lab result updated successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/lab-results/{id}")
    @Operation(summary = "Delete lab result")
    public ResponseEntity<ApiResponse<Void>> deleteLabResult(@PathVariable UUID id) {
        boolean deleted = patientService.deleteLabResult(id);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success(null, "Lab result deleted successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Imaging Study endpoints

    @PostMapping("/imaging-studies")
    @Operation(summary = "Create a new imaging study")
    public ResponseEntity<ApiResponse<ImagingStudyDto>> createImagingStudy(@Valid @RequestBody ImagingStudyDto imagingStudyDto) {
        ImagingStudyDto createdImagingStudy = patientService.createImagingStudy(imagingStudyDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdImagingStudy, "Imaging study created successfully"));
    }

    @GetMapping("/imaging-studies/patient-record/{patientRecordId}")
    @Operation(summary = "Get imaging studies by patient record ID")
    public ResponseEntity<ApiResponse<List<ImagingStudyDto>>> getImagingStudiesByPatientRecordId(@PathVariable UUID patientRecordId) {
        List<ImagingStudyDto> imagingStudies = patientService.getImagingStudiesByPatientRecordId(patientRecordId);
        return ResponseEntity.ok(ApiResponse.success(imagingStudies, "Imaging studies retrieved successfully"));
    }

    @GetMapping("/imaging-studies/patient/{patientId}")
    @Operation(summary = "Get imaging studies by patient ID")
    public ResponseEntity<ApiResponse<List<ImagingStudyDto>>> getImagingStudiesByPatientId(@PathVariable UUID patientId) {
        List<ImagingStudyDto> imagingStudies = patientService.getImagingStudiesByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(imagingStudies, "Imaging studies retrieved successfully"));
    }

    // Vital Signs endpoints

    @PostMapping("/vital-signs")
    @Operation(summary = "Create new vital signs")
    public ResponseEntity<ApiResponse<VitalSignsDto>> createVitalSigns(@Valid @RequestBody VitalSignsDto vitalSignsDto) {
        VitalSignsDto createdVitalSigns = patientService.createVitalSigns(vitalSignsDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdVitalSigns, "Vital signs created successfully"));
    }

    @GetMapping("/vital-signs/patient-record/{patientRecordId}")
    @Operation(summary = "Get vital signs by patient record ID")
    public ResponseEntity<ApiResponse<List<VitalSignsDto>>> getVitalSignsByPatientRecordId(@PathVariable UUID patientRecordId) {
        List<VitalSignsDto> vitalSigns = patientService.getVitalSignsByPatientRecordId(patientRecordId);
        return ResponseEntity.ok(ApiResponse.success(vitalSigns, "Vital signs retrieved successfully"));
    }

    @GetMapping("/vital-signs/patient/{patientId}")
    @Operation(summary = "Get vital signs by patient ID")
    public ResponseEntity<ApiResponse<List<VitalSignsDto>>> getVitalSignsByPatientId(@PathVariable UUID patientId) {
        List<VitalSignsDto> vitalSigns = patientService.getVitalSignsByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(vitalSigns, "Vital signs retrieved successfully"));
    }

    // Medication endpoints

    @PostMapping("/medications")
    @Operation(summary = "Create a new medication")
    public ResponseEntity<ApiResponse<MedicationDto>> createMedication(@Valid @RequestBody MedicationDto medicationDto) {
        MedicationDto createdMedication = patientService.createMedication(medicationDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdMedication, "Medication created successfully"));
    }

    @GetMapping("/medications/patient-record/{patientRecordId}")
    @Operation(summary = "Get medications by patient record ID")
    public ResponseEntity<ApiResponse<List<MedicationDto>>> getMedicationsByPatientRecordId(@PathVariable UUID patientRecordId) {
        List<MedicationDto> medications = patientService.getMedicationsByPatientRecordId(patientRecordId);
        return ResponseEntity.ok(ApiResponse.success(medications, "Medications retrieved successfully"));
    }

    @GetMapping("/medications/patient/{patientId}")
    @Operation(summary = "Get medications by patient ID")
    public ResponseEntity<ApiResponse<List<MedicationDto>>> getMedicationsByPatientId(@PathVariable UUID patientId) {
        List<MedicationDto> medications = patientService.getMedicationsByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(medications, "Medications retrieved successfully"));
    }

    @GetMapping("/medications/active/patient-record/{patientRecordId}")
    @Operation(summary = "Get active medications by patient record ID")
    public ResponseEntity<ApiResponse<List<MedicationDto>>> getActiveMedicationsByPatientRecordId(@PathVariable UUID patientRecordId) {
        List<MedicationDto> medications = patientService.getActiveMedicationsByPatientRecordId(patientRecordId);
        return ResponseEntity.ok(ApiResponse.success(medications, "Active medications retrieved successfully"));
    }

    // Allergy endpoints

    @PostMapping("/allergies")
    @Operation(summary = "Create a new allergy")
    public ResponseEntity<ApiResponse<AllergyDto>> createAllergy(@Valid @RequestBody AllergyDto allergyDto) {
        AllergyDto createdAllergy = patientService.createAllergy(allergyDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdAllergy, "Allergy created successfully"));
    }

    @GetMapping("/allergies/patient-record/{patientRecordId}")
    @Operation(summary = "Get allergies by patient record ID")
    public ResponseEntity<ApiResponse<List<AllergyDto>>> getAllergiesByPatientRecordId(@PathVariable UUID patientRecordId) {
        List<AllergyDto> allergies = patientService.getAllergiesByPatientRecordId(patientRecordId);
        return ResponseEntity.ok(ApiResponse.success(allergies, "Allergies retrieved successfully"));
    }

    @GetMapping("/allergies/patient/{patientId}")
    @Operation(summary = "Get allergies by patient ID")
    public ResponseEntity<ApiResponse<List<AllergyDto>>> getAllergiesByPatientId(@PathVariable UUID patientId) {
        List<AllergyDto> allergies = patientService.getAllergiesByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(allergies, "Allergies retrieved successfully"));
    }

    @GetMapping("/allergies/active/patient-record/{patientRecordId}")
    @Operation(summary = "Get active allergies by patient record ID")
    public ResponseEntity<ApiResponse<List<AllergyDto>>> getActiveAllergiesByPatientRecordId(@PathVariable UUID patientRecordId) {
        List<AllergyDto> allergies = patientService.getActiveAllergiesByPatientRecordId(patientRecordId);
        return ResponseEntity.ok(ApiResponse.success(allergies, "Active allergies retrieved successfully"));
    }
}