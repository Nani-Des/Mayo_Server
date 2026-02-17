package com.mayo.hospitalintegration.dto;

import com.mayo.common.core.enums.DeviceType;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalDeviceDto {

    private UUID id;
    private String deviceId;
    private UUID hospitalId;
    private String hospitalName;
    private DeviceType deviceType;
    private String deviceName;
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String firmwareVersion;
    private String ipAddress;
    private String macAddress;
    private HospitalDevice.ConnectionType connectionType;
    private HospitalDevice.DeviceStatus status;
    private String supportedProtocols;
    private String supportedDataFormats;
    private String certificate;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime registeredAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}