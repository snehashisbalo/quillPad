package org.openjfx;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.util.Callback;
import java.nio.file.Path;

public class LazyTreeCell {

    public static Callback<TreeView<Path>, TreeCell<Path>> forTreeView(String loadingText, Callback<Path, String> stringConverter) {
        return treeView -> new TreeCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(stringConverter.call(item));
                }
            }
        };
    }
}