package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for device authorization request (OAuth 2.0 Device Flow)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAuthorizationResponse {

    private String deviceCode;
    private String userCode;
    private String verificationUri;
    private String verificationUriComplete;
    private Integer expiresIn;
    private Integer interval;
}