#!/bin/bash
# QuillPad IDE Launcher for NixOS
# This script sets up the required NixOS environment and runs QuillPad

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

exec nix-shell "$SCRIPT_DIR/shell.nix" --run "$SCRIPT_DIR/run.sh"
