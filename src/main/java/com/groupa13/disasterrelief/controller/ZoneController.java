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

public class ZoneController extends Utility {

    // TODO: replace with com.groupa13.disasterrelief.model.Zone once the model layer
    // exists. Mirrors the `zones` table (zone_id, disaster_id, name, severity,
    // primary_shelter_id, coordinator_contact).
    public static class ZoneRow {
        private final StringProperty zoneId;
        private final StringProperty name;
        private final StringProperty disasterId;
        private final StringProperty severity;
        private final StringProperty primaryShelter;
        private final StringProperty coordinatorContact;

        public ZoneRow(String zoneId, String name, String disasterId, String severity,
                String primaryShelter, String coordinatorContact) {
            this.zoneId = new SimpleStringProperty(zoneId);
            this.name = new SimpleStringProperty(name);
            this.disasterId = new SimpleStringProperty(disasterId);
            this.severity = new SimpleStringProperty(severity);
            this.primaryShelter = new SimpleStringProperty(primaryShelter);
            this.coordinatorContact = new SimpleStringProperty(coordinatorContact);
        }

        public String getZoneId() { return zoneId.get(); }
        public String getName() { return name.get(); }
        public String getDisasterId() { return disasterId.get(); }
        public String getSeverity() { return severity.get(); }
        public String getPrimaryShelter() { return primaryShelter.get(); }
        public String getCoordinatorContact() { return coordinatorContact.get(); }
    }

    @FXML private TextField zoneIdField;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> disasterCombo;
    @FXML private ComboBox<String> severityCombo;
    @FXML private ComboBox<String> primaryShelterCombo;
    @FXML private TextField coordinatorContactField;
    @FXML private Label errorLabel;

    @FXML private TableView<ZoneRow> zoneTable;
    @FXML private TableColumn<ZoneRow, String> colZoneId;
    @FXML private TableColumn<ZoneRow, String> colName;
    @FXML private TableColumn<ZoneRow, String> colDisasterId;
    @FXML private TableColumn<ZoneRow, String> colSeverity;
    @FXML private TableColumn<ZoneRow, String> colPrimaryShelter;
    @FXML private TableColumn<ZoneRow, String> colCoordinatorContact;

    private final ObservableList<ZoneRow> dataList = FXCollections.observableArrayList();
    private final ObservableList<ZoneRow> searchList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        severityCombo.getItems().addAll("low", "medium", "high", "critical");
        severityCombo.getSelectionModel().selectFirst();

