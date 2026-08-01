package com.groupa13.disasterrelief.controller;

import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.util.Utility;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import javafx.scene.control.cell.PropertyValueFactory;

public class ResourceController extends Utility {

    // TODO: replace with com.groupa13.disasterrelief.model.Resource once the model layer
    // exists. Mirrors the `resources` table (resource_id, name, category_id, unit).
    public static class ResourceRow {
        private final StringProperty resourceId;
        private final StringProperty name;
        private final StringProperty category;
        private final StringProperty unit;

        public ResourceRow(String resourceId, String name, String category, String unit) {
            this.resourceId = new SimpleStringProperty(resourceId);
            this.name = new SimpleStringProperty(name);
            this.category = new SimpleStringProperty(category);
            this.unit = new SimpleStringProperty(unit);
        }

        public String getResourceId() { return resourceId.get(); }
        public String getName() { return name.get(); }
        public String getCategory() { return category.get(); }
        public String getUnit() { return unit.get(); }
    }

    // TODO: replace with com.groupa13.disasterrelief.model.ResourceCategory once the model
    // layer exists. Mirrors the self-referencing `resource_categories` table
    // (category_id, name, parent_id). Held here just as tree/combo display data.
    private static class CategoryNode {
        final String categoryId;
        final String name;

        CategoryNode(String categoryId, String name) {
            this.categoryId = categoryId;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @FXML private TreeView<CategoryNode> categoryTree;

    @FXML private TextField resourceIdField;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> unitCombo;
    @FXML private Label errorLabel;

    @FXML private TableView<ResourceRow> resourceTable;
    @FXML private TableColumn<ResourceRow, String> colResourceId;
    @FXML private TableColumn<ResourceRow, String> colName;
    @FXML private TableColumn<ResourceRow, String> colCategory;
    @FXML private TableColumn<ResourceRow, String> colUnit;

    private final ObservableList<ResourceRow> dataList = FXCollections.observableArrayList();
    private final ObservableList<ResourceRow> searchList = FXCollections.observableArrayList();
    private int nextCategorySeq = 1;

    @FXML
    public void initialize() {
        unitCombo.getItems().addAll("packets", "litres", "pieces", "kg", "boxes", "units");

        colResourceId.setCellValueFactory(new PropertyValueFactory<>("resourceId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unit"));

        buildCategoryTree();
        refreshCategoryCombo();
        loadResources();
    }

    // ------------------------------------------------------------------
    // Category tree (self-referencing resource_categories hierarchy)
    // ------------------------------------------------------------------

    private void buildCategoryTree() {
        // TODO: replace with CategoryService.getCategoryTree() -> a recursive CTE over
        // resource_categories (WITH RECURSIVE cat_tree AS (... UNION ALL ...)) that
        // returns the full parent/child hierarchy in one round trip.
        TreeItem<CategoryNode> root = new TreeItem<>(new CategoryNode("C0", "All Categories"));
        root.setExpanded(true);

        TreeItem<CategoryNode> medical = new TreeItem<>(new CategoryNode("C1", "Medical"));
        TreeItem<CategoryNode> firstAid = new TreeItem<>(new CategoryNode("C2", "First Aid"));
        firstAid.getChildren().addAll(
                new TreeItem<>(new CategoryNode("C3", "Bandages")),
                new TreeItem<>(new CategoryNode("C4", "Antiseptic")));
        medical.getChildren().add(firstAid);
        medical.setExpanded(true);
        firstAid.setExpanded(true);

        TreeItem<CategoryNode> food = new TreeItem<>(new CategoryNode("C5", "Food & Water"));
        food.getChildren().addAll(
                new TreeItem<>(new CategoryNode("C6", "Dry Rations")),
                new TreeItem<>(new CategoryNode("C7", "Drinking Water")));

        TreeItem<CategoryNode> shelterItems = new TreeItem<>(new CategoryNode("C8", "Shelter & Bedding"));
        shelterItems.getChildren().addAll(
                new TreeItem<>(new CategoryNode("C9", "Tarpaulins")),
                new TreeItem<>(new CategoryNode("C10", "Blankets")));

        root.getChildren().addAll(medical, food, shelterItems);
        nextCategorySeq = 11;

        categoryTree.setRoot(root);
        categoryTree.setShowRoot(true);
        categoryTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Selecting a category in the tree pre-fills the resource form's category combo.
            if (newVal != null && newVal.getValue() != null) {
                String path = buildPath(newVal);
                if (categoryCombo.getItems().contains(path)) {
                    categoryCombo.setValue(path);
                }
            }
        });
    }

    private String buildPath(TreeItem<CategoryNode> item) {
        StringBuilder sb = new StringBuilder(item.getValue().name);
        TreeItem<CategoryNode> parent = item.getParent();
        while (parent != null && parent.getValue() != null && parent.getParent() != null) {
            sb.insert(0, parent.getValue().name + " > ");
            parent = parent.getParent();
        }
        return sb.toString();
    }

    private void refreshCategoryCombo() {
        categoryCombo.getItems().clear();
        flattenTree(categoryTree.getRoot(), categoryCombo.getItems());
    }

    private void flattenTree(TreeItem<CategoryNode> item, ObservableList<String> target) {
        if (item == null) {
            return;
        }
        if (item.getParent() != null) {
            target.add(buildPath(item));
        }
        for (TreeItem<CategoryNode> child : item.getChildren()) {
            flattenTree(child, target);
        }
    }

    @FXML
    public void handleAddCategory(ActionEvent event) {
        addCategory(categoryTree.getRoot());
    }

