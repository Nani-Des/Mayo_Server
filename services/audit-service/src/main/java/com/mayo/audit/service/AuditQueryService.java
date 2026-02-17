package com.mayo.audit.service;

import com.mayo.audit.dto.AuditEventDto;
import com.mayo.audit.dto.AuditStatisticsDto;
import com.mayo.audit.dto.ComplianceViolationDto;
import com.mayo.audit.entity.AuditEvent;
import com.mayo.audit.entity.ElasticsearchAuditEvent;
import com.mayo.audit.repository.AuditEventRepository;
import com.mayo.audit.repository.elasticsearch.ElasticsearchAuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;

/**
 * Service for querying audit events and generating statistics
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditEventRepository auditEventRepository;
    private final ElasticsearchAuditEventRepository elasticsearchAuditEventRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * Get audit events with filters
     */
    public Page<AuditEventDto> getAuditEventsWithFilters(UUID userId, UUID patientId, String action,
            String resourceType, LocalDateTime startDate,
            LocalDateTime endDate, Pageable pageable) {

        AuditEvent.AuditAction auditAction = null;
        if (action != null) {
            try {
                auditAction = AuditEvent.AuditAction.valueOf(action.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid action filter: {}", action);
            }
        }

        Page<AuditEvent> events = auditEventRepository.findAuditEventsWithFilters(
                userId, patientId, auditAction, resourceType, startDate, endDate, pageable);

        return events.map(this::convertToDto);
    }

    /**
     * Get audit event by ID
     */
    public Optional<AuditEventDto> getAuditEventById(String eventId) {
        return auditEventRepository.findByEventId(eventId)
                .map(this::convertToDto);
    }

    /**
     * Search audit events using Elasticsearch
     */
    public Page<AuditEventDto> searchAuditEvents(UUID userId, UUID patientId, String action,
            String resourceType, String severity,
            LocalDateTime startDate, LocalDateTime endDate,
            String query, Pageable pageable) {
        log.info(
                "Elasticsearch search called with filters - userId: {}, patientId: {}, action: {}, resourceType: {}, severity: {}, startDate: {}, endDate: {}, query: {}",
                userId, patientId, action, resourceType, severity, startDate, endDate, query);

        try {
            Criteria criteria = new Criteria();

            // Add filters based on provided parameters
            if (userId != null) {
                criteria.and("userId").is(userId);
            }

            if (patientId != null) {
                criteria.and("patientId").is(patientId);
            }

            if (action != null && !action.trim().isEmpty()) {
                criteria.and("action").is(action.toUpperCase());
            }

            if (resourceType != null && !resourceType.trim().isEmpty()) {
                criteria.and("resourceType").is(resourceType);
            }

            if (severity != null && !severity.trim().isEmpty()) {
                criteria.and("severity").is(severity.toUpperCase());
            }

            // Date range filtering
            if (startDate != null || endDate != null) {
                Criteria dateCriteria = new Criteria("timestamp");
                if (startDate != null && endDate != null) {
                    dateCriteria.between(startDate, endDate);
                } else if (startDate != null) {
                    dateCriteria.greaterThanEqual(startDate);
                } else if (endDate != null) {
                    dateCriteria.lessThanEqual(endDate);
                }
                criteria.and(dateCriteria);
            }

            // Free-text search across multiple fields
            if (query != null && !query.trim().isEmpty()) {
                String searchQuery = query.trim();
                Criteria textCriteria = new Criteria()
                        .or("eventId").contains(searchQuery)
                        .or("resourceType").contains(searchQuery)
                        .or("action").contains(searchQuery)
                        .or("userAgent").contains(searchQuery)
                        .or("ipAddress").contains(searchQuery)
                        .or("severity").contains(searchQuery);
                criteria.and(textCriteria);
            }

            // Build the query
            Query searchQuery = new CriteriaQuery(criteria).setPageable(pageable);

            // Execute search
            var searchHits = elasticsearchOperations.search(searchQuery, ElasticsearchAuditEvent.class);
            var content = searchHits.getSearchHits().stream()
                    .map(hit -> convertElasticsearchToDto(hit.getContent()))
                    .collect(Collectors.toList());

            // Get total count for pagination
            long totalElements = searchHits.getTotalHits();
            int totalPages = pageable.getPageSize() > 0
                    ? (int) Math.ceil((double) totalElements / pageable.getPageSize())
                    : 0;

            return new PageImpl<>(content, pageable, totalElements);

        } catch (Exception e) {
            log.error("Error performing Elasticsearch search", e);
            // Fallback to empty results
            return Page.empty(pageable);
        }
    }

    /**
     * Get audit events for a specific user
     */
    public Page<AuditEventDto> getUserAuditEvents(UUID userId, Pageable pageable) {
        Page<AuditEvent> events = auditEventRepository.findByUserId(userId, pageable);
        return events.map(this::convertToDto);
    }

    /**
     * Get audit events for a specific patient
     */
    public Page<AuditEventDto> getPatientAuditEvents(UUID patientId, Pageable pageable) {
        Page<AuditEvent> events = auditEventRepository.findByPatientId(patientId, pageable);
        return events.map(this::convertToDto);
    }

    /**
     * Get audit statistics
     */
    public AuditStatisticsDto getAuditStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = startDate != null ? startDate : now.minusDays(30);
        LocalDateTime end = endDate != null ? endDate : now;

        // Get total events in the period
        long totalEvents = auditEventRepository.findByTimestampBetween(start, end, Pageable.unpaged())
                .getTotalElements();

        // Get events in different time ranges
        LocalDateTime last24Hours = now.minusHours(24);
        LocalDateTime last7Days = now.minusDays(7);
        LocalDateTime last30Days = now.minusDays(30);

        long eventsLast24Hours = auditEventRepository.countEventsSince(last24Hours);
        long eventsLast7Days = auditEventRepository.countEventsSince(last7Days);
        long eventsLast30Days = auditEventRepository.countEventsSince(last30Days);

        // Get events by action
        Map<String, Long> eventsByAction = Arrays.stream(AuditEvent.AuditAction.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        action -> auditEventRepository.countActionsSince(action, start)));

        // Get events by severity
        Map<String, Long> eventsBySeverity = Arrays.stream(AuditEvent.Severity.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        severity -> auditEventRepository.countBySeverity(severity, start)));

        // Get events by resource type
        Map<String, Long> eventsByResourceType = auditEventRepository.countByResourceTypeSince(start).stream()
                .collect(Collectors.toMap(arr -> (String) arr[0], arr -> (Long) arr[1]));

        // Get unique counts
        List<UUID> activeUsers = auditEventRepository.findActiveUsersSince(start);
        long uniqueUsers = activeUsers.size();

        // Calculate averages
        double hoursDiff = java.time.Duration.between(start, end).toHours();
        double averageEventsPerHour = hoursDiff > 0 ? totalEvents / hoursDiff : 0;

        double daysDiff = java.time.Duration.between(start, end).toDays();
        double averageEventsPerDay = daysDiff > 0 ? totalEvents / daysDiff : 0;

        return AuditStatisticsDto.builder()
                .totalEvents(totalEvents)
                .eventsLast24Hours(eventsLast24Hours)
                .eventsLast7Days(eventsLast7Days)
                .eventsLast30Days(eventsLast30Days)
                .eventsByAction(eventsByAction)
                .eventsBySeverity(eventsBySeverity)
                .eventsByResourceType(eventsByResourceType)
                .uniqueUsers(uniqueUsers)
                .uniquePatients(auditEventRepository.countDistinctPatientsSince(start))
                .uniqueDevices(auditEventRepository.countDistinctDevicesSince(start))
                .averageEventsPerHour(averageEventsPerHour)
                .averageEventsPerDay(averageEventsPerDay)
                .complianceViolations(auditEventRepository.countViolationsSince(start))
                .violationsBySeverity(new HashMap<>())
                .build();
    }

    /**
     * Convert entity to DTO
     */
    private AuditEventDto convertToDto(AuditEvent event) {
        return AuditEventDto.builder()
                .id(event.getId())
                .eventId(event.getEventId())
                .timestamp(event.getTimestamp())
                .userId(event.getUserId())
                .deviceId(event.getDeviceId())
                .hospitalId(event.getHospitalId())
                .sessionId(event.getSessionId())
                .action(event.getAction() != null ? event.getAction().name() : null)
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId() != null ? event.getResourceId().toString() : null)
                .patientId(event.getPatientId())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .location(event.getLocation() != null ? event.getLocation().toString() : null)
                .metadata(event.getMetadata() != null ? event.getMetadata().toString() : null)
                .severity(event.getSeverity() != null ? event.getSeverity().name() : null)
                .complianceFlags(event.getComplianceFlags() != null ? event.getComplianceFlags().toString() : null)
                .hashValue(event.getHashValue())

                // Versioning fields
                .originatingDeviceId(event.getOriginatingDeviceId())
                .doctorUserId(event.getDoctorUserId())
                .versionNumber(event.getVersionNumber())
                .parentVersionHash(event.getParentVersionHash())
                .versioningDigitalSignature(event.getVersioningDigitalSignature())
                .conflictResolutionMetadata(event.getConflictResolutionMetadata())

                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    /**
     * Get compliance violations with pagination
     */
    public Page<ComplianceViolationDto> getComplianceViolations(Boolean resolved, Pageable pageable) {
        // This would need a repository method - for now return empty page
        // In a real implementation, you'd inject ComplianceViolationRepository
        return Page.empty(pageable);
    }

    /**
     * Get compliance violations by date range
     */
    public List<ComplianceViolationDto> getComplianceViolationsByDateRange(LocalDateTime startDate,
            LocalDateTime endDate) {
        // This would need a repository method - for now return empty list
        return List.of();
    }

    /**
     * Get compliance summary statistics
     */
    public Map<String, Object> getComplianceSummary(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime start = startDate != null ? startDate : LocalDateTime.now().minusDays(30);
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();

        // Get basic audit statistics
        var auditStats = getAuditStatistics(start, end);

        // Calculate compliance metrics
        long totalEvents = auditStats.getTotalEvents();
        long violations = auditStats.getComplianceViolations();
        double complianceScore = totalEvents > 0 ? (1.0 - (double) violations / totalEvents) * 100.0 : 100.0;

        return Map.of(
                "period", Map.of("start", start, "end", end),
                "totalEvents", totalEvents,
                "totalViolations", violations,
                "complianceScore", Math.max(0.0, complianceScore),
                "eventsBySeverity", auditStats.getEventsBySeverity(),
                "eventsByAction", auditStats.getEventsByAction(),
                "unresolvedViolations", violations, // Placeholder
                "criticalViolations", 0, // Placeholder
                "highPriorityViolations", 0 // Placeholder
        );
    }

    /**
     * Convert Elasticsearch document to DTO
     */
    private AuditEventDto convertElasticsearchToDto(ElasticsearchAuditEvent event) {
        return AuditEventDto.builder()
                .id(UUID.fromString(event.getId()))
                .eventId(event.getEventId())
                .timestamp(event.getTimestamp())
                .userId(event.getUserId())
                .deviceId(event.getDeviceId())
                .hospitalId(event.getHospitalId())
                .sessionId(event.getSessionId())
                .action(event.getAction())
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId() != null ? event.getResourceId().toString() : null)
                .patientId(event.getPatientId())
                .ipAddress(parseIpAddress(event.getIpAddress()))
                .userAgent(event.getUserAgent())
                .location(event.getLocation() != null ? event.getLocation().toString() : null)
                .metadata(event.getMetadata() != null ? event.getMetadata().toString() : null)
                .severity(event.getSeverity())
                .complianceFlags(event.getComplianceFlags() != null ? event.getComplianceFlags().toString() : null)
                .hashValue(event.getHashValue())

                // Versioning fields
                .originatingDeviceId(event.getOriginatingDeviceId())
                .doctorUserId(event.getDoctorUserId())
                .versionNumber(event.getVersionNumber())
                .parentVersionHash(event.getParentVersionHash())
                .versioningDigitalSignature(event.getVersioningDigitalSignature())
                .conflictResolutionMetadata(event.getConflictResolutionMetadata() != null ? event.getConflictResolutionMetadata().toString() : null)

                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    private java.net.InetAddress parseIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        try {
            return java.net.InetAddress.getByName(ipAddress);
        } catch (java.net.UnknownHostException e) {
            log.warn("Could not parse IP address from Elasticsearch event: {}", ipAddress);
            return null;
        }
    }
}