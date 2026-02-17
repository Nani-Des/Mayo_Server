package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.repository.HospitalDeviceRepository;
import com.mayo.hospitalintegration.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessVerificationService {

    private final HospitalRepository hospitalRepository;
    private final HospitalDeviceRepository deviceRepository;
    private final ActivityTrackingService activityTrackingService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ACCESS_CACHE_PREFIX = "access:verification:";

    public boolean verifyDeviceAccess(String deviceId, String hospitalId, String accessToken) {
        String cacheKey = ACCESS_CACHE_PREFIX + deviceId + ":" + hospitalId;

        // Check cache first
        Boolean cachedResult = (Boolean) redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        boolean hasAccess = performAccessVerification(deviceId, hospitalId, accessToken);

        // Cache result for 15 minutes
        redisTemplate.opsForValue().set(cacheKey, hasAccess, 15, TimeUnit.MINUTES);

        // Track access attempt
        trackAccessAttempt(deviceId, hospitalId, hasAccess);

        return hasAccess;
    }

    public boolean verifyDataAccess(String deviceId, UUID patientId, String dataType) {
        Optional<HospitalDevice> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty()) {
            return false;
        }

        HospitalDevice device = deviceOpt.get();
        Hospital hospital = device.getHospital();

        // Check if hospital supports this data type
        if (hospital.getSupportedDataTypes() != null &&
            !hospital.getSupportedDataTypes().contains(dataType)) {
            return false;
        }

        // Additional verification logic can be added here
        // For now, allow access if device is active and hospital supports the data type
        return device.getStatus() == HospitalDevice.DeviceStatus.ACTIVE;
    }

    private boolean performAccessVerification(String deviceId, String hospitalId, String accessToken) {
        // Validate device exists and is active
        Optional<HospitalDevice> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty()) {
            return false;
        }

        HospitalDevice device = deviceOpt.get();

        // Check device status
        if (device.getStatus() != HospitalDevice.DeviceStatus.ACTIVE) {
            return false;
        }

        // Check hospital integration is enabled
        if (!device.getHospital().getIntegrationEnabled()) {
            return false;
        }

        // Validate hospital ID matches
        if (!device.getHospital().getHospitalId().equals(hospitalId)) {
            return false;
        }

        // Additional token validation can be implemented here
        // For now, basic validation is sufficient
        return accessToken != null && !accessToken.isEmpty();
    }

    private void trackAccessAttempt(String deviceId, String hospitalId, boolean granted) {
        Optional<HospitalDevice> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isPresent()) {
            HospitalDevice device = deviceOpt.get();
            activityTrackingService.trackActivity(
                    device.getHospital(),
                    device,
                    granted ? ActivityTrackingService.ActivityType.ACCESS_GRANTED :
                             ActivityTrackingService.ActivityType.ACCESS_DENIED,
                    "Access " + (granted ? "granted" : "denied") + " for device: " + deviceId,
                    null, null, null, null, null
            );
        }
    }

    public void invalidateAccessCache(String deviceId, String hospitalId) {
        String cacheKey = ACCESS_CACHE_PREFIX + deviceId + ":" + hospitalId;
        redisTemplate.delete(cacheKey);
        log.debug("Invalidated access cache for device: {}, hospital: {}", deviceId, hospitalId);
    }
}