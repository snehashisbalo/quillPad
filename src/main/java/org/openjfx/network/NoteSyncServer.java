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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class NoteSyncServer {

    private static final int DEFAULT_PORT = 8080;
    private static final Path BASE_DIR = Paths.get("remote-notes");

    public static void main(String[] args) throws IOException {
        int port = readPort(args);
        String requiredApiKey = System.getenv("QUILLPAD_API_KEY");

        Files.createDirectories(BASE_DIR);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/notes/sync", new SyncHandler(requiredApiKey));
        server.createContext("/api/notes", new DeleteHandler(requiredApiKey));
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
                Path noteFile = userDir.resolve(safeNote + ".txt");
                Files.writeString(noteFile, content == null ? "" : content, StandardCharsets.UTF_8);

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

    private static class DeleteHandler implements HttpHandler {
        private final String requiredApiKey;

        private DeleteHandler(String requiredApiKey) {
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
                String noteName = query.get("noteName");
                if (isBlank(username) || isBlank(noteName)) {
                    sendJson(exchange, 400, "{\"error\":\"username and noteName are required\"}");
                    return;
                }

                String safeUser = sanitizeSegment(username);
                String safeNote = sanitizeSegment(noteName);
                Path noteFile = BASE_DIR.resolve(safeUser).resolve(safeNote + ".txt");

                if (Files.exists(noteFile)) {
                    Files.delete(noteFile);
                }

                sendJson(exchange, 200, "{\"status\":\"ok\"}");
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
