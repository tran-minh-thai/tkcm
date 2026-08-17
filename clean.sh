#!/usr/bin/env bash
# clean.sh - Removes all intermediate and generated files from the USPAN project

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"

echo "Cleaning USPAN project..."

# Remove compiled class files
CLASS_COUNT=$(find "$SRC_DIR" -name "*.class" | wc -l | tr -d ' ')
find "$SRC_DIR" -name "*.class" -delete
echo "  Removed $CLASS_COUNT .class file(s)"

# Remove generated output files
OUTPUT_COUNT=$(find "$SRC_DIR" -name "output*.txt" | wc -l | tr -d ' ')
find "$SRC_DIR" -name "output*.txt" -delete
echo "  Removed $OUTPUT_COUNT output file(s)"

# Remove test build output
if [ -d "$SCRIPT_DIR/test/bin" ]; then
    rm -rf "$SCRIPT_DIR/test/bin"
    echo "  Removed test/bin"
fi

echo "Clean complete."
