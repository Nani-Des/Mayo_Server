package com.mayo.hospitalintegration.dto;

import com.mayo.hospitalintegration.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityAnalyticsDto {

    private UUID hospitalId;
    private String hospitalName;
    private LocalDate date;
    private long totalActivities;
    private Map<Activity.ActivityType, Long> activitiesByType;
    private Map<UUID, Long> activitiesByUser;
    private Map<String, Long> activitiesByDate; // Date string -> count
    private double averageActivitiesPerDay;
    private Activity.ActivityType mostCommonActivityType;
    private long uniqueUsersCount;
    private long uniquePatientsCount;
    private long failedActivitiesCount;
    private long successfulActivitiesCount;
}