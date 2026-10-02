package com.groupa13.disasterrelief.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.model.core.Disaster;
import com.groupa13.disasterrelief.model.core.DisasterStatus;
import com.groupa13.disasterrelief.model.core.Severity;
import com.groupa13.disasterrelief.service.core.DisasterService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.Utility;

import javafx.beans.property.ReadOnlyStringWrapper;
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

public class DisasterController extends Utility {

    private static final Logger LOGGER = Logger.getLogger(DisasterController.class.getName());

    @FXML private TextField disasterIdField;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> typeCombo;
    @FXML private ComboBox<Severity> severityCombo;
    @FXML private DatePicker activePeriodStartPicker;
    @FXML private DatePicker activePeriodEndPicker;
    @FXML private ComboBox<DisasterStatus> statusCombo;
    @FXML private Label errorLabel;

    @FXML private TableView<Disaster> disasterTable;
    @FXML private TableColumn<Disaster, String> colDisasterId;
    @FXML private TableColumn<Disaster, String> colName;
    @FXML private TableColumn<Disaster, String> colType;
    @FXML private TableColumn<Disaster, String> colSeverity;
    @FXML private TableColumn<Disaster, String> colActivePeriod;
    @FXML private TableColumn<Disaster, String> colStatus;

    private final ObservableList<Disaster> dataList = FXCollections.observableArrayList();
    private final DisasterService disasterService;

    public DisasterController() {
        this(new DisasterService());
    }

    DisasterController(DisasterService disasterService) {
        this.disasterService = disasterService;
    }

    @FXML
    public void initialize() {
        typeCombo.getItems().setAll("Flood", "Earthquake", "Cyclone", "Landslide", "Other");
        severityCombo.getItems().setAll(Severity.values());
        statusCombo.getItems().setAll(DisasterStatus.values());
        typeCombo.getSelectionModel().selectFirst();
        severityCombo.getSelectionModel().selectFirst();
        statusCombo.getSelectionModel().selectFirst();

        colDisasterId.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper("D" + cell.getValue().disasterId()));
        colName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        colType.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().type() == null ? "—" : cell.getValue().type()));
        colSeverity.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().severity().toString()));
        colActivePeriod.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().activePeriod() == null
                        ? "—" : cell.getValue().activePeriod().displayValue()));
        colStatus.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().status().toString()));
        disasterTable.setItems(dataList);
        disasterTable.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> populateForm(selected));
        loadDisasters();
    }

    private void loadDisasters() {
        runServiceTask(disasterService::listDisasters, disasters -> {
            dataList.setAll(disasters);
            disasterTable.setItems(dataList);
        }, "disaster-loader");
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        if (!validForm()) {
            return;
        }
        runServiceTask(() -> disasterService.createDisaster(
                nameField.getText(), typeCombo.getValue(), severityCombo.getValue(),
                activePeriodStartPicker.getValue(), activePeriodEndPicker.getValue(),
                statusCombo.getValue()), ignored -> {
                    handleClear(null);
                    loadDisasters();
                }, "disaster-create");
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        Integer disasterId = readId(disasterIdField.getText(), 'D', "Invalid disaster ID");
        if (disasterId == null || !validForm()) {
            return;
        }
        DisasterStatus status = statusCombo.getValue();
        if (status == DisasterStatus.RESOLVED || status == DisasterStatus.CLOSED) {
            Alert confirmation = new Alert(AlertType.CONFIRMATION);
            confirmation.setTitle("Confirm Status Change");
            confirmation.setHeaderText("Mark this disaster as " + status);
            confirmation.setContentText(
                    "Pending requests may be cancelled by the operations trigger. Continue?");
            if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                return;
            }
        }
        runServiceTask(() -> disasterService.updateDisaster(
                disasterId, nameField.getText(), typeCombo.getValue(), severityCombo.getValue(),
                activePeriodStartPicker.getValue(), activePeriodEndPicker.getValue(), status),
                ignored -> loadDisasters(), "disaster-update");
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        Integer disasterId = readId(disasterIdField.getText(), 'D', "Invalid disaster ID");
        if (disasterId == null) {
            return;
        }
        runServiceTask(() -> disasterService.deleteDisaster(disasterId), ignored -> {
            handleClear(null);
            loadDisasters();
        }, "disaster-delete");
    }

    @FXML
    public void handleFind(ActionEvent event) {
        Integer disasterId = readId(disasterIdField.getText(), 'D', "Invalid disaster ID");
        if (disasterId == null) {
            return;
        }
        runServiceTask(() -> disasterService.findDisaster(disasterId), disaster -> {
            disasterTable.setItems(FXCollections.observableArrayList(disaster));
            populateForm(disaster);
        }, "disaster-find");
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

    private boolean validForm() {
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            errorLabel.setText("Disaster name is required");
            return false;
        }
        LocalDate start = activePeriodStartPicker.getValue();
        LocalDate end = activePeriodEndPicker.getValue();
        if (start != null && end != null && !start.isBefore(end)) {
            errorLabel.setText("Active-period end must be after its start");
            return false;
        }
        return true;
    }

    private void populateForm(Disaster disaster) {
        if (disaster == null) {
            return;
        }
        disasterIdField.setText("D" + disaster.disasterId());
        nameField.setText(disaster.name());
        typeCombo.setValue(disaster.type());
        severityCombo.setValue(disaster.severity());
        statusCombo.setValue(disaster.status());
        activePeriodStartPicker.setValue(disaster.activePeriod() == null
                ? null : disaster.activePeriod().startInclusive());
        activePeriodEndPicker.setValue(disaster.activePeriod() == null
                ? null : disaster.activePeriod().endExclusive());
    }

    private Integer readId(String text, char prefix, String message) {
        try {
            String normalized = text == null ? "" : text.trim();
            if (!normalized.isEmpty() && Character.toUpperCase(normalized.charAt(0)) == prefix) {
                normalized = normalized.substring(1);
            }
            int id = Integer.parseInt(normalized);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            errorLabel.setText(message);
            return null;
        }
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
            LOGGER.log(Level.SEVERE, "Disaster operation failed", task.getException());
            errorLabel.setText("Disaster operation failed");
        });
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
