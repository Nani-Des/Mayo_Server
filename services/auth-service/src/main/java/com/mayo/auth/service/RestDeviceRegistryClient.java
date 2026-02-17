package com.mayo.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.common.core.enums.DeviceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * REST client implementation for device registry service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestDeviceRegistryClient implements DeviceRegistryClient {

    @Value("${device.registry.url:http://device-registry-service:8084}")
    private String deviceRegistryUrl;

    @Value("${device.registry.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public DeviceInfo validateDevice(String deviceId) {
        try {
            log.debug("Validating device {} with registry service", deviceId);

            String url = deviceRegistryUrl + "/devices/by-device-id/" + deviceId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("X-API-Key", apiKey);
            }

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<DeviceRegistryResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, DeviceRegistryResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                DeviceRegistryResponse registryResponse = response.getBody();
                if (registryResponse.isActive()) {
                    return new DeviceInfo(
                            registryResponse.getDeviceId(),
                            registryResponse.getHospitalId(),
                            DeviceStatus.ACTIVE,
                            registryResponse.getProtocol(),
                            registryResponse.getDeviceType());
                } else {
                    log.warn("Device {} is not active according to registry", deviceId);
                    return null;
                }
            } else {
                log.warn("Device {} not found in registry (status: {})", deviceId, response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to validate device {} with registry service", deviceId, e);
            // Return null to indicate validation failure
            return null;
        }
    }

    @Override
    public List<String> getActiveDeviceIds() {
        try {
            log.debug("Fetching active device IDs from registry service");

            String url = deviceRegistryUrl + "/devices/active";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("X-API-Key", apiKey);
            }

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<String[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            } else {
                log.warn("Failed to fetch active devices (status: {})", response.getStatusCode());
                return List.of();
            }

        } catch (Exception e) {
            log.error("Failed to fetch active device IDs from registry service", e);
            return List.of();
        }
    }

    /**
     * Response DTO from device registry service
     */
    private static class DeviceRegistryResponse {
        private String deviceId;
        private String hospitalId;
        private boolean active;
        private String protocol;
        private String deviceType;

        // Getters and setters
        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getHospitalId() {
            return hospitalId;
        }

        public void setHospitalId(String hospitalId) {
            this.hospitalId = hospitalId;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public void setDeviceType(String deviceType) {
            this.deviceType = deviceType;
        }
    }
}