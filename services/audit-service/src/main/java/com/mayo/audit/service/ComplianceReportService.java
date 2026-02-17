package com.mayo.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.audit.dto.ComplianceReportDto;
import com.mayo.audit.entity.ComplianceReport;
import com.mayo.audit.repository.ComplianceReportRepository;
import com.mayo.audit.repository.ComplianceViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for generating compliance reports
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceReportService {

    private final ComplianceReportRepository complianceReportRepository;
    private final ComplianceViolationRepository complianceViolationRepository;
    private final AuditQueryService auditQueryService;
    private final ObjectMapper objectMapper;
    private final ReportGenerator reportGenerator;

    private static final String REPORTS_DIR = "reports";
    private static final int REPORT_RETENTION_DAYS = 30;

    /**
     * Generate a compliance report asynchronously
     */
    @Async
    public CompletableFuture<ComplianceReportDto> generateReport(String reportType, LocalDateTime startDate,
            LocalDateTime endDate, String format,
            UUID generatedBy) {
        try {
            log.info("Starting generation of {} report for period {} to {}", reportType, startDate, endDate);

            // Create report entity
            ComplianceReport report = new ComplianceReport();
            report.setReportType(reportType);
            report.setFormat(format);
            report.setStatus("GENERATING");
            report.setGeneratedBy(generatedBy);
            report.setGeneratedAt(LocalDateTime.now());

            // Set parameters
            Map<String, Object> params = Map.of(
                    "startDate", startDate,
                    "endDate", endDate,
                    "reportType", reportType);
            report.setParameters(objectMapper.valueToTree(params));

            report = complianceReportRepository.save(report);

            // Generate the actual report
            String filePath = generateReportFile(report, startDate, endDate);
            long fileSize = Files.size(Paths.get(filePath));

            // Update report with file info
            report.setFilePath(filePath);
            report.setFileSize(fileSize);
            report.setStatus("COMPLETED");
            report.setExpiresAt(LocalDateTime.now().plusDays(REPORT_RETENTION_DAYS));
            report.setRecordCount(getRecordCountForReport(reportType, startDate, endDate));

            report = complianceReportRepository.save(report);

            log.info("Successfully generated report {} with file size {} bytes", report.getId(), fileSize);

            return CompletableFuture.completedFuture(convertToDto(report));

        } catch (Exception e) {
            log.error("Failed to generate compliance report: {}", e.getMessage(), e);
            throw new RuntimeException("Report generation failed", e);
        }
    }

    /**
     * Get compliance reports with pagination
     */
    public Page<ComplianceReportDto> getComplianceReports(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return complianceReportRepository.findActiveReports(now, pageable)
                .map(this::convertToDto);
    }

    /**
     * Get compliance report file content
     */
    public byte[] getReportFile(UUID reportId) throws IOException {
        ComplianceReport report = complianceReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        if (report.getExpiresAt() != null && report.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Report has expired");
        }

        Path filePath = Paths.get(report.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Report file not found");
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Generate compliance summary metrics
     */
    public Map<String, Object> generateComplianceSummary(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime start = startDate != null ? startDate : LocalDateTime.now().minusDays(30);
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();

        // Get violation statistics
        long totalViolations = complianceViolationRepository.findByDateRange(start, end).size();
        long unresolvedViolations = complianceViolationRepository.countUnresolvedBySeverity(
                com.mayo.audit.entity.ComplianceRule.Severity.CRITICAL) +
                complianceViolationRepository.countUnresolvedBySeverity(
                        com.mayo.audit.entity.ComplianceRule.Severity.HIGH)
                +
                complianceViolationRepository.countUnresolvedBySeverity(
                        com.mayo.audit.entity.ComplianceRule.Severity.MEDIUM)
                +
                complianceViolationRepository.countUnresolvedBySeverity(
                        com.mayo.audit.entity.ComplianceRule.Severity.LOW);

        // Get audit statistics
        var auditStats = auditQueryService.getAuditStatistics(start, end);

        return Map.of(
                "period", Map.of("start", start, "end", end),
                "violations", Map.of(
                        "total", totalViolations,
                        "unresolved", unresolvedViolations,
                        "resolved", totalViolations - unresolvedViolations),
                "auditEvents", Map.of(
                        "total", auditStats.getTotalEvents(),
                        "last24Hours", auditStats.getEventsLast24Hours(),
                        "last7Days", auditStats.getEventsLast7Days(),
                        "last30Days", auditStats.getEventsLast30Days()),
                "complianceScore", calculateComplianceScore(totalViolations, auditStats.getTotalEvents()));
    }

    private String generateReportFile(ComplianceReport report, LocalDateTime startDate, LocalDateTime endDate)
            throws IOException {
        // Ensure reports directory exists
        Path reportsPath = Paths.get(REPORTS_DIR);
        if (!Files.exists(reportsPath)) {
            Files.createDirectories(reportsPath);
        }

        String fileName = String.format("%s_%s_%s.%s",
                report.getReportType().toLowerCase(),
                startDate.toLocalDate(),
                endDate.toLocalDate(),
                report.getFormat().toLowerCase());

        Path filePath = reportsPath.resolve(fileName);

        // Generate report based on type and format
        switch (report.getFormat().toUpperCase()) {
            case "PDF":
                reportGenerator.generatePdfReport(report, startDate, endDate, filePath);
                break;
            case "CSV":
                reportGenerator.generateCsvReport(report, startDate, endDate, filePath);
                break;
            case "JSON":
                reportGenerator.generateJsonReport(report, startDate, endDate, filePath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + report.getFormat());
        }

        return filePath.toString();
    }

    private int getRecordCountForReport(String reportType, LocalDateTime startDate, LocalDateTime endDate) {
        return switch (reportType.toUpperCase()) {
            case "VIOLATIONS" -> complianceViolationRepository.findByDateRange(startDate, endDate).size();
            case "AUDIT_EVENTS" -> (int) auditQueryService.getAuditStatistics(startDate, endDate).getTotalEvents();
            case "COMPLIANCE_SUMMARY" -> complianceViolationRepository.findByDateRange(startDate, endDate).size() +
                    (int) auditQueryService.getAuditStatistics(startDate, endDate).getTotalEvents();
            case "HIPAA_COMPLIANCE", "GDPR_COMPLIANCE", "NIST_COMPLIANCE", "ISO27001_COMPLIANCE",
                 "GHANA_DATA_PROTECTION_COMPLIANCE" ->
                // For regulatory reports, count relevant compliance violations and audit events
                    complianceViolationRepository.findByDateRange(startDate, endDate).size() +
                            (int) auditQueryService.getAuditStatistics(startDate, endDate).getTotalEvents();
            default -> 0;
        };
    }

    private double calculateComplianceScore(long violations, long totalEvents) {
        if (totalEvents == 0)
            return 100.0;
        // Simple compliance score: 100 - (violations/totalEvents * 100), with minimum 0
        double score = 100.0 - ((double) violations / totalEvents * 100.0);
        return Math.max(0.0, score);
    }

    private ComplianceReportDto convertToDto(ComplianceReport report) {
        return ComplianceReportDto.builder()
                .id(report.getId())
                .reportType(report.getReportType())
                .parameters(report.getParameters())
                .status(report.getStatus())
                .format(report.getFormat())
                .filePath(report.getFilePath())
                .fileSize(report.getFileSize())
                .recordCount(report.getRecordCount())
                .generatedBy(report.getGeneratedBy())
                .generatedAt(report.getGeneratedAt())
                .expiresAt(report.getExpiresAt())
                .createdAt(report.getCreatedAt())
                .build();
    }
}