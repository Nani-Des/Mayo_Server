package com.mayo.patientrecord.dto;

import com.mayo.patientrecord.entity.Activity;
import com.mayo.patientrecord.entity.RecordType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDto {

    private UUID id;
    private UUID patientId;
    private UUID recordId;
    private RecordType recordType;
    private Activity.Action action;
    private LocalDateTime timestamp;
    private String userId;
    private UUID deviceId;
    private UUID hospitalId;
    private LocalDateTime createdAt;
}