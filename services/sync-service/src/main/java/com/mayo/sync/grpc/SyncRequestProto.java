package com.mayo.sync.grpc;

import java.util.List;

/**
 * Protobuf message for sync request
 */
public class SyncRequestProto {
    private String userId;
    private String deviceId;
    private List<DeltaChangeProto> changes;
    private long lastSyncVersion;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public List<DeltaChangeProto> getChangesList() { return changes; }
    public void setChanges(List<DeltaChangeProto> changes) { this.changes = changes; }

    public long getLastSyncVersion() { return lastSyncVersion; }
    public void setLastSyncVersion(long lastSyncVersion) { this.lastSyncVersion = lastSyncVersion; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final SyncRequestProto instance = new SyncRequestProto();

        public Builder setUserId(String userId) {
            instance.userId = userId;
            return this;
        }

        public Builder setDeviceId(String deviceId) {
            instance.deviceId = deviceId;
            return this;
        }

        public Builder addChanges(DeltaChangeProto change) {
            if (instance.changes == null) {
                instance.changes = new java.util.ArrayList<>();
            }
            instance.changes.add(change);
            return this;
        }

        public Builder setLastSyncVersion(long version) {
            instance.lastSyncVersion = version;
            return this;
        }

        public SyncRequestProto build() {
            return instance;
        }
    }
}