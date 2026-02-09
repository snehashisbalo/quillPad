package org.quillpad.core.git;

import javafx.application.Platform;
import javafx.beans.property.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Path repositoryPath;
    private Git git;
    private final BooleanProperty isRepository = new SimpleBooleanProperty(false);
    private final StringProperty currentBranch = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final List<GitListener> listeners = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GitService() {
    }

    public boolean openRepository(Path path) {
        try {
            File repoDir = findGitRepository(path);
            if (repoDir != null) {
                this.repositoryPath = repoDir.toPath();
                this.git = Git.open(repoDir);
                updateRepositoryInfo();
                notifyListeners(GitEvent.REPOSITORY_OPENED);
                return true;
            }
            return false;
        } catch (IOException e) {
            statusMessage.set("Failed to open repository: " + e.getMessage());
            return false;
        }
    }

    private File findGitRepository(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }

        Path current = path;
        while (current != null) {
            File gitDir = current.resolve(".git").toFile();
            if (gitDir.exists() && gitDir.isDirectory()) {
                return current.toFile();
            }
            current = current.getParent();
        }
        return null;
    }

    public boolean initRepository(Path path) {
        try {
            File repoDir = path.toFile();
            if (!repoDir.exists()) {
                repoDir.mkdirs();
            }
            this.git = Git.init().setDirectory(repoDir).call();
            this.repositoryPath = path;
            updateRepositoryInfo();
            notifyListeners(GitEvent.REPOSITORY_OPENED);
            return true;
        } catch (GitAPIException e) {
            statusMessage.set("Failed to initialize repository: " + e.getMessage());
            return false;
        }
    }

    private void updateRepositoryInfo() {
        if (git != null) {
            try {
                String branch = git.getRepository().getBranch();
                currentBranch.set(branch);
                isRepository.set(true);
            } catch (IOException e) {
                currentBranch.set("unknown");
            }
        }
    }

    public void stageFile(Path path) {
        executor.execute(() -> {
            try {
                git.add().addFilepattern(path.toFile().getName()).call();
                statusMessage.set("Staged: " + path.getFileName());
                notifyListeners(GitEvent.STATUS_CHANGED);
            } catch (GitAPIException e) {
                statusMessage.set("Failed to stage file: " + e.getMessage());
            }
        });
    }

    public void commit(String message) {
        executor.execute(() -> {
            try {
                git.commit().setMessage(message).call();
                statusMessage.set("Committed successfully");
                notifyListeners(GitEvent.COMMIT_CREATED);
            } catch (GitAPIException e) {
                statusMessage.set("Failed to commit: " + e.getMessage());
            }
        });
    }

    public void push() {
        executor.execute(() -> {
            try {
                statusMessage.set("Pushing...");
                git.push().call();
                statusMessage.set("Pushed successfully");
                notifyListeners(GitEvent.PUSH_COMPLETED);
            } catch (GitAPIException e) {
                statusMessage.set("Failed to push: " + e.getMessage());
            }
        });
    }

    public void pull() {
        executor.execute(() -> {
            try {
                statusMessage.set("Pulling...");
                git.pull().call();
                statusMessage.set("Pulled successfully");
                notifyListeners(GitEvent.PULL_COMPLETED);
            } catch (GitAPIException e) {
                statusMessage.set("Failed to pull: " + e.getMessage());
            }
        });
    }

    public List<GitCommit> getCommitHistory(int count) {
        List<GitCommit> commits = new ArrayList<>();
        if (git == null) return commits;

        try {
            Iterable<RevCommit> log = git.log().setMaxCount(count).call();
            for (RevCommit commit : log) {
                GitCommit gitCommit = new GitCommit();
                gitCommit.setHash(commit.getName());
                gitCommit.setShortHash(commit.getName().substring(0, 7));
                gitCommit.setMessage(commit.getFullMessage().split("\n")[0]);
                gitCommit.setAuthor(commit.getAuthorIdent().getName());
                gitCommit.setEmail(commit.getAuthorIdent().getEmailAddress());
                gitCommit.setDate(LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(),
                    java.time.ZoneId.systemDefault()));
                commits.add(gitCommit);
            }
        } catch (GitAPIException e) {
            statusMessage.set("Failed to get commit history: " + e.getMessage());
        }
        return commits;
    }

    public String getCurrentBranch() {
        return currentBranch.get();
    }

    public ReadOnlyStringProperty currentBranchProperty() {
        return currentBranch;
    }

    public boolean isRepository() {
        return isRepository.get();
    }

    public ReadOnlyBooleanProperty isRepositoryProperty() {
        return isRepository;
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    public ReadOnlyStringProperty statusMessageProperty() {
        return statusMessage;
    }

    public Path getRepositoryPath() {
        return repositoryPath;
    }

    public void addListener(GitListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(GitEvent event) {
        Platform.runLater(() -> {
            for (GitListener listener : listeners) {
                listener.onGitEvent(event);
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
        if (git != null) {
            git.close();
        }
    }

    public enum GitEvent {
        REPOSITORY_OPENED,
        STATUS_CHANGED,
        COMMIT_CREATED,
        PUSH_COMPLETED,
        PULL_COMPLETED
    }

    public interface GitListener {
        void onGitEvent(GitEvent event);
    }

    public static class GitCommit {
        private String hash;
        private String shortHash;
        private String message;
        private String author;
        private String email;
        private LocalDateTime date;

        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }

        public String getShortHash() { return shortHash; }
        public void setShortHash(String shortHash) { this.shortHash = shortHash; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public LocalDateTime getDate() { return date; }
        public void setDate(LocalDateTime date) { this.date = date; }

        public String getFormattedDate() {
            return date != null ? date.format(DATE_FORMATTER) : "";
        }
    }
}
