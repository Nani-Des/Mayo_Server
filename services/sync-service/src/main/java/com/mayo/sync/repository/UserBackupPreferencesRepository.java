package com.mayo.sync.repository;

import com.mayo.sync.entity.UserBackupPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBackupPreferencesRepository extends JpaRepository<UserBackupPreferences, UUID> {

    Optional<UserBackupPreferences> findByUserId(UUID userId);
}