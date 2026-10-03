package com.groupa13.disasterrelief.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.groupa13.disasterrelief.model.Volunteer;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.people.VolunteerService;
import com.groupa13.disasterrelief.support.PostgresTestSupport;

/**
 * Service-level tests for VolunteerService: business validation, transaction
 * boundaries, SQLState → friendly-message mapping, and skill search (AC-4).
 */
@Testcontainers
class VolunteerServiceTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = PostgresTestSupport.newContainer();

    private static DataSource dataSource;

    private final VolunteerService service = new VolunteerService();

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

    // ── Test 21: missing name fails before any DB call ────────────────────────

    @Test
    void testRegisterVolunteer_missingName_returnsFailure() throws SQLException {
        ServiceResult<Volunteer> result = service.register("", null, null, null, null, null);

        assertFalse(result.success());
        assertTrue(result.message().contains("Name is required"));
        assertEquals(0, countVolunteers());
    }

    // ── Test 22 / AC-2: malformed tsrange fails safely ────────────────────────

    @Test
    void testRegisterVolunteer_invalidTsrange_returnsFailure() {
        ServiceResult<Volunteer> result =
                service.register("Bob", null, null, null, "not-a-range", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("tsrange"));

        ServiceResult<Volunteer> ac2 = service.register("Bob", null, null, null, "BADRANGE", null);
        assertFalse(ac2.success());
        assertTrue(ac2.message().contains("tsrange"));
    }

    // ── AC-4: skill-array search returns only the correct rows ────────────────

    @Test
    void testFindBySkills_returnsOnlyMatchingVolunteers() {
        ServiceResult<Volunteer> alice = service.register("Alice", null, null,
                List.of("first_aid", "driving"), null, null);
        ServiceResult<Volunteer> bob = service.register("Bob", null, null,
                List.of("cooking"), null, null);
        assertTrue(alice.success());
        assertTrue(bob.success());

        ServiceResult<List<Volunteer>> firstAid = service.findBySkills("first_aid");
        assertTrue(firstAid.success());
        assertEquals(1, firstAid.value().size());
        assertEquals("Alice", firstAid.value().get(0).getName());

        ServiceResult<List<Volunteer>> cooking = service.findBySkills("cooking");
        assertTrue(cooking.success());
        assertEquals(1, cooking.value().size());
        assertEquals("Bob", cooking.value().get(0).getName());

        // Multi-skill comma-separated input: volunteer must have ALL skills
        ServiceResult<List<Volunteer>> both = service.findBySkills("first_aid,driving");
        assertTrue(both.success());
        assertEquals(1, both.value().size());
        assertEquals("Alice", both.value().get(0).getName());

        // No match → friendly failure message
        ServiceResult<List<Volunteer>> none = service.findBySkills("translation");
        assertFalse(none.success());
        assertTrue(none.message().contains("No volunteers found"));

        // Blank input rejected
        ServiceResult<List<Volunteer>> blank = service.findBySkills("   ");
        assertFalse(blank.success());
        assertTrue(blank.message().contains("at least one skill"));
    }

    // ── AC-4 step 5: assigned_zone persisted and mapped via findAll() ─────────
    //     (exercises VolunteerDao.findAll's LEFT JOIN zones → zoneName mapping)

    @Test
    void testGetAllVolunteers_zoneAssignment_reflectedInFindAll() throws SQLException {
        int zoneId = insertZone("Zone North");

        // Volunteer A: assigned to a zone; Volunteer B: unassigned (null zone)
        ServiceResult<Volunteer> assigned = service.register(
                "Alice", null, null, List.of("first_aid"), null, zoneId);
        ServiceResult<Volunteer> unassigned = service.register(
                "Bob", null, null, List.of("cooking"), null, null);
        assertTrue(assigned.success());
        assertTrue(unassigned.success());

        ServiceResult<List<Volunteer>> all = service.getAllVolunteers();
        assertTrue(all.success());
        assertEquals(2, all.value().size());

        Volunteer withZone = all.value().stream()
                .filter(v -> v.getPersonId() == assigned.value().getPersonId())
                .findFirst().orElseThrow();
        Volunteer withoutZone = all.value().stream()
                .filter(v -> v.getPersonId() == unassigned.value().getPersonId())
                .findFirst().orElseThrow();

        // Volunteer A: zone persisted, zoneName joined from zones table
        assertEquals(Integer.valueOf(zoneId), withZone.getAssignedZone());
        assertEquals("Zone North", withZone.getZoneName());

        // Volunteer B: zone stays null, no zone name from LEFT JOIN
        assertNull(withoutZone.getAssignedZone());
        assertNull(withoutZone.getZoneName());
    }

    // ── Register → getAll → update → delete round trip ────────────────────────

    @Test
    void testRegister_getAll_update_delete_roundTrip() {
        ServiceResult<Volunteer> created = service.register("Dana", "123", "dana@example.org",
                List.of("cooking"), "[2026-07-09 08:00, 2026-07-09 20:00)", null);
        assertTrue(created.success());
        int personId = created.value().getPersonId();

        ServiceResult<List<Volunteer>> all = service.getAllVolunteers();
        assertTrue(all.success());
        assertEquals(1, all.value().size());

        ServiceResult<Volunteer> updated = service.update(personId, "Dana Updated", null, null,
                null, "[2026-08-01 09:00, 2026-08-01 17:00)", null);
        assertTrue(updated.success());
        assertEquals("Dana Updated", updated.value().getName());
        assertTrue(updated.value().getAvailability().contains("2026-08-01"));
        assertEquals(List.of("cooking"), updated.value().getSkills()); // preserved

        ServiceResult<Void> deleted = service.delete(personId);
        assertTrue(deleted.success());
        assertTrue(service.findById(personId).success() == false);

        ServiceResult<Void> deleteAgain = service.delete(personId);
        assertFalse(deleteAgain.success());
    }

    // ── SQLState 23503 → friendly FK message ─────────────────────────────────

    @Test
    void testRegister_unknownZone_returnsFriendlyForeignKeyMessage() {
        ServiceResult<Volunteer> result = service.register("Eve", null, null, null, null, 999999);

        assertFalse(result.success());
        assertEquals("Referenced zone does not exist", result.message());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private static int countVolunteers() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM volunteers")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
