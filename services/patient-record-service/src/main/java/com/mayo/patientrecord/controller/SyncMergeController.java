package com.mayo.patientrecord.controller;

import com.mayo.common.core.dto.ApiResponse;
import com.mayo.patientrecord.dto.SyncMergeRequestDto;
import com.mayo.patientrecord.dto.SyncMergeResponseDto;
import com.mayo.patientrecord.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient-records/sync-merge")
@RequiredArgsConstructor
@Tag(name = "Sync Merge", description = "Atomic data synchronization APIs")
public class SyncMergeController {

    private final PatientService patientService;

    @PostMapping
    @Operation(summary = "Atomically merge patient data, medications, labs, and records")
    public ResponseEntity<ApiResponse<SyncMergeResponseDto>> syncMerge(@Valid @RequestBody SyncMergeRequestDto request) {
        SyncMergeResponseDto response = patientService.syncMerge(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Data merged and synchronized successfully"));
    }
}
