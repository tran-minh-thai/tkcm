#!/usr/bin/env bash
# build.sh - Cleans and freshly builds the USPAN project

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"

# Run clean first
source "$SCRIPT_DIR/clean.sh"

echo ""
echo "Building USPAN project..."

# Compile all Java source files
if javac -cp "$SRC_DIR" "$SRC_DIR"/*.java; then
    CLASS_COUNT=$(find "$SRC_DIR" -name "*.class" | wc -l | tr -d ' ')
    echo "  Compiled $CLASS_COUNT .class file(s)"
    echo "Build successful."
else
    echo "Build FAILED." >&2
    exit 1
fi
