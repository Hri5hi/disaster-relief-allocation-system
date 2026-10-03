package com.groupa13.disasterrelief.dao.people;

import com.groupa13.disasterrelief.model.AffectedPerson;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the 'affected_persons' table.
 * All methods receive a live Connection from the service transaction scope.
 */
public class AffectedPersonDao {

    // ── Insert ────────────────────────────────────────────────────────────────

    /**
     * INSERT a new affected person. Returns the generated person_id.
     * The trg_auto_update_shelter_occupancy trigger fires automatically.
     *
     * SQL: INSERT INTO affected_persons (name, contact, email, medical_needs, shelter_id)
     *      VALUES (?, ?, ?, ?::jsonb, ?) RETURNING person_id, registered_at
     */
    public AffectedPerson insert(Connection conn, String name, String contact, String email,
                                 String medicalNeeds, Integer shelterId) throws SQLException {
        String sql = "INSERT INTO affected_persons (name, contact, email, medical_needs, shelter_id) "
                   + "VALUES (?, ?, ?, ?::jsonb, ?) "
                   + "RETURNING person_id, registered_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            bindJsonb(ps, 4, medicalNeeds);
            if (shelterId == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, shelterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AffectedPerson ap = new AffectedPerson();
                    ap.setPersonId(rs.getInt("person_id"));
                    ap.setName(name);
                    ap.setContact(contact);
                    ap.setEmail(email);
                    ap.setMedicalNeeds(medicalNeeds);
                    ap.setShelterId(shelterId);
                    ap.setRegisteredAt(rs.getString("registered_at"));
                    return ap;
                }
                throw new SQLException("INSERT into affected_persons returned no row");
            }
        }
    }

    // ── Select All ────────────────────────────────────────────────────────────

    /**
     * SELECT all affected persons, joined with shelter name.
     */
    public List<AffectedPerson> findAll(Connection conn) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "ORDER BY ap.registered_at DESC";
        List<AffectedPerson> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    // ── Select by Zone ────────────────────────────────────────────────────────

    /**
     * SELECT affected persons at shelters belonging to a given zone.
     * Used for Zone Coordinator filtered view.
     *
     * SQL: SELECT ap.*, s.name AS shelter_name
     *      FROM affected_persons ap
     *      JOIN shelters s ON ap.shelter_id = s.shelter_id
     *      JOIN zones z ON ... -- shelters don't directly FK to zones
     *      -- Filter: only shelters that are the primary_shelter of the given zone
     *      -- OR: persons at any shelter in a zone (requires zone-shelter relationship)
     *
     * NOTE: The schema does not have a shelters.zone_id FK directly.
     * Zones reference shelters via primary_shelter_id (the primary shelter).
     * For zone filtering, query affected_persons WHERE shelter_id =
     *   (SELECT primary_shelter_id FROM zones WHERE zone_id = ?)
     * This returns persons at the zone's primary shelter only.
     * A broader approach would require Dev A's shelter-zone relationship.
     * For now, use primary_shelter_id filter:
     */
    public List<AffectedPerson> findByZone(Connection conn, int zoneId) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "WHERE ap.shelter_id = (SELECT primary_shelter_id FROM zones WHERE zone_id = ?) "
                   + "ORDER BY ap.registered_at DESC";
        List<AffectedPerson> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Select by ID ──────────────────────────────────────────────────────────

    public Optional<AffectedPerson> findById(Connection conn, int personId) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "WHERE ap.person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    // ── Select by Shelter ─────────────────────────────────────────────────────

    public List<AffectedPerson> findByShelterId(Connection conn, int shelterId) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "WHERE ap.shelter_id = ? "
                   + "ORDER BY ap.registered_at DESC";
        List<AffectedPerson> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shelterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * UPDATE affected person. Uses COALESCE for partial updates.
     * The trg_auto_update_shelter_occupancy fires on UPDATE OF shelter_id.
     */
    public boolean update(Connection conn, int personId, String name, String contact,
                          String email, String medicalNeeds, Integer shelterId) throws SQLException {
        String sql = "UPDATE affected_persons "
                   + "SET name = COALESCE(?, name), "
                   + "    contact = COALESCE(?, contact), "
                   + "    email = COALESCE(?, email), "
                   + "    medical_needs = COALESCE(?::jsonb, medical_needs), "
                   + "    shelter_id = COALESCE(?, shelter_id) "
                   + "WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            bindJsonb(ps, 4, medicalNeeds);
            if (shelterId == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, shelterId);
            ps.setInt(6, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * DELETE affected person by person_id.
     * The trg_auto_update_shelter_occupancy fires automatically.
     * Java does NOT manually update occupancy.
     */
    public boolean delete(Connection conn, int personId) throws SQLException {
        String sql = "DELETE FROM affected_persons WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── ResultSet Mapping ─────────────────────────────────────────────────────

    private AffectedPerson mapRow(ResultSet rs) throws SQLException {
        AffectedPerson ap = new AffectedPerson();
        ap.setPersonId(rs.getInt("person_id"));
        ap.setName(rs.getString("name"));
        ap.setContact(rs.getString("contact"));
        ap.setEmail(rs.getString("email"));
        ap.setMedicalNeeds(rs.getString("medical_needs")); // JSONB as text
        int sid = rs.getInt("shelter_id");
        ap.setShelterId(rs.wasNull() ? null : sid);
        ap.setRegisteredAt(rs.getString("registered_at"));
        ap.setShelterName(rs.getString("shelter_name"));
        return ap;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, value);
    }

    /**
     * Bind a JSONB value to a PreparedStatement parameter.
     * If the value is null or blank, bind SQL NULL.
     */
    private void bindJsonb(PreparedStatement ps, int index, String json) throws SQLException {
        if (json == null || json.isBlank()) {
            ps.setNull(index, Types.OTHER);
        } else {
            PGobject jsonb = new PGobject();
            jsonb.setType("jsonb");
            jsonb.setValue(json);
            ps.setObject(index, jsonb);
        }
    }
}
