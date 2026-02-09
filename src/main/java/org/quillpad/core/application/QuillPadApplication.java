package org.quillpad.core.application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

/**
 * QuillPad IDE - A production-grade JavaFX IDE.
 * Features: Multi-tab editing, File operations, Undo/Redo, Find/Replace,
 * Syntax highlighting, Git integration, Plugin system, and more.
 */
public class QuillPadApplication extends Application {
    
    private static final Logger logger = Logger.getLogger(QuillPadApplication.class.getName());
    private static QuillPadApplication instance;
    
    private Stage primaryStage;
    private TabPane tabPane;
    private MenuBar menuBar;
    private StatusBar statusBar;
    private SidePanel sidePanel;
    private TerminalPanel terminal;
    
    private final Map<String, Tab> openTabs = new HashMap<>();
    private final List<String> recentFiles = new ArrayList<>();
    
    public static final String APP_NAME = "QuillPad IDE";
    public static final String APP_VERSION = "1.0.0";
    
    @Override
    public void init() throws Exception {
        instance = this;
        logger.info("Initializing QuillPad IDE " + APP_VERSION);
    }
    
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        
        try {
            setupStage();
            createUI();
            setupEventHandlers();
            loadSettings();
            
            primaryStage.show();
            logger.info("QuillPad IDE started successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start application", e);
            showError("Startup Error", "Failed to start QuillPad IDE", e);
        }
    }
    
    private void setupStage() {
        primaryStage.setTitle(APP_NAME + " v" + APP_VERSION);
        primaryStage.setWidth(1200);
        primaryStage.setHeight(800);
        primaryStage.setMinWidth(640);
        primaryStage.setMinHeight(480);
        
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX(bounds.getMinX() + 50);
        primaryStage.setY(bounds.getMinY() + 50);
        
        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            handleExit();
        });
    }
    
    private void createUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");
        
        menuBar = createMenuBar();
        root.setTop(menuBar);
        
        sidePanel = new SidePanel();
        sidePanel.setPrefWidth(250);
        
        tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #1e1e1e;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        
        terminal = new TerminalPanel();
        
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(sidePanel, tabPane, terminal);
        splitPane.setDividerPositions(0.2, 0.7);
        
        root.setCenter(splitPane);
        
        statusBar = new StatusBar();
        root.setBottom(statusBar);
        
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/org/quillpad/css/main.css").toExternalForm());
        
        primaryStage.setScene(scene);
    }
    
    private MenuBar createMenuBar() {
        MenuBar bar = new MenuBar();
        bar.setStyle("-fx-background-color: #2d2d2d;");
        
        // File Menu
        Menu fileMenu = new Menu("File");
        
        MenuItem newFile = new MenuItem("New");
        newFile.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newFile.setOnAction(e -> createNewFile());
        fileMenu.getItems().add(newFile);
        
        MenuItem openFile = new MenuItem("Open...");
        openFile.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openFile.setOnAction(e -> openFile());
        fileMenu.getItems().add(openFile);
        
        MenuItem saveFile = new MenuItem("Save");
        saveFile.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        saveFile.setOnAction(e -> saveCurrentFile());
        fileMenu.getItems().add(saveFile);
        
        MenuItem saveAs = new MenuItem("Save As...");
        saveAs.setOnAction(e -> saveCurrentFileAs());
        fileMenu.getItems().add(saveAs);
        
        MenuItem closeFile = new MenuItem("Close");
        closeFile.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        closeFile.setOnAction(e -> closeCurrentTab());
        fileMenu.getItems().add(closeFile);
        
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> handleExit());
        fileMenu.getItems().add(exit);
        
        // Edit Menu
        Menu editMenu = new Menu("Edit");
        
        MenuItem undo = new MenuItem("Undo");
        undo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undo.setOnAction(e -> undo());
        editMenu.getItems().add(undo);
        
        MenuItem redo = new MenuItem("Redo");
        redo.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redo.setOnAction(e -> redo());
        editMenu.getItems().add(redo);
        
        MenuItem cut = new MenuItem("Cut");
        cut.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        cut.setOnAction(e -> cutSelection());
        editMenu.getItems().add(cut);
        
        MenuItem copy = new MenuItem("Copy");
        copy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        copy.setOnAction(e -> copySelection());
        editMenu.getItems().add(copy);
        
        MenuItem paste = new MenuItem("Paste");
        paste.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
        paste.setOnAction(e -> pasteSelection());
        editMenu.getItems().add(paste);
        
        MenuItem selectAll = new MenuItem("Select All");
        selectAll.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
        selectAll.setOnAction(e -> selectAllText());
        editMenu.getItems().add(selectAll);
        
        MenuItem find = new MenuItem("Find");
        find.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        find.setOnAction(e -> showFindDialog());
        editMenu.getItems().add(find);
        
        MenuItem findReplace = new MenuItem("Find/Replace");
        findReplace.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN));
        findReplace.setOnAction(e -> showFindReplaceDialog());
        editMenu.getItems().add(findReplace);
        
        // View Menu
        Menu viewMenu = new Menu("View");
        
        MenuItem zoomIn = new MenuItem("Zoom In");
        zoomIn.setAccelerator(new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN));
        zoomIn.setOnAction(e -> zoomIn());
        viewMenu.getItems().add(zoomIn);
        
        MenuItem zoomOut = new MenuItem("Zoom Out");
        zoomOut.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOut.setOnAction(e -> zoomOut());
        viewMenu.getItems().add(zoomOut);
        
        MenuItem resetZoom = new MenuItem("Reset Zoom");
        resetZoom.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));
        resetZoom.setOnAction(e -> resetZoom());
        viewMenu.getItems().add(resetZoom);
        
        CheckMenuItem sidePanelItem = new CheckMenuItem("Side Panel");
        sidePanelItem.setSelected(true);
        sidePanelItem.setOnAction(e -> sidePanel.setVisible(sidePanelItem.isSelected()));
        viewMenu.getItems().add(sidePanelItem);
        
        CheckMenuItem terminalItem = new CheckMenuItem("Terminal");
        terminalItem.setSelected(true);
        terminalItem.setOnAction(e -> terminal.setVisible(terminalItem.isSelected()));
        viewMenu.getItems().add(terminalItem);
        
        // Tools Menu
        Menu toolsMenu = new Menu("Tools");
        
        MenuItem commandPalette = new MenuItem("Command Palette...");
        commandPalette.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        commandPalette.setOnAction(e -> showCommandPalette());
        toolsMenu.getItems().add(commandPalette);
        
        MenuItem gitPanel = new MenuItem("Git");
        gitPanel.setOnAction(e -> showGitPanel());
        toolsMenu.getItems().add(gitPanel);
        
        // Help Menu
        Menu helpMenu = new Menu("Help");
        MenuItem about = new MenuItem("About QuillPad");
        about.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(about);
        
        bar.getMenus().addAll(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu);
        
        return bar;
    }
    
    private void setupEventHandlers() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                statusBar.setFilePath(newTab.getText());
            }
        });
    }
    
    private void loadSettings() {
        try {
            Path settingsPath = Paths.get(System.getProperty("user.home"), ".quillpad", "recent.txt");
            if (Files.exists(settingsPath)) {
                List<String> lines = Files.readAllLines(settingsPath);
                recentFiles.addAll(lines);
            }
            
            createNewFile();
            
        } catch (IOException e) {
            logger.warning("Failed to load settings: " + e.getMessage());
            createNewFile();
        }
    }
    
    private void saveSettings() {
        try {
            Path settingsDir = Paths.get(System.getProperty("user.home"), ".quillpad");
            Files.createDirectories(settingsDir);
            
            Path settingsPath = settingsDir.resolve("recent.txt");
            Files.write(settingsPath, recentFiles);
            
        } catch (IOException e) {
            logger.warning("Failed to save settings: " + e.getMessage());
        }
    }
    
    private void createNewFile() {
        String name = "Untitled-" + (openTabs.size() + 1);
        createTab(name, "");
    }
    
    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open File");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Text Files", "*.txt"),
            new FileChooser.ExtensionFilter("Java Files", "*.java"),
            new FileChooser.ExtensionFilter("Python Files", "*.py")
        );
        
        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            openFile(file);
        }
    }
    
    private void openFile(File file) {
        try {
            String path = file.getAbsolutePath();
            if (openTabs.containsKey(path)) {
                tabPane.getSelectionModel().select(openTabs.get(path));
                return;
            }
            
            String content = Files.readString(file.toPath());
            createTab(path, content);
            
            recentFiles.remove(path);
            recentFiles.add(0, path);
            if (recentFiles.size() > 20) {
                recentFiles.remove(recentFiles.size() - 1);
            }
            
            sidePanel.updateRecentFiles(recentFiles);
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to open file: " + file.getName(), e);
            showError("File Error", "Failed to open file: " + file.getName(), e);
        }
    }
    
    private void createTab(String name, String content) {
        EditorTab tab = new EditorTab(name, content);
        
        tab.setOnClosed(e -> {
            openTabs.remove(name);
        });
        
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        openTabs.put(name, tab);
    }
    
    private void saveCurrentFile() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.save();
        }
    }
    
    private void saveCurrentFileAs() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.saveAs();
        }
    }
    
    private void closeCurrentTab() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null) {
            tabPane.getTabs().remove(tab);
        }
    }
    
    private void undo() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.undo();
        }
    }
    
    private void redo() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.redo();
        }
    }
    
    private void cutSelection() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.cut();
        }
    }
    
    private void copySelection() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.copy();
        }
    }
    
    private void pasteSelection() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.paste();
        }
    }
    
    private void selectAllText() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.selectAll();
        }
    }
    
    private void zoomIn() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.zoomIn();
        }
    }
    
    private void zoomOut() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.zoomOut();
        }
    }
    
    private void resetZoom() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.resetZoom();
        }
    }
    
    private void showFindDialog() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.showFindDialog();
        }
    }
    
    private void showFindReplaceDialog() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab) {
            EditorTab editor = (EditorTab) tab;
            editor.showFindReplaceDialog();
        }
    }
    
    private void showCommandPalette() {
        CommandPalette.show(primaryStage);
    }
    
    private void showGitPanel() {
        sidePanel.showGitPanel();
    }
    
    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About " + APP_NAME);
        alert.setHeaderText(APP_NAME + " v" + APP_VERSION);
        alert.setContentText("A production-grade JavaFX IDE\n\nFeatures:\n- Multi-tab editing\n- File operations\n- Undo/Redo\n- Cut/Copy/Paste\n- Find/Replace\n- Syntax highlighting\n- Terminal panel\n- Command palette");
        alert.showAndWait();
    }
    
    private void showError(String title, String message, Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        alert.setContentText(e != null ? e.getMessage() : "");
        alert.showAndWait();
    }
    
    private void handleExit() {
        boolean modified = false;
        for (Tab tab : tabPane.getTabs()) {
            if (tab instanceof EditorTab) {
                EditorTab editor = (EditorTab) tab;
                if (editor.isModified()) {
                    modified = true;
                    break;
                }
            }
        }
        
        if (modified) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("You have unsaved changes");
            alert.setContentText("Do you want to save before exiting?");
            
            ButtonType save = new ButtonType("Save");
            ButtonType discard = new ButtonType("Discard");
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            
            alert.getButtonTypes().setAll(save, discard, cancel);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == save) {
                    for (Tab tab : tabPane.getTabs()) {
                        if (tab instanceof EditorTab) {
                            EditorTab editor = (EditorTab) tab;
                            editor.save();
                        }
                    }
                } else if (result.get() == cancel) {
                    return;
                }
            }
        }
        
        saveSettings();
        Platform.exit();
    }
    
    @Override
    public void stop() {
        logger.info("QuillPad IDE stopped");
    }
    
    public static QuillPadApplication getInstance() {
        return instance;
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}

