package com.groupa13.disasterrelief.controller;

import com.groupa13.disasterrelief.util.UserSession;
import com.groupa13.disasterrelief.util.UserSession.UserRole;
import com.groupa13.disasterrelief.util.Utility;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/**
 * Handles user authentication and role selection for DRRAS.
 * On successful login, populates {@link UserSession} with the
 * authenticated user's identity, role, and (for ZONE_COORDINATOR)
 * their assigned zone, then hands off to the dashboard.
 */
public class LoginController extends Utility {

    @FXML
    private TextField userIdField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<UserRole> roleCombo;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    @FXML
    public void initialize() {
        errorLabel.setText("");
        loginButton.setDefaultButton(true);

        roleCombo.getItems().setAll(UserRole.values());
        roleCombo.setConverter(new StringConverter<UserRole>() {
            @Override
            public String toString(UserRole role) {
                return role == null ? "" : role.getDisplayName();
            }

            @Override
            public UserRole fromString(String string) {
                return null;
            }
        });
        roleCombo.getSelectionModel().selectFirst();
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String userId = userIdField.getText();
        String password = passwordField.getText();
        UserRole selectedRole = roleCombo.getValue();

        if (userId == null || userId.isBlank() || password == null || password.isBlank()) {
            errorLabel.setText("Enter user ID and password");
            return;
        }

        if (selectedRole == null) {
            errorLabel.setText("Select a role to continue");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("Signing in...");

        Task<Boolean> loginTask = new Task<Boolean>() {
            @Override
            protected Boolean call() {
                // TODO: replace with real authentication against the `people`-derived
                // credentials table (or a dedicated `app_users` table) via the DAO/service
                // layer, e.g. AuthService.authenticate(userId, password, selectedRole).
                // TODO: the returned user record should also supply assignedZoneId for
                // ZONE_COORDINATOR logins, pulled from the `zones` table.
                return authenticatePlaceholder(userId, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            loginButton.setDisable(false);
            if (Boolean.TRUE.equals(loginTask.getValue())) {
                errorLabel.setText("");

                // TODO: replace placeholder identity/zone values once AuthService is wired in.
                UserSession session = UserSession.getInstance();
                session.setUserId(userId);
                session.setUserName(userId);
                session.setRole(selectedRole);
                session.setAssignedZoneId(selectedRole == UserRole.ZONE_COORDINATOR ? 1 : null);

                // TODO: for ZONE_COORDINATOR sessions, the DAO layer must issue
                // SET app.current_zone_id = <assignedZoneId> on the underlying
                // connection/transaction before any RLS-protected query runs.

                changeScene(event, "dashboard", "Dashboard");
            } else {
                errorLabel.setText("Invalid user ID, password, or role");
            }
        });

        loginTask.setOnFailed(e -> {
            loginButton.setDisable(false);
            errorLabel.setText("Unable to connect. Try again.");
            if (loginTask.getException() != null) {
                loginTask.getException().printStackTrace();
            }
        });

        Thread loginThread = new Thread(loginTask, "login-worker");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    /**
     * TODO: DAO/service placeholder — remove once real authentication is implemented.
     * Currently accepts any non-blank credentials so the UI flow can be demoed
     * end to end before the backend is wired in.
     */
    private boolean authenticatePlaceholder(String userId, String password) {
        return userId != null && !userId.isBlank() && password != null && !password.isBlank();
    }
}