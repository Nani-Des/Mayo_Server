package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalResponse {
    private UUID id;
    private String hospitalId;
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    private String phone;
    private String email;
    private String status;
    private boolean integrationEnabled;
    private List<String> supportedDataTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
