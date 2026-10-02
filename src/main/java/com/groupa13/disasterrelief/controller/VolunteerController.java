package com.groupa13.disasterrelief.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.groupa13.disasterrelief.model.Volunteer;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.people.VolunteerService;
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
 * Controller for volunteer.fxml — Volunteer Directory & Skill Matching.
 * Target Roles: VOLUNTEER_COORDINATOR, SYSTEM_ADMIN
 *
 * Features:
 * - CRUD operations on volunteers table (inherits people)
 * - Skill-based search using PostgreSQL TEXT[] array contains operator (@>)
 * - Zone assignment via dropdown
 *
 * All database work happens in {@link VolunteerService} called from JavaFX
 * {@link Task}s on background threads. This controller contains no SQL.
 */
public class VolunteerController extends Utility {

    // --- Form fields ---
    @FXML private TextField personIdField;
    @FXML private TextField nameField;
    @FXML private TextField contactField;
    @FXML private TextField emailField;
    @FXML private TextField skillsField;
    @FXML private TextField availabilityField;
    @FXML private ComboBox<String> zoneCombo;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

    // --- Table ---
    @FXML private TableView<Volunteer> volunteerTable;
    @FXML private TableColumn<Volunteer, Integer> colPersonId;
    @FXML private TableColumn<Volunteer, String> colName;
    @FXML private TableColumn<Volunteer, String> colContact;
    @FXML private TableColumn<Volunteer, String> colEmail;
    @FXML private TableColumn<Volunteer, String> colSkills;
    @FXML private TableColumn<Volunteer, String> colAvailability;
    @FXML private TableColumn<Volunteer, String> colZone;

    private ObservableList<Volunteer> dataList = FXCollections.observableArrayList();
    private ObservableList<Volunteer> searchList = FXCollections.observableArrayList();

    private final VolunteerService volunteerService = new VolunteerService();

    /** Tracks ComboBox selections as "zone_id — zone_name" → zone_id. */
    private final Map<String, Integer> zoneNameToId = new HashMap<>();

    @FXML
    public void initialize() {
        // Bind table columns to Volunteer model properties
        colPersonId.setCellValueFactory(new PropertyValueFactory<>("personId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contact"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        // skills is List<String>; skillsDisplay renders it comma-joined for display
        colSkills.setCellValueFactory(new PropertyValueFactory<>("skillsDisplay"));
        colAvailability.setCellValueFactory(new PropertyValueFactory<>("availability"));
        colZone.setCellValueFactory(new PropertyValueFactory<>("zoneName"));

        volunteerTable.setItems(dataList);

        // Load zone list for the ComboBox asynchronously
        Task<List<String>> loadZonesTask = new Task<>() {
            @Override
            protected List<String> call() {
                return volunteerService.getAllZonesForCombo();
            }
        };
        loadZonesTask.setOnSucceeded(e -> {
            List<String> zones = loadZonesTask.getValue();
            zoneCombo.getItems().setAll(zones);
            zoneNameToId.clear();
            for (String item : zones) {
                zoneNameToId.put(item, parseZoneId(item));
            }
        });
        loadZonesTask.setOnFailed(e ->
                errorLabel.setText("Failed to load zones from database"));

        // Load all volunteers asynchronously
        Task<ServiceResult<List<Volunteer>>> loadVolunteersTask = new Task<>() {
            @Override
            protected ServiceResult<List<Volunteer>> call() {
                return volunteerService.getAllVolunteers();
            }
        };
        loadVolunteersTask.setOnSucceeded(e -> {
            ServiceResult<List<Volunteer>> result = loadVolunteersTask.getValue();
            if (result.success()) {
                dataList.setAll(result.value());
                volunteerTable.setItems(dataList);
            } else {
                errorLabel.setText(result.message());
            }
        });
        loadVolunteersTask.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            errorLabel.setText("Failed to load volunteers from database");
        });

        new Thread(loadZonesTask) {{ setDaemon(true); }}.start();
        new Thread(loadVolunteersTask) {{ setDaemon(true); }}.start();
    }

