package org.openjfx;

import javafx.scene.text.Font;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class SettingsManager {
    private static final String SETTINGS_FILE = "quillpad.settings";
    private static Properties settings = new Properties();
    
    // Default settings
    private static final String DEFAULT_FONT_FAMILY = "System";
    private static final String DEFAULT_FONT_SIZE = "14";
    private static final String DEFAULT_THEME = "CATPPUCCIN";
    
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
            return ThemeManager.Theme.CATPPUCCIN;
        }
    }
    
    public static void setTheme(ThemeManager.Theme theme) {
        settings.setProperty("theme", theme.name());
        saveSettings();
    }
    
    public static Font getAppFont() {
        return Font.font(getFontFamily(), getFontSize());
    }
}