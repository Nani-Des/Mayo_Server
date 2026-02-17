package com.mayo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHospitalRequest {
    @NotBlank(message = "Hospital ID is required")
    private String hospitalId;

    @NotBlank(message = "Name is required")
    private String name;

    private String address;
    private String city;
    private String state;
    private String country;
    private String phone;
    private String email;
    private List<String> supportedDataTypes;
    
    @Builder.Default
    private boolean integrationEnabled = true;
}
