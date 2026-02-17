package com.mayo.hospitalintegration.service.adapter;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * HL7 protocol adapter for parsing, validating, and transforming HL7 v2.x messages
 */
@Component
@Slf4j
public class HL7Adapter implements ProtocolAdapter {

    @Value("${hospital-integration.hl7.encoding:UTF-8}")
    private String encoding;

    @Value("${hospital-integration.hl7.field-separator:|}")
    private String fieldSeparator;

    @Value("${hospital-integration.hl7.component-separator:^}")
    private String componentSeparator;

    @Value("${hospital-integration.hl7.validation-strictness:STANDARD}")
    private String validationStrictness;

    @Value("${hospital-integration.hl7.message-timeout:30000}")
    private int messageTimeout;

    private static final Pattern HL7_MESSAGE_PATTERN = Pattern.compile(
        "MSH\\|\\^~\\\\\\&\\|[^|]*\\|[^|]*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\|[^|]*\\|[^|]*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\|[^|]*\\|\\|[^|]*\\|\\|[^|]*\\|\\|.*",
        Pattern.DOTALL
    );

    @Override
    public DataTransferSession.TransferProtocol getProtocol() {
        return DataTransferSession.TransferProtocol.HL7;
    }

    @Override
    public ParsedData parse(String rawData) throws AdapterException {
        try {
            log.debug("Parsing HL7 message");

            // Remove MLLP wrappers if present
            String hl7Message = unwrapMLLP(rawData);

            // Basic validation
            if (!isValidHL7Message(hl7Message)) {
                throw new AdapterException("Invalid HL7 message format");
            }

            // Parse message into segments
            Map<String, Object> parsedMessage = parseHL7Message(hl7Message);

            // Extract metadata
            Map<String, Object> metadata = extractMetadata(hl7Message);

            return new ParsedData(parsedMessage, metadata);

        } catch (Exception e) {
            log.error("Failed to parse HL7 message", e);
            throw new AdapterException("HL7 parsing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(ParsedData parsedData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) parsedData.getStructuredData();

            Map<String, String> fieldErrors = new HashMap<>();

            // Validate MSH segment
            if (!message.containsKey("MSH")) {
                fieldErrors.put("MSH", "Missing MSH segment");
                if ("STRICT".equals(validationStrictness)) {
                    return ValidationResult.invalid("HL7 validation failed", fieldErrors);
                }
            } else {
                Map<String, Object> msh = (Map<String, Object>) message.get("MSH");
                if (msh.get("9") == null || !isValidMessageType((String) msh.get("9"))) {
                    fieldErrors.put("MSH.9", "Invalid or missing message type");
                }

                // Validate encoding characters if in strict mode
                if ("STRICT".equals(validationStrictness)) {
                    String encodingChars = (String) msh.get("2");
                    if (encodingChars == null || !encodingChars.equals("^~\\&")) {
                        fieldErrors.put("MSH.2", "Invalid encoding characters");
                    }
                }
            }

            // Validate required segments based on message type
            String messageType = getMessageType(message);
            if ("ADT".equals(messageType)) {
                validateADTSegments(message, fieldErrors);
            } else if ("ORU".equals(messageType)) {
                validateORUSegments(message, fieldErrors);
            } else if ("ORM".equals(messageType)) {
                validateORMSegments(message, fieldErrors);
            }

            // Additional validations for strict mode
            if ("STRICT".equals(validationStrictness)) {
                validateHL7Version(message, fieldErrors);
                validateSegmentOrder(message, fieldErrors);
            }

            if (fieldErrors.isEmpty()) {
                return ValidationResult.valid();
            } else {
                return ValidationResult.invalid("HL7 validation failed", fieldErrors);
            }

        } catch (Exception e) {
            log.error("HL7 validation error", e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    @Override
    public TransformedData transform(ParsedData parsedData) throws AdapterException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) parsedData.getStructuredData();

            // Determine data type and transform accordingly
            String messageType = getMessageType(message);
            Object internalData;
            String dataType;

            switch (messageType) {
                case "ADT":
                    internalData = transformADTMessage(message);
                    dataType = "PATIENT_DEMOGRAPHICS";
                    break;
                case "ORU":
                    internalData = transformORUMessage(message);
                    dataType = "LAB_RESULTS";
                    break;
                case "ORM":
                    internalData = transformORMMessage(message);
                    dataType = "ORDER_MESSAGE";
                    break;
                default:
                    internalData = message; // Pass through for unknown types
                    dataType = "UNKNOWN";
            }

            Map<String, Object> metadata = new HashMap<>(parsedData.getMetadata());
            metadata.put("transformedAt", System.currentTimeMillis());
            metadata.put("dataType", dataType);

            return new TransformedData(internalData, dataType, metadata);

        } catch (Exception e) {
            log.error("HL7 transformation error", e);
            throw new AdapterException("HL7 transformation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String serialize(TransformedData internalData) throws AdapterException {
        try {
            // Simplified serialization - in real implementation, would convert internal format back to HL7
            String hl7Message = generateHL7FromInternal(internalData);

            // Wrap in MLLP if needed
            return wrapMLLP(hl7Message);

        } catch (Exception e) {
            log.error("HL7 serialization error", e);
            throw new AdapterException("HL7 serialization failed: " + e.getMessage(), e);
        }
    }

    private String unwrapMLLP(String rawData) {
        if (rawData.startsWith("\u000b") && rawData.contains("\u001c")) {
            int startIndex = 1; // Skip <SB>
            int endIndex = rawData.indexOf("\u001c");
            return rawData.substring(startIndex, endIndex);
        }
        return rawData;
    }

    private String wrapMLLP(String message) {
        return "\u000b" + message + "\u001c\r";
    }

    private boolean isValidHL7Message(String message) {
        return message != null && !message.trim().isEmpty() &&
               HL7_MESSAGE_PATTERN.matcher(message).find();
    }

    private Map<String, Object> parseHL7Message(String message) {
        Map<String, Object> result = new HashMap<>();
        String[] segments = message.split("\r");

        for (String segment : segments) {
            if (segment.trim().isEmpty()) continue;

            String[] fields = segment.split("\\|");
            if (fields.length > 0) {
                String segmentName = fields[0];
                Map<String, Object> segmentData = new HashMap<>();

                for (int i = 1; i < fields.length; i++) {
                    segmentData.put(String.valueOf(i), fields[i]);
                }

                result.put(segmentName, segmentData);
            }
        }

        return result;
    }

    private Map<String, Object> extractMetadata(String message) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("protocol", "HL7");
        metadata.put("version", "2.5"); // Default assumption
        metadata.put("messageLength", message.length());

        // Extract message type from MSH
        String[] lines = message.split("\r");
        for (String line : lines) {
            if (line.startsWith("MSH|")) {
                String[] fields = line.split("\\|");
                if (fields.length > 8) {
                    metadata.put("messageType", fields[8]);
                }
                break;
            }
        }

        return metadata;
    }

    private String getMessageType(Map<String, Object> message) {
        if (message.containsKey("MSH")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> msh = (Map<String, Object>) message.get("MSH");
            String messageType = (String) msh.get("9");
            if (messageType != null && messageType.contains("^")) {
                return messageType.split("\\^")[0];
            }
            return messageType;
        }
        return "UNKNOWN";
    }

    private boolean isValidMessageType(String messageType) {
        return messageType != null && (messageType.startsWith("ADT") ||
                messageType.startsWith("ORU") || messageType.startsWith("ORM") ||
                messageType.startsWith("ACK") || messageType.startsWith("QRY"));
    }

    private void validateADTSegments(Map<String, Object> message, Map<String, String> fieldErrors) {
        if (!message.containsKey("PID")) {
            fieldErrors.put("PID", "Missing PID segment for ADT message");
        }
        if (!message.containsKey("PV1")) {
            fieldErrors.put("PV1", "Missing PV1 segment for ADT message");
        }
    }

    private void validateORUSegments(Map<String, Object> message, Map<String, String> fieldErrors) {
        if (!message.containsKey("PID")) {
            fieldErrors.put("PID", "Missing PID segment for ORU message");
        }
        if (!message.containsKey("OBR")) {
            fieldErrors.put("OBR", "Missing OBR segment for ORU message");
        }
        if (!message.containsKey("OBX")) {
            fieldErrors.put("OBX", "Missing OBX segment for ORU message");
        }
    }

    private void validateORMSegments(Map<String, Object> message, Map<String, String> fieldErrors) {
        if (!message.containsKey("PID")) {
            fieldErrors.put("PID", "Missing PID segment for ORM message");
        }
        if (!message.containsKey("ORC")) {
            fieldErrors.put("ORC", "Missing ORC segment for ORM message");
        }
        if (!message.containsKey("OBR")) {
            fieldErrors.put("OBR", "Missing OBR segment for ORM message");
        }

        // Validate ORC segment fields
        if (message.containsKey("ORC")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orc = (Map<String, Object>) message.get("ORC");
            if (orc.get("1") == null || ((String) orc.get("1")).trim().isEmpty()) {
                fieldErrors.put("ORC.1", "Missing Order Control in ORC segment");
            }
        }
    }

    private void validateHL7Version(Map<String, Object> message, Map<String, String> fieldErrors) {
        if (message.containsKey("MSH")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> msh = (Map<String, Object>) message.get("MSH");
            String version = (String) msh.get("12"); // MSH.12 - Version ID
            if (version != null) {
                // Check if version is supported
                java.util.List<String> supportedVersions = java.util.Arrays.asList(
                    "2.3", "2.4", "2.5", "2.5.1", "2.6", "2.7", "2.8", "2.8.1", "2.8.2"
                );
                if (!supportedVersions.contains(version)) {
                    fieldErrors.put("MSH.12", "Unsupported HL7 version: " + version);
                }
            }
        }
    }

    private void validateSegmentOrder(Map<String, Object> message, Map<String, String> fieldErrors) {
        // Basic segment order validation - MSH should be first
        // In a real implementation, this would be more comprehensive
        if (!message.containsKey("MSH")) {
            fieldErrors.put("SEGMENT_ORDER", "MSH segment must be present and first");
        }
    }

    private Object transformADTMessage(Map<String, Object> message) {
        Map<String, Object> patient = new HashMap<>();

        if (message.containsKey("PID")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pid = (Map<String, Object>) message.get("PID");
            patient.put("patientId", pid.get("3")); // PID.3 - Patient ID
            patient.put("name", parseName((String) pid.get("5"))); // PID.5 - Patient Name
            patient.put("dateOfBirth", pid.get("7")); // PID.7 - Date of Birth
            patient.put("gender", pid.get("8")); // PID.8 - Gender
        }

        if (message.containsKey("PV1")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pv1 = (Map<String, Object>) message.get("PV1");
            patient.put("visitNumber", pv1.get("19")); // PV1.19 - Visit Number
            patient.put("admissionDate", pv1.get("44")); // PV1.44 - Admit Date/Time
        }

        return patient;
    }

    private Object transformORUMessage(Map<String, Object> message) {
        Map<String, Object> result = new HashMap<>();

        if (message.containsKey("PID")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pid = (Map<String, Object>) message.get("PID");
            result.put("patientId", pid.get("3"));
            result.put("patientName", parseName((String) pid.get("5")));
        }

        if (message.containsKey("OBR")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> obr = (Map<String, Object>) message.get("OBR");
            result.put("orderId", obr.get("3"));
            result.put("testName", obr.get("4"));
            result.put("observationDate", obr.get("7"));
        }

        // Handle multiple OBX segments
        java.util.List<Map<String, Object>> observations = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : message.entrySet()) {
            if (entry.getKey().startsWith("OBX")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> obx = (Map<String, Object>) entry.getValue();
                Map<String, Object> obs = new HashMap<>();
                obs.put("testCode", obx.get("3"));
                obs.put("value", obx.get("5"));
                obs.put("units", obx.get("6"));
                obs.put("referenceRange", obx.get("7"));
                obs.put("resultStatus", obx.get("11"));
                observations.add(obs);
            }
        }
        result.put("observations", observations);

        return result;
    }

    private Object transformORMMessage(Map<String, Object> message) {
        Map<String, Object> order = new HashMap<>();

        if (message.containsKey("PID")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pid = (Map<String, Object>) message.get("PID");
            order.put("patientId", pid.get("3"));
            order.put("patientName", parseName((String) pid.get("5")));
        }

        if (message.containsKey("PV1")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pv1 = (Map<String, Object>) message.get("PV1");
            order.put("visitNumber", pv1.get("19"));
        }

        if (message.containsKey("ORC")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orc = (Map<String, Object>) message.get("ORC");
            order.put("orderControl", orc.get("1")); // ORC.1 - Order Control
            order.put("placerOrderNumber", orc.get("2")); // ORC.2 - Placer Order Number
            order.put("fillerOrderNumber", orc.get("3")); // ORC.3 - Filler Order Number
            order.put("orderStatus", orc.get("5")); // ORC.5 - Order Status
            order.put("orderingProvider", parseName((String) orc.get("12"))); // ORC.12 - Ordering Provider
            order.put("orderDateTime", orc.get("9")); // ORC.9 - Date/Time of Transaction
        }

        // Handle multiple OBR segments for order details
        java.util.List<Map<String, Object>> orderDetails = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : message.entrySet()) {
            if (entry.getKey().startsWith("OBR")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> obr = (Map<String, Object>) entry.getValue();
                Map<String, Object> detail = new HashMap<>();
                detail.put("setId", obr.get("1"));
                detail.put("placerOrderNumber", obr.get("2"));
                detail.put("fillerOrderNumber", obr.get("3"));
                detail.put("universalServiceId", obr.get("4"));
                detail.put("priority", obr.get("5"));
                detail.put("requestedDateTime", obr.get("6"));
                detail.put("observationDateTime", obr.get("7"));
                detail.put("observationEndDateTime", obr.get("8"));
                detail.put("collectionVolume", obr.get("9"));
                detail.put("collectorIdentifier", obr.get("10"));
                detail.put("specimenActionCode", obr.get("11"));
                detail.put("dangerCode", obr.get("12"));
                detail.put("relevantClinicalInfo", obr.get("13"));
                detail.put("specimenReceivedDateTime", obr.get("14"));
                detail.put("specimenSource", obr.get("15"));
                detail.put("orderingProvider", parseName((String) obr.get("16")));
                detail.put("orderCallbackPhoneNumber", obr.get("17"));
                detail.put("placerField1", obr.get("18"));
                detail.put("placerField2", obr.get("19"));
                detail.put("fillerField1", obr.get("20"));
                detail.put("fillerField2", obr.get("21"));
                detail.put("resultsRptStatusChngDateTime", obr.get("22"));
                detail.put("chargeToPractice", obr.get("23"));
                detail.put("diagnosticServSectId", obr.get("24"));
                detail.put("resultStatus", obr.get("25"));
                orderDetails.add(detail);
            }
        }
        order.put("orderDetails", orderDetails);

        return order;
    }

