package com.mayo.hospitalintegration.dto;

import com.mayo.hospitalintegration.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityReportDto {

    private String reportType; // "COMPLIANCE", "AUDIT", "SECURITY"
    private UUID hospitalId;
    private String hospitalName;
    private LocalDateTime generatedAt;
    private LocalDateTime reportStartDate;
    private LocalDateTime reportEndDate;

    // Compliance metrics
    private long totalActivities;
    private long complianceViolations;
    private long securityIncidents;
    private double complianceRate; // percentage

    // Activity breakdown
    private Map<Activity.ActivityType, Long> activitiesByType;
    private List<ActivityDto> criticalActivities; // High-risk activities
    private List<ActivityDto> violationActivities; // Activities that violate compliance rules

    // User activity summary
    private Map<UUID, Long> activitiesByUser;
    private List<UUID> usersWithSuspiciousActivity;

    // Security metrics
    private long failedLoginAttempts;
    private long unauthorizedAccessAttempts;
    private long dataAccessViolations;

    // Recommendations
    private List<String> complianceRecommendations;
    private List<String> securityRecommendations;
}