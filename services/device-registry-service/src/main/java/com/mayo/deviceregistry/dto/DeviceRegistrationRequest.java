package com.mayo.deviceregistry.dto;

import com.mayo.common.core.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Device registration request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {

    @NotBlank
    private String deviceId;

    @NotNull
    private DeviceType deviceType;

    @NotNull
    private UUID hospitalId;

    @NotBlank
    private String protocol;
}