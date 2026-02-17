package com.mayo.common.security.certificate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import java.util.Set;

/**
 * Device certificate validation service
 * Validates device certificates for secure device communication
 */
@Service
@Slf4j
public class DeviceCertificateValidator implements X509TrustManager {

    private final Set<String> trustedDeviceIds;

    public DeviceCertificateValidator() {
        // In production, this should be loaded from a secure configuration
        this.trustedDeviceIds = Set.of(
            "device-001", "device-002", "device-003" // Example trusted devices
        );
    }

    /**
     * Validate device certificate
     */
    public boolean validateDeviceCertificate(X509Certificate certificate, String deviceId) {
        try {
            // Check if device ID is in trusted list
            if (!trustedDeviceIds.contains(deviceId)) {
                log.warn("Device ID {} is not in trusted list", deviceId);
                return false;
            }

            // Validate certificate chain
            certificate.checkValidity();

            // Additional certificate validation logic can be added here
            // - Check certificate issuer
            // - Check certificate subject
            // - Check certificate extensions
            // - Check revocation status (CRL/OCSP)

            log.info("Device certificate validated successfully for device: {}", deviceId);
            return true;

        } catch (CertificateException e) {
            log.error("Certificate validation failed for device {}: {}", deviceId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during certificate validation for device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }

    /**
     * Validate device certificate from PEM string
     */
    public boolean validateDeviceCertificate(String certificatePem, String deviceId) {
        try {
            // Parse PEM certificate
            X509Certificate certificate = CertificateUtils.parseCertificateFromPem(certificatePem);
            return validateDeviceCertificate(certificate, deviceId);
        } catch (Exception e) {
            log.error("Failed to parse certificate for device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }

    // X509TrustManager implementation for SSL/TLS

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("Client certificate chain is empty");
        }

        X509Certificate clientCert = chain[0];
        String deviceId = extractDeviceIdFromCertificate(clientCert);

        if (!validateDeviceCertificate(clientCert, deviceId)) {
            throw new CertificateException("Client certificate validation failed for device: " + deviceId);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Server certificate validation - could be implemented if needed
        throw new CertificateException("Server certificate validation not implemented");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    /**
     * Extract device ID from certificate subject or extensions
     */
    private String extractDeviceIdFromCertificate(X509Certificate certificate) {
        // Extract device ID from certificate subject alternative name or custom extension
        // This is a simplified implementation - in production, use proper certificate fields
        String subject = certificate.getSubjectX500Principal().getName();

        // Example: CN=device-001, OU=Devices, O=Mayo Clinic
        if (subject.contains("CN=")) {
            String cn = subject.substring(subject.indexOf("CN=") + 3);
            int commaIndex = cn.indexOf(",");
            if (commaIndex > 0) {
                return cn.substring(0, commaIndex);
            }
            return cn;
        }

        return "unknown-device";
    }

    /**
     * Add trusted device ID dynamically
     */
    public void addTrustedDevice(String deviceId) {
        // In production, this should be persisted securely
        trustedDeviceIds.add(deviceId);
        log.info("Added trusted device: {}", deviceId);
    }

    /**
     * Remove trusted device ID
     */
    public void removeTrustedDevice(String deviceId) {
        trustedDeviceIds.remove(deviceId);
        log.info("Removed trusted device: {}", deviceId);
    }
}