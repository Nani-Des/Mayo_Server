package com.mayo.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.sync.entity.MedicalRecordVersion;
import com.mayo.sync.repository.MedicalRecordVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VersioningService {

    private final MedicalRecordVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a new version for a medical record
     */
    @Transactional
    public MedicalRecordVersion createVersion(
            UUID userId,
            String recordType,
            String recordId,
            String content,
            String changeType,
            String originatingDeviceId,
            UUID doctorUserId,
            String metadata
    ) {
        // Get current version number
        Long currentVersion = getCurrentVersionNumber(userId, recordType, recordId);
        Long newVersion = currentVersion + 1;

        // Get parent version hash
        String parentHash = currentVersion > 0 ?
            getVersionHash(userId, recordType, recordId, currentVersion) : null;

        // Generate content hash
        String contentHash = generateContentHash(content, metadata, originatingDeviceId, doctorUserId, newVersion);

        // Create version
        MedicalRecordVersion version = MedicalRecordVersion.builder()
            .userId(userId)
            .recordType(recordType)
            .recordId(recordId)
            .version(newVersion)
            .parentVersionId(currentVersion > 0 ? getVersionId(userId, recordType, recordId, currentVersion) : null)
            .content(content)
            .metadata(metadata)
            .originatingDeviceId(originatingDeviceId)
            .updatedBy(doctorUserId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .contentHash(contentHash)
            .isLatest(true)
            .changeType(changeType)
            .doctorUserId(doctorUserId)
            .conflictResolutionStatus("none")
            .chainDepth(currentVersion.intValue() + 1)
            .parentHash(parentHash)
            .isValid(true)
            .lastVerified(LocalDateTime.now())
            .build();

        // Save version
        MedicalRecordVersion savedVersion = versionRepository.save(version);

        // Mark previous versions as not latest
        if (currentVersion > 0) {
            versionRepository.markPreviousVersionsAsNotLatest(userId, recordType, recordId, savedVersion.getId());
        }

        log.info("Created version {} for record {}/{} for user {}", newVersion, recordType, recordId, userId);
        return savedVersion;
    }

    /**
     * Get latest version for a record
     */
    public Optional<MedicalRecordVersion> getLatestVersion(UUID userId, String recordType, String recordId) {
        return versionRepository.findLatestVersion(userId, recordType, recordId);
    }

    /**
     * Get version history for a record
     */
    public List<MedicalRecordVersion> getVersionHistory(UUID userId, String recordType, String recordId) {
        return versionRepository.findVersionsByRecord(userId, recordType, recordId);
    }

    /**
     * Get versions after a specific version number (for delta sync)
     */
    public List<MedicalRecordVersion> getVersionsAfter(UUID userId, Long version) {
        return versionRepository.findVersionsAfter(userId, version);
    }

    /**
     * Get max version number for user
     */
    public Long getMaxVersion(UUID userId) {
        Long maxVersion = versionRepository.findMaxVersionByUserId(userId);
        return maxVersion != null ? maxVersion : 0L;
    }

    /**
     * Validate version chain integrity
     */
    public boolean validateChainIntegrity(UUID userId, String recordType, String recordId) {
        List<MedicalRecordVersion> versions = getVersionHistory(userId, recordType, recordId);

        for (int i = 0; i < versions.size(); i++) {
            MedicalRecordVersion version = versions.get(i);

            // Verify version hash
            String expectedHash = generateContentHash(
                version.getContent(),
                version.getMetadata(),
                version.getOriginatingDeviceId(),
                version.getDoctorUserId(),
                version.getVersion()
            );

            if (!expectedHash.equals(version.getContentHash())) {
                log.error("Version {} hash mismatch for record {}/{}", version.getVersion(), recordType, recordId);
                return false;
            }

            // Verify parent hash chain
            if (i > 0) {
                MedicalRecordVersion parentVersion = versions.get(i - 1);
                if (!parentVersion.getContentHash().equals(version.getParentHash())) {
                    log.error("Version chain broken at version {} for record {}/{}", version.getVersion(), recordType, recordId);
                    return false;
                }
            } else if (version.getParentHash() != null) {
                log.error("First version should not have parent hash for record {}/{}", recordType, recordId);
                return false;
            }
        }

        return true;
    }

    /**
     * Detect conflicts between versions
     */
    public ConflictDetectionResult detectConflicts(
            MedicalRecordVersion localVersion,
            MedicalRecordVersion remoteVersion
    ) {
        if (!localVersion.getVersion().equals(remoteVersion.getVersion())) {
            return ConflictDetectionResult.noConflict();
        }

        // Same version number - check content
        if (localVersion.getContentHash().equals(remoteVersion.getContentHash())) {
            return ConflictDetectionResult.noConflict();
        }

        // Find conflicting fields
        List<String> conflictingFields = findConflictingFields(localVersion.getContent(), remoteVersion.getContent());

        if (conflictingFields.isEmpty()) {
            return ConflictDetectionResult.noConflict();
        }

        return ConflictDetectionResult.conflict(conflictingFields);
    }

    /**
     * Resolve conflict with three-way merge
     */
    public String resolveConflictThreeWay(
            MedicalRecordVersion baseVersion,
            MedicalRecordVersion localVersion,
            MedicalRecordVersion remoteVersion
    ) {
        try {
            JsonNode baseContent = objectMapper.readTree(baseVersion.getContent());
            JsonNode localContent = objectMapper.readTree(localVersion.getContent());
            JsonNode remoteContent = objectMapper.readTree(remoteVersion.getContent());

            JsonNode mergedContent = mergeJsonNodes(baseContent, localContent, remoteContent);
            return objectMapper.writeValueAsString(mergedContent);
        } catch (Exception e) {
            log.error("Failed to perform three-way merge", e);
            throw new RuntimeException("Three-way merge failed", e);
        }
    }

    // ===== PRIVATE HELPER METHODS =====

    private Long getCurrentVersionNumber(UUID userId, String recordType, String recordId) {
        Optional<MedicalRecordVersion> latest = versionRepository.findLatestVersion(userId, recordType, recordId);
        return latest.map(MedicalRecordVersion::getVersion).orElse(0L);
    }

    private String getVersionHash(UUID userId, String recordType, String recordId, Long version) {
        List<MedicalRecordVersion> versions = versionRepository.findVersionsByRecord(userId, recordType, recordId);
        return versions.stream()
            .filter(v -> v.getVersion().equals(version))
            .map(MedicalRecordVersion::getContentHash)
            .findFirst()
            .orElse(null);
    }

    private UUID getVersionId(UUID userId, String recordType, String recordId, Long version) {
        List<MedicalRecordVersion> versions = versionRepository.findVersionsByRecord(userId, recordType, recordId);
        return versions.stream()
            .filter(v -> v.getVersion().equals(version))
            .map(MedicalRecordVersion::getId)
            .findFirst()
            .orElse(null);
    }

    private String generateContentHash(String content, String metadata, String deviceId, UUID doctorId, Long version) {
        try {
            String data = content + (metadata != null ? metadata : "") + deviceId +
                         (doctorId != null ? doctorId.toString() : "") + version.toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private List<String> findConflictingFields(String localContent, String remoteContent) {
        try {
            JsonNode local = objectMapper.readTree(localContent);
            JsonNode remote = objectMapper.readTree(remoteContent);

            return findConflictingFieldsRecursive(local, remote, "");
        } catch (Exception e) {
            log.error("Failed to parse content for conflict detection", e);
            return Collections.emptyList();
        }
    }

    private List<String> findConflictingFieldsRecursive(JsonNode local, JsonNode remote, String path) {
        List<String> conflicts = new ArrayList<>();

        Set<String> allFields = new HashSet<>();
        local.fieldNames().forEachRemaining(allFields::add);
        remote.fieldNames().forEachRemaining(allFields::add);

        for (String field : allFields) {
            String fieldPath = path.isEmpty() ? field : path + "." + field;
            JsonNode localValue = local.get(field);
            JsonNode remoteValue = remote.get(field);

            if (localValue == null && remoteValue != null) {
                conflicts.add(fieldPath);
            } else if (localValue != null && remoteValue == null) {
                conflicts.add(fieldPath);
            } else if (localValue != null && remoteValue != null) {
                if (localValue.isObject() && remoteValue.isObject()) {
                    conflicts.addAll(findConflictingFieldsRecursive(localValue, remoteValue, fieldPath));
                } else if (!localValue.equals(remoteValue)) {
                    conflicts.add(fieldPath);
                }
            }
        }

        return conflicts;
    }

    private JsonNode mergeJsonNodes(JsonNode base, JsonNode local, JsonNode remote) {
        // Simple three-way merge: prefer local changes, but keep remote additions
        Map<String, JsonNode> merged = new HashMap<>();

        // Start with base
        base.fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));

        // Apply local changes
        local.fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));

        // Apply remote changes that don't conflict
        remote.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!local.has(key) || local.get(key).equals(base.get(key))) {
                merged.put(key, entry.getValue());
            }
        });

        return objectMapper.valueToTree(merged);
    }

    // ===== INNER CLASSES =====

    public static class ConflictDetectionResult {
        private final boolean hasConflict;
        private final List<String> conflictingFields;

        private ConflictDetectionResult(boolean hasConflict, List<String> conflictingFields) {
            this.hasConflict = hasConflict;
            this.conflictingFields = conflictingFields;
        }

        public static ConflictDetectionResult noConflict() {
            return new ConflictDetectionResult(false, Collections.emptyList());
        }

        public static ConflictDetectionResult conflict(List<String> conflictingFields) {
            return new ConflictDetectionResult(true, conflictingFields);
        }

        public boolean hasConflict() { return hasConflict; }
        public List<String> getConflictingFields() { return conflictingFields; }
    }
}