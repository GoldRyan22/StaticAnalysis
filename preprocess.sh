if [ $# -lt 1 ]; then
    echo "Usage: $0 <file.c|file.h> [output_file]"
    exit 1
fi

SOURCE_FILE="$1"

# Handle .h files differently - process them directly without looking for another header
if [[ "$SOURCE_FILE" == *.h ]]; then
    if [ $# -eq 2 ]; then
        OUTPUT_FILE="$2"
    else
        OUTPUT_FILE="${SOURCE_FILE}_preprocessed.c"
    fi
    
    echo "Processing header file directly: $SOURCE_FILE"
    # Process the header file itself
    HEADER_FILE="$SOURCE_FILE"
    
    {
        # Extract and clean typedefs from the header
        python3 << PYTHON_SCRIPT
import re

try:
    with open("$HEADER_FILE", 'r') as f:
        content = f.read()
except:
    exit(0)

# Remove preprocessor directives
lines = [line for line in content.split('\n') if not line.strip().startswith('#')]
content = '\n'.join(lines)

# Remove comments
content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

# Find all typedefs
typedef_pattern = r'typedef\s+(?:struct\s+\w+\s*)?\{[^}]*\}\s*\w+\s*;|typedef\s+\w+(?:\s*\*)*\s+\w+\s*;'
typedefs = re.findall(typedef_pattern, content, re.MULTILINE | re.DOTALL)

basic_types = {'int', 'char', 'void', 'double', 'float', 'long', 'short'}

for typedef in typedefs:
    # Skip function pointer typedefs
    if re.search(r'typedef[^;]*\([^)]*\)[^;]*\(', typedef):
        continue
    
    # Clean up whitespace
    typedef = re.sub(r'\s+', ' ', typedef).strip()
    # Convert unsigned long to int
    typedef = typedef.replace('unsigned long', 'int')
    # Convert long long to int
    typedef = typedef.replace('long long', 'int')
    # Convert monotime to int (Redis custom type)
    typedef = typedef.replace('monotime', 'int')
    
    # Convert unknown pointer types to void* (function pointer typedefs)
    # First handle struct pointers - keep as is
    # Then look for TYPE *fieldname; where TYPE is not a basic type
    def replace_unknown_ptr(match):
        full_match = match.group(0)
        type_name = match.group(1)
        field_name = match.group(2)
        
        # Skip struct pointers completely
        if 'struct' in full_match:
            return full_match
        # Keep basic type pointers as-is
        if type_name in basic_types:
            return full_match
        # Convert unknown types (likely function pointer typedefs) to void*
        return f'void *{field_name};'
    
    typedef = re.sub(r'((?:struct\s+)?[a-zA-Z_][a-zA-Z0-9_]*)\s*\*\s*(\w+);', replace_unknown_ptr, typedef)
    
    # Convert function pointer fields to void* (e.g., void *(*dup)(void *ptr) -> void *dup)
    # Pattern: type (*name)(...) -> void *name
    typedef = re.sub(r'\w+\s+\*?\(\s*\*\s*(\w+)\s*\)\s*\([^)]*\)', r'void *\1', typedef)
    
    # Convert arrays to simple pointers (e.g., void *privdata[2] -> void *privdata)
    typedef = re.sub(r'(\w+\s+\*\w+)\[[0-9]+\]', r'\1', typedef)
    
    print(typedef)
PYTHON_SCRIPT
    } > "$OUTPUT_FILE"
    
    echo "Created preprocessed file: $OUTPUT_FILE"
    exit 0
fi

# For .c files, look for corresponding .h header
HEADER_FILE="${SOURCE_FILE%.c}.h"

if [ $# -eq 2 ]; then
    OUTPUT_FILE="$2"
else
    OUTPUT_FILE="${SOURCE_FILE%.c}_preprocessed.c"
fi

# Check if header exists
if [ ! -f "$HEADER_FILE" ]; then
    echo "Warning: Header file $HEADER_FILE not found, using source as-is"
    echo "Attempting to extract typedefs from source file itself..."
    
    # Try to extract typedefs from the .c file and prepend them
    {
        # Add common Redis type stubs for files without headers
        cat << 'EOF'
/* Common Redis type stubs for files without headers */
typedef void* rax;
typedef void* user;
typedef void* list;
typedef void* dict;
typedef void* sds;
typedef void* client;
typedef int uint64_t;
typedef int mstime_t;

EOF
        
        python3 << PYTHON_SCRIPT
import re

try:
    with open("$SOURCE_FILE", 'r') as f:
        content = f.read()
except:
    exit(0)

# Remove preprocessor directives
lines = [line for line in content.split('\n') if not line.strip().startswith('#')]
content = '\n'.join(lines)

# Remove comments
content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

# Find all typedefs
typedef_pattern = r'typedef\s+(?:struct\s+\w+\s*)?\{[^}]*\}\s*\w+\s*;|typedef\s+\w+(?:\s*\*)*\s+\w+\s*;'
typedefs = re.findall(typedef_pattern, content, re.MULTILINE | re.DOTALL)

basic_types = {'int', 'char', 'void', 'double', 'float', 'long', 'short'}

for typedef in typedefs:
    # Skip function pointer typedefs
    if re.search(r'typedef[^;]*\([^)]*\)[^;]*\(', typedef):
        continue
    
    # Clean up whitespace
    typedef = re.sub(r'\s+', ' ', typedef).strip()
    # Convert unsigned long to int
    typedef = typedef.replace('unsigned long', 'int')
    # Convert long long to int
    typedef = typedef.replace('long long', 'int')
    # Convert monotime to int (Redis custom type)
    typedef = typedef.replace('monotime', 'int')
    typedef = typedef.replace('uint64_t', 'int')
    typedef = typedef.replace('mstime_t', 'int')
    typedef = typedef.replace('sds', 'void*')
    
    # Convert unknown pointer types to void*
    def replace_unknown_ptr(match):
        full_match = match.group(0)
        type_name = match.group(1)
        field_name = match.group(2)
        
        if 'struct' in full_match:
            return full_match
        if type_name in basic_types:
            return full_match
        return f'void *{field_name};'
    
    typedef = re.sub(r'((?:struct\s+)?[a-zA-Z_][a-zA-Z0-9_]*)\s*\*\s*(\w+);', replace_unknown_ptr, typedef)
    typedef = re.sub(r'\w+\s+\*?\(\s*\*\s*(\w+)\s*\)\s*\([^)]*\)', r'void *\1', typedef)
    typedef = re.sub(r'(\w+\s+\*\w+)\[[0-9]+\]', r'\1', typedef)
    
    print(typedef)
PYTHON_SCRIPT
        
        echo ""
        echo "/* ===== Source Code ===== */"
        echo ""
        # Convert long long and other unsupported types in source
        sed 's/long long/int/g' "$SOURCE_FILE" | \
        sed 's/unsigned long/int/g' | \
        sed 's/ long / int /g' | \
        sed 's/NULL/0/g'
    } > "$OUTPUT_FILE"
    exit 0
fi

# Extract typedefs and struct definitions from header
# This preserves the actual struct definitions with all fields
{
    # Process header file to extract complete typedefs using Python
    python3 << PYTHON_SCRIPT
import re

try:
    with open("$HEADER_FILE", 'r') as f:
        content = f.read()
except:
    exit(0)

# Remove preprocessor directives
lines = [line for line in content.split('\n') if not line.strip().startswith('#')]
content = '\n'.join(lines)

# Remove comments
content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

# Find all typedefs
typedef_pattern = r'typedef\s+(?:struct\s+\w+\s*)?\{[^}]*\}\s*\w+\s*;|typedef\s+\w+(?:\s*\*)*\s+\w+\s*;'
typedefs = re.findall(typedef_pattern, content, re.MULTILINE | re.DOTALL)

basic_types = {'int', 'char', 'void', 'double', 'float', 'long', 'short'}

for typedef in typedefs:
    # Skip function pointer typedefs
    if re.search(r'typedef[^;]*\([^)]*\)[^;]*\(', typedef):
        continue
    
    # Clean up whitespace
    typedef = re.sub(r'\s+', ' ', typedef).strip()
    # Convert unsigned long to int
    typedef = typedef.replace('unsigned long', 'int')
    # Convert long long to int
    typedef = typedef.replace('long long', 'int')
    # Convert monotime to int (Redis custom type)
    typedef = typedef.replace('monotime', 'int')
    
    # Convert unknown pointer types to void* (function pointer typedefs)
    # First handle struct pointers - keep as is
    # Then look for TYPE *fieldname; where TYPE is not a basic type
    def replace_unknown_ptr(match):
        full_match = match.group(0)
        type_name = match.group(1)
        field_name = match.group(2)
        
        # Skip struct pointers completely
        if 'struct' in full_match:
            return full_match
        # Keep basic type pointers as-is
        if type_name in basic_types:
            return full_match
        # Convert unknown types (likely function pointer typedefs) to void*
        return f'void *{field_name};'
    
    typedef = re.sub(r'((?:struct\s+)?[a-zA-Z_][a-zA-Z0-9_]*)\s*\*\s*(\w+);', replace_unknown_ptr, typedef)
    
    # Convert function pointer fields to void* (e.g., void *(*dup)(void *ptr) -> void *dup)
    # Pattern: type (*name)(...) -> void *name
    typedef = re.sub(r'\w+\s+\*?\(\s*\*\s*(\w+)\s*\)\s*\([^)]*\)', r'void *\1', typedef)
    
    # Convert arrays to simple pointers (e.g., void *privdata[2] -> void *privdata)
    typedef = re.sub(r'(\w+\s+\*\w+)\[[0-9]+\]', r'\1', typedef)
    
    print(typedef)
PYTHON_SCRIPT
    
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
