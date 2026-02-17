package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHospitalRequest {
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    private String phone;
    private String email;
    private String status;
    private Boolean integrationEnabled;
    private List<String> supportedDataTypes;
}
