package com.groupa13.disasterrelief.service.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.core.DisasterDao;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.core.ActivePeriod;
import com.groupa13.disasterrelief.model.core.Disaster;
import com.groupa13.disasterrelief.model.core.DisasterStatus;
import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Severity;

public final class DisasterService {

    private static final Logger LOGGER = Logger.getLogger(DisasterService.class.getName());

    private final TransactionRunner transactions;
    private final DisasterDao dao;

    public DisasterService() {
        this(TransactionRunner.application());
    }

    public DisasterService(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.dao = new DisasterDao();
    }

    public ServiceResult<List<Disaster>> listDisasters() {
        try {
            return ServiceResult.success(transactions.run(dao::findAll), "Disasters loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load disasters");
        }
    }

    public ServiceResult<List<EntityOption>> listOpenDisasterOptions() {
        try {
            return ServiceResult.success(transactions.run(dao::findOpenOptions),
                    "Disaster options loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load disaster options");
        }
    }

    public ServiceResult<Disaster> findDisaster(int disasterId) {
        try {
            CoreServiceSupport.positiveId(disasterId, "Disaster ID");
            return transactions.run(connection -> dao.findById(connection, disasterId)
                    .map(value -> ServiceResult.success(value, "Disaster found"))
                    .orElseGet(() -> ServiceResult.failure("Disaster not found")));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not find disaster");
        }
    }

    public ServiceResult<Disaster> createDisaster(String name, String type, Severity severity,
            LocalDate startInclusive, LocalDate endExclusive, DisasterStatus status) {
        try {
            String validName = CoreServiceSupport.requiredText(name, "Disaster name", 100);
            String validType = CoreServiceSupport.optionalText(type, "Disaster type", 50);
            Objects.requireNonNull(severity, "Severity is required");
            Objects.requireNonNull(status, "Status is required");
            ActivePeriod period = period(startInclusive, endExclusive);
            Disaster created = transactions.run(connection -> dao.create(connection, validName,
                    validType, severity, period, status));
            return ServiceResult.success(created, "Disaster created");
        } catch (NullPointerException | IllegalArgumentException exception) {
            return ServiceResult.failure(exception.getMessage());
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not create disaster");
        }
    }

    public ServiceResult<Disaster> updateDisaster(int disasterId, String name, String type,
            Severity severity, LocalDate startInclusive, LocalDate endExclusive,
            DisasterStatus status) {
        try {
            CoreServiceSupport.positiveId(disasterId, "Disaster ID");
            String validName = CoreServiceSupport.requiredText(name, "Disaster name", 100);
            String validType = CoreServiceSupport.optionalText(type, "Disaster type", 50);
            Objects.requireNonNull(severity, "Severity is required");
            Objects.requireNonNull(status, "Status is required");
            ActivePeriod period = period(startInclusive, endExclusive);
            return transactions.run(connection -> dao.update(connection, disasterId, validName,
                    validType, severity, period, status)
                    .map(value -> ServiceResult.success(value, "Disaster updated"))
                    .orElseGet(() -> ServiceResult.failure("Disaster not found")));
        } catch (NullPointerException | IllegalArgumentException exception) {
            return ServiceResult.failure(exception.getMessage());
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not update disaster");
        }
    }

    public ServiceResult<Void> deleteDisaster(int disasterId) {
        try {
            CoreServiceSupport.positiveId(disasterId, "Disaster ID");
            return transactions.run(connection -> dao.delete(connection, disasterId)
                    ? ServiceResult.success(null, "Disaster deleted")
                    : ServiceResult.failure("Disaster not found"));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            String message = "fk_zones_disaster".equals(exception.constraintName())
                    ? "Disaster cannot be deleted while zones reference it"
                    : "Could not delete disaster";
            return CoreServiceSupport.databaseFailure(LOGGER, exception, message);
        }
    }

    private static ActivePeriod period(LocalDate startInclusive, LocalDate endExclusive) {
        return startInclusive == null && endExclusive == null
                ? null : new ActivePeriod(startInclusive, endExclusive);
    }
}
