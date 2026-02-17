package com.mayo.deviceregistry.dto;

import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.common.core.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Device DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDto {

    private UUID id;
    private String deviceId;
    private DeviceType deviceType;
    private UUID hospitalId;
    private DeviceStatus status;
    private String protocol;
    private String deviceName;
    private LocalDateTime lastHeartbeat;
    private String pairingCode;
    private UUID registeredBy;
    private LocalDateTime registeredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
