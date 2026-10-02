package com.groupa13.disasterrelief.service.core;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.core.InventoryDao;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.core.ShelterInventory;

public final class InventoryService {

    private static final Logger LOGGER = Logger.getLogger(InventoryService.class.getName());

    private final TransactionRunner transactions;
    private final InventoryDao dao;

    public InventoryService() {
        this(TransactionRunner.application());
    }

    public InventoryService(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.dao = new InventoryDao();
    }

    public ServiceResult<List<ShelterInventory>> listInventory() {
        try {
            return ServiceResult.success(transactions.run(dao::findAll), "Inventory loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load inventory");
        }
    }

    public ServiceResult<List<ShelterInventory>> listLowStock() {
        try {
            return ServiceResult.success(transactions.run(dao::findLowStock),
                    "Low-stock inventory loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load low-stock inventory");
        }
    }

    public ServiceResult<ShelterInventory> findInventory(int shelterId, int resourceId) {
        try {
            validateIds(shelterId, resourceId);
            return transactions.run(connection -> dao.find(connection, shelterId, resourceId)
                    .map(value -> ServiceResult.success(value, "Inventory record found"))
                    .orElseGet(() -> ServiceResult.failure("Inventory record not found")));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not find inventory record");
        }
    }

    /** Replaces an absolute stock value; donation increments belong to Dev C's function. */
    public ServiceResult<ShelterInventory> replaceInventory(int shelterId, int resourceId,
            int quantity, int reorderLevel) {
        try {
            validateIds(shelterId, resourceId);
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity cannot be negative");
            }
            if (reorderLevel < 0) {
                throw new IllegalArgumentException("Reorder level cannot be negative");
            }
            ShelterInventory inventory = transactions.run(connection ->
                    dao.upsertReplacement(connection, shelterId, resourceId,
                            quantity, reorderLevel));
            return ServiceResult.success(inventory, "Inventory saved");
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            String message = exception.constraintName() != null
                    && exception.constraintName().startsWith("fk_shelter_inventory_")
                    ? "Selected shelter or resource does not exist"
                    : "Could not save inventory";
            return CoreServiceSupport.databaseFailure(LOGGER, exception, message);
        }
    }

    public ServiceResult<Void> deleteInventory(int shelterId, int resourceId) {
        try {
            validateIds(shelterId, resourceId);
            return transactions.run(connection -> dao.delete(connection, shelterId, resourceId)
                    ? ServiceResult.success(null, "Inventory record deleted")
                    : ServiceResult.failure("Inventory record not found"));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not delete inventory record");
        }
    }

    private static void validateIds(int shelterId, int resourceId) {
        CoreServiceSupport.positiveId(shelterId, "Shelter ID");
        CoreServiceSupport.positiveId(resourceId, "Resource ID");
    }
}
