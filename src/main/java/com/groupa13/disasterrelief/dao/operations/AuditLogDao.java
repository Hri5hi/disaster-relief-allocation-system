package com.groupa13.disasterrelief.dao.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.groupa13.disasterrelief.model.AuditLog;

public final class AuditLogDao {

    private static final String SELECT_BASE = """
            SELECT log_id, table_name, operation,
                   old_values::text AS old_values,
                   new_values::text AS new_values,
                   changed_by, changed_at
            FROM audit_log
            """;

    public List<AuditLog> findByFilter(Connection connection, String tableName,
            String operation, String changedBy, LocalDate fromDate, LocalDate toDate,
            Integer logId) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (logId != null && logId > 0) {
            conditions.add("log_id = ?");
            params.add(logId);
        }
        if (tableName != null && !tableName.isBlank() && !tableName.equalsIgnoreCase("All")) {
            conditions.add("table_name = ?");
            params.add(tableName.trim().toLowerCase());
        }
        if (operation != null && !operation.isBlank() && !operation.equalsIgnoreCase("All")) {
            conditions.add("operation = ?");
            params.add(operation.trim().toUpperCase());
        }
        if (changedBy != null && !changedBy.isBlank()) {
            conditions.add("changed_by ILIKE ?");
            params.add("%" + changedBy.trim() + "%");
        }
        if (fromDate != null) {
            conditions.add("changed_at >= ?::timestamptz");
            params.add(fromDate.atStartOfDay().toString());
        }
        if (toDate != null) {
            conditions.add("changed_at <= ?::timestamptz");
            params.add(toDate.plusDays(1).atStartOfDay().toString());
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY changed_at DESC, log_id DESC LIMIT 500");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<AuditLog> list = new ArrayList<>();
                while (rows.next()) {
                    list.add(map(rows));
                }
                return List.copyOf(list);
            }
        }
    }

    private static AuditLog map(ResultSet rows) throws SQLException {
        int logId = rows.getInt("log_id");
        String tableName = rows.getString("table_name");
        String operation = rows.getString("operation");
        String oldValues = rows.getString("old_values");
        String newValues = rows.getString("new_values");
        String changedBy = rows.getString("changed_by");
        Timestamp changedAt = rows.getTimestamp("changed_at");

        return new AuditLog(
                logId, tableName, operation, oldValues, newValues, changedBy,
                changedAt != null ? changedAt.toString() : ""
        );
    }
}
