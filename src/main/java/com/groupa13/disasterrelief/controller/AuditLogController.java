package com.groupa13.disasterrelief.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.groupa13.disasterrelief.model.AuditLog;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.operations.AuditLogService;
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

public class AuditLogController extends Utility {

    @FXML private ComboBox<String> tableNameCombo;
    @FXML private ComboBox<String> operationCombo;
    @FXML private TextField changedByField;
    @FXML private TextField dateFromField;
    @FXML private TextField dateToField;
    @FXML private TextField logIdField;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

    @FXML private TextArea oldValuesArea;
    @FXML private TextArea newValuesArea;

    @FXML private TableView<AuditLog> auditTable;
    @FXML private TableColumn<AuditLog, Integer> colLogId;
    @FXML private TableColumn<AuditLog, String> colTableName;
    @FXML private TableColumn<AuditLog, String> colOperation;
    @FXML private TableColumn<AuditLog, String> colChangedBy;
    @FXML private TableColumn<AuditLog, String> colChangedAt;
    @FXML private TableColumn<AuditLog, String> colOldValues;
    @FXML private TableColumn<AuditLog, String> colNewValues;

    private final ObservableList<AuditLog> dataList = FXCollections.observableArrayList();
    private final AuditLogService auditLogService;

    public AuditLogController() {
        this(new AuditLogService());
    }

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @FXML
    public void initialize() {
        colLogId.setCellValueFactory(new PropertyValueFactory<>("logId"));
        colTableName.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        colOperation.setCellValueFactory(new PropertyValueFactory<>("operation"));
        colChangedBy.setCellValueFactory(new PropertyValueFactory<>("changedBy"));
        colChangedAt.setCellValueFactory(new PropertyValueFactory<>("changedAt"));
        colOldValues.setCellValueFactory(new PropertyValueFactory<>("oldValues"));
        colNewValues.setCellValueFactory(new PropertyValueFactory<>("newValues"));

        operationCombo.getItems().setAll("All", "INSERT", "UPDATE", "DELETE");
        operationCombo.getSelectionModel().select("All");

        tableNameCombo.getItems().setAll(
                "All",
                "disasters", "zones", "shelters", "resources",
                "shelter_inventory", "volunteers", "affected_persons",
                "resource_requests", "volunteer_shifts", "donation_records"
        );
        tableNameCombo.getSelectionModel().select("All");

        UserRole role = UserSession.getInstance().getRole();
        if (roleBadge != null) {
            roleBadge.setText(role != null ? role.getDisplayName() : "");
        }

        auditTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                oldValuesArea.setText(formatJson(newVal.getOldValues()));
                newValuesArea.setText(formatJson(newVal.getNewValues()));
            } else {
                oldValuesArea.clear();
                newValuesArea.clear();
            }
        });

        auditTable.setItems(dataList);
        loadLogs(null, null, null, null, null, null);
    }

    private void loadLogs(String tableName, String operation, String changedBy,
            LocalDate from, LocalDate to, Integer logId) {
        errorLabel.setText("Loading audit trail...");

        Task<ServiceResult<List<AuditLog>>> task = new Task<>() {
            @Override
            protected ServiceResult<List<AuditLog>> call() {
                return auditLogService.filterLogs(tableName, operation, changedBy, from, to, logId);
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<List<AuditLog>> result = task.getValue();
            if (result != null && result.success()) {
                dataList.setAll(result.value());
                errorLabel.setText("");
            } else {
                errorLabel.setText(result != null ? result.message() : "Failed to load audit logs");
            }
        });

        task.setOnFailed(e -> errorLabel.setText("Database connection failure"));

        Thread thread = new Thread(task, "audit-log-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleApplyFilters(ActionEvent event) {
        String tableName = tableNameCombo.getValue();
        String operation = operationCombo.getValue();
        String changedBy = changedByField.getText();
        String fromText = dateFromField.getText();
        String toText = dateToField.getText();

        LocalDate from = null;
        LocalDate to = null;

        try {
            if (fromText != null && !fromText.isBlank()) {
                from = LocalDate.parse(fromText.trim());
            }
            if (toText != null && !toText.isBlank()) {
                to = LocalDate.parse(toText.trim());
            }
        } catch (DateTimeParseException e) {
            errorLabel.setText("Date format must be yyyy-MM-dd");
            return;
        }

        loadLogs(tableName, operation, changedBy, from, to, null);
    }

    @FXML
    public void handleFindById(ActionEvent event) {
        String logIdText = logIdField.getText();
        if (logIdText == null || logIdText.isBlank()) {
            errorLabel.setText("Enter Log ID to search");
            return;
        }

        int logId;
        try {
            logId = Integer.parseInt(logIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Log ID must be an integer");
            return;
        }

        loadLogs(null, null, null, null, null, logId);
    }

    @FXML
    public void handleShowDiff(ActionEvent event) {
        AuditLog selected = auditTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            oldValuesArea.setText(formatJson(selected.getOldValues()));
            newValuesArea.setText(formatJson(selected.getNewValues()));
            errorLabel.setText("");
        } else {
            errorLabel.setText("Select a row in the table to display JSON diff");
        }
    }

    @FXML
    public void handleClear(ActionEvent event) {
        tableNameCombo.getSelectionModel().select("All");
        operationCombo.getSelectionModel().select("All");
        changedByField.clear();
        dateFromField.clear();
        dateToField.clear();
        logIdField.clear();
        oldValuesArea.clear();
        newValuesArea.clear();
        errorLabel.setText("");
        loadLogs(null, null, null, null, null, null);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    private String formatJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty() || "null".equalsIgnoreCase(rawJson.trim())) {
            return "(none)";
        }
        return rawJson.replace("{", "{\n  ")
                .replace("}", "\n}")
                .replace(", \"", ",\n  \"")
                .replace(",\"", ",\n  \"");
    }
}
