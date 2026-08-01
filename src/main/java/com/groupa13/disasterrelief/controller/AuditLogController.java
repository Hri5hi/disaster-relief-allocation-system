package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.AuditLog;
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
 * Controller for audit_log.fxml — System Mutation Audit Trail.
 * Target Role: SYSTEM_ADMIN only
 *
 * Features:
 * - Read-only viewer (table populated entirely by trg_audit_log trigger)
 * - Multi-criteria filtering: table name, operation, user, date range
 * - JSON diff viewer: displays old_values vs new_values side-by-side
 * - Search by specific log ID
 *
 * The audit_log table is never written to by Java application code.
 * All entries are created by database triggers on INSERT/UPDATE/DELETE.
 */
public class AuditLogController extends Utility {

    // --- Filter fields ---
    @FXML private ComboBox<String> tableNameCombo;
    @FXML private ComboBox<String> operationCombo;
    @FXML private TextField changedByField;
    @FXML private TextField dateFromField;
    @FXML private TextField dateToField;
    @FXML private TextField logIdField;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

    // --- JSON Diff viewer ---
    @FXML private TextArea oldValuesArea;
    @FXML private TextArea newValuesArea;

    // --- Table ---
    @FXML private TableView<AuditLog> auditTable;
    @FXML private TableColumn<AuditLog, Integer> colLogId;
    @FXML private TableColumn<AuditLog, String> colTableName;
    @FXML private TableColumn<AuditLog, String> colOperation;
    @FXML private TableColumn<AuditLog, String> colChangedBy;
    @FXML private TableColumn<AuditLog, String> colChangedAt;
    @FXML private TableColumn<AuditLog, String> colOldValues;
    @FXML private TableColumn<AuditLog, String> colNewValues;

    private ObservableList<AuditLog> dataList = FXCollections.observableArrayList();
    private ObservableList<AuditLog> searchList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Bind table columns
        colLogId.setCellValueFactory(new PropertyValueFactory<>("logId"));
        colTableName.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        colOperation.setCellValueFactory(new PropertyValueFactory<>("operation"));
        colChangedBy.setCellValueFactory(new PropertyValueFactory<>("changedBy"));
        colChangedAt.setCellValueFactory(new PropertyValueFactory<>("changedAt"));
        colOldValues.setCellValueFactory(new PropertyValueFactory<>("oldValues"));
        colNewValues.setCellValueFactory(new PropertyValueFactory<>("newValues"));

        // Populate operation filter combo with possible values
        operationCombo.getItems().addAll("INSERT", "UPDATE", "DELETE");

        // Populate table name combo with audited tables
        // These are the critical tables that have audit triggers per project_description_v2.md
        tableNameCombo.getItems().addAll(
                "disasters", "zones", "shelters",
                "resource_categories", "resources", "shelter_inventory",
                "volunteers", "affected_persons",
                "resource_requests", "distribution_log", "volunteer_shifts",
                "donation_records"
        );

        // Listen for table row selection to auto-populate JSON diff viewer
        auditTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                oldValuesArea.setText(formatJson(newVal.getOldValues()));
                newValuesArea.setText(formatJson(newVal.getNewValues()));
            } else {
                oldValuesArea.clear();
                newValuesArea.clear();
            }
        });

        // Load all audit logs asynchronously
        // TODO: Replace with actual DAO/Service call
        /*
        Task<List<AuditLog>> loadTask = new Task<>() {
            @Override
            protected List<AuditLog> call() throws Exception {
                return auditLogService.getAllLogs();
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            auditTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            Throwable ex = loadTask.getException();
            ex.printStackTrace();
            errorLabel.setText("Failed to load audit logs from database");
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
        */

        auditTable.setItems(dataList);
    }

    /**
     * Apply all active filters (table name, operation, user, date range).
     * Builds a dynamic WHERE clause based on which filters are populated.
     */
    @FXML
    public void handleApplyFilters(ActionEvent event) {
        try {
            errorLabel.setText("");
            String tableName = tableNameCombo.getValue();
            String operation = operationCombo.getValue();
            String changedBy = changedByField.getText();
            String dateFrom = dateFromField.getText();
            String dateTo = dateToField.getText();

            // At least one filter should be set
            if ((tableName == null || tableName.isEmpty()) &&
                (operation == null || operation.isEmpty()) &&
                (changedBy == null || changedBy.isEmpty()) &&
                (dateFrom == null || dateFrom.isEmpty())) {
                errorLabel.setText("Set at least one filter criterion");
                return;
            }

            // TODO: Call service layer with filter parameters
            // List<AuditLog> filtered = auditLogService.filterLogs(tableName, operation, changedBy, dateFrom, dateTo);
            // searchList.clear();
            // searchList.setAll(filtered);
            // auditTable.setItems(searchList);

            errorLabel.setText("TODO: Multi-criteria filter integration pending");

        } catch (Exception e) {
            errorLabel.setText("Error applying filters");
            e.printStackTrace();
        }
    }

    /**
     * Find a specific audit log entry by its ID.
     */
    @FXML
    public void handleFindById(ActionEvent event) {
        try {
            errorLabel.setText("");
            String logIdText = logIdField.getText();
            if (logIdText == null || logIdText.trim().isEmpty()) {
                errorLabel.setText("Enter Log ID to search");
                auditTable.setItems(dataList);
                return;
            }
            int logId = Integer.parseInt(logIdText);

            // TODO: Call service layer
            // AuditLog log = auditLogService.findLogById(logId);
            // if (log != null) {
            //     searchList.clear();
            //     searchList.add(log);
            //     auditTable.setItems(searchList);
            // } else {
            //     errorLabel.setText("Audit log entry not found");
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Log ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error finding audit log entry");
            e.printStackTrace();
        }
    }

    /**
     * Display the JSON diff for the currently selected audit log entry.
     * Shows old_values and new_values side-by-side in the diff viewer.
     */
    @FXML
    public void handleShowDiff(ActionEvent event) {
        AuditLog selected = auditTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            errorLabel.setText("Select a row from the table first");
            return;
        }
        errorLabel.setText("");
        oldValuesArea.setText(formatJson(selected.getOldValues()));
        newValuesArea.setText(formatJson(selected.getNewValues()));
    }

    @FXML
    public void handleClear(ActionEvent event) {
        tableNameCombo.getSelectionModel().clearSelection();
        operationCombo.getSelectionModel().clearSelection();
        changedByField.clear();
        dateFromField.clear();
        dateToField.clear();
        logIdField.clear();
        oldValuesArea.clear();
        newValuesArea.clear();
        errorLabel.setText("");
        auditTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    /**
     * Simple JSON pretty-printer for display in the diff viewer.
     * Adds newlines after commas and colons for readability.
     * A production version would use a proper JSON library.
     */
    private String formatJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "(null — no data for this operation)";
        }

        // Basic pretty-print: add indentation
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;

        for (char c : json.toCharArray()) {
            if (c == '"' && (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (!inString) {
                switch (c) {
                    case '{':
                    case '[':
                        sb.append(c);
                        sb.append('\n');
                        indent++;
                        sb.append("  ".repeat(indent));
                        break;
                    case '}':
                    case ']':
                        sb.append('\n');
                        indent--;
                        sb.append("  ".repeat(Math.max(0, indent)));
                        sb.append(c);
                        break;
                    case ',':
                        sb.append(c);
                        sb.append('\n');
                        sb.append("  ".repeat(indent));
                        break;
                    case ':':
                        sb.append(c);
                        sb.append(' ');
                        break;
                    default:
                        sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
