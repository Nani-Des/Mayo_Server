package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new family
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFamilyRequest {

    @NotBlank(message = "Family name is required")
    @Size(min = 1, max = 100, message = "Family name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}