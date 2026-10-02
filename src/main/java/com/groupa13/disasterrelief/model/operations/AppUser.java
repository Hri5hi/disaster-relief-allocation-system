package com.groupa13.disasterrelief.model.operations;

import com.groupa13.disasterrelief.util.UserRole;

/**
 * Model representing an application account from the app_users table.
 */
public record AppUser(
        int userId,
        int personId,
        String username,
        String passwordHash,
        UserRole role,
        Integer assignedZoneId,
        boolean isActive
) {
    public AppUser {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (role == UserRole.ZONE_COORDINATOR && assignedZoneId == null) {
            throw new IllegalArgumentException("Zone coordinator must have an assigned zone");
        }
    }
}
