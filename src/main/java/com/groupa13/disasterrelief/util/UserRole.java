package com.groupa13.disasterrelief.util;

/**
 * The five supported RBAC roles defined in project_description_v2.md §4.
 * Used throughout the UI layer to gate screen access and tailor dashboard views.
 */
public enum UserRole {
    SYSTEM_ADMIN("System Administrator"),
    ZONE_COORDINATOR("Zone Coordinator"),
    WAREHOUSE_MANAGER("Warehouse / Inventory Manager"),
    VOLUNTEER_COORDINATOR("Volunteer Coordinator"),
    ANALYTICS_USER("Reporting / Analytics User");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label shown in the login ComboBox and header badge. */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
