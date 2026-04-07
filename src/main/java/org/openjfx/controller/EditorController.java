package org.openjfx.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import org.openjfx.AppConstants;
import org.openjfx.QuillPad;
import org.openjfx.RemoteNoteRegistry;
import org.openjfx.SettingsManager;
import org.openjfx.ThemeManager;
import org.openjfx.component.RichTextEditor;
import org.openjfx.model.StyledDocument;
import org.openjfx.model.Tag;
import org.openjfx.network.NetworkSyncService;
import org.openjfx.service.NoteTagService;
import org.openjfx.service.NoteService;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.Timer;
import java.util.Base64;

public class EditorController implements Initializable {

    @FXML private TabPane tabPane;
    @FXML private Label lineLabel;
    @FXML private Label columnLabel;
    @FXML private Label wordCountLabel;
    @FXML private Label charCountLabel;
    @FXML private Label fileStatusLabel;

    @FXML private MenuItem newMenuItem;
    @FXML private MenuItem openMenuItem;
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem saveAsMenuItem;
    @FXML private MenuItem renameMenuItem;
    @FXML private MenuItem closeMenuItem;
    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;
    @FXML private MenuItem cutMenuItem;
    @FXML private MenuItem copyMenuItem;
    @FXML private MenuItem pasteMenuItem;
    @FXML private MenuItem selectAllMenuItem;
    @FXML private MenuItem findMenuItem;
    @FXML private MenuItem replaceMenuItem;
    @FXML private MenuItem addTagMenuItem;
    @FXML private MenuItem manageTagMenuItem;
    @FXML private CheckMenuItem wordWrapMenuItem;
    @FXML private MenuItem aboutMenuItem;
    @FXML private MenuItem increaseFontMenuItem;
    @FXML private MenuItem decreaseFontMenuItem;
    @FXML private MenuItem resetFontMenuItem;

    @FXML private ComboBox<String> fontFamilyCombo;
    @FXML private ComboBox<Integer> fontSizeCombo;
    @FXML private ToggleButton boldButton;
    @FXML private ToggleButton italicButton;
    @FXML private ToggleButton underlineButton;
    @FXML private ToggleButton strikethroughButton;
    @FXML private ColorPicker textColorPicker;
    @FXML private Button themeToggleButton;
    @FXML private HBox tagChipPane;

    private QuillPad mainApp;
    private final NoteService noteService = new NoteService();
    private final NoteTagService noteTagService = new NoteTagService();
    private String currentUser;
    private String currentNoteName;
    private Map<Tab, RichTextEditor> tabEditorMap = new HashMap<>();
    private Map<Tab, Boolean> tabModifiedMap = new HashMap<>();
    private Map<Tab, String> tabFilePathMap = new HashMap<>();
    private Map<Tab, String> tabManagedNotePathMap = new HashMap<>();
    private Map<Tab, Boolean> tabPendingRemoteSyncMap = new HashMap<>();
    private Map<Tab, RemoteNoteRegistry.RemoteBinding> tabRemoteBindingMap = new HashMap<>();
    private Timer autoSaveTimer;
    private Timer remoteAutoSaveTimer;
    private int untitledCounter = 1;
    private boolean updatingToolbarState;
    private boolean dashboardNavigationPending;

