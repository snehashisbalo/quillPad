package org.openjfx.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import org.openjfx.QuillPad;
import org.openjfx.SettingsManager;
import org.openjfx.ThemeManager;
import org.openjfx.network.NetworkSyncService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private Button syncButton;

    @FXML
    private Button themeToggleButton;

    @FXML
    private Button settingsButton;

    @FXML
    private HBox homeNavItem;

    @FXML
    private HBox myNotesNavItem;

    @FXML
    private HBox starredNavItem;

    @FXML
    private HBox trashNavItem;

    private QuillPad mainApp;
    private File notesDir;
    private File trashDir;
    private String currentUser;
    private ObservableList<String> allNotes;
    private DashboardView currentView = DashboardView.MY_NOTES;
    private Set<String> starredNotes;

    private enum DashboardView {
        MY_NOTES,
        STARRED,
        TRASH
    }

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
        trashDir = new File(notesDir, "trash");
        if (!trashDir.exists()) {
            trashDir.mkdir();
        }

        starredNotes = new LinkedHashSet<>(SettingsManager.getStarredNotes());
        allNotes = FXCollections.observableArrayList();
        loadRecentProjects(currentView);
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

        setupNoteListContextMenu();
        // Initialize theme button
        updateThemeButton();
    }

    private void loadRecentProjects(DashboardView view) {
        currentView = view;
        allNotes.clear();
        File sourceDir = (view == DashboardView.TRASH) ? trashDir : notesDir;
        if (sourceDir.exists() && sourceDir.isDirectory()) {
            File[] files = sourceDir.listFiles((dir, name) -> {
                File file = new File(dir, name);
                if (!file.isFile()) {
                    return false;
                }
                return !name.startsWith(".");
            });
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                for (File file : files) {
                    String noteName = removeExtension(file.getName());
                    if (view == DashboardView.STARRED && !starredNotes.contains(noteName)) {
                        continue;
                    }
                    allNotes.add(noteName);
                }
            }
        }
        recentProjectsList.setItems(allNotes);
        updateViewUI();
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
            String prefix;
            if (currentView == DashboardView.STARRED) {
                prefix = "starred ";
            } else if (currentView == DashboardView.TRASH) {
                prefix = "trashed ";
            } else {
                prefix = "";
            }
            statsLabel.setText(noteCount + " " + prefix + "note" + (noteCount != 1 ? "s" : "") + " available");
        }
    }

    @FXML
    private void handleHome(ActionEvent event) {
        loadRecentProjects(DashboardView.MY_NOTES);
        updateStats();
        if (searchField != null) {
            searchField.clear();
        }
    }

    @FXML
    private void handleMyNotes(MouseEvent event) {
        loadRecentProjects(DashboardView.MY_NOTES);
        updateStats();
    }

    @FXML
    private void handleStarred(MouseEvent event) {
        loadRecentProjects(DashboardView.STARRED);
        updateStats();
    }

    @FXML
    private void handleTrash(MouseEvent event) {
        loadRecentProjects(DashboardView.TRASH);
        updateStats();
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
                if (currentView == DashboardView.TRASH) {
                    restoreNoteFromTrash(selectedNote);
                } else {
                    mainApp.showEditor(selectedNote);
                }
            }
        }
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        String selectedNote = recentProjectsList.getSelectionModel().getSelectedItem();
        if (selectedNote != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            if (currentView == DashboardView.TRASH) {
                alert.setTitle("Delete Permanently");
                alert.setHeaderText("Permanently delete \"" + selectedNote + "\"?");
                alert.setContentText("This action cannot be undone.");
            } else {
                alert.setTitle("Move to Trash");
                alert.setHeaderText("Move \"" + selectedNote + "\" to Trash?");
                alert.setContentText("You can restore it later from Trash.");
            }

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (currentView == DashboardView.TRASH) {
                    File fileToDelete = new File(trashDir, selectedNote + ".txt");
                    if (fileToDelete.delete()) {
                        loadRecentProjects(DashboardView.TRASH);
                        updateStats();
                        showInfo("Note permanently deleted");
                        NetworkSyncService.deleteNoteAsync(currentUser, selectedNote);
                    } else {
                        showError("Failed to delete note");
                    }
                } else {
                    if (moveNoteToTrash(selectedNote)) {
                        loadRecentProjects(currentView);
                        updateStats();
                        showInfo("Note moved to trash");
                    } else {
                        showError("Failed to move note to trash");
                    }
                }
            }
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadRecentProjects(currentView);
        updateStats();
        if (searchField != null) {
            searchField.clear();
        }
        showInfo("Notes refreshed");
    }

    @FXML
    private void handleSync(ActionEvent event) {
        if (currentUser == null || currentUser.isBlank()) {
            showError("No active user session. Please log out and log in again.");
            return;
        }

        if (!NetworkSyncService.isConfigured()) {
            showError("Network sync is disabled. Open Settings and enable Network Sync first.");
            return;
        }

        if (syncButton != null) {
            syncButton.setDisable(true);
        }

        NetworkSyncService.syncAllNotesAsync(currentUser, notesDir)
            .whenComplete((summary, throwable) -> Platform.runLater(() -> {
                if (syncButton != null) {
                    syncButton.setDisable(false);
                }
                if (throwable != null) {
                    showError("Sync failed: " + throwable.getMessage());
                    return;
                }
                StringBuilder message = new StringBuilder("Sync complete.\nAttempted: " + summary.attempted()
                    + "\nSucceeded: " + summary.success()
                    + "\nFailed: " + summary.failed());
                if (summary.failed() > 0 && summary.errorSamples() != null && !summary.errorSamples().isEmpty()) {
                    message.append("\n\nSample errors:");
                    for (String error : summary.errorSamples()) {
                        message.append("\n- ").append(error);
                    }
                }
                showInfo(message.toString());
            }));
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
        loadRecentProjects(currentView);
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

        // Network Settings
        CheckBox networkEnabled = new CheckBox("Enable Network Sync");
        networkEnabled.setSelected(SettingsManager.isNetworkEnabled());
        grid.add(networkEnabled, 0, 3, 2, 1);

        Label networkUrlLabel = new Label("Server URL:");
        TextField networkUrlField = new TextField(SettingsManager.getNetworkBaseUrl());
        networkUrlField.setPromptText("http://localhost:8080");
        grid.add(networkUrlLabel, 0, 4);
        grid.add(networkUrlField, 1, 4);

        Label apiKeyLabel = new Label("API Key:");
        PasswordField apiKeyField = new PasswordField();
        apiKeyField.setText(SettingsManager.getNetworkApiKey());
        grid.add(apiKeyLabel, 0, 5);
        grid.add(apiKeyField, 1, 5);

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

            // Save network settings
            SettingsManager.setNetworkEnabled(networkEnabled.isSelected());
            SettingsManager.setNetworkBaseUrl(networkUrlField.getText());
            SettingsManager.setNetworkApiKey(apiKeyField.getText());

            showInfo("Settings saved successfully!\nChanges will apply to new tabs.");
        }
    }

    private void setupNoteListContextMenu() {
        recentProjectsList.setCellFactory(listView -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else if (currentView == DashboardView.STARRED) {
                        setText("★ " + item);
                    } else {
                        setText(item);
                    }
                }
            };

            MenuItem toggleStarItem = new MenuItem("Toggle Star");
            toggleStarItem.setOnAction(e -> {
                String note = cell.getItem();
                if (note != null && currentView != DashboardView.TRASH) {
                    toggleStar(note);
                }
            });

            MenuItem restoreItem = new MenuItem("Restore from Trash");
            restoreItem.setOnAction(e -> {
                String note = cell.getItem();
                if (note != null && currentView == DashboardView.TRASH) {
                    restoreNoteFromTrash(note);
                }
            });

            ContextMenu contextMenu = new ContextMenu(toggleStarItem, restoreItem);
            cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    cell.setContextMenu(null);
                } else if (currentView == DashboardView.TRASH) {
                    restoreItem.setVisible(true);
                    toggleStarItem.setVisible(false);
                    cell.setContextMenu(contextMenu);
                } else {
                    restoreItem.setVisible(false);
                    toggleStarItem.setVisible(true);
                    cell.setContextMenu(contextMenu);
                }
            });

            return cell;
        });
    }

    private void toggleStar(String noteName) {
        if (starredNotes.contains(noteName)) {
            starredNotes.remove(noteName);
            showInfo("Removed from starred: " + noteName);
        } else {
            starredNotes.add(noteName);
            showInfo("Added to starred: " + noteName);
        }
        SettingsManager.setStarredNotes(starredNotes);
        if (currentView == DashboardView.STARRED) {
            loadRecentProjects(DashboardView.STARRED);
            updateStats();
        }
    }

    private boolean moveNoteToTrash(String noteName) {
        File source = resolveNoteFile(notesDir, noteName);
        if (source == null || !source.exists()) {
            return false;
        }
        File target = new File(trashDir, source.getName());
        if (target.exists()) {
            String timestampedName = noteName + "-" + System.currentTimeMillis() + ".txt";
            target = new File(trashDir, timestampedName);
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void restoreNoteFromTrash(String noteName) {
        File source = resolveNoteFile(trashDir, noteName);
        if (source == null || !source.exists()) {
            showError("Note not found in trash");
            return;
        }
        File target = new File(notesDir, source.getName());
        if (target.exists()) {
            target = new File(notesDir, noteName + "-restored-" + System.currentTimeMillis() + ".txt");
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            loadRecentProjects(DashboardView.TRASH);
            updateStats();
            showInfo("Restored: " + removeExtension(target.getName()));
        } catch (IOException e) {
            showError("Failed to restore note");
        }
    }

    private File resolveNoteFile(File dir, String noteName) {
        if (dir == null || !dir.exists()) {
            return null;
        }
        File[] candidates = dir.listFiles((d, name) -> removeExtension(name).equals(noteName));
        if (candidates != null && candidates.length > 0) {
            return candidates[0];
        }
        return new File(dir, noteName + ".txt");
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    private void updateViewUI() {
        if (deleteButton != null) {
            if (currentView == DashboardView.TRASH) {
                deleteButton.setText("🗑️ Delete Permanently");
            } else {
                deleteButton.setText("🗑️ Move to Trash");
            }
        }
    }
}
