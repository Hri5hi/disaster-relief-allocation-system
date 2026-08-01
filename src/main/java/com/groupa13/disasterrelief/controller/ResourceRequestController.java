package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.ResourceRequest;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.Utility;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller for resource_request.fxml — Priority Request Submission & Fulfillment.
 * Target Roles: ZONE_COORDINATOR (submit), WAREHOUSE_MANAGER (fulfill), SYSTEM_ADMIN (all)
 *
 * Features:
 * - Submit resource requests with urgency levels → DB generates priority_score
 * - Fulfill button triggers fn_allocate_resources() atomic transfer
 * - Zone Coordinators: pre-filtered to their assigned zone
 * - Warehouse Managers: see fulfillment mode with Fulfill button
 * - Status filtering (pending/fulfilled/cancelled)
 *
 * Adapted from: Hostel-Management-System AllocationController.java pattern
 */
public class ResourceRequestController extends Utility {

    // --- Form fields ---
    @FXML private TextField requestIdField;
    @FXML private ComboBox<String> zoneCombo;
    @FXML private ComboBox<String> resourceCombo;
    @FXML private TextField quantityField;
    @FXML private ComboBox<String> urgencyCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Button btnFulfill;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

    // --- Table ---
    @FXML private TableView<ResourceRequest> requestTable;
    @FXML private TableColumn<ResourceRequest, Integer> colRequestId;
    @FXML private TableColumn<ResourceRequest, String> colZoneName;
    @FXML private TableColumn<ResourceRequest, String> colResourceName;
    @FXML private TableColumn<ResourceRequest, Integer> colQuantity;
    @FXML private TableColumn<ResourceRequest, String> colUrgency;
    @FXML private TableColumn<ResourceRequest, Integer> colPriorityScore;
    @FXML private TableColumn<ResourceRequest, String> colStatus;
    @FXML private TableColumn<ResourceRequest, String> colRequestedAt;
    @FXML private TableColumn<ResourceRequest, String> colFulfilledAt;

