package com.groupa13.disasterrelief.model;

/**
 * Model for the 'audit_log' table (trigger-populated).
 * Schema: project_description_v2.md Table 14
 *
 * This table is NEVER written to by Java — it is populated entirely by the
 * trg_audit_log trigger on INSERT/UPDATE/DELETE of critical tables.
 * The UI provides a read-only viewer with JSON diff capabilities.
 */
public class AuditLog {

    private int logId;
    private String tableName;     // Which table was modified
    private String operation;     // 'INSERT', 'UPDATE', or 'DELETE'
    private String oldValues;     // JSONB — previous row state (NULL for INSERT)
    private String newValues;     // JSONB — new row state (NULL for DELETE)
    private String changedBy;     // session_user captured by trigger
    private String changedAt;     // TIMESTAMPTZ as display string

    public AuditLog() {
    }

    public AuditLog(int logId, String tableName, String operation,
                    String oldValues, String newValues,
                    String changedBy, String changedAt) {
        this.logId = logId;
        this.tableName = tableName;
        this.operation = operation;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    // --- Getters ---

    public int getLogId() {
        return logId;
    }

    public String getTableName() {
        return tableName;
    }

    public String getOperation() {
        return operation;
    }

    public String getOldValues() {
        return oldValues;
    }

    public String getNewValues() {
        return newValues;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getChangedAt() {
        return changedAt;
    }

    // --- Setters ---

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setOldValues(String oldValues) {
        this.oldValues = oldValues;
    }

    public void setNewValues(String newValues) {
        this.newValues = newValues;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public void setChangedAt(String changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public String toString() {
        return "Audit #" + logId + " [" + operation + " on " + tableName + "]";
    }
}
