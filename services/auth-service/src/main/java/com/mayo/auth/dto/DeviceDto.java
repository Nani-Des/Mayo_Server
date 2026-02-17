package com.mayo.auth.dto;

import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.common.core.enums.DeviceType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Device information DTO
 */
@Data
@Builder
public class DeviceDto {
    private UUID id;
    private String deviceId;
    private DeviceType deviceType;
    private String publicKey;
    private DeviceStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime lastCertificateUpdate;
    private UUID userId;
}