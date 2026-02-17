package com.mayo.hospitalintegration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.events.topics.Topics;
import com.mayo.hospitalintegration.dto.HospitalDto;
import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HospitalService {

    // Event type constants
    private static final String HOSPITAL_REGISTERED = "HOSPITAL_REGISTERED";
    private static final String HOSPITAL_UPDATED = "HOSPITAL_UPDATED";
    private static final String DEVICE_PAIRED = "DEVICE_PAIRED";
    private static final String PROTOCOL_DETECTED = "PROTOCOL_DETECTED";
    private static final String DATA_TRANSFER_INITIATED = "DATA_TRANSFER_INITIATED";
    private static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";
    private static final String AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE";

    private final HospitalRepository hospitalRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ProtocolDetectionService protocolDetectionService;

    @Transactional(readOnly = true)
    public List<HospitalDto> getAllHospitals() {
        return hospitalRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<HospitalDto> getHospitalById(UUID id) {
        return hospitalRepository.findById(id)
                .map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Optional<HospitalDto> getHospitalByHospitalId(String hospitalId) {
        return hospitalRepository.findByHospitalId(hospitalId)
                .map(this::mapToDto);
    }

    @Transactional
    public HospitalDto createHospital(HospitalDto hospitalDto) {
        if (hospitalRepository.existsByHospitalId(hospitalDto.getHospitalId())) {
            throw new IllegalArgumentException("Hospital with ID " + hospitalDto.getHospitalId() + " already exists");
        }

        Hospital hospital = mapToEntity(hospitalDto);
        hospital = hospitalRepository.save(hospital);

        // Publish hospital created event
        UUID currentUserId = getCurrentUserIdAsUUID();
        publishHospitalEvent(hospital.getId(), HOSPITAL_REGISTERED, currentUserId,
            Map.of("hospitalId", hospital.getHospitalId(), "name", hospital.getName()));

        log.info("Created new hospital: {}", hospital.getHospitalId());
        return mapToDto(hospital);
    }

    @Transactional
    public Optional<HospitalDto> updateHospital(UUID id, HospitalDto hospitalDto) {
        return hospitalRepository.findById(id)
                .map(existingHospital -> {
                    updateEntityFromDto(existingHospital, hospitalDto);
                    Hospital updated = hospitalRepository.save(existingHospital);

                    // Publish hospital updated event
                    UUID currentUserId = getCurrentUserIdAsUUID();
                    publishHospitalEvent(updated.getId(), HOSPITAL_UPDATED, currentUserId,
                        Map.of("hospitalId", updated.getHospitalId(), "name", updated.getName()));

                    log.info("Updated hospital: {}", updated.getHospitalId());
                    return mapToDto(updated);
                });
    }

    @Transactional
    public boolean deleteHospital(UUID id) {
        if (hospitalRepository.existsById(id)) {
            hospitalRepository.deleteById(id);
            log.info("Deleted hospital with ID: {}", id);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<HospitalDto> getHospitalsByStatus(Hospital.HospitalStatus status) {
        return hospitalRepository.findByStatus(status).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HospitalDto> getIntegrationEnabledHospitals() {
        return hospitalRepository.findByIntegrationEnabledTrue().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private HospitalDto mapToDto(Hospital hospital) {
        HospitalDto dto = new HospitalDto();
        dto.setId(hospital.getId());
        dto.setHospitalId(hospital.getHospitalId());
        dto.setName(hospital.getName());
        dto.setAddress(hospital.getAddress());
        dto.setCity(hospital.getCity());
        dto.setState(hospital.getState());
        dto.setCountry(hospital.getCountry());
        dto.setPostalCode(hospital.getPostalCode());
        dto.setPhone(hospital.getPhone());
        dto.setEmail(hospital.getEmail());
        dto.setWebsite(hospital.getWebsite());
        dto.setStatus(hospital.getStatus());
        dto.setIntegrationEnabled(hospital.getIntegrationEnabled());
        dto.setApiEndpoint(hospital.getApiEndpoint());
        dto.setSupportedDataTypes(hospital.getSupportedDataTypes());
        dto.setCreatedAt(hospital.getCreatedAt());
        dto.setUpdatedAt(hospital.getUpdatedAt());
        return dto;
    }

    private Hospital mapToEntity(HospitalDto dto) {
        Hospital hospital = new Hospital();
        hospital.setHospitalId(dto.getHospitalId());
        hospital.setName(dto.getName());
        hospital.setAddress(dto.getAddress());
        hospital.setCity(dto.getCity());
        hospital.setState(dto.getState());
        hospital.setCountry(dto.getCountry());
        hospital.setPostalCode(dto.getPostalCode());
        hospital.setPhone(dto.getPhone());
        hospital.setEmail(dto.getEmail());
        hospital.setWebsite(dto.getWebsite());
        hospital.setStatus(dto.getStatus());
        hospital.setIntegrationEnabled(dto.getIntegrationEnabled());
        hospital.setApiEndpoint(dto.getApiEndpoint());
        hospital.setSupportedDataTypes(dto.getSupportedDataTypes());
        return hospital;
    }

    private void updateEntityFromDto(Hospital hospital, HospitalDto dto) {
        hospital.setName(dto.getName());
        hospital.setAddress(dto.getAddress());
        hospital.setCity(dto.getCity());
        hospital.setState(dto.getState());
        hospital.setCountry(dto.getCountry());
        hospital.setPostalCode(dto.getPostalCode());
        hospital.setPhone(dto.getPhone());
        hospital.setEmail(dto.getEmail());
        hospital.setWebsite(dto.getWebsite());
        hospital.setStatus(dto.getStatus());
        hospital.setIntegrationEnabled(dto.getIntegrationEnabled());
        hospital.setApiEndpoint(dto.getApiEndpoint());
        hospital.setSupportedDataTypes(dto.getSupportedDataTypes());
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private UUID getCurrentUserIdAsUUID() {
        String userIdStr = getCurrentUserId();
        if (userIdStr != null && !userIdStr.equals("system")) {
            try {
                return UUID.fromString(userIdStr);
            } catch (Exception e) {
                log.warn("Invalid userId format: {}", userIdStr);
            }
        }
        return null;
    }

    /**
     * Publish device paired event
     */
    public void publishDevicePairedEvent(UUID hospitalId, String deviceId, String deviceType, String protocol, String integrationStatus) {
        publishHospitalEvent(hospitalId, DEVICE_PAIRED, null,
            Map.of("deviceId", deviceId, "deviceType", deviceType, "protocol", protocol, "integrationStatus", integrationStatus));
    }

    /**
     * Publish protocol detected event
     */
    public void publishProtocolDetectedEvent(UUID hospitalId, String deviceId, String deviceType, String protocol, String integrationStatus) {
        publishHospitalEvent(hospitalId, PROTOCOL_DETECTED, null,
            Map.of("deviceId", deviceId, "deviceType", deviceType, "protocol", protocol, "integrationStatus", integrationStatus));
    }

    /**
     * Publish data transfer initiated event
     */
    public void publishDataTransferInitiatedEvent(UUID hospitalId, String deviceId, String deviceType, String protocol, String integrationStatus) {
        publishHospitalEvent(hospitalId, DATA_TRANSFER_INITIATED, null,
            Map.of("deviceId", deviceId, "deviceType", deviceType, "protocol", protocol, "integrationStatus", integrationStatus));
    }

    /**
     * Publish authentication success event
     */
    public void publishAuthenticationSuccessEvent(UUID hospitalId, String deviceId, String deviceType, String protocol, String integrationStatus) {
        publishHospitalEvent(hospitalId, AUTHENTICATION_SUCCESS, null,
            Map.of("deviceId", deviceId, "deviceType", deviceType, "protocol", protocol, "integrationStatus", integrationStatus));
    }

    /**
     * Publish authentication failure event
     */
    public void publishAuthenticationFailureEvent(UUID hospitalId, String deviceId, String deviceType, String protocol, String integrationStatus) {
        publishHospitalEvent(hospitalId, AUTHENTICATION_FAILURE, null,
            Map.of("deviceId", deviceId, "deviceType", deviceType, "protocol", protocol, "integrationStatus", integrationStatus));
    }

    /**
     * Process incoming data stream and detect protocol
     * @param hospitalId The hospital ID
     * @param deviceId The device ID
     * @param deviceType The device type
     * @param data The incoming data bytes
     * @return The detected protocol, or null if unknown
     */
    public DataTransferSession.TransferProtocol processIncomingDataStream(UUID hospitalId, String deviceId, String deviceType, byte[] data) {
        log.info("Processing incoming data stream for hospital: {}, device: {}", hospitalId, deviceId);

        // Use protocol detection service to analyze the data
        return protocolDetectionService.detectAndPublishProtocol(
            hospitalId.toString(),
            deviceId,
            deviceType,
            data
        );
    }

    /**
     * Publish hospital event to Kafka
     */
    private void publishHospitalEvent(UUID hospitalId, String eventType, UUID actorId, Map<String, Object> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"hospitalId\":\"%s\",\"actorId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    UUID.randomUUID(), eventType, hospitalId, actorId, java.time.LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.HOSPITAL_EVENTS, hospitalId.toString(), eventMessage);
            log.debug("Published hospital event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish hospital event for hospital {} event {}", hospitalId, eventType, e);
        }
    }
}