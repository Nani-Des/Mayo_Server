package com.mayo.hospitalintegration.service.protocol;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Generic REST API protocol handler for data transfer
 */
@Component
@Slf4j
public class RestApiProtocolHandler extends AbstractProtocolHandler {

    private static final String CONFIG_BASE_URL = "baseUrl";
    private static final String CONFIG_ENDPOINT = "endpoint";
    private static final String CONFIG_METHOD = "method";
    private static final String CONFIG_HEADERS = "headers";
    private static final String CONFIG_AUTH_TOKEN = "authToken";
    private static final String CONFIG_TIMEOUT = "timeout";

    private final RestTemplate restTemplate;

    public RestApiProtocolHandler() {
        this.restTemplate = new RestTemplate();
        // In a real implementation, configure timeouts, SSL, etc.
    }

    @Override
    public DataTransferSession.TransferProtocol getProtocol() {
        return DataTransferSession.TransferProtocol.REST_API;
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        ValidationResult required = validateRequiredConfig(config, CONFIG_BASE_URL, CONFIG_ENDPOINT);
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
        String endpoint = config.get(CONFIG_ENDPOINT).toString();
        String method = config.get(CONFIG_METHOD) != null ?
            config.get(CONFIG_METHOD).toString() : "GET";
        String authToken = config.get(CONFIG_AUTH_TOKEN) != null ?
            config.get(CONFIG_AUTH_TOKEN).toString() : null;

        try {
            updateTransferStatus(sessionId, TransferStatus.TRANSFERRING);

            String url = baseUrl + endpoint;
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authToken != null) {
                headers.setBearerAuth(authToken);
            }

            // Add custom headers if provided
            if (config.containsKey(CONFIG_HEADERS)) {
                @SuppressWarnings("unchecked")
                Map<String, String> customHeaders = (Map<String, String>) config.get(CONFIG_HEADERS);
                customHeaders.forEach(headers::set);
            }

            // Prepare request body based on transfer type
            String requestBody = prepareRequestBody(session);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            log.info("Executing REST API {} request to: {}", httpMethod, url);
            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            String responseBody = response.getBody();

            updateTransferStatus(sessionId, success ? TransferStatus.COMPLETED : TransferStatus.FAILED);
            removeActiveTransfer(sessionId);

            return new TransferResult(success,
                success ? null : "HTTP " + response.getStatusCode() + ": " + responseBody,
                responseBody,
                responseBody != null ? responseBody.length() : 0,
                estimateRecordCount(responseBody));

        } catch (Exception e) {
            log.error("REST API transfer failed for session: {}", sessionId, e);
            updateTransferStatus(sessionId, TransferStatus.FAILED);
            removeActiveTransfer(sessionId);
            return new TransferResult(false, "REST API transfer failed: " + e.getMessage(), null, 0, 0);
        }
    }

    private String prepareRequestBody(DataTransferSession session) {
        // Generate request body based on data type and transfer type
        switch (session.getDataType()) {
            case PATIENT_RECORD:
                return generatePatientRecordRequest(session);
            case PRESCRIPTION:
                return generatePrescriptionRequest(session);
            case LAB_RESULT:
                return generateLabResultRequest(session);
            case IMAGING:
                return generateImagingRequest(session);
            default:
                return generateGenericRequest(session);
        }
    }

    private String generatePatientRecordRequest(DataTransferSession session) {
        return "{\n" +
               "  \"patientId\": \"PATIENT123\",\n" +
               "  \"transferType\": \"" + session.getTransferType() + "\",\n" +
               "  \"dataType\": \"" + session.getDataType() + "\",\n" +
               "  \"sessionId\": \"" + session.getSessionId() + "\"\n" +
               "}";
    }

    private String generatePrescriptionRequest(DataTransferSession session) {
        return "{\n" +
               "  \"prescriptionId\": \"RX123\",\n" +
               "  \"patientId\": \"PATIENT123\",\n" +
               "  \"medication\": \"Sample Medication\",\n" +
               "  \"dosage\": \"10mg\",\n" +
               "  \"transferType\": \"" + session.getTransferType() + "\",\n" +
               "  \"sessionId\": \"" + session.getSessionId() + "\"\n" +
               "}";
    }

    private String generateLabResultRequest(DataTransferSession session) {
        return "{\n" +
               "  \"labOrderId\": \"LAB123\",\n" +
               "  \"patientId\": \"PATIENT123\",\n" +
               "  \"testName\": \"Complete Blood Count\",\n" +
               "  \"results\": \"Normal\",\n" +
               "  \"transferType\": \"" + session.getTransferType() + "\",\n" +
               "  \"sessionId\": \"" + session.getSessionId() + "\"\n" +
               "}";
    }

    private String generateImagingRequest(DataTransferSession session) {
        return "{\n" +
               "  \"studyId\": \"IMG123\",\n" +
               "  \"patientId\": \"PATIENT123\",\n" +
               "  \"modality\": \"CT\",\n" +
               "  \"description\": \"Chest CT\",\n" +
               "  \"transferType\": \"" + session.getTransferType() + "\",\n" +
               "  \"sessionId\": \"" + session.getSessionId() + "\"\n" +
               "}";
    }

    private String generateGenericRequest(DataTransferSession session) {
        return "{\n" +
               "  \"dataType\": \"" + session.getDataType() + "\",\n" +
               "  \"transferType\": \"" + session.getTransferType() + "\",\n" +
               "  \"sessionId\": \"" + session.getSessionId() + "\",\n" +
               "  \"timestamp\": \"" + java.time.LocalDateTime.now() + "\"\n" +
               "}";
    }

    private long estimateRecordCount(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return 0;
        }

        // Simple estimation based on JSON structure
        if (responseBody.startsWith("[")) {
            // Array response
            return Math.max(1, responseBody.split("\\{").length - 1);
        } else if (responseBody.contains("\"entries\"") || responseBody.contains("\"items\"")) {
            // Paginated response
            return Math.max(1, responseBody.split("\\{").length - 1);
        } else {
            // Single record
            return 1;
        }
    }
}