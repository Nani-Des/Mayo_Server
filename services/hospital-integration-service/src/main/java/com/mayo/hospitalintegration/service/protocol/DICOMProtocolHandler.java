package com.mayo.hospitalintegration.service.protocol;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DICOM Protocol Exceptions
 */
class DICOMException extends Exception {
    public DICOMException(String message) {
        super(message);
    }

    public DICOMException(String message, Throwable cause) {
        super(message, cause);
    }
}

class DICOMAssociationException extends DICOMException {
    public DICOMAssociationException(String message) {
        super(message);
    }

    public DICOMAssociationException(String message, Throwable cause) {
        super(message, cause);
    }
}

class DICOMProtocolException extends DICOMException {
    public DICOMProtocolException(String message) {
        super(message);
    }

    public DICOMProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}

class DICOMTransferSyntaxException extends DICOMException {
    public DICOMTransferSyntaxException(String message) {
        super(message);
    }
}

/**
 * DICOM protocol handler for medical imaging data transfer
 */
@Component
@Slf4j
public class DICOMProtocolHandler extends AbstractProtocolHandler {

    // DICOM PDU Types
    private static final byte PDU_A_ASSOCIATE_RQ = 0x01;
    private static final byte PDU_A_ASSOCIATE_AC = 0x02;
    private static final byte PDU_A_ASSOCIATE_RJ = 0x03;
    private static final byte PDU_P_DATA_TF = 0x04;
    private static final byte PDU_A_RELEASE_RQ = 0x05;
    private static final byte PDU_A_RELEASE_RP = 0x06;
    private static final byte PDU_A_ABORT = 0x07;

    // DICOM Command Field Types
    private static final int CMD_C_STORE_RQ = 0x0001;
    private static final int CMD_C_STORE_RSP = 0x8001;

    // Configuration keys
    private static final String CONFIG_HOST = "host";
    private static final String CONFIG_PORT = "port";
    private static final String CONFIG_AET = "callingAET";
    private static final String CONFIG_CALLEDAET = "calledAET";
    private static final String CONFIG_TIMEOUT = "timeout";
    private static final String CONFIG_MODE = "mode"; // "SCU" or "SCP"
    private static final String CONFIG_STORAGE_DIR = "storageDirectory";
    private static final String CONFIG_SUPPORTED_TS = "supportedTransferSyntaxes";

    // Default values
    private static final String DEFAULT_STORAGE_DIR = "/tmp/dicom";
    private static final int DEFAULT_TIMEOUT = 30000;

    // Configuration properties
    @Value("${hospital-integration.dicom.ae-title:MAYO_HOSPITAL}")
    private String defaultAETitle;

    @Value("${hospital-integration.dicom.port:104}")
    private int defaultPort;

    @Value("${hospital-integration.dicom.storage-directory:/tmp/dicom}")
    private String defaultStorageDir;

    @Value("${hospital-integration.dicom.timeout:30000}")
    private int defaultTimeout;

    // Active associations (for SCP mode)
    private final Map<String, DICOMAssociation> activeAssociations = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    @Override
    public DataTransferSession.TransferProtocol getProtocol() {
        return DataTransferSession.TransferProtocol.DICOM;
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        String mode = config.getOrDefault(CONFIG_MODE, "SCU").toString().toUpperCase();

        if ("SCU".equals(mode)) {
            ValidationResult required = validateRequiredConfig(config, CONFIG_HOST, CONFIG_PORT,
                CONFIG_AET, CONFIG_CALLEDAET);
            if (!required.isValid()) {
                return required;
            }
        } else if ("SCP".equals(mode)) {
            ValidationResult required = validateRequiredConfig(config, CONFIG_PORT, CONFIG_CALLEDAET);
            if (!required.isValid()) {
                return required;
            }
        } else {
            return ValidationResult.invalid("Invalid mode: " + mode + ". Must be SCU or SCP");
        }

        // Validate port
        try {
            String portStr = config.getOrDefault(CONFIG_PORT, String.valueOf(defaultPort)).toString();
            int port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                return ValidationResult.invalid("Invalid port number: " + port);
            }
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("Port must be a valid number");
        }

