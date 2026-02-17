package com.mayo.patientrecord.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiateOwnershipTransferRequest {

    @NotNull(message = "New owner ID is required")
    private UUID newOwnerId;

    private String reason;

    private Boolean confirmationRequired = true;
}