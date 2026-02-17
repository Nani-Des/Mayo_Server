package com.mayo.sync.grpc;

/**
 * Protobuf message for acknowledgment response
 */
public class AckResponse {
    private boolean success;
    private String message;

    public boolean getSuccess() { return success; }
    public String getMessage() { return message; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final AckResponse instance = new AckResponse();

        public Builder setSuccess(boolean success) { instance.success = success; return this; }
        public Builder setMessage(String message) { instance.message = message; return this; }

        public AckResponse build() { return instance; }
    }
}