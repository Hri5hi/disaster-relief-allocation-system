package com.groupa13.disasterrelief.util;

/**
 * Singleton session context — holds the identity, role, and zone (if applicable)
 * of the currently authenticated user for the lifetime of the application session.
 *
 * <p>Usage pattern:
 * <pre>
 *   UserSession session = UserSession.getInstance();
 *   session.setUserId(userId);
 *   session.setUserName(displayName);
 *   session.setRole(UserRole.ZONE_COORDINATOR);
 *   session.setAssignedZoneId(3); // non-null only for ZONE_COORDINATOR
 * </pre>
 *
 * <p>Call {@link #clear()} on logout to reset the session.
 *
 * @author Developer A
 */
public class UserSession {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static UserSession instance;

    /** Returns the single application-wide session instance. */
    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    private UserSession() {
        // private — use getInstance()
    }

    // -------------------------------------------------------------------------
    // Session State
    // -------------------------------------------------------------------------

    private String userId;
    private String userName;
    private UserRole role;

    /**
     * Non-null only when {@code role == ZONE_COORDINATOR}.
     * Corresponds to {@code zones.zone_id} in the database.
     * The DAO layer must issue {@code SET app.current_zone_id = <assignedZoneId>}
     * on every connection before running RLS-protected queries.
     */
    private Integer assignedZoneId;

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    /**
     * Returns the zone ID assigned to a {@code ZONE_COORDINATOR}, or {@code null}
     * for all other roles.
     */
    public Integer getAssignedZoneId() {
        return assignedZoneId;
    }

    public void setAssignedZoneId(Integer assignedZoneId) {
        this.assignedZoneId = assignedZoneId;
    }

    // -------------------------------------------------------------------------
    // Session Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Clears all session state. Call this on logout before returning to the
     * login screen so no stale role/zone data leaks to the next user.
     */
    public void clear() {
        userId = null;
        userName = null;
        role = null;
        assignedZoneId = null;
    }

    @Override
    public String toString() {
        return "UserSession{userId='" + userId + "', role=" + role
                + ", assignedZoneId=" + assignedZoneId + "}";
    }
}
