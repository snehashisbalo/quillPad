package org.openjfx.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import org.openjfx.QuillPad;
import org.openjfx.SettingsManager;
import org.openjfx.ThemeManager;
import org.openjfx.component.RichTextEditor;
import org.quillpad.collab.CollabClient;
import org.quillpad.collab.Document;
import org.quillpad.collab.Message;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.Timer;

public class EditorController implements Initializable {

    @FXML private TabPane tabPane;
    @FXML private Label lineLabel;
    @FXML private Label columnLabel;
    @FXML private Label wordCountLabel;
    @FXML private Label charCountLabel;
    @FXML private Label fileStatusLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private Label collabStatusLabel;

    @FXML private MenuItem newMenuItem;
    @FXML private MenuItem openMenuItem;
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem saveAsMenuItem;
    @FXML private MenuItem closeMenuItem;
    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;
    @FXML private MenuItem cutMenuItem;
    @FXML private MenuItem copyMenuItem;
    @FXML private MenuItem pasteMenuItem;
    @FXML private MenuItem selectAllMenuItem;
    @FXML private MenuItem findMenuItem;
    @FXML private MenuItem replaceMenuItem;
    @FXML private CheckMenuItem wordWrapMenuItem;
    @FXML private MenuItem aboutMenuItem;
    @FXML private MenuItem increaseFontMenuItem;
    @FXML private MenuItem decreaseFontMenuItem;
    @FXML private MenuItem resetFontMenuItem;

    @FXML private MenuItem connectMenuItem;
    @FXML private MenuItem disconnectMenuItem;
    @FXML private MenuItem createCollabDocMenuItem;
    @FXML private MenuItem joinDocumentMenuItem;
    @FXML private MenuItem leaveDocumentMenuItem;
    @FXML private MenuItem showUsersMenuItem;

    @FXML private ComboBox<String> fontFamilyCombo;
    @FXML private ComboBox<Integer> fontSizeCombo;
    @FXML private ToggleButton boldButton;
    @FXML private ToggleButton italicButton;
    @FXML private ToggleButton underlineButton;
    @FXML private ToggleButton strikethroughButton;
    @FXML private ColorPicker textColorPicker;
    @FXML private ColorPicker highlightColorPicker;
    @FXML private Button requestWriteButton;
    @FXML private Button themeToggleButton;

    private QuillPad mainApp;
    private String currentUser;
    private String currentNoteName;
    private Map<Tab, RichTextEditor> tabEditorMap = new HashMap<>();
    private Map<Tab, Boolean> tabModifiedMap = new HashMap<>();
    private Map<Tab, String> tabFilePathMap = new HashMap<>();
    private Timer autoSaveTimer;
    private int untitledCounter = 1;

    private CollabClient collabClient;
    private boolean isCollaborating = false;
    private boolean isReceivingUpdate = false;
    private final Map<String, String> writeOwnerByDocument = new HashMap<>();
    private final Map<String, String> collabDocNameById = new HashMap<>();
    private final Map<String, String> collabDocOwnerById = new HashMap<>();
    private String activeUsersText = "";

