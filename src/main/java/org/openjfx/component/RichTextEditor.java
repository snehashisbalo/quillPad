package org.openjfx.component;

import org.fxmisc.richtext.InlineCssTextArea;
import org.openjfx.ThemeManager;
import org.quillpad.collab.Document;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RichTextEditor extends StackPane {

    public enum Language {
        PLAIN_TEXT, JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, HTML, CSS, SCSS, JSON, XML, SQL,
        C, CPP, C_SHARP, GO, RUST, PHP, RUBY, SWIFT, KOTLIN, SHELL, MARKDOWN, YAML
    }

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

    private Language currentLanguage = Language.PLAIN_TEXT;
    private boolean syntaxHighlightingEnabled = true;
    private Pattern keywordPattern = Pattern.compile("(?!)");
    private Pattern typePattern = Pattern.compile("(?!)");
    private Pattern commentPattern = Pattern.compile("(?!)");
    private Pattern stringPattern = Pattern.compile("(?!)");
    private Pattern numberPattern = Pattern.compile("(?!)");
    private Pattern tagPattern = Pattern.compile("(?!)");
    private Pattern preprocessorPattern = Pattern.compile("(?!)");
    private static final String INDENT_UNIT = "    ";
    private static final Set<Language> BRACE_LANGUAGES = Set.of(
            Language.JAVA, Language.JAVASCRIPT, Language.TYPESCRIPT, Language.C, Language.CPP,
            Language.C_SHARP, Language.GO, Language.RUST, Language.PHP, Language.KOTLIN, Language.SWIFT
    );

    private static final String[] JAVA_KEYWORDS = {
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
        "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
        "true", "false", "null", "var", "record", "sealed", "permits", "yield", "when"
    };

    private static final String[] JAVA_TYPES = {
        "String", "Integer", "Long", "Byte", "Short", "Float", "Double", "Boolean", "Character", "Object",
        "List", "ArrayList", "LinkedList", "HashMap", "HashSet", "Map", "Set", "Collection", "Optional",
        "Stream", "CompletableFuture", "Iterator", "Comparable", "Iterable", "Runnable", "Thread", "Exception",
        "RuntimeException", "Throwable", "Error", "Number", "Math", "System", "Arrays", "Collections", "Objects",
        "StringBuilder", "StringBuffer", "Path", "Files", "Paths", "BufferedReader", "BufferedWriter",
        "FileReader", "FileWriter", "InputStream", "OutputStream", "Reader", "Writer", "PrintWriter", "Scanner"
    };

    private static final String[] PYTHON_KEYWORDS = {
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
        "match", "case", "self", "cls"
    };

    private static final String[] PYTHON_BUILTINS = {
        "abs", "all", "any", "bin", "bool", "bytes", "callable", "chr", "classmethod", "compile", "complex",
        "delattr", "dict", "dir", "divmod", "enumerate", "eval", "exec", "filter", "float", "format",
        "frozenset", "getattr", "globals", "hasattr", "hash", "help", "hex", "id", "input", "int", "isinstance",
        "issubclass", "iter", "len", "list", "locals", "map", "max", "min", "next", "object", "oct", "open",
        "ord", "pow", "print", "property", "range", "repr", "reversed", "round", "set", "setattr", "slice",
        "sorted", "staticmethod", "str", "sum", "super", "tuple", "type", "vars", "zip", "__import__"
    };

    private static final String[] JS_KEYWORDS = {
        "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default",
        "delete", "do", "else", "export", "extends", "finally", "for", "function", "if", "import", "in",
        "instanceof", "let", "new", "return", "static", "super", "switch", "this", "throw", "try", "typeof",
        "var", "void", "while", "with", "yield", "true", "false", "null", "undefined", "of", "from"
    };

    private static final String[] JS_TYPES = {
        "Array", "Boolean", "Date", "Error", "Function", "JSON", "Map", "Math", "Number", "Object", "Promise",
        "Proxy", "RegExp", "Set", "String", "Symbol", "WeakMap", "WeakSet", "console", "document", "window",
        "navigator", "fetch", "Event", "Element", "HTMLElement", "Node"
    };

    private static final String[] HTML_TAGS = {
        "html", "head", "body", "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "tr", "td",
        "th", "form", "input", "button", "select", "option", "textarea", "label", "h1", "h2", "h3", "h4",
        "h5", "h6", "header", "footer", "nav", "main", "section", "article", "aside", "script", "style",
        "link", "meta", "title", "br", "hr", "canvas", "video", "audio", "iframe", "svg"
    };

    private static final String[] SQL_KEYWORDS = {
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN", "IS", "NULL", "INSERT",
        "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "ADD", "COLUMN",
        "INDEX", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK", "DEFAULT", "CONSTRAINT",
        "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "FULL", "CROSS", "ON", "AS", "ORDER", "BY", "ASC", "DESC",
        "GROUP", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX",
        "CASE", "WHEN", "THEN", "ELSE", "END", "EXISTS", "TRUE", "FALSE", "INTEGER", "VARCHAR", "TEXT",
        "BOOLEAN", "DATE", "TIMESTAMP", "FLOAT", "DOUBLE", "DECIMAL"
    };

    private static final String[] C_KEYWORDS = {
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else",
        "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
        "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
        "union", "unsigned", "void", "volatile", "while", "_Bool", "_Complex", "_Imaginary",
        "true", "false", "NULL"
    };

    private static final String[] C_TYPES = {
        "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t",
        "size_t", "ssize_t", "ptrdiff_t", "FILE", "time_t", "clock_t", "wchar_t", "va_list"
    };

    private static final String[] CPP_KEYWORDS = {
        "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool", "break",
        "case", "catch", "char", "char16_t", "char32_t", "class", "compl", "const", "constexpr",
        "const_cast", "continue", "decltype", "default", "delete", "do", "double", "dynamic_cast",
        "else", "enum", "explicit", "export", "extern", "false", "float", "for", "friend", "goto",
        "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not", "not_eq",
        "nullptr", "operator", "or", "or_eq", "private", "protected", "public", "register",
        "reinterpret_cast", "return", "short", "signed", "sizeof", "static", "static_assert",
        "static_cast", "struct", "switch", "template", "this", "thread_local", "throw", "true",
        "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual", "void",
        "volatile", "wchar_t", "while", "xor", "xor_eq", "override", "final"
    };

    private static final String[] CPP_TYPES = {
        "string", "wstring", "u16string", "u32string", "vector", "map", "set", "unordered_map", "unordered_set",
        "list", "deque", "array", "pair", "tuple", "shared_ptr", "unique_ptr", "weak_ptr",
        "istream", "ostream", "stringstream", "ifstream", "ofstream", "exception", "runtime_error",
        "logic_error", "optional", "variant", "any", "function", "thread", "mutex", "condition_variable",
        "cout", "cin", "cerr", "clog", "endl", "std"
    };

    public RichTextEditor() {
        this.textArea = new InlineCssTextArea();
        this.modified = false;

        getStyleClass().add("rich-text-editor");
        textArea.getStyleClass().add("rich-editor-area");
        textArea.setWrapText(true);
        sceneProperty().addListener((obs, oldScene, newScene) -> attachThemeListeners(newScene));
        applyEditorStyle();
        setupTypingAssists();

        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            modified = true;
            if (syntaxHighlightingEnabled && currentLanguage != Language.PLAIN_TEXT) {
                applySyntaxHighlightingDelayed();
            }
        });

        getChildren().add(textArea);
        setStyle("-fx-background-color: transparent;");
        setLanguage(Language.PLAIN_TEXT);
    }

    private void setupTypingAssists() {
        textArea.addEventFilter(KeyEvent.KEY_TYPED, this::handlePairCompletion);
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleAutoIndent);
    }

    private void handlePairCompletion(KeyEvent event) {
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        String ch = event.getCharacter();
        if (ch == null || ch.isEmpty()) {
            return;
        }

        int caret = textArea.getCaretPosition();
        String text = textArea.getText();
        javafx.scene.control.IndexRange selection = textArea.getSelection();
        boolean hasSelection = selection != null && selection.getLength() > 0;

        if (")]}".contains(ch)) {
            if (!hasSelection && caret < text.length() && text.charAt(caret) == ch.charAt(0)) {
                textArea.moveTo(caret + 1);
                event.consume();
            }
            return;
        }

        if ("\"'".contains(ch)) {
            if (!hasSelection && caret < text.length() && text.charAt(caret) == ch.charAt(0)) {
                textArea.moveTo(caret + 1);
                event.consume();
                return;
            }
        }

        switch (ch) {
            case "(":
                insertPair("(", ")", hasSelection);
                event.consume();
                break;
            case "[":
                insertPair("[", "]", hasSelection);
                event.consume();
                break;
            case "{":
                if (BRACE_LANGUAGES.contains(currentLanguage)) {
                    insertPair("{", "}", hasSelection);
                    event.consume();
                }
                break;
            case "\"":
                if (shouldPairQuote(caret, text)) {
                    insertPair("\"", "\"", hasSelection);
                    event.consume();
                }
                break;
            case "'":
                if (shouldPairSingleQuote(caret, text)) {
                    insertPair("'", "'", hasSelection);
                    event.consume();
                }
                break;
            default:
                break;
        }
    }

    private void insertPair(String open, String close, boolean hasSelection) {
        if (hasSelection) {
            String selected = textArea.getSelectedText();
            int start = textArea.getSelection().getStart();
            textArea.replaceSelection(open + selected + close);
            textArea.selectRange(start + 1, start + 1 + selected.length());
        } else {
            int caret = textArea.getCaretPosition();
            textArea.insertText(caret, open + close);
            textArea.moveTo(caret + 1);
        }
    }

    private boolean shouldPairQuote(int caret, String text) {
        if (caret < text.length() && text.charAt(caret) == '"') {
            return false;
        }
        return true;
    }

    private boolean shouldPairSingleQuote(int caret, String text) {
        char prev = caret > 0 ? text.charAt(caret - 1) : '\0';
        char next = caret < text.length() ? text.charAt(caret) : '\0';
        if (Character.isLetterOrDigit(prev) && Character.isLetterOrDigit(next)) {
            return false;
        }
        return !(caret < text.length() && text.charAt(caret) == '\'');
    }

    private void handleAutoIndent(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }

        int caret = textArea.getCaretPosition();
        String text = textArea.getText();
        int lineStart = Math.max(0, text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1);
        String lineBeforeCaret = text.substring(lineStart, caret);
        String baseIndent = leadingWhitespace(lineBeforeCaret);
        char nextChar = caret < text.length() ? text.charAt(caret) : '\0';

        boolean increaseIndent = shouldIncreaseIndent(lineBeforeCaret.trim());
        String indentForNewLine = baseIndent;
        if (increaseIndent) {
            indentForNewLine = baseIndent + INDENT_UNIT;
        } else if (isClosingBracket(nextChar) && baseIndent.length() >= INDENT_UNIT.length()) {
            indentForNewLine = baseIndent.substring(0, baseIndent.length() - INDENT_UNIT.length());
        }

        if (increaseIndent && isClosingBracket(nextChar)) {
            textArea.insertText(caret, "\n" + indentForNewLine + "\n" + baseIndent);
            textArea.moveTo(caret + 1 + indentForNewLine.length());
        } else {
            textArea.insertText(caret, "\n" + indentForNewLine);
            textArea.moveTo(caret + 1 + indentForNewLine.length());
        }

        event.consume();
    }

    private String leadingWhitespace(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') {
                break;
            }
            i++;
        }
        return s.substring(0, i);
    }

    private boolean shouldIncreaseIndent(String trimmedLine) {
        if (trimmedLine.isEmpty()) {
            return false;
        }
        if (currentLanguage == Language.PYTHON || currentLanguage == Language.YAML) {
            return trimmedLine.endsWith(":");
        }
        if (currentLanguage == Language.HTML || currentLanguage == Language.XML) {
            return trimmedLine.endsWith(">");
        }
        if (BRACE_LANGUAGES.contains(currentLanguage)) {
            return trimmedLine.endsWith("{");
        }
        return trimmedLine.endsWith("{") || trimmedLine.endsWith(":");
    }

    private boolean isClosingBracket(char c) {
        return c == '}' || c == ')' || c == ']';
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
        if (syntaxHighlightingEnabled) {
            applySyntaxHighlightingDelayed();
        }
    }

    public void setFont(Font font) {
        if (font == null) {
            return;
        }
        fontFamily = font.getFamily();
        fontSize = font.getSize();
        applyEditorStyle();
        if (syntaxHighlightingEnabled) {
            applySyntaxHighlightingDelayed();
        }
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
        if (syntaxHighlightingEnabled) {
            applySyntaxHighlightingDelayed();
        }
    }

    public void updateTheme() {
        updateThemePalette();
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

        if (syntaxHighlightingEnabled) {
            applySyntaxHighlightingDelayed();
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

    public void setLanguage(Language language) {
        this.currentLanguage = language;
        setupPatterns(language);
        if (syntaxHighlightingEnabled) {
            applySyntaxHighlightingDelayed();
        }
    }

    public void setLanguageFromFileName(String fileName) {
        setLanguage(detectLanguage(fileName));
    }

    public Language getLanguage() {
        return currentLanguage;
    }

    public void setSyntaxHighlightingEnabled(boolean enabled) {
        this.syntaxHighlightingEnabled = enabled;
        if (enabled) {
            applySyntaxHighlightingDelayed();
        }
    }

    public boolean isSyntaxHighlightingEnabled() {
        return syntaxHighlightingEnabled;
    }

    private Language detectLanguage(String fileName) {
        if (fileName == null) {
            return Language.PLAIN_TEXT;
        }

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java")) return Language.JAVA;
        if (lower.endsWith(".py")) return Language.PYTHON;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Language.HTML;
        if (lower.endsWith(".c") || lower.endsWith(".h")) return Language.C;
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx") || lower.endsWith(".hpp")) return Language.CPP;

        return Language.PLAIN_TEXT;
    }

    private void setupPatterns(Language language) {
        String[] keywords;
        String[] types = null;
        boolean hasTypes = false;

        switch (language) {
            case JAVA:
                keywords = JAVA_KEYWORDS;
                types = JAVA_TYPES;
                hasTypes = true;
                break;
            case PYTHON:
                keywords = combineArrays(PYTHON_KEYWORDS, PYTHON_BUILTINS);
                break;
            case JAVASCRIPT:
            case TYPESCRIPT:
                keywords = JS_KEYWORDS;
                types = JS_TYPES;
                hasTypes = true;
                break;
            case HTML:
            case XML:
                keywords = HTML_TAGS;
                types = HTML_TAGS;
                hasTypes = true;
                break;
            case SQL:
                keywords = SQL_KEYWORDS;
                break;
            case C:
                keywords = C_KEYWORDS;
                types = C_TYPES;
                hasTypes = true;
                break;
            case CPP:
                keywords = CPP_KEYWORDS;
                types = CPP_TYPES;
                hasTypes = true;
                break;
            default:
                keywords = new String[0];
        }

        keywordPattern = createKeywordPattern(keywords);
        typePattern = hasTypes && types != null ? createKeywordPattern(types) : Pattern.compile("(?!)");
        preprocessorPattern = (language == Language.C || language == Language.CPP)
                ? Pattern.compile("(?m)^\\s*#\\s*[a-zA-Z_]\\w*[^\\r\\n]*$")
                : Pattern.compile("(?!)");

        switch (language) {
            case PYTHON:
            case SHELL:
            case YAML:
                commentPattern = Pattern.compile("#.*$", Pattern.MULTILINE);
                break;
            case HTML:
            case XML:
                commentPattern = Pattern.compile("<!--[\\s\\S]*?-->", Pattern.MULTILINE | Pattern.DOTALL);
                break;
            case SQL:
                commentPattern = Pattern.compile("--.*$|/\\*[\\s\\S]*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
                break;
            default:
                commentPattern = Pattern.compile("//[^\\n]*|/\\*(.|\\R)*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
        }

        stringPattern = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`[^`]*`");
        numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b|\\b0x[0-9a-fA-F]+\\b|\\b0b[01]+\\b");

        if (language == Language.HTML || language == Language.XML) {
            tagPattern = Pattern.compile("</?[^>]+>");
        } else {
            tagPattern = Pattern.compile("(?!)");
        }
    }

    private String[] combineArrays(String[]... arrays) {
        int totalLength = 0;
        for (String[] arr : arrays) {
            totalLength += arr.length;
        }

        String[] result = new String[totalLength];
        int index = 0;
        for (String[] arr : arrays) {
            System.arraycopy(arr, 0, result, index, arr.length);
            index += arr.length;
        }
        return result;
    }

    private Pattern createKeywordPattern(String[] keywords) {
        if (keywords == null || keywords.length == 0) {
            return Pattern.compile("(?!)");
        }
        return Pattern.compile("\\b(" + String.join("|", keywords) + ")\\b");
    }

    private void applySyntaxHighlightingDelayed() {
        Platform.runLater(this::applySyntaxHighlighting);
    }

    private void applySyntaxHighlighting() {
        if (!syntaxHighlightingEnabled || currentLanguage == Language.PLAIN_TEXT) {
            return;
        }

        String text = textArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        StringBuilder combined = new StringBuilder();
        combined.append("(?<KEYWORD>").append(keywordPattern.pattern()).append(")");
        combined.append("|(?<TYPE>").append(typePattern.pattern()).append(")");
        combined.append("|(?<COMMENT>").append(commentPattern.pattern()).append(")");
        combined.append("|(?<STRING>").append(stringPattern.pattern()).append(")");
        combined.append("|(?<NUMBER>").append(numberPattern.pattern()).append(")");
        combined.append("|(?<TAG>").append(tagPattern.pattern()).append(")");
        combined.append("|(?<PREPROC>").append(preprocessorPattern.pattern()).append(")");

        final Pattern combinedPattern;
        try {
            combinedPattern = Pattern.compile(combined.toString(), Pattern.MULTILINE | Pattern.DOTALL);
        } catch (Exception ignored) {
            return;
        }

        Matcher matcher = combinedPattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String style = getStyleForMatch(matcher);
            if (style == null) {
                continue;
            }

            if (start > lastEnd) {
                textArea.setStyle(lastEnd, start, "");
            }
            textArea.setStyle(start, end, style);
            lastEnd = end;
        }

        if (lastEnd < text.length()) {
            textArea.setStyle(lastEnd, text.length(), "");
        }
    }

    private String getStyleForMatch(Matcher matcher) {
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();

        if (matcher.group("KEYWORD") != null) {
            if (theme == ThemeManager.Theme.TOKYO_NIGHT) return "-fx-fill: #f7768e;";
            if (theme == ThemeManager.Theme.CATPPUCCIN) return "-fx-fill: #cba6f7;";
            if (theme == ThemeManager.Theme.DARK) return "-fx-fill: #569cd6;";
            return "-fx-fill: #0000ff; -fx-font-weight: bold;";
        }

        if (matcher.group("TYPE") != null) {
            if (theme == ThemeManager.Theme.TOKYO_NIGHT) return "-fx-fill: #73daca;";
            if (theme == ThemeManager.Theme.CATPPUCCIN) return "-fx-fill: #f9e2af;";
            if (theme == ThemeManager.Theme.DARK) return "-fx-fill: #4ec9b0;";
            return "-fx-fill: #267f99;";
        }

        if (matcher.group("COMMENT") != null) {
            if (theme == ThemeManager.Theme.TOKYO_NIGHT) return "-fx-fill: #565f89; -fx-font-style: italic;";
            if (theme == ThemeManager.Theme.CATPPUCCIN) return "-fx-fill: #6c7086; -fx-font-style: italic;";
            if (theme == ThemeManager.Theme.DARK) return "-fx-fill: #6a9955; -fx-font-style: italic;";
            return "-fx-fill: #008000; -fx-font-style: italic;";
        }

        if (matcher.group("STRING") != null) {
            if (theme == ThemeManager.Theme.TOKYO_NIGHT) return "-fx-fill: #9ece6a;";
            if (theme == ThemeManager.Theme.CATPPUCCIN) return "-fx-fill: #a6e3a1;";
            if (theme == ThemeManager.Theme.DARK) return "-fx-fill: #ce9178;";
            return "-fx-fill: #a31515;";
        }

        if (matcher.group("NUMBER") != null) {
            if (theme == ThemeManager.Theme.TOKYO_NIGHT) return "-fx-fill: #ff9e64;";
            if (theme == ThemeManager.Theme.CATPPUCCIN) return "-fx-fill: #fab387;";
            if (theme == ThemeManager.Theme.DARK) return "-fx-fill: #b5cea8;";
            return "-fx-fill: #098658;";
        }

        if (matcher.group("TAG") != null) {
            if (theme == ThemeManager.Theme.TOKYO_NIGHT) return "-fx-fill: #7aa2f7;";
            if (theme == ThemeManager.Theme.CATPPUCCIN) return "-fx-fill: #89b4fa;";
            if (theme == ThemeManager.Theme.DARK) return "-fx-fill: #4ec9b0;";
            return "-fx-fill: #800000;";
        }

        if (matcher.group("PREPROC") != null) {
            if (theme == ThemeManager.Theme.TOKYO_NIGHT) return "-fx-fill: #bb9af7; -fx-font-weight: bold;";
            if (theme == ThemeManager.Theme.CATPPUCCIN) return "-fx-fill: #cba6f7; -fx-font-weight: bold;";
            if (theme == ThemeManager.Theme.DARK) return "-fx-fill: #c586c0; -fx-font-weight: bold;";
            return "-fx-fill: #7c3aed; -fx-font-weight: bold;";
        }

        return null;
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
