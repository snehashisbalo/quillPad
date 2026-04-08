# Installation Guide

This guide covers local setup for the QuillPad desktop app and the optional bundled sync server.

## 1. Prerequisites

Install these first:

- Java 21 or newer
- Maven 3.6 or newer
- Git

Check your versions:

```bash
java -version
mvn -version
```

## 2. Get the project

```bash
git clone <your-repo-url>
cd QuillPadProject
```

## 3. Build the app

```bash
mvn clean package -DskipTests
```

This produces:

- `target/quillpad-1.0.0-SNAPSHOT.jar`

## 4. Run the desktop app

### Option A: run directly with Maven

```bash
mvn javafx:run
```

This is the safest development path because Maven resolves the JavaFX runtime for the current platform.

### Option B: run the packaged jar

```bash
java -jar target/quillpad-1.0.0-SNAPSHOT.jar
```

Use this after a successful `mvn package`.

## 5. First launch behavior

On first run, QuillPad creates and uses project-local data files:

- `users.txt`
- `quillpad.settings`
- `notes/`
- `remote-notes/` when the sync server is used

User notes are stored in:

```text
notes/<username>/
```

Deleted notes move to:

```text
notes/<username>/trash/
```

## 6. Create an account

Launch the app, register a user, then sign in. Credentials are stored in `users.txt`. Existing legacy plaintext records are migrated to hashed records when that user logs in successfully.

## 7. Optional: run the bundled sync server

Start the included local HTTP server:

```bash
./run-backend.sh
```

Run it on a custom port:

```bash
./run-backend.sh 9090
```

The server provides:

- `GET /api/health`
- `POST /api/notes/sync`
- `GET /api/notes`
- `DELETE /api/notes`
- `GET /api/notes/content`
- `DELETE /api/notes/by-author`

Server data is written under:

```text
remote-notes/
```

## 8. Enable sync inside QuillPad

In the app:

1. Sign in.
2. Open `Settings`.
3. Enable `Network Sync`.
4. Set `Server URL` to `http://localhost:8080` or your custom port.
5. Set the remote autosave interval if needed.
6. Save settings.

After that you can upload local notes, browse remote notes, download them, and use remote autosave for linked notes.

## 9. Optional API key protection

Protect the bundled server with an API key:

```bash
export QUILLPAD_API_KEY=my-secret
./run-backend.sh
```

If you enable this, the desktop app must be configured to send the same key. The repository currently stores an API key setting in `quillpad.settings`.
The current settings dialog does not expose that field, so set it manually:

```properties
network.api_key=my-secret
```

## 10. Platform notes

### macOS and Linux

Use:

```bash
mvn javafx:run
```

or:

```bash
./mvnw javafx:run
```

### Windows

Use installed Maven:

```powershell
mvn javafx:run
```

The checked-in `mvnw.cmd` file is empty in this repository, so the Maven wrapper is not currently usable on Windows without restoring that script.

## 11. Recommended development workflow

```bash
# run the UI
mvn javafx:run

# run the backend in another terminal
./run-backend.sh

# rebuild the jar when needed
mvn clean package -DskipTests
```

## 12. Troubleshooting

### Build fails with an unsupported Java version

The project compiles with Java release `21`. Install Java 21+ and make sure `java -version` and Maven both point to that JDK.

### `javafx:run` fails

Confirm:

- Java is installed
- Maven is installed
- dependencies can be downloaded
- you are running the command from the repository root

Then retry:

```bash
mvn -U clean javafx:run
```

### The app opens but notes are missing

Notes are user-scoped. Check:

- `notes/<username>/`
- `notes/<username>/trash/`

### Remote features are disabled in the UI

Make sure all of these are true:

- you are signed in
- `Network Sync` is enabled in settings
- the server URL is correct
- the sync server is running

### `run.sh` does not work

That script contains hardcoded JavaFX paths for a different machine. Use Maven commands instead unless you want to rewrite it for your own environment.

## 13. Clean rebuild

```bash
mvn clean
mvn package -DskipTests
```

If you also want to clear generated runtime data created by the app, manually remove project data directories only if that is acceptable for your workspace:

- `notes/`
- `remote-notes/`
- `quillpad.settings`
- `users.txt`
