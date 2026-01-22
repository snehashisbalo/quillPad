package org.openjfx.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.openjfx.QuillPad;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    private QuillPad mainApp;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Dummy authentication: always succeeds
        if (!username.isEmpty() && !password.isEmpty()) {
            mainApp.showDashboard();
        } else {
            // For now, just proceed anyway
            mainApp.showDashboard();
        }
    }
}