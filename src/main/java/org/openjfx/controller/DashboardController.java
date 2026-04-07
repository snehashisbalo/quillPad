package org.openjfx.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.openjfx.AppConstants;
import org.openjfx.QuillPad;
import org.openjfx.RemoteNoteRegistry;
import org.openjfx.SettingsManager;
import org.openjfx.ThemeManager;
import org.openjfx.model.Note;
import org.openjfx.model.Tag;
import org.openjfx.network.NetworkSyncService;
import org.openjfx.service.NoteTagService;
import org.openjfx.service.NoteService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DashboardController implements Initializable {

    @FXML private Button homeButton;
    @FXML private Button logoutButton;
    @FXML private Button newProjectButton;
    @FXML private ListView<Note> recentProjectsList;
    @FXML private TextField searchField;
    @FXML private Label welcomeLabel;
    @FXML private Label statsLabel;
    @FXML private Button deleteButton;
    @FXML private Button openFileButton;
    @FXML private Button refreshButton;
    @FXML private Button uploadButton;
    @FXML private Button remoteButton;
    @FXML private Button myUploadsButton;
    @FXML private Button themeToggleButton;
    @FXML private Button settingsButton;
    @FXML private Button resetStorageButton;
    @FXML private ToggleButton manageTagsButton;
    @FXML private HBox homeNavItem;
    @FXML private HBox starredNavItem;
    @FXML private HBox trashNavItem;

    private final NoteService noteService = new NoteService();
    private final NoteTagService noteTagService = new NoteTagService();
    private QuillPad mainApp;
    private Path notesDir;
    private Path trashDir;
    private String currentUser;
    private final ObservableList<Note> notes = FXCollections.observableArrayList();
    private FilteredList<Note> filteredNotes;
    private DashboardView currentView = DashboardView.MY_NOTES;
    private Set<String> starredNotes;
    private final Set<String> activeTagFilters = new LinkedHashSet<>();
    private String activeTextQuery = "";

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
        loadRecentProjects(currentView);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        starredNotes = new LinkedHashSet<>(SettingsManager.getStarredNotes());
        filteredNotes = new FilteredList<>(notes, note -> true);
        recentProjectsList.setItems(filteredNotes);
        configureStoragePaths();
        setupSearchBinding();
        setupDeleteBinding();
        setupNoteListContextMenu();
        loadRecentProjects(currentView);
        updateThemeButton();
        updateTagToggleButton();
    }

    private void setupSearchBinding() {
        recentProjectsList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Note item, boolean empty) {
                super.updateItem(item, empty);
                getProperties().put("list-cell", this);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String prefix = item.starred() ? "★ " : "";
                    setText(prefix + formatNoteLabel(item));
                }
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> applyCombinedSearch(newVal));
        }
        if (recentProjectsList != null) {
            recentProjectsList.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getTarget() instanceof javafx.scene.Node node) {
                    Object row = node.getProperties().get("list-cell");
                    if (!(row instanceof ListCell<?>)) {
                        recentProjectsList.getSelectionModel().clearSelection();
                    }
                }
            });
        }
    }

    private void setupDeleteBinding() {
        if (deleteButton != null) {
            deleteButton.disableProperty().bind(recentProjectsList.getSelectionModel().selectedItemProperty().isNull());
        }
        if (recentProjectsList != null) {
            recentProjectsList.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE && recentProjectsList.getSelectionModel().getSelectedItem() != null) {
                    handleDelete(new ActionEvent(recentProjectsList, recentProjectsList));
                    event.consume();
                }
            });
        }
    }

    private void loadRecentProjects(DashboardView view) {
        currentView = view;
        loadNotesAsync(noteFilterFor(view));
    }

    private void loadNotesAsync(NoteService.NoteFilter filter) {
        if (notesDir == null) {
            return;
        }

        Task<List<Note>> task = new Task<>() {
            @Override
            protected List<Note> call() {
                return noteService.listNotes(notesDir, filter, starredNotes, noteTagService, Set.of());
            }
        };

        task.setOnSucceeded(event -> {
            notes.setAll(task.getValue());
            filterNotes(activeTextQuery);
            updateStats();
            updateViewUI();
        });
        task.setOnFailed(event -> showError("Failed to load notes: " + safeMessage(task.getException())));
        startTask(task, "load-notes");
    }

    private void filterNotes(String searchText) {
        String query = searchText == null ? "" : searchText.trim().toLowerCase();
        activeTextQuery = query;
        filteredNotes.setPredicate(note -> matchesTextQuery(note, query) && matchesActiveTags(note));
    }

    private void applyCombinedSearch(String rawSearch) {
        SearchQuery parsedQuery = parseSearchQuery(rawSearch);
        activeTagFilters.clear();
        activeTagFilters.addAll(parsedQuery.tags());
        filterNotes(parsedQuery.textQuery());
    }

    private void updateStats() {
        if (statsLabel == null) {
            return;
        }
        int noteCount = notes.size();
        String prefix = switch (currentView) {
            case STARRED -> "starred ";
            case TRASH -> "trashed ";
            default -> "";
        };
        statsLabel.setText(noteCount + " " + prefix + "note" + (noteCount != 1 ? "s" : "") + " available");
    }

    @FXML
    private void handleHome(ActionEvent event) {
        loadRecentProjects(DashboardView.MY_NOTES);
        if (searchField != null) {
            searchField.clear();
        }
    }

    @FXML
    private void handleMyNotes(MouseEvent event) {
        loadRecentProjects(DashboardView.MY_NOTES);
    }

    @FXML
    private void handleStarred(MouseEvent event) {
        loadRecentProjects(DashboardView.STARRED);
    }

    @FXML
    private void handleTrash(MouseEvent event) {
        loadRecentProjects(DashboardView.TRASH);
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
        if (notesDir != null && Files.exists(notesDir)) {
            fileChooser.setInitialDirectory(notesDir.toFile());
        }

        File selectedFile = fileChooser.showOpenDialog(mainApp.getPrimaryStage());
        if (selectedFile != null) {
            mainApp.showEditor(selectedFile);
        }
    }

    @FXML
    private void handleProjectSelected(MouseEvent event) {
        if (event.getClickCount() != 2) {
            return;
        }
        Note selectedNote = recentProjectsList.getSelectionModel().getSelectedItem();
        if (selectedNote == null) {
            return;
        }
        if (currentView == DashboardView.TRASH) {
            restoreNoteFromTrash(selectedNote.displayName());
        } else {
            mainApp.showEditor(selectedNote.displayName());
        }
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        Note selectedNote = recentProjectsList.getSelectionModel().getSelectedItem();
        if (selectedNote == null) {
            return;
        }

        String displayName = selectedNote.displayName();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (currentView == DashboardView.TRASH) {
            alert.setTitle("Delete Permanently");
            alert.setHeaderText("Permanently delete \"" + displayName + "\"?");
            alert.setContentText("This action cannot be undone.");
        } else {
            alert.setTitle("Move to Trash");
            alert.setHeaderText("Move \"" + displayName + "\" to Trash?");
            alert.setContentText("You can restore it later from Trash.");
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            if (currentView == DashboardView.TRASH) {
                Path source = noteService.resolve(trashDir, displayName)
                        .orElseThrow(() -> new IOException("Note not found in trash"));
                noteService.deletePermanently(notesDir, displayName);
                noteTagService.deleteNote(notesDir, source.getFileName().toString());
                loadRecentProjects(DashboardView.TRASH);
                showInfo("Note permanently deleted");
                NetworkSyncService.deleteNoteAsync(remoteNamespace(), displayName);
            } else {
                Path source = noteService.resolve(notesDir, displayName)
                        .orElseThrow(() -> new IOException("Note not found"));
                Path target = noteService.moveToTrash(notesDir, displayName);
                if (!source.getFileName().toString().equals(target.getFileName().toString())) {
                    noteTagService.renameNote(notesDir, source.getFileName().toString(), target.getFileName().toString());
                }
                Note sourceNote = selectedNoteFromName(displayName);
                if (sourceNote != null) {
                    RemoteNoteRegistry.remove(notesDir, sourceNote.path().getFileName().toString());
                }
                loadRecentProjects(currentView);
                showInfo("Note moved to trash");
            }
        } catch (IOException e) {
            showError(currentView == DashboardView.TRASH ? "Failed to delete note" : "Failed to move note to trash");
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadRecentProjects(currentView);
        showInfo("Notes refreshed");
    }

    @FXML
    private void handleManageTags(ActionEvent event) {
        updateTagToggleButton();
        applyCombinedSearch(searchField == null ? "" : searchField.getText());
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
                .whenComplete((remoteNotes, throwable) -> Platform.runLater(() -> {
                    if (remoteButton != null) {
                        remoteButton.setDisable(false);
                    }
                    if (throwable != null) {
                        showError("Failed to fetch remote notes: " + throwable.getMessage());
                        return;
                    }
                    showRemoteNotesDialog(remoteNotes == null ? List.of() : remoteNotes);
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
                .whenComplete((remoteNotes, throwable) -> Platform.runLater(() -> {
                    if (myUploadsButton != null) {
                        myUploadsButton.setDisable(false);
                    }
                    if (throwable != null) {
                        showError("Failed to fetch your uploaded notes: " + throwable.getMessage());
                        return;
                    }
                    List<NetworkSyncService.RemoteNoteRef> uploadedNotes = (remoteNotes == null ? List.<NetworkSyncService.RemoteNoteRef>of() : remoteNotes)
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

        Note selectedNote = recentProjectsList.getSelectionModel().getSelectedItem();
        if (selectedNote == null) {
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
            uploadNotes(selected);
            return;
        }
        List<String> selected = List.of(selectedNote.displayName());
        uploadNotes(selected);
    }

    private void uploadNotes(List<String> selected) {
        if (uploadButton != null) {
            uploadButton.setDisable(true);
        }
        NetworkSyncService.syncSelectedNotesAsync(remoteNamespace(), currentUser, notesDir.toFile(), selected)
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
            clearDirectoryContents(notesDir);
            Files.createDirectories(trashDir);
            RemoteNoteRegistry.clear(notesDir);
        } catch (IOException ex) {
            showError("Failed to reset storage: " + ex.getMessage());
            return;
        }

        if (NetworkSyncService.isConfigured()) {
            NetworkSyncService.deleteRemoteByAuthorAsync(namespace, currentUser)
                    .whenComplete((deletedCount, throwable) -> Platform.runLater(() -> {
                        loadRecentProjects(DashboardView.MY_NOTES);
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
        downloadButton.disableProperty().bind(Bindings.or(listView.getSelectionModel().selectedItemProperty().isNull(), downloadInProgress));

        refreshButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            refreshButton.setDisable(true);
            NetworkSyncService.listRemoteNotesDetailedAsync(remoteNamespace())
                    .whenComplete((remoteList, throwable) -> Platform.runLater(() -> {
                        refreshButton.setDisable(false);
                        if (throwable != null) {
                            showError("Refresh failed: " + throwable.getMessage());
                            return;
                        }
                        notesModel.setAll(remoteList == null ? List.of() : remoteList);
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
        deleteButton.disableProperty().bind(Bindings.or(listView.getSelectionModel().selectedItemProperty().isNull(), deleteInProgress));

        refreshButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            refreshButton.setDisable(true);
            NetworkSyncService.listRemoteNotesDetailedAsync(remoteNamespace())
                    .whenComplete((remoteList, throwable) -> Platform.runLater(() -> {
                        refreshButton.setDisable(false);
                        if (throwable != null) {
                            showError("Refresh failed: " + throwable.getMessage());
                            return;
                        }
                        notesModel.setAll((remoteList == null ? List.<NetworkSyncService.RemoteNoteRef>of() : remoteList)
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
        String suffixExt = ext.isEmpty() ? AppConstants.EXT_TXT : ext;
        return nextDuplicateFile(notesDir.toFile(), base + "-by-" + safeAuthor + suffixExt);
    }

    private List<String> listLocalNotesForUpload() {
        return noteService.listNotes(notesDir, NoteService.NoteFilter.myNotes(), starredNotes, noteTagService, Set.of())
                .stream()
                .filter(note -> matchesTextQuery(note, activeTextQuery) && matchesActiveTags(note))
                .map(Note::displayName)
                .sorted()
                .toList();
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
                    NetworkSyncService.RemoteNote note = NetworkSyncService.downloadRemoteNoteAsync(remoteNamespace(), noteName, author).join();
                    if (note == null) {
                        failed++;
                        if (errors.size() < 3) {
                            errors.add(noteName + ": not found");
                        }
                        continue;
                    }
                    File target = resolveLocalTarget(note.noteName(), author);
                    Files.writeString(target.toPath(), note.content());
                    RemoteNoteRegistry.put(notesDir, target.getName(), note.noteName(), author);
                    success++;
                    if (savedFiles.size() < 5) {
                        savedFiles.add(noteService.displayName(target.toPath()));
                    }
                } catch (Exception e) {
                    failed++;
                    if (errors.size() < 3) {
                        errors.add(noteName + ": " + safeMessage(e));
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
                    NetworkSyncService.DeleteResult result = NetworkSyncService.deleteNoteDetailedAsync(remoteNamespace(), ref.noteName()).join();
                    if (result.success()) {
                        success++;
                        deletedRefs.add(ref);
                        RemoteNoteRegistry.removeByRemote(notesDir, ref.noteName(), ref.author());
                    } else {
                        failed++;
                        if (errors.size() < 3) {
                            errors.add(ref.noteName() + ": " + result.message());
                        }
                    }
                } catch (Exception e) {
                    failed++;
                    if (errors.size() < 3) {
                        errors.add(ref.noteName() + ": " + safeMessage(e));
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
        String userSegment = (currentUser == null || currentUser.isBlank()) ? "default" : sanitizeName(currentUser);
        notesDir = Path.of(AppConstants.NOTES_DIR, userSegment);
        try {
            Files.createDirectories(notesDir);
            migrateLegacyRootNotesIfNeeded(notesDir);
            trashDir = notesDir.resolve(AppConstants.TRASH_DIR);
            Files.createDirectories(trashDir);
        } catch (IOException e) {
            showError("Failed to initialize note storage: " + e.getMessage());
        }
    }

    private void migrateLegacyRootNotesIfNeeded(Path userNotesDir) {
        Path rootNotesDir = Path.of(AppConstants.NOTES_DIR);
        if (!Files.isDirectory(rootNotesDir)) {
            return;
        }
        try (var stream = Files.list(rootNotesDir)) {
            for (Path legacy : stream.filter(Files::isRegularFile).filter(path -> !path.getFileName().toString().startsWith(".")).toList()) {
                Path target = userNotesDir.resolve(legacy.getFileName());
                if (Files.exists(target)) {
                    continue;
                }
                try {
                    Files.move(legacy, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
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
        ButtonType creditsButtonType = new ButtonType("Credits", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, creditsButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        Label fontLabel = new Label("Font Family:");
        ComboBox<String> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll("System", "Arial", "Consolas", "Courier New", "Monaco", "Verdana", "Georgia", "Times New Roman");
        fontCombo.setValue(SettingsManager.getFontFamily());
        grid.add(fontLabel, 0, 0);
        grid.add(fontCombo, 1, 0);

        Label sizeLabel = new Label("Font Size:");
        Spinner<Integer> sizeSpinner = new Spinner<>(8, 32, SettingsManager.getFontSize());
        sizeSpinner.setEditable(true);
        grid.add(sizeLabel, 0, 1);
        grid.add(sizeSpinner, 1, 1);

        Label themeLabel = new Label("Theme:");
        ComboBox<String> themeCombo = new ComboBox<>();
        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            themeCombo.getItems().add(ThemeManager.getThemeIcon(theme) + " " + theme.getDisplayName());
        }
        themeCombo.setValue(ThemeManager.getThemeIcon(ThemeManager.getCurrentTheme()) + " " + ThemeManager.getCurrentTheme().getDisplayName());
        grid.add(themeLabel, 0, 2);
        grid.add(themeCombo, 1, 2);

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

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        if (saveButton != null) {
            saveButton.setStyle("-fx-font-weight: bold;");
        }
        Button creditsButton = (Button) dialog.getDialogPane().lookupButton(creditsButtonType);
        if (creditsButton != null) {
            creditsButton.setText("View Credits");
            creditsButton.setStyle(
                    "-fx-background-color: linear-gradient(to right, #0f4c75, #3282b8);"
                            + "-fx-text-fill: white;"
                            + "-fx-font-weight: bold;"
                            + "-fx-background-radius: 999;"
                            + "-fx-padding: 8 16 8 16;"
            );
        }

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == creditsButtonType) {
            if (mainApp != null) {
                mainApp.showCredits();
            }
            return;
        }
        if (result.isPresent() && result.get() == saveButtonType) {
            String selectedFont = fontCombo.getValue();
            if (selectedFont != null) {
                SettingsManager.setFontFamily(selectedFont);
            }
            SettingsManager.setFontSize(sizeSpinner.getValue());

            int themeIndex = themeCombo.getSelectionModel().getSelectedIndex();
            if (themeIndex >= 0) {
                ThemeManager.setTheme(ThemeManager.Theme.values()[themeIndex]);
            }

            SettingsManager.setNetworkEnabled(networkEnabled.isSelected());
            SettingsManager.setNetworkBaseUrl(networkUrlField.getText());
            SettingsManager.setRemoteAutoSaveSeconds(remoteAutoSaveSpinner.getValue());

            showInfo("Settings saved successfully!\nChanges will apply to new tabs.");
        }
    }

    private void setupNoteListContextMenu() {
        recentProjectsList.setCellFactory(listView -> {
            ListCell<Note> cell = new ListCell<>() {
                @Override
                protected void updateItem(Note item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String prefix = item.starred() ? "★ " : "";
                        setText(prefix + formatNoteLabel(item));
                    }
                }
            };

            MenuItem toggleStarItem = new MenuItem("Toggle Star");
            toggleStarItem.setOnAction(e -> {
                Note note = cell.getItem();
                if (note != null && currentView != DashboardView.TRASH) {
                    toggleStar(note.displayName());
                }
            });

            MenuItem renameItem = new MenuItem("Rename...");
            renameItem.setOnAction(e -> {
                Note note = cell.getItem();
                if (note != null && currentView != DashboardView.TRASH) {
                    renameNote(note.displayName());
                }
            });

            MenuItem addTagItem = new MenuItem("Add Tag...");
            addTagItem.setOnAction(e -> {
                Note note = cell.getItem();
                if (note != null && currentView != DashboardView.TRASH) {
                    showAddTagDialog(note);
                }
            });

            MenuItem restoreItem = new MenuItem("Restore from Trash");
            restoreItem.setOnAction(e -> {
                Note note = cell.getItem();
                if (note != null && currentView == DashboardView.TRASH) {
                    restoreNoteFromTrash(note.displayName());
                }
            });

            ContextMenu contextMenu = new ContextMenu(toggleStarItem, renameItem, addTagItem, restoreItem);
            cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    cell.setContextMenu(null);
                } else if (currentView == DashboardView.TRASH) {
                    restoreItem.setVisible(true);
                    toggleStarItem.setVisible(false);
                    renameItem.setVisible(false);
                    addTagItem.setVisible(false);
                    cell.setContextMenu(contextMenu);
                } else {
                    restoreItem.setVisible(false);
                    toggleStarItem.setVisible(true);
                    renameItem.setVisible(true);
                    addTagItem.setVisible(true);
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
        } else {
            notes.replaceAll(note -> note.displayName().equals(noteName)
                    ? new Note(note.name(), note.path(), note.lastModified(), starredNotes.contains(noteName), note.trashed(), note.getTags())
                    : note);
        }
    }

    private void renameNote(String noteName) {
        Optional<Path> sourcePath = noteService.resolve(notesDir, noteName);
        if (sourcePath.isEmpty()) {
            showError("File not found");
            return;
        }

        Path source = sourcePath.get();
        String sourceName = source.getFileName().toString();
        String currentBaseName = removeExtension(sourceName);
        String currentExt = extensionOf(sourceName);
        if (currentExt.isEmpty()) {
            currentExt = AppConstants.EXT_TXT;
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
        extensionCombo.setItems(FXCollections.observableArrayList(AppConstants.EXT_TXT, ".c", ".cpp", ".java", ".py", ".html"));
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
        String ext = extensionCombo.getValue() == null ? AppConstants.EXT_TXT : extensionCombo.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        if (ext.isEmpty()) {
            ext = AppConstants.EXT_TXT;
        } else if (!ext.startsWith(".")) {
            ext = "." + ext;
        }

        String targetName = sanitizeName(name) + ext;
        Path requestedTarget = notesDir.resolve(targetName);
        Path target;
        try {
            target = requestedTarget.equals(source) ? requestedTarget : noteService.nextDuplicatePath(notesDir, targetName);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            RemoteNoteRegistry.rename(notesDir, source.getFileName().toString(), target.getFileName().toString());
            noteTagService.renameNote(notesDir, source.getFileName().toString(), target.getFileName().toString());

            String oldDisplay = noteService.displayName(source);
            String newDisplay = noteService.displayName(target);
            if (starredNotes.remove(oldDisplay)) {
                starredNotes.add(newDisplay);
                SettingsManager.setStarredNotes(starredNotes);
            }

            loadRecentProjects(currentView);
            showInfo("Renamed: " + newDisplay);
        } catch (IOException e) {
            showError("Failed to rename file: " + e.getMessage());
        }
    }

    private void restoreNoteFromTrash(String noteName) {
        try {
            Path source = noteService.resolve(trashDir, noteName)
                    .orElseThrow(() -> new IOException("Note not found in trash"));
            Path target = noteService.restoreFromTrash(notesDir, noteName);
            if (!source.getFileName().toString().equals(target.getFileName().toString())) {
                noteTagService.renameNote(notesDir, source.getFileName().toString(), target.getFileName().toString());
            }
            loadRecentProjects(DashboardView.TRASH);
            showInfo("Restored: " + noteService.displayName(target));
        } catch (IOException e) {
            showError("Failed to restore note");
        }
    }

    private void registerSelectedNotesAsRemote(List<String> selected, String author) {
        if (selected == null || selected.isEmpty()) {
            return;
        }
        for (String noteName : selected) {
            Optional<Path> source = noteService.resolve(notesDir, noteName);
            if (source.isEmpty()) {
                continue;
            }
            RemoteNoteRegistry.put(notesDir, source.get().getFileName().toString(), toRemoteNoteName(source.get().getFileName().toString()), author);
        }
    }

    private String toRemoteNoteName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.toLowerCase().endsWith(AppConstants.EXT_TXT) ? removeExtension(fileName) : fileName;
    }

    private List<String> parseTagInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawInput.split("[,\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Tag::normalize)
                .distinct()
                .toList();
    }

    private String formatNoteLabel(Note note) {
        if (note == null) {
            return "";
        }
        if (note.getTags().isEmpty()) {
            return note.displayName();
        }
        String joinedTags = note.getTags().stream()
                .map(tag -> "#" + tag.displayName())
                .limit(3)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return note.displayName() + "    " + joinedTags;
    }

    private boolean matchesTextQuery(Note note, String query) {
        return query == null || query.isBlank() || note.displayName().toLowerCase().contains(query);
    }

    private boolean matchesActiveTags(Note note) {
        if (activeTagFilters.isEmpty()) {
            return true;
        }
        Set<String> noteTags = note.getTags().stream()
                .map(Tag::normalizedName)
                .collect(java.util.stream.Collectors.toSet());
        for (String queryTag : activeTagFilters) {
            boolean matched = noteTags.stream().anyMatch(noteTag -> noteTag.startsWith(queryTag));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private SearchQuery parseSearchQuery(String rawSearch) {
        if (rawSearch == null || rawSearch.isBlank()) {
            return new SearchQuery("", Set.of());
        }

        if (manageTagsButton != null && manageTagsButton.isSelected()) {
            return new SearchQuery("", new LinkedHashSet<>(parseTagInput(rawSearch)));
        }

        List<String> textParts = new ArrayList<>();
        Set<String> tagParts = new LinkedHashSet<>();
        for (String token : rawSearch.trim().split("\\s+")) {
            if (token.startsWith("#") && token.length() > 1) {
                tagParts.addAll(parseTagInput(token.substring(1)));
            } else if (!token.isBlank()) {
                textParts.add(token.toLowerCase());
            }
        }
        return new SearchQuery(String.join(" ", textParts), tagParts);
    }

    private void updateTagToggleButton() {
        if (manageTagsButton == null) {
            return;
        }
        boolean enabled = manageTagsButton.isSelected();
        manageTagsButton.setText(enabled ? "🏷 Tags On" : "🏷 Tags Off");
        manageTagsButton.setStyle(enabled
                ? "-fx-background-color: #1f6f5f; -fx-text-fill: white;"
                : "");
    }

    private void showManageTagsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Manage Tags");
        dialog.setHeaderText("Rename or delete reusable tags");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(480, 420);

        ObservableList<Tag> tagItems = FXCollections.observableArrayList(noteTagService.getAllTags(notesDir));
        ListView<Tag> tagListView = new ListView<>(tagItems);
        tagListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Tag item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "#" + item.displayName());
            }
        });

        TextField renameField = new TextField();
        renameField.setPromptText("New tag name");
        Button renameButton = new Button("Rename");
        Button deleteTagButton = new Button("Delete");
        renameButton.disableProperty().bind(Bindings.or(tagListView.getSelectionModel().selectedItemProperty().isNull(), renameField.textProperty().isEmpty()));
        deleteTagButton.disableProperty().bind(tagListView.getSelectionModel().selectedItemProperty().isNull());

        renameButton.setOnAction(event -> {
            Tag selectedTag = tagListView.getSelectionModel().getSelectedItem();
            if (selectedTag == null) {
                return;
            }
            try {
                String newName = renameField.getText();
                noteTagService.renameTag(notesDir, selectedTag.displayName(), newName);
                if (searchField != null && searchField.getText() != null) {
                    searchField.setText(searchField.getText().replace("#" + selectedTag.displayName(), "#" + Tag.normalize(newName)));
                }
                renameField.clear();
                tagItems.setAll(noteTagService.getAllTags(notesDir));
                applyCombinedSearch(searchField == null ? "" : searchField.getText());
            } catch (Exception e) {
                showError("Failed to rename tag: " + safeMessage(e));
            }
        });

        deleteTagButton.setOnAction(event -> {
            Tag selectedTag = tagListView.getSelectionModel().getSelectedItem();
            if (selectedTag == null) {
                return;
            }
            try {
                noteTagService.deleteTag(notesDir, selectedTag.displayName());
                tagItems.setAll(noteTagService.getAllTags(notesDir));
                applyCombinedSearch(searchField == null ? "" : searchField.getText());
            } catch (IOException e) {
                showError("Failed to delete tag: " + e.getMessage());
            }
        });

        VBox content = new VBox(10,
                new Label("Available tags"),
                tagListView,
                new HBox(8, renameField, renameButton, deleteTagButton));
        content.setPadding(new Insets(10));
        VBox.setVgrow(tagListView, javafx.scene.layout.Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showAddTagDialog(Note note) {
        if (note == null) {
            return;
        }

        String fileName = note.path().getFileName().toString();
        if (noteTagService.getTags(notesDir, fileName).size() >= AppConstants.MAX_TAGS_PER_FILE) {
            showError("A note can have at most " + AppConstants.MAX_TAGS_PER_FILE + " tags.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Tag");
        dialog.setHeaderText("Add a tag to " + note.displayName());

        ButtonType addButtonType = new ButtonType("Add Tag", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField tagField = new TextField();
        tagField.setPromptText("Tag name");
        grid.add(new Label("Tag:"), 0, 0);
        grid.add(tagField, 1, 0);

        dialog.getDialogPane().setContent(grid);
        Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
        addButton.disableProperty().bind(tagField.textProperty().isEmpty());

        Platform.runLater(tagField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != addButtonType) {
            return;
        }

        try {
            if (noteTagService.getTags(notesDir, fileName).size() >= AppConstants.MAX_TAGS_PER_FILE) {
                showError("A note can have at most " + AppConstants.MAX_TAGS_PER_FILE + " tags.");
                return;
            }
            noteTagService.addTag(notesDir, fileName, tagField.getText());
            loadRecentProjects(currentView);
        } catch (Exception e) {
            showError("Failed to add tag: " + safeMessage(e));
        }
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

    private String removeExtension(String filename) {
        return noteService.removeExtension(filename);
    }

    private String extensionOf(String filename) {
        return noteService.extensionOf(filename);
    }

    private void updateViewUI() {
        if (deleteButton != null) {
            deleteButton.setText(currentView == DashboardView.TRASH ? "🗑️ Delete Permanently" : "🗑️ Move to Trash");
        }
    }

    private NoteService.NoteFilter noteFilterFor(DashboardView view) {
        return switch (view) {
            case STARRED -> NoteService.NoteFilter.starredOnly();
            case TRASH -> NoteService.NoteFilter.trash();
            default -> NoteService.NoteFilter.myNotes();
        };
    }

    private Note selectedNoteFromName(String noteName) {
        return notes.stream().filter(note -> note.displayName().equals(noteName)).findFirst().orElse(null);
    }

    private void startTask(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "unknown error";
        }
        return throwable.getMessage();
    }

    private record SearchQuery(String textQuery, Set<String> tags) {
    }
}
