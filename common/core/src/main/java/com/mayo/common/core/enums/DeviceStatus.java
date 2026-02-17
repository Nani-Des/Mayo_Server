package com.mayo.common.core.enums;

/**
 * Device registration status
 */
public enum DeviceStatus {
    PENDING("Device is registered but not yet paired"),
    ACTIVE("Device is active and can access the system"),
    INACTIVE("Device is registered but currently inactive"),
    BLOCKED("Device access has been blocked");

    private final String description;

    DeviceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isBlocked() {
        return this == BLOCKED;
    }
}