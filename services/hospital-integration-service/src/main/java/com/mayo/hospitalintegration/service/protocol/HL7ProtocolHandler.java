package com.mayo.hospitalintegration.service.protocol;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

/**
 * HL7 protocol handler for healthcare data exchange using MLLP (Minimal Lower Layer Protocol)
 */
@Component
@Slf4j
public class HL7ProtocolHandler extends AbstractProtocolHandler {

    private static final String CONFIG_HOST = "host";
    private static final String CONFIG_PORT = "port";
    private static final String CONFIG_TIMEOUT = "timeout";
    private static final String CONFIG_MESSAGE_TYPE = "messageType";

    @Override
    public DataTransferSession.TransferProtocol getProtocol() {
        return DataTransferSession.TransferProtocol.HL7;
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        ValidationResult required = validateRequiredConfig(config, CONFIG_HOST, CONFIG_PORT);
        if (!required.isValid()) {
            return required;
        }

        // Validate port
        try {
            int port = Integer.parseInt(config.get(CONFIG_PORT).toString());
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
        String sessionId = session.getSessionId();
        updateTransferStatus(sessionId, TransferStatus.CONNECTING);

        String host = config.get(CONFIG_HOST).toString();
        int port = Integer.parseInt(config.get(CONFIG_PORT).toString());
        int timeout = config.containsKey(CONFIG_TIMEOUT) ?
            Integer.parseInt(config.get(CONFIG_TIMEOUT).toString()) : 30000;

        Socket socket = null;
        PrintWriter writer = null;
        BufferedReader reader = null;

        try {
            // Establish connection
            socket = new Socket(host, port);
            socket.setSoTimeout(timeout);

            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            updateTransferStatus(sessionId, TransferStatus.TRANSFERRING);

            // For HL7, we would typically send/receive HL7 messages
            // This is a simplified implementation
            String hl7Message = generateHL7Message(session, config);
            String wrappedMessage = wrapMLLP(hl7Message);

            log.info("Sending HL7 message for session: {}", sessionId);
            writer.print(wrappedMessage);
            writer.flush();

            // Read acknowledgment
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
                if (line.trim().endsWith("\u001c")) { // MLLP end block
                    break;
                }
            }

            String ackMessage = unwrapMLLP(response.toString());
            boolean success = isAcknowledgmentPositive(ackMessage);

            // If acknowledgment is negative, log the issue
            if (!success) {
                log.warn("Received negative acknowledgment for session: {}", sessionId);
            }

            updateTransferStatus(sessionId, success ? TransferStatus.COMPLETED : TransferStatus.FAILED);
            removeActiveTransfer(sessionId);

            return new TransferResult(success, success ? null : "Negative acknowledgment received",
                ackMessage, wrappedMessage.length(), 1);

        } catch (IOException e) {
            log.error("HL7 transfer failed for session: {}", sessionId, e);
            updateTransferStatus(sessionId, TransferStatus.FAILED);
            removeActiveTransfer(sessionId);
            return new TransferResult(false, "Connection failed: " + e.getMessage(), null, 0, 0);
        } finally {
            closeResources(socket, writer, reader);
        }
    }

    private String generateHL7Message(DataTransferSession session, Map<String, Object> config) {
        // Generate appropriate HL7 message based on session data
        String messageType = config.getOrDefault(CONFIG_MESSAGE_TYPE, "ADT^A01").toString();

        if (messageType.startsWith("ADT")) {
            return generateADTMessage(session, config);
        } else if (messageType.startsWith("ORU")) {
            return generateORUMessage(session, config);
        } else if (messageType.startsWith("ORM")) {
            return generateORMMessage(session, config);
        } else {
            return generateBasicHL7Message(session, messageType, config);
        }
    }

    private String generateADTMessage(DataTransferSession session, Map<String, Object> config) {
        String timestamp = getCurrentTimestamp();
        String messageId = "MSG" + System.currentTimeMillis();

        return "MSH|^~\\&|MAYO|HOSPITAL|" + config.get(CONFIG_HOST) + "|SYSTEM|" + timestamp + "||ADT^A01|" + messageId + "|P|2.5|||AL|NE|||||\r" +
               "EVN|A01|" + timestamp + "|||SYSTEM\r" +
               "PID|1||" + session.getSessionId() + "||DOE^JOHN||19800101|M||||||||||||||||" + session.getSessionId() + "\r" +
               "PV1|1|I|WARD001^BEDS001||||DR001^SMITH^JOHN|||SUR|||||ADM|||" + timestamp + "\r";
    }

