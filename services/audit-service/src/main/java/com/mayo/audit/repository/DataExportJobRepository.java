package com.mayo.audit.repository;

import com.mayo.audit.entity.DataExportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for DataExportJob entity
 */
@Repository
public interface DataExportJobRepository extends JpaRepository<DataExportJob, UUID> {

    List<DataExportJob> findByStatus(String status);

    List<DataExportJob> findByJobType(String jobType);

    List<DataExportJob> findByRequestedBy(UUID requestedBy);

    @Query("SELECT j FROM DataExportJob j WHERE j.expiresAt > :now ORDER BY j.createdAt DESC")
    Page<DataExportJob> findActiveJobs(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT j FROM DataExportJob j WHERE j.status = 'PENDING' AND j.createdAt < :cutoff")
    List<DataExportJob> findStalePendingJobs(@Param("cutoff") LocalDateTime cutoff);
}