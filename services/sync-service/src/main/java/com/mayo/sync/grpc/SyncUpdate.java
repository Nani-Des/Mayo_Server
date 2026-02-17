package com.mayo.sync.grpc;

import java.util.List;

/**
 * Protobuf message for sync update notifications
 */
public class SyncUpdate {
    private String userId;
    private String deviceId;
    private String updateType;
    private String message;
    private long timestamp;
    private List<DeltaChangeProto> changes;

    public String getUserId() { return userId; }
    public String getDeviceId() { return deviceId; }
    public String getUpdateType() { return updateType; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public List<DeltaChangeProto> getChangesList() { return changes; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final SyncUpdate instance = new SyncUpdate();

        public Builder setUserId(String userId) { instance.userId = userId; return this; }
        public Builder setDeviceId(String deviceId) { instance.deviceId = deviceId; return this; }
        public Builder setUpdateType(String updateType) { instance.updateType = updateType; return this; }
        public Builder setMessage(String message) { instance.message = message; return this; }
        public Builder setTimestamp(long timestamp) { instance.timestamp = timestamp; return this; }

        public Builder addChanges(DeltaChangeProto change) {
            if (instance.changes == null) {
                instance.changes = new java.util.ArrayList<>();
            }
            instance.changes.add(change);
            return this;
        }

        public SyncUpdate build() { return instance; }
    }
}