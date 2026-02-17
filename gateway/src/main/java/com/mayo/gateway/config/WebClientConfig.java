package com.mayo.gateway.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * WebClient configuration with mTLS support
 */
@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${ssl.key-store:classpath:certs/server/keystore.p12}")
    private Resource keyStoreResource;

    @Value("${ssl.key-store-password:changeit}")
    private String keyStorePassword;

    @Value("${ssl.trust-store:classpath:certs/ca/ca.crt}")
    private Resource trustStoreResource;

    @Bean
    public WebClient.Builder webClientBuilder() {
        try {
            if (!keyStoreResource.exists() || !trustStoreResource.exists()) {
                log.warn("SSL certificates not found, using WebClient without mTLS");
                return WebClient.builder();
            }

            // Load keystore
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream keyStoreStream = keyStoreResource.getInputStream()) {
                keyStore.load(keyStoreStream, keyStorePassword.toCharArray());
            }

            // Load truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (InputStream trustStoreStream = trustStoreResource.getInputStream()) {
                trustStore.load(trustStoreStream, null);
            }

            // Create key manager
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

            // Create trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            // Build SSL context
            SslContext sslContext = SslContextBuilder.forClient()
                    .keyManager(keyManagerFactory)
                    .trustManager(trustManagerFactory)
                    .protocols("TLSv1.3", "TLSv1.2")
                    .ciphers(java.util.Arrays.asList(
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_AES_128_GCM_SHA256",
                        "TLS_CHACHA20_POLY1305_SHA256"
                    ))
                    .build();

            HttpClient httpClient = HttpClient.create()
                    .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));

            return WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient));

        } catch (Exception e) {
            log.error("Failed to configure WebClient with mTLS", e);
            // Fallback to regular WebClient
            return WebClient.builder();
        }
    }
}