package org.openjfx;

public final class AppConstants {
    public static final String NOTES_DIR = "notes";
    public static final String TRASH_DIR = "trash";
    public static final String EXT_TXT = ".txt";
    public static final String SETTINGS_FILE = "quillpad.settings";
    public static final String USERS_FILE = "users.txt";
    public static final String REGISTRY_FILE = ".remote-notes.properties";
    public static final String TAGS_FILE = ".note-tags.properties";
    public static final String STYLED_DOC_HEADER = "QPAD-DOC-1";
    public static final long AUTOSAVE_INTERVAL_MS = 30_000L;
    public static final int DEFAULT_FONT_SIZE = 14;
    public static final String DEFAULT_FONT_FAMILY = "System";
    public static final int MAX_TAGS_PER_FILE = 3;

    private AppConstants() {
    }
}
