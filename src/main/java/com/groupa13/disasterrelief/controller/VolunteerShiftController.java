package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.VolunteerShift;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.people.VolunteerShiftService;
import com.groupa13.disasterrelief.util.Utility;

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

/**
 * Controller for volunteer_shift.fxml — Conflict-Free Shift Scheduling.
 * Target Roles: VOLUNTEER_COORDINATOR, SYSTEM_ADMIN
 *
 * Features:
 * - Schedule volunteer shifts with tsrange duty periods
 * - DB-level EXCLUDE USING gist constraint prevents overlapping shifts for same volunteer
 * - View shifts by volunteer or by zone
 *
 * All database work happens in {@link VolunteerShiftService} called from JavaFX
 * {@link Task}s on background threads. Overlap detection is delegated entirely to
 * PostgreSQL's exclusion constraint (SQLState 23P01), which the service maps to a
 * user-friendly message.
 */
public class VolunteerShiftController extends Utility {

    // --- Form fields ---
    @FXML private TextField shiftIdField;
    @FXML private ComboBox<String> volunteerCombo;
    @FXML private ComboBox<String> zoneCombo;
    @FXML private TextField dutyStartField;
    @FXML private TextField dutyEndField;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

    // --- Table ---
    @FXML private TableView<VolunteerShift> shiftTable;
    @FXML private TableColumn<VolunteerShift, Integer> colShiftId;
    @FXML private TableColumn<VolunteerShift, String> colVolunteerName;
    @FXML private TableColumn<VolunteerShift, String> colZoneName;
    @FXML private TableColumn<VolunteerShift, String> colDutyPeriod;

    private ObservableList<VolunteerShift> dataList = FXCollections.observableArrayList();
    private ObservableList<VolunteerShift> searchList = FXCollections.observableArrayList();

    private final VolunteerShiftService shiftService = new VolunteerShiftService();

