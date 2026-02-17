package com.mayo.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mayo.audit.entity.ComplianceRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for ComplianceRule entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceRuleDto {

    private UUID id;
    private String name;
    private String description;
    private ComplianceRule.RuleType ruleType;
    private JsonNode conditions;
    private JsonNode actions;
    private ComplianceRule.Severity severity;
    private ComplianceRule.RuleStatus status;
    private ComplianceRule.ComplianceFramework complianceFramework;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}