package org.openjfx.service;

import org.openjfx.AppConstants;
import org.openjfx.model.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NoteTagService {
    private final TagPool tagPool = TagPool.getInstance();
    private final Map<Path, TagIndex> indexes = new ConcurrentHashMap<>();

    public Set<Tag> getTags(Path userDir, String noteFileName) {
        return indexFor(userDir).tagsFor(noteFileName);
    }

    public Set<Tag> addTag(Path userDir, String noteFileName, String tagName) throws IOException {
        TagIndex index = indexFor(userDir);
        Tag tag = tagPool.intern(tagName);
        index.addTag(noteFileName, tag);
        index.save();
        return index.tagsFor(noteFileName);
    }

    public Set<Tag> removeTag(Path userDir, String noteFileName, String tagName) throws IOException {
        TagIndex index = indexFor(userDir);
        index.removeTag(noteFileName, Tag.normalize(tagName));
        index.save();
        return index.tagsFor(noteFileName);
    }

    public void renameTag(Path userDir, String oldTagName, String newTagName) throws IOException {
        TagIndex index = indexFor(userDir);
        index.renameTag(tagPool.intern(oldTagName), tagPool.intern(newTagName));
        index.save();
    }

    public void deleteTag(Path userDir, String tagName) throws IOException {
        TagIndex index = indexFor(userDir);
        index.deleteTag(Tag.normalize(tagName));
        index.save();
    }

    public void renameNote(Path userDir, String oldFileName, String newFileName) throws IOException {
        TagIndex index = indexFor(userDir);
        index.renameNote(oldFileName, newFileName);
        index.save();
    }

    public void deleteNote(Path userDir, String fileName) throws IOException {
        TagIndex index = indexFor(userDir);
        index.deleteNote(fileName);
        index.save();
    }

    public Set<String> filterNoteFileNames(Path userDir, Collection<String> tagNames) {
        return indexFor(userDir).filterByTags(tagNames);
    }

    public List<Tag> getAllTags(Path userDir) {
        return indexFor(userDir).allTags();
    }

    private TagIndex indexFor(Path userDir) {
        Path normalizedDir = userDir.toAbsolutePath().normalize();
        return indexes.computeIfAbsent(normalizedDir, this::loadIndex);
    }

    private TagIndex loadIndex(Path userDir) {
        TagIndex index = new TagIndex(userDir.resolve(AppConstants.TAGS_FILE));
        index.load();
        return index;
    }

    private final class TagIndex {
        private final Path storagePath;
        private final Map<String, Set<Tag>> noteToTags = new ConcurrentHashMap<>();
        private final Map<Tag, Set<String>> tagToNotes = new ConcurrentHashMap<>();

        private TagIndex(Path storagePath) {
            this.storagePath = storagePath;
        }

        private void load() {
            noteToTags.clear();
            tagToNotes.clear();
            if (!Files.exists(storagePath)) {
                return;
            }

            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(storagePath)) {
                properties.load(inputStream);
            } catch (IOException e) {
                return;
            }

            for (String noteFileName : properties.stringPropertyNames()) {
                String rawValue = properties.getProperty(noteFileName, "");
                if (rawValue.isBlank()) {
                    continue;
                }
                for (String rawTag : rawValue.split(",")) {
                    if (rawTag.isBlank()) {
                        continue;
                    }
                    Tag tag = tagPool.intern(rawTag);
                    noteToTags.computeIfAbsent(noteFileName, ignored -> new LinkedHashSet<>()).add(tag);
                    tagToNotes.computeIfAbsent(tag, ignored -> ConcurrentHashMap.newKeySet()).add(noteFileName);
                }
            }
        }

        private synchronized void save() throws IOException {
            Files.createDirectories(storagePath.getParent());
            Properties properties = new Properties();
            for (Map.Entry<String, Set<Tag>> entry : noteToTags.entrySet()) {
                List<String> tags = entry.getValue().stream()
                        .map(Tag::normalizedName)
                        .sorted()
                        .toList();
                if (!tags.isEmpty()) {
                    properties.setProperty(entry.getKey(), String.join(",", tags));
                }
            }
            try (OutputStream outputStream = Files.newOutputStream(storagePath)) {
                properties.store(outputStream, "QuillPad note tags");
            }
        }

        private synchronized void addTag(String noteFileName, Tag tag) {
            noteToTags.computeIfAbsent(noteFileName, ignored -> new LinkedHashSet<>()).add(tag);
            tagToNotes.computeIfAbsent(tag, ignored -> ConcurrentHashMap.newKeySet()).add(noteFileName);
        }

        private synchronized void removeTag(String noteFileName, String normalizedTagName) {
            Set<Tag> tags = noteToTags.get(noteFileName);
            if (tags == null) {
                return;
            }
            Tag removedTag = null;
            for (Tag tag : new LinkedHashSet<>(tags)) {
                if (tag.normalizedName().equals(normalizedTagName)) {
                    tags.remove(tag);
                    removedTag = tag;
                    break;
                }
            }
            if (tags.isEmpty()) {
                noteToTags.remove(noteFileName);
            }
            if (removedTag == null) {
                return;
            }
            Set<String> noteNames = tagToNotes.get(removedTag);
            if (noteNames == null) {
                return;
            }
            noteNames.remove(noteFileName);
            if (noteNames.isEmpty()) {
                tagToNotes.remove(removedTag);
            }
        }

        private synchronized void renameTag(Tag oldTag, Tag newTag) {
            if (oldTag.equals(newTag)) {
                return;
            }
            Set<String> notes = new LinkedHashSet<>(tagToNotes.getOrDefault(oldTag, Set.of()));
            for (String noteFileName : notes) {
                addTag(noteFileName, newTag);
                removeTag(noteFileName, oldTag.normalizedName());
            }
        }

        private synchronized void deleteTag(String normalizedTagName) {
            List<String> noteNames = noteToTags.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().anyMatch(tag -> tag.normalizedName().equals(normalizedTagName)))
                    .map(Map.Entry::getKey)
                    .toList();
            for (String noteFileName : noteNames) {
                removeTag(noteFileName, normalizedTagName);
            }
        }

        private synchronized void renameNote(String oldFileName, String newFileName) {
            Set<Tag> tags = noteToTags.remove(oldFileName);
            if (tags == null || tags.isEmpty()) {
                return;
            }
            noteToTags.put(newFileName, new LinkedHashSet<>(tags));
            for (Tag tag : tags) {
                Set<String> noteNames = tagToNotes.get(tag);
                if (noteNames != null) {
                    noteNames.remove(oldFileName);
                    noteNames.add(newFileName);
                }
            }
        }

        private synchronized void deleteNote(String fileName) {
            Set<Tag> tags = noteToTags.remove(fileName);
            if (tags == null) {
                return;
            }
            for (Tag tag : tags) {
                Set<String> noteNames = tagToNotes.get(tag);
                if (noteNames == null) {
                    continue;
                }
                noteNames.remove(fileName);
                if (noteNames.isEmpty()) {
                    tagToNotes.remove(tag);
                }
            }
        }

        private synchronized Set<Tag> tagsFor(String noteFileName) {
            Set<Tag> tags = noteToTags.get(noteFileName);
            if (tags == null || tags.isEmpty()) {
                return Set.of();
            }
            return Collections.unmodifiableSet(new TreeSet<>(tags));
        }

        private synchronized Set<String> filterByTags(Collection<String> tagNames) {
            if (tagNames == null || tagNames.isEmpty()) {
                return Set.of();
            }

            List<Set<String>> noteSets = tagNames.stream()
                    .map(Tag::normalize)
                    .map(this::noteNamesForTag)
                    .filter(set -> !set.isEmpty())
                    .sorted(Comparator.comparingInt(Set::size))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (noteSets.isEmpty() || noteSets.size() != tagNames.stream().map(Tag::normalize).collect(Collectors.toSet()).size()) {
                return Set.of();
            }

            Set<String> intersection = new LinkedHashSet<>(noteSets.get(0));
            for (int i = 1; i < noteSets.size(); i++) {
                intersection.retainAll(noteSets.get(i));
                if (intersection.isEmpty()) {
                    return Set.of();
                }
            }
            return intersection;
        }

        private Set<String> noteNamesForTag(String normalizedTagName) {
            for (Map.Entry<Tag, Set<String>> entry : tagToNotes.entrySet()) {
                if (entry.getKey().normalizedName().equals(normalizedTagName)) {
                    return new LinkedHashSet<>(entry.getValue());
                }
            }
            return Set.of();
        }

        private synchronized List<Tag> allTags() {
            return tagToNotes.keySet().stream()
                    .sorted()
                    .toList();
        }
    }
}
