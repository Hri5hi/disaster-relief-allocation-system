package com.groupa13.disasterrelief.model;

/**
 * Model for the 'affected_persons' table (INHERITS people).
 * Schema: docs/3.3_relational_schema.md Relation 9
 *
 * Affected-person-specific columns:
 *   medical_needs JSONB         -- stored as JSON text string in Java
 *   shelter_id    INTEGER       -- FK → shelters(shelter_id), nullable
 *   registered_at TIMESTAMPTZ  -- stored as display string (OffsetDateTime.toString())
 *
 * JDBC binding:
 *   medical_needs: PGobject with type "jsonb"
 *   shelter_id: setInt() or setNull(Types.INTEGER)
 */
public class AffectedPerson extends Person {

    /**
     * JSON document for medical needs.
     * e.g. {"conditions": ["diabetes"], "wheelchair": true}
     * NULL or empty string means no recorded medical needs.
     * JDBC: PGobject with type "jsonb"
     */
    private String medicalNeeds;

    /** FK → shelters(shelter_id). Nullable. */
    private Integer shelterId;

    /** TIMESTAMPTZ stored as formatted string for display. */
    private String registeredAt;

    /** Display-friendly shelter name (joined from shelters, not a DB column). */
    private String shelterName;

    public AffectedPerson() {}

    public AffectedPerson(int personId, String name, String contact, String email,
                          String medicalNeeds, Integer shelterId, String registeredAt) {
        super(personId, name, contact, email);
        this.medicalNeeds = medicalNeeds;
        this.shelterId = shelterId;
        this.registeredAt = registeredAt;
    }

    // --- Getters ---
    public String getMedicalNeeds() { return medicalNeeds; }
    public Integer getShelterId() { return shelterId; }
    public String getRegisteredAt() { return registeredAt; }
    public String getShelterName() { return shelterName; }

    // --- Setters ---
    public void setMedicalNeeds(String medicalNeeds) { this.medicalNeeds = medicalNeeds; }
    public void setShelterId(Integer shelterId) { this.shelterId = shelterId; }
    public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }
    public void setShelterName(String shelterName) { this.shelterName = shelterName; }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
