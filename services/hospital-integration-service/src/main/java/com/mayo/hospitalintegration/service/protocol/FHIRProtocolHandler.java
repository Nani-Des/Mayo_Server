package com.mayo.hospitalintegration.service.protocol;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * FHIR protocol handler for healthcare data exchange using FHIR REST API
 */
@Component
@Slf4j
public class FHIRProtocolHandler extends AbstractProtocolHandler {

    private static final String CONFIG_BASE_URL = "baseUrl";
    private static final String CONFIG_AUTH_TOKEN = "authToken";
    private static final String CONFIG_TIMEOUT = "timeout";
    private static final String CONFIG_RESOURCE_TYPE = "resourceType";

    private final RestTemplate restTemplate;

    public FHIRProtocolHandler() {
        this.restTemplate = new RestTemplate();
        // In a real implementation, configure timeouts, SSL, etc.
    }

    @Override
    public DataTransferSession.TransferProtocol getProtocol() {
        return DataTransferSession.TransferProtocol.FHIR;
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        ValidationResult required = validateRequiredConfig(config, CONFIG_BASE_URL);
        if (!required.isValid()) {
            return required;
        }

        ValidationResult endpoint = validateEndpoint(config.get(CONFIG_BASE_URL).toString());
        if (!endpoint.isValid()) {
            return endpoint;
        }

        return ValidationResult.valid();
    }

    @Override
    public TransferResult executeTransfer(DataTransferSession session, Map<String, Object> config) {
        String sessionId = session.getSessionId();
        updateTransferStatus(sessionId, TransferStatus.CONNECTING);

        String baseUrl = config.get(CONFIG_BASE_URL).toString();
        String authToken = config.get(CONFIG_AUTH_TOKEN) != null ?
            config.get(CONFIG_AUTH_TOKEN).toString() : null;
        String resourceType = config.get(CONFIG_RESOURCE_TYPE) != null ?
            config.get(CONFIG_RESOURCE_TYPE).toString() : "Patient";

        try {
            updateTransferStatus(sessionId, TransferStatus.TRANSFERRING);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authToken != null) {
                headers.setBearerAuth(authToken);
            }

            // Determine operation based on transfer type
            TransferResult result;
            switch (session.getTransferType()) {
                case PULL:
                    result = executePull(baseUrl, resourceType, headers, session);
                    break;
                case PUSH:
                    result = executePush(baseUrl, resourceType, headers, session);
                    break;
                case SYNC:
                    result = executeSync(baseUrl, resourceType, headers, session);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported transfer type: " + session.getTransferType());
            }

            updateTransferStatus(sessionId, result.isSuccess() ? TransferStatus.COMPLETED : TransferStatus.FAILED);
            removeActiveTransfer(sessionId);

            return result;

        } catch (Exception e) {
            log.error("FHIR transfer failed for session: {}", sessionId, e);
            updateTransferStatus(sessionId, TransferStatus.FAILED);
            removeActiveTransfer(sessionId);
            return new TransferResult(false, "Transfer failed: " + e.getMessage(), null, 0, 0);
        }
    }

    private TransferResult executePull(String baseUrl, String resourceType, HttpHeaders headers,
                                     DataTransferSession session) {
        String url = baseUrl + "/" + resourceType;

        // Add search parameters based on data type
        if (session.getDataType() != null) {
            url += "?_count=100"; // Limit results for demo
        }

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            String responseBody = response.getBody();
            long recordCount = countResources(responseBody);
            return new TransferResult(true, null, responseBody,
                responseBody != null ? responseBody.length() : 0, recordCount);
        } else {
            return new TransferResult(false, "HTTP " + response.getStatusCode() + ": " + response.getBody(), null, 0, 0);
        }
    }

    private TransferResult executePush(String baseUrl, String resourceType, HttpHeaders headers,
                                     DataTransferSession session) {
        String url = baseUrl + "/" + resourceType;

        // Generate sample FHIR resource
        String fhirResource = generateFHIRResource(session);

        HttpEntity<String> entity = new HttpEntity<>(fhirResource, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return new TransferResult(true, null, response.getBody(), fhirResource.length(), 1);
        } else {
            return new TransferResult(false, "HTTP " + response.getStatusCode() + ": " + response.getBody(),
                null, 0, 0);
        }
    }

    private TransferResult executeSync(String baseUrl, String resourceType, HttpHeaders headers,
                                     DataTransferSession session) {
        // For sync, first pull existing data, then push updates
        TransferResult pullResult = executePull(baseUrl, resourceType, headers, session);
        if (!pullResult.isSuccess()) {
            return pullResult;
        }

        TransferResult pushResult = executePush(baseUrl, resourceType, headers, session);
        if (!pushResult.isSuccess()) {
            return pushResult;
        }

        return new TransferResult(true, null,
            "Sync completed: pulled " + pullResult.getRecordsProcessed() + ", pushed " + pushResult.getRecordsProcessed(),
            pullResult.getBytesTransferred() + pushResult.getBytesTransferred(),
            pullResult.getRecordsProcessed() + pushResult.getRecordsProcessed());
    }

    private String generateFHIRResource(DataTransferSession session) {
        // Simplified FHIR Patient resource
        return "{\n" +
               "  \"resourceType\": \"Patient\",\n" +
               "  \"id\": \"example-patient\",\n" +
               "  \"identifier\": [{\n" +
               "    \"system\": \"urn:oid:1.2.36.146.595.217.0.1\",\n" +
               "    \"value\": \"12345\"\n" +
               "  }],\n" +
               "  \"name\": [{\n" +
               "    \"family\": \"Doe\",\n" +
               "    \"given\": [\"John\"]\n" +
               "  }],\n" +
               "  \"gender\": \"male\",\n" +
               "  \"birthDate\": \"1980-01-01\"\n" +
               "}";
    }

    private long countResources(String responseBody) {
        if (responseBody == null) return 0;
        // Simple count of resource entries in JSON response
        long count = responseBody.split("\"resourceType\"").length - 1;
        return Math.max(0, count);
    }
}