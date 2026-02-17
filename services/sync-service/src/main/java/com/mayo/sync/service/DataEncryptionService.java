package com.mayo.sync.service;

import com.mayo.common.security.encryption.AesEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Data encryption service for Sync Service
 * Handles encryption/decryption of sensitive sync data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataEncryptionService {

    private final AesEncryptionService aesEncryptionService;

    /**
     * Encrypt sync data before storage
     */
    public String encryptSyncData(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        try {
            String encrypted = aesEncryptionService.encrypt(data);
            log.debug("Sync data encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt sync data: {}", e.getMessage());
            throw new RuntimeException("Data encryption failed", e);
        }
    }

    /**
     * Decrypt sync data after retrieval
     */
    public String decryptSyncData(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            String decrypted = aesEncryptionService.decrypt(encryptedData);
            log.debug("Sync data decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt sync data: {}", e.getMessage());
            throw new RuntimeException("Data decryption failed", e);
        }
    }

    /**
     * Encrypt device public key
     */
    public String encryptDeviceKey(String publicKey) {
        if (publicKey == null || publicKey.isEmpty()) {
            return publicKey;
        }

        try {
            String encrypted = aesEncryptionService.encrypt(publicKey);
            log.debug("Device public key encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt device key: {}", e.getMessage());
            throw new RuntimeException("Device key encryption failed", e);
        }
    }

    /**
     * Decrypt device public key
     */
    public String decryptDeviceKey(String encryptedKey) {
        if (encryptedKey == null || encryptedKey.isEmpty()) {
            return encryptedKey;
        }

        try {
            String decrypted = aesEncryptionService.decrypt(encryptedKey);
            log.debug("Device public key decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt device key: {}", e.getMessage());
            throw new RuntimeException("Device key decryption failed", e);
        }
    }

    /**
     * Encrypt conflict data
     */
    public String encryptConflictData(String conflictData) {
        if (conflictData == null || conflictData.isEmpty()) {
            return conflictData;
        }

        try {
            String encrypted = aesEncryptionService.encrypt(conflictData);
            log.debug("Conflict data encrypted successfully");
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt conflict data: {}", e.getMessage());
            throw new RuntimeException("Conflict data encryption failed", e);
        }
    }

    /**
     * Decrypt conflict data
     */
    public String decryptConflictData(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        try {
            String decrypted = aesEncryptionService.decrypt(encryptedData);
            log.debug("Conflict data decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt conflict data: {}", e.getMessage());
            throw new RuntimeException("Conflict data decryption failed", e);
        }
    }
}