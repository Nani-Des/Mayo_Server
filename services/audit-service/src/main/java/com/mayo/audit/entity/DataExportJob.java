package com.mayo.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a data export job
 */
@Entity
@Table(name = "data_export_jobs", schema = "audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType;

    @Column(name = "parameters", columnDefinition = "jsonb")
    private JsonNode parameters;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "format", nullable = false, length = 10)
    private String format = "CSV";

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}