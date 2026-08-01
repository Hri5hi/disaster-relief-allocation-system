package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.util.Utility;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class InventoryController extends Utility {

    // TODO: replace with com.groupa13.disasterrelief.model.ShelterInventory once the model
    // layer exists. Mirrors the `shelter_inventory` table (shelter_id, resource_id,
    // quantity, reorder_level, last_updated) — composite PK (shelter_id, resource_id).
    public static class InventoryRow {
        private final StringProperty shelter;
        private final StringProperty resource;
        private final StringProperty quantity;
        private final StringProperty reorderLevel;
        private final StringProperty lastUpdated;
        private final boolean lowStock;

        public InventoryRow(String shelter, String resource, int quantity, int reorderLevel, String lastUpdated) {
            this.shelter = new SimpleStringProperty(shelter);
            this.resource = new SimpleStringProperty(resource);
            this.quantity = new SimpleStringProperty(String.valueOf(quantity));
            this.reorderLevel = new SimpleStringProperty(String.valueOf(reorderLevel));
            this.lastUpdated = new SimpleStringProperty(lastUpdated);
            this.lowStock = quantity < reorderLevel;
        }

        public String getShelter() { return shelter.get(); }
        public String getResource() { return resource.get(); }
        public String getQuantity() { return quantity.get(); }
        public String getReorderLevel() { return reorderLevel.get(); }
        public String getLastUpdated() { return lastUpdated.get(); }
        public String getStockStatus() { return lowStock ? "LOW STOCK" : "OK"; }
        public boolean isLowStock() { return lowStock; }
    }

    @FXML private ComboBox<String> shelterCombo;
    @FXML private ComboBox<String> resourceCombo;
    @FXML private TextField quantityField;
    @FXML private TextField reorderLevelField;
    @FXML private TextField lastUpdatedField;
    @FXML private Label errorLabel;
    @FXML private Label lowStockBadge;

    @FXML private TableView<InventoryRow> inventoryTable;
    @FXML private TableColumn<InventoryRow, String> colShelter;
    @FXML private TableColumn<InventoryRow, String> colResource;
    @FXML private TableColumn<InventoryRow, String> colQuantity;
    @FXML private TableColumn<InventoryRow, String> colReorderLevel;
    @FXML private TableColumn<InventoryRow, String> colLastUpdated;
    @FXML private TableColumn<InventoryRow, String> colStockStatus;

    private final ObservableList<InventoryRow> dataList = FXCollections.observableArrayList();
    private final ObservableList<InventoryRow> searchList = FXCollections.observableArrayList();
    private boolean showingLowStockOnly = false;

    @FXML
    public void initialize() {
        colShelter.setCellValueFactory(new PropertyValueFactory<>("shelter"));
        colResource.setCellValueFactory(new PropertyValueFactory<>("resource"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colReorderLevel.setCellValueFactory(new PropertyValueFactory<>("reorderLevel"));
        colLastUpdated.setCellValueFactory(new PropertyValueFactory<>("lastUpdated"));
        colStockStatus.setCellValueFactory(new PropertyValueFactory<>("stockStatus"));

        // Highlight the whole row when stock is below the reorder threshold.
        inventoryTable.setRowFactory(tv -> new TableRow<InventoryRow>() {
            @Override
            protected void updateItem(InventoryRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("row-low-stock");
                if (!empty && row != null && row.isLowStock()) {
                    getStyleClass().add("row-low-stock");
                }
            }
        });

        loadShelterOptions();
        loadResourceOptions();
        loadInventory();

        // TODO: subscribe to LISTEN/NOTIFY on channel 'low_stock_alert' here via a
        // background thread holding a dedicated PGConnection (PgConnection.getNotifications()
        // after issuing LISTEN low_stock_alert). On notification, refresh the affected row
        // and lowStockBadge without waiting for a manual reload — this table currently only
        // recalculates low-stock state when loadInventory()/handleAdd/handleUpdate run.
    }

    private void loadShelterOptions() {
        // TODO: replace with ShelterService.getAllShelters() -> SELECT shelter_id, name FROM shelters.
        shelterCombo.getItems().setAll(
                "S2001 — Government School Hall, Meppadi",
                "S2002 — Community Hall, Kalpetta");
    }

    private void loadResourceOptions() {
        // TODO: replace with ResourceService.getAllResources() -> SELECT resource_id, name FROM resources.
        resourceCombo.getItems().setAll(
                "R3001 — Paracetamol 500mg",
                "R3002 — 20L Water Drum");
    }

    private void loadInventory() {
        Task<List<InventoryRow>> loadTask = new Task<>() {
            @Override
            protected List<InventoryRow> call() {
                // TODO: replace with InventoryService.getAllInventory() -> SELECT si.*, s.name
                // AS shelter_name, r.name AS resource_name FROM shelter_inventory si
                // JOIN shelters s ON si.shelter_id = s.shelter_id
                // JOIN resources r ON si.resource_id = r.resource_id.
                return List.of(
                        new InventoryRow("Government School Hall, Meppadi", "Paracetamol 500mg", 8, 20, "2026-07-30 09:12"),
                        new InventoryRow("Government School Hall, Meppadi", "20L Water Drum", 145, 30, "2026-07-30 09:12"),
                        new InventoryRow("Community Hall, Kalpetta", "Paracetamol 500mg", 25, 20, "2026-07-29 18:40")
                );
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            applyCurrentFilter();
            updateLowStockBadge();
        });

        loadTask.setOnFailed(e -> {
            errorLabel.setText("Failed to load inventory from database");
            if (loadTask.getException() != null) {
                loadTask.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(loadTask, "inventory-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateLowStockBadge() {
        long count = dataList.stream().filter(InventoryRow::isLowStock).count();
        lowStockBadge.setText(count + " Low Stock");
    }

    private void applyCurrentFilter() {
        if (showingLowStockOnly) {
            ObservableList<InventoryRow> filtered = FXCollections.observableArrayList();
            dataList.stream().filter(InventoryRow::isLowStock).forEach(filtered::add);
            inventoryTable.setItems(filtered);
        } else {
            inventoryTable.setItems(dataList);
        }
    }

    @FXML
    public void handleToggleLowStockOnly(ActionEvent event) {
        showingLowStockOnly = !showingLowStockOnly;
        applyCurrentFilter();
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        try {
            String shelter = shelterCombo.getValue();
            String resource = resourceCombo.getValue();
            if (shelter == null || resource == null) {
                errorLabel.setText("Select a shelter and a resource");
                return;
            }

            int quantity;
            int reorderLevel;
            try {
                quantity = Integer.parseInt(quantityField.getText());
                reorderLevel = reorderLevelField.getText().isBlank() ? 10 : Integer.parseInt(reorderLevelField.getText());
            } catch (NumberFormatException nfe) {
                errorLabel.setText("Quantity and reorder level must be numbers");
                return;
            }

            if (quantity < 0) {
                errorLabel.setText("Quantity cannot be negative");
                return;
            }

            // TODO: call InventoryService.upsertInventory(shelterId, resourceId, quantity,
            // reorderLevel) -> INSERT INTO shelter_inventory (...) VALUES (...)
            // ON CONFLICT (shelter_id, resource_id) DO UPDATE SET quantity = ...
            // NOTE: this is also the shape used by donation intake (UPSERT), so the DAO
            // method should likely be shared between InventoryController and DonationController.
            InventoryRow row = new InventoryRow(stripId(shelter), stripId(resource), quantity, reorderLevel, "just now");
            dataList.add(0, row);
            applyCurrentFilter();
            updateLowStockBadge();
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not add inventory record");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            String shelter = shelterCombo.getValue();
            String resource = resourceCombo.getValue();
            if (shelter == null || resource == null) {
                errorLabel.setText("Select a shelter and a resource to update");
                return;
            }

            // TODO: call InventoryService.updateInventory(shelterId, resourceId, quantity,
            // reorderLevel) -> UPDATE shelter_inventory SET quantity=?, reorder_level=?,
            // last_updated=NOW() WHERE shelter_id=? AND resource_id=?.
            // NOTE: the low-stock trigger (trg_low_stock_alert) fires AFTER UPDATE here —
            // this is the write path that can push quantity below reorder_level and
            // trigger a pg_notify('low_stock_alert', ...) server-side.

            errorLabel.setText("");
            loadInventory();
        } catch (Exception e) {
            errorLabel.setText("Could not update inventory record");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            String shelter = shelterCombo.getValue();
            String resource = resourceCombo.getValue();
            if (shelter == null || resource == null) {
                errorLabel.setText("Select a shelter and a resource to delete");
                return;
            }

            // TODO: call InventoryService.deleteInventory(shelterId, resourceId) -> DELETE
            // FROM shelter_inventory WHERE shelter_id=? AND resource_id=?.
            String shelterName = stripId(shelter);
            String resourceName = stripId(resource);
            dataList.removeIf(r -> r.getShelter().equals(shelterName) && r.getResource().equals(resourceName));
            applyCurrentFilter();
            updateLowStockBadge();
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not delete inventory record");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleFind(ActionEvent event) {
        try {
            String shelter = shelterCombo.getValue();
            String resource = resourceCombo.getValue();
            if (shelter == null || resource == null) {
                errorLabel.setText("Select a shelter and a resource to find");
                return;
            }

            // TODO: call InventoryService.find(shelterId, resourceId) -> SELECT * FROM
            // shelter_inventory WHERE shelter_id=? AND resource_id=?.
            String shelterName = stripId(shelter);
            String resourceName = stripId(resource);
            InventoryRow found = dataList.stream()
                    .filter(r -> r.getShelter().equals(shelterName) && r.getResource().equals(resourceName))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                errorLabel.setText("No inventory record found for that shelter/resource pair");
                return;
            }

            searchList.setAll(found);
            inventoryTable.setItems(searchList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not find inventory record");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        shelterCombo.getSelectionModel().clearSelection();
        resourceCombo.getSelectionModel().clearSelection();
        quantityField.clear();
        reorderLevelField.clear();
        lastUpdatedField.clear();
        errorLabel.setText("");
        applyCurrentFilter();
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    /** Strips the leading "ID — " prefix from a combo box entry, leaving just the display name. */
    private String stripId(String comboEntry) {
        if (comboEntry == null) {
            return null;
        }
        int dashIndex = comboEntry.indexOf(" — ");
        return dashIndex == -1 ? comboEntry : comboEntry.substring(dashIndex + 3);
    }
}
