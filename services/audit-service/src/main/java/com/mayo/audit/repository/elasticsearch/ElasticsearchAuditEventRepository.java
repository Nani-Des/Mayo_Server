package com.mayo.audit.repository.elasticsearch;

import com.mayo.audit.entity.ElasticsearchAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Elasticsearch repository for audit events
 */
@Repository
public interface ElasticsearchAuditEventRepository extends ElasticsearchRepository<ElasticsearchAuditEvent, String> {

    // Basic search methods
    Page<ElasticsearchAuditEvent> findByUserId(UUID userId, Pageable pageable);

    Page<ElasticsearchAuditEvent> findByPatientId(UUID patientId, Pageable pageable);

    Page<ElasticsearchAuditEvent> findByAction(String action, Pageable pageable);

    Page<ElasticsearchAuditEvent> findByResourceType(String resourceType, Pageable pageable);

    Page<ElasticsearchAuditEvent> findBySeverity(String severity, Pageable pageable);

    Page<ElasticsearchAuditEvent> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Combined filters
    Page<ElasticsearchAuditEvent> findByUserIdAndTimestampBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<ElasticsearchAuditEvent> findByPatientIdAndTimestampBetween(UUID patientId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<ElasticsearchAuditEvent> findByActionAndTimestampBetween(String action, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<ElasticsearchAuditEvent> findByResourceTypeAndTimestampBetween(String resourceType, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<ElasticsearchAuditEvent> findBySeverityAndTimestampBetween(String severity, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}