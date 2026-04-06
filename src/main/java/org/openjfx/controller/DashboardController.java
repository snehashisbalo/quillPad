package org.openjfx.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import org.openjfx.QuillPad;
import org.openjfx.RemoteNoteRegistry;
import org.openjfx.SettingsManager;
import org.openjfx.ThemeManager;
import org.openjfx.network.NetworkSyncService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    private Button openFileButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button uploadButton;

    @FXML
    private Button remoteButton;

    @FXML
    private Button myUploadsButton;

    @FXML
    private Button themeToggleButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Button resetStorageButton;

    @FXML
    private HBox homeNavItem;

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
        configureStoragePaths();
        if (allNotes != null) {
            loadRecentProjects(currentView);
            updateStats();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        starredNotes = new LinkedHashSet<>(SettingsManager.getStarredNotes());
        allNotes = FXCollections.observableArrayList();
        configureStoragePaths();
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
                    String noteName = toDisplayNoteName(file.getName());
                    if (view == DashboardView.STARRED
                            && !starredNotes.contains(noteName)
                            && !starredNotes.contains(removeExtension(file.getName()))) {
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
        mainApp.showEditor((String) null);
    }

    @FXML
    private void handleOpenFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md", "*.json", "*.xml", "*.yaml", "*.yml"),
            new FileChooser.ExtensionFilter("Code Files", "*.java", "*.py", "*.js", "*.ts", "*.c", "*.cpp", "*.cs", "*.go", "*.rs", "*.php", "*.rb", "*.sql", "*.css", "*.html", "*.sh")
        );
        if (notesDir != null && notesDir.exists()) {
            fileChooser.setInitialDirectory(notesDir);
        }

        File selectedFile = fileChooser.showOpenDialog(mainApp.getPrimaryStage());
        if (selectedFile != null) {
            mainApp.showEditor(selectedFile);
        }
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
                    File fileToDelete = resolveNoteFile(trashDir, selectedNote);
                    if (fileToDelete.delete()) {
                        loadRecentProjects(DashboardView.TRASH);
                        updateStats();
                        showInfo("Note permanently deleted");
                        NetworkSyncService.deleteNoteAsync(remoteNamespace(), selectedNote);
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
    private void handleRemoteBrowse(ActionEvent event) {
        if (!validateRemoteFeaturePrerequisites()) {
            return;
        }

        if (remoteButton != null) {
            remoteButton.setDisable(true);
        }

        NetworkSyncService.listRemoteNotesDetailedAsync(remoteNamespace())
            .whenComplete((notes, throwable) -> Platform.runLater(() -> {
                if (remoteButton != null) {
                    remoteButton.setDisable(false);
                }
                if (throwable != null) {
                    showError("Failed to fetch remote notes: " + throwable.getMessage());
                    return;
                }
                showRemoteNotesDialog(notes == null ? List.of() : notes);
            }));
    }

    @FXML
    private void handleMyUploads(ActionEvent event) {
        if (!validateRemoteFeaturePrerequisites()) {
            return;
        }

        if (myUploadsButton != null) {
            myUploadsButton.setDisable(true);
        }

        NetworkSyncService.listRemoteNotesDetailedAsync(remoteNamespace())
            .whenComplete((notes, throwable) -> Platform.runLater(() -> {
                if (myUploadsButton != null) {
                    myUploadsButton.setDisable(false);
                }
                if (throwable != null) {
                    showError("Failed to fetch your uploaded notes: " + throwable.getMessage());
                    return;
                }
                List<NetworkSyncService.RemoteNoteRef> uploadedNotes = (notes == null ? List.<NetworkSyncService.RemoteNoteRef>of() : notes)
                    .stream()
                    .filter(note -> currentUser != null && currentUser.equals(note.author()))
                    .toList();
                showMyUploadsDialog(uploadedNotes);
            }));
    }

    @FXML
    private void handleUploadSelected(ActionEvent event) {
        if (!validateRemoteFeaturePrerequisites()) {
            return;
        }

        List<String> localNotes = listLocalNotesForUpload();
        if (localNotes.isEmpty()) {
            showInfo("No local notes found to upload.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Upload Selected Notes");
        dialog.setHeaderText("Select local notes to upload to server");
        dialog.getDialogPane().setPrefSize(700, 480);

        ObservableList<String> notesModel = FXCollections.observableArrayList(localNotes);
        ListView<String> listView = new ListView<>(notesModel);
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setPrefSize(660, 360);

        Label hint = new Label("Tip: use Ctrl/Cmd click or Shift to select multiple notes.");
        VBox content = new VBox(10, hint, listView);
        content.setPadding(new Insets(8, 4, 4, 4));
        dialog.getDialogPane().setContent(content);

        ButtonType uploadButtonType = new ButtonType("Upload Selected", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(uploadButtonType, ButtonType.CANCEL);
        Button uploadBtn = (Button) dialog.getDialogPane().lookupButton(uploadButtonType);
        uploadBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != uploadButtonType) {
            return;
        }

        List<String> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }

        if (uploadButton != null) {
            uploadButton.setDisable(true);
        }
        NetworkSyncService.syncSelectedNotesAsync(remoteNamespace(), currentUser, notesDir, selected)
            .whenComplete((summary, throwable) -> Platform.runLater(() -> {
                if (uploadButton != null) {
                    uploadButton.setDisable(false);
                }
                if (throwable != null) {
                    showError("Upload failed: " + throwable.getMessage());
                    return;
                }
                StringBuilder message = new StringBuilder("Upload complete.\nSelected: " + summary.attempted()
                    + "\nSucceeded: " + summary.success()
                    + "\nFailed: " + summary.failed());
                if (summary.failed() > 0 && summary.errorSamples() != null && !summary.errorSamples().isEmpty()) {
                    message.append("\n\nSample errors:");
                    for (String error : summary.errorSamples()) {
                        message.append("\n- ").append(error);
                    }
                }
                if (summary.success() > 0) {
                    registerSelectedNotesAsRemote(selected, currentUser);
                }
                showInfo(message.toString());
            }));
    }

    @FXML
    private void handleResetStorage(ActionEvent event) {
        if (currentUser == null || currentUser.isBlank()) {
            showError("No active user session. Please log out and log in again.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset Storage");
        String namespace = remoteNamespace();
        confirm.setHeaderText("Reset local storage + your remote files?");
        confirm.setContentText("This will delete local notes for \"" + currentUser + "\" and ONLY remote files authored by \"" + currentUser + "\" in namespace \"" + namespace + "\".");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }

        try {
            clearDirectoryContents(notesDir.toPath());
            Files.createDirectories(trashDir.toPath());
            RemoteNoteRegistry.clear(notesDir.toPath());
        } catch (IOException ex) {
            showError("Failed to reset storage: " + ex.getMessage());
            return;
        }

        if (NetworkSyncService.isConfigured()) {
            NetworkSyncService.deleteRemoteByAuthorAsync(namespace, currentUser)
                .whenComplete((deletedCount, throwable) -> Platform.runLater(() -> {
                    loadRecentProjects(DashboardView.MY_NOTES);
                    updateStats();
                    if (throwable != null) {
                        showError("Local reset completed, but remote cleanup failed: " + throwable.getMessage());
                        return;
                    }
                    showInfo("Storage reset complete.\nLocal user: " + currentUser
                        + "\nRemote namespace: " + namespace
                        + "\nRemote files deleted (author-matched): " + deletedCount);
                }));
        } else {
            loadRecentProjects(DashboardView.MY_NOTES);
            updateStats();
            showInfo("Local storage reset complete. Network sync is disabled, so remote files were not changed.");
        }
    }

    private void showRemoteNotesDialog(List<NetworkSyncService.RemoteNoteRef> remoteNotes) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Remote Notes");
        dialog.setHeaderText("Browse notes stored on the server");
        dialog.getDialogPane().setPrefSize(700, 480);

        ObservableList<NetworkSyncService.RemoteNoteRef> notesModel = FXCollections.observableArrayList(remoteNotes);
        ListView<NetworkSyncService.RemoteNoteRef> listView = new ListView<>(notesModel);
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setPrefSize(660, 360);
        listView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(NetworkSyncService.RemoteNoteRef item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String author = (item.author() == null || item.author().isBlank()) ? "unknown" : item.author();
                setText(item.noteName() + "   (author: " + author + ")");
            }
        });

        Label hint = new Label("Select one or more remote notes to download locally. Author is shown beside each file.");
        VBox content = new VBox(10, hint, listView);
        content.setPadding(new Insets(8, 4, 4, 4));
        dialog.getDialogPane().setContent(content);

        ButtonType downloadButtonType = new ButtonType("Download Selected", ButtonBar.ButtonData.OK_DONE);
        ButtonType refreshButtonType = new ButtonType("Refresh");
        dialog.getDialogPane().getButtonTypes().addAll(downloadButtonType, refreshButtonType, ButtonType.CLOSE);

        Button downloadButton = (Button) dialog.getDialogPane().lookupButton(downloadButtonType);
        Button refreshButton = (Button) dialog.getDialogPane().lookupButton(refreshButtonType);
        BooleanProperty downloadInProgress = new SimpleBooleanProperty(false);
        downloadButton.disableProperty().bind(
            Bindings.or(listView.getSelectionModel().selectedItemProperty().isNull(), downloadInProgress)
        );

        refreshButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            refreshButton.setDisable(true);
            NetworkSyncService.listRemoteNotesDetailedAsync(remoteNamespace())
                .whenComplete((notes, throwable) -> Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    if (throwable != null) {
                        showError("Refresh failed: " + throwable.getMessage());
                        return;
                    }
                    notesModel.setAll(notes == null ? List.of() : notes);
                }));
        });

        downloadButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            List<NetworkSyncService.RemoteNoteRef> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                return;
            }
            downloadInProgress.set(true);
            downloadRemoteNotes(selected)
                .whenComplete((summary, throwable) -> Platform.runLater(() -> {
                    downloadInProgress.set(false);
                    if (throwable != null) {
                        showError("Download failed: " + throwable.getMessage());
                        return;
                    }
                    loadRecentProjects(DashboardView.MY_NOTES);
                    updateStats();
                    StringBuilder message = new StringBuilder("Download complete.\nSelected: " + summary.attempted()
                        + "\nSucceeded: " + summary.success()
                        + "\nFailed: " + summary.failed());
                    if (summary.success() > 0 && !summary.savedFiles().isEmpty()) {
                        message.append("\n\nSaved files:");
                        for (String file : summary.savedFiles()) {
                            message.append("\n- ").append(file);
                        }
                    }
                    if (summary.failed() > 0 && !summary.errors().isEmpty()) {
                        message.append("\n\nSample errors:");
                        for (String error : summary.errors()) {
                            message.append("\n- ").append(error);
                        }
                    }
                    showInfo(message.toString());
                }));
        });

        dialog.showAndWait();
    }

    private void showMyUploadsDialog(List<NetworkSyncService.RemoteNoteRef> remoteNotes) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("My Uploaded Notes");
        dialog.setHeaderText("Notes you uploaded to the server");
        dialog.getDialogPane().setPrefSize(700, 480);

        ObservableList<NetworkSyncService.RemoteNoteRef> notesModel = FXCollections.observableArrayList(remoteNotes);
        ListView<NetworkSyncService.RemoteNoteRef> listView = new ListView<>(notesModel);
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setPrefSize(660, 360);
        listView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(NetworkSyncService.RemoteNoteRef item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.noteName());
            }
        });

        Label hint = new Label("Select one or more uploaded notes to delete them from the server.");
        VBox content = new VBox(10, hint, listView);
        content.setPadding(new Insets(8, 4, 4, 4));
        dialog.getDialogPane().setContent(content);

        ButtonType deleteButtonType = new ButtonType("Delete Selected", ButtonBar.ButtonData.OK_DONE);
        ButtonType refreshButtonType = new ButtonType("Refresh");
        dialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, refreshButtonType, ButtonType.CLOSE);

        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        Button refreshButton = (Button) dialog.getDialogPane().lookupButton(refreshButtonType);
        BooleanProperty deleteInProgress = new SimpleBooleanProperty(false);
        deleteButton.disableProperty().bind(
            Bindings.or(listView.getSelectionModel().selectedItemProperty().isNull(), deleteInProgress)
        );

        refreshButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            refreshButton.setDisable(true);
            NetworkSyncService.listRemoteNotesDetailedAsync(remoteNamespace())
                .whenComplete((notes, throwable) -> Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    if (throwable != null) {
                        showError("Refresh failed: " + throwable.getMessage());
                        return;
                    }
                    notesModel.setAll((notes == null ? List.<NetworkSyncService.RemoteNoteRef>of() : notes)
                        .stream()
                        .filter(note -> currentUser != null && currentUser.equals(note.author()))
                        .toList());
                }));
        });

        deleteButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            List<NetworkSyncService.RemoteNoteRef> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Uploaded Notes");
            confirm.setHeaderText("Delete " + selected.size() + " selected server note" + (selected.size() == 1 ? "" : "s") + "?");
            confirm.setContentText("This removes them from the server.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }

            deleteInProgress.set(true);
            deleteRemoteNotes(selected)
                .whenComplete((summary, throwable) -> Platform.runLater(() -> {
                    deleteInProgress.set(false);
                    if (throwable != null) {
                        showError("Delete failed: " + throwable.getMessage());
                        return;
                    }
                    if (!summary.deletedRefs().isEmpty()) {
                        notesModel.removeAll(summary.deletedRefs());
                    }
                    StringBuilder message = new StringBuilder("Remote delete complete.\nSelected: " + summary.attempted()
                        + "\nSucceeded: " + summary.success()
                        + "\nFailed: " + summary.failed());
                    if (summary.failed() > 0 && !summary.errors().isEmpty()) {
                        message.append("\n\nSample errors:");
                        for (String error : summary.errors()) {
                            message.append("\n- ").append(error);
                        }
                    }
                    showInfo(message.toString());
                }));
        });

        dialog.showAndWait();
    }

    private File resolveLocalTarget(String noteName, String author) {
        String safeName = sanitizeName(noteName);
        String safeAuthor = sanitizeName(author == null || author.isBlank() ? "unknown" : author);
        String ext = extensionOf(safeName);
        String base = removeExtension(safeName);
        String suffixExt = ext.isEmpty() ? ".txt" : ext;
        return nextDuplicateFile(notesDir, base + "-by-" + safeAuthor + suffixExt);
    }

    private List<String> listLocalNotesForUpload() {
        List<String> notes = new ArrayList<>();
        File[] files = notesDir.listFiles((dir, name) -> !name.startsWith(".") && new File(dir, name).isFile());
        if (files == null) {
            return notes;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            notes.add(toDisplayNoteName(file.getName()));
        }
        return notes;
    }

    private CompletableFuture<DownloadSummary> downloadRemoteNotes(List<NetworkSyncService.RemoteNoteRef> noteRefs) {
        if (noteRefs == null || noteRefs.isEmpty()) {
            return CompletableFuture.completedFuture(new DownloadSummary(0, 0, 0, List.of(), List.of()));
        }
        return CompletableFuture.supplyAsync(() -> {
            int attempted = noteRefs.size();
            int success = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();
            List<String> savedFiles = new ArrayList<>();

            for (NetworkSyncService.RemoteNoteRef ref : noteRefs) {
                String noteName = ref.noteName();
                String author = ref.author();
                try {
                    NetworkSyncService.RemoteNote note =
                        NetworkSyncService.downloadRemoteNoteAsync(remoteNamespace(), noteName, author).join();
                    if (note == null) {
                        failed++;
                        if (errors.size() < 3) {
                            errors.add(noteName + ": not found");
                        }
                        continue;
                    }
                    File target = resolveLocalTarget(note.noteName(), author);
                    Files.writeString(target.toPath(), note.content());
                    RemoteNoteRegistry.put(notesDir.toPath(), target.getName(), note.noteName(), author);
                    success++;
                    if (savedFiles.size() < 5) {
                        savedFiles.add(toDisplayNoteName(target.getName()));
                    }
                } catch (Exception e) {
                    failed++;
                    if (errors.size() < 3) {
                        String message = (e.getMessage() == null || e.getMessage().isBlank())
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                        errors.add(noteName + ": " + message);
                    }
                }
            }
            return new DownloadSummary(attempted, success, failed, errors, savedFiles);
        });
    }

    private record DownloadSummary(int attempted, int success, int failed, List<String> errors, List<String> savedFiles) {
    }

    private CompletableFuture<DeleteSummary> deleteRemoteNotes(List<NetworkSyncService.RemoteNoteRef> noteRefs) {
        if (noteRefs == null || noteRefs.isEmpty()) {
            return CompletableFuture.completedFuture(new DeleteSummary(0, 0, 0, List.of(), List.of()));
        }
        return CompletableFuture.supplyAsync(() -> {
            int attempted = noteRefs.size();
            int success = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();
            List<NetworkSyncService.RemoteNoteRef> deletedRefs = new ArrayList<>();

            for (NetworkSyncService.RemoteNoteRef ref : noteRefs) {
                try {
                    NetworkSyncService.DeleteResult result =
                        NetworkSyncService.deleteNoteDetailedAsync(remoteNamespace(), ref.noteName()).join();
                    if (result.success()) {
                        success++;
                        deletedRefs.add(ref);
                        RemoteNoteRegistry.removeByRemote(notesDir.toPath(), ref.noteName(), ref.author());
                    } else {
                        failed++;
                        if (errors.size() < 3) {
                            errors.add(ref.noteName() + ": " + result.message());
                        }
                    }
                } catch (Exception e) {
                    failed++;
                    if (errors.size() < 3) {
                        String message = (e.getMessage() == null || e.getMessage().isBlank())
                            ? e.getClass().getSimpleName()
                            : e.getMessage();
                        errors.add(ref.noteName() + ": " + message);
                    }
                }
            }

            return new DeleteSummary(attempted, success, failed, errors, deletedRefs);
        });
    }

    private record DeleteSummary(int attempted, int success, int failed, List<String> errors,
                                 List<NetworkSyncService.RemoteNoteRef> deletedRefs) {
    }

    private String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Untitled";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String remoteNamespace() {
        return sanitizeName(SettingsManager.getNetworkNamespace());
    }

    private boolean validateRemoteFeaturePrerequisites() {
        if (currentUser == null || currentUser.isBlank()) {
            showError("No active user session. Please log out and log in again.");
            return false;
        }
        if (!NetworkSyncService.isConfigured()) {
            showError("Network sync is disabled. Open Settings and enable Network Sync first.");
            return false;
        }
        return true;
    }

    private void configureStoragePaths() {
        String userSegment = (currentUser == null || currentUser.isBlank())
            ? "default"
            : sanitizeName(currentUser);
        notesDir = new File("notes/" + userSegment);
        if (!notesDir.exists()) {
            notesDir.mkdirs();
        }
        migrateLegacyRootNotesIfNeeded(notesDir);
        trashDir = new File(notesDir, "trash");
        if (!trashDir.exists()) {
            trashDir.mkdirs();
        }
    }

    private void migrateLegacyRootNotesIfNeeded(File userNotesDir) {
        File rootNotesDir = new File("notes");
        if (!rootNotesDir.exists() || !rootNotesDir.isDirectory()) {
            return;
        }
        File[] legacyFiles = rootNotesDir.listFiles((dir, name) -> {
            File file = new File(dir, name);
            return file.isFile() && !name.startsWith(".");
        });
        if (legacyFiles == null || legacyFiles.length == 0) {
            return;
        }
        for (File legacy : legacyFiles) {
            File target = new File(userNotesDir, legacy.getName());
            if (target.exists()) {
                continue;
            }
            try {
                Files.move(legacy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    private void clearDirectoryContents(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path child : stream.toList()) {
                deleteRecursively(child);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
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

        Label remoteAutoSaveLabel = new Label("Remote Autosave (sec):");
        Spinner<Integer> remoteAutoSaveSpinner = new Spinner<>(5, 3600, SettingsManager.getRemoteAutoSaveSeconds());
        remoteAutoSaveSpinner.setEditable(true);
        grid.add(remoteAutoSaveLabel, 0, 5);
        grid.add(remoteAutoSaveSpinner, 1, 5);

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
            SettingsManager.setRemoteAutoSaveSeconds(remoteAutoSaveSpinner.getValue());

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

            MenuItem renameItem = new MenuItem("Rename...");
            renameItem.setOnAction(e -> {
                String note = cell.getItem();
                if (note != null && currentView != DashboardView.TRASH) {
                    renameNote(note);
                }
            });

            MenuItem restoreItem = new MenuItem("Restore from Trash");
            restoreItem.setOnAction(e -> {
                String note = cell.getItem();
                if (note != null && currentView == DashboardView.TRASH) {
                    restoreNoteFromTrash(note);
                }
            });

            ContextMenu contextMenu = new ContextMenu(toggleStarItem, renameItem, restoreItem);
            cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    cell.setContextMenu(null);
                } else if (currentView == DashboardView.TRASH) {
                    restoreItem.setVisible(true);
                    toggleStarItem.setVisible(false);
                    renameItem.setVisible(false);
                    cell.setContextMenu(contextMenu);
                } else {
                    restoreItem.setVisible(false);
                    toggleStarItem.setVisible(true);
                    renameItem.setVisible(true);
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

    private void renameNote(String noteName) {
        File source = resolveNoteFile(notesDir, noteName);
        if (source == null || !source.exists()) {
            showError("File not found");
            return;
        }

        String sourceName = source.getName();
        String currentBaseName = removeExtension(sourceName);
        String currentExt = extensionOf(sourceName);
        if (currentExt.isEmpty()) {
            currentExt = ".txt";
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Rename File");
        dialog.setHeaderText("Choose new file name and extension");

        ButtonType renameButtonType = new ButtonType("Rename", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(renameButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(currentBaseName);
        ComboBox<String> extensionCombo = new ComboBox<>();
        extensionCombo.setItems(FXCollections.observableArrayList(
                ".txt", ".c", ".cpp", ".java", ".py", ".html"
        ));
        extensionCombo.setEditable(true);
        extensionCombo.setValue(currentExt);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Extension:"), 0, 1);
        grid.add(extensionCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != renameButtonType) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String ext = extensionCombo.getValue() == null ? ".txt" : extensionCombo.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        if (ext.isEmpty()) {
            ext = ".txt";
        } else if (!ext.startsWith(".")) {
            ext = "." + ext;
        }

        String targetName = sanitizeName(name) + ext;
        File requestedTarget = new File(notesDir, targetName);
        File target = requestedTarget.equals(source)
                ? requestedTarget
                : nextDuplicateFile(notesDir, targetName);

        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            RemoteNoteRegistry.rename(notesDir.toPath(), source.getName(), target.getName());

            String oldDisplay = toDisplayNoteName(source.getName());
            String newDisplay = toDisplayNoteName(target.getName());
            if (starredNotes.remove(oldDisplay)) {
                starredNotes.add(newDisplay);
                SettingsManager.setStarredNotes(starredNotes);
            }

            loadRecentProjects(currentView);
            updateStats();
            showInfo("Renamed: " + newDisplay);
        } catch (IOException e) {
            showError("Failed to rename file: " + e.getMessage());
        }
    }

    private boolean moveNoteToTrash(String noteName) {
        File source = resolveNoteFile(notesDir, noteName);
        if (source == null || !source.exists()) {
            return false;
        }
        File target = new File(trashDir, source.getName());
        if (target.exists()) {
            target = nextDuplicateFile(trashDir, source.getName());
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            RemoteNoteRegistry.remove(notesDir.toPath(), source.getName());
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
            target = nextDuplicateFile(notesDir, source.getName());
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            loadRecentProjects(DashboardView.TRASH);
            updateStats();
            showInfo("Restored: " + toDisplayNoteName(target.getName()));
        } catch (IOException e) {
            showError("Failed to restore note");
        }
    }

    private File resolveNoteFile(File dir, String noteName) {
        if (dir == null || !dir.exists()) {
            return null;
        }
        File exact = new File(dir, noteName);
        if (exact.isFile()) {
            return exact;
        }
        File txt = new File(dir, noteName + ".txt");
        if (txt.isFile()) {
            return txt;
        }
        File[] candidates = dir.listFiles((d, name) -> toDisplayNoteName(name).equals(noteName));
        if (candidates != null && candidates.length > 0 && candidates[0].isFile()) {
            return candidates[0];
        }
        return exact;
    }

    private String toDisplayNoteName(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".txt")
                ? removeExtension(fileName)
                : fileName;
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    private String extensionOf(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }

    private void registerSelectedNotesAsRemote(List<String> selected, String author) {
        if (selected == null || selected.isEmpty()) {
            return;
        }
        for (String noteName : selected) {
            File source = resolveNoteFile(notesDir, noteName);
            if (source == null || !source.exists()) {
                continue;
            }
            RemoteNoteRegistry.put(notesDir.toPath(), source.getName(), toRemoteNoteName(source.getName()), author);
        }
    }

    private void registerAllLocalNotesAsRemote() {
        File[] files = notesDir.listFiles((dir, name) -> !name.startsWith(".") && new File(dir, name).isFile());
        if (files == null) {
            return;
        }
        for (File file : files) {
            RemoteNoteRegistry.put(notesDir.toPath(), file.getName(), toRemoteNoteName(file.getName()), currentUser);
        }
    }

    private String toRemoteNoteName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.toLowerCase().endsWith(".txt") ? removeExtension(fileName) : fileName;
    }

    private File nextDuplicateFile(File directory, String fileName) {
        File candidate = new File(directory, fileName);
        if (!candidate.exists()) {
            return candidate;
        }

        String baseName = removeExtension(fileName);
        String extension = extensionOf(fileName);
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(directory, baseName + " (" + suffix + ")" + extension);
            suffix++;
        }
        return candidate;
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
