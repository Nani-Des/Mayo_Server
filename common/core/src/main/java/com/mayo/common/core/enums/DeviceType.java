package com.mayo.common.core.enums;

/**
 * Device types that can access the Mayo EMR system
 */
public enum DeviceType {
    MOBILE("Patient mobile app (iOS/Android)"),
    DESKTOP("Hospital desktop application"),
    TABLET("Tablet device");

    private final String description;

    DeviceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPatientDevice() {
        return this == MOBILE || this == TABLET;
    }

    public boolean isHospitalDevice() {
        return this == DESKTOP;
    }
}
