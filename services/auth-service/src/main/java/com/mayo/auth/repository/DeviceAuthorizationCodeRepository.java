package com.mayo.auth.repository;

import com.mayo.auth.entity.DeviceAuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for DeviceAuthorizationCode entity
 */
@Repository
public interface DeviceAuthorizationCodeRepository extends JpaRepository<DeviceAuthorizationCode, UUID> {

    /**
     * Find by device code
     */
    Optional<DeviceAuthorizationCode> findByDeviceCode(String deviceCode);

    /**
     * Find by user code
     */
    Optional<DeviceAuthorizationCode> findByUserCode(String userCode);

    /**
     * Find expired codes
     */
    @Query("SELECT c FROM DeviceAuthorizationCode c WHERE c.expiresAt <= :now AND c.status = 'PENDING'")
    List<DeviceAuthorizationCode> findExpiredCodes(@Param("now") LocalDateTime now);

    /**
     * Check if device code exists
     */
    boolean existsByDeviceCode(String deviceCode);

    /**
     * Check if user code exists
     */
    boolean existsByUserCode(String userCode);
}