package org.openjfx.service;

import org.openjfx.model.Tag;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TagPool {
    private static final TagPool INSTANCE = new TagPool();

    private final Map<String, Tag> tags = new ConcurrentHashMap<>();

    private TagPool() {
    }

    public static TagPool getInstance() {
        return INSTANCE;
    }

    public Tag intern(String name) {
        String normalized = Tag.normalize(name);
        return tags.computeIfAbsent(normalized, Tag::new);
    }

    public Collection<Tag> allTags() {
        return Collections.unmodifiableCollection(tags.values());
    }
}
