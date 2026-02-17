package com.mayo.hospitalintegration.repository;

import com.mayo.hospitalintegration.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, UUID> {

    Optional<Hospital> findByHospitalId(String hospitalId);

    List<Hospital> findByStatus(Hospital.HospitalStatus status);

    List<Hospital> findByIntegrationEnabledTrue();

    @Query("SELECT h FROM Hospital h WHERE h.name LIKE %:name%")
    List<Hospital> findByNameContaining(@Param("name") String name);

    @Query("SELECT h FROM Hospital h WHERE h.city = :city AND h.state = :state")
    List<Hospital> findByCityAndState(@Param("city") String city, @Param("state") String state);

    boolean existsByHospitalId(String hospitalId);
}