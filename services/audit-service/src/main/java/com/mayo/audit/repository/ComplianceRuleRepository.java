package com.mayo.audit.repository;

import com.mayo.audit.entity.ComplianceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ComplianceRule entity
 */
@Repository
public interface ComplianceRuleRepository extends JpaRepository<ComplianceRule, UUID> {

    List<ComplianceRule> findByStatus(ComplianceRule.RuleStatus status);

    List<ComplianceRule> findByRuleType(ComplianceRule.RuleType ruleType);

    List<ComplianceRule> findBySeverity(ComplianceRule.Severity severity);

    List<ComplianceRule> findByComplianceFramework(ComplianceRule.ComplianceFramework framework);

    @Query("SELECT r FROM ComplianceRule r WHERE r.status = :status AND r.ruleType = :ruleType")
    List<ComplianceRule> findByStatusAndRuleType(@Param("status") ComplianceRule.RuleStatus status,
                                                @Param("ruleType") ComplianceRule.RuleType ruleType);

    @Query("SELECT r FROM ComplianceRule r WHERE r.status = 'ACTIVE' AND r.complianceFramework = :framework ORDER BY r.severity DESC, r.createdAt DESC")
    List<ComplianceRule> findActiveRulesByFramework(@Param("framework") ComplianceRule.ComplianceFramework framework);

    @Query("SELECT r FROM ComplianceRule r WHERE r.status = 'ACTIVE' ORDER BY r.severity DESC, r.createdAt DESC")
    List<ComplianceRule> findActiveRulesOrderedByPriority();
}