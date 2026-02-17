package com.mayo.hospitalintegration.controller;

import com.mayo.hospitalintegration.dto.*;
import com.mayo.hospitalintegration.entity.Activity;
import com.mayo.hospitalintegration.service.ActivityAnalyticsService;
import com.mayo.hospitalintegration.service.ActivityAuditService;
import com.mayo.hospitalintegration.service.ActivityQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Activity Management", description = "Activity tracking, querying, analytics, and compliance monitoring")
public class ActivityController {

    private final ActivityQueryService activityQueryService;
    private final ActivityAnalyticsService activityAnalyticsService;
    private final ActivityAuditService activityAuditService;

    @GetMapping
    @Operation(summary = "Query activities with advanced filtering and pagination")
    public ResponseEntity<Page<ActivityDto>> queryActivities(
            @Parameter(description = "Hospital ID filter") @RequestParam(required = false) UUID hospitalId,
            @Parameter(description = "Device ID filter") @RequestParam(required = false) UUID deviceId,
            @Parameter(description = "User ID filter") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Patient ID filter") @RequestParam(required = false) UUID patientId,
            @Parameter(description = "Activity type filter") @RequestParam(required = false) Activity.ActivityType activityType,
            @Parameter(description = "Record type filter") @RequestParam(required = false) Activity.RecordType recordType,
            @Parameter(description = "Start date filter") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date filter") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "timestamp") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDirection) {

        log.debug("Querying activities with filters");

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setHospitalId(hospitalId);
        request.setDeviceId(deviceId);
        request.setUserId(userId);
        request.setPatientId(patientId);
        request.setActivityType(activityType);
        request.setRecordType(recordType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPage(page);
        request.setSize(size);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);

        Page<ActivityDto> result = activityQueryService.queryActivities(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/hospital/{hospitalId}")
    @Operation(summary = "Query activities for a specific hospital")
    public ResponseEntity<Page<ActivityDto>> queryActivitiesByHospital(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "User ID filter") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Activity type filter") @RequestParam(required = false) Activity.ActivityType activityType,
            @Parameter(description = "Start date filter") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date filter") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        log.debug("Querying activities for hospital: {}", hospitalId);

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setUserId(userId);
        request.setActivityType(activityType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPage(page);
        request.setSize(size);

        Page<ActivityDto> result = activityQueryService.queryActivitiesByHospital(hospitalId, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Query activities for a specific user")
    public ResponseEntity<Page<ActivityDto>> queryActivitiesByUser(
            @Parameter(description = "User ID", required = true) @PathVariable UUID userId,
            @Parameter(description = "Hospital ID filter") @RequestParam(required = false) UUID hospitalId,
            @Parameter(description = "Activity type filter") @RequestParam(required = false) Activity.ActivityType activityType,
            @Parameter(description = "Start date filter") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date filter") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        log.debug("Querying activities for user: {}", userId);

        ActivityQueryRequest request = new ActivityQueryRequest();
        request.setHospitalId(hospitalId);
        request.setActivityType(activityType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPage(page);
        request.setSize(size);

        Page<ActivityDto> result = activityQueryService.queryActivitiesByUser(userId, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recent/{hospitalId}")
    @Operation(summary = "Get recent activities for a hospital")
    public ResponseEntity<List<ActivityDto>> getRecentActivities(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Maximum number of activities to return") @RequestParam(defaultValue = "50") int limit) {

        log.debug("Getting recent activities for hospital: {} with limit: {}", hospitalId, limit);

        List<ActivityDto> result = activityQueryService.getRecentActivities(hospitalId, limit);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analytics/{hospitalId}")
    @Operation(summary = "Get activity analytics for a hospital")
    public ResponseEntity<ActivityAnalyticsDto> getActivityAnalytics(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {

        log.debug("Getting activity analytics for hospital: {} over {} days", hospitalId, days);

        ActivityAnalyticsDto result = activityAnalyticsService.getActivityAnalytics(hospitalId, days);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/statistics/{hospitalId}")
    @Operation(summary = "Get activity statistics for a hospital in a date range")
    public ResponseEntity<ActivityStatisticsDto> getActivityStatistics(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Start date", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.debug("Getting activity statistics for hospital: {} from {} to {}", hospitalId, startDate, endDate);

        ActivityStatisticsDto result = activityAnalyticsService.getActivityStatistics(hospitalId, startDate, endDate);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/trends/{hospitalId}")
    @Operation(summary = "Get activity trends for a hospital")
    public ResponseEntity<Map<String, Long>> getActivityTrends(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "30") int days) {

        log.debug("Getting activity trends for hospital: {} over {} days", hospitalId, days);

        Map<String, Long> result = activityAnalyticsService.getActivityTrends(hospitalId, days);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/reports/compliance/{hospitalId}")
    @Operation(summary = "Generate compliance report for a hospital")
    public ResponseEntity<ActivityReportDto> generateComplianceReport(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Start date", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.debug("Generating compliance report for hospital: {} from {} to {}", hospitalId, startDate, endDate);

        ActivityReportDto result = activityAuditService.generateComplianceReport(hospitalId, startDate, endDate);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/reports/audit/{hospitalId}")
    @Operation(summary = "Generate audit report for a hospital")
    public ResponseEntity<ActivityReportDto> generateAuditReport(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Start date", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.debug("Generating audit report for hospital: {} from {} to {}", hospitalId, startDate, endDate);

        ActivityReportDto result = activityAuditService.generateAuditReport(hospitalId, startDate, endDate);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/compliance/violations/{hospitalId}")
    @Operation(summary = "Get compliance violations for a hospital")
    public ResponseEntity<List<ActivityDto>> getComplianceViolations(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Since date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {

        log.debug("Getting compliance violations for hospital: {} since {}", hospitalId, since);

        LocalDateTime sinceDate = since != null ? since : LocalDateTime.now().minusDays(30);
        List<ActivityDto> result = activityAuditService.getComplianceViolations(hospitalId, sinceDate);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/security/incidents/{hospitalId}")
    @Operation(summary = "Get users with security incidents for a hospital")
    public ResponseEntity<List<UUID>> getUsersWithSecurityIncidents(
            @Parameter(description = "Hospital ID", required = true) @PathVariable UUID hospitalId,
            @Parameter(description = "Since date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {

        log.debug("Getting users with security incidents for hospital: {} since {}", hospitalId, since);

        LocalDateTime sinceDate = since != null ? since : LocalDateTime.now().minusDays(30);
        List<UUID> result = activityAuditService.getUsersWithSecurityIncidents(hospitalId, sinceDate);
        return ResponseEntity.ok(result);
    }
}