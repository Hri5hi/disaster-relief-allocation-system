package com.groupa13.disasterrelief.dao.people;

import com.groupa13.disasterrelief.model.VolunteerShift;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the 'volunteer_shifts' table.
 * The GiST exclusion constraint (volunteer_id WITH =, duty_period WITH &&)
 * is enforced by PostgreSQL. When it fires, SQLState is '23P01'.
 * The service layer maps this to: "Volunteer already has an overlapping shift."
 */
public class VolunteerShiftDao {

    // ── Insert ────────────────────────────────────────────────────────────────

    /**
     * INSERT a new shift. Returns the created VolunteerShift.
     * If exclusion constraint fires, throws SQLException with SQLState 23P01.
     *
     * SQL: INSERT INTO volunteer_shifts (volunteer_id, zone_id, duty_period)
     *      VALUES (?, ?, ?::tsrange)
     *      RETURNING shift_id
     */
    public VolunteerShift insert(Connection conn, int volunteerId, int zoneId,
                                 String dutyPeriod) throws SQLException {
        String sql = "INSERT INTO volunteer_shifts (volunteer_id, zone_id, duty_period) "
                   + "VALUES (?, ?, ?::tsrange) RETURNING shift_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, volunteerId);
            ps.setInt(2, zoneId);
            PGobject range = new PGobject();
            range.setType("tsrange");
            range.setValue(dutyPeriod);
            ps.setObject(3, range);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    VolunteerShift shift = new VolunteerShift();
                    shift.setShiftId(rs.getInt("shift_id"));
                    shift.setVolunteerId(volunteerId);
                    shift.setZoneId(zoneId);
                    shift.setDutyPeriod(dutyPeriod);
                    return shift;
                }
                throw new SQLException("INSERT into volunteer_shifts returned no row");
            }
        }
    }

    // ── Select All ────────────────────────────────────────────────────────────

    public List<VolunteerShift> findAll(Connection conn) throws SQLException {
        String sql = "SELECT vs.shift_id, vs.volunteer_id, vs.zone_id, "
                   + "       vs.duty_period::text, "
                   + "       v.name AS volunteer_name, z.name AS zone_name "
                   + "FROM volunteer_shifts vs "
                   + "JOIN volunteers v ON vs.volunteer_id = v.person_id "
                   + "JOIN zones z ON vs.zone_id = z.zone_id "
                   + "ORDER BY vs.duty_period DESC";
        List<VolunteerShift> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    // ── Select by ID ──────────────────────────────────────────────────────────

    public Optional<VolunteerShift> findById(Connection conn, int shiftId) throws SQLException {
        String sql = "SELECT vs.shift_id, vs.volunteer_id, vs.zone_id, "
                   + "       vs.duty_period::text, "
                   + "       v.name AS volunteer_name, z.name AS zone_name "
                   + "FROM volunteer_shifts vs "
                   + "JOIN volunteers v ON vs.volunteer_id = v.person_id "
                   + "JOIN zones z ON vs.zone_id = z.zone_id "
                   + "WHERE vs.shift_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    // ── Select by Volunteer ───────────────────────────────────────────────────

    public List<VolunteerShift> findByVolunteerId(Connection conn, int volunteerId) throws SQLException {
        String sql = "SELECT vs.shift_id, vs.volunteer_id, vs.zone_id, "
                   + "       vs.duty_period::text, "
                   + "       v.name AS volunteer_name, z.name AS zone_name "
                   + "FROM volunteer_shifts vs "
                   + "JOIN volunteers v ON vs.volunteer_id = v.person_id "
                   + "JOIN zones z ON vs.zone_id = z.zone_id "
                   + "WHERE vs.volunteer_id = ? "
                   + "ORDER BY vs.duty_period";
        List<VolunteerShift> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, volunteerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * UPDATE zone or duty_period of a shift.
     * The exclusion constraint will re-evaluate for the new duty_period.
     */
    public boolean update(Connection conn, int shiftId, Integer zoneId,
                          String dutyPeriod) throws SQLException {
        String sql = "UPDATE volunteer_shifts "
                   + "SET zone_id = COALESCE(?, zone_id), "
                   + "    duty_period = COALESCE(?::tsrange, duty_period) "
                   + "WHERE shift_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (zoneId == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, zoneId);
            if (dutyPeriod == null || dutyPeriod.isBlank()) {
                ps.setNull(2, Types.OTHER);
            } else {
                PGobject range = new PGobject();
                range.setType("tsrange");
                range.setValue(dutyPeriod);
                ps.setObject(2, range);
            }
            ps.setInt(3, shiftId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public boolean delete(Connection conn, int shiftId) throws SQLException {
        String sql = "DELETE FROM volunteer_shifts WHERE shift_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── ResultSet Mapping ─────────────────────────────────────────────────────

    private VolunteerShift mapRow(ResultSet rs) throws SQLException {
        VolunteerShift s = new VolunteerShift();
        s.setShiftId(rs.getInt("shift_id"));
        s.setVolunteerId(rs.getInt("volunteer_id"));
        s.setZoneId(rs.getInt("zone_id"));
        s.setDutyPeriod(rs.getString("duty_period"));
        s.setVolunteerName(rs.getString("volunteer_name"));
        s.setZoneName(rs.getString("zone_name"));
        return s;
    }
}
