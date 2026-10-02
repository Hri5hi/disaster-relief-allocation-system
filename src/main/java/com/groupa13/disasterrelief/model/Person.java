package com.groupa13.disasterrelief.model;

/**
 * Base model for the 'people' table.
 * Corresponds to the PostgreSQL superclass table in the ISA hierarchy.
 * Both Volunteer and AffectedPerson extend this class.
 *
 * Schema: docs/3.3_relational_schema.md Relation 7
 * DB table: people(person_id, name, contact, email)
 *
 * Note: PostgreSQL's INHERITS means SELECT * FROM people returns
 * rows from volunteers and affected_persons too.
 */
public abstract class Person {

    protected int personId;
    protected String name;
    protected String contact;
    protected String email;

    protected Person() {}

    protected Person(int personId, String name, String contact, String email) {
        this.personId = personId;
        this.name = name;
        this.contact = contact;
        this.email = email;
    }

    public int getPersonId() { return personId; }
    public void setPersonId(int personId) { this.personId = personId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
