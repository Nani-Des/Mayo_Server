package com.mayo.hospitalintegration.dto;

import com.mayo.hospitalintegration.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityStatisticsDto {

    private UUID hospitalId;
    private String hospitalName;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    // Basic counts
    private long totalActivities;
    private long uniqueUsers;
    private long uniquePatients;
    private long uniqueDevices;

    // Activity type breakdown
    private Map<Activity.ActivityType, Long> activitiesByType;

    // Time-based metrics
    private double averageActivitiesPerHour;
    private double averageActivitiesPerDay;
    private long peakHourActivities;
    private LocalDateTime peakHour;

    // Success/failure metrics
    private long successfulActivities;
    private long failedActivities;
    private double successRate;

    // User activity metrics
    private UUID mostActiveUser;
    private long mostActiveUserActivities;
    private Map<UUID, Long> topUsersByActivity;

    // Trend indicators
    private double activityGrowthRate; // percentage change from previous period
    private boolean isIncreasingTrend;
}