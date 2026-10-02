package com.groupa13.disasterrelief.controller;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Severity;
import com.groupa13.disasterrelief.model.core.Zone;
import com.groupa13.disasterrelief.service.core.DisasterService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.core.ShelterService;
import com.groupa13.disasterrelief.service.core.ZoneService;
import com.groupa13.disasterrelief.util.Utility;

import javafx.beans.property.ReadOnlyStringWrapper;
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

public class ZoneController extends Utility {

    private static final Logger LOGGER = Logger.getLogger(ZoneController.class.getName());

    @FXML private TextField zoneIdField;
    @FXML private TextField nameField;
    @FXML private ComboBox<EntityOption> disasterCombo;
    @FXML private ComboBox<Severity> severityCombo;
    @FXML private ComboBox<EntityOption> primaryShelterCombo;
    @FXML private TextField coordinatorContactField;
    @FXML private Label errorLabel;

    @FXML private TableView<Zone> zoneTable;
    @FXML private TableColumn<Zone, String> colZoneId;
    @FXML private TableColumn<Zone, String> colName;
    @FXML private TableColumn<Zone, String> colDisasterId;
    @FXML private TableColumn<Zone, String> colSeverity;
    @FXML private TableColumn<Zone, String> colPrimaryShelter;
    @FXML private TableColumn<Zone, String> colCoordinatorContact;

    private final ObservableList<Zone> dataList = FXCollections.observableArrayList();
    private final ZoneService zoneService;
    private final DisasterService disasterService;
    private final ShelterService shelterService;

    public ZoneController() {
        this(new ZoneService(), new DisasterService(), new ShelterService());
    }

    ZoneController(ZoneService zoneService, DisasterService disasterService,
            ShelterService shelterService) {
        this.zoneService = zoneService;
        this.disasterService = disasterService;
        this.shelterService = shelterService;
    }

    @FXML
    public void initialize() {
        severityCombo.getItems().setAll(Severity.values());
        severityCombo.getSelectionModel().selectFirst();
        colZoneId.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper("Z" + cell.getValue().zoneId()));
        colName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        colDisasterId.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                "D" + cell.getValue().disasterId() + " — " + cell.getValue().disasterName()));
        colSeverity.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().severity().toString()));
        colPrimaryShelter.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().primaryShelterId() == null ? "—"
                        : "S" + cell.getValue().primaryShelterId() + " — "
                                + cell.getValue().primaryShelterName()));
        colCoordinatorContact.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().coordinatorContact() == null
                        ? "—" : cell.getValue().coordinatorContact()));
        zoneTable.setItems(dataList);
        zoneTable.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> populateForm(selected));
        loadDisasterOptions();
        loadShelterOptions();
        loadZones();
    }

    private void loadDisasterOptions() {
        runServiceTask(disasterService::listOpenDisasterOptions,
                options -> disasterCombo.getItems().setAll(options), "zone-disaster-options");
    }

    private void loadShelterOptions() {
        runServiceTask(shelterService::listShelterOptions,
                options -> primaryShelterCombo.getItems().setAll(options),
                "zone-shelter-options");
    }

    private void loadZones() {
        runServiceTask(zoneService::listZones, zones -> {
            dataList.setAll(zones);
            zoneTable.setItems(dataList);
        }, "zone-loader");
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        if (!validName()) {
            return;
        }
        EntityOption disaster = disasterCombo.getValue();
        if (disaster == null) {
            errorLabel.setText("Select a disaster for this zone");
            return;
        }
        EntityOption shelter = primaryShelterCombo.getValue();
        runServiceTask(() -> zoneService.createZone(disaster.id(), nameField.getText(),
                severityCombo.getValue(), shelter == null ? null : shelter.id(),
                coordinatorContactField.getText()), ignored -> {
                    handleClear(null);
                    loadZones();
                }, "zone-create");
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        Integer zoneId = readId(zoneIdField.getText());
        if (zoneId == null || !validName()) {
            return;
        }
        EntityOption shelter = primaryShelterCombo.getValue();
        runServiceTask(() -> zoneService.updateZone(zoneId, nameField.getText(),
                severityCombo.getValue(), shelter == null ? null : shelter.id(),
                coordinatorContactField.getText()), ignored -> loadZones(), "zone-update");
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        Integer zoneId = readId(zoneIdField.getText());
        if (zoneId == null) {
            return;
        }
        runServiceTask(() -> zoneService.deleteZone(zoneId), ignored -> {
            handleClear(null);
            loadZones();
        }, "zone-delete");
    }

    @FXML
    public void handleFind(ActionEvent event) {
        Integer zoneId = readId(zoneIdField.getText());
        if (zoneId == null) {
            return;
        }
        runServiceTask(() -> zoneService.findZone(zoneId), zone -> {
            zoneTable.setItems(FXCollections.observableArrayList(zone));
            populateForm(zone);
        }, "zone-find");
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

    private void populateForm(Zone zone) {
        if (zone == null) {
            return;
        }
        zoneIdField.setText("Z" + zone.zoneId());
        nameField.setText(zone.name());
        severityCombo.setValue(zone.severity());
        coordinatorContactField.setText(zone.coordinatorContact());
        selectOption(disasterCombo, zone.disasterId());
        if (zone.primaryShelterId() == null) {
            primaryShelterCombo.getSelectionModel().clearSelection();
        } else {
            selectOption(primaryShelterCombo, zone.primaryShelterId());
        }
    }

    private static void selectOption(ComboBox<EntityOption> combo, int id) {
        combo.getItems().stream().filter(option -> option.id() == id).findFirst()
                .ifPresent(combo::setValue);
    }

    private Integer readId(String text) {
        try {
            String normalized = text == null ? "" : text.trim();
            if (!normalized.isEmpty() && Character.toUpperCase(normalized.charAt(0)) == 'Z') {
                normalized = normalized.substring(1);
            }
            int id = Integer.parseInt(normalized);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            errorLabel.setText("Invalid zone ID");
            return null;
        }
    }

    private boolean validName() {
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            errorLabel.setText("Zone name is required");
            return false;
        }
        return true;
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
            LOGGER.log(Level.SEVERE, "Zone operation failed", task.getException());
            errorLabel.setText("Zone operation failed");
        });
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
