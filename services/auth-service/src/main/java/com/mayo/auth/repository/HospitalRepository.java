package com.mayo.auth.repository;

import com.mayo.auth.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, UUID> {
    Optional<Hospital> findByHospitalId(String hospitalId);
    boolean existsByHospitalId(String hospitalId);
    List<Hospital> findByStatus(String status);
    List<Hospital> findByIntegrationEnabledTrue();
}
