package com.groupa13.disasterrelief.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.operations.DonationItem;
import com.groupa13.disasterrelief.model.operations.DonationRecord;
import com.groupa13.disasterrelief.service.core.ResourceService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.core.ShelterService;
import com.groupa13.disasterrelief.service.operations.DonationService;
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

    public static class DonationItemRow {
        private final int resourceId;
        private final StringProperty resource;
        private final StringProperty quantity;

        public DonationItemRow(int resourceId, String resource, int quantity) {
            this.resourceId = resourceId;
            this.resource = new SimpleStringProperty(resource);
            this.quantity = new SimpleStringProperty(String.valueOf(quantity));
        }

        public int getResourceId() { return resourceId; }
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
    private final Map<String, Integer> shelterNameToId = new HashMap<>();
    private final Map<String, Integer> resourceNameToId = new HashMap<>();

    private final DonationService donationService;
    private final ShelterService shelterService;
    private final ResourceService resourceService;

    public DonationController() {
        this(new DonationService(), new ShelterService(), new ResourceService());
    }

    public DonationController(DonationService donationService, ShelterService shelterService,
            ResourceService resourceService) {
        this.donationService = donationService;
        this.shelterService = shelterService;
        this.resourceService = resourceService;
    }

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

        loadOptionsAndHistory();
    }

    private void loadOptionsAndHistory() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                ServiceResult<List<EntityOption>> shelters = shelterService.listShelterOptions();
                if (shelters.success()) {
                    for (EntityOption s : shelters.value()) {
                        shelterNameToId.put("S" + s.id() + " — " + s.name(), s.id());
                    }
                }

                ServiceResult<List<EntityOption>> resources = resourceService.listResourceOptions();
                if (resources.success()) {
                    for (EntityOption r : resources.value()) {
                        resourceNameToId.put("R" + r.id() + " — " + r.name(), r.id());
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            receivingShelterCombo.getItems().setAll(shelterNameToId.keySet());
            itemResourceCombo.getItems().setAll(resourceNameToId.keySet());
            loadDonationHistory();
        });

        task.setOnFailed(e -> errorLabel.setText("Failed to load options"));

        Thread thread = new Thread(task, "donation-options-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadDonationHistory() {
        Task<ServiceResult<List<DonationRecord>>> loadTask = new Task<>() {
            @Override
            protected ServiceResult<List<DonationRecord>> call() {
                return donationService.listDonations();
            }
        };

        loadTask.setOnSucceeded(e -> {
            ServiceResult<List<DonationRecord>> result = loadTask.getValue();
            if (result != null && result.success()) {
                List<DonationRow> rows = new ArrayList<>();
                for (DonationRecord rec : result.value()) {
                    rows.add(new DonationRow(
                            "DN" + rec.donationId(),
                            rec.donorName() == null ? "Anonymous" : rec.donorName(),
                            rec.receivingShelterName() == null ? "Central Shelter" : rec.receivingShelterName(),
                            rec.itemsJson(),
                            rec.receivedAt()
                    ));
                }
                donationHistory.setAll(rows);
            }
        });

        loadTask.setOnFailed(e -> errorLabel.setText("Failed to load donation history"));

        Thread thread = new Thread(loadTask, "donation-history-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleAddItem(ActionEvent event) {
        String resourceText = itemResourceCombo.getValue();
        if (resourceText == null) {
            errorLabel.setText("Select a resource to add");
            return;
        }

        Integer resId = resourceNameToId.get(resourceText);
        if (resId == null) {
            errorLabel.setText("Invalid resource selected");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(itemQuantityField.getText().trim());
            if (quantity <= 0) {
                errorLabel.setText("Quantity must be greater than zero");
                return;
            }
        } catch (NumberFormatException nfe) {
            errorLabel.setText("Quantity must be a positive number");
            return;
        }

        DonationItemRow existing = currentItems.stream()
                .filter(item -> item.getResourceId() == resId)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantityAsInt() + quantity);
            itemsTable.refresh();
        } else {
            currentItems.add(new DonationItemRow(resId, resourceText, quantity));
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
        String donorName = donorNameField.getText();
        if (donorName == null || donorName.isBlank()) {
            donorName = "Anonymous";
        }

        String shelterText = receivingShelterCombo.getValue();
        if (shelterText == null) {
            errorLabel.setText("Select a receiving shelter");
            return;
        }

        Integer shelterId = shelterNameToId.get(shelterText);
        if (shelterId == null) {
            errorLabel.setText("Invalid shelter selected");
            return;
        }

        if (currentItems.isEmpty()) {
            errorLabel.setText("Add at least one item before recording the donation");
            return;
        }

        List<DonationItem> itemsToRecord = new ArrayList<>();
        for (DonationItemRow row : currentItems) {
            itemsToRecord.add(new DonationItem(row.getResourceId(), row.getQuantityAsInt()));
        }

        final String finalDonor = donorName;
        Task<ServiceResult<Integer>> task = new Task<>() {
            @Override
            protected ServiceResult<Integer> call() {
                return donationService.processDonation(finalDonor, shelterId, itemsToRecord);
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<Integer> result = task.getValue();
            if (result != null && result.success()) {
                showInfoAlert("Success", result.message());
                handleClear(event);
                loadDonationHistory();
            } else {
                errorLabel.setText(result != null ? result.message() : "Donation recording failed");
            }
        });

        task.setOnFailed(e -> errorLabel.setText("Donation processing task failed"));

        Thread thread = new Thread(task, "record-donation-worker");
        thread.setDaemon(true);
        thread.start();
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
}
