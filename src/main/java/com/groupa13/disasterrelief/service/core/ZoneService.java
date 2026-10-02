package com.groupa13.disasterrelief.service.core;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.core.ShelterDao;
import com.groupa13.disasterrelief.dao.core.ZoneDao;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.core.Severity;
import com.groupa13.disasterrelief.model.core.Zone;

public final class ZoneService {

    private static final Logger LOGGER = Logger.getLogger(ZoneService.class.getName());

    private final TransactionRunner transactions;
    private final ZoneDao dao;
    private final ShelterDao shelterDao;

    public ZoneService() {
        this(TransactionRunner.application());
    }

    public ZoneService(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.dao = new ZoneDao();
        this.shelterDao = new ShelterDao();
    }

    public ServiceResult<List<Zone>> listZones() {
        try {
            return ServiceResult.success(transactions.run(dao::findAll), "Zones loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception, "Could not load zones");
        }
    }

    public ServiceResult<List<Zone>> listZonesForDisaster(int disasterId) {
        try {
            CoreServiceSupport.positiveId(disasterId, "Disaster ID");
            return ServiceResult.success(
                    transactions.run(connection -> dao.findByDisaster(connection, disasterId)),
                    "Zones loaded");
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception, "Could not load zones");
        }
    }

    public ServiceResult<Zone> findZone(int zoneId) {
        try {
            CoreServiceSupport.positiveId(zoneId, "Zone ID");
            return transactions.run(connection -> dao.findById(connection, zoneId)
                    .map(value -> ServiceResult.success(value, "Zone found"))
                    .orElseGet(() -> ServiceResult.failure("Zone not found")));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception, "Could not find zone");
        }
    }

    public ServiceResult<Zone> createZone(int disasterId, String name, Severity severity,
            Integer primaryShelterId, String coordinatorContact) {
        try {
            validateReferences(disasterId, primaryShelterId);
            String validName = CoreServiceSupport.requiredText(name, "Zone name", 100);
            String validContact = CoreServiceSupport.optionalText(
                    coordinatorContact, "Coordinator contact", 20);
            Objects.requireNonNull(severity, "Severity is required");
            Zone zone = transactions.run(connection -> dao.create(connection, disasterId,
                    validName, severity, primaryShelterId, validContact));
            return ServiceResult.success(zone, "Zone created");
        } catch (NullPointerException | IllegalArgumentException exception) {
            return ServiceResult.failure(exception.getMessage());
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    zoneDatabaseMessage(exception, "Could not create zone"));
        }
    }

    public ServiceResult<Zone> updateZone(int zoneId, String name, Severity severity,
            Integer primaryShelterId, String coordinatorContact) {
        try {
            CoreServiceSupport.positiveId(zoneId, "Zone ID");
            validateOptionalId(primaryShelterId, "Primary shelter ID");
            String validName = CoreServiceSupport.requiredText(name, "Zone name", 100);
            String validContact = CoreServiceSupport.optionalText(
                    coordinatorContact, "Coordinator contact", 20);
            Objects.requireNonNull(severity, "Severity is required");
            return transactions.run(connection -> dao.update(connection, zoneId, validName,
                    severity, primaryShelterId, validContact)
                    .map(value -> ServiceResult.success(value, "Zone updated"))
                    .orElseGet(() -> ServiceResult.failure("Zone not found")));
        } catch (NullPointerException | IllegalArgumentException exception) {
            return ServiceResult.failure(exception.getMessage());
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    zoneDatabaseMessage(exception, "Could not update zone"));
        }
    }

    public ServiceResult<Zone> assignPrimaryShelter(int zoneId, Integer shelterId) {
        try {
            CoreServiceSupport.positiveId(zoneId, "Zone ID");
            validateOptionalId(shelterId, "Primary shelter ID");
            return transactions.run(connection -> {
                if (dao.findById(connection, zoneId).isEmpty()) {
                    return ServiceResult.failure("Zone not found");
                }
                if (shelterId != null && shelterDao.findById(connection, shelterId).isEmpty()) {
                    return ServiceResult.failure("Selected primary shelter does not exist");
                }
                return dao.assignPrimaryShelter(connection, zoneId, shelterId)
                        .map(value -> ServiceResult.success(value,
                                shelterId == null
                                        ? "Primary shelter cleared" : "Primary shelter assigned"))
                        .orElseGet(() -> ServiceResult.failure("Zone not found"));
            });
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    zoneDatabaseMessage(exception, "Could not assign primary shelter"));
        }
    }

    public ServiceResult<Void> deleteZone(int zoneId) {
        try {
            CoreServiceSupport.positiveId(zoneId, "Zone ID");
            return transactions.run(connection -> dao.delete(connection, zoneId)
                    ? ServiceResult.success(null, "Zone deleted")
                    : ServiceResult.failure("Zone not found"));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Zone cannot be deleted while other records reference it");
        }
    }

    private static void validateReferences(int disasterId, Integer primaryShelterId) {
        CoreServiceSupport.positiveId(disasterId, "Disaster ID");
        validateOptionalId(primaryShelterId, "Primary shelter ID");
    }

    private static void validateOptionalId(Integer id, String field) {
        if (id != null) {
            CoreServiceSupport.positiveId(id, field);
        }
    }

    private static String zoneDatabaseMessage(DatabaseException exception, String fallback) {
        return switch (String.valueOf(exception.constraintName())) {
            case "uq_zones_disaster_name" -> "A zone with that name already exists for the disaster";
            case "fk_zones_disaster" -> "Selected disaster does not exist";
            case "fk_zones_primary_shelter" -> "Selected primary shelter does not exist";
            default -> fallback;
        };
    }
}
