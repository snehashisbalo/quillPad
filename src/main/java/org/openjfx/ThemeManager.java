package org.openjfx;

import javafx.scene.Scene;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
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
    private static final List<WeakReference<Scene>> registeredScenes = new ArrayList<>();

    static {
        currentTheme = SettingsManager.getSavedTheme();
    }

    public static void setTheme(Theme theme) {
        currentTheme = theme;
        SettingsManager.setTheme(theme);
        pruneRegisteredScenes();
        for (WeakReference<Scene> ref : registeredScenes) {
            Scene scene = ref.get();
            if (scene != null) {
                applyThemeToScene(scene);
            }
        }
    }
    
    public static Theme getCurrentTheme() {
        return currentTheme;
    }
    
    public static void registerScene(Scene scene) {
        if (scene == null) {
            return;
        }
        pruneRegisteredScenes();
        for (WeakReference<Scene> ref : registeredScenes) {
            if (ref.get() == scene) {
                applyThemeToScene(scene);
                return;
            }
        }
        registeredScenes.add(new WeakReference<>(scene));
        applyThemeToScene(scene);
    }
    
    public static void applyThemeToScene(Scene scene) {
        if (scene == null) return;

        scene.getStylesheets().clear();

        String baseStyles = resolveStylesheet("styles.css");
        if (baseStyles != null) {
            scene.getStylesheets().add(baseStyles);
        }

        if (currentTheme == Theme.DARK) {
            String darkStyles = resolveStylesheet("dark-theme.css");
            if (darkStyles != null) {
                scene.getStylesheets().add(darkStyles);
            }
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

    private static void pruneRegisteredScenes() {
        Iterator<WeakReference<Scene>> iterator = registeredScenes.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().get() == null) {
                iterator.remove();
            }
        }
    }

    private static String resolveStylesheet(String resourceName) {
        var resource = ThemeManager.class.getResource(resourceName);
        if (resource == null) {
            System.err.println("Missing theme stylesheet: " + resourceName);
            return null;
        }
        return resource.toExternalForm();
    }
}
