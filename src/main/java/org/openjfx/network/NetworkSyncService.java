package org.openjfx.network;

import org.openjfx.SettingsManager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class NetworkSyncService {

    private NetworkSyncService() {
    }

    public static boolean isConfigured() {
        return SettingsManager.isNetworkEnabled()
            && !SettingsManager.getNetworkBaseUrl().isEmpty();
    }

    public static CompletableFuture<Boolean> syncNoteAsync(String username, String noteName, String content) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> sendNote(username, username, noteName, content));
    }

    public static CompletableFuture<Boolean> syncRemoteNoteAsync(String username, String author, String noteName, String content) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> sendNote(username, author, noteName, content));
    }

    public static CompletableFuture<Boolean> deleteNoteAsync(String username, String noteName) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> deleteRemoteNote(username, noteName));
    }

    public static CompletableFuture<DeleteResult> deleteNoteDetailedAsync(String username, String noteName) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(new DeleteResult(false, "Network sync is disabled"));
        }
        return CompletableFuture.supplyAsync(() -> deleteRemoteNoteDetailed(username, noteName));
    }

    public static CompletableFuture<Integer> deleteRemoteByAuthorAsync(String username, String author) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> deleteRemoteByAuthor(username, author));
    }

    public static CompletableFuture<List<String>> listRemoteNotesAsync(String username) {
        return listRemoteNotesDetailedAsync(username)
            .thenApply(items -> items.stream().map(RemoteNoteRef::noteName).collect(Collectors.toList()));
    }

    public static CompletableFuture<List<RemoteNoteRef>> listRemoteNotesDetailedAsync(String username) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> fetchRemoteNoteList(username));
    }

    public static CompletableFuture<RemoteNote> downloadRemoteNoteAsync(String username, String noteName) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> fetchRemoteNote(username, noteName));
    }

    public static CompletableFuture<RemoteNote> downloadRemoteNoteAsync(String username, String noteName, String author) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> fetchRemoteNote(username, noteName, author));
    }

    public static CompletableFuture<SyncSummary> syncAllNotesAsync(String username, File notesDir) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(new SyncSummary(0, 0, 0, true, new ArrayList<>()));
        }
        return CompletableFuture.supplyAsync(() -> {
            int attempted = 0;
            int success = 0;
            int failed = 0;
            List<String> errorSamples = new ArrayList<>();

            File[] files = notesDir.listFiles((dir, name) -> !name.startsWith(".") && new File(dir, name).isFile());
            if (files == null || files.length == 0) {
                return new SyncSummary(0, 0, 0, true, errorSamples);
            }

            for (File file : files) {
                attempted++;
                try {
                    String noteName = toRemoteNoteName(file.getName());
                    String content = Files.readString(file.toPath());
                    SendResult sendResult = sendNoteDetailed(username, username, noteName, content);
                    if (sendResult.success()) {
                        success++;
                    } else {
                        failed++;
                        if (errorSamples.size() < 3) {
                            errorSamples.add(noteName + ": " + sendResult.message());
                        }
                    }
                } catch (IOException e) {
                    failed++;
                    if (errorSamples.size() < 3) {
                        errorSamples.add(file.getName() + ": " + e.getMessage());
                    }
                }
            }
            return new SyncSummary(attempted, success, failed, true, errorSamples);
        });
    }

    public static CompletableFuture<SyncSummary> syncSelectedNotesAsync(String username, String author, File notesDir, List<String> noteNames) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(new SyncSummary(0, 0, 0, true, new ArrayList<>()));
        }
        if (noteNames == null || noteNames.isEmpty()) {
            return CompletableFuture.completedFuture(new SyncSummary(0, 0, 0, true, new ArrayList<>()));
        }
        return CompletableFuture.supplyAsync(() -> {
            int attempted = 0;
            int success = 0;
            int failed = 0;
            List<String> errorSamples = new ArrayList<>();

            for (String noteName : noteNames) {
                if (noteName == null || noteName.isBlank()) {
                    continue;
                }
                attempted++;
                try {
                    File source = findLocalNoteFile(notesDir, noteName);
                    if (source == null || !source.exists()) {
                        failed++;
                        if (errorSamples.size() < 3) {
                            errorSamples.add(noteName + ": local file not found");
                        }
                        continue;
                    }
                    String content = Files.readString(source.toPath());
                    String remoteName = toRemoteNoteName(source.getName());
                    SendResult sendResult = sendNoteDetailed(username, author, remoteName, content);
                    if (sendResult.success()) {
                        success++;
                    } else {
                        failed++;
                        if (errorSamples.size() < 3) {
                            errorSamples.add(remoteName + ": " + sendResult.message());
                        }
                    }
                } catch (IOException e) {
                    failed++;
                    if (errorSamples.size() < 3) {
                        errorSamples.add(noteName + ": " + e.getMessage());
                    }
                }
            }
            return new SyncSummary(attempted, success, failed, true, errorSamples);
        });
    }

    public static CompletableFuture<SyncSummary> syncAllNotesAsync(String username, String author, File notesDir) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(new SyncSummary(0, 0, 0, true, new ArrayList<>()));
        }
        return CompletableFuture.supplyAsync(() -> {
            int attempted = 0;
            int success = 0;
            int failed = 0;
            List<String> errorSamples = new ArrayList<>();

            File[] files = notesDir.listFiles((dir, name) -> !name.startsWith(".") && new File(dir, name).isFile());
            if (files == null || files.length == 0) {
                return new SyncSummary(0, 0, 0, true, errorSamples);
            }

            for (File file : files) {
                attempted++;
                try {
                    String noteName = toRemoteNoteName(file.getName());
                    String content = Files.readString(file.toPath());
                    SendResult sendResult = sendNoteDetailed(username, author, noteName, content);
                    if (sendResult.success()) {
                        success++;
                    } else {
                        failed++;
                        if (errorSamples.size() < 3) {
                            errorSamples.add(noteName + ": " + sendResult.message());
                        }
                    }
                } catch (IOException e) {
                    failed++;
                    if (errorSamples.size() < 3) {
                        errorSamples.add(file.getName() + ": " + e.getMessage());
                    }
                }
            }
            return new SyncSummary(attempted, success, failed, true, errorSamples);
        });
    }

    private static boolean sendNote(String username, String author, String noteName, String content) {
        return sendNoteDetailed(username, author, noteName, content).success();
    }

    private static SendResult sendNoteDetailed(String username, String author, String noteName, String content) {
        try {
            HttpResponse<String> response = createClient().send(buildUpsertRequest(username, author, noteName, content), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new SendResult(true, "OK");
            }
            return new SendResult(false, "HTTP " + response.statusCode());
        } catch (Exception e) {
            return new SendResult(false, e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private static boolean deleteRemoteNote(String username, String noteName) {
        return deleteRemoteNoteDetailed(username, noteName).success();
    }

    private static DeleteResult deleteRemoteNoteDetailed(String username, String noteName) {
        try {
            HttpResponse<String> response = createClient().send(buildDeleteRequest(username, noteName), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new DeleteResult(true, "OK");
            }
            return new DeleteResult(false, "HTTP " + response.statusCode());
        } catch (Exception e) {
            return new DeleteResult(false, e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private static int deleteRemoteByAuthor(String username, String author) {
        try {
            HttpResponse<String> response = createClient().send(buildDeleteByAuthorRequest(username, author), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return 0;
            }
            String body = response.body();
            if (body == null) {
                return 0;
            }
            String marker = "\"deleted\":";
            int idx = body.indexOf(marker);
            if (idx < 0) {
                return 0;
            }
            int start = idx + marker.length();
            int end = start;
            while (end < body.length() && Character.isDigit(body.charAt(end))) {
                end++;
            }
            if (end <= start) {
                return 0;
            }
            return Integer.parseInt(body.substring(start, end));
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<RemoteNoteRef> fetchRemoteNoteList(String username) {
        try {
            HttpResponse<String> response = createClient().send(buildListRequest(username), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Remote list failed with HTTP " + response.statusCode());
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return List.of();
            }
            return Arrays.stream(body.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(NetworkSyncService::parseRemoteRefLine)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(RemoteNoteRef::noteName))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch remote note list: " + safeMessage(e), e);
        }
    }

    private static RemoteNote fetchRemoteNote(String username, String noteName) {
        return fetchRemoteNote(username, noteName, null);
    }

    private static RemoteNote fetchRemoteNote(String username, String noteName, String author) {
        try {
            HttpResponse<String> response = createClient().send(buildFetchRequest(username, noteName, author), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 405) {
                    throw new IllegalStateException("Server does not support download endpoint yet. Restart backend with latest code.");
                }
                throw new IllegalStateException("Remote download failed with HTTP " + response.statusCode());
            }
            String returnedAuthor = response.headers().firstValue("X-QuillPad-Author").orElse("");
            return new RemoteNote(noteName, response.body() == null ? "" : response.body(), returnedAuthor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download remote note \"" + noteName + "\": " + safeMessage(e), e);
        }
    }

    private static HttpClient createClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(SettingsManager.getNetworkTimeoutSeconds()))
            .build();
    }

    private static HttpRequest buildUpsertRequest(String username, String author, String noteName, String content) {
        String json = "{"
            + "\"username\":\"" + escapeJson(username) + "\","
            + "\"author\":\"" + escapeJson(author == null ? "" : author) + "\","
            + "\"noteName\":\"" + escapeJson(noteName) + "\","
            + "\"content\":\"" + escapeJson(content) + "\""
            + "}";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + "/api/notes/sync"))
            .timeout(Duration.ofSeconds(SettingsManager.getNetworkTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));

        attachApiKey(builder);
        return builder.build();
    }

    private static HttpRequest buildDeleteRequest(String username, String noteName) {
        String encodedUser = URLEncoder.encode(username == null ? "" : username, StandardCharsets.UTF_8);
        String encodedNote = URLEncoder.encode(noteName == null ? "" : noteName, StandardCharsets.UTF_8);
        String uri = baseUrl() + "/api/notes?username=" + encodedUser + "&noteName=" + encodedNote;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(SettingsManager.getNetworkTimeoutSeconds()))
            .DELETE();

        attachApiKey(builder);
        return builder.build();
    }

    private static HttpRequest buildDeleteByAuthorRequest(String username, String author) {
        String encodedUser = URLEncoder.encode(username == null ? "" : username, StandardCharsets.UTF_8);
        String encodedAuthor = URLEncoder.encode(author == null ? "" : author, StandardCharsets.UTF_8);
        String uri = baseUrl() + "/api/notes/by-author?username=" + encodedUser + "&author=" + encodedAuthor;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(SettingsManager.getNetworkTimeoutSeconds()))
            .DELETE();

        attachApiKey(builder);
        return builder.build();
    }

    private static HttpRequest buildListRequest(String username) {
        String encodedUser = URLEncoder.encode(username == null ? "" : username, StandardCharsets.UTF_8);
        String uri = baseUrl() + "/api/notes?username=" + encodedUser;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(SettingsManager.getNetworkTimeoutSeconds()))
            .GET();
        attachApiKey(builder);
        return builder.build();
    }

    private static HttpRequest buildFetchRequest(String username, String noteName) {
        return buildFetchRequest(username, noteName, null);
    }

    private static HttpRequest buildFetchRequest(String username, String noteName, String author) {
        String encodedUser = URLEncoder.encode(username == null ? "" : username, StandardCharsets.UTF_8);
        String encodedNote = URLEncoder.encode(noteName == null ? "" : noteName, StandardCharsets.UTF_8);
        String uri = baseUrl() + "/api/notes/content?username=" + encodedUser + "&noteName=" + encodedNote;
        if (author != null && !author.isBlank()) {
            uri += "&author=" + URLEncoder.encode(author, StandardCharsets.UTF_8);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(SettingsManager.getNetworkTimeoutSeconds()))
            .GET();
        attachApiKey(builder);
        return builder.build();
    }

    private static String baseUrl() {
        String baseUrl = SettingsManager.getNetworkBaseUrl();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static void attachApiKey(HttpRequest.Builder builder) {
        String apiKey = SettingsManager.getNetworkApiKey();
        if (!apiKey.isEmpty()) {
            builder.header("X-API-Key", apiKey);
        }
    }

    private static String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0) {
            return filename;
        }
        return filename.substring(0, lastDot);
    }

    private static File findLocalNoteFile(File notesDir, String noteName) {
        if (notesDir == null || noteName == null) {
            return null;
        }
        File exact = new File(notesDir, noteName);
        if (exact.exists() && exact.isFile()) {
            return exact;
        }
        File[] candidates = notesDir.listFiles((dir, name) -> removeExtension(name).equals(noteName));
        if (candidates != null && candidates.length > 0) {
            return candidates[0];
        }
        return new File(notesDir, noteName + ".txt");
    }

    private static String toRemoteNoteName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.toLowerCase().endsWith(".txt") ? removeExtension(fileName) : fileName;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String safeMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return "no message";
        }
        return e.getMessage();
    }

    private record SendResult(boolean success, String message) {
    }

    private static RemoteNoteRef parseRemoteRefLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\t", 2);
        String noteName = parts[0].trim();
        if (noteName.isBlank()) {
            return null;
        }
        String author = parts.length > 1 ? parts[1].trim() : "";
        return new RemoteNoteRef(noteName, author);
    }

    public record SyncSummary(int attempted, int success, int failed, boolean networkConfigured, List<String> errorSamples) {
    }

    public record DeleteResult(boolean success, String message) {
    }

    public record RemoteNote(String noteName, String content, String author) {
    }

    public record RemoteNoteRef(String noteName, String author) {
    }
}
