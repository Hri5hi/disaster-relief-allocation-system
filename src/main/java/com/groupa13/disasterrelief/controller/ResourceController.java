package com.groupa13.disasterrelief.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.model.core.Resource;
import com.groupa13.disasterrelief.model.core.ResourceCategory;
import com.groupa13.disasterrelief.service.core.ResourceCategoryService;
import com.groupa13.disasterrelief.service.core.ResourceService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.Utility;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class ResourceController extends Utility {

    private static final Logger LOGGER = Logger.getLogger(ResourceController.class.getName());
    private static final ResourceCategory ROOT =
            new ResourceCategory(0, "All Categories", null, -1, "All Categories");

    @FXML private TreeView<ResourceCategory> categoryTree;
    @FXML private TextField resourceIdField;
    @FXML private TextField nameField;
    @FXML private ComboBox<ResourceCategory> categoryCombo;
    @FXML private ComboBox<String> unitCombo;
    @FXML private Label errorLabel;
    @FXML private TableView<Resource> resourceTable;
    @FXML private TableColumn<Resource, String> colResourceId;
    @FXML private TableColumn<Resource, String> colName;
    @FXML private TableColumn<Resource, String> colCategory;
    @FXML private TableColumn<Resource, String> colUnit;

    private final ObservableList<Resource> dataList = FXCollections.observableArrayList();
    private final ResourceCategoryService categoryService;
    private final ResourceService resourceService;

    public ResourceController() {
        this(new ResourceCategoryService(), new ResourceService());
    }

    ResourceController(ResourceCategoryService categoryService, ResourceService resourceService) {
        this.categoryService = categoryService;
        this.resourceService = resourceService;
    }

    @FXML
    public void initialize() {
        unitCombo.getItems().setAll("packets", "litres", "pieces", "kg", "boxes", "units");
        colResourceId.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper("R" + cell.getValue().resourceId()));
        colName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        colCategory.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().categoryPath()));
        colUnit.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().unit() == null ? "—" : cell.getValue().unit()));
        resourceTable.setItems(dataList);
        resourceTable.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> populateForm(selected));
        categoryTree.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> {
                    if (selected != null && selected.getValue().categoryId() > 0) {
                        categoryCombo.setValue(selected.getValue());
                    }
                });
        loadCategories();
        loadResources();
    }

    private void loadCategories() {
        runServiceTask(categoryService::getCategoryTree, this::displayCategories,
                "category-loader");
    }

    private void displayCategories(List<ResourceCategory> categories) {
        TreeItem<ResourceCategory> root = new TreeItem<>(ROOT);
        root.setExpanded(true);
        Map<Integer, TreeItem<ResourceCategory>> items = new HashMap<>();
        for (ResourceCategory category : categories) {
            TreeItem<ResourceCategory> item = new TreeItem<>(category);
            items.put(category.categoryId(), item);
            TreeItem<ResourceCategory> parent = category.parentId() == null
                    ? root : items.get(category.parentId());
            (parent == null ? root : parent).getChildren().add(item);
        }
        categoryTree.setRoot(root);
        categoryTree.setShowRoot(true);
        categoryCombo.getItems().setAll(categories);
    }

    @FXML
    public void handleAddCategory(ActionEvent event) {
        requestCategoryName(null, "Add top-level category");
    }

    @FXML
    public void handleAddSubcategory(ActionEvent event) {
        TreeItem<ResourceCategory> selected = categoryTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            errorLabel.setText("Select a parent category first");
            return;
        }
        Integer parentId = selected.getValue().categoryId() == 0
                ? null : selected.getValue().categoryId();
        requestCategoryName(parentId, "Add subcategory under " + selected.getValue().name());
    }

    private void requestCategoryName(Integer parentId, String header) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Category");
        dialog.setHeaderText(header);
        dialog.setContentText("Category name:");
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get().isBlank()) {
            return;
        }
        runServiceTask(() -> categoryService.createCategory(answer.get(), parentId),
                ignored -> loadCategories(), "category-create");
    }

    @FXML
    public void handleDeleteCategory(ActionEvent event) {
        TreeItem<ResourceCategory> selected = categoryTree.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue().categoryId() == 0) {
            errorLabel.setText("Select a category to delete");
            return;
        }
        Alert confirmation = new Alert(AlertType.CONFIRMATION,
                "Delete " + selected.getValue().path() + "?", ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Delete Category");
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        int categoryId = selected.getValue().categoryId();
        runServiceTask(() -> categoryService.deleteCategory(categoryId),
                ignored -> loadCategories(), "category-delete");
    }

    private void loadResources() {
        runServiceTask(resourceService::listResources, resources -> {
            dataList.setAll(resources);
            resourceTable.setItems(dataList);
        }, "resource-loader");
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        if (!validName()) {
            return;
        }
        ResourceCategory category = categoryCombo.getValue();
        if (category == null) {
            errorLabel.setText("Select a category");
            return;
        }
        runServiceTask(() -> resourceService.createResource(nameField.getText(),
                category.categoryId(), unitCombo.getValue()), ignored -> {
                    handleClear(null);
                    loadResources();
                }, "resource-create");
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        Integer resourceId = readId(resourceIdField.getText());
        ResourceCategory category = categoryCombo.getValue();
        if (resourceId == null || !validName()) {
            return;
        }
        if (category == null) {
            errorLabel.setText("Select a category");
            return;
        }
        runServiceTask(() -> resourceService.updateResource(resourceId, nameField.getText(),
                category.categoryId(), unitCombo.getValue()), ignored -> loadResources(),
                "resource-update");
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        Integer resourceId = readId(resourceIdField.getText());
        if (resourceId == null) {
            return;
        }
        runServiceTask(() -> resourceService.deleteResource(resourceId), ignored -> {
            handleClear(null);
            loadResources();
        }, "resource-delete");
    }

    @FXML
    public void handleFind(ActionEvent event) {
        Integer resourceId = readId(resourceIdField.getText());
        if (resourceId == null) {
            return;
        }
        runServiceTask(() -> resourceService.findResource(resourceId), resource -> {
            resourceTable.setItems(FXCollections.observableArrayList(resource));
            populateForm(resource);
        }, "resource-find");
    }

    @FXML
    public void handleClear(ActionEvent event) {
        resourceIdField.clear();
        nameField.clear();
        categoryCombo.getSelectionModel().clearSelection();
        unitCombo.getSelectionModel().clearSelection();
        errorLabel.setText("");
        resourceTable.setItems(dataList);
    }

    @FXML
    public void goBack(ActionEvent event) {
        changeScene(event, "dashboard", "Dashboard");
    }

    private void populateForm(Resource resource) {
        if (resource == null) {
            return;
        }
        resourceIdField.setText("R" + resource.resourceId());
        nameField.setText(resource.name());
        unitCombo.setValue(resource.unit());
        categoryCombo.getItems().stream()
                .filter(category -> category.categoryId() == resource.categoryId())
                .findFirst().ifPresent(categoryCombo::setValue);
    }

    private Integer readId(String text) {
        try {
            String normalized = text == null ? "" : text.trim();
            if (!normalized.isEmpty() && Character.toUpperCase(normalized.charAt(0)) == 'R') {
                normalized = normalized.substring(1);
            }
            int id = Integer.parseInt(normalized);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            errorLabel.setText("Invalid resource ID");
            return null;
        }
    }

    private boolean validName() {
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            errorLabel.setText("Resource name is required");
            return false;
        }
        return true;
    }

    private <T> void runServiceTask(Supplier<ServiceResult<T>> call, Consumer<T> onSuccess,
            String threadName) {
        Task<ServiceResult<T>> task = new Task<>() {
            @Override
            protected ServiceResult<T> call() {
                return call.get();
            }
        };
        task.setOnSucceeded(ignored -> {
            ServiceResult<T> result = task.getValue();
            errorLabel.setText(result.success() ? "" : result.message());
            if (result.success()) {
                onSuccess.accept(result.value());
            }
        });
        task.setOnFailed(ignored -> {
            LOGGER.log(Level.SEVERE, "Resource operation failed", task.getException());
            errorLabel.setText("Resource operation failed");
        });
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
