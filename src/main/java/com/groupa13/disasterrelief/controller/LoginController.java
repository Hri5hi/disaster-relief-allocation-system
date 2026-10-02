package com.groupa13.disasterrelief.controller;

import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
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

    private final com.groupa13.disasterrelief.auth.AuthService authService;

    public LoginController() {
        this(new com.groupa13.disasterrelief.auth.AuthService());
    }

    public LoginController(com.groupa13.disasterrelief.auth.AuthService authService) {
        this.authService = authService;
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String userId = userIdField.getText();
        String password = passwordField.getText();

        if (userId == null || userId.isBlank() || password == null || password.isBlank()) {
            errorLabel.setText("Enter user ID and password");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("Signing in...");

        Task<com.groupa13.disasterrelief.service.core.ServiceResult<UserSession>> loginTask = new Task<>() {
            @Override
            protected com.groupa13.disasterrelief.service.core.ServiceResult<UserSession> call() {
                return authService.authenticate(userId, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            loginButton.setDisable(false);
            com.groupa13.disasterrelief.service.core.ServiceResult<UserSession> result = loginTask.getValue();
            if (result != null && result.success()) {
                errorLabel.setText("");
                changeScene(event, "dashboard", "Dashboard");
            } else {
                errorLabel.setText(result != null ? result.message() : "Invalid credentials");
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
}