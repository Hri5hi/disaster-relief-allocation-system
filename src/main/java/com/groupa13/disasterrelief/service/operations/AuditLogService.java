package com.groupa13.disasterrelief.service.operations;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.operations.AuditLogDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.AuditLog;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;

public class AuditLogService {

    private static final Logger LOGGER = Logger.getLogger(AuditLogService.class.getName());

    private final AuditLogDao dao;

    public AuditLogService() {
        this(new AuditLogDao());
    }

    public AuditLogService(AuditLogDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    public ServiceResult<List<AuditLog>> filterLogs(String tableName, String operation,
            String changedBy, LocalDate fromDate, LocalDate toDate, Integer logId) {
        UserSession session = UserSession.getInstance();
        if (session.getRole() != UserRole.SYSTEM_ADMIN) {
            return ServiceResult.failure("Only System Administrators can inspect the audit trail");
        }

        try {
            List<AuditLog> list = Database.inTransaction(session,
                    connection -> dao.findByFilter(connection, tableName, operation, changedBy, fromDate, toDate, logId));
            return ServiceResult.success(list, "Audit logs loaded");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to load audit logs", exception);
            return ServiceResult.failure("Unable to load audit logs");
        }
    }
}
