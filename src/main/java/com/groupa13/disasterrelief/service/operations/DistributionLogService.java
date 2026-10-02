package com.groupa13.disasterrelief.service.operations;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.operations.DistributionLogDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.DistributionLog;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.UserSession;

public class DistributionLogService {

    private static final Logger LOGGER = Logger.getLogger(DistributionLogService.class.getName());

    private final DistributionLogDao dao;

    public DistributionLogService() {
        this(new DistributionLogDao());
    }

    public DistributionLogService(DistributionLogDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    public ServiceResult<List<DistributionLog>> filterLogs(LocalDate fromDate, LocalDate toDate, Integer logId) {
        UserSession session = UserSession.getInstance();
        try {
            List<DistributionLog> list = Database.inTransaction(session,
                    connection -> dao.findByFilter(connection, fromDate, toDate, logId));
            return ServiceResult.success(list, "Distribution logs loaded");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to filter distribution logs", exception);
            return ServiceResult.failure("Unable to load distribution logs");
        }
    }
}
