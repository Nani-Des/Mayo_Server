package com.mayo.sync.grpc;

import com.google.protobuf.ByteString;

/**
 * Protobuf message for delta change
 */
public class DeltaChangeProto {
    private String recordId;
    private String recordType;
    private String changeType;
    private long version;
    private String timestamp;
    private String data;
    private String documentId;
    private ByteString crdtState;
    private boolean isCrdtEnabled;

    public String getRecordId() { return recordId; }
    public String getRecordType() { return recordType; }
    public String getChangeType() { return changeType; }
    public long getVersion() { return version; }
    public String getTimestamp() { return timestamp; }
    public String getData() { return data; }
    public String getDocumentId() { return documentId; }
    public ByteString getCrdtState() { return crdtState; }
    public boolean getIsCrdtEnabled() { return isCrdtEnabled; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final DeltaChangeProto instance = new DeltaChangeProto();

        public Builder setRecordId(String recordId) { instance.recordId = recordId; return this; }
        public Builder setRecordType(String recordType) { instance.recordType = recordType; return this; }
        public Builder setChangeType(String changeType) { instance.changeType = changeType; return this; }
        public Builder setVersion(long version) { instance.version = version; return this; }
        public Builder setTimestamp(String timestamp) { instance.timestamp = timestamp; return this; }
        public Builder setData(String data) { instance.data = data; return this; }
        public Builder setDocumentId(String documentId) { instance.documentId = documentId; return this; }
        public Builder setCrdtState(ByteString crdtState) { instance.crdtState = crdtState; return this; }
        public Builder setIsCrdtEnabled(boolean isCrdtEnabled) { instance.isCrdtEnabled = isCrdtEnabled; return this; }

        public DeltaChangeProto build() { return instance; }
    }
}