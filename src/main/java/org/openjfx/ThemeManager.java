package org.openjfx;

import javafx.scene.Scene;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    public enum Theme {
        LIGHT("Light", "light-theme"),
        DARK("Dark", "dark-theme");

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

        if (currentTheme == Theme.DARK) {
            String darkStyles = ThemeManager.class.getResource("dark-theme.css").toExternalForm();
            scene.getStylesheets().add(darkStyles);
        }

        scene.getRoot().getStyleClass().removeAll(
            Theme.LIGHT.getStyleClass(),
            Theme.DARK.getStyleClass()
        );
        scene.getRoot().getStyleClass().add(currentTheme.getStyleClass());
    }

    public static void toggleTheme() {
        setTheme(currentTheme == Theme.DARK ? Theme.LIGHT : Theme.DARK);
    }

    public static String getThemeIcon(Theme theme) {
        switch (theme) {
            case LIGHT: return "☀️";
            case DARK: return "🌙";
            default: return "🎨";
        }
    }
}
