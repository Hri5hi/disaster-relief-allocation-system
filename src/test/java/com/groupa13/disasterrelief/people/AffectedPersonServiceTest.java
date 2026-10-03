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
import com.groupa13.disasterrelief.model.AffectedPerson;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.people.AffectedPersonService;
import com.groupa13.disasterrelief.support.PostgresTestSupport;

/**
 * Service-level tests for AffectedPersonService: JSONB validation before the
 * DB (AC-2), occupancy maintained by the PostgreSQL trigger end-to-end (AC-1),
 * and zone-scoped listing for ZONE_COORDINATOR sessions (D-12).
 * Java never writes shelters.current_occupancy.
 */
@Testcontainers
class AffectedPersonServiceTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = PostgresTestSupport.newContainer();

    private static DataSource dataSource;

    private final AffectedPersonService service = new AffectedPersonService();

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

    // ── Test 24 / AC-2: invalid JSON rejected by service validation ───────────

    @Test
    void testRegisterAffectedPerson_invalidJson_returnsFailure() {
        ServiceResult<AffectedPerson> notJson =
                service.register("Alice", null, null, "not-json", null, null);
        assertFalse(notJson.success());
        assertTrue(notJson.message().contains("valid JSON"));

        ServiceResult<AffectedPerson> invalid =
                service.register("Alice", null, null, "INVALID", null, null);
        assertFalse(invalid.success());
        assertTrue(invalid.message().contains("valid JSON"));
    }

    // ── AC-2: missing name fails safely ───────────────────────────────────────

    @Test
    void testRegisterAffectedPerson_missingName_returnsFailure() {
        ServiceResult<AffectedPerson> result = service.register("  ", null, null, null, null, null);
        assertFalse(result.success());
        assertTrue(result.message().contains("Name is required"));
    }

    // ── Test 25 / AC-1: register → occupancy → delete → occupancy, via service ─

    @Test
    void testDeleteAffectedPerson_updatesOccupancy() throws SQLException {
        int shelterId = insertShelter("Shelter A");
        assertEquals(0, currentOccupancy(shelterId));

        ServiceResult<AffectedPerson> registered = service.register(
                "Alice", "999", null, "{\"conditions\": [\"diabetes\"]}", shelterId, null);
        assertTrue(registered.success());
        assertEquals(1, currentOccupancy(shelterId));

        ServiceResult<AffectedPerson> found = service.findById(registered.value().getPersonId());
        assertTrue(found.success());
        assertEquals("{\"conditions\": [\"diabetes\"]}", found.value().getMedicalNeeds());

        ServiceResult<Void> deleted = service.delete(registered.value().getPersonId(), null);
        assertTrue(deleted.success());
        assertEquals(0, currentOccupancy(shelterId));

        ServiceResult<Void> deleteAgain = service.delete(registered.value().getPersonId(), null);
        assertFalse(deleteAgain.success());
        assertTrue(deleteAgain.message().contains("not found"));
    }

    // ── AC-1: shelter reassignment through the service fires the trigger ──────

    @Test
    void testUpdate_shelterReassignment_updatesOccupancy() throws SQLException {
        int shelterA = insertShelter("Shelter A");
        int shelterB = insertShelter("Shelter B");

        ServiceResult<AffectedPerson> registered =
                service.register("Alice", null, null, null, shelterA, null);
        assertTrue(registered.success());
        int personId = registered.value().getPersonId();

        ServiceResult<AffectedPerson> moved = service.update(
                personId, null, null, null, null, shelterB, null);
        assertTrue(moved.success());
        assertEquals(0, currentOccupancy(shelterA));
        assertEquals(1, currentOccupancy(shelterB));
    }

    // ── D-12: zoneId scopes the listing to the zone's primary shelter ─────────

    @Test
    void testGetAllAffectedPersons_zoneScoped() throws SQLException {
        int shelterA = insertShelter("Shelter A");
        int shelterB = insertShelter("Shelter B");
        int zoneId = insertZone(shelterA, "Zone North");

        assertTrue(service.register("Alice", null, null, null, shelterA, null).success());
        assertTrue(service.register("Bob", null, null, null, shelterA, null).success());
        assertTrue(service.register("Carol", null, null, null, shelterB, null).success());

        // Admin (null zone) sees everyone
        ServiceResult<List<AffectedPerson>> adminView = service.getAllAffectedPersons(null);
        assertTrue(adminView.success());
        assertEquals(3, adminView.value().size());

        // Zone coordinator sees only persons at the zone's primary shelter
        ServiceResult<List<AffectedPerson>> zoneView = service.getAllAffectedPersons(zoneId);
        assertTrue(zoneView.success());
        assertEquals(2, zoneView.value().size());
        assertTrue(zoneView.value().stream()
                .allMatch(p -> Integer.valueOf(shelterA).equals(p.getShelterId())));
    }

    // ── findByShelterId through the service ───────────────────────────────────

    @Test
    void testFindByShelterId_returnsOnlyThatSheltersPersons() throws SQLException {
        int shelterA = insertShelter("Shelter A");
        int shelterB = insertShelter("Shelter B");

        assertTrue(service.register("Alice", null, null, null, shelterA, null).success());
        assertTrue(service.register("Bob", null, null, null, shelterA, null).success());
        assertTrue(service.register("Carol", null, null, null, shelterB, null).success());

        ServiceResult<List<AffectedPerson>> atA = service.findByShelterId(shelterA);
        assertTrue(atA.success());
        assertEquals(2, atA.value().size());
    }

    // ── SQLState 23503 → friendly FK message ─────────────────────────────────

    @Test
    void testRegister_unknownShelter_returnsFriendlyForeignKeyMessage() {
        ServiceResult<AffectedPerson> result =
                service.register("Dave", null, null, null, 999999, null);

        assertFalse(result.success());
        assertEquals("Shelter does not exist", result.message());
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
                    return rows.getInt(1);
                }
            }
        }
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
