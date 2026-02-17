package com.mayo.auth.repository;

import com.mayo.auth.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for FamilyMember entity
 */
@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    /**
     * Find all members of a family
     */
    List<FamilyMember> findByFamilyId(UUID familyId);

    /**
     * Find active members of a family
     */
    @Query("SELECT fm FROM FamilyMember fm WHERE fm.familyId = :familyId AND fm.status = 'ACTIVE'")
    List<FamilyMember> findActiveByFamilyId(@Param("familyId") UUID familyId);

    /**
     * Find family member by user ID
     */
    List<FamilyMember> findByUserId(UUID userId);

    /**
     * Find family member by family and user
     */
    Optional<FamilyMember> findByFamilyIdAndUserId(UUID familyId, UUID userId);

    /**
     * Find family member by patient ID
     */
    Optional<FamilyMember> findByPatientId(UUID patientId);

    /**
     * Check if user is a member of any family
     */
    boolean existsByUserId(UUID userId);

    /**
     * Check if user is a member of specific family
     */
    boolean existsByFamilyIdAndUserId(UUID familyId, UUID userId);

    /**
     * Count active members in a family
     */
    @Query("SELECT COUNT(fm) FROM FamilyMember fm WHERE fm.familyId = :familyId AND fm.status = 'ACTIVE'")
    long countActiveByFamilyId(@Param("familyId") UUID familyId);
}