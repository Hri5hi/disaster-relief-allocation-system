package com.groupa13.disasterrelief.controller;

import java.time.LocalDate;
import java.util.List;

import com.groupa13.disasterrelief.util.UserSession.UserRole;
import com.groupa13.disasterrelief.util.Utility;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class DisasterController extends Utility {

    // TODO: replace with com.groupa13.disasterrelief.model.Disaster once the model
    // layer exists. Field names below mirror the `disasters` table from
    // project_description_v2.md (disaster_id, name, type, severity, active_period,
    // status) with activePeriod pre-formatted for display.
    public static class DisasterRow {
        private final StringProperty disasterId;
        private final StringProperty name;
        private final StringProperty type;
        private final StringProperty severity;
        private final StringProperty activePeriod;
        private final StringProperty status;

        public DisasterRow(String disasterId, String name, String type, String severity,
                String activePeriod, String status) {
            this.disasterId = new SimpleStringProperty(disasterId);
            this.name = new SimpleStringProperty(name);
            this.type = new SimpleStringProperty(type);
            this.severity = new SimpleStringProperty(severity);
            this.activePeriod = new SimpleStringProperty(activePeriod);
            this.status = new SimpleStringProperty(status);
        }

        public String getDisasterId() { return disasterId.get(); }
        public String getName() { return name.get(); }
        public String getType() { return type.get(); }
        public String getSeverity() { return severity.get(); }
        public String getActivePeriod() { return activePeriod.get(); }
        public String getStatus() { return status.get(); }
    }

    @FXML private TextField disasterIdField;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private ComboBox<String> severityCombo;
    @FXML private DatePicker activePeriodStartPicker;
    @FXML private DatePicker activePeriodEndPicker;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label errorLabel;

    @FXML private TableView<DisasterRow> disasterTable;
    @FXML private TableColumn<DisasterRow, String> colDisasterId;
    @FXML private TableColumn<DisasterRow, String> colName;
    @FXML private TableColumn<DisasterRow, String> colType;
    @FXML private TableColumn<DisasterRow, String> colSeverity;
    @FXML private TableColumn<DisasterRow, String> colActivePeriod;
    @FXML private TableColumn<DisasterRow, String> colStatus;

    private final ObservableList<DisasterRow> dataList = FXCollections.observableArrayList();
    private final ObservableList<DisasterRow> searchList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        typeCombo.getItems().addAll("Flood", "Earthquake", "Cyclone", "Landslide", "Other");
        severityCombo.getItems().addAll("low", "medium", "high", "critical");
        statusCombo.getItems().addAll("active", "monitoring", "resolved", "closed");
        typeCombo.getSelectionModel().selectFirst();
        severityCombo.getSelectionModel().selectFirst();
        statusCombo.getSelectionModel().selectFirst();

        colDisasterId.setCellValueFactory(new PropertyValueFactory<>("disasterId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colSeverity.setCellValueFactory(new PropertyValueFactory<>("severity"));
        colActivePeriod.setCellValueFactory(new PropertyValueFactory<>("activePeriod"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        loadDisasters();
    }

    private void loadDisasters() {
        Task<List<DisasterRow>> loadTask = new Task<>() {
            @Override
            protected List<DisasterRow> call() {
                // TODO: replace with DisasterService.getAllDisasters() -> SELECT * FROM disasters
                // (or v_active_disasters for the active-only subset).
                return List.of(
                        new DisasterRow("D1001", "Kerala Floods 2026", "Flood", "critical",
                                "2026-07-01 → 2026-07-15", "active"),
                        new DisasterRow("D1002", "Wayanad Landslide 2026", "Landslide", "high",
                                "2026-06-10 → 2026-06-25", "monitoring")
                );
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            disasterTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            errorLabel.setText("Failed to load disasters from database");
            if (loadTask.getException() != null) {
                loadTask.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(loadTask, "disaster-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        try {
            String name = nameField.getText();
            if (name == null || name.isBlank()) {
                errorLabel.setText("Disaster name is required");
                return;
            }

            String type = typeCombo.getValue();
            String severity = severityCombo.getValue();
            String status = statusCombo.getValue();
            String periodText = formatActivePeriod();

            // TODO: call DisasterService.registerDisaster(name, type, severity,
            // activePeriodStart, activePeriodEnd, status) -> INSERT INTO disasters (...)
            // and use the SERIAL disaster_id returned instead of a client-generated one.
            String disasterId = disasterIdField.getText().isBlank()
                    ? "D" + (1000 + dataList.size() + 1)
                    : disasterIdField.getText();

            DisasterRow row = new DisasterRow(disasterId, name, type, severity, periodText, status);
            dataList.add(0, row);
            disasterTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not add disaster");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            String disasterId = disasterIdField.getText();
            if (disasterId == null || disasterId.isBlank()) {
                errorLabel.setText("Invalid disaster ID");
                return;
            }

            String status = statusCombo.getValue();

            if ("resolved".equals(status) || "closed".equals(status)) {
                Alert confirm = new Alert(AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Status Change");
                confirm.setHeaderText("Marking this disaster as \"" + status + "\"");
                confirm.setContentText(
                        "This will automatically cancel any still-pending resource requests "
                                + "for all zones under this disaster (trg_cascade_disaster_close). "
                                + "This action cannot be undone from the UI. Continue?");
                var result = confirm.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    errorLabel.setText("Status change cancelled");
                    return;
                }
            }

            // TODO: call DisasterService.updateDisaster(disasterId, name, type, severity,
            // activePeriodStart, activePeriodEnd, status) -> UPDATE disasters SET ... WHERE disaster_id = ?
            // The resolve/close cascade itself happens inside trg_cascade_disaster_close —
            // the UI only needs to warn the admin before triggering it, not replicate the logic.

            errorLabel.setText("");
            loadDisasters();
        } catch (Exception e) {
            errorLabel.setText("Could not update disaster");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            String disasterId = disasterIdField.getText();
            if (disasterId == null || disasterId.isBlank()) {
                errorLabel.setText("Invalid disaster ID");
                return;
            }

            // TODO: call DisasterService.deleteDisaster(disasterId) -> DELETE FROM disasters
            // WHERE disaster_id = ?. Confirm whether deletes should even be allowed once a
            // disaster has zones/requests attached, or whether "closed" status should be
            // the only terminal state (deletion may violate FK constraints by design).
            dataList.removeIf(d -> d.getDisasterId().equals(disasterId));
            disasterTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not delete disaster");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleFind(ActionEvent event) {
        try {
            String disasterId = disasterIdField.getText();
            if (disasterId == null || disasterId.isBlank()) {
                errorLabel.setText("Invalid disaster ID");
                return;
            }

            // TODO: call DisasterService.findById(disasterId) -> SELECT * FROM disasters WHERE disaster_id = ?
            DisasterRow found = dataList.stream()
                    .filter(d -> d.getDisasterId().equals(disasterId))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                errorLabel.setText("Disaster not found");
                return;
            }

            searchList.setAll(found);
            disasterTable.setItems(searchList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not find disaster");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        disasterIdField.clear();
        nameField.clear();
        typeCombo.getSelectionModel().selectFirst();
        severityCombo.getSelectionModel().selectFirst();
        statusCombo.getSelectionModel().selectFirst();
        activePeriodStartPicker.setValue(null);
        activePeriodEndPicker.setValue(null);
        errorLabel.setText("");
        disasterTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    private String formatActivePeriod() {
        LocalDate start = activePeriodStartPicker.getValue();
        LocalDate end = activePeriodEndPicker.getValue();
        if (start == null && end == null) {
            return "—";
        }
        String startText = start == null ? "?" : start.toString();
        String endText = end == null ? "?" : end.toString();
        return startText + " → " + endText;
    }
}
