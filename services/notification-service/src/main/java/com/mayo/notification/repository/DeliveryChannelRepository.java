package com.mayo.notification.repository;

import com.mayo.notification.entity.DeliveryChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryChannelRepository extends JpaRepository<DeliveryChannel, UUID> {

    List<DeliveryChannel> findByActive(boolean active);

    Optional<DeliveryChannel> findByNameAndActive(String name, boolean active);

    List<DeliveryChannel> findByProvider(String provider);
}