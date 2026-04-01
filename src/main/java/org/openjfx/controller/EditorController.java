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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import org.openjfx.QuillPad;
import org.openjfx.SettingsManager;
import org.openjfx.ThemeManager;
import org.openjfx.component.RichTextEditor;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.Timer;

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
    @FXML private ColorPicker highlightColorPicker;
    @FXML private Button themeToggleButton;

    private QuillPad mainApp;
    private String currentUser;
    private String currentNoteName;
    private Map<Tab, RichTextEditor> tabEditorMap = new HashMap<>();
    private Map<Tab, Boolean> tabModifiedMap = new HashMap<>();
    private Map<Tab, String> tabFilePathMap = new HashMap<>();
    private Timer autoSaveTimer;
    private int untitledCounter = 1;

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
    }

    private void setupTabListeners(Tab tab, RichTextEditor editor) {
        editor.getTextArea().caretPositionProperty().addListener((caretObs, oldPos, newPos) -> {
            updateStatus(editor, newPos.intValue());
        });

        editor.getTextArea().textProperty().addListener((obs, oldText, newText) -> {
            markTabModified(tab, true);
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

        if (noteName != null && noteName.contains(".")) {
            File direct = new File(userNotesDir, noteName);
            if (direct.exists() && direct.isFile()) {
                file = direct;
            }
        }

        if (file == null && notesDir.exists() && notesDir.isDirectory()) {
            File[] matchingFiles = notesDir.listFiles((dir, name) -> {
                int lastDot = name.lastIndexOf('.');
                String nameWithoutExt = lastDot > 0 ? name.substring(0, lastDot) : name;
                return nameWithoutExt.equals(noteName);
            });

            if (matchingFiles != null && matchingFiles.length > 0) {
                file = matchingFiles[0];
            }
        }

        if (file == null && (noteName == null || !noteName.contains("."))) {
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
                editor.setLanguageFromFileName(file.getName());
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
            String fileName = baseName.matches(".*\\.[A-Za-z0-9]+$") ? baseName : baseName + ".txt";
            File target = new File(notesDir, fileName);
            filePath = target.getAbsolutePath();
            tab.setText(formatTabTitleForFileName(fileName));
            tabFilePathMap.put(tab, filePath);
        }

        File file = new File(filePath);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
            tabFilePathMap.put(tab, file.getAbsolutePath());
            editor.setLanguageFromFileName(file.getName());
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
        }, 30000, 30000);
    }

    @FXML private void handleNew(ActionEvent event) {
        createNewTab(null);
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
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }

                String fileName = file.getName();
                createNewTab(fileName);
                Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
                RichTextEditor editor = tabEditorMap.get(currentTab);
                editor.setText(content.toString());
                editor.setLanguageFromFileName(file.getName());
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
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Save As");
            dialog.setHeaderText("Choose file name and extension");

            ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            String currentTitle = selectedTab.getText().replace("*", "");
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
            if (result.isPresent() && result.get() == saveButtonType) {
                String name = nameField.getText() == null ? "" : nameField.getText().trim();
                String ext = extensionCombo.getValue() == null ? ".txt" : extensionCombo.getValue().trim();

                if (!name.isEmpty()) {
                    if (ext.isEmpty()) {
                        ext = ".txt";
                    } else if (!ext.startsWith(".")) {
                        ext = "." + ext;
                    }

                    selectedTab.setText(formatTabTitleForFileName(name + ext));
                    tabFilePathMap.remove(selectedTab);
                    saveTab(selectedTab);
                }
            }
        }
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
        themeToggleButton.setText("Theme");
        themeToggleButton.setTooltip(new Tooltip("Theme: " + current.getDisplayName() + " (click to toggle)"));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
