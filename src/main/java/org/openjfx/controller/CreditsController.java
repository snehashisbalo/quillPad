package org.openjfx.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import org.openjfx.QuillPad;

public class CreditsController {

    @FXML private Button backButton;

    private QuillPad mainApp;

    public void setMainApp(QuillPad mainApp) {
        this.mainApp = mainApp;
    }

    @FXML
    private void handleBack(ActionEvent event) {
        if (mainApp != null) {
            mainApp.showPreviousScene();
        }
    }
}
