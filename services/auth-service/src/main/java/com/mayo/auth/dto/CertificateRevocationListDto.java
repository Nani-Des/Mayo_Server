package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for certificate revocation list response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateRevocationListDto {

    private String crlPem;
    private Instant thisUpdate;
    private Instant nextUpdate;
    private List<RevokedCertificateDto> revokedCertificates;
    private int version;
}