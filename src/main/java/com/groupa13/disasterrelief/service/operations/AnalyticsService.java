package com.groupa13.disasterrelief.service.operations;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.operations.AnalyticsDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.operations.DashboardMetrics;
import com.groupa13.disasterrelief.model.operations.ZoneDeficit;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;

public class AnalyticsService {

    private static final Logger LOGGER = Logger.getLogger(AnalyticsService.class.getName());

    private final AnalyticsDao dao;

    public AnalyticsService() {
        this(new AnalyticsDao());
    }

    public AnalyticsService(AnalyticsDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    public ServiceResult<List<ZoneDeficit>> getZoneDeficitReport(int disasterId) {
        if (disasterId <= 0) {
            return ServiceResult.failure("Select a valid disaster");
        }

        UserSession session = UserSession.getInstance();
        try {
            List<ZoneDeficit> list = Database.inTransaction(session,
                    connection -> dao.getZoneDeficitReport(connection, disasterId));
            return ServiceResult.success(list, "Deficit report generated");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to run zone deficit report", exception);
            return ServiceResult.failure("Unable to generate deficit report");
        }
    }

    public ServiceResult<Void> refreshViews() {
        UserSession session = UserSession.getInstance();
        if (session.getRole() != UserRole.SYSTEM_ADMIN) {
            return ServiceResult.failure("Only System Administrators can refresh materialized views");
        }

        try {
            Database.inTransaction(session, connection -> {
                dao.refreshMaterializedViews(connection);
                return null;
            });
            return ServiceResult.success(null, "Materialized views refreshed concurrently");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to refresh materialized views", exception);
            return ServiceResult.failure("Failed to refresh materialized views: " + exception.getMessage());
        }
    }

    public ServiceResult<DashboardMetrics> getDashboardMetrics(UserSession session) {
        Integer zoneId = null;
        if (session != null && session.getRole() == UserRole.ZONE_COORDINATOR) {
            zoneId = session.getAssignedZoneId();
        }

        final Integer finalZone = zoneId;
        try {
            DashboardMetrics metrics = Database.inTransaction(session,
                    connection -> dao.getDashboardMetrics(connection, finalZone));
            return ServiceResult.success(metrics, "Metrics loaded");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to load dashboard metrics", exception);
            return ServiceResult.failure("Unable to load dashboard metrics");
        }
    }
}