    private String parseName(String nameField) {
        if (nameField == null) return null;
        // Parse HL7 name format: Family^Given^Middle^Suffix^Prefix
        String[] parts = nameField.split("\\^");
        if (parts.length >= 2) {
            return parts[1] + " " + parts[0]; // Given Family
        }
        return nameField;
    }

    private String generateHL7FromInternal(TransformedData internalData) {
        String dataType = internalData.getDataType();
        Object data = internalData.getInternalData();

        switch (dataType) {
            case "PATIENT_DEMOGRAPHICS":
                return generateADTMessage(data);
            case "LAB_RESULTS":
                return generateORUMessage(data);
            case "ORDER_MESSAGE":
                return generateORMMessage(data);
            case "ACK":
                return generateACKMessage(data);
            default:
                // Generate a basic message for unknown types
                return generateBasicHL7Message(dataType, data);
        }
    }

    private String generateADTMessage(Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> patientData = (Map<String, Object>) data;

        String patientId = (String) patientData.getOrDefault("patientId", "UNKNOWN");
        String patientName = formatHL7Name((String) patientData.getOrDefault("name", "UNKNOWN^UNKNOWN"));
        String dob = (String) patientData.getOrDefault("dateOfBirth", "");
        String gender = (String) patientData.getOrDefault("gender", "U");

        return "MSH|^~\\&|MAYO|HOSPITAL|DEVICE|SYSTEM|" + getCurrentTimestamp() + "||ADT^A01|MSG" + System.currentTimeMillis() + "|P|2.5|||AL|NE|||||\r" +
               "EVN|A01|" + getCurrentTimestamp() + "|||SYSTEM\r" +
               "PID|1||" + patientId + "||" + patientName + "||" + dob + "|" + gender + "||||||||||||||||" + patientId + "\r" +
               "PV1|1|I|||||||||||||||||||||||||||||||||||||\r";
    }

