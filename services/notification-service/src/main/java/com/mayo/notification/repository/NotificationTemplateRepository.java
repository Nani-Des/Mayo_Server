package com.mayo.notification.repository;

import com.mayo.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> {

    List<NotificationTemplate> findByType(String type);

    List<NotificationTemplate> findByActive(boolean active);

    Optional<NotificationTemplate> findByIdAndActive(String id, boolean active);
}