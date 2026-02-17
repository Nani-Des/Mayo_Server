package com.mayo.hospitalintegration.service.protocol;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Protocol auto-detection engine that analyzes incoming data streams
 * to automatically identify HL7, FHIR, and DICOM protocols
 */
@Component
@Slf4j
public class ProtocolDetector {

    // HL7 patterns - simplified MSH segment detection
    private static final Pattern HL7_MSH_PATTERN = Pattern.compile(
        "^MSH\\|\\^~\\\\&\\|.*\\|.*\\|.*\\|.*\\|[^|]*\\|\\|[^|]*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\|"
    );

    // FHIR patterns - JSON with resourceType
    private static final Pattern FHIR_JSON_PATTERN = Pattern.compile(
        "\"resourceType\"\\s*:\\s*\"[^\"]+\""
    );

    // FHIR XML pattern
    private static final Pattern FHIR_XML_PATTERN = Pattern.compile(
        "<[^>]*resourceType[^>]*>[^<]*</[^>]*>"
    );

    // DICOM PDU header patterns (simplified)
    private static final Pattern DICOM_PDU_PATTERN = Pattern.compile(
        "^\\x01\\x00.*|^\\x02\\x00.*|^\\x03\\x00.*|^\\x04\\x00.*|^\\x05\\x00.*|^\\x06\\x00.*|^\\x07\\x00.*"
    );

    /**
     * Detects the protocol from the given data stream
     * @param data The raw data bytes to analyze
     * @return Detected protocol or null if unknown
     */
    public DataTransferSession.TransferProtocol detectProtocol(byte[] data) {
        if (data == null || data.length == 0) {
            log.debug("Empty data provided for protocol detection");
            return null;
        }

        String dataString = new String(data).trim();

        // Check for HL7 first (most specific pattern)
        if (isHL7Message(dataString)) {
            log.info("Detected HL7 protocol");
            return DataTransferSession.TransferProtocol.HL7;
        }

        // Check for FHIR JSON
        if (isFHIRJSON(dataString)) {
            log.info("Detected FHIR JSON protocol");
            return DataTransferSession.TransferProtocol.FHIR;
        }

        // Check for FHIR XML
        if (isFHIRXML(dataString)) {
            log.info("Detected FHIR XML protocol");
            return DataTransferSession.TransferProtocol.FHIR;
        }

        // Check for DICOM PDU
        if (isDICOMPDU(data)) {
            log.info("Detected DICOM protocol");
            return DataTransferSession.TransferProtocol.DICOM;
        }

        log.debug("No known protocol detected in data stream");
        return null;
    }

    /**
     * Detects HL7 v2.x messages by checking for MSH segment patterns
     */
    private boolean isHL7Message(String data) {
        return HL7_MSH_PATTERN.matcher(data).find();
    }

    /**
     * Detects FHIR JSON resources by checking for resourceType field
     */
    private boolean isFHIRJSON(String data) {
        return FHIR_JSON_PATTERN.matcher(data).find();
    }

    /**
     * Detects FHIR XML resources by checking for resourceType element
     */
    private boolean isFHIRXML(String data) {
        return FHIR_XML_PATTERN.matcher(data).find();
    }

    /**
     * Detects DICOM network protocol by checking PDU headers
     * DICOM uses specific PDU types with reserved bytes
     */
    private boolean isDICOMPDU(byte[] data) {
        if (data.length < 6) {
            return false;
        }

        // Check for DICOM PDU type (first byte) and reserved byte (second byte = 0x00)
        int pduType = data[0] & 0xFF;
        int reserved = data[1] & 0xFF;

        // Valid DICOM PDU types: A-ASSOCIATE-RQ (0x01), A-ASSOCIATE-AC (0x02),
        // A-ASSOCIATE-RJ (0x03), P-DATA-TF (0x04), A-RELEASE-RQ (0x05),
        // A-RELEASE-RP (0x06), A-ABORT (0x07)
        return reserved == 0x00 &&
               (pduType >= 0x01 && pduType <= 0x07);
    }

    /**
     * Gets a human-readable description of the detection algorithm for a protocol
     */
    public String getDetectionAlgorithm(DataTransferSession.TransferProtocol protocol) {
        switch (protocol) {
            case HL7:
                return "HL7 v2.x detection: Matches MSH segment pattern with field separators (^~\\&|) and message type structure";
            case FHIR:
                return "FHIR detection: Identifies resourceType field in JSON format or resourceType element in XML format";
            case DICOM:
                return "DICOM detection: Recognizes PDU (Protocol Data Unit) headers with valid PDU types (0x01-0x07) and reserved bytes";
            default:
                return "Unknown protocol detection algorithm";
        }
    }
}