package org.openjfx.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import org.openjfx.QuillPad;

import java.io.*;
import java.net.URL;
import java.util.ResourceBundle;

public class EditorController implements Initializable {

    @FXML
    private TabPane tabPane;

    @FXML
    private Label lineLabel;

    @FXML
    private Label columnLabel;

    @FXML
    private Label fileStatusLabel;

    private QuillPad mainApp;
    private String currentNoteName;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    public void setCurrentNote(String noteName) {
        this.currentNoteName = noteName;
        if (noteName != null) {
            loadNote(noteName);
        } else {
            createNewTab("Untitled");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Add listener to update status on caret move
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                TextArea textArea = (TextArea) newTab.getContent();
                textArea.caretPositionProperty().addListener((caretObs, oldPos, newPos) -> {
                    updateStatus(textArea, newPos.intValue());
                });
                updateStatus(textArea, textArea.getCaretPosition());
            }
        });
    }

    private void updateStatus(TextArea textArea, int caretPosition) {
        String text = textArea.getText();
        int line = text.substring(0, caretPosition).split("\n", -1).length;
        int column = caretPosition - text.lastIndexOf('\n', caretPosition - 1);
        lineLabel.setText("Line: " + line);
        columnLabel.setText("Column: " + column);
    }

    private void createNewTab(String title) {
        Tab tab = new Tab(title);
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);
        tab.setContent(textArea);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    private void loadNote(String noteName) {
        File file = new File("notes/" + noteName + ".txt");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                createNewTab(noteName);
                TextArea textArea = (TextArea) tabPane.getSelectionModel().getSelectedItem().getContent();
                textArea.setText(content.toString());
                fileStatusLabel.setText("Loaded");
            } catch (IOException e) {
                fileStatusLabel.setText("Error loading file");
            }
        } else {
            createNewTab(noteName);
        }
    }

    private void saveNote() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            TextArea textArea = (TextArea) selectedTab.getContent();
            String content = textArea.getText();
            String fileName = selectedTab.getText();
            if (fileName.equals("Untitled")) {
                fileName = "Note" + System.currentTimeMillis();
                selectedTab.setText(fileName);
            }
            File file = new File("notes/" + fileName + ".txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(content);
                fileStatusLabel.setText("Saved");
            } catch (IOException e) {
                fileStatusLabel.setText("Error saving file");
            }
        }
    }

    @FXML
    private void handleNew(ActionEvent event) {
        createNewTab("Untitled");
    }

    @FXML
    private void handleOpen(ActionEvent event) {
        // For now, just create new
        createNewTab("Untitled");
    }

    @FXML
    private void handleSave(ActionEvent event) {
        saveNote();
    }

    @FXML
    private void handleSaveAs(ActionEvent event) {
        // Similar to save
        saveNote();
    }

    @FXML
    private void handleClose(ActionEvent event) {
        mainApp.showDashboard();
    }

    // Other menu handlers can be added similarly
}