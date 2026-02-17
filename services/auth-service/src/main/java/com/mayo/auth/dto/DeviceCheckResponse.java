package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for device registration check response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCheckResponse {
    private boolean isRegistered;
    private UUID hospitalId;
    private DeviceDto device;
}
