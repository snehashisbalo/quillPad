package org.openjfx;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CacheManager {
    private static final String CACHE_DIR = ".quillpad_cache";
    private static final String RECENT_FILES_CACHE = "recent_files.cache";
    private static final String UNSAVED_CHANGES_CACHE = "unsaved_changes.cache";
    
    public static void ensureCacheDirExists() {
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }
    }
    
    public static void saveRecentFiles(List<String> files) {
        ensureCacheDirExists();
        File cacheFile = new File(CACHE_DIR, RECENT_FILES_CACHE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile))) {
            for (String file : files) {
                writer.write(file);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving recent files cache: " + e.getMessage());
        }
    }
    
    public static List<String> loadRecentFiles() {
        List<String> files = new ArrayList<>();
        File cacheFile = new File(CACHE_DIR, RECENT_FILES_CACHE);
        if (cacheFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    files.add(line);
                }
            } catch (IOException e) {
                System.err.println("Error loading recent files cache: " + e.getMessage());
            }
        }
        return files;
    }
    
    public static void saveUnsavedChanges(String filename, String content) {
        ensureCacheDirExists();
        File cacheFile = new File(CACHE_DIR, UNSAVED_CHANGES_CACHE + "." + sanitizeFilename(filename));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile))) {
            writer.write(content);
        } catch (IOException e) {
            System.err.println("Error saving unsaved changes: " + e.getMessage());
        }
    }
    
    public static String loadUnsavedChanges(String filename) {
        File cacheFile = new File(CACHE_DIR, UNSAVED_CHANGES_CACHE + "." + sanitizeFilename(filename));
        if (cacheFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString().trim();
            } catch (IOException e) {
                System.err.println("Error loading unsaved changes: " + e.getMessage());
            }
        }
        return null;
    }
    
    public static void clearUnsavedChanges(String filename) {
        File cacheFile = new File(CACHE_DIR, UNSAVED_CHANGES_CACHE + "." + sanitizeFilename(filename));
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }
    
    public static void clearAllCache() {
        File cacheDir = new File(CACHE_DIR);
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
    
    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
    
    public static String getCacheDir() {
        ensureCacheDirExists();
        return CACHE_DIR;
    }
}