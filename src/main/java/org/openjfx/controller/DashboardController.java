package org.openjfx.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import org.openjfx.QuillPad;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    @FXML
    private Button homeButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Button newProjectButton;

    @FXML
    private ListView<String> recentProjectsList;

    private QuillPad mainApp;
    private File notesDir;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        notesDir = new File("notes");
        if (!notesDir.exists()) {
            notesDir.mkdir();
        }
        loadRecentProjects();
    }

    private void loadRecentProjects() {
        ObservableList<String> notes = FXCollections.observableArrayList();
        if (notesDir.exists() && notesDir.isDirectory()) {
            File[] files = notesDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File file : files) {
                    notes.add(file.getName().replace(".txt", ""));
                }
            }
        }
        recentProjectsList.setItems(notes);
    }

    @FXML
    private void handleHome(ActionEvent event) {
        // Already on home
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        mainApp.showLogin();
    }

    @FXML
    private void handleNewProject(ActionEvent event) {
        mainApp.showEditor(null); // null means new note
    }

    @FXML
    private void handleProjectSelected(MouseEvent event) {
        if (event.getClickCount() == 2) { // Double click
            String selectedNote = recentProjectsList.getSelectionModel().getSelectedItem();
            if (selectedNote != null) {
                mainApp.showEditor(selectedNote);
            }
        }
    }
}