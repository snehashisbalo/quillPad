{ pkgs ? import <nixpkgs> {} }:

let
  java = pkgs.jdk21;
  javafxLibs = with pkgs; [
    libx11
    libxxf86vm
    libxrandr
    libxrender
    libxext
    libxcursor
    libxi
    libXtst
    libGL
    openal
    freetype
    fontconfig
    gtk3
    glib
    pango
    atk
    cairo
    gdk-pixbuf
  ];
in
pkgs.mkShell {
  buildInputs = [ java pkgs.maven ] ++ javafxLibs;
  
  shellHook = ''
    export LD_LIBRARY_PATH="${pkgs.lib.makeLibraryPath javafxLibs}:$LD_LIBRARY_PATH"
  '';
}
