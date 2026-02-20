package org.openjfx.component;

import org.fxmisc.richtext.InlineCssTextArea;
import org.quillpad.collab.Document;

import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;

public class RichTextEditor extends StackPane {
    
    private final InlineCssTextArea textArea;
    private String fontFamily = "Consolas";
    private double fontSize = 14.0;
    private String editorBg = "#FFFFFF";
    private String editorFg = "#2C3E50";
    private String selectionBg = "rgba(74, 144, 226, 0.35)";
    private String documentId;
    private String documentName;
    private String filePath;
    private boolean modified;
    
    public RichTextEditor() {
        this.textArea = new InlineCssTextArea();
        this.modified = false;
        
        getStyleClass().add("rich-text-editor");
        textArea.getStyleClass().add("rich-editor-area");
        textArea.setWrapText(true);
        sceneProperty().addListener((obs, oldScene, newScene) -> attachThemeListeners(newScene));
        applyEditorStyle();
        
        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            modified = true;
        });
        
        getChildren().add(textArea);
        setStyle("-fx-background-color: transparent;");
    }
    
    public InlineCssTextArea getTextArea() {
        return textArea;
    }
    
    public String getText() {
        return textArea.getText();
    }
    
    public void setText(String text) {
        textArea.clear();
        textArea.appendText(text);
        modified = false;
    }
    
    public void setFont(Font font) {
        if (font == null) {
            return;
        }
        fontFamily = font.getFamily();
        fontSize = font.getSize();
        applyEditorStyle();
    }

    private void applyEditorStyle() {
        String style = String.format(
                "-fx-font-family: \"%s\"; -fx-font-size: %.0fpx; "
                        + "-fx-background-color: %s; "
                        + "-fx-control-inner-background: %s; "
                        + "-fx-highlight-fill: %s; "
                        + "-fx-fill: %s;",
                fontFamily, fontSize, editorBg, editorBg, selectionBg, editorFg
        );
        textArea.setStyle(style);
        setStyle("-fx-background-color: " + editorBg + ";");
    }

    private void attachThemeListeners(Scene scene) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        Parent root = scene.getRoot();
        root.getStyleClass().addListener((ListChangeListener<String>) change -> updateThemePalette());
        updateThemePalette();
    }

    private void updateThemePalette() {
        Scene scene = getScene();
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        var styles = scene.getRoot().getStyleClass();
        if (styles.contains("dark-theme")) {
            editorBg = "#1e1e1e";
            editorFg = "#d4d4d4";
            selectionBg = "#264f78";
        } else if (styles.contains("catppuccin-theme")) {
            editorBg = "#1e1e2e";
            editorFg = "#cdd6f4";
            selectionBg = "#585b70";
        } else if (styles.contains("tokyo-night-theme")) {
            editorBg = "#1a1b26";
            editorFg = "#a9b1d6";
            selectionBg = "#3b4261";
        } else {
            editorBg = "#ffffff";
            editorFg = "#2c3e50";
            selectionBg = "rgba(74, 144, 226, 0.35)";
        }
        applyEditorStyle();
    }
    
    public void setWrapText(boolean wrap) {
        textArea.setWrapText(wrap);
    }
    
    public boolean isWrapText() {
        return textArea.isWrapText();
    }
    
    public int getCaretPosition() {
        return textArea.getCaretPosition();
    }
    
    public void setCaretPosition(int position) {
        textArea.moveTo(position);
    }
    
    public void selectRange(int anchor, int caretPosition) {
        textArea.selectRange(anchor, caretPosition);
    }
    
    public void selectAll() {
        textArea.selectAll();
    }
    
    public void cut() {
        textArea.cut();
    }
    
    public void copy() {
        textArea.copy();
    }
    
    public void paste() {
        textArea.paste();
    }
    
    public void undo() {
        textArea.undo();
    }
    
    public void redo() {
        textArea.redo();
    }
    
    public boolean isModified() {
        return modified;
    }
    
    public void setModified(boolean modified) {
        this.modified = modified;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
    
    public String getDocumentName() {
        return documentName;
    }
    
    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public void applyBoldToSelection() {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            boolean isBold = currentStyle.contains("-fx-font-weight: bold");
            String newStyle = isBold ? 
                    currentStyle.replace("-fx-font-weight: bold;", "") :
                    currentStyle + "-fx-font-weight: bold;";
            setStyle(start, end, newStyle);
        }
    }
    
    public void applyItalicToSelection() {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            boolean isItalic = currentStyle.contains("-fx-font-style: italic;");
            String newStyle = isItalic ? 
                    currentStyle.replace("-fx-font-style: italic;", "") :
                    currentStyle + "-fx-font-style: italic;";
            setStyle(start, end, newStyle);
        }
    }
    
    public void applyUnderlineToSelection() {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            boolean isUnderline = currentStyle.contains("-fx-underline: true;");
            String newStyle = isUnderline ? 
                    currentStyle.replace("-fx-underline: true;", "") :
                    currentStyle + "-fx-underline: true;";
            setStyle(start, end, newStyle);
        }
    }
    
    public void applyStrikethroughToSelection() {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            boolean isStrikethrough = currentStyle.contains("-fx-strikethrough: true;");
            String newStyle = isStrikethrough ? 
                    currentStyle.replace("-fx-strikethrough: true;", "") :
                    currentStyle + "-fx-strikethrough: true;";
            setStyle(start, end, newStyle);
        }
    }
    
    public void applyFontFamilyToSelection(String fontFamily) {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            currentStyle = currentStyle.replaceAll("-fx-font-family: \"[^\"]+\";", "");
            String newStyle = currentStyle + String.format("-fx-font-family: \"%s\";", fontFamily);
            setStyle(start, end, newStyle);
        }
    }
    
    public void applyFontSizeToSelection(double fontSize) {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            currentStyle = currentStyle.replaceAll("-fx-font-size: [0-9.]+px;", "");
            String newStyle = currentStyle + String.format("-fx-font-size: %.0fpx;", fontSize);
            setStyle(start, end, newStyle);
        }
    }
    
    public void applyTextColorToSelection(String color) {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            currentStyle = currentStyle.replaceAll("-fx-fill: [^;]+;", "");
            String newStyle = currentStyle + String.format("-fx-fill: %s;", color);
            setStyle(start, end, newStyle);
        }
    }
    
    public void applyHighlightToSelection(String color) {
        IndexRange selection = getSelection();
        int start = selection.getStart();
        int end = selection.getEnd();
        
        if (selection.getLength() == 0) {
            start = textArea.getCaretPosition();
            end = start + 1;
        }
        
        if (end > start) {
            String currentStyle = getStyleAtPosition(start);
            currentStyle = currentStyle.replaceAll("-fx-background-color: [^;]+;", "");
            String newStyle = currentStyle + String.format("-fx-background-color: %s;", color);
            setStyle(start, end, newStyle);
        }
    }
    
    public IndexRange getSelection() {
        return new IndexRange(textArea.getSelection().getStart(), textArea.getSelection().getEnd());
    }
    
    public String getSelectedText() {
        return textArea.getSelectedText();
    }
    
    public void clearSelection() {
        textArea.deselect();
    }
    
    private String getStyleAtPosition(int position) {
        return textArea.getStyleAtPosition(position);
    }
    
    private void setStyle(int from, int to, String style) {
        textArea.setStyle(from, to, style);
    }
    
    public void insertText(int position, String text) {
        textArea.insertText(position, text);
    }
    
    public void deleteText(int from, int to) {
        textArea.deleteText(from, to);
    }
    
    public void replaceSelection(String replacement) {
        textArea.replaceSelection(replacement);
    }
    
    public Document toDocument() {
        Document doc = new Document(documentId, documentName, null);
        doc.setPlainText(textArea.getText());
        
        java.util.List<Document.StyleSegment> segments = new java.util.ArrayList<>();
        org.fxmisc.richtext.model.StyleSpans<String> spans = textArea.getStyleSpans(0, textArea.getLength());
        
        int start = 0;
        for (org.fxmisc.richtext.model.StyleSpan<String> span : spans) {
            if (span.getLength() > 0) {
                Document.StyleSegment segment = new Document.StyleSegment(start, span.getLength());
                String style = span.getStyle();
                parseStyleToSegment(style, segment);
                segments.add(segment);
            }
            start += span.getLength();
        }
        
        doc.setStyleSegments(segments);
        return doc;
    }
    
    public void fromDocument(Document doc) {
        textArea.clear();
        textArea.appendText(doc.getPlainText());
        
        this.documentId = doc.getDocumentId();
        this.documentName = doc.getDocumentName();
        
        if (doc.getStyleSegments() != null && !doc.getStyleSegments().isEmpty()) {
            for (Document.StyleSegment segment : doc.getStyleSegments()) {
                String style = segment.toCss();
                if (!style.isEmpty()) {
                    textArea.setStyle(segment.getStart(), segment.getStart() + segment.getLength(), style);
                }
            }
        }
        
        modified = false;
    }
    
    private void parseStyleToSegment(String style, Document.StyleSegment segment) {
        if (style.contains("-fx-font-family:")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("-fx-font-family: \"([^\"]+)\";").matcher(style);
            if (m.find()) segment.setFontFamily(m.group(1));
        }
        if (style.contains("-fx-font-size:")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("-fx-font-size: ([0-9.]+)px;").matcher(style);
            if (m.find()) segment.setFontSize(Double.parseDouble(m.group(1)));
        }
        if (style.contains("-fx-font-weight: bold")) {
            segment.setFontWeight("bold");
        }
        if (style.contains("-fx-font-style: italic")) {
            segment.setFontStyle("italic");
        }
        if (style.contains("-fx-fill:")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("-fx-fill: ([^;]+);").matcher(style);
            if (m.find()) segment.setTextColor(m.group(1));
        }
        if (style.contains("-fx-background-color:")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("-fx-background-color: ([^;]+);").matcher(style);
            if (m.find()) segment.setBackgroundColor(m.group(1));
        }
    }
    
    public static class IndexRange {
        private final int start;
        private final int end;
        
        public IndexRange(int start, int end) {
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
        }
        
        public int getStart() { return start; }
        public int getEnd() { return end; }
        public int getLength() { return end - start; }
    }
}
