package com.groupa13.disasterrelief.service.people;

import com.groupa13.disasterrelief.dao.people.VolunteerShiftDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.model.VolunteerShift;
import com.groupa13.disasterrelief.service.core.ServiceResult;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for volunteer_shifts operations.
 * The GiST exclusion constraint (SQLState 23P01) is mapped to a user-friendly message.
 */
public class VolunteerShiftService {

    private final VolunteerShiftDao dao = new VolunteerShiftDao();

    /** Schedule a new shift. Returns failure if exclusion constraint fires. */
    public ServiceResult<VolunteerShift> createShift(int volunteerId, int zoneId, String dutyPeriod) {
        if (dutyPeriod == null || dutyPeriod.isBlank())
            return ServiceResult.failure("Duty period is required");
        if (!isValidTsrange(dutyPeriod))
            return ServiceResult.failure("Duty period must be a valid tsrange, e.g. [2026-07-09 08:00, 2026-07-09 20:00)");
        try {
            VolunteerShift shift = Database.inTransaction(conn -> dao.insert(conn, volunteerId, zoneId, dutyPeriod));
            return ServiceResult.success(shift, "Shift scheduled successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<List<VolunteerShift>> getAllShifts() {
        try {
            List<VolunteerShift> list = Database.inTransaction(conn -> dao.findAll(conn));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<VolunteerShift> findById(int shiftId) {
        try {
            Optional<VolunteerShift> s = Database.inTransaction(conn -> dao.findById(conn, shiftId));
            if (s.isPresent()) return ServiceResult.success(s.get(), "");
            return ServiceResult.failure("Shift with ID " + shiftId + " not found");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<List<VolunteerShift>> getShiftsByVolunteerId(int volunteerId) {
        try {
            List<VolunteerShift> list = Database.inTransaction(conn -> dao.findByVolunteerId(conn, volunteerId));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<VolunteerShift> updateShift(int shiftId, Integer zoneId, String dutyPeriod) {
        if (dutyPeriod != null && !dutyPeriod.isBlank() && !isValidTsrange(dutyPeriod))
            return ServiceResult.failure("Duty period must be a valid tsrange");
        try {
            VolunteerShift updated = Database.inTransaction(conn -> {
                boolean changed = dao.update(conn, shiftId, zoneId, dutyPeriod);
                if (!changed) throw new RuntimeException("Shift with ID " + shiftId + " not found");
                return dao.findById(conn, shiftId).orElseThrow();
            });
            return ServiceResult.success(updated, "Shift updated successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<Void> deleteShift(int shiftId) {
        try {
            Database.inTransaction(conn -> {
                boolean deleted = dao.delete(conn, shiftId);
                if (!deleted) throw new RuntimeException("Shift with ID " + shiftId + " not found");
                return null;
            });
            return ServiceResult.success(null, "Shift cancelled successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Load all volunteers for the VolunteerShiftController ComboBox.
     * TODO: Replace with VolunteerService.getAllVolunteers() combo helper.
     */
    public List<String> getAllVolunteersForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT person_id, name FROM volunteers ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getInt("person_id") + " — " + rs.getString("name"));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Load zones for the zone combo. */
    public List<String> getAllZonesForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT zone_id, name FROM zones ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getInt("zone_id") + " — " + rs.getString("name"));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    private <T> ServiceResult<T> mapException(Exception e) {
        if (e instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state == null) return ServiceResult.failure("Database error: " + sql.getMessage());
            return switch (state) {
                case "23P01" -> ServiceResult.failure("Volunteer already has an overlapping shift for this time period");
                case "23502" -> ServiceResult.failure("Required field is missing");
                case "23503" -> ServiceResult.failure("Volunteer or zone does not exist");
                case "22007" -> ServiceResult.failure("Invalid date/time format. Use: yyyy-MM-dd HH:mm");
                default -> ServiceResult.failure("Database error: " + sql.getMessage());
            };
        }
        if (e.getCause() instanceof SQLException sql) return mapException(sql);
        return ServiceResult.failure(e.getMessage() != null ? e.getMessage() : "Unexpected error");
    }

    private boolean isValidTsrange(String s) {
        return s.matches("[\\[(].+,.+[\\])]");
    }
}
