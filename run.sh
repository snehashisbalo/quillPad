#!/bin/bash
# QuillPad IDE Launcher Script

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

JAR_FILE="$SCRIPT_DIR/target/quillpad-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    echo "Please build the project first with: mvn package -DskipTests"
    exit 1
fi

echo "Starting QuillPad IDE..."
echo "JAR file: $JAR_FILE"

# Run the application using Java modules
exec java \
    --module-path /usr/share/openjfx/lib \
    --add-modules javafx.controls,javafx.fxml \
    -jar "$JAR_FILE" "$@"
