package com.groupa13.disasterrelief.dao.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.ResourceRequest;
import com.groupa13.disasterrelief.model.operations.AllocationResult;

public final class ResourceRequestDao {

    private static final String SELECT_BASE = """
            SELECT r.request_id, r.zone_id, z.name AS zone_name,
                   r.resource_id, res.name AS resource_name,
                   r.destination_shelter_id, sh.name AS destination_shelter_name,
                   r.quantity_requested, r.urgency, r.priority_score,
                   r.status, r.requested_at, r.fulfilled_at
            FROM resource_requests r
            JOIN zones z ON r.zone_id = z.zone_id
            JOIN resources res ON r.resource_id = res.resource_id
            JOIN shelters sh ON r.destination_shelter_id = sh.shelter_id
            """;

    public ResourceRequest create(Connection connection, int zoneId, int resourceId,
            int destinationShelterId, int quantity, String urgency) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO resource_requests (zone_id, resource_id, destination_shelter_id, quantity_requested, urgency)
                VALUES (?, ?, ?, ?, ?)
                RETURNING request_id
                """)) {
            statement.setInt(1, zoneId);
            statement.setInt(2, resourceId);
            statement.setInt(3, destinationShelterId);
            statement.setInt(4, quantity);
            statement.setString(5, urgency);

            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                int requestId = rows.getInt("request_id");
                return findById(connection, requestId).orElseThrow();
            }
        }
    }

    public Optional<ResourceRequest> findById(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SELECT_BASE + " WHERE r.request_id = ?")) {
            statement.setInt(1, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.of(map(rows));
                }
                return Optional.empty();
            }
        }
    }

    public List<ResourceRequest> findAll(Connection connection, Integer zoneIdFilter, String statusFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();

        List<String> conditions = new ArrayList<>();
        if (zoneIdFilter != null && zoneIdFilter > 0) {
            conditions.add("r.zone_id = ?");
            params.add(zoneIdFilter);
        }
        if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("All")) {
            conditions.add("r.status = ?");
            params.add(statusFilter.toLowerCase());
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY r.priority_score DESC, r.requested_at ASC");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<ResourceRequest> list = new ArrayList<>();
                while (rows.next()) {
                    list.add(map(rows));
                }
                return List.copyOf(list);
            }
        }
    }

    public boolean cancelRequest(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE resource_requests
                SET status = 'cancelled'
                WHERE request_id = ? AND status = 'pending'
                """)) {
            statement.setInt(1, requestId);
            return statement.executeUpdate() > 0;
        }
    }

    public AllocationResult callAllocateResources(Connection connection, int requestId, int sourceShelterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT log_id, request_id, source_shelter, dest_shelter, resource_id, quantity, fulfilled_at
                FROM fn_allocate_resources(?, ?)
                """)) {
            statement.setInt(1, requestId);
            statement.setInt(2, sourceShelterId);

            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Allocation function did not return a result row");
                }
                int logId = rows.getInt("log_id");
                int reqId = rows.getInt("request_id");
                int sourceId = rows.getInt("source_shelter");
                int destId = rows.getInt("dest_shelter");
                int resId = rows.getInt("resource_id");
                int qty = rows.getInt("quantity");
                Timestamp ts = rows.getTimestamp("fulfilled_at");
                String fulfilledAt = ts != null ? ts.toString() : "";

                return new AllocationResult(logId, reqId, sourceId, destId, resId, qty, fulfilledAt);
            }
        }
    }

    private static ResourceRequest map(ResultSet rows) throws SQLException {
        int requestId = rows.getInt("request_id");
        int zoneId = rows.getInt("zone_id");
        int resourceId = rows.getInt("resource_id");
        int destShelterId = rows.getInt("destination_shelter_id");
        int quantity = rows.getInt("quantity_requested");
        String urgency = rows.getString("urgency");
        int priorityScore = rows.getInt("priority_score");
        String status = rows.getString("status");
        Timestamp reqAt = rows.getTimestamp("requested_at");
        Timestamp fulAt = rows.getTimestamp("fulfilled_at");

        ResourceRequest req = new ResourceRequest(
                requestId, zoneId, resourceId, destShelterId, quantity, urgency,
                priorityScore, status,
                reqAt != null ? reqAt.toString() : "",
                fulAt != null ? fulAt.toString() : null
        );
        req.setZoneName(rows.getString("zone_name"));
        req.setResourceName(rows.getString("resource_name"));
        req.setDestinationShelterName(rows.getString("destination_shelter_name"));
        return req;
    }
}