    /**
     * Register a new volunteer.
     * Inserts into volunteers table (which inherits from people).
     */
    @FXML
    public void handleAdd(ActionEvent event) {
        errorLabel.setText("");
        String name = nameField.getText();
        String contact = contactField.getText();
        String email = emailField.getText();

        List<String> skills = skillsField.getText() == null || skillsField.getText().isBlank()
            ? List.of()
            : Arrays.stream(skillsField.getText().split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList();
        String availability = availabilityField.getText() == null || availabilityField.getText().isBlank()
            ? null : availabilityField.getText().trim();
        Integer zoneId = parseZoneId(zoneCombo.getValue());

        Task<ServiceResult<Volunteer>> task = new Task<>() {
            @Override
            protected ServiceResult<Volunteer> call() {
                return volunteerService.register(name, contact, email, skills, availability, zoneId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<Volunteer> result = task.getValue();
            if (result.success()) {
                dataList.add(0, result.value());
                volunteerTable.setItems(dataList);
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
     * Update an existing volunteer's details.
     * Blank fields mean "keep existing value" (COALESCE in the DAO).
     */
    @FXML
    public void handleUpdate(ActionEvent event) {
        errorLabel.setText("");
        String personIdText = personIdField.getText();
        if (personIdText == null || personIdText.trim().isEmpty()) {
            errorLabel.setText("Volunteer ID is required for update");
            volunteerTable.setItems(dataList);
            return;
        }

        int personId;
        try {
            personId = Integer.parseInt(personIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Volunteer ID must be a number");
            return;
        }

        String name = nameField.getText().isEmpty() ? null : nameField.getText();
        String contact = contactField.getText().isEmpty() ? null : contactField.getText();
        String email = emailField.getText().isEmpty() ? null : emailField.getText();
        List<String> skills = skillsField.getText().isEmpty() ? null
            : Arrays.stream(skillsField.getText().split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList();
        String availability = availabilityField.getText().isEmpty() ? null : availabilityField.getText().trim();
        Integer zoneId = parseZoneId(zoneCombo.getValue());

        Task<ServiceResult<Volunteer>> task = new Task<>() {
            @Override
            protected ServiceResult<Volunteer> call() {
                return volunteerService.update(personId, name, contact, email, skills,
                                               availability, zoneId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<Volunteer> result = task.getValue();
            if (result.success()) {
                dataList.removeIf(v -> v.getPersonId() == personId);
                dataList.add(0, result.value());
                volunteerTable.setItems(dataList);
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
     * Remove a volunteer from the system.
     */
    @FXML
    public void handleDelete(ActionEvent event) {
        errorLabel.setText("");
        String personIdText = personIdField.getText();
        if (personIdText == null || personIdText.trim().isEmpty()) {
            errorLabel.setText("Volunteer ID is required for deletion");
            volunteerTable.setItems(dataList);
            return;
        }

        int personId;
        try {
            personId = Integer.parseInt(personIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Volunteer ID must be a number");
            return;
        }

        Task<ServiceResult<Void>> deleteTask = new Task<>() {
            @Override
            protected ServiceResult<Void> call() {
                return volunteerService.delete(personId);
            }
        };
        deleteTask.setOnSucceeded(e -> {
            ServiceResult<Void> result = deleteTask.getValue();
            if (result.success()) {
                dataList.removeIf(v -> v.getPersonId() == personId);
                volunteerTable.setItems(dataList);
                handleClear(null);
            } else {
                errorLabel.setText(result.message());
            }
        });
        deleteTask.setOnFailed(e -> {
            deleteTask.getException().printStackTrace();
            errorLabel.setText("Unexpected error: " + deleteTask.getException().getMessage());
        });
        new Thread(deleteTask) {{ setDaemon(true); }}.start();
    }

    /**
     * Find a volunteer by ID.
     */
    @FXML
    public void handleFind(ActionEvent event) {
        errorLabel.setText("");
        String personIdText = personIdField.getText();
        if (personIdText == null || personIdText.trim().isEmpty()) {
            errorLabel.setText("Enter Volunteer ID to search");
            volunteerTable.setItems(dataList);
            return;
        }

        int personId;
        try {
            personId = Integer.parseInt(personIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Volunteer ID must be a number");
            return;
        }

        Task<ServiceResult<Volunteer>> task = new Task<>() {
            @Override
            protected ServiceResult<Volunteer> call() {
                return volunteerService.findById(personId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<Volunteer> result = task.getValue();
            if (result.success()) {
                searchList.clear();
                searchList.add(result.value());
                volunteerTable.setItems(searchList);
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
     * Search volunteers by skill using PostgreSQL TEXT[] array contains operator (@>).
     * Queries: SELECT * FROM volunteers WHERE skills @> ARRAY['first_aid']::TEXT[]
     */
    @FXML
    public void handleSearchBySkill(ActionEvent event) {
        errorLabel.setText("");
        String skillQuery = skillsField.getText();
        if (skillQuery == null || skillQuery.trim().isEmpty()) {
            errorLabel.setText("Enter skill(s) in the Skills field to search");
            volunteerTable.setItems(dataList);
            return;
        }

        Task<ServiceResult<List<Volunteer>>> task = new Task<>() {
            @Override
            protected ServiceResult<List<Volunteer>> call() {
                return volunteerService.findBySkills(skillQuery);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<List<Volunteer>> result = task.getValue();
            if (result.success()) {
                searchList.clear();
                searchList.setAll(result.value());
                volunteerTable.setItems(searchList);
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
        personIdField.clear();
        nameField.clear();
        contactField.clear();
        emailField.clear();
        skillsField.clear();
        availabilityField.clear();
        if (zoneCombo.getItems() != null && !zoneCombo.getItems().isEmpty()) {
            zoneCombo.getSelectionModel().clearSelection();
        }
        errorLabel.setText("");
        volunteerTable.setItems(dataList);
    }

    /**
     * Navigate back to the dashboard.
     */
    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    /**
     * Extracts integer zone_id from a ComboBox item formatted as "3 — Wayanad North".
     */
    private Integer parseZoneId(String selection) {
        if (selection == null || selection.isBlank()) return null;
        try { return Integer.parseInt(selection.split(" — ")[0].trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
