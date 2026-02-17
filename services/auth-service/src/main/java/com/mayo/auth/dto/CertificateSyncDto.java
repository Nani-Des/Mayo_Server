package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for certificate synchronization data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateSyncDto {

    private String deviceId;
    private String certificatePem;
    private String certificateSubject;
    private String certificateIssuer;
    private Instant validFrom;
    private Instant validUntil;
    private Instant lastUpdated;
    private String status;
}