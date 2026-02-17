package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.service.protocol.ProtocolDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for detecting protocols in incoming data streams and publishing detection events
 */
@Service
@Slf4j
public class ProtocolDetectionService {

    private final ProtocolDetector protocolDetector;
    private final HospitalService hospitalService;

    public ProtocolDetectionService(ProtocolDetector protocolDetector, @org.springframework.context.annotation.Lazy HospitalService hospitalService) {
        this.protocolDetector = protocolDetector;
        this.hospitalService = hospitalService;
    }

    /**
     * Analyzes incoming data stream and detects the protocol
     * @param hospitalId The hospital ID where the data is coming from
     * @param deviceId The device ID sending the data
     * @param deviceType The type of device
     * @param data The raw data bytes to analyze
     * @return The detected protocol, or null if unknown
     */
    public DataTransferSession.TransferProtocol detectAndPublishProtocol(
            String hospitalId, String deviceId, String deviceType, byte[] data) {

        log.debug("Starting protocol detection for hospital: {}, device: {}", hospitalId, deviceId);

        DataTransferSession.TransferProtocol detectedProtocol = protocolDetector.detectProtocol(data);

        if (detectedProtocol != null) {
            // Publish protocol detected event
            hospitalService.publishProtocolDetectedEvent(
                java.util.UUID.fromString(hospitalId),
                deviceId,
                deviceType,
                detectedProtocol.name(),
                "DETECTED"
            );

            log.info("Protocol {} detected and event published for hospital: {}, device: {}",
                detectedProtocol, hospitalId, deviceId);
        } else {
            // Publish unknown protocol event
            hospitalService.publishProtocolDetectedEvent(
                java.util.UUID.fromString(hospitalId),
                deviceId,
                deviceType,
                "UNKNOWN",
                "DETECTION_FAILED"
            );

            log.warn("No protocol detected for hospital: {}, device: {}", hospitalId, deviceId);
        }

        return detectedProtocol;
    }

    /**
     * Gets the detection algorithm description for a specific protocol
     * @param protocol The protocol to get algorithm for
     * @return Human-readable description of the detection algorithm
     */
    public String getDetectionAlgorithm(DataTransferSession.TransferProtocol protocol) {
        return protocolDetector.getDetectionAlgorithm(protocol);
    }

    /**
     * Validates if a detected protocol is supported by the system
     * @param protocol The protocol to validate
     * @return true if supported, false otherwise
     */
    public boolean isProtocolSupported(DataTransferSession.TransferProtocol protocol) {
        return protocol != null &&
               (protocol == DataTransferSession.TransferProtocol.HL7 ||
                protocol == DataTransferSession.TransferProtocol.FHIR ||
                protocol == DataTransferSession.TransferProtocol.DICOM);
    }
}