        colZoneId.setCellValueFactory(new PropertyValueFactory<>("zoneId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colDisasterId.setCellValueFactory(new PropertyValueFactory<>("disasterId"));
        colSeverity.setCellValueFactory(new PropertyValueFactory<>("severity"));
        colPrimaryShelter.setCellValueFactory(new PropertyValueFactory<>("primaryShelter"));
        colCoordinatorContact.setCellValueFactory(new PropertyValueFactory<>("coordinatorContact"));

        loadDisasterOptions();
        loadShelterOptions();
        loadZones();
    }

    private void loadDisasterOptions() {
        // TODO: replace with DisasterService.getAllDisasters() -> SELECT disaster_id, name
        // FROM disasters WHERE status IN ('active','monitoring') so closed disasters
        // can't have new zones added to them.
        disasterCombo.getItems().setAll("D1001 — Kerala Floods 2026", "D1002 — Wayanad Landslide 2026");
    }

    private void loadShelterOptions() {
        // TODO: replace with ShelterService.getAllShelters() -> SELECT shelter_id, name FROM shelters.
        // Ideally filtered/sorted by proximity to the zone once `coordinates` are captured.
        primaryShelterCombo.getItems().setAll(
                "S2001 — Government School Hall, Meppadi",
                "S2002 — Community Hall, Kalpetta");
    }

    private void loadZones() {
        Task<List<ZoneRow>> loadTask = new Task<>() {
            @Override
            protected List<ZoneRow> call() {
                // TODO: replace with ZoneService.getAllZones() -> SELECT * FROM zones
                // (optionally joined to shelters for the primary shelter name).
                return List.of(
                        new ZoneRow("Z1001", "Wayanad North Sector", "D1002", "high",
                                "S2001 — Government School Hall, Meppadi", "+91 90000 00001"),
                        new ZoneRow("Z1002", "Wayanad South Sector", "D1002", "critical",
                                "S2002 — Community Hall, Kalpetta", "+91 90000 00002")
                );
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            zoneTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            errorLabel.setText("Failed to load zones from database");
            if (loadTask.getException() != null) {
                loadTask.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(loadTask, "zone-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        try {
            String name = nameField.getText();
            if (name == null || name.isBlank()) {
                errorLabel.setText("Zone name is required");
                return;
            }

            String disaster = disasterCombo.getValue();
            if (disaster == null) {
                errorLabel.setText("Select a disaster for this zone");
                return;
            }

            String severity = severityCombo.getValue();
            String primaryShelter = primaryShelterCombo.getValue();
            String coordinatorContact = coordinatorContactField.getText();

            // TODO: call ZoneService.registerZone(name, disasterId, severity,
            // primaryShelterId, coordinatorContact) -> INSERT INTO zones (...)
            String zoneId = zoneIdField.getText().isBlank()
                    ? "Z" + (1000 + dataList.size() + 1)
                    : zoneIdField.getText();

            ZoneRow row = new ZoneRow(zoneId, name, extractId(disaster), severity, primaryShelter, coordinatorContact);
            dataList.add(0, row);
            zoneTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not add zone");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            String zoneId = zoneIdField.getText();
            if (zoneId == null || zoneId.isBlank()) {
                errorLabel.setText("Invalid zone ID");
                return;
            }

            // TODO: call ZoneService.updateZone(zoneId, name, severity, primaryShelterId,
            // coordinatorContact) -> UPDATE zones SET ... WHERE zone_id = ?
            // NOTE: disaster_id is intentionally not editable here — moving a zone between
            // disasters is a structural change that should go through a dedicated flow, if
            // it's allowed at all.

            errorLabel.setText("");
            loadZones();
        } catch (Exception e) {
            errorLabel.setText("Could not update zone");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            String zoneId = zoneIdField.getText();
            if (zoneId == null || zoneId.isBlank()) {
                errorLabel.setText("Invalid zone ID");
                return;
            }

            // TODO: call ZoneService.deleteZone(zoneId) -> DELETE FROM zones WHERE zone_id = ?
            // Check FK impact: resource_requests and volunteer_shifts reference zone_id,
            // so this will likely need to be blocked or cascaded deliberately, not silently.
            dataList.removeIf(z -> z.getZoneId().equals(zoneId));
            zoneTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not delete zone");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleFind(ActionEvent event) {
        try {
            String zoneId = zoneIdField.getText();
            if (zoneId == null || zoneId.isBlank()) {
                errorLabel.setText("Invalid zone ID");
                return;
            }

            // TODO: call ZoneService.findById(zoneId) -> SELECT * FROM zones WHERE zone_id = ?
            ZoneRow found = dataList.stream()
                    .filter(z -> z.getZoneId().equals(zoneId))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                errorLabel.setText("Zone not found");
                return;
            }

            searchList.setAll(found);
            zoneTable.setItems(searchList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not find zone");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        zoneIdField.clear();
        nameField.clear();
        disasterCombo.getSelectionModel().clearSelection();
        severityCombo.getSelectionModel().selectFirst();
        primaryShelterCombo.getSelectionModel().clearSelection();
        coordinatorContactField.clear();
        errorLabel.setText("");
        zoneTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    /** Extracts the leading ID token from a "ID — Label" combo box entry. */
    private String extractId(String comboEntry) {
        if (comboEntry == null) {
            return null;
        }
        int dashIndex = comboEntry.indexOf(" — ");
        return dashIndex == -1 ? comboEntry : comboEntry.substring(0, dashIndex);
    }
}
