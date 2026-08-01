package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.UserSession.UserRole;
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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ShelterController extends Utility {

    // TODO: replace with com.groupa13.disasterrelief.model.Shelter once the model layer
    // exists. Mirrors the `shelters` table (shelter_id, name, location (coordinates
    // composite type), capacity, current_occupancy, status). current_occupancy is
    // trigger-maintained (trg_auto_update_shelter_occupancy) — never write to it directly.
    public static class ShelterRow {
        private final StringProperty shelterId;
        private final StringProperty name;
        private final StringProperty coordinates;
        private final StringProperty capacity;
        private final StringProperty occupancy;
        private final double occupancyRatio;
        private final StringProperty status;

        public ShelterRow(String shelterId, String name, String coordinates, int capacity,
                int occupancy, String status) {
            this.shelterId = new SimpleStringProperty(shelterId);
            this.name = new SimpleStringProperty(name);
            this.coordinates = new SimpleStringProperty(coordinates);
            this.capacity = new SimpleStringProperty(String.valueOf(capacity));
            this.occupancy = new SimpleStringProperty(String.valueOf(occupancy));
            this.occupancyRatio = capacity <= 0 ? 0 : Math.min(1.0, (double) occupancy / capacity);
            this.status = new SimpleStringProperty(status);
        }

        public String getShelterId() { return shelterId.get(); }
        public String getName() { return name.get(); }
        public String getCoordinates() { return coordinates.get(); }
        public String getCapacity() { return capacity.get(); }
        public String getOccupancy() { return occupancy.get(); }
        public double getOccupancyRatio() { return occupancyRatio; }
        public String getStatus() { return status.get(); }
    }

    @FXML private Label pageTitleLabel;
    @FXML private VBox formPanel;
    @FXML private HBox writeActionsRow1;
    @FXML private HBox writeActionsRow2;

    @FXML private TextField shelterIdField;
    @FXML private TextField nameField;
    @FXML private TextField latitudeField;
    @FXML private TextField longitudeField;
    @FXML private TextField capacityField;
    @FXML private TextField currentOccupancyField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label errorLabel;

    @FXML private TableView<ShelterRow> shelterTable;
    @FXML private TableColumn<ShelterRow, String> colShelterId;
    @FXML private TableColumn<ShelterRow, String> colName;
    @FXML private TableColumn<ShelterRow, String> colCoordinates;
    @FXML private TableColumn<ShelterRow, String> colCapacity;
    @FXML private TableColumn<ShelterRow, String> colOccupancy;
    @FXML private TableColumn<ShelterRow, ShelterRow> colOccupancyBar;
    @FXML private TableColumn<ShelterRow, String> colStatus;

    private final ObservableList<ShelterRow> dataList = FXCollections.observableArrayList();
    private final ObservableList<ShelterRow> searchList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        statusCombo.getItems().addAll("operational", "full", "closed");
        statusCombo.getSelectionModel().selectFirst();

        colShelterId.setCellValueFactory(new PropertyValueFactory<>("shelterId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCoordinates.setCellValueFactory(new PropertyValueFactory<>("coordinates"));
        colCapacity.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        colOccupancy.setCellValueFactory(new PropertyValueFactory<>("occupancy"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colOccupancyBar.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue()));
        colOccupancyBar.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar(0);

            @Override
            protected void updateItem(ShelterRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                bar.setProgress(row.getOccupancyRatio());
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.getStyleClass().removeAll("status-low", "status-medium", "status-high", "status-critical");
                if (row.getOccupancyRatio() >= 1.0) {
                    bar.getStyleClass().add("status-critical");
                } else if (row.getOccupancyRatio() >= 0.85) {
                    bar.getStyleClass().add("status-high");
                } else if (row.getOccupancyRatio() >= 0.6) {
                    bar.getStyleClass().add("status-medium");
                } else {
                    bar.getStyleClass().add("status-low");
                }
                setGraphic(bar);
            }
        });

        applyRoleRestrictions();
        loadShelters();
    }

    /**
     * Per UI_IMPLEMENTATION_PLAN.md section 1.1: shelter.fxml is shared across
     * SYSTEM_ADMIN (full CRUD), ZONE_COORDINATOR (read-only zone shelters), and
     * ANALYTICS_USER (read-only occupancy view). Only admins get write access.
     */
    private void applyRoleRestrictions() {
        UserRole role = UserSession.getInstance().getRole();
        boolean isAdmin = role == UserRole.SYSTEM_ADMIN;

        if (!isAdmin) {
            formPanel.setDisable(true);
            writeActionsRow1.setVisible(false);
            writeActionsRow1.setManaged(false);
            writeActionsRow2.setVisible(false);
            writeActionsRow2.setManaged(false);
        }

        if (role == UserRole.ZONE_COORDINATOR) {
            pageTitleLabel.setText("Zone Shelters (Read-Only)");
        } else if (role == UserRole.ANALYTICS_USER) {
            pageTitleLabel.setText("Shelter Occupancy Dashboard");
        }
    }

    private void loadShelters() {
        Task<List<ShelterRow>> loadTask = new Task<>() {
            @Override
            protected List<ShelterRow> call() {
                UserRole role = UserSession.getInstance().getRole();
                // TODO: replace with ShelterService.getAllShelters() -> SELECT * FROM shelters,
                // or v_shelter_dashboard for the occupancy-focused ANALYTICS_USER view.
                // TODO: for ZONE_COORDINATOR, filter to shelters within the coordinator's
                // assigned zone (UserSession.getAssignedZoneId()) — RLS on the `shelters`
                // table itself may not apply since shelters aren't zone-scoped 1:1, so this
                // filter likely needs to happen via an explicit WHERE / JOIN in the query.
                return List.of(
                        new ShelterRow("S2001", "Government School Hall, Meppadi", "11.6091, 76.0851",
                                200, 168, "operational"),
                        new ShelterRow("S2002", "Community Hall, Kalpetta", "11.6085, 76.0837",
                                150, 150, "full")
                );
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            shelterTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            errorLabel.setText("Failed to load shelters from database");
            if (loadTask.getException() != null) {
                loadTask.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(loadTask, "shelter-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        try {
            String name = nameField.getText();
            if (name == null || name.isBlank()) {
                errorLabel.setText("Shelter name is required");
                return;
            }

            int capacity;
            try {
                capacity = Integer.parseInt(capacityField.getText());
                if (capacity <= 0) {
                    errorLabel.setText("Capacity must be greater than zero");
                    return;
                }
            } catch (NumberFormatException nfe) {
                errorLabel.setText("Capacity must be a number");
                return;
            }

            String coordinates = formatCoordinates();
            String status = statusCombo.getValue();

            // TODO: call ShelterService.registerShelter(name, latitude, longitude, capacity,
            // status) -> INSERT INTO shelters (name, location, capacity, status) VALUES
            // (?, ROW(?, ?)::coordinates, ?, ?). current_occupancy defaults to 0 and is
            // never set from the client.
            String shelterId = shelterIdField.getText().isBlank()
                    ? "S" + (2000 + dataList.size() + 1)
                    : shelterIdField.getText();

            ShelterRow row = new ShelterRow(shelterId, name, coordinates, capacity, 0, status);
            dataList.add(0, row);
            shelterTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not add shelter");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            String shelterId = shelterIdField.getText();
            if (shelterId == null || shelterId.isBlank()) {
                errorLabel.setText("Invalid shelter ID");
                return;
            }

            // TODO: call ShelterService.updateShelter(shelterId, name, latitude, longitude,
            // capacity, status) -> UPDATE shelters SET name=?, location=ROW(?,?)::coordinates,
            // capacity=?, status=? WHERE shelter_id=?. current_occupancy is intentionally
            // excluded from this statement — it must never be set from the UI.

            errorLabel.setText("");
            loadShelters();
        } catch (Exception e) {
            errorLabel.setText("Could not update shelter");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            String shelterId = shelterIdField.getText();
            if (shelterId == null || shelterId.isBlank()) {
                errorLabel.setText("Invalid shelter ID");
                return;
            }

            // TODO: call ShelterService.deleteShelter(shelterId) -> DELETE FROM shelters
            // WHERE shelter_id = ?. Block/confirm if current_occupancy > 0 or if the shelter
            // is referenced as a zone's primary_shelter_id or has inventory rows.
            dataList.removeIf(s -> s.getShelterId().equals(shelterId));
            shelterTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not delete shelter");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleFind(ActionEvent event) {
        try {
            String shelterId = shelterIdField.getText();
            if (shelterId == null || shelterId.isBlank()) {
                errorLabel.setText("Invalid shelter ID");
                return;
            }

            // TODO: call ShelterService.findById(shelterId) -> SELECT * FROM shelters WHERE shelter_id = ?
            ShelterRow found = dataList.stream()
                    .filter(s -> s.getShelterId().equals(shelterId))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                errorLabel.setText("Shelter not found");
                return;
            }

            searchList.setAll(found);
            shelterTable.setItems(searchList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not find shelter");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        shelterIdField.clear();
        nameField.clear();
        latitudeField.clear();
        longitudeField.clear();
        capacityField.clear();
        currentOccupancyField.clear();
        statusCombo.getSelectionModel().selectFirst();
        errorLabel.setText("");
        shelterTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    private String formatCoordinates() {
        String lat = latitudeField.getText();
        String lon = longitudeField.getText();
        if ((lat == null || lat.isBlank()) && (lon == null || lon.isBlank())) {
            return "—";
        }
        return (lat == null || lat.isBlank() ? "?" : lat) + ", " + (lon == null || lon.isBlank() ? "?" : lon);
    }
}
