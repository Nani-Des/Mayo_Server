package com.mayo.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a compliance report
 */
@Entity
@Table(name = "compliance_reports", schema = "audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "report_type", nullable = false, length = 100)
    private String reportType;

    @Column(name = "parameters", columnDefinition = "jsonb")
    private JsonNode parameters;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "GENERATING";

    @Column(name = "format", nullable = false, length = 10)
    private String format = "PDF";

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "generated_by", nullable = false)
    private UUID generatedBy;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}