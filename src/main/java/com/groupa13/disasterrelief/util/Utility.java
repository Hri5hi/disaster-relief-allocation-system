package com.groupa13.disasterrelief.util;

import java.io.IOException;
import java.util.Arrays;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Base utility class that all controllers extend.
 *
 * <p>Provides a centralized, RBAC-guarded scene-change mechanism (§5 of
 * UI_IMPLEMENTATION_PLAN.md).  Every navigation action goes through
 * {@link #changeScene(Event, String, String, UserRole...)} so authorization
 * is enforced at the point of navigation, not just by hiding buttons.
 *
 * <p>CSS from {@code /css/application.css} is applied automatically on every
 * scene transition, so individual screens do not need to load the stylesheet
 * themselves.
 *
 * @author Developer A
 */
public class Utility {

    private static final java.util.Map<String, java.util.Set<UserRole>> SCREEN_PERMISSIONS = java.util.Map.ofEntries(
            java.util.Map.entry("login", java.util.Set.of()),
            java.util.Map.entry("dashboard", java.util.Set.of(UserRole.values())),
            java.util.Map.entry("disaster", java.util.Set.of(UserRole.SYSTEM_ADMIN)),
            java.util.Map.entry("zone", java.util.Set.of(UserRole.SYSTEM_ADMIN)),
            java.util.Map.entry("shelter", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.ZONE_COORDINATOR, UserRole.ANALYTICS_USER)),
            java.util.Map.entry("resource", java.util.Set.of(UserRole.SYSTEM_ADMIN)),
            java.util.Map.entry("inventory", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.WAREHOUSE_MANAGER)),
            java.util.Map.entry("resource_request", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.ZONE_COORDINATOR, UserRole.WAREHOUSE_MANAGER)),
            java.util.Map.entry("distribution_log", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.WAREHOUSE_MANAGER, UserRole.ANALYTICS_USER)),
            java.util.Map.entry("volunteer", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.VOLUNTEER_COORDINATOR)),
            java.util.Map.entry("volunteer_shift", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.VOLUNTEER_COORDINATOR)),
            java.util.Map.entry("affected_person", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.ZONE_COORDINATOR)),
            java.util.Map.entry("donation", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.WAREHOUSE_MANAGER)),
            java.util.Map.entry("analytics", java.util.Set.of(UserRole.SYSTEM_ADMIN, UserRole.ZONE_COORDINATOR, UserRole.ANALYTICS_USER)),
            java.util.Map.entry("audit_log", java.util.Set.of(UserRole.SYSTEM_ADMIN))
    );

    /**
     * Navigate to a new FXML scene with central RBAC enforcement.
     *
     * <p>If {@code allowedRoles} is non-empty, it overrides the central screen map.
     * Otherwise, permissions are looked up from the central map. If the current
     * session role is not permitted, an "Access Denied" alert is shown and
     * navigation is aborted.
     */
    public void changeScene(Event event, String fxmlName, String title,
                            UserRole... allowedRoles) {

        // ── RBAC Guard ─────────────────────────────────────────────────────
        UserRole currentRole = UserSession.getInstance().getRole();
        java.util.Set<UserRole> permitted = allowedRoles.length > 0
                ? java.util.Set.of(allowedRoles)
                : SCREEN_PERMISSIONS.getOrDefault(fxmlName, java.util.Set.of());

        if (!permitted.isEmpty() && !permitted.contains(currentRole)) {
            showErrorAlert("Access Denied",
                    "Your role (" + (currentRole == null ? "none" : currentRole.getDisplayName())
                    + ") does not have permission to access screen: " + fxmlName);
            return;
        }

        // ── Scene Load ─────────────────────────────────────────────────────
        try {
            String fxmlPath = "/fxml/" + fxmlName + ".fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            // Apply the shared stylesheet
            java.net.URL css = getClass().getResource("/css/application.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            StageState previousState = StageState.capture(stage);
            previousState.leaveWindowModeForSceneReplacement(stage);
            stage.setTitle(title);
            stage.setScene(scene);
            previousState.restoreBounds(stage);
            stage.show();

            // A new scene must be attached while the stage is in its normal
            // mode. Some Linux window managers otherwise retain the full-screen
            // flag but leave the new scene at its old, windowed dimensions.
            previousState.restoreWindowMode(stage);
            Platform.runLater(() -> {
                previousState.restoreWindowMode(stage);
                previousState.ensureFullScreenSceneFits(stage);
            });

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Navigation Error",
                    "Could not load screen: " + fxmlName + ".fxml\n" + e.getMessage());
        }
    }

    /** Window geometry captured immediately before a scene transition. */
    private record StageState(boolean fullScreen, boolean maximized,
                              double x, double y, double width, double height) {

        private static StageState capture(Stage stage) {
            return new StageState(
                    stage.isFullScreen(),
                    stage.isMaximized(),
                    stage.getX(),
                    stage.getY(),
                    stage.getWidth(),
                    stage.getHeight());
        }

        private void leaveWindowModeForSceneReplacement(Stage stage) {
            if (fullScreen) {
                stage.setFullScreen(false);
            }
            if (maximized) {
                stage.setMaximized(false);
            }
        }

        private void restoreBounds(Stage stage) {
            if (fullScreen || maximized) {
                return;
            }
            if (Double.isFinite(x) && Double.isFinite(y)) {
                stage.setX(x);
                stage.setY(y);
            }
            if (Double.isFinite(width) && width > 0) {
                stage.setWidth(Math.max(width, stage.getMinWidth()));
            }
            if (Double.isFinite(height) && height > 0) {
                stage.setHeight(Math.max(height, stage.getMinHeight()));
            }
        }

        private void restoreWindowMode(Stage stage) {
            if (maximized) {
                stage.setMaximized(true);
            }
            if (fullScreen) {
                stage.setFullScreen(true);
            }
        }

        /**
         * Work around window managers that report full-screen mode as enabled
         * but do not resize a newly attached JavaFX scene to the display.
         */
        private void ensureFullScreenSceneFits(Stage stage) {
            if (!fullScreen || !stage.isFullScreen() || stage.getScene() == null) {
                return;
            }

            Rectangle2D displayBounds = targetScreen(stage).getBounds();
            double sceneWidth = stage.getScene().getWidth();
            double sceneHeight = stage.getScene().getHeight();
            if (sceneWidth >= displayBounds.getWidth() - 1
                    && sceneHeight >= displayBounds.getHeight() - 1) {
                return;
            }

            stage.setFullScreen(false);
            stage.setMaximized(false);
            stage.setX(displayBounds.getMinX());
            stage.setY(displayBounds.getMinY());
            stage.setWidth(Math.max(displayBounds.getWidth(), stage.getMinWidth()));
            stage.setHeight(Math.max(displayBounds.getHeight(), stage.getMinHeight()));
            stage.setFullScreen(true);
        }

        private Screen targetScreen(Stage stage) {
            return Screen.getScreensForRectangle(
                    stage.getX(), stage.getY(),
                    Math.max(stage.getWidth(), 1), Math.max(stage.getHeight(), 1))
                    .stream()
                    .findFirst()
                    .orElse(Screen.getPrimary());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows a modal error alert dialog.
     *
     * @param header  the bold header text
     * @param content the body message
     */
    public static void showErrorAlert(String header, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Shows a modal informational alert dialog.
     *
     * @param header  the bold header text
     * @param content the body message
     */
    public static void showInfoAlert(String header, String content) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Shows a modal warning alert dialog.
     *
     * @param header  the bold header text
     * @param content the body message
     */
    public static void showWarningAlert(String header, String content) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
