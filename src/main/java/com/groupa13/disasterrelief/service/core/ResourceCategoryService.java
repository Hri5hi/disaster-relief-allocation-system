package com.groupa13.disasterrelief.service.core;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.core.ResourceCategoryDao;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.core.ResourceCategory;

public final class ResourceCategoryService {

    private static final Logger LOGGER = Logger.getLogger(
            ResourceCategoryService.class.getName());

    private final TransactionRunner transactions;
    private final ResourceCategoryDao dao;

    public ResourceCategoryService() {
        this(TransactionRunner.application());
    }

    public ResourceCategoryService(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.dao = new ResourceCategoryDao();
    }

    public ServiceResult<List<ResourceCategory>> getCategoryTree() {
        try {
            return ServiceResult.success(transactions.run(dao::findTree), "Categories loaded");
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not load categories");
        }
    }

    public ServiceResult<ResourceCategory> findCategory(int categoryId) {
        try {
            CoreServiceSupport.positiveId(categoryId, "Category ID");
            return transactions.run(connection -> dao.findById(connection, categoryId)
                    .map(value -> ServiceResult.success(value, "Category found"))
                    .orElseGet(() -> ServiceResult.failure("Category not found")));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Could not find category");
        }
    }

    public ServiceResult<ResourceCategory> createCategory(String name, Integer parentId) {
        try {
            String validName = CoreServiceSupport.requiredText(name, "Category name", 100);
            validateOptionalId(parentId, "Parent category ID");
            ResourceCategory category = transactions.run(
                    connection -> dao.create(connection, validName, parentId));
            return ServiceResult.success(category, "Category created");
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    categoryDatabaseMessage(exception, "Could not create category"));
        }
    }

    public ServiceResult<ResourceCategory> updateCategory(int categoryId, String name,
            Integer parentId) {
        try {
            CoreServiceSupport.positiveId(categoryId, "Category ID");
            validateOptionalId(parentId, "Parent category ID");
            String validName = CoreServiceSupport.requiredText(name, "Category name", 100);
            return transactions.run(connection -> {
                if (parentId != null && dao.wouldCreateCycle(connection, categoryId, parentId)) {
                    return ServiceResult.failure(
                            "A category cannot be moved under itself or one of its descendants");
                }
                return dao.update(connection, categoryId, validName, parentId)
                        .map(value -> ServiceResult.success(value, "Category updated"))
                        .orElseGet(() -> ServiceResult.failure("Category not found"));
            });
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    categoryDatabaseMessage(exception, "Could not update category"));
        }
    }

    public ServiceResult<Void> deleteCategory(int categoryId) {
        try {
            CoreServiceSupport.positiveId(categoryId, "Category ID");
            return transactions.run(connection -> dao.delete(connection, categoryId)
                    ? ServiceResult.success(null, "Category deleted")
                    : ServiceResult.failure("Category not found"));
        } catch (IllegalArgumentException exception) {
            return CoreServiceSupport.validationFailure(exception);
        } catch (DatabaseException exception) {
            return CoreServiceSupport.databaseFailure(LOGGER, exception,
                    "Category cannot be deleted while it has children or resources");
        }
    }

    private static void validateOptionalId(Integer id, String field) {
        if (id != null) {
            CoreServiceSupport.positiveId(id, field);
        }
    }

    private static String categoryDatabaseMessage(DatabaseException exception, String fallback) {
        return "fk_resource_categories_parent".equals(exception.constraintName())
                ? "Parent category does not exist" : fallback;
    }
}
