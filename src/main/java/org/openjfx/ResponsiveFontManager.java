package org.openjfx;

import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.text.Font;

public class ResponsiveFontManager {
    
    public static void bindResponsiveFont(Label label, Scene scene, double divisor) {
        label.styleProperty().bind(
            Bindings.concat("-fx-font-size: ", 
                scene.widthProperty().divide(divisor).asString(), "px;")
        );
    }
    
    public static void bindResponsiveFont(Label label, Scene scene, double divisor, double minSize, double maxSize) {
        label.styleProperty().bind(
            Bindings.concat("-fx-font-size: ", 
                Bindings.min(maxSize, 
                    Bindings.max(minSize, scene.widthProperty().divide(divisor))
                ).asString(), "px;")
        );
    }
    
    public static String getResponsiveFontSize(Scene scene, double divisor) {
        return String.valueOf(scene.getWidth() / divisor);
    }
    
    public static String getResponsiveFontSize(Scene scene, double divisor, double minSize, double maxSize) {
        double size = scene.getWidth() / divisor;
        return String.valueOf(Math.min(maxSize, Math.max(minSize, size)));
    }
}