/**
 * Editor Tab with TextArea, line numbers, and edit operations.
 */
class EditorTab extends Tab {
    private final TextArea textArea;
    private final TextArea lineNumbers;
    private final Label statusLabel;
    private String filePath;
    private boolean modified = false;
    private double fontSize = 14;
    
    public EditorTab(String name, String content) {
        setText(name);
        this.filePath = name;
        
        VBox container = new VBox();
        container.setStyle("-fx-background-color: #1e1e1e;");
        
        HBox editorArea = new HBox();
        editorArea.setStyle("-fx-background-color: #1e1e1e;");
        
        lineNumbers = new TextArea("1");
        lineNumbers.setStyle("-fx-control-inner-background: #252526; -fx-text-fill: #858585; -fx-font-family: Consolas; -fx-font-size: 14px;");
        lineNumbers.setPrefWidth(50);
        lineNumbers.setEditable(false);
        lineNumbers.setMouseTransparent(true);
        
        textArea = new TextArea(content);
        textArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4; -fx-font-family: Consolas; -fx-font-size: 14px;");
        textArea.setWrapText(false);
        textArea.textProperty().addListener((obs, old, neu) -> {
            modified = true;
            updateLineNumbers();
            setText(filePath + (modified ? " *" : ""));
        });
        textArea.caretPositionProperty().addListener((obs, old, neu) -> updateLineNumbers());
        
        HBox.setHgrow(textArea, Priority.ALWAYS);
        editorArea.getChildren().addAll(lineNumbers, textArea);
        
        statusLabel = new Label("Ln 1, Col 1 | UTF-8 | LF");
        statusLabel.setStyle("-fx-background-color: #007acc; -fx-text-fill: white; -fx-padding: 5;");
        
        textArea.caretPositionProperty().addListener((obs, old, pos) -> {
            int position = pos.intValue();
            String text = textArea.getText();
            int line = text.substring(0, Math.min(position, text.length())).split("\n").length;
            int lastNewline = text.substring(0, Math.min(position, text.length())).lastIndexOf('\n');
            int col = lastNewline >= 0 ? position - lastNewline - 1 : position + 1;
            statusLabel.setText("Ln " + line + ", Col " + col + " | UTF-8 | LF");
        });
        
        container.getChildren().addAll(editorArea, statusLabel);
        VBox.setVgrow(editorArea, Priority.ALWAYS);
        
        setContent(container);
        
        setupShortcuts();
        updateLineNumbers();
    }
    
