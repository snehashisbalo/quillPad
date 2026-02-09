package org.quillpad.core.editor;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    public enum Language {
        PLAIN_TEXT,
        JAVA,
        PYTHON,
        JAVASCRIPT,
        HTML,
        CSS,
        XML
    }

    private static final Color KEYWORD_COLOR = Color.valueOf("#cc7832");
    private static final Color STRING_COLOR = Color.valueOf("#6a8759");
    private static final Color NUMBER_COLOR = Color.valueOf("#6897bb");
    private static final Color COMMENT_COLOR = Color.valueOf("#808080");
    private static final Color TYPE_COLOR = Color.valueOf("#4ec9b0");
    private static final Color DEFAULT_COLOR = Color.valueOf("#d4d4d4");

    private static final Pattern JAVA_KEYWORDS = Pattern.compile(
        "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null)\\b"
    );

    private static final Pattern PYTHON_KEYWORDS = Pattern.compile(
        "\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield|True|False|None)\\b"
    );

    private static final Pattern JS_KEYWORDS = Pattern.compile(
        "\\b(async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|let|new|return|static|super|switch|this|throw|try|typeof|var|void|while|with|yield|true|false|null|undefined)\\b"
    );

    private static final Pattern JAVA_TYPES = Pattern.compile(
        "\\b(String|Integer|Long|Byte|Short|Float|Double|Boolean|Character|Object|List|ArrayList|HashMap|Map|Set|Collection|Exception|RuntimeException|Throwable|Error|Thread|Runnable)\\b"
    );

    private static final Pattern SINGLE_LINE_COMMENT = Pattern.compile("//.*$", Pattern.MULTILINE);
    private static final Pattern MULTI_LINE_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern PYTHON_COMMENT = Pattern.compile("#.*$", Pattern.MULTILINE);

    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b");

    public static TextFlow highlight(String code, Language language) {
        TextFlow textFlow = new TextFlow();
        textFlow.setStyle("-fx-font-family: Consolas; -fx-font-size: 14px;");

        String processed = escapeHtml(code);
        processed = highlightComments(processed, language);
        processed = highlightStrings(processed);
        processed = highlightNumbers(processed);

        Pattern keywordPattern = getKeywordPattern(language);
        processed = applyKeywordPattern(processed, keywordPattern, "<K>", "</K>");

        if (language == Language.JAVA) {
            processed = applyPattern(processed, JAVA_TYPES, "<T>", "</T>");
        }

        processed = processed
            .replace("<K>", "\u0000")
            .replace("</K>", "\u0001")
            .replace("<T>", "\u0002")
            .replace("</T>", "\u0003")
            .replace("<S>", "\u0004")
            .replace("</S>", "\u0005")
            .replace("<N>", "\u0006")
            .replace("</N>", "\u0007")
            .replace("<C>", "\u0008")
            .replace("</C>", "\u0009");

        List<HighlightToken> tokens = tokenize(processed);

        for (HighlightToken token : tokens) {
            Text text = new Text(token.text);
            text.setFill(token.color);
            textFlow.getChildren().add(text);
        }

        return textFlow;
    }

    private static String escapeHtml(String code) {
        return code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String highlightComments(String code, Language language) {
        Matcher matcher;
        StringBuffer sb = new StringBuffer();

        if (language == Language.PYTHON) {
            matcher = PYTHON_COMMENT.matcher(code);
        } else if (language == Language.JAVA || language == Language.JAVASCRIPT) {
            String pattern = SINGLE_LINE_COMMENT.pattern() + "|" + MULTI_LINE_COMMENT.pattern();
            matcher = Pattern.compile(pattern, Pattern.MULTILINE | Pattern.DOTALL).matcher(code);
        } else {
            return code;
        }

        while (matcher.find()) {
            matcher.appendReplacement(sb, "<C>" + matcher.group() + "</C>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String highlightStrings(String code) {
        Matcher matcher = STRING_LITERAL.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<S>" + matcher.group() + "</S>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String highlightNumbers(String code) {
        Matcher matcher = NUMBER_LITERAL.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<N>" + matcher.group() + "</N>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String applyKeywordPattern(String code, Pattern pattern, String open, String close) {
        if (pattern.pattern().isEmpty()) {
            return code;
        }
        return applyPattern(code, pattern, open, close);
    }

    private static String applyPattern(String code, Pattern pattern, String open, String close) {
        Matcher matcher = pattern.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, open + matcher.group() + close);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Pattern getKeywordPattern(Language language) {
        switch (language) {
            case JAVA: return JAVA_KEYWORDS;
            case PYTHON: return PYTHON_KEYWORDS;
            case JAVASCRIPT: return JS_KEYWORDS;
            default: return Pattern.compile("");
        }
    }

    private static List<HighlightToken> tokenize(String text) {
        List<HighlightToken> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        Color currentColor = DEFAULT_COLOR;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '\u0000':
                    if (currentToken.length() > 0) {
                        tokens.add(new HighlightToken(currentToken.toString(), currentColor));
                        currentToken = new StringBuilder();
                    }
                    currentColor = KEYWORD_COLOR;
                    break;
                case '\u0001':
                    currentColor = DEFAULT_COLOR;
                    break;
                case '\u0002':
                    if (currentToken.length() > 0) {
                        tokens.add(new HighlightToken(currentToken.toString(), currentColor));
                        currentToken = new StringBuilder();
                    }
                    currentColor = TYPE_COLOR;
                    break;
                case '\u0003':
                    currentColor = DEFAULT_COLOR;
                    break;
                case '\u0004':
                    if (currentToken.length() > 0) {
                        tokens.add(new HighlightToken(currentToken.toString(), currentColor));
                        currentToken = new StringBuilder();
                    }
                    currentColor = STRING_COLOR;
                    break;
                case '\u0005':
                    currentColor = DEFAULT_COLOR;
                    break;
                case '\u0006':
                    if (currentToken.length() > 0) {
                        tokens.add(new HighlightToken(currentToken.toString(), currentColor));
                        currentToken = new StringBuilder();
                    }
                    currentColor = NUMBER_COLOR;
                    break;
                case '\u0007':
                    currentColor = DEFAULT_COLOR;
                    break;
                case '\u0008':
                    if (currentToken.length() > 0) {
                        tokens.add(new HighlightToken(currentToken.toString(), currentColor));
                        currentToken = new StringBuilder();
                    }
                    currentColor = COMMENT_COLOR;
                    break;
                case '\u0009':
                    currentColor = DEFAULT_COLOR;
                    break;
                default:
                    currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(new HighlightToken(currentToken.toString(), currentColor));
        }

        return tokens;
    }

    public static Language getLanguageForExtension(String fileName) {
        if (fileName == null) {
            return Language.PLAIN_TEXT;
        }

        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java")) return Language.JAVA;
        if (lower.endsWith(".py")) return Language.PYTHON;
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return Language.JAVASCRIPT;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Language.HTML;
        if (lower.endsWith(".css")) return Language.CSS;
        if (lower.endsWith(".xml") || lower.endsWith(".fxml")) return Language.XML;
        return Language.PLAIN_TEXT;
    }

    private static class HighlightToken {
        final String text;
        final Color color;

        HighlightToken(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }
}