        return ValidationResult.valid();
    }

    @Override
    public TransferResult executeTransfer(DataTransferSession session, Map<String, Object> config) {
        String mode = config.getOrDefault(CONFIG_MODE, "SCU").toString().toUpperCase();

        try {
            if ("SCP".equals(mode)) {
                return executeSCP(session, config);
            } else {
                return executeSCU(session, config);
            }
        } catch (DICOMException e) {
            log.error("DICOM protocol error during transfer: {}", session.getSessionId(), e);
            return new TransferResult(false, "DICOM protocol error: " + e.getMessage(), null, 0, 0);
        }
    }

    private TransferResult executeSCU(DataTransferSession session, Map<String, Object> config) throws DICOMException, DICOMProtocolException {
        String sessionId = session.getSessionId();
        updateTransferStatus(sessionId, TransferStatus.CONNECTING);

        String host = config.get(CONFIG_HOST).toString();
        int port = Integer.parseInt(config.getOrDefault(CONFIG_PORT, String.valueOf(defaultPort)).toString());
        String callingAET = config.getOrDefault(CONFIG_AET, defaultAETitle).toString();
        String calledAET = config.get(CONFIG_CALLEDAET).toString();
        int timeout = config.containsKey(CONFIG_TIMEOUT) ?
            Integer.parseInt(config.get(CONFIG_TIMEOUT).toString()) : defaultTimeout;

        Socket socket = null;
        DataInputStream inputStream = null;
        DataOutputStream outputStream = null;

        try {
            // Establish DICOM association
            socket = new Socket(host, port);
            socket.setSoTimeout(timeout);

            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());

            updateTransferStatus(sessionId, TransferStatus.TRANSFERRING);

            // DICOM association negotiation
            DICOMAssociation association = negotiateAssociationSCU(outputStream, inputStream,
                callingAET, calledAET);

            // Execute C-STORE operation for image transfer
            TransferResult result = executeCStoreSCU(session, association);

            // Release association
            releaseAssociationSCU(association);

            updateTransferStatus(sessionId, result.isSuccess() ? TransferStatus.COMPLETED : TransferStatus.FAILED);
            removeActiveTransfer(sessionId);

            return result;

        } catch (IOException e) {
            log.error("DICOM transfer failed for session: {}", sessionId, e);
            updateTransferStatus(sessionId, TransferStatus.FAILED);
            removeActiveTransfer(sessionId);
            return new TransferResult(false, "DICOM transfer failed: " + e.getMessage(), null, 0, 0);
        } finally {
            closeResources(socket, inputStream, outputStream);
        }
    }

    private TransferResult executeSCP(DataTransferSession session, Map<String, Object> config) {
        String sessionId = session.getSessionId();
        updateTransferStatus(sessionId, TransferStatus.CONNECTING);

        int port = Integer.parseInt(config.getOrDefault(CONFIG_PORT, String.valueOf(defaultPort)).toString());
        String calledAET = config.getOrDefault(CONFIG_CALLEDAET, defaultAETitle).toString();
        String storageDir = config.getOrDefault(CONFIG_STORAGE_DIR, defaultStorageDir).toString();

        try {
            // Start SCP server for this session
            startSCP(sessionId, port, calledAET, storageDir);

            // For SCP, the transfer result is based on whether the server started successfully
            // In a real implementation, this would wait for specific operations or timeout
            updateTransferStatus(sessionId, TransferStatus.COMPLETED);
            removeActiveTransfer(sessionId);

            return new TransferResult(true, null, null, 0, 0);

        } catch (Exception e) {
            log.error("DICOM SCP failed for session: {}", sessionId, e);
            updateTransferStatus(sessionId, TransferStatus.FAILED);
            removeActiveTransfer(sessionId);
            return new TransferResult(false, "DICOM SCP failed: " + e.getMessage(), null, 0, 0);
        }
    }

    private void startSCP(String sessionId, int port, String calledAET, String storageDir) throws IOException, DICOMProtocolException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            throw new IOException("SCP server already running");
        }

        serverSocket = new ServerSocket(port);
        running = true;

        // Start server in a separate thread
        Thread serverThread = new Thread(() -> {
            try {
                log.info("DICOM SCP started on port {} for AE: {}", port, calledAET);
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    handleSCPConnection(clientSocket, calledAET, storageDir);
                }
            } catch (IOException | DICOMProtocolException e) {
                if (running) {
                    log.error("Error in DICOM SCP server", e);
                }
            } finally {
                stopSCP();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleSCPConnection(Socket clientSocket, String calledAET, String storageDir) throws DICOMProtocolException {
        String associationId = UUID.randomUUID().toString();
        DataInputStream input = null;
        DataOutputStream output = null;

        try {
            input = new DataInputStream(clientSocket.getInputStream());
            output = new DataOutputStream(clientSocket.getOutputStream());

            // Read A-ASSOCIATE-RQ
            DICOMPDU pdu = readPDU(input);
            if (!(pdu instanceof AssociateRQPDU)) {
                throw new IOException("Expected A-ASSOCIATE-RQ, got: " + pdu.pduType);
            }

            AssociateRQPDU associateRQ = (AssociateRQPDU) pdu;
            String callingAET = associateRQ.callingAET;

            // Create association
            DICOMAssociation association = new DICOMAssociation(associationId, callingAET, calledAET,
                clientSocket, input, output);
            activeAssociations.put(associationId, association);

            // Parse presentation contexts from the request
            parsePresentationContextsFromPDU(association, associateRQ.variableItems);

            // Send A-ASSOCIATE-AC
            sendAssociateAC(association);

            // Handle operations
            handleSCPOperations(association, storageDir);

        } catch (Exception e) {
            log.error("Error handling SCP connection", e);
        } finally {
            activeAssociations.remove(associationId);
            closeResources(clientSocket, input, output);
        }
    }

    private void sendAssociateAC(DICOMAssociation association) throws IOException {
        // Create accepted presentation contexts
        byte[] variableItems = createAcceptedPresentationContexts();

        AssociateACPDU associateAC = new AssociateACPDU(association.getCalledAET(),
            association.getCallingAET(), variableItems);
        association.getOutput().write(associateAC.toBytes());
        association.getOutput().flush();
    }

    private byte[] createAcceptedPresentationContexts() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Application Context Item
        dos.write(0x10);
        dos.write(0x00);
        dos.writeShort(21);
        dos.writeBytes("1.2.840.10008.3.1.1.1");

        // Presentation Context Item Response
        dos.write(0x21); // Item type (0x20 | 0x01 for acceptance)
        dos.write(0x00);
        dos.writeShort(8); // Length
        dos.write(0x01); // Presentation Context ID

        // Reserved
        dos.write(0x00);
        dos.write(0x00);
        dos.write(0x00);

        // Result: Acceptance
        dos.write(0x00);

        // Transfer Syntax
        dos.write(0x40);
        dos.write(0x00);
        dos.writeShort(22);
        dos.writeBytes("1.2.840.10008.1.2");

        return baos.toByteArray();
    }

    private void handleSCPOperations(DICOMAssociation association, String storageDir) throws IOException, DICOMProtocolException {
        while (true) {
            DICOMPDU pdu = readPDU(association.getInput());

            if (pdu.pduType == PDU_P_DATA_TF) {
                PDataTFPDU pDataPDU = (PDataTFPDU) pdu;
                handleCStoreRequest(association, pDataPDU.presentationDataValues, storageDir);
            } else if (pdu.pduType == PDU_A_RELEASE_RQ) {
                // Send A-RELEASE-RP
                ReleaseRPPDU releaseRP = new ReleaseRPPDU();
                association.getOutput().write(releaseRP.toBytes());
                association.getOutput().flush();
                break;
            } else if (pdu.pduType == PDU_A_ABORT) {
                log.warn("Association aborted by remote peer");
                break;
            }
        }
    }

    private void handleCStoreRequest(DICOMAssociation association, byte[] pdvData, String storageDir) throws IOException, DICOMProtocolException {
        // Parse PDV data to extract command and data set
        ByteBuffer buffer = ByteBuffer.wrap(pdvData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Skip PDV header
        buffer.get(); // ctxId
        int flags = buffer.get() & 0xFF;
        int pdvLength = buffer.getInt();

        boolean hasCommand = (flags & 0x01) != 0;
        boolean hasData = (flags & 0x02) != 0;

        if (hasCommand) {
            // Parse command
            DICOMCommand command = parseCStoreCommand(buffer);
            if (command.getCommandField() == CMD_C_STORE_RQ) {
                byte[] dicomData = null;

                if (hasData) {
                    // Data is in the same PDV after the command
                    int remaining = buffer.remaining();
                    if (remaining > 0) {
                        dicomData = new byte[remaining];
                        buffer.get(dicomData);
                    }
                } else {
                    // Data might be in a subsequent PDV - for now, handle single PDV case
                    // In a full implementation, would read next PDV if data flag is set in next PDU
                    log.warn("C-STORE request with data in separate PDV not fully implemented");
                }

                // Store the DICOM data
                storeDICOMData(command, dicomData, storageDir);

                // Send C-STORE response
                sendCStoreResponse(association, command);
            }
        }
    }

    private DICOMCommand parseCStoreCommand(ByteBuffer buffer) throws DICOMProtocolException {
        DICOMCommand command = new DICOMCommand(0);

        // Parse command elements (simplified)
        while (buffer.hasRemaining()) {
            int group = buffer.getShort() & 0xFFFF;
            int element = buffer.getShort() & 0xFFFF;
            String vr = new String(new byte[]{buffer.get(), buffer.get()});
            int length = (vr.equals("OB") || vr.equals("OF") || vr.equals("OW") || vr.equals("SQ")) ?
                (buffer.getInt() + buffer.getInt()) : buffer.getShort() & 0xFFFF;

            if (length < 0 || length > buffer.remaining()) {
                throw new DICOMProtocolException("Invalid element length: " + length);
            }

            if (group == 0x0000 && element == 0x0002) { // Affected SOP Class UID
                byte[] uidBytes = new byte[length];
                buffer.get(uidBytes);
                command.setAffectedSOPClassUID(new String(uidBytes).trim());
            } else if (group == 0x0000 && element == 0x0100) { // Command Field
                command.setCommandField(buffer.getShort() & 0xFFFF);
            } else if (group == 0x0000 && element == 0x0110) { // Message ID
                command.setMessageID(buffer.getShort() & 0xFFFF);
            } else if (group == 0x0000 && element == 0x1000) { // Affected SOP Instance UID
                byte[] uidBytes = new byte[length];
                buffer.get(uidBytes);
                command.setAffectedSOPInstanceUID(new String(uidBytes).trim());
            } else {
                buffer.position(buffer.position() + length);
            }
        }

        // Validate required fields
        if (command.getCommandField() == 0) {
            throw new DICOMProtocolException("Missing Command Field in C-STORE request");
        }
        if (command.getAffectedSOPClassUID() == null || command.getAffectedSOPClassUID().isEmpty()) {
            throw new DICOMProtocolException("Missing Affected SOP Class UID in C-STORE request");
        }
        if (command.getAffectedSOPInstanceUID() == null || command.getAffectedSOPInstanceUID().isEmpty()) {
            throw new DICOMProtocolException("Missing Affected SOP Instance UID in C-STORE request");
        }

        return command;
    }

    private void storeDICOMData(DICOMCommand command, byte[] dicomData, String storageDir) throws IOException {
        if (dicomData != null && dicomData.length > 0) {
            // Create storage directory if needed
            java.io.File dir = new java.io.File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Store with SOP Instance UID as filename
            String filename = command.getAffectedSOPInstanceUID() + ".dcm";
            java.io.File file = new java.io.File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(dicomData);
            }
            log.info("Stored DICOM image: {}", file.getAbsolutePath());
        }
    }

    private void sendCStoreResponse(DICOMAssociation association, DICOMCommand request) throws IOException {
        DICOMCommand response = new DICOMCommand(CMD_C_STORE_RSP);
        response.setAffectedSOPClassUID(request.getAffectedSOPClassUID());
        response.setAffectedSOPInstanceUID(request.getAffectedSOPInstanceUID());
        response.setMessageID(request.getMessageID());
        response.setStatus(0x0000); // Success

        ByteArrayOutputStream commandStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(commandStream);

        // Calculate command elements (excluding Command Group Length itself)
        int commandElementsLength = 0;

        // Affected SOP Class UID element: 8 bytes header + UID length
        commandElementsLength += 8 + response.getAffectedSOPClassUID().length();

        // Command Field element: 8 bytes header + 2 bytes data
        commandElementsLength += 8 + 2;

        // Message ID Being Responded To element: 8 bytes header + 2 bytes data
        commandElementsLength += 8 + 2;

        // Command Data Set Type element: 8 bytes header + 2 bytes data
        commandElementsLength += 8 + 2;

        // Status element: 8 bytes header + 2 bytes data
        commandElementsLength += 8 + 2;

        // Affected SOP Instance UID element: 8 bytes header + UID length
        commandElementsLength += 8 + response.getAffectedSOPInstanceUID().length();

        // Command Group Length
        dos.writeShort(0x0000);
        dos.writeShort(0x0000);
        dos.writeBytes("UL");
        dos.writeShort(4);
        dos.writeInt(commandElementsLength);

        // Affected SOP Class UID
        dos.writeShort(0x0000);
        dos.writeShort(0x0002);
        dos.writeBytes("UI");
        dos.writeShort(response.getAffectedSOPClassUID().length());
        dos.writeBytes(response.getAffectedSOPClassUID());

        // Command Field
        dos.writeShort(0x0000);
        dos.writeShort(0x0100);
        dos.writeBytes("US");
        dos.writeShort(2);
        dos.writeShort(response.getCommandField());

        // Message ID Being Responded To
        dos.writeShort(0x0000);
        dos.writeShort(0x0120);
        dos.writeBytes("US");
        dos.writeShort(2);
        dos.writeShort(response.getMessageID());

        // Command Data Set Type
        dos.writeShort(0x0000);
        dos.writeShort(0x0800);
        dos.writeBytes("US");
        dos.writeShort(2);
        dos.writeShort(0x0101); // No data set

        // Status
        dos.writeShort(0x0000);
        dos.writeShort(0x0900);
        dos.writeBytes("US");
        dos.writeShort(2);
        dos.writeShort(response.getStatus());

        // Affected SOP Instance UID
        dos.writeShort(0x0000);
        dos.writeShort(0x1000);
        dos.writeBytes("UI");
        dos.writeShort(response.getAffectedSOPInstanceUID().length());
        dos.writeBytes(response.getAffectedSOPInstanceUID());

        byte[] commandData = commandStream.toByteArray();

        // Send via P-DATA-TF
        ByteArrayOutputStream pduStream = new ByteArrayOutputStream();
        DataOutputStream pduDos = new DataOutputStream(pduStream);

        pduDos.write(0x01); // Presentation Context ID
        pduDos.write(0x03); // Command + last fragment
        pduDos.writeInt(commandData.length);
        pduDos.write(commandData);

        PDataTFPDU pDataPDU = new PDataTFPDU(pduStream.toByteArray());
        association.getOutput().write(pDataPDU.toBytes());
        association.getOutput().flush();
    }

    private void stopSCP() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("Error closing SCP server socket", e);
            }
        }
    }

    private DICOMAssociation negotiateAssociationSCU(DataOutputStream output, DataInputStream input,
                                        String callingAET, String calledAET) throws DICOMException, IOException, DICOMProtocolException {
        // Create presentation context for C-STORE
        byte[] variableItems = createPresentationContexts();

        // Send A-ASSOCIATE-RQ
        AssociateRQPDU associateRQ = new AssociateRQPDU(callingAET, calledAET, variableItems);
        output.write(associateRQ.toBytes());
        output.flush();

        // Read response
        DICOMPDU responsePDU = readPDU(input);

        if (responsePDU instanceof AssociateACPDU) {
            AssociateACPDU associateAC = (AssociateACPDU) responsePDU;
            // Create association object
            String associationId = UUID.randomUUID().toString();
            DICOMAssociation association = new DICOMAssociation(associationId, callingAET, calledAET, null, input, output);
            // Parse presentation contexts from response
            parsePresentationContexts(association, associateAC.variableItems);
            return association;
        } else if (responsePDU.pduType == PDU_A_ASSOCIATE_RJ) {
            throw new DICOMAssociationException("DICOM association rejected by remote peer");
        } else {
            throw new DICOMProtocolException("Unexpected PDU type during association negotiation: " + responsePDU.pduType);
        }
    }

    private byte[] createPresentationContexts() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Application Context Item
        dos.write(0x10); // Item type
        dos.write(0x00); // Reserved
        dos.writeShort(21); // Length
        dos.writeBytes("1.2.840.10008.3.1.1.1"); // DICOM Application Context

        // Presentation Context Item for C-STORE
        dos.write(0x20); // Item type
        dos.write(0x00); // Reserved
        dos.writeShort(46); // Length
        dos.write(0x01); // Presentation Context ID

        // Reserved
        dos.write(0x00);
        dos.write(0x00);
        dos.write(0x00);
        dos.write(0x00);

        // Abstract Syntax: CT Image Storage
        dos.write(0x30); // Item type
        dos.write(0x00); // Reserved
        dos.writeShort(26); // Length
        dos.writeBytes("1.2.840.10008.5.1.4.1.1.2"); // CT Image Storage SOP Class

        // Transfer Syntax: Implicit VR Little Endian
        dos.write(0x40); // Item type
        dos.write(0x00); // Reserved
        dos.writeShort(22); // Length
        dos.writeBytes("1.2.840.10008.1.2"); // Implicit VR Little Endian

        return baos.toByteArray();
    }

    private void parsePresentationContexts(DICOMAssociation association, byte[] variableItems) {
        // Simplified parsing - in real implementation, would properly parse variable items
        // For now, assume context 1 is accepted
        PresentationContext context = new PresentationContext(1,
            "1.2.840.10008.5.1.4.1.1.2", // CT Image Storage
            Arrays.asList("1.2.840.10008.1.2")); // Implicit VR Little Endian
        association.presentationContexts.put(1, context);
    }

    private void parsePresentationContextsFromPDU(DICOMAssociation association, byte[] variableItems) {
        // Parse presentation contexts from Associate-RQ PDU variable items
        ByteBuffer buffer = ByteBuffer.wrap(variableItems);
        buffer.order(ByteOrder.BIG_ENDIAN);

        while (buffer.hasRemaining()) {
            byte itemType = buffer.get();
            buffer.get(); // Reserved

            int itemLength = buffer.getShort() & 0xFFFF;

            if (itemType == 0x20) { // Presentation Context Item
                int contextId = buffer.get() & 0xFF;
                buffer.get(); // Reserved
                buffer.get(); // Reserved
                buffer.get(); // Reserved

                // Read Abstract Syntax
                byte asItemType = buffer.get();
                buffer.get(); // Reserved
                int asLength = buffer.getShort() & 0xFFFF;
                byte[] asUidBytes = new byte[asLength];
                buffer.get(asUidBytes);
                String abstractSyntax = new String(asUidBytes).trim();

                // Read Transfer Syntax(es)
                List<String> transferSyntaxes = new ArrayList<>();
                while (buffer.hasRemaining() && buffer.get(buffer.position()) == 0x40) {
                    buffer.get(); // Item type
                    buffer.get(); // Reserved
                    int tsLength = buffer.getShort() & 0xFFFF;
                    byte[] tsUidBytes = new byte[tsLength];
                    buffer.get(tsUidBytes);
                    transferSyntaxes.add(new String(tsUidBytes).trim());
                }

                PresentationContext context = new PresentationContext(contextId, abstractSyntax, transferSyntaxes);
                association.presentationContexts.put(contextId, context);
            } else {
                // Skip unknown item types
                buffer.position(buffer.position() + itemLength);
            }
        }
    }

    private TransferResult executeCStoreSCU(DataTransferSession session, DICOMAssociation association) throws IOException, DICOMProtocolException {
        // Generate DICOM data for the session
        byte[] dicomData = generateDICOMData(session);

        // Create C-STORE command
        DICOMCommand command = new DICOMCommand(CMD_C_STORE_RQ);
        command.setAffectedSOPClassUID("1.2.840.10008.5.1.4.1.1.2"); // CT Image Storage
        command.setAffectedSOPInstanceUID(UUID.randomUUID().toString());
        command.setMessageID(1);

        // Send C-STORE request via P-DATA-TF
        sendCStoreRequest(association, command, dicomData);

        // Read C-STORE response
        DICOMCommand response = readCStoreResponse(association);

        boolean success = (response != null && response.getStatus() == 0x0000);
        return new TransferResult(success, success ? null : "C-STORE operation failed",
            null, dicomData.length, 1);
    }

    private void sendCStoreRequest(DICOMAssociation association, DICOMCommand command, byte[] dicomData) throws IOException {
        ByteArrayOutputStream commandStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(commandStream);

        // Calculate command elements length (excluding Command Group Length itself)
        int commandElementsLength = 0;

        // Affected SOP Class UID element: 8 bytes header + UID length
        commandElementsLength += 8 + command.getAffectedSOPClassUID().length();

        // Command Field element: 8 bytes header + 2 bytes data
        commandElementsLength += 8 + 2;

        // Message ID element: 8 bytes header + 2 bytes data
        commandElementsLength += 8 + 2;

        // Command Data Set Type element: 8 bytes header + 2 bytes data
        commandElementsLength += 8 + 2;

        // Affected SOP Instance UID element: 8 bytes header + UID length
        commandElementsLength += 8 + command.getAffectedSOPInstanceUID().length();

        // Command Group Length
        dos.writeShort(0x0000);
        dos.writeShort(0x0000);
        dos.writeBytes("UL");
        dos.writeShort(4);
        dos.writeInt(commandElementsLength);

        // Affected SOP Class UID
        dos.writeShort(0x0000);
        dos.writeShort(0x0002);
        dos.writeBytes("UI");
        dos.writeShort(command.getAffectedSOPClassUID().length());
        dos.writeBytes(command.getAffectedSOPClassUID());

        // Command Field
        dos.writeShort(0x0000);
        dos.writeShort(0x0100);
        dos.writeBytes("US");
        dos.writeShort(2);
        dos.writeShort(command.getCommandField());

        // Message ID
        dos.writeShort(0x0000);
        dos.writeShort(0x0110);
        dos.writeBytes("US");
        dos.writeShort(2);
        dos.writeShort(command.getMessageID());

        // Command Data Set Type
        dos.writeShort(0x0000);
        dos.writeShort(0x0800);
        dos.writeBytes("US");
        dos.writeShort(2);
        dos.writeShort(0x0101); // Command has data set

        // Affected SOP Instance UID
        dos.writeShort(0x0000);
        dos.writeShort(0x1000);
        dos.writeBytes("UI");
        dos.writeShort(command.getAffectedSOPInstanceUID().length());
        dos.writeBytes(command.getAffectedSOPInstanceUID());

        byte[] commandData = commandStream.toByteArray();

        // Create P-DATA-TF PDU with command and data
        ByteArrayOutputStream pduStream = new ByteArrayOutputStream();
        DataOutputStream pduDos = new DataOutputStream(pduStream);

        // PDV for command
        pduDos.write(0x01); // Presentation Context ID
        pduDos.write(0x03); // PDV flags: command + last fragment
        pduDos.writeInt(commandData.length);
        pduDos.write(commandData);

        // PDV for data
        pduDos.write(0x01); // Presentation Context ID
        pduDos.write(0x02); // PDV flags: data + last fragment
        pduDos.writeInt(dicomData.length);
        pduDos.write(dicomData);

        PDataTFPDU pDataPDU = new PDataTFPDU(pduStream.toByteArray());
        association.getOutput().write(pDataPDU.toBytes());
        association.getOutput().flush();
    }

    private DICOMCommand readCStoreResponse(DICOMAssociation association) throws IOException, DICOMProtocolException {
        PDataTFPDU pDataPDU = (PDataTFPDU) readPDU(association.getInput());

        // Parse command from PDV
        ByteBuffer buffer = ByteBuffer.wrap(pDataPDU.presentationDataValues);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Skip PDV header (5 bytes: ctxId, flags, length)
        buffer.get(); // ctxId
        buffer.get(); // flags
        int pdvLength = buffer.getInt();

        DICOMCommand command = new DICOMCommand(0);
        // Parse command elements (simplified)
        while (buffer.hasRemaining()) {
            int group = buffer.getShort() & 0xFFFF;
            int element = buffer.getShort() & 0xFFFF;
            String vr = new String(new byte[]{buffer.get(), buffer.get()});
            int length = (vr.equals("OB") || vr.equals("OF") || vr.equals("OW") || vr.equals("SQ")) ?
                (buffer.getInt() + buffer.getInt()) : buffer.getShort() & 0xFFFF;

            if (group == 0x0000 && element == 0x0100) { // Command Field
                command.setCommandField(buffer.getShort() & 0xFFFF);
            } else if (group == 0x0000 && element == 0x0120) { // Message ID Being Responded To
                command.setMessageID(buffer.getShort() & 0xFFFF);
            } else if (group == 0x0000 && element == 0x0900) { // Status
                command.setStatus(buffer.getShort() & 0xFFFF);
            } else {
                buffer.position(buffer.position() + length);
            }
        }

        return command;
    }

    private void releaseAssociationSCU(DICOMAssociation association) throws IOException, DICOMProtocolException {
        // Send A-RELEASE-RQ
        ReleaseRQPDU releaseRQ = new ReleaseRQPDU();
        association.getOutput().write(releaseRQ.toBytes());
        association.getOutput().flush();

        // Read A-RELEASE-RP
        DICOMPDU response = readPDU(association.getInput());
        if (!(response instanceof DICOMPDU) || response.pduType != PDU_A_RELEASE_RP) {
            log.warn("Unexpected response to A-RELEASE-RQ: {}", response.pduType);
        }
    }


    private byte[] generateDICOMData(DataTransferSession session) {
        // Generate minimal DICOM file structure
        // This is highly simplified - real DICOM files are complex
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (DataOutputStream dos = new DataOutputStream(baos)) {
            // DICOM preamble (128 bytes of zeros)
            for (int i = 0; i < 128; i++) {
                dos.writeByte(0x00);
            }

            // DICOM prefix "DICM"
            dos.writeBytes("DICM");

            // Sample DICOM elements (simplified)
            // Group 0002, Element 0000 - File Meta Information Group Length
            dos.writeShort(0x0002); // Group
            dos.writeShort(0x0000); // Element
            dos.writeBytes("UL");    // VR (Value Representation)
            dos.writeShort(4);       // Length
            dos.writeInt(196);       // Value

            // Group 0002, Element 0001 - File Meta Information Version
            dos.writeShort(0x0002);
            dos.writeShort(0x0001);
            dos.writeBytes("OB");
            dos.writeShort(2);
            dos.writeShort(0x0001);

            // Add more DICOM elements as needed...

        } catch (IOException e) {
            log.error("Error generating DICOM data", e);
        }

        return baos.toByteArray();
    }

    private void closeResources(Socket socket, DataInputStream input, DataOutputStream output) {
        try {
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            log.warn("Error closing DICOM connection resources", e);
        }
    }

    // PDU parsing methods
    private DICOMPDU readPDU(DataInputStream input) throws IOException, DICOMProtocolException {
        byte pduType = input.readByte();
        input.readByte(); // Reserved
        int pduLength = input.readInt();

        switch (pduType) {
            case PDU_A_ASSOCIATE_RQ:
                return readAssociateRQPDU(input, pduLength);
            case PDU_A_ASSOCIATE_AC:
                return readAssociateACPDU(input, pduLength);
            case PDU_A_ASSOCIATE_RJ:
                return readAssociateRJPDU(input, pduLength);
            case PDU_P_DATA_TF:
                return readPDataTFPDU(input, pduLength);
            case PDU_A_RELEASE_RQ:
                return readReleaseRQPDU(input, pduLength);
            case PDU_A_RELEASE_RP:
                return readReleaseRPPDU(input, pduLength);
            case PDU_A_ABORT:
                return readAbortPDU(input, pduLength);
            default:
                // Skip unknown PDU
                input.skipBytes(pduLength);
                return new DICOMPDU(pduType, pduLength);
        }
    }

    private AssociateRQPDU readAssociateRQPDU(DataInputStream input, int pduLength) throws IOException, DICOMProtocolException {
        short protocolVersion = input.readShort();
        input.readShort(); // Reserved
        input.readInt(); // Reserved

        byte[] callingAETBytes = new byte[16];
        input.readFully(callingAETBytes);
        String callingAET = new String(callingAETBytes, "ASCII").trim();

        byte[] calledAETBytes = new byte[16];
        input.readFully(calledAETBytes);
        String calledAET = new String(calledAETBytes, "ASCII").trim();

        input.skipBytes(32); // Reserved

        // Fixed header length: protocolVersion(2) + reserved(2) + reserved(4) + callingAET(16) + calledAET(16) + reserved(32) = 72
        int variableLength = pduLength - 72;
        if (variableLength < 0) {
            throw new DICOMProtocolException("Invalid PDU length: " + pduLength + ", variable length would be negative");
        }
        byte[] variableItems = new byte[variableLength];
        input.readFully(variableItems);

        AssociateRQPDU pdu = new AssociateRQPDU(callingAET, calledAET, variableItems);
        return pdu;
    }

    private AssociateACPDU readAssociateACPDU(DataInputStream input, int pduLength) throws IOException, DICOMProtocolException {
        short protocolVersion = input.readShort();
        input.readShort(); // Reserved
        input.readInt(); // Reserved

        byte[] calledAETBytes = new byte[16];
        input.readFully(calledAETBytes);
        String calledAET = new String(calledAETBytes, "ASCII").trim();

        byte[] callingAETBytes = new byte[16];
        input.readFully(callingAETBytes);
        String callingAET = new String(callingAETBytes, "ASCII").trim();

        input.skipBytes(32); // Reserved

        // Fixed header length: protocolVersion(2) + reserved(2) + reserved(4) + calledAET(16) + callingAET(16) + reserved(32) = 72
        int variableLength = pduLength - 72;
        if (variableLength < 0) {
            throw new DICOMProtocolException("Invalid PDU length: " + pduLength + ", variable length would be negative");
        }
        byte[] variableItems = new byte[variableLength];
        input.readFully(variableItems);

        AssociateACPDU pdu = new AssociateACPDU(calledAET, callingAET, variableItems);
        return pdu;
    }

    private DICOMPDU readAssociateRJPDU(DataInputStream input, int pduLength) throws IOException {
        // For simplicity, just skip and return basic PDU
        input.skipBytes(pduLength);
        return new DICOMPDU(PDU_A_ASSOCIATE_RJ, pduLength);
    }

    private PDataTFPDU readPDataTFPDU(DataInputStream input, int pduLength) throws IOException {
        byte[] data = new byte[pduLength];
        input.readFully(data);
        return new PDataTFPDU(data);
    }

    private DICOMPDU readReleaseRQPDU(DataInputStream input, int pduLength) throws IOException {
        input.skipBytes(pduLength);
        return new DICOMPDU(PDU_A_RELEASE_RQ, pduLength);
    }

    private DICOMPDU readReleaseRPPDU(DataInputStream input, int pduLength) throws IOException {
        input.skipBytes(pduLength);
        return new DICOMPDU(PDU_A_RELEASE_RP, pduLength);
    }

    private DICOMPDU readAbortPDU(DataInputStream input, int pduLength) throws IOException {
        input.skipBytes(pduLength);
        return new DICOMPDU(PDU_A_ABORT, pduLength);
    }

    // DICOM PDU Classes
    private static class DICOMPDU {
        protected byte pduType;
        protected int pduLength;

        public DICOMPDU(byte pduType, int pduLength) {
            this.pduType = pduType;
            this.pduLength = pduLength;
        }

        public byte getPduType() { return pduType; }
        public int getPduLength() { return pduLength; }
    }

    private static class AssociateRQPDU extends DICOMPDU {
        private short protocolVersion;
        private String callingAET;
        private String calledAET;
        private byte[] variableItems;

        public AssociateRQPDU(String callingAET, String calledAET, byte[] variableItems) {
            super(PDU_A_ASSOCIATE_RQ, 4 + 2 + 2 + 16 + 16 + 32 + variableItems.length);
            this.protocolVersion = 0x0001;
            this.callingAET = callingAET;
            this.calledAET = calledAET;
            this.variableItems = variableItems;
        }

        public byte[] toBytes() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(pduType);
            dos.writeByte(0x00); // Reserved
            dos.writeInt(pduLength);
            dos.writeShort(protocolVersion);
            dos.writeShort(0x0000); // Reserved
            dos.writeInt(0x00000000); // Reserved

            // Calling AE Title (16 bytes, space padded)
            byte[] callingBytes = callingAET.getBytes("ASCII");
            dos.write(callingBytes, 0, Math.min(callingBytes.length, 16));
            for (int i = callingBytes.length; i < 16; i++) {
                dos.writeByte(0x20);
            }

            // Called AE Title (16 bytes, space padded)
            byte[] calledBytes = calledAET.getBytes("ASCII");
            dos.write(calledBytes, 0, Math.min(calledBytes.length, 16));
            for (int i = calledBytes.length; i < 16; i++) {
                dos.writeByte(0x20);
            }

            // Reserved (32 bytes)
            for (int i = 0; i < 32; i++) {
                dos.writeByte(0x00);
            }

            // Variable items
            dos.write(variableItems);

            return baos.toByteArray();
        }
    }

    private static class AssociateACPDU extends DICOMPDU {
        private short protocolVersion;
        private String calledAET;
        private String callingAET;
        private byte[] variableItems;

        public AssociateACPDU(String calledAET, String callingAET, byte[] variableItems) {
            super(PDU_A_ASSOCIATE_AC, 4 + 2 + 2 + 16 + 16 + 32 + variableItems.length);
            this.protocolVersion = 0x0001;
            this.calledAET = calledAET;
            this.callingAET = callingAET;
            this.variableItems = variableItems;
        }

        public byte[] toBytes() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(pduType);
            dos.writeByte(0x00);
            dos.writeInt(pduLength);
            dos.writeShort(protocolVersion);
            dos.writeShort(0x0000);
            dos.writeInt(0x00000000);

            // Called AE Title
            byte[] calledBytes = calledAET.getBytes("ASCII");
            dos.write(calledBytes, 0, Math.min(calledBytes.length, 16));
            for (int i = calledBytes.length; i < 16; i++) {
                dos.writeByte(0x20);
            }

            // Calling AE Title
            byte[] callingBytes = callingAET.getBytes("ASCII");
            dos.write(callingBytes, 0, Math.min(callingBytes.length, 16));
            for (int i = callingBytes.length; i < 16; i++) {
                dos.writeByte(0x20);
            }

            // Reserved
            for (int i = 0; i < 32; i++) {
                dos.writeByte(0x00);
            }

            dos.write(variableItems);

            return baos.toByteArray();
        }
    }

    private static class PDataTFPDU extends DICOMPDU {
        private byte[] presentationDataValues;

        public PDataTFPDU(byte[] presentationDataValues) {
            super(PDU_P_DATA_TF, presentationDataValues.length);
            this.presentationDataValues = presentationDataValues;
        }

        public byte[] toBytes() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(pduType);
            dos.writeByte(0x00);
            dos.writeInt(pduLength);
            dos.write(presentationDataValues);

            return baos.toByteArray();
        }
    }

    private static class ReleaseRQPDU extends DICOMPDU {
        public ReleaseRQPDU() {
            super(PDU_A_RELEASE_RQ, 4);
        }

        public byte[] toBytes() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(pduType);
            dos.writeByte(0x00);
            dos.writeInt(pduLength);
            dos.writeInt(0x00000000); // Reserved

            return baos.toByteArray();
        }
    }

    private static class ReleaseRPPDU extends DICOMPDU {
        public ReleaseRPPDU() {
            super(PDU_A_RELEASE_RP, 4);
        }

        public byte[] toBytes() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeByte(pduType);
            dos.writeByte(0x00);
            dos.writeInt(pduLength);
            dos.writeInt(0x00000000);

            return baos.toByteArray();
        }
    }

    // DICOM Association Context
    private static class DICOMAssociation {
        private String associationId;
        private String callingAET;
        private String calledAET;
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private Map<Integer, PresentationContext> presentationContexts;

        public DICOMAssociation(String associationId, String callingAET, String calledAET,
                              Socket socket, DataInputStream input, DataOutputStream output) {
            this.associationId = associationId;
            this.callingAET = callingAET;
            this.calledAET = calledAET;
            this.socket = socket;
            this.input = input;
            this.output = output;
            this.presentationContexts = new HashMap<>();
        }

        // Getters
        public String getAssociationId() { return associationId; }
        public String getCallingAET() { return callingAET; }
        public String getCalledAET() { return calledAET; }
        public Socket getSocket() { return socket; }
        public DataInputStream getInput() { return input; }
        public DataOutputStream getOutput() { return output; }
        public Map<Integer, PresentationContext> getPresentationContexts() { return presentationContexts; }
    }

    private static class PresentationContext {
        private int contextId;
        private String abstractSyntax;
        private List<String> transferSyntaxes;

        public PresentationContext(int contextId, String abstractSyntax, List<String> transferSyntaxes) {
            this.contextId = contextId;
            this.abstractSyntax = abstractSyntax;
            this.transferSyntaxes = transferSyntaxes;
        }

        // Getters
        public int getContextId() { return contextId; }
        public String getAbstractSyntax() { return abstractSyntax; }
        public List<String> getTransferSyntaxes() { return transferSyntaxes; }
    }

    // DICOM Command and Data Structures
    private static class DICOMCommand {
        private int commandField;
        private String affectedSOPClassUID;
        private String affectedSOPInstanceUID;
        private int messageID;
        private int status;

        public DICOMCommand(int commandField) {
            this.commandField = commandField;
        }

        // Getters and setters
        public int getCommandField() { return commandField; }
        public void setCommandField(int commandField) { this.commandField = commandField; }
        public String getAffectedSOPClassUID() { return affectedSOPClassUID; }
        public void setAffectedSOPClassUID(String affectedSOPClassUID) { this.affectedSOPClassUID = affectedSOPClassUID; }
        public String getAffectedSOPInstanceUID() { return affectedSOPInstanceUID; }
        public void setAffectedSOPInstanceUID(String affectedSOPInstanceUID) { this.affectedSOPInstanceUID = affectedSOPInstanceUID; }
        public int getMessageID() { return messageID; }
        public void setMessageID(int messageID) { this.messageID = messageID; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
    }
}