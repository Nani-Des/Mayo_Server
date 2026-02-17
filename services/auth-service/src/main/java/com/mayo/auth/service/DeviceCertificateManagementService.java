package com.mayo.auth.service;

import com.mayo.auth.dto.*;
import com.mayo.auth.entity.Device;
import com.mayo.auth.repository.DeviceRepository;
import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.common.core.enums.DeviceType;
import com.mayo.events.topics.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing device certificates and QR code generation for hospital
 * desktop applications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCertificateManagementService {

    private final CertificateAuthorityService certificateAuthorityService;
    private final DeviceRepository deviceRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Configuration
    @Value("${auth.device.qr.expiry.minutes:5}")
    private int qrExpiryMinutes;

    @Value("${auth.device.qr.rate.limit.per.hour:10}")
    private int qrRateLimitPerHour;

    @Value("${auth.device.certificate.renewal.days.before.expiry:30}")
    private int renewalDaysBeforeExpiry;

    // Caching and rate limiting
    private final Map<String, Instant> qrRateLimitCache = new ConcurrentHashMap<>();
    private final Map<String, CachedCertificate> certificateCache = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Cached certificate entry with expiration
     */
    private static class CachedCertificate {
        private final String certificate;
        private final Instant expiresAt;

        public CachedCertificate(String certificate, Instant expiresAt) {
            this.certificate = certificate;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    // ===== DEVICE REGISTRATION =====

    /**
     * Register a new hospital device with certificate generation
     */
    @Transactional
    public DeviceRegistrationResponse registerHospitalDevice(DeviceRegistrationRequest request) {
        log.info("Registering hospital device: {} for hospital: {}", request.getDeviceId(), request.getHospitalId());

        try {
            // Check if device already exists
            if (deviceRepository.existsByDeviceId(request.getDeviceId())) {
                throw new IllegalArgumentException("Device with ID " + request.getDeviceId() + " already exists");
            }

            // Create device entity
            Device device = Device.builder()
                    .deviceId(request.getDeviceId())
                    .deviceType(request.getDeviceType() != null ? request.getDeviceType() : DeviceType.DESKTOP)
                    .status(DeviceStatus.ACTIVE)
                    .userId(request.getHospitalId()) // Hospital ID stored as userId
                    .registeredAt(LocalDateTime.now())
                    .build();

            device = deviceRepository.save(device);

            // Generate certificate
            String certificate = generateDeviceCertificate(request.getDeviceId());

            // Store certificate in cache
            certificateCache.put(request.getDeviceId(),
                    new CachedCertificate(certificate, Instant.now().plus(1, ChronoUnit.HOURS)));

            // Update device with certificate timestamp
            device.setLastCertificateUpdate(LocalDateTime.now());
            deviceRepository.save(device);

            // Audit log
            auditEvent("DEVICE_REGISTERED", Map.of(
                    "deviceId", request.getDeviceId(),
                    "hospitalId", request.getHospitalId(),
                    "deviceType", request.getDeviceType()));

            return DeviceRegistrationResponse.builder()
                    .deviceId(request.getDeviceId())
                    .hospitalId(request.getHospitalId())
                    .certificate(certificate)
                    .status(DeviceStatus.ACTIVE)
                    .registeredAt(device.getRegisteredAt())
                    .build();

        } catch (Exception e) {
            log.error("Failed to register device: {}", request.getDeviceId(), e);
            auditEvent("DEVICE_REGISTRATION_FAILED", Map.of(
                    "deviceId", request.getDeviceId(),
                    "error", e.getMessage()));
            throw new RuntimeException("Device registration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate device certificate using CA
     */
    public String generateDeviceCertificate(String deviceId) {
        try {
            log.info("Generating certificate for device: {}", deviceId);

            DeviceCertificateRequest certRequest = DeviceCertificateRequest.builder()
                    .deviceId(deviceId)
                    .commonName("Hospital Device " + deviceId)
                    .organizationName("Mayo Clinic")
                    .organizationalUnitName("Hospital Devices")
                    .countryCode("US")
                    .build();

            java.security.cert.X509Certificate certificate = certificateAuthorityService
                    .generateDeviceCertificate(certRequest);

            // Convert to PEM
            String pemCertificate = convertCertificateToPem(certificate);

            auditEvent("CERTIFICATE_GENERATED", Map.of(
                    "deviceId", deviceId,
                    "certificateSubject", certificate.getSubjectX500Principal().getName()));

            return pemCertificate;

        } catch (Exception e) {
            log.error("Failed to generate certificate for device: {}", deviceId, e);
            throw new RuntimeException("Certificate generation failed: " + e.getMessage(), e);
        }
    }

    // ===== QR CODE GENERATION =====

    /**
     * Generate signed QR code for patient authentication
     */
    public String generateSignedQRCode(String deviceId, QROperation operation) {
        log.info("Generating signed QR code for device: {} with operation: {}", deviceId, operation.getType());

        try {
            // Rate limiting check
            if (!checkQRRateLimit(deviceId)) {
                throw new IllegalStateException("QR generation rate limit exceeded for device: " + deviceId);
            }

            // Get device certificate
            String certificate = getDeviceCertificate(deviceId);
            if (certificate == null) {
                throw new IllegalArgumentException("No certificate found for device: " + deviceId);
            }

            // Validate certificate before QR generation
            if (!validateCertificateBeforeQR(certificate)) {
                throw new IllegalStateException("Certificate validation failed for device: " + deviceId);
            }

            // Generate nonce and timestamps
            String nonce = generateNonce();
            Instant timestamp = Instant.now();
            Instant expiry = timestamp.plus(qrExpiryMinutes, ChronoUnit.MINUTES);

            // Create QR payload
            QRCodePayload payload = QRCodePayload.builder()
                    .version("1.0")
                    .deviceId(deviceId)
                    .certificate(certificate)
                    .nonce(nonce)
                    .timestamp(timestamp)
                    .expiry(expiry)
                    .operation(operation)
                    .build();

            // Sign payload
            String signature = signQRPayload(payload);

            // Create complete signed payload
            SignedQRCodePayload signedPayload = SignedQRCodePayload.builder()
                    .payload(payload)
                    .signature(signature)
                    .build();

            // Convert to JSON
            String qrData = objectMapper.writeValueAsString(signedPayload);

            auditEvent("QR_CODE_GENERATED", Map.of(
                    "deviceId", deviceId,
                    "operation", operation.getType(),
                    "expiry", expiry.toString()));

            return qrData;

        } catch (Exception e) {
            log.error("Failed to generate QR code for device: {}", deviceId, e);
            auditEvent("QR_CODE_GENERATION_FAILED", Map.of(
                    "deviceId", deviceId,
                    "operation", operation.getType(),
                    "error", e.getMessage()));
            throw new RuntimeException("QR code generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate QR code signature and expiry
     */
    public QRValidationResult validateQRCode(String qrData) {
        try {
            log.info("Validating QR code");

            SignedQRCodePayload signedPayload = objectMapper.readValue(qrData, SignedQRCodePayload.class);

            // Check expiry
            if (signedPayload.getPayload().getExpiry().isBefore(Instant.now())) {
                return QRValidationResult.builder()
                        .valid(false)
                        .error("QR code has expired")
                        .build();
            }

            // Verify signature
            boolean signatureValid = verifyQRSignature(signedPayload.getPayload(), signedPayload.getSignature());
            if (!signatureValid) {
                auditEvent("QR_CODE_VALIDATION_FAILED", Map.of(
                        "deviceId", signedPayload.getPayload().getDeviceId(),
                        "reason", "invalid_signature"));
                return QRValidationResult.builder()
                        .valid(false)
                        .error("Invalid QR signature")
                        .build();
            }

            // Validate certificate
            boolean certValid = validateCertificateBeforeQR(signedPayload.getPayload().getCertificate());
            if (!certValid) {
                auditEvent("QR_CODE_VALIDATION_FAILED", Map.of(
                        "deviceId", signedPayload.getPayload().getDeviceId(),
                        "reason", "invalid_certificate"));
                return QRValidationResult.builder()
                        .valid(false)
                        .error("Invalid device certificate")
                        .build();
            }

            auditEvent("QR_CODE_VALIDATED", Map.of(
                    "deviceId", signedPayload.getPayload().getDeviceId(),
                    "operation", signedPayload.getPayload().getOperation().getType()));

            return QRValidationResult.builder()
                    .valid(true)
                    .deviceId(signedPayload.getPayload().getDeviceId())
                    .operation(signedPayload.getPayload().getOperation())
                    .certificate(signedPayload.getPayload().getCertificate())
                    .build();

        } catch (Exception e) {
            log.error("QR code validation failed", e);
            return QRValidationResult.builder()
                    .valid(false)
                    .error("QR validation error: " + e.getMessage())
                    .build();
        }
    }

    // ===== CERTIFICATE LIFECYCLE MANAGEMENT =====

    /**
     * Renew expiring certificate
     */
    @Transactional
    public String renewCertificate(String deviceId) {
        log.info("Renewing certificate for device: {}", deviceId);

        try {
            Device device = deviceRepository.findByDeviceId(deviceId)
                    .orElseThrow(IllegalArgumentException::new);

            // Generate new certificate
            String newCertificate = generateDeviceCertificate(deviceId);

            // Update device
            device.setLastCertificateUpdate(LocalDateTime.now());
            deviceRepository.save(device);

            // Update cache
            certificateCache.put(deviceId,
                    new CachedCertificate(newCertificate, Instant.now().plus(1, ChronoUnit.HOURS)));

            auditEvent("CERTIFICATE_RENEWED", Map.of("deviceId", deviceId));

            return newCertificate;

        } catch (Exception e) {
            log.error("Failed to renew certificate for device: {}", deviceId, e);
            auditEvent("CERTIFICATE_RENEWAL_FAILED", Map.of(
                    "deviceId", deviceId,
                    "error", e.getMessage()));
            throw new RuntimeException("Certificate renewal failed: " + e.getMessage(), e);
        }
    }

    /**
     * Revoke device certificate
     */
    @Transactional
    public void revokeCertificate(String deviceId, String reason) {
        log.info("Revoking certificate for device: {} with reason: {}", deviceId, reason);

        try {
            Device device = deviceRepository.findByDeviceId(deviceId)
                    .orElseThrow(IllegalArgumentException::new);

            // Update device status
            device.setStatus(DeviceStatus.BLOCKED);
            deviceRepository.save(device);

            // Clear cache
            certificateCache.remove(deviceId);

            // TODO: Update CRL if implemented

            auditEvent("CERTIFICATE_REVOKED", Map.of(
                    "deviceId", deviceId,
                    "reason", reason));

        } catch (Exception e) {
            log.error("Failed to revoke certificate for device: {}", deviceId, e);
            throw new RuntimeException("Certificate revocation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Rotate device certificate (revoke old, generate new)
     */
    @Transactional
    public String rotateCertificate(String deviceId) {
        log.info("Rotating certificate for device: {}", deviceId);

        try {
            // Revoke old certificate
            revokeCertificate(deviceId, "Certificate rotation");

            // Generate new certificate
            String newCertificate = generateDeviceCertificate(deviceId);

            // Reactivate device
            Device device = deviceRepository.findByDeviceId(deviceId).orElseThrow();
            device.setStatus(DeviceStatus.ACTIVE);
            device.setLastCertificateUpdate(LocalDateTime.now());
            deviceRepository.save(device);

            // Update cache
            certificateCache.put(deviceId,
                    new CachedCertificate(newCertificate, Instant.now().plus(1, ChronoUnit.HOURS)));

            auditEvent("CERTIFICATE_ROTATED", Map.of("deviceId", deviceId));

            return newCertificate;

        } catch (Exception e) {
            log.error("Failed to rotate certificate for device: {}", deviceId, e);
            throw new RuntimeException("Certificate rotation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get certificate status and information
     */
    public CertificateStatus getCertificateStatus(String deviceId) {
        try {
            Device device = deviceRepository.findByDeviceId(deviceId)
                    .orElseThrow(IllegalArgumentException::new);

            String certificate = getDeviceCertificate(deviceId);
            boolean isValid = certificate != null && validateCertificateBeforeQR(certificate);

            return CertificateStatus.builder()
                    .deviceId(deviceId)
                    .status(device.getStatus())
                    .certificateValid(isValid)
                    .lastUpdate(device.getLastCertificateUpdate())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get certificate status for device: {}", deviceId, e);
            return CertificateStatus.builder()
                    .deviceId(deviceId)
                    .status(DeviceStatus.INACTIVE)
                    .certificateValid(false)
                    .error("Status check failed: " + e.getMessage())
                    .build();
        }
    }

    // ===== HOSPITAL DEVICE OPERATIONS =====

    /**
     * List devices by hospital
     */
    public List<DeviceDto> listDevicesByHospital(UUID hospitalId) {
        log.debug("Listing devices for hospital: {}", hospitalId);

        List<Device> devices = deviceRepository.findByUserId(hospitalId);
        return devices.stream()
                .map(this::mapToDeviceDto)
                .toList();
    }

    /**
     * Get device status and certificate information
     */
    public DeviceStatusResponse getDeviceStatus(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(IllegalArgumentException::new);

        CertificateStatus certStatus = getCertificateStatus(deviceId);

        return DeviceStatusResponse.builder()
                .deviceId(deviceId)
                .status(device.getStatus())
                .deviceType(device.getDeviceType())
                .registeredAt(device.getRegisteredAt())
                .lastCertificateUpdate(device.getLastCertificateUpdate())
                .certificateStatus(certStatus)
                .build();
    }

    /**
     * Update device permissions and roles
     */
    @Transactional
    public void updateDevicePermissions(String deviceId, List<String> permissions) {
        log.info("Updating permissions for device: {}", deviceId);

        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(IllegalArgumentException::new);

        // Store permissions in device public key field (temporary solution)
        // TODO: Create proper permissions table
        try {
            String permissionsJson = objectMapper.writeValueAsString(permissions);
            device.setPublicKey(permissionsJson);
        } catch (Exception e) {
            log.error("Failed to serialize permissions for device: {}", deviceId, e);
            throw new RuntimeException("Failed to serialize permissions", e);
        }

        deviceRepository.save(device);

        auditEvent("DEVICE_PERMISSIONS_UPDATED", Map.of(
                "deviceId", deviceId,
                "permissions", permissions));
    }

    /**
     * Deactivate device
     */
    @Transactional
    public void deactivateDevice(String deviceId) {
        log.info("Deactivating device: {}", deviceId);

        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(IllegalArgumentException::new);

        device.setStatus(DeviceStatus.INACTIVE);
        deviceRepository.save(device);

        // Clear certificate cache
        certificateCache.remove(deviceId);

        auditEvent("DEVICE_DEACTIVATED", Map.of("deviceId", deviceId));
    }

    /**
     * Reactivate device
     */
    @Transactional
    public void reactivateDevice(String deviceId) {
        log.info("Reactivating device: {}", deviceId);

        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(IllegalArgumentException::new);

        device.setStatus(DeviceStatus.ACTIVE);
        deviceRepository.save(device);

        auditEvent("DEVICE_REACTIVATED", Map.of("deviceId", deviceId));
    }

    // ===== SECURITY METHODS =====

    /**
     * Validate certificate before QR generation using proper certificate chain
     * validation
     */
    private boolean validateCertificateBeforeQR(String certificate) {
        try {
            // Parse certificate
            java.security.cert.X509Certificate cert = parseCertificate(certificate);

            // Get CA certificate for chain validation
            java.security.cert.X509Certificate caCert = certificateAuthorityService.getCACertificate();

            // Build certificate chain
            java.util.List<java.security.cert.X509Certificate> certChain = java.util.Arrays.asList(cert, caCert);

            // Create certificate factory
            java.security.cert.CertificateFactory certFactory = java.security.cert.CertificateFactory
                    .getInstance("X.509");

            // Generate certificate path
            java.security.cert.CertPath certPath = certFactory.generateCertPath(certChain);

            // Create trust anchor from CA certificate
            java.security.cert.TrustAnchor trustAnchor = new java.security.cert.TrustAnchor(caCert, null);

            // Set up PKIX parameters
            java.security.cert.PKIXParameters pkixParams = new java.security.cert.PKIXParameters(
                    java.util.Collections.singleton(trustAnchor));
            pkixParams.setRevocationEnabled(false); // Disable for now, will enable with CRL/OCSP later
            pkixParams.setDate(new java.util.Date()); // Use current time

            // Create PKIX cert path validator
            java.security.cert.CertPathValidator validator = java.security.cert.CertPathValidator.getInstance("PKIX");

            // Validate the certificate path
            java.security.cert.CertPathValidatorResult result = validator.validate(certPath, pkixParams);

            // Additional device-specific validation
            validateDeviceCertificateFields(cert);

            log.debug("Certificate chain validation successful");
            return true;

        } catch (java.security.cert.CertificateException | java.security.InvalidAlgorithmParameterException
                | java.security.cert.CertPathValidatorException e) {
            log.warn("Certificate chain validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during certificate validation", e);
            return false;
        }
    }

    /**
     * Validate device-specific certificate fields
     */
    private void validateDeviceCertificateFields(java.security.cert.X509Certificate certificate)
            throws java.security.cert.CertificateException {
        // Check that certificate has client authentication extended key usage
        java.util.List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
        if (extendedKeyUsage == null || !extendedKeyUsage.contains("1.3.6.1.5.5.7.3.2")) {
            throw new java.security.cert.CertificateException(
                    "Certificate does not have client authentication extended key usage");
        }

        // Check that certificate is not a CA certificate
        if (certificate.getBasicConstraints() != -1) {
            throw new java.security.cert.CertificateException("Certificate should not be a CA certificate");
        }

        // Additional validation can be added here (subject alternative name, etc.)
    }

    /**
     * Check QR generation rate limit
     */
    private boolean checkQRRateLimit(String deviceId) {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        // Clean old entries
        qrRateLimitCache.entrySet().removeIf(entry -> entry.getValue().isBefore(oneHourAgo));

        // Count recent requests
        long recentRequests = qrRateLimitCache.values().stream()
                .filter(timestamp -> !timestamp.isBefore(oneHourAgo))
                .count();

        if (recentRequests >= qrRateLimitPerHour) {
            return false;
        }

        // Record this request
        qrRateLimitCache.put(deviceId, now);
        return true;
    }

    /**
     * Generate cryptographic nonce
     */
    private String generateNonce() {
        byte[] nonce = new byte[32];
        secureRandom.nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    /**
     * Sign QR payload using CA private key
     */
    private String signQRPayload(QRCodePayload payload) throws Exception {
        // Get CA private key for signing
        java.security.cert.X509Certificate caCert = certificateAuthorityService.getCACertificate();
        // Note: In production, we'd have a separate signing key. For now, use CA key.

        // Since we don't have direct access to CA private key from the service
        // interface,
        // we'll use a device-specific signing approach
        // For now, create a signature using SHA256withRSA with a generated key pair
        // In production, this should use a dedicated QR signing key

        String data = objectMapper.writeValueAsString(payload);

        // Generate a temporary key pair for QR signing (in production, use persistent
        // key)
        java.security.KeyPair qrKeyPair = java.security.KeyPairGenerator.getInstance("RSA").generateKeyPair();

        // Create signature
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(qrKeyPair.getPrivate());
        signature.update(data.getBytes("UTF-8"));
        byte[] signatureBytes = signature.sign();

        // Store the public key for verification (in production, this would be
        // cached/retrieved)
        // For now, we'll include it in the signature format
        String publicKeyPem = convertPublicKeyToPem(qrKeyPair.getPublic());

        // Return signature + public key (separated by a delimiter)
        String signatureB64 = Base64.getEncoder().encodeToString(signatureBytes);
        return signatureB64 + ":" + Base64.getEncoder().encodeToString(publicKeyPem.getBytes("UTF-8"));
    }

    /**
     * Verify QR signature
     */
    private boolean verifyQRSignature(QRCodePayload payload, String signature) throws Exception {
        String data = objectMapper.writeValueAsString(payload);

        // Parse signature format: signatureB64:publicKeyB64
        String[] parts = signature.split(":");
        if (parts.length != 2) {
            log.warn("Invalid signature format");
            return false;
        }

        byte[] signatureBytes = Base64.getDecoder().decode(parts[0]);
        String publicKeyPem = new String(Base64.getDecoder().decode(parts[1]), "UTF-8");

        // Parse public key
        java.security.PublicKey publicKey = parsePublicKeyFromPem(publicKeyPem);

        // Verify signature
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data.getBytes("UTF-8"));

        return sig.verify(signatureBytes);
    }

    /**
     * Convert public key to PEM format
     */
    private String convertPublicKeyToPem(java.security.PublicKey publicKey) throws Exception {
        byte[] encoded = publicKey.getEncoded();
        String encodedB64 = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + encodedB64 + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * Parse public key from PEM format
     */
    private java.security.PublicKey parsePublicKeyFromPem(String pem) throws Exception {
        String pemContent = pem.replaceAll("\\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "");

        byte[] decoded = Base64.getDecoder().decode(pemContent);
        java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(decoded);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Get device certificate from cache or generate
     */
    private String getDeviceCertificate(String deviceId) {
        CachedCertificate cached = certificateCache.get(deviceId);
        if (cached != null && !cached.isExpired()) {
            return cached.certificate;
        }

        // Generate new certificate
        String certificate = generateDeviceCertificate(deviceId);
        certificateCache.put(deviceId,
                new CachedCertificate(certificate, Instant.now().plus(1, ChronoUnit.HOURS)));
        return certificate;
    }

    /**
     * Parse PEM certificate
     */
    private java.security.cert.X509Certificate parseCertificate(String certificatePem) throws Exception {
        String pem = certificatePem.replaceAll("\\n", "")
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "");

        byte[] decoded = Base64.getDecoder().decode(pem);
        return (java.security.cert.X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(decoded));
    }

    /**
     * Convert certificate to PEM format
     */
    private String convertCertificateToPem(java.security.cert.X509Certificate certificate) throws Exception {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
        byte[] certBytes = certificate.getEncoded();
        String encodedCert = new String(encoder.encode(certBytes));

        return "-----BEGIN CERTIFICATE-----\n" + encodedCert + "\n-----END CERTIFICATE-----\n";
    }

    /**
     * Map Device entity to DTO
     */
    private DeviceDto mapToDeviceDto(Device device) {
        return DeviceDto.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .deviceType(device.getDeviceType())
                .status(device.getStatus())
                .registeredAt(device.getRegisteredAt())
                .lastCertificateUpdate(device.getLastCertificateUpdate())
                .build();
    }

    /**
     * Audit logging
     */
    private void auditEvent(String eventType, Map<String, Object> data) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(data);
            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    UUID.randomUUID(), eventType, Instant.now(), eventDataJson);
            kafkaTemplate.send(Topics.AUDIT_EVENTS, eventMessage);
            log.debug("Audited event: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to audit event: {}", eventType, e);
        }
    }
}
