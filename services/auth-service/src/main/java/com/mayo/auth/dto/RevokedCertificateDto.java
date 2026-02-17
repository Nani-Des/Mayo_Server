package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Instant;

/**
 * DTO for revoked certificate information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokedCertificateDto {

    private BigInteger serialNumber;
    private Instant revocationDate;
    private String revocationReason;
    private String deviceId;
}