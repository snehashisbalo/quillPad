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
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        return CompletableFuture.supplyAsync(() -> sendNote(username, noteName, content));
    }

    public static CompletableFuture<Boolean> deleteNoteAsync(String username, String noteName) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> deleteRemoteNote(username, noteName));
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
                    String noteName = removeExtension(file.getName());
                    String content = Files.readString(file.toPath());
                    SendResult sendResult = sendNoteDetailed(username, noteName, content);
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

    private static boolean sendNote(String username, String noteName, String content) {
        return sendNoteDetailed(username, noteName, content).success();
    }

    private static SendResult sendNoteDetailed(String username, String noteName, String content) {
        try {
            HttpResponse<String> response = createClient().send(buildUpsertRequest(username, noteName, content), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new SendResult(true, "OK");
            }
            return new SendResult(false, "HTTP " + response.statusCode());
        } catch (Exception e) {
            return new SendResult(false, e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private static boolean deleteRemoteNote(String username, String noteName) {
        try {
            HttpResponse<String> response = createClient().send(buildDeleteRequest(username, noteName), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static HttpClient createClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(SettingsManager.getNetworkTimeoutSeconds()))
            .build();
    }

    private static HttpRequest buildUpsertRequest(String username, String noteName, String content) {
        String json = "{"
            + "\"username\":\"" + escapeJson(username) + "\","
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

    public record SyncSummary(int attempted, int success, int failed, boolean networkConfigured, List<String> errorSamples) {
    }
}
