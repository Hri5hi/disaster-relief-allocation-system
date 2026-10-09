package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.AffectedPerson;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.people.AffectedPersonService;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller for affected_person.fxml — Displaced Person Registration.
 * Target Roles: ZONE_COORDINATOR, SYSTEM_ADMIN
 *
 * Features:
 * - CRUD on affected_persons table (inherits people)
 * - JSONB medical_needs field with free-form JSON input
 * - Zone Coordinators: pre-filtered to zone shelters via UserSession.assignedZoneId
 * - Shelter assignment via dropdown
 *
 * All database work happens in {@link AffectedPersonService} called from JavaFX
 * {@link Task}s on background threads. Shelter occupancy is never written here —
 * the PostgreSQL trigger trg_auto_update_shelter_occupancy owns it.
 */
public class AffectedPersonController extends Utility {

    // --- Form fields ---
    @FXML private TextField personIdField;
    @FXML private TextField nameField;
    @FXML private TextField contactField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> shelterCombo;
    @FXML private TextArea medicalNeedsArea;
    @FXML private Label errorLabel;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Label roleBadge;

    // --- Table ---
    @FXML private TableView<AffectedPerson> personTable;
    @FXML private TableColumn<AffectedPerson, Integer> colPersonId;
    @FXML private TableColumn<AffectedPerson, String> colName;
    @FXML private TableColumn<AffectedPerson, String> colContact;
    @FXML private TableColumn<AffectedPerson, String> colEmail;
    @FXML private TableColumn<AffectedPerson, String> colShelter;
    @FXML private TableColumn<AffectedPerson, String> colMedicalNeeds;
    @FXML private TableColumn<AffectedPerson, String> colRegisteredAt;

    private ObservableList<AffectedPerson> dataList = FXCollections.observableArrayList();
    private ObservableList<AffectedPerson> searchList = FXCollections.observableArrayList();

    private final AffectedPersonService affectedPersonService = new AffectedPersonService();

    /** Non-null only for ZONE_COORDINATOR sessions; scopes all service calls. */
    private Integer currentZoneId = null;

