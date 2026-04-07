package org.openjfx.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StyledDocument implements Serializable {
    private static final long serialVersionUID = 1L;

    private String documentId;
    private String documentName;
    private String plainText;
    private List<StyleSegment> styleSegments;
    private String owner;
    private long lastModified;
    private int version;

    public StyledDocument() {
        this.styleSegments = new ArrayList<>();
        this.plainText = "";
        this.version = 0;
        this.lastModified = System.currentTimeMillis();
    }

    public StyledDocument(String documentId, String documentName, String owner) {
        this();
        this.documentId = documentId;
        this.documentName = documentName;
        this.owner = owner;
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

    public String getPlainText() {
        return plainText;
    }

    public void setPlainText(String plainText) {
        this.plainText = plainText;
        this.lastModified = System.currentTimeMillis();
        this.version++;
    }

    public List<StyleSegment> getStyleSegments() {
        return styleSegments;
    }

    public void setStyleSegments(List<StyleSegment> styleSegments) {
        this.styleSegments = styleSegments;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public static class StyleSegment implements Serializable {
        private static final long serialVersionUID = 1L;

        private int start;
        private int length;
        private String fontFamily;
        private double fontSize;
        private String fontWeight;
        private String fontStyle;
        private boolean underline;
        private boolean strikethrough;
        private String textColor;
        private String backgroundColor;

        public StyleSegment() {}

        public StyleSegment(int start, int length) {
            this.start = start;
            this.length = length;
        }

        public int getStart() { return start; }
        public void setStart(int start) { this.start = start; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public double getFontSize() { return fontSize; }
        public void setFontSize(double fontSize) { this.fontSize = fontSize; }
        public String getFontWeight() { return fontWeight; }
        public void setFontWeight(String fontWeight) { this.fontWeight = fontWeight; }
        public String getFontStyle() { return fontStyle; }
        public void setFontStyle(String fontStyle) { this.fontStyle = fontStyle; }
        public boolean isUnderline() { return underline; }
        public void setUnderline(boolean underline) { this.underline = underline; }
        public boolean isStrikethrough() { return strikethrough; }
        public void setStrikethrough(boolean strikethrough) { this.strikethrough = strikethrough; }
        public String getTextColor() { return textColor; }
        public void setTextColor(String textColor) { this.textColor = textColor; }
        public String getBackgroundColor() { return backgroundColor; }
        public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

        public String toCss() {
            StringBuilder css = new StringBuilder();
            if (fontFamily != null && !fontFamily.isEmpty()) {
                css.append("-fx-font-family: \"").append(fontFamily).append("\";");
            }
            if (fontSize > 0) {
                css.append("-fx-font-size: ").append(fontSize).append("px;");
            }
            if ("bold".equals(fontWeight)) {
                css.append("-fx-font-weight: bold;");
            }
            if ("italic".equals(fontStyle)) {
                css.append("-fx-font-style: italic;");
            }
            if (underline) {
                css.append("-fx-underline: true;");
            }
            if (strikethrough) {
                css.append("-fx-strikethrough: true;");
            }
            if (textColor != null && !textColor.isEmpty()) {
                css.append("-fx-fill: ").append(textColor).append(";");
            }
            if (backgroundColor != null && !backgroundColor.isEmpty()) {
                css.append("-fx-background-color: ").append(backgroundColor).append(";");
            }
            return css.toString();
        }
    }
}
