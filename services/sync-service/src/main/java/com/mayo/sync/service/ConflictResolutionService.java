package com.mayo.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.sync.dto.ConflictResolutionRequest;
import com.mayo.sync.entity.Conflict;
import com.mayo.sync.entity.MedicalRecordVersion;
import com.mayo.sync.repository.ConflictRepository;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictResolutionService {

    private final ConflictRepository conflictRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final VersioningService versioningService;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.mayo.sync.grpc.SyncGrpcService syncGrpcService;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    @Transactional
    public void resolveConflict(ConflictResolutionRequest request) {
        Conflict conflict = conflictRepository.findById(request.getConflictId())
            .orElseThrow(() -> new IllegalArgumentException("Conflict not found: " + request.getConflictId()));

        conflict.setResolutionStatus(Conflict.ResolutionStatus.valueOf(request.getResolution().name()));
        conflict.setResolvedData(request.getResolvedData());
        conflict.setResolvedAt(LocalDateTime.now());

        conflictRepository.save(conflict);

        // Send notification
        sendConflictResolutionNotification(conflict);

        log.info("Resolved conflict {} with resolution {}", conflict.getId(), request.getResolution());
    }

    public Conflict detectConflict(UUID userId, String recordId, Long localVersion, Long serverVersion, String recordType) {
        if (localVersion.equals(serverVersion)) {
            return null; // No conflict
        }

        Conflict conflict = new Conflict();
        conflict.setUserId(userId);
        conflict.setRecordId(recordId);
        conflict.setRecordType(recordType);
        conflict.setLocalVersion(localVersion);
        conflict.setServerVersion(serverVersion);
        conflict.setConflictType(Conflict.ConflictType.VERSION_CONFLICT);
        conflict.setResolutionStatus(Conflict.ResolutionStatus.PENDING);

        Conflict savedConflict = conflictRepository.save(conflict);

        // Send conflict notification
        sendConflictNotification(savedConflict);

        // Send real-time update via gRPC if available
        if (syncGrpcService != null) {
            syncGrpcService.pushRealTimeUpdate(userId.toString(), "CONFLICT_DETECTED",
                "Conflict detected for record " + recordId, null);
        }

        // Send push notification via Firebase/APNs if available
        if (pushNotificationService != null) {
            pushNotificationService.sendConflictNotification(userId.toString(), recordId, recordType, conflict.getConflictType().name());
        }

        log.info("Detected conflict for record {} of type {} for user {}", recordId, recordType, userId);
        return savedConflict;
    }

    @Transactional
    public void autoResolveLastWriteWins(UUID conflictId) {
        Conflict conflict = conflictRepository.findById(conflictId)
            .orElseThrow(() -> new IllegalArgumentException("Conflict not found: " + conflictId));

        if (conflict.getResolutionStatus() != Conflict.ResolutionStatus.PENDING) {
            throw new IllegalArgumentException("Conflict is already resolved");
        }

        // Determine winner based on version numbers (higher version wins)
        String resolvedData;
        Conflict.ResolutionStatus resolutionStatus;

        if (conflict.getLocalVersion() > conflict.getServerVersion()) {
            resolvedData = conflict.getLocalData();
            resolutionStatus = Conflict.ResolutionStatus.RESOLVED_LOCAL_WINS;
        } else {
            resolvedData = conflict.getServerData();
            resolutionStatus = Conflict.ResolutionStatus.RESOLVED_SERVER_WINS;
        }

        conflict.setResolutionStatus(resolutionStatus);
        conflict.setResolvedData(resolvedData);
        conflict.setResolvedAt(LocalDateTime.now());

        conflictRepository.save(conflict);

        // Send notification
        sendConflictResolutionNotification(conflict);

        // Send real-time update via gRPC if available
        if (syncGrpcService != null) {
            syncGrpcService.pushRealTimeUpdate(conflict.getUserId().toString(), "CONFLICT_RESOLVED",
                "Conflict resolved for record " + conflict.getRecordId(), null);
        }

        // Send push notification via Firebase/APNs if available
        if (pushNotificationService != null) {
            pushNotificationService.sendConflictResolutionNotification(
                conflict.getUserId().toString(), conflict.getRecordId(), conflict.getResolutionStatus().name());
        }

        log.info("Auto-resolved conflict {} using last-write-wins strategy: {}", conflictId, resolutionStatus);
    }

    @Transactional
    public void autoResolveMerge(UUID conflictId) {
        Conflict conflict = conflictRepository.findById(conflictId)
            .orElseThrow(() -> new IllegalArgumentException("Conflict not found: " + conflictId));

        if (conflict.getResolutionStatus() != Conflict.ResolutionStatus.PENDING) {
            throw new IllegalArgumentException("Conflict is already resolved");
        }

        try {
            // Get the version history to find base version for three-way merge
            List<MedicalRecordVersion> versionHistory = versioningService.getVersionHistory(
                conflict.getUserId(), conflict.getRecordType(), conflict.getRecordId());

            // Find the common ancestor (base version)
            MedicalRecordVersion baseVersion = findCommonAncestor(versionHistory, conflict.getLocalVersion(), conflict.getServerVersion());

            if (baseVersion != null) {
                // Create local and remote version objects for three-way merge
                MedicalRecordVersion localVersion = createVersionFromConflictData(conflict, conflict.getLocalData(), conflict.getLocalVersion());
                MedicalRecordVersion remoteVersion = createVersionFromConflictData(conflict, conflict.getServerData(), conflict.getServerVersion());

                // Perform three-way merge
                String mergedData = versioningService.resolveConflictThreeWay(baseVersion, localVersion, remoteVersion);

                conflict.setResolutionStatus(Conflict.ResolutionStatus.RESOLVED_MERGED);
                conflict.setResolvedData(mergedData);
                conflict.setResolvedAt(LocalDateTime.now());

                conflictRepository.save(conflict);

                // Create new merged version in the versioning system
                versioningService.createVersion(
                    conflict.getUserId(),
                    conflict.getRecordType(),
                    conflict.getRecordId(),
                    mergedData,
                    "MERGE",
                    "conflict-resolution-service", // System-generated
                    conflict.getUserId(),
                    "Auto-resolved conflict using three-way merge"
                );

                // Send notification
                sendConflictResolutionNotification(conflict);

                log.info("Auto-resolved conflict {} using three-way merge", conflictId);
            } else {
                // Fall back to simple merge if no common ancestor
                String mergedData = mergeJsonData(conflict.getLocalData(), conflict.getServerData());
                conflict.setResolutionStatus(Conflict.ResolutionStatus.RESOLVED_MERGED);
                conflict.setResolvedData(mergedData);
                conflict.setResolvedAt(LocalDateTime.now());

                conflictRepository.save(conflict);

                // Send notification
                sendConflictResolutionNotification(conflict);

                log.info("Auto-resolved conflict {} using fallback merge (no common ancestor)", conflictId);
            }
        } catch (Exception e) {
            log.error("Failed to merge conflict {}: {}", conflictId, e.getMessage());
            throw new IllegalArgumentException("Failed to merge conflicting data: " + e.getMessage());
        }
    }

    private String mergeJsonData(String localData, String serverData) throws Exception {
        if (localData == null || serverData == null) {
            return localData != null ? localData : serverData;
        }

        JsonNode localNode = objectMapper.readTree(localData);
        JsonNode serverNode = objectMapper.readTree(serverData);

        // Simple merge strategy: server data takes precedence for conflicts
        // In a real implementation, you might want more sophisticated merging logic
        JsonNode mergedNode = mergeJsonNodes(localNode, serverNode);

        return objectMapper.writeValueAsString(mergedNode);
    }

    private JsonNode mergeJsonNodes(JsonNode local, JsonNode server) {
        if (server.isObject() && local.isObject()) {
            // For objects, merge properties
            Map<String, JsonNode> mergedFields = new HashMap<>();

            // Add all local fields
            local.fields().forEachRemaining(entry -> mergedFields.put(entry.getKey(), entry.getValue()));

            // Override/add server fields
            server.fields().forEachRemaining(entry -> mergedFields.put(entry.getKey(), entry.getValue()));

            // Create merged object
            return objectMapper.valueToTree(mergedFields);
        } else {
            // For non-objects, server takes precedence
            return server;
        }
    }

    private void sendConflictNotification(Conflict conflict) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "CONFLICT_DETECTED");
            notification.put("conflictId", conflict.getId().toString());
            notification.put("userId", conflict.getUserId().toString());
            notification.put("recordId", conflict.getRecordId());
            notification.put("recordType", conflict.getRecordType());
            notification.put("conflictType", conflict.getConflictType().toString());
            notification.put("timestamp", LocalDateTime.now().toString());

            String message = objectMapper.writeValueAsString(notification);
            kafkaTemplate.send(Topics.NOTIFICATION_EVENTS, conflict.getUserId().toString(), message);

            log.debug("Sent conflict notification for conflict {}", conflict.getId());
        } catch (Exception e) {
            log.error("Failed to send conflict notification for conflict {}: {}", conflict.getId(), e.getMessage());
        }
    }

    private void sendConflictResolutionNotification(Conflict conflict) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "CONFLICT_RESOLVED");
            notification.put("conflictId", conflict.getId().toString());
            notification.put("userId", conflict.getUserId().toString());
            notification.put("recordId", conflict.getRecordId());
            notification.put("recordType", conflict.getRecordType());
            notification.put("resolutionStatus", conflict.getResolutionStatus().toString());
            notification.put("timestamp", LocalDateTime.now().toString());

            String message = objectMapper.writeValueAsString(notification);
            kafkaTemplate.send(Topics.NOTIFICATION_EVENTS, conflict.getUserId().toString(), message);

            log.debug("Sent conflict resolution notification for conflict {}", conflict.getId());
        } catch (Exception e) {
            log.error("Failed to send conflict resolution notification for conflict {}: {}", conflict.getId(), e.getMessage());
        }
    }

    /**
     * Find the common ancestor version for three-way merge
     */
    private MedicalRecordVersion findCommonAncestor(List<MedicalRecordVersion> versionHistory, Long localVersion, Long serverVersion) {
        // Find versions that are ancestors of both local and server versions
        Optional<MedicalRecordVersion> commonAncestor = versionHistory.stream()
            .filter(v -> v.getVersion() < Math.min(localVersion, serverVersion))
            .max((v1, v2) -> Long.compare(v1.getVersion(), v2.getVersion()));

        return commonAncestor.orElse(null);
    }

    /**
     * Create a MedicalRecordVersion object from conflict data for merge operations
     */
    private MedicalRecordVersion createVersionFromConflictData(Conflict conflict, String data, Long version) {
        return MedicalRecordVersion.builder()
            .userId(conflict.getUserId())
            .recordType(conflict.getRecordType())
            .recordId(conflict.getRecordId())
            .version(version)
            .content(data)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Detect version forks in the version chain
     */
    public List<ForkDetectionResult> detectVersionForks(UUID userId, String recordType, String recordId) {
        List<MedicalRecordVersion> versions = versioningService.getVersionHistory(userId, recordType, recordId);

        // Group versions by their parent to detect forks
        Map<UUID, List<MedicalRecordVersion>> versionsByParent = new HashMap<>();
        for (MedicalRecordVersion version : versions) {
            UUID parentId = version.getParentVersionId();
            if (parentId != null) {
                versionsByParent.computeIfAbsent(parentId, k -> new java.util.ArrayList<>()).add(version);
            }
        }

        List<ForkDetectionResult> forks = new java.util.ArrayList<>();
        for (Map.Entry<UUID, List<MedicalRecordVersion>> entry : versionsByParent.entrySet()) {
            if (entry.getValue().size() > 1) {
                // Multiple versions have the same parent - this is a fork
                forks.add(new ForkDetectionResult(entry.getKey(), entry.getValue()));
            }
        }

        return forks;
    }

    // ===== INNER CLASSES =====

    public static class ForkDetectionResult {
        private final UUID parentVersionId;
        private final List<MedicalRecordVersion> forkedVersions;

        public ForkDetectionResult(UUID parentVersionId, List<MedicalRecordVersion> forkedVersions) {
            this.parentVersionId = parentVersionId;
            this.forkedVersions = forkedVersions;
        }

        public UUID getParentVersionId() { return parentVersionId; }
        public List<MedicalRecordVersion> getForkedVersions() { return forkedVersions; }
        public int getForkCount() { return forkedVersions.size(); }
    }
}