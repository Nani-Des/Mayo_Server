package com.mayo.hospitalintegration.service;

import com.mayo.common.security.encryption.AesEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Data encryption service for Hospital Integration Service
 * Handles encryption/decryption of sensitive hospital data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataEncryptionService {

    private final AesEncryptionService aesEncryptionService;

    /**
     * Encrypt patient data before storage or transmission
     */
    public String encryptPatientData(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        try {
            String encrypted = aesEncryptionService.encrypt(data);
            log.debug("Patient data encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt patient data: {}", e.getMessage());
            throw new RuntimeException("Patient data encryption failed", e);
        }
    }

    /**
     * Decrypt patient data after retrieval
     */
    public String decryptPatientData(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            String decrypted = aesEncryptionService.decrypt(encryptedData);
            log.debug("Patient data decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt patient data: {}", e.getMessage());
            throw new RuntimeException("Patient data decryption failed", e);
        }
    }

    /**
     * Encrypt device configuration data
     */
    public String encryptDeviceConfig(String configData) {
        if (configData == null || configData.isEmpty()) {
            return configData;
        }

        try {
            String encrypted = aesEncryptionService.encrypt(configData);
            log.debug("Device configuration encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt device config: {}", e.getMessage());
            throw new RuntimeException("Device config encryption failed", e);
        }
    }

    /**
     * Decrypt device configuration data
     */
    public String decryptDeviceConfig(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            String decrypted = aesEncryptionService.decrypt(encryptedData);
            log.debug("Device configuration decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt device config: {}", e.getMessage());
            throw new RuntimeException("Device config decryption failed", e);
        }
    }

    /**
     * Encrypt hospital integration data
     */
    public String encryptIntegrationData(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        try {
            String encrypted = aesEncryptionService.encrypt(data);
            log.debug("Integration data encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt integration data: {}", e.getMessage());
            throw new RuntimeException("Integration data encryption failed", e);
        }
    }

    /**
     * Decrypt hospital integration data
     */
    public String decryptIntegrationData(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            String decrypted = aesEncryptionService.decrypt(encryptedData);
            log.debug("Integration data decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt integration data: {}", e.getMessage());
            throw new RuntimeException("Integration data decryption failed", e);
        }
    }
}