package com.mayo.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a compliance violation
 */
@Entity
@Table(name = "compliance_violations", schema = "audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "violation_type", nullable = false, length = 100)
    private String violationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private ComplianceRule.Severity severity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "details", columnDefinition = "jsonb")
    private JsonNode details;

    @Column(name = "resolved")
    private Boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}