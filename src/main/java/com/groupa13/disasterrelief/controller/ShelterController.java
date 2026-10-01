package com.groupa13.disasterrelief.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.Utility;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
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

    // ─── Inner model ────────────────────────────────────────────────────────────
    // TODO: replace with com.groupa13.disasterrelief.model.Shelter once the model
    // layer exists. Mirrors the `shelters` table:
    //   shelter_id, name, address (VARCHAR — UI display), location (coordinates
    //   composite type — backend spatial), capacity, current_occupancy (trigger-
    //   maintained by trg_auto_update_shelter_occupancy — never write directly),
    //   status.
    public static class ShelterRow {
        private final StringProperty shelterId;
        private final StringProperty name;
        private final StringProperty address;      // human-readable; shown in table
        private final StringProperty coordinates;  // "lat, lon" or "—"; for tooltips only
        private final StringProperty capacity;
        private final StringProperty occupancy;
        private final double occupancyRatio;
        private final StringProperty status;

        public ShelterRow(String shelterId, String name, String address, String coordinates,
                int capacity, int occupancy, String status) {
            this.shelterId   = new SimpleStringProperty(shelterId);
            this.name        = new SimpleStringProperty(name);
            this.address     = new SimpleStringProperty(address);
            this.coordinates = new SimpleStringProperty(coordinates);
            this.capacity    = new SimpleStringProperty(String.valueOf(capacity));
            this.occupancy   = new SimpleStringProperty(String.valueOf(occupancy));
            this.occupancyRatio = capacity <= 0 ? 0 : Math.min(1.0, (double) occupancy / capacity);
            this.status      = new SimpleStringProperty(status);
        }

        public String getShelterId()   { return shelterId.get(); }
        public String getName()        { return name.get(); }
        public String getAddress()     { return address.get(); }
        public String getCoordinates() { return coordinates.get(); }
        public String getCapacity()    { return capacity.get(); }
        public String getOccupancy()   { return occupancy.get(); }
        public double getOccupancyRatio() { return occupancyRatio; }
        public String getStatus()      { return status.get(); }
    }

    // ─── FXML injections ────────────────────────────────────────────────────────
    @FXML private Label    pageTitleLabel;
    @FXML private VBox     formPanel;
    @FXML private HBox     writeActionsRow1;
    @FXML private HBox     writeActionsRow2;

    @FXML private TextField shelterIdField;
    @FXML private TextField nameField;
    @FXML private TextField addressField;       // NEW — human-readable address
    @FXML private TextField latitudeField;
    @FXML private TextField longitudeField;
    @FXML private TextField capacityField;
    @FXML private TextField currentOccupancyField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Button   btnAutoFill;         // NEW — geocoding button
    @FXML private Label    errorLabel;

    @FXML private TableView<ShelterRow>        shelterTable;
    @FXML private TableColumn<ShelterRow, String> colShelterId;
    @FXML private TableColumn<ShelterRow, String> colName;
    @FXML private TableColumn<ShelterRow, String> colAddress;    // renamed from colCoordinates
    @FXML private TableColumn<ShelterRow, String> colCapacity;
    @FXML private TableColumn<ShelterRow, String> colOccupancy;
    @FXML private TableColumn<ShelterRow, ShelterRow> colOccupancyBar;
    @FXML private TableColumn<ShelterRow, String> colStatus;

    private final ObservableList<ShelterRow> dataList   = FXCollections.observableArrayList();
    private final ObservableList<ShelterRow> searchList = FXCollections.observableArrayList();

    // ─── Lifecycle ──────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        statusCombo.getItems().addAll("operational", "full", "closed");
        statusCombo.getSelectionModel().selectFirst();

        // Table column bindings
        colShelterId.setCellValueFactory(new PropertyValueFactory<>("shelterId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address")); // shows address, not raw coords
        colCapacity.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        colOccupancy.setCellValueFactory(new PropertyValueFactory<>("occupancy"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Occupancy progress bar column
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

    // ─── Role restrictions ──────────────────────────────────────────────────────
    /**
     * Per UI_IMPLEMENTATION_PLAN.md §1.1: shelter.fxml is shared across
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
            // Geocoding button is also write-side — hide for read-only roles
            btnAutoFill.setVisible(false);
            btnAutoFill.setManaged(false);
        }

        if (role == UserRole.ZONE_COORDINATOR) {
            pageTitleLabel.setText("Zone Shelters (Read-Only)");
        } else if (role == UserRole.ANALYTICS_USER) {
            pageTitleLabel.setText("Shelter Occupancy Dashboard");
        }
    }

    // ─── Data loading ───────────────────────────────────────────────────────────
    private void loadShelters() {
        Task<List<ShelterRow>> loadTask = new Task<>() {
            @Override
            protected List<ShelterRow> call() {
                // TODO: replace with ShelterService.getAllShelters()
                //   → SELECT shelter_id, name, address,
                //            (location).latitude || ', ' || (location).longitude AS coordinates,
                //            capacity, current_occupancy, status
                //     FROM shelters
                // For ZONE_COORDINATOR, add WHERE shelter_id IN (
                //   SELECT primary_shelter_id FROM zones WHERE zone_id = ?)
                return List.of(
                        new ShelterRow("S2001", "Government School Hall, Meppadi",
                                "Meppadi, Wayanad, Kerala 673577",
                                "11.6091, 76.0851", 200, 168, "operational"),
                        new ShelterRow("S2002", "Community Hall, Kalpetta",
                                "Kalpetta, Wayanad, Kerala 673121",
                                "11.6085, 76.0837", 150, 150, "full")
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

    // ─── Geocoding ──────────────────────────────────────────────────────────────
    /**
     * Calls the free OpenStreetMap Nominatim API to auto-populate latitude and
     * longitude from the address the user has typed.
     *
     * Design decision (UI_IMPLEMENTATION_PLAN.md §9):
     *  - address is ALWAYS entered by the user (the field shown in the table).
     *  - coordinates are OPTIONAL — only needed for spatial proximity queries.
     *  - If offline (common in disaster zones), the button shows a warning and
     *    the user can enter coordinates manually or leave them blank.
     *
     * Nominatim terms of use: must set a User-Agent header and not exceed
     * 1 request/second. This button is user-triggered so rate limits are
     * naturally respected.
     */
    @FXML
    public void handleAutoFillCoordinates() {
        String address = addressField.getText().trim();
        if (address.isEmpty()) {
            errorLabel.setText("Enter an address first, then click Auto-fill.");
            return;
        }

        // Disable button while request is in-flight to prevent double-clicks
        btnAutoFill.setDisable(true);
        btnAutoFill.setText("⏳  Looking up coordinates…");
        errorLabel.setText("");

        Task<double[]> geocodeTask = new Task<>() {
            @Override
            protected double[] call() throws Exception {
                String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
                String url = "https://nominatim.openstreetmap.org/search?q="
                           + encoded + "&format=json&limit=1&addressdetails=0";

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        // Nominatim policy: must identify your application in User-Agent
                        .header("User-Agent", "DRRAS/1.0 (Amrita DBMS Course Project)")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                String body = response.body().trim();

                // Parse the JSON array manually to avoid adding a JSON library dependency.
                // Expected format: [{"lat":"11.6091","lon":"76.0851",...}, ...]
                if (body.equals("[]") || body.isEmpty()) {
                    return null; // address not found
                }
                // Extract first "lat" value
                int latIdx = body.indexOf("\"lat\":\"");
                int lonIdx = body.indexOf("\"lon\":\"");
                if (latIdx == -1 || lonIdx == -1) return null;

                String latStr = body.substring(latIdx + 7, body.indexOf("\"", latIdx + 7));
                String lonStr = body.substring(lonIdx + 7, body.indexOf("\"", lonIdx + 7));
                return new double[]{ Double.parseDouble(latStr), Double.parseDouble(lonStr) };
            }
        };

        geocodeTask.setOnSucceeded(e -> {
            double[] coords = geocodeTask.getValue();
            if (coords == null) {
                errorLabel.setText("Address not found on OpenStreetMap. Check spelling or enter coordinates manually.");
            } else {
                // Round to 6 decimal places (~0.1m precision) for display clarity
                latitudeField.setText(String.format("%.6f", coords[0]));
                longitudeField.setText(String.format("%.6f", coords[1]));
                errorLabel.setText("");
            }
            resetAutoFillButton();
        });

        geocodeTask.setOnFailed(e -> {
            // Graceful degradation — API unreachable (no network in disaster zone)
            errorLabel.setText("Geocoding unavailable (no network). Enter coordinates manually or leave blank.");
            resetAutoFillButton();
        });

        Thread thread = new Thread(geocodeTask, "geocode-request");
        thread.setDaemon(true);
        thread.start();
    }

    private void resetAutoFillButton() {
        Platform.runLater(() -> {
            btnAutoFill.setDisable(false);
            btnAutoFill.setText("📍  Auto-fill coordinates from address");
        });
    }

    // ─── CRUD handlers ──────────────────────────────────────────────────────────
    @FXML
    public void handleAdd(ActionEvent event) {
        try {
            String name = nameField.getText();
            if (name == null || name.isBlank()) {
                errorLabel.setText("Shelter name is required");
                return;
            }

            String address = addressField.getText().trim();
            if (address.isBlank()) {
                errorLabel.setText("Address is required");
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

            // TODO: call ShelterService.registerShelter(name, address, latitude, longitude,
            // capacity, status)
            //   → INSERT INTO shelters (name, address, location, capacity, status)
            //     VALUES (?, ?, ROW(?, ?)::coordinates, ?, ?)
            //   current_occupancy defaults to 0 and is never set from the client.
            String shelterId = shelterIdField.getText().isBlank()
                    ? "S" + (2000 + dataList.size() + 1)
                    : shelterIdField.getText();

            ShelterRow row = new ShelterRow(shelterId, name, address, coordinates, capacity, 0, status);
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

            // TODO: call ShelterService.updateShelter(shelterId, name, address, latitude,
            // longitude, capacity, status)
            //   → UPDATE shelters
            //     SET name=?, address=?, location=ROW(?,?)::coordinates, capacity=?, status=?
            //     WHERE shelter_id=?
            //   current_occupancy is intentionally excluded — it must never be set from the UI.

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

            // TODO: call ShelterService.deleteShelter(shelterId)
            //   → DELETE FROM shelters WHERE shelter_id = ?
            //   Block/confirm if current_occupancy > 0 or if the shelter is referenced as
            //   a zone's primary_shelter_id or has rows in shelter_inventory.
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

            // TODO: call ShelterService.findById(shelterId)
            //   → SELECT * FROM shelters WHERE shelter_id = ?
            ShelterRow found = dataList.stream()
                    .filter(s -> s.getShelterId().equals(shelterId))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                errorLabel.setText("Shelter not found");
                return;
            }

            // Populate form fields from selected row
            nameField.setText(found.getName());
            addressField.setText(found.getAddress());
            String[] parts = found.getCoordinates().split(",");
            if (parts.length == 2) {
                latitudeField.setText(parts[0].trim());
                longitudeField.setText(parts[1].trim());
            }
            capacityField.setText(found.getCapacity());
            currentOccupancyField.setText(found.getOccupancy());
            statusCombo.setValue(found.getStatus());

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
        addressField.clear();
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

    // ─── Helpers ────────────────────────────────────────────────────────────────
    /** Formats coordinates for internal storage in ShelterRow (tooltip use only). */
    private String formatCoordinates() {
        String lat = latitudeField.getText();
        String lon = longitudeField.getText();
        if ((lat == null || lat.isBlank()) && (lon == null || lon.isBlank())) {
            return "—";
        }
        return (lat == null || lat.isBlank() ? "?" : lat.trim())
             + ", "
             + (lon == null || lon.isBlank() ? "?" : lon.trim());
    }
}
