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

import com.groupa13.disasterrelief.dao.people.AffectedPersonDao;
import com.groupa13.disasterrelief.db.FlywayMigrator;
import com.groupa13.disasterrelief.model.AffectedPerson;
import com.groupa13.disasterrelief.support.PostgresTestSupport;

/**
 * Integration tests for AffectedPersonDao against a real PostgreSQL 15 Testcontainer.
 * Primary focus: trg_auto_update_shelter_occupancy must recalculate
 * shelters.current_occupancy on INSERT, DELETE, and UPDATE OF shelter_id
 * without any Java code ever writing current_occupancy.
 */
@Testcontainers
class AffectedPersonDaoTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = PostgresTestSupport.newContainer();

    private static DataSource dataSource;

    private final AffectedPersonDao dao = new AffectedPersonDao();

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

    // ── Test 9: INSERT fires the occupancy trigger ────────────────────────────

    @Test
    void testInsert_updatesOccupancy() throws SQLException {
        int shelterId = insertShelter("Shelter A");
        assertEquals(0, currentOccupancy(shelterId));

        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, "Alice", null, null, null, shelterId);
        }

        assertEquals(1, currentOccupancy(shelterId));
    }

    // ── Test 10: DELETE fires the occupancy trigger ───────────────────────────

    @Test
    void testDelete_updatesOccupancy() throws SQLException {
        int shelterId = insertShelter("Shelter A");

        int firstId;
        int secondId;
        try (Connection conn = dataSource.getConnection()) {
            firstId = dao.insert(conn, "Alice", null, null, null, shelterId).getPersonId();
            secondId = dao.insert(conn, "Bob", null, null, null, shelterId).getPersonId();
        }
        assertEquals(2, currentOccupancy(shelterId));

        try (Connection conn = dataSource.getConnection()) {
            assertTrue(dao.delete(conn, firstId));
        }
        assertEquals(1, currentOccupancy(shelterId));

        try (Connection conn = dataSource.getConnection()) {
            assertTrue(dao.delete(conn, secondId));
        }
        assertEquals(0, currentOccupancy(shelterId));
    }

    // ── Test 11: UPDATE OF shelter_id recalculates BOTH shelters ──────────────

    @Test
    void testUpdate_shelterReassignment_updatesOccupancy() throws SQLException {
        int shelterA = insertShelter("Shelter A");
        int shelterB = insertShelter("Shelter B");

        int personId;
        try (Connection conn = dataSource.getConnection()) {
            personId = dao.insert(conn, "Alice", null, null, null, shelterA).getPersonId();
        }
        assertEquals(1, currentOccupancy(shelterA));
        assertEquals(0, currentOccupancy(shelterB));

        try (Connection conn = dataSource.getConnection()) {
            assertTrue(dao.update(conn, personId, null, null, null, null, shelterB));
        }

        assertEquals(0, currentOccupancy(shelterA));
        assertEquals(1, currentOccupancy(shelterB));
    }

    // ── Test 12: invalid JSONB rejected at the database level ─────────────────

    @Test
    void testInsert_invalidJsonb() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            SQLException exception = assertThrows(SQLException.class,
                    () -> dao.insert(conn, "BadJSON", null, null, "not-json", null));
            assertEquals("22P02", exception.getSQLState());
        }
    }

    // ── Test 13: shelter-scoped lookup ────────────────────────────────────────

    @Test
    void testFindByShelterId() throws SQLException {
        int shelterA = insertShelter("Shelter A");
        int shelterB = insertShelter("Shelter B");

        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, "Alice", null, null, null, shelterA);
            dao.insert(conn, "Bob", null, null, null, shelterA);
            dao.insert(conn, "Carol", null, null, null, shelterB);
        }

        try (Connection conn = dataSource.getConnection()) {
            assertEquals(2, dao.findByShelterId(conn, shelterA).size());
            assertEquals(1, dao.findByShelterId(conn, shelterB).size());
        }
    }

    // ── Test 14: zone lookup via zones.primary_shelter_id ─────────────────────

    @Test
    void testFindByZone() throws SQLException {
        int shelterA = insertShelter("Shelter A");
        int shelterB = insertShelter("Shelter B");
        int zoneId = insertZone(shelterA, "Zone North");

        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, "Alice", null, null, null, shelterA);
            dao.insert(conn, "Bob", null, null, null, shelterA);
            dao.insert(conn, "Carol", null, null, null, shelterB);
        }

        try (Connection conn = dataSource.getConnection()) {
            List<AffectedPerson> inZone = dao.findByZone(conn, zoneId);
            assertEquals(2, inZone.size());
            assertTrue(inZone.stream().allMatch(p -> Integer.valueOf(shelterA).equals(p.getShelterId())));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int insertShelter(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO shelters (name, address, capacity)
                        VALUES (?, 'Test Street', 10)
                        RETURNING shelter_id
                        """)) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static int insertZone(int primaryShelterId, String name) throws SQLException {
        int zoneId;
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
                    INSERT INTO zones (disaster_id, name, severity, primary_shelter_id)
                    VALUES (?, ?, 'high', ?)
                    RETURNING zone_id
                    """)) {
                statement.setInt(1, disasterId);
                statement.setString(2, name);
                statement.setInt(3, primaryShelterId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    zoneId = rows.getInt(1);
                }
            }
        }
        return zoneId;
    }

    private static int currentOccupancy(int shelterId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT current_occupancy FROM shelters WHERE shelter_id = ?")) {
            statement.setInt(1, shelterId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }
}
