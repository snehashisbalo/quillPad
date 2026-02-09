package org.openjfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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
    private String currentUser;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        stage.setTitle("QuillPad - Professional Note Editor");

        try {
            // stage.getIcons().add(new Image(getClass().getResourceAsStream("/icon.png")));
        } catch (Exception e) {
            // Icon not found, continue without it
        }

        // Load login scene
        loadLoginScene();

        // Set minimum size
        stage.setMinWidth(600);
        stage.setMinHeight(400);

        stage.setScene(loginScene);
        stage.show();
    }

    private void loadLoginScene() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
        loginScene = new Scene(loader.load(), 800, 520);
        ThemeManager.registerScene(loginScene);
        LoginController controller = loader.getController();
        controller.setMainApp(this);
    }

    private void loadDashboardScene() throws IOException {
        if (dashboardScene == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard.fxml"));
            dashboardScene = new Scene(loader.load(), 920, 580);
            ThemeManager.registerScene(dashboardScene);
            DashboardController controller = loader.getController();
            controller.setMainApp(this);
            controller.setCurrentUser(currentUser);
        }
    }

    private void loadEditorScene(String noteName) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("editor.fxml"));
        editorScene = new Scene(loader.load(), 1100, 750);
        ThemeManager.registerScene(editorScene);
        EditorController controller = loader.getController();
        controller.setMainApp(this);
        controller.setCurrentNote(noteName);
        controller.setCurrentUser(currentUser);
    }

    public void showLogin() {
        primaryStage.setScene(loginScene);
        primaryStage.setWidth(800);
        primaryStage.setHeight(520);
        currentUser = null;
    }

    public void showDashboard(String username) {
        try {
            this.currentUser = username;
            dashboardScene = null;
            loadDashboardScene();
            primaryStage.setScene(dashboardScene);
            primaryStage.setWidth(920);
            primaryStage.setHeight(580);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showEditor(String noteName) {
        try {
            loadEditorScene(noteName);
            primaryStage.setScene(editorScene);
            primaryStage.setWidth(1100);
            primaryStage.setHeight(750);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}