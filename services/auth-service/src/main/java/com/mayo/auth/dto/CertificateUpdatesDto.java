package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for certificate updates since a timestamp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateUpdatesDto {

    private Instant since;
    private Instant currentTimestamp;
    private List<CertificateSyncDto> newCertificates;
    private List<CertificateSyncDto> updatedCertificates;
    private List<RevokedCertificateDto> revokedCertificates;
    private boolean hasMoreUpdates;
}