package com.mayo.deviceregistry.dto;

import com.mayo.common.core.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * DTO for admin device registration request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDeviceRegistrationRequest {

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    @NotBlank(message = "Hospital ID is required")
    private String hospitalId;

    private String deviceName;

    @NotBlank(message = "Admin User ID is required")
    private String adminUserId;
}
