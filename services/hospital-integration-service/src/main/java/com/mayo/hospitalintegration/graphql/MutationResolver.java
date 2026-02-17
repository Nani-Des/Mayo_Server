package com.mayo.hospitalintegration.graphql;

import com.mayo.common.core.enums.DeviceType;
import com.mayo.hospitalintegration.dto.DataTransferSessionDto;
import com.mayo.hospitalintegration.dto.HospitalDeviceDto;
import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import com.mayo.hospitalintegration.service.DataTransferService;
import com.mayo.hospitalintegration.service.DeviceRegistrationService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MutationResolver implements GraphQLMutationResolver {

    private final DeviceRegistrationService deviceRegistrationService;
    private final DataTransferService dataTransferService;

    public HospitalDeviceDto registerDevice(RegisterDeviceInput input) {
        log.debug("GraphQL mutation: registerDevice for deviceId: {}", input.getDeviceId());

        HospitalDeviceDto deviceDto = new HospitalDeviceDto();
        deviceDto.setHospitalId(UUID.fromString(input.getHospitalId()));
        deviceDto.setDeviceId(input.getDeviceId());
        deviceDto.setDeviceType(input.getDeviceType());
        deviceDto.setDeviceName(input.getDeviceName());
        deviceDto.setManufacturer(input.getManufacturer());
        deviceDto.setModel(input.getModel());
        deviceDto.setSerialNumber(input.getSerialNumber());
        deviceDto.setFirmwareVersion(input.getFirmwareVersion());
        deviceDto.setIpAddress(input.getIpAddress());
        deviceDto.setMacAddress(input.getMacAddress());
        deviceDto.setConnectionType(input.getConnectionType());
        deviceDto.setSupportedProtocols(input.getSupportedProtocols());
        deviceDto.setSupportedDataFormats(input.getSupportedDataFormats());

        return deviceRegistrationService.registerDevice(deviceDto);
    }

    public DataTransferSessionDto transferData(TransferDataInput input) {
        log.debug("GraphQL mutation: transferData for hospitalId: {}, deviceId: {}",
                 input.getHospitalId(), input.getDeviceId());

        DataTransferSessionDto sessionDto = new DataTransferSessionDto();
        sessionDto.setHospitalId(UUID.fromString(input.getHospitalId()));
        sessionDto.setDeviceId(UUID.fromString(input.getDeviceId()));
        sessionDto.setTransferType(input.getTransferType());
        sessionDto.setDataType(input.getDataType());
        sessionDto.setProtocol(input.getProtocol());
        sessionDto.setTotalRecords(input.getTotalRecords());
        sessionDto.setDataSizeBytes(input.getDataSizeBytes());
        sessionDto.setSourceEndpoint(input.getSourceEndpoint());
        sessionDto.setDestinationEndpoint(input.getDestinationEndpoint());
        if (input.getInitiatedBy() != null) {
            sessionDto.setInitiatedBy(UUID.fromString(input.getInitiatedBy()));
        }
        sessionDto.setMetadata(input.getMetadata());

        return dataTransferService.initiateTransfer(sessionDto);
    }

    // Input classes for GraphQL
    public static class RegisterDeviceInput {
        private String hospitalId;
        private String deviceId;
        private DeviceType deviceType;
        private String deviceName;
        private String manufacturer;
        private String model;
        private String serialNumber;
        private String firmwareVersion;
        private String ipAddress;
        private String macAddress;
        private HospitalDevice.ConnectionType connectionType;
        private String supportedProtocols;
        private String supportedDataFormats;

        // Getters and setters
        public String getHospitalId() { return hospitalId; }
        public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public DeviceType getDeviceType() { return deviceType; }
        public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }

        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

        public String getFirmwareVersion() { return firmwareVersion; }
        public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public String getMacAddress() { return macAddress; }
        public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

        public HospitalDevice.ConnectionType getConnectionType() { return connectionType; }
        public void setConnectionType(HospitalDevice.ConnectionType connectionType) { this.connectionType = connectionType; }

        public String getSupportedProtocols() { return supportedProtocols; }
        public void setSupportedProtocols(String supportedProtocols) { this.supportedProtocols = supportedProtocols; }

        public String getSupportedDataFormats() { return supportedDataFormats; }
        public void setSupportedDataFormats(String supportedDataFormats) { this.supportedDataFormats = supportedDataFormats; }
    }

    public static class TransferDataInput {
        private String hospitalId;
        private String deviceId;
        private DataTransferSession.TransferType transferType;
        private DataTransferSession.DataType dataType;
        private DataTransferSession.TransferProtocol protocol;
        private Long totalRecords;
        private Long dataSizeBytes;
        private String sourceEndpoint;
        private String destinationEndpoint;
        private String initiatedBy;
        private String metadata;

        // Getters and setters
        public String getHospitalId() { return hospitalId; }
        public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public DataTransferSession.TransferType getTransferType() { return transferType; }
        public void setTransferType(DataTransferSession.TransferType transferType) { this.transferType = transferType; }

        public DataTransferSession.DataType getDataType() { return dataType; }
        public void setDataType(DataTransferSession.DataType dataType) { this.dataType = dataType; }

        public DataTransferSession.TransferProtocol getProtocol() { return protocol; }
        public void setProtocol(DataTransferSession.TransferProtocol protocol) { this.protocol = protocol; }

        public Long getTotalRecords() { return totalRecords; }
        public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }

        public Long getDataSizeBytes() { return dataSizeBytes; }
        public void setDataSizeBytes(Long dataSizeBytes) { this.dataSizeBytes = dataSizeBytes; }

        public String getSourceEndpoint() { return sourceEndpoint; }
        public void setSourceEndpoint(String sourceEndpoint) { this.sourceEndpoint = sourceEndpoint; }

        public String getDestinationEndpoint() { return destinationEndpoint; }
        public void setDestinationEndpoint(String destinationEndpoint) { this.destinationEndpoint = destinationEndpoint; }

        public String getInitiatedBy() { return initiatedBy; }
        public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }
}