package com.mayo.hospitalintegration.service.adapter;

import com.mayo.hospitalintegration.entity.DataTransferSession;

import java.util.Map;

/**
 * Interface for protocol adapters that handle parsing, validation, and transformation
 * of healthcare data in different formats (HL7, FHIR, DICOM)
 */
public interface ProtocolAdapter {

    /**
     * Returns the protocol type this adapter supports
     */
    DataTransferSession.TransferProtocol getProtocol();

    /**
     * Parses raw data into structured format
     * @param rawData The raw data to parse
     * @return ParsedData containing structured data
     * @throws AdapterException if parsing fails
     */
    ParsedData parse(String rawData) throws AdapterException;

    /**
     * Validates parsed data against protocol specifications
     * @param parsedData The parsed data to validate
     * @return ValidationResult
     */
    ValidationResult validate(ParsedData parsedData);

    /**
     * Transforms parsed data to internal format
     * @param parsedData The parsed data to transform
     * @return TransformedData in internal format
     * @throws AdapterException if transformation fails
     */
    TransformedData transform(ParsedData parsedData) throws AdapterException;

    /**
     * Serializes internal data back to protocol format
     * @param internalData The internal data to serialize
     * @return String in protocol format
     * @throws AdapterException if serialization fails
     */
    String serialize(TransformedData internalData) throws AdapterException;

    /**
     * Result of data parsing
     */
    class ParsedData {
        private final Object structuredData;
        private final Map<String, Object> metadata;

        public ParsedData(Object structuredData, Map<String, Object> metadata) {
            this.structuredData = structuredData;
            this.metadata = metadata;
        }

        public Object getStructuredData() { return structuredData; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Result of data transformation
     */
    class TransformedData {
        private final Object internalData;
        private final String dataType;
        private final Map<String, Object> metadata;

        public TransformedData(Object internalData, String dataType, Map<String, Object> metadata) {
            this.internalData = internalData;
            this.dataType = dataType;
            this.metadata = metadata;
        }

        public Object getInternalData() { return internalData; }
        public String getDataType() { return dataType; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Result of validation
     */
    class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Map<String, String> fieldErrors;

        public ValidationResult(boolean valid, String errorMessage, Map<String, String> fieldErrors) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.fieldErrors = fieldErrors;
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, String> getFieldErrors() { return fieldErrors; }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, null);
        }

        public static ValidationResult invalid(String message, Map<String, String> fieldErrors) {
            return new ValidationResult(false, message, fieldErrors);
        }
    }

    /**
     * Exception thrown by adapter operations
     */
    class AdapterException extends Exception {
        public AdapterException(String message) {
            super(message);
        }

        public AdapterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}