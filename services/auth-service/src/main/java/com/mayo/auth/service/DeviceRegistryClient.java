package com.mayo.auth.service;

import com.mayo.common.core.enums.DeviceStatus;

import java.util.List;

/**
 * Client interface for device registry service
 */
public interface DeviceRegistryClient {

    /**
     * Validate device and get device information
     */
    DeviceInfo validateDevice(String deviceId);

    /**
     * Get all active device IDs
     */
    List<String> getActiveDeviceIds();

    /**
     * Device information DTO
     */
    class DeviceInfo {
        private String deviceId;
        private String hospitalId;
        private DeviceStatus status;
        private String protocol;
        private String deviceType;

        public DeviceInfo(String deviceId, String hospitalId, DeviceStatus status, String protocol, String deviceType) {
            this.deviceId = deviceId;
            this.hospitalId = hospitalId;
            this.status = status;
            this.protocol = protocol;
            this.deviceType = deviceType;
        }

        // Getters
        public String getDeviceId() {
            return deviceId;
        }

        public String getHospitalId() {
            return hospitalId;
        }

        public DeviceStatus getStatus() {
            return status;
        }

        public String getProtocol() {
            return protocol;
        }

        public String getDeviceType() {
            return deviceType;
        }
    }
}