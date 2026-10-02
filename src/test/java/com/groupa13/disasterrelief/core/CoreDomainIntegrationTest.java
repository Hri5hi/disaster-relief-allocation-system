package com.groupa13.disasterrelief.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.groupa13.disasterrelief.db.FlywayMigrator;
import com.groupa13.disasterrelief.model.core.Disaster;
import com.groupa13.disasterrelief.model.core.DisasterStatus;
import com.groupa13.disasterrelief.model.core.ResourceCategory;
import com.groupa13.disasterrelief.model.core.Severity;
import com.groupa13.disasterrelief.model.core.Shelter;
import com.groupa13.disasterrelief.model.core.ShelterInventory;
import com.groupa13.disasterrelief.model.core.ShelterStatus;
import com.groupa13.disasterrelief.model.core.Zone;
import com.groupa13.disasterrelief.service.core.DisasterService;
import com.groupa13.disasterrelief.service.core.InventoryService;
import com.groupa13.disasterrelief.service.core.ResourceCategoryService;
import com.groupa13.disasterrelief.service.core.ResourceService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.core.ShelterService;
import com.groupa13.disasterrelief.service.core.TransactionRunner;
import com.groupa13.disasterrelief.service.core.ZoneService;
import com.groupa13.disasterrelief.support.PostgresTestSupport;

