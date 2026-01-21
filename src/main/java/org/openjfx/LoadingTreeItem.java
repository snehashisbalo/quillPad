package org.openjfx;

import javafx.scene.control.TreeItem;
import java.util.concurrent.Callable;
import java.util.List;

public class LoadingTreeItem<T> extends TreeItem<T> {

    public LoadingTreeItem(T value) {
        super(value);
    }

    public LoadingTreeItem(T value, TreeItem<T>... children) {
        super(value, null);
        getChildren().addAll(children);
    }

    public LoadingTreeItem(T value, Callable<List<? extends TreeItem<T>>> loader) {
        super(value);
        // For simplicity, not implementing lazy loading here
        // Assume loader is called elsewhere
    }

    public static <T> javafx.event.EventType<javafx.scene.control.TreeItem.TreeModificationEvent<T>> preAddLoadedChildrenEvent() {
        return TreeItem.childrenModificationEvent();
    }

    public static <T> javafx.event.EventType<javafx.scene.control.TreeItem.TreeModificationEvent<T>> postAddLoadedChildrenEvent() {
        return TreeItem.childrenModificationEvent();
    }
}