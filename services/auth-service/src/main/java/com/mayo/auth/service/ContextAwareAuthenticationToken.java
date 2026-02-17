package com.mayo.auth.service;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Authentication token for context-aware authentication
 */
public class ContextAwareAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private final Object credentials;
    private final String deviceId;
    private final String certificatePem;
    private final String apiKey;
    private final ContextAwareAuthenticationProvider.DeviceContext deviceContext;
    private final List<String> authenticationMethods;

    /**
     * Constructor for unauthenticated token
     */
    public ContextAwareAuthenticationToken(
            Object principal,
            Object credentials,
            String deviceId,
            String certificatePem,
            String apiKey) {

        super(null);
        this.principal = principal;
        this.credentials = credentials;
        this.deviceId = deviceId;
        this.certificatePem = certificatePem;
        this.apiKey = apiKey;
        this.deviceContext = null;
        this.authenticationMethods = null;
        setAuthenticated(false);
    }

    /**
     * Constructor for authenticated token
     */
    public ContextAwareAuthenticationToken(
            Object principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities,
            ContextAwareAuthenticationProvider.DeviceContext deviceContext,
            List<String> authenticationMethods) {

        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        this.deviceId = deviceContext != null ? deviceContext.getDeviceId() : null;
        this.certificatePem = null;
        this.apiKey = null;
        this.deviceContext = deviceContext;
        this.authenticationMethods = authenticationMethods;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getCertificatePem() {
        return certificatePem;
    }

    public String getApiKey() {
        return apiKey;
    }

    public ContextAwareAuthenticationProvider.DeviceContext getDeviceContext() {
        return deviceContext;
    }

    public List<String> getAuthenticationMethods() {
        return authenticationMethods;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException(
                    "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
        }
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        // Don't erase device context or authentication methods as they might be needed
    }
}