package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.dto.ActivityDto;
import com.mayo.hospitalintegration.dto.ActivityReportDto;
import com.mayo.hospitalintegration.entity.Activity;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.repository.ActivityRepository;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityAuditService {

    private final ActivityRepository activityRepository;
    private final HospitalRepository hospitalRepository;

    // Define compliance-critical activity types
    private static final List<Activity.ActivityType> COMPLIANCE_CRITICAL_TYPES = Arrays.asList(
        Activity.ActivityType.ACCESS_DENIED,
        Activity.ActivityType.ACCESS_REQUESTED,
        Activity.ActivityType.LOGIN_FAILED,
        Activity.ActivityType.DATA_TRANSFER_FAILED,
        Activity.ActivityType.ERROR_OCCURRED
    );

    private static final List<Activity.ActivityType> SECURITY_TYPES = Arrays.asList(
        Activity.ActivityType.LOGIN_FAILED,
        Activity.ActivityType.ACCESS_DENIED,
        Activity.ActivityType.ACCESS_REVOKED
    );

    @Transactional(readOnly = true)
    public ActivityReportDto generateComplianceReport(UUID hospitalId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Generating compliance report for hospital: {} from {} to {}", hospitalId, startDate, endDate);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return null;
        }

        Hospital hospital = hospitalOpt.get();

        // Get all activities in the period
        List<Activity> activities = activityRepository.findActivitiesByHospitalBetweenDates(hospital, startDate, endDate);

        // Calculate compliance metrics
        long totalActivities = activities.size();
        long complianceViolations = activities.stream()
                .mapToLong(activity -> COMPLIANCE_CRITICAL_TYPES.contains(activity.getActivityType()) ? 1 : 0)
                .sum();
        long securityIncidents = activities.stream()
                .mapToLong(activity -> SECURITY_TYPES.contains(activity.getActivityType()) ? 1 : 0)
                .sum();

        double complianceRate = totalActivities > 0 ? ((double) (totalActivities - complianceViolations) / totalActivities) * 100 : 100.0;

        // Activity breakdown
        Map<Activity.ActivityType, Long> activitiesByType = activities.stream()
                .collect(Collectors.groupingBy(Activity::getActivityType, Collectors.counting()));

        // Critical activities (high-risk)
        List<ActivityDto> criticalActivities = activities.stream()
                .filter(activity -> isCriticalActivity(activity))
                .sorted(Comparator.comparing(Activity::getTimestamp).reversed())
                .limit(50)
                .map(this::mapToDto)
                .collect(Collectors.toList());

        // Violation activities
        List<ActivityDto> violationActivities = activities.stream()
                .filter(activity -> COMPLIANCE_CRITICAL_TYPES.contains(activity.getActivityType()))
                .sorted(Comparator.comparing(Activity::getTimestamp).reversed())
                .limit(50)
                .map(this::mapToDto)
                .collect(Collectors.toList());

        // User activity summary
        Map<UUID, Long> activitiesByUser = activities.stream()
                .filter(activity -> activity.getUserId() != null)
                .collect(Collectors.groupingBy(Activity::getUserId, Collectors.counting()));

        // Users with suspicious activity (high failure rates, unusual patterns)
        List<UUID> suspiciousUsers = identifySuspiciousUsers(activities);

        // Security metrics
        long failedLoginAttempts = activities.stream()
                .mapToLong(activity -> activity.getActivityType() == Activity.ActivityType.LOGIN_FAILED ? 1 : 0)
                .sum();
        long unauthorizedAccessAttempts = activities.stream()
                .mapToLong(activity -> activity.getActivityType() == Activity.ActivityType.ACCESS_DENIED ? 1 : 0)
                .sum();
        long dataAccessViolations = activities.stream()
                .mapToLong(activity -> {
                    switch (activity.getActivityType()) {
                        case ACCESS_DENIED:
                        case PRESCRIPTION_ACCESSED: // Could be unauthorized
                        case DIAGNOSIS_ACCESSED:    // Could be unauthorized
                            return 1;
                        default:
                            return 0;
                    }
                })
                .sum();

        // Generate recommendations
        List<String> complianceRecommendations = generateComplianceRecommendations(complianceRate, complianceViolations);
        List<String> securityRecommendations = generateSecurityRecommendations(securityIncidents, failedLoginAttempts);

        ActivityReportDto report = new ActivityReportDto();
        report.setReportType("COMPLIANCE");
        report.setHospitalId(hospitalId);
        report.setHospitalName(hospital.getName());
        report.setGeneratedAt(LocalDateTime.now());
        report.setReportStartDate(startDate);
        report.setReportEndDate(endDate);
        report.setTotalActivities(totalActivities);
        report.setComplianceViolations(complianceViolations);
        report.setSecurityIncidents(securityIncidents);
        report.setComplianceRate(complianceRate);
        report.setActivitiesByType(activitiesByType);
        report.setCriticalActivities(criticalActivities);
        report.setViolationActivities(violationActivities);
        report.setActivitiesByUser(activitiesByUser);
        report.setUsersWithSuspiciousActivity(suspiciousUsers);
        report.setFailedLoginAttempts(failedLoginAttempts);
        report.setUnauthorizedAccessAttempts(unauthorizedAccessAttempts);
        report.setDataAccessViolations(dataAccessViolations);
        report.setComplianceRecommendations(complianceRecommendations);
        report.setSecurityRecommendations(securityRecommendations);

        return report;
    }

    @Transactional(readOnly = true)
    public ActivityReportDto generateAuditReport(UUID hospitalId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Generating audit report for hospital: {} from {} to {}", hospitalId, startDate, endDate);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return null;
        }

        Hospital hospital = hospitalOpt.get();

        // Get all activities in the period
        List<Activity> activities = activityRepository.findActivitiesByHospitalBetweenDates(hospital, startDate, endDate);

        // Similar logic to compliance report but focused on audit trail
        Map<Activity.ActivityType, Long> activitiesByType = activities.stream()
                .collect(Collectors.groupingBy(Activity::getActivityType, Collectors.counting()));

        List<ActivityDto> criticalActivities = activities.stream()
                .filter(this::isCriticalActivity)
                .sorted(Comparator.comparing(Activity::getTimestamp).reversed())
                .limit(100)
                .map(this::mapToDto)
                .collect(Collectors.toList());

        ActivityReportDto report = new ActivityReportDto();
        report.setReportType("AUDIT");
        report.setHospitalId(hospitalId);
        report.setHospitalName(hospital.getName());
        report.setGeneratedAt(LocalDateTime.now());
        report.setReportStartDate(startDate);
        report.setReportEndDate(endDate);
        report.setTotalActivities(activities.size());
        report.setActivitiesByType(activitiesByType);
        report.setCriticalActivities(criticalActivities);

        return report;
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> getComplianceViolations(UUID hospitalId, LocalDateTime since) {
        log.debug("Getting compliance violations for hospital: {} since {}", hospitalId, since);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return List.of();
        }

        List<Activity> violations = activityRepository.findActivitiesByHospitalAndTypesSince(
            hospitalOpt.get(), COMPLIANCE_CRITICAL_TYPES, since);

        return violations.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UUID> getUsersWithSecurityIncidents(UUID hospitalId, LocalDateTime since) {
        log.debug("Getting users with security incidents for hospital: {} since {}", hospitalId, since);

        return activityRepository.findUsersWithActivitiesSince(SECURITY_TYPES, since);
    }

    private boolean isCriticalActivity(Activity activity) {
        return COMPLIANCE_CRITICAL_TYPES.contains(activity.getActivityType()) ||
               activity.getActivityType() == Activity.ActivityType.SYSTEM_MAINTENANCE ||
               activity.getActivityType() == Activity.ActivityType.CONFIGURATION_CHANGE;
    }

    private List<UUID> identifySuspiciousUsers(List<Activity> activities) {
        // Simple heuristic: users with high failure rates
        Map<UUID, List<Activity>> activitiesByUser = activities.stream()
                .filter(activity -> activity.getUserId() != null)
                .collect(Collectors.groupingBy(Activity::getUserId));

        return activitiesByUser.entrySet().stream()
                .filter(entry -> {
                    List<Activity> userActivities = entry.getValue();
                    long failures = userActivities.stream()
                            .mapToLong(activity -> SECURITY_TYPES.contains(activity.getActivityType()) ? 1 : 0)
                            .sum();
                    return userActivities.size() > 10 && (double) failures / userActivities.size() > 0.3; // 30% failure rate
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> generateComplianceRecommendations(double complianceRate, long violations) {
        List<String> recommendations = new ArrayList<>();

        if (complianceRate < 80.0) {
            recommendations.add("Implement additional access controls and monitoring");
        }
        if (violations > 100) {
            recommendations.add("Review user access permissions and training");
        }
        if (complianceRate < 95.0) {
            recommendations.add("Enhance audit logging and monitoring systems");
        }

        return recommendations;
    }

    private List<String> generateSecurityRecommendations(long securityIncidents, long failedLogins) {
        List<String> recommendations = new ArrayList<>();

        if (failedLogins > 50) {
            recommendations.add("Implement multi-factor authentication");
        }
        if (securityIncidents > 20) {
            recommendations.add("Review and strengthen access control policies");
        }
        if (failedLogins > 10) {
            recommendations.add("Monitor for brute force attacks and implement rate limiting");
        }

        return recommendations;
    }

    private ActivityDto mapToDto(Activity activity) {
        ActivityDto dto = new ActivityDto();
        dto.setId(activity.getId());
        dto.setHospitalId(activity.getHospital().getId());
        dto.setHospitalName(activity.getHospital().getName());
        if (activity.getDevice() != null) {
            dto.setDeviceId(activity.getDevice().getId());
            dto.setDeviceName(activity.getDevice().getDeviceName());
        }
        dto.setActivityType(activity.getActivityType());
        dto.setActivityDescription(activity.getActivityDescription());
        dto.setUserId(activity.getUserId());
        dto.setPatientId(activity.getPatientId());
        dto.setRecordId(activity.getRecordId());
        dto.setRecordType(activity.getRecordType());
        dto.setIpAddress(activity.getIpAddress());
        dto.setUserAgent(activity.getUserAgent());
        dto.setMetadata(activity.getMetadata());
        dto.setTimestamp(activity.getTimestamp());
        dto.setCreatedAt(activity.getCreatedAt());
        return dto;
    }
}