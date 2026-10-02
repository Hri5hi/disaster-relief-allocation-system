package com.groupa13.disasterrelief.dao.people;

import com.groupa13.disasterrelief.model.Volunteer;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the 'volunteers' table.
 * All methods receive a live Connection from the service transaction scope.
 * No method opens or closes connections.
 * No SQL string concatenation is used anywhere.
 * All parameters are bound with PreparedStatement.
 */
public class VolunteerDao {

    // ── Insert ────────────────────────────────────────────────────────────────

    /**
     * INSERT a new volunteer. Returns the generated person_id.
     *
     * SQL: INSERT INTO volunteers (name, contact, email, skills, availability, assigned_zone)
     *      VALUES (?, ?, ?, ?, ?::tsrange, ?) RETURNING person_id
     *
     * @param conn       live connection (in transaction)
     * @param name       required, NOT NULL
     * @param contact    nullable
     * @param email      nullable
     * @param skills     nullable list; empty list stored as empty TEXT[]
     * @param availability nullable tsrange literal; null if not specified
     * @param zoneId     nullable FK to zones
     * @return generated person_id
     * @throws SQLException on any DB error
     */
    public int insert(Connection conn, String name, String contact, String email,
                      List<String> skills, String availability, Integer zoneId)
            throws SQLException {
        String sql = "INSERT INTO volunteers (name, contact, email, skills, availability, assigned_zone) "
                   + "VALUES (?, ?, ?, ?, ?::tsrange, ?) RETURNING person_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            // Bind TEXT[]
            if (skills == null || skills.isEmpty()) {
                ps.setArray(4, conn.createArrayOf("text", new String[0]));
            } else {
                ps.setArray(4, conn.createArrayOf("text", skills.toArray(new String[0])));
            }
            // Bind tsrange
            if (availability == null || availability.isBlank()) {
                ps.setNull(5, Types.OTHER);
            } else {
                PGobject tsrange = new PGobject();
                tsrange.setType("tsrange");
                tsrange.setValue(availability);
                ps.setObject(5, tsrange);
            }
            // Bind zone FK
            if (zoneId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, zoneId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("person_id");
                }
                throw new SQLException("INSERT into volunteers returned no RETURNING id");
            }
        }
    }

    // ── Select All ────────────────────────────────────────────────────────────

    /**
     * SELECT all volunteers with joined zone name.
     *
     * SQL: SELECT v.*, z.name AS zone_name
     *      FROM volunteers v
     *      LEFT JOIN zones z ON v.assigned_zone = z.zone_id
     *      ORDER BY v.name
     */
    public List<Volunteer> findAll(Connection conn) throws SQLException {
        String sql = "SELECT v.person_id, v.name, v.contact, v.email, "
                   + "       v.skills, v.availability::text, v.assigned_zone, z.name AS zone_name "
                   + "FROM volunteers v "
                   + "LEFT JOIN zones z ON v.assigned_zone = z.zone_id "
                   + "ORDER BY v.name";
        List<Volunteer> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Select by ID ──────────────────────────────────────────────────────────

    /**
     * SELECT volunteer by person_id.
     *
     * SQL: SELECT v.*, z.name AS zone_name
     *      FROM volunteers v
     *      LEFT JOIN zones z ON v.assigned_zone = z.zone_id
     *      WHERE v.person_id = ?
     */
    public Optional<Volunteer> findById(Connection conn, int personId) throws SQLException {
        String sql = "SELECT v.person_id, v.name, v.contact, v.email, "
                   + "       v.skills, v.availability::text, v.assigned_zone, z.name AS zone_name "
                   + "FROM volunteers v "
                   + "LEFT JOIN zones z ON v.assigned_zone = z.zone_id "
                   + "WHERE v.person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    // ── Select by Skill ───────────────────────────────────────────────────────

    /**
     * SELECT volunteers who have ALL of the given skills.
     * Uses PostgreSQL array containment: skills @> ARRAY[...]::text[]
     *
     * @param conn   live connection
     * @param skills one or more skills to match (all must be present)
     */
    public List<Volunteer> findBySkills(Connection conn, List<String> skills) throws SQLException {
        // Build: WHERE skills @> ARRAY[?]::text[]
        // We bind the skills as a PostgreSQL text[] parameter
        String sql = "SELECT v.person_id, v.name, v.contact, v.email, "
                   + "       v.skills, v.availability::text, v.assigned_zone, z.name AS zone_name "
                   + "FROM volunteers v "
                   + "LEFT JOIN zones z ON v.assigned_zone = z.zone_id "
                   + "WHERE v.skills @> ? "
                   + "ORDER BY v.name";
        List<Volunteer> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Bind as text[] using createArrayOf
            Array skillArray = conn.createArrayOf("text", skills.toArray(new String[0]));
            ps.setArray(1, skillArray);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * UPDATE volunteer fields. All parameters are nullable — only non-null
     * values from the caller update the corresponding columns.
     * Uses COALESCE to preserve existing values when parameter is null.
     *
     * SQL: UPDATE volunteers
     *      SET name=COALESCE(?,name), contact=COALESCE(?,contact),
     *          email=COALESCE(?,email), skills=COALESCE(?,skills),
     *          availability=COALESCE(?::tsrange,availability),
     *          assigned_zone=COALESCE(?,assigned_zone)
     *      WHERE person_id=?
     */
    public boolean update(Connection conn, int personId, String name, String contact, String email,
                          List<String> skills, String availability, Integer zoneId)
            throws SQLException {
        String sql = "UPDATE volunteers "
                   + "SET name = COALESCE(?, name), "
                   + "    contact = COALESCE(?, contact), "
                   + "    email = COALESCE(?, email), "
                   + "    skills = COALESCE(?, skills), "
                   + "    availability = COALESCE(?::tsrange, availability), "
                   + "    assigned_zone = COALESCE(?, assigned_zone) "
                   + "WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            if (skills == null) {
                ps.setNull(4, Types.ARRAY);
            } else {
                ps.setArray(4, conn.createArrayOf("text", skills.toArray(new String[0])));
            }
            if (availability == null || availability.isBlank()) {
                ps.setNull(5, Types.OTHER);
            } else {
                PGobject tsrange = new PGobject();
                tsrange.setType("tsrange");
                tsrange.setValue(availability);
                ps.setObject(5, tsrange);
            }
            if (zoneId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, zoneId);
            }
            ps.setInt(7, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * DELETE volunteer by person_id.
     * CASCADE will also delete their volunteer_shifts.
     *
     * SQL: DELETE FROM volunteers WHERE person_id = ?
     */
    public boolean delete(Connection conn, int personId) throws SQLException {
        String sql = "DELETE FROM volunteers WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── ResultSet Mapping ─────────────────────────────────────────────────────

    private Volunteer mapRow(ResultSet rs) throws SQLException {
        Volunteer v = new Volunteer();
        v.setPersonId(rs.getInt("person_id"));
        v.setName(rs.getString("name"));
        v.setContact(rs.getString("contact"));
        v.setEmail(rs.getString("email"));
        // Map TEXT[] skills
        Array skillsArray = rs.getArray("skills");
        if (skillsArray != null) {
            v.setSkills(new ArrayList<>(Arrays.asList((String[]) skillsArray.getArray())));
        }
        v.setAvailability(rs.getString("availability")); // tsrange as string
        int zone = rs.getInt("assigned_zone");
        v.setAssignedZone(rs.wasNull() ? null : zone);
        v.setZoneName(rs.getString("zone_name")); // nullable, from LEFT JOIN
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
