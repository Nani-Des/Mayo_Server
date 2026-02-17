package com.mayo.audit.repository;

import com.mayo.audit.entity.ComplianceReport;
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
 * Repository interface for ComplianceReport entity
 */
@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, UUID> {

    List<ComplianceReport> findByStatus(String status);

    List<ComplianceReport> findByReportType(String reportType);

    List<ComplianceReport> findByGeneratedBy(UUID generatedBy);

    @Query("SELECT r FROM ComplianceReport r WHERE r.expiresAt > :now ORDER BY r.createdAt DESC")
    Page<ComplianceReport> findActiveReports(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT r FROM ComplianceReport r WHERE r.status = 'GENERATING' AND r.createdAt < :cutoff")
    List<ComplianceReport> findStaleGeneratingReports(@Param("cutoff") LocalDateTime cutoff);
}