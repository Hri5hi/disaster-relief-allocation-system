package com.groupa13.disasterrelief.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.groupa13.disasterrelief.model.DistributionLog;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.operations.DistributionLogService;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
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

public class DistributionLogController extends Utility {

    @FXML private TextField dateFromField;
    @FXML private TextField dateToField;
    @FXML private TextField logIdField;
    @FXML private Label errorLabel;
    @FXML private Label roleBadge;

    @FXML private TableView<DistributionLog> logTable;
    @FXML private TableColumn<DistributionLog, Integer> colLogId;
    @FXML private TableColumn<DistributionLog, String> colSourceShelter;
    @FXML private TableColumn<DistributionLog, String> colDestShelter;
    @FXML private TableColumn<DistributionLog, String> colResourceName;
    @FXML private TableColumn<DistributionLog, Integer> colQuantity;
    @FXML private TableColumn<DistributionLog, String> colDistributedAt;

    private final ObservableList<DistributionLog> dataList = FXCollections.observableArrayList();
    private final DistributionLogService distributionLogService;

    public DistributionLogController() {
        this(new DistributionLogService());
    }

    public DistributionLogController(DistributionLogService distributionLogService) {
        this.distributionLogService = distributionLogService;
    }

    @FXML
    public void initialize() {
        colLogId.setCellValueFactory(new PropertyValueFactory<>("logId"));
        colSourceShelter.setCellValueFactory(new PropertyValueFactory<>("sourceShelterName"));
        colDestShelter.setCellValueFactory(new PropertyValueFactory<>("destShelterName"));
        colResourceName.setCellValueFactory(new PropertyValueFactory<>("resourceName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDistributedAt.setCellValueFactory(new PropertyValueFactory<>("distributedAt"));

        UserRole role = UserSession.getInstance().getRole();
        if (roleBadge != null) {
            roleBadge.setText(role != null ? role.getDisplayName() : "");
        }

        logTable.setItems(dataList);
        loadLogs(null, null, null);
    }

    private void loadLogs(LocalDate fromDate, LocalDate toDate, Integer logId) {
        errorLabel.setText("Loading distribution logs...");

        Task<ServiceResult<List<DistributionLog>>> task = new Task<>() {
            @Override
            protected ServiceResult<List<DistributionLog>> call() {
                return distributionLogService.filterLogs(fromDate, toDate, logId);
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<List<DistributionLog>> result = task.getValue();
            if (result != null && result.success()) {
                dataList.setAll(result.value());
                errorLabel.setText("");
            } else {
                errorLabel.setText(result != null ? result.message() : "Failed to load logs");
            }
        });

        task.setOnFailed(e -> errorLabel.setText("Database connection failure"));

        Thread thread = new Thread(task, "dist-log-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleFilterByDate(ActionEvent event) {
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

        loadLogs(from, to, null);
    }

    @FXML
    public void handleFindById(ActionEvent event) {
        String logIdText = logIdField.getText();
        if (logIdText == null || logIdText.isBlank()) {
            errorLabel.setText("Enter a Log ID to search");
            return;
        }

        int logId;
        try {
            logId = Integer.parseInt(logIdText.trim());
        } catch (NumberFormatException e) {
            errorLabel.setText("Log ID must be an integer");
            return;
        }

        loadLogs(null, null, logId);
    }

    @FXML
    public void handleShowAll(ActionEvent event) {
        handleClear(event);
    }

    @FXML
    public void handleClear(ActionEvent event) {
        dateFromField.clear();
        dateToField.clear();
        logIdField.clear();
        errorLabel.setText("");
        loadLogs(null, null, null);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }
}
