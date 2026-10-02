package com.groupa13.disasterrelief.service.operations;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.operations.ResourceRequestDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.ResourceRequest;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;

public class ResourceRequestService {

    private static final Logger LOGGER = Logger.getLogger(ResourceRequestService.class.getName());

    private final ResourceRequestDao dao;

    public ResourceRequestService() {
        this(new ResourceRequestDao());
    }

    public ResourceRequestService(ResourceRequestDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    public ServiceResult<ResourceRequest> submitRequest(int zoneId, int resourceId,
            int destinationShelterId, int quantity, String urgency) {
        if (zoneId <= 0) {
            return ServiceResult.failure("Invalid zone selected");
        }
        if (resourceId <= 0) {
            return ServiceResult.failure("Invalid resource selected");
        }
        if (destinationShelterId <= 0) {
            return ServiceResult.failure("Invalid destination shelter selected");
        }
        if (quantity <= 0) {
            return ServiceResult.failure("Requested quantity must be positive");
        }
        if (urgency == null || urgency.isBlank()) {
            return ServiceResult.failure("Urgency level is required");
        }

        UserSession session = UserSession.getInstance();
        if (session.getRole() == UserRole.ZONE_COORDINATOR) {
            if (session.getAssignedZoneId() == null || session.getAssignedZoneId() != zoneId) {
                return ServiceResult.failure("You can only submit requests for your assigned zone");
            }
        }

        try {
            ResourceRequest req = Database.inTransaction(session,
                    connection -> dao.create(connection, zoneId, resourceId, destinationShelterId, quantity, urgency.toLowerCase()));
            return ServiceResult.success(req, "Resource request submitted successfully");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to submit resource request", exception);
            return ServiceResult.failure("Unable to submit request: " + exception.getMessage());
        }
    }

    public ServiceResult<List<ResourceRequest>> listRequests(Integer zoneFilter, String statusFilter) {
        UserSession session = UserSession.getInstance();
        Integer effectiveZone = zoneFilter;
        if (session.getRole() == UserRole.ZONE_COORDINATOR) {
            effectiveZone = session.getAssignedZoneId();
        }

        final Integer finalZone = effectiveZone;
        try {
            List<ResourceRequest> list = Database.inTransaction(session,
                    connection -> dao.findAll(connection, finalZone, statusFilter));
            return ServiceResult.success(list, "Requests loaded");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to list resource requests", exception);
            return ServiceResult.failure("Unable to load resource requests");
        }
    }

    public ServiceResult<Void> cancelRequest(int requestId) {
        if (requestId <= 0) {
            return ServiceResult.failure("Invalid request ID");
        }

        UserSession session = UserSession.getInstance();
        try {
            boolean cancelled = Database.inTransaction(session, connection -> dao.cancelRequest(connection, requestId));
            if (cancelled) {
                return ServiceResult.success(null, "Request cancelled successfully");
            }
            return ServiceResult.failure("Request not found or not in pending status");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to cancel request " + requestId, exception);
            return ServiceResult.failure("Unable to cancel request");
        }
    }
}
