package com.mayo.hospitalintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.events.topics.Topics;
import com.mayo.hospitalintegration.dto.DataTransferSessionDto;
import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.repository.HospitalDeviceRepository;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that listens to device pairing, protocol detection, and authentication events
 * to automatically initiate data transfer processes when all prerequisites are met.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataTransferInitiationService {

    private final DataTransferService dataTransferService;
    private final HospitalService hospitalService;
    private final HospitalRepository hospitalRepository;
    private final HospitalDeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    // Track device states for transfer initiation
    private final Map<String, DeviceState> deviceStates = new ConcurrentHashMap<>();

    // Event types
    private static final String DEVICE_PAIRED = "DEVICE_PAIRED";
    private static final String PROTOCOL_DETECTED = "PROTOCOL_DETECTED";
    private static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";

    /**
     * Device state tracking for transfer prerequisites
     */
    private static class DeviceState {
        boolean devicePaired = false;
        boolean protocolDetected = false;
        boolean authenticationSuccess = false;
        String protocol;
        String deviceType;
        LocalDateTime lastUpdated;

        boolean isReadyForTransfer() {
            return devicePaired && protocolDetected && authenticationSuccess;
        }

        void reset() {
            devicePaired = false;
            protocolDetected = false;
            authenticationSuccess = false;
            protocol = null;
            deviceType = null;
            lastUpdated = LocalDateTime.now();
        }
    }

    /**
     * Kafka listener for hospital events to detect transfer initiation triggers
     */
    @KafkaListener(topics = Topics.HOSPITAL_EVENTS, groupId = "hospital-integration-service", concurrency = "#{${hospital.kafka.consumer.concurrency:2}}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consumeHospitalEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received hospital event: key={}, offset={}", key, offset);

            // Parse the event
            HospitalEvent event = parseEvent(message);
            if (event == null) {
                log.warn("Failed to parse hospital event: {}", message);
                acknowledgment.acknowledge();
                return;
            }

            // Process the event
            processEvent(event);

            acknowledgment.acknowledge();
            log.debug("Successfully processed hospital event: {}", event.eventType);

        } catch (Exception e) {
            log.error("Failed to process hospital event: key={}, error={}", key, e.getMessage(), e);
            // Don't re-throw to avoid retry loops, but could implement dead letter queue
            acknowledgment.acknowledge();
        }
    }

    /**
     * Parse hospital event from JSON message
     */
    private HospitalEvent parseEvent(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);

            HospitalEvent event = new HospitalEvent();
            event.eventId = jsonNode.has("eventId") ? jsonNode.get("eventId").asText() : UUID.randomUUID().toString();
            event.eventType = jsonNode.has("eventType") ? jsonNode.get("eventType").asText() : null;
            event.hospitalId = jsonNode.has("hospitalId") ? UUID.fromString(jsonNode.get("hospitalId").asText()) : null;
            event.actorId = jsonNode.has("actorId") && !jsonNode.get("actorId").isNull() ?
                UUID.fromString(jsonNode.get("actorId").asText()) : null;
            event.timestamp = jsonNode.has("timestamp") ? LocalDateTime.parse(jsonNode.get("timestamp").asText()) : LocalDateTime.now();

            // Parse event data
            if (jsonNode.has("data")) {
                JsonNode data = jsonNode.get("data");
                event.deviceId = data.has("deviceId") ? data.get("deviceId").asText() : null;
                event.deviceType = data.has("deviceType") ? data.get("deviceType").asText() : null;
                event.protocol = data.has("protocol") ? data.get("protocol").asText() : null;
                event.integrationStatus = data.has("integrationStatus") ? data.get("integrationStatus").asText() : null;
            }

            return event;

        } catch (Exception e) {
            log.error("Error parsing hospital event: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Process the parsed event and update device state
     */
    private void processEvent(HospitalEvent event) {
        if (event.deviceId == null || event.hospitalId == null) {
            log.debug("Event missing deviceId or hospitalId, skipping: {}", event.eventType);
            return;
        }

        String deviceKey = event.hospitalId + ":" + event.deviceId;
        DeviceState state = deviceStates.computeIfAbsent(deviceKey, k -> new DeviceState());

        boolean stateChanged = false;

        switch (event.eventType) {
            case DEVICE_PAIRED:
                if (!state.devicePaired) {
                    state.devicePaired = true;
                    state.deviceType = event.deviceType;
                    state.lastUpdated = LocalDateTime.now();
                    stateChanged = true;
                    log.info("Device paired: hospital={}, device={}", event.hospitalId, event.deviceId);
                }
                break;

            case PROTOCOL_DETECTED:
                if (!state.protocolDetected && event.protocol != null) {
                    state.protocolDetected = true;
                    state.protocol = event.protocol;
                    state.lastUpdated = LocalDateTime.now();
                    stateChanged = true;
                    log.info("Protocol detected: hospital={}, device={}, protocol={}",
                        event.hospitalId, event.deviceId, event.protocol);
                }
                break;

            case AUTHENTICATION_SUCCESS:
                if (!state.authenticationSuccess) {
                    state.authenticationSuccess = true;
                    state.lastUpdated = LocalDateTime.now();
                    stateChanged = true;
                    log.info("Authentication success: hospital={}, device={}", event.hospitalId, event.deviceId);
                }
                break;

            default:
                // Other events not relevant for transfer initiation
                return;
        }

        // Check if all prerequisites are met
        if (stateChanged && state.isReadyForTransfer()) {
            log.info("All prerequisites met for device {}, initiating transfer", deviceKey);
            initiateDataTransfer(event.hospitalId, event.deviceId, state);

            // Reset state after successful initiation
            state.reset();
        }
    }

    /**
     * Initiate data transfer when all prerequisites are validated
     */
    private void initiateDataTransfer(UUID hospitalId, String deviceId, DeviceState state) {
        try {
            // Validate hospital and device exist and are active
            Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found: " + hospitalId));

            HospitalDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

            // Additional validations
            if (!isDeviceActive(device)) {
                log.warn("Device not active, skipping transfer initiation: {}", deviceId);
                return;
            }

            if (!isProtocolSupported(state.protocol)) {
                log.warn("Protocol not supported, skipping transfer initiation: {}", state.protocol);
                return;
            }

            // Create transfer session DTO
            DataTransferSessionDto sessionDto = new DataTransferSessionDto();
            sessionDto.setHospitalId(hospitalId);
            sessionDto.setDeviceId(device.getId());
            sessionDto.setTransferType(DataTransferSession.TransferType.SYNC);
            sessionDto.setDataType(DataTransferSession.DataType.PATIENT_RECORD); // Default data type
            sessionDto.setProtocol(DataTransferSession.TransferProtocol.valueOf(state.protocol));
            sessionDto.setInitiatedBy(null); // System initiated
            sessionDto.setMetadata("{\"autoInitiated\":true,\"deviceType\":\"" + state.deviceType + "\",\"initiationReason\":\"ALL_PREREQUISITES_MET\"}");

            // Initiate the transfer
            DataTransferSessionDto initiatedSession = dataTransferService.initiateTransfer(sessionDto);

            // Publish data transfer initiated event
            hospitalService.publishDataTransferInitiatedEvent(
                hospitalId,
                deviceId,
                state.deviceType,
                state.protocol,
                "INITIATED"
            );

            log.info("Successfully initiated automatic data transfer: session={}, hospital={}, device={}",
                initiatedSession.getSessionId(), hospitalId, deviceId);

        } catch (Exception e) {
            log.error("Failed to initiate data transfer for hospital={}, device={}: {}",
                hospitalId, deviceId, e.getMessage(), e);

            // Publish failure event or implement retry logic
            handleInitiationFailure(hospitalId, deviceId, state, e);
        }
    }

    /**
     * Validate if device is active and ready for transfer
     */
    private boolean isDeviceActive(HospitalDevice device) {
        // Check device status - assuming there's a status field
        return device.getStatus() == HospitalDevice.DeviceStatus.ACTIVE;
    }

    /**
     * Validate if protocol is supported for automatic transfer
     */
    private boolean isProtocolSupported(String protocol) {
        try {
            DataTransferSession.TransferProtocol transferProtocol = DataTransferSession.TransferProtocol.valueOf(protocol);
            return transferProtocol == DataTransferSession.TransferProtocol.HL7 ||
                   transferProtocol == DataTransferSession.TransferProtocol.FHIR ||
                   transferProtocol == DataTransferSession.TransferProtocol.DICOM;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Handle transfer initiation failures with retry logic
     */
    private void handleInitiationFailure(UUID hospitalId, String deviceId, DeviceState state, Exception e) {
        // Implement retry logic or exponential backoff
        // For now, log and potentially schedule retry

        log.warn("Transfer initiation failed, will retry: hospital={}, device={}, error={}",
            hospitalId, deviceId, e.getMessage());

        // Could implement retry with scheduled task or dead letter queue
        // For simplicity, just log for now
    }

    /**
     * Inner class for parsed hospital event
     */
    private static class HospitalEvent {
        String eventId;
        String eventType;
        UUID hospitalId;
        UUID actorId;
        LocalDateTime timestamp;
        String deviceId;
        String deviceType;
        String protocol;
        String integrationStatus;
    }
}