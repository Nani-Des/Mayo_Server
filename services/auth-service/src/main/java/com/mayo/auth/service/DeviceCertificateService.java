package com.mayo.auth.service;

import com.mayo.auth.dto.DeviceCertificateRequest;
import com.mayo.auth.dto.DeviceCertificateValidationResult;
import com.mayo.events.topics.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.security.cert.CertificateException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;

/**
 * Service for X.509 certificate validation and management for hospital devices
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCertificateService {

    /**
     * Cached CRL entry with expiration
     */
    private static class CachedCRL {
        private final X509CRL crl;
        private final Instant expiresAt;

        public CachedCRL(X509CRL crl, Instant expiresAt) {
            this.crl = crl;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Cached OCSP response entry with expiration
     */
    private static class CachedOCSPResponse {
        private final int status;
        private final Instant expiresAt;

        public CachedOCSPResponse(int status, Instant expiresAt) {
            this.status = status;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    @Value("${auth.certificate.truststore.path:classpath:truststore.jks}")
    private String trustStorePath;

    @Value("${auth.certificate.truststore.password:changeit}")
    private String trustStorePassword;

    @Value("${auth.certificate.ocsp.enabled:false}")
    private boolean ocspEnabled;

    @Value("${auth.certificate.crl.enabled:true}")
    private boolean crlEnabled;

    private CertificateFactory certificateFactory;
    private final CertificateAuthorityService certificateAuthorityService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // CRL and OCSP caching
    private final Map<String, CachedCRL> crlCache = new ConcurrentHashMap<>();
    private final Map<String, CachedOCSPResponse> ocspCache = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            this.certificateFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Failed to initialize CertificateFactory", e);
        }
    }

    /**
     * Validate device certificate
     */
    public DeviceCertificateValidationResult validateDeviceCertificate(String deviceId, String certificatePem) {
        log.info("Validating certificate for device: {}", deviceId);

        try {
            // Parse certificate
            X509Certificate certificate = parseCertificate(certificatePem);

            // Basic validation
            certificate.checkValidity();

            // Load trust store
            KeyStore trustStore = loadTrustStore();

            // Build certificate path
            CertPath certPath = certificateFactory.generateCertPath(List.of(certificate));
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");

            PKIXParameters params = new PKIXParameters(trustStore);
            params.setRevocationEnabled(true);

            // Validate certificate path
            validator.validate(certPath, params);

            // Additional device-specific validation
            validateDeviceCertificateFields(certificate, deviceId);

            // Check revocation if enabled
            if (crlEnabled) {
                checkCRLRevocation(certificate);
            }

            if (ocspEnabled) {
                checkOCSPRevocation(certificate, trustStore);
            }

            log.info("Certificate validation successful for device: {}", deviceId);

            // Publish certificate validation event
            publishCertificateValidationEvent(deviceId, "CERTIFICATE_VALIDATION_SUCCESS",
                    Map.of("certificateSubject", certificate.getSubjectX500Principal().getName(),
                            "certificateIssuer", certificate.getIssuerX500Principal().getName()));

            return DeviceCertificateValidationResult.builder()
                    .valid(true)
                    .deviceId(deviceId)
                    .certificateSubject(certificate.getSubjectX500Principal().getName())
                    .certificateIssuer(certificate.getIssuerX500Principal().getName())
                    .validFrom(certificate.getNotBefore().toInstant())
                    .validUntil(certificate.getNotAfter().toInstant())
                    .build();

        } catch (CertificateException | CertPathValidatorException e) {
            log.warn("Certificate validation failed for device: {} - {}", deviceId, e.getMessage());

            // Publish certificate validation failure event
            publishCertificateValidationEvent(deviceId, "CERTIFICATE_VALIDATION_FAILED",
                    Map.of("error", e.getMessage()));

            return DeviceCertificateValidationResult.builder()
                    .valid(false)
                    .deviceId(deviceId)
                    .errorMessage(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error during certificate validation for device: {}", deviceId, e);

            // Publish certificate validation failure event
            publishCertificateValidationEvent(deviceId, "CERTIFICATE_VALIDATION_ERROR",
                    Map.of("error", e.getMessage()));

            return DeviceCertificateValidationResult.builder()
                    .valid(false)
                    .deviceId(deviceId)
                    .errorMessage("Internal validation error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Generate device certificate using the CA
     */
    public String generateDeviceCertificate(String deviceId, String commonName) {
        try {
            log.info("Generating certificate for device: {} with common name: {}", deviceId, commonName);

            // Create certificate request
            DeviceCertificateRequest request = DeviceCertificateRequest.builder()
                    .deviceId(deviceId)
                    .commonName(commonName)
                    .build();

            // Generate certificate
            X509Certificate certificate = certificateAuthorityService.generateDeviceCertificate(request);

            // Convert to PEM format
            String pemCertificate = convertCertificateToPem(certificate);

            log.info("Certificate generated successfully for device: {}", deviceId);

            // Publish certificate generation event
            publishCertificateValidationEvent(deviceId, "CERTIFICATE_GENERATION_SUCCESS",
                    Map.of("certificateSubject", certificate.getSubjectX500Principal().getName(),
                            "certificateIssuer", certificate.getIssuerX500Principal().getName()));

            return pemCertificate;

        } catch (Exception e) {
            log.error("Failed to generate certificate for device: {}", deviceId, e);

            // Publish certificate generation failure event
            publishCertificateValidationEvent(deviceId, "CERTIFICATE_GENERATION_FAILED",
                    Map.of("error", e.getMessage()));

            throw new RuntimeException("Certificate generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Revoke device certificate
     */
    public void revokeDeviceCertificate(String deviceId, String certificateSerial, CRLReason reason) {
        log.info("Revoking certificate for device: {} with reason: {}", deviceId, reason);

        // This would update a CRL or OCSP responder
        // Implementation depends on the CA infrastructure
        log.warn("Certificate revocation not fully implemented - requires CA integration");
    }

    /**
     * Check certificate revocation via CRL
     */
    private void checkCRLRevocation(X509Certificate certificate) throws CertificateException {
        try {
            // Get CRL distribution points from certificate
            byte[] crlDpExtension = certificate.getExtensionValue("2.5.29.31");
            if (crlDpExtension == null) {
                log.debug("No CRL distribution points found in certificate");
                return;
            }

            // Parse CRL distribution points
            List<String> crlUrls = parseCRLDistributionPoints(crlDpExtension);
            if (crlUrls.isEmpty()) {
                log.debug("No CRL URLs found in distribution points");
                return;
            }

            // Try each CRL URL
            for (String crlUrl : crlUrls) {
                try {
                    X509CRL crl = getCRL(crlUrl);
                    if (crl != null && isCertificateRevoked(crl, certificate)) {
                        throw new CertificateException("Certificate is revoked according to CRL: " + crlUrl);
                    }
                    log.debug("Certificate not revoked in CRL: {}", crlUrl);
                    break; // Successfully checked one CRL, no need to check others
                } catch (Exception e) {
                    log.warn("Failed to check CRL from {}: {}", crlUrl, e.getMessage());
                    // Continue to next URL
                }
            }
        } catch (CertificateException e) {
            throw e; // Re-throw certificate exceptions
        } catch (Exception e) {
            log.warn("CRL revocation check failed: {}", e.getMessage());
            // Don't fail validation for CRL check errors in development
        }
    }

    /**
     * Parse CRL distribution points extension
     */
    private List<String> parseCRLDistributionPoints(byte[] extensionValue) throws Exception {
        List<String> urls = new ArrayList<>();
        try {
            org.bouncycastle.asn1.x509.CRLDistPoint crlDistPoint = org.bouncycastle.asn1.x509.CRLDistPoint.getInstance(
                    org.bouncycastle.asn1.ASN1Primitive.fromByteArray(extensionValue));

            for (org.bouncycastle.asn1.x509.DistributionPoint dp : crlDistPoint.getDistributionPoints()) {
                org.bouncycastle.asn1.x509.DistributionPointName dpName = dp.getDistributionPoint();
                if (dpName != null && dpName.getType() == org.bouncycastle.asn1.x509.DistributionPointName.FULL_NAME) {
                    org.bouncycastle.asn1.x509.GeneralNames generalNames = org.bouncycastle.asn1.x509.GeneralNames
                            .getInstance(dpName.getName());

                    for (org.bouncycastle.asn1.x509.GeneralName generalName : generalNames.getNames()) {
                        if (generalName
                                .getTagNo() == org.bouncycastle.asn1.x509.GeneralName.uniformResourceIdentifier) {
                            String url = ((org.bouncycastle.asn1.DERIA5String) generalName.getName()).getString();
                            urls.add(url);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse CRL distribution points: {}", e.getMessage());
        }
        return urls;
    }

    /**
     * Get CRL from URL with caching
     */
    private X509CRL getCRL(String crlUrl) throws Exception {
        // Check cache first
        CachedCRL cached = crlCache.get(crlUrl);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached CRL for: {}", crlUrl);
            return cached.crl;
        }

        // Fetch new CRL
        log.debug("Fetching CRL from: {}", crlUrl);
        URL url = URI.create(crlUrl).toURL();
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(5000); // 5 seconds
        connection.setReadTimeout(10000); // 10 seconds

        try (InputStream inputStream = connection.getInputStream()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509CRL crl = (X509CRL) cf.generateCRL(inputStream);

            // Cache the CRL (cache for 1 hour or until next update, whichever is sooner)
            Instant nextUpdate = crl.getNextUpdate() != null ? crl.getNextUpdate().toInstant()
                    : Instant.now().plusSeconds(3600);
            Instant expiresAt = Instant.now().plusSeconds(3600); // 1 hour max cache
            if (nextUpdate.isBefore(expiresAt)) {
                expiresAt = nextUpdate;
            }

            crlCache.put(crlUrl, new CachedCRL(crl, expiresAt));
            log.debug("Cached CRL for: {} until {}", crlUrl, expiresAt);

            return crl;
        }
    }

    /**
     * Check if certificate is revoked in the CRL
     */
    private boolean isCertificateRevoked(X509CRL crl, X509Certificate certificate) {
        return crl.getRevokedCertificate(certificate.getSerialNumber()) != null;
    }

    /**
     * Check certificate revocation via OCSP
     */
    private void checkOCSPRevocation(X509Certificate certificate, KeyStore trustStore) throws Exception {
        try {
            X509Certificate issuerCert = findIssuerCert(certificate, trustStore);
            if (issuerCert == null) {
                log.debug("Issuer certificate not found in trust store for OCSP");
                return;
            }
            // Get OCSP responder URL from certificate
            byte[] aiaExtension = certificate.getExtensionValue("1.3.6.1.5.5.7.1.1");
            if (aiaExtension == null) {
                log.debug("No Authority Information Access extension found in certificate");
                return;
            }

            // Parse Authority Information Access extension
            List<String> ocspUrls = parseAuthorityInformationAccess(aiaExtension);
            if (ocspUrls.isEmpty()) {
                log.debug("No OCSP URLs found in Authority Information Access extension");
                return;
            }

            // Try each OCSP URL
            for (String ocspUrl : ocspUrls) {
                try {
                    int status = getOCSPStatus(ocspUrl, certificate, issuerCert);
                    if (status == 1) { // Revoked
                        throw new CertificateException("Certificate is revoked according to OCSP: " + ocspUrl);
                    } else if (status == 0) { // Good
                        log.debug("Certificate status is good according to OCSP: {}", ocspUrl);
                        break; // Successfully checked, no need to check others
                    } else { // Unknown
                        log.warn("OCSP returned unknown status for certificate from: {}", ocspUrl);
                    }
                } catch (Exception e) {
                    log.warn("Failed to check OCSP from {}: {}", ocspUrl, e.getMessage());
                    // Continue to next URL
                }
            }
        } catch (CertificateException e) {
            throw e; // Re-throw certificate exceptions
        } catch (Exception e) {
            log.warn("OCSP revocation check failed: {}", e.getMessage());
            // Don't fail validation for OCSP check errors in development
        }
    }

    /**
     * Find the issuer certificate in the trust store
     */
    private X509Certificate findIssuerCert(X509Certificate certificate, KeyStore trustStore) throws Exception {
        try {
            String issuerDN = certificate.getIssuerX500Principal().getName();

            // Iterate through all certificates in the trust store
            java.util.Enumeration<String> aliases = trustStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();

                if (trustStore.isCertificateEntry(alias)) {
                    X509Certificate candidateCert = (X509Certificate) trustStore.getCertificate(alias);

                    if (candidateCert != null) {
                        String subjectDN = candidateCert.getSubjectX500Principal().getName();

                        // Check if this certificate's subject matches the issuer we're looking for
                        if (issuerDN.equals(subjectDN)) {
                            // Verify that this certificate actually signed the given certificate
                            try {
                                certificate.verify(candidateCert.getPublicKey());
                                return candidateCert;
                            } catch (Exception e) {
                                // This certificate didn't sign it, continue searching
                                log.debug("Certificate {} didn't verify with {}: {}", alias, issuerDN, e.getMessage());
                            }
                        }
                    }
                }
            }

            log.debug("Issuer certificate not found in trust store for: {}", issuerDN);
            return null;

        } catch (Exception e) {
            log.warn("Error finding issuer certificate: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Parse Authority Information Access extension for OCSP URLs
     */
    private List<String> parseAuthorityInformationAccess(byte[] extensionValue) throws Exception {
        List<String> urls = new ArrayList<>();
        try {
            org.bouncycastle.asn1.x509.AuthorityInformationAccess aia = org.bouncycastle.asn1.x509.AuthorityInformationAccess
                    .getInstance(
                            org.bouncycastle.asn1.ASN1Primitive.fromByteArray(extensionValue));

            for (org.bouncycastle.asn1.x509.AccessDescription accessDescription : aia.getAccessDescriptions()) {
                if (accessDescription.getAccessMethod()
                        .equals(org.bouncycastle.asn1.x509.AccessDescription.id_ad_ocsp)) {
                    org.bouncycastle.asn1.x509.GeneralName location = accessDescription.getAccessLocation();
                    if (location.getTagNo() == org.bouncycastle.asn1.x509.GeneralName.uniformResourceIdentifier) {
                        String url = ((org.bouncycastle.asn1.DERIA5String) location.getName()).getString();
                        urls.add(url);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Authority Information Access extension: {}", e.getMessage());
        }
        return urls;
    }

    /**
     * Get OCSP status for certificate with caching
     */
    private int getOCSPStatus(String ocspUrl, X509Certificate certificate, X509Certificate issuerCert) throws Exception {
        String cacheKey = ocspUrl + ":" + certificate.getSerialNumber();

        // Check cache first
        CachedOCSPResponse cached = ocspCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached OCSP response for: {}", cacheKey);
            return cached.status;
        }

        // Create OCSP request
        log.debug("Querying OCSP responder: {}", ocspUrl);

        // For simplicity, we'll use a basic HTTP POST to the OCSP responder
        // In production, you might want to use a more robust OCSP client library
        byte[] requestBytes = createOCSPRequest(certificate, issuerCert);

        URL url = URI.create(ocspUrl).toURL();
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/ocsp-request");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        try (java.io.OutputStream os = connection.getOutputStream()) {
            os.write(requestBytes);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("OCSP responder returned HTTP " + responseCode);
        }

        byte[] responseBytes;
        try (java.io.InputStream is = connection.getInputStream()) {
            responseBytes = is.readAllBytes();
        }

        int status = parseOCSPResponse(responseBytes);

        // Cache the response (cache for 1 hour or until next update, whichever is
        // sooner)
        // For simplicity, we'll cache for 1 hour
        Instant expiresAt = Instant.now().plusSeconds(3600);
        ocspCache.put(cacheKey, new CachedOCSPResponse(status, expiresAt));
        log.debug("Cached OCSP response for: {} until {}", cacheKey, expiresAt);

        return status;
    }

    /**
     * Create OCSP request for certificate status check
     */
    private byte[] createOCSPRequest(X509Certificate certificate, X509Certificate issuerCert) throws Exception {
        // Create OCSP request using Bouncy Castle
        CertificateID certId = new CertificateID(
                new JcaDigestCalculatorProviderBuilder().build()
                        .get(AlgorithmIdentifier.getInstance(org.bouncycastle.asn1.oiw.OIWObjectIdentifiers.idSHA1)),
                new X509CertificateHolder(issuerCert.getEncoded()),
                certificate.getSerialNumber());

        OCSPReqBuilder reqBuilder = new OCSPReqBuilder();
        reqBuilder.addRequest(certId);

        OCSPReq ocspReq = reqBuilder.build();

        return ocspReq.getEncoded();
    }

    /**
     * Parse OCSP response and return certificate status
     * Return values: 0 = good, 1 = revoked, 2 = unknown
     */
    private int parseOCSPResponse(byte[] responseBytes) throws Exception {
        try {
            OCSPResp ocspResp = new OCSPResp(responseBytes);

            if (ocspResp.getStatus() != 0) {
                // OCSP response status is not successful
                log.warn("OCSP response status: {}", ocspResp.getStatus());
                return 2; // Unknown
            }

            BasicOCSPResp basicResp = (BasicOCSPResp) ocspResp.getResponseObject();

            // Verify response signature if present
            // Note: In production, you should verify the OCSP response signature

            SingleResp[] responses = basicResp.getResponses();
            if (responses.length == 0) {
                return 2; // Unknown
            }

            SingleResp singleResp = responses[0];
            Object certStatus = singleResp.getCertStatus();

            if (certStatus == null) {
                // Good status
                return 0;
            } else if (certStatus instanceof RevokedStatus) {
                // Certificate is revoked
                return 1;
            } else {
                // Unknown status
                return 2;
            }

        } catch (Exception e) {
            log.warn("Failed to parse OCSP response: {}", e.getMessage());
            return 2; // Unknown on parsing error
        }
    }

    /**
     * Validate device-specific certificate fields
     */
    private void validateDeviceCertificateFields(X509Certificate certificate, String deviceId)
            throws CertificateException {
        // Check subject alternative name contains device ID
        Collection<List<?>> subjectAlternativeNames = certificate.getSubjectAlternativeNames();
        if (subjectAlternativeNames != null) {
            boolean deviceIdFound = subjectAlternativeNames.stream()
                    .anyMatch(san -> san.get(1).equals(deviceId));
            if (!deviceIdFound) {
                throw new CertificateException("Certificate does not contain device ID in subject alternative names");
            }
        }

        // Check extended key usage for client authentication
        List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        if (extendedKeyUsage == null || !extendedKeyUsage.contains("1.3.6.1.5.5.7.3.2")) {
            throw new CertificateException("Certificate does not have client authentication extended key usage");

    /**
     * Publish certificate validation event to audit service
     */
        }
    }

    /**
     * Parse PEM certificate
     */
    private X509Certificate parseCertificate(String certificatePem) throws CertificateException {
        try {
            String pem = certificatePem.replaceAll("\\n", "")
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "");

            byte[] decoded = Base64.getDecoder().decode(pem);
            return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(decoded));
        } catch (Exception e) {
            throw new CertificateException("Failed to parse certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Load trust store
     */
    private KeyStore loadTrustStore() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("truststore.jks")) {
            if (inputStream == null) {
                // Create empty trust store for development
                trustStore.load(null, null);
                log.warn("Trust store not found, using empty trust store");
            } else {
                trustStore.load(inputStream, trustStorePassword.toCharArray());
            }
        }
        return trustStore;
    }

    /**
     * Convert X509 certificate to PEM format
     */
    private String convertCertificateToPem(X509Certificate certificate) throws Exception {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
        byte[] certBytes = certificate.getEncoded();
        String encodedCert = new String(encoder.encode(certBytes));

        return "-----BEGIN CERTIFICATE-----\n" + encodedCert + "\n-----END CERTIFICATE-----\n";
    }

    /**
     * Publish certificate validation event to audit service
     */
    private void publishCertificateValidationEvent(String deviceId, String eventType, Map<String, Object> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"deviceId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    java.util.UUID.randomUUID(), eventType, deviceId, java.time.LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.AUDIT_EVENTS, deviceId, eventMessage);
            log.debug("Published certificate validation event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish certificate validation event for device {} event {}", deviceId, eventType, e);
        }
    }
}