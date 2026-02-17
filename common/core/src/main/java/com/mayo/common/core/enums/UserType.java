package com.mayo.common.core.enums;

/**
 * User types in the Mayo EMR system
 */
public enum UserType {
    SUPER_ADMIN("Super Administrator - system-wide access"),
    HOSPITAL_ADMIN("Hospital Administrator - hospital-wide access"),
    DOCTOR("Doctor - can diagnose and prescribe"),
    NURSE("Nurse - can record vitals and observations"),
    RECEPTIONIST("Receptionist - patient registration"),
    PATIENT("Patient - mobile app user");

    private final String description;

    UserType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    public boolean isHospitalAdmin() {
        return this == HOSPITAL_ADMIN;
    }

    public boolean isAdmin() {
        return this == HOSPITAL_ADMIN || this == SUPER_ADMIN;
    }

    public boolean isHealthcareProvider() {
        return this == DOCTOR || this == NURSE;
    }

    public boolean isStaff() {
        return this == DOCTOR || this == NURSE || this == RECEPTIONIST;
    }

    public boolean isPatient() {
        return this == PATIENT;
    }

    public boolean isHospitalStaff() {
        return this != PATIENT;
    }
}
