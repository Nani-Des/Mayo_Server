package com.mayo.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.audit.dto.AuditEventDto;
import com.mayo.audit.dto.DataExportDto;
import com.mayo.audit.entity.DataExportJob;
import com.mayo.audit.repository.DataExportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling data export jobs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataExportService {

    private final DataExportJobRepository dataExportJobRepository;
    private final AuditQueryService auditQueryService;
    private final ObjectMapper objectMapper;
    private final ReportGenerator reportGenerator;

    private static final String EXPORTS_DIR = "exports";
    private static final int EXPORT_RETENTION_DAYS = 7;
    private static final int MAX_EXPORT_DAYS_RANGE = 90; // Maximum 90 days range for exports

    /**
     * Validate export request parameters
     */
    private void validateExportRequest(String jobType, LocalDateTime startDate, LocalDateTime endDate, String format) {
        if (jobType == null || jobType.trim().isEmpty()) {
            throw new IllegalArgumentException("Job type cannot be null or empty");
        }

        if (!Set.of("AUDIT_EVENTS", "VIOLATIONS", "USER_ACTIVITY").contains(jobType.toUpperCase())) {
            throw new IllegalArgumentException("Invalid job type: " + jobType);
        }

        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Format cannot be null or empty");
        }

        if (!Set.of("CSV", "JSON", "PDF").contains(format.toUpperCase())) {
            throw new IllegalArgumentException("Invalid format: " + format + ". Supported formats: CSV, JSON, PDF");
        }

        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }

            if (startDate.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Start date cannot be in the future");
            }

            long daysBetween = java.time.Duration.between(startDate, endDate).toDays();
            if (daysBetween > MAX_EXPORT_DAYS_RANGE) {
                throw new IllegalArgumentException(
                        "Export date range cannot exceed " + MAX_EXPORT_DAYS_RANGE + " days");
            }
        }
    }

    /**
     * Request a data export job asynchronously
     */
    @Async
    public CompletableFuture<DataExportDto> requestDataExport(String jobType, LocalDateTime startDate,
            LocalDateTime endDate, String format,
            UUID requestedBy) {
        try {
            // Validate input parameters
            validateExportRequest(jobType, startDate, endDate, format);

            log.info("Starting data export job: type={}, format={}, startDate={}, endDate={}",
                    jobType, format, startDate, endDate);

            // Create export job entity
            DataExportJob exportJob = new DataExportJob();
            exportJob.setJobType(jobType);
            exportJob.setFormat(format.toUpperCase());
            exportJob.setStatus("PROCESSING");
            exportJob.setRequestedBy(requestedBy);
            exportJob.setStartedAt(LocalDateTime.now());

            // Set parameters
            Map<String, Object> params = Map.of(
                    "startDate", startDate,
                    "endDate", endDate,
                    "jobType", jobType);
            exportJob.setParameters(objectMapper.valueToTree(params));

            exportJob = dataExportJobRepository.save(exportJob);

            // Generate the export file
            String filePath = generateExportFile(exportJob, startDate, endDate);
            long fileSize = Files.size(Paths.get(filePath));
            int recordCount = getRecordCountForExport(jobType, startDate, endDate);

            // Update job with completion info
            exportJob.setFilePath(filePath);
            exportJob.setFileSize(fileSize);
            exportJob.setRecordCount(recordCount);
            exportJob.setStatus("COMPLETED");
            exportJob.setCompletedAt(LocalDateTime.now());
            exportJob.setExpiresAt(LocalDateTime.now().plusDays(EXPORT_RETENTION_DAYS));

            exportJob = dataExportJobRepository.save(exportJob);

            log.info("Successfully completed export job {} with {} records, file size {} bytes",
                    exportJob.getId(), recordCount, fileSize);

            return CompletableFuture.completedFuture(convertToDto(exportJob));

        } catch (Exception e) {
            log.error("Failed to complete data export job: {}", e.getMessage(), e);
            throw new RuntimeException("Data export failed", e);
        }
    }

    /**
     * Get data export jobs with pagination
     */
    public Page<DataExportDto> getDataExportJobs(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return dataExportJobRepository.findActiveJobs(now, pageable)
                .map(this::convertToDto);
    }

    /**
     * Get exported data file content
     */
    public byte[] getExportedDataFile(UUID jobId) throws IOException {
        return getExportedDataFileWithMetadata(jobId).getFileContent();
    }

    /**
     * Get exported data file content with metadata
     */
    public ExportedDataResult getExportedDataFileWithMetadata(UUID jobId) throws IOException {
        DataExportJob exportJob = dataExportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Export job not found"));

        if (exportJob.getExpiresAt() != null && exportJob.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Export file has expired");
        }

        if (!"COMPLETED".equals(exportJob.getStatus())) {
            throw new IllegalArgumentException("Export job is not completed");
        }

        Path filePath = Paths.get(exportJob.getFilePath());
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Export file not found");
        }

        byte[] fileContent = Files.readAllBytes(filePath);
        return new ExportedDataResult(fileContent, exportJob.getFormat(), exportJob.getJobType());
    }

    /**
     * Clean up expired export files
     */
    @Transactional
    public void cleanupExpiredExports() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(EXPORT_RETENTION_DAYS);
        List<DataExportJob> expiredJobs = dataExportJobRepository.findStalePendingJobs(cutoff);

        for (DataExportJob job : expiredJobs) {
            try {
                if (job.getFilePath() != null && Files.exists(Paths.get(job.getFilePath()))) {
                    Files.delete(Paths.get(job.getFilePath()));
                    log.info("Deleted expired export file: {}", job.getFilePath());
                }
            } catch (IOException e) {
                log.warn("Failed to delete expired export file {}: {}", job.getFilePath(), e.getMessage());
            }
        }

        log.info("Cleaned up {} expired export jobs", expiredJobs.size());
    }

    private String generateExportFile(DataExportJob exportJob, LocalDateTime startDate, LocalDateTime endDate)
            throws IOException {
        // Ensure exports directory exists
        Path exportsPath = Paths.get(EXPORTS_DIR);
        if (!Files.exists(exportsPath)) {
            Files.createDirectories(exportsPath);
        }

        String fileName = String.format("export_%s_%s_%s.%s",
                exportJob.getJobType().toLowerCase(),
                startDate.toLocalDate(),
                endDate.toLocalDate(),
                exportJob.getFormat().toLowerCase());

        Path filePath = exportsPath.resolve(fileName);

        // Generate export based on job type and format
        switch (exportJob.getJobType().toUpperCase()) {
            case "AUDIT_EVENTS":
                generateAuditEventsExport(exportJob, startDate, endDate, filePath);
                break;
            case "VIOLATIONS":
                generateViolationsExport(exportJob, startDate, endDate, filePath);
                break;
            case "USER_ACTIVITY":
                generateUserActivityExport(exportJob, startDate, endDate, filePath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported export job type: " + exportJob.getJobType());
        }

        return filePath.toString();
    }

    private void generateAuditEventsExport(DataExportJob exportJob, LocalDateTime startDate, LocalDateTime endDate,
            Path filePath) throws IOException {
        // Create a temporary compliance report for reuse of report generation logic
        com.mayo.audit.entity.ComplianceReport tempReport = new com.mayo.audit.entity.ComplianceReport();
        tempReport.setReportType("AUDIT_EVENTS");
        tempReport.setFormat(exportJob.getFormat());

        switch (exportJob.getFormat().toUpperCase()) {
            case "CSV":
                reportGenerator.generateCsvReport(tempReport, startDate, endDate, filePath);
                break;
            case "JSON":
                reportGenerator.generateJsonReport(tempReport, startDate, endDate, filePath);
                break;
            case "PDF":
                reportGenerator.generatePdfReport(tempReport, startDate, endDate, filePath);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported format for audit events export: " + exportJob.getFormat());
        }
    }

    private void generateViolationsExport(DataExportJob exportJob, LocalDateTime startDate, LocalDateTime endDate,
            Path filePath) throws IOException {
        com.mayo.audit.entity.ComplianceReport tempReport = new com.mayo.audit.entity.ComplianceReport();
        tempReport.setReportType("VIOLATIONS");
        tempReport.setFormat(exportJob.getFormat());

        switch (exportJob.getFormat().toUpperCase()) {
            case "CSV":
                reportGenerator.generateCsvReport(tempReport, startDate, endDate, filePath);
                break;
            case "JSON":
                reportGenerator.generateJsonReport(tempReport, startDate, endDate, filePath);
                break;
            case "PDF":
                reportGenerator.generatePdfReport(tempReport, startDate, endDate, filePath);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported format for violations export: " + exportJob.getFormat());
        }
    }

    private void generateUserActivityExport(DataExportJob exportJob, LocalDateTime startDate, LocalDateTime endDate,
            Path filePath) throws IOException {
        // Get user activity data - group audit events by user
        Page<AuditEventDto> events = auditQueryService.getAuditEventsWithFilters(
                null, null, null, null, startDate, endDate, PageRequest.of(0, 50000));

        List<String> lines = new java.util.ArrayList<>();
        lines.add("User ID,Total Events,Last Activity,Actions Count");

        // Group by user
        Map<UUID, List<AuditEventDto>> eventsByUser = events.getContent().stream()
                .filter(e -> e.getUserId() != null)
                .collect(java.util.stream.Collectors.groupingBy(AuditEventDto::getUserId));

        for (Map.Entry<UUID, List<AuditEventDto>> entry : eventsByUser.entrySet()) {
            UUID userId = entry.getKey();
            List<AuditEventDto> userEvents = entry.getValue();

            LocalDateTime lastActivity = userEvents.stream()
                    .map(AuditEventDto::getTimestamp)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            Map<String, Long> actionsCount = userEvents.stream()
                    .filter(e -> e.getAction() != null)
                    .collect(java.util.stream.Collectors.groupingBy(
                            e -> e.getAction(),
                            java.util.stream.Collectors.counting()));

            lines.add(String.format("%s,%d,%s,%s",
                    userId,
                    userEvents.size(),
                    lastActivity != null ? lastActivity.toString() : "",
                    actionsCount.toString()));
        }

        Files.write(filePath, lines);
    }

    private int getRecordCountForExport(String jobType, LocalDateTime startDate, LocalDateTime endDate) {
        switch (jobType.toUpperCase()) {
            case "AUDIT_EVENTS":
                return (int) auditQueryService.getAuditEventsWithFilters(
                        null, null, null, null, startDate, endDate, PageRequest.of(0, 1)).getTotalElements();
            case "VIOLATIONS":
                return dataExportJobRepository.findAll().size(); // Placeholder - should query violations
            case "USER_ACTIVITY":
                return auditQueryService.getAuditEventsWithFilters(
                        null, null, null, null, startDate, endDate, PageRequest.of(0, 1)).getContent().stream()
                        .map(AuditEventDto::getUserId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet()).size();
            default:
                return 0;
        }
    }

    private DataExportDto convertToDto(DataExportJob exportJob) {
        return DataExportDto.builder()
                .id(exportJob.getId())
                .jobType(exportJob.getJobType())
                .parameters(exportJob.getParameters())
                .status(exportJob.getStatus())
                .format(exportJob.getFormat())
                .filePath(exportJob.getFilePath())
                .recordCount(exportJob.getRecordCount())
                .fileSize(exportJob.getFileSize())
                .requestedBy(exportJob.getRequestedBy())
                .startedAt(exportJob.getStartedAt())
                .completedAt(exportJob.getCompletedAt())
                .expiresAt(exportJob.getExpiresAt())
                .createdAt(exportJob.getCreatedAt())
                .build();
    }

    /**
     * Result class for exported data with metadata
     */
    public static class ExportedDataResult {
        private final byte[] fileContent;
        private final String format;
        private final String jobType;

        public ExportedDataResult(byte[] fileContent, String format, String jobType) {
            this.fileContent = fileContent;
            this.format = format;
            this.jobType = jobType;
        }

        public byte[] getFileContent() {
            return fileContent;
        }

        public String getFormat() {
            return format;
        }

        public String getJobType() {
            return jobType;
        }
    }
}