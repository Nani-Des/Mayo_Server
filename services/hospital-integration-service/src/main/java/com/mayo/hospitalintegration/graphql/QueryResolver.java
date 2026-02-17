package com.mayo.hospitalintegration.graphql;

import com.mayo.hospitalintegration.dto.ActivityDto;
import com.mayo.hospitalintegration.dto.HospitalDeviceDto;
import com.mayo.hospitalintegration.dto.HospitalDto;
import com.mayo.hospitalintegration.service.ActivityService;
import com.mayo.hospitalintegration.service.DeviceRegistrationService;
import com.mayo.hospitalintegration.service.HospitalService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueryResolver implements GraphQLQueryResolver {

    private final HospitalService hospitalService;
    private final DeviceRegistrationService deviceRegistrationService;
    private final ActivityService activityService;

    public List<HospitalDto> getHospitals() {
        log.debug("GraphQL query: getHospitals");
        return hospitalService.getAllHospitals();
    }

    public HospitalDto getHospital(String id) {
        log.debug("GraphQL query: getHospital with id: {}", id);
        try {
            UUID hospitalId = UUID.fromString(id);
            return hospitalService.getHospitalById(hospitalId).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for hospital id: {}", id);
            return null;
        }
    }

    public List<HospitalDeviceDto> getDevices(String hospitalId) {
        log.debug("GraphQL query: getDevices for hospitalId: {}", hospitalId);
        if (hospitalId != null) {
            try {
                UUID hospitalUuid = UUID.fromString(hospitalId);
                return deviceRegistrationService.getDevicesByHospitalId(hospitalUuid);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format for hospitalId: {}", hospitalId);
                return List.of();
            }
        }
        // If no hospitalId provided, return empty list (could be changed to return all devices if needed)
        return List.of();
    }

    public HospitalDeviceDto getDevice(String deviceId) {
        log.debug("GraphQL query: getDevice with deviceId: {}", deviceId);
        return deviceRegistrationService.getDeviceById(deviceId).orElse(null);
    }

    public List<ActivityDto> getActivities(String hospitalId, String deviceId, Integer limit, Integer offset) {
        log.debug("GraphQL query: getActivities with hospitalId: {}, deviceId: {}, limit: {}, offset: {}",
                 hospitalId, deviceId, limit, offset);
        return activityService.getActivities(hospitalId, deviceId, limit, offset);
    }
}