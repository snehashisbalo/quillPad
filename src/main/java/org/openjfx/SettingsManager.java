package org.openjfx;

import javafx.scene.text.Font;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public class SettingsManager {
    private static final String SETTINGS_FILE = "quillpad.settings";
    private static Properties settings = new Properties();
    
    // Default settings
    private static final String DEFAULT_FONT_FAMILY = "System";
    private static final String DEFAULT_FONT_SIZE = "14";
    private static final String DEFAULT_THEME = "DARK";
    private static final String DEFAULT_NETWORK_ENABLED = "false";
    private static final String DEFAULT_NETWORK_BASE_URL = "http://localhost:8080";
    private static final String DEFAULT_NETWORK_API_KEY = "";
    private static final String DEFAULT_NETWORK_TIMEOUT_SECONDS = "8";
    private static final String DEFAULT_NETWORK_NAMESPACE = "shared";
    private static final String DEFAULT_REMOTE_AUTOSAVE_SECONDS = "30";
    private static final String DEFAULT_STARRED_NOTES = "";
    
    static {
        loadSettings();
    }
    
    public static void loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                settings.load(is);
            } catch (IOException e) {
                System.err.println("Error loading settings: " + e.getMessage());
                setDefaults();
            }
        } else {
            setDefaults();
        }
    }
    
    public static void saveSettings() {
        try (OutputStream os = new FileOutputStream(SETTINGS_FILE)) {
            settings.store(os, "QuillPad Settings");
        } catch (IOException e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }
    
    private static void setDefaults() {
        settings.setProperty("font.family", DEFAULT_FONT_FAMILY);
        settings.setProperty("font.size", DEFAULT_FONT_SIZE);
        settings.setProperty("theme", DEFAULT_THEME);
        settings.setProperty("network.enabled", DEFAULT_NETWORK_ENABLED);
        settings.setProperty("network.base_url", DEFAULT_NETWORK_BASE_URL);
        settings.setProperty("network.api_key", DEFAULT_NETWORK_API_KEY);
        settings.setProperty("network.timeout_seconds", DEFAULT_NETWORK_TIMEOUT_SECONDS);
        settings.setProperty("network.namespace", DEFAULT_NETWORK_NAMESPACE);
        settings.setProperty("network.remote_autosave_seconds", DEFAULT_REMOTE_AUTOSAVE_SECONDS);
        settings.setProperty("starred.notes", DEFAULT_STARRED_NOTES);
        saveSettings();
    }
    
    // Font Family
    public static String getFontFamily() {
        return settings.getProperty("font.family", DEFAULT_FONT_FAMILY);
    }
    
    public static void setFontFamily(String family) {
        settings.setProperty("font.family", family);
        saveSettings();
    }
    
    // Font Size
    public static int getFontSize() {
        try {
            return Integer.parseInt(settings.getProperty("font.size", DEFAULT_FONT_SIZE));
        } catch (NumberFormatException e) {
            return 14;
        }
    }
    
    public static void setFontSize(int size) {
        settings.setProperty("font.size", String.valueOf(size));
        saveSettings();
    }
    
    // Theme
    public static ThemeManager.Theme getSavedTheme() {
        String themeName = settings.getProperty("theme", DEFAULT_THEME);
        try {
            return ThemeManager.Theme.valueOf(themeName);
        } catch (IllegalArgumentException e) {
            return ThemeManager.Theme.DARK;
        }
    }
    
    public static void setTheme(ThemeManager.Theme theme) {
        settings.setProperty("theme", theme.name());
        saveSettings();
    }
    
    public static Font getAppFont() {
        return Font.font(getFontFamily(), getFontSize());
    }

    // Network
    public static boolean isNetworkEnabled() {
        return Boolean.parseBoolean(settings.getProperty("network.enabled", DEFAULT_NETWORK_ENABLED));
    }

    public static void setNetworkEnabled(boolean enabled) {
        settings.setProperty("network.enabled", String.valueOf(enabled));
        saveSettings();
    }

    public static String getNetworkBaseUrl() {
        return settings.getProperty("network.base_url", DEFAULT_NETWORK_BASE_URL).trim();
    }

    public static void setNetworkBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            settings.setProperty("network.base_url", DEFAULT_NETWORK_BASE_URL);
        } else {
            settings.setProperty("network.base_url", baseUrl.trim());
        }
        saveSettings();
    }

    public static String getNetworkApiKey() {
        return settings.getProperty("network.api_key", DEFAULT_NETWORK_API_KEY);
    }

    public static void setNetworkApiKey(String apiKey) {
        settings.setProperty("network.api_key", apiKey == null ? "" : apiKey.trim());
        saveSettings();
    }

    public static int getNetworkTimeoutSeconds() {
        try {
            return Integer.parseInt(settings.getProperty("network.timeout_seconds", DEFAULT_NETWORK_TIMEOUT_SECONDS));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULT_NETWORK_TIMEOUT_SECONDS);
        }
    }

    public static void setNetworkTimeoutSeconds(int timeoutSeconds) {
        int safeTimeout = Math.max(1, Math.min(timeoutSeconds, 60));
        settings.setProperty("network.timeout_seconds", String.valueOf(safeTimeout));
        saveSettings();
    }

    public static String getNetworkNamespace() {
        String raw = settings.getProperty("network.namespace", DEFAULT_NETWORK_NAMESPACE);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_NETWORK_NAMESPACE;
        }
        return raw.trim();
    }

    public static void setNetworkNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            settings.setProperty("network.namespace", DEFAULT_NETWORK_NAMESPACE);
        } else {
            settings.setProperty("network.namespace", namespace.trim());
        }
        saveSettings();
    }

    public static int getRemoteAutoSaveSeconds() {
        try {
            return Integer.parseInt(settings.getProperty("network.remote_autosave_seconds", DEFAULT_REMOTE_AUTOSAVE_SECONDS));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULT_REMOTE_AUTOSAVE_SECONDS);
        }
    }

    public static void setRemoteAutoSaveSeconds(int seconds) {
        int safeSeconds = Math.max(5, Math.min(seconds, 3600));
        settings.setProperty("network.remote_autosave_seconds", String.valueOf(safeSeconds));
        saveSettings();
    }

    // Starred notes
    public static Set<String> getStarredNotes() {
        String raw = settings.getProperty("starred.notes", DEFAULT_STARRED_NOTES);
        Set<String> notes = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return notes;
        }
        Arrays.stream(raw.split("\u001F"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(notes::add);
        return notes;
    }

    public static void setStarredNotes(Set<String> starredNotes) {
        if (starredNotes == null || starredNotes.isEmpty()) {
            settings.setProperty("starred.notes", "");
            saveSettings();
            return;
        }
        String value = String.join("\u001F", starredNotes);
        settings.setProperty("starred.notes", value);
        saveSettings();
    }
}