    @FXML
    public void initialize() {
        // Bind table columns
        colPersonId.setCellValueFactory(new PropertyValueFactory<>("personId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contact"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colShelter.setCellValueFactory(new PropertyValueFactory<>("shelterName"));
        colMedicalNeeds.setCellValueFactory(new PropertyValueFactory<>("medicalNeeds"));
        colRegisteredAt.setCellValueFactory(new PropertyValueFactory<>("registeredAt"));

        personTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        personTable.setItems(dataList);

        // Zone Coordinator context filtering: only this coordinator's zone.
        UserSession session = UserSession.getInstance();
        if (session.getRole() == UserRole.ZONE_COORDINATOR) {
            this.currentZoneId = session.getAssignedZoneId();
            applyRoleContext(
                    "Register Displaced Persons",
                    "Register people and manage shelter placement within your assigned zone.",
                    "ZONE SCOPED",
                    "role-badge-zone");
        } else {
            applyRoleContext(
                    "Affected Person Registration",
                    "Register people, capture medical needs, and manage shelter placement.",
                    "SYSTEM ADMIN",
                    "role-badge-admin");
        }

        // Load shelter list for ComboBox asynchronously
        Task<List<String>> loadSheltersTask = new Task<>() {
            @Override
            protected List<String> call() {
                return affectedPersonService.getAllSheltersForCombo();
            }
        };
        loadSheltersTask.setOnSucceeded(e -> shelterCombo.getItems().setAll(loadSheltersTask.getValue()));
        loadSheltersTask.setOnFailed(e ->
                errorLabel.setText("Failed to load shelters from database"));

        // Load affected persons asynchronously (zone-scoped for coordinators)
        Task<ServiceResult<List<AffectedPerson>>> loadTask = new Task<>() {
            @Override
            protected ServiceResult<List<AffectedPerson>> call() {
                return affectedPersonService.getAllAffectedPersons(currentZoneId);
            }
        };
        loadTask.setOnSucceeded(e -> {
            ServiceResult<List<AffectedPerson>> result = loadTask.getValue();
            if (result.success()) {
                dataList.setAll(result.value());
                personTable.setItems(dataList);
            } else {
                errorLabel.setText(result.message());
            }
        });
        loadTask.setOnFailed(e -> {
            e.getSource().getException().printStackTrace();
            errorLabel.setText("Failed to load affected persons from database");
        });

        new Thread(loadSheltersTask) {{ setDaemon(true); }}.start();
        new Thread(loadTask) {{ setDaemon(true); }}.start();
    }

    /** Applies presentation-only role context without changing data permissions. */
    private void applyRoleContext(String title, String subtitle, String badgeText,
                                  String badgeStyleClass) {
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle);
        roleBadge.setText(badgeText);
        roleBadge.getStyleClass().removeAll(
                "role-badge-admin", "role-badge-zone", "role-badge-analytics");
        roleBadge.getStyleClass().add(badgeStyleClass);
    }

    /**
     * Register a new displaced person.
     * Inserts into affected_persons table (inherits from people).
     * medicalNeeds is stored as JSONB in PostgreSQL.
     */
    @FXML
    public void handleRegister(ActionEvent event) {
        errorLabel.setText("");
        String name = nameField.getText();
        String contact = contactField.getText();
        String email = emailField.getText();
        String medicalNeeds = medicalNeedsArea.getText();
        Integer shelterId = parseShelterId(shelterCombo.getValue());

        if (name == null || name.trim().isEmpty()) {
            errorLabel.setText("Name is required");
            return;
        }

        // Validate JSON format for medical_needs
        if (medicalNeeds != null && !medicalNeeds.trim().isEmpty()) {
            medicalNeeds = medicalNeeds.trim();
            if (!medicalNeeds.startsWith("{") || !medicalNeeds.endsWith("}")) {
                errorLabel.setText("Medical needs must be valid JSON (e.g. {\"conditions\": [\"diabetes\"]})");
                return;
            }
        }

        // Final copy captured by the background Task (validation may reassign above)
        final String medicalNeedsJson = medicalNeeds;
        Integer zoneId = currentZoneId;
        Task<ServiceResult<AffectedPerson>> task = new Task<>() {
            @Override
            protected ServiceResult<AffectedPerson> call() {
                return affectedPersonService.register(name, contact, email, medicalNeedsJson,
                                                      shelterId, zoneId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<AffectedPerson> result = task.getValue();
            if (result.success()) {
                dataList.add(0, result.value());
                personTable.setItems(dataList);
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
     * Update an existing affected person's details or medical needs.
     */
    @FXML
    public void handleUpdate(ActionEvent event) {
        errorLabel.setText("");
        String personIdText = personIdField.getText();
        if (personIdText == null || personIdText.trim().isEmpty()) {
            errorLabel.setText("Person ID is required for update");
            personTable.setItems(dataList);
            return;
        }

        int personId;
        try {
            personId = Integer.parseInt(personIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Person ID must be a number");
            return;
        }

        String name = nameField.getText().isEmpty() ? null : nameField.getText();
        String contact = contactField.getText().isEmpty() ? null : contactField.getText();
        String email = emailField.getText().isEmpty() ? null : emailField.getText();
        String medicalNeeds = medicalNeedsArea.getText().isEmpty() ? null : medicalNeedsArea.getText().trim();
        Integer shelterId = parseShelterId(shelterCombo.getValue());
        Integer zoneId = currentZoneId;

        Task<ServiceResult<AffectedPerson>> task = new Task<>() {
            @Override
            protected ServiceResult<AffectedPerson> call() {
                return affectedPersonService.update(personId, name, contact, email, medicalNeeds,
                                                    shelterId, zoneId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<AffectedPerson> result = task.getValue();
            if (result.success()) {
                dataList.removeIf(ap -> ap.getPersonId() == personId);
                dataList.add(0, result.value());
                personTable.setItems(dataList);
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
     * Remove an affected person record.
     */
    @FXML
    public void handleDelete(ActionEvent event) {
        errorLabel.setText("");
        String personIdText = personIdField.getText();
        if (personIdText == null || personIdText.trim().isEmpty()) {
            errorLabel.setText("Person ID is required for deletion");
            personTable.setItems(dataList);
            return;
        }

        int personId;
        try {
            personId = Integer.parseInt(personIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Person ID must be a number");
            return;
        }

        Integer zoneId = currentZoneId;
        Task<ServiceResult<Void>> task = new Task<>() {
            @Override
            protected ServiceResult<Void> call() {
                return affectedPersonService.delete(personId, zoneId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<Void> result = task.getValue();
            if (result.success()) {
                dataList.removeIf(ap -> ap.getPersonId() == personId);
                personTable.setItems(dataList);
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
     * Find an affected person by their ID.
     */
    @FXML
    public void handleFind(ActionEvent event) {
        errorLabel.setText("");
        String personIdText = personIdField.getText();
        if (personIdText == null || personIdText.trim().isEmpty()) {
            errorLabel.setText("Enter Person ID to search");
            personTable.setItems(dataList);
            return;
        }

        int personId;
        try {
            personId = Integer.parseInt(personIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Person ID must be a number");
            return;
        }

        Task<ServiceResult<AffectedPerson>> task = new Task<>() {
            @Override
            protected ServiceResult<AffectedPerson> call() {
                return affectedPersonService.findById(personId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<AffectedPerson> result = task.getValue();
            if (result.success()) {
                searchList.clear();
                searchList.add(result.value());
                personTable.setItems(searchList);
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
     * View all affected persons at the currently selected shelter.
     */
    @FXML
    public void handleFindByShelter(ActionEvent event) {
        errorLabel.setText("");
        String shelterSelection = shelterCombo.getValue();
        if (shelterSelection == null) {
            errorLabel.setText("Select a shelter first");
            personTable.setItems(dataList);
            return;
        }
        Integer shelterId = parseShelterId(shelterSelection);
        if (shelterId == null) {
            errorLabel.setText("Select a shelter first");
            personTable.setItems(dataList);
            return;
        }

        Task<ServiceResult<List<AffectedPerson>>> task = new Task<>() {
            @Override
            protected ServiceResult<List<AffectedPerson>> call() {
                return affectedPersonService.findByShelterId(shelterId);
            }
        };
        task.setOnSucceeded(e -> {
            ServiceResult<List<AffectedPerson>> result = task.getValue();
            if (result.success()) {
                searchList.clear();
                searchList.setAll(result.value());
                personTable.setItems(searchList);
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

    @FXML
    public void handleClear(ActionEvent event) {
        personIdField.clear();
        nameField.clear();
        contactField.clear();
        emailField.clear();
        medicalNeedsArea.clear();
        if (shelterCombo.getItems() != null && !shelterCombo.getItems().isEmpty()) {
            shelterCombo.getSelectionModel().clearSelection();
        }
        errorLabel.setText("");
        personTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    /**
     * Extracts integer shelter_id from a ComboBox item formatted as "2 — Central Camp".
     */
    private Integer parseShelterId(String selection) {
        if (selection == null || selection.isBlank()) return null;
        try { return Integer.parseInt(selection.split(" — ")[0].trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
