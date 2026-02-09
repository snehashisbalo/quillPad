package org.openjfx;

import javafx.scene.Scene;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    
    public enum Theme {
        LIGHT("Light", "light-theme"),
        DARK("Dark", "dark-theme"),
        CATPPUCCIN("Catppuccin", "catppuccin-theme"),
        TOKYO_NIGHT("Tokyo Night", "tokyo-night-theme");
        
        private final String displayName;
        private final String styleClass;
        
        Theme(String displayName, String styleClass) {
            this.displayName = displayName;
            this.styleClass = styleClass;
        }
        
        public String getDisplayName() { return displayName; }
        public String getStyleClass() { return styleClass; }
    }
    
    private static Theme currentTheme;
    private static final List<Scene> registeredScenes = new ArrayList<>();
    
    static {
        // Load saved theme from settings
        currentTheme = SettingsManager.getSavedTheme();
    }
    
    public static void setTheme(Theme theme) {
        currentTheme = theme;
        SettingsManager.setTheme(theme);
        for (Scene scene : registeredScenes) {
            applyThemeToScene(scene);
        }
    }
    
    public static Theme getCurrentTheme() {
        return currentTheme;
    }
    
    public static void registerScene(Scene scene) {
        if (!registeredScenes.contains(scene)) {
            registeredScenes.add(scene);
            applyThemeToScene(scene);
        }
    }
    
    public static void applyThemeToScene(Scene scene) {
        if (scene == null) return;
        
        scene.getStylesheets().clear();
        
        String baseStyles = ThemeManager.class.getResource("styles.css").toExternalForm();
        scene.getStylesheets().add(baseStyles);
        
        switch (currentTheme) {
            case CATPPUCCIN:
                String catppuccinStyles = ThemeManager.class.getResource("catppuccin.css").toExternalForm();
                scene.getStylesheets().add(catppuccinStyles);
                break;
            case TOKYO_NIGHT:
                String tokyoStyles = ThemeManager.class.getResource("tokyo-night.css").toExternalForm();
                scene.getStylesheets().add(tokyoStyles);
                break;
            case DARK:
                String darkStyles = ThemeManager.class.getResource("dark-theme.css").toExternalForm();
                scene.getStylesheets().add(darkStyles);
                break;
            default:
                break;
        }
        
        scene.getRoot().getStyleClass().removeAll(
            Theme.LIGHT.getStyleClass(),
            Theme.DARK.getStyleClass(),
            Theme.CATPPUCCIN.getStyleClass(),
            Theme.TOKYO_NIGHT.getStyleClass()
        );
        scene.getRoot().getStyleClass().add(currentTheme.getStyleClass());
    }
    
    public static void toggleTheme() {
        Theme[] themes = Theme.values();
        int nextIndex = (currentTheme.ordinal() + 1) % themes.length;
        setTheme(themes[nextIndex]);
    }
    
    public static String getThemeIcon(Theme theme) {
        switch (theme) {
            case LIGHT: return "☀️";
            case DARK: return "🌙";
            case CATPPUCCIN: return "🌸";
            case TOKYO_NIGHT: return "🗼";
            default: return "🎨";
        }
    }
}