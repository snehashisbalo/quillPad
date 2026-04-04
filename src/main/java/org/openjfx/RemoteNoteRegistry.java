package org.openjfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class RemoteNoteRegistry {
    private static final String REGISTRY_FILE = ".remote-notes.properties";
    private static final String SEPARATOR = "\u001F";

    private RemoteNoteRegistry() {
    }

    public static synchronized Optional<RemoteBinding> get(Path notesDir, String localFileName) {
        if (notesDir == null || localFileName == null || localFileName.isBlank()) {
            return Optional.empty();
        }
        Properties properties = load(notesDir);
        String raw = properties.getProperty(localFileName);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.split(SEPARATOR, 2);
        String remoteName = parts[0].trim();
        if (remoteName.isBlank()) {
            return Optional.empty();
        }
        String author = parts.length > 1 ? parts[1].trim() : "";
        return Optional.of(new RemoteBinding(remoteName, author));
    }

    public static synchronized void put(Path notesDir, String localFileName, String remoteName, String author) {
        if (notesDir == null || localFileName == null || localFileName.isBlank() || remoteName == null || remoteName.isBlank()) {
            return;
        }
        Properties properties = load(notesDir);
        properties.setProperty(localFileName, remoteName.trim() + SEPARATOR + (author == null ? "" : author.trim()));
        store(notesDir, properties);
    }

    public static synchronized void remove(Path notesDir, String localFileName) {
        if (notesDir == null || localFileName == null || localFileName.isBlank()) {
            return;
        }
        Properties properties = load(notesDir);
        if (properties.remove(localFileName) != null) {
            store(notesDir, properties);
        }
    }

    public static synchronized void rename(Path notesDir, String oldLocalFileName, String newLocalFileName) {
        if (notesDir == null || oldLocalFileName == null || newLocalFileName == null
                || oldLocalFileName.isBlank() || newLocalFileName.isBlank()
                || Objects.equals(oldLocalFileName, newLocalFileName)) {
            return;
        }
        Properties properties = load(notesDir);
        String value = properties.getProperty(oldLocalFileName);
        if (value == null) {
            return;
        }
        properties.remove(oldLocalFileName);
        properties.setProperty(newLocalFileName, value);
        store(notesDir, properties);
    }

    public static synchronized void clear(Path notesDir) {
        if (notesDir == null) {
            return;
        }
        Properties properties = new Properties();
        store(notesDir, properties);
    }

    public static synchronized void removeByRemote(Path notesDir, String remoteName, String author) {
        if (notesDir == null || remoteName == null || remoteName.isBlank()) {
            return;
        }
        Properties properties = load(notesDir);
        boolean changed = false;
        for (String key : new ArrayList<>(properties.stringPropertyNames())) {
            Optional<RemoteBinding> binding = parse(properties.getProperty(key));
            if (binding.isEmpty()) {
                continue;
            }
            RemoteBinding value = binding.get();
            if (remoteName.trim().equals(value.remoteName())
                    && Objects.equals(author == null ? "" : author.trim(), value.author())) {
                properties.remove(key);
                changed = true;
            }
        }
        if (changed) {
            store(notesDir, properties);
        }
    }

    private static Properties load(Path notesDir) {
        Properties properties = new Properties();
        Path path = registryPath(notesDir);
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException ignored) {
        }
        return properties;
    }

    private static void store(Path notesDir, Properties properties) {
        Path path = registryPath(notesDir);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "QuillPad Remote Note Registry");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path registryPath(Path notesDir) {
        return notesDir.resolve(REGISTRY_FILE);
    }

    private static Optional<RemoteBinding> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.split(SEPARATOR, 2);
        String remoteName = parts[0].trim();
        if (remoteName.isBlank()) {
            return Optional.empty();
        }
        String author = parts.length > 1 ? parts[1].trim() : "";
        return Optional.of(new RemoteBinding(remoteName, author));
    }

    public record RemoteBinding(String remoteName, String author) {
    }
}
