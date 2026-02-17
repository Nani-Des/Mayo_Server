package com.mayo.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mayo.audit.entity.ComplianceRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for compliance violations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceViolationDto {

    private UUID id;
    private UUID ruleId;
    private String ruleName;
    private ComplianceRule.ComplianceFramework framework;
    private UUID eventId;
    private String violationType;
    private ComplianceRule.Severity severity;
    private String description;
    private JsonNode details;
    private Boolean resolved;
    private LocalDateTime resolvedAt;
    private UUID resolvedBy;
    private LocalDateTime createdAt;
}