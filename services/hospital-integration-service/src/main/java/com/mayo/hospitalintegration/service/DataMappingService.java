package com.mayo.hospitalintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for data mapping and transformation between different healthcare data formats
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataMappingService {

    private final ObjectMapper objectMapper;

    /**
     * Transforms data from source format to target format
     * @param sourceData The source data to transform
     * @param sourceFormat Source data format (HL7, FHIR, JSON, etc.)
     * @param targetFormat Target data format
     * @param session Transfer session for context
     * @return Transformed data
     */
    public Object transformData(Object sourceData, String sourceFormat, String targetFormat,
                              DataTransferSession session) {
        try {
            log.info("Transforming data from {} to {} for session: {}", sourceFormat, targetFormat,
                    session.getSessionId());

            // Convert source data to internal representation
            JsonNode internalData = convertToInternal(sourceData, sourceFormat, session);

            // Convert from internal representation to target format
            Object result = convertFromInternal(internalData, targetFormat, session);

            log.info("Data transformation completed for session: {}", session.getSessionId());
            return result;

        } catch (Exception e) {
            log.error("Data transformation failed for session: {}", session.getSessionId(), e);
            throw new RuntimeException("Data transformation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates data format and structure
     * @param data Data to validate
     * @param format Data format
     * @param session Transfer session
     * @return true if valid
     */
    public boolean validateData(Object data, String format, DataTransferSession session) {
        try {
            switch (format.toUpperCase()) {
                case "HL7":
                    return validateHL7Data(data);
                case "FHIR":
                    return validateFHIRData(data);
                case "DICOM":
                    return validateDICOMData(data);
                case "JSON":
                    return validateJSONData(data);
                case "XML":
                    return validateXMLData(data);
                default:
                    log.warn("Unknown format for validation: {}", format);
                    return true; // Allow unknown formats
            }
        } catch (Exception e) {
            log.error("Data validation failed for format: {}", format, e);
            return false;
        }
    }

    private JsonNode convertToInternal(Object sourceData, String sourceFormat, DataTransferSession session) {
        switch (sourceFormat.toUpperCase()) {
            case "HL7":
                return convertHL7ToInternal(sourceData, session);
            case "FHIR":
                return convertFHIRToInternal(sourceData, session);
            case "DICOM":
                return convertDICOMToInternal(sourceData, session);
            case "JSON":
                return convertJSONToInternal(sourceData);
            case "XML":
                return convertXMLToInternal(sourceData);
            default:
                // Assume already in internal format (JSON)
                return objectMapper.valueToTree(sourceData);
        }
    }

    private Object convertFromInternal(JsonNode internalData, String targetFormat, DataTransferSession session) {
        switch (targetFormat.toUpperCase()) {
            case "HL7":
                return convertInternalToHL7(internalData, session);
            case "FHIR":
                return convertInternalToFHIR(internalData, session);
            case "DICOM":
                return convertInternalToDICOM(internalData, session);
            case "JSON":
                return internalData;
            case "XML":
                return convertInternalToXML(internalData);
            default:
                return internalData.toString();
        }
    }

    // HL7 conversion methods
    private JsonNode convertHL7ToInternal(Object hl7Data, DataTransferSession session) {
        Map<String, Object> internal = new HashMap<>();
        String hl7String = hl7Data.toString();

        // Parse basic HL7 segments
        String[] segments = hl7String.split("\\r");

        for (String segment : segments) {
            if (segment.startsWith("MSH|")) {
                // Message Header
                String[] fields = segment.split("\\|");
                internal.put("messageType", fields.length > 8 ? fields[8] : "UNKNOWN");
                internal.put("messageId", fields.length > 9 ? fields[9] : "UNKNOWN");
            } else if (segment.startsWith("PID|")) {
                // Patient Identification
                String[] fields = segment.split("\\|");
                Map<String, Object> patient = new HashMap<>();
                patient.put("id", fields.length > 3 ? fields[3] : "");
                patient.put("name", fields.length > 5 ? parseHL7Name(fields[5]) : "");
                patient.put("dob", fields.length > 7 ? fields[7] : "");
                patient.put("gender", fields.length > 8 ? fields[8] : "");
                internal.put("patient", patient);
            } else if (segment.startsWith("PV1|")) {
                // Patient Visit
                String[] fields = segment.split("\\|");
                Map<String, Object> visit = new HashMap<>();
                visit.put("visitNumber", fields.length > 19 ? fields[19] : "");
                visit.put("admissionDate", fields.length > 44 ? fields[44] : "");
                internal.put("visit", visit);
            }
        }

        return objectMapper.valueToTree(internal);
    }

    private Object convertInternalToHL7(JsonNode internalData, DataTransferSession session) {
        StringBuilder hl7 = new StringBuilder();

        // MSH segment
        hl7.append("MSH|^~\\&|MAYO|HOSPITAL|DEVICE|SYSTEM|")
           .append(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
           .append("||ADT^A01|MSG")
           .append(System.currentTimeMillis())
           .append("|P|2.5|||AL|NE|||||\r");

        // PID segment
        if (internalData.has("patient")) {
            JsonNode patient = internalData.get("patient");
            hl7.append("PID|1||")
               .append(patient.get("id").asText(""))
               .append("||")
               .append(formatHL7Name(patient.get("name").asText("")))
               .append("||")
               .append(patient.get("dob").asText(""))
               .append("|")
               .append(patient.get("gender").asText(""))
               .append("|||||||||||||||\r");
        }

        // PV1 segment
        if (internalData.has("visit")) {
            JsonNode visit = internalData.get("visit");
            hl7.append("PV1|1|I|WARD001^BEDS001||||DR001^SMITH^JOHN|||SUR|||||ADM|||")
               .append(visit.get("admissionDate").asText(""))
               .append("|||||||\r");
        }

        return hl7.toString();
    }

    // FHIR conversion methods
    private JsonNode convertFHIRToInternal(Object fhirData, DataTransferSession session) {
        try {
            JsonNode fhirJson = objectMapper.valueToTree(fhirData);
            Map<String, Object> internal = new HashMap<>();

            if (fhirJson.has("resourceType") && "Patient".equals(fhirJson.get("resourceType").asText())) {
                Map<String, Object> patient = new HashMap<>();
                patient.put("id", fhirJson.get("id").asText(""));
                if (fhirJson.has("name") && fhirJson.get("name").isArray()) {
                    patient.put("name", formatFHIRName(fhirJson.get("name").get(0)));
                }
                if (fhirJson.has("birthDate")) {
                    patient.put("dob", fhirJson.get("birthDate").asText());
                }
                if (fhirJson.has("gender")) {
                    patient.put("gender", fhirJson.get("gender").asText());
                }
                internal.put("patient", patient);
            }

            return objectMapper.valueToTree(internal);
        } catch (Exception e) {
            log.error("Failed to convert FHIR to internal format", e);
            return objectMapper.createObjectNode();
        }
    }

    private Object convertInternalToFHIR(JsonNode internalData, DataTransferSession session) {
        Map<String, Object> fhir = new HashMap<>();
        fhir.put("resourceType", "Patient");

        if (internalData.has("patient")) {
            JsonNode patient = internalData.get("patient");
            fhir.put("id", patient.get("id").asText("example-patient"));

            // Name
            if (patient.has("name")) {
                Map<String, Object> name = new HashMap<>();
                name.put("family", patient.get("name").asText().split("\\^")[0]);
                name.put("given", new String[]{patient.get("name").asText().split("\\^")[1]});
                fhir.put("name", new Map[]{name});
            }

            // Birth date
            if (patient.has("dob")) {
                fhir.put("birthDate", patient.get("dob").asText());
            }

            // Gender
            if (patient.has("gender")) {
                fhir.put("gender", patient.get("gender").asText());
            }
        }

        return fhir;
    }

    // DICOM conversion methods (simplified)
    private JsonNode convertDICOMToInternal(Object dicomData, DataTransferSession session) {
        Map<String, Object> internal = new HashMap<>();
        internal.put("dicomData", dicomData.toString());
        internal.put("modality", "UNKNOWN");
        internal.put("studyId", "UNKNOWN");
        return objectMapper.valueToTree(internal);
    }

    private Object convertInternalToDICOM(JsonNode internalData, DataTransferSession session) {
        // Simplified DICOM conversion - in reality this would be complex binary format
        return "DICOM_DATA_PLACEHOLDER";
    }

    // JSON/XML conversion methods
    private JsonNode convertJSONToInternal(Object jsonData) {
        try {
            if (jsonData instanceof String) {
                return objectMapper.readTree((String) jsonData);
            }
            return objectMapper.valueToTree(jsonData);
        } catch (Exception e) {
            log.error("Failed to parse JSON", e);
            return objectMapper.createObjectNode();
        }
    }

    private Object convertInternalToXML(JsonNode internalData) {
        // Simplified XML conversion
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<data>\n");

        if (internalData.has("patient")) {
            JsonNode patient = internalData.get("patient");
            xml.append("  <patient>\n");
            xml.append("    <id>").append(patient.get("id").asText()).append("</id>\n");
            xml.append("    <name>").append(patient.get("name").asText()).append("</name>\n");
            xml.append("  </patient>\n");
        }

        xml.append("</data>");
        return xml.toString();
    }

    private JsonNode convertXMLToInternal(Object xmlData) {
        // Simplified XML parsing - in reality would use proper XML parser
        Map<String, Object> internal = new HashMap<>();
        String xmlString = xmlData.toString();
        internal.put("xmlContent", xmlString);
        return objectMapper.valueToTree(internal);
    }

    // Validation methods
    private boolean validateHL7Data(Object data) {
        String hl7String = data.toString();
        return hl7String.contains("MSH|") && hl7String.split("\\r").length > 0;
    }

    private boolean validateFHIRData(Object data) {
        try {
            JsonNode json = objectMapper.valueToTree(data);
            return json.has("resourceType");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateDICOMData(Object data) {
        // Simplified DICOM validation
        return data != null;
    }

    private boolean validateJSONData(Object data) {
        try {
            objectMapper.readTree(data.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateXMLData(Object data) {
        String xmlString = data.toString();
        return xmlString.startsWith("<?xml") || xmlString.startsWith("<");
    }

    // Helper methods
    private String parseHL7Name(String hl7Name) {
        if (hl7Name == null) return "";
        String[] parts = hl7Name.split("\\^");
        if (parts.length >= 2) {
            return parts[1] + " " + parts[0]; // First Last
        }
        return hl7Name;
    }

    private String formatHL7Name(String name) {
        if (name == null) return "";
        String[] parts = name.split(" ");
        if (parts.length >= 2) {
            return parts[1] + "^" + parts[0]; // Last^First
        }
        return name;
    }

    private String formatFHIRName(JsonNode nameNode) {
        if (nameNode.has("given") && nameNode.has("family")) {
            return nameNode.get("family").asText() + "^" + nameNode.get("given").get(0).asText();
        }
        return "";
    }
}