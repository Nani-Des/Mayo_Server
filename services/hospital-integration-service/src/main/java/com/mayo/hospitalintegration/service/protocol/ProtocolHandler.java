package com.mayo.hospitalintegration.service.protocol;

import com.mayo.hospitalintegration.entity.DataTransferSession;

import java.util.Map;

/**
 * Interface for protocol handlers that manage data transfer operations
 * for different healthcare integration protocols (HL7, FHIR, DICOM, etc.)
 */
public interface ProtocolHandler {

    /**
     * Returns the protocol type this handler supports
     */
    DataTransferSession.TransferProtocol getProtocol();

    /**
     * Initiates a data transfer using this protocol
     * @param session The transfer session
     * @param config Protocol-specific configuration
     * @return true if initiation successful
     */
    boolean initiateTransfer(DataTransferSession session, Map<String, Object> config);

    /**
     * Executes the actual data transfer
     * @param session The transfer session
     * @param config Protocol-specific configuration
     * @return TransferResult containing success status and any data
     */
    TransferResult executeTransfer(DataTransferSession session, Map<String, Object> config);

    /**
     * Cancels an ongoing transfer
     * @param session The transfer session
     * @return true if cancellation successful
     */
    boolean cancelTransfer(DataTransferSession session);

    /**
     * Gets the current status of a transfer
     * @param session The transfer session
     * @return TransferStatus
     */
    TransferStatus getTransferStatus(DataTransferSession session);

    /**
     * Validates protocol-specific configuration
     * @param config Configuration to validate
     * @return ValidationResult
     */
    ValidationResult validateConfig(Map<String, Object> config);

    /**
     * Result of a transfer operation
     */
    class TransferResult {
        private final boolean success;
        private final String errorMessage;
        private final Object data;
        private final long bytesTransferred;
        private final long recordsProcessed;

        public TransferResult(boolean success, String errorMessage, Object data,
                            long bytesTransferred, long recordsProcessed) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.data = data;
            this.bytesTransferred = bytesTransferred;
            this.recordsProcessed = recordsProcessed;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Object getData() { return data; }
        public long getBytesTransferred() { return bytesTransferred; }
        public long getRecordsProcessed() { return recordsProcessed; }
    }

    /**
     * Status of a transfer operation
     */
    enum TransferStatus {
        NOT_STARTED,
        CONNECTING,
        TRANSFERRING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Result of configuration validation
     */
    class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}