    private ObservableList<ResourceRequest> dataList = FXCollections.observableArrayList();
    private ObservableList<ResourceRequest> searchList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Bind table columns
        colRequestId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colZoneName.setCellValueFactory(new PropertyValueFactory<>("zoneName"));
        colResourceName.setCellValueFactory(new PropertyValueFactory<>("resourceName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantityRequested"));
        colUrgency.setCellValueFactory(new PropertyValueFactory<>("urgency"));
        colPriorityScore.setCellValueFactory(new PropertyValueFactory<>("priorityScore"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colRequestedAt.setCellValueFactory(new PropertyValueFactory<>("requestedAt"));
        colFulfilledAt.setCellValueFactory(new PropertyValueFactory<>("fulfilledAt"));

        // Populate urgency combo with allowed CHECK values
        urgencyCombo.getItems().addAll("critical", "high", "medium", "low");
        urgencyCombo.getSelectionModel().select("medium");

        // Populate status combo for filtering
        statusCombo.getItems().addAll("pending", "fulfilled", "cancelled");
        statusCombo.getSelectionModel().select("pending");

        // Role-based UI adaptation:
        // - ZONE_COORDINATOR: hide Fulfill button, pre-select zone
        // - WAREHOUSE_MANAGER: show Fulfill button, hide Submit
        // - SYSTEM_ADMIN: show all
        // UserSession session = UserSession.getInstance();
        // if (session.getRole() == UserRole.ZONE_COORDINATOR) {
        //     btnFulfill.setVisible(false);
        //     btnFulfill.setManaged(false);
        //     // Pre-filter zone combo to assigned zone
        // } else if (session.getRole() == UserRole.WAREHOUSE_MANAGER) {
        //     // Show fulfillment mode
        // }

        // TODO: Load zones and resources for combos from DB
        // TODO: Load all requests asynchronously
        /*
        Task<Object[]> loadAllTask = new Task<>() {
            @Override
            protected Object[] call() throws Exception {
                List<ResourceRequest> requests = requestService.getAllRequests();
                // Also load zones and resources for combos
                return new Object[] { requests, zones, resources };
            }
        };

        loadAllTask.setOnSucceeded(e -> {
            Object[] results = loadAllTask.getValue();
            dataList.setAll((List<ResourceRequest>) results[0]);
            requestTable.setItems(dataList);
        });

        loadAllTask.setOnFailed(e -> {
            errorLabel.setText("Failed to load requests from database");
        });

        Thread thread = new Thread(loadAllTask);
        thread.setDaemon(true);
        thread.start();
        */

        requestTable.setItems(dataList);
    }

    /**
     * Submit a new resource request.
     * The DB auto-generates the priority_score via a generated column.
     */
    @FXML
    public void handleSubmitRequest(ActionEvent event) {
        try {
            errorLabel.setText("");
            String zone = zoneCombo.getValue();
            String resource = resourceCombo.getValue();
            String quantityText = quantityField.getText();
            String urgency = urgencyCombo.getValue();

            if (zone == null || resource == null) {
                errorLabel.setText("Select both zone and resource");
                return;
            }
            if (quantityText == null || quantityText.trim().isEmpty()) {
                errorLabel.setText("Quantity is required");
                return;
            }

            int quantity = Integer.parseInt(quantityText);
            if (quantity <= 0) {
                errorLabel.setText("Quantity must be positive");
                return;
            }

            // TODO: Call service layer
            // ResourceRequest rr = requestService.submitRequest(zoneId, resourceId, quantity, urgency);
            // if (rr != null) {
            //     dataList.add(0, rr);
            //     requestTable.setItems(dataList);
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Quantity must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error submitting request");
            e.printStackTrace();
        }
    }

    /**
     * Update an existing request (e.g., change urgency or quantity before fulfillment).
     */
    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            errorLabel.setText("");
            String requestIdText = requestIdField.getText();
            if (requestIdText == null || requestIdText.trim().isEmpty()) {
                errorLabel.setText("Request ID is required for update");
                return;
            }
            int requestId = Integer.parseInt(requestIdText);

            // TODO: Call service layer
            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Request ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error updating request");
            e.printStackTrace();
        }
    }

    /**
     * Fulfill a pending request.
     * Calls fn_allocate_resources() which performs an ATOMIC transfer:
     * 1. Deducts from source shelter inventory
     * 2. Adds to destination shelter inventory
     * 3. Logs to distribution_log
     * 4. Updates request status to 'fulfilled'
     * All within a single transaction — either everything succeeds or nothing changes.
     */
    @FXML
    public void handleFulfill(ActionEvent event) {
        try {
            errorLabel.setText("");
            String requestIdText = requestIdField.getText();
            if (requestIdText == null || requestIdText.trim().isEmpty()) {
                errorLabel.setText("Enter the Request ID to fulfill");
                return;
            }
            int requestId = Integer.parseInt(requestIdText);

            // TODO: Call service layer which invokes fn_allocate_resources()
            // boolean success = requestService.fulfillRequest(requestId);
            // if (success) {
            //     // Refresh the request in the table
            //     dataList.removeIf(r -> r.getRequestId() == requestId);
            //     ResourceRequest updated = requestService.findRequestById(requestId);
            //     dataList.add(0, updated);
            //     requestTable.setItems(dataList);
            //     showInfoAlert("Success", "Request #" + requestId + " fulfilled — atomic transfer complete");
            // } else {
            //     errorLabel.setText("Fulfillment failed — check inventory levels");
            // }

            errorLabel.setText("TODO: fn_allocate_resources() integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Request ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error fulfilling request");
            e.printStackTrace();
        }
    }

    /**
     * Cancel a pending request (sets status to 'cancelled').
     */
    @FXML
    public void handleCancel(ActionEvent event) {
        try {
            errorLabel.setText("");
            String requestIdText = requestIdField.getText();
            if (requestIdText == null || requestIdText.trim().isEmpty()) {
                errorLabel.setText("Request ID is required to cancel");
                return;
            }
            int requestId = Integer.parseInt(requestIdText);

            // TODO: Call service layer
            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Request ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error cancelling request");
            e.printStackTrace();
        }
    }

    /**
     * Find a request by its ID.
     */
    @FXML
    public void handleFindById(ActionEvent event) {
        try {
            errorLabel.setText("");
            String requestIdText = requestIdField.getText();
            if (requestIdText == null || requestIdText.trim().isEmpty()) {
                errorLabel.setText("Enter Request ID to search");
                requestTable.setItems(dataList);
                return;
            }
            int requestId = Integer.parseInt(requestIdText);

            // TODO: Call service layer
            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Request ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error finding request");
            e.printStackTrace();
        }
    }

    /**
     * Filter requests by the selected status (pending/fulfilled/cancelled).
     */
    @FXML
    public void handleFilterByStatus(ActionEvent event) {
        try {
            errorLabel.setText("");
            String status = statusCombo.getValue();
            if (status == null) {
                errorLabel.setText("Select a status to filter by");
                requestTable.setItems(dataList);
                return;
            }

            // TODO: Call service layer
            // List<ResourceRequest> filtered = requestService.findByStatus(status);
            // searchList.clear();
            // searchList.setAll(filtered);
            // requestTable.setItems(searchList);

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (Exception e) {
            errorLabel.setText("Error filtering requests");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        requestIdField.clear();
        quantityField.clear();
        if (zoneCombo.getItems() != null && !zoneCombo.getItems().isEmpty()) {
            zoneCombo.getSelectionModel().clearSelection();
        }
        if (resourceCombo.getItems() != null && !resourceCombo.getItems().isEmpty()) {
            resourceCombo.getSelectionModel().clearSelection();
        }
        urgencyCombo.getSelectionModel().select("medium");
        statusCombo.getSelectionModel().select("pending");
        errorLabel.setText("");
        requestTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }
}
