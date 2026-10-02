package com.groupa13.disasterrelief.service.operations;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.operations.ResourceRequestDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.operations.AllocationResult;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;

public class AllocationService {

    private static final Logger LOGGER = Logger.getLogger(AllocationService.class.getName());

    private final ResourceRequestDao dao;

    public AllocationService() {
        this(new ResourceRequestDao());
    }

    public AllocationService(ResourceRequestDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    public ServiceResult<AllocationResult> allocateResources(int requestId, int sourceShelterId) {
        if (requestId <= 0) {
            return ServiceResult.failure("Invalid request ID");
        }
        if (sourceShelterId <= 0) {
            return ServiceResult.failure("Select a valid source shelter for fulfillment");
        }

        UserSession session = UserSession.getInstance();
        UserRole role = session.getRole();
        if (role != UserRole.SYSTEM_ADMIN && role != UserRole.WAREHOUSE_MANAGER) {
            return ServiceResult.failure("Only Warehouse Managers and Administrators can fulfill requests");
        }

        try {
            AllocationResult result = Database.inTransaction(session,
                    connection -> dao.callAllocateResources(connection, requestId, sourceShelterId));
            return ServiceResult.success(result, "Request #" + requestId + " successfully fulfilled from shelter #" + sourceShelterId);
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to allocate resources for request " + requestId, exception);
            String message = exception.getMessage();
            if (message != null && message.contains("Insufficient stock")) {
                return ServiceResult.failure("Insufficient stock at source shelter");
            }
            if (message != null && message.contains("cannot be identical")) {
                return ServiceResult.failure("Source and destination shelters cannot be identical");
            }
            return ServiceResult.failure("Allocation failed: " + exception.getMessage());
        }
    }
}