    @FXML
    public void handleAddSubcategory(ActionEvent event) {
        TreeItem<CategoryNode> selected = categoryTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            errorLabel.setText("Select a parent category first");
            return;
        }
        addCategory(selected);
    }

    private void addCategory(TreeItem<CategoryNode> parent) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Category");
        dialog.setHeaderText("Add category under \"" + parent.getValue().name + "\"");
        dialog.setContentText("Category name:");
        Optional<String> result = dialog.showAndWait();

        result.filter(name -> !name.isBlank()).ifPresent(name -> {
            // TODO: call CategoryService.createCategory(name, parentId) -> INSERT INTO
            // resource_categories (name, parent_id) VALUES (?, ?) where parent_id is
            // NULL when parent is the synthetic "All Categories" root.
            String newId = "C" + (nextCategorySeq++);
            TreeItem<CategoryNode> newItem = new TreeItem<>(new CategoryNode(newId, name));
            parent.getChildren().add(newItem);
            parent.setExpanded(true);
            refreshCategoryCombo();
            errorLabel.setText("");
        });
    }

    @FXML
    public void handleDeleteCategory(ActionEvent event) {
        TreeItem<CategoryNode> selected = categoryTree.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getParent() == null) {
            errorLabel.setText("Select a category to delete");
            return;
        }

        boolean inUse = dataList.stream().anyMatch(r -> r.getCategory().equals(buildPath(selected)));
        if (inUse || !selected.getChildren().isEmpty()) {
            Alert warn = new Alert(AlertType.CONFIRMATION);
            warn.setTitle("Delete Category");
            warn.setHeaderText("This category has resources and/or subcategories under it");
            warn.setContentText("Deleting it will require reassigning or removing those first. Continue anyway?");
            Optional<ButtonType> result = warn.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        // TODO: call CategoryService.deleteCategory(categoryId) -> DELETE FROM
        // resource_categories WHERE category_id = ?. The FK from `resources` and from
        // child categories' parent_id will reject this unless reassigned/cascaded first.
        selected.getParent().getChildren().remove(selected);
        refreshCategoryCombo();
        errorLabel.setText("");
    }

    // ------------------------------------------------------------------
    // Resource CRUD
    // ------------------------------------------------------------------

    private void loadResources() {
        Task<List<ResourceRow>> loadTask = new Task<>() {
            @Override
            protected List<ResourceRow> call() {
                // TODO: replace with ResourceService.getAllResources() -> SELECT r.*, c.name
                // AS category_name FROM resources r JOIN resource_categories c
                // ON r.category_id = c.category_id.
                return List.of(
                        new ResourceRow("R3001", "Paracetamol 500mg", "Medical > First Aid > Bandages", "packets"),
                        new ResourceRow("R3002", "20L Water Drum", "Food & Water > Drinking Water", "pieces")
                );
            }
        };

        loadTask.setOnSucceeded(e -> {
            dataList.setAll(loadTask.getValue());
            resourceTable.setItems(dataList);
        });

        loadTask.setOnFailed(e -> {
            errorLabel.setText("Failed to load resources from database");
            if (loadTask.getException() != null) {
                loadTask.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(loadTask, "resource-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        try {
            String name = nameField.getText();
            if (name == null || name.isBlank()) {
                errorLabel.setText("Resource name is required");
                return;
            }

            String category = categoryCombo.getValue();
            if (category == null) {
                errorLabel.setText("Select a category");
                return;
            }

            String unit = unitCombo.getValue();

            // TODO: call ResourceService.registerResource(name, categoryId, unit) ->
            // INSERT INTO resources (name, category_id, unit) VALUES (?, ?, ?)
            String resourceId = resourceIdField.getText().isBlank()
                    ? "R" + (3000 + dataList.size() + 1)
                    : resourceIdField.getText();

            ResourceRow row = new ResourceRow(resourceId, name, category, unit);
            dataList.add(0, row);
            resourceTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not add resource");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        try {
            String resourceId = resourceIdField.getText();
            if (resourceId == null || resourceId.isBlank()) {
                errorLabel.setText("Invalid resource ID");
                return;
            }

            // TODO: call ResourceService.updateResource(resourceId, name, categoryId, unit)
            // -> UPDATE resources SET name=?, category_id=?, unit=? WHERE resource_id=?

            errorLabel.setText("");
            loadResources();
        } catch (Exception e) {
            errorLabel.setText("Could not update resource");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        try {
            String resourceId = resourceIdField.getText();
            if (resourceId == null || resourceId.isBlank()) {
                errorLabel.setText("Invalid resource ID");
                return;
            }

            // TODO: call ResourceService.deleteResource(resourceId) -> DELETE FROM resources
            // WHERE resource_id = ?. Will be blocked by FK if shelter_inventory,
            // resource_requests, or distribution_log rows reference it.
            dataList.removeIf(r -> r.getResourceId().equals(resourceId));
            resourceTable.setItems(dataList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not delete resource");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleFind(ActionEvent event) {
        try {
            String resourceId = resourceIdField.getText();
            if (resourceId == null || resourceId.isBlank()) {
                errorLabel.setText("Invalid resource ID");
                return;
            }

            // TODO: call ResourceService.findById(resourceId) -> SELECT * FROM resources WHERE resource_id = ?
            ResourceRow found = dataList.stream()
                    .filter(r -> r.getResourceId().equals(resourceId))
                    .findFirst()
                    .orElse(null);

            if (found == null) {
                errorLabel.setText("Resource not found");
                return;
            }

            searchList.setAll(found);
            resourceTable.setItems(searchList);
            errorLabel.setText("");
        } catch (Exception e) {
            errorLabel.setText("Could not find resource");
            e.printStackTrace();
        }
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
}