    private String generateORUMessage(Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> resultData = (Map<String, Object>) data;

        String patientId = (String) resultData.getOrDefault("patientId", "UNKNOWN");
        String patientName = formatHL7Name((String) resultData.getOrDefault("patientName", "UNKNOWN^UNKNOWN"));

        StringBuilder message = new StringBuilder();
        message.append("MSH|^~\\&|MAYO|HOSPITAL|DEVICE|SYSTEM|").append(getCurrentTimestamp()).append("||ORU^R01|MSG").append(System.currentTimeMillis()).append("|P|2.5|||AL|NE|||||\r");
        message.append("PID|1||").append(patientId).append("||").append(patientName).append("||||||||||||||||").append(patientId).append("\r");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> observations = (java.util.List<Map<String, Object>>) resultData.get("observations");
        if (observations != null && !observations.isEmpty()) {
            message.append("OBR|1|||LAB^Laboratory|||").append(getCurrentTimestamp()).append("|||||||||||||||\r");
            for (int i = 0; i < observations.size(); i++) {
                Map<String, Object> obs = observations.get(i);
                message.append("OBX|").append(i + 1).append("||").append(obs.getOrDefault("testCode", "UNKNOWN")).append("^").append(obs.getOrDefault("testName", "Unknown Test")).append("||").append(obs.getOrDefault("value", "")).append("|").append(obs.getOrDefault("units", "")).append("|||").append(obs.getOrDefault("resultStatus", "F")).append("|||").append(getCurrentTimestamp()).append("|||||\r");
            }
        }

        return message.toString();
    }

