package com.mayo.hospitalintegration.dto;

import com.mayo.hospitalintegration.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDto {

    private UUID id;
    private UUID hospitalId;
    private String hospitalName;
    private UUID deviceId;
    private String deviceName;
    private Activity.ActivityType activityType;
    private String activityDescription;
    private UUID userId;
    private UUID patientId;
    private String recordId;
    private Activity.RecordType recordType;
    private String ipAddress;
    private String userAgent;
    private String metadata;
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
}