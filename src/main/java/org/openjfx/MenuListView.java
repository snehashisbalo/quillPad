package org.openjfx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.openjfx.ScreenUtil.getMenuWidth;
import static org.openjfx.ScreenUtil.screenHeight;
import static org.openjfx.TextAreaView.loadFileToTextArea;

public class MenuListView {
    static Map<String, File> nameToFile = new HashMap<>();
    static List<File> ls = new ArrayList<>();
    static File parent = null;

    private static Image getFileIcon(String fname) {
        // Simplified: no icon
        return null;
    }

    int getDistance(File root) {
        if(root == parent || root == null)    return 0;
        return 1 + getDistance(root.getParentFile());
    }

    public TreeItem<File> getNodesForDirectory(File directory) {
        TreeItem<File> root = new TreeItem<File>(directory);
        for(File f : directory.listFiles()) {
            System.out.println("Loading " + f.getName());
            if(f.isDirectory()) { //Then we call the function recursively
                root.getChildren().add(getNodesForDirectory(f));
            } else {
                root.getChildren().add(new TreeItem<File>(f));
            }
        }
        return root;
    }

    private static String pathToString(Path p) {
        if (p == null) {
            return "null";
        } else if (p.getFileName() == null) {
            return p.toString();
        }
        return p.getFileName().toString();
    }

    VBox buildListView(File root) {
        parent = root;

        TreeView<Path> listView = new TreeView<>(new TreeItem<>());
        Arrays.stream(Objects.requireNonNull(root.listFiles())).forEach(file -> {
            try {
                nameToFile.put(file.getName(), file);
                ls.add(file);
            } catch (Exception e) {
            }
        });
        listView.setEditable(true);
        listView.setMinHeight(screenHeight);

        //Creating the layout
        VBox layout = new VBox();
        layout.getChildren().addAll(listView);
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setShowRoot(false);
        listView.setCellFactory(LazyTreeCell.forTreeView("Loading...", MenuListView::pathToString));
        TreeViewUtils.installSelectionBugWorkaround(listView);

        for (File f: Objects.requireNonNull(root.listFiles())) {
            listView.getRoot().getChildren().add(f.isDirectory()
                    ? new LoadingTreeItem<Path>(f.toPath(), new DirectoryLoader(f))
                    : new TreeItem<>(f.toPath()));
        }

//        listView.setCellFactory(lv -> new TreeCell<File>() {
//            private TextField textField = new TextField() ;
//
//            {
//                textField.setOnAction(e -> {
//                    commitEdit(getItem());
//                });
//                textField.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
//                    if (e.getCode() == KeyCode.ESCAPE) {
//                        cancelEdit();
//                    }
//                });
//            }
//
//            @Override
//            protected void updateItem(File person, boolean empty) {
//                super.updateItem(person, empty);
//                if (empty) {
//                    setText(null);
//                    setGraphic(null);
//                } else if (isEditing()) {
//                    textField.setText(person.getName());
//                    setText(null);
//                    setGraphic(textField);
//                } else {
//                    Image fxImage = getFileIcon(person.getName());
//                    ImageView imageView = new ImageView(fxImage);
//
//                    VBox vb = new VBox(imageView);
////                    vb.setPadding(new Insets(0, 0, 0, 10));
//                    int distance = getDistance(person.getParentFile());
//                    System.out.println(distance);
//                    VBox.setMargin( vb, new Insets( 0, 0, 0, 10 * distance ) );
//                    setGraphic(vb);
//                    setText(person.getName());
//                }
//            }
//
//            @Override
//            public void startEdit() {
//                super.startEdit();
//                textField.setText(getItem().getName());
//                setText(null);
//                setGraphic(textField);
//                textField.selectAll();
//                textField.requestFocus();
//            }
//
//            @Override
//            public void cancelEdit() {
//                super.cancelEdit();
//                setText(getItem().getName());
//                setGraphic(null);
//            }
//
//            @Override
//            public void commitEdit(File person) {
//                super.commitEdit(person);
//                Path source = person.toPath();
//                try {
//                    Files.move(source, source.resolveSibling(textField.getText()));
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//                setText(textField.getText());
//                setGraphic(null);
//            }
//        });

        listView.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                System.out.println("clicked on " + listView.getSelectionModel().getSelectedItem());
                File selectedFile = listView.getSelectionModel().getSelectedItem().getValue().toFile();

                if(selectedFile.isDirectory()) {
//                    System.out.println("is a directory");
//                    int idx = ls.indexOf(selectedFile);
//                    List<File> tlist = new ArrayList<>();
//
//                    Arrays.stream(Objects.requireNonNull(selectedFile.listFiles())).sequential().forEach(file -> {
//                        if(!nameToFile.containsKey(file.getName())) {
//                            nameToFile.put(file.getName(), file);
//                        }
//                        tlist.add(file);
//                    });
//
//                    if(ls.containsAll(tlist)) {
//                        System.out.println("exist already");
//                        ls.removeAll(tlist);
//                    } else {
//                        System.out.println("adding ");
//                        ls.addAll(idx + 1, tlist);
//                    }
//                    listView.setItems(FXCollections.observableList(ls));
                } else {
                    loadFileToTextArea(selectedFile);
                }
            }
        });
        return layout;
    }
}