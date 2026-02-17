package com.mayo.sync.grpc;

import com.mayo.sync.dto.DeltaChangeDto;
import com.mayo.sync.dto.SyncRequest;
import com.mayo.sync.dto.SyncResponse;
import com.mayo.sync.service.SyncService;
import com.mayo.sync.service.DeltaSyncService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import com.mayo.sync.grpc.SyncServiceGrpc;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * gRPC service for real-time sync operations
 * NOTE: This service is currently disabled due to gRPC protobuf compatibility issues
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class SyncGrpcService extends SyncServiceGrpc.SyncServiceImplBase {

    private final SyncService syncService;
    private final DeltaSyncService deltaSyncService;

    // Store active streaming connections for real-time push notifications
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<StreamObserver<SyncUpdate>>> activeStreams =
        new ConcurrentHashMap<>();

    // Commented out gRPC methods due to protobuf compatibility issues
    @Override
    public StreamObserver<SyncRequestProto> streamSync(StreamObserver<SyncResponseProto> responseObserver) {
        return new StreamObserver<SyncRequestProto>() {
            private String userId;
            private String deviceId;

            @Override
            public void onNext(SyncRequestProto request) {
                try {
                    userId = request.getUserId();
                    deviceId = request.getDeviceId();

                    // Convert protobuf to DTO
                    SyncRequest syncRequest = convertToSyncRequest(request);

                    // Perform sync
                    SyncResponse syncResponse = syncService.performSync(syncRequest);

                    // Send response
                    SyncResponseProto responseProto = convertToSyncResponseProto(syncResponse);
                    responseObserver.onNext(responseProto);

                    log.info("gRPC sync completed for user {} device {}", userId, deviceId);

                } catch (Exception e) {
                    log.error("Error in gRPC stream sync for user {} device {}: {}", userId, deviceId, e.getMessage(), e);
                    responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Sync failed: " + e.getMessage())
                        .asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("gRPC stream error for user {} device {}: {}", userId, deviceId, t.getMessage(), t);
            }

            @Override
            public void onCompleted() {
                log.info("gRPC stream completed for user {} device {}", userId, deviceId);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void realTimeUpdate(SyncUpdate request, StreamObserver<AckResponse> responseObserver) {
        try {
            String userId = request.getUserId();
            String deviceId = request.getDeviceId();

            // Process real-time update (could be conflict notification, sync completion, etc.)
            log.info("Received real-time update for user {} device {}: {}", userId, deviceId, request.getUpdateType());

            // Send acknowledgment
            AckResponse ack = AckResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Update processed successfully")
                .build();

            responseObserver.onNext(ack);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error processing real-time update: {}", e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Update processing failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    /**
     * Push real-time update to connected devices
     */
    public void pushRealTimeUpdate(String userId, String updateType, String message, List<DeltaChangeDto> changes) {
        CopyOnWriteArrayList<StreamObserver<SyncUpdate>> userStreams = activeStreams.get(userId);
        if (userStreams != null && !userStreams.isEmpty()) {
            SyncUpdate.Builder updateBuilder = SyncUpdate.newBuilder()
                .setUserId(userId)
                .setUpdateType(updateType)
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis());

            // Add changes if provided
            if (changes != null) {
                for (DeltaChangeDto change : changes) {
                    DeltaChangeProto changeProto = DeltaChangeProto.newBuilder()
                        .setRecordId(change.getRecordId())
                        .setRecordType(change.getRecordType())
                        .setChangeType(change.getChangeType().name())
                        .setVersion(change.getVersion())
                        .setTimestamp(change.getTimestamp().toString())
                        .setData(change.getData() != null ? change.getData() : "")
                        .build();
                    updateBuilder.addChanges(changeProto);
                }
            }

            SyncUpdate update = updateBuilder.build();

            // Send to all connected devices for this user
            for (StreamObserver<SyncUpdate> observer : userStreams) {
                try {
                    observer.onNext(update);
                } catch (Exception e) {
                    log.warn("Failed to send real-time update to stream: {}", e.getMessage());
                    // Remove failed stream
                    userStreams.remove(observer);
                }
            }

            log.debug("Pushed real-time update to {} streams for user {}", userStreams.size(), userId);
        }
    }
    /**
     * Register a stream for real-time updates
     */
    private void registerStream(String userId, String deviceId, StreamObserver<SyncUpdate> observer) {
        activeStreams.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(observer);
        log.debug("Registered stream for user {} device {}", userId, deviceId);
    }

    /**
     * Unregister a stream from real-time updates
     */
    private void unregisterStream(String userId, String deviceId, StreamObserver<SyncUpdate> observer) {
        CopyOnWriteArrayList<StreamObserver<SyncUpdate>> userStreams = activeStreams.get(userId);
        if (userStreams != null) {
            userStreams.remove(observer);
            if (userStreams.isEmpty()) {
                activeStreams.remove(userId);
            }
            log.debug("Unregistered stream for user {} device {}", userId, deviceId);
        }
    }


    /**
     * Convert protobuf SyncRequest to DTO
     */
    private SyncRequest convertToSyncRequest(SyncRequestProto proto) {
        SyncRequest request = new SyncRequest();
        request.setUserId(proto.getUserId());
        request.setDeviceId(proto.getDeviceId());
        request.setLastSyncVersion(proto.getLastSyncVersion());

        // Convert changes
        List<DeltaChangeDto> changes = proto.getChangesList().stream()
            .map(this::convertToDeltaChangeDto)
            .collect(java.util.stream.Collectors.toList());
        request.setChanges(changes);

        return request;
    }

    /**
     * Convert protobuf DeltaChange to DTO
     */
    private DeltaChangeDto convertToDeltaChangeDto(DeltaChangeProto proto) {
        DeltaChangeDto dto = new DeltaChangeDto();
        dto.setRecordId(proto.getRecordId());
        dto.setRecordType(proto.getRecordType());
        dto.setChangeType(DeltaChangeDto.ChangeType.valueOf(proto.getChangeType()));
        dto.setVersion(proto.getVersion());
        dto.setTimestamp(java.time.LocalDateTime.parse(proto.getTimestamp()));
        dto.setData(proto.getData());
        dto.setDocumentId(proto.getDocumentId());
        dto.setCrdtState(proto.getCrdtState().toByteArray());
        dto.setIsCrdtEnabled(proto.getIsCrdtEnabled());
        return dto;
    }

    /**
     * Convert DTO SyncResponse to protobuf
     */
    private SyncResponseProto convertToSyncResponseProto(SyncResponse response) {
        SyncResponseProto.Builder builder = SyncResponseProto.newBuilder()
            .setStatus(response.getStatus())
            .setNewVersion(response.getNewVersion());

        // Convert conflicts
        if (response.getConflicts() != null) {
            response.getConflicts().forEach(conflict -> {
                ConflictProto conflictProto = ConflictProto.newBuilder()
                    .setId(conflict.getId().toString())
                    .setRecordId(conflict.getRecordId())
                    .setRecordType(conflict.getRecordType())
                    .setLocalVersion(conflict.getLocalVersion())
                    .setServerVersion(conflict.getServerVersion())
                    .setConflictType(conflict.getConflictType().name())
                    .setResolutionStatus(conflict.getResolutionStatus().name())
                    .setLocalData(conflict.getLocalData() != null ? conflict.getLocalData() : "")
                    .setServerData(conflict.getServerData() != null ? conflict.getServerData() : "")
                    .build();
                builder.addConflicts(conflictProto);
            });
        }

        // Convert server changes
        if (response.getServerChanges() != null) {
            response.getServerChanges().forEach(change -> {
                DeltaChangeProto changeProto = DeltaChangeProto.newBuilder()
                    .setRecordId(change.getRecordId())
                    .setRecordType(change.getRecordType())
                    .setChangeType(change.getChangeType().name())
                    .setVersion(change.getVersion())
                    .setTimestamp(change.getTimestamp().toString())
                    .setData(change.getData() != null ? change.getData() : "")
                    .setDocumentId(change.getDocumentId() != null ? change.getDocumentId() : "")
                    .setIsCrdtEnabled(change.getIsCrdtEnabled() != null ? change.getIsCrdtEnabled() : false)
                    .build();
                builder.addServerChanges(changeProto);
            });
        }

        return builder.build();
    }
}