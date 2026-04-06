package org.openjfx.auth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class UserStore {

    private static final String USERS_FILE = "users.txt";

    private UserStore() {
    }

    public static boolean authenticate(String username, String password) {
        UserRecord user = loadUsers().get(username);
        return user != null && user.password().equals(password);
    }

    public static boolean userExists(String username) {
        return loadUsers().containsKey(username);
    }

    public static void registerUser(String username, String password) throws IOException {
        Files.createDirectories(Paths.get("."));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE, true))) {
            writer.write(username + ":" + password);
            writer.newLine();
        }
    }

    public static Map<String, UserRecord> loadUsers() {
        Map<String, UserRecord> users = new HashMap<>();
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            return users;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                UserRecord user = parseUser(line);
                if (user != null) {
                    users.put(user.username(), user);
                }
            }
        } catch (IOException e) {
            return users;
        }
        return users;
    }

    private static UserRecord parseUser(String line) {
        String[] parts = line.split(":", 3);
        if (parts.length == 3) {
            return new UserRecord(parts[0], parts[1], parts[2]);
        }

        String[] legacyParts = line.split(":", 2);
        if (legacyParts.length == 2) {
            return new UserRecord(legacyParts[0], legacyParts[0], legacyParts[1]);
        }

        return null;
    }

    public record UserRecord(String name, String username, String password) {
    }
}
