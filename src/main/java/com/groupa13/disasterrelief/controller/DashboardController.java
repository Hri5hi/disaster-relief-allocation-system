package com.groupa13.disasterrelief.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.Utility;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Dynamic role-adapted dashboard. Stat cards and navigation cards are not
 * hard-coded in FXML -- they are built at runtime from {@link UserSession#getRole()}
 * so every one of the 5 roles sees only the stats and screens relevant to them.
 */
public class DashboardController extends Utility {

    @FXML
    private Label headerTitle;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label roleBadge;
    @FXML
    private Label overviewTitle;
    @FXML
    private Label statusLabel;
    @FXML
    private FlowPane statCardsPane;
    @FXML
    private FlowPane navCardsPane;

    /** stat key -> the Label showing its value, so the async load task can update it. */
    private final Map<String, Label> statValueLabels = new LinkedHashMap<>();

    private static final String[] STAT_CARD_STYLES =
            { "stat-card-blue", "stat-card-green", "stat-card-gray", "stat-card-purple" };
    private static final String[] NAV_CARD_STYLES =
            { "btn-nav", "btn-nav-green", "btn-nav-gray" };

    @FXML
    public void initialize() {
        UserSession session = UserSession.getInstance();
        UserRole role = session.getRole();

        userNameLabel.setText(session.getUserName() == null ? "" : session.getUserName());
        roleBadge.setText(role == null ? "" : role.getDisplayName());
        roleBadge.getStyleClass().removeIf(c -> c.startsWith("role-badge-"));
        roleBadge.getStyleClass().add(roleBadgeStyleFor(role));

        headerTitle.setText(headerTitleFor(role, session));
        overviewTitle.setText("Overview");

        buildStatCards(role);
        buildNavCards(role);
        loadStats(role);
    }

    private String headerTitleFor(UserRole role, UserSession session) {
        if (role == null) {
            return "Dashboard";
        }
        switch (role) {
            case SYSTEM_ADMIN:
                return "System Administration & Overview";
            case ZONE_COORDINATOR:
                // TODO: replace static "Zone Operations" text with the coordinator's
                // actual zone name once ZoneService/DAO can resolve assignedZoneId.
                return "Zone Operations" + (session.getAssignedZoneId() != null
                        ? " — Zone #" + session.getAssignedZoneId() : "");
            case WAREHOUSE_MANAGER:
                return "Inventory & Fulfillment Center";
            case VOLUNTEER_COORDINATOR:
                return "Volunteer Management & Deployment";
            case ANALYTICS_USER:
                return "Executive Analytics & Decision Support";
            default:
                return "Dashboard";
        }
    }

    private String roleBadgeStyleFor(UserRole role) {
        if (role == null) {
            return "role-badge-admin";
        }
        switch (role) {
            case SYSTEM_ADMIN:
                return "role-badge-admin";
            case ZONE_COORDINATOR:
                return "role-badge-zone";
            case WAREHOUSE_MANAGER:
                return "role-badge-warehouse";
            case VOLUNTEER_COORDINATOR:
                return "role-badge-volunteer";
            case ANALYTICS_USER:
                return "role-badge-analytics";
            default:
                return "role-badge-admin";
        }
    }

    // ------------------------------------------------------------------
    // Stat cards
    // ------------------------------------------------------------------

    private record StatDef(String key, String label, String icon) {
    }

    private List<StatDef> statDefsFor(UserRole role) {
        List<StatDef> defs = new ArrayList<>();
        if (role == null) {
            return defs;
        }
        switch (role) {
            case SYSTEM_ADMIN:
                defs.add(new StatDef("activeDisasters", "Active Disasters", "fas-exclamation-triangle"));
                defs.add(new StatDef("totalShelters", "Total Shelters", "fas-home"));
                defs.add(new StatDef("pendingRequests", "Pending Requests", "fas-clipboard-list"));
                defs.add(new StatDef("activeVolunteers", "Active Volunteers", "fas-hands-helping"));
                defs.add(new StatDef("totalDonations", "Total Donations", "fas-gift"));
                defs.add(new StatDef("auditEvents", "System Audit Events", "fas-history"));
                break;
            case ZONE_COORDINATOR:
                defs.add(new StatDef("zoneSeverity", "Assigned Zone Severity", "fas-exclamation-triangle"));
                defs.add(new StatDef("zoneOccupancy", "Zone Shelter Occupancy %", "fas-home"));
                defs.add(new StatDef("pendingZoneRequests", "Pending Zone Requests", "fas-clipboard-list"));
                defs.add(new StatDef("affectedInZone", "Affected Persons in Zone", "fas-user-injured"));
                break;
            case WAREHOUSE_MANAGER:
                defs.add(new StatDef("lowStockAlerts", "Low-Stock Alerts", "fas-bell"));
                defs.add(new StatDef("pendingFulfillments", "Pending Fulfillments", "fas-clipboard-list"));
                defs.add(new StatDef("totalStockUnits", "Total Items in Stock", "fas-boxes"));
                defs.add(new StatDef("recentDonations", "Recent Donations", "fas-gift"));
                break;
            case VOLUNTEER_COORDINATOR:
                defs.add(new StatDef("totalVolunteers", "Total Volunteers", "fas-hands-helping"));
                defs.add(new StatDef("shiftsToday", "Shifts Scheduled Today", "fas-calendar-alt"));
                defs.add(new StatDef("unassignedVolunteers", "Unassigned Volunteers", "fas-user-clock"));
                defs.add(new StatDef("skillTypes", "Skill Types Count", "fas-tools"));
                break;
            case ANALYTICS_USER:
                defs.add(new StatDef("criticalZones", "Critical Zone Count", "fas-exclamation-triangle"));
                defs.add(new StatDef("totalDeficit", "Overall Deficit Severity", "fas-chart-line"));
                defs.add(new StatDef("consumptionRate", "7-Day Supply Consumption Rate", "fas-chart-area"));
                defs.add(new StatDef("shelterUtilization", "Shelter Utilization %", "fas-home"));
                break;
            default:
                break;
        }
        return defs;
    }

    private void buildStatCards(UserRole role) {
        statCardsPane.getChildren().clear();
        statValueLabels.clear();

        List<StatDef> defs = statDefsFor(role);
        int i = 0;
        for (StatDef def : defs) {
            String cardStyle = STAT_CARD_STYLES[i % STAT_CARD_STYLES.length];
            i++;

            Label statLabel = new Label(def.label());
            statLabel.setMaxWidth(Double.MAX_VALUE);
            statLabel.getStyleClass().add("stat-label");
            HBox.setHgrow(statLabel, javafx.scene.layout.Priority.ALWAYS);

            FontIcon icon = new FontIcon(def.icon());
            icon.getStyleClass().add("icon-stat");

            HBox headerRow = new HBox(statLabel, icon);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            Label valueLabel = new Label("...");
            valueLabel.getStyleClass().add("stat-number");
            statValueLabels.put(def.key(), valueLabel);

            VBox card = new VBox(12, headerRow, valueLabel);
            card.getStyleClass().addAll("stat-card", cardStyle);

            statCardsPane.getChildren().add(card);
        }
    }

    // ------------------------------------------------------------------
    // Navigation cards
    // ------------------------------------------------------------------

    private record NavDef(String label, String icon, String fxml, String title) {
    }

    private List<NavDef> navDefsFor(UserRole role) {
        List<NavDef> defs = new ArrayList<>();
        if (role == null) {
            return defs;
        }
        switch (role) {
            case SYSTEM_ADMIN:
                defs.add(new NavDef("Disaster Management", "fas-exclamation-triangle", "disaster", "Disaster Management"));
                defs.add(new NavDef("Zone Management", "fas-map-marked-alt", "zone", "Zone Management"));
                defs.add(new NavDef("Shelter Management", "fas-home", "shelter", "Shelter Management"));
                defs.add(new NavDef("Resource Master & Categories", "fas-boxes", "resource", "Resource Catalog"));
                defs.add(new NavDef("Inventory Overview", "fas-warehouse", "inventory", "Inventory Overview"));
                defs.add(new NavDef("Resource Requests", "fas-clipboard-list", "resource_request", "Resource Requests"));
                defs.add(new NavDef("Distribution Log", "fas-truck", "distribution_log", "Distribution Log"));
                defs.add(new NavDef("Volunteer Directory", "fas-hands-helping", "volunteer", "Volunteer Directory"));
                defs.add(new NavDef("Volunteer Shifts", "fas-calendar-alt", "volunteer_shift", "Volunteer Shifts"));
                defs.add(new NavDef("Affected Persons", "fas-user-injured", "affected_person", "Affected Persons"));
                defs.add(new NavDef("Donation Intake", "fas-gift", "donation", "Donation Intake"));
                defs.add(new NavDef("Analytics & Reports", "fas-chart-line", "analytics", "Analytics & Reports"));
                defs.add(new NavDef("Audit Log Viewer", "fas-history", "audit_log", "Audit Log Viewer"));
                break;
            case ZONE_COORDINATOR:
                defs.add(new NavDef("Submit & Track Resource Requests", "fas-clipboard-list", "resource_request", "Resource Requests"));
                defs.add(new NavDef("Register Displaced Persons", "fas-user-injured", "affected_person", "Affected Persons"));
                defs.add(new NavDef("View Zone Shelters", "fas-home", "shelter", "Zone Shelters"));
                defs.add(new NavDef("Zone Deficit Status", "fas-chart-line", "analytics", "Zone Deficit Status"));
                break;
            case WAREHOUSE_MANAGER:
                defs.add(new NavDef("Shelter Inventory Management", "fas-warehouse", "inventory", "Inventory Management"));
                defs.add(new NavDef("Fulfill Resource Requests", "fas-bolt", "resource_request", "Fulfill Requests"));
                defs.add(new NavDef("Record Incoming Donations", "fas-gift", "donation", "Donation Intake"));
                defs.add(new NavDef("Resource Transfer Log", "fas-truck", "distribution_log", "Distribution Log"));
                break;
            case VOLUNTEER_COORDINATOR:
                defs.add(new NavDef("Volunteer Directory", "fas-hands-helping", "volunteer", "Volunteer Directory"));
                defs.add(new NavDef("Shift Scheduling", "fas-calendar-alt", "volunteer_shift", "Shift Scheduling"));
                defs.add(new NavDef("Volunteer Availability View", "fas-user-clock", "volunteer", "Volunteer Availability"));
                break;
            case ANALYTICS_USER:
                defs.add(new NavDef("Analytics Suite", "fas-chart-line", "analytics", "Analytics Suite"));
                defs.add(new NavDef("Shelter Occupancy Dashboard", "fas-home", "shelter", "Shelter Occupancy"));
                defs.add(new NavDef("Zone Deficit Summary", "fas-chart-area", "analytics", "Zone Deficit Summary"));
                defs.add(new NavDef("Distribution Analytics", "fas-truck", "distribution_log", "Distribution Analytics"));
                break;
            default:
                break;
        }
        return defs;
    }

    private void buildNavCards(UserRole role) {
        navCardsPane.getChildren().clear();

        List<NavDef> defs = navDefsFor(role);
        int i = 0;
        for (NavDef def : defs) {
            String navStyle = NAV_CARD_STYLES[i % NAV_CARD_STYLES.length];
            i++;

            Button button = new Button(def.label());
            button.setMaxWidth(Double.MAX_VALUE);
            button.setPrefWidth(340.0);
            button.getStyleClass().add(navStyle);

            FontIcon icon = new FontIcon(def.icon());
            icon.getStyleClass().add("icon-sm");
            button.setGraphic(icon);

            button.setOnAction((ActionEvent event) -> {
                // NOTE: Utility.changeScene enforces RBAC via allowedRoles;
                // dashboard only *offers* cards the role is entitled to, but the
                // guard is the actual enforcement point (defends against a bug here).
                changeScene(event, def.fxml(), def.title());
            });

            navCardsPane.getChildren().add(button);
        }
    }

    // ------------------------------------------------------------------
    // Stats loading
    // ------------------------------------------------------------------

    private void loadStats(UserRole role) {
        statusLabel.setText("Loading dashboard metrics...");

        Task<Map<String, Integer>> loadStatsTask = new Task<>() {
            @Override
            protected Map<String, Integer> call() {
                // TODO: replace with real calls to the DAO/service layer, e.g.
                // DashboardService.getStatsForRole(role, UserSession.getInstance()),
                // which should pull from v_shelter_dashboard, v_pending_requests_ranked,
                // v_active_disasters, mv_zone_resource_summary, etc. depending on role.
                Map<String, Integer> dummy = new LinkedHashMap<>();
                for (String key : statValueLabels.keySet()) {
                    dummy.put(key, 0);
                }
                return dummy;
            }
        };

        loadStatsTask.setOnSucceeded(e -> {
            Map<String, Integer> stats = loadStatsTask.getValue();
            stats.forEach((key, value) -> {
                Label label = statValueLabels.get(key);
                if (label != null) {
                    label.setText(String.valueOf(value));
                }
            });
            statusLabel.setText("Connected to Database");
        });

        loadStatsTask.setOnFailed(e -> {
            statValueLabels.values().forEach(label -> label.setText("0"));
            statusLabel.setText("Database Connection Failed!");
            if (loadStatsTask.getException() != null) {
                loadStatsTask.getException().printStackTrace();
            }
        });

        Thread statsThread = new Thread(loadStatsTask, "dashboard-stats-loader");
        statsThread.setDaemon(true);
        statsThread.start();
    }
}
