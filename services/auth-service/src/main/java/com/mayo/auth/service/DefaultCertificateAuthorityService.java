package com.mayo.auth.service;

import com.mayo.auth.dto.DeviceCertificateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

/**
 * Default implementation of Certificate Authority Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultCertificateAuthorityService implements CertificateAuthorityService {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Value("${auth.certificate.ca.key.path:classpath:certs/ca/ca.key}")
    private String caKeyPath;

    @Value("${auth.certificate.ca.cert.path:classpath:certs/ca/ca.crt}")
    private String caCertPath;

    @Value("${auth.certificate.ca.key.password:}")
    private String caKeyPassword;

    private volatile X509Certificate caCertificate;
    private volatile PrivateKey caPrivateKey;
    private volatile boolean initialized = false;

    /**
     * Generate a device certificate signed by the CA
     */
    @Override
    public X509Certificate generateDeviceCertificate(DeviceCertificateRequest request) {
        if (!initialized) {
            initialize();
        }

        try {
            log.info("Generating certificate for device: {}", request.getDeviceId());

            // Generate key pair for the device
            KeyPair deviceKeyPair = generateKeyPair();

            // Build certificate
            X509Certificate deviceCertificate = buildDeviceCertificate(request, deviceKeyPair.getPublic());

            log.info("Certificate generated successfully for device: {}", request.getDeviceId());
            return deviceCertificate;

        } catch (Exception e) {
            log.error("Failed to generate certificate for device: {}", request.getDeviceId(), e);
            throw new RuntimeException("Certificate generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public X509Certificate getCACertificate() {
        if (!initialized) {
            initialize();
        }
        return caCertificate;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Initialize the CA service
     */
    private synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            loadCaCredentials();
            initialized = true;
            log.info("Certificate Authority service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Certificate Authority service", e);
            throw new RuntimeException("CA initialization failed", e);
        }
    }

    /**
     * Load CA certificate and private key
     */
    private synchronized void loadCaCredentials() throws Exception {
        if (caCertificate != null && caPrivateKey != null) {
            return;
        }

        log.info("Loading CA credentials from: {} and {}", caCertPath, caKeyPath);

        try {
            // Try to load existing CA credentials
            if (tryLoadExistingCaCredentials()) {
                log.info("Successfully loaded existing CA credentials");
                return;
            }

            // If loading fails, generate self-signed CA for development
            log.warn("CA credentials not found or invalid, generating self-signed CA for development");
            generateSelfSignedCa();

        } catch (Exception e) {
            log.error("Failed to load CA credentials", e);
            throw new RuntimeException("CA credentials loading failed", e);
        }
    }

    /**
     * Try to load existing CA credentials from files
     */
    private boolean tryLoadExistingCaCredentials() throws Exception {
        try {
            // Load CA certificate
            ClassPathResource caCertResource = new ClassPathResource(caCertPath.replace("classpath:", ""));
            if (caCertResource.exists()) {
                try (InputStream certStream = caCertResource.getInputStream()) {
                    caCertificate = parseCaCertificate(certStream);
                }
            } else {
                return false;
            }

            // Load CA private key
            ClassPathResource caKeyResource = new ClassPathResource(caKeyPath.replace("classpath:", ""));
            if (caKeyResource.exists()) {
                try (InputStream keyStream = caKeyResource.getInputStream()) {
                    caPrivateKey = parseCaPrivateKey(keyStream, caKeyPassword);
                }
            } else {
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to load existing CA credentials: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate self-signed CA certificate for development
     */
    private void generateSelfSignedCa() throws Exception {
        log.info("Generating self-signed CA certificate for development");

        // Generate CA key pair
        KeyPair caKeyPair = generateKeyPair();
        caPrivateKey = caKeyPair.getPrivate();

        // CA certificate details
        Instant now = Instant.now();
        BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());

        X500Name caSubject = new X500Name("CN=Mayo EMR CA, O=Mayo Clinic, OU=EMR, C=GH");
        X500Name issuer = caSubject; // Self-signed

        // Build CA certificate
        X509v3CertificateBuilder caCertBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                Date.from(now),
                Date.from(now.plus(10, ChronoUnit.YEARS)), // 10 years validity
                caSubject,
                caKeyPair.getPublic());

        // Add CA extensions
        caCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        caCertBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        // Sign CA certificate
        ContentSigner caSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(caPrivateKey);
        X509CertificateHolder caCertHolder = caCertBuilder.build(caSigner);

        // Convert to X509Certificate
        caCertificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(caCertHolder);

        log.info("Self-signed CA certificate generated successfully");
    }

    /**
     * Generate RSA key pair for device
     */
    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    /**
     * Build device certificate with proper extensions
     */
    private X509Certificate buildDeviceCertificate(DeviceCertificateRequest request, PublicKey publicKey)
            throws Exception {
        // Certificate validity
        Instant notBefore = request.getNotBefore() != null ? request.getNotBefore() : Instant.now();
        Instant notAfter = request.getNotAfter() != null ? request.getNotAfter() : notBefore.plus(365, ChronoUnit.DAYS);

        // Generate serial number
        BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());

        // Subject and issuer
        X500Name subject = buildSubjectName(request);
        X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());

        // Build certificate
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                Date.from(notBefore),
                Date.from(notAfter),
                subject,
                publicKey);

        // Add extensions
        addCertificateExtensions(certBuilder, request);

        // Sign certificate
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(caPrivateKey);
        X509CertificateHolder certHolder = certBuilder.build(signer);

        // Convert to X509Certificate
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
    }

    /**
     * Build X.500 subject name from request
     */
    private X500Name buildSubjectName(DeviceCertificateRequest request) {
        StringBuilder subjectBuilder = new StringBuilder();
        subjectBuilder.append("CN=").append(request.getCommonName());

        if (request.getOrganizationName() != null) {
            subjectBuilder.append(",O=").append(request.getOrganizationName());
        }
        if (request.getOrganizationalUnitName() != null) {
            subjectBuilder.append(",OU=").append(request.getOrganizationalUnitName());
        }
        if (request.getCountryCode() != null) {
            subjectBuilder.append(",C=").append(request.getCountryCode());
        }
        if (request.getStateOrProvinceName() != null) {
            subjectBuilder.append(",ST=").append(request.getStateOrProvinceName());
        }
        if (request.getLocalityName() != null) {
            subjectBuilder.append(",L=").append(request.getLocalityName());
        }

        return new X500Name(subjectBuilder.toString());
    }

    /**
     * Add certificate extensions for device authentication
     */
    private void addCertificateExtensions(X509v3CertificateBuilder certBuilder, DeviceCertificateRequest request)
            throws Exception {
        // Subject Alternative Name - include device ID
        GeneralNames subjectAltNames = new GeneralNames(
                new GeneralName(GeneralName.dNSName, request.getDeviceId()));
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

        // Extended Key Usage - client authentication
        KeyPurposeId[] keyPurposes = { KeyPurposeId.id_kp_clientAuth };
        certBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(keyPurposes));

        // Key Usage - digital signature and key encipherment
        certBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        // Basic Constraints - not a CA
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        // CRL Distribution Points - for CRL checking
        GeneralName crlName = new GeneralName(GeneralName.uniformResourceIdentifier,
                "http://localhost:8080/api/auth/crl"); // Placeholder URL
        GeneralNames crlGeneralNames = new GeneralNames(crlName);
        DistributionPointName crlDistPointName = new DistributionPointName(
                DistributionPointName.FULL_NAME, crlGeneralNames);
        DistributionPoint crlDistPoint = new DistributionPoint(crlDistPointName, null, null);
        CRLDistPoint crlDistPoints = new CRLDistPoint(new DistributionPoint[] { crlDistPoint });
        certBuilder.addExtension(Extension.cRLDistributionPoints, false, crlDistPoints);

        // Authority Information Access - for OCSP (placeholder - OCSP implementation
        // incomplete)
        // TODO: Add proper AIA extension when OCSP is fully implemented
    }

    /**
     * Parse CA certificate from input stream
     * Supports both PEM and DER formats
     */
    private X509Certificate parseCaCertificate(InputStream certStream) throws Exception {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

            // Read all bytes from stream
            byte[] certBytes = certStream.readAllBytes();
            String certContent = new String(certBytes);

            if (certContent.contains("-----BEGIN CERTIFICATE-----")) {
                // PEM format - extract base64 content
                String pem = certContent.replaceAll("\\n", "")
                        .replace("-----BEGIN CERTIFICATE-----", "")
                        .replace("-----END CERTIFICATE-----", "");

                byte[] decoded = Base64.getDecoder().decode(pem);
                return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(decoded));
            } else {
                // Assume DER format
                return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            }
        } catch (Exception e) {
            log.error("Failed to parse CA certificate", e);
            throw new RuntimeException("CA certificate parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse CA private key from input stream
     * Supports PKCS#8 unencrypted format (most common for development)
     */
    private PrivateKey parseCaPrivateKey(InputStream keyStream, String password) throws Exception {
        try {
            byte[] keyBytes = keyStream.readAllBytes();
            String keyContent = new String(keyBytes);

            // For now, support only PKCS#8 unencrypted format
            // This is the most common format for development CA keys
            if (keyContent.contains("-----BEGIN PRIVATE KEY-----")) {
                // PKCS#8 unencrypted
                String pem = keyContent.replaceAll("\\n", "")
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "");

                byte[] decoded = Base64.getDecoder().decode(pem);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePrivate(keySpec);

            } else if (keyContent.contains("-----BEGIN RSA PRIVATE KEY-----")) {
                // PKCS#1 format - convert to PKCS#8 using Bouncy Castle
                String pem = keyContent.replaceAll("\\n", "")
                        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                        .replace("-----END RSA PRIVATE KEY-----", "");

                byte[] decoded = Base64.getDecoder().decode(pem);

                // Use Bouncy Castle to parse PKCS#1 and convert to PKCS#8
                org.bouncycastle.asn1.pkcs.RSAPrivateKey rsaPrivateKey = org.bouncycastle.asn1.pkcs.RSAPrivateKey
                        .getInstance(decoded);

                // Create PKCS#8 structure
                PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(
                        new AlgorithmIdentifier(org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.rsaEncryption),
                        rsaPrivateKey);

                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyInfo.getEncoded());
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePrivate(keySpec);

            } else {
                // Try DER format (PKCS#8)
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePrivate(keySpec);
            }

        } catch (Exception e) {
            log.error("Failed to parse CA private key. Supported formats: PKCS#8 unencrypted, PKCS#1. " +
                    "For encrypted keys, use unencrypted PKCS#8 format for development.", e);
            throw new RuntimeException("CA private key parsing failed: " + e.getMessage() +
                    ". Ensure key is in PKCS#8 unencrypted format.", e);
        }
    }
}