package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.model.DistributionLog;
import com.groupa13.disasterrelief.util.Utility;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller for distribution_log.fxml — Read-Only Resource Movement History.
 * Target Roles: WAREHOUSE_MANAGER, SYSTEM_ADMIN, ANALYTICS_USER
 *
 * Features:
 * - Read-only view (records created by fn_allocate_resources() during fulfillment)
 * - Date range filtering leverages PostgreSQL table partitioning (partition pruning)
 * - Search by log ID
 *
 * The distribution_log table is range-partitioned by distributed_at (monthly partitions),
 * so date range queries automatically benefit from partition pruning.
 *
 * Adapted from: Hostel-Management-System RoomController.java handleShowAvailable() pattern
 */
public class DistributionLogController extends Utility {

    // --- Filter fields ---
    @FXML private TextField dateFromField;
    @FXML private TextField dateToField;
    @FXML private TextField logIdField;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

    // --- Table ---
    @FXML private TableView<DistributionLog> logTable;
    @FXML private TableColumn<DistributionLog, Integer> colLogId;
    @FXML private TableColumn<DistributionLog, String> colSourceShelter;
    @FXML private TableColumn<DistributionLog, String> colDestShelter;
    @FXML private TableColumn<DistributionLog, String> colResourceName;
    @FXML private TableColumn<DistributionLog, Integer> colQuantity;
    @FXML private TableColumn<DistributionLog, String> colDistributedAt;

    private ObservableList<DistributionLog> dataList = FXCollections.observableArrayList();
    private ObservableList<DistributionLog> searchList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Bind table columns
        colLogId.setCellValueFactory(new PropertyValueFactory<>("logId"));
        colSourceShelter.setCellValueFactory(new PropertyValueFactory<>("sourceShelterName"));
        colDestShelter.setCellValueFactory(new PropertyValueFactory<>("destShelterName"));
        colResourceName.setCellValueFactory(new PropertyValueFactory<>("resourceName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDistributedAt.setCellValueFactory(new PropertyValueFactory<>("distributedAt"));

        // Load all distribution logs asynchronously
        // TODO: Replace with actual DAO/Service call
        /*
        Task<List<DistributionLog>> loadTask = new Task<>() {
            @Override
            protected List<DistributionLog> call() throws Exception {
                return distributionLogService.getAllLogs();
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            logTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            Throwable ex = loadTask.getException();
            ex.printStackTrace();
            errorLabel.setText("Failed to load distribution logs from database");
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
        */

        logTable.setItems(dataList);
    }

    /**
     * Filter distribution logs by date range.
     * Leverages PostgreSQL table partitioning — the query planner will
     * prune partitions outside the requested date range.
     * SQL: WHERE distributed_at >= ? AND distributed_at < ?
     */
    @FXML
    public void handleFilterByDate(ActionEvent event) {
        try {
            errorLabel.setText("");
            String dateFrom = dateFromField.getText();
            String dateTo = dateToField.getText();

            if (dateFrom == null || dateFrom.trim().isEmpty() ||
                dateTo == null || dateTo.trim().isEmpty()) {
                errorLabel.setText("Both From and To dates are required");
                return;
            }

            // Basic date format validation
            if (!dateFrom.matches("\\d{4}-\\d{2}-\\d{2}") || !dateTo.matches("\\d{4}-\\d{2}-\\d{2}")) {
                errorLabel.setText("Date format must be yyyy-MM-dd");
                return;
            }

            // TODO: Call service layer with date range
            // List<DistributionLog> filtered = distributionLogService.getLogsByDateRange(dateFrom, dateTo);
            // searchList.clear();
            // searchList.setAll(filtered);
            // logTable.setItems(searchList);

            errorLabel.setText("TODO: Date range query with partition pruning pending");

        } catch (Exception e) {
            errorLabel.setText("Error filtering by date range");
            e.printStackTrace();
        }
    }

    /**
     * Find a specific distribution log entry by its ID.
     */
    @FXML
    public void handleFindById(ActionEvent event) {
        try {
            errorLabel.setText("");
            String logIdText = logIdField.getText();
            if (logIdText == null || logIdText.trim().isEmpty()) {
                errorLabel.setText("Enter Log ID to search");
                logTable.setItems(dataList);
                return;
            }
            int logId = Integer.parseInt(logIdText);

            // TODO: Call service layer
            // DistributionLog log = distributionLogService.findLogById(logId);
            // if (log != null) {
            //     searchList.clear();
            //     searchList.add(log);
            //     logTable.setItems(searchList);
            // } else {
            //     errorLabel.setText("Log entry not found");
            // }

            errorLabel.setText("TODO: Service layer integration pending");

        } catch (NumberFormatException e) {
            errorLabel.setText("Log ID must be a number");
        } catch (Exception e) {
            errorLabel.setText("Error finding log entry");
            e.printStackTrace();
        }
    }

    /**
     * Reset the table to show all distribution log entries.
     */
    @FXML
    public void handleShowAll(ActionEvent event) {
        logTable.setItems(dataList);
        errorLabel.setText("");
    }

    @FXML
    public void handleClear(ActionEvent event) {
        dateFromField.clear();
        dateToField.clear();
        logIdField.clear();
        errorLabel.setText("");
        logTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }
}
