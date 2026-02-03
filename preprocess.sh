#!/bin/bash
# Preprocessor: Merge C header and source files for static analysis
# Extracts struct definitions and typedefs from headers
# Converts unsupported constructs (function pointers, unsigned long)

if [ $# -lt 1 ]; then
    echo "Usage: $0 <file.c> [output_file]"
    exit 1
fi

SOURCE_FILE="$1"
HEADER_FILE="${SOURCE_FILE%.c}.h"

if [ $# -eq 2 ]; then
    OUTPUT_FILE="$2"
else
    OUTPUT_FILE="${SOURCE_FILE%.c}_preprocessed.c"
fi

# Check if header exists
if [ ! -f "$HEADER_FILE" ]; then
    echo "Warning: Header file $HEADER_FILE not found, using source as-is"
    cp "$SOURCE_FILE" "$OUTPUT_FILE"
    exit 0
fi

# Extract typedefs and struct definitions from header
# This preserves the actual struct definitions with all fields
{
    # Remove preprocessor directives but keep struct/typedef definitions
    grep -v "^#" "$HEADER_FILE" | \
    awk '
    /^typedef struct/ { in_typedef=1; print; next }
    /^struct [a-zA-Z_]+ \{/ { in_struct=1; print; next }
    in_typedef && /^\}/ { print; in_typedef=0; next }
    in_struct && /^\}/ { print; in_struct=0; next }
    in_typedef || in_struct {
        # Convert function pointers to void pointers
        if ($0 ~ /\(\*[a-zA-Z_]+\)/) {
            # Extract field name from function pointer: void *(*name)(...) -> void *name
            match($0, /\(\*([a-zA-Z_]+)\)/, arr)
            if (arr[1] != "") {
                print "    void *" arr[1] ";"
                next
            }
        }
        # Convert unsigned long to int
        gsub(/unsigned long/, "int")
        print
    }
    '
    
    echo ""
    echo "/* ===== Source Code ===== */"
    echo ""
    
    # Append source without #include directives
    # Convert: NULL->0, (void)->(), sizeof->16, unsigned long->int, long->int, remove casts
    # Convert compound assignments: += to =, -= to =, etc.
    # Expand chained assignments and split multi-variable pointer declarations
    grep -v "^#include" "$SOURCE_FILE" | \
    sed 's/NULL/0/g' | \
    sed -E 's/\(\s*void\s*\)/()/g' | \
    sed -E 's/sizeof\([^)]*\)/16/g' | \
    sed 's/unsigned long/int/g' | \
    sed 's/ long / int /g' | \
    sed -E 's/\(struct [a-zA-Z_][a-zA-Z0-9_]*\s*\*\)//g' | \
    sed -E 's/\([a-zA-Z_][a-zA-Z0-9_]*\s*\*\)//g' | \
    sed -E 's/([a-zA-Z_][a-zA-Z0-9_>-]*)\s*\+=\s*/\1 = \1 + /g' | \
    sed -E 's/([a-zA-Z_][a-zA-Z0-9_>-]*)\s*-=\s*/\1 = \1 - /g' | \
    sed -E 's/([a-zA-Z_>-]+)\s*=\s*([a-zA-Z_>-]+)\s*=\s*([0-9]+);/\1 = \3;\n    \2 = \3;/g' | \
    sed -E 's/^([[:space:]]*)([a-zA-Z_][a-zA-Z0-9_]*)[[:space:]]+\*([a-zA-Z_][a-zA-Z0-9_]*)[[:space:]]*,[[:space:]]*\*([a-zA-Z_][a-zA-Z0-9_]*);/\1\2 *\3;\n\1\2 *\4;/g'
    
} > "$OUTPUT_FILE"

echo "Created preprocessed file: $OUTPUT_FILE"
echo "  - Merged structs/typedefs from: $HEADER_FILE"
echo "  - Source code from: $SOURCE_FILE"
echo "  - Converted function pointers to void*"
echo "  - Converted NULL to 0"
