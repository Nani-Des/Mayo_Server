package com.mayo.patientrecord.repository;

import com.mayo.patientrecord.entity.OwnershipTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for OwnershipTransfer entity
 */
@Repository
public interface OwnershipTransferRepository extends JpaRepository<OwnershipTransfer, UUID> {

    /**
     * Find all ownership transfers for a specific patient
     */
    List<OwnershipTransfer> findByPatientIdOrderByInitiatedAtDesc(UUID patientId);

    /**
     * Find pending transfers for a specific patient
     */
    List<OwnershipTransfer> findByPatientIdAndStatus(UUID patientId, OwnershipTransfer.TransferStatus status);

    /**
     * Find transfers initiated by a specific user
     */
    List<OwnershipTransfer> findByInitiatedByOrderByInitiatedAtDesc(UUID initiatedBy);

    /**
     * Find transfers where user is the new owner and status is pending
     */
    @Query("SELECT ot FROM OwnershipTransfer ot WHERE ot.newOwnerId = :userId AND ot.status = :status")
    List<OwnershipTransfer> findPendingTransfersForNewOwner(@Param("userId") UUID userId,
                                                           @Param("status") OwnershipTransfer.TransferStatus status);

    /**
     * Find the latest completed transfer for a patient
     */
    Optional<OwnershipTransfer> findFirstByPatientIdAndStatusOrderByCompletedAtDesc(UUID patientId,
                                                                                   OwnershipTransfer.TransferStatus status);
}