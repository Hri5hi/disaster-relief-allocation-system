package com.groupa13.disasterrelief.model;

/**
 * Model for the 'affected_persons' table (INHERITS people).
 * Schema: project_description_v2.md Table 9
 *
 * Fields from people: person_id, name, contact, email
 * Affected-person-specific: medical_needs (JSONB), shelter_id (FK shelters), registered_at
 */
public class AffectedPerson {

    private int personId;
    private String name;
    private String contact;
    private String email;
    private String medicalNeeds;  // PostgreSQL JSONB, e.g. {"conditions": ["diabetes"], "wheelchair": true}
    private Integer shelterId;    // FK → shelters(shelter_id)
    private String registeredAt;  // TIMESTAMPTZ as display string
    private String shelterName;   // Display-friendly (joined from shelters)

    public AffectedPerson() {
    }

    public AffectedPerson(int personId, String name, String contact, String email,
                          String medicalNeeds, Integer shelterId, String registeredAt) {
        this.personId = personId;
        this.name = name;
        this.contact = contact;
        this.email = email;
        this.medicalNeeds = medicalNeeds;
        this.shelterId = shelterId;
        this.registeredAt = registeredAt;
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

    public String getMedicalNeeds() {
        return medicalNeeds;
    }

    public Integer getShelterId() {
        return shelterId;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public String getShelterName() {
        return shelterName;
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

    public void setMedicalNeeds(String medicalNeeds) {
        this.medicalNeeds = medicalNeeds;
    }

    public void setShelterId(Integer shelterId) {
        this.shelterId = shelterId;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt;
    }

    public void setShelterName(String shelterName) {
        this.shelterName = shelterName;
    }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
