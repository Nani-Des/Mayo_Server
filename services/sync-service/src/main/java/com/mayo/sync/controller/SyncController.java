package com.mayo.sync.controller;

import com.mayo.sync.dto.*;
import com.mayo.sync.service.SyncService;
import com.mayo.sync.service.CrdtDocumentService;
import com.mayo.sync.service.DeltaSyncService;
import com.mayo.sync.entity.CrdtDocument;
import com.mayo.sync.entity.Device;
import com.mayo.sync.entity.SyncSession;
import com.mayo.sync.entity.DeltaChange;
import com.mayo.sync.repository.DeviceRepository;
import com.mayo.sync.repository.SyncSessionRepository;
import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.core.enums.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for sync operations
 */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(name = "Sync Operations", description = "Synchronization APIs")
public class SyncController {

    private final SyncService syncService;
    private final CrdtDocumentService crdtDocumentService;
    private final DeltaSyncService deltaSyncService;
    private final DeviceRepository deviceRepository;
    private final SyncSessionRepository syncSessionRepository;

    /**
     * Perform bidirectional sync
     */
    @PostMapping("/sync")
    @Operation(summary = "Perform bidirectional sync")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA') and hasAuthority('SYNC_WRITE_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<SyncResponse>> performSync(@Valid @RequestBody SyncRequest request) {
        SyncResponse response = syncService.performSync(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Sync completed successfully"));
    }

    /**
     * Initiate sync session
     */
    @PostMapping("/initiate")
    @Operation(summary = "Initiate sync session")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA') and hasAuthority('SYNC_WRITE_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<String>> initiateSync(@Valid @RequestBody SyncRequest request) {
        try {
            UUID userId = UUID.fromString(request.getUserId());

            // Validate device
            Device device = deviceRepository.findByDeviceId(request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.getDeviceId()));

            if (!device.getUserId().equals(userId)) {
                throw new IllegalArgumentException("Device does not belong to user");
            }

            // Create sync session
            SyncSession session = new SyncSession();
            session.setUserId(userId);
            session.setDeviceId(request.getDeviceId());
            session.setStatus(SyncSession.SyncStatus.ACTIVE);
            syncSessionRepository.save(session);

            return ResponseEntity.ok(ApiResponse.success(session.getId().toString(), "Sync session initiated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to initiate sync session: " + e.getMessage()));
        }
    }

    /**
     * Get sync status
     */
    @GetMapping("/status/{sessionId}")
    @Operation(summary = "Get sync status")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<String>> getSyncStatus(@PathVariable UUID sessionId) {
        try {
            SyncSession session = syncSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sync session not found: " + sessionId));

            return ResponseEntity.ok(ApiResponse.success(session.getStatus().name(), "Sync status retrieved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get sync status: " + e.getMessage()));
        }
    }

    /**
     * Enhanced delta sync endpoint with optimized change detection and transfer
     */
    @PostMapping("/delta")
    @Operation(summary = "Enhanced delta sync with optimized change detection")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA') and hasAuthority('SYNC_WRITE_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<DeltaSyncResponse>> deltaSync(@Valid @RequestBody DeltaSyncRequest request) {
        try {
            UUID userId = UUID.fromString(request.getUserId());

            // Validate device
            Device device = deviceRepository.findByDeviceId(request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.getDeviceId()));

            if (!device.getUserId().equals(userId)) {
                throw new IllegalArgumentException("Device does not belong to user");
            }

            // Perform optimized delta sync
            DeltaSyncResponse response = deltaSyncService.performDeltaSync(request);

            return ResponseEntity.ok(ApiResponse.success(response, "Enhanced delta sync completed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Delta sync failed: " + e.getMessage()));
        }
    }

    /**
     * Get delta sync statistics for monitoring
     */
    @GetMapping("/delta/stats/{userId}")
    @Operation(summary = "Get delta sync statistics")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<DeltaSyncService.DeltaSyncStats>> getDeltaSyncStats(@PathVariable UUID userId) {
        try {
            DeltaSyncService.DeltaSyncStats stats = deltaSyncService.getSyncStats(userId);
            return ResponseEntity.ok(ApiResponse.success(stats, "Delta sync statistics retrieved successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get delta sync statistics: " + e.getMessage()));
        }
    }

    /**
     * Clear delta cache for a user (admin operation)
     */
    @PostMapping("/delta/cache/clear/{userId}")
    @Operation(summary = "Clear delta sync cache for user")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<String>> clearDeltaCache(@PathVariable UUID userId) {
        try {
            deltaSyncService.clearDeltaCache(userId);
            return ResponseEntity.ok(ApiResponse.success("Cache cleared successfully", "Delta sync cache cleared for user"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to clear delta cache: " + e.getMessage()));
        }
    }

    /**
     * Get CRDT document state
     */
    @GetMapping("/crdt/{documentId}")
    @Operation(summary = "Get CRDT document state")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<CrdtDocument>> getCrdtDocument(@PathVariable String documentId) {
        return crdtDocumentService.getDocument(documentId)
            .map(doc -> ResponseEntity.ok(ApiResponse.success(doc, "CRDT document retrieved successfully")))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Merge CRDT states for document synchronization
     */
    @PostMapping("/crdt/{documentId}/merge")
    @Operation(summary = "Merge CRDT states")
    @PreAuthorize("hasAuthority('SYNC_WRITE_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<String>> mergeCrdtStates(
            @PathVariable String documentId,
            @RequestBody byte[] crdtState) {
        try {
            crdtDocumentService.mergeCrdtStates(documentId, crdtState);
            return ResponseEntity.ok(ApiResponse.success("States merged successfully", "CRDT states merged successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Merge failed: " + e.getMessage()));
        }
    }

    /**
     * Get CRDT changes since a specific version
     */
    @GetMapping("/crdt/{documentId}/changes")
    @Operation(summary = "Get CRDT changes since version")
    @PreAuthorize("hasAuthority('SYNC_READ_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<List<DeltaChangeDto>>> getCrdtChangesSince(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "0") Long sinceVersion) {
        List<DeltaChangeDto> changes = crdtDocumentService.getCrdtChangesSince(documentId, sinceVersion)
            .stream()
            .map(change -> {
                DeltaChangeDto dto = new DeltaChangeDto();
                dto.setRecordId(change.getRecordId());
                dto.setRecordType(change.getRecordType());
                dto.setChangeType(DeltaChangeDto.ChangeType.valueOf(change.getChangeType().name()));
                dto.setVersion(change.getVersion());
                dto.setTimestamp(change.getTimestamp());
                dto.setData(change.getData());
                dto.setDocumentId(change.getDocumentId());
                dto.setCrdtState(change.getCrdtState());
                dto.setIsCrdtEnabled(change.getIsCrdtEnabled());
                return dto;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(changes, "CRDT changes retrieved successfully"));
    }

    /**
     * Create or update CRDT document
     */
    @PostMapping("/crdt/{documentId}")
    @Operation(summary = "Create or update CRDT document")
    @PreAuthorize("hasAuthority('SYNC_WRITE_DEVICE_DATA')")
    public ResponseEntity<ApiResponse<CrdtDocument>> createOrUpdateCrdtDocument(
            @PathVariable String documentId,
            @RequestParam String documentType,
            @RequestBody byte[] initialState) {
        CrdtDocument document = crdtDocumentService.createOrUpdateDocument(documentId, documentType, initialState);
        return ResponseEntity.ok(ApiResponse.success(document, "CRDT document created/updated successfully"));
    }

}