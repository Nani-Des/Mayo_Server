package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for CA certificate and public key response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaCertificateDto {

    private String certificatePem;
    private String publicKeyPem;
    private String certificateSubject;
    private String certificateIssuer;
    private Instant validFrom;
    private Instant validUntil;
}