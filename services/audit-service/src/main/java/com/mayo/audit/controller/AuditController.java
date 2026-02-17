package com.mayo.audit.controller;

import com.mayo.audit.dto.AuditEventDto;
import com.mayo.audit.dto.AuditStatisticsDto;
import com.mayo.audit.dto.ComplianceReportDto;
import com.mayo.audit.dto.ComplianceViolationDto;
import com.mayo.audit.dto.DataExportDto;
import com.mayo.audit.service.AuditQueryService;
import com.mayo.audit.service.ComplianceReportService;
import com.mayo.audit.service.DataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * REST controller for audit event queries and operations
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditQueryService auditQueryService;
    private final ComplianceReportService complianceReportService;
    private final DataExportService dataExportService;

    /**
     * Get audit events with filtering and pagination
     */
    @GetMapping("/events")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<AuditEventDto>> getAuditEvents(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Fetching audit events with filters - userId: {}, patientId: {}, action: {}, page: {}, size: {}",
                userId, patientId, action, page, size);

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<AuditEventDto> events = auditQueryService.getAuditEventsWithFilters(
                userId, patientId, action, resourceType, startDate, endDate, pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Get specific audit event by ID
     */
    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<AuditEventDto> getAuditEvent(@PathVariable String eventId) {
        log.info("Fetching audit event: {}", eventId);

        return auditQueryService.getAuditEventById(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search audit events using Elasticsearch
     */
    @GetMapping("/events/search")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<AuditEventDto>> searchAuditEvents(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info(
                "Searching audit events with filters - userId: {}, patientId: {}, action: {}, resourceType: {}, severity: {}, startDate: {}, endDate: {}, query: {}",
                userId, patientId, action, resourceType, severity, startDate, endDate, query);

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditEventDto> results = auditQueryService.searchAuditEvents(userId, patientId, action, resourceType,
                severity, startDate, endDate, query, pageable);

        return ResponseEntity.ok(results);
    }

    /**
     * Get audit events for a specific user
     */
    @GetMapping("/users/{userId}/events")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER') or #userId == authentication.principal")
    public ResponseEntity<Page<AuditEventDto>> getUserAuditEvents(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching audit events for user: {}", userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditEventDto> events = auditQueryService.getUserAuditEvents(userId, pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Get audit events for a specific patient
     */
    @GetMapping("/patients/{patientId}/events")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER') or hasRole('DOCTOR')")
    public ResponseEntity<Page<AuditEventDto>> getPatientAuditEvents(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching audit events for patient: {}", patientId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditEventDto> events = auditQueryService.getPatientAuditEvents(patientId, pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Get audit statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<AuditStatisticsDto> getAuditStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Fetching audit statistics for period: {} to {}", startDate, endDate);

        AuditStatisticsDto statistics = auditQueryService.getAuditStatistics(startDate, endDate);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Generate compliance report
     */
    @PostMapping("/compliance/reports")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ComplianceReportDto> generateComplianceReport(
            @RequestParam String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "PDF") String format) {

        log.info("Generating compliance report: type={}, format={}, startDate={}, endDate={}",
                reportType, format, startDate, endDate);

        try {
            // Get current user ID (placeholder - would come from security context)
            UUID generatedBy = UUID.randomUUID(); // TODO: Get from authentication

            var future = complianceReportService.generateReport(reportType, startDate, endDate, format, generatedBy);

            // For async processing, return accepted immediately
            // In a real implementation, you might want to wait for completion or return job
            // status
            ComplianceReportDto report = new ComplianceReportDto();
            report.setReportType(reportType);
            report.setFormat(format);
            report.setStatus("GENERATING");
            report.setGeneratedAt(LocalDateTime.now());

            return ResponseEntity.accepted().body(report);

        } catch (Exception e) {
            log.error("Failed to generate compliance report: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get compliance reports
     */
    @GetMapping("/compliance/reports")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<ComplianceReportDto>> getComplianceReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching compliance reports: page={}, size={}", page, size);

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<ComplianceReportDto> reports = complianceReportService.getComplianceReports(pageable);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            log.error("Failed to fetch compliance reports: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download compliance report
     */
    @GetMapping("/compliance/reports/{reportId}/download")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<byte[]> downloadComplianceReport(@PathVariable UUID reportId) {

        log.info("Downloading compliance report: {}", reportId);

        try {
            byte[] fileContent = complianceReportService.getReportFile(reportId);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"compliance_report_" + reportId + ".pdf\"")
                    .header("Content-Type", "application/pdf")
                    .body(fileContent);

        } catch (IllegalArgumentException e) {
            log.warn("Report not found or expired: {}", reportId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to download compliance report {}: {}", reportId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get compliance violations
     */
    @GetMapping("/compliance/violations")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<ComplianceViolationDto>> getComplianceViolations(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching compliance violations: resolved={}, page={}, size={}", resolved, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<ComplianceViolationDto> violations = auditQueryService.getComplianceViolations(resolved, pageable);
            return ResponseEntity.ok(violations);
        } catch (Exception e) {
            log.error("Failed to fetch compliance violations: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Resolve compliance violation
     */
    @PutMapping("/compliance/violations/{violationId}/resolve")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Void> resolveComplianceViolation(@PathVariable UUID violationId) {

        log.info("Resolving compliance violation: {}", violationId);

        // Implementation would update violation status
        return ResponseEntity.ok().build();
    }

    /**
     * Get compliance summary
     */
    @GetMapping("/compliance/summary")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Map<String, Object>> getComplianceSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("Fetching compliance summary for period: {} to {}", startDate, endDate);

        try {
            Map<String, Object> summary = auditQueryService.getComplianceSummary(startDate, endDate);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to generate compliance summary: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Request data export
     */
    @PostMapping("/data/export")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<DataExportDto> requestDataExport(
            @RequestParam String jobType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "CSV") String format) {

        log.info("Requesting data export: type={}, format={}, startDate={}, endDate={}",
                jobType, format, startDate, endDate);

        try {
            // Get current user ID (placeholder - would come from authentication)
            UUID requestedBy = UUID.randomUUID(); // TODO: Get from authentication

            var future = dataExportService.requestDataExport(jobType, startDate, endDate, format, requestedBy);

            // For async processing, return accepted immediately
            DataExportDto export = new DataExportDto();
            export.setJobType(jobType);
            export.setFormat(format);
            export.setStatus("PROCESSING");
            export.setCreatedAt(LocalDateTime.now());

            return ResponseEntity.accepted().body(export);

        } catch (Exception e) {
            log.error("Failed to request data export: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get data export jobs
     */
    @GetMapping("/data/export/jobs")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<Page<DataExportDto>> getDataExportJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching data export jobs: page={}, size={}", page, size);

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<DataExportDto> jobs = dataExportService.getDataExportJobs(pageable);
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            log.error("Failed to fetch data export jobs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download exported data
     */
    @GetMapping("/data/export/{jobId}/download")
    @PreAuthorize("hasRole('AUDIT_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<byte[]> downloadExportedData(@PathVariable UUID jobId) {

        log.info("Downloading exported data: {}", jobId);

        try {
            var exportData = dataExportService.getExportedDataFileWithMetadata(jobId);
            byte[] fileContent = exportData.getFileContent();
            String format = exportData.getFormat();
            String jobType = exportData.getJobType();

            String contentType;
            String fileExtension;
            switch (format.toUpperCase()) {
                case "PDF":
                    contentType = "application/pdf";
                    fileExtension = "pdf";
                    break;
                case "JSON":
                    contentType = "application/json";
                    fileExtension = "json";
                    break;
                case "CSV":
                default:
                    contentType = "text/csv";
                    fileExtension = "csv";
                    break;
            }

            String filename = String.format("%s_export_%s.%s", jobType.toLowerCase(), jobId, fileExtension);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", contentType)
                    .body(fileContent);

        } catch (IllegalArgumentException e) {
            log.warn("Export job not found, not completed, or expired: {}", jobId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to download exported data {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}