package com.mayo.deviceregistry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for public device check response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCheckResponse {

    private boolean registered;
    private String deviceId;
    private String hospitalId;
    private String deviceName;
    private String status;
    private String registeredAt;
}
