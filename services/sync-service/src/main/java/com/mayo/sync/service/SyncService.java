package com.mayo.sync.service;

import com.mayo.sync.dto.ConflictDto;
import com.mayo.sync.dto.DeltaChangeDto;
import com.mayo.sync.dto.SyncRequest;
import com.mayo.sync.dto.SyncResponse;
import com.mayo.sync.entity.*;
import com.mayo.sync.service.SyncProcessingResult;
import com.mayo.sync.repository.*;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final SyncSessionRepository syncSessionRepository;
    private final DeviceRepository deviceRepository;
    private final DeltaChangeRepository deltaChangeRepository;
    private final ConflictRepository conflictRepository;
    private final ConflictResolutionService conflictResolutionService;
    private final CrdtDocumentService crdtDocumentService;
    private final VersioningService versioningService;
    private final MedicalRecordVersionRepository medicalRecordVersionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.mayo.sync.grpc.SyncGrpcService syncGrpcService;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    private static final String SYNC_STATE_KEY_PREFIX = "sync:state:";

    @Transactional
    public SyncResponse performSync(SyncRequest request) {
        UUID userId = UUID.fromString(request.getUserId());

        // Validate device
        Device device = deviceRepository.findByDeviceId(request.getDeviceId())
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.getDeviceId()));

        if (!device.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Device does not belong to user");
        }

        // Create or get active sync session
        SyncSession session = getOrCreateSyncSession(userId, request.getDeviceId());

        try {
            // Get server changes since last sync using new versioning system
            List<MedicalRecordVersion> serverChanges = medicalRecordVersionRepository
                .findVersionsAfter(userId, request.getLastSyncVersion());

            // Process client changes using versioning system
            SyncProcessingResult processingResult = processClientChangesWithVersioning(request.getChanges(), userId, request.getDeviceId());

            // Update sync state
            Long maxVersion = versioningService.getMaxVersion(userId);
            updateSyncState(userId, request.getDeviceId(), maxVersion);

            // Mark session as completed
            completeSyncSession(session);

            SyncResponse response = new SyncResponse();
            response.setStatus("SUCCESS");
            response.setConflicts(processingResult.getConflicts().stream().map(this::mapToConflictDto).collect(Collectors.toList()));
            response.setServerChanges(serverChanges.stream().map(this::mapVersionToDeltaChangeDto).collect(Collectors.toList()));
            response.setNewVersion(maxVersion);

            // Publish sync completion event
            publishSyncEvent(userId, request.getDeviceId(), "SYNC_COMPLETED", serverChanges.size(), processingResult.getConflicts().size());

            // Send real-time update via gRPC if available
            if (syncGrpcService != null) {
                syncGrpcService.pushRealTimeUpdate(userId.toString(), "SYNC_COMPLETED",
                    "Sync completed with " + serverChanges.size() + " changes and " + processingResult.getConflicts().size() + " conflicts",
                    serverChanges.stream().map(this::mapVersionToDeltaChangeDto).collect(java.util.stream.Collectors.toList()));
            }

            // Send push notification via Firebase/APNs if available
            if (pushNotificationService != null) {
                pushNotificationService.sendSyncCompletionNotification(
                    userId.toString(), request.getDeviceId(), serverChanges.size(), processingResult.getConflicts().size());
            }

            log.info("Sync completed successfully for user {} device {}: {} server changes, {} conflicts",
                userId, request.getDeviceId(), serverChanges.size(), processingResult.getConflicts().size());

            return response;

        } catch (Exception e) {
            log.error("Sync failed for user {} device {}", userId, request.getDeviceId(), e);
            failSyncSession(session);
            throw e;
        }
    }

    private SyncSession getOrCreateSyncSession(UUID userId, String deviceId) {
        return syncSessionRepository
            .findByUserIdAndDeviceIdAndStatus(userId, deviceId, SyncSession.SyncStatus.ACTIVE)
            .orElseGet(() -> {
                SyncSession session = new SyncSession();
                session.setUserId(userId);
                session.setDeviceId(deviceId);
                session.setStatus(SyncSession.SyncStatus.ACTIVE);
                return syncSessionRepository.save(session);
            });
    }

    private SyncProcessingResult processClientChanges(List<DeltaChangeDto> clientChanges, UUID userId, String deviceId) {
        List<Conflict> conflicts = new java.util.ArrayList<>();
        List<String> processedCrdtDocuments = new java.util.ArrayList<>();

        for (DeltaChangeDto changeDto : clientChanges) {
            // Check if this is a CRDT-enabled change
            if (changeDto.getDocumentId() != null && crdtDocumentService.isDocumentCrdtEnabled(changeDto.getDocumentId())) {
                // Process CRDT change - merge automatically
                processCrdtChange(changeDto, userId, deviceId);
                processedCrdtDocuments.add(changeDto.getDocumentId());
            } else {
                // Process traditional delta change with conflict detection
                Conflict conflict = processTraditionalChange(changeDto, userId, deviceId);
                if (conflict != null) {
                    conflicts.add(conflict);
                }
            }
        }

        return new SyncProcessingResult(conflicts, processedCrdtDocuments);
    }

    private SyncProcessingResult processClientChangesWithVersioning(List<DeltaChangeDto> clientChanges, UUID userId, String deviceId) {
        List<Conflict> conflicts = new java.util.ArrayList<>();
        List<String> processedCrdtDocuments = new java.util.ArrayList<>();

        for (DeltaChangeDto changeDto : clientChanges) {
            // Check if this is a CRDT-enabled change
            if (changeDto.getDocumentId() != null && crdtDocumentService.isDocumentCrdtEnabled(changeDto.getDocumentId())) {
                // Process CRDT change - merge automatically
                processCrdtChange(changeDto, userId, deviceId);
                processedCrdtDocuments.add(changeDto.getDocumentId());
            } else {
                // Process change using new versioning system
                Conflict conflict = processVersionedChange(changeDto, userId, deviceId);
                if (conflict != null) {
                    conflicts.add(conflict);
                }
            }
        }

        return new SyncProcessingResult(conflicts, processedCrdtDocuments);
    }

    private void processCrdtChange(DeltaChangeDto changeDto, UUID userId, String deviceId) {
        try {
            // For CRDT changes, we merge the states automatically
            if (changeDto.getCrdtState() != null && changeDto.getCrdtState().length > 0) {
                crdtDocumentService.mergeCrdtStates(changeDto.getDocumentId(), changeDto.getCrdtState());

                // Record the CRDT change in delta_changes for audit/history
                Long nextVersion = getNextVersion(userId);
                crdtDocumentService.recordCrdtChange(
                    changeDto.getDocumentId(),
                    changeDto.getRecordId(),
                    changeDto.getRecordType(),
                    userId,
                    deviceId,
                    changeDto.getCrdtState(),
                    nextVersion
                );
            }
        } catch (Exception e) {
            log.error("Failed to process CRDT change for document {}", changeDto.getDocumentId(), e);
            throw new RuntimeException("CRDT merge failed", e);
        }
    }

    private Conflict processTraditionalChange(DeltaChangeDto changeDto, UUID userId, String deviceId) {
        try {
            // Check for existing changes to this record
            List<DeltaChange> existingChanges = deltaChangeRepository
                .findByRecordIdAndRecordTypeOrderByVersionDesc(changeDto.getRecordId(), changeDto.getRecordType());

            if (!existingChanges.isEmpty()) {
                DeltaChange latestChange = existingChanges.get(0);
                Long serverVersion = latestChange.getVersion();

                // If client version is older than server version, there's a conflict
                if (changeDto.getVersion() != null && changeDto.getVersion() < serverVersion) {
                    log.warn("Version conflict detected for record {}: client version {}, server version {}",
                        changeDto.getRecordId(), changeDto.getVersion(), serverVersion);

                    Conflict conflict = conflictResolutionService.detectConflict(
                        userId,
                        changeDto.getRecordId(),
                        changeDto.getVersion(),
                        serverVersion,
                        changeDto.getRecordType()
                    );

                    // Set conflict data
                    conflict.setLocalData(changeDto.getData());
                    conflict.setServerData(latestChange.getData());

                    Conflict savedConflict = conflictRepository.save(conflict);

                    // Publish conflict event
                    publishConflictEvent(userId, changeDto.getRecordId(), conflict.getConflictType().name());

                    return savedConflict;
                }
            }

            // No conflict - save the change
            Long nextVersion = getNextVersion(userId);
            DeltaChange deltaChange = new DeltaChange();
            deltaChange.setRecordId(changeDto.getRecordId());
            deltaChange.setRecordType(changeDto.getRecordType());
            deltaChange.setChangeType(DeltaChange.ChangeType.valueOf(changeDto.getChangeType().name()));
            deltaChange.setVersion(nextVersion);
            deltaChange.setUserId(userId);
            deltaChange.setDeviceId(deviceId);
            deltaChange.setData(changeDto.getData());
            deltaChange.setDocumentId(changeDto.getDocumentId());
            deltaChange.setCrdtState(changeDto.getCrdtState());
            deltaChange.setIsCrdtEnabled(changeDto.getIsCrdtEnabled() != null ? changeDto.getIsCrdtEnabled() : false);

            deltaChangeRepository.save(deltaChange);

            log.info("Processed traditional change for record {} with version {}", changeDto.getRecordId(), nextVersion);
            return null; // No conflict

        } catch (Exception e) {
            log.error("Failed to process traditional change for record {}", changeDto.getRecordId(), e);
            throw new RuntimeException("Failed to process traditional change", e);
        }
    }

    private Long getNextVersion(UUID userId) {
        Long maxVersion = deltaChangeRepository.findMaxVersionByUserId(userId);
        return maxVersion != null ? maxVersion + 1 : 1L;
    }

    private void updateSyncState(UUID userId, String deviceId, Long version) {
        String key = SYNC_STATE_KEY_PREFIX + userId + ":" + deviceId;
        redisTemplate.opsForHash().put(key, "lastVersion", version);
        redisTemplate.opsForHash().put(key, "lastSync", LocalDateTime.now().toString());
    }

    private void completeSyncSession(SyncSession session) {
        session.setStatus(SyncSession.SyncStatus.COMPLETED);
        session.setSessionEnd(LocalDateTime.now());
        session.setLastSyncTimestamp(LocalDateTime.now());
        syncSessionRepository.save(session);
    }

    private void failSyncSession(SyncSession session) {
        session.setStatus(SyncSession.SyncStatus.FAILED);
        session.setSessionEnd(LocalDateTime.now());
        syncSessionRepository.save(session);
    }

    private Long getMaxVersion(List<DeltaChange> changes) {
        return changes.stream()
            .mapToLong(DeltaChange::getVersion)
            .max()
            .orElse(0L);
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

    private DeltaChangeDto mapToDeltaChangeDto(DeltaChange change) {
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
    }

    private DeltaChangeDto mapVersionToDeltaChangeDto(MedicalRecordVersion version) {
        DeltaChangeDto dto = new DeltaChangeDto();
        dto.setRecordId(version.getRecordId());
        dto.setRecordType(version.getRecordType());
        dto.setChangeType(DeltaChangeDto.ChangeType.valueOf(version.getChangeType()));
        dto.setVersion(version.getVersion());
        dto.setTimestamp(version.getCreatedAt());
        dto.setData(version.getContent());
        dto.setDocumentId(version.getRecordId()); // Use recordId as documentId for compatibility
        dto.setCrdtState(null); // Versioning system doesn't use CRDT state
        dto.setIsCrdtEnabled(false);
        return dto;
    }

    private Conflict processVersionedChange(DeltaChangeDto changeDto, UUID userId, String deviceId) {
        try {
            // Check for existing version of this record
            Optional<MedicalRecordVersion> existingVersion = versioningService.getLatestVersion(userId, changeDto.getRecordType(), changeDto.getRecordId());

            if (existingVersion.isPresent()) {
                MedicalRecordVersion serverVersion = existingVersion.get();
                Long serverVersionNum = serverVersion.getVersion();

                // If client version is older than server version, there's a conflict
                if (changeDto.getVersion() != null && changeDto.getVersion() < serverVersionNum) {
                    log.warn("Version conflict detected for record {}: client version {}, server version {}",
                        changeDto.getRecordId(), changeDto.getVersion(), serverVersionNum);

                    Conflict conflict = conflictResolutionService.detectConflict(
                        userId,
                        changeDto.getRecordId(),
                        changeDto.getVersion(),
                        serverVersionNum,
                        changeDto.getRecordType()
                    );

                    // Set conflict data
                    conflict.setLocalData(changeDto.getData());
                    conflict.setServerData(serverVersion.getContent());

                    Conflict savedConflict = conflictRepository.save(conflict);

                    // Publish conflict event
                    publishConflictEvent(userId, changeDto.getRecordId(), conflict.getConflictType().name());

                    return savedConflict;
                }
            }

            // No conflict - create new version
            versioningService.createVersion(
                userId,
                changeDto.getRecordType(),
                changeDto.getRecordId(),
                changeDto.getData(),
                changeDto.getChangeType().name(),
                deviceId,
                userId, // Assuming userId is the doctor for now
                null // No metadata for now
            );

            log.info("Created versioned change for record {} with type {}", changeDto.getRecordId(), changeDto.getChangeType());
            return null; // No conflict

        } catch (Exception e) {
            log.error("Failed to process versioned change for record {}", changeDto.getRecordId(), e);
            throw new RuntimeException("Failed to process versioned change", e);
        }
    }

    private void publishSyncEvent(UUID userId, String deviceId, String eventType, int changeCount, int conflictCount) {
        try {
            String eventMessage = String.format(
                "{\"userId\":\"%s\",\"deviceId\":\"%s\",\"eventType\":\"%s\",\"changeCount\":%d,\"conflictCount\":%d,\"timestamp\":\"%s\"}",
                userId, deviceId, eventType, changeCount, conflictCount, LocalDateTime.now()
            );
            kafkaTemplate.send(Topics.SYNC_EVENTS, userId.toString(), eventMessage);
            log.debug("Published sync event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish sync event for user {} device {}", userId, deviceId, e);
        }
    }

    private void publishConflictEvent(UUID userId, String recordId, String conflictType) {
        try {
            String eventMessage = String.format(
                "{\"userId\":\"%s\",\"recordId\":\"%s\",\"eventType\":\"CONFLICT_DETECTED\",\"conflictType\":\"%s\",\"timestamp\":\"%s\"}",
                userId, recordId, conflictType, LocalDateTime.now()
            );
            kafkaTemplate.send(Topics.SYNC_EVENTS, userId.toString(), eventMessage);
            log.debug("Published conflict event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish conflict event for user {} record {}", userId, recordId, e);
        }
    }
}