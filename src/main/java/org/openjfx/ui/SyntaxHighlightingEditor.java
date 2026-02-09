package org.openjfx.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.openjfx.SettingsManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlightingEditor extends HBox {
    
    private CodeArea codeArea;
    private IntegerProperty currentFontSize = new SimpleIntegerProperty(SettingsManager.getFontSize());
    private String currentFontFamily = SettingsManager.getFontFamily();
    
    // Syntax highlighting patterns for Java-like languages
    private static final String[] KEYWORDS = new String[] {
        "abstract", "assert", "boolean", "break", "byte",
        "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else",
        "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super",
        "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while",
        "true", "false", "null"
    };
    
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "//[^\\n]*|/\\*(.|\\R)*?\\*/";
    private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?\\b";
    
    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
        + "|(?<PAREN>" + PAREN_PATTERN + ")"
        + "|(?<BRACE>" + BRACE_PATTERN + ")"
        + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
        + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN + ")"
        + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    );
    
    public SyntaxHighlightingEditor() {
        setupUI();
        setupFont();
    }
    
    private void setupUI() {
        setSpacing(0);
        setStyle("-fx-background-color: transparent;");
        
        // Create CodeArea
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setWrapText(false);
        
        // Set default font using CSS style
        updateFontStyle();
        
        // Enable syntax highlighting
        codeArea.multiPlainChanges()
            .successionEnds(Duration.ofMillis(500))
            .subscribe(ignore -> {
                codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText()));
            });
        
        HBox.setHgrow(codeArea, Priority.ALWAYS);
        getChildren().add(codeArea);
    }
    
    private void updateFontStyle() {
        codeArea.setStyle("-fx-font-family: '" + currentFontFamily + "'; -fx-font-size: " + currentFontSize.get() + "px;");
    }
    
    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        
        while (matcher.find()) {
            String styleClass =
                matcher.group("KEYWORD") != null ? "keyword" :
                matcher.group("PAREN") != null ? "paren" :
                matcher.group("BRACE") != null ? "brace" :
                matcher.group("BRACKET") != null ? "bracket" :
                matcher.group("SEMICOLON") != null ? "semicolon" :
                matcher.group("STRING") != null ? "string" :
                matcher.group("COMMENT") != null ? "comment" :
                matcher.group("NUMBER") != null ? "number" :
                null;
            
            assert styleClass != null;
            
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
    
    private void setupFont() {
        currentFontSize.addListener((obs, oldVal, newVal) -> {
            updateFontStyle();
        });
    }
    
    public CodeArea getCodeArea() {
        return codeArea;
    }
    
    public void increaseFontSize() {
        if (currentFontSize.get() < 32) {
            currentFontSize.set(currentFontSize.get() + 2);
            SettingsManager.setFontSize(currentFontSize.get());
            updateFontStyle();
        }
    }
    
    public void decreaseFontSize() {
        if (currentFontSize.get() > 8) {
            currentFontSize.set(currentFontSize.get() - 2);
            SettingsManager.setFontSize(currentFontSize.get());
            updateFontStyle();
        }
    }
    
    public void resetFontSize() {
        currentFontSize.set(SettingsManager.getFontSize());
        updateFontStyle();
    }
    
    public int getFontSize() {
        return currentFontSize.get();
    }
    
    public IntegerProperty fontSizeProperty() {
        return currentFontSize;
    }
    
    public void setWrapText(boolean wrap) {
        codeArea.setWrapText(wrap);
    }
}