package org.openjfx.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class NoteSyncServer {

    private static final int DEFAULT_PORT = 8080;
    private static final Path BASE_DIR = Paths.get("remote-notes");
    private static final String AUTHOR_NOTE_SEPARATOR = "__qp_author__";

    public static void main(String[] args) throws IOException {
        int port = readPort(args);
        String requiredApiKey = System.getenv("QUILLPAD_API_KEY");

        Files.createDirectories(BASE_DIR);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/notes/sync", new SyncHandler(requiredApiKey));
        server.createContext("/api/notes", new NotesHandler(requiredApiKey));
        server.createContext("/api/notes/content", new NoteContentHandler(requiredApiKey));
        server.createContext("/api/notes/by-author", new DeleteByAuthorHandler(requiredApiKey));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("QuillPad Note Sync Server running on http://localhost:" + port);
        System.out.println("Storage directory: " + BASE_DIR.toAbsolutePath());
        if (requiredApiKey == null || requiredApiKey.isBlank()) {
            System.out.println("Auth: disabled (QUILLPAD_API_KEY not set)");
        } else {
            System.out.println("Auth: enabled via X-API-Key");
        }
    }

    private static int readPort(String[] args) {
        if (args != null && args.length > 0) {
            try {
                int parsed = Integer.parseInt(args[0]);
                if (parsed > 0 && parsed < 65536) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_PORT;
    }

    private static class SyncHandler implements HttpHandler {
        private final String requiredApiKey;

        private SyncHandler(String requiredApiKey) {
            this.requiredApiKey = requiredApiKey;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                    return;
                }
                if (!isAuthorized(exchange, requiredApiKey)) {
                    sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                    return;
                }

                String body = readBody(exchange.getRequestBody());
                String username = extractJsonString(body, "username");
                String noteName = extractJsonString(body, "noteName");
                String content = extractJsonString(body, "content");
                String author = extractJsonString(body, "author");

                if (isBlank(username) || isBlank(noteName)) {
                    sendJson(exchange, 400, "{\"error\":\"username and noteName are required\"}");
                    return;
                }

                String safeUser = sanitizeSegment(username);
                String safeNote = sanitizeSegment(noteName);
                if (safeUser.isBlank() || safeNote.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Invalid username or noteName\"}");
                    return;
                }

                Path userDir = BASE_DIR.resolve(safeUser);
                Files.createDirectories(userDir);
                String safeAuthor = isBlank(author) ? safeUser : sanitizeSegment(author);
                String storedNoteName = toAuthorScopedNoteName(safeNote, safeAuthor);
                Path noteFile = userDir.resolve(storedNoteName);
                Files.writeString(noteFile, content == null ? "" : content, StandardCharsets.UTF_8);
                Path authorFile = userDir.resolve(storedNoteName + ".author");
                Files.writeString(authorFile, safeAuthor, StandardCharsets.UTF_8);

                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private static class NotesHandler implements HttpHandler {
        private final String requiredApiKey;

        private NotesHandler(String requiredApiKey) {
            this.requiredApiKey = requiredApiKey;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!isAuthorized(exchange, requiredApiKey)) {
                    sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                    return;
                }

                Map<String, String> query = parseQuery(exchange.getRequestURI());
                String username = query.get("username");
                if (isBlank(username)) {
                    sendJson(exchange, 400, "{\"error\":\"username is required\"}");
                    return;
                }

                String safeUser = sanitizeSegment(username);
                Path userDir = BASE_DIR.resolve(safeUser);
                String method = exchange.getRequestMethod();
                if ("DELETE".equalsIgnoreCase(method)) {
                    String noteName = query.get("noteName");
                    if (isBlank(noteName)) {
                        sendJson(exchange, 400, "{\"error\":\"noteName is required\"}");
                        return;
                    }
                    String safeNote = sanitizeSegment(noteName);
                    String author = query.get("author");
                    if (!isBlank(author)) {
                        String safeAuthor = sanitizeSegment(author);
                        String scopedName = toAuthorScopedNoteName(safeNote, safeAuthor);
                        Files.deleteIfExists(userDir.resolve(scopedName));
                        Files.deleteIfExists(userDir.resolve(scopedName + ".txt"));
                        Files.deleteIfExists(userDir.resolve(scopedName + ".author"));
                    } else {
                        Path noteFile = resolveNoteFile(userDir, safeNote);
                        Files.deleteIfExists(noteFile);
                        Files.deleteIfExists(userDir.resolve(safeNote + ".author"));
                    }
                    sendJson(exchange, 200, "{\"status\":\"ok\"}");
                    return;
                }
                if ("GET".equalsIgnoreCase(method)) {
                    if (!Files.exists(userDir) || !Files.isDirectory(userDir)) {
                        sendText(exchange, 200, "");
                        return;
                    }
                    List<String> lines = new ArrayList<>();
                    try (var stream = Files.list(userDir)) {
                        stream.filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .filter(name -> !name.endsWith(".author"))
                            .forEach(storedName -> {
                                String[] parsed = parseAuthorScopedNoteName(storedName);
                                String noteNameForList = parsed != null ? parsed[0] : storedName;
                                String authorForList = parsed != null ? parsed[1] : resolveAuthor(userDir, storedName, safeUser);
                                lines.add(noteNameForList + "\t" + authorForList);
                            });
                    }
                    lines.sort(Comparator.naturalOrder());
                    sendText(exchange, 200, String.join("\n", lines));
                    return;
                }
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private static class NoteContentHandler implements HttpHandler {
        private final String requiredApiKey;

        private NoteContentHandler(String requiredApiKey) {
            this.requiredApiKey = requiredApiKey;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                    return;
                }
                if (!isAuthorized(exchange, requiredApiKey)) {
                    sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                    return;
                }
                Map<String, String> query = parseQuery(exchange.getRequestURI());
                String username = query.get("username");
                String noteName = query.get("noteName");
                String author = query.get("author");
                if (isBlank(username) || isBlank(noteName)) {
                    sendJson(exchange, 400, "{\"error\":\"username and noteName are required\"}");
                    return;
                }
                String safeUser = sanitizeSegment(username);
                String safeNote = sanitizeSegment(noteName);
                Path userDir = BASE_DIR.resolve(safeUser);
                Path noteFile;
                if (!isBlank(author)) {
                    String safeAuthor = sanitizeSegment(author);
                    noteFile = resolveAuthorScopedNoteFile(userDir, safeNote, safeAuthor);
                    if (!Files.exists(noteFile)) {
                        Path legacy = resolveNoteFile(userDir, safeNote);
                        if (Files.exists(legacy)) {
                            String legacyAuthor = resolveAuthor(userDir, legacy.getFileName().toString(), safeUser);
                            if (safeAuthor.equals(legacyAuthor)) {
                                noteFile = legacy;
                            }
                        }
                    }
                } else {
                    noteFile = resolveNoteFile(userDir, safeNote);
                }
                if (!Files.exists(noteFile)) {
                    sendJson(exchange, 404, "{\"error\":\"Note not found\"}");
                    return;
                }
                String content = Files.readString(noteFile, StandardCharsets.UTF_8);
                String resolvedAuthor = resolveAuthor(userDir, noteFile.getFileName().toString(), safeUser);
                sendText(exchange, 200, content, resolvedAuthor);
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private static class DeleteByAuthorHandler implements HttpHandler {
        private final String requiredApiKey;

        private DeleteByAuthorHandler(String requiredApiKey) {
            this.requiredApiKey = requiredApiKey;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                    return;
                }
                if (!isAuthorized(exchange, requiredApiKey)) {
                    sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                    return;
                }
                Map<String, String> query = parseQuery(exchange.getRequestURI());
                String username = query.get("username");
                String author = query.get("author");
                if (isBlank(username) || isBlank(author)) {
                    sendJson(exchange, 400, "{\"error\":\"username and author are required\"}");
                    return;
                }
                String safeUser = sanitizeSegment(username);
                String safeAuthor = sanitizeSegment(author);
                Path userDir = BASE_DIR.resolve(safeUser);
                if (!Files.exists(userDir) || !Files.isDirectory(userDir)) {
                    sendJson(exchange, 200, "{\"status\":\"ok\",\"deleted\":0}");
                    return;
                }

                int deleted = 0;
                try (var stream = Files.list(userDir)) {
                    List<Path> authorFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".author"))
                        .toList();
                    for (Path authorFile : authorFiles) {
                        String fileAuthor = Files.readString(authorFile, StandardCharsets.UTF_8).trim();
                        if (!safeAuthor.equals(fileAuthor)) {
                            continue;
                        }
                        String fileName = authorFile.getFileName().toString();
                        String noteBase = fileName.substring(0, fileName.length() - ".author".length());
                        Path noteFile = resolveNoteFile(userDir, noteBase);
                        Files.deleteIfExists(noteFile);
                        Files.deleteIfExists(authorFile);
                        deleted++;
                    }
                }
                sendJson(exchange, 200, "{\"status\":\"ok\",\"deleted\":" + deleted + "}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    private static boolean isAuthorized(HttpExchange exchange, String requiredApiKey) {
        if (requiredApiKey == null || requiredApiKey.isBlank()) {
            return true;
        }
        String incoming = exchange.getRequestHeaders().getFirst("X-API-Key");
        return requiredApiKey.equals(incoming);
    }

    private static String readBody(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        sendText(exchange, status, body, null);
    }

    private static void sendText(HttpExchange exchange, int status, String body, String author) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        if (author != null && !author.isBlank()) {
            exchange.getResponseHeaders().set("X-QuillPad-Author", author);
        }
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static String resolveAuthor(Path userDir, String safeNote, String fallback) {
        try {
            Path authorFile = userDir.resolve(safeNote + ".author");
            if (Files.exists(authorFile)) {
                String author = Files.readString(authorFile, StandardCharsets.UTF_8).trim();
                if (!author.isBlank()) {
                    return author;
                }
            }
        } catch (Exception ignored) {
        }
        String[] parsed = parseAuthorScopedNoteName(safeNote);
        if (parsed != null && !parsed[1].isBlank()) {
            return parsed[1];
        }
        return fallback;
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) {
            return null;
        }
        String marker = "\"" + key + "\"";
        int keyIdx = json.indexOf(marker);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = json.indexOf(':', keyIdx + marker.length());
        if (colonIdx < 0) {
            return null;
        }

        int i = colonIdx + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;

        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        while (i < json.length()) {
            char ch = json.charAt(i++);
            if (escaped) {
                switch (ch) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    default:
                        out.append(ch);
                        break;
                }
                escaped = false;
                continue;
            }

            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                break;
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key;
            String value;
            if (eq < 0) {
                key = pair;
                value = "";
            } else {
                key = pair.substring(0, eq);
                value = pair.substring(eq + 1);
            }
            key = decodeQueryPart(key);
            value = decodeQueryPart(value);
            map.put(key, value);
        }
        return map;
    }

    private static String decodeQueryPart(String part) {
        return URLDecoder.decode(part, StandardCharsets.UTF_8);
    }

    private static Path resolveNoteFile(Path userDir, String safeNote) {
        Path direct = userDir.resolve(safeNote);
        if (Files.exists(direct)) {
            return direct;
        }
        Path txt = userDir.resolve(safeNote + ".txt");
        if (Files.exists(txt)) {
            return txt;
        }
        return direct;
    }

    private static Path resolveAuthorScopedNoteFile(Path userDir, String safeNote, String safeAuthor) {
        String scoped = toAuthorScopedNoteName(safeNote, safeAuthor);
        Path direct = userDir.resolve(scoped);
        if (Files.exists(direct)) {
            return direct;
        }
        Path txt = userDir.resolve(scoped + ".txt");
        if (Files.exists(txt)) {
            return txt;
        }
        return direct;
    }

    private static String toAuthorScopedNoteName(String safeNote, String safeAuthor) {
        return safeNote + AUTHOR_NOTE_SEPARATOR + safeAuthor;
    }

    private static String[] parseAuthorScopedNoteName(String storedName) {
        int split = storedName.lastIndexOf(AUTHOR_NOTE_SEPARATOR);
        if (split <= 0 || split >= storedName.length() - AUTHOR_NOTE_SEPARATOR.length()) {
            return null;
        }
        String note = storedName.substring(0, split);
        String author = storedName.substring(split + AUTHOR_NOTE_SEPARATOR.length());
        if (note.isBlank() || author.isBlank()) {
            return null;
        }
        return new String[]{note, author};
    }

    private static String sanitizeSegment(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
