package com.mayo.auth.service;

import com.mayo.auth.dto.*;
import com.mayo.auth.entity.Device;
import com.mayo.auth.repository.DeviceRepository;
import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for certificate synchronization operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateSyncService {

    private final CertificateAuthorityService certificateAuthorityService;
    private final DeviceRepository deviceRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${auth.certificate.sync.page-size:50}")
    private int defaultPageSize;

    /**
     * Get CA certificate and public key
     */
    public CaCertificateDto getCaCertificate() {
        log.info("Retrieving CA certificate");

        X509Certificate caCert = certificateAuthorityService.getCACertificate();

        // Convert certificate to PEM
        String certificatePem = convertCertificateToPem(caCert);

        // Extract public key
        String publicKeyPem = convertPublicKeyToPem(caCert.getPublicKey());

        // Audit the access
        auditCertificateAccess("CA_CERTIFICATE_ACCESS", null, "CA certificate retrieved");

        return CaCertificateDto.builder()
                .certificatePem(certificatePem)
                .publicKeyPem(publicKeyPem)
                .certificateSubject(caCert.getSubjectX500Principal().getName())
                .certificateIssuer(caCert.getIssuerX500Principal().getName())
                .validFrom(caCert.getNotBefore().toInstant())
                .validUntil(caCert.getNotAfter().toInstant())
                .build();
    }

    /**
     * Get paginated list of active device certificates
     */
    public DeviceCertificatesResponse getDeviceCertificates(int page, int size) {
        log.info("Retrieving device certificates - page: {}, size: {}", page, size);

        if (size <= 0 || size > 100) {
            size = defaultPageSize;
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Device> devicePage = deviceRepository.findAll(pageable);

        List<CertificateSyncDto> certificates = devicePage.getContent().stream()
                .filter(device -> device.getPublicKey() != null && !device.getPublicKey().trim().isEmpty())
                .map(this::convertDeviceToCertificateSyncDto)
                .collect(Collectors.toList());

        // Audit the access
        auditCertificateAccess("DEVICE_CERTIFICATES_LIST_ACCESS", null,
                String.format("Retrieved %d device certificates", certificates.size()));

        return DeviceCertificatesResponse.builder()
                .certificates(certificates)
                .currentPage(devicePage.getNumber())
                .totalPages(devicePage.getTotalPages())
                .totalElements(devicePage.getTotalElements())
                .hasNext(devicePage.hasNext())
                .hasPrevious(devicePage.hasPrevious())
                .build();
    }

    /**
     * Get specific device certificate
     */
    public CertificateSyncDto getDeviceCertificate(String deviceId) {
        log.info("Retrieving certificate for device: {}", deviceId);

        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty()) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }

        Device device = deviceOpt.get();
        if (device.getPublicKey() == null || device.getPublicKey().trim().isEmpty()) {
            throw new IllegalArgumentException("No certificate found for device: " + deviceId);
        }

        // Audit the access
        auditCertificateAccess("DEVICE_CERTIFICATE_ACCESS", deviceId, "Device certificate retrieved");

        return convertDeviceToCertificateSyncDto(device);
    }

    /**
     * Get certificate revocation list
     */
    public CertificateRevocationListDto getCertificateRevocationList() {
        log.info("Retrieving certificate revocation list");

        // For now, return an empty CRL since revocation management is not fully
        // implemented
        // In production, this would generate or retrieve an actual CRL

        List<RevokedCertificateDto> revokedCertificates = getRevokedCertificates();

        // Audit the access
        auditCertificateAccess("CRL_ACCESS", null, "Certificate revocation list retrieved");

        return CertificateRevocationListDto.builder()
                .crlPem(generateEmptyCrlPem())
                .thisUpdate(Instant.now())
                .nextUpdate(Instant.now().plusSeconds(3600)) // 1 hour validity
                .revokedCertificates(revokedCertificates)
                .version(1)
                .build();
    }

    /**
     * Get certificate updates since timestamp
     */
    public CertificateUpdatesDto getCertificateUpdates(Instant since) {
        log.info("Retrieving certificate updates since: {}", since);

        Instant currentTimestamp = Instant.now();

        // Get devices updated since the timestamp
        List<Device> updatedDevices = deviceRepository.findAll().stream()
                .filter(device -> {
                    LocalDateTime updateTime = device.getLastCertificateUpdate() != null
                            ? device.getLastCertificateUpdate()
                            : device.getRegisteredAt();
                    return updateTime.toInstant(java.time.ZoneOffset.UTC).isAfter(since);
                })
                .filter(device -> device.getPublicKey() != null && !device.getPublicKey().trim().isEmpty())
                .collect(Collectors.toList());

        List<CertificateSyncDto> newCertificates = updatedDevices.stream()
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .map(this::convertDeviceToCertificateSyncDto)
                .collect(Collectors.toList());

        List<CertificateSyncDto> updatedCertificates = updatedDevices.stream()
                .filter(device -> device.getStatus() != DeviceStatus.ACTIVE)
                .map(this::convertDeviceToCertificateSyncDto)
                .collect(Collectors.toList());

        List<RevokedCertificateDto> revokedCertificates = getRevokedCertificatesSince(since);

        // Audit the access
        auditCertificateAccess("CERTIFICATE_UPDATES_ACCESS", null,
                String.format("Retrieved updates since %s: %d new, %d updated, %d revoked",
                        since, newCertificates.size(), updatedCertificates.size(), revokedCertificates.size()));

        return CertificateUpdatesDto.builder()
                .since(since)
                .currentTimestamp(currentTimestamp)
                .newCertificates(newCertificates)
                .updatedCertificates(updatedCertificates)
                .revokedCertificates(revokedCertificates)
                .hasMoreUpdates(false) // For simplicity, assume all updates fit in one response
                .build();
    }

    /**
     * Convert Device entity to CertificateSyncDto
     */
    private CertificateSyncDto convertDeviceToCertificateSyncDto(Device device) {
        // Parse certificate to extract details
        String certificateSubject = "Unknown";
        String certificateIssuer = "Unknown";
        Instant validFrom = null;
        Instant validUntil = null;

        try {
            if (device.getPublicKey() != null && !device.getPublicKey().trim().isEmpty()) {
                // Assuming the publicKey field contains the certificate PEM
                X509Certificate cert = parseCertificateFromPem(device.getPublicKey());
                certificateSubject = cert.getSubjectX500Principal().getName();
                certificateIssuer = cert.getIssuerX500Principal().getName();
                validFrom = cert.getNotBefore().toInstant();
                validUntil = cert.getNotAfter().toInstant();
            }
        } catch (Exception e) {
            log.warn("Failed to parse certificate for device {}: {}", device.getDeviceId(), e.getMessage());
        }

        LocalDateTime lastUpdate = device.getLastCertificateUpdate() != null ? device.getLastCertificateUpdate()
                : device.getRegisteredAt();

        return CertificateSyncDto.builder()
                .deviceId(device.getDeviceId())
                .certificatePem(device.getPublicKey())
                .certificateSubject(certificateSubject)
                .certificateIssuer(certificateIssuer)
                .validFrom(validFrom)
                .validUntil(validUntil)
                .lastUpdated(lastUpdate.toInstant(java.time.ZoneOffset.UTC))
                .status(device.getStatus().name())
                .build();
    }

    /**
     * Get list of revoked certificates (placeholder implementation)
     */
    private List<RevokedCertificateDto> getRevokedCertificates() {
        // In a real implementation, this would query a revocation database
        // For now, return empty list
        return List.of();
    }

    /**
     * Get revoked certificates since timestamp (placeholder implementation)
     */
    private List<RevokedCertificateDto> getRevokedCertificatesSince(Instant since) {
        // In a real implementation, this would query revocation records since timestamp
        return List.of();
    }

    /**
     * Generate empty CRL in PEM format (placeholder)
     */
    private String generateEmptyCrlPem() {
        // In production, this would generate a proper CRL
        return "-----BEGIN X509 CRL-----\n-----END X509 CRL-----\n";
    }

    /**
     * Parse certificate from PEM string
     */
    private X509Certificate parseCertificateFromPem(String pem) throws Exception {
        String pemContent = pem.replaceAll("\\n", "")
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "");

        byte[] decoded = Base64.getDecoder().decode(pemContent);
        return (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(decoded));
    }

    /**
     * Convert certificate to PEM format
     */
    private String convertCertificateToPem(X509Certificate certificate) {
        try {
            Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
            byte[] certBytes = certificate.getEncoded();
            String encodedCert = new String(encoder.encode(certBytes));
            return "-----BEGIN CERTIFICATE-----\n" + encodedCert + "\n-----END CERTIFICATE-----\n";
        } catch (Exception e) {
            log.error("Failed to convert certificate to PEM", e);
            return null;
        }
    }

    /**
     * Convert public key to PEM format
     */
    private String convertPublicKeyToPem(java.security.PublicKey publicKey) {
        try {
            Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());
            byte[] keyBytes = publicKey.getEncoded();
            String encodedKey = new String(encoder.encode(keyBytes));
            return "-----BEGIN PUBLIC KEY-----\n" + encodedKey + "\n-----END PUBLIC KEY-----\n";
        } catch (Exception e) {
            log.error("Failed to convert public key to PEM", e);
            return null;
        }
    }

    /**
     * Audit certificate access
     */
    private void auditCertificateAccess(String eventType, String deviceId, String details) {
        try {
            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"deviceId\":\"%s\",\"timestamp\":\"%s\",\"details\":\"%s\"}",
                    java.util.UUID.randomUUID(), eventType, deviceId != null ? deviceId : "SYSTEM",
                    Instant.now(), details);
            kafkaTemplate.send(Topics.AUDIT_EVENTS, deviceId != null ? deviceId : "SYSTEM", eventMessage);
            log.debug("Audited certificate access: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to audit certificate access for device {}: {}", deviceId, e);
        }
    }
}