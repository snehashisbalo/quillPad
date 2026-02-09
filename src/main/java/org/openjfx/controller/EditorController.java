package org.openjfx.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import org.openjfx.QuillPad;
import org.openjfx.SettingsManager;

import java.io.*;
import java.net.URL;
import java.util.*;

public class EditorController implements Initializable {

    @FXML
    private TabPane tabPane;

    @FXML
    private Label lineLabel;

    @FXML
    private Label columnLabel;

    @FXML
    private Label wordCountLabel;

    @FXML
    private Label charCountLabel;

    @FXML
    private Label fileStatusLabel;

    @FXML
    private MenuItem newMenuItem;

    @FXML
    private MenuItem openMenuItem;

    @FXML
    private MenuItem saveMenuItem;

    @FXML
    private MenuItem saveAsMenuItem;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuItem undoMenuItem;

    @FXML
    private MenuItem redoMenuItem;

    @FXML
    private MenuItem cutMenuItem;

    @FXML
    private MenuItem copyMenuItem;

    @FXML
    private MenuItem pasteMenuItem;

    @FXML
    private MenuItem selectAllMenuItem;

    @FXML
    private MenuItem findMenuItem;

    @FXML
    private MenuItem replaceMenuItem;

    @FXML
    private CheckMenuItem wordWrapMenuItem;

    @FXML
    private MenuItem aboutMenuItem;

    @FXML
    private MenuItem increaseFontMenuItem;

    @FXML
    private MenuItem decreaseFontMenuItem;

    @FXML
    private MenuItem resetFontMenuItem;

