package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.Volunteer;
import com.groupa13.disasterrelief.model.VolunteerShift;
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
 * Adapted from: Hostel-Management-System AllocationController.java pattern
 * (multi-entity combo loading + CRUD with foreign key lookups)
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

    @FXML
    public void initialize() {
        // Bind table columns to VolunteerShift model properties
        colShiftId.setCellValueFactory(new PropertyValueFactory<>("shiftId"));
        colVolunteerName.setCellValueFactory(new PropertyValueFactory<>("volunteerName"));
        colZoneName.setCellValueFactory(new PropertyValueFactory<>("zoneName"));
        colDutyPeriod.setCellValueFactory(new PropertyValueFactory<>("dutyPeriod"));

        // Load combo data + shift list asynchronously
        // Adapted from Hostel AllocationController pattern (Task<Object[]> for multi-entity load)
        // TODO: Replace with actual DAO/Service call
        /*
        Task<Object[]> loadAllTask = new Task<>() {
            @Override
            protected Object[] call() throws Exception {
                List<VolunteerShift> shifts = shiftService.getAllShifts();
                List<Volunteer> volunteers = volunteerService.getAllVolunteers();
                List<Zone> zones = zoneService.getAllZones();
                return new Object[] { shifts, volunteers, zones };
            }
        };

        loadAllTask.setOnSucceeded(e -> {
            Object[] results = loadAllTask.getValue();
            List<VolunteerShift> shifts = (List<VolunteerShift>) results[0];
            List<Volunteer> volunteers = (List<Volunteer>) results[1];
            // Populate combos with volunteer names/IDs and zone names/IDs
            // ...
            dataList.setAll(shifts);
            shiftTable.setItems(dataList);
        });

        loadAllTask.setOnFailed(e -> {
            Throwable ex = loadAllTask.getException();
            ex.printStackTrace();
            errorLabel.setText("Failed to load shift data from database");
        });

        Thread thread = new Thread(loadAllTask);
        thread.setDaemon(true);
        thread.start();
        */

        shiftTable.setItems(dataList);
    }

    /**
     * Schedule a new volunteer shift.
     * Builds a tsrange from dutyStart and dutyEnd fields: [start, end)
     * DB exclusion constraint will reject if it overlaps an existing shift for the same volunteer.
     */
    @FXML
    public void handleCreate(ActionEvent event) {
        try {
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

            // Construct tsrange: [dutyStart, dutyEnd)
            String dutyPeriod = "[" + dutyStart + ", " + dutyEnd + ")";

            // TODO: Call service layer to create shift
            // VolunteerShift shift = shiftService.createShift(volunteerId, zoneId, dutyPeriod);
            // if (shift != null) {
            //     dataList.add(0, shift);
            //     shiftTable.setItems(dataList);
            // } else {
            //     errorLabel.setText("Shift conflicts with existing schedule (exclusion constraint)");
            // }

            errorLabel.setText("TODO: Service layer integration pending — tsrange: " + dutyPeriod);

        } catch (Exception e) {
            errorLabel.setText("Error scheduling shift");
            e.printStackTrace();
        }
    }

    /**
     * Update an existing shift's duty period or zone.
     */
    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            errorLabel.setText("");
            String shiftIdText = shiftIdField.getText();
            if (shiftIdText == null || shiftIdText.trim().isEmpty()) {
                errorLabel.setText("Shift ID is required for update");
                return;
            }
            int shiftId = Integer.parseInt(shiftIdText);

            String dutyStart = dutyStartField.getText();
            String dutyEnd = dutyEndField.getText();
            String dutyPeriod = null;
            if (!dutyStart.isEmpty() && !dutyEnd.isEmpty()) {
                dutyPeriod = "[" + dutyStart + ", " + dutyEnd + ")";
            }

            // TODO: Call service layer to update shift
            // if (shiftService.updateShift(shiftId, zoneId, dutyPeriod) != null) {
            //     dataList.removeIf(s -> s.getShiftId() == shiftId);
            //     VolunteerShift updated = shiftService.findShiftById(shiftId);
            //     dataList.add(0, updated);
            //     shiftTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Shift ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error updating shift");
            e.printStackTrace();
        }
    }

    /**
     * Cancel/delete a shift.
     */
    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            errorLabel.setText("");
            String shiftIdText = shiftIdField.getText();
            if (shiftIdText == null || shiftIdText.trim().isEmpty()) {
                errorLabel.setText("Shift ID is required for cancellation");
                return;
            }
            int shiftId = Integer.parseInt(shiftIdText);

            // TODO: Call service layer to delete shift
            // if (shiftService.deleteShift(shiftId)) {
            //     dataList.removeIf(s -> s.getShiftId() == shiftId);
            //     shiftTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Shift ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error cancelling shift");
            e.printStackTrace();
        }
    }

    /**
     * Find a shift by its ID.
     */
    @FXML
    public void handleFindById(ActionEvent event) {
        try {
            errorLabel.setText("");
            String shiftIdText = shiftIdField.getText();
            if (shiftIdText == null || shiftIdText.trim().isEmpty()) {
                errorLabel.setText("Enter Shift ID to search");
                shiftTable.setItems(dataList);
                return;
            }
            int shiftId = Integer.parseInt(shiftIdText);

            // TODO: Call service layer
            // VolunteerShift shift = shiftService.findShiftById(shiftId);
            // if (shift != null) {
            //     searchList.clear();
            //     searchList.add(shift);
            //     shiftTable.setItems(searchList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Shift ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error finding shift");
            e.printStackTrace();
        }
    }

    /**
     * Show all shifts for the selected volunteer.
     */
    @FXML
    public void handleFindByVolunteer(ActionEvent event) {
        try {
            errorLabel.setText("");
            String volunteerSelection = volunteerCombo.getValue();
            if (volunteerSelection == null) {
                errorLabel.setText("Select a volunteer first");
                shiftTable.setItems(dataList);
                return;
            }

            // TODO: Extract volunteerId from selection and query
            // List<VolunteerShift> shifts = shiftService.getShiftsByVolunteerId(volunteerId);
            // searchList.clear();
            // searchList.setAll(shifts);
            // shiftTable.setItems(searchList);

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (Exception e) {
            errorLabel.setText("Error finding volunteer shifts");
            e.printStackTrace();
        }
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
}
