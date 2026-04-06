package org.openjfx.controller;

import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField passwordTextField;

    @FXML
    private CheckBox showPasswordCheckBox;

    @FXML
    private Button loginButton;

    @FXML
    private Button themeToggleButton;

    @FXML
    private Label errorLabel;

    @FXML
    private Label appTitleLabel;

    @FXML
    private Label appSubtitleLabel1;

    @FXML
    private Label appSubtitleLabel2;

    private QuillPad mainApp;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup password field sync
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordTextField.setText(newVal);
        });
        passwordTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordField.setText(newVal);
        });

        // Setup key handlers
        passwordField.setOnKeyPressed(this::handleKeyPress);
        passwordTextField.setOnKeyPressed(this::handleKeyPress);
        usernameField.setOnKeyPressed(this::handleKeyPress);

        // Clear error on input
        usernameField.textProperty().addListener((obs, old, newVal) -> {
            if (errorLabel != null) {
                errorLabel.setText("");
            }
        });

        passwordField.textProperty().addListener((obs, old, newVal) -> {
            if (errorLabel != null) {
                errorLabel.setText("");
            }
        });

        // Initialize theme button
        updateThemeButton();

        // Bind responsive fonts when scene is available
        usernameField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                setupResponsiveFonts(newScene);
            }
        });
    }

    private void setupResponsiveFonts(Scene scene) {
        // Bind font sizes to scene width
        if (appTitleLabel != null) {
            appTitleLabel.styleProperty().bind(
                Bindings.concat("-fx-font-size: ", 
                    Bindings.min(48, Bindings.max(24, scene.widthProperty().divide(25))).asString(), "px;")
            );
        }
        if (appSubtitleLabel1 != null) {
            appSubtitleLabel1.styleProperty().bind(
                Bindings.concat("-fx-font-size: ", 
                    Bindings.min(28, Bindings.max(16, scene.widthProperty().divide(40))).asString(), "px;")
            );
        }
        if (appSubtitleLabel2 != null) {
            appSubtitleLabel2.styleProperty().bind(
                Bindings.concat("-fx-font-size: ", 
                    Bindings.min(28, Bindings.max(16, scene.widthProperty().divide(40))).asString(), "px;")
            );
        }
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleLogin(null);
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty()) {
            showError("Please enter a username");
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter a password");
            passwordField.requestFocus();
            return;
        }

        if (username.length() < 3) {
            showError("Username must be at least 3 characters");
            return;
        }

        if (UserStore.authenticate(username, password)) {
            errorLabel.setText("");
            mainApp.showDashboard(username);
        } else {
            showError("Invalid username or password");
        }
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        mainApp.showRegister();
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setTextFill(Color.RED);
        }
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

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Forgot Password");
        alert.setHeaderText("Password Recovery");
        alert.setContentText("Please contact the administrator to reset your password.\n\nDefault credentials:\nUsername: admin\nPassword: admin");
        alert.showAndWait();
    }
}
