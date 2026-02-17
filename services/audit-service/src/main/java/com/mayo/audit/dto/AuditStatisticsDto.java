package com.mayo.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for audit statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditStatisticsDto {

    private long totalEvents;
    private long eventsLast24Hours;
    private long eventsLast7Days;
    private long eventsLast30Days;

    private Map<String, Long> eventsByAction;
    private Map<String, Long> eventsBySeverity;
    private Map<String, Long> eventsByResourceType;

    private long uniqueUsers;
    private long uniquePatients;
    private long uniqueDevices;

    private double averageEventsPerHour;
    private double averageEventsPerDay;

    private long complianceViolations;
    private Map<String, Long> violationsBySeverity;
}