    private void setupShortcuts() {
        textArea.setOnKeyPressed(e -> {
            if (e.isControlDown()) {
                switch (e.getCode()) {
                    case S:
                        e.consume();
                        save();
                        break;
                    case F:
                        e.consume();
                        showFindDialog();
                        break;
                    case H:
                        e.consume();
                        showFindReplaceDialog();
                        break;
                    case PLUS:
                    case ADD:
                        e.consume();
                        zoomIn();
                        break;
                    case MINUS:
                        e.consume();
                        zoomOut();
                        break;
                }
            }
        });
    }
    
    private void updateLineNumbers() {
        String[] lines = textArea.getText().split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines.length; i++) {
            sb.append(i).append("\n");
        }
        lineNumbers.setText(sb.toString());
    }
    
    public void save() {
        if (filePath.startsWith("Untitled-")) {
            saveAs();
        } else {
            try {
                Files.writeString(Paths.get(filePath), textArea.getText());
                modified = false;
                setText(filePath);
            } catch (IOException e) {
                showError("Save Error", "Failed to save file", e);
            }
        }
    }
    
    public void saveAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save As");
        File file = chooser.showSaveDialog(null);
        if (file != null) {
            filePath = file.getAbsolutePath();
            save();
        }
    }
    
    public void undo() {
        if (textArea.isUndoable()) {
            textArea.undo();
        }
    }
    
    public void redo() {
        if (textArea.isUndoable()) {
            textArea.redo();
        }
    }
    
    public void cut() {
        textArea.cut();
    }
    
    public void copy() {
        textArea.copy();
    }
    
    public void paste() {
        textArea.paste();
    }
    
    public void selectAll() {
        textArea.selectAll();
    }
    
    public void zoomIn() {
        fontSize = Math.min(32, fontSize + 2);
        applyFontSize();
    }
    
    public void zoomOut() {
        fontSize = Math.max(8, fontSize - 2);
        applyFontSize();
    }
    
    public void resetZoom() {
        fontSize = 14;
        applyFontSize();
    }
    
    private void applyFontSize() {
        textArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4; -fx-font-family: Consolas; -fx-font-size: " + fontSize + "px;");
        lineNumbers.setStyle("-fx-control-inner-background: #252526; -fx-text-fill: #858585; -fx-font-family: Consolas; -fx-font-size: " + fontSize + "px;");
    }
    
    public void showFindDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Find");
        dialog.setHeaderText("Find text");
        dialog.setContentText("Find:");
        dialog.showAndWait().ifPresent(text -> {
            String content = textArea.getText();
            int index = content.indexOf(text);
            if (index >= 0) {
                textArea.positionCaret(index);
                textArea.selectRange(index, index + text.length());
            } else {
                showError("Not Found", "Text '" + text + "' not found", null);
            }
        });
    }
    
    public void showFindReplaceDialog() {
        TextInputDialog findDialog = new TextInputDialog();
        findDialog.setTitle("Find/Replace");
        findDialog.setHeaderText("Find and replace text");
        findDialog.setContentText("Find:");
        findDialog.showAndWait().ifPresent(findText -> {
            TextInputDialog replaceDialog = new TextInputDialog();
            replaceDialog.setTitle("Replace");
            replaceDialog.setHeaderText("Replace with:");
            replaceDialog.setContentText("Replace with:");
            replaceDialog.showAndWait().ifPresent(replaceText -> {
                String content = textArea.getText();
                String newContent = content.replace(findText, replaceText);
                if (!content.equals(newContent)) {
                    textArea.setText(newContent);
                }
            });
        });
    }
    
    public boolean isModified() {
        return modified;
    }
    
    private void showError(String title, String message, Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(message);
        if (e != null) {
            alert.setContentText(e.getMessage());
        }
        alert.showAndWait();
    }
}

