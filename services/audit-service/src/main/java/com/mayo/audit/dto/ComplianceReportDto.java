package com.mayo.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for compliance reports
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceReportDto {

    private UUID id;
    private String reportType;
    private Object parameters; // JSON parameters
    private String status;
    private String format;
    private String filePath;
    private Long fileSize;
    private Integer recordCount;
    private UUID generatedBy;
    private LocalDateTime generatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}