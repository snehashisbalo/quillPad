#!/bin/bash
# QuillPad launcher script
# For Linux: Install dependencies first with:
# nix-env -iA nixpkgs.xorg.libX11 nixpkgs.xorg.libXext nixpkgs.xorg.libXrender \
#   nixpkgs.libxfixes nixpkgs.libxi nixpkgs.libxcursor nixpkgs.libxrandr \
#   nixpkgs.libxinerama nixpkgs.libxxf86vm nixpkgs.libglvnd nixpkgs.glib nixpkgs.gtk3

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

export LD_LIBRARY_PATH=~/.nix-profile/lib:$LD_LIBRARY_PATH

echo "Compiling QuillPad..."
mvn compile -q

echo "Starting QuillPad..."
mvn exec:java -Dexec.mainClass="org.openjfx.QuillPad"
