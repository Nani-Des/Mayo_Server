package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * QR operation data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QROperation {
    private String type;
    private Map<String, Object> parameters;
    private String description;
}