package com.mayo.auth.dto;

import com.mayo.common.core.enums.DeviceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for device registration
 */
@Data
@Builder
public class DeviceRegistrationResponse {
    private String deviceId;
    private UUID hospitalId;
    private String certificate;
    private DeviceStatus status;
    private LocalDateTime registeredAt;
}