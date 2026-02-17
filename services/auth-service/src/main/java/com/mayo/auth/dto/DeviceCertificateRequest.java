package com.mayo.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Request DTO for device certificate generation
 */
@Data
@Builder
public class DeviceCertificateRequest {
    private String deviceId;
    private String commonName;
    private String organizationName;
    private String organizationalUnitName;
    private String countryCode;
    private String stateOrProvinceName;
    private String localityName;
    private Instant notBefore;
    private Instant notAfter;
    private String publicKeyPem;
}