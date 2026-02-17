package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.dto.ActivityDto;
import com.mayo.hospitalintegration.dto.ActivityQueryRequest;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityQueryService {

    private final ActivityRepository activityRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalDeviceRepository deviceRepository;

    @Transactional(readOnly = true)
    public Page<ActivityDto> queryActivities(ActivityQueryRequest request) {
        log.debug("Querying activities with filters: {}", request);

        // Build pageable
        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        // Resolve entities
        Hospital hospital = request.getHospitalId() != null ?
            hospitalRepository.findById(request.getHospitalId()).orElse(null) : null;
        HospitalDevice device = request.getDeviceId() != null ?
            deviceRepository.findById(request.getDeviceId()).orElse(null) : null;

        // Query activities
        Page<Activity> activities = activityRepository.findActivitiesWithFilters(
            hospital, device, request.getUserId(), request.getPatientId(),
            request.getActivityType(), request.getRecordType(),
            request.getStartDate(), request.getEndDate(), pageable
        );

        return activities.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Page<ActivityDto> queryActivitiesByHospital(UUID hospitalId, ActivityQueryRequest request) {
        log.debug("Querying activities for hospital: {} with filters: {}", hospitalId, request);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return Page.empty();
        }

        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<Activity> activities = activityRepository.findActivitiesByHospitalWithFilters(
            hospitalOpt.get(), request.getUserId(), request.getActivityType(),
            request.getStartDate(), request.getEndDate(), pageable
        );

        return activities.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Page<ActivityDto> queryActivitiesByUser(UUID userId, ActivityQueryRequest request) {
        log.debug("Querying activities for user: {} with filters: {}", userId, request);

        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Hospital hospital = request.getHospitalId() != null ?
            hospitalRepository.findById(request.getHospitalId()).orElse(null) : null;

        Page<Activity> activities = activityRepository.findActivitiesByUserWithFilters(
            userId, hospital, request.getActivityType(),
            request.getStartDate(), request.getEndDate(), pageable
        );

        return activities.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> getRecentActivities(UUID hospitalId, int limit) {
        log.debug("Getting recent activities for hospital: {} with limit: {}", hospitalId, limit);

        Optional<Hospital> hospitalOpt = hospitalRepository.findById(hospitalId);
        if (hospitalOpt.isEmpty()) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Activity> activities = activityRepository.findByHospitalOrderByTimestampDesc(hospitalOpt.get(), pageable);

        return activities.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> getActivitiesByTypes(List<Activity.ActivityType> activityTypes, int limit) {
        log.debug("Getting activities by types: {} with limit: {}", activityTypes, limit);

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Activity> activities = activityRepository.findAll(pageable);

        return activities.getContent().stream()
                .filter(activity -> activityTypes.contains(activity.getActivityType()))
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