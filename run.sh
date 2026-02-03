#!/bin/bash
# Complete workflow: preprocess, merge, and analyze

if [ $# -lt 1 ]; then
    echo "Usage: $0 <file.c>"
    echo "This script will:"
    echo "  1. Preprocess the C file (merge with header)"
    echo "  2. Run static analysis with Main -all"
    exit 1
fi

SOURCE_FILE="$1"
PREPROCESSED_FILE="${SOURCE_FILE%.c}_preprocessed.c"

echo "================================================"
echo "Running complete analysis pipeline for: $SOURCE_FILE"
echo "================================================"
echo ""

# Step 1: Preprocess
echo "[1/3] Preprocessing $SOURCE_FILE..."
./preprocess.sh "$SOURCE_FILE"
if [ $? -ne 0 ]; then
    echo "Error: Preprocessing failed"
    exit 1
fi
echo "✓ Preprocessing complete: $PREPROCESSED_FILE"
echo ""

# Step 2: Compile Java files if needed
echo "[2/3] Checking Java compilation..."
if [ ! -f "Main.class" ] || [ "Main.java" -nt "Main.class" ]; then
    echo "Compiling Java files..."
    javac *.java
    if [ $? -ne 0 ]; then
        echo "Error: Java compilation failed"
        exit 1
    fi
    echo "✓ Java compilation complete"
else
    echo "✓ Java files already compiled"
fi
echo ""

# Step 3: Run Main with -all flag
echo "[3/3] Running static analysis with --all flag..."
java Main "$PREPROCESSED_FILE" --all
if [ $? -ne 0 ]; then
    echo "Error: Static analysis failed"
    exit 1
fi

echo ""
echo "================================================"
echo "Analysis complete!"
echo "================================================"
