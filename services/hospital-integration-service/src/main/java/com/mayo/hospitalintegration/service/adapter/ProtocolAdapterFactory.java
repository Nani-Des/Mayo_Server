package com.mayo.hospitalintegration.service.adapter;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.mayo.hospitalintegration.service.adapter.ProtocolAdapter.AdapterException;

/**
 * Factory for creating and managing protocol adapters
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProtocolAdapterFactory {

    private final HL7Adapter hl7Adapter;
    private final FHIRAdapter fhirAdapter;
    private final DICOMAdapter dicomAdapter;

    // Cache for adapter instances
    private final Map<DataTransferSession.TransferProtocol, ProtocolAdapter> adapterCache = new ConcurrentHashMap<>();

    /**
     * Get adapter for the specified protocol
     * @param protocol The transfer protocol
     * @return ProtocolAdapter instance
     * @throws AdapterException if no adapter found for protocol
     */
    public ProtocolAdapter getAdapter(DataTransferSession.TransferProtocol protocol) throws AdapterException {
        // Check cache first
        ProtocolAdapter adapter = adapterCache.get(protocol);
        if (adapter != null) {
            return adapter;
        }

        // Create and cache adapter
        switch (protocol) {
            case HL7:
                adapter = hl7Adapter;
                break;
            case FHIR:
                adapter = fhirAdapter;
                break;
            case DICOM:
                adapter = dicomAdapter;
                break;
            default:
                throw new AdapterException("No adapter available for protocol: " + protocol);
        }

        adapterCache.put(protocol, adapter);
        log.debug("Created adapter for protocol: {}", protocol);

        return adapter;
    }

    /**
     * Get all supported protocols
     * @return Array of supported protocols
     */
    public DataTransferSession.TransferProtocol[] getSupportedProtocols() {
        return new DataTransferSession.TransferProtocol[] {
            DataTransferSession.TransferProtocol.HL7,
            DataTransferSession.TransferProtocol.FHIR,
            DataTransferSession.TransferProtocol.DICOM
        };
    }

    /**
     * Check if protocol is supported
     * @param protocol The protocol to check
     * @return true if supported
     */
    public boolean isProtocolSupported(DataTransferSession.TransferProtocol protocol) {
        try {
            getAdapter(protocol);
            return true;
        } catch (AdapterException e) {
            return false;
        }
    }

    /**
     * Get adapter capabilities for a protocol
     * @param protocol The protocol
     * @return Map of capabilities
     */
    public Map<String, Object> getAdapterCapabilities(DataTransferSession.TransferProtocol protocol) {
        Map<String, Object> capabilities = new ConcurrentHashMap<>();

        try {
            ProtocolAdapter adapter = getAdapter(protocol);

            capabilities.put("protocol", protocol.name());
            capabilities.put("supported", true);

            // Add protocol-specific capabilities
            switch (protocol) {
                case HL7:
                    capabilities.put("messageTypes", new String[]{"ADT", "ORU", "ORM", "ACK"});
                    capabilities.put("mlpSupport", true);
                    capabilities.put("validation", true);
                    capabilities.put("transformation", true);
                    break;
                case FHIR:
                    capabilities.put("resourceTypes", new String[]{"Patient", "Observation", "Condition", "MedicationRequest"});
                    capabilities.put("oauthSupport", true);
                    capabilities.put("restClient", true);
                    capabilities.put("schemaValidation", true);
                    break;
                case DICOM:
                    capabilities.put("modalities", new String[]{"CT", "MR", "US", "CR", "DX", "SR"});
                    capabilities.put("networkProtocol", true);
                    capabilities.put("metadataExtraction", true);
                    capabilities.put("imageProcessing", true);
                    break;
            }

        } catch (AdapterException e) {
            capabilities.put("protocol", protocol.name());
            capabilities.put("supported", false);
            capabilities.put("error", e.getMessage());
        }

        return capabilities;
    }

    /**
     * Clear adapter cache
     */
    public void clearCache() {
        adapterCache.clear();
        log.info("Adapter cache cleared");
    }

    /**
     * Get cache statistics
     * @return Map with cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedAdapters", adapterCache.size());
        stats.put("supportedProtocols", getSupportedProtocols().length);
        return stats;
    }
}