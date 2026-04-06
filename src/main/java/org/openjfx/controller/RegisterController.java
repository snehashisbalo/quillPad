package org.openjfx.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import org.openjfx.QuillPad;
import org.openjfx.ThemeManager;
import org.openjfx.auth.UserStore;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class RegisterController implements Initializable {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField passwordTextField;

    @FXML
    private CheckBox showPasswordCheckBox;

    @FXML
    private Button themeToggleButton;

    @FXML
    private Label errorLabel;

    private QuillPad mainApp;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> passwordTextField.setText(newVal));
        passwordTextField.textProperty().addListener((obs, oldVal, newVal) -> passwordField.setText(newVal));

        usernameField.setOnKeyPressed(this::handleKeyPress);
        passwordField.setOnKeyPressed(this::handleKeyPress);
        passwordTextField.setOnKeyPressed(this::handleKeyPress);

        usernameField.textProperty().addListener((obs, oldVal, newVal) -> clearError());
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> clearError());

        updateThemeButton();
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleCreateAccount(null);
        }
    }

    @FXML
    private void handleCreateAccount(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();

        if (username.isEmpty()) {
            showError("Please enter a username");
            usernameField.requestFocus();
            return;
        }

        if (password == null || password.isBlank()) {
            showError("Please enter a password");
            passwordField.requestFocus();
            return;
        }

        if (username.length() < 3) {
            showError("Username must be at least 3 characters");
            return;
        }

        if (username.contains(":") || password.contains(":")) {
            showError("Username and password cannot contain ':'");
            return;
        }

        if (UserStore.userExists(username)) {
            showError("Username already exists");
            return;
        }

        try {
            UserStore.registerUser(username, password);
            clearError();
            mainApp.showDashboard(username);
        } catch (IOException e) {
            showError("Error saving user data.");
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        mainApp.showLogin();
    }

    @FXML
    private void handleShowPasswordToggle(ActionEvent event) {
        boolean show = showPasswordCheckBox.isSelected();
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordTextField.setVisible(show);
        passwordTextField.setManaged(show);
    }

    @FXML
    private void handleThemeToggle(ActionEvent event) {
        ThemeManager.toggleTheme();
        updateThemeButton();
    }

    private void updateThemeButton() {
        if (themeToggleButton != null) {
            themeToggleButton.setText(ThemeManager.getThemeIcon(ThemeManager.getCurrentTheme()));
        }
    }

    private void clearError() {
        if (errorLabel != null) {
            errorLabel.setText("");
        }
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setTextFill(Color.RED);
            errorLabel.setText(message);
        }
    }
}