    private static final String[] FONT_FAMILIES = {
        "System", "Arial", "Times New Roman", "Courier New", "Consolas", 
        "Verdana", "Georgia", "Comic Sans MS", "Trebuchet MS", "Lucida Console"
    };
    private static final Integer[] FONT_SIZES = {8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72};
    private static final List<String> SAVEABLE_EXTENSIONS = List.of(
            ".txt", ".c", ".cpp", ".java", ".py", ".html"
    );

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
        migrateLegacyNullUserNotes();
        if (currentNoteName != null && tabPane != null && tabPane.getTabs().isEmpty()) {
            loadNoteAsync(currentNoteName);
        }
    }

    public void setCurrentNote(String noteName) {
        this.currentNoteName = noteName;
        if (currentUser == null || currentUser.isBlank()) {
            return;
        }
        if (noteName != null) {
            loadNoteAsync(noteName);
        } else {
            createNewTab(null);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFontCombos();
        setupKeyboardShortcuts();
        setupToolbarListeners();
        setupTagControls();

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                RichTextEditor editor = tabEditorMap.get(newTab);
                if (editor != null) {
                    updateStatus(editor, editor.getCaretPosition());
                    refreshFormatToggleButtons(editor);
                }
            }
            refreshTagControls();
        });

        startAutoSave();
        startRemoteAutoSave();

        tabPane.getTabs().addListener((javafx.collections.ListChangeListener.Change<? extends Tab> c) -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    for (Tab removedTab : c.getRemoved()) {
                        tabEditorMap.remove(removedTab);
                        tabModifiedMap.remove(removedTab);
                        tabFilePathMap.remove(removedTab);
                        tabManagedNotePathMap.remove(removedTab);
                        tabPendingRemoteSyncMap.remove(removedTab);
                        tabRemoteBindingMap.remove(removedTab);
                    }
                }
            }
            if (tabPane.getTabs().isEmpty() && mainApp != null) {
                navigateBackToDashboard();
            }
            refreshTagControls();
        });
    }

    private void setupTagControls() {
        refreshTagControls();
    }

    private void setupFontCombos() {
        ObservableList<String> families = FXCollections.observableArrayList(FONT_FAMILIES);
        fontFamilyCombo.setItems(families);
        fontFamilyCombo.setValue(SettingsManager.getFontFamily());
        fontFamilyCombo.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        fontFamilyCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });

        ObservableList<Integer> sizes = FXCollections.observableArrayList(FONT_SIZES);
        fontSizeCombo.setItems(sizes);
        setFontSizeComboValue(SettingsManager.getFontSize());
        fontSizeCombo.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.valueOf(item));
            }
        });
        fontSizeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.valueOf(item));
            }
        });
    }

    private void setupKeyboardShortcuts() {
        if (newMenuItem != null) {
            newMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
            newMenuItem.setOnAction(this::handleNew);
        }
        if (openMenuItem != null) {
            openMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
            openMenuItem.setOnAction(this::handleOpen);
        }
        if (saveMenuItem != null) {
            saveMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
            saveMenuItem.setOnAction(this::handleSave);
        }
        if (saveAsMenuItem != null) {
            saveAsMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
            saveAsMenuItem.setOnAction(this::handleSaveAs);
        }
        if (renameMenuItem != null) {
            renameMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.F2));
            renameMenuItem.setOnAction(this::handleRenameFile);
        }
        if (closeMenuItem != null) {
            closeMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
            closeMenuItem.setOnAction(this::handleCloseTab);
        }
        if (undoMenuItem != null) {
            undoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
            undoMenuItem.setOnAction(this::handleUndo);
        }
        if (redoMenuItem != null) {
            redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
            redoMenuItem.setOnAction(this::handleRedo);
        }
        if (cutMenuItem != null) {
            cutMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN));
            cutMenuItem.setOnAction(this::handleCut);
        }
        if (copyMenuItem != null) {
            copyMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
            copyMenuItem.setOnAction(this::handleCopy);
        }
        if (pasteMenuItem != null) {
            pasteMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
            pasteMenuItem.setOnAction(this::handlePaste);
        }
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN));
            selectAllMenuItem.setOnAction(this::handleSelectAll);
        }
        if (findMenuItem != null) {
            findMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
            findMenuItem.setOnAction(this::handleFind);
        }
        if (replaceMenuItem != null) {
            replaceMenuItem.setAccelerator(new KeyCodeCombination(
                    KeyCode.F,
                    KeyCombination.SHORTCUT_DOWN,
                    KeyCombination.ALT_DOWN
            ));
            replaceMenuItem.setOnAction(this::handleReplace);
        }
        if (addTagMenuItem != null) {
            addTagMenuItem.setOnAction(this::handleAddTag);
        }
        if (manageTagMenuItem != null) {
            manageTagMenuItem.setOnAction(this::handleManageTags);
        }
        if (wordWrapMenuItem != null) {
            wordWrapMenuItem.setSelected(true);
            wordWrapMenuItem.setOnAction(this::handleWordWrap);
        }
        if (aboutMenuItem != null) {
            aboutMenuItem.setOnAction(this::handleAbout);
        }
        if (increaseFontMenuItem != null) {
            increaseFontMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN));
            increaseFontMenuItem.setOnAction(this::handleIncreaseFont);
        }
        if (decreaseFontMenuItem != null) {
            decreaseFontMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN));
            decreaseFontMenuItem.setOnAction(this::handleDecreaseFont);
        }
        if (resetFontMenuItem != null) {
            resetFontMenuItem.setOnAction(this::handleResetFont);
        }

        if (themeToggleButton != null) {
            themeToggleButton.setOnAction(this::handleThemeToggle);
            refreshThemeToggleButton();
        }
    }

    private void setupToolbarListeners() {
        fontFamilyCombo.setOnAction(e -> applyFontFamily());
        fontSizeCombo.setOnAction(e -> applyFontSize());

        boldButton.setOnAction(e -> applyBold());
        italicButton.setOnAction(e -> applyItalic());
        underlineButton.setOnAction(e -> applyUnderline());
        strikethroughButton.setOnAction(e -> applyStrikethrough());
        
        textColorPicker.setOnAction(e -> applyTextColor());
    }

    private Tab createNewTab(String title) {
        String tabTitle;
        if (title == null || title.isEmpty() || title.equals("Untitled")) {
            tabTitle = "Untitled-" + untitledCounter++;
        } else {
            tabTitle = formatTabTitleForFileName(title);
        }

        Tab tab = new Tab(tabTitle);
        RichTextEditor editor = new RichTextEditor();
        editor.setDocumentName(tabTitle);
        editor.setWrapText(wordWrapMenuItem != null && wordWrapMenuItem.isSelected());
        editor.setFont(Font.font(SettingsManager.getFontFamily(), SettingsManager.getFontSize()));
        editor.setLanguageFromFileName(tabTitle);

        tab.setContent(editor);
        tabEditorMap.put(tab, editor);
        tabModifiedMap.put(tab, false);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        setupTabListeners(tab, editor);
        return tab;
    }

    private void setupTabListeners(Tab tab, RichTextEditor editor) {
        editor.getTextArea().caretPositionProperty().addListener((caretObs, oldPos, newPos) -> {
            updateStatus(editor, newPos.intValue());
            refreshFormatToggleButtons(editor);
        });

        editor.getTextArea().selectionProperty().addListener((obs, oldSelection, newSelection) ->
                refreshFormatToggleButtons(editor));

        editor.getTextArea().textProperty().addListener((obs, oldText, newText) -> {
            markTabModified(tab, true);
            tabPendingRemoteSyncMap.put(tab, true);
            updateStatus(editor, editor.getCaretPosition());
        });

        tab.setOnCloseRequest(event -> {
            if (isTabModified(tab)) {
                event.consume();
                if (confirmCloseTab(tab)) {
                    tabPane.getTabs().remove(tab);
                }
            }
        });

        updateStatus(editor, 0);
        refreshFormatToggleButtons(editor);
    }

    private void updateStatus(RichTextEditor editor, int caretPosition) {
        String text = editor.getText();

        int line = 1;
        int column = 1;
        for (int i = 0; i < caretPosition && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        lineLabel.setText("Line: " + line);
        columnLabel.setText("Col: " + column);

        String[] words = text.trim().split("\\s+");
        int wordCount = text.trim().isEmpty() ? 0 : words.length;
        wordCountLabel.setText("Words: " + wordCount);

        charCountLabel.setText("Chars: " + text.length());
    }

    private void markTabModified(Tab tab, boolean modified) {
        tabModifiedMap.put(tab, modified);
        String currentTitle = tab.getText();
        if (modified && !currentTitle.endsWith("*")) {
            tab.setText(currentTitle + "*");
            fileStatusLabel.setText("Modified");
        } else if (!modified && currentTitle.endsWith("*")) {
            tab.setText(currentTitle.substring(0, currentTitle.length() - 1));
            fileStatusLabel.setText("Saved");
        }
    }

    private boolean isTabModified(Tab tab) {
        return tabModifiedMap.getOrDefault(tab, false);
    }

    private boolean confirmCloseTab(Tab tab) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Do you want to save changes to \"" + tab.getText().replace("*", "") + "\"?");
        alert.setContentText("Your changes will be lost if you don't save them.");

        ButtonType saveButton = new ButtonType("Save");
        ButtonType dontSaveButton = new ButtonType("Don't Save");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(saveButton, dontSaveButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == saveButton) {
                return saveTab(tab);
            } else if (result.get() == dontSaveButton) {
                return true;
            }
        }
        return false;
    }

    private void loadNoteAsync(String noteName) {
        Task<LoadedNote> task = new Task<>() {
            @Override
            protected LoadedNote call() throws Exception {
                Path userNotesDir = Path.of(getUserNotesDir());
                File file = noteService.resolve(userNotesDir, noteName)
                        .map(Path::toFile)
                        .orElse(null);
                if (file == null || !file.exists()) {
                    return new LoadedNote(noteName, null, null);
                }
                return new LoadedNote(noteName, file, Files.readString(file.toPath()));
            }
        };

        task.setOnSucceeded(event -> {
            LoadedNote loadedNote = task.getValue();
            if (loadedNote.file() == null) {
                createNewTab(noteName);
                return;
            }

            try {
                Tab currentTab = createNewTab(loadedNote.noteName());
                RichTextEditor editor = editorForTab(currentTab);
                if (editor == null) {
                    fileStatusLabel.setText("Error loading file");
                    return;
                }
                loadEditorFromContent(editor, loadedNote.file(), loadedNote.content());
                editor.setFont(Font.font(SettingsManager.getFontFamily(), SettingsManager.getFontSize()));
                editor.setLanguageFromFileName(loadedNote.file().getName());
                tabFilePathMap.put(currentTab, loadedNote.file().getAbsolutePath());
                tabManagedNotePathMap.put(currentTab, loadedNote.file().getAbsolutePath());
                loadRemoteBinding(currentTab, loadedNote.file());
                tabPendingRemoteSyncMap.put(currentTab, false);
                markTabModified(currentTab, false);
                fileStatusLabel.setText("Loaded");
                refreshTagControls();
            } catch (IOException | ClassNotFoundException e) {
                fileStatusLabel.setText("Error loading file");
                showError("Failed to load file: " + e.getMessage());
            }
        });
        task.setOnFailed(event -> {
            fileStatusLabel.setText("Error loading file");
            showError("Failed to load file: " + safeMessage(task.getException()));
        });

        Thread thread = new Thread(task, "load-note");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean saveTab(Tab tab) {
        RichTextEditor editor = editorForTab(tab);
        if (editor == null) {
            fileStatusLabel.setText("No editor available");
            return false;
        }
        String filePath = tabFilePathMap.get(tab);

        if (filePath == null || filePath.isBlank()) {
            File targetFile = promptForSaveLocation(tab);
            if (targetFile == null) {
                fileStatusLabel.setText("Save cancelled");
                return false;
            }
            filePath = targetFile.getAbsolutePath();
        }

        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try {
            writeEditorToFile(editor, file);
            tabFilePathMap.put(tab, file.getAbsolutePath());
            syncManagedNoteCopy(tab, file, editor);
            editor.setLanguageFromFileName(file.getName());
            markTabModified(tab, false);
            fileStatusLabel.setText("Saved: " + file.getName());
            if (tab.equals(tabPane.getSelectionModel().getSelectedItem())) {
                refreshTagControls();
            }
            return true;
        } catch (IOException e) {
            fileStatusLabel.setText("Error saving file");
            showError("Failed to save file: " + e.getMessage());
            return false;
        }
    }

    private String sanitizeFileName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getUserNotesDir() {
        String userSegment = sanitizeFileName(
            currentUser == null || currentUser.isBlank() ? "default" : currentUser
        );
        if (userSegment.isBlank()) {
            userSegment = "default";
        }
        return AppConstants.NOTES_DIR + "/" + userSegment;
    }

    private void migrateLegacyNullUserNotes() {
        if (currentUser == null || currentUser.isBlank()) {
            return;
        }
        File legacyDir = new File(AppConstants.NOTES_DIR + "/null");
        if (!legacyDir.exists() || !legacyDir.isDirectory()) {
            return;
        }
        File targetDir = new File(getUserNotesDir());
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File[] legacyFiles = legacyDir.listFiles((dir, name) -> new File(dir, name).isFile() && !name.startsWith("."));
        if (legacyFiles == null || legacyFiles.length == 0) {
            return;
        }
        for (File legacyFile : legacyFiles) {
            File targetFile = new File(targetDir, legacyFile.getName());
            if (targetFile.exists()) {
                continue;
            }
            legacyFile.renameTo(targetFile);
        }
    }

    private String removeTxtExtension(String filename) {
        if (filename != null && filename.endsWith(".txt")) {
            return filename.substring(0, filename.length() - 4);
        }
        return filename;
    }

    private String formatTabTitleForFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.toLowerCase().endsWith(".txt") ? removeTxtExtension(fileName) : fileName;
    }

    private void startAutoSave() {
        autoSaveTimer = new Timer(true);
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    for (Tab tab : tabPane.getTabs()) {
                        if (isTabModified(tab) && tabFilePathMap.containsKey(tab)) {
                            saveTab(tab);
                        }
                    }
                });
            }
        }, AppConstants.AUTOSAVE_INTERVAL_MS, AppConstants.AUTOSAVE_INTERVAL_MS);
    }

    private void startRemoteAutoSave() {
        int intervalSeconds = SettingsManager.getRemoteAutoSaveSeconds();
        remoteAutoSaveTimer = new Timer(true);
        remoteAutoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    for (Tab tab : tabPane.getTabs()) {
                        if (!tabPendingRemoteSyncMap.getOrDefault(tab, false)) {
                            continue;
                        }
                        RemoteNoteRegistry.RemoteBinding binding = resolveRemoteBinding(tab);
                        if (binding == null) {
                            continue;
                        }
                        RichTextEditor editor = tabEditorMap.get(tab);
                        if (editor == null) {
                            continue;
                        }
                        NetworkSyncService.syncRemoteNoteAsync(
                                SettingsManager.getNetworkNamespace(),
                                binding.author(),
                                binding.remoteName(),
                                serializeEditorContent(editor)
                        ).whenComplete((success, throwable) -> Platform.runLater(() -> {
                            if (Boolean.TRUE.equals(success) && throwable == null) {
                                tabPendingRemoteSyncMap.put(tab, false);
                                if (tab.equals(tabPane.getSelectionModel().getSelectedItem())) {
                                    fileStatusLabel.setText("Remote autosaved");
                                }
                            }
                        }));
                    }
                });
            }
        }, intervalSeconds * 1000L, intervalSeconds * 1000L);
    }

    private void stopBackgroundTimers() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer = null;
        }
        if (remoteAutoSaveTimer != null) {
            remoteAutoSaveTimer.cancel();
            remoteAutoSaveTimer = null;
        }
    }

    private void navigateBackToDashboard() {
        if (mainApp == null || dashboardNavigationPending) {
            return;
        }
        dashboardNavigationPending = true;
        stopBackgroundTimers();
        Platform.runLater(() -> {
            try {
                if (mainApp != null) {
                    mainApp.showDashboard(currentUser);
                }
            } finally {
                dashboardNavigationPending = false;
            }
        });
    }

    @FXML private void handleNew(ActionEvent event) {
        createNewTab(null);
    }

    private RichTextEditor editorForTab(Tab tab) {
        return tab == null ? null : tabEditorMap.get(tab);
    }

    private RichTextEditor getActiveEditor() {
        if (tabPane == null) {
            return null;
        }
        return editorForTab(tabPane.getSelectionModel().getSelectedItem());
    }

    @FXML private void handleOpen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md", "*.json", "*.xml", "*.yaml", "*.yml"),
                new FileChooser.ExtensionFilter("Code Files", "*.java", "*.py", "*.js", "*.ts", "*.c", "*.cpp", "*.cs", "*.go", "*.rs", "*.php", "*.rb", "*.sql", "*.css", "*.html", "*.sh")
        );
        fileChooser.setInitialDirectory(new File("notes"));

        File file = fileChooser.showOpenDialog(tabPane.getScene().getWindow());
        if (file != null) {
            openFile(file);
        }
    }

    public void openFile(File file) {
        if (file == null) {
            return;
        }
        try {
            String fileName = file.getName();
            Tab currentTab = createNewTab(fileName);
            RichTextEditor editor = editorForTab(currentTab);
            if (editor == null) {
                showError("Failed to open file: editor is unavailable.");
                return;
            }
            loadEditorFromFile(editor, file);
            editor.setLanguageFromFileName(file.getName());
            tabFilePathMap.put(currentTab, file.getAbsolutePath());
            if (isManagedNotesFile(file)) {
                tabManagedNotePathMap.put(currentTab, file.getAbsolutePath());
                loadRemoteBinding(currentTab, file);
            }
            tabPendingRemoteSyncMap.put(currentTab, false);
            markTabModified(currentTab, false);
            refreshTagControls();
        } catch (IOException | ClassNotFoundException e) {
            showError("Failed to open file: " + e.getMessage());
        }
    }

    private void writeEditorToFile(RichTextEditor editor, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(AppConstants.STYLED_DOC_HEADER);
            writer.newLine();
            writer.write(encodeDocument(editor.toDocument()));
        }
    }

    private void loadEditorFromFile(RichTextEditor editor, File file) throws IOException, ClassNotFoundException {
        loadEditorFromContent(editor, file, Files.readString(file.toPath()));
    }

    private void loadEditorFromContent(RichTextEditor editor, File file, String content) throws IOException, ClassNotFoundException {
        if (content.startsWith(AppConstants.STYLED_DOC_HEADER + System.lineSeparator())
                || content.startsWith(AppConstants.STYLED_DOC_HEADER + "\n")
                || content.equals(AppConstants.STYLED_DOC_HEADER)) {
            String encoded = content.substring(AppConstants.STYLED_DOC_HEADER.length()).stripLeading();
            editor.fromDocument(decodeDocument(encoded));
            return;
        }
        editor.setText(content);
    }

    private String encodeDocument(StyledDocument document) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(document);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private StyledDocument decodeDocument(String encoded) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try (ObjectInputStream in = new CompatibleDocumentInputStream(new ByteArrayInputStream(bytes))) {
            return (StyledDocument) in.readObject();
        }
    }

    private static class CompatibleDocumentInputStream extends ObjectInputStream {
        private static final String LEGACY_DOCUMENT_CLASS = "org.quillpad.collab.Document";
        private static final String LEGACY_SEGMENT_CLASS = "org.quillpad.collab.Document$StyleSegment";

        CompatibleDocumentInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            return switch (desc.getName()) {
                case LEGACY_DOCUMENT_CLASS, "org.openjfx.model.StyledDocument" -> StyledDocument.class;
                case LEGACY_SEGMENT_CLASS, "org.openjfx.model.StyledDocument$StyleSegment" -> StyledDocument.StyleSegment.class;
                default -> super.resolveClass(desc);
            };
        }
    }

    @FXML private void handleSave(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            saveTab(selectedTab);
        }
    }

    @FXML private void handleSaveAs(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            File targetFile = promptForSaveLocation(selectedTab);
            if (targetFile != null) {
                saveTab(selectedTab);
            }
        }
    }

    private File promptForSaveLocation(Tab tab) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save As");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("C Files", "*.c"),
                new FileChooser.ExtensionFilter("C++ Files", "*.cpp"),
                new FileChooser.ExtensionFilter("Java Files", "*.java"),
                new FileChooser.ExtensionFilter("Python Files", "*.py"),
                new FileChooser.ExtensionFilter("HTML Files", "*.html"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        String currentPath = tabFilePathMap.get(tab);
        File initialDirectory = resolveInitialSaveDirectory(currentPath);
        if (initialDirectory != null) {
            fileChooser.setInitialDirectory(initialDirectory);
        }

        String currentFileName = buildSaveAsFileName(tab);
        if (currentFileName != null && !currentFileName.isBlank()) {
            fileChooser.setInitialFileName(currentFileName);
        }

        File file = fileChooser.showSaveDialog(tabPane.getScene().getWindow());
        if (file == null) {
            return null;
        }

        String fileName = ensureSupportedExtension(file.getName(), fileChooser.getSelectedExtensionFilter());
        File targetFile = fileName.equals(file.getName())
                ? file
                : new File(file.getParentFile(), fileName);

        tab.setText(formatTabTitleForFileName(targetFile.getName()));
        tabFilePathMap.put(tab, targetFile.getAbsolutePath());
        if (!isManagedNotesFile(targetFile)) {
            tabRemoteBindingMap.remove(tab);
        }
        return targetFile;
    }

    private File resolveInitialSaveDirectory(String currentPath) {
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            File parent = currentFile.getParentFile();
            if (parent != null && parent.exists()) {
                return parent;
            }
        }

        File userNotesDir = new File(getUserNotesDir());
        if (userNotesDir.exists()) {
            return userNotesDir;
        }

        File notesDir = new File("notes");
        return notesDir.exists() ? notesDir : null;
    }

    private String buildSaveAsFileName(Tab tab) {
        String currentPath = tabFilePathMap.get(tab);
        if (currentPath != null && !currentPath.isBlank()) {
            return new File(currentPath).getName();
        }

        String tabTitle = tab.getText().replace("*", "").trim();
        if (tabTitle.isEmpty()) {
            return "Untitled.txt";
        }

        if (tabTitle.contains(".") || tabTitle.startsWith(".")) {
            return tabTitle;
        }

        return tabTitle + ".txt";
    }

    private String ensureSupportedExtension(String fileName, FileChooser.ExtensionFilter selectedFilter) {
        if (fileName == null || fileName.isBlank()) {
            return "Untitled.txt";
        }

        if (fileName.contains(".")) {
            return fileName;
        }

        if (selectedFilter == null) {
            return fileName + ".txt";
        }

        List<String> patterns = selectedFilter.getExtensions();
        if (patterns == null || patterns.isEmpty()) {
            return fileName + ".txt";
        }

        String pattern = patterns.get(0);
        if ("*.*".equals(pattern)) {
            return fileName;
        }

        String extension = pattern.replace("*", "").trim();
        if (extension.isEmpty()) {
            return fileName;
        }

        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        return SAVEABLE_EXTENSIONS.contains(extension.toLowerCase()) ? fileName + extension : fileName;
    }

    private void syncManagedNoteCopy(Tab tab, File savedFile, RichTextEditor editor) throws IOException {
        if (savedFile == null || editor == null) {
            return;
        }

        File managedFile;
        if (isManagedNotesFile(savedFile)) {
            managedFile = savedFile;
        } else {
            managedFile = resolveManagedNoteTarget(tab, savedFile.getName());
            writeEditorToFile(editor, managedFile);
        }

        String previousManagedPath = tabManagedNotePathMap.get(tab);
        if (previousManagedPath != null && !previousManagedPath.isBlank()) {
            File previousManagedFile = new File(previousManagedPath);
            if (!previousManagedFile.getAbsolutePath().equals(managedFile.getAbsolutePath())
                    && previousManagedFile.exists()
                    && isManagedNotesFile(previousManagedFile)) {
                noteTagService.renameNote(Path.of(getUserNotesDir()), previousManagedFile.getName(), managedFile.getName());
                RemoteNoteRegistry.remove(Path.of(getUserNotesDir()), previousManagedFile.getName());
                Files.deleteIfExists(previousManagedFile.toPath());
            }
        }

        tabManagedNotePathMap.put(tab, managedFile.getAbsolutePath());
        RemoteNoteRegistry.RemoteBinding binding = tabRemoteBindingMap.get(tab);
        if (binding != null) {
            RemoteNoteRegistry.put(Path.of(getUserNotesDir()), managedFile.getName(), binding.remoteName(), binding.author());
        }
    }

    private File resolveManagedNoteTarget(Tab tab, String fileName) {
        File notesDir = new File(getUserNotesDir());
        if (!notesDir.exists()) {
            notesDir.mkdirs();
        }

        String sanitizedFileName = sanitizeFileName(fileName);
        if (sanitizedFileName.isBlank()) {
            sanitizedFileName = "Untitled.txt";
        }

        String previousManagedPath = tabManagedNotePathMap.get(tab);
        if (previousManagedPath != null) {
            File previousManagedFile = new File(previousManagedPath);
            if (isManagedNotesFile(previousManagedFile)) {
                return previousManagedFile;
            }
        }

        File candidate = new File(notesDir, sanitizedFileName);
        if (!candidate.exists()) {
            return candidate;
        }

        return nextDuplicateFile(notesDir, sanitizedFileName);
    }

    private boolean isManagedNotesFile(File file) {
        if (file == null) {
            return false;
        }

        try {
            Path managedDir = Path.of(getUserNotesDir()).toAbsolutePath().normalize();
            Path target = file.toPath().toAbsolutePath().normalize();
            return target.startsWith(managedDir);
        } catch (Exception e) {
            return false;
        }
    }

    private void loadRemoteBinding(Tab tab, File managedFile) {
        if (tab == null || managedFile == null || !isManagedNotesFile(managedFile)) {
            return;
        }
        RemoteNoteRegistry.get(Path.of(getUserNotesDir()), managedFile.getName())
                .ifPresent(binding -> tabRemoteBindingMap.put(tab, binding));
    }

    private RemoteNoteRegistry.RemoteBinding resolveRemoteBinding(Tab tab) {
        if (tab == null) {
            return null;
        }
        RemoteNoteRegistry.RemoteBinding existing = tabRemoteBindingMap.get(tab);
        if (existing != null) {
            return existing;
        }
        String managedPath = tabManagedNotePathMap.get(tab);
        if (managedPath == null || managedPath.isBlank()) {
            return null;
        }
        File managedFile = new File(managedPath);
        if (!managedFile.exists() || !isManagedNotesFile(managedFile)) {
            return null;
        }
        Optional<RemoteNoteRegistry.RemoteBinding> binding =
                RemoteNoteRegistry.get(Path.of(getUserNotesDir()), managedFile.getName());
        binding.ifPresent(value -> tabRemoteBindingMap.put(tab, value));
        return binding.orElse(null);
    }

    private String serializeEditorContent(RichTextEditor editor) {
        try {
            return AppConstants.STYLED_DOC_HEADER + System.lineSeparator() + encodeDocument(editor.toDocument());
        } catch (IOException e) {
            return editor.getText();
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex);
    }

    private String removeExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private File nextDuplicateFile(File directory, String fileName) {
        String baseName = removeExtension(fileName);
        String extension = extensionOf(fileName);
        int suffix = 2;
        File candidate = new File(directory, fileName);
        while (candidate.exists()) {
            candidate = new File(directory, baseName + " (" + suffix + ")" + extension);
            suffix++;
        }
        return candidate;
    }

    @FXML private void handleAddTag(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        Path managedNotePath = currentManagedNotePath(selectedTab);
        if (managedNotePath == null) {
            showError("Save the note inside your QuillPad notes folder before assigning tags.");
            return;
        }
        showAddTagDialog(managedNotePath);
    }

    private void showAddTagDialog(Path managedNotePath) {
        Set<Tag> existingTags = noteTagService.getTags(Path.of(getUserNotesDir()), managedNotePath.getFileName().toString());
        if (existingTags.size() >= AppConstants.MAX_TAGS_PER_FILE) {
            showError("A note can have at most " + AppConstants.MAX_TAGS_PER_FILE + " tags.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Tag");
        dialog.setHeaderText("Add a tag to this note");

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
        dialog.getDialogPane().applyCss();
        Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
        if (addButton != null) {
            addButton.disableProperty().bind(tagField.textProperty().isEmpty());
        }

        Platform.runLater(tagField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != addButtonType) {
            return;
        }

        try {
            if (noteTagService.getTags(Path.of(getUserNotesDir()), managedNotePath.getFileName().toString()).size() >= AppConstants.MAX_TAGS_PER_FILE) {
                showError("A note can have at most " + AppConstants.MAX_TAGS_PER_FILE + " tags.");
                return;
            }
            noteTagService.addTag(Path.of(getUserNotesDir()), managedNotePath.getFileName().toString(), tagField.getText());
            refreshTagControls();
        } catch (Exception e) {
            showError("Failed to add tag: " + safeMessage(e));
        }
    }

    @FXML private void handleManageTags(ActionEvent event) {
        showManageTagsDialog();
    }

    private void refreshTagControls() {
        boolean editable = currentManagedNotePath(tabPane == null ? null : tabPane.getSelectionModel().getSelectedItem()) != null;
        if (addTagMenuItem != null) {
            addTagMenuItem.setDisable(!editable);
        }
        if (manageTagMenuItem != null) {
            manageTagMenuItem.setDisable(currentUser == null || currentUser.isBlank());
        }
        renderTagChips();
    }

    private void renderTagChips() {
        if (tagChipPane == null) {
            return;
        }
        tagChipPane.getChildren().clear();
        Path managedNotePath = currentManagedNotePath(tabPane == null ? null : tabPane.getSelectionModel().getSelectedItem());
        if (managedNotePath == null) {
            tagChipPane.getChildren().add(new Label("Save this note into QuillPad storage to manage tags."));
            return;
        }

        Set<Tag> tags = noteTagService.getTags(Path.of(getUserNotesDir()), managedNotePath.getFileName().toString());
        if (tags.isEmpty()) {
            tagChipPane.getChildren().add(new Label("No tags assigned."));
            return;
        }

        for (Tag tag : tags) {
            Button chip = new Button("#" + tag.displayName() + " ×");
            chip.getStyleClass().add("tag-chip-button");
            chip.setOnAction(event -> {
                try {
                    noteTagService.removeTag(Path.of(getUserNotesDir()), managedNotePath.getFileName().toString(), tag.displayName());
                    renderTagChips();
                } catch (IOException e) {
                    showError("Failed to remove tag: " + e.getMessage());
                }
            });
            tagChipPane.getChildren().add(chip);
        }
    }

    private Path currentManagedNotePath(Tab tab) {
        if (tab == null) {
            return null;
        }
        String managedPath = tabManagedNotePathMap.get(tab);
        if (managedPath == null || managedPath.isBlank()) {
            return null;
        }
        Path path = Path.of(managedPath);
        return Files.exists(path) ? path : null;
    }

    private void showManageTagsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Manage Tags");
        dialog.setHeaderText("Rename or delete reusable tags");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(480, 420);

        ObservableList<Tag> tagItems = FXCollections.observableArrayList(noteTagService.getAllTags(Path.of(getUserNotesDir())));
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
        renameButton.disableProperty().bind(javafx.beans.binding.Bindings.or(tagListView.getSelectionModel().selectedItemProperty().isNull(), renameField.textProperty().isEmpty()));
        deleteTagButton.disableProperty().bind(tagListView.getSelectionModel().selectedItemProperty().isNull());

        renameButton.setOnAction(action -> {
            Tag selectedTag = tagListView.getSelectionModel().getSelectedItem();
            if (selectedTag == null) {
                return;
            }
            try {
                noteTagService.renameTag(Path.of(getUserNotesDir()), selectedTag.displayName(), renameField.getText());
                renameField.clear();
                tagItems.setAll(noteTagService.getAllTags(Path.of(getUserNotesDir())));
                refreshTagControls();
            } catch (Exception e) {
                showError("Failed to rename tag: " + safeMessage(e));
            }
        });

        deleteTagButton.setOnAction(action -> {
            Tag selectedTag = tagListView.getSelectionModel().getSelectedItem();
            if (selectedTag == null) {
                return;
            }
            try {
                noteTagService.deleteTag(Path.of(getUserNotesDir()), selectedTag.displayName());
                tagItems.setAll(noteTagService.getAllTags(Path.of(getUserNotesDir())));
                refreshTagControls();
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

    @FXML private void handleRenameFile(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) {
            return;
        }

        String currentTitle = selectedTab.getText().replace("*", "");
        String currentPath = tabFilePathMap.get(selectedTab);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Rename File");
        dialog.setHeaderText("Choose new file name and extension");

        ButtonType renameButtonType = new ButtonType("Rename", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(renameButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        String currentBaseName = currentTitle;
        String currentExt = ".txt";
        int dot = currentTitle.lastIndexOf('.');
        if (dot > 0 && dot < currentTitle.length() - 1) {
            currentBaseName = currentTitle.substring(0, dot);
            currentExt = currentTitle.substring(dot);
        }

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

        String newFileName = name + ext;
        boolean isModified = isTabModified(selectedTab);

        if (currentPath != null && !currentPath.isBlank()) {
            try {
                Path oldPath = Path.of(currentPath);
                Path newPath = oldPath.resolveSibling(newFileName);
                if (Files.exists(newPath) && !oldPath.equals(newPath)) {
                    showError("A file with this name already exists.");
                    return;
                }
                Files.createDirectories(newPath.getParent());
                Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                tabFilePathMap.put(selectedTab, newPath.toString());
                if (isManagedNotesFile(oldPath.toFile())) {
                    tabManagedNotePathMap.put(selectedTab, newPath.toString());
                    RemoteNoteRegistry.rename(Path.of(getUserNotesDir()), oldPath.getFileName().toString(), newPath.getFileName().toString());
                    noteTagService.renameNote(Path.of(getUserNotesDir()), oldPath.getFileName().toString(), newPath.getFileName().toString());
                }
            } catch (IOException e) {
                showError("Failed to rename file: " + e.getMessage());
                return;
            }
        } else {
            tabFilePathMap.remove(selectedTab);
        }

        selectedTab.setText(formatTabTitleForFileName(newFileName) + (isModified ? "*" : ""));
        RichTextEditor editor = tabEditorMap.get(selectedTab);
        if (editor != null) {
            editor.setDocumentName(newFileName);
            editor.setLanguageFromFileName(newFileName);
        }
        fileStatusLabel.setText("Renamed: " + newFileName);
        refreshTagControls();
    }

    @FXML private void handleCloseTab(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            if (isTabModified(selectedTab)) {
                if (confirmCloseTab(selectedTab)) {
                    tabPane.getTabs().remove(selectedTab);
                }
            } else {
                tabPane.getTabs().remove(selectedTab);
            }
        }

        if (tabPane.getTabs().isEmpty()) {
            handleClose(null);
        }
    }

    @FXML private void handleClose(ActionEvent event) {
        boolean hasUnsaved = false;
        for (Tab tab : tabPane.getTabs()) {
            if (isTabModified(tab)) {
                hasUnsaved = true;
                break;
            }
        }

        if (hasUnsaved) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("You have unsaved changes in one or more tabs.");
            alert.setContentText("Do you want to save all changes before closing?");

            ButtonType saveAllButton = new ButtonType("Save All");
            ButtonType discardButton = new ButtonType("Discard All");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(saveAllButton, discardButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == saveAllButton) {
                    for (Tab tab : tabPane.getTabs()) {
                        if (isTabModified(tab)) {
                            saveTab(tab);
                        }
                    }
                    navigateBackToDashboard();
                } else if (result.get() == discardButton) {
                    navigateBackToDashboard();
                }
            }
        } else {
            navigateBackToDashboard();
        }
    }

    @FXML private void handleUndo(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.undo();
        }
    }

    @FXML private void handleRedo(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.redo();
        }
    }

    @FXML private void handleCut(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.cut();
        }
    }

    @FXML private void handleCopy(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.copy();
        }
    }

    @FXML private void handlePaste(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.paste();
        }
    }

    @FXML private void handleSelectAll(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.selectAll();
        }
    }

    @FXML private void handleFind(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            showFindDialog(editor);
        }
    }

    @FXML private void handleReplace(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Find and Replace");
        dialog.setHeaderText(null);

        ButtonType findNextType = new ButtonType("Find Next", ButtonBar.ButtonData.LEFT);
        ButtonType replaceType = new ButtonType("Replace", ButtonBar.ButtonData.LEFT);
        ButtonType replaceAllType = new ButtonType("Replace All", ButtonBar.ButtonData.LEFT);
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(findNextType, replaceType, replaceAllType, closeType);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField findField = new TextField();
        findField.setPromptText("Find");
        findField.setPrefWidth(280);
        TextField replaceField = new TextField();
        replaceField.setPromptText("Replace with");
        replaceField.setPrefWidth(280);
        Label statusLabel = new Label("");

        grid.add(new Label("Find:"), 0, 0);
        grid.add(findField, 1, 0);
        grid.add(new Label("Replace:"), 0, 1);
        grid.add(replaceField, 1, 1);
        grid.add(statusLabel, 1, 2);

        dialog.getDialogPane().setContent(grid);

        java.util.List<Integer> matches = new ArrayList<>();
        int[] currentMatchIndex = {-1};

        Runnable refreshMatches = () -> {
            matches.clear();
            currentMatchIndex[0] = -1;
            String query = findField.getText();
            if (query == null || query.isEmpty()) {
                statusLabel.setText("");
                editor.clearSelection();
                return;
            }
            matches.addAll(findAllMatches(editor.getText(), query));
            if (matches.isEmpty()) {
                statusLabel.setText("Not found");
                editor.clearSelection();
                return;
            }
            currentMatchIndex[0] = 0;
            selectSearchMatch(editor, query, matches, 0, statusLabel);
        };

        findField.textProperty().addListener((obs, oldVal, newVal) -> refreshMatches.run());

        dialog.getDialogPane().applyCss();
        Button findNextBtn = (Button) dialog.getDialogPane().lookupButton(findNextType);
        Button replaceBtn = (Button) dialog.getDialogPane().lookupButton(replaceType);
        Button replaceAllBtn = (Button) dialog.getDialogPane().lookupButton(replaceAllType);

        if (findNextBtn != null) {
            findNextBtn.addEventFilter(ActionEvent.ACTION, e -> {
                e.consume();
                if (matches.isEmpty()) {
                    refreshMatches.run();
                    return;
                }
                currentMatchIndex[0] = currentMatchIndex[0] < 0 ? 0
                        : (currentMatchIndex[0] + 1) % matches.size();
                selectSearchMatch(editor, findField.getText(), matches, currentMatchIndex[0], statusLabel);
            });
        }

        if (replaceBtn != null) {
            replaceBtn.addEventFilter(ActionEvent.ACTION, e -> {
                e.consume();
                if (matches.isEmpty() || currentMatchIndex[0] < 0) {
                    refreshMatches.run();
                    return;
                }
                String query = findField.getText();
                String replacement = replaceField.getText() == null ? "" : replaceField.getText();
                int start = matches.get(currentMatchIndex[0]);
                int end = start + query.length();
                if (editor.replaceAt(start, end, replacement)) {
                    // Recalculate matches after the edit and advance to next
                    matches.clear();
                    currentMatchIndex[0] = -1;
                    matches.addAll(findAllMatches(editor.getText(), query));
                    if (matches.isEmpty()) {
                        statusLabel.setText("No more occurrences");
                        editor.clearSelection();
                    } else {
                        // Find the match at or after the replacement end
                        int nextPos = start + replacement.length();
                        currentMatchIndex[0] = 0;
                        for (int i = 0; i < matches.size(); i++) {
                            if (matches.get(i) >= nextPos) {
                                currentMatchIndex[0] = i;
                                break;
                            }
                        }
                        selectSearchMatch(editor, query, matches, currentMatchIndex[0], statusLabel);
                    }
                }
            });
        }

        if (replaceAllBtn != null) {
            replaceAllBtn.addEventFilter(ActionEvent.ACTION, e -> {
                e.consume();
                String query = findField.getText();
                String replacement = replaceField.getText() == null ? "" : replaceField.getText();
                if (query == null || query.isEmpty()) {
                    return;
                }
                int count = editor.replaceAll(query, replacement);
                matches.clear();
                currentMatchIndex[0] = -1;
                statusLabel.setText(count > 0
                        ? "Replaced " + count + " occurrence" + (count == 1 ? "" : "s")
                        : "Not found");
                if (fileStatusLabel != null) {
                    fileStatusLabel.setText(statusLabel.getText());
                }
            });
        }

        Platform.runLater(findField::requestFocus);
        dialog.showAndWait();
        editor.clearSelection();
    }

    @FXML private void handleWordWrap(ActionEvent event) {
        boolean wrapText = wordWrapMenuItem.isSelected();
        for (Tab tab : tabPane.getTabs()) {
            RichTextEditor editor = editorForTab(tab);
            if (editor != null) {
                editor.setWrapText(wrapText);
            }
        }
    }

    @FXML private void handleAbout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About QuillPad");
        alert.setHeaderText("QuillPad - Professional Note Editor");
        alert.setContentText(
                "Version 1.0\n\n" +
                        "A feature-rich text editor with:\n" +
                        "• Rich text editing (font, size, color)\n" +
                        "• Multiple tabs\n" +
                        "• Auto-save\n" +
                        "• Find and Replace\n" +
                        "• And more!\n\n" +
                        "Developed with JavaFX"
        );
        alert.showAndWait();
    }

    @FXML private void handleIncreaseFont(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            double newSize = Math.min(72, currentEditorFontSize(editor) + 2);
            editor.setFont(Font.font(SettingsManager.getFontFamily(), newSize));
            setFontSizeComboValue((int) newSize);
            fileStatusLabel.setText("Font size: " + (int) newSize + "px");
        }
    }

    @FXML private void handleDecreaseFont(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            double newSize = Math.max(8, currentEditorFontSize(editor) - 2);
            editor.setFont(Font.font(SettingsManager.getFontFamily(), newSize));
            setFontSizeComboValue((int) newSize);
            fileStatusLabel.setText("Font size: " + (int) newSize + "px");
        }
    }

    /** Returns the current font size of the editor, reading from its inline style if set. */
    private double currentEditorFontSize(RichTextEditor editor) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("-fx-font-size: ([0-9.]+)px;")
                .matcher(editor.getTextArea().getStyle());
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return SettingsManager.getFontSize();
    }

    @FXML private void handleResetFont(ActionEvent event) {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            int savedSize = SettingsManager.getFontSize();
            editor.setFont(Font.font(SettingsManager.getFontFamily(), savedSize));
            fontFamilyCombo.setValue(SettingsManager.getFontFamily());
            setFontSizeComboValue(savedSize);
            fileStatusLabel.setText("Font reset");
        }
    }

    private void applyFontFamily() {
        if (updatingToolbarState) {
            return;
        }
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            String fontFamily = fontFamilyCombo.getValue();
            if (fontFamily != null) {
                editor.applyFontFamilyToSelection(fontFamily);
            }
        }
    }

    private void applyFontSize() {
        if (updatingToolbarState) {
            return;
        }
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            Integer fontSize = fontSizeCombo.getValue();
            if (fontSize != null) {
                editor.applyFontSizeToSelection(fontSize);
            }
        }
    }

    private void applyBold() {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.applyBoldToSelection(boldButton.isSelected());
            refreshFormatToggleButtons(editor);
        }
    }

    private void applyItalic() {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.applyItalicToSelection(italicButton.isSelected());
            refreshFormatToggleButtons(editor);
        }
    }

    private void applyUnderline() {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.applyUnderlineToSelection(underlineButton.isSelected());
            refreshFormatToggleButtons(editor);
        }
    }

    private void applyStrikethrough() {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            editor.applyStrikethroughToSelection(strikethroughButton.isSelected());
            refreshFormatToggleButtons(editor);
        }
    }

    private void refreshFormatToggleButtons(RichTextEditor editor) {
        if (editor == null) {
            return;
        }
        updatingToolbarState = true;
        try {
            fontFamilyCombo.setValue(editor.getCurrentFontFamily());
            setFontSizeComboValue(editor.getCurrentFontSize());
            boldButton.setSelected(editor.isBoldActive());
            italicButton.setSelected(editor.isItalicActive());
            underlineButton.setSelected(editor.isUnderlineActive());
            strikethroughButton.setSelected(editor.isStrikethroughActive());
        } finally {
            updatingToolbarState = false;
        }
    }

    private void setFontSizeComboValue(int size) {
        if (fontSizeCombo == null) {
            return;
        }
        ObservableList<Integer> items = fontSizeCombo.getItems();
        if (items != null && !items.contains(size)) {
            items.add(size);
            FXCollections.sort(items);
        }
        fontSizeCombo.setValue(size);
    }

    private void applyTextColor() {
        RichTextEditor editor = getActiveEditor();
        if (editor != null) {
            Color color = textColorPicker.getValue();
            if (color != null) {
                String colorHex = String.format("#%02X%02X%02X", 
                    (int)(color.getRed() * 255),
                    (int)(color.getGreen() * 255),
                    (int)(color.getBlue() * 255));
                editor.applyTextColorToSelection(colorHex);
            }
        }
    }

    @FXML private void handleThemeToggle(ActionEvent event) {
        ThemeManager.toggleTheme();
        for (RichTextEditor editor : tabEditorMap.values()) {
            editor.updateTheme();
        }
        refreshThemeToggleButton();
    }

    private void refreshThemeToggleButton() {
        if (themeToggleButton == null) {
            return;
        }
        ThemeManager.Theme current = ThemeManager.getCurrentTheme();
        String icon = ThemeManager.getThemeIcon(current);
        ThemeManager.Theme next = current == ThemeManager.Theme.DARK
            ? ThemeManager.Theme.LIGHT
            : ThemeManager.Theme.DARK;
        themeToggleButton.setText(icon);
        themeToggleButton.setTooltip(new Tooltip("Theme: " + current.getDisplayName() + " (switch to " + next.getDisplayName() + ")"));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "unknown error";
        }
        return throwable.getMessage();
    }

    private record LoadedNote(String noteName, File file, String content) {
    }

    private void showFindDialog(RichTextEditor editor) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Find");
        dialog.setHeaderText("Find in current document");

        ButtonType previousButtonType = new ButtonType("Previous", ButtonBar.ButtonData.LEFT);
        ButtonType nextButtonType = new ButtonType("Next", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(previousButtonType, nextButtonType, closeButtonType);

        TextField findField = new TextField();
        findField.setPromptText("Search for");
        Label countLabel = new Label("0 matches");

        VBox content = new VBox(10);
        content.getChildren().addAll(new Label("Find:"), findField, countLabel);
        dialog.getDialogPane().setContent(content);

        java.util.List<Integer> matches = new ArrayList<>();
        int[] currentMatchIndex = { -1 };

        Runnable refreshMatches = () -> {
            matches.clear();
            currentMatchIndex[0] = -1;

            String query = findField.getText();
            if (query == null || query.isEmpty()) {
                countLabel.setText("0 matches");
                editor.clearSelection();
                return;
            }

            matches.addAll(findAllMatches(editor.getText(), query));
            if (matches.isEmpty()) {
                countLabel.setText("0 matches");
                editor.clearSelection();
                return;
            }

            int selectionStart = editor.getSelection().getStart();
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i) >= selectionStart) {
                    currentMatchIndex[0] = i;
                    break;
                }
            }
            if (currentMatchIndex[0] < 0) {
                currentMatchIndex[0] = 0;
            }
            selectSearchMatch(editor, findField.getText(), matches, currentMatchIndex[0], countLabel);
        };

        Runnable goNext = () -> {
            if (matches.isEmpty()) {
                return;
            }
            currentMatchIndex[0] = currentMatchIndex[0] < 0 ? 0 : (currentMatchIndex[0] + 1) % matches.size();
            selectSearchMatch(editor, findField.getText(), matches, currentMatchIndex[0], countLabel);
        };

        Runnable goPrevious = () -> {
            if (matches.isEmpty()) {
                return;
            }
            currentMatchIndex[0] = currentMatchIndex[0] < 0
                    ? matches.size() - 1
                    : (currentMatchIndex[0] - 1 + matches.size()) % matches.size();
            selectSearchMatch(editor, findField.getText(), matches, currentMatchIndex[0], countLabel);
        };

        findField.textProperty().addListener((obs, oldValue, newValue) -> refreshMatches.run());
        findField.setOnAction(e -> goNext.run());

        dialog.getDialogPane().applyCss();
        Button previousButton = (Button) dialog.getDialogPane().lookupButton(previousButtonType);
        Button nextButton = (Button) dialog.getDialogPane().lookupButton(nextButtonType);
        if (previousButton != null) {
            previousButton.addEventFilter(ActionEvent.ACTION, e -> {
                e.consume();
                goPrevious.run();
            });
        }
        if (nextButton != null) {
            nextButton.addEventFilter(ActionEvent.ACTION, e -> {
                e.consume();
                goNext.run();
            });
        }

        Platform.runLater(findField::requestFocus);
        dialog.showAndWait();
    }

    private java.util.List<Integer> findAllMatches(String content, String query) {
        java.util.List<Integer> matches = new ArrayList<>();
        if (content == null || content.isEmpty() || query == null || query.isEmpty()) {
            return matches;
        }
        int index = 0;
        while ((index = content.indexOf(query, index)) >= 0) {
            matches.add(index);
            index += query.length();
        }
        return matches;
    }

    private void selectSearchMatch(RichTextEditor editor, String query, java.util.List<Integer> matches, int matchIndex, Label countLabel) {
        if (matches.isEmpty() || query == null || query.isEmpty() || matchIndex < 0 || matchIndex >= matches.size()) {
            countLabel.setText("0 matches");
            return;
        }
        int start = matches.get(matchIndex);
        int end = start + query.length();
        int docLen = editor.getText().length();
        if (start < 0 || end > docLen) {
            countLabel.setText("0 matches");
            return;
        }
        editor.selectRange(start, end);
        editor.getTextArea().requestFocus();
        countLabel.setText((matchIndex + 1) + " of " + matches.size() + " matches");
    }
}
