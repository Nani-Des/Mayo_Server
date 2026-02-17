package com.mayo.notification.repository;

import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

        Page<Notification> findByUserId(UUID userId, Pageable pageable);

        Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

        List<Notification> findByUserIdAndStatus(UUID userId, NotificationStatus status);

        List<Notification> findByStatus(NotificationStatus status);

        Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

        Page<Notification> findByTypeOrderByCreatedAtDesc(String type, Pageable pageable);

        @Query("SELECT n FROM Notification n WHERE n.scheduledAt <= :now AND n.status = :status")
        List<Notification> findScheduledNotifications(@Param("now") Instant now,
                        @Param("status") NotificationStatus status);

        @Query("SELECT n FROM Notification n WHERE n.status IN :statuses AND n.createdAt < :cutoffDate")
        List<Notification> findOldNotifications(@Param("statuses") List<NotificationStatus> statuses,
                        @Param("cutoffDate") Instant cutoffDate);

        long countByStatus(NotificationStatus status);

        long countByUserIdAndStatus(UUID userId, NotificationStatus status);

        long countByType(String type);
}