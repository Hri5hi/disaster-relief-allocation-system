package com.groupa13.disasterrelief.service.people;

import com.groupa13.disasterrelief.dao.people.VolunteerDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.model.Volunteer;
import com.groupa13.disasterrelief.service.core.ServiceResult;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service for volunteer domain operations.
 * Transaction boundary: each public method is one transaction.
 * Error messages follow Wave 0 convention: short, user-friendly.
 */
public class VolunteerService {

    private final VolunteerDao dao = new VolunteerDao();

    /**
     * Register a new volunteer.
     * Validates: name required, availability tsrange format if provided.
     *
     * @param name         required
     * @param contact      nullable
     * @param email        nullable
     * @param skills       nullable; parsed from comma-separated input
     * @param availability nullable tsrange string e.g. "[2026-07-09 08:00, 2026-07-09 20:00)"
     * @param zoneId       nullable FK
     */
    public ServiceResult<Volunteer> register(String name, String contact, String email,
                                              List<String> skills, String availability,
                                              Integer zoneId) {
        // Business validation
        if (name == null || name.isBlank())
            return ServiceResult.failure("Name is required");
        if (availability != null && !availability.isBlank() && !isValidTsrange(availability))
            return ServiceResult.failure("Availability must be a valid tsrange, e.g. [2026-07-09 08:00, 2026-07-20 18:00)");

        try {
            Volunteer created = Database.inTransaction(conn -> {
                int id = dao.insert(conn, name.trim(), blankToNull(contact), blankToNull(email),
                                    skills, blankToNull(availability), zoneId);
                return dao.findById(conn, id).orElseThrow(() ->
                    new RuntimeException("Volunteer not found after insert"));
            });
            return ServiceResult.success(created, "Volunteer registered successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Load all volunteers for the table view. */
    public ServiceResult<List<Volunteer>> getAllVolunteers() {
        try {
            List<Volunteer> list = Database.inTransaction(conn -> dao.findAll(conn));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Find a single volunteer by ID. */
    public ServiceResult<Volunteer> findById(int personId) {
        try {
            Optional<Volunteer> v = Database.inTransaction(conn -> dao.findById(conn, personId));
            if (v.isPresent()) return ServiceResult.success(v.get(), "");
            return ServiceResult.failure("Volunteer with ID " + personId + " not found");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Search volunteers by one or more skills.
     * Input is a comma-separated string from the UI field, e.g. "first_aid, driving".
     */
    public ServiceResult<List<Volunteer>> findBySkills(String skillsInput) {
        if (skillsInput == null || skillsInput.isBlank())
            return ServiceResult.failure("Enter at least one skill to search");
        List<String> skills = Arrays.stream(skillsInput.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .toList();
        if (skills.isEmpty())
            return ServiceResult.failure("Enter at least one skill to search");
        try {
            List<Volunteer> results = Database.inTransaction(conn -> dao.findBySkills(conn, skills));
            if (results.isEmpty())
                return ServiceResult.failure("No volunteers found with the specified skills");
            return ServiceResult.success(results, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Update an existing volunteer. Null fields = keep existing value. */
    public ServiceResult<Volunteer> update(int personId, String name, String contact,
                                            String email, List<String> skills,
                                            String availability, Integer zoneId) {
        if (availability != null && !availability.isBlank() && !isValidTsrange(availability))
            return ServiceResult.failure("Availability must be a valid tsrange, e.g. [2026-07-09 08:00, 2026-07-20 18:00)");
        try {
            Volunteer updated = Database.inTransaction(conn -> {
                boolean changed = dao.update(conn, personId, blankToNull(name), blankToNull(contact),
                                             blankToNull(email), skills, blankToNull(availability), zoneId);
                if (!changed) throw new RuntimeException("Volunteer with ID " + personId + " not found");
                return dao.findById(conn, personId).orElseThrow();
            });
            return ServiceResult.success(updated, "Volunteer updated successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Delete a volunteer. Their shifts are cascade-deleted. */
    public ServiceResult<Void> delete(int personId) {
        try {
            Database.inTransaction(conn -> {
                boolean deleted = dao.delete(conn, personId);
                if (!deleted) throw new RuntimeException("Volunteer with ID " + personId + " not found");
                return null;
            });
            return ServiceResult.success(null, "Volunteer removed successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Load all zones for the ComboBox in VolunteerController.
     * Returns a List of String formatted as "zone_id — zone_name".
     * TODO: Replace with Dev A's ZoneService.getAllZones() once available.
     */
    public List<String> getAllZonesForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> zones = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT zone_id, name FROM zones ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        zones.add(rs.getInt("zone_id") + " — " + rs.getString("name"));
                    }
                }
                return zones;
            });
        } catch (Exception e) {
            return new ArrayList<>(); // Return empty list on failure; controller shows no options
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    private <T> ServiceResult<T> mapException(Exception e) {
        if (e instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state == null) return ServiceResult.failure("Database error: " + sql.getMessage());
            return switch (state) {
                case "23502" -> ServiceResult.failure("Required field is missing");
                case "23503" -> ServiceResult.failure("Referenced zone does not exist");
                case "23505" -> ServiceResult.failure("A duplicate volunteer record already exists");
                default -> ServiceResult.failure("Database error: " + sql.getMessage());
            };
        }
        if (e.getCause() instanceof SQLException sql) return mapException(sql);
        return ServiceResult.failure(e.getMessage() != null ? e.getMessage() : "Unexpected error");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Basic tsrange format validation.
     * Acceptable: [YYYY-MM-DD HH:mm, YYYY-MM-DD HH:mm) or similar bracketed forms.
     */
    private boolean isValidTsrange(String s) {
        return s.matches("[\\[(].+,.+[\\])]");
    }
}
