package com.groupa13.disasterrelief.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class CoreControllerContractTest {

    private static final Path CONTROLLERS = Path.of(
            "src/main/java/com/groupa13/disasterrelief/controller");
    private static final Path FXML = Path.of("src/main/resources/fxml");
    private static final List<String> NAMES = List.of(
            "Disaster", "Zone", "Shelter", "Resource", "Inventory");

    @Test
    void ownedControllersUseAsynchronousServicesWithoutSqlOrSampleBackendPaths()
            throws IOException {
        for (String name : NAMES) {
            String source = Files.readString(CONTROLLERS.resolve(name + "Controller.java"));
            assertTrue(source.contains("Task<ServiceResult<T>>"), name);
            assertTrue(source.contains("setOnSucceeded"), name);
            assertTrue(source.contains("setOnFailed"), name);
            assertTrue(source.contains("Service"), name);
            assertFalse(source.contains("TODO:"), name);
            assertFalse(source.contains("printStackTrace"), name);
            assertFalse(source.contains("java.sql"), name);
            assertFalse(source.contains("DriverManager"), name);
            assertFalse(source.contains("Kerala Floods 2026"), name);
            assertFalse(source.contains("Paracetamol 500mg"), name);
        }
    }

    @Test
    void everyFxmlActionStillResolvesToAnOwnedControllerMethod() throws IOException {
        Pattern handler = Pattern.compile("onAction=\"#([A-Za-z0-9_]+)\"");
        for (String name : NAMES) {
            String source = Files.readString(CONTROLLERS.resolve(name + "Controller.java"));
            String fxml = Files.readString(FXML.resolve(name.toLowerCase() + ".fxml"));
            Matcher matcher = handler.matcher(fxml);
            while (matcher.find()) {
                assertTrue(source.contains(" " + matcher.group(1) + "("),
                        name + " is missing #" + matcher.group(1));
            }
        }
    }

    @Test
    void occupancyRemainsReadOnlyAndNotificationsReReadDatabaseState() throws IOException {
        String shelter = Files.readString(CONTROLLERS.resolve("ShelterController.java"));
        assertFalse(shelter.contains("currentOccupancyField.getText"));

        String inventory = Files.readString(CONTROLLERS.resolve("InventoryController.java"));
        assertTrue(inventory.contains("Platform.runLater(() -> refreshInventoryRow(alert))"));
        assertTrue(inventory.contains("inventoryService.findInventory("));

        String listener = Files.readString(Path.of(
                "src/main/java/com/groupa13/disasterrelief/service/core/"
                        + "PostgresLowStockNotificationListener.java"));
        assertTrue(listener.contains("PGSimpleDataSource"));
        assertTrue(listener.contains("LISTEN low_stock_alert"));
        assertFalse(listener.contains("Database.dataSource()"));
    }
}
