package com.mayo.auth.repository;

import com.mayo.auth.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Family entity
 */
@Repository
public interface FamilyRepository extends JpaRepository<Family, UUID> {

    /**
     * Find families created by a specific user
     */
    List<Family> findByCreatedBy(UUID createdBy);

    /**
     * Find active families created by a specific user
     */
    @Query("SELECT f FROM Family f WHERE f.createdBy = :createdBy AND f.isActive = true")
    List<Family> findActiveByCreatedBy(@Param("createdBy") UUID createdBy);

    /**
     * Find family by name and creator
     */
    Optional<Family> findByNameAndCreatedBy(String name, UUID createdBy);

    /**
     * Check if family name exists for a user
     */
    boolean existsByNameAndCreatedBy(String name, UUID createdBy);
}