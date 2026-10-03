package com.groupa13.disasterrelief.model;

/**
 * Model for the 'volunteer_shifts' table.
 * Schema: project_description_v2.md Table 12
 *
 * Exclusion constraint: EXCLUDE USING gist (volunteer_id WITH =, duty_period WITH &&)
 * — same volunteer cannot have overlapping shifts (enforced at DB level).
 */
public class VolunteerShift {

    private int shiftId;
    private int volunteerId;
    private int zoneId;
    // JDBC binding: PGobject with type "tsrange" and value equal to this
    // tsrange literal, e.g. "[2026-07-09 08:00, 2026-07-09 20:00)".
    // Reading: rs.getString("duty_period") returns PostgreSQL's literal form.
    private String dutyPeriod;    // PostgreSQL tsrange, e.g. "[2026-07-09 08:00, 2026-07-09 20:00)"
    private String volunteerName; // Display-friendly (joined from volunteers)
    private String zoneName;      // Display-friendly (joined from zones)

    public VolunteerShift() {
    }

    public VolunteerShift(int shiftId, int volunteerId, int zoneId, String dutyPeriod) {
        this.shiftId = shiftId;
        this.volunteerId = volunteerId;
        this.zoneId = zoneId;
        this.dutyPeriod = dutyPeriod;
    }

    // --- Getters ---

    public int getShiftId() {
        return shiftId;
    }

    public int getVolunteerId() {
        return volunteerId;
    }

    public int getZoneId() {
        return zoneId;
    }

    public String getDutyPeriod() {
        return dutyPeriod;
    }

    public String getVolunteerName() {
        return volunteerName;
    }

    public String getZoneName() {
        return zoneName;
    }

    // --- Setters ---

    public void setShiftId(int shiftId) {
        this.shiftId = shiftId;
    }

    public void setVolunteerId(int volunteerId) {
        this.volunteerId = volunteerId;
    }

    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    public void setDutyPeriod(String dutyPeriod) {
        this.dutyPeriod = dutyPeriod;
    }

    public void setVolunteerName(String volunteerName) {
        this.volunteerName = volunteerName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    @Override
    public String toString() {
        return "Shift #" + shiftId + " — " + volunteerName;
    }
}
