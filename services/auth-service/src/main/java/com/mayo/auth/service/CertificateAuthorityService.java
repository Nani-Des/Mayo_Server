package com.mayo.auth.service;

import com.mayo.auth.dto.DeviceCertificateRequest;
import java.security.cert.X509Certificate;

/**
 * Interface for Certificate Authority operations
 */
public interface CertificateAuthorityService {

    /**
     * Generate a device certificate signed by the CA
     */
    X509Certificate generateDeviceCertificate(DeviceCertificateRequest request);

    /**
     * Get the CA certificate
     */
    X509Certificate getCACertificate();

    /**
     * Check if the CA service is initialized
     */
    boolean isInitialized();
}