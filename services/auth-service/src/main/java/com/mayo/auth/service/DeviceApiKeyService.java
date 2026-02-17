package com.mayo.auth.service;

import com.mayo.auth.dto.DeviceApiKeyDto;
import com.mayo.auth.dto.DeviceApiKeyRequest;
import com.mayo.auth.entity.DeviceApiKey;
import com.mayo.auth.repository.DeviceApiKeyRepository;
import com.mayo.events.topics.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing device API keys
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceApiKeyService {

    private final DeviceApiKeyRepository apiKeyRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new API key for a device
     */
    @Transactional
    public DeviceApiKeyDto generateApiKey(DeviceApiKeyRequest request) {
        log.info("Generating API key for device: {} in hospital: {}", request.getDeviceId(), request.getHospitalId());

        // Generate unique API key
        String apiKey;
        do {
            apiKey = generateSecureApiKey();
        } while (apiKeyRepository.existsByApiKey(apiKey));

        // Create API key entity
        DeviceApiKey deviceApiKey = DeviceApiKey.builder()
                .apiKey(apiKey)
                .deviceId(request.getDeviceId())
                .hospitalId(request.getHospitalId())
                .name(request.getName())
                .description(request.getDescription())
                .status(DeviceApiKey.Status.ACTIVE)
                .expiresAt(request.getExpiresAt())
                .build();

        DeviceApiKey savedKey = apiKeyRepository.save(deviceApiKey);
        log.info("API key generated successfully for device: {}", request.getDeviceId());

        return convertToDto(savedKey);
    }

    /**
     * Validate API key and return device information
     */
    public Optional<DeviceApiKeyDto> validateApiKey(String apiKey) {
        Optional<DeviceApiKey> keyOptional = apiKeyRepository.findByApiKey(apiKey);

        if (keyOptional.isEmpty()) {
            log.debug("API key not found: {}", maskApiKey(apiKey));
            return Optional.empty();
        }

        DeviceApiKey key = keyOptional.get();

        if (!key.isActive()) {
            log.warn("API key is not active: {} (status: {})", maskApiKey(apiKey), key.getStatus());
            return Optional.empty();
        }

        // Update last used timestamp
        key.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(key);

        log.debug("API key validated successfully for device: {}", key.getDeviceId());

        // Publish API key validation event
        publishApiKeyEvent(key.getDeviceId(), "API_KEY_VALIDATION_SUCCESS",
                Map.of("apiKeyId", key.getId().toString()));

        return Optional.of(convertToDto(key));
    }

    /**
     * Revoke API key
     */
    @Transactional
    public void revokeApiKey(UUID apiKeyId) {
        DeviceApiKey key = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        key.setStatus(DeviceApiKey.Status.REVOKED);
        apiKeyRepository.save(key);

        log.info("API key revoked for device: {}", key.getDeviceId());
    }

    /**
     * Get API keys for a device
     */
    public List<DeviceApiKeyDto> getApiKeysForDevice(String deviceId) {
        List<DeviceApiKey> keys = apiKeyRepository.findByDeviceId(deviceId);
        return keys.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get API keys for a hospital
     */
    public List<DeviceApiKeyDto> getApiKeysForHospital(UUID hospitalId) {
        List<DeviceApiKey> keys = apiKeyRepository.findByHospitalId(hospitalId);
        return keys.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Scheduled task to expire old API keys
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void expireOldApiKeys() {
        LocalDateTime now = LocalDateTime.now();
        List<DeviceApiKey> expiredKeys = apiKeyRepository.findExpiredKeys(now);

        for (DeviceApiKey key : expiredKeys) {
            key.setStatus(DeviceApiKey.Status.EXPIRED);
            apiKeyRepository.save(key);
            log.info("API key expired for device: {}", key.getDeviceId());
        }

        if (!expiredKeys.isEmpty()) {
            log.info("Expired {} API keys", expiredKeys.size());
        }
    }

    /**
     * Generate a secure random API key
     */
    private String generateSecureApiKey() {
        byte[] keyBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(keyBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
    }

    /**
     * Mask API key for logging
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * Convert entity to DTO
     */
    private DeviceApiKeyDto convertToDto(DeviceApiKey key) {
        return DeviceApiKeyDto.builder()
                .id(key.getId())
                .apiKey(maskApiKey(key.getApiKey())) // Never expose full API key
                .deviceId(key.getDeviceId())
                .hospitalId(key.getHospitalId())
                .name(key.getName())
                .description(key.getDescription())
                .status(key.getStatus().name())
                .expiresAt(key.getExpiresAt())
                .lastUsedAt(key.getLastUsedAt())
                .createdAt(key.getCreatedAt())
                .updatedAt(key.getUpdatedAt())
                .build();
    }

    /**
     * Publish API key event to audit service
     */
    private void publishApiKeyEvent(String deviceId, String eventType, Map<String, Object> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"deviceId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    java.util.UUID.randomUUID(), eventType, deviceId, java.time.LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.AUDIT_EVENTS, deviceId, eventMessage);
            log.debug("Published API key event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish API key event for device {} event {}", deviceId, eventType, e);
        }
    }
}