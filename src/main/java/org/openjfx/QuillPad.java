package org.openjfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.openjfx.controller.DashboardController;
import org.openjfx.controller.EditorController;
import org.openjfx.controller.LoginController;

import java.io.IOException;

public class QuillPad extends Application {

    private Stage primaryStage;
    private Scene loginScene;
    private Scene dashboardScene;
    private Scene editorScene;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        stage.setTitle("QuillPad");

        // Load login scene
        loadLoginScene();

        stage.setScene(loginScene);
        stage.show();
    }

    private void loadLoginScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
        loginScene = new Scene(loader.load(), 400, 300);
        LoginController controller = loader.getController();
        controller.setMainApp(this);
    }

    private void loadDashboardScene() throws IOException {
        if (dashboardScene == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard.fxml"));
            dashboardScene = new Scene(loader.load(), 800, 600);
            DashboardController controller = loader.getController();
            controller.setMainApp(this);
        }
    }

    private void loadEditorScene(String noteName) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("editor.fxml"));
        editorScene = new Scene(loader.load(), 1000, 700);
        EditorController controller = loader.getController();
        controller.setMainApp(this);
        controller.setCurrentNote(noteName);
    }

    public void showLogin() {
        primaryStage.setScene(loginScene);
    }

    public void showDashboard() {
        try {
            loadDashboardScene();
            primaryStage.setScene(dashboardScene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showEditor(String noteName) {
        try {
            loadEditorScene(noteName);
            primaryStage.setScene(editorScene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}