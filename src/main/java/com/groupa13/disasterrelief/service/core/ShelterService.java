package com.groupa13.disasterrelief.service.core;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.core.ShelterDao;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.core.Coordinates;
import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Shelter;
import com.groupa13.disasterrelief.model.core.ShelterStatus;

public final class ShelterService {

    private static final Logger LOGGER = Logger.getLogger(ShelterService.class.getName());

    private final TransactionRunner transactions;
    private final ShelterDao dao;

    public ShelterService() {
        this(TransactionRunner.application());
    }

    public ShelterService(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.dao = new ShelterDao();
    }

    public ServiceResult<List<Shelter>> listShelters() {
        try {
            return ServiceResult.success(transactions.run(dao::findAll), "Shelters loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load shelters");
        }
    }

    public ServiceResult<List<EntityOption>> listShelterOptions() {
        try {
            return ServiceResult.success(transactions.run(dao::findOptions),
                    "Shelter options loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load shelter options");
        }
    }

    public ServiceResult<Shelter> findShelter(int shelterId) {
        try {
            CoreServiceSupport.positiveId(shelterId, "Shelter ID");
            return transactions.run(connection -> dao.findById(connection, shelterId)
                    .map(value -> ServiceResult.success(value, "Shelter found"))
                    .orElseGet(() -> ServiceResult.failure("Shelter not found")));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not find shelter");
        }
    }

    public ServiceResult<Shelter> createShelter(String name, String address, Double latitude,
            Double longitude, int capacity, ShelterStatus status) {
        try {
            String validName = CoreServiceSupport.requiredText(name, "Shelter name", 100);
            String validAddress = CoreServiceSupport.requiredText(address, "Address", 255);
            Coordinates location = coordinates(latitude, longitude);
            validateCapacity(capacity);
            Objects.requireNonNull(status, "Status is required");
            Shelter shelter = transactions.run(connection -> dao.create(connection, validName,
                    validAddress, location, capacity, status));
            return ServiceResult.success(shelter, "Shelter created");
        } catch (NullPointerException | IllegalArgumentException exception) {
            return ServiceResult.failure(exception.getMessage());
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not create shelter");
        }
    }

    public ServiceResult<Shelter> updateShelter(int shelterId, String name, String address,
            Double latitude, Double longitude, int capacity, ShelterStatus status) {
        try {
            CoreServiceSupport.positiveId(shelterId, "Shelter ID");
            String validName = CoreServiceSupport.requiredText(name, "Shelter name", 100);
            String validAddress = CoreServiceSupport.requiredText(address, "Address", 255);
            Coordinates location = coordinates(latitude, longitude);
            validateCapacity(capacity);
            Objects.requireNonNull(status, "Status is required");
            return transactions.run(connection -> dao.update(connection, shelterId, validName,
                    validAddress, location, capacity, status)
                    .map(value -> ServiceResult.success(value, "Shelter updated"))
                    .orElseGet(() -> ServiceResult.failure("Shelter not found")));
        } catch (NullPointerException | IllegalArgumentException exception) {
            return ServiceResult.failure(exception.getMessage());
        } catch (DatabaseException exception) {
            String message = "chk_shelters_occupancy_within_capacity".equals(
                    exception.constraintName())
                    ? "Capacity cannot be below current occupancy"
                    : "Could not update shelter";
            return CoreServiceSupport.databaseFailure(LOGGER, exception, message);
        }
    }

    public ServiceResult<Void> deleteShelter(int shelterId) {
        try {
            CoreServiceSupport.positiveId(shelterId, "Shelter ID");
            return transactions.run(connection -> {
                var shelter = dao.findById(connection, shelterId);
                if (shelter.isEmpty()) {
                    return ServiceResult.failure("Shelter not found");
                }
                if (shelter.get().currentOccupancy() > 0) {
                    return ServiceResult.failure("Occupied shelter cannot be deleted");
                }
                if (dao.delete(connection, shelterId)) {
                    return ServiceResult.success(null, "Shelter deleted");
                }
                return dao.findById(connection, shelterId)
                        .filter(current -> current.currentOccupancy() > 0)
                        .map(current -> ServiceResult.<Void>failure(
                                "Occupied shelter cannot be deleted"))
                        .orElseGet(() -> ServiceResult.failure("Shelter not found"));
            });
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Shelter cannot be deleted while other records reference it");
        }
    }

    private static Coordinates coordinates(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return null;
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Both latitude and longitude are required");
        }
        return new Coordinates(latitude, longitude);
    }

    private static void validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero");
        }
    }
}
