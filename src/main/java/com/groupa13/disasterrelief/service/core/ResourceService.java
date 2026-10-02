package com.groupa13.disasterrelief.service.core;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.core.ResourceDao;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Resource;

public final class ResourceService {

    private static final Logger LOGGER = Logger.getLogger(ResourceService.class.getName());

    private final TransactionRunner transactions;
    private final ResourceDao dao;

    public ResourceService() {
        this(TransactionRunner.application());
    }

    public ResourceService(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.dao = new ResourceDao();
    }

    public ServiceResult<List<Resource>> listResources() {
        try {
            return ServiceResult.success(transactions.run(dao::findAll), "Resources loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load resources");
        }
    }

    public ServiceResult<List<EntityOption>> listResourceOptions() {
        try {
            return ServiceResult.success(transactions.run(dao::findOptions),
                    "Resource options loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load resource options");
        }
    }

    public ServiceResult<Resource> findResource(int resourceId) {
        try {
            CoreServiceSupport.positiveId(resourceId, "Resource ID");
            return transactions.run(connection -> dao.findById(connection, resourceId)
                    .map(value -> ServiceResult.success(value, "Resource found"))
                    .orElseGet(() -> ServiceResult.failure("Resource not found")));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not find resource");
        }
    }

    public ServiceResult<Resource> createResource(String name, int categoryId, String unit) {
        try {
            String validName = CoreServiceSupport.requiredText(name, "Resource name", 100);
            CoreServiceSupport.positiveId(categoryId, "Category ID");
            String validUnit = CoreServiceSupport.optionalText(unit, "Unit", 20);
            Resource resource = transactions.run(
                    connection -> dao.create(connection, validName, categoryId, validUnit));
            return ServiceResult.success(resource, "Resource created");
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    resourceDatabaseMessage(exception, "Could not create resource"));
        }
    }

    public ServiceResult<Resource> updateResource(int resourceId, String name, int categoryId,
            String unit) {
        try {
            CoreServiceSupport.positiveId(resourceId, "Resource ID");
            CoreServiceSupport.positiveId(categoryId, "Category ID");
            String validName = CoreServiceSupport.requiredText(name, "Resource name", 100);
            String validUnit = CoreServiceSupport.optionalText(unit, "Unit", 20);
            return transactions.run(connection -> dao.update(connection, resourceId, validName,
                    categoryId, validUnit)
                    .map(value -> ServiceResult.success(value, "Resource updated"))
                    .orElseGet(() -> ServiceResult.failure("Resource not found")));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    resourceDatabaseMessage(exception, "Could not update resource"));
        }
    }

    public ServiceResult<Void> deleteResource(int resourceId) {
        try {
            CoreServiceSupport.positiveId(resourceId, "Resource ID");
            return transactions.run(connection -> dao.delete(connection, resourceId)
                    ? ServiceResult.success(null, "Resource deleted")
                    : ServiceResult.failure("Resource not found"));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Resource cannot be deleted while inventory or operations reference it");
        }
    }

    private static String resourceDatabaseMessage(DatabaseException exception, String fallback) {
        return "fk_resources_category".equals(exception.constraintName())
                ? "Selected category does not exist" : fallback;
    }
}
