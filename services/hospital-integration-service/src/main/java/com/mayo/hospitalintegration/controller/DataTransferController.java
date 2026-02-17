package com.mayo.hospitalintegration.controller;

import com.mayo.hospitalintegration.dto.DataTransferSessionDto;
import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.service.DataTransferService;
import com.mayo.hospitalintegration.service.TransferAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for Data Transfer operations
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Transfer", description = "Data transfer management endpoints")
public class DataTransferController {

    private final DataTransferService dataTransferService;

    /**
     * Initiate a new data transfer
     */
    @PostMapping("/initiate")
    @Operation(summary = "Initiate data transfer", description = "Start a new data transfer session")
    public ResponseEntity<?> initiateTransfer(
            @RequestBody TransferRequest request,
            @RequestHeader(value = "Authorization", required = false) String authToken) {

        try {
            log.info("Initiating transfer: {}", request);

            // Create session DTO from request
            DataTransferSessionDto sessionDto = new DataTransferSessionDto();
            sessionDto.setHospitalId(request.getHospitalId() != null ? UUID.fromString(request.getHospitalId()) : null);
            sessionDto.setDeviceId(request.getDeviceId() != null ? UUID.fromString(request.getDeviceId()) : null);
            sessionDto.setTransferType(request.getTransferType());
            sessionDto.setDataType(request.getDataType());
            sessionDto.setProtocol(request.getProtocol());
            sessionDto.setSourceEndpoint(request.getSourceEndpoint());
            sessionDto.setDestinationEndpoint(request.getDestinationEndpoint());
            sessionDto.setInitiatedBy(
                    request.getInitiatedBy() != null ? UUID.fromString(request.getInitiatedBy()) : null);
            sessionDto.setMetadata(request.getMetadata());

            // Initiate transfer
            DataTransferSessionDto result = dataTransferService.initiateTransfer(sessionDto);

            log.info("Transfer initiated successfully: {}", result.getSessionId());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to initiate transfer", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to initiate transfer: " + e.getMessage()));
        }
    }

    /**
     * Execute a data transfer
     */
    @PostMapping("/{sessionId}/execute")
    @Operation(summary = "Execute data transfer", description = "Execute the actual data transfer operation")
    public ResponseEntity<?> executeTransfer(
            @PathVariable String sessionId,
            @RequestBody TransferExecutionRequest request) {

        try {
            log.info("Executing transfer: {}", sessionId);

            // Execute transfer
            TransferResult result = dataTransferService.executeTransfer(sessionId, request.getConfig());

            if (result.isSuccess()) {
                log.info("Transfer executed successfully: {}", sessionId);
                return ResponseEntity.ok(result);
            } else {
                log.warn("Transfer execution failed: {}", sessionId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", result.getErrorMessage()));
            }

        } catch (Exception e) {
            log.error("Failed to execute transfer: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Transfer execution failed: " + e.getMessage()));
        }
    }

    /**
     * Get transfer status
     */
    @GetMapping("/{sessionId}/status")
    @Operation(summary = "Get transfer status", description = "Get the current status of a transfer session")
    public ResponseEntity<?> getTransferStatus(@PathVariable String sessionId) {

        try {
            Optional<DataTransferSessionDto> session = dataTransferService.getTransferStatus(sessionId);

            if (session.isPresent()) {
                return ResponseEntity.ok(session.get());
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to get transfer status: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get transfer status: " + e.getMessage()));
        }
    }

    /**
     * Update transfer status
     */
    @PutMapping("/{sessionId}/status")
    @Operation(summary = "Update transfer status", description = "Update the status of a transfer session")
    public ResponseEntity<?> updateTransferStatus(
            @PathVariable String sessionId,
            @RequestBody StatusUpdateRequest request) {

        try {
            log.info("Updating transfer status: {} to {}", sessionId, request.getStatus());

            Optional<DataTransferSessionDto> result = dataTransferService.updateTransferStatus(
                    sessionId, request.getStatus());

            if (result.isPresent()) {
                return ResponseEntity.ok(result.get());
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to update transfer status: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to update transfer status: " + e.getMessage()));
        }
    }

    /**
     * Cancel a transfer
     */
    @PostMapping("/{sessionId}/cancel")
    @Operation(summary = "Cancel transfer", description = "Cancel an ongoing transfer session")
    public ResponseEntity<?> cancelTransfer(@PathVariable String sessionId) {

        try {
            log.info("Cancelling transfer: {}", sessionId);

            boolean cancelled = dataTransferService.cancelTransfer(sessionId);

            if (cancelled) {
                return ResponseEntity.ok(Map.of("message", "Transfer cancelled successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Failed to cancel transfer"));
            }

        } catch (Exception e) {
            log.error("Failed to cancel transfer: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to cancel transfer: " + e.getMessage()));
        }
    }

    /**
     * Get transfer history for a hospital
     */
    @GetMapping("/hospital/{hospitalId}")
    @Operation(summary = "Get hospital transfers", description = "Get transfer history for a hospital")
    public ResponseEntity<?> getHospitalTransfers(
            @PathVariable String hospitalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            List<DataTransferSessionDto> transfers = dataTransferService.getHospitalTransfers(hospitalId, page, size);
            return ResponseEntity.ok(Map.of(
                    "transfers", transfers,
                    "page", page,
                    "size", size));

        } catch (Exception e) {
            log.error("Failed to get hospital transfers: {}", hospitalId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get hospital transfers: " + e.getMessage()));
        }
    }

    /**
     * Get transfer statistics
     */
    @GetMapping("/stats")
    @Operation(summary = "Get transfer statistics", description = "Get overall transfer statistics")
    public ResponseEntity<?> getTransferStatistics(
            @RequestParam(required = false) String hospitalId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        try {
            TransferStatistics stats = dataTransferService.getTransferStatistics(hospitalId, dateFrom, dateTo);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get transfer statistics", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get transfer statistics: " + e.getMessage()));
        }
    }

    /**
     * Validate transfer configuration
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate transfer config", description = "Validate transfer configuration before execution")
    public ResponseEntity<?> validateTransferConfig(@RequestBody TransferValidationRequest request) {

        try {
            ValidationResult result = dataTransferService.validateTransferConfig(
                    request.getProtocol(), request.getConfig());

            if (result.isValid()) {
                return ResponseEntity.ok(Map.of("valid", true));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("valid", false, "error", result.getErrorMessage()));
            }

        } catch (Exception e) {
            log.error("Failed to validate transfer config", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Validation failed: " + e.getMessage()));
        }
    }

    // Request/Response DTOs
    public static class TransferRequest {
        private String hospitalId;
        private String deviceId;
        private DataTransferSession.TransferType transferType;
        private DataTransferSession.DataType dataType;
        private DataTransferSession.TransferProtocol protocol;
        private String sourceEndpoint;
        private String destinationEndpoint;
        private String initiatedBy;
        private String metadata;

        // Getters and setters
        public String getHospitalId() {
            return hospitalId;
        }

        public void setHospitalId(String hospitalId) {
            this.hospitalId = hospitalId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public DataTransferSession.TransferType getTransferType() {
            return transferType;
        }

        public void setTransferType(DataTransferSession.TransferType transferType) {
            this.transferType = transferType;
        }

        public DataTransferSession.DataType getDataType() {
            return dataType;
        }

        public void setDataType(DataTransferSession.DataType dataType) {
            this.dataType = dataType;
        }

        public DataTransferSession.TransferProtocol getProtocol() {
            return protocol;
        }

        public void setProtocol(DataTransferSession.TransferProtocol protocol) {
            this.protocol = protocol;
        }

        public String getSourceEndpoint() {
            return sourceEndpoint;
        }

        public void setSourceEndpoint(String sourceEndpoint) {
            this.sourceEndpoint = sourceEndpoint;
        }

        public String getDestinationEndpoint() {
            return destinationEndpoint;
        }

        public void setDestinationEndpoint(String destinationEndpoint) {
            this.destinationEndpoint = destinationEndpoint;
        }

        public String getInitiatedBy() {
            return initiatedBy;
        }

        public void setInitiatedBy(String initiatedBy) {
            this.initiatedBy = initiatedBy;
        }

        public String getMetadata() {
            return metadata;
        }

        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }
    }

    public static class TransferExecutionRequest {
        private Map<String, Object> config;

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }

    public static class StatusUpdateRequest {
        private DataTransferSession.TransferStatus status;

        public DataTransferSession.TransferStatus getStatus() {
            return status;
        }

        public void setStatus(DataTransferSession.TransferStatus status) {
            this.status = status;
        }
    }

    public static class TransferValidationRequest {
        private DataTransferSession.TransferProtocol protocol;
        private Map<String, Object> config;

        public DataTransferSession.TransferProtocol getProtocol() {
            return protocol;
        }

        public void setProtocol(DataTransferSession.TransferProtocol protocol) {
            this.protocol = protocol;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }

    public static class TransferResult {
        private boolean success;
        private String errorMessage;
        private Object data;
        private long bytesTransferred;
        private long recordsProcessed;

        public TransferResult() {
        }

        public TransferResult(boolean success, String errorMessage, Object data,
                long bytesTransferred, long recordsProcessed) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.data = data;
            this.bytesTransferred = bytesTransferred;
            this.recordsProcessed = recordsProcessed;
        }

        // Getters and setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public long getBytesTransferred() {
            return bytesTransferred;
        }

        public void setBytesTransferred(long bytesTransferred) {
            this.bytesTransferred = bytesTransferred;
        }

        public long getRecordsProcessed() {
            return recordsProcessed;
        }

        public void setRecordsProcessed(long recordsProcessed) {
            this.recordsProcessed = recordsProcessed;
        }
    }

    public static class TransferStatistics {
        private long totalTransfers;
        private long successfulTransfers;
        private long failedTransfers;
        private long totalBytesTransferred;
        private double averageTransferTime;

        // Getters and setters
        public long getTotalTransfers() {
            return totalTransfers;
        }

        public void setTotalTransfers(long totalTransfers) {
            this.totalTransfers = totalTransfers;
        }

        public long getSuccessfulTransfers() {
            return successfulTransfers;
        }

        public void setSuccessfulTransfers(long successfulTransfers) {
            this.successfulTransfers = successfulTransfers;
        }

        public long getFailedTransfers() {
            return failedTransfers;
        }

        public void setFailedTransfers(long failedTransfers) {
            this.failedTransfers = failedTransfers;
        }

        public long getTotalBytesTransferred() {
            return totalBytesTransferred;
        }

        public void setTotalBytesTransferred(long totalBytesTransferred) {
            this.totalBytesTransferred = totalBytesTransferred;
        }

        public double getAverageTransferTime() {
            return averageTransferTime;
        }

        public void setAverageTransferTime(double averageTransferTime) {
            this.averageTransferTime = averageTransferTime;
        }
    }

    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;

        public ValidationResult() {
        }

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}