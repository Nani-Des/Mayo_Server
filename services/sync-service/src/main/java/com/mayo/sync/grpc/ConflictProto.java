package com.mayo.sync.grpc;

/**
 * Protobuf message for conflict
 */
public class ConflictProto {
    private String id;
    private String recordId;
    private String recordType;
    private long localVersion;
    private long serverVersion;
    private String conflictType;
    private String resolutionStatus;
    private String localData;
    private String serverData;

    public String getId() { return id; }
    public String getRecordId() { return recordId; }
    public String getRecordType() { return recordType; }
    public long getLocalVersion() { return localVersion; }
    public long getServerVersion() { return serverVersion; }
    public String getConflictType() { return conflictType; }
    public String getResolutionStatus() { return resolutionStatus; }
    public String getLocalData() { return localData; }
    public String getServerData() { return serverData; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final ConflictProto instance = new ConflictProto();

        public Builder setId(String id) { instance.id = id; return this; }
        public Builder setRecordId(String recordId) { instance.recordId = recordId; return this; }
        public Builder setRecordType(String recordType) { instance.recordType = recordType; return this; }
        public Builder setLocalVersion(long version) { instance.localVersion = version; return this; }
        public Builder setServerVersion(long version) { instance.serverVersion = version; return this; }
        public Builder setConflictType(String type) { instance.conflictType = type; return this; }
        public Builder setResolutionStatus(String status) { instance.resolutionStatus = status; return this; }
        public Builder setLocalData(String data) { instance.localData = data; return this; }
        public Builder setServerData(String data) { instance.serverData = data; return this; }

        public ConflictProto build() { return instance; }
    }
}