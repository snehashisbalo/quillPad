package org.openjfx.service;

import org.openjfx.AppConstants;
import org.openjfx.model.Note;
import org.openjfx.model.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class NoteService {

    public List<Note> listNotes(Path userDir, NoteFilter filter, Set<String> starredNotes,
                                NoteTagService tagService, Collection<String> requiredTags) {
        Path source = filter.trashed() ? trashDir(userDir) : userDir;
        if (!Files.isDirectory(source)) {
            return List.of();
        }

        Set<String> candidateNames = (tagService == null || requiredTags == null || requiredTags.isEmpty())
                ? null
                : tagService.filterNoteFileNames(userDir, requiredTags);
        if (candidateNames != null && candidateNames.isEmpty()) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(source)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> candidateNames == null || candidateNames.contains(path.getFileName().toString()))
                    .map(path -> toNote(path, filter.trashed(), starredNotes, tagService, userDir))
                    .filter(note -> !filter.starred() || note.starred())
                    .sorted(Comparator.comparing(Note::lastModified).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Optional<Path> resolve(Path directory, String noteName) {
        if (directory == null || noteName == null || noteName.isBlank() || !Files.isDirectory(directory)) {
            return Optional.empty();
        }

        Path exact = directory.resolve(noteName);
        if (Files.isRegularFile(exact)) {
            return Optional.of(exact);
        }

        Path txt = directory.resolve(noteName + AppConstants.EXT_TXT);
        if (Files.isRegularFile(txt)) {
            return Optional.of(txt);
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> displayName(path).equals(noteName))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path moveToTrash(Path userDir, String noteName) throws IOException {
        Path source = resolve(userDir, noteName)
                .orElseThrow(() -> new IOException("Note not found: " + noteName));
        Files.createDirectories(trashDir(userDir));
        Path target = nextDuplicatePath(trashDir(userDir), source.getFileName().toString());
        return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public Path restoreFromTrash(Path userDir, String noteName) throws IOException {
        Path source = resolve(trashDir(userDir), noteName)
                .orElseThrow(() -> new IOException("Note not found in trash: " + noteName));
        Files.createDirectories(userDir);
        Path target = nextDuplicatePath(userDir, source.getFileName().toString());
        return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void deletePermanently(Path userDir, String noteName) throws IOException {
        Path source = resolve(trashDir(userDir), noteName)
                .orElseThrow(() -> new IOException("Note not found in trash: " + noteName));
        Files.deleteIfExists(source);
    }

    public Path nextDuplicatePath(Path directory, String fileName) throws IOException {
        Files.createDirectories(directory);
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String extension = extensionOf(fileName);
        String baseName = removeExtension(fileName);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + " (" + suffix + ")" + extension);
            suffix++;
        }
        return candidate;
    }

    public Instant lastModified(Path path) {
        try {
            FileTime fileTime = Files.getLastModifiedTime(path);
            return fileTime.toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    public String displayName(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(AppConstants.EXT_TXT)
                ? fileName.substring(0, fileName.length() - AppConstants.EXT_TXT.length())
                : fileName;
    }

    public String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    public String extensionOf(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }

    private Path trashDir(Path userDir) {
        return userDir.resolve(AppConstants.TRASH_DIR);
    }

    private Note toNote(Path path, boolean trashed, Set<String> starredNotes, NoteTagService tagService, Path userDir) {
        String displayName = displayName(path);
        String fileName = path.getFileName().toString();
        boolean starred = starredNotes.contains(displayName) || starredNotes.contains(removeExtension(fileName));
        Set<Tag> tags = tagService == null ? Set.of() : tagService.getTags(userDir, fileName);
        return new Note(displayName, path, lastModified(path), starred, trashed, tags);
    }

    public record NoteFilter(boolean starred, boolean trashed) {
        public static NoteFilter myNotes() {
            return new NoteFilter(false, false);
        }

        public static NoteFilter starredOnly() {
            return new NoteFilter(true, false);
        }

        public static NoteFilter trash() {
            return new NoteFilter(false, true);
        }
    }
}
