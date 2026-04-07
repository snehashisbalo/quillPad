package org.openjfx.auth;

import org.openjfx.AppConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UserStore {
    private static final Object FILE_LOCK = new Object();
    private static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private UserStore() {
    }

    public static boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        synchronized (FILE_LOCK) {
            Map<String, UserRecord> users = loadUsersInternal();
            UserRecord user = users.get(username);
            if (user == null) {
                return false;
            }

            if (user.isLegacy()) {
                boolean matches = password.equals(user.legacyPassword());
                if (matches) {
                    String salt = generateSalt();
                    String hashedPassword = hashPassword(salt, password);
                    users.put(username, user.migrated(salt, hashedPassword));
                    try {
                        writeUsers(users);
                    } catch (IOException ignored) {
                    }
                }
                return matches;
            }

            String hashedInput = hashPassword(user.salt(), password);
            return hashedInput.equals(user.passwordHash());
        }
    }

    public static boolean userExists(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        synchronized (FILE_LOCK) {
            return loadUsersInternal().containsKey(username);
        }
    }

    public static void registerUser(String username, String password) throws IOException {
        if (username == null || username.isBlank() || password == null) {
            throw new IOException("Username and password are required");
        }

        synchronized (FILE_LOCK) {
            Map<String, UserRecord> users = loadUsersInternal();
            if (users.containsKey(username)) {
                throw new IOException("User already exists");
            }

            String salt = generateSalt();
            String hashedPassword = hashPassword(salt, password);
            users.put(username, UserRecord.hashed(username, username, salt, hashedPassword));
            writeUsers(users);
        }
    }

    public static Map<String, UserRecord> loadUsers() {
        synchronized (FILE_LOCK) {
            return new LinkedHashMap<>(loadUsersInternal());
        }
    }

    private static Map<String, UserRecord> loadUsersInternal() {
        Map<String, UserRecord> users = new LinkedHashMap<>();
        File file = new File(AppConstants.USERS_FILE);
        if (!file.exists()) {
            return users;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                UserRecord user = parseUser(line);
                if (user != null) {
                    users.put(user.username(), user);
                }
            }
        } catch (IOException ignored) {
            return users;
        }
        return users;
    }

    private static UserRecord parseUser(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] hashedParts = trimmed.split(":", 3);
        if (hashedParts.length == 3
                && !hashedParts[0].isBlank()
                && !hashedParts[1].isBlank()
                && !hashedParts[2].isBlank()) {
            return UserRecord.hashed(hashedParts[0], hashedParts[0], hashedParts[1], hashedParts[2]);
        }

        String[] legacyParts = trimmed.split(":", 2);
        if (legacyParts.length == 2 && !legacyParts[0].isBlank() && !legacyParts[1].isBlank()) {
            return UserRecord.legacy(legacyParts[0], legacyParts[0], legacyParts[1]);
        }

        return null;
    }

    private static void writeUsers(Map<String, UserRecord> users) throws IOException {
        Files.createDirectories(Paths.get("."));
        Path file = Path.of(AppConstants.USERS_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (UserRecord user : users.values()) {
                writer.write(user.serialize());
                writer.newLine();
            }
        }
    }

    private static String generateSalt() {
        byte[] saltBytes = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    private static String hashPassword(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = (salt + password).getBytes(StandardCharsets.UTF_8);
            byte[] hashedBytes = digest.digest(input);
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record UserRecord(
            String name,
            String username,
            String salt,
            String passwordHash,
            String legacyPassword,
            boolean isLegacy
    ) {
        private static UserRecord hashed(String name, String username, String salt, String passwordHash) {
            return new UserRecord(name, username, salt, passwordHash, null, false);
        }

        private static UserRecord legacy(String name, String username, String legacyPassword) {
            return new UserRecord(name, username, null, null, legacyPassword, true);
        }

        private UserRecord migrated(String salt, String passwordHash) {
            return hashed(name, username, salt, passwordHash);
        }

        private String serialize() {
            return username + ":" + salt + ":" + passwordHash;
        }
    }
}