    private static final String[] FONT_FAMILIES = {
        "System", "Arial", "Times New Roman", "Courier New", "Consolas", 
        "Verdana", "Georgia", "Comic Sans MS", "Trebuchet MS", "Lucida Console"
    };
    private static final Integer[] FONT_SIZES = {8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72};

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
        migrateLegacyNullUserNotes();
        if (currentNoteName != null && tabPane != null && tabPane.getTabs().isEmpty()) {
            loadNote(currentNoteName);
        }
    }

    public void setCurrentNote(String noteName) {
        this.currentNoteName = noteName;
        if (currentUser == null || currentUser.isBlank()) {
            return;
        }
        if (noteName != null) {
            loadNote(noteName);
        } else {
            createNewTab(null);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFontCombos();
        setupKeyboardShortcuts();
        setupToolbarListeners();

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                RichTextEditor editor = tabEditorMap.get(newTab);
                if (editor != null) {
                    updateStatus(editor, editor.getCaretPosition());
                }
            }
            refreshWriteAccessUI();
        });

        startAutoSave();

        tabPane.getTabs().addListener((javafx.collections.ListChangeListener.Change<? extends Tab> c) -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    for (Tab removedTab : c.getRemoved()) {
                        tabEditorMap.remove(removedTab);
                        tabModifiedMap.remove(removedTab);
                        tabFilePathMap.remove(removedTab);
                    }
                }
            }
            if (tabPane.getTabs().isEmpty() && mainApp != null) {
                mainApp.showDashboard(currentUser);
            }
        });
    }

    private void setupFontCombos() {
        ObservableList<String> families = FXCollections.observableArrayList(FONT_FAMILIES);
        fontFamilyCombo.setItems(families);
        fontFamilyCombo.setValue(SettingsManager.getFontFamily());

        ObservableList<Integer> sizes = FXCollections.observableArrayList(FONT_SIZES);
        fontSizeCombo.setItems(sizes);
        fontSizeCombo.setValue(SettingsManager.getFontSize());
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
            replaceMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN));
            replaceMenuItem.setOnAction(this::handleReplace);
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

        if (connectMenuItem != null) {
            connectMenuItem.setOnAction(this::handleConnect);
        }
        if (disconnectMenuItem != null) {
            disconnectMenuItem.setOnAction(this::handleDisconnect);
        }
        if (createCollabDocMenuItem != null) {
            createCollabDocMenuItem.setOnAction(this::handleCreateCollabDoc);
        }
        if (joinDocumentMenuItem != null) {
            joinDocumentMenuItem.setOnAction(this::handleJoinDocument);
        }
        if (leaveDocumentMenuItem != null) {
            leaveDocumentMenuItem.setOnAction(this::handleLeaveDocument);
        }
        if (showUsersMenuItem != null) {
            showUsersMenuItem.setOnAction(this::handleShowUsers);
        }
        if (requestWriteButton != null) {
            requestWriteButton.setVisible(false);
            requestWriteButton.setManaged(false);
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
        highlightColorPicker.setOnAction(e -> applyHighlight());
    }

    private void createNewTab(String title) {
        String tabTitle;
        if (title == null || title.isEmpty() || title.equals("Untitled")) {
            tabTitle = "Untitled-" + untitledCounter++;
        } else {
            tabTitle = title;
        }

        Tab tab = new Tab(tabTitle);
        RichTextEditor editor = new RichTextEditor();
        editor.setDocumentName(tabTitle);
        editor.setWrapText(wordWrapMenuItem != null && wordWrapMenuItem.isSelected());
        editor.setFont(Font.font(SettingsManager.getFontFamily(), SettingsManager.getFontSize()));

        tab.setContent(editor);
        tabEditorMap.put(tab, editor);
        tabModifiedMap.put(tab, false);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        setupTabListeners(tab, editor);
    }

    private void setupTabListeners(Tab tab, RichTextEditor editor) {
        editor.getTextArea().caretPositionProperty().addListener((caretObs, oldPos, newPos) -> {
            updateStatus(editor, newPos.intValue());
            if (collabClient != null && collabClient.isConnected() && isCollaborativeTab(tab)) {
                collabClient.sendCursorPosition(newPos.intValue());
            }
        });

        editor.getTextArea().textProperty().addListener((obs, oldText, newText) -> {
            if (isCollaborativeTab(tab) && !hasWriteAccessForTab(tab)) {
                return;
            }
            markTabModified(tab, true);
            updateStatus(editor, editor.getCaretPosition());

            if (collabClient != null && collabClient.isConnected() && isCollaborativeTab(tab)) {
                sendDocumentUpdate(tab);
            }
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

    private void loadNote(String noteName) {
        // Load from user-specific directory
        String userNotesDir = getUserNotesDir();
        File notesDir = new File(userNotesDir);
        File file = null;

        if (notesDir.exists() && notesDir.isDirectory()) {
            File[] matchingFiles = notesDir.listFiles((dir, name) -> {
                int lastDot = name.lastIndexOf('.');
                String nameWithoutExt = lastDot > 0 ? name.substring(0, lastDot) : name;
                return nameWithoutExt.equals(noteName);
            });

            if (matchingFiles != null && matchingFiles.length > 0) {
                file = matchingFiles[0];
            }
        }

        if (file == null) {
            file = new File(userNotesDir, noteName + ".txt");
        }

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }

                if (content.length() > 0 && content.charAt(content.length() - 1) == '\n') {
                    content.setLength(content.length() - 1);
                }

                createNewTab(noteName);
                Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
                RichTextEditor editor = tabEditorMap.get(currentTab);
                editor.setText(content.toString());
                editor.setFont(Font.font(SettingsManager.getFontFamily(), SettingsManager.getFontSize()));
                tabFilePathMap.put(currentTab, file.getAbsolutePath());
                markTabModified(currentTab, false);
                fileStatusLabel.setText("Loaded");
            } catch (IOException e) {
                fileStatusLabel.setText("Error loading file");
                showError("Failed to load file: " + e.getMessage());
            }
        } else {
            createNewTab(noteName);
        }
    }

    private boolean saveTab(Tab tab) {
        RichTextEditor editor = tabEditorMap.get(tab);
        String content = editor.getText();
        String tabTitle = tab.getText().replace("*", "");

        String filePath = tabFilePathMap.get(tab);

        // Handle collaborative documents
        if (filePath != null && filePath.startsWith("collab:")) {
            String docId = filePath.substring(6);
            Document doc = editor.toDocument();
            doc.setDocumentId(docId);
            doc.setDocumentName(tabTitle);
            collabClient.updateDocument(doc);
            markTabModified(tab, false);
            fileStatusLabel.setText("Saved to server: " + tabTitle);
            return true;
        }

        // Regular file save
        if (filePath == null) {
            String userNotesDir = getUserNotesDir();
            File notesDir = new File(userNotesDir);
            if (!notesDir.exists()) {
                notesDir.mkdirs();
            }
            String baseName = sanitizeFileName(tabTitle);
            if (baseName.isBlank()) {
                baseName = "Untitled";
            }
            File target = new File(notesDir, baseName + ".txt");
            filePath = target.getAbsolutePath();
            tab.setText(removeTxtExtension(target.getName()));
            tabFilePathMap.put(tab, filePath);
        }

        File file = new File(filePath);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
            tabFilePathMap.put(tab, file.getAbsolutePath());
            markTabModified(tab, false);
            fileStatusLabel.setText("Saved: " + file.getName());
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
        return "notes/" + userSegment;
    }

    private void migrateLegacyNullUserNotes() {
        if (currentUser == null || currentUser.isBlank()) {
            return;
        }
        File legacyDir = new File("notes/null");
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
        }, 30000, 30000);
    }

    @FXML private void handleNew(ActionEvent event) {
        createNewTab(null);
    }

    @FXML private void handleOpen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        fileChooser.setInitialDirectory(new File("notes"));

        File file = fileChooser.showOpenDialog(tabPane.getScene().getWindow());
        if (file != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }

                String fileName = file.getName().replace(".txt", "");
                createNewTab(fileName);
                Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
                RichTextEditor editor = tabEditorMap.get(currentTab);
                editor.setText(content.toString());
                tabFilePathMap.put(currentTab, file.getAbsolutePath());
                markTabModified(currentTab, false);
            } catch (IOException e) {
                showError("Failed to open file: " + e.getMessage());
            }
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
            TextInputDialog dialog = new TextInputDialog(selectedTab.getText().replace("*", ""));
            dialog.setTitle("Save As");
            dialog.setHeaderText("Enter new file name:");
            dialog.setContentText("Name:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(name -> {
                if (!name.isEmpty()) {
                    selectedTab.setText(name);
                    tabFilePathMap.remove(selectedTab);
                    saveTab(selectedTab);
                }
            });
        }
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
        if (isCollaborating && collabClient != null) {
            collabClient.leaveDocument();
            collabClient.disconnect();
        }

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
                    if (mainApp != null) {
                        mainApp.showDashboard(currentUser);
                    }
                } else if (result.get() == discardButton) {
                    if (mainApp != null) {
                        mainApp.showDashboard(currentUser);
                    }
                }
            }
        } else {
            if (mainApp != null) {
                mainApp.showDashboard(currentUser);
            }
        }
    }

    @FXML private void handleUndo(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.undo();
        }
    }

    @FXML private void handleRedo(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.redo();
        }
    }

    @FXML private void handleCut(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.cut();
        }
    }

    @FXML private void handleCopy(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.copy();
        }
    }

    @FXML private void handlePaste(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.paste();
        }
    }

    @FXML private void handleSelectAll(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.selectAll();
        }
    }

    @FXML private void handleFind(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Find");
            dialog.setHeaderText("Find text:");
            dialog.setContentText("Search for:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(searchText -> {
                String content = editor.getText();
                int index = content.indexOf(searchText);
                if (index >= 0) {
                    editor.selectRange(index, index + searchText.length());
                    editor.getTextArea().requestFocus();
                } else {
                    showInfo("Text not found: " + searchText);
                }
            });
        }
    }

    @FXML private void handleReplace(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Find and Replace");
            dialog.setHeaderText("Replace text:");

            ButtonType replaceAllButtonType = new ButtonType("Replace All", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(replaceAllButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField findField = new TextField();
            findField.setPromptText("Find");
            TextField replaceField = new TextField();
            replaceField.setPromptText("Replace with");

            grid.add(new Label("Find:"), 0, 0);
            grid.add(findField, 1, 0);
            grid.add(new Label("Replace:"), 0, 1);
            grid.add(replaceField, 1, 1);

            dialog.getDialogPane().setContent(grid);

            Platform.runLater(findField::requestFocus);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isPresent() && result.get() == replaceAllButtonType) {
                String findText = findField.getText();
                String replaceText = replaceField.getText();
                if (!findText.isEmpty()) {
                    String content = editor.getText();
                    String newContent = content.replace(findText, replaceText);
                    editor.setText(newContent);
                    showInfo("Replaced all occurrences");
                }
            }
        }
    }

    @FXML private void handleWordWrap(ActionEvent event) {
        boolean wrapText = wordWrapMenuItem.isSelected();
        for (Tab tab : tabPane.getTabs()) {
            RichTextEditor editor = tabEditorMap.get(tab);
            editor.setWrapText(wrapText);
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
                        "• Real-time collaboration\n" +
                        "• Find and Replace\n" +
                        "• And more!\n\n" +
                        "Developed with JavaFX"
        );
        alert.showAndWait();
    }

    @FXML private void handleIncreaseFont(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            String currentStyle = editor.getTextArea().getStyle();
            double currentSize = SettingsManager.getFontSize();
            
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("-fx-font-size: ([0-9.]+)px;");
            java.util.regex.Matcher matcher = pattern.matcher(currentStyle);
            if (matcher.find()) {
                currentSize = Double.parseDouble(matcher.group(1));
            }
            
            double newSize = Math.min(72, currentSize + 2);
            editor.setFont(Font.font(SettingsManager.getFontFamily(), newSize));
            fontSizeCombo.setValue((int) newSize);
            fileStatusLabel.setText("Font size: " + (int)newSize + "px");
        }
    }

    @FXML private void handleDecreaseFont(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            String currentStyle = editor.getTextArea().getStyle();
            double currentSize = SettingsManager.getFontSize();
            
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("-fx-font-size: ([0-9.]+)px;");
            java.util.regex.Matcher matcher = pattern.matcher(currentStyle);
            if (matcher.find()) {
                currentSize = Double.parseDouble(matcher.group(1));
            }
            
            double newSize = Math.max(8, currentSize - 2);
            editor.setFont(Font.font(SettingsManager.getFontFamily(), newSize));
            fontSizeCombo.setValue((int) newSize);
            fileStatusLabel.setText("Font size: " + (int)newSize + "px");
        }
    }

    @FXML private void handleResetFont(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            int savedSize = SettingsManager.getFontSize();
            editor.setFont(Font.font(SettingsManager.getFontFamily(), savedSize));
            fontFamilyCombo.setValue(SettingsManager.getFontFamily());
            fontSizeCombo.setValue(savedSize);
            fileStatusLabel.setText("Font reset");
        }
    }

    private void applyFontFamily() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            String fontFamily = fontFamilyCombo.getValue();
            if (fontFamily != null) {
                editor.applyFontFamilyToSelection(fontFamily);
            }
        }
    }

    private void applyFontSize() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            Integer fontSize = fontSizeCombo.getValue();
            if (fontSize != null) {
                editor.applyFontSizeToSelection(fontSize);
            }
        }
    }

    private void applyBold() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.applyBoldToSelection();
        }
    }

    private void applyItalic() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.applyItalicToSelection();
        }
    }

    private void applyUnderline() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.applyUnderlineToSelection();
        }
    }

    private void applyStrikethrough() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            editor.applyStrikethroughToSelection();
        }
    }

    private void applyTextColor() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
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

    private void applyHighlight() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            Color color = highlightColorPicker.getValue();
            if (color != null) {
                String colorHex = String.format("#%02X%02X%02X", 
                    (int)(color.getRed() * 255),
                    (int)(color.getGreen() * 255),
                    (int)(color.getBlue() * 255));
                editor.applyHighlightToSelection(colorHex);
            }
        }
    }

    @FXML private void handleConnect(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Connect to Collaboration Server");
        dialog.setHeaderText("Enter server details:");
        dialog.initOwner(tabPane.getScene().getWindow());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField hostField = new TextField();
        hostField.setText("localhost");
        TextField portField = new TextField();
        portField.setText("9999");
        TextField userIdField = new TextField();
        userIdField.setText(currentUser != null ? currentUser : "User" + new Random().nextInt(1000));

        grid.add(new Label("Host:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Port:"), 0, 1);
        grid.add(portField, 1, 1);
        grid.add(new Label("User ID:"), 0, 2);
        grid.add(userIdField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String host = hostField.getText();
            int port;
            try {
                port = Integer.parseInt(portField.getText());
            } catch (NumberFormatException e) {
                port = 9999;
            }
            String userId = userIdField.getText();

            connectToServer(host, port, userId);
        }
    }

    private void connectToServer(String host, int port, String userId) {
        collabClient = new CollabClient(host, port);

        collabClient.addMessageHandler(message -> {
            Platform.runLater(() -> handleCollabMessage(message));
        });

        collabClient.addErrorListener(error -> {
            Platform.runLater(() -> showError(error));
        });

        collabClient.addConnectionListener(() -> {
            Platform.runLater(this::updateCollabUI);
        });

        if (collabClient.connect(userId)) {
            isCollaborating = true;
            connectionStatusLabel.setText("Connected");
            connectionStatusLabel.setStyle("-fx-text-fill: #50fa7b;");
            updateCollabUI();
            showInfo("Connected to collaboration server");
        } else {
            showError("Failed to connect to server");
        }
    }

    private void handleCollabMessage(Message message) {
        String senderId = message.getSenderId();
        boolean isFromMe = senderId != null && senderId.equals(collabClient.getUserId());
        
        switch (message.getType()) {
            case DOCUMENT_SYNC:
            case DOCUMENT_UPDATE:
                Document doc = message.getDocument();
                if (doc != null) {
                    String docId = doc.getDocumentId();
                    String docName = doc.getDocumentName();
                    collabDocNameById.put(docId, docName);
                    collabDocOwnerById.put(docId, doc.getOwner());
                    if (doc.getOwner() != null && !doc.getOwner().isBlank()) {
                        writeOwnerByDocument.putIfAbsent(docId, doc.getOwner());
                    }
                    
                    // Find if we already have this document open
                    Tab existingTab = findTabByDocumentId(docId);
                    
                    if (existingTab != null) {
                        // Update existing tab - set flag to prevent loops
                        tabPane.getSelectionModel().select(existingTab);
                        RichTextEditor editor = tabEditorMap.get(existingTab);
                        
                        isReceivingUpdate = true;
                        editor.fromDocument(doc);
                        isReceivingUpdate = false;
                        
                        markTabModified(existingTab, false);
                        
                        // Show notification if change is from another user
                        if (!isFromMe) {
                            fileStatusLabel.setText("Updated by: " + senderId);
                            showNotification("Document Updated", docName + " was updated by " + senderId);
                        } else {
                            fileStatusLabel.setText("Synced: " + docName);
                        }
                    } else {
                        // Create new tab for this document
                        Tab newTab = new Tab(docName);
                        RichTextEditor editor = new RichTextEditor();
                        editor.setDocumentId(docId);
                        editor.setDocumentName(docName);
                        editor.fromDocument(doc);
                        editor.setWrapText(wordWrapMenuItem != null && wordWrapMenuItem.isSelected());
                        editor.setFont(Font.font(SettingsManager.getFontFamily(), SettingsManager.getFontSize()));
                        
                        newTab.setContent(editor);
                        tabEditorMap.put(newTab, editor);
                        tabModifiedMap.put(newTab, false);
                        
                        // Store document ID for collaboration
                        tabFilePathMap.put(newTab, "collab:" + docId);
                        
                        tabPane.getTabs().add(newTab);
                        tabPane.getSelectionModel().select(newTab);
                        
                        setupCollabTabListeners(newTab, editor);
                        
                        if (!isFromMe) {
                            showNotification("New Document", docName + " was shared by " + senderId);
                        }
                    }
                    
                    if (isFromMe) {
                        fileStatusLabel.setText("Synced: " + docName);
                    }
                    refreshWriteAccessUI();
                }
                break;

            case USER_LIST:
                String users = message.getPayload();
                if (users != null && !users.isEmpty()) {
                    activeUsersText = users;
                } else {
                    activeUsersText = "";
                }
                refreshWriteAccessUI();
                break;

            case WRITE_ACCESS_STATUS:
                String lockDocId = message.getDocumentId();
                String payload = message.getPayload();
                String owner = payload;
                String docOwner = null;
                if (payload != null && payload.contains("|")) {
                    String[] parts = payload.split("\\|", 2);
                    owner = parts.length > 0 ? parts[0] : "";
                    docOwner = parts.length > 1 ? parts[1] : "";
                }
                if (lockDocId != null && !lockDocId.isBlank()) {
                    if (owner == null || owner.isBlank()) {
                        writeOwnerByDocument.remove(lockDocId);
                    } else {
                        writeOwnerByDocument.put(lockDocId, owner);
                    }
                    if (docOwner != null && !docOwner.isBlank()) {
                        collabDocOwnerById.put(lockDocId, docOwner);
                    }
                }
                refreshWriteAccessUI();
                break;

            case WRITE_ACCESS_REQUEST:
                String requestDocId = message.getDocumentId();
                String requesterUserId = message.getSenderId();
                if (requestDocId != null && requesterUserId != null &&
                        isCurrentUserOwnerOfDoc(requestDocId)) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Write Access Request");
                    alert.setHeaderText("Grant write access?");
                    String docName = collabDocNameById.getOrDefault(requestDocId, requestDocId);
                    alert.setContentText(requesterUserId + " requested write access for \"" + docName + "\".");
                    ButtonType grantButton = new ButtonType("Grant");
                    ButtonType denyButton = new ButtonType("Deny");
                    alert.getButtonTypes().setAll(grantButton, denyButton);
                    Optional<ButtonType> choice = alert.showAndWait();
                    if (choice.isPresent() && choice.get() == grantButton) {
                        collabClient.grantWriteAccess(requestDocId, requesterUserId);
                    }
                }
                break;

            case ERROR:
                showError(message.getPayload());
                break;

            default:
                break;
        }
    }
    
    private Tab findTabByDocumentId(String docId) {
        for (Tab tab : tabPane.getTabs()) {
            String path = tabFilePathMap.get(tab);
            if (path != null && path.equals("collab:" + docId)) {
                return tab;
            }
        }
        return null;
    }
    
    private void setupCollabTabListeners(Tab tab, RichTextEditor editor) {
        String docId = editor.getDocumentId();
        
        editor.getTextArea().caretPositionProperty().addListener((caretObs, oldPos, newPos) -> {
            updateStatus(editor, newPos.intValue());
            if (collabClient != null && collabClient.isConnected()) {
                collabClient.sendCursorPosition(newPos.intValue());
            }
        });

        editor.getTextArea().textProperty().addListener((obs, oldText, newText) -> {
            // Don't send updates if we're receiving a remote update
            if (isReceivingUpdate) {
                return;
            }
            
            markTabModified(tab, true);
            updateStatus(editor, editor.getCaretPosition());

            if (collabClient != null && collabClient.isConnected() && oldText != null && !oldText.equals(newText)) {
                sendDocumentUpdate(tab);
            }
        });

        tab.setOnCloseRequest(event -> {
            // Leave the collaborative document when closing tab
            if (docId != null && collabClient != null && collabClient.isConnected()) {
                collabClient.leaveDocument();
            }
            
            if (isTabModified(tab)) {
                event.consume();
                if (confirmCloseTab(tab)) {
                    tabPane.getTabs().remove(tab);
                }
            } else {
                tabPane.getTabs().remove(tab);
            }
        });
    }

    private void updateCollabUI() {
        if (collabClient != null && collabClient.isConnected()) {
            connectMenuItem.setDisable(true);
            disconnectMenuItem.setDisable(false);
            createCollabDocMenuItem.setDisable(false);
            joinDocumentMenuItem.setDisable(false);
            showUsersMenuItem.setDisable(false);
            refreshWriteAccessUI();

            if (collabClient.getCurrentDocumentId() != null) {
                leaveDocumentMenuItem.setDisable(false);
            }
        } else {
            connectMenuItem.setDisable(false);
            disconnectMenuItem.setDisable(true);
            createCollabDocMenuItem.setDisable(true);
            joinDocumentMenuItem.setDisable(true);
            leaveDocumentMenuItem.setDisable(true);
            showUsersMenuItem.setDisable(true);
            if (requestWriteButton != null) {
                requestWriteButton.setDisable(true);
                requestWriteButton.setText("Live Edit");
            }
            connectionStatusLabel.setText("Offline");
            connectionStatusLabel.setStyle("-fx-text-fill: #ff5555;");
        }
    }

    @FXML private void handleDisconnect(ActionEvent event) {
        if (collabClient != null) {
            collabClient.leaveDocument();
            collabClient.disconnect();
            isCollaborating = false;
            writeOwnerByDocument.clear();
            collabDocNameById.clear();
            collabDocOwnerById.clear();
            activeUsersText = "";
            connectionStatusLabel.setText("Offline");
            connectionStatusLabel.setStyle("-fx-text-fill: #ff5555;");
            collabStatusLabel.setText("");
            updateCollabUI();
            showInfo("Disconnected from server");
        }
    }

    @FXML private void handleCreateCollabDoc(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Shared Document");
        dialog.setHeaderText("Enter document name:");
        dialog.setContentText("Document Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.isEmpty() && collabClient != null) {
                collabClient.createDocument(name);
            }
        });
    }

    @FXML private void handleJoinDocument(ActionEvent event) {
        if (collabClient == null || !collabClient.isConnected()) {
            showError("Not connected to server");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Join Document");
        dialog.setHeaderText("Select a shared document");
        dialog.getDialogPane().setPrefSize(780, 520);

        ObservableList<String> docsModel = FXCollections.observableArrayList(collabClient.getDocumentList());
        ListView<String> listView = new ListView<>(docsModel);
        listView.setPrefSize(740, 420);

        Label help = new Label("Tip: Select a document then use Join or Delete.");
        VBox container = new VBox(10, help, listView);
        container.setPadding(new Insets(8, 4, 4, 4));
        dialog.getDialogPane().setContent(container);

        ButtonType joinButtonType = new ButtonType("Join", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButtonType = new ButtonType("Delete Selected");
        ButtonType refreshButtonType = new ButtonType("Refresh");
        dialog.getDialogPane().getButtonTypes().addAll(joinButtonType, deleteButtonType, refreshButtonType, ButtonType.CANCEL);

        Button joinButton = (Button) dialog.getDialogPane().lookupButton(joinButtonType);
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteButtonType);
        Button refreshButton = (Button) dialog.getDialogPane().lookupButton(refreshButtonType);
        joinButton.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

        refreshButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            docsModel.setAll(collabClient.getDocumentList());
        });

        deleteButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            String docId = extractDocumentId(selected);
            if (docId == null) {
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Document");
            confirm.setHeaderText("Delete selected shared document?");
            confirm.setContentText(selected);
            Optional<ButtonType> confirmation = confirm.showAndWait();
            if (confirmation.isPresent() && confirmation.get() == ButtonType.OK) {
                collabClient.deleteDocument(docId);
                docsModel.remove(selected);
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == joinButtonType) {
            String selected = listView.getSelectionModel().getSelectedItem();
            String docId = extractDocumentId(selected);
            if (docId != null) {
                collabClient.joinDocument(docId);
                leaveDocumentMenuItem.setDisable(false);
                showInfo("Joined document: " + selected);
            }
        }
    }

    private String extractDocumentId(String documentListEntry) {
        if (documentListEntry == null) {
            return null;
        }
        int separator = documentListEntry.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        return documentListEntry.substring(0, separator);
    }

    @FXML private void handleLeaveDocument(ActionEvent event) {
        if (collabClient != null) {
            collabClient.leaveDocument();
            leaveDocumentMenuItem.setDisable(true);
            collabStatusLabel.setText("");
            activeUsersText = "";
            refreshWriteAccessUI();
            showInfo("Left document");
        }
    }

    @FXML private void handleShowUsers(ActionEvent event) {
        if (collabClient != null) {
            List<String> users = collabClient.getActiveUsers();
            String userList = users.isEmpty() ? "No other users" : String.join(", ", users);
            showInfo("Active users: " + userList);
        }
    }

    @FXML private void handleRequestWriteAccess(ActionEvent event) {
        showInfo("Live editing is enabled for everyone in the active shared document.");
    }

    @FXML private void handleThemeToggle(ActionEvent event) {
        ThemeManager.toggleTheme();
        refreshThemeToggleButton();
    }

    private void refreshThemeToggleButton() {
        if (themeToggleButton == null) {
            return;
        }
        ThemeManager.Theme current = ThemeManager.getCurrentTheme();
        themeToggleButton.setText("Theme");
        themeToggleButton.setTooltip(new Tooltip("Theme: " + current.getDisplayName() + " (click to toggle)"));
    }

    private void sendDocumentUpdate(Tab tab) {
        if (collabClient != null && collabClient.isConnected() && tab != null) {
            String docId = getCollabDocumentId(tab);
            if (docId == null) {
                return;
            }

            RichTextEditor editor = tabEditorMap.get(tab);
            if (editor == null) {
                return;
            }

            Document doc = editor.toDocument();
            doc.setDocumentId(docId);
            doc.setDocumentName(tab.getText().replace("*", ""));
            collabClient.updateDocument(doc);
        }
    }

    private String getCollabDocumentId(Tab tab) {
        if (tab == null) {
            return null;
        }
        String filePath = tabFilePathMap.get(tab);
        if (filePath != null && filePath.startsWith("collab:")) {
            return filePath.substring(6);
        }
        return null;
    }

    private String getSelectedCollabDocumentId() {
        return getCollabDocumentId(tabPane.getSelectionModel().getSelectedItem());
    }

    private boolean isCollaborativeTab(Tab tab) {
        return getCollabDocumentId(tab) != null;
    }

    private boolean hasWriteAccessForTab(Tab tab) {
        String docId = getCollabDocumentId(tab);
        return docId == null || hasWriteAccessForDocument(docId);
    }

    private boolean hasWriteAccessForDocument(String docId) {
        return docId != null && collabClient != null && collabClient.isConnected();
    }

    private boolean isCurrentUserOwnerOfDoc(String docId) {
        if (docId == null || collabClient == null) {
            return false;
        }
        String owner = collabDocOwnerById.get(docId);
        String me = collabClient.getUserId();
        return owner != null && me != null && owner.equals(me);
    }

    private void refreshWriteAccessUI() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        String docId = getCollabDocumentId(selectedTab);
        boolean isCollabDoc = docId != null && collabClient != null && collabClient.isConnected();

        if (requestWriteButton != null) {
            requestWriteButton.setDisable(true);
        }

        if (isCollabDoc) {
            String docName = collabDocNameById.getOrDefault(docId, docId);
            String usersText = activeUsersText == null || activeUsersText.isBlank() ? "none" : activeUsersText;
            collabStatusLabel.setText("Active Doc: " + docName + " | Users: " + usersText);

            if (requestWriteButton != null) {
                requestWriteButton.setText("Live Edit");
            }
        } else if (requestWriteButton != null) {
            requestWriteButton.setText("Live Edit");
        }

        if (selectedTab != null) {
            RichTextEditor editor = tabEditorMap.get(selectedTab);
            if (editor != null) {
                editor.getTextArea().setEditable(true);
            }
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showNotification(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
