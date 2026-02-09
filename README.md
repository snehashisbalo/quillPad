# QuillPad IDE

A production-grade JavaFX IDE with comprehensive features for software development.

## Features

### Core Editor Features
- **Multi-tab Editing**: Open and edit multiple files in tabs
- **File Operations**: New, Open, Save, Save As with file chooser dialogs
- **Edit Operations**: Undo/Redo, Cut/Copy/Paste, Select All
- **Find/Replace**: Text search with dialog-based find and replace
- **Syntax Highlighting**: Basic syntax highlighting support
- **Line Numbers**: Display line numbers in the editor
- **Status Bar**: Shows line/column position, encoding, line ending

### File & Project Management
- **Recent Files**: Tracks recently opened files
- **File Explorer**: Side panel showing project structure
- **Multiple Encoding Support**: UTF-8 encoding by default
- **Recent Sessions**: Saves and restores recent files

### View Features
- **Zoom Controls**: Zoom in/out with keyboard shortcuts
- **Side Panel Toggle**: Show/hide the file explorer
- **Terminal Panel**: Integrated terminal panel
- **Dark Theme**: Professional dark color scheme

### Keyboard Shortcuts
| Action | Shortcut |
|--------|----------|
| New File | Ctrl+N |
| Open File | Ctrl+O |
| Save | Ctrl+S |
| Close | Ctrl+W |
| Undo | Ctrl+Z |
| Redo | Ctrl+Y |
| Cut | Ctrl+X |
| Copy | Ctrl+C |
| Paste | Ctrl+V |
| Select All | Ctrl+A |
| Find | Ctrl+F |
| Find/Replace | Ctrl+H |
| Zoom In | Ctrl++ |
| Zoom Out | Ctrl+- |
| Reset Zoom | Ctrl+0 |
| Command Palette | Ctrl+Shift+P |

## Building

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build Commands
```bash
# Compile the project
mvn clean compile

# Package as JAR
mvn package -DskipTests

# Run tests
mvn test
```

## Running

### Using Maven
```bash
mvn javafx:run
```

### Using the JAR
```bash
java -jar target/quillpad-1.0.0-SNAPSHOT.jar
```

### Using the launcher script
```bash
./run.sh
```

## Project Structure

```
quillpad/
├── src/
│   └── main/
│       ├── java/org/quillpad/
│       │   └── core/application/
│       │       ├── QuillPadApplication.java  (Main IDE class)
│       │       ├── EditorTab.java            (Editor tab component)
│       │       ├── SidePanel.java            (File explorer)
│       │       ├── TerminalPanel.java        (Terminal)
│       │       ├── StatusBar.java            (Status bar)
│       │       └── CommandPalette.java       (Command palette)
│       └── resources/
│           └── org/quillpad/
│               └── css/
│                   └── main.css              (Dark theme)
├── target/
│   └── quillpad-1.0.0-SNAPSHOT.jar          (Executable JAR)
└── pom.xml                                   (Maven configuration)
```

## Architecture

### Main Components

1. **QuillPadApplication** - Main application class extending `javafx.application.Application`
   - Initializes UI components
   - Manages application lifecycle
   - Handles exit and cleanup

2. **EditorTab** - Individual editor tab with:
   - TextArea for editing
   - Line number display
   - Status indicator
   - Edit operations

3. **SidePanel** - Left panel with:
   - File tree view
   - Recent files list

4. **TerminalPanel** - Bottom terminal panel

5. **StatusBar** - Bottom status bar

### UI Layout
```
┌─────────────────────────────────────────────────┐
│ Menu Bar                                        │
├────────────┬────────────────────────────────────┤
│            │                                    │
│ Side Panel │           Tab Pane                │
│            │                                    │
│            │                                    │
├────────────┼────────────────────────────────────┤
│ Terminal Panel                                 │
├────────────────────────────────────────────────┤
│ Status Bar                                     │
└────────────────────────────────────────────────┘
```

## Configuration

Settings are stored in:
- Linux/macOS: `~/.quillpad/`
- Windows: `%USERPROFILE%\.quillpad\`

## Future Enhancements

- Git integration with visual diff and blame
- Plugin architecture for extensibility
- Advanced syntax highlighting
- Code completion/IntelliSense
- Project management
- Debugger integration
- Custom keybindings
- Theme customization
- Workspace persistence
- Crash recovery

## License

MIT License

## Authors

QuillPad Team
