package com.groupa13.disasterrelief.dao.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.groupa13.disasterrelief.model.operations.DashboardMetrics;
import com.groupa13.disasterrelief.model.operations.ZoneDeficit;

public final class AnalyticsDao {

    public List<ZoneDeficit> getZoneDeficitReport(Connection connection, int disasterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT zone_id, zone_name, resource_id, resource_name,
                       required_qty, available_qty, deficit_qty, urgency_level
                FROM fn_zone_deficit_report(?)
                """)) {
            statement.setInt(1, disasterId);
            try (ResultSet rows = statement.executeQuery()) {
                List<ZoneDeficit> list = new ArrayList<>();
                while (rows.next()) {
                    list.add(new ZoneDeficit(
                            rows.getInt("zone_id"),
                            rows.getString("zone_name"),
                            rows.getInt("resource_id"),
                            rows.getString("resource_name"),
                            rows.getLong("required_qty"),
                            rows.getLong("available_qty"),
                            rows.getLong("deficit_qty"),
                            rows.getString("urgency_level")
                    ));
                }
                return List.copyOf(list);
            }
        }
    }

    public void refreshMaterializedViews(Connection connection) throws SQLException {
        try (PreparedStatement s1 = connection.prepareStatement(
                "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_zone_resource_summary");
             PreparedStatement s2 = connection.prepareStatement(
                "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_distribution_stats")) {
            s1.execute();
            s2.execute();
        }
    }

    public DashboardMetrics getDashboardMetrics(Connection connection, Integer zoneId) throws SQLException {
        int activeDisasters = queryCount(connection, "SELECT COUNT(*) FROM disasters WHERE status IN ('active', 'monitoring')");

        String reqSql = (zoneId != null && zoneId > 0)
                ? "SELECT COUNT(*) FROM resource_requests WHERE status = 'pending' AND zone_id = " + zoneId
                : "SELECT COUNT(*) FROM resource_requests WHERE status = 'pending'";
        int pendingRequests = queryCount(connection, reqSql);

        int totalVolunteers = queryCount(connection, "SELECT COUNT(*) FROM volunteers");
        int operationalShelters = queryCount(connection, "SELECT COUNT(*) FROM shelters WHERE status = 'operational'");
        int lowStock = queryCount(connection, "SELECT COUNT(*) FROM shelter_inventory WHERE quantity < reorder_level");
        int totalDistributions = queryCount(connection, "SELECT COUNT(*) FROM distribution_log");

        return new DashboardMetrics(
                activeDisasters, pendingRequests, totalVolunteers,
                operationalShelters, lowStock, totalDistributions
        );
    }

    private static int queryCount(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            if (rows.next()) {
                return rows.getInt(1);
            }
            return 0;
        }
    }
}
