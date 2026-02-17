package com.mayo.sync.config;

import com.mayo.common.security.certificate.DeviceCertificateValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * SSL/TLS configuration for Sync Service
 * Configures secure communication channels
 */
@Configuration
@Slf4j
public class SslConfig {

    @Value("${server.ssl.key-store:classpath:certs/server/server.p12}")
    private String keyStorePath;

    @Value("${server.ssl.key-store-password:changeit}")
    private String keyStorePassword;

    @Value("${server.ssl.key-store-type:PKCS12}")
    private String keyStoreType;

    @Value("${server.ssl.trust-store:classpath:certs/ca/ca.p12}")
    private String trustStorePath;

    @Value("${server.ssl.trust-store-password:changeit}")
    private String trustStorePassword;

    @Value("${server.ssl.trust-store-type:PKCS12}")
    private String trustStoreType;

    @Value("${server.ssl.protocol:TLSv1.3}")
    private String sslProtocol;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.io.ResourceLoader resourceLoader;

    /**
     * Configure SSL context for server
     */
    @Bean
    public SSLContext serverSslContext() {
        try {
            // Load server keystore
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            org.springframework.core.io.Resource keyStoreResource = resourceLoader.getResource(keyStorePath);
            try (java.io.InputStream keyStoreStream = keyStoreResource.getInputStream()) {
                keyStore.load(keyStoreStream, keyStorePassword.toCharArray());
            }

            // Initialize key manager factory
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

            // Load truststore
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            org.springframework.core.io.Resource trustStoreResource = resourceLoader.getResource(trustStorePath);
            try (java.io.InputStream trustStoreStream = trustStoreResource.getInputStream()) {
                trustStore.load(trustStoreStream, trustStorePassword.toCharArray());
            }

            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance(sslProtocol);
            sslContext.init(
                keyManagerFactory.getKeyManagers(),
                new TrustManager[]{deviceCertificateValidator()},
                new java.security.SecureRandom()
            );

            log.info("SSL context configured successfully for Sync Service");
            return sslContext;

        } catch (Exception e) {
            log.error("Failed to configure SSL context: {}", e.getMessage());
            throw new RuntimeException("SSL configuration failed", e);
        }
    }

    /**
     * Device certificate validator as trust manager
     */
    @Bean
    public DeviceCertificateValidator deviceCertificateValidator() {
        return new DeviceCertificateValidator();
    }
}