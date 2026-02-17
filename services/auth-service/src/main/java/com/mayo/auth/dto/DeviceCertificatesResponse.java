package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * DTO for paginated device certificates response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCertificatesResponse {

    private List<CertificateSyncDto> certificates;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    private boolean hasNext;
    private boolean hasPrevious;
}