# QuillPad

QuillPad is a JavaFX desktop note editor with local user workspaces, rich-text formatting, tagging, trash recovery, and optional HTTP sync through a small bundled server.

It is designed as a lightweight desktop workspace: sign in, create notes, organize them by tags or stars, and optionally publish or pull notes through the included sync API.

## Highlights

- Local per-user note storage under `notes/<username>/`
- Login and registration backed by `users.txt`
- Multi-tab editor with autosave
- Rich-text formatting, font controls, and theme switching
- Tagging, starred notes, search, and trash restore
- Import existing files into the editor
- Optional remote sync, upload, download, and remote cleanup
- Included lightweight Java HTTP sync server

## Quick Start

### Run the desktop app

```bash
mvn javafx:run
```

### Build the packaged jar

```bash
mvn clean package -DskipTests
java -jar target/quillpad-1.0.0-SNAPSHOT.jar
```

### Run the local sync server

```bash
./run-backend.sh
```

The server starts on `http://localhost:8080` by default.

## Requirements

- Java 21 or newer
- Maven 3.6+

## Project Layout

```text
.
├── src/main/java/org/openjfx/
│   ├── QuillPad.java
│   ├── controller/
│   ├── component/
│   ├── network/
│   └── service/
├── src/main/resources/org/openjfx/
├── notes/
├── remote-notes/
├── run-backend.sh
├── pom.xml
└── INSTALLATION.md
```

## How It Works

### Local storage

QuillPad keeps project data in the repository directory:

- `notes/<username>/` for each user's notes
- `notes/<username>/trash/` for deleted notes
- `quillpad.settings` for application settings
- `users.txt` for account records
- `remote-notes/` for the bundled sync server's storage

### Remote sync

If network sync is enabled in the app settings, QuillPad can:

- upload selected local notes
- browse notes stored on the server
- download remote notes into the local workspace
- remotely autosave notes already linked to a remote copy
- delete uploaded notes by author

The bundled server exposes endpoints under `/api/...` and is implemented in `org.openjfx.network.NoteSyncServer`.

## Recommended Commands

```bash
# run the app
mvn javafx:run

# compile only
mvn clean compile

# build distributable jar
mvn clean package -DskipTests

# run the sync server on a custom port
./run-backend.sh 9090
```

## Documentation

- Installation and setup: [INSTALLATION.md](./INSTALLATION.md)

## Notes

- `run.sh` is a legacy launcher with machine-specific JavaFX paths. Prefer Maven-based commands unless you rewrite that script for your environment.
- The Maven wrapper is present as `./mvnw`, but the checked-in `mvnw.cmd` is empty, so Windows users should use an installed `mvn` unless they restore the wrapper script.
