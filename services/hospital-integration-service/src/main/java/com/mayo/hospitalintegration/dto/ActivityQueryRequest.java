package com.mayo.hospitalintegration.dto;

import com.mayo.hospitalintegration.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityQueryRequest {

    private UUID hospitalId;
    private UUID deviceId;
    private UUID userId;
    private UUID patientId;
    private Activity.ActivityType activityType;
    private Activity.RecordType recordType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private List<Activity.ActivityType> activityTypes;
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "timestamp";
    private String sortDirection = "desc";
}