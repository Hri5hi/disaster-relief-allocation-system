package com.groupa13.disasterrelief.service.people;

import com.groupa13.disasterrelief.dao.people.AffectedPersonDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.model.AffectedPerson;
import com.groupa13.disasterrelief.service.core.ServiceResult;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for affected_persons domain operations.
 * Occupancy is NEVER updated by this service — the DB trigger handles it.
 */
public class AffectedPersonService {

    private final AffectedPersonDao dao = new AffectedPersonDao();

    /**
     * Register a new displaced person.
     * zoneId is used only for RLS set_config; pass null for admin.
     */
    public ServiceResult<AffectedPerson> register(String name, String contact, String email,
                                                   String medicalNeeds, Integer shelterId,
                                                   Integer zoneId) {
        if (name == null || name.isBlank())
            return ServiceResult.failure("Name is required");
        if (medicalNeeds != null && !medicalNeeds.isBlank()) {
            String trimmed = medicalNeeds.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
                return ServiceResult.failure("Medical needs must be valid JSON, e.g. {\"conditions\": [\"diabetes\"]}");
        }
        try {
            AffectedPerson created = Database.inTransaction(conn -> {
                if (zoneId != null) setZoneContext(conn, zoneId);
                return dao.insert(conn, name.trim(), blankToNull(contact), blankToNull(email),
                                  blankToNull(medicalNeeds), shelterId);
            });
            return ServiceResult.success(created, "Person registered successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Load all affected persons. zoneId = null means admin (all zones). */
    public ServiceResult<List<AffectedPerson>> getAllAffectedPersons(Integer zoneId) {
        try {
            List<AffectedPerson> list = Database.inTransaction(conn -> {
                if (zoneId != null) {
                    setZoneContext(conn, zoneId);
                    return dao.findByZone(conn, zoneId);
                }
                return dao.findAll(conn);
            });
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Find by person_id. */
    public ServiceResult<AffectedPerson> findById(int personId) {
        try {
            Optional<AffectedPerson> ap = Database.inTransaction(conn -> dao.findById(conn, personId));
            if (ap.isPresent()) return ServiceResult.success(ap.get(), "");
            return ServiceResult.failure("Person with ID " + personId + " not found");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Find all persons at a specific shelter. */
    public ServiceResult<List<AffectedPerson>> findByShelterId(int shelterId) {
        try {
            List<AffectedPerson> list = Database.inTransaction(conn -> dao.findByShelterId(conn, shelterId));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Update person details. Trigger handles occupancy on shelter_id change. */
    public ServiceResult<AffectedPerson> update(int personId, String name, String contact,
                                                 String email, String medicalNeeds,
                                                 Integer shelterId, Integer zoneId) {
        if (medicalNeeds != null && !medicalNeeds.isBlank()) {
            String trimmed = medicalNeeds.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
                return ServiceResult.failure("Medical needs must be valid JSON");
        }
        try {
            AffectedPerson updated = Database.inTransaction(conn -> {
                if (zoneId != null) setZoneContext(conn, zoneId);
                boolean changed = dao.update(conn, personId, blankToNull(name), blankToNull(contact),
                                             blankToNull(email), blankToNull(medicalNeeds), shelterId);
                if (!changed) throw new RuntimeException("Person with ID " + personId + " not found");
                return dao.findById(conn, personId).orElseThrow();
            });
            return ServiceResult.success(updated, "Person updated successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Delete a person. Trigger updates shelter occupancy automatically. */
    public ServiceResult<Void> delete(int personId, Integer zoneId) {
        try {
            Database.inTransaction(conn -> {
                if (zoneId != null) setZoneContext(conn, zoneId);
                boolean deleted = dao.delete(conn, personId);
                if (!deleted) throw new RuntimeException("Person with ID " + personId + " not found");
                return null;
            });
            return ServiceResult.success(null, "Person removed successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Load all shelters for the ComboBox.
     * Returns "shelter_id — shelter_name" strings.
     * TODO: Replace with Dev A's ShelterService.getAllShelters() once available.
     */
    public List<String> getAllSheltersForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT shelter_id, name FROM shelters ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getInt("shelter_id") + " — " + rs.getString("name"));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── RLS context helper ────────────────────────────────────────────────────

    /**
     * Sets the transaction-local zone context for RLS enforcement.
     * The 'true' third argument makes the setting transaction-local —
     * it resets automatically when the transaction ends.
     * This prevents Hikari connection pool from leaking zone identity.
     */
    private void setZoneContext(java.sql.Connection conn, int zoneId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_zone_id', ?, true)")) {
            ps.setString(1, String.valueOf(zoneId));
            ps.execute();
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    private <T> ServiceResult<T> mapException(Exception e) {
        if (e instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state == null) return ServiceResult.failure("Database error: " + sql.getMessage());
            return switch (state) {
                case "23502" -> ServiceResult.failure("Required field is missing");
                case "23503" -> ServiceResult.failure("Shelter does not exist");
                case "22007" -> ServiceResult.failure("Invalid date/time format");
                default -> ServiceResult.failure("Database error: " + sql.getMessage());
            };
        }
        if (e.getCause() instanceof SQLException sql) return mapException(sql);
        return ServiceResult.failure(e.getMessage() != null ? e.getMessage() : "Unexpected error");
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
