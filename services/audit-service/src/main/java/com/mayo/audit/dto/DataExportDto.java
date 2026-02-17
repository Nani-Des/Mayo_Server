package com.mayo.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for data export jobs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataExportDto {

    private UUID id;
    private String jobType;
    private Object parameters; // JSON parameters
    private String status;
    private String format;
    private String filePath;
    private Integer recordCount;
    private Long fileSize;
    private UUID requestedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}