    private QuillPad mainApp;
    private String currentNoteName;
    private String currentUser;
    private Map<Tab, Boolean> tabModifiedMap = new HashMap<>();
    private Map<Tab, String> tabFilePathMap = new HashMap<>();
    private Map<Tab, Stack<String>> undoStackMap = new HashMap<>();
    private Map<Tab, Stack<String>> redoStackMap = new HashMap<>();
    private Timer autoSaveTimer;
    private int untitledCounter = 1;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    public void setCurrentNote(String noteName) {
        this.currentNoteName = noteName;
        if (noteName != null) {
            loadNote(noteName);
        } else {
            createNewTab(null);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupKeyboardShortcuts();

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                TextArea textArea = (TextArea) newTab.getContent();
                updateStatus(textArea, textArea.getCaretPosition());
            }
        });

        startAutoSave();

        tabPane.getTabs().addListener((javafx.collections.ListChangeListener.Change<? extends Tab> c) -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    for (Tab removedTab : c.getRemoved()) {
                        tabModifiedMap.remove(removedTab);
                        tabFilePathMap.remove(removedTab);
                        undoStackMap.remove(removedTab);
                        redoStackMap.remove(removedTab);
                    }
                }
            }
            // Auto-return to dashboard when no tabs are open
            if (tabPane.getTabs().isEmpty() && mainApp != null) {
                mainApp.showDashboard(currentUser);
            }
        });

    }

    private void setupKeyboardShortcuts() {
        if (newMenuItem != null) {
            newMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
            newMenuItem.setOnAction(this::handleNew);
        }
        if (openMenuItem != null) {
            openMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
            openMenuItem.setOnAction(this::handleOpen);
        }
        if (saveMenuItem != null) {
            saveMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
            saveMenuItem.setOnAction(this::handleSave);
        }
        if (saveAsMenuItem != null) {
            saveAsMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
            saveAsMenuItem.setOnAction(this::handleSaveAs);
        }
        if (closeMenuItem != null) {
            closeMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
            closeMenuItem.setOnAction(this::handleCloseTab);
        }
        if (undoMenuItem != null) {
            undoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
            undoMenuItem.setOnAction(this::handleUndo);
        }
        if (redoMenuItem != null) {
            redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
            redoMenuItem.setOnAction(this::handleRedo);
        }
        if (cutMenuItem != null) {
            cutMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
            cutMenuItem.setOnAction(this::handleCut);
        }
        if (copyMenuItem != null) {
            copyMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
            copyMenuItem.setOnAction(this::handleCopy);
        }
        if (pasteMenuItem != null) {
            pasteMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
            pasteMenuItem.setOnAction(this::handlePaste);
        }
        if (selectAllMenuItem != null) {
            selectAllMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
            selectAllMenuItem.setOnAction(this::handleSelectAll);
        }
        if (findMenuItem != null) {
            findMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
            findMenuItem.setOnAction(this::handleFind);
        }
        if (replaceMenuItem != null) {
            replaceMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN));
            replaceMenuItem.setOnAction(this::handleReplace);
        }
        if (wordWrapMenuItem != null) {
            wordWrapMenuItem.setSelected(true);
            wordWrapMenuItem.setOnAction(this::handleWordWrap);
        }
        if (aboutMenuItem != null) {
            aboutMenuItem.setOnAction(this::handleAbout);
        }
        // Font size shortcuts
        if (increaseFontMenuItem != null) {
            increaseFontMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN));
            increaseFontMenuItem.setOnAction(this::handleIncreaseFont);
        }
        if (decreaseFontMenuItem != null) {
            decreaseFontMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
            decreaseFontMenuItem.setOnAction(this::handleDecreaseFont);
        }
        if (resetFontMenuItem != null) {
            resetFontMenuItem.setOnAction(this::handleResetFont);
        }
    }

    private void setupTab(Tab tab) {
        TextArea textArea = null;

        if (tab.getContent() instanceof TextArea) {
            textArea = (TextArea) tab.getContent();
        } else {
            textArea = new TextArea();
            textArea.setWrapText(wordWrapMenuItem != null && wordWrapMenuItem.isSelected());
            textArea.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-border-color: transparent;");
            // Set default font
            textArea.setFont(Font.font(SettingsManager.getFontFamily(), SettingsManager.getFontSize()));
            tab.setContent(textArea);
        }

        undoStackMap.put(tab, new Stack<>());
        redoStackMap.put(tab, new Stack<>());
        tabModifiedMap.put(tab, false);

        final TextArea finalTextArea = textArea;

        // Add key filter to prevent Ctrl+H from deleting text
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.H && event.isControlDown()) {
                event.consume();
                handleReplace(null);
            }
        });

        // Add font size shortcuts
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.PLUS) {
                    event.consume();
                    handleIncreaseFont(null);
                } else if (event.getCode() == KeyCode.MINUS) {
                    event.consume();
                    handleDecreaseFont(null);
                } else if (event.getCode() == KeyCode.DIGIT0) {
                    event.consume();
                    handleResetFont(null);
                }
            }
        });

        textArea.caretPositionProperty().addListener((caretObs, oldPos, newPos) -> {
            updateStatus(finalTextArea, newPos.intValue());
        });

        textArea.textProperty().addListener((obs, oldText, newText) -> {
            markTabModified(tab, true);
            updateStatus(finalTextArea, finalTextArea.getCaretPosition());

            Stack<String> undoStack = undoStackMap.get(tab);
            if (undoStack != null && oldText != null) {
                undoStack.push(oldText);
                if (undoStack.size() > 100) {
                    undoStack.remove(0);
                }
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

        updateStatus(textArea, 0);
    }

    private void updateStatus(TextArea textArea, int caretPosition) {
        String text = textArea.getText();

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

    private void createNewTab(String title) {
        String tabTitle;
        if (title == null || title.isEmpty() || title.equals("Untitled")) {
            tabTitle = "Untitled-" + untitledCounter++;
        } else {
            tabTitle = title;
        }
        
        Tab tab = new Tab(tabTitle);
        TextArea textArea = new TextArea();
        textArea.setWrapText(wordWrapMenuItem != null && wordWrapMenuItem.isSelected());
        tab.setContent(textArea);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        setupTab(tab);
    }

    private void loadNote(String noteName) {
        // Try to find the file with any extension
        File notesDir = new File("notes");
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
            // Fallback to .txt extension
            file = new File("notes/" + noteName + ".txt");
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
                TextArea textArea = (TextArea) currentTab.getContent();
                textArea.setText(content.toString());
                textArea.setFont(Font.font(SettingsManager.getFontFamily(), SettingsManager.getFontSize()));
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
        TextArea textArea = (TextArea) tab.getContent();
        String content = textArea.getText();
        String tabTitle = tab.getText().replace("*", "");

        String filePath = tabFilePathMap.get(tab);
        
        // If no file path exists (new note), prompt user with FileChooser
        if (filePath == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Note");
            fileChooser.setInitialFileName(tabTitle.startsWith("Untitled") ? "Untitled-1" : tabTitle);
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            
            // Set initial directory
            File notesDir = new File("notes");
            if (!notesDir.exists()) {
                notesDir.mkdir();
            }
            fileChooser.setInitialDirectory(notesDir);
            
            File selectedFile = fileChooser.showSaveDialog(tabPane.getScene().getWindow());
            if (selectedFile == null) {
                // User cancelled
                return false;
            }
            
            filePath = selectedFile.getAbsolutePath();
            // Update tab title with new filename (without extension)
            String newFileName = selectedFile.getName().replace(".txt", "");
            tab.setText(newFileName);
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

    @FXML
    private void handleNew(ActionEvent event) {
        createNewTab(null);
    }

    @FXML
    private void handleOpen(ActionEvent event) {
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
                TextArea textArea = (TextArea) currentTab.getContent();
                textArea.setText(content.toString());
                tabFilePathMap.put(currentTab, file.getAbsolutePath());
                markTabModified(currentTab, false);
            } catch (IOException e) {
                showError("Failed to open file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleSave(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            saveTab(selectedTab);
        }
    }

    @FXML
    private void handleSaveAs(ActionEvent event) {
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

    @FXML
    private void handleCloseTab(ActionEvent event) {
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

    @FXML
    private void handleClose(ActionEvent event) {
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

    @FXML
    private void handleUndo(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            Stack<String> undoStack = undoStackMap.get(selectedTab);
            Stack<String> redoStack = redoStackMap.get(selectedTab);
            TextArea textArea = (TextArea) selectedTab.getContent();

            if (undoStack != null && !undoStack.isEmpty()) {
                redoStack.push(textArea.getText());
                String previousText = undoStack.pop();
                textArea.setText(previousText);
            }
        }
    }

    @FXML
    private void handleRedo(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            Stack<String> redoStack = redoStackMap.get(selectedTab);
            TextArea textArea = (TextArea) selectedTab.getContent();

            if (redoStack != null && !redoStack.isEmpty()) {
                String nextText = redoStack.pop();
                textArea.setText(nextText);
            }
        }
    }

    @FXML
    private void handleCut(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            textArea.cut();
        }
    }

    @FXML
    private void handleCopy(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            textArea.copy();
        }
    }

    @FXML
    private void handlePaste(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            textArea.paste();
        }
    }

    @FXML
    private void handleSelectAll(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            textArea.selectAll();
        }
    }

    @FXML
    private void handleFind(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Find");
            dialog.setHeaderText("Find text:");
            dialog.setContentText("Search for:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(searchText -> {
                String content = textArea.getText();
                int index = content.indexOf(searchText);
                if (index >= 0) {
                    textArea.selectRange(index, index + searchText.length());
                    textArea.requestFocus();
                } else {
                    showInfo("Text not found: " + searchText);
                }
            });
        }
    }

    @FXML
    private void handleReplace(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();

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
                    String content = textArea.getText();
                    String newContent = content.replace(findText, replaceText);
                    textArea.setText(newContent);
                    showInfo("Replaced all occurrences");
                }
            }
        }
    }

    @FXML
    private void handleWordWrap(ActionEvent event) {
        boolean wrapText = wordWrapMenuItem.isSelected();
        for (Tab tab : tabPane.getTabs()) {
            TextArea textArea = (TextArea) tab.getContent();
            textArea.setWrapText(wrapText);
        }
    }

    @FXML
    private void handleAbout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About QuillPad");
        alert.setHeaderText("QuillPad - Professional Note Editor");
        alert.setContentText(
                "Version 1.0\n\n" +
                        "A feature-rich text editor with:\n" +
                        "• Multiple tabs\n" +
                        "• Auto-save\n" +
                        "• Find and Replace\n" +
                        "• Undo/Redo\n" +
                        "• Word count\n" +
                        "• And more!\n\n" +
                        "Developed with JavaFX"
        );
        alert.showAndWait();
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

    @FXML
    private void handleIncreaseFont(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            Font currentFont = textArea.getFont();
            double newSize = Math.min(32, currentFont.getSize() + 2);
            textArea.setFont(Font.font(currentFont.getFamily(), newSize));
            fileStatusLabel.setText("Font size: " + (int)newSize + "px");
        }
    }

    @FXML
    private void handleDecreaseFont(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            Font currentFont = textArea.getFont();
            double newSize = Math.max(8, currentFont.getSize() - 2);
            textArea.setFont(Font.font(currentFont.getFamily(), newSize));
            fileStatusLabel.setText("Font size: " + (int)newSize + "px");
        }
    }

    @FXML
    private void handleResetFont(ActionEvent event) {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            int savedSize = SettingsManager.getFontSize();
            textArea.setFont(Font.font(SettingsManager.getFontFamily(), savedSize));
            fileStatusLabel.setText("Font size reset: " + savedSize + "px");
        }
    }
}
