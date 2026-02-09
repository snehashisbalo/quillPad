package org.quillpad.core.explorer;

import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileExplorer extends TreeView<String> {

    private static final Image FOLDER_ICON;
    private static final Image FILE_ICON;

    static {
        FOLDER_ICON = loadIcon("/org/quillpad/icons/folder.png");
        FILE_ICON = loadIcon("/org/quillpad/icons/file.png");
    }

    private Path currentDirectory;
    private final List<FileExplorerListener> listeners = new ArrayList<>();

    public FileExplorer() {
        super();

        setCellFactory(treeView -> new TreeCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    TreeItem<String> treeItem = getTreeItem();
                    boolean isDir = treeItem != null && treeItem.getChildren().size() > 0;
                    setGraphic(isDir ? new ImageView(FOLDER_ICON) : new ImageView(FILE_ICON));
                }
            }
        });

        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                TreeItem<String> selected = getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null) {
                    Path path = getPathForItem(selected);
                    if (path != null && Files.isRegularFile(path)) {
                        fireFileSelected(path);
                    }
                }
            }
        });

        TreeItem<String> root = new TreeItem<>("Project");
        setRoot(root);
        setShowRoot(true);
    }

    private Path getPathForItem(TreeItem<String> item) {
        List<String> pathParts = new ArrayList<>();
        TreeItem<String> current = item;
        while (current != null && current.getValue() != null) {
            pathParts.add(0, current.getValue());
            current = current.getParent();
        }
        if (currentDirectory == null || pathParts.isEmpty()) {
            return null;
        }
        Path result = currentDirectory;
        for (int i = 1; i < pathParts.size(); i++) {
            result = result.resolve(pathParts.get(i));
        }
        return result;
    }

    public void setRootDirectory(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return;
        }
        this.currentDirectory = path;
        TreeItem<String> root = new TreeItem<>(path.getFileName().toString());
        root.setExpanded(true);
        populateTree(root, path);
        setRoot(root);
    }

    private void populateTree(TreeItem<String> parent, Path dir) {
        try {
            List<Path> directories = new ArrayList<>();
            List<Path> files = new ArrayList<>();

            Files.list(dir).forEach(path -> {
                try {
                    if (Files.isDirectory(path) && !path.getFileName().toString().startsWith(".")) {
                        directories.add(path);
                    } else if (Files.isRegularFile(path)) {
                        files.add(path);
                    }
                } catch (Exception e) {
                }
            });

            directories.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));

            for (Path d : directories) {
                TreeItem<String> child = new TreeItem<>(d.getFileName().toString());
                parent.getChildren().add(child);
            }

            for (Path f : files) {
                TreeItem<String> child = new TreeItem<>(f.getFileName().toString());
                parent.getChildren().add(child);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void refresh() {
        if (currentDirectory != null) {
            setRootDirectory(currentDirectory);
        }
    }

    public void expandAll() {
        expandTree(getRoot());
    }

    private void expandTree(TreeItem<String> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            item.getChildren().forEach(this::expandTree);
        }
    }

    public void collapseAll() {
        collapseTree(getRoot());
    }

    private void collapseTree(TreeItem<String> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(false);
            item.getChildren().forEach(this::collapseTree);
        }
    }

    public void addListener(FileExplorerListener listener) {
        listeners.add(listener);
    }

    private void fireFileSelected(Path path) {
        for (FileExplorerListener listener : listeners) {
            listener.onFileSelected(path);
        }
    }

    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    private static Image loadIcon(String path) {
        try {
            return new Image(FileExplorer.class.getResourceAsStream(path));
        } catch (Exception e) {
            return null;
        }
    }

    public interface FileExplorerListener {
        void onFileSelected(Path path);
    }
}
