package com.groupa13.disasterrelief.controller;

import java.util.List;

import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.UserSession.UserRole;
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

    // TODO: replace with com.groupa13.disasterrelief.model.ZoneDeficit (or a dedicated DTO,
    // since this row is the return shape of fn_zone_deficit_report(disaster_id), not a
    // table). Fields mirror what a CTE comparing required vs. available resources per zone
    // would return: zone, resource, required, available, deficit, urgency classification.
    public static class DeficitRow {
        private final StringProperty zone;
        private final StringProperty resource;
        private final StringProperty required;
        private final StringProperty available;
        private final StringProperty deficit;
        private final StringProperty urgency;

        public DeficitRow(String zone, String resource, int required, int available, String urgency) {
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

    @FXML
    public void initialize() {
        colDeficitZone.setCellValueFactory(new PropertyValueFactory<>("zone"));
        colDeficitResource.setCellValueFactory(new PropertyValueFactory<>("resource"));
        colDeficitRequired.setCellValueFactory(new PropertyValueFactory<>("required"));
        colDeficitAvailable.setCellValueFactory(new PropertyValueFactory<>("available"));
        colDeficitAmount.setCellValueFactory(new PropertyValueFactory<>("deficit"));
        colDeficitUrgency.setCellValueFactory(new PropertyValueFactory<>("urgency"));

        // Color the urgency cell using the status-critical/high/medium/low classes from
        // application.css section 14, matching resource_request.fxml's urgency styling.
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

        deficitTable.setItems(deficitList);

        applyRoleRestrictions();
        loadDisasterOptions();
        loadAllAnalytics();
    }

    /**
     * Per UI_IMPLEMENTATION_PLAN.md section 3.2, ZONE_COORDINATOR only gets the
     * "Zone Deficit Status" view within analytics.fxml — the other three tabs
     * (category roll-up, consumption trends, distribution) are Admin/Analytics-only.
     */
    private void applyRoleRestrictions() {
        UserRole role = UserSession.getInstance().getRole();

        if (role == UserRole.ZONE_COORDINATOR) {
            pageTitleLabel.setText("Zone Deficit Status");
            analyticsTabPane.getTabs().removeAll(categoryRollupTab, consumptionTrendTab, distributionTab);
            analyticsTabPane.getSelectionModel().select(deficitTab);
        } else if (role == UserRole.ANALYTICS_USER) {
            pageTitleLabel.setText("Executive Analytics & Decision Support");
        }
    }

    private void loadDisasterOptions() {
        // TODO: replace with DisasterService.getActiveDisasters() -> SELECT disaster_id, name
        // FROM disasters WHERE status IN ('active','monitoring') (mirrors v_active_disasters).
        disasterFilterCombo.getItems().setAll("D1001 — Kerala Floods 2026", "D1002 — Wayanad Landslide 2026");
        disasterFilterCombo.getSelectionModel().selectFirst();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        errorLabel.setText("");
        loadAllAnalytics();
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
        Task<List<DeficitRow>> task = new Task<>() {
            @Override
            protected List<DeficitRow> call() {
                // TODO: replace with AnalyticsService.getZoneDeficitReport(disasterId) ->
                // SELECT * FROM fn_zone_deficit_report(?). For ZONE_COORDINATOR sessions,
                // Row-Level Security (current_setting('app.current_zone_id')) restricts this
                // to their own zone automatically — no extra WHERE clause needed client-side.
                return List.of(
                        new DeficitRow("Wayanad North Sector", "20L Water Drum", 500, 145, "critical"),
                        new DeficitRow("Wayanad North Sector", "Paracetamol 500mg", 300, 8, "critical"),
                        new DeficitRow("Wayanad South Sector", "Blankets", 200, 160, "medium")
                );
            }
        };
        task.setOnSucceeded(e -> deficitList.setAll(task.getValue()));
        task.setOnFailed(e -> {
            errorLabel.setText("Failed to load zone deficit report");
            if (task.getException() != null) task.getException().printStackTrace();
        });
        Thread t = new Thread(task, "deficit-report-loader");
        t.setDaemon(true);
        t.start();
    }

    private void loadCategoryRollup() {
        Task<XYChart.Series<String, Number>> task = new Task<>() {
            @Override
            protected XYChart.Series<String, Number> call() {
                // TODO: replace with AnalyticsService.getCategoryRollup(disasterId) -> a
                // recursive CTE over resource_categories joined to shelter_inventory that
                // sums quantity across each category's full subtree (Medical -> First Aid
                // -> Bandages, Antiseptic, ... all rolled into "Medical").
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.getData().add(new XYChart.Data<>("Medical", 420));
                series.getData().add(new XYChart.Data<>("Food & Water", 1830));
                series.getData().add(new XYChart.Data<>("Shelter & Bedding", 610));
                return series;
            }
        };
        task.setOnSucceeded(e -> {
            categoryRollupChart.getData().clear();
            categoryRollupChart.getData().add(task.getValue());
        });
        task.setOnFailed(e -> {
            errorLabel.setText("Failed to load category roll-up");
            if (task.getException() != null) task.getException().printStackTrace();
        });
        Thread t = new Thread(task, "category-rollup-loader");
        t.setDaemon(true);
        t.start();
    }

    private void loadConsumptionTrend() {
        Task<XYChart.Series<String, Number>> task = new Task<>() {
            @Override
            protected XYChart.Series<String, Number> call() {
                // TODO: replace with AnalyticsService.getConsumptionTrend(disasterId) ->
                // a window function query: AVG(quantity) OVER (ORDER BY day ROWS BETWEEN
                // 6 PRECEDING AND CURRENT ROW) against distribution_log, grouped by day.
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("7-Day Moving Average");
                series.getData().add(new XYChart.Data<>("Jul 24", 180));
                series.getData().add(new XYChart.Data<>("Jul 25", 210));
                series.getData().add(new XYChart.Data<>("Jul 26", 195));
                series.getData().add(new XYChart.Data<>("Jul 27", 260));
                series.getData().add(new XYChart.Data<>("Jul 28", 240));
                series.getData().add(new XYChart.Data<>("Jul 29", 300));
                series.getData().add(new XYChart.Data<>("Jul 30", 275));
                return series;
            }
        };
        task.setOnSucceeded(e -> {
            consumptionTrendChart.getData().clear();
            consumptionTrendChart.getData().add(task.getValue());
        });
        task.setOnFailed(e -> {
            errorLabel.setText("Failed to load consumption trend");
            if (task.getException() != null) task.getException().printStackTrace();
        });
        Thread t = new Thread(task, "consumption-trend-loader");
        t.setDaemon(true);
        t.start();
    }

    private void loadDistributionBreakdown() {
        Task<ObservableList<PieChart.Data>> task = new Task<>() {
            @Override
            protected ObservableList<PieChart.Data> call() {
                // TODO: replace with AnalyticsService.getDistributionBreakdown(disasterId) ->
                // SELECT * FROM mv_daily_distribution_stats (materialized view). Consider
                // exposing a "last refreshed at" timestamp in the UI so users know this data
                // isn't live — REFRESH MATERIALIZED VIEW CONCURRENTLY only runs on demand
                // (this screen's Refresh button) or on a schedule, not on every write.
                return FXCollections.observableArrayList(
                        new PieChart.Data("Medical", 420),
                        new PieChart.Data("Food & Water", 1830),
                        new PieChart.Data("Shelter & Bedding", 610));
            }
        };
        task.setOnSucceeded(e -> {
            distributionPieChart.setData(task.getValue());
        });
        task.setOnFailed(e -> {
            errorLabel.setText("Failed to load distribution analytics");
            if (task.getException() != null) task.getException().printStackTrace();
        });
        Thread t = new Thread(task, "distribution-breakdown-loader");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }
}
