#!/bin/bash
# Merge C header and source files for static analysis

if [ $# -lt 1 ]; then
    echo "Usage: $0 <file.c>"
    exit 1
fi

SOURCE_FILE="$1"
HEADER_FILE="${SOURCE_FILE%.c}.h"
OUTPUT_FILE="${SOURCE_FILE%.c}_merged.c"

# Check if header exists
if [ ! -f "$HEADER_FILE" ]; then
    echo "Header file $HEADER_FILE not found"
    exit 1
fi

# Extract struct definitions and typedefs from header (skip preprocessor directives)
grep -v "^#" "$HEADER_FILE" | \
grep -E "^typedef struct|^struct|^}" | \
grep -v "Prototypes" > "$OUTPUT_FILE"

echo "" >> "$OUTPUT_FILE"

# Append source file (skip includes)
grep -v "^#include" "$SOURCE_FILE" >> "$OUTPUT_FILE"

echo "Created merged file: $OUTPUT_FILE"
