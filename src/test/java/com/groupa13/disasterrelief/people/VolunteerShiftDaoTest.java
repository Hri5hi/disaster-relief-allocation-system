package com.groupa13.disasterrelief.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.groupa13.disasterrelief.dao.people.VolunteerShiftDao;
import com.groupa13.disasterrelief.db.FlywayMigrator;
import com.groupa13.disasterrelief.model.VolunteerShift;
import com.groupa13.disasterrelief.support.PostgresTestSupport;

/**
 * Integration tests for VolunteerShiftDao against a real PostgreSQL 15 Testcontainer.
 * Primary focus: EXCLUDE USING gist (volunteer_id WITH =, duty_period WITH &&).
 * Overlaps for the same volunteer must fail with SQLState 23P01; adjacent
 * (half-open) ranges and different volunteers must succeed.
 */
@Testcontainers
class VolunteerShiftDaoTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = PostgresTestSupport.newContainer();

    private static DataSource dataSource;

    private final VolunteerShiftDao dao = new VolunteerShiftDao();

    @BeforeAll
    static void migrateFreshDatabase() {
        dataSource = PostgresTestSupport.dataSource(POSTGRES);
        FlywayMigrator.migrate(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource = null;
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

    // ── Test 15: successful shift INSERT + SELECT round-trip ──────────────────

    @Test
    void testInsert_successfulShift() throws SQLException {
        int volunteerId = insertVolunteer("Alice");
        int zoneId = insertZone("Zone North");

        try (Connection conn = dataSource.getConnection()) {
            VolunteerShift shift = dao.insert(conn, volunteerId, zoneId,
                    "[2026-07-09 08:00, 2026-07-09 20:00)");
            assertTrue(shift.getShiftId() > 0);

            VolunteerShift found = dao.findById(conn, shift.getShiftId()).orElseThrow();
            assertEquals(volunteerId, found.getVolunteerId());
            assertEquals(zoneId, found.getZoneId());
            assertTrue(found.getDutyPeriod().contains("2026-07-09"));
            assertEquals("Alice", found.getVolunteerName());
            assertEquals("Zone North", found.getZoneName());
        }
    }

    // ── Test 16: overlap rejected by exclusion constraint (23P01) ─────────────

    @Test
    void testInsert_overlappingShift_failsWithExclusionConstraint() throws SQLException {
        int volunteerId = insertVolunteer("Alice");
        int zoneId = insertZone("Zone North");

        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, volunteerId, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)");

            SQLException exception = assertThrows(SQLException.class,
                    () -> dao.insert(conn, volunteerId, zoneId,
                            "[2026-07-09 12:00, 2026-07-10 08:00)"));
            assertEquals("23P01", exception.getSQLState());
        }
    }

    // ── Test 17: adjacent half-open ranges do not overlap ─────────────────────

    @Test
    void testInsert_adjacentShift_succeeds() throws SQLException {
        int volunteerId = insertVolunteer("Alice");
        int zoneId = insertZone("Zone North");

        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, volunteerId, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)");
            // Starts exactly when the first ends: [first.end, ...) is not an overlap
            VolunteerShift second = dao.insert(conn, volunteerId, zoneId,
                    "[2026-07-09 20:00, 2026-07-10 08:00)");
            assertTrue(second.getShiftId() > 0);
        }
    }

    // ── Test 18: exclusion is per-volunteer ───────────────────────────────────

    @Test
    void testInsert_differentVolunteer_overlappingTime_succeeds() throws SQLException {
        int volunteerOne = insertVolunteer("Alice");
        int volunteerTwo = insertVolunteer("Bob");
        int zoneId = insertZone("Zone North");

        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, volunteerOne, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)");
            VolunteerShift second = dao.insert(conn, volunteerTwo, zoneId,
                    "[2026-07-09 08:00, 2026-07-09 20:00)");
            assertTrue(second.getShiftId() > 0);
        }
    }

    // ── Test 19: volunteer-scoped lookup ──────────────────────────────────────

    @Test
    void testFindByVolunteerId() throws SQLException {
        int volunteerOne = insertVolunteer("Alice");
        int volunteerTwo = insertVolunteer("Bob");
        int zoneId = insertZone("Zone North");

        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, volunteerOne, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)");
            dao.insert(conn, volunteerOne, zoneId, "[2026-07-10 08:00, 2026-07-10 20:00)");
            dao.insert(conn, volunteerTwo, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)");

            List<VolunteerShift> firstShifts = dao.findByVolunteerId(conn, volunteerOne);
            List<VolunteerShift> secondShifts = dao.findByVolunteerId(conn, volunteerTwo);
            assertEquals(2, firstShifts.size());
            assertEquals(1, secondShifts.size());
        }
    }

    // ── Test 20: deletion does not cascade to the volunteer ───────────────────

    @Test
    void testDelete() throws SQLException {
        int volunteerId = insertVolunteer("Alice");
        int zoneId = insertZone("Zone North");

        int shiftId;
        try (Connection conn = dataSource.getConnection()) {
            shiftId = dao.insert(conn, volunteerId, zoneId,
                    "[2026-07-09 08:00, 2026-07-09 20:00)").getShiftId();
        }

        try (Connection conn = dataSource.getConnection()) {
            assertTrue(dao.delete(conn, shiftId));
            assertTrue(dao.findById(conn, shiftId).isEmpty());
        }

        // Volunteer must still exist (shift deletion does not delete the volunteer)
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM volunteers WHERE person_id = ?")) {
            statement.setInt(1, volunteerId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    // ── V004 ON DELETE CASCADE: deleting a volunteer removes their shifts ─────

    @Test
    void testDeleteVolunteer_cascadesToShifts() throws SQLException {
        int volunteerId = insertVolunteer("Alice");
        int zoneId = insertZone("Zone North");

        int shiftId;
        try (Connection conn = dataSource.getConnection()) {
            shiftId = dao.insert(conn, volunteerId, zoneId,
                    "[2026-07-09 08:00, 2026-07-09 20:00)").getShiftId();
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM volunteers WHERE person_id = ?")) {
            statement.setInt(1, volunteerId);
            assertEquals(1, statement.executeUpdate());
        }

        try (Connection conn = dataSource.getConnection()) {
            assertTrue(dao.findById(conn, shiftId).isEmpty());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int insertVolunteer(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO volunteers (name, contact, skills)
                        VALUES (?, '000', ARRAY['first_aid'])
                        RETURNING person_id
                        """)) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
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
