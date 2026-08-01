package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.Volunteer;
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
 * Adapted from: Hostel-Management-System StudentController.java pattern
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

    @FXML
    public void initialize() {
        // Bind table columns to Volunteer model properties
        colPersonId.setCellValueFactory(new PropertyValueFactory<>("personId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contact"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colSkills.setCellValueFactory(new PropertyValueFactory<>("skills"));
        colAvailability.setCellValueFactory(new PropertyValueFactory<>("availability"));
        colZone.setCellValueFactory(new PropertyValueFactory<>("zoneName"));

        // TODO: Load zone list from DB for the ComboBox
        // Task<List<Zone>> loadZonesTask = ...
        // zoneCombo.getItems().addAll(zoneNames);

        // Load all volunteers asynchronously
        // TODO: Replace with actual DAO/Service call when service layer is ready
        // Pattern adapted from Hostel StudentController.initialize()
        /*
        Task<List<Volunteer>> loadTask = new Task<>() {
            @Override
            protected List<Volunteer> call() throws Exception {
                return volunteerService.getAllVolunteers();
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            volunteerTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            Throwable ex = loadTask.getException();
            ex.printStackTrace();
            errorLabel.setText("Failed to load volunteers from database");
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
        */

        volunteerTable.setItems(dataList);
    }

    /**
     * Register a new volunteer.
     * Inserts into volunteers table (which inherits from people).
     */
    @FXML
    public void handleAdd(ActionEvent event) {
        try {
            errorLabel.setText("");
            String name = nameField.getText();
            String contact = contactField.getText();
            String email = emailField.getText();
            String skills = skillsField.getText();
            String availability = availabilityField.getText();
            String zone = zoneCombo.getValue();

            if (name == null || name.trim().isEmpty()) {
                errorLabel.setText("Name is required");
                return;
            }

            // TODO: Call service layer to insert volunteer
            // Volunteer v = volunteerService.registerVolunteer(name, contact, email, skills, availability, zoneId);
            // if (v != null) {
            //     dataList.add(0, v);
            //     volunteerTable.setItems(dataList);
            // } else {
            //     errorLabel.setText("Failed to register volunteer");
            // }

            errorLabel.setText("TODO: Service layer integration pending (Developer A infrastructure)");

        } catch (Exception e) {
            errorLabel.setText("Error registering volunteer");
            e.printStackTrace();
        }
    }

    /**
     * Update an existing volunteer's details.
     */
    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            errorLabel.setText("");
            String personIdText = personIdField.getText();
            if (personIdText == null || personIdText.trim().isEmpty()) {
                errorLabel.setText("Volunteer ID is required for update");
                volunteerTable.setItems(dataList);
                return;
            }
            int personId = Integer.parseInt(personIdText);

            String name = nameField.getText().isEmpty() ? null : nameField.getText();
            String contact = contactField.getText().isEmpty() ? null : contactField.getText();
            String email = emailField.getText().isEmpty() ? null : emailField.getText();
            String skills = skillsField.getText().isEmpty() ? null : skillsField.getText();
            String availability = availabilityField.getText().isEmpty() ? null : availabilityField.getText();

            // TODO: Call service layer to update volunteer
            // if (volunteerService.updateVolunteer(personId, name, contact, email, skills, availability, zoneId)) {
            //     dataList.removeIf(v -> v.getPersonId() == personId);
            //     Volunteer updated = volunteerService.findVolunteerById(personId);
            //     dataList.add(0, updated);
            //     volunteerTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Volunteer ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error updating volunteer");
            e.printStackTrace();
        }
    }

    /**
     * Remove a volunteer from the system.
     */
    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            errorLabel.setText("");
            String personIdText = personIdField.getText();
            if (personIdText == null || personIdText.trim().isEmpty()) {
                errorLabel.setText("Volunteer ID is required for deletion");
                volunteerTable.setItems(dataList);
                return;
            }
            int personId = Integer.parseInt(personIdText);

            // TODO: Call service layer to delete volunteer
            // if (volunteerService.deleteVolunteer(personId)) {
            //     dataList.removeIf(v -> v.getPersonId() == personId);
            //     volunteerTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Volunteer ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error removing volunteer");
            e.printStackTrace();
        }
    }

    /**
     * Find a volunteer by ID.
     */
    @FXML
    public void handleFind(ActionEvent event) {
        try {
            errorLabel.setText("");
            String personIdText = personIdField.getText();
            if (personIdText == null || personIdText.trim().isEmpty()) {
                errorLabel.setText("Enter Volunteer ID to search");
                volunteerTable.setItems(dataList);
                return;
            }
            int personId = Integer.parseInt(personIdText);

            // TODO: Call service layer to find volunteer
            // Volunteer v = volunteerService.findVolunteerById(personId);
            // if (v != null) {
            //     searchList.clear();
            //     searchList.add(v);
            //     volunteerTable.setItems(searchList);
            // } else {
            //     errorLabel.setText("Volunteer not found");
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Volunteer ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error finding volunteer");
            e.printStackTrace();
        }
    }

    /**
     * Search volunteers by skill using PostgreSQL TEXT[] array contains operator (@>).
     * Queries: SELECT * FROM volunteers WHERE skills @> ARRAY['first_aid']::TEXT[]
     */
    @FXML
    public void handleSearchBySkill(ActionEvent event) {
        try {
            errorLabel.setText("");
            String skillQuery = skillsField.getText();
            if (skillQuery == null || skillQuery.trim().isEmpty()) {
                errorLabel.setText("Enter skill(s) in the Skills field to search");
                volunteerTable.setItems(dataList);
                return;
            }

            // TODO: Call service layer to search by skills
            // List<Volunteer> results = volunteerService.findVolunteersBySkills(skillQuery.split(","));
            // searchList.clear();
            // searchList.setAll(results);
            // volunteerTable.setItems(searchList);

            errorLabel.setText("TODO: Skill search integration pending — uses TEXT[] @> operator");

        } catch (Exception e) {
            errorLabel.setText("Error searching by skill");
            e.printStackTrace();
        }
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
}
