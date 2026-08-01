package com.groupa13.disasterrelief.model;

/**
 * Model for the 'volunteers' table (INHERITS people).
 * Schema: project_description_v2.md Table 8
 *
 * Fields from people: person_id, name, contact, email
 * Volunteer-specific: skills (TEXT[]), availability (tsrange), assigned_zone (FK zones)
 */
public class Volunteer {

    private int personId;
    private String name;
    private String contact;
    private String email;
    private String skills;        // PostgreSQL TEXT[] displayed as comma-separated, e.g. "first_aid, driving"
    private String availability;  // PostgreSQL tsrange displayed as string, e.g. "[2026-07-09 08:00, 2026-07-20 18:00)"
    private Integer assignedZone; // FK → zones(zone_id), nullable
    private String zoneName;      // Display-friendly zone name (joined from zones table)

    public Volunteer() {
    }

    public Volunteer(int personId, String name, String contact, String email,
                     String skills, String availability, Integer assignedZone) {
        this.personId = personId;
        this.name = name;
        this.contact = contact;
        this.email = email;
        this.skills = skills;
        this.availability = availability;
        this.assignedZone = assignedZone;
    }

    // --- Getters ---

    public int getPersonId() {
        return personId;
    }

    public String getName() {
        return name;
    }

    public String getContact() {
        return contact;
    }

    public String getEmail() {
        return email;
    }

    public String getSkills() {
        return skills;
    }

    public String getAvailability() {
        return availability;
    }

    public Integer getAssignedZone() {
        return assignedZone;
    }

    public String getZoneName() {
        return zoneName;
    }

    // --- Setters ---

    public void setPersonId(int personId) {
        this.personId = personId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public void setAssignedZone(Integer assignedZone) {
        this.assignedZone = assignedZone;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
