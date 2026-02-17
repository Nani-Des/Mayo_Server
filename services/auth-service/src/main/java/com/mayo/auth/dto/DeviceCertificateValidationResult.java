package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of device certificate validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCertificateValidationResult {

    private boolean valid;
    private String deviceId;
    private String certificateSubject;
    private String certificateIssuer;
    private Instant validFrom;
    private Instant validUntil;
    private String errorMessage;
    private String validationDetails;
}