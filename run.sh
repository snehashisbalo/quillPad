#!/bin/bash
# QuillPad IDE Launcher Script for NixOS

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

MP=/home/scythe/.m2/repository/org/openjfx
CACHE=/home/scythe/.openjfx/cache/21.0.2+5/amd64

MODULE_PATH="$MP/javafx-controls/21.0.2/javafx-controls-21.0.2.jar"
MODULE_PATH="$MODULE_PATH:$MP/javafx-controls/21.0.2/javafx-controls-21.0.2-linux.jar"
MODULE_PATH="$MODULE_PATH:$MP/javafx-graphics/21.0.2/javafx-graphics-21.0.2.jar"
MODULE_PATH="$MODULE_PATH:$MP/javafx-graphics/21.0.2/javafx-graphics-21.0.2-linux.jar"
MODULE_PATH="$MODULE_PATH:$MP/javafx-base/21.0.2/javafx-base-21.0.2.jar"
MODULE_PATH="$MODULE_PATH:$MP/javafx-base/21.0.2/javafx-base-21.0.2-linux.jar"
MODULE_PATH="$MODULE_PATH:$MP/javafx-fxml/21.0.2/javafx-fxml-21.0.2.jar"
MODULE_PATH="$MODULE_PATH:$MP/javafx-fxml/21.0.2/javafx-fxml-21.0.2-linux.jar"

exec java -Djava.library.path="$CACHE" \
     --module-path "$MODULE_PATH" \
     --add-modules javafx.controls,javafx.fxml \
     -cp target/classes \
     org.openjfx.QuillPad "$@"
