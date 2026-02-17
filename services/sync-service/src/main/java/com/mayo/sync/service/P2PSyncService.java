package com.mayo.sync.service;

import com.mayo.sync.dto.DeltaSyncRequest;
import com.mayo.sync.dto.DeltaSyncResponse;
import com.mayo.sync.dto.VersionSummary;
import com.mayo.sync.entity.Device;
import com.mayo.sync.entity.MedicalRecordVersion;
import com.mayo.sync.repository.DeviceRepository;
import com.mayo.sync.repository.MedicalRecordVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * P2P Sync Service for doctor devices with latest-only version transfer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class P2PSyncService {

    private final MedicalRecordVersionRepository versionRepository;
    private final DeviceRepository deviceRepository;
    private final VersioningService versioningService;

    /**
     * Get latest versions for P2P sync (doctors only get latest, not history)
     */
    public List<MedicalRecordVersion> getLatestVersionsForP2P(UUID userId, String deviceId, Long lastSeenVersion) {
        // Verify device belongs to user
        Device device = deviceRepository.findByDeviceId(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!device.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Device does not belong to user");
        }

        // For doctor devices, only return versions newer than last seen
        // This implements the "latest-only" policy for doctors
        List<MedicalRecordVersion> newVersions = versionRepository.findVersionsByDeviceAfterVersion(deviceId, lastSeenVersion);

        // Filter to only include latest versions per record (not history)
        return newVersions.stream()
            .collect(Collectors.groupingBy(
                v -> v.getRecordType() + ":" + v.getRecordId(),
                Collectors.maxBy((v1, v2) -> Long.compare(v1.getVersion(), v2.getVersion()))
            ))
            .values()
            .stream()
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    /**
     * Process P2P delta sync request from doctor device
     */
    @Transactional
    public DeltaSyncResponse processP2PDeltaSync(DeltaSyncRequest request) {
        UUID userId = UUID.fromString(request.getUserId());

        log.debug("Starting P2P delta sync for device {} with lastSyncVersion {}", request.getDeviceId(), request.getLastSyncVersion());

        try {
            // Get latest versions for this doctor device
            List<MedicalRecordVersion> latestVersions = getLatestVersionsForP2P(
                userId,
                request.getDeviceId(),
                request.getLastSyncVersion()
            );

            log.debug("Retrieved {} latest versions for P2P sync", latestVersions.size());

            DeltaSyncResponse response = new DeltaSyncResponse();
            response.setStatus("SUCCESS");
            response.setProcessedChanges(latestVersions.size());
            response.setTotalChanges((long) latestVersions.size());
            response.setLatestVersions(latestVersions.stream()
                .map(this::mapToVersionSummary)
                .collect(Collectors.toList()));

            log.info("P2P delta sync completed for doctor device {}: {} latest versions", request.getDeviceId(), latestVersions.size());

            return response;

        } catch (Exception e) {
            log.error("P2P delta sync failed for device {}", request.getDeviceId(), e);
            DeltaSyncResponse errorResponse = new DeltaSyncResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage(e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Validate that P2P sync maintains version chain integrity
     */
    public boolean validateP2PSyncIntegrity(UUID userId, String recordType, String recordId) {
        return versioningService.validateChainIntegrity(userId, recordType, recordId);
    }

    /**
     * Get P2P sync statistics for a doctor device
     */
    public P2PSyncStats getP2PSyncStats(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        Long totalVersions = versionRepository.countVersionsForRecord(
            device.getUserId(), "PATIENT", "*" // This would need to be adjusted for actual counting
        );

        return new P2PSyncStats(totalVersions != null ? totalVersions : 0L, 0L, 0L);
    }

    // ===== PRIVATE HELPER METHODS =====

    private VersionSummary mapToVersionSummary(MedicalRecordVersion version) {
        return new VersionSummary(
            version.getRecordType(),
            version.getRecordId(),
            version.getVersion(),
            version.getContentHash(),
            version.getCreatedAt(),
            version.getDoctorUserId()
        );
    }


    public static class P2PSyncStats {
        private final Long totalVersions;
        private final Long syncedVersions;
        private final Long pendingVersions;

        public P2PSyncStats(Long totalVersions, Long syncedVersions, Long pendingVersions) {
            this.totalVersions = totalVersions;
            this.syncedVersions = syncedVersions;
            this.pendingVersions = pendingVersions;
        }

        public Long getTotalVersions() { return totalVersions; }
        public Long getSyncedVersions() { return syncedVersions; }
        public Long getPendingVersions() { return pendingVersions; }
    }
}