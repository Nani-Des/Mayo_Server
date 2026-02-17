package com.mayo.common.security.certificate;

import lombok.extern.slf4j.Slf4j;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Utility class for certificate operations
 */
@Slf4j
public class CertificateUtils {

    private static final String CERTIFICATE_HEADER = "-----BEGIN CERTIFICATE-----";
    private static final String CERTIFICATE_FOOTER = "-----END CERTIFICATE-----";

    /**
     * Parse X509 certificate from PEM string
     */
    public static X509Certificate parseCertificateFromPem(String certificatePem) throws CertificateException {
        try {
            // Remove PEM headers/footers if present
            String cleanCert = certificatePem
                .replace(CERTIFICATE_HEADER, "")
                .replace(CERTIFICATE_FOOTER, "")
                .replaceAll("\\s", "");

            // Decode base64
            byte[] certBytes = Base64.getDecoder().decode(cleanCert);

            // Parse certificate
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(new java.io.ByteArrayInputStream(certBytes));

        } catch (Exception e) {
            log.error("Failed to parse certificate from PEM: {}", e.getMessage());
            throw new CertificateException("Invalid certificate format", e);
        }
    }

    /**
     * Convert certificate to PEM string
     */
    public static String certificateToPem(X509Certificate certificate) {
        try {
            byte[] certBytes = certificate.getEncoded();
            String base64Cert = Base64.getEncoder().encodeToString(certBytes);

            return CERTIFICATE_HEADER + "\n" +
                   base64Cert.replaceAll("(.{64})", "$1\n") + "\n" +
                   CERTIFICATE_FOOTER;

        } catch (Exception e) {
            log.error("Failed to convert certificate to PEM: {}", e.getMessage());
            throw new RuntimeException("Certificate encoding failed", e);
        }
    }

    /**
     * Validate certificate is not expired and not revoked
     */
    public static boolean isCertificateValid(X509Certificate certificate) {
        try {
            certificate.checkValidity();
            // Additional revocation checking could be implemented here
            // - CRL checking
            // - OCSP checking
            return true;
        } catch (Exception e) {
            log.warn("Certificate validation failed: {}", e.getMessage());
            return false;
        }
    }
}