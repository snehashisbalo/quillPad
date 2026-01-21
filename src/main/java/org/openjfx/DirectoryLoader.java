package org.openjfx;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.scene.control.TreeItem;

public class DirectoryLoader implements Callable<List<? extends TreeItem<Path>>> {

    private static final Comparator<File> COMPARATOR = (left, right) -> {
        boolean leftIsDir = left.isDirectory();
        if (leftIsDir ^ right.isDirectory()) {
            return leftIsDir ? -1 : 1;
        }
        return left.compareTo(right);
    };

    private final File directory;

    public DirectoryLoader(File directory) {
        this.directory = directory;
    }

    @Override
    public List<? extends TreeItem<Path>> call() throws Exception {
        if(directory == null || directory.listFiles() == null)   return Collections.emptyList();
        try (Stream<File> stream = Arrays.stream(directory.listFiles())) {
            return stream.sorted(COMPARATOR)
                    .map(this::toTreeItem)
                    .collect(Collectors.toList());
        }
    }

    private TreeItem<Path> toTreeItem(File path) {
        return path.isDirectory()
                ? new LoadingTreeItem<Path>(path.toPath(), new DirectoryLoader(path))
                : new TreeItem<>(path.toPath());
    }

}