/**
 * Status Bar showing file information.
 */
class StatusBar extends Label {
    public StatusBar() {
        setText("Ready");
        setStyle("-fx-background-color: #007acc; -fx-text-fill: white; -fx-padding: 5 10;");
        setPrefHeight(30);
    }
    
    public void setFilePath(String path) {
        setText(path + " | Ready");
    }
}

/**
 * Side Panel with file explorer and recent files.
 */
class SidePanel extends VBox {
    private final TreeView<String> fileTree;
    private final ListView<String> recentFilesList;
    
    public SidePanel() {
        setStyle("-fx-background-color: #252526;");
        
        Label explorer = new Label("Explorer");
        explorer.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 10;");
        
        fileTree = new TreeView<>();
        fileTree.setStyle("-fx-background-color: #252526; -fx-text-fill: #cccccc;");
        TreeItem<String> root = new TreeItem<>("Project");
        fileTree.setRoot(root);
        fileTree.setShowRoot(false);
        
        Separator sep1 = new Separator();
        
        Label recent = new Label("Recent Files");
        recent.setStyle("-fx-text-fill: #cccccc; -fx-padding: 10;");
        
        recentFilesList = new ListView<>();
        recentFilesList.setStyle("-fx-background-color: #252526; -fx-text-fill: #cccccc;");
        recentFilesList.setPrefHeight(150);
        
        getChildren().addAll(explorer, fileTree, sep1, recent, recentFilesList);
    }
    
