package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for device token (OAuth 2.0 Device Flow)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {

    private String grantType; // "urn:ietf:params:oauth:grant-type:device_code"
    private String deviceCode;
    private String clientId;
}