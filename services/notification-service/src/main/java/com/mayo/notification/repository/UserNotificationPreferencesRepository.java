package com.mayo.notification.repository;

import com.mayo.notification.entity.UserNotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationPreferencesRepository extends JpaRepository<UserNotificationPreferences, UUID> {

    List<UserNotificationPreferences> findByUserId(UUID userId);

    Optional<UserNotificationPreferences> findByUserIdAndNotificationType(UUID userId, String notificationType);

    @Query("SELECT p FROM UserNotificationPreferences p WHERE p.userId = :userId AND p.notificationType IN :types")
    List<UserNotificationPreferences> findByUserIdAndTypes(@Param("userId") UUID userId, @Param("types") List<String> types);

    List<UserNotificationPreferences> findByFcmToken(String fcmToken);

    List<UserNotificationPreferences> findByApnsToken(String apnsToken);
}