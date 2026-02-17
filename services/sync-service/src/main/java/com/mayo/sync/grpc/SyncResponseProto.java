package com.mayo.sync.grpc;

import java.util.List;

/**
 * Protobuf message for sync response
 */
public class SyncResponseProto {
    private String status;
    private List<ConflictProto> conflicts;
    private List<DeltaChangeProto> serverChanges;
    private long newVersion;

    public String getStatus() { return status; }
    public List<ConflictProto> getConflictsList() { return conflicts; }
    public List<DeltaChangeProto> getServerChangesList() { return serverChanges; }
    public long getNewVersion() { return newVersion; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final SyncResponseProto instance = new SyncResponseProto();

        public Builder setStatus(String status) {
            instance.status = status;
            return this;
        }

        public Builder addConflicts(ConflictProto conflict) {
            if (instance.conflicts == null) {
                instance.conflicts = new java.util.ArrayList<>();
            }
            instance.conflicts.add(conflict);
            return this;
        }

        public Builder addServerChanges(DeltaChangeProto change) {
            if (instance.serverChanges == null) {
                instance.serverChanges = new java.util.ArrayList<>();
            }
            instance.serverChanges.add(change);
            return this;
        }

        public Builder setNewVersion(long version) {
            instance.newVersion = version;
            return this;
        }

        public SyncResponseProto build() {
            return instance;
        }
    }
}