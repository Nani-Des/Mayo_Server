package com.mayo.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_rules", schema = "audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Column(name = "conditions", nullable = false, columnDefinition = "jsonb")
    private JsonNode conditions;

    @Column(name = "actions", nullable = false, columnDefinition = "jsonb")
    private JsonNode actions;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private Severity severity = Severity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RuleStatus status = RuleStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_framework")
    private ComplianceFramework complianceFramework;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "version")
    private Integer version = 1;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RuleType {
        THRESHOLD, PATTERN, TIME_BASED, AGGREGATION
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum RuleStatus {
        ACTIVE, INACTIVE, DRAFT
    }

    public enum ComplianceFramework {
        HIPAA, GDPR, GHANA_DATA_PROTECTION_ACT, ISO_27001, NIST
    }
}