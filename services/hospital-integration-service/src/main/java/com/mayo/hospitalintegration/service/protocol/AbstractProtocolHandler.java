package com.mayo.hospitalintegration.service.protocol;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for protocol handlers providing common functionality
 */
@Slf4j
public abstract class AbstractProtocolHandler implements ProtocolHandler {

    protected final Map<String, TransferStatus> activeTransfers = new ConcurrentHashMap<>();

    @Override
    public boolean initiateTransfer(DataTransferSession session, Map<String, Object> config) {
        try {
            ValidationResult validation = validateConfig(config);
            if (!validation.isValid()) {
                log.error("Invalid configuration for protocol {}: {}", getProtocol(), validation.getErrorMessage());
                return false;
            }

            activeTransfers.put(session.getSessionId(), TransferStatus.CONNECTING);
            log.info("Initiated transfer for session: {} using protocol: {}", session.getSessionId(), getProtocol());
            return true;
        } catch (Exception e) {
            log.error("Failed to initiate transfer for session: {}", session.getSessionId(), e);
            return false;
        }
    }

    @Override
    public boolean cancelTransfer(DataTransferSession session) {
        try {
            activeTransfers.put(session.getSessionId(), TransferStatus.CANCELLED);
            log.info("Cancelled transfer for session: {}", session.getSessionId());
            return true;
        } catch (Exception e) {
            log.error("Failed to cancel transfer for session: {}", session.getSessionId(), e);
            return false;
        }
    }

    @Override
    public TransferStatus getTransferStatus(DataTransferSession session) {
        return activeTransfers.getOrDefault(session.getSessionId(), TransferStatus.NOT_STARTED);
    }

    /**
     * Updates the status of an active transfer
     */
    protected void updateTransferStatus(String sessionId, TransferStatus status) {
        activeTransfers.put(sessionId, status);
        log.debug("Updated transfer status for session {} to {}", sessionId, status);
    }

    /**
     * Removes a completed transfer from active tracking
     */
    protected void removeActiveTransfer(String sessionId) {
        activeTransfers.remove(sessionId);
        log.debug("Removed active transfer: {}", sessionId);
    }

    /**
     * Common validation for required configuration keys
     */
    protected ValidationResult validateRequiredConfig(Map<String, Object> config, String... requiredKeys) {
        for (String key : requiredKeys) {
            if (!config.containsKey(key) || config.get(key) == null) {
                return ValidationResult.invalid("Missing required configuration: " + key);
            }
        }
        return ValidationResult.valid();
    }

    /**
     * Common validation for endpoint URLs
     */
    protected ValidationResult validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return ValidationResult.invalid("Endpoint URL is required");
        }
        // Basic URL validation
        if (!endpoint.startsWith("http") && !endpoint.startsWith("https") &&
            !endpoint.startsWith("ftp") && !endpoint.startsWith("sftp")) {
            return ValidationResult.invalid("Invalid endpoint URL format");
        }
        return ValidationResult.valid();
    }
}