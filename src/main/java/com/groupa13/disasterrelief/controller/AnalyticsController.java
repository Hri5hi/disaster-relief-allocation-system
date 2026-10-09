package com.groupa13.disasterrelief.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.operations.ZoneDeficit;
import com.groupa13.disasterrelief.service.core.DisasterService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.operations.AnalyticsService;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.Utility;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class AnalyticsController extends Utility {

    public static class DeficitRow {
        private final StringProperty zone;
        private final StringProperty resource;
        private final StringProperty required;
        private final StringProperty available;
        private final StringProperty deficit;
        private final StringProperty urgency;

        public DeficitRow(String zone, String resource, long required, long available, String urgency) {
            this.zone = new SimpleStringProperty(zone);
            this.resource = new SimpleStringProperty(resource);
            this.required = new SimpleStringProperty(String.valueOf(required));
            this.available = new SimpleStringProperty(String.valueOf(available));
            this.deficit = new SimpleStringProperty(String.valueOf(Math.max(0, required - available)));
            this.urgency = new SimpleStringProperty(urgency);
        }

        public String getZone() { return zone.get(); }
        public String getResource() { return resource.get(); }
        public String getRequired() { return required.get(); }
        public String getAvailable() { return available.get(); }
        public String getDeficit() { return deficit.get(); }
        public String getUrgency() { return urgency.get(); }
    }

    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Label roleBadge;
    @FXML private Label errorLabel;
    @FXML private ComboBox<String> disasterFilterCombo;
    @FXML private TabPane analyticsTabPane;

    @FXML private Tab deficitTab;
    @FXML private Tab categoryRollupTab;
    @FXML private Tab consumptionTrendTab;
    @FXML private Tab distributionTab;

    @FXML private TableView<DeficitRow> deficitTable;
    @FXML private TableColumn<DeficitRow, String> colDeficitZone;
    @FXML private TableColumn<DeficitRow, String> colDeficitResource;
    @FXML private TableColumn<DeficitRow, String> colDeficitRequired;
    @FXML private TableColumn<DeficitRow, String> colDeficitAvailable;
    @FXML private TableColumn<DeficitRow, String> colDeficitAmount;
    @FXML private TableColumn<DeficitRow, String> colDeficitUrgency;

    @FXML private BarChart<String, Number> categoryRollupChart;
    @FXML private LineChart<String, Number> consumptionTrendChart;
    @FXML private PieChart distributionPieChart;

    private final ObservableList<DeficitRow> deficitList = FXCollections.observableArrayList();
    private final Map<String, Integer> disasterNameToId = new HashMap<>();

    private final AnalyticsService analyticsService;
    private final DisasterService disasterService;

    public AnalyticsController() {
        this(new AnalyticsService(), new DisasterService());
    }

    public AnalyticsController(AnalyticsService analyticsService, DisasterService disasterService) {
        this.analyticsService = analyticsService;
        this.disasterService = disasterService;
    }

    @FXML
    public void initialize() {
        colDeficitZone.setCellValueFactory(new PropertyValueFactory<>("zone"));
        colDeficitResource.setCellValueFactory(new PropertyValueFactory<>("resource"));
        colDeficitRequired.setCellValueFactory(new PropertyValueFactory<>("required"));
        colDeficitAvailable.setCellValueFactory(new PropertyValueFactory<>("available"));
        colDeficitAmount.setCellValueFactory(new PropertyValueFactory<>("deficit"));
        colDeficitUrgency.setCellValueFactory(new PropertyValueFactory<>("urgency"));
        deficitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colDeficitUrgency.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String urgency, boolean empty) {
                super.updateItem(urgency, empty);
                getStyleClass().removeAll("status-critical", "status-high", "status-medium", "status-low");
                if (empty || urgency == null) {
                    setText(null);
                    return;
                }
                setText(urgency);
                getStyleClass().add("status-" + urgency.toLowerCase());
            }
        });

        colDeficitAmount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String deficit, boolean empty) {
                super.updateItem(deficit, empty);
                getStyleClass().removeAll("deficit-positive", "deficit-clear");
                if (empty || deficit == null) {
                    setText(null);
                    return;
                }
                setText(deficit);
                try {
                    getStyleClass().add(Long.parseLong(deficit) > 0
                            ? "deficit-positive"
                            : "deficit-clear");
                } catch (NumberFormatException ignored) {
                    // Keep the value visible without a status color if a report
                    // source ever supplies a non-numeric deficit label.
                }
            }
        });

        deficitTable.setItems(deficitList);

        applyRoleRestrictions();
        loadDisastersAndAnalytics();
    }

    private void applyRoleRestrictions() {
        UserRole role = UserSession.getInstance().getRole();

        if (role == UserRole.ZONE_COORDINATOR) {
            applyRoleContext(
                    "Zone Deficit Status",
                    "Review zone-scoped shortages and urgency for the selected disaster.",
                    "ZONE SCOPED",
                    "role-badge-zone");
            analyticsTabPane.getTabs().removeAll(categoryRollupTab, consumptionTrendTab, distributionTab);
            analyticsTabPane.getSelectionModel().select(deficitTab);
        } else if (role == UserRole.ANALYTICS_USER) {
            applyRoleContext(
                    "Executive Analytics & Decision Support",
                    "Explore shortages, inventory mix, consumption, and distribution trends.",
                    "ANALYTICS USER",
                    "role-badge-analytics");
        } else {
            applyRoleContext(
                    "Analytics & Reports",
                    "Monitor shortages, inventory mix, consumption, and distribution activity.",
                    "SYSTEM ADMIN",
                    "role-badge-admin");
        }
    }

    /** Applies presentation-only role context; access remains enforced by navigation RBAC. */
    private void applyRoleContext(String title, String subtitle, String badgeText,
                                  String badgeStyleClass) {
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle);
        roleBadge.setText(badgeText);
        roleBadge.getStyleClass().removeAll(
                "role-badge-admin", "role-badge-zone", "role-badge-analytics");
        roleBadge.getStyleClass().add(badgeStyleClass);
    }

    private void loadDisastersAndAnalytics() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                ServiceResult<List<EntityOption>> result = disasterService.listOpenDisasterOptions();
                if (result.success()) {
                    for (EntityOption d : result.value()) {
                        disasterNameToId.put("D" + d.id() + " — " + d.name(), d.id());
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            disasterFilterCombo.getItems().setAll(disasterNameToId.keySet());
            if (!disasterFilterCombo.getItems().isEmpty()) {
                disasterFilterCombo.getSelectionModel().selectFirst();
            }
            loadAllAnalytics();
        });

        task.setOnFailed(e -> errorLabel.setText("Failed to load disasters"));

        Thread thread = new Thread(task, "analytics-disaster-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        errorLabel.setText("Refreshing analytics...");

        UserRole role = UserSession.getInstance().getRole();
        if (role == UserRole.SYSTEM_ADMIN) {
            Task<ServiceResult<Void>> refreshTask = new Task<>() {
                @Override
                protected ServiceResult<Void> call() {
                    return analyticsService.refreshViews();
                }
            };
            refreshTask.setOnSucceeded(e -> loadAllAnalytics());
            refreshTask.setOnFailed(e -> loadAllAnalytics());
            Thread thread = new Thread(refreshTask, "mv-refresh-worker");
            thread.setDaemon(true);
            thread.start();
        } else {
            loadAllAnalytics();
        }
    }

    private void loadAllAnalytics() {
        loadDeficitReport();
        if (analyticsTabPane.getTabs().contains(categoryRollupTab)) {
            loadCategoryRollup();
        }
        if (analyticsTabPane.getTabs().contains(consumptionTrendTab)) {
            loadConsumptionTrend();
        }
        if (analyticsTabPane.getTabs().contains(distributionTab)) {
            loadDistributionBreakdown();
        }
    }

    private void loadDeficitReport() {
        String selected = disasterFilterCombo.getValue();
        Integer disasterId = selected != null ? disasterNameToId.get(selected) : 1;
        if (disasterId == null) {
            disasterId = 1;
        }

        final int targetDisasterId = disasterId;
        Task<ServiceResult<List<ZoneDeficit>>> task = new Task<>() {
            @Override
            protected ServiceResult<List<ZoneDeficit>> call() {
                return analyticsService.getZoneDeficitReport(targetDisasterId);
            }
        };

        task.setOnSucceeded(e -> {
            ServiceResult<List<ZoneDeficit>> result = task.getValue();
            if (result != null && result.success()) {
                List<DeficitRow> rows = new ArrayList<>();
                for (ZoneDeficit d : result.value()) {
                    rows.add(new DeficitRow(
                            d.zoneName(),
                            d.resourceName(),
                            d.requiredQty(),
                            d.availableQty(),
                            d.urgencyLevel()
                    ));
                }
                deficitList.setAll(rows);
                errorLabel.setText("");
            } else {
                errorLabel.setText(result != null ? result.message() : "Failed to load deficit report");
            }
        });

        task.setOnFailed(e -> errorLabel.setText("Deficit report task failed"));

        Thread t = new Thread(task, "deficit-report-loader");
        t.setDaemon(true);
        t.start();
    }

    private void loadCategoryRollup() {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Medical", 65));
        series.getData().add(new XYChart.Data<>("Food & Water", 430));
        series.getData().add(new XYChart.Data<>("Shelter & Clothing", 300));

        categoryRollupChart.getData().clear();
        categoryRollupChart.getData().add(series);
    }

    private void loadConsumptionTrend() {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Historical Transfers");
        series.getData().add(new XYChart.Data<>("Sep 28", 120));
        series.getData().add(new XYChart.Data<>("Sep 29", 150));
        series.getData().add(new XYChart.Data<>("Sep 30", 180));
        series.getData().add(new XYChart.Data<>("Oct 01", 100));
        series.getData().add(new XYChart.Data<>("Oct 02", 50));

        consumptionTrendChart.getData().clear();
        consumptionTrendChart.getData().add(series);
    }

    private void loadDistributionBreakdown() {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                new PieChart.Data("Packaged Water", 100),
                new PieChart.Data("Woolen Blankets", 50)
        );
        distributionPieChart.setData(pieData);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }
}
