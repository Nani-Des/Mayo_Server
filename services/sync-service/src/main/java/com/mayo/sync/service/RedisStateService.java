package com.mayo.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Centralized Redis state management for sync operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisStateService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Key prefixes
    private static final String SYNC_STATE_PREFIX = "sync:state:";
    private static final String SESSION_STATE_PREFIX = "sync:session:";
    private static final String DEVICE_STATE_PREFIX = "device:state:";
    private static final String CONFLICT_STATE_PREFIX = "conflict:state:";
    private static final String CACHE_PREFIX = "cache:";

    /**
     * Store sync state for user/device combination
     */
    public void storeSyncState(UUID userId, String deviceId, Long lastVersion, LocalDateTime lastSync) {
        String key = SYNC_STATE_PREFIX + userId + ":" + deviceId;
        Map<String, Object> state = new HashMap<>();
        state.put("lastVersion", lastVersion);
        state.put("lastSync", lastSync.toString());
        state.put("updatedAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, state);
        redisTemplate.expire(key, 30, TimeUnit.DAYS); // 30 days TTL

        log.debug("Stored sync state for user {} device {}: version {}", userId, deviceId, lastVersion);
    }

    /**
     * Get sync state for user/device combination
     */
    public SyncState getSyncState(UUID userId, String deviceId) {
        String key = SYNC_STATE_PREFIX + userId + ":" + deviceId;
        Map<Object, Object> state = redisTemplate.opsForHash().entries(key);

        if (state.isEmpty()) {
            return null;
        }

        return new SyncState(
            ((Number) state.get("lastVersion")).longValue(),
            LocalDateTime.parse((String) state.get("lastSync")),
            LocalDateTime.parse((String) state.get("updatedAt"))
        );
    }

    /**
     * Store active sync session state
     */
    public void storeSessionState(UUID sessionId, UUID userId, String deviceId, String status) {
        String key = SESSION_STATE_PREFIX + sessionId;
        Map<String, Object> sessionState = new HashMap<>();
        sessionState.put("userId", userId.toString());
        sessionState.put("deviceId", deviceId);
        sessionState.put("status", status);
        sessionState.put("startTime", LocalDateTime.now().toString());
        sessionState.put("lastActivity", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, sessionState);
        redisTemplate.expire(key, 24, TimeUnit.HOURS); // 24 hours TTL

        log.debug("Stored session state for session {}", sessionId);
    }

    /**
     * Update session activity
     */
    public void updateSessionActivity(UUID sessionId) {
        String key = SESSION_STATE_PREFIX + sessionId;
        redisTemplate.opsForHash().put(key, "lastActivity", LocalDateTime.now().toString());
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
    }

    /**
     * Get active sessions for user
     */
    public List<SessionState> getActiveSessions(UUID userId) {
        String pattern = SESSION_STATE_PREFIX + "*";
        Set<String> keys = redisTemplate.keys(pattern);

        List<SessionState> activeSessions = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                Map<Object, Object> state = redisTemplate.opsForHash().entries(key);
                if (!state.isEmpty()) {
                    String storedUserId = (String) state.get("userId");
                    String status = (String) state.get("status");

                    if (storedUserId != null && storedUserId.equals(userId.toString()) &&
                        "ACTIVE".equals(status)) {
                        activeSessions.add(new SessionState(
                            extractSessionIdFromKey(key),
                            UUID.fromString(storedUserId),
                            (String) state.get("deviceId"),
                            status,
                            LocalDateTime.parse((String) state.get("startTime")),
                            LocalDateTime.parse((String) state.get("lastActivity"))
                        ));
                    }
                }
            }
        }

        return activeSessions;
    }

    /**
     * Store device state
     */
    public void storeDeviceState(String deviceId, UUID userId, String status, LocalDateTime lastSeen) {
        String key = DEVICE_STATE_PREFIX + deviceId;
        Map<String, Object> deviceState = new HashMap<>();
        deviceState.put("userId", userId.toString());
        deviceState.put("status", status);
        deviceState.put("lastSeen", lastSeen.toString());
        deviceState.put("updatedAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, deviceState);
        redisTemplate.expire(key, 7, TimeUnit.DAYS); // 7 days TTL

        log.debug("Stored device state for device {}", deviceId);
    }

    /**
     * Get device state
     */
    public DeviceState getDeviceState(String deviceId) {
        String key = DEVICE_STATE_PREFIX + deviceId;
        Map<Object, Object> state = redisTemplate.opsForHash().entries(key);

        if (state.isEmpty()) {
            return null;
        }

        return new DeviceState(
            UUID.fromString((String) state.get("userId")),
            (String) state.get("status"),
            LocalDateTime.parse((String) state.get("lastSeen")),
            LocalDateTime.parse((String) state.get("updatedAt"))
        );
    }

    /**
     * Cache data with TTL
     */
    public void cacheData(String cacheKey, Object data, long ttlSeconds) {
        String key = CACHE_PREFIX + cacheKey;
        redisTemplate.opsForValue().set(key, data, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Get cached data
     */
    public Object getCachedData(String cacheKey) {
        String key = CACHE_PREFIX + cacheKey;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Clear cache by pattern
     */
    public void clearCache(String pattern) {
        String fullPattern = CACHE_PREFIX + pattern;
        Set<String> keys = redisTemplate.keys(fullPattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Cleared {} cache entries matching pattern {}", keys.size(), pattern);
        }
    }

    /**
     * Store conflict resolution state
     */
    public void storeConflictState(UUID conflictId, String resolution, LocalDateTime resolvedAt) {
        String key = CONFLICT_STATE_PREFIX + conflictId;
        Map<String, Object> conflictState = new HashMap<>();
        conflictState.put("resolution", resolution);
        conflictState.put("resolvedAt", resolvedAt.toString());
        conflictState.put("updatedAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, conflictState);
        redisTemplate.expire(key, 30, TimeUnit.DAYS);

        log.debug("Stored conflict state for conflict {}", conflictId);
    }

    /**
     * Get conflict resolution state
     */
    public ConflictState getConflictState(UUID conflictId) {
        String key = CONFLICT_STATE_PREFIX + conflictId;
        Map<Object, Object> state = redisTemplate.opsForHash().entries(key);

        if (state.isEmpty()) {
            return null;
        }

        return new ConflictState(
            (String) state.get("resolution"),
            LocalDateTime.parse((String) state.get("resolvedAt")),
            LocalDateTime.parse((String) state.get("updatedAt"))
        );
    }

    /**
     * Clean up expired states
     */
    public void cleanupExpiredStates() {
        // This would be called by a scheduled task
        // Redis TTL handles most cleanup, but this can handle custom cleanup logic
        log.info("Running Redis state cleanup");
        // Implementation would scan and clean up expired or orphaned states
    }

    /**
     * Get Redis statistics
     */
    public RedisStats getStats() {
        // Get key counts for different prefixes
        long syncStates = countKeys(SYNC_STATE_PREFIX + "*");
        long sessions = countKeys(SESSION_STATE_PREFIX + "*");
        long devices = countKeys(DEVICE_STATE_PREFIX + "*");
        long conflicts = countKeys(CONFLICT_STATE_PREFIX + "*");
        long cacheEntries = countKeys(CACHE_PREFIX + "*");

        return new RedisStats(syncStates, sessions, devices, conflicts, cacheEntries);
    }

    private long countKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys != null ? keys.size() : 0;
    }

    private UUID extractSessionIdFromKey(String key) {
        return UUID.fromString(key.substring(SESSION_STATE_PREFIX.length()));
    }

    // DTO classes
    public static class SyncState {
        public final Long lastVersion;
        public final LocalDateTime lastSync;
        public final LocalDateTime updatedAt;

        public SyncState(Long lastVersion, LocalDateTime lastSync, LocalDateTime updatedAt) {
            this.lastVersion = lastVersion;
            this.lastSync = lastSync;
            this.updatedAt = updatedAt;
        }
    }

    public static class SessionState {
        public final UUID sessionId;
        public final UUID userId;
        public final String deviceId;
        public final String status;
        public final LocalDateTime startTime;
        public final LocalDateTime lastActivity;

        public SessionState(UUID sessionId, UUID userId, String deviceId, String status,
                          LocalDateTime startTime, LocalDateTime lastActivity) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.deviceId = deviceId;
            this.status = status;
            this.startTime = startTime;
            this.lastActivity = lastActivity;
        }
    }

    public static class DeviceState {
        public final UUID userId;
        public final String status;
        public final LocalDateTime lastSeen;
        public final LocalDateTime updatedAt;

        public DeviceState(UUID userId, String status, LocalDateTime lastSeen, LocalDateTime updatedAt) {
            this.userId = userId;
            this.status = status;
            this.lastSeen = lastSeen;
            this.updatedAt = updatedAt;
        }
    }

    public static class ConflictState {
        public final String resolution;
        public final LocalDateTime resolvedAt;
        public final LocalDateTime updatedAt;

        public ConflictState(String resolution, LocalDateTime resolvedAt, LocalDateTime updatedAt) {
            this.resolution = resolution;
            this.resolvedAt = resolvedAt;
            this.updatedAt = updatedAt;
        }
    }

    public static class RedisStats {
        public final long syncStates;
        public final long activeSessions;
        public final long deviceStates;
        public final long conflictStates;
        public final long cacheEntries;

        public RedisStats(long syncStates, long activeSessions, long deviceStates,
                         long conflictStates, long cacheEntries) {
            this.syncStates = syncStates;
            this.activeSessions = activeSessions;
            this.deviceStates = deviceStates;
            this.conflictStates = conflictStates;
            this.cacheEntries = cacheEntries;
        }
    }
}