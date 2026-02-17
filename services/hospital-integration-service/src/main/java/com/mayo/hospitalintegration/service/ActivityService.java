package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.dto.ActivityDto;
import com.mayo.hospitalintegration.entity.Activity;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.repository.ActivityRepository;
import com.mayo.hospitalintegration.repository.HospitalDeviceRepository;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalDeviceRepository deviceRepository;

    @Transactional(readOnly = true)
    public List<ActivityDto> getActivities(String hospitalId, String deviceId, Integer limit, Integer offset) {
        Pageable pageable = PageRequest.of(offset != null ? offset : 0, limit != null ? limit : 50);

        Page<Activity> activities;
        if (hospitalId != null && deviceId != null) {
            // Get activities for specific hospital and device
            Optional<Hospital> hospital = hospitalRepository.findById(UUID.fromString(hospitalId));
            Optional<HospitalDevice> device = deviceRepository.findById(UUID.fromString(deviceId));
            if (hospital.isPresent() && device.isPresent()) {
                activities = activityRepository.findByDeviceOrderByTimestampDesc(device.get(), pageable);
            } else {
                return List.of();
            }
        } else if (hospitalId != null) {
            // Get activities for specific hospital
            Optional<Hospital> hospital = hospitalRepository.findById(UUID.fromString(hospitalId));
            if (hospital.isPresent()) {
                activities = activityRepository.findByHospitalOrderByTimestampDesc(hospital.get(), pageable);
            } else {
                return List.of();
            }
        } else {
            // Get all activities (with pagination)
            activities = activityRepository.findAll(pageable);
        }

        return activities.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private ActivityDto mapToDto(Activity activity) {
        ActivityDto dto = new ActivityDto();
        dto.setId(activity.getId());
        dto.setHospitalId(activity.getHospital().getId());
        dto.setHospitalName(activity.getHospital().getName());
        if (activity.getDevice() != null) {
            dto.setDeviceId(activity.getDevice().getId());
            dto.setDeviceName(activity.getDevice().getDeviceName());
        }
        dto.setActivityType(activity.getActivityType());
        dto.setActivityDescription(activity.getActivityDescription());
        dto.setUserId(activity.getUserId());
        dto.setPatientId(activity.getPatientId());
        dto.setRecordId(activity.getRecordId());
        dto.setRecordType(activity.getRecordType());
        dto.setIpAddress(activity.getIpAddress());
        dto.setUserAgent(activity.getUserAgent());
        dto.setMetadata(activity.getMetadata());
        dto.setTimestamp(activity.getTimestamp());
        dto.setCreatedAt(activity.getCreatedAt());
        return dto;
    }
}