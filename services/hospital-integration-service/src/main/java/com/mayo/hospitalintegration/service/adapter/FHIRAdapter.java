package com.mayo.hospitalintegration.service.adapter;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * FHIR protocol adapter for parsing, validating, and transforming FHIR resources
 */
@Component
@Slf4j
public class FHIRAdapter implements ProtocolAdapter {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FHIRAdapter() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public DataTransferSession.TransferProtocol getProtocol() {
        return DataTransferSession.TransferProtocol.FHIR;
    }

    @Override
    public ParsedData parse(String rawData) throws AdapterException {
        try {
            log.debug("Parsing FHIR resource");

            // Parse JSON
            JsonNode jsonNode = objectMapper.readTree(rawData);

            // Validate basic FHIR structure
            if (!jsonNode.has("resourceType")) {
                throw new AdapterException("Invalid FHIR resource: missing resourceType");
            }

            String resourceType = jsonNode.get("resourceType").asText();
            Map<String, Object> structuredData = objectMapper.convertValue(jsonNode, Map.class);

            // Extract metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("resourceType", resourceType);
            metadata.put("fhirVersion", "R4"); // Assume R4
            metadata.put("id", jsonNode.has("id") ? jsonNode.get("id").asText() : null);
            metadata.put("version", jsonNode.has("meta") && jsonNode.get("meta").has("versionId") ?
                jsonNode.get("meta").get("versionId").asText() : null);

            return new ParsedData(structuredData, metadata);

        } catch (Exception e) {
            log.error("Failed to parse FHIR resource", e);
            throw new AdapterException("FHIR parsing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(ParsedData parsedData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) parsedData.getStructuredData();

            Map<String, String> fieldErrors = new HashMap<>();

            // Validate resource type
            if (!resource.containsKey("resourceType")) {
                fieldErrors.put("resourceType", "Missing resourceType");
            } else {
                String resourceType = (String) resource.get("resourceType");
                if (!isValidResourceType(resourceType)) {
                    fieldErrors.put("resourceType", "Invalid resource type: " + resourceType);
                }
            }

            // Resource-specific validation
            String resourceType = (String) resource.get("resourceType");
            if ("Patient".equals(resourceType)) {
                validatePatientResource(resource, fieldErrors);
            } else if ("Observation".equals(resourceType)) {
                validateObservationResource(resource, fieldErrors);
            } else if ("Condition".equals(resourceType)) {
                validateConditionResource(resource, fieldErrors);
            }

            // Validate required FHIR fields
            validateRequiredFHIRFields(resource, fieldErrors);

            if (fieldErrors.isEmpty()) {
                return ValidationResult.valid();
            } else {
                return ValidationResult.invalid("FHIR validation failed", fieldErrors);
            }

        } catch (Exception e) {
            log.error("FHIR validation error", e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    @Override
    public TransformedData transform(ParsedData parsedData) throws AdapterException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) parsedData.getStructuredData();

            String resourceType = (String) resource.get("resourceType");
            Object internalData;
            String dataType;

            switch (resourceType) {
                case "Patient":
                    internalData = transformPatientResource(resource);
                    dataType = "PATIENT_DEMOGRAPHICS";
                    break;
                case "Observation":
                    internalData = transformObservationResource(resource);
                    dataType = "LAB_RESULTS";
                    break;
                case "Condition":
                    internalData = transformConditionResource(resource);
                    dataType = "DIAGNOSIS";
                    break;
                case "MedicationRequest":
                    internalData = transformMedicationRequestResource(resource);
                    dataType = "MEDICATION_ORDER";
                    break;
                default:
                    internalData = resource; // Pass through
                    dataType = "UNKNOWN";
            }

            Map<String, Object> metadata = new HashMap<>(parsedData.getMetadata());
            metadata.put("transformedAt", System.currentTimeMillis());
            metadata.put("dataType", dataType);

            return new TransformedData(internalData, dataType, metadata);

        } catch (Exception e) {
            log.error("FHIR transformation error", e);
            throw new AdapterException("FHIR transformation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String serialize(TransformedData internalData) throws AdapterException {
        try {
            // Convert internal data back to FHIR JSON
            return objectMapper.writeValueAsString(internalData.getInternalData());

        } catch (Exception e) {
            log.error("FHIR serialization error", e);
            throw new AdapterException("FHIR serialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Performs OAuth authentication for FHIR server access
     */
    public String authenticate(String tokenEndpoint, String clientId, String clientSecret) throws AdapterException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            } else {
                throw new AdapterException("OAuth authentication failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("OAuth authentication error", e);
            throw new AdapterException("OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches FHIR resource from server
     */
    public String fetchResource(String baseUrl, String resourceType, String id, String accessToken) throws AdapterException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = baseUrl + "/" + resourceType + "/" + id;

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new AdapterException("Failed to fetch FHIR resource: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to fetch FHIR resource", e);
            throw new AdapterException("Resource fetch failed: " + e.getMessage(), e);
        }
    }

    private boolean isValidResourceType(String resourceType) {
        // Common FHIR resource types
        return resourceType != null && (
            "Patient".equals(resourceType) ||
            "Observation".equals(resourceType) ||
            "Condition".equals(resourceType) ||
            "MedicationRequest".equals(resourceType) ||
            "Encounter".equals(resourceType) ||
            "Practitioner".equals(resourceType) ||
            "Organization".equals(resourceType)
        );
    }

    private void validateRequiredFHIRFields(Map<String, Object> resource, Map<String, String> fieldErrors) {
        // All FHIR resources must have resourceType
        if (!resource.containsKey("resourceType")) {
            fieldErrors.put("resourceType", "Required field missing");
        }
    }

    private void validatePatientResource(Map<String, Object> resource, Map<String, String> fieldErrors) {
        // Patient-specific validation
        if (!resource.containsKey("name") || !(resource.get("name") instanceof java.util.List)) {
            fieldErrors.put("name", "Patient must have name array");
        }
    }

    private void validateObservationResource(Map<String, Object> resource, Map<String, String> fieldErrors) {
        // Observation-specific validation
        if (!resource.containsKey("code")) {
            fieldErrors.put("code", "Observation must have code");
        }
        if (!resource.containsKey("valueQuantity") && !resource.containsKey("valueCodeableConcept") &&
            !resource.containsKey("valueString") && !resource.containsKey("valueBoolean")) {
            fieldErrors.put("value[x]", "Observation must have a value");
        }
    }

    private void validateConditionResource(Map<String, Object> resource, Map<String, String> fieldErrors) {
        // Condition-specific validation
        if (!resource.containsKey("code")) {
            fieldErrors.put("code", "Condition must have code");
        }
    }

    private Object transformPatientResource(Map<String, Object> resource) {
        Map<String, Object> patient = new HashMap<>();

        patient.put("id", resource.get("id"));

        // Extract name
        if (resource.containsKey("name")) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> names = (java.util.List<Map<String, Object>>) resource.get("name");
            if (!names.isEmpty()) {
                Map<String, Object> name = names.get(0);
                @SuppressWarnings("unchecked")
                java.util.List<String> given = (java.util.List<String>) name.get("given");
                String family = (String) name.get("family");
                patient.put("firstName", given != null && !given.isEmpty() ? given.get(0) : null);
                patient.put("lastName", family);
            }
        }

        // Extract identifiers
        if (resource.containsKey("identifier")) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> identifiers = (java.util.List<Map<String, Object>>) resource.get("identifier");
            for (Map<String, Object> identifier : identifiers) {
                if ("MR".equals(identifier.get("use"))) { // Medical Record Number
                    patient.put("medicalRecordNumber", identifier.get("value"));
                }
            }
        }

        patient.put("gender", resource.get("gender"));
        patient.put("birthDate", resource.get("birthDate"));

        return patient;
    }

    private Object transformObservationResource(Map<String, Object> resource) {
        Map<String, Object> observation = new HashMap<>();

        observation.put("id", resource.get("id"));

        // Extract code
        if (resource.containsKey("code")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> code = (Map<String, Object>) resource.get("code");
            if (code.containsKey("coding")) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> coding = (java.util.List<Map<String, Object>>) code.get("coding");
                if (!coding.isEmpty()) {
                    Map<String, Object> codeInfo = coding.get(0);
                    observation.put("testCode", codeInfo.get("code"));
                    observation.put("testName", codeInfo.get("display"));
                }
            }
        }

        // Extract value
        if (resource.containsKey("valueQuantity")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) resource.get("valueQuantity");
            observation.put("value", value.get("value"));
            observation.put("unit", value.get("unit"));
        } else if (resource.containsKey("valueString")) {
            observation.put("value", resource.get("valueString"));
        }

        // Extract reference range
        if (resource.containsKey("referenceRange")) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> ranges = (java.util.List<Map<String, Object>>) resource.get("referenceRange");
            if (!ranges.isEmpty()) {
                Map<String, Object> range = ranges.get(0);
                observation.put("referenceRange", range.get("text"));
            }
        }

        observation.put("status", resource.get("status"));
        observation.put("effectiveDateTime", resource.get("effectiveDateTime"));

        return observation;
    }

    private Object transformConditionResource(Map<String, Object> resource) {
        Map<String, Object> condition = new HashMap<>();

        condition.put("id", resource.get("id"));

        // Extract code
        if (resource.containsKey("code")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> code = (Map<String, Object>) resource.get("code");
            if (code.containsKey("coding")) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> coding = (java.util.List<Map<String, Object>>) code.get("coding");
                if (!coding.isEmpty()) {
                    Map<String, Object> codeInfo = coding.get(0);
                    condition.put("diagnosisCode", codeInfo.get("code"));
                    condition.put("diagnosisName", codeInfo.get("display"));
                }
            }
        }

        condition.put("clinicalStatus", resource.get("clinicalStatus"));
        condition.put("verificationStatus", resource.get("verificationStatus"));
        condition.put("onsetDateTime", resource.get("onsetDateTime"));

        return condition;
    }

    private Object transformMedicationRequestResource(Map<String, Object> resource) {
        Map<String, Object> medication = new HashMap<>();

        medication.put("id", resource.get("id"));

        // Extract medication
        if (resource.containsKey("medicationCodeableConcept")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> med = (Map<String, Object>) resource.get("medicationCodeableConcept");
            if (med.containsKey("coding")) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> coding = (java.util.List<Map<String, Object>>) med.get("coding");
                if (!coding.isEmpty()) {
                    Map<String, Object> codeInfo = coding.get(0);
                    medication.put("medicationCode", codeInfo.get("code"));
                    medication.put("medicationName", codeInfo.get("display"));
                }
            }
        }

        medication.put("status", resource.get("status"));
        medication.put("intent", resource.get("intent"));
        medication.put("authoredOn", resource.get("authoredOn"));

        return medication;
    }
}