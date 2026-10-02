package com.groupa13.disasterrelief.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.model.core.Shelter;
import com.groupa13.disasterrelief.model.core.ShelterStatus;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.core.ShelterService;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.Utility;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ShelterController extends Utility {

    private static final Logger LOGGER = Logger.getLogger(ShelterController.class.getName());

    @FXML private Label pageTitleLabel;
    @FXML private VBox formPanel;
    @FXML private HBox writeActionsRow1;
    @FXML private HBox writeActionsRow2;
    @FXML private TextField shelterIdField;
    @FXML private TextField nameField;
    @FXML private TextField addressField;
    @FXML private TextField latitudeField;
    @FXML private TextField longitudeField;
    @FXML private TextField capacityField;
    @FXML private TextField currentOccupancyField;
    @FXML private ComboBox<ShelterStatus> statusCombo;
    @FXML private Button btnAutoFill;
    @FXML private Label errorLabel;

    @FXML private TableView<Shelter> shelterTable;
    @FXML private TableColumn<Shelter, String> colShelterId;
    @FXML private TableColumn<Shelter, String> colName;
    @FXML private TableColumn<Shelter, String> colAddress;
    @FXML private TableColumn<Shelter, String> colCapacity;
    @FXML private TableColumn<Shelter, String> colOccupancy;
    @FXML private TableColumn<Shelter, Shelter> colOccupancyBar;
    @FXML private TableColumn<Shelter, String> colStatus;

    private final ObservableList<Shelter> dataList = FXCollections.observableArrayList();
    private final ShelterService shelterService;
    private final HttpClient httpClient;

    public ShelterController() {
        this(new ShelterService(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8)).build());
    }

    ShelterController(ShelterService shelterService, HttpClient httpClient) {
        this.shelterService = shelterService;
        this.httpClient = httpClient;
    }

    @FXML
    public void initialize() {
        statusCombo.getItems().setAll(ShelterStatus.values());
        statusCombo.getSelectionModel().selectFirst();
        colShelterId.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper("S" + cell.getValue().shelterId()));
        colName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        colAddress.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().address()));
        colCapacity.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(Integer.toString(cell.getValue().capacity())));
        colOccupancy.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Integer.toString(cell.getValue().currentOccupancy())));
        colStatus.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().status().toString()));
        colOccupancyBar.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue()));
        colOccupancyBar.setCellFactory(ignored -> occupancyCell());
        shelterTable.setItems(dataList);
        shelterTable.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> populateForm(selected));
        applyRoleRestrictions();
        loadShelters();
    }

    private TableCell<Shelter, Shelter> occupancyCell() {
        return new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();

            @Override
            protected void updateItem(Shelter shelter, boolean empty) {
                super.updateItem(shelter, empty);
                if (empty || shelter == null) {
                    setGraphic(null);
                    return;
                }
                double ratio = shelter.occupancyRatio();
                bar.setProgress(ratio);
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.getStyleClass().removeAll(
                        "status-low", "status-medium", "status-high", "status-critical");
                bar.getStyleClass().add(ratio >= 1 ? "status-critical"
                        : ratio >= .85 ? "status-high" : ratio >= .6 ? "status-medium"
                                : "status-low");
                setGraphic(bar);
            }
        };
    }

    private void applyRoleRestrictions() {
        UserRole role = UserSession.getInstance().getRole();
        boolean administrator = role == UserRole.SYSTEM_ADMIN;
        if (!administrator) {
            formPanel.setDisable(true);
            writeActionsRow1.setVisible(false);
            writeActionsRow1.setManaged(false);
            writeActionsRow2.setVisible(false);
            writeActionsRow2.setManaged(false);
            btnAutoFill.setVisible(false);
            btnAutoFill.setManaged(false);
        }
        if (role == UserRole.ZONE_COORDINATOR) {
            pageTitleLabel.setText("Zone Shelters (Read-Only)");
        } else if (role == UserRole.ANALYTICS_USER) {
            pageTitleLabel.setText("Shelter Occupancy Dashboard");
        }
    }

    private void loadShelters() {
        runServiceTask(shelterService::listShelters, shelters -> {
            dataList.setAll(shelters);
            shelterTable.setItems(dataList);
        }, "shelter-loader");
    }

    @FXML
    public void handleAutoFillCoordinates() {
        String address = addressField.getText() == null ? "" : addressField.getText().trim();
        if (address.isEmpty()) {
            errorLabel.setText("Enter an address first, then click Auto-fill");
            return;
        }
        btnAutoFill.setDisable(true);
        btnAutoFill.setText("Looking up coordinates…");
        String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://nominatim.openstreetmap.org/search?q=" + encoded
                        + "&format=json&limit=1&addressdetails=0"))
                .header("User-Agent", "DRRAS/1.0 (Amrita DBMS Course Project)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> parseCoordinates(response.body()))
                .whenComplete((coordinates, failure) -> Platform.runLater(() -> {
                    if (failure != null) {
                        LOGGER.log(Level.WARNING, "Geocoding failed", failure);
                        errorLabel.setText(
                                "Geocoding unavailable. Enter coordinates manually or leave them blank");
                    } else if (coordinates == null) {
                        errorLabel.setText("Address not found. Check it or enter coordinates manually");
                    } else {
                        latitudeField.setText(String.format(Locale.ROOT, "%.6f", coordinates[0]));
                        longitudeField.setText(String.format(Locale.ROOT, "%.6f", coordinates[1]));
                        errorLabel.setText("");
                    }
                    btnAutoFill.setDisable(false);
                    btnAutoFill.setText("📍  Auto-fill coordinates from address");
                }));
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        ParsedShelter form = parseShelterForm();
        if (form == null) {
            return;
        }
        runServiceTask(() -> shelterService.createShelter(nameField.getText(),
                addressField.getText(), form.latitude(), form.longitude(), form.capacity(),
                statusCombo.getValue()), ignored -> {
                    handleClear(null);
                    loadShelters();
                }, "shelter-create");
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        Integer shelterId = readId(shelterIdField.getText());
        ParsedShelter form = parseShelterForm();
        if (shelterId == null || form == null) {
            return;
        }
        runServiceTask(() -> shelterService.updateShelter(shelterId, nameField.getText(),
                addressField.getText(), form.latitude(), form.longitude(), form.capacity(),
                statusCombo.getValue()), ignored -> loadShelters(), "shelter-update");
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        Integer shelterId = readId(shelterIdField.getText());
        if (shelterId == null) {
            return;
        }
        runServiceTask(() -> shelterService.deleteShelter(shelterId), ignored -> {
            handleClear(null);
            loadShelters();
        }, "shelter-delete");
    }

    @FXML
    public void handleFind(ActionEvent event) {
        Integer shelterId = readId(shelterIdField.getText());
        if (shelterId == null) {
            return;
        }
        runServiceTask(() -> shelterService.findShelter(shelterId), shelter -> {
            shelterTable.setItems(FXCollections.observableArrayList(shelter));
            populateForm(shelter);
        }, "shelter-find");
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

    private ParsedShelter parseShelterForm() {
        try {
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                throw new IllegalArgumentException("Shelter name is required");
            }
            if (addressField.getText() == null || addressField.getText().isBlank()) {
                throw new IllegalArgumentException("Address is required");
            }
            int capacity = Integer.parseInt(capacityField.getText().trim());
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be greater than zero");
            }
            String latitudeText = latitudeField.getText() == null
                    ? "" : latitudeField.getText().trim();
            String longitudeText = longitudeField.getText() == null
                    ? "" : longitudeField.getText().trim();
            if (latitudeText.isEmpty() != longitudeText.isEmpty()) {
                throw new IllegalArgumentException("Enter both latitude and longitude");
            }
            Double latitude = latitudeText.isEmpty() ? null : Double.valueOf(latitudeText);
            Double longitude = longitudeText.isEmpty() ? null : Double.valueOf(longitudeText);
            if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)) {
                throw new IllegalArgumentException("Latitude must be between -90 and 90");
            }
            if (longitude != null
                    && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
                throw new IllegalArgumentException("Longitude must be between -180 and 180");
            }
            return new ParsedShelter(latitude, longitude, capacity);
        } catch (NumberFormatException exception) {
            errorLabel.setText("Capacity and coordinates must be valid numbers");
            return null;
        } catch (IllegalArgumentException exception) {
            errorLabel.setText(exception.getMessage());
            return null;
        }
    }

    private void populateForm(Shelter shelter) {
        if (shelter == null) {
            return;
        }
        shelterIdField.setText("S" + shelter.shelterId());
        nameField.setText(shelter.name());
        addressField.setText(shelter.address());
        latitudeField.setText(shelter.location() == null
                ? "" : Double.toString(shelter.location().latitude()));
        longitudeField.setText(shelter.location() == null
                ? "" : Double.toString(shelter.location().longitude()));
        capacityField.setText(Integer.toString(shelter.capacity()));
        currentOccupancyField.setText(Integer.toString(shelter.currentOccupancy()));
        statusCombo.setValue(shelter.status());
    }

    private Integer readId(String text) {
        try {
            String normalized = text == null ? "" : text.trim();
            if (!normalized.isEmpty() && Character.toUpperCase(normalized.charAt(0)) == 'S') {
                normalized = normalized.substring(1);
            }
            int id = Integer.parseInt(normalized);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            errorLabel.setText("Invalid shelter ID");
            return null;
        }
    }

    private static double[] parseCoordinates(String body) {
        if (body == null || body.isBlank() || body.strip().equals("[]")) {
            return null;
        }
        int latitudeStart = body.indexOf("\"lat\":\"");
        int longitudeStart = body.indexOf("\"lon\":\"");
        if (latitudeStart < 0 || longitudeStart < 0) {
            return null;
        }
        latitudeStart += 7;
        longitudeStart += 7;
        int latitudeEnd = body.indexOf('"', latitudeStart);
        int longitudeEnd = body.indexOf('"', longitudeStart);
        return new double[] {
                Double.parseDouble(body.substring(latitudeStart, latitudeEnd)),
                Double.parseDouble(body.substring(longitudeStart, longitudeEnd))
        };
    }

    private <T> void runServiceTask(Supplier<ServiceResult<T>> call, Consumer<T> onSuccess,
            String threadName) {
        Task<ServiceResult<T>> task = new Task<>() {
            @Override
            protected ServiceResult<T> call() {
                return call.get();
            }
        };
        task.setOnSucceeded(ignored -> {
            ServiceResult<T> result = task.getValue();
            errorLabel.setText(result.success() ? "" : result.message());
            if (result.success()) {
                onSuccess.accept(result.value());
            }
        });
        task.setOnFailed(ignored -> {
            LOGGER.log(Level.SEVERE, "Shelter operation failed", task.getException());
            errorLabel.setText("Shelter operation failed");
        });
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private record ParsedShelter(Double latitude, Double longitude, int capacity) {
    }
}