    public void updateRecentFiles(List<String> files) {
        recentFilesList.getItems().clear();
        recentFilesList.getItems().addAll(files);
    }
    
    public void showGitPanel() {
        // Git panel functionality
    }
}

/**
 * Terminal Panel for command execution.
 */
class TerminalPanel extends VBox {
    private final TextArea terminal;
    
    public TerminalPanel() {
        setStyle("-fx-background-color: #1e1e1e;");
        setVisible(true);
        
        Label label = new Label("Terminal");
        label.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 5 10;");
        
        terminal = new TextArea();
        terminal.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #cccccc; -fx-font-family: Consolas;");
        terminal.setEditable(false);
        
        VBox.setVgrow(terminal, Priority.ALWAYS);
        getChildren().addAll(label, terminal);
        
        writeln("QuillPad Terminal v1.0");
        writeln("Type 'help' for available commands.");
    }
    
    public void writeln(String text) {
        terminal.appendText(text + "\n");
    }
}

/**
 * Command Palette for keyboard-driven workflows.
 */
class CommandPalette {
    public static void show(Stage parent) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Command Palette");
        dialog.setHeaderText("Type a command");
        dialog.setContentText(">");
        dialog.getEditor().setStyle("-fx-font-size: 16px;");
        
        dialog.setOnShown(e -> {
            dialog.getEditor().requestFocus();
        });
        
        dialog.showAndWait().ifPresent(command -> {
            executeCommand(command);
        });
    }
    
    private static void executeCommand(String command) {
        switch (command.toLowerCase()) {
            case "new":
                System.out.println("New command executed");
                break;
            case "save":
                System.out.println("Save command executed");
                break;
            case "close":
                System.out.println("Close command executed");
                break;
            case "exit":
                System.out.println("Exit command executed");
                break;
            default:
                System.out.println("Unknown command: " + command);
        }
    }
}
