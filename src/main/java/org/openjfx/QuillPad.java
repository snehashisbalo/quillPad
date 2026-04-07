package org.openjfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.openjfx.controller.CreditsController;
import org.openjfx.controller.DashboardController;
import org.openjfx.controller.EditorController;
import org.openjfx.controller.LoginController;
import org.openjfx.controller.RegisterController;

import java.io.File;
import java.io.IOException;

public class QuillPad extends Application {

    private Stage primaryStage;
    private Scene loginScene;
    private Scene registerScene;
    private Scene dashboardScene;
    private Scene editorScene;
    private Scene creditsScene;
    private String currentUser;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        stage.setTitle("QuillPad");

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
        loginScene = new Scene(loader.load(), 920, 620);
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

    private void loadRegisterScene() throws IOException {
        if (registerScene == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("register.fxml"));
            registerScene = new Scene(loader.load(), 920, 640);
            ThemeManager.registerScene(registerScene);
            RegisterController controller = loader.getController();
            controller.setMainApp(this);
        }
    }

    private void loadEditorScene(String noteName, File fileToOpen) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("editor.fxml"));
        editorScene = new Scene(loader.load(), 1100, 750);
        ThemeManager.registerScene(editorScene);
        EditorController controller = loader.getController();
        controller.setMainApp(this);
        controller.setCurrentUser(currentUser);
        if (fileToOpen != null) {
            controller.openFile(fileToOpen);
        } else {
            controller.setCurrentNote(noteName);
        }
    }

    private void loadCreditsScene() throws IOException {
        if (creditsScene == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("credits.fxml"));
            creditsScene = new Scene(loader.load(), 1100, 750);
            ThemeManager.registerScene(creditsScene);
            CreditsController controller = loader.getController();
            controller.setMainApp(this);
        }
    }

    public void showLogin() {
        applyScenePreservingWindowState(loginScene);
        currentUser = null;
    }

    public void showRegister() {
        try {
            loadRegisterScene();
            applyScenePreservingWindowState(registerScene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showDashboard(String username) {
        try {
            this.currentUser = username;
            dashboardScene = null;
            loadDashboardScene();
            applyScenePreservingWindowState(dashboardScene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showEditor(String noteName) {
        try {
            loadEditorScene(noteName, null);
            applyScenePreservingWindowState(editorScene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showEditor(File fileToOpen) {
        try {
            loadEditorScene(null, fileToOpen);
            applyScenePreservingWindowState(editorScene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showCredits() {
        try {
            loadCreditsScene();
            applyScenePreservingWindowState(creditsScene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showPreviousScene() {
        showDashboard(currentUser);
    }

    private void applyScenePreservingWindowState(Scene nextScene) {
        if (primaryStage == null || nextScene == null) {
            return;
        }

        if (!primaryStage.isShowing()) {
            primaryStage.setScene(nextScene);
            return;
        }

        boolean wasFullScreen = primaryStage.isFullScreen();
        boolean wasMaximized = primaryStage.isMaximized();
        double width = primaryStage.getWidth();
        double height = primaryStage.getHeight();
        double x = primaryStage.getX();
        double y = primaryStage.getY();

        primaryStage.setScene(nextScene);
        primaryStage.setX(x);
        primaryStage.setY(y);
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);

        Parent root = nextScene.getRoot();
        if (root != null) {
            root.applyCss();
            root.requestLayout();
            root.layout();
        }

        if (wasMaximized && !primaryStage.isMaximized()) {
            primaryStage.setMaximized(true);
        } else if (!wasMaximized && primaryStage.isMaximized()) {
            primaryStage.setMaximized(false);
        }
        if (wasFullScreen && !primaryStage.isFullScreen()) {
            primaryStage.setFullScreen(true);
        } else if (!wasFullScreen && primaryStage.isFullScreen()) {
            primaryStage.setFullScreen(false);
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
