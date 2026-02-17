package com.mayo.audit.repository;

import com.mayo.audit.entity.ComplianceRule;
import com.mayo.audit.entity.ComplianceViolation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ComplianceViolation entity
 */
@Repository
public interface ComplianceViolationRepository extends JpaRepository<ComplianceViolation, UUID> {

    List<ComplianceViolation> findByRuleId(UUID ruleId);

    List<ComplianceViolation> findByResolved(Boolean resolved);

    List<ComplianceViolation> findBySeverity(ComplianceRule.Severity severity);

    @Query("SELECT v FROM ComplianceViolation v WHERE v.createdAt BETWEEN :startDate AND :endDate")
    List<ComplianceViolation> findByDateRange(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT v FROM ComplianceViolation v WHERE v.resolved = false ORDER BY v.severity DESC, v.createdAt DESC")
    Page<ComplianceViolation> findUnresolvedViolations(Pageable pageable);

    @Query("SELECT COUNT(v) FROM ComplianceViolation v WHERE v.resolved = false AND v.severity = :severity")
    long countUnresolvedBySeverity(@Param("severity") ComplianceRule.Severity severity);
}