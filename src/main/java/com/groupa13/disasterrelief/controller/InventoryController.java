package com.groupa13.disasterrelief.controller;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.db.DatabaseConfig;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.LowStockAlert;
import com.groupa13.disasterrelief.model.core.ShelterInventory;
import com.groupa13.disasterrelief.service.core.InventoryService;
import com.groupa13.disasterrelief.service.core.LowStockNotificationListener;
import com.groupa13.disasterrelief.service.core.PostgresLowStockNotificationListener;
import com.groupa13.disasterrelief.service.core.ResourceService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.core.ShelterService;
import com.groupa13.disasterrelief.util.Utility;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class InventoryController extends Utility implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(InventoryController.class.getName());
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private ComboBox<EntityOption> shelterCombo;
    @FXML private ComboBox<EntityOption> resourceCombo;
    @FXML private TextField quantityField;
    @FXML private TextField reorderLevelField;
    @FXML private TextField lastUpdatedField;
    @FXML private Label errorLabel;
    @FXML private Label lowStockBadge;
    @FXML private TableView<ShelterInventory> inventoryTable;
    @FXML private TableColumn<ShelterInventory, String> colShelter;
    @FXML private TableColumn<ShelterInventory, String> colResource;
    @FXML private TableColumn<ShelterInventory, String> colQuantity;
    @FXML private TableColumn<ShelterInventory, String> colReorderLevel;
    @FXML private TableColumn<ShelterInventory, String> colLastUpdated;
    @FXML private TableColumn<ShelterInventory, String> colStockStatus;

    private final ObservableList<ShelterInventory> dataList = FXCollections.observableArrayList();
    private final InventoryService inventoryService;
    private final ShelterService shelterService;
    private final ResourceService resourceService;
    private LowStockNotificationListener notificationListener;
    private boolean showingLowStockOnly;

    public InventoryController() {
        this(new InventoryService(), new ShelterService(), new ResourceService(), null);
    }

    InventoryController(InventoryService inventoryService, ShelterService shelterService,
            ResourceService resourceService, LowStockNotificationListener notificationListener) {
        this.inventoryService = inventoryService;
        this.shelterService = shelterService;
        this.resourceService = resourceService;
        this.notificationListener = notificationListener;
    }

    @FXML
    public void initialize() {
        colShelter.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                "S" + cell.getValue().shelterId() + " — " + cell.getValue().shelterName()));
        colResource.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                "R" + cell.getValue().resourceId() + " — " + cell.getValue().resourceName()));
        colQuantity.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(Integer.toString(cell.getValue().quantity())));
        colReorderLevel.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(Integer.toString(cell.getValue().reorderLevel())));
        colLastUpdated.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().lastUpdated().format(TIMESTAMP)));
        colStockStatus.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().lowStock() ? "LOW STOCK" : "OK"));
        inventoryTable.setRowFactory(ignored -> new TableRow<>() {
            @Override
            protected void updateItem(ShelterInventory inventory, boolean empty) {
                super.updateItem(inventory, empty);
                getStyleClass().remove("row-low-stock");
                if (!empty && inventory != null && inventory.lowStock()) {
                    getStyleClass().add("row-low-stock");
                }
            }
        });
        inventoryTable.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> populateForm(selected));
        loadShelterOptions();
        loadResourceOptions();
        loadInventory();
        startNotificationListener();
    }

    private void loadShelterOptions() {
        runServiceTask(shelterService::listShelterOptions,
                options -> shelterCombo.getItems().setAll(options),
                "inventory-shelter-options");
    }

    private void loadResourceOptions() {
        runServiceTask(resourceService::listResourceOptions,
                options -> resourceCombo.getItems().setAll(options),
                "inventory-resource-options");
    }

    private void loadInventory() {
        runServiceTask(inventoryService::listInventory, inventory -> {
            dataList.setAll(inventory);
            applyCurrentFilter();
            updateLowStockBadge();
        }, "inventory-loader");
    }

    @FXML
    public void handleToggleLowStockOnly(ActionEvent event) {
        showingLowStockOnly = !showingLowStockOnly;
        applyCurrentFilter();
    }

    @FXML
    public void handleAdd(ActionEvent event) {
        saveInventory("inventory-create");
    }

    @FXML
    public void handleUpdate(ActionEvent event) {
        saveInventory("inventory-update");
    }

    private void saveInventory(String threadName) {
        EntityOption shelter = shelterCombo.getValue();
        EntityOption resource = resourceCombo.getValue();
        if (shelter == null || resource == null) {
            errorLabel.setText("Select a shelter and a resource");
            return;
        }
        int[] values = parseStockValues();
        if (values == null) {
            return;
        }
        runServiceTask(() -> inventoryService.replaceInventory(
                shelter.id(), resource.id(), values[0], values[1]), inventory -> {
                    upsertDisplayedRow(inventory);
                    populateForm(inventory);
                }, threadName);
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        EntityOption shelter = shelterCombo.getValue();
        EntityOption resource = resourceCombo.getValue();
        if (shelter == null || resource == null) {
            errorLabel.setText("Select a shelter and a resource to delete");
            return;
        }
        runServiceTask(() -> inventoryService.deleteInventory(shelter.id(), resource.id()),
                ignored -> {
                    dataList.removeIf(row -> row.shelterId() == shelter.id()
                            && row.resourceId() == resource.id());
                    applyCurrentFilter();
                    updateLowStockBadge();
                    handleClear(null);
                }, "inventory-delete");
    }

    @FXML
    public void handleFind(ActionEvent event) {
        EntityOption shelter = shelterCombo.getValue();
        EntityOption resource = resourceCombo.getValue();
        if (shelter == null || resource == null) {
            errorLabel.setText("Select a shelter and a resource to find");
            return;
        }
        runServiceTask(() -> inventoryService.findInventory(shelter.id(), resource.id()),
                inventory -> {
                    inventoryTable.setItems(FXCollections.observableArrayList(inventory));
                    populateForm(inventory);
                }, "inventory-find");
    }

    @FXML
    public void handleClear(ActionEvent event) {
        shelterCombo.getSelectionModel().clearSelection();
        resourceCombo.getSelectionModel().clearSelection();
        quantityField.clear();
        reorderLevelField.clear();
        lastUpdatedField.clear();
        errorLabel.setText("");
        applyCurrentFilter();
    }

    @FXML
    public void goBack(ActionEvent event) {
        close();
        changeScene(event, "dashboard", "Dashboard");
    }

    @Override
    public void close() {
        LowStockNotificationListener listener = notificationListener;
        notificationListener = null;
        if (listener != null) {
            listener.close();
        }
    }

    private int[] parseStockValues() {
        try {
            int quantity = Integer.parseInt(quantityField.getText().trim());
            String thresholdText = reorderLevelField.getText() == null
                    ? "" : reorderLevelField.getText().trim();
            int reorderLevel = thresholdText.isEmpty() ? 10 : Integer.parseInt(thresholdText);
            if (quantity < 0 || reorderLevel < 0) {
                errorLabel.setText("Quantity and reorder level cannot be negative");
                return null;
            }
            return new int[] {quantity, reorderLevel};
        } catch (NumberFormatException exception) {
            errorLabel.setText("Quantity and reorder level must be whole numbers");
            return null;
        }
    }

    private void applyCurrentFilter() {
        if (showingLowStockOnly) {
            ObservableList<ShelterInventory> filtered = FXCollections.observableArrayList();
            dataList.stream().filter(ShelterInventory::lowStock).forEach(filtered::add);
            inventoryTable.setItems(filtered);
        } else {
            inventoryTable.setItems(dataList);
        }
    }

    private void updateLowStockBadge() {
        long count = dataList.stream().filter(ShelterInventory::lowStock).count();
        lowStockBadge.setText(count + " Low Stock");
    }

    private void upsertDisplayedRow(ShelterInventory inventory) {
        int replacementIndex = -1;
        for (int index = 0; index < dataList.size(); index++) {
            ShelterInventory row = dataList.get(index);
            if (row.shelterId() == inventory.shelterId()
                    && row.resourceId() == inventory.resourceId()) {
                replacementIndex = index;
                break;
            }
        }
        if (replacementIndex < 0) {
            dataList.add(inventory);
        } else {
            dataList.set(replacementIndex, inventory);
        }
        applyCurrentFilter();
        updateLowStockBadge();
    }

    private void populateForm(ShelterInventory inventory) {
        if (inventory == null) {
            return;
        }
        selectOption(shelterCombo, inventory.shelterId());
        selectOption(resourceCombo, inventory.resourceId());
        quantityField.setText(Integer.toString(inventory.quantity()));
        reorderLevelField.setText(Integer.toString(inventory.reorderLevel()));
        lastUpdatedField.setText(inventory.lastUpdated().format(TIMESTAMP));
    }

    private static void selectOption(ComboBox<EntityOption> combo, int id) {
        combo.getItems().stream().filter(option -> option.id() == id).findFirst()
                .ifPresent(combo::setValue);
    }

    private void startNotificationListener() {
        try {
            if (notificationListener == null) {
                notificationListener = new PostgresLowStockNotificationListener(
                        DatabaseConfig.load());
            }
            notificationListener.start(alert ->
                    Platform.runLater(() -> refreshInventoryRow(alert)));
        } catch (DatabaseException | IllegalStateException exception) {
            LOGGER.log(Level.WARNING, "Low-stock listener is unavailable", exception);
        }
    }

    private void refreshInventoryRow(LowStockAlert alert) {
        runServiceTask(() -> inventoryService.findInventory(
                alert.shelterId(), alert.resourceId()), this::upsertDisplayedRow,
                "inventory-notification-refresh");
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
            LOGGER.log(Level.SEVERE, "Inventory operation failed", task.getException());
            errorLabel.setText("Inventory operation failed");
        });
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
