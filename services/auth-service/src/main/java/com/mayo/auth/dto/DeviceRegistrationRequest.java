package com.mayo.auth.dto;

import com.mayo.common.core.enums.DeviceType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Request DTO for device registration
 */
@Data
@Builder
public class DeviceRegistrationRequest {
    private String deviceId;
    private UUID hospitalId;
    private DeviceType deviceType;
    private String deviceName;
    private String publicKey;
}