    private String generateORMMessage(Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> orderData = (Map<String, Object>) data;

        String patientId = (String) orderData.getOrDefault("patientId", "UNKNOWN");
        String patientName = formatHL7Name((String) orderData.getOrDefault("patientName", "UNKNOWN^UNKNOWN"));
        String orderControl = (String) orderData.getOrDefault("orderControl", "NW"); // New Order

        StringBuilder message = new StringBuilder();
        message.append("MSH|^~\\&|MAYO|HOSPITAL|DEVICE|SYSTEM|").append(getCurrentTimestamp()).append("||ORM^O01|MSG").append(System.currentTimeMillis()).append("|P|2.5|||AL|NE|||||\r");
        message.append("PID|1||").append(patientId).append("||").append(patientName).append("||||||||||||||||").append(patientId).append("\r");
        message.append("ORC|").append(orderControl).append("|||||||||||||||||||||||||||||||||||||\r");

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> orderDetails = (java.util.List<Map<String, Object>>) orderData.get("orderDetails");
        if (orderDetails != null && !orderDetails.isEmpty()) {
            for (int i = 0; i < orderDetails.size(); i++) {
                Map<String, Object> detail = orderDetails.get(i);
                message.append("OBR|").append(i + 1).append("|||").append(detail.getOrDefault("universalServiceId", "UNKNOWN^Unknown Service")).append("|||").append(getCurrentTimestamp()).append("|||||||||||||||\r");
            }
        }

        return message.toString();
    }