    @FXML
    public void initialize() {
        // Bind table columns to VolunteerShift model properties
        colShiftId.setCellValueFactory(new PropertyValueFactory<>("shiftId"));
        colVolunteerName.setCellValueFactory(new PropertyValueFactory<>("volunteerName"));
        colZoneName.setCellValueFactory(new PropertyValueFactory<>("zoneName"));
        colDutyPeriod.setCellValueFactory(new PropertyValueFactory<>("dutyPeriod"));

        shiftTable.setItems(dataList);

        // Load volunteer + zone combos and the shift list asynchronously
        Task<List<String>> loadVolunteersTask = new Task<>() {
            @Override
            protected List<String> call() {
                return shiftService.getAllVolunteersForCombo();
            }
        };
        loadVolunteersTask.setOnSucceeded(e ->
                volunteerCombo.getItems().setAll(loadVolunteersTask.getValue()));
        loadVolunteersTask.setOnFailed(e ->
                errorLabel.setText("Failed to load volunteers from database"));

        Task<List<String>> loadZonesTask = new Task<>() {
            @Override
            protected List<String> call() {
                return shiftService.getAllZonesForCombo();
            }
        };
        loadZonesTask.setOnSucceeded(e -> zoneCombo.getItems().setAll(loadZonesTask.getValue()));
        loadZonesTask.setOnFailed(e ->
                errorLabel.setText("Failed to load zones from database"));

        Task<ServiceResult<List<VolunteerShift>>> loadShiftsTask = new Task<>() {
            @Override
            protected ServiceResult<List<VolunteerShift>> call() {
                return shiftService.getAllShifts();
            }
        };
        loadShiftsTask.setOnSucceeded(e -> {
            ServiceResult<List<VolunteerShift>> result = loadShiftsTask.getValue();
            if (result.success()) {
                dataList.setAll(result.value());
                shiftTable.setItems(dataList);
            } else {
                errorLabel.setText(result.message());
            }
        });
        loadShiftsTask.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            errorLabel.setText("Failed to load shift data from database");
        });

        new Thread(loadVolunteersTask) {{ setDaemon(true); }}.start();
        new Thread(loadZonesTask) {{ setDaemon(true); }}.start();
        new Thread(loadShiftsTask) {{ setDaemon(true); }}.start();
    }

    /**
     * Schedule a new volunteer shift.
     * Builds a tsrange from dutyStart and dutyEnd fields: [start, end)
     * DB exclusion constraint will reject if it overlaps an existing shift for the same volunteer.
     */
    @FXML
    public void handleCreate(ActionEvent event) {
        errorLabel.setText("");
        String volunteerSelection = volunteerCombo.getValue();
        String zoneSelection = zoneCombo.getValue();
        String dutyStart = dutyStartField.getText();
        String dutyEnd = dutyEndField.getText();

        if (volunteerSelection == null || zoneSelection == null) {
            errorLabel.setText("Select both a volunteer and a zone");
            return;
        }
        if (dutyStart == null || dutyStart.isEmpty() || dutyEnd == null || dutyEnd.isEmpty()) {
            errorLabel.setText("Both duty start and end times are required");
            return;
        }

        Integer volunteerId = parseId(volunteerSelection);
        Integer zoneId = parseId(zoneSelection);
        if (volunteerId == null || zoneId == null) {
            errorLabel.setText("Select both a volunteer and a zone");
            return;
        }

        // Construct tsrange: [dutyStart, dutyEnd)
        String dutyPeriod = "[" + dutyStart.trim() + ", " + dutyEnd.trim() + ")";

        Task<ServiceResult<VolunteerShift>> task = new Task<>() {
            @Override
            protected ServiceResult<VolunteerShift> call() {
                return shiftService.createShift(volunteerId, zoneId, dutyPeriod);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<VolunteerShift> result = task.getValue();
            if (result.success()) {
                dataList.add(0, result.value());
                shiftTable.setItems(dataList);
                handleClear(null);
            } else {
                // e.g. "Volunteer already has an overlapping shift for this time period"
                errorLabel.setText(result.message());
            }
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            errorLabel.setText("Unexpected error: " + task.getException().getMessage());
        });
        new Thread(task) {{ setDaemon(true); }}.start();
    }

    /**
     * Update an existing shift's duty period or zone.
     */
    @FXML
    public void handleUpdate(ActionEvent event) {
        errorLabel.setText("");
        String shiftIdText = shiftIdField.getText();
        if (shiftIdText == null || shiftIdText.trim().isEmpty()) {
            errorLabel.setText("Shift ID is required for update");
            return;
        }

        int shiftId;
        try {
            shiftId = Integer.parseInt(shiftIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Shift ID must be a number");
            return;
        }

        String dutyStart = dutyStartField.getText();
        String dutyEnd = dutyEndField.getText();
        String dutyPeriodValue = null;
        if (dutyStart != null && dutyEnd != null && !dutyStart.isEmpty() && !dutyEnd.isEmpty()) {
            dutyPeriodValue = "[" + dutyStart.trim() + ", " + dutyEnd.trim() + ")";
        }
        final String dutyPeriod = dutyPeriodValue;
        Integer zoneId = parseId(zoneCombo.getValue());

        Task<ServiceResult<VolunteerShift>> task = new Task<>() {
            @Override
            protected ServiceResult<VolunteerShift> call() {
                return shiftService.updateShift(shiftId, zoneId, dutyPeriod);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<VolunteerShift> result = task.getValue();
            if (result.success()) {
                dataList.removeIf(s -> s.getShiftId() == shiftId);
                dataList.add(0, result.value());
                shiftTable.setItems(dataList);
                handleClear(null);
            } else {
                errorLabel.setText(result.message());
            }
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            errorLabel.setText("Unexpected error: " + task.getException().getMessage());
        });
        new Thread(task) {{ setDaemon(true); }}.start();
    }

    /**
     * Cancel/delete a shift.
     */
    @FXML
    public void handleDelete(ActionEvent event) {
        errorLabel.setText("");
        String shiftIdText = shiftIdField.getText();
        if (shiftIdText == null || shiftIdText.trim().isEmpty()) {
            errorLabel.setText("Shift ID is required for cancellation");
            return;
        }

        int shiftId;
        try {
            shiftId = Integer.parseInt(shiftIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Shift ID must be a number");
            return;
        }

        Task<ServiceResult<Void>> task = new Task<>() {
            @Override
            protected ServiceResult<Void> call() {
                return shiftService.deleteShift(shiftId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<Void> result = task.getValue();
            if (result.success()) {
                dataList.removeIf(s -> s.getShiftId() == shiftId);
                shiftTable.setItems(dataList);
                handleClear(null);
            } else {
                errorLabel.setText(result.message());
            }
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            errorLabel.setText("Unexpected error: " + task.getException().getMessage());
        });
        new Thread(task) {{ setDaemon(true); }}.start();
    }

    /**
     * Find a shift by its ID.
     */
    @FXML
    public void handleFindById(ActionEvent event) {
        errorLabel.setText("");
        String shiftIdText = shiftIdField.getText();
        if (shiftIdText == null || shiftIdText.trim().isEmpty()) {
            errorLabel.setText("Enter Shift ID to search");
            shiftTable.setItems(dataList);
            return;
        }

        int shiftId;
        try {
            shiftId = Integer.parseInt(shiftIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Shift ID must be a number");
            return;
        }

        Task<ServiceResult<VolunteerShift>> task = new Task<>() {
            @Override
            protected ServiceResult<VolunteerShift> call() {
                return shiftService.findById(shiftId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<VolunteerShift> result = task.getValue();
            if (result.success()) {
                searchList.clear();
                searchList.add(result.value());
                shiftTable.setItems(searchList);
            } else {
                errorLabel.setText(result.message());
            }
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            errorLabel.setText("Unexpected error: " + task.getException().getMessage());
        });
        new Thread(task) {{ setDaemon(true); }}.start();
    }

    /**
     * Show all shifts for the selected volunteer.
     */
    @FXML
    public void handleFindByVolunteer(ActionEvent event) {
        errorLabel.setText("");
        String volunteerSelection = volunteerCombo.getValue();
        if (volunteerSelection == null) {
            errorLabel.setText("Select a volunteer first");
            shiftTable.setItems(dataList);
            return;
        }
        Integer volunteerId = parseId(volunteerSelection);
        if (volunteerId == null) {
            errorLabel.setText("Select a volunteer first");
            shiftTable.setItems(dataList);
            return;
        }

        Task<ServiceResult<List<VolunteerShift>>> task = new Task<>() {
            @Override
            protected ServiceResult<List<VolunteerShift>> call() {
                return shiftService.getShiftsByVolunteerId(volunteerId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<List<VolunteerShift>> result = task.getValue();
            if (result.success()) {
                searchList.clear();
                searchList.setAll(result.value());
                shiftTable.setItems(searchList);
            } else {
                errorLabel.setText(result.message());
            }
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            errorLabel.setText("Unexpected error: " + task.getException().getMessage());
        });
        new Thread(task) {{ setDaemon(true); }}.start();
    }

    /**
     * Clear all form fields.
     */
    @FXML
    public void handleClear(ActionEvent event) {
        shiftIdField.clear();
        dutyStartField.clear();
        dutyEndField.clear();
        if (volunteerCombo.getItems() != null && !volunteerCombo.getItems().isEmpty()) {
            volunteerCombo.getSelectionModel().clearSelection();
        }
        if (zoneCombo.getItems() != null && !zoneCombo.getItems().isEmpty()) {
            zoneCombo.getSelectionModel().clearSelection();
        }
        errorLabel.setText("");
        shiftTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    /**
     * Extracts the leading integer ID from a ComboBox item formatted as "3 — Wayanad North".
     */
    private Integer parseId(String selection) {
        if (selection == null || selection.isBlank()) return null;
        try { return Integer.parseInt(selection.split(" — ")[0].trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
