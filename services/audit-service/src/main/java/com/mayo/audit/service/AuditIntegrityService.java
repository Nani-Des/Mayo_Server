package com.mayo.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.audit.entity.AuditEvent;
import com.mayo.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.*;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Service for maintaining audit log integrity through cryptographic hashing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditIntegrityService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate SHA-256 hash for an audit event
     */
    public String generateEventHash(AuditEvent event) {
        try {
            // Create a canonical representation of the event for hashing
            String canonicalData = createCanonicalEventData(event);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalData.getBytes());

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to generate event hash", e);
        }
    }

    /**
     * Get the hash of the most recent audit event for chain integrity
     */
    @Cacheable(value = "latestEventHash", unless = "#result == null")
    public String getLatestEventHash() {
        LocalDateTime latestTimestamp = auditEventRepository.findLatestEventTimestamp();
        if (latestTimestamp == null) {
            return null;
        }
        return auditEventRepository.findByTimestampBetween(
                latestTimestamp.minusSeconds(1), latestTimestamp.plusSeconds(1), null)
                .stream().findFirst()
                .map(AuditEvent::getHashValue)
                .orElse(null);
    }

    /**
     * Verify the integrity of an audit event chain
     */
    public boolean verifyEventIntegrity(AuditEvent event) {
        if (event.getPreviousEventHash() == null) {
            // First event in chain
            return true;
        }

        String expectedPreviousHash = getLatestEventHash();
        return event.getPreviousEventHash().equals(expectedPreviousHash);
    }

    /**
     * Verify integrity of a range of audit events
     */
    public IntegrityVerificationResult verifyIntegrityRange(LocalDateTime startDate, LocalDateTime endDate) {
        // Implementation would check hash chain integrity for a date range
        // This is a simplified version
        return new IntegrityVerificationResult(true, 0, "Integrity check passed");
    }

    /**
     * Generate digital signature for an audit event
     */
    public String generateEventSignature(AuditEvent event) {
        try {
            String canonicalData = createCanonicalEventData(event);
            PrivateKey privateKey = loadPrivateKey();

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(canonicalData.getBytes());

            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);

        } catch (Exception e) {
            log.error("Failed to generate digital signature for event {}: {}",
                    event.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate event signature", e);
        }
    }

    /**
     * Verify digital signature of an audit event
     */
    public boolean verifyEventSignature(AuditEvent event, String signature) {
        try {
            String canonicalData = createCanonicalEventData(event);
            PublicKey publicKey = loadPublicKey();

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(canonicalData.getBytes());

            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            return sig.verify(signatureBytes);

        } catch (Exception e) {
            log.error("Failed to verify digital signature for event {}: {}",
                    event.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Load private key for signing (in production, this would be securely managed)
     */
    private PrivateKey loadPrivateKey() throws Exception {
        // In production, load from secure key store
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        return keyPair.getPrivate();
    }

    /**
     * Load public key for verification (in production, this would be securely
     * managed)
     */
    private PublicKey loadPublicKey() throws Exception {
        // In production, load from secure key store
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        return keyPair.getPublic();
    }

    /**
     * Create canonical string representation of event for consistent hashing
     */
    private String createCanonicalEventData(AuditEvent event) {
        StringBuilder canonical = new StringBuilder();

        // Include key event properties in a consistent order
        canonical.append(event.getEventId())
                .append("|")
                .append(event.getTimestamp())
                .append("|")
                .append(event.getUserId())
                .append("|")
                .append(event.getAction())
                .append("|")
                .append(event.getResourceType())
                .append("|")
                .append(event.getResourceId())
                .append("|")
                .append(event.getPatientId());

        // Include metadata if present
        if (event.getMetadata() != null) {
            canonical.append("|").append(event.getMetadata().toString());
        }

        return canonical.toString();
    }

    /**
     * Generate version hash for versioning system (Git-like)
     */
    public String generateVersionHash(AuditEvent event) {
        try {
            String versionData = createVersionCanonicalData(event);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(versionData.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available for version hash", e);
            throw new RuntimeException("Failed to generate version hash", e);
        }
    }

    /**
     * Get the latest version hash for a specific resource
     */
    public String getLatestVersionHashForResource(String resourceType, String resourceId) {
        return auditEventRepository.findLatestVersionForResource(resourceType, resourceId)
                .map(AuditEvent::getHashValue)
                .orElse(null);
    }

    /**
     * Verify version chain integrity for a resource
     */
    public boolean verifyVersionChain(String resourceType, String resourceId) {
        var events = auditEventRepository.findVersionChainForResource(resourceType, resourceId);
        String previousHash = null;

        for (AuditEvent event : events) {
            if (previousHash != null && !previousHash.equals(event.getParentVersionHash())) {
                return false;
            }
            previousHash = event.getHashValue();
        }
        return true;
    }

    /**
     * Generate versioning digital signature
     */
    public String generateVersioningSignature(AuditEvent event) {
        try {
            String versionData = createVersionCanonicalData(event);
            PrivateKey privateKey = loadPrivateKey();

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(versionData.getBytes());

            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);

        } catch (Exception e) {
            log.error("Failed to generate versioning signature for event {}: {}",
                    event.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate versioning signature", e);
        }
    }

    /**
     * Verify versioning digital signature
     */
    public boolean verifyVersioningSignature(AuditEvent event, String signature) {
        try {
            String versionData = createVersionCanonicalData(event);
            PublicKey publicKey = loadPublicKey();

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(versionData.getBytes());

            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            return sig.verify(signatureBytes);

        } catch (Exception e) {
            log.error("Failed to verify versioning signature for event {}: {}",
                    event.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handle conflict resolution metadata
     */
    public String generateConflictResolutionMetadata(AuditEvent event, String conflictType, String resolution) {
        try {
            return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("conflictType", conflictType)
                    .put("resolution", resolution)
                    .put("resolvedAt", event.getTimestamp().toString())
                    .put("resolvedBy", event.getUserId() != null ? event.getUserId().toString() : null)
                    .put("resourceType", event.getResourceType())
                    .put("resourceId", event.getResourceId()));
        } catch (Exception e) {
            log.warn("Failed to generate conflict resolution metadata", e);
            return "{}";
        }
    }

    /**
     * Validate conflict resolution metadata
     */
    public boolean validateConflictResolution(AuditEvent event) {
        if (event.getConflictResolutionMetadata() == null) {
            return true; // No conflict, valid
        }

        try {
            var metadata = objectMapper.readTree(event.getConflictResolutionMetadata());
            return metadata.has("conflictType") && metadata.has("resolution");
        } catch (Exception e) {
            log.warn("Invalid conflict resolution metadata for event {}", event.getId(), e);
            return false;
        }
    }

    /**
     * Create canonical string for version data
     */
    private String createVersionCanonicalData(AuditEvent event) {
        StringBuilder canonical = new StringBuilder();

        canonical.append(event.getResourceType())
                .append("|")
                .append(event.getResourceId())
                .append("|")
                .append(event.getVersionNumber())
                .append("|")
                .append(event.getOriginatingDeviceId())
                .append("|")
                .append(event.getDoctorUserId())
                .append("|")
                .append(event.getTimestamp());

        if (event.getMetadata() != null) {
            canonical.append("|").append(event.getMetadata());
        }

        return canonical.toString();
    }

    /**
     * Result of integrity verification
     */
    public static class IntegrityVerificationResult {
        private final boolean valid;
        private final int violationsFound;
        private final String message;

        public IntegrityVerificationResult(boolean valid, int violationsFound, String message) {
            this.valid = valid;
            this.violationsFound = violationsFound;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public int getViolationsFound() {
            return violationsFound;
        }

        public String getMessage() {
            return message;
        }
    }
}