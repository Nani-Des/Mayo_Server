package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.dto.ActivityAnalyticsDto;
import com.mayo.hospitalintegration.dto.ActivityStatisticsDto;
import com.mayo.hospitalintegration.entity.Activity;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.repository.ActivityRepository;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityAnalyticsService {

    private final ActivityRepository activityRepository;
    private final HospitalRepository hospitalRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "activityAnalytics", key = "#hospitalId + '_' + #days")
    public ActivityAnalyticsDto getActivityAnalytics(UUID hospitalId, int days) {
        log.debug("Generating activity analytics for hospital: {} over {} days", hospitalId, days);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return null;
        }

        Hospital hospital = hospitalOpt.get();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        // Get activity counts by type
        List<Object[]> typeCounts = activityRepository.countActivitiesByTypeForHospital(hospital, since);
        Map<Activity.ActivityType, Long> activitiesByType = typeCounts.stream()
                .collect(Collectors.toMap(
                    row -> (Activity.ActivityType) row[0],
                    row -> (Long) row[1]
                ));

        // Get activity counts by date
        List<Object[]> dateCounts = activityRepository.countActivitiesByDateForHospital(hospital, since);
        Map<String, Long> activitiesByDate = dateCounts.stream()
                .collect(Collectors.toMap(
                    row -> row[0].toString(),
                    row -> (Long) row[1]
                ));

        // Get activity counts by user
        List<Object[]> userCounts = activityRepository.countActivitiesByUserForHospital(hospital, since);
        Map<UUID, Long> activitiesByUser = userCounts.stream()
                .collect(Collectors.toMap(
                    row -> (UUID) row[0],
                    row -> (Long) row[1]
                ));

        // Calculate metrics
        long totalActivities = activitiesByType.values().stream().mapToLong(Long::longValue).sum();
        long uniqueUsers = activitiesByUser.size();
        double averageActivitiesPerDay = days > 0 ? (double) totalActivities / days : 0;

        // Find most common activity type
        Activity.ActivityType mostCommonType = activitiesByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // Calculate success/failure metrics (simplified - based on activity types)
        long failedActivities = activitiesByType.getOrDefault(Activity.ActivityType.DATA_TRANSFER_FAILED, 0L) +
                               activitiesByType.getOrDefault(Activity.ActivityType.LOGIN_FAILED, 0L);
        long successfulActivities = totalActivities - failedActivities;

        ActivityAnalyticsDto analytics = new ActivityAnalyticsDto();
        analytics.setHospitalId(hospitalId);
        analytics.setHospitalName(hospital.getName());
        analytics.setDate(LocalDate.now());
        analytics.setTotalActivities(totalActivities);
        analytics.setActivitiesByType(activitiesByType);
        analytics.setActivitiesByUser(activitiesByUser);
        analytics.setActivitiesByDate(activitiesByDate);
        analytics.setAverageActivitiesPerDay(averageActivitiesPerDay);
        analytics.setMostCommonActivityType(mostCommonType);
        analytics.setUniqueUsersCount(uniqueUsers);
        analytics.setUniquePatientsCount(0L); // Would need additional query
        analytics.setFailedActivitiesCount(failedActivities);
        analytics.setSuccessfulActivitiesCount(successfulActivities);

        return analytics;
    }

    @Transactional(readOnly = true)
    public ActivityStatisticsDto getActivityStatistics(UUID hospitalId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Generating activity statistics for hospital: {} from {} to {}", hospitalId, startDate, endDate);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return null;
        }

        Hospital hospital = hospitalOpt.get();

        // Get all activities in the period
        List<Activity> activities = activityRepository.findActivitiesByHospitalBetweenDates(hospital, startDate, endDate);

        // Calculate basic metrics
        long totalActivities = activities.size();
        long uniqueUsers = activities.stream()
                .map(Activity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long uniquePatients = activities.stream()
                .map(Activity::getPatientId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // Activities by type
        Map<Activity.ActivityType, Long> activitiesByType = activities.stream()
                .collect(Collectors.groupingBy(Activity::getActivityType, Collectors.counting()));

        // Time-based calculations
        long hoursBetween = ChronoUnit.HOURS.between(startDate, endDate);
        long daysBetween = ChronoUnit.DAYS.between(startDate.toLocalDate(), endDate.toLocalDate()) + 1;

        double averageActivitiesPerHour = hoursBetween > 0 ? (double) totalActivities / hoursBetween : 0;
        double averageActivitiesPerDay = daysBetween > 0 ? (double) totalActivities / daysBetween : 0;

        // Success/failure metrics
        long failedActivities = activities.stream()
                .mapToLong(activity -> {
                    switch (activity.getActivityType()) {
                        case DATA_TRANSFER_FAILED:
                        case LOGIN_FAILED:
                        case ACCESS_DENIED:
                            return 1;
                        default:
                            return 0;
                    }
                })
                .sum();
        long successfulActivities = totalActivities - failedActivities;
        double successRate = totalActivities > 0 ? (double) successfulActivities / totalActivities * 100 : 0;

        // Top users
        Map<UUID, Long> userActivityCounts = activities.stream()
                .filter(activity -> activity.getUserId() != null)
                .collect(Collectors.groupingBy(Activity::getUserId, Collectors.counting()));

        Map.Entry<UUID, Long> topUser = userActivityCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        ActivityStatisticsDto statistics = new ActivityStatisticsDto();
        statistics.setHospitalId(hospitalId);
        statistics.setHospitalName(hospital.getName());
        statistics.setPeriodStart(startDate);
        statistics.setPeriodEnd(endDate);
        statistics.setTotalActivities(totalActivities);
        statistics.setUniqueUsers(uniqueUsers);
        statistics.setUniquePatients(uniquePatients);
        statistics.setUniqueDevices(0L); // Would need device tracking
        statistics.setActivitiesByType(activitiesByType);
        statistics.setAverageActivitiesPerHour(averageActivitiesPerHour);
        statistics.setAverageActivitiesPerDay(averageActivitiesPerDay);
        statistics.setPeakHourActivities(0L); // Would need hourly aggregation
        statistics.setSuccessfulActivities(successfulActivities);
        statistics.setFailedActivities(failedActivities);
        statistics.setSuccessRate(successRate);
        statistics.setMostActiveUser(topUser != null ? topUser.getKey() : null);
        statistics.setMostActiveUserActivities(topUser != null ? topUser.getValue() : 0L);
        statistics.setTopUsersByActivity(userActivityCounts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        statistics.setActivityGrowthRate(0.0); // Would need comparison with previous period
        statistics.setIncreasingTrend(false);

        return statistics;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getActivityTrends(UUID hospitalId, int days) {
        log.debug("Getting activity trends for hospital: {} over {} days", hospitalId, days);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return Map.of();
        }

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> dateCounts = activityRepository.countActivitiesByDateForHospital(hospitalOpt.get(), since);

        return dateCounts.stream()
                .collect(Collectors.toMap(
                    row -> row[0].toString(),
                    row -> (Long) row[1]
                ));
    }
}