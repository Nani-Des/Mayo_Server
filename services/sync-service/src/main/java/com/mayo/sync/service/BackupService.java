package com.mayo.sync.service;

import com.mayo.sync.entity.MedicalRecordVersion;
import com.mayo.sync.entity.UserBackupPreferences;
import com.mayo.sync.repository.MedicalRecordVersionRepository;
import com.mayo.sync.repository.UserBackupPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling user opt-in server backup processes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final UserBackupPreferencesRepository backupPreferencesRepository;
    private final MedicalRecordVersionRepository versionRepository;
    private final VersioningService versioningService;

    /**
     * Perform user backup with consent validation
     */
    @Transactional
    public BackupResult performUserBackup(UUID userId, String deviceId) {
        try {
            // Validate user consent for backup
            UserBackupPreferences preferences = backupPreferencesRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User has not consented to backup"));

            if (!preferences.getBackupEnabled()) {
                throw new IllegalStateException("User backup is disabled");
            }

            // Check if backup is due based on frequency
            if (!isBackupDue(preferences)) {
                return BackupResult.notDue();
            }

            // Get all medical record versions for the user
            List<MedicalRecordVersion> userVersions = versionRepository.findVersionsAfter(userId, 0L);

            // Create encrypted backup (simplified - in real implementation would use proper encryption)
            String encryptedBackup = createEncryptedBackup(userVersions, preferences.getEncryptionKeyHash());

            // Store backup (in real implementation, this would go to MinIO or similar)
            String backupId = storeBackup(encryptedBackup, userId);

            // Update last backup timestamp
            preferences.setLastBackupAt(LocalDateTime.now());
            backupPreferencesRepository.save(preferences);

            // Log audit event
            logAuditEvent(userId, "BACKUP_COMPLETED", "Backup completed successfully with " + userVersions.size() + " versions");

            log.info("Backup completed for user {}: {} versions backed up", userId, userVersions.size());

            return BackupResult.success(backupId, userVersions.size());

        } catch (Exception e) {
            log.error("Backup failed for user {}", userId, e);

            // Log audit event for failed backup
            logAuditEvent(userId, "BACKUP_FAILED", e.getMessage());

            return BackupResult.failure(e.getMessage());
        }
    }

    /**
     * Update user backup preferences (opt-in/opt-out)
     */
    @Transactional
    public void updateBackupPreferences(UUID userId, boolean enabled, String encryptionKey, int retentionYears) {
        UserBackupPreferences preferences = backupPreferencesRepository.findByUserId(userId)
            .orElse(UserBackupPreferences.builder()
                .userId(userId)
                .backupEnabled(false)
                .createdAt(LocalDateTime.now())
                .build());

        preferences.setBackupEnabled(enabled);
        if (encryptionKey != null) {
            preferences.setEncryptionKeyHash(encryptionKey);
        }
        preferences.setRetentionYears(retentionYears);
        preferences.setUpdatedAt(LocalDateTime.now());

        backupPreferencesRepository.save(preferences);

        log.info("Updated backup preferences for user {}: enabled={}, retention={} years", userId, enabled, retentionYears);

        // Log audit event
        logAuditEvent(userId, "BACKUP_PREFERENCES_UPDATED",
            "Backup " + (enabled ? "enabled" : "disabled") + ", retention: " + retentionYears + " years");
    }

    /**
     * Get user backup status
     */
    public BackupStatus getBackupStatus(UUID userId) {
        UserBackupPreferences preferences = backupPreferencesRepository.findByUserId(userId)
            .orElse(null);

        if (preferences == null) {
            return new BackupStatus(false, null, null, "Not configured");
        }

        String statusMessage;
        if (!preferences.getBackupEnabled()) {
            statusMessage = "Disabled";
        } else if (preferences.getLastBackupAt() == null) {
            statusMessage = "Never backed up";
        } else {
            statusMessage = "Last backup: " + preferences.getLastBackupAt();
        }

        return new BackupStatus(
            preferences.getBackupEnabled(),
            preferences.getLastBackupAt(),
            preferences.getRetentionYears(),
            statusMessage
        );
    }

    /**
     * Validate backup integrity
     */
    public boolean validateBackupIntegrity(UUID userId, String backupId) {
        try {
            // In real implementation, retrieve and validate backup
            // For now, just check if user has backup preferences
            UserBackupPreferences preferences = backupPreferencesRepository.findByUserId(userId)
                .orElse(null);

            return preferences != null && preferences.getBackupEnabled();
        } catch (Exception e) {
            log.error("Backup integrity validation failed for user {}", userId, e);
            return false;
        }
    }

    // ===== PRIVATE HELPER METHODS =====

    private boolean isBackupDue(UserBackupPreferences preferences) {
        if (preferences.getLastBackupAt() == null) {
            return true; // Never backed up
        }

        // Check based on frequency (simplified - daily backups)
        LocalDateTime nextBackup = preferences.getLastBackupAt().plusDays(1);
        return LocalDateTime.now().isAfter(nextBackup);
    }

    private String createEncryptedBackup(List<MedicalRecordVersion> versions, String encryptionKey) {
        // Simplified encryption - in real implementation use proper encryption
        try {
            String backupData = versions.stream()
                .map(v -> String.format("%s|%s|%s|%s",
                    v.getRecordType(), v.getRecordId(), v.getVersion(), v.getContent()))
                .reduce("", (a, b) -> a + "\n" + b);

            // Simple XOR encryption for demo (NOT secure for production)
            return simpleEncrypt(backupData, encryptionKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create encrypted backup", e);
        }
    }

    private String storeBackup(String encryptedBackup, UUID userId) {
        // In real implementation, store in MinIO/S3
        // For now, just return a mock backup ID
        String backupId = "backup_" + userId + "_" + System.currentTimeMillis();
        log.info("Mock backup stored with ID: {}", backupId);
        return backupId;
    }

    private void logAuditEvent(UUID userId, String eventType, String details) {
        // In real implementation, log to audit service
        log.info("Audit event for user {}: {} - {}", userId, eventType, details);
    }

    private String simpleEncrypt(String data, String key) {
        // Very basic XOR encryption for demo purposes only
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            result.append((char) (data.charAt(i) ^ key.charAt(i % key.length())));
        }
        return result.toString();
    }

    // ===== INNER CLASSES =====

    public static class BackupResult {
        private final boolean success;
        private final String backupId;
        private final Integer versionCount;
        private final String errorMessage;

        private BackupResult(boolean success, String backupId, Integer versionCount, String errorMessage) {
            this.success = success;
            this.backupId = backupId;
            this.versionCount = versionCount;
            this.errorMessage = errorMessage;
        }

        public static BackupResult success(String backupId, int versionCount) {
            return new BackupResult(true, backupId, versionCount, null);
        }

        public static BackupResult failure(String errorMessage) {
            return new BackupResult(false, null, null, errorMessage);
        }

        public static BackupResult notDue() {
            return new BackupResult(false, null, null, "Backup not due yet");
        }

        public boolean isSuccess() { return success; }
        public String getBackupId() { return backupId; }
        public Integer getVersionCount() { return versionCount; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class BackupStatus {
        private final boolean enabled;
        private final LocalDateTime lastBackup;
        private final Integer retentionYears;
        private final String statusMessage;

        public BackupStatus(boolean enabled, LocalDateTime lastBackup, Integer retentionYears, String statusMessage) {
            this.enabled = enabled;
            this.lastBackup = lastBackup;
            this.retentionYears = retentionYears;
            this.statusMessage = statusMessage;
        }

        public boolean isEnabled() { return enabled; }
        public LocalDateTime getLastBackup() { return lastBackup; }
        public Integer getRetentionYears() { return retentionYears; }
        public String getStatusMessage() { return statusMessage; }
    }
}