    private String generateACKMessage(Object data) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ackData = (Map<String, Object>) data;

        String originalMessageId = (String) ackData.getOrDefault("originalMessageId", "UNKNOWN");
        String acknowledgmentCode = (String) ackData.getOrDefault("acknowledgmentCode", "AA"); // AA=Accept, AE=Error, AR=Reject
        String errorText = (String) ackData.getOrDefault("errorText", "");

        return "MSH|^~\\&|MAYO|HOSPITAL|DEVICE|SYSTEM|" + getCurrentTimestamp() + "||ACK^R01|ACK" + System.currentTimeMillis() + "|P|2.5|||AL|NE|||||\r" +
               "MSA|" + acknowledgmentCode + "|" + originalMessageId + "|" + errorText + "\r";
    }

    private String generateBasicHL7Message(String dataType, Object data) {
        return "MSH|^~\\&|MAYO|HOSPITAL|DEVICE|SYSTEM|" + getCurrentTimestamp() + "||" + dataType + "^R01|MSG" + System.currentTimeMillis() + "|P|2.5|||AL|NE|||||\r";
    }

    private String formatHL7Name(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "UNKNOWN^UNKNOWN";
        }
        // If already in HL7 format, return as is
        if (name.contains("^")) {
            return name;
        }
        // Convert "First Last" to "Last^First"
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return parts[1] + "^" + parts[0];
        }
        return name + "^UNKNOWN";
    }

    private String getCurrentTimestamp() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now());
    }
}