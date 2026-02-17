package com.mayo.auth.repository;

import com.mayo.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by Ghana Card ID
     */
    Optional<User> findByGhanaCardId(String ghanaCardId);

    /**
     * Find user by email or Ghana Card ID
     */
    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.ghanaCardId = :identifier")
    Optional<User> findByEmailOrGhanaCardId(@Param("identifier") String identifier);

    /**
     * Check if Ghana Card ID exists
     */
    boolean existsByGhanaCardId(String ghanaCardId);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find all users belonging to a specific hospital
     */
    java.util.List<User> findByHospitalId(UUID hospitalId);

    /**
     * Find users by hospital and type (e.g. all DOCTORS in a hospital)
     */
    java.util.List<User> findByHospitalIdAndUserType(UUID hospitalId, com.mayo.common.core.enums.UserType userType);
}