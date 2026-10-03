package com.groupa13.disasterrelief.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.FlywayMigrator;
import com.groupa13.disasterrelief.model.VolunteerShift;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.people.VolunteerService;
import com.groupa13.disasterrelief.service.people.VolunteerShiftService;
import com.groupa13.disasterrelief.support.PostgresTestSupport;

/**
 * Service-level tests for VolunteerShiftService: SQLState 23P01 (GiST
 * exclusion constraint) maps to the friendly overlap message (test 23 / AC-3),
 * adjacent shifts succeed, malformed tsrange fails safely, and shift
 * update/delete behave correctly with constraint re-evaluation.
 */
@Testcontainers
class VolunteerShiftServiceTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = PostgresTestSupport.newContainer();

    private static DataSource dataSource;

    private final VolunteerShiftService service = new VolunteerShiftService();
    private final VolunteerService volunteerService = new VolunteerService();

    @BeforeAll
    static void migrateAndInitializePool() {
        dataSource = PostgresTestSupport.dataSource(POSTGRES);
        FlywayMigrator.migrate(dataSource);
        Database.initialize(PostgresTestSupport.databaseConfig(POSTGRES));
    }

    @AfterAll
    static void closeApplicationPool() {
        Database.shutdown();
    }

    @BeforeEach
    void resetTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE
                        people, volunteers, affected_persons, volunteer_shifts,
                        shelter_inventory, resources, resource_categories,
                        zones, shelters, disasters
                    RESTART IDENTITY CASCADE
                    """);
        }
    }

    // ── Test 23 / AC-3: overlap → friendly message; adjacent → success ────────

    @Test
    void testCreateShift_overlapping_returnsFriendlyMessage_adjacentSucceeds()
            throws SQLException {
        int volunteerId = registerVolunteer("Alice");
        int zoneId = insertZone("Zone North");

        // Step 1: first shift succeeds
        ServiceResult<VolunteerShift> first = service.createShift(
                volunteerId, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)");
        assertTrue(first.success());
        assertTrue(first.value().getShiftId() > 0);

        // Step 2: overlapping shift for the SAME volunteer fails with the exact
        // friendly message mapped from SQLState 23P01 (no client-side duplicate check)
        ServiceResult<VolunteerShift> overlapping = service.createShift(
                volunteerId, zoneId, "[2026-07-09 12:00, 2026-07-10 08:00)");
        assertFalse(overlapping.success());
        assertEquals("Volunteer already has an overlapping shift for this time period",
                overlapping.message());

        // Step 3: adjacent (half-open) shift succeeds
        ServiceResult<VolunteerShift> adjacent = service.createShift(
                volunteerId, zoneId, "[2026-07-09 20:00, 2026-07-10 08:00)");
        assertTrue(adjacent.success());
    }

    // ── AC-3: a different volunteer may book the same window ──────────────────

    @Test
    void testCreateShift_differentVolunteer_sameWindow_succeeds() throws SQLException {
        int volunteerOne = registerVolunteer("Alice");
        int volunteerTwo = registerVolunteer("Bob");
        int zoneId = insertZone("Zone North");

        assertTrue(service.createShift(
                volunteerOne, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)").success());
        assertTrue(service.createShift(
                volunteerTwo, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)").success());
    }

    // ── AC-2: malformed timestamp range fails safely at the service layer ─────

    @Test
    void testCreateShift_malformedTsrange_returnsFailure() throws SQLException {
        int volunteerId = registerVolunteer("Alice");
        int zoneId = insertZone("Zone North");

        ServiceResult<VolunteerShift> malformed =
                service.createShift(volunteerId, zoneId, "bad-range");
        assertFalse(malformed.success());
        assertTrue(malformed.message().contains("tsrange"));

        ServiceResult<VolunteerShift> blank = service.createShift(volunteerId, zoneId, "  ");
        assertFalse(blank.success());
        assertTrue(blank.message().contains("Duty period is required"));
    }

    // ── SQLState 23503 → friendly FK message ─────────────────────────────────

    @Test
    void testCreateShift_unknownVolunteer_returnsFriendlyForeignKeyMessage()
            throws SQLException {
        int zoneId = insertZone("Zone North");

        ServiceResult<VolunteerShift> result =
                service.createShift(999999, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)");

        assertFalse(result.success());
        assertEquals("Volunteer or zone does not exist", result.message());
    }

    // ── getAll / findByVolunteer / update (constraint re-check) / delete ──────

    @Test
    void testShiftLifecycle_getUpdateDelete() throws SQLException {
        int volunteerId = registerVolunteer("Alice");
        int zoneId = insertZone("Zone North");
        int otherZoneId = insertZone("Zone South");

        ServiceResult<VolunteerShift> shiftOne = service.createShift(
                volunteerId, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00]");
        ServiceResult<VolunteerShift> shiftTwo = service.createShift(
                volunteerId, zoneId, "[2026-07-10 08:00, 2026-07-10 20:00]");
        assertTrue(shiftOne.success());
        assertTrue(shiftTwo.success());
        int shiftOneId = shiftOne.value().getShiftId();
        int shiftTwoId = shiftTwo.value().getShiftId();

        ServiceResult<List<VolunteerShift>> byVolunteer =
                service.getShiftsByVolunteerId(volunteerId);
        assertTrue(byVolunteer.success());
        assertEquals(2, byVolunteer.value().size());

        // Update: move shift to another zone — succeeds
        ServiceResult<VolunteerShift> zoneChange =
                service.updateShift(shiftOneId, otherZoneId, null);
        assertTrue(zoneChange.success());
        assertEquals(otherZoneId, zoneChange.value().getZoneId());

        // Update: reassign duty period to overlap the other shift → 23P01 mapping
        ServiceResult<VolunteerShift> conflict = service.updateShift(
                shiftOneId, null, "[2026-07-10 09:00, 2026-07-10 12:00)");
        assertFalse(conflict.success());
        assertEquals("Volunteer already has an overlapping shift for this time period",
                conflict.message());

        // Find
        ServiceResult<VolunteerShift> found = service.findById(shiftTwoId);
        assertTrue(found.success());

        // Delete
        ServiceResult<Void> deleted = service.deleteShift(shiftTwoId);
        assertTrue(deleted.success());
        assertFalse(service.findById(shiftTwoId).success());

        ServiceResult<Void> deleteAgain = service.deleteShift(shiftTwoId);
        assertFalse(deleteAgain.success());
        assertTrue(deleteAgain.message().contains("not found"));
    }

    // ── Combo loaders return "id — name" strings ─────────────────────────────

    @Test
    void testComboLoaders() throws SQLException {
        registerVolunteer("Alice");
        insertZone("Zone North");

        List<String> volunteers = service.getAllVolunteersForCombo();
        assertEquals(1, volunteers.size());
        assertTrue(volunteers.get(0).contains("—"));
        assertTrue(volunteers.get(0).contains("Alice"));

        List<String> zones = service.getAllZonesForCombo();
        assertEquals(1, zones.size());
        assertTrue(zones.get(0).contains("Zone North"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int registerVolunteer(String name) {
        ServiceResult<com.groupa13.disasterrelief.model.Volunteer> result =
                volunteerService.register(name, null, null, List.of("first_aid"), null, null);
        assertTrue(result.success());
        return result.value().getPersonId();
    }

    private static int insertZone(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            int disasterId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO disasters (name, type, severity)
                    VALUES ('Test Disaster', 'flood', 'high')
                    RETURNING disaster_id
                    """)) {
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    disasterId = rows.getInt(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO zones (disaster_id, name, severity)
                    VALUES (?, ?, 'high')
                    RETURNING zone_id
                    """)) {
                statement.setInt(1, disasterId);
                statement.setString(2, name);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    return rows.getInt(1);
                }
            }
        }
    }
}
