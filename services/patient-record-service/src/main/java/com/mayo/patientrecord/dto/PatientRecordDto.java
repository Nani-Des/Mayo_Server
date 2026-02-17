package com.mayo.patientrecord.dto;

import com.fasterxml.jackson.databind.JsonNode;
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
public class PatientRecordDto {

    private UUID id;
    private UUID patientId;
    private RecordType recordType;
    private String title;
    private String description;
    private JsonNode metadata;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}