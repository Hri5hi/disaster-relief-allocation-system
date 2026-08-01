package com.groupa13.disasterrelief.util;

import java.io.IOException;
import java.util.Arrays;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
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

    /**
     * Navigate to a new FXML scene with optional RBAC enforcement.
     *
     * <p>If {@code allowedRoles} is non-empty and the current session role is
     * not in that list, an "Access Denied" alert is shown and navigation is
     * aborted — the user stays on the current screen.
     *
     * @param event        the action event whose source node supplies the Stage
     * @param fxmlName     the bare FXML filename <em>without</em> extension
     *                     (e.g. {@code "dashboard"}, {@code "shelter"})
     * @param title        the window title to set on the Stage
     * @param allowedRoles zero or more roles permitted to access this screen;
     *                     pass none (varargs empty) to allow all roles
     */
    public void changeScene(Event event, String fxmlName, String title,
                            UserRole... allowedRoles) {

        // ── RBAC Guard ─────────────────────────────────────────────────────
        if (allowedRoles.length > 0) {
            UserRole currentRole = UserSession.getInstance().getRole();
            if (!Arrays.asList(allowedRoles).contains(currentRole)) {
                showErrorAlert("Access Denied",
                        "Your role (" + (currentRole == null ? "none" : currentRole.getDisplayName())
                        + ") does not have permission to access this screen.");
                return;
            }
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
            stage.setTitle(title);
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Navigation Error",
                    "Could not load screen: " + fxmlName + ".fxml\n" + e.getMessage());
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
