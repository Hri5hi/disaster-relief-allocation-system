package com.groupa13.disasterrelief.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.groupa13.disasterrelief.model.ResourceRequest;
import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Resource;
import com.groupa13.disasterrelief.model.core.Shelter;
import com.groupa13.disasterrelief.model.core.Zone;
import com.groupa13.disasterrelief.model.operations.AllocationResult;
import com.groupa13.disasterrelief.service.core.ResourceService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.core.ShelterService;
import com.groupa13.disasterrelief.service.core.ZoneService;
import com.groupa13.disasterrelief.service.operations.AllocationService;
import com.groupa13.disasterrelief.service.operations.ResourceRequestService;
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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;

public class ResourceRequestController extends Utility {

    @FXML private TextField requestIdField;
    @FXML private ComboBox<String> zoneCombo;
    @FXML private ComboBox<String> resourceCombo;
    @FXML private TextField quantityField;
    @FXML private ComboBox<String> urgencyCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Button btnFulfill;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

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

    private final ObservableList<ResourceRequest> dataList = FXCollections.observableArrayList();
    private final Map<String, Integer> zoneNameToId = new HashMap<>();
    private final Map<String, Integer> zoneNameToPrimaryShelter = new HashMap<>();
    private final Map<String, Integer> resourceNameToId = new HashMap<>();

    private final ResourceRequestService requestService;
    private final AllocationService allocationService;
    private final ZoneService zoneService;
    private final ResourceService resourceService;
    private final ShelterService shelterService;

    public ResourceRequestController() {
        this(new ResourceRequestService(), new AllocationService(), new ZoneService(),
             new ResourceService(), new ShelterService());
    }

    public ResourceRequestController(ResourceRequestService requestService,
            AllocationService allocationService, ZoneService zoneService,
            ResourceService resourceService, ShelterService shelterService) {
        this.requestService = requestService;
        this.allocationService = allocationService;
        this.zoneService = zoneService;
        this.resourceService = resourceService;
        this.shelterService = shelterService;
    }

    @FXML
    public void initialize() {
        colRequestId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colZoneName.setCellValueFactory(new PropertyValueFactory<>("zoneName"));
        colResourceName.setCellValueFactory(new PropertyValueFactory<>("resourceName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantityRequested"));
        colUrgency.setCellValueFactory(new PropertyValueFactory<>("urgency"));
        colPriorityScore.setCellValueFactory(new PropertyValueFactory<>("priorityScore"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colRequestedAt.setCellValueFactory(new PropertyValueFactory<>("requestedAt"));
        colFulfilledAt.setCellValueFactory(new PropertyValueFactory<>("fulfilledAt"));

        urgencyCombo.getItems().setAll("critical", "high", "medium", "low");
        urgencyCombo.getSelectionModel().select("medium");

        statusCombo.getItems().setAll("All", "pending", "fulfilled", "cancelled");
        statusCombo.getSelectionModel().select("pending");

        UserSession session = UserSession.getInstance();
        UserRole role = session.getRole();
        if (roleBadge != null) {
            roleBadge.setText(role != null ? role.getDisplayName() : "");
        }

        if (role == UserRole.ZONE_COORDINATOR) {
            btnFulfill.setVisible(false);
            btnFulfill.setManaged(false);
        }

        requestTable.setItems(dataList);

        requestTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                requestIdField.setText(String.valueOf(newSel.getRequestId()));
                zoneCombo.setValue(newSel.getZoneName());
                resourceCombo.setValue(newSel.getResourceName());
                quantityField.setText(String.valueOf(newSel.getQuantityRequested()));
                urgencyCombo.setValue(newSel.getUrgency());
                statusCombo.setValue(newSel.getStatus());
            }
        });

