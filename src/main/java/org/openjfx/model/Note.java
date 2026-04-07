package org.openjfx.model;

import org.openjfx.AppConstants;
import org.openjfx.service.TagPool;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public final class Note {
    private final String name;
    private final Path path;
    private final Instant lastModified;
    private final boolean starred;
    private final boolean trashed;
    private final Set<Tag> tags;

    public Note(String name, Path path, Instant lastModified, boolean starred, boolean trashed, Collection<Tag> tags) {
        this.name = name;
        this.path = path;
        this.lastModified = lastModified;
        this.starred = starred;
        this.trashed = trashed;
        this.tags = new LinkedHashSet<>(tags == null ? Set.of() : tags);
    }

    public String name() {
        return name;
    }

    public Path path() {
        return path;
    }

    public Instant lastModified() {
        return lastModified;
    }

    public boolean starred() {
        return starred;
    }

    public boolean trashed() {
        return trashed;
    }

    public boolean addTag(String tagName) {
        return tags.add(TagPool.getInstance().intern(tagName));
    }

    public boolean removeTag(String tagName) {
        String normalized = Tag.normalize(tagName);
        return tags.removeIf(tag -> tag.normalizedName().equals(normalized));
    }

    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(new TreeSet<>(tags));
    }

    public String displayName() {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(AppConstants.EXT_TXT)
                ? fileName.substring(0, fileName.length() - AppConstants.EXT_TXT.length())
                : fileName;
    }
}