    private String generateORUMessage(DataTransferSession session, Map<String, Object> config) {
        String timestamp = getCurrentTimestamp();
        String messageId = "MSG" + System.currentTimeMillis();

        return "MSH|^~\\&|MAYO|HOSPITAL|" + config.get(CONFIG_HOST) + "|SYSTEM|" + timestamp + "||ORU^R01|" + messageId + "|P|2.5|||AL|NE|||||\r" +
               "PID|1||" + session.getSessionId() + "||DOE^JOHN||||||||||||||||" + session.getSessionId() + "\r" +
               "OBR|1|||LAB^Laboratory|||" + timestamp + "|||||||||||||||\r" +
               "OBX|1||WBC^White Blood Cell Count||7.5|10^9/L|||N|||F|||20231203000000|||||\r";
    }

    private String generateORMMessage(DataTransferSession session, Map<String, Object> config) {
        String timestamp = getCurrentTimestamp();
        String messageId = "MSG" + System.currentTimeMillis();

        return "MSH|^~\\&|MAYO|HOSPITAL|" + config.get(CONFIG_HOST) + "|SYSTEM|" + timestamp + "||ORM^O01|" + messageId + "|P|2.5|||AL|NE|||||\r" +
               "PID|1||" + session.getSessionId() + "||DOE^JOHN||||||||||||||||" + session.getSessionId() + "\r" +
               "ORC|NW|||||||||||||||||||||||||||||||||||||\r" +
               "OBR|1|||CBC^Complete Blood Count|||" + timestamp + "|||||||||||||||\r";
    }

    private String generateBasicHL7Message(DataTransferSession session, String messageType, Map<String, Object> config) {
        String timestamp = getCurrentTimestamp();
        String messageId = "MSG" + System.currentTimeMillis();

        return "MSH|^~\\&|MAYO|HOSPITAL|" + config.get(CONFIG_HOST) + "|SYSTEM|" + timestamp + "||" + messageType + "|" + messageId + "|P|2.5|||AL|NE|||||\r";
    }

    private String getCurrentTimestamp() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now());
    }

    private String wrapMLLP(String message) {
        // MLLP wrapping: <SB> + message + <EB> + <CR>
        return "\u000b" + message + "\u001c\r";
    }

    private String unwrapMLLP(String wrappedMessage) {
        if (wrappedMessage.startsWith("\u000b") && wrappedMessage.contains("\u001c")) {
            int startIndex = 1; // Skip <SB>
            int endIndex = wrappedMessage.indexOf("\u001c");
            return wrappedMessage.substring(startIndex, endIndex);
        }
        return wrappedMessage;
    }

    private boolean isAcknowledgmentPositive(String ackMessage) {
        if (ackMessage == null || ackMessage.trim().isEmpty()) {
            return false;
        }

        // Check for MSA segment with positive acknowledgment codes
        // AA = Application Accept, CA = Commit Accept, CE = Commit Error, CR = Commit Reject
        return ackMessage.contains("MSA|AA|") || ackMessage.contains("MSA|CA|");
    }

    private String generateAcknowledgmentMessage(String originalMessage, boolean isPositive, String errorMessage, Map<String, Object> config) {
        try {
            // Extract message control ID from original message
            String messageId = extractMessageId(originalMessage);
            String acknowledgmentCode = isPositive ? "AA" : "AE"; // AA=Accept, AE=Application Error
            String timestamp = getCurrentTimestamp();
            String host = config != null ? config.get(CONFIG_HOST).toString() : "UNKNOWN";

            StringBuilder ack = new StringBuilder();
            ack.append("MSH|^~\\&|MAYO|HOSPITAL|").append(host).append("|SYSTEM|")
               .append(timestamp).append("||ACK^R01|ACK").append(System.currentTimeMillis())
               .append("|P|2.5|||AL|NE|||||\r");

            ack.append("MSA|").append(acknowledgmentCode).append("|").append(messageId);
            if (!isPositive && errorMessage != null && !errorMessage.trim().isEmpty()) {
                ack.append("|").append(errorMessage.replace("|", "\\F\\"));
            }
            ack.append("\r");

            return ack.toString();
        } catch (Exception e) {
            log.error("Failed to generate acknowledgment message", e);
            return null;
        }
    }

    private String extractMessageId(String hl7Message) {
        if (hl7Message == null) return "UNKNOWN";

        String[] lines = hl7Message.split("\r");
        for (String line : lines) {
            if (line.startsWith("MSH|")) {
                String[] fields = line.split("\\|");
                if (fields.length > 9) {
                    return fields[9]; // MSH.10 is Message Control ID
                }
            }
        }
        return "UNKNOWN";
    }

    private void closeResources(Socket socket, PrintWriter writer, BufferedReader reader) {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            log.warn("Error closing HL7 connection resources", e);
        }
    }
}