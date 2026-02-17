package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.controller.DataTransferController;
import com.mayo.hospitalintegration.dto.DataTransferSessionDto;
import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.repository.DataTransferSessionRepository;
import com.mayo.hospitalintegration.repository.HospitalDeviceRepository;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import com.mayo.hospitalintegration.service.protocol.ProtocolHandler;
import com.mayo.hospitalintegration.service.protocol.HL7ProtocolHandler;
import com.mayo.hospitalintegration.service.protocol.FHIRProtocolHandler;
import com.mayo.hospitalintegration.service.protocol.DICOMProtocolHandler;
import com.mayo.hospitalintegration.service.protocol.RestApiProtocolHandler;
import com.mayo.hospitalintegration.service.protocol.SFTPProtocolHandler;
import com.mayo.hospitalintegration.service.adapter.ProtocolAdapterFactory;
import com.mayo.hospitalintegration.service.adapter.ProtocolAdapter;
import com.mayo.hospitalintegration.service.adapter.DICOMAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataTransferService {

    private final DataTransferSessionRepository sessionRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalDeviceRepository deviceRepository;
    private final ActivityTrackingService activityTrackingService;
    private final TransferAuthenticationService authService;
    private final DataMappingService dataMappingService;
    private final DataEncryptionService encryptionService;
    private final ProtocolAdapterFactory adapterFactory;

    // Protocol handlers
    private final HL7ProtocolHandler hl7Handler;
    private final FHIRProtocolHandler fhirHandler;
    private final DICOMProtocolHandler dicomHandler;
    private final RestApiProtocolHandler restApiHandler;
    private final SFTPProtocolHandler sftpHandler;

    @Transactional
    public DataTransferSessionDto initiateTransfer(DataTransferSessionDto sessionDto) {
        Hospital hospital = hospitalRepository.findById(sessionDto.getHospitalId())
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));

        HospitalDevice device = deviceRepository.findById(sessionDto.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));

        DataTransferSession session = mapToEntity(sessionDto);
        session.setHospital(hospital);
        session.setDevice(device);
        session.setSessionId(generateSessionId());
        session.setStatus(DataTransferSession.TransferStatus.INITIATED);
        session.setStartedAt(LocalDateTime.now());
        session = sessionRepository.save(session);

        // Track activity
        activityTrackingService.trackActivity(
                hospital, device, ActivityTrackingService.ActivityType.DATA_TRANSFER_INITIATED,
                "Data transfer initiated: " + session.getDataType() + " via " + session.getProtocol(),
                null, null, null, null, session.getMetadata());

        log.info("Initiated data transfer session: {} for protocol: {}", session.getSessionId(), session.getProtocol());
        return mapToDto(session);
    }

    @Transactional
    public Optional<DataTransferSessionDto> updateTransferStatus(String sessionId,
            DataTransferSession.TransferStatus status) {
        return sessionRepository.findBySessionId(sessionId)
                .map(session -> {
                    session.setStatus(status);
                    if (status == DataTransferSession.TransferStatus.COMPLETED) {
                        session.setCompletedAt(LocalDateTime.now());
                    }
                    session = sessionRepository.save(session);

                    // Track completion
                    if (status == DataTransferSession.TransferStatus.COMPLETED) {
                        activityTrackingService.trackActivity(
                                session.getHospital(), session.getDevice(),
                                ActivityTrackingService.ActivityType.DATA_TRANSFER_COMPLETED,
                                "Data transfer completed", null, null, null, null, null);
                    }

                    log.info("Updated transfer session {} status to {}", sessionId, status);
                    return mapToDto(session);
                });
    }

    private String generateSessionId() {
        return "TRANSFER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private DataTransferSessionDto mapToDto(DataTransferSession session) {
        DataTransferSessionDto dto = new DataTransferSessionDto();
        dto.setId(session.getId());
        dto.setSessionId(session.getSessionId());
        dto.setHospitalId(session.getHospital().getId());
        dto.setHospitalName(session.getHospital().getName());
        dto.setDeviceId(session.getDevice().getId());
        dto.setDeviceName(session.getDevice().getDeviceName());
        dto.setTransferType(session.getTransferType());
        dto.setDataType(session.getDataType());
        dto.setProtocol(session.getProtocol());
        dto.setStatus(session.getStatus());
        dto.setTotalRecords(session.getTotalRecords());
        dto.setProcessedRecords(session.getProcessedRecords());
        dto.setFailedRecords(session.getFailedRecords());
        dto.setDataSizeBytes(session.getDataSizeBytes());
        dto.setTransferredBytes(session.getTransferredBytes());
        dto.setSourceEndpoint(session.getSourceEndpoint());
        dto.setDestinationEndpoint(session.getDestinationEndpoint());
        dto.setInitiatedBy(session.getInitiatedBy());
        dto.setErrorMessage(session.getErrorMessage());
        dto.setMetadata(session.getMetadata());
        dto.setStartedAt(session.getStartedAt());
        dto.setCompletedAt(session.getCompletedAt());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }

    private DataTransferSession mapToEntity(DataTransferSessionDto dto) {
        DataTransferSession session = new DataTransferSession();
        session.setTransferType(dto.getTransferType());
        session.setDataType(dto.getDataType());
        session.setProtocol(dto.getProtocol());
        session.setTotalRecords(dto.getTotalRecords());
        session.setDataSizeBytes(dto.getDataSizeBytes());
        session.setSourceEndpoint(dto.getSourceEndpoint());
        session.setDestinationEndpoint(dto.getDestinationEndpoint());
        session.setInitiatedBy(dto.getInitiatedBy());
        session.setMetadata(dto.getMetadata());
        return session;
    }

    /**
     * Execute a data transfer using the appropriate protocol handler
     */
    @Transactional
    public DataTransferController.TransferResult executeTransfer(String sessionId, Map<String, Object> config) {
        DataTransferSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer session not found: " + sessionId));

        // Validate session
        if (!authService.validateSession(session)) {
            throw new IllegalArgumentException("Invalid transfer session");
        }

        // Authenticate transfer
        TransferAuthenticationService.AuthenticationResult authResult = authService.authenticateTransfer(session,
                config);
        if (!authResult.isAuthenticated()) {
            throw new SecurityException("Transfer authentication failed: " + authResult.getErrorMessage());
        }

        // Authorize transfer
        TransferAuthenticationService.AuthorizationResult authzResult = authService.authorizeTransfer(session,
                "EXECUTE");
        if (!authzResult.isAllowed()) {
            throw new SecurityException("Transfer authorization failed: " + authzResult.getDenialReason());
        }

        // Update status to connecting
        session.setStatus(DataTransferSession.TransferStatus.CONNECTING);
        sessionRepository.save(session);

        try {
            // Get protocol handler
            ProtocolHandler handler = getProtocolHandler(session.getProtocol());
            if (handler == null) {
                throw new IllegalArgumentException("Unsupported protocol: " + session.getProtocol());
            }

            // Initiate transfer
            if (!handler.initiateTransfer(session, config)) {
                throw new RuntimeException("Failed to initiate transfer");
            }

            // Update status to transferring
            session.setStatus(DataTransferSession.TransferStatus.TRANSFERRING);
            sessionRepository.save(session);

            // Execute transfer
            ProtocolHandler.TransferResult result = handler.executeTransfer(session, config);

            // Update session with results
            updateSessionWithResult(session, result);

            // Track activity
            trackTransferActivity(session,
                    result.isSuccess() ? ActivityTrackingService.ActivityType.DATA_TRANSFER_COMPLETED
                            : ActivityTrackingService.ActivityType.DATA_TRANSFER_FAILED);

            return new DataTransferController.TransferResult(
                    result.isSuccess(),
                    result.getErrorMessage(),
                    result.getData(),
                    result.getBytesTransferred(),
                    result.getRecordsProcessed());

        } catch (Exception e) {
            log.error("Transfer execution failed for session: {}", sessionId, e);

            // Update session with failure
            session.setStatus(DataTransferSession.TransferStatus.FAILED);
            session.setErrorMessage(e.getMessage());
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);

            // Track failure
            trackTransferActivity(session, ActivityTrackingService.ActivityType.DATA_TRANSFER_FAILED);

            return new DataTransferController.TransferResult(false, e.getMessage(), null, 0, 0);
        }
    }

    /**
     * Cancel a transfer
     */
    @Transactional
    public boolean cancelTransfer(String sessionId) {
        DataTransferSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer session not found: " + sessionId));

        if (session.getStatus() == DataTransferSession.TransferStatus.COMPLETED ||
                session.getStatus() == DataTransferSession.TransferStatus.CANCELLED) {
            return false; // Already completed or cancelled
        }

        // Get protocol handler and cancel
        ProtocolHandler handler = getProtocolHandler(session.getProtocol());
        if (handler != null) {
            handler.cancelTransfer(session);
        }

        // Update session
        session.setStatus(DataTransferSession.TransferStatus.CANCELLED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Track cancellation
        trackTransferActivity(session, ActivityTrackingService.ActivityType.DATA_TRANSFER_FAILED);

        log.info("Cancelled transfer session: {}", sessionId);
        return true;
    }

    /**
     * Get transfer status
     */
    public Optional<DataTransferSessionDto> getTransferStatus(String sessionId) {
        return sessionRepository.findBySessionId(sessionId).map(this::mapToDto);
    }

    /**
     * Get hospital transfers with pagination
     */
    public List<DataTransferSessionDto> getHospitalTransfers(String hospitalId, int page, int size) {
        try {
            return sessionRepository.findByHospital_IdOrderByCreatedAtDesc(UUID.fromString(hospitalId))
                    .stream()
                    .skip((long) page * size)
                    .limit(size)
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            log.error("Invalid hospitalId format: {}", hospitalId, e);
            throw new IllegalArgumentException("Invalid hospitalId format: " + hospitalId);
        }
    }

    /**
     * Get transfer statistics
     */
    public DataTransferController.TransferStatistics getTransferStatistics(String hospitalId,
            String dateFrom, String dateTo) {
        // Simplified statistics - in a real implementation, this would query the
        // database
        List<DataTransferSession> sessions;
        try {
            sessions = hospitalId != null
                    ? sessionRepository.findByHospital_IdOrderByCreatedAtDesc(UUID.fromString(hospitalId))
                    : sessionRepository.findAll();
        } catch (IllegalArgumentException e) {
            log.error("Invalid hospitalId format: {}", hospitalId, e);
            throw new IllegalArgumentException("Invalid hospitalId format: " + hospitalId);
        }

        DataTransferController.TransferStatistics stats = new DataTransferController.TransferStatistics();
        stats.setTotalTransfers(sessions.size());
        stats.setSuccessfulTransfers(sessions.stream()
                .filter(s -> s.getStatus() == DataTransferSession.TransferStatus.COMPLETED).count());
        stats.setFailedTransfers(sessions.stream()
                .filter(s -> s.getStatus() == DataTransferSession.TransferStatus.FAILED).count());
        stats.setTotalBytesTransferred(sessions.stream()
                .mapToLong(s -> s.getTransferredBytes() != null ? s.getTransferredBytes() : 0).sum());

        return stats;
    }

    /**
     * Process data using protocol adapters
     */
    public DataProcessingResult processData(String sessionId, String rawData) {
        DataTransferSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer session not found: " + sessionId));

        try {
            ProtocolAdapter adapter = adapterFactory.getAdapter(session.getProtocol());

            // Parse the data
            ProtocolAdapter.ParsedData parsedData = adapter.parse(rawData);

            // Validate the parsed data
            ProtocolAdapter.ValidationResult validation = adapter.validate(parsedData);
            if (!validation.isValid()) {
                return new DataProcessingResult(false, "Validation failed: " + validation.getErrorMessage(),
                    null, validation.getFieldErrors());
            }

            // Transform to internal format
            ProtocolAdapter.TransformedData transformedData = adapter.transform(parsedData);

            return new DataProcessingResult(true, null, transformedData, null);

        } catch (ProtocolAdapter.AdapterException e) {
            log.error("Data processing failed for session: {}", sessionId, e);
            return new DataProcessingResult(false, "Processing failed: " + e.getMessage(), null, null);
        }
    }

    /**
     * Process DICOM data (binary)
     */
    public DataProcessingResult processDICOMData(String sessionId, byte[] dicomData) {
        DataTransferSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer session not found: " + sessionId));

        if (session.getProtocol() != DataTransferSession.TransferProtocol.DICOM) {
            return new DataProcessingResult(false, "Session protocol is not DICOM", null, null);
        }

        try {
            DICOMAdapter dicomAdapter = (DICOMAdapter) adapterFactory.getAdapter(session.getProtocol());

            // Parse the DICOM data
            ProtocolAdapter.ParsedData parsedData = dicomAdapter.parse(dicomData);

            // Validate the parsed data
            ProtocolAdapter.ValidationResult validation = dicomAdapter.validate(parsedData);
            if (!validation.isValid()) {
                return new DataProcessingResult(false, "Validation failed: " + validation.getErrorMessage(),
                    null, validation.getFieldErrors());
            }

            // Transform to internal format
            ProtocolAdapter.TransformedData transformedData = dicomAdapter.transform(parsedData);

            return new DataProcessingResult(true, null, transformedData, null);

        } catch (ProtocolAdapter.AdapterException e) {
            log.error("DICOM processing failed for session: {}", sessionId, e);
            return new DataProcessingResult(false, "DICOM processing failed: " + e.getMessage(), null, null);
        }
    }

    /**
     * Serialize internal data back to protocol format
     */
    public String serializeData(String sessionId, ProtocolAdapter.TransformedData internalData) {
        DataTransferSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer session not found: " + sessionId));

        try {
            ProtocolAdapter adapter = adapterFactory.getAdapter(session.getProtocol());
            return adapter.serialize(internalData);
        } catch (ProtocolAdapter.AdapterException e) {
            log.error("Data serialization failed for session: {}", sessionId, e);
            throw new RuntimeException("Serialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get adapter capabilities for a protocol
     */
    public Map<String, Object> getAdapterCapabilities(DataTransferSession.TransferProtocol protocol) {
        return adapterFactory.getAdapterCapabilities(protocol);
    }

    /**
     * Validate transfer configuration
     */
    public DataTransferController.ValidationResult validateTransferConfig(DataTransferSession.TransferProtocol protocol,
            Map<String, Object> config) {
        ProtocolHandler handler = getProtocolHandler(protocol);
        if (handler == null) {
            return new DataTransferController.ValidationResult(false, "Unsupported protocol: " + protocol);
        }

        ProtocolHandler.ValidationResult result = handler.validateConfig(config);
        return new DataTransferController.ValidationResult(result.isValid(), result.getErrorMessage());
    }

    private ProtocolHandler getProtocolHandler(DataTransferSession.TransferProtocol protocol) {
        switch (protocol) {
            case HL7:
                return hl7Handler;
            case FHIR:
                return fhirHandler;
            case DICOM:
                return dicomHandler;
            case REST_API:
                return restApiHandler;
            case SFTP:
                return sftpHandler;
            default:
                return null;
        }
    }

    private void updateSessionWithResult(DataTransferSession session, ProtocolHandler.TransferResult result) {
        if (result.isSuccess()) {
            session.setStatus(DataTransferSession.TransferStatus.COMPLETED);
            session.setProcessedRecords(result.getRecordsProcessed());
            session.setTransferredBytes(result.getBytesTransferred());
        } else {
            session.setStatus(DataTransferSession.TransferStatus.FAILED);
            session.setErrorMessage(result.getErrorMessage());
        }
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private void trackTransferActivity(DataTransferSession session, ActivityTrackingService.ActivityType activityType) {
        activityTrackingService.trackActivity(
                session.getHospital(),
                session.getDevice(),
                activityType,
                "Data transfer " + activityType.toString().toLowerCase().replace("_", " ") +
                        ": " + session.getDataType() + " via " + session.getProtocol(),
                session.getInitiatedBy(),
                null, // patientId
                null, // recordId
                null, // recordType
                session.getMetadata());
    }

    /**
     * Result of data processing operations
     */
    public static class DataProcessingResult {
        private final boolean success;
        private final String errorMessage;
        private final ProtocolAdapter.TransformedData processedData;
        private final Map<String, String> validationErrors;

        public DataProcessingResult(boolean success, String errorMessage,
                ProtocolAdapter.TransformedData processedData, Map<String, String> validationErrors) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.processedData = processedData;
            this.validationErrors = validationErrors;
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public ProtocolAdapter.TransformedData getProcessedData() { return processedData; }
        public Map<String, String> getValidationErrors() { return validationErrors; }
    }
}