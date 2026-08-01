package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.AffectedPerson;
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
 * Adapted from: Hostel-Management-System StudentController.java pattern
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

        // Zone Coordinator context filtering:
        // If the current user is a ZONE_COORDINATOR, only load shelters in their assigned zone
        // and only show affected persons registered at those shelters.
        // UserSession session = UserSession.getInstance();
        // if (session.getRole() == UserRole.ZONE_COORDINATOR) {
        //     Integer zoneId = session.getAssignedZoneId();
        //     // Load only zone shelters for the combo
        //     // Load only affected persons at zone shelters
        // }

        // TODO: Load shelter list for ComboBox from DB
        // TODO: Load affected persons list asynchronously
        /*
        Task<List<AffectedPerson>> loadTask = new Task<>() {
            @Override
            protected List<AffectedPerson> call() throws Exception {
                return affectedPersonService.getAllAffectedPersons();
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            personTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            Throwable ex = loadTask.getException();
            ex.printStackTrace();
            errorLabel.setText("Failed to load affected persons from database");
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
        */

        personTable.setItems(dataList);
    }

    /**
     * Register a new displaced person.
     * Inserts into affected_persons table (inherits from people).
     * medicalNeeds is stored as JSONB in PostgreSQL.
     */
    @FXML
    public void handleRegister(ActionEvent event) {
        try {
            errorLabel.setText("");
            String name = nameField.getText();
            String contact = contactField.getText();
            String email = emailField.getText();
            String shelter = shelterCombo.getValue();
            String medicalNeeds = medicalNeedsArea.getText();

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

            // TODO: Call service layer
            // AffectedPerson ap = affectedPersonService.registerPerson(name, contact, email, shelterId, medicalNeeds);
            // if (ap != null) {
            //     dataList.add(0, ap);
            //     personTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (Exception e) {
            errorLabel.setText("Error registering affected person");
            e.printStackTrace();
        }
    }

    /**
     * Update an existing affected person's details or medical needs.
     */
    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            errorLabel.setText("");
            String personIdText = personIdField.getText();
            if (personIdText == null || personIdText.trim().isEmpty()) {
                errorLabel.setText("Person ID is required for update");
                personTable.setItems(dataList);
                return;
            }
            int personId = Integer.parseInt(personIdText);

            String name = nameField.getText().isEmpty() ? null : nameField.getText();
            String contact = contactField.getText().isEmpty() ? null : contactField.getText();
            String email = emailField.getText().isEmpty() ? null : emailField.getText();
            String medicalNeeds = medicalNeedsArea.getText().isEmpty() ? null : medicalNeedsArea.getText();

            // TODO: Call service layer
            // if (affectedPersonService.updatePerson(personId, name, contact, email, shelterId, medicalNeeds)) {
            //     dataList.removeIf(ap -> ap.getPersonId() == personId);
            //     AffectedPerson updated = affectedPersonService.findPersonById(personId);
            //     dataList.add(0, updated);
            //     personTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Person ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error updating affected person");
            e.printStackTrace();
        }
    }

    /**
     * Remove an affected person record.
     */
    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            errorLabel.setText("");
            String personIdText = personIdField.getText();
            if (personIdText == null || personIdText.trim().isEmpty()) {
                errorLabel.setText("Person ID is required for deletion");
                personTable.setItems(dataList);
                return;
            }
            int personId = Integer.parseInt(personIdText);

            // TODO: Call service layer
            // if (affectedPersonService.deletePerson(personId)) {
            //     dataList.removeIf(ap -> ap.getPersonId() == personId);
            //     personTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Person ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error removing affected person");
            e.printStackTrace();
        }
    }

    /**
     * Find an affected person by their ID.
     */
    @FXML
    public void handleFind(ActionEvent event) {
        try {
            errorLabel.setText("");
            String personIdText = personIdField.getText();
            if (personIdText == null || personIdText.trim().isEmpty()) {
                errorLabel.setText("Enter Person ID to search");
                personTable.setItems(dataList);
                return;
            }
            int personId = Integer.parseInt(personIdText);

            // TODO: Call service layer
            // AffectedPerson ap = affectedPersonService.findPersonById(personId);
            // if (ap != null) {
            //     searchList.clear();
            //     searchList.add(ap);
            //     personTable.setItems(searchList);
            // } else {
            //     errorLabel.setText("Person not found");
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Person ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error finding affected person");
            e.printStackTrace();
        }
    }

    /**
     * View all affected persons at the currently selected shelter.
     */
    @FXML
    public void handleFindByShelter(ActionEvent event) {
        try {
            errorLabel.setText("");
            String shelterSelection = shelterCombo.getValue();
            if (shelterSelection == null) {
                errorLabel.setText("Select a shelter first");
                personTable.setItems(dataList);
                return;
            }

            // TODO: Extract shelterId and query
            // List<AffectedPerson> persons = affectedPersonService.findByShelterId(shelterId);
            // searchList.clear();
            // searchList.setAll(persons);
            // personTable.setItems(searchList);

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (Exception e) {
            errorLabel.setText("Error finding persons by shelter");
            e.printStackTrace();
        }
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
}
