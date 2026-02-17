package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.entity.Activity;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityTrackingService {

    private final ActivityRepository activityRepository;

    @Transactional
    public void trackActivity(Hospital hospital, HospitalDevice device, ActivityType activityType,
                            String description, UUID userId, UUID patientId, String recordId,
                            Activity.RecordType recordType, String metadata) {
        try {
            Activity activity = new Activity();
            activity.setHospital(hospital);
            activity.setDevice(device);
            activity.setActivityType(activityType.toEntityType());
            activity.setActivityDescription(description);
            activity.setUserId(userId);
            activity.setPatientId(patientId);
            activity.setRecordId(recordId);
            activity.setRecordType(recordType);
            activity.setMetadata(metadata);
            activity.setTimestamp(LocalDateTime.now());

            activityRepository.save(activity);

            log.debug("Tracked activity: {} for hospital: {}", activityType, hospital.getHospitalId());
        } catch (Exception e) {
            log.error("Failed to track activity: {}", activityType, e);
        }
    }

    // Enum for service-level activity types
    public enum ActivityType {
        DEVICE_REGISTRATION(Activity.ActivityType.DEVICE_REGISTRATION),
        DEVICE_HEARTBEAT(Activity.ActivityType.DEVICE_HEARTBEAT),
        DEVICE_STATUS_CHANGE(Activity.ActivityType.DEVICE_STATUS_CHANGE),
        DATA_TRANSFER_INITIATED(Activity.ActivityType.DATA_TRANSFER_INITIATED),
        DATA_TRANSFER_COMPLETED(Activity.ActivityType.DATA_TRANSFER_COMPLETED),
        DATA_TRANSFER_FAILED(Activity.ActivityType.DATA_TRANSFER_FAILED),
        DATA_TRANSFER_CANCELLED(Activity.ActivityType.DATA_TRANSFER_CANCELLED),
        ACCESS_REQUESTED(Activity.ActivityType.ACCESS_REQUESTED),
        ACCESS_GRANTED(Activity.ActivityType.ACCESS_GRANTED),
        ACCESS_DENIED(Activity.ActivityType.ACCESS_DENIED),
        PRESCRIPTION_ACCESSED(Activity.ActivityType.PRESCRIPTION_ACCESSED),
        DIAGNOSIS_ACCESSED(Activity.ActivityType.DIAGNOSIS_ACCESSED);

        private final Activity.ActivityType entityType;

        ActivityType(Activity.ActivityType entityType) {
            this.entityType = entityType;
        }

        public Activity.ActivityType toEntityType() {
            return entityType;
        }
    }
}