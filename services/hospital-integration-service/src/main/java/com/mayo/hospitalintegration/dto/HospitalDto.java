package com.mayo.hospitalintegration.dto;

import com.mayo.hospitalintegration.entity.Hospital;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalDto {

    private UUID id;
    private String hospitalId;
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String phone;
    private String email;
    private String website;
    private Hospital.HospitalStatus status;
    private Boolean integrationEnabled;
    private String apiEndpoint;
    private String supportedDataTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Note: apiKey is intentionally excluded for security reasons
}