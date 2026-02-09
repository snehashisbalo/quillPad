package org.openjfx.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import org.openjfx.QuillPad;
import org.openjfx.SettingsManager;
import org.openjfx.ThemeManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class DashboardController implements Initializable {

    @FXML
    private Button homeButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Button newProjectButton;

    @FXML
    private ListView<String> recentProjectsList;

    @FXML
    private TextField searchField;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label statsLabel;

    @FXML
    private Button deleteButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button themeToggleButton;

    @FXML
    private Button settingsButton;

    private QuillPad mainApp;
    private File notesDir;
    private String currentUser;
    private ObservableList<String> allNotes;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome back, " + username + "!");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        notesDir = new File("notes");
        if (!notesDir.exists()) {
            notesDir.mkdir();
        }

        allNotes = FXCollections.observableArrayList();
        loadRecentProjects();
        updateStats();

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                filterNotes(newVal);
            });
        }

        if (deleteButton != null) {
            deleteButton.setDisable(true);
            recentProjectsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                deleteButton.setDisable(newVal == null);
            });
        }

        // Initialize theme button
        updateThemeButton();
    }

    private void loadRecentProjects() {
        allNotes.clear();
        if (notesDir.exists() && notesDir.isDirectory()) {
            // Show all files except hidden files (starting with .)
            File[] files = notesDir.listFiles((dir, name) -> !name.startsWith("."));
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

                for (File file : files) {
                    String noteName = file.getName();
                    // Remove extension for display
                    int lastDot = noteName.lastIndexOf('.');
                    if (lastDot > 0) {
                        noteName = noteName.substring(0, lastDot);
                    }
                    allNotes.add(noteName);
                }
            }
        }
        recentProjectsList.setItems(allNotes);
    }

    private void filterNotes(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            recentProjectsList.setItems(allNotes);
        } else {
            ObservableList<String> filtered = FXCollections.observableArrayList();
            String lowerSearch = searchText.toLowerCase();
            for (String note : allNotes) {
                if (note.toLowerCase().contains(lowerSearch)) {
                    filtered.add(note);
                }
            }
            recentProjectsList.setItems(filtered);
        }
    }

    private void updateStats() {
        if (statsLabel != null) {
            int noteCount = allNotes.size();
            statsLabel.setText(noteCount + " note" + (noteCount != 1 ? "s" : "") + " available");
        }
    }

    @FXML
    private void handleHome(ActionEvent event) {
        loadRecentProjects();
        updateStats();
        if (searchField != null) {
            searchField.clear();
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Are you sure you want to logout?");
        alert.setContentText("Any unsaved work will be lost.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            mainApp.showLogin();
        }
    }

    @FXML
    private void handleNewProject(ActionEvent event) {
        mainApp.showEditor(null);
    }

    @FXML
    private void handleProjectSelected(MouseEvent event) {
        if (event.getClickCount() == 2) {
            String selectedNote = recentProjectsList.getSelectionModel().getSelectedItem();
            if (selectedNote != null) {
                mainApp.showEditor(selectedNote);
            }
        }
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        String selectedNote = recentProjectsList.getSelectionModel().getSelectedItem();
        if (selectedNote != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Note");
            alert.setHeaderText("Delete \"" + selectedNote + "\"?");
            alert.setContentText("This action cannot be undone.");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                File fileToDelete = new File("notes/" + selectedNote + ".txt");
                if (fileToDelete.delete()) {
                    loadRecentProjects();
                    updateStats();
                    showInfo("Note deleted successfully");
                } else {
                    showError("Failed to delete note");
                }
            }
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadRecentProjects();
        updateStats();
        if (searchField != null) {
            searchField.clear();
        }
        showInfo("Notes refreshed");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void refreshList() {
        loadRecentProjects();
        updateStats();
    }

    @FXML
    private void handleThemeToggle(ActionEvent event) {
        ThemeManager.toggleTheme();
        updateThemeButton();
    }

    private void updateThemeButton() {
        if (themeToggleButton != null) {
            String icon = ThemeManager.getThemeIcon(ThemeManager.getCurrentTheme());
            themeToggleButton.setText(icon + " Theme");
        }
    }

    @FXML
    private void handleSettings(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Application Settings");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Font Family Selection
        Label fontLabel = new Label("Font Family:");
        ComboBox<String> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll("System", "Arial", "Consolas", "Courier New", "Monaco", "Verdana", "Georgia", "Times New Roman");
        fontCombo.setValue(SettingsManager.getFontFamily());
        grid.add(fontLabel, 0, 0);
        grid.add(fontCombo, 1, 0);

        // Font Size Selection
        Label sizeLabel = new Label("Font Size:");
        Spinner<Integer> sizeSpinner = new Spinner<>(8, 32, SettingsManager.getFontSize());
        sizeSpinner.setEditable(true);
        grid.add(sizeLabel, 0, 1);
        grid.add(sizeSpinner, 1, 1);

        // Theme Selection
        Label themeLabel = new Label("Theme:");
        ComboBox<String> themeCombo = new ComboBox<>();
        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            themeCombo.getItems().add(ThemeManager.getThemeIcon(theme) + " " + theme.getDisplayName());
        }
        themeCombo.setValue(ThemeManager.getThemeIcon(ThemeManager.getCurrentTheme()) + " " + ThemeManager.getCurrentTheme().getDisplayName());
        grid.add(themeLabel, 0, 2);
        grid.add(themeCombo, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveButtonType) {
            // Save font family
            String selectedFont = fontCombo.getValue();
            if (selectedFont != null) {
                SettingsManager.setFontFamily(selectedFont);
            }

            // Save font size
            SettingsManager.setFontSize(sizeSpinner.getValue());

            // Save theme
            int themeIndex = themeCombo.getSelectionModel().getSelectedIndex();
            if (themeIndex >= 0) {
                ThemeManager.setTheme(ThemeManager.Theme.values()[themeIndex]);
            }

            showInfo("Settings saved successfully!\nChanges will apply to new tabs.");
        }
    }
}