@Testcontainers
class CoreDomainIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = PostgresTestSupport.newContainer();

    private static DataSource dataSource;
    private static DisasterService disasters;
    private static ZoneService zones;
    private static ShelterService shelters;
    private static ResourceCategoryService categories;
    private static ResourceService resources;
    private static InventoryService inventory;

    @BeforeAll
    static void migrateAndCreateServices() {
        dataSource = PostgresTestSupport.dataSource(POSTGRES);
        FlywayMigrator.migrate(dataSource);
        TransactionRunner transactions = TransactionRunner.forDataSource(dataSource);
        disasters = new DisasterService(transactions);
        zones = new ZoneService(transactions);
        shelters = new ShelterService(transactions);
        categories = new ResourceCategoryService(transactions);
        resources = new ResourceService(transactions);
        inventory = new InventoryService(transactions);
    }

    @BeforeEach
    void clearData() throws SQLException {
        PostgresTestSupport.truncateCoreTables(dataSource);
    }

    @Test
    void disasterZoneAndShelterCrudUsesDatabaseValuesAndSafeConstraintMessages()
            throws SQLException {
        Disaster disaster = value(disasters.createDisaster(
                "Monsoon Flood", "Flood", Severity.CRITICAL,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20),
                DisasterStatus.ACTIVE));
        Shelter shelter = value(shelters.createShelter(
                "Central School", "Meppadi, Wayanad", 11.61, 76.08, 250,
                ShelterStatus.OPERATIONAL));
        Zone zone = value(zones.createZone(disaster.disasterId(), "North Sector",
                Severity.HIGH, shelter.shelterId(), "+91 9000000001"));

        assertEquals(disaster.disasterId(), zone.disasterId());
        assertEquals(shelter.shelterId(), zone.primaryShelterId());
        assertEquals("Central School", zone.primaryShelterName());
        assertEquals(List.of(zone), value(zones.listZonesForDisaster(disaster.disasterId())));
        assertEquals(1, value(disasters.listOpenDisasterOptions()).size());
        assertEquals(1, value(shelters.listShelterOptions()).size());

        Zone cleared = value(zones.assignPrimaryShelter(zone.zoneId(), null));
        assertEquals(null, cleared.primaryShelterId());
        Zone reassigned = value(zones.assignPrimaryShelter(zone.zoneId(), shelter.shelterId()));
        assertEquals(shelter.shelterId(), reassigned.primaryShelterId());
        ServiceResult<Zone> missingShelter = zones.assignPrimaryShelter(zone.zoneId(), 999_999);
        assertFalse(missingShelter.success());
        assertEquals("Selected primary shelter does not exist", missingShelter.message());

        ServiceResult<Zone> duplicate = zones.createZone(disaster.disasterId(),
                "North Sector", Severity.MEDIUM, null, null);
        assertFalse(duplicate.success());
        assertEquals("A zone with that name already exists for the disaster", duplicate.message());
        assertFalse(duplicate.message().contains("23505"));

        ServiceResult<Void> referencedDisaster = disasters.deleteDisaster(disaster.disasterId());
        assertFalse(referencedDisaster.success());
        assertEquals("Disaster cannot be deleted while zones reference it",
                referencedDisaster.message());

        executeUpdate("UPDATE shelters SET current_occupancy = 40 WHERE shelter_id = ?",
                shelter.shelterId());
        ServiceResult<Shelter> tooSmall = shelters.updateShelter(shelter.shelterId(),
                shelter.name(), shelter.address(), 11.61, 76.08, 20, shelter.status());
        assertFalse(tooSmall.success());
        assertEquals("Capacity cannot be below current occupancy", tooSmall.message());
        Shelter unchanged = value(shelters.findShelter(shelter.shelterId()));
        assertEquals(250, unchanged.capacity());
        assertEquals(40, unchanged.currentOccupancy());

        Zone updatedZone = value(zones.updateZone(zone.zoneId(), "Northern Sector",
                Severity.CRITICAL, shelter.shelterId(), null));
        assertEquals("Northern Sector", updatedZone.name());
        Disaster updatedDisaster = value(disasters.updateDisaster(disaster.disasterId(),
                "Monsoon Flood 2026", "Flood", Severity.HIGH,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 21),
                DisasterStatus.MONITORING));
        assertEquals(DisasterStatus.MONITORING, updatedDisaster.status());
        Shelter resized = value(shelters.updateShelter(shelter.shelterId(), shelter.name(),
                shelter.address(), 11.61, 76.08, 50, ShelterStatus.FULL));
        assertEquals(40, resized.currentOccupancy());

        assertTrue(zones.deleteZone(zone.zoneId()).success());
        assertTrue(disasters.deleteDisaster(disaster.disasterId()).success());
        ServiceResult<Void> occupiedDelete = shelters.deleteShelter(shelter.shelterId());
        assertFalse(occupiedDelete.success());
        assertEquals("Occupied shelter cannot be deleted", occupiedDelete.message());
        executeUpdate("UPDATE shelters SET current_occupancy = 0 WHERE shelter_id = ?",
                shelter.shelterId());
        assertTrue(shelters.deleteShelter(shelter.shelterId()).success());
    }

    @Test
    void categoryTreeIsParentFirstAndRejectsDescendantCyclesAndInUseDeletes() {
        ResourceCategory medical = value(categories.createCategory("Medical", null));
        ResourceCategory firstAid = value(categories.createCategory(
                "First Aid", medical.categoryId()));
        ResourceCategory bandages = value(categories.createCategory(
                "Bandages", firstAid.categoryId()));

        List<ResourceCategory> tree = value(categories.getCategoryTree());
        assertEquals(List.of("Medical", "Medical > First Aid",
                "Medical > First Aid > Bandages"),
                tree.stream().map(ResourceCategory::path).toList());
        assertEquals(List.of(0, 1, 2), tree.stream().map(ResourceCategory::depth).toList());
        assertEquals("Medical > First Aid", value(
                categories.findCategory(firstAid.categoryId())).path());

        ServiceResult<ResourceCategory> cycle = categories.updateCategory(
                medical.categoryId(), medical.name(), bandages.categoryId());
        assertFalse(cycle.success());
        assertTrue(cycle.message().contains("descendants"));
        assertEquals(3, value(categories.getCategoryTree()).size());

        ResourceCategory renamed = value(categories.updateCategory(
                bandages.categoryId(), "Dressings", firstAid.categoryId()));
        assertEquals("Medical > First Aid > Dressings", renamed.path());

        var resource = value(resources.createResource(
                "Sterile gauze", bandages.categoryId(), "boxes"));
        assertEquals("Medical > First Aid > Dressings", resource.categoryPath());
        assertEquals(1, value(resources.listResourceOptions()).size());
        var updatedResource = value(resources.updateResource(resource.resourceId(),
                "Sterile gauze pads", bandages.categoryId(), "packs"));
        assertEquals("packs", updatedResource.unit());

        ServiceResult<Void> parentDelete = categories.deleteCategory(firstAid.categoryId());
        assertFalse(parentDelete.success());
        assertEquals("Category cannot be deleted while it has children or resources",
                parentDelete.message());
        ServiceResult<Void> resourceCategoryDelete = categories.deleteCategory(
                bandages.categoryId());
        assertFalse(resourceCategoryDelete.success());

        assertTrue(resources.deleteResource(resource.resourceId()).success());
        assertTrue(categories.deleteCategory(bandages.categoryId()).success());
        assertTrue(categories.deleteCategory(firstAid.categoryId()).success());
        assertTrue(categories.deleteCategory(medical.categoryId()).success());
    }

    @Test
    void inventoryReplacementIsAtomicAndThresholdFilteringUsesStrictlyBelow() {
        CoreIds ids = createInventoryParents();

        ShelterInventory low = value(inventory.replaceInventory(
                ids.shelterId(), ids.resourceId(), 8, 10));
        assertTrue(low.lowStock());
        ShelterInventory replaced = value(inventory.replaceInventory(
                ids.shelterId(), ids.resourceId(), 10, 10));
        assertFalse(replaced.lowStock());
        assertEquals(1, value(inventory.listInventory()).size());
        assertTrue(value(inventory.listLowStock()).isEmpty());

        value(inventory.replaceInventory(ids.shelterId(), ids.resourceId(), 9, 10));
        assertEquals(1, value(inventory.listLowStock()).size());
        ServiceResult<ShelterInventory> negative = inventory.replaceInventory(
                ids.shelterId(), ids.resourceId(), -1, 10);
        assertFalse(negative.success());
        assertEquals(9, value(inventory.findInventory(
                ids.shelterId(), ids.resourceId())).quantity());
        assertTrue(inventory.deleteInventory(ids.shelterId(), ids.resourceId()).success());
        assertTrue(value(inventory.listInventory()).isEmpty());
    }

    @Test
    void concurrentInventoryReplacementsKeepOneNonnegativeCompositeKeyRow() throws Exception {
        CoreIds ids = createInventoryParents();
        int writers = 12;
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        try {
            List<Future<ServiceResult<ShelterInventory>>> futures = java.util.stream.IntStream
                    .range(0, writers)
                    .mapToObj(quantity -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return inventory.replaceInventory(
                                ids.shelterId(), ids.resourceId(), quantity, 5);
                    })).toList();
            ready.await();
            start.countDown();
            Set<Integer> submitted = new HashSet<>();
            for (int quantity = 0; quantity < writers; quantity++) {
                submitted.add(quantity);
            }
            for (Future<ServiceResult<ShelterInventory>> future : futures) {
                assertTrue(future.get().success());
            }
            ShelterInventory finalRow = value(inventory.findInventory(
                    ids.shelterId(), ids.resourceId()));
            assertTrue(submitted.contains(finalRow.quantity()));
            assertEquals(1, queryInt("""
                    SELECT count(*) FROM shelter_inventory
                    WHERE shelter_id = ? AND resource_id = ?
                    """, ids.shelterId(), ids.resourceId()));
            assertTrue(finalRow.quantity() >= 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private CoreIds createInventoryParents() {
        Shelter shelter = value(shelters.createShelter(
                "Warehouse", "Kalpetta", null, null, 100,
                ShelterStatus.OPERATIONAL));
        ResourceCategory category = value(categories.createCategory("Water", null));
        var resource = value(resources.createResource(
                "Drinking water", category.categoryId(), "litres"));
        return new CoreIds(shelter.shelterId(), resource.resourceId());
    }

    private static <T> T value(ServiceResult<T> result) {
        assertTrue(result.success(), result.message());
        assertNotNull(result.value());
        return result.value();
    }

    private static void executeUpdate(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private static int queryInt(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private record CoreIds(int shelterId, int resourceId) {
    }
}
