package org.openjfx.model;

import java.util.Locale;
import java.util.Objects;

public final class Tag implements Comparable<Tag> {
    private final String normalizedName;
    private final String displayName;

    public Tag(String name) {
        String normalized = normalize(name);
        this.normalizedName = normalized;
        this.displayName = normalized;
    }

    public static String normalize(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Tag name cannot be null");
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Tag name cannot be blank");
        }
        return normalized;
    }

    public String normalizedName() {
        return normalizedName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public int compareTo(Tag other) {
        return normalizedName.compareTo(other.normalizedName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Tag other)) {
            return false;
        }
        return normalizedName.equals(other.normalizedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedName);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
