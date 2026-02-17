package com.mayo.auth.service;

import com.mayo.auth.dto.CreateHospitalRequest;
import com.mayo.auth.dto.HospitalResponse;
import com.mayo.auth.dto.UpdateHospitalRequest;
import com.mayo.auth.entity.Hospital;
import com.mayo.auth.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HospitalService {

    private final HospitalRepository hospitalRepository;

    @Transactional
    public HospitalResponse createHospital(CreateHospitalRequest request) {
        log.info("Creating new hospital with ID: {}", request.getHospitalId());

        if (hospitalRepository.existsByHospitalId(request.getHospitalId())) {
            throw new IllegalArgumentException("Hospital ID already exists: " + request.getHospitalId());
        }

        Hospital hospital = Hospital.builder()
                .hospitalId(request.getHospitalId())
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .phone(request.getPhone())
                .email(request.getEmail())
                .supportedDataTypes(request.getSupportedDataTypes())
                .integrationEnabled(request.isIntegrationEnabled())
                .status("ACTIVE")
                .build();

        Hospital savedHospital = hospitalRepository.save(hospital);
        log.info("Hospital created successfully: {}", savedHospital.getId());

        return mapToResponse(savedHospital);
    }

    @Transactional(readOnly = true)
    public List<HospitalResponse> getAllHospitals() {
        return hospitalRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HospitalResponse getHospital(UUID id) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found with ID: " + id));
        return mapToResponse(hospital);
    }
    
    @Transactional(readOnly = true)
    public HospitalResponse getHospitalByHospitalId(String hospitalId) {
        Hospital hospital = hospitalRepository.findByHospitalId(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found with Hospital ID: " + hospitalId));
        return mapToResponse(hospital);
    }

    @Transactional
    public HospitalResponse updateHospital(UUID id, UpdateHospitalRequest request) {
        log.info("Updating hospital: {}", id);
        
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found with ID: " + id));

        if (request.getName() != null) hospital.setName(request.getName());
        if (request.getAddress() != null) hospital.setAddress(request.getAddress());
        if (request.getCity() != null) hospital.setCity(request.getCity());
        if (request.getState() != null) hospital.setState(request.getState());
        if (request.getCountry() != null) hospital.setCountry(request.getCountry());
        if (request.getPhone() != null) hospital.setPhone(request.getPhone());
        if (request.getEmail() != null) hospital.setEmail(request.getEmail());
        if (request.getStatus() != null) hospital.setStatus(request.getStatus());
        if (request.getIntegrationEnabled() != null) hospital.setIntegrationEnabled(request.getIntegrationEnabled());
        if (request.getSupportedDataTypes() != null) hospital.setSupportedDataTypes(request.getSupportedDataTypes());

        Hospital updatedHospital = hospitalRepository.save(hospital);
        return mapToResponse(updatedHospital);
    }

    @Transactional
    public void deleteHospital(UUID id) {
        log.info("Deleting hospital: {}", id);
        if (!hospitalRepository.existsById(id)) {
            throw new IllegalArgumentException("Hospital not found with ID: " + id);
        }
        hospitalRepository.deleteById(id);
    }

    private HospitalResponse mapToResponse(Hospital hospital) {
        return HospitalResponse.builder()
                .id(hospital.getId())
                .hospitalId(hospital.getHospitalId())
                .name(hospital.getName())
                .address(hospital.getAddress())
                .city(hospital.getCity())
                .state(hospital.getState())
                .country(hospital.getCountry())
                .phone(hospital.getPhone())
                .email(hospital.getEmail())
                .status(hospital.getStatus())
                .integrationEnabled(hospital.isIntegrationEnabled())
                .supportedDataTypes(hospital.getSupportedDataTypes())
                .createdAt(hospital.getCreatedAt())
                .updatedAt(hospital.getUpdatedAt())
                .build();
    }
}