        loadReferenceDataAndRequests();
    }

    private void loadReferenceDataAndRequests() {
        errorLabel.setText("Loading requests...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                ServiceResult<List<Zone>> zResult = zoneService.listZones();
                if (zResult.success()) {
                    for (Zone z : zResult.value()) {
                        zoneNameToId.put(z.name(), z.zoneId());
                        if (z.primaryShelterId() != null) {
                            zoneNameToPrimaryShelter.put(z.name(), z.primaryShelterId());
                        }
                    }
                }

                ServiceResult<List<Resource>> rResult = resourceService.listResources();
                if (rResult.success()) {
                    for (Resource r : rResult.value()) {
                        resourceNameToId.put(r.name(), r.resourceId());
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            zoneCombo.getItems().setAll(zoneNameToId.keySet());
            resourceCombo.getItems().setAll(resourceNameToId.keySet());

            UserSession session = UserSession.getInstance();
            if (session.getRole() == UserRole.ZONE_COORDINATOR && session.getAssignedZoneId() != null) {
                for (Map.Entry<String, Integer> entry : zoneNameToId.entrySet()) {
                    if (entry.getValue().equals(session.getAssignedZoneId())) {
                        zoneCombo.setValue(entry.getKey());
                        zoneCombo.setDisable(true);
                        break;
                    }
                }
            }
            refreshTableData();
        });

        task.setOnFailed(e -> errorLabel.setText("Failed to load reference data"));

        Thread thread = new Thread(task, "resource-req-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshTableData() {
        String status = statusCombo.getValue();
        UserSession session = UserSession.getInstance();
        Integer zoneFilter = (session.getRole() == UserRole.ZONE_COORDINATOR) ? session.getAssignedZoneId() : null;

        Task<ServiceResult<List<ResourceRequest>>> task = new Task<>() {
            @Override
            protected ServiceResult<List<ResourceRequest>> call() {
                return requestService.listRequests(zoneFilter, status);
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<List<ResourceRequest>> result = task.getValue();
            if (result != null && result.success()) {
                dataList.setAll(result.value());
                errorLabel.setText("");
            } else {
                errorLabel.setText(result != null ? result.message() : "Failed to load requests");
            }
        });

        task.setOnFailed(e -> errorLabel.setText("Unable to connect to database"));

        Thread thread = new Thread(task, "resource-req-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleSubmitRequest(ActionEvent event) {
        String zoneName = zoneCombo.getValue();
        String resourceName = resourceCombo.getValue();
        String quantityText = quantityField.getText();
        String urgency = urgencyCombo.getValue();

        if (zoneName == null || resourceName == null) {
            errorLabel.setText("Select both zone and resource");
            return;
        }

        Integer zoneId = zoneNameToId.get(zoneName);
        Integer resourceId = resourceNameToId.get(resourceName);
        if (zoneId == null || resourceId == null) {
            errorLabel.setText("Invalid zone or resource");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityText.trim());
        } catch (Exception e) {
            errorLabel.setText("Quantity must be a positive number");
            return;
        }

        Integer destShelter = zoneNameToPrimaryShelter.get(zoneName);
        if (destShelter == null) {
            destShelter = 1; // Fallback to primary camp
        }

        final int finalDestShelter = destShelter;
        Task<ServiceResult<ResourceRequest>> task = new Task<>() {
            @Override
            protected ServiceResult<ResourceRequest> call() {
                return requestService.submitRequest(zoneId, resourceId, finalDestShelter, quantity, urgency);
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<ResourceRequest> result = task.getValue();
            if (result != null && result.success()) {
                showInfoAlert("Success", result.message());
                handleClear(event);
                refreshTableData();
            } else {
                errorLabel.setText(result != null ? result.message() : "Failed to submit request");
            }
        });

        task.setOnFailed(e -> errorLabel.setText("Submission failed"));

        Thread thread = new Thread(task, "submit-request-worker");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        // Refresh table data
        refreshTableData();
    }

    @FXML
    public void handleFulfill(ActionEvent event) {
        ResourceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningAlert("Selection Required", "Select a pending request to fulfill");
            return;
        }

        if (!"pending".equalsIgnoreCase(selected.getStatus())) {
            showWarningAlert("Invalid Status", "Only pending requests can be fulfilled");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Fulfill Resource Request #" + selected.getRequestId());
        dialog.setHeaderText("Fulfill Request for " + selected.getQuantityRequested() + " " + selected.getResourceName());
        dialog.setContentText("Enter Source Shelter ID:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        int sourceShelterId;
        try {
            sourceShelterId = Integer.parseInt(result.get().trim());
        } catch (Exception e) {
            showErrorAlert("Invalid Input", "Source Shelter ID must be a positive integer");
            return;
        }

        Task<ServiceResult<AllocationResult>> task = new Task<>() {
            @Override
            protected ServiceResult<AllocationResult> call() {
                return allocationService.allocateResources(selected.getRequestId(), sourceShelterId);
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<AllocationResult> allocResult = task.getValue();
            if (allocResult != null && allocResult.success()) {
                showInfoAlert("Fulfillment Complete", allocResult.message());
                refreshTableData();
            } else {
                showErrorAlert("Fulfillment Failed", allocResult != null ? allocResult.message() : "Allocation error");
            }
        });

        task.setOnFailed(e -> showErrorAlert("System Error", "Allocation task failed"));

        Thread thread = new Thread(task, "allocation-worker");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        ResourceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarningAlert("Selection Required", "Select a pending request to cancel");
            return;
        }

        Task<ServiceResult<Void>> task = new Task<>() {
            @Override
            protected ServiceResult<Void> call() {
                return requestService.cancelRequest(selected.getRequestId());
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<Void> res = task.getValue();
            if (res != null && res.success()) {
                showInfoAlert("Success", res.message());
                refreshTableData();
            } else {
                errorLabel.setText(res != null ? res.message() : "Failed to cancel request");
            }
        });

        task.setOnFailed(e -> errorLabel.setText("Cancel failed"));

        Thread thread = new Thread(task, "cancel-request-worker");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleFindById(ActionEvent event) {
        String idText = requestIdField.getText();
        if (idText == null || idText.isBlank()) {
            errorLabel.setText("Enter a Request ID to search");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idText.trim());
        } catch (Exception e) {
            errorLabel.setText("Request ID must be an integer");
            return;
        }

        for (ResourceRequest req : dataList) {
            if (req.getRequestId() == id) {
                requestTable.getSelectionModel().select(req);
                requestTable.scrollTo(req);
                errorLabel.setText("");
                return;
            }
        }
        errorLabel.setText("Request #" + id + " not found in current view");
    }

    @FXML
    public void handleFilterByStatus(ActionEvent event) {
        refreshTableData();
    }

    @FXML
    public void handleClear(ActionEvent event) {
        requestIdField.clear();
        quantityField.clear();
        errorLabel.setText("");
        urgencyCombo.getSelectionModel().select("medium");
        requestTable.getSelectionModel().clearSelection();
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }
}
