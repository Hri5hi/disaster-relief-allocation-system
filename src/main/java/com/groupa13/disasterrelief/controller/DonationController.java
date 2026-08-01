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
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class DonationController extends Utility {

    // TODO: replace with com.groupa13.disasterrelief.model.DonationRecord once the model
    // layer exists. Mirrors the `donation_records` table (donation_id, donor_name, items
    // JSONB, received_at, receiving_shelter). itemsSummary is a display-only flattening of
    // the JSONB array for the table; the DAO should serialize the real item list to JSONB.
    public static class DonationRow {
        private final StringProperty donationId;
        private final StringProperty donorName;
        private final StringProperty receivingShelter;
        private final StringProperty itemsSummary;
        private final StringProperty receivedAt;

        public DonationRow(String donationId, String donorName, String receivingShelter,
                String itemsSummary, String receivedAt) {
            this.donationId = new SimpleStringProperty(donationId);
            this.donorName = new SimpleStringProperty(donorName);
            this.receivingShelter = new SimpleStringProperty(receivingShelter);
            this.itemsSummary = new SimpleStringProperty(itemsSummary);
            this.receivedAt = new SimpleStringProperty(receivedAt);
        }

        public String getDonationId() { return donationId.get(); }
        public String getDonorName() { return donorName.get(); }
        public String getReceivingShelter() { return receivingShelter.get(); }
        public String getItemsSummary() { return itemsSummary.get(); }
        public String getReceivedAt() { return receivedAt.get(); }
    }

    /** One line item in the donation being built — maps to a single {"resource_id","quantity"} entry. */
    public static class DonationItemRow {
        private final StringProperty resource;
        private final StringProperty quantity;

        public DonationItemRow(String resource, int quantity) {
            this.resource = new SimpleStringProperty(resource);
            this.quantity = new SimpleStringProperty(String.valueOf(quantity));
        }

        public String getResource() { return resource.get(); }
        public String getQuantity() { return quantity.get(); }
        public int getQuantityAsInt() { return Integer.parseInt(quantity.get()); }
        public void setQuantity(int newQty) { quantity.set(String.valueOf(newQty)); }
    }

    @FXML private TextField donationIdField;
    @FXML private TextField donorNameField;
    @FXML private ComboBox<String> receivingShelterCombo;
    @FXML private ComboBox<String> itemResourceCombo;
    @FXML private TextField itemQuantityField;
    @FXML private Label errorLabel;

    @FXML private TableView<DonationItemRow> itemsTable;
    @FXML private TableColumn<DonationItemRow, String> colItemResource;
    @FXML private TableColumn<DonationItemRow, String> colItemQuantity;

    @FXML private TableView<DonationRow> donationTable;
    @FXML private TableColumn<DonationRow, String> colDonationId;
    @FXML private TableColumn<DonationRow, String> colDonorName;
    @FXML private TableColumn<DonationRow, String> colReceivingShelter;
    @FXML private TableColumn<DonationRow, String> colItemsSummary;
    @FXML private TableColumn<DonationRow, String> colReceivedAt;

    private final ObservableList<DonationItemRow> currentItems = FXCollections.observableArrayList();
    private final ObservableList<DonationRow> donationHistory = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colItemResource.setCellValueFactory(new PropertyValueFactory<>("resource"));
        colItemQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        itemsTable.setItems(currentItems);

        colDonationId.setCellValueFactory(new PropertyValueFactory<>("donationId"));
        colDonorName.setCellValueFactory(new PropertyValueFactory<>("donorName"));
        colReceivingShelter.setCellValueFactory(new PropertyValueFactory<>("receivingShelter"));
        colItemsSummary.setCellValueFactory(new PropertyValueFactory<>("itemsSummary"));
        colReceivedAt.setCellValueFactory(new PropertyValueFactory<>("receivedAt"));
        donationTable.setItems(donationHistory);

        loadShelterOptions();
        loadResourceOptions();
        loadDonationHistory();
    }

    private void loadShelterOptions() {
        // TODO: replace with ShelterService.getAllShelters() -> SELECT shelter_id, name FROM shelters.
        receivingShelterCombo.getItems().setAll(
                "S2001 — Government School Hall, Meppadi",
                "S2002 — Community Hall, Kalpetta");
    }

    private void loadResourceOptions() {
        // TODO: replace with ResourceService.getAllResources() -> SELECT resource_id, name FROM resources.
        itemResourceCombo.getItems().setAll(
                "R3001 — Paracetamol 500mg",
                "R3002 — 20L Water Drum");
    }

    private void loadDonationHistory() {
        Task<List<DonationRow>> loadTask = new Task<>() {
            @Override
            protected List<DonationRow> call() {
                // TODO: replace with DonationService.getRecentDonations() -> SELECT * FROM
                // donation_records ORDER BY received_at DESC. itemsSummary should be built by
                // flattening the JSONB `items` column, e.g. via jsonb_array_elements in SQL
                // or by parsing client-side after fetch.
                return List.of(
                        new DonationRow("DN4001", "Rotary Club of Kochi",
                                "Government School Hall, Meppadi",
                                "Paracetamol 500mg x100, 20L Water Drum x50", "2026-07-29 14:20")
                );
            }
        };

        loadTask.setOnSucceeded(e -> donationHistory.setAll(loadTask.getValue()));
        loadTask.setOnFailed(e -> {
            errorLabel.setText("Failed to load donation history from database");
            if (loadTask.getException() != null) {
                loadTask.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(loadTask, "donation-history-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleAddItem(ActionEvent event) {
        String resource = itemResourceCombo.getValue();
        if (resource == null) {
            errorLabel.setText("Select a resource to add");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(itemQuantityField.getText());
            if (quantity <= 0) {
                errorLabel.setText("Quantity must be greater than zero");
                return;
            }
        } catch (NumberFormatException nfe) {
            errorLabel.setText("Quantity must be a number");
            return;
        }

        // Merge into an existing line for the same resource rather than adding a duplicate row.
        DonationItemRow existing = currentItems.stream()
                .filter(item -> item.getResource().equals(resource))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantityAsInt() + quantity);
            itemsTable.refresh();
        } else {
            currentItems.add(new DonationItemRow(resource, quantity));
        }

        itemQuantityField.clear();
        errorLabel.setText("");
    }

    @FXML
    public void handleRemoveItem(ActionEvent event) {
        DonationItemRow selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            errorLabel.setText("Select an item to remove");
            return;
        }
        currentItems.remove(selected);
        errorLabel.setText("");
    }

    @FXML
    public void handleRecordDonation(ActionEvent event) {
        try {
            String donorName = donorNameField.getText();
            if (donorName == null || donorName.isBlank()) {
                errorLabel.setText("Donor name is required");
                return;
            }

            String shelter = receivingShelterCombo.getValue();
            if (shelter == null) {
                errorLabel.setText("Select a receiving shelter");
                return;
            }

            if (currentItems.isEmpty()) {
                errorLabel.setText("Add at least one item before recording the donation");
                return;
            }

            // TODO: call DonationService.recordDonation(donorName, shelterId, items) which
            // should, in a single transaction:
            //   1. INSERT INTO donation_records (donor_name, items, receiving_shelter)
            //      VALUES (?, ?::jsonb, ?) RETURNING donation_id
            //   2. For each item, UPSERT into shelter_inventory:
            //      INSERT INTO shelter_inventory (shelter_id, resource_id, quantity)
            //      VALUES (?, ?, ?) ON CONFLICT (shelter_id, resource_id)
            //      DO UPDATE SET quantity = shelter_inventory.quantity + EXCLUDED.quantity,
            //      last_updated = NOW()
            // If any UPSERT drops a resulting quantity below reorder_level, trg_low_stock_alert
            // will NOT fire here since this is an increase, not a decrease — noted for whoever
            // implements the trigger, not a UI concern.

            String donationId = "DN" + (4000 + donationHistory.size() + 1);
            String itemsSummary = buildItemsSummary();

            DonationRow row = new DonationRow(donationId, donorName, stripId(shelter), itemsSummary, "just now");
            donationHistory.add(0, row);

            handleClear(event);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not record donation");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        donationIdField.clear();
        donorNameField.clear();
        receivingShelterCombo.getSelectionModel().clearSelection();
        itemResourceCombo.getSelectionModel().clearSelection();
        itemQuantityField.clear();
        currentItems.clear();
        errorLabel.setText("");
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    private String buildItemsSummary() {
        StringBuilder sb = new StringBuilder();
        for (DonationItemRow item : currentItems) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(stripId(item.getResource())).append(" x").append(item.getQuantity());
        }
        return sb.toString();
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
