package com.groupa13.disasterrelief.dao.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.groupa13.disasterrelief.model.DistributionLog;

public final class DistributionLogDao {

    private static final String SELECT_BASE = """
            SELECT dl.log_id, dl.source_shelter, s1.name AS source_shelter_name,
                   dl.dest_shelter, s2.name AS dest_shelter_name,
                   dl.resource_id, res.name AS resource_name,
                   dl.quantity, dl.distributed_at
            FROM distribution_log dl
            JOIN shelters s1 ON dl.source_shelter = s1.shelter_id
            JOIN shelters s2 ON dl.dest_shelter = s2.shelter_id
            JOIN resources res ON dl.resource_id = res.resource_id
            """;

    public List<DistributionLog> findByFilter(Connection connection, LocalDate fromDate,
            LocalDate toDate, Integer logId) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (logId != null && logId > 0) {
            conditions.add("dl.log_id = ?");
            params.add(logId);
        }
        if (fromDate != null) {
            conditions.add("dl.distributed_at >= ?::timestamptz");
            params.add(fromDate.atStartOfDay().toString());
        }
        if (toDate != null) {
            conditions.add("dl.distributed_at <= ?::timestamptz");
            params.add(toDate.plusDays(1).atStartOfDay().toString());
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY dl.distributed_at DESC, dl.log_id DESC");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<DistributionLog> list = new ArrayList<>();
                while (rows.next()) {
                    list.add(map(rows));
                }
                return List.copyOf(list);
            }
        }
    }

    private static DistributionLog map(ResultSet rows) throws SQLException {
        int logId = rows.getInt("log_id");
        int sourceShelter = rows.getInt("source_shelter");
        int destShelter = rows.getInt("dest_shelter");
        int resourceId = rows.getInt("resource_id");
        int quantity = rows.getInt("quantity");
        Timestamp distributedAt = rows.getTimestamp("distributed_at");

        DistributionLog log = new DistributionLog(
                logId, sourceShelter, destShelter, resourceId, quantity,
                distributedAt != null ? distributedAt.toString() : ""
        );
        log.setSourceShelterName(rows.getString("source_shelter_name"));
        log.setDestShelterName(rows.getString("dest_shelter_name"));
        log.setResourceName(rows.getString("resource_name"));
        return log;
    }
}
