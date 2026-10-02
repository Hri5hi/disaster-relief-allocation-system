package com.groupa13.disasterrelief.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for the 'volunteers' table (INHERITS people).
 * Schema: docs/3.3_relational_schema.md Relation 8
 *
 * Volunteer-specific columns:
 *   skills       TEXT[]   -- PostgreSQL array; Java List<String>
 *   availability TSRANGE  -- stored/displayed as a string literal
 *   assigned_zone INTEGER -- FK → zones(zone_id), nullable
 *
 * JDBC binding:
 *   skills: conn.createArrayOf("text", skills.toArray(new String[0]))
 *   availability: PGobject with type "tsrange"
 */
public class Volunteer extends Person {

    /** Skill tags. PostgreSQL TEXT[]. JDBC: createArrayOf("text", ...) */
    private List<String> skills;

    /**
     * Availability window as a tsrange literal string,
     * e.g. "[2026-07-09 08:00, 2026-07-20 18:00)".
     * Null or empty means not specified.
     * JDBC: PGobject with type "tsrange"
     */
    private String availability;

    /** FK → zones(zone_id). Nullable. */
    private Integer assignedZone;

    /** Display-friendly zone name (joined from zones, not a DB column). */
    private String zoneName;

    public Volunteer() {
        this.skills = new ArrayList<>();
    }

    public Volunteer(int personId, String name, String contact, String email,
                     List<String> skills, String availability, Integer assignedZone) {
        super(personId, name, contact, email);
        this.skills = skills != null ? skills : new ArrayList<>();
        this.availability = availability;
        this.assignedZone = assignedZone;
    }

    // --- Getters ---

    public List<String> getSkills() { return skills; }

    /**
     * Returns skills as a comma-separated string for JavaFX TableColumn display.
     * PropertyValueFactory in the controller binds to "skillsDisplay".
     */
    public String getSkillsDisplay() {
        return skills == null || skills.isEmpty() ? "" : String.join(", ", skills);
    }

    public String getAvailability() { return availability; }
    public Integer getAssignedZone() { return assignedZone; }
    public String getZoneName() { return zoneName; }

    // --- Setters ---

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }
    public void setAvailability(String availability) { this.availability = availability; }
    public void setAssignedZone(Integer assignedZone) { this.assignedZone = assignedZone; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
