package com.mayo.common.core.enums;

/**
 * Fine-grained permissions for EMR system
 */
public enum Permission {
    // Super Admin permissions (system-wide)
    SUPER_ADMIN_ALL_ACCESS("Full system-wide access"),
    SUPER_ADMIN_MANAGE_ALL_HOSPITALS("Manage all hospitals"),
    SUPER_ADMIN_MANAGE_SYSTEM_CONFIG("Manage system configuration"),
    SUPER_ADMIN_VIEW_ALL_AUDIT_LOGS("View all audit logs across hospitals"),
    SUPER_ADMIN_MANAGE_ROLES("Manage global roles and permissions"),
    SUPER_ADMIN_MANAGE_SERVICE_CONFIG("Manage microservice configuration"),

    // Hospital Admin permissions (hospital-wide)
    HOSPITAL_ADMIN_ALL_ACCESS("Full hospital-wide access"),
    HOSPITAL_ADMIN_MANAGE_HOSPITAL_USERS("Manage hospital users"),
    HOSPITAL_ADMIN_MANAGE_HOSPITAL_ROLES("Manage hospital roles and permissions"),
    HOSPITAL_ADMIN_VIEW_HOSPITAL_AUDIT_LOGS("View hospital audit logs"),
    HOSPITAL_ADMIN_MANAGE_HOSPITAL_SETTINGS("Manage hospital settings"),
    HOSPITAL_ADMIN_MANAGE_DEPARTMENTS("Manage hospital departments"),
    HOSPITAL_ADMIN_VIEW_REPORTS("View hospital reports and analytics"),

    // Patient permissions
    PATIENT_READ_OWN_RECORDS("Read own medical records"),
    PATIENT_UPDATE_OWN_PROFILE("Update own profile"),
    PATIENT_VIEW_APPOINTMENTS("View own appointments"),

    // Healthcare provider permissions
    PROVIDER_READ_PATIENT_RECORDS("Read patient medical records"),
    PROVIDER_WRITE_PATIENT_RECORDS("Write patient medical records"),
    PROVIDER_PRESCRIBE_MEDICATIONS("Prescribe medications"),
    PROVIDER_ORDER_TESTS("Order diagnostic tests"),
    PROVIDER_VIEW_ALL_PATIENTS("View all patients in practice"),

    // Nurse permissions
    NURSE_RECORD_VITALS("Record patient vitals"),
    NURSE_UPDATE_OBSERVATIONS("Update patient observations"),
    NURSE_ADMINISTER_MEDICATIONS("Administer medications"),


    // Receptionist permissions
    RECEPTIONIST_REGISTER_PATIENTS("Register new patients"),
    RECEPTIONIST_SCHEDULE_APPOINTMENTS("Schedule appointments"),
    RECEPTIONIST_MANAGE_INSURANCE("Manage insurance information"),

    // Sync permissions
    SYNC_READ_DEVICE_DATA("Read device synchronization data"),
    SYNC_WRITE_DEVICE_DATA("Write device synchronization data"),
    SYNC_MANAGE_DEVICES("Manage device registrations"),

    // Notification permissions
    NOTIFICATION_SEND_ALERTS("Send medical alerts"),
    NOTIFICATION_MANAGE_TEMPLATES("Manage notification templates"),
    NOTIFICATION_VIEW_DELIVERY_STATUS("View notification delivery status");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this permission is a super admin permission
     */
    public boolean isSuperAdminPermission() {
        return this.name().startsWith("SUPER_ADMIN");
    }

    /**
     * Check if this permission is a hospital admin permission
     */
    public boolean isHospitalAdminPermission() {
        return this.name().startsWith("HOSPITAL_ADMIN");
    }

    /**
     * Check if this permission is an admin permission (including legacy)
     */
    public boolean isAdminPermission() {
        return isSuperAdminPermission() || isHospitalAdminPermission();
    }

    /**
     * Check if this permission is a healthcare provider permission
     */
    public boolean isProviderPermission() {
        return this.name().startsWith("PROVIDER");
    }

    /**
     * Check if this permission is a nurse permission
     */
    public boolean isNursePermission() {
        return this.name().startsWith("NURSE");
    }

    /**
     * Check if this permission is a patient permission
     */
    public boolean isPatientPermission() {
        return this.name().startsWith("PATIENT");
    }

    /**
     * Get all permissions for Super Admin role
     */
    public static java.util.Set<Permission> getSuperAdminPermissions() {
        return java.util.Arrays.stream(values())
                .filter(Permission::isSuperAdminPermission)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all permissions for Hospital Admin role
     */
    public static java.util.Set<Permission> getHospitalAdminPermissions() {
        return java.util.Arrays.stream(values())
                .filter(p -> p.isHospitalAdminPermission() || p.isAdminPermission())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all permissions for Doctor role
     */
    public static java.util.Set<Permission> getDoctorPermissions() {
        return java.util.Arrays.stream(values())
                .filter(p -> p.isProviderPermission())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all permissions for Nurse role
     */
    public static java.util.Set<Permission> getNursePermissions() {
        return java.util.Arrays.stream(values())
                .filter(p -> p.isNursePermission() || p.isProviderPermission())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all permissions for Receptionist role
     */
    public static java.util.Set<Permission> getReceptionistPermissions() {
        return java.util.Arrays.stream(values())
                .filter(p -> p.name().startsWith("RECEPTIONIST"))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all permissions for Patient role
     */
    public static java.util.Set<Permission> getPatientPermissions() {
        return java.util.Arrays.stream(values())
                .filter(Permission::isPatientPermission)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get permissions based on user type
     */
    public static java.util.Set<Permission> getPermissionsForUserType(UserType userType) {
        if (userType == null) {
            return java.util.Set.of();
        }
        return switch (userType) {
            case SUPER_ADMIN -> getSuperAdminPermissions();
            case HOSPITAL_ADMIN -> getHospitalAdminPermissions();
            case DOCTOR -> getDoctorPermissions();
            case NURSE -> getNursePermissions();
            case RECEPTIONIST -> getReceptionistPermissions();
            case PATIENT -> getPatientPermissions();
        };
    }
}
