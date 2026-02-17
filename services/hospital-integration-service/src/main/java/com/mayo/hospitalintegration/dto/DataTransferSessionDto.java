package com.mayo.hospitalintegration.dto;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataTransferSessionDto {

    private UUID id;
    private String sessionId;
    private UUID hospitalId;
    private String hospitalName;
    private UUID deviceId;
    private String deviceName;
    private DataTransferSession.TransferType transferType;
    private DataTransferSession.DataType dataType;
    private DataTransferSession.TransferProtocol protocol;
    private DataTransferSession.TransferStatus status;
    private Long totalRecords;
    private Long processedRecords;
    private Long failedRecords;
    private Long dataSizeBytes;
    private Long transferredBytes;
    private String sourceEndpoint;
    private String destinationEndpoint;
    private UUID initiatedBy;
    private String errorMessage;
    private String metadata;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}