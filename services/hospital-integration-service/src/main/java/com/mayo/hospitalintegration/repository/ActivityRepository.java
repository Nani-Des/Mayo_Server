package com.mayo.hospitalintegration.repository;

import com.mayo.hospitalintegration.entity.Activity;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Page<Activity> findByHospitalOrderByTimestampDesc(Hospital hospital, Pageable pageable);

    Page<Activity> findByDeviceOrderByTimestampDesc(HospitalDevice device, Pageable pageable);

    List<Activity> findByHospitalAndActivityType(Hospital hospital, Activity.ActivityType activityType);

    List<Activity> findByDeviceAndActivityType(HospitalDevice device, Activity.ActivityType activityType);

    @Query("SELECT a FROM Activity a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<Activity> findActivitiesBetweenDates(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT a FROM Activity a WHERE a.hospital = :hospital AND a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<Activity> findActivitiesByHospitalBetweenDates(@Param("hospital") Hospital hospital, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT a FROM Activity a WHERE a.userId = :userId ORDER BY a.timestamp DESC")
    List<Activity> findActivitiesByUser(@Param("userId") UUID userId);

    @Query("SELECT a FROM Activity a WHERE a.patientId = :patientId ORDER BY a.timestamp DESC")
    List<Activity> findActivitiesByPatient(@Param("patientId") UUID patientId);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.hospital = :hospital AND a.activityType = :activityType AND a.timestamp >= :since")
    long countActivitiesByHospitalAndTypeSince(@Param("hospital") Hospital hospital, @Param("activityType") Activity.ActivityType activityType, @Param("since") LocalDateTime since);

    @Query("SELECT a FROM Activity a WHERE a.timestamp < :before ORDER BY a.timestamp DESC")
    List<Activity> findActivitiesBeforeDate(@Param("before") LocalDateTime before);

    // Enhanced query methods for advanced filtering
    @Query("SELECT a FROM Activity a WHERE " +
           "(:hospital IS NULL OR a.hospital = :hospital) AND " +
           "(:device IS NULL OR a.device = :device) AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:patientId IS NULL OR a.patientId = :patientId) AND " +
           "(:activityType IS NULL OR a.activityType = :activityType) AND " +
           "(:recordType IS NULL OR a.recordType = :recordType) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<Activity> findActivitiesWithFilters(@Param("hospital") Hospital hospital,
                                           @Param("device") HospitalDevice device,
                                           @Param("userId") UUID userId,
                                           @Param("patientId") UUID patientId,
                                           @Param("activityType") Activity.ActivityType activityType,
                                           @Param("recordType") Activity.RecordType recordType,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate,
                                           Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.hospital = :hospital AND " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:activityType IS NULL OR a.activityType = :activityType) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<Activity> findActivitiesByHospitalWithFilters(@Param("hospital") Hospital hospital,
                                                     @Param("userId") UUID userId,
                                                     @Param("activityType") Activity.ActivityType activityType,
                                                     @Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate,
                                                     Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.userId = :userId AND " +
           "(:hospital IS NULL OR a.hospital = :hospital) AND " +
           "(:activityType IS NULL OR a.activityType = :activityType) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<Activity> findActivitiesByUserWithFilters(@Param("userId") UUID userId,
                                                 @Param("hospital") Hospital hospital,
                                                 @Param("activityType") Activity.ActivityType activityType,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate,
                                                 Pageable pageable);

    // Analytics queries
    @Query("SELECT COUNT(a) FROM Activity a WHERE a.hospital = :hospital AND a.timestamp >= :since")
    long countActivitiesByHospitalSince(@Param("hospital") Hospital hospital, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.activityType = :activityType AND a.timestamp >= :since")
    long countActivitiesByTypeSince(@Param("activityType") Activity.ActivityType activityType, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.userId = :userId AND a.timestamp >= :since")
    long countActivitiesByUserSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    @Query("SELECT a.activityType, COUNT(a) FROM Activity a WHERE a.hospital = :hospital AND a.timestamp >= :since GROUP BY a.activityType")
    List<Object[]> countActivitiesByTypeForHospital(@Param("hospital") Hospital hospital, @Param("since") LocalDateTime since);

    @Query("SELECT DATE(a.timestamp), COUNT(a) FROM Activity a WHERE a.hospital = :hospital AND a.timestamp >= :since GROUP BY DATE(a.timestamp) ORDER BY DATE(a.timestamp)")
    List<Object[]> countActivitiesByDateForHospital(@Param("hospital") Hospital hospital, @Param("since") LocalDateTime since);

    @Query("SELECT a.userId, COUNT(a) FROM Activity a WHERE a.hospital = :hospital AND a.timestamp >= :since AND a.userId IS NOT NULL GROUP BY a.userId ORDER BY COUNT(a) DESC")
    List<Object[]> countActivitiesByUserForHospital(@Param("hospital") Hospital hospital, @Param("since") LocalDateTime since);

    // Compliance monitoring queries
    @Query("SELECT a FROM Activity a WHERE a.activityType IN :activityTypes AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<Activity> findActivitiesByTypesSince(@Param("activityTypes") List<Activity.ActivityType> activityTypes, @Param("since") LocalDateTime since);

    @Query("SELECT a FROM Activity a WHERE a.hospital = :hospital AND a.activityType IN :activityTypes AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<Activity> findActivitiesByHospitalAndTypesSince(@Param("hospital") Hospital hospital, @Param("activityTypes") List<Activity.ActivityType> activityTypes, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.activityType = :activityType AND a.timestamp BETWEEN :startDate AND :endDate")
    long countActivitiesByTypeInDateRange(@Param("activityType") Activity.ActivityType activityType, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DISTINCT a.userId FROM Activity a WHERE a.activityType IN :activityTypes AND a.timestamp >= :since")
    List<UUID> findUsersWithActivitiesSince(@Param("activityTypes") List<Activity.ActivityType> activityTypes, @Param("since") LocalDateTime since);
}