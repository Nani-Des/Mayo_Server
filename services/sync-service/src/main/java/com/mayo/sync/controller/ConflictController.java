package com.mayo.sync.controller;

import com.mayo.sync.dto.ConflictDto;
import com.mayo.sync.dto.ConflictResolutionRequest;
import com.mayo.sync.entity.Conflict;
import com.mayo.sync.service.ConflictResolutionService;
import com.mayo.sync.repository.ConflictRepository;
import com.mayo.common.core.dto.ApiResponse;
import com.mayo.common.core.enums.Permission;
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
import java.util.stream.Collectors;

/**
 * REST controller for conflict resolution operations
 */
@RestController
@RequestMapping("/api/v1/conflicts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Conflict Resolution", description = "Conflict resolution APIs")
public class ConflictController {

    private final ConflictResolutionService conflictResolutionService;
    private final ConflictRepository conflictRepository;

    /**
     * Get all conflicts for a user
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all conflicts for a user")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<List<ConflictDto>>> getUserConflicts(@PathVariable UUID userId) {
        try {
            List<Conflict> conflicts = conflictRepository.findByUserId(userId);
            List<ConflictDto> conflictDtos = conflicts.stream()
                .map(this::mapToConflictDto)
                .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(conflictDtos, "User conflicts retrieved successfully"));
        } catch (Exception e) {
            log.error("Failed to retrieve conflicts for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve conflicts: " + e.getMessage()));
        }
    }

    /**
     * Get pending conflicts for a user
     */
    @GetMapping("/user/{userId}/pending")
    @Operation(summary = "Get pending conflicts for a user")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<List<ConflictDto>>> getPendingConflicts(@PathVariable UUID userId) {
        try {
            List<Conflict> conflicts = conflictRepository.findPendingConflictsByUser(userId);
            List<ConflictDto> conflictDtos = conflicts.stream()
                .map(this::mapToConflictDto)
                .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(conflictDtos, "Pending conflicts retrieved successfully"));
        } catch (Exception e) {
            log.error("Failed to retrieve pending conflicts for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve pending conflicts: " + e.getMessage()));
        }
    }

    /**
     * Get conflict by ID
     */
    @GetMapping("/{conflictId}")
    @Operation(summary = "Get conflict by ID")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<ConflictDto>> getConflict(@PathVariable UUID conflictId) {
        try {
            Conflict conflict = conflictRepository.findById(conflictId)
                .orElseThrow(() -> new IllegalArgumentException("Conflict not found: " + conflictId));

            ConflictDto conflictDto = mapToConflictDto(conflict);
            return ResponseEntity.ok(ApiResponse.success(conflictDto, "Conflict retrieved successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Conflict not found: {}", conflictId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to retrieve conflict {}: {}", conflictId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve conflict: " + e.getMessage()));
        }
    }

    /**
     * Resolve conflict manually
     */
    @PostMapping("/resolve")
    @Operation(summary = "Resolve conflict manually")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<String>> resolveConflict(@Valid @RequestBody ConflictResolutionRequest request) {
        try {
            conflictResolutionService.resolveConflict(request);
            return ResponseEntity.ok(ApiResponse.success("Conflict resolved successfully", "Conflict resolved successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Conflict resolution failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to resolve conflict: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to resolve conflict: " + e.getMessage()));
        }
    }

    /**
     * Auto-resolve conflict using last-write-wins strategy
     */
    @PostMapping("/{conflictId}/auto-resolve/last-write-wins")
    @Operation(summary = "Auto-resolve conflict using last-write-wins strategy")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<String>> autoResolveLastWriteWins(@PathVariable UUID conflictId) {
        try {
            conflictResolutionService.autoResolveLastWriteWins(conflictId);
            return ResponseEntity.ok(ApiResponse.success("Conflict auto-resolved using last-write-wins", "Conflict auto-resolved successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Auto-resolution failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to auto-resolve conflict {}: {}", conflictId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to auto-resolve conflict: " + e.getMessage()));
        }
    }

    /**
     * Auto-resolve conflict using merge-based strategy
     */
    @PostMapping("/{conflictId}/auto-resolve/merge")
    @Operation(summary = "Auto-resolve conflict using merge-based strategy")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<String>> autoResolveMerge(@PathVariable UUID conflictId) {
        try {
            conflictResolutionService.autoResolveMerge(conflictId);
            return ResponseEntity.ok(ApiResponse.success("Conflict auto-resolved using merge strategy", "Conflict auto-resolved successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Merge auto-resolution failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to merge-resolve conflict {}: {}", conflictId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to merge-resolve conflict: " + e.getMessage()));
        }
    }

    /**
     * Get conflict statistics for a user
     */
    @GetMapping("/user/{userId}/stats")
    @Operation(summary = "Get conflict statistics for a user")
    @PreAuthorize("hasAuthority('SYNC_MANAGE_DEVICES')")
    public ResponseEntity<ApiResponse<ConflictStatsDto>> getConflictStats(@PathVariable UUID userId) {
        try {
            long totalConflicts = conflictRepository.countByUserIdAndResolutionStatus(userId, Conflict.ResolutionStatus.PENDING) +
                                conflictRepository.countByUserIdAndResolutionStatus(userId, Conflict.ResolutionStatus.RESOLVED_LOCAL_WINS) +
                                conflictRepository.countByUserIdAndResolutionStatus(userId, Conflict.ResolutionStatus.RESOLVED_SERVER_WINS) +
                                conflictRepository.countByUserIdAndResolutionStatus(userId, Conflict.ResolutionStatus.RESOLVED_MERGED) +
                                conflictRepository.countByUserIdAndResolutionStatus(userId, Conflict.ResolutionStatus.RESOLVED_MANUAL);

            long pendingConflicts = conflictRepository.countByUserIdAndResolutionStatus(userId, Conflict.ResolutionStatus.PENDING);

            ConflictStatsDto stats = new ConflictStatsDto();
            stats.setTotalConflicts(totalConflicts);
            stats.setPendingConflicts(pendingConflicts);
            stats.setResolvedConflicts(totalConflicts - pendingConflicts);

            return ResponseEntity.ok(ApiResponse.success(stats, "Conflict statistics retrieved successfully"));
        } catch (Exception e) {
            log.error("Failed to retrieve conflict statistics for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve conflict statistics: " + e.getMessage()));
        }
    }

    private ConflictDto mapToConflictDto(Conflict conflict) {
        ConflictDto dto = new ConflictDto();
        dto.setId(conflict.getId());
        dto.setRecordId(conflict.getRecordId());
        dto.setRecordType(conflict.getRecordType());
        dto.setLocalVersion(conflict.getLocalVersion());
        dto.setServerVersion(conflict.getServerVersion());
        dto.setConflictType(ConflictDto.ConflictType.valueOf(conflict.getConflictType().name()));
        dto.setResolutionStatus(ConflictDto.ResolutionStatus.valueOf(conflict.getResolutionStatus().name()));
        dto.setLocalData(conflict.getLocalData());
        dto.setServerData(conflict.getServerData());
        return dto;
    }

    // Inner class for statistics DTO
    public static class ConflictStatsDto {
        private long totalConflicts;
        private long pendingConflicts;
        private long resolvedConflicts;

        // Getters and setters
        public long getTotalConflicts() { return totalConflicts; }
        public void setTotalConflicts(long totalConflicts) { this.totalConflicts = totalConflicts; }

        public long getPendingConflicts() { return pendingConflicts; }
        public void setPendingConflicts(long pendingConflicts) { this.pendingConflicts = pendingConflicts; }

        public long getResolvedConflicts() { return resolvedConflicts; }
        public void setResolvedConflicts(long resolvedConflicts) { this.resolvedConflicts = resolvedConflicts; }
    }
}