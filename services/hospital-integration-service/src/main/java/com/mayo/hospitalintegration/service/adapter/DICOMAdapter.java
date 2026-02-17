package com.mayo.hospitalintegration.service.adapter;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.service.protocol.DICOMProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DICOM protocol adapter for parsing, validating, and transforming DICOM data
 */
@Component
@Slf4j
public class DICOMAdapter implements ProtocolAdapter {

    @Autowired
    private DICOMProtocolHandler dicomProtocolHandler;

    private static final int DICOM_PREAMBLE_SIZE = 128;
    private static final String DICOM_PREFIX = "DICM";

    @Override
    public DataTransferSession.TransferProtocol getProtocol() {
        return DataTransferSession.TransferProtocol.DICOM;
    }

    @Override
    public ParsedData parse(String rawData) throws AdapterException {
        // DICOM is binary, so rawData should be base64 or we need to handle byte arrays
        // For this implementation, assume rawData is a file path or handle as binary data
        throw new AdapterException("DICOM parsing requires binary data input");
    }

    /**
     * Parse DICOM data from byte array
     */
    public ParsedData parse(byte[] dicomData) throws AdapterException {
        try {
            log.debug("Parsing DICOM data, size: {} bytes", dicomData.length);

            // Validate DICOM format
            if (!isValidDICOM(dicomData)) {
                throw new AdapterException("Invalid DICOM format");
            }

            // Parse DICOM file
            Map<String, Object> parsedData = parseDICOMFile(dicomData);

            // Extract metadata
            Map<String, Object> metadata = extractDICOMMetadata(dicomData, parsedData);

            return new ParsedData(parsedData, metadata);

        } catch (Exception e) {
            log.error("Failed to parse DICOM data", e);
            throw new AdapterException("DICOM parsing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ValidationResult validate(ParsedData parsedData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> dicomData = (Map<String, Object>) parsedData.getStructuredData();

            Map<String, String> fieldErrors = new HashMap<>();

            // Validate required DICOM elements
            if (!dicomData.containsKey("SOPClassUID")) {
                fieldErrors.put("SOPClassUID", "Missing SOP Class UID");
            }
            if (!dicomData.containsKey("SOPInstanceUID")) {
                fieldErrors.put("SOPInstanceUID", "Missing SOP Instance UID");
            }
            if (!dicomData.containsKey("StudyInstanceUID")) {
                fieldErrors.put("StudyInstanceUID", "Missing Study Instance UID");
            }
            if (!dicomData.containsKey("SeriesInstanceUID")) {
                fieldErrors.put("SeriesInstanceUID", "Missing Series Instance UID");
            }

            // Validate image-specific elements for image IODs
            String sopClassUID = (String) dicomData.get("SOPClassUID");
            if (isImageSOPClass(sopClassUID)) {
                if (!dicomData.containsKey("Rows")) {
                    fieldErrors.put("Rows", "Missing Rows for image IOD");
                }
                if (!dicomData.containsKey("Columns")) {
                    fieldErrors.put("Columns", "Missing Columns for image IOD");
                }
                if (!dicomData.containsKey("BitsAllocated")) {
                    fieldErrors.put("BitsAllocated", "Missing BitsAllocated for image IOD");
                }
            }

            if (fieldErrors.isEmpty()) {
                return ValidationResult.valid();
            } else {
                return ValidationResult.invalid("DICOM validation failed", fieldErrors);
            }

        } catch (Exception e) {
            log.error("DICOM validation error", e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }

    @Override
    public TransformedData transform(ParsedData parsedData) throws AdapterException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> dicomData = (Map<String, Object>) parsedData.getStructuredData();

            // Determine modality and transform accordingly
            String modality = (String) dicomData.get("Modality");
            Object internalData;
            String dataType;

            if ("CT".equals(modality) || "MR".equals(modality) || "US".equals(modality) ||
                "CR".equals(modality) || "DX".equals(modality)) {
                internalData = transformMedicalImage(dicomData);
                dataType = "MEDICAL_IMAGE";
            } else if ("SR".equals(modality)) {
                internalData = transformStructuredReport(dicomData);
                dataType = "STRUCTURED_REPORT";
            } else {
                internalData = dicomData; // Pass through for other modalities
                dataType = "DICOM_DATA";
            }

            Map<String, Object> metadata = new HashMap<>(parsedData.getMetadata());
            metadata.put("transformedAt", System.currentTimeMillis());
            metadata.put("dataType", dataType);

            return new TransformedData(internalData, dataType, metadata);

        } catch (Exception e) {
            log.error("DICOM transformation error", e);
            throw new AdapterException("DICOM transformation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String serialize(TransformedData internalData) throws AdapterException {
        // DICOM serialization to binary format - simplified implementation
        throw new AdapterException("DICOM serialization to binary format not implemented");
    }

    /**
     * Perform DICOM network operations (C-FIND, C-MOVE, C-STORE) using the protocol handler
     */
    public DICOMNetworkResult performNetworkOperation(String host, int port, String callingAET,
            String calledAET, String operation, Map<String, Object> parameters) throws AdapterException {
        try {
            // Create configuration for the protocol handler
            Map<String, Object> config = new HashMap<>();
            config.put("host", host);
            config.put("port", String.valueOf(port));
            config.put("callingAET", callingAET);
            config.put("calledAET", calledAET);
            config.put("mode", "SCU"); // Service Class User for network operations

            // For C-STORE SCP, we would set mode to "SCP"
            if ("SCP".equals(operation)) {
                config.put("mode", "SCP");
            }

            // Create a mock session for the protocol handler
            DataTransferSession session = new DataTransferSession();
            session.setSessionId(UUID.randomUUID().toString());
            session.setProtocol(DataTransferSession.TransferProtocol.DICOM);

            // Execute transfer using protocol handler
            var result = dicomProtocolHandler.executeTransfer(session, config);

            return new DICOMNetworkResult(result.isSuccess(), result.getErrorMessage(), result);

        } catch (Exception e) {
            log.error("DICOM network operation failed", e);
            throw new AdapterException("Network operation failed: " + e.getMessage(), e);
        }
    }

    private boolean isValidDICOM(byte[] data) {
        if (data.length < DICOM_PREAMBLE_SIZE + 4) {
            return false;
        }

        // Check DICOM prefix
        String prefix = new String(data, DICOM_PREAMBLE_SIZE, 4);
        return DICOM_PREFIX.equals(prefix);
    }

    private Map<String, Object> parseDICOMFile(byte[] data) throws IOException {
        Map<String, Object> result = new HashMap<>();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Skip preamble
        buffer.position(DICOM_PREAMBLE_SIZE + 4);

        // Parse DICOM elements (simplified)
        while (buffer.hasRemaining()) {
            try {
                int group = buffer.getShort() & 0xFFFF;
                int element = buffer.getShort() & 0xFFFF;
                String vr = new String(new byte[]{buffer.get(), buffer.get()});

                int length;
                if ("OB".equals(vr) || "OF".equals(vr) || "OW".equals(vr) || "SQ".equals(vr)) {
                    buffer.getInt(); // Reserved
                    length = buffer.getInt();
                } else {
                    length = buffer.getShort() & 0xFFFF;
                }

                if (length > 0 && length < buffer.remaining()) {
                    byte[] valueBytes = new byte[length];
                    buffer.get(valueBytes);

                    String tag = String.format("(%04X,%04X)", group, element);
                    String value = parseDICOMValue(vr, valueBytes);
                    result.put(tag, value);

                    // Also store by name for common tags
                    String name = getDICOMTagName(group, element);
                    if (name != null) {
                        result.put(name, value);
                    }
                } else {
                    break; // End of data or invalid length
                }

            } catch (Exception e) {
                log.warn("Error parsing DICOM element", e);
                break;
            }
        }

        return result;
    }

    private String parseDICOMValue(String vr, byte[] valueBytes) {
        try {
            switch (vr) {
                case "UI": // Unique Identifier
                case "SH": // Short String
                case "LO": // Long String
                case "PN": // Person Name
                case "CS": // Code String
                    return new String(valueBytes, "UTF-8").trim();
                case "IS": // Integer String
                case "DS": // Decimal String
                    return new String(valueBytes, "UTF-8").trim();
                case "US": // Unsigned Short
                    if (valueBytes.length >= 2) {
                        return String.valueOf(ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF);
                    }
                    break;
                case "UL": // Unsigned Long
                    if (valueBytes.length >= 4) {
                        return String.valueOf(ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL);
                    }
                    break;
                default:
                    return "[Binary data: " + valueBytes.length + " bytes]";
            }
        } catch (Exception e) {
            log.warn("Error parsing DICOM value for VR: " + vr, e);
        }
        return "[Parse error]";
    }

    private String getDICOMTagName(int group, int element) {
        // Common DICOM tags
        if (group == 0x0002) {
            switch (element) {
                case 0x0002: return "MediaStorageSOPClassUID";
                case 0x0003: return "MediaStorageSOPInstanceUID";
                case 0x0010: return "TransferSyntaxUID";
            }
        } else if (group == 0x0008) {
            switch (element) {
                case 0x0005: return "SpecificCharacterSet";
                case 0x0008: return "ImageType";
                case 0x0016: return "SOPClassUID";
                case 0x0018: return "SOPInstanceUID";
                case 0x0020: return "StudyDate";
                case 0x0030: return "StudyTime";
                case 0x0050: return "AccessionNumber";
                case 0x0060: return "Modality";
                case 0x0080: return "InstitutionName";
                case 0x0081: return "InstitutionAddress";
                case 0x0090: return "ReferringPhysicianName";
                case 0x1030: return "StudyDescription";
                case 0x1040: return "InstitutionalDepartmentName";
                case 0x1050: return "PerformingPhysicianName";
                case 0x1080: return "AdmittingDiagnosesDescription";
                case 0x1090: return "ManufacturerModelName";
            }
        } else if (group == 0x0010) {
            switch (element) {
                case 0x0010: return "PatientName";
                case 0x0020: return "PatientID";
                case 0x0030: return "PatientBirthDate";
                case 0x0040: return "PatientSex";
                case 0x1010: return "PatientAge";
                case 0x1030: return "PatientWeight";
            }
        } else if (group == 0x0020) {
            switch (element) {
                case 0x000D: return "StudyInstanceUID";
                case 0x000E: return "SeriesInstanceUID";
                case 0x0010: return "StudyID";
                case 0x0011: return "SeriesNumber";
                case 0x0013: return "InstanceNumber";
            }
        } else if (group == 0x0028) {
            switch (element) {
                case 0x0010: return "Rows";
                case 0x0011: return "Columns";
                case 0x0030: return "PixelSpacing";
                case 0x0100: return "BitsAllocated";
                case 0x0101: return "BitsStored";
                case 0x0102: return "HighBit";
                case 0x0103: return "PixelRepresentation";
                case 0x1052: return "RescaleIntercept";
                case 0x1053: return "RescaleSlope";
            }
        }
        return null;
    }

    private Map<String, Object> extractDICOMMetadata(byte[] data, Map<String, Object> parsedData) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("protocol", "DICOM");
        metadata.put("fileSize", data.length);
        metadata.put("modality", parsedData.get("Modality"));
        metadata.put("studyInstanceUID", parsedData.get("StudyInstanceUID"));
        metadata.put("seriesInstanceUID", parsedData.get("SeriesInstanceUID"));
        metadata.put("sopInstanceUID", parsedData.get("SOPInstanceUID"));
        metadata.put("patientID", parsedData.get("PatientID"));
        metadata.put("studyDate", parsedData.get("StudyDate"));
        return metadata;
    }

    private boolean isImageSOPClass(String sopClassUID) {
        return sopClassUID != null && (
            sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.2") || // CT Image
            sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.4") || // MR Image
            sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.6") || // US Image
            sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.1") || // CR Image
            sopClassUID.startsWith("1.2.840.10008.5.1.4.1.1.2.1")   // DX Image
        );
    }

    private Object transformMedicalImage(Map<String, Object> dicomData) {
        Map<String, Object> imageData = new HashMap<>();

        // Extract patient information
        imageData.put("patientId", dicomData.get("PatientID"));
        imageData.put("patientName", dicomData.get("PatientName"));
        imageData.put("patientBirthDate", dicomData.get("PatientBirthDate"));
        imageData.put("patientSex", dicomData.get("PatientSex"));

        // Extract study information
        imageData.put("studyInstanceUID", dicomData.get("StudyInstanceUID"));
        imageData.put("studyDate", dicomData.get("StudyDate"));
        imageData.put("studyTime", dicomData.get("StudyTime"));
        imageData.put("studyDescription", dicomData.get("StudyDescription"));
        imageData.put("accessionNumber", dicomData.get("AccessionNumber"));
        imageData.put("modality", dicomData.get("Modality"));

        // Extract series information
        imageData.put("seriesInstanceUID", dicomData.get("SeriesInstanceUID"));
        imageData.put("seriesNumber", dicomData.get("SeriesNumber"));
        imageData.put("seriesDescription", dicomData.get("SeriesDescription"));

        // Extract image information
        imageData.put("sopInstanceUID", dicomData.get("SOPInstanceUID"));
        imageData.put("instanceNumber", dicomData.get("InstanceNumber"));
        imageData.put("rows", dicomData.get("Rows"));
        imageData.put("columns", dicomData.get("Columns"));
        imageData.put("bitsAllocated", dicomData.get("BitsAllocated"));
        imageData.put("bitsStored", dicomData.get("BitsStored"));
        imageData.put("pixelRepresentation", dicomData.get("PixelRepresentation"));

        // Image processing metadata
        imageData.put("imageType", dicomData.get("ImageType"));
        imageData.put("pixelSpacing", dicomData.get("PixelSpacing"));
        imageData.put("rescaleIntercept", dicomData.get("RescaleIntercept"));
        imageData.put("rescaleSlope", dicomData.get("RescaleSlope"));

        return imageData;
    }

    private Object transformStructuredReport(Map<String, Object> dicomData) {
        Map<String, Object> reportData = new HashMap<>();

        // SR-specific transformation
        reportData.put("patientId", dicomData.get("PatientID"));
        reportData.put("studyInstanceUID", dicomData.get("StudyInstanceUID"));
        reportData.put("seriesInstanceUID", dicomData.get("SeriesInstanceUID"));
        reportData.put("sopInstanceUID", dicomData.get("SOPInstanceUID"));

        // Content would be in structured form - simplified here
        reportData.put("reportType", "Structured Report");
        reportData.put("completionFlag", dicomData.get("CompletionFlag"));
        reportData.put("verificationFlag", dicomData.get("VerificationFlag"));

        return reportData;
    }

    // Network operation methods - now properly implemented using protocol handler
    private boolean negotiateAssociation(DataOutputStream output, DataInputStream input,
                                       String callingAET, String calledAET) throws AdapterException {
        try {
            // Delegate to protocol handler for proper DICOM association
            // Since the handler works with sessions, we'll implement basic negotiation here
            // In a full implementation, this would use the handler's association logic
            throw new AdapterException("Use DICOMProtocolHandler for network operations");
        } catch (Exception e) {
            throw new AdapterException("DICOM association negotiation failed: " + e.getMessage(), e);
        }
    }


    /**
     * Result of DICOM network operations
     */
    public static class DICOMNetworkResult {
        private final boolean success;
        private final String errorMessage;
        private final Object result;

        public DICOMNetworkResult(boolean success, String errorMessage, Object result) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.result = result;
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Object getResult() { return result; }
    }
}