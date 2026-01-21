package org.openjfx;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.layout.*;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import java.io.*;

import static org.openjfx.ScreenUtil.*;
import static org.openjfx.TextAreaView.textArea;

public class HelloApplication extends Application {
    private MenuListView menuListView = new MenuListView();
    enum TAB {
        PROJECT ("Project"),
        BOOKMARK ("Bookmark"),
        STRUCTURE ("Structure");

        private String value;
        TAB(String value){
            this.value = value;
        }
    }
    DirectoryChooser directoryChooser = new DirectoryChooser();

    private Tab projectTab;
    private Tab bookmarkTab;
    private Tab structureTab;

    public MenuBar buildMenuBar(Stage stage) {
        // create a menu
        Menu file = new Menu("File");

        // create menuitems
        MenuItem m1 = new MenuItem("New");
        MenuItem m2 = new MenuItem("Open");
        m2.setOnAction(e -> {
            File selectedDirectory = directoryChooser.showDialog(stage);
            if (selectedDirectory != null) {
                projectTab.setContent(menuListView.buildListView(selectedDirectory));
            }
        });

        MenuItem m3 = new MenuItem("Recent Projects");
        MenuItem m4 = new MenuItem("Close Projects");

        // add menu items to menu
        file.getItems().add(m1);
        file.getItems().add(m2);
        file.getItems().add(m3);
        file.getItems().add(m4);

        // create a menu
        Menu edit = new Menu("Edit");
        MenuItem e1 = new MenuItem("Edit");
        edit.getItems().add(e1);

        // create a menu
        Menu view = new Menu("View");
        MenuItem v1 = new MenuItem("View");
        view.getItems().add(v1);

        // create a menubar
        MenuBar mb = new MenuBar();
        // add menu to menubar
        mb.getMenus().add(file);
        mb.getMenus().add(edit);
        mb.getMenus().add(view);

        return mb;
    }
    EventHandler<Event> replaceBackgroundColorHandler = event -> {
        Tab currentTab = (Tab) event.getTarget();
        System.out.println(currentTab.getText() + currentTab.isSelected());
    };
    public Tab buildTab(TAB tabType) {
        Tab tab = new Tab();
        tab.setClosable(false);
                                
        tab.setText(tabType.value);
        switch (tabType) {
            case PROJECT:
                tab.setOnSelectionChanged(replaceBackgroundColorHandler);
                break;
            case BOOKMARK:
                tab.setOnSelectionChanged(replaceBackgroundColorHandler);
                break;
            case STRUCTURE:
                tab.setOnSelectionChanged(replaceBackgroundColorHandler);
                break;
        }
        return tab;
    }

    TabPane tabPane = new TabPane();
    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("QuillPad");

        VBox vb = new VBox(buildMenuBar(stage));
        tabPane.setSide(Side.LEFT);

        projectTab = buildTab(TAB.PROJECT);
        bookmarkTab = buildTab(TAB.BOOKMARK);
        structureTab = buildTab(TAB.STRUCTURE);

        tabPane.getTabs().add(projectTab);
        tabPane.getTabs().add(bookmarkTab);
        tabPane.getTabs().add(structureTab);

        SplitPane leftRightSplitPane = new SplitPane();
        leftRightSplitPane.minHeight(screenHeight);
        leftRightSplitPane.minWidth(screenWidth);
        stage.showingProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    leftRightSplitPane.setDividerPositions(0.1);
                    observable.removeListener(this);
                }
            }
        });

        tabPane.setMinWidth(getMenuWidth());
        tabPane.maxWidth(screenWidth);
        leftRightSplitPane.getItems().add(tabPane);
        leftRightSplitPane.getItems().add(textArea);

        vb.getChildren().add(leftRightSplitPane);
        VBox.setVgrow(leftRightSplitPane, Priority.ALWAYS);
        VBox.setVgrow(vb, Priority.ALWAYS);
        Scene scene = new Scene(vb);

        stage.setScene(scene);

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}