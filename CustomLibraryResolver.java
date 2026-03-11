import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * CustomLibraryResolver - Parses custom header files (e.g., adlist.h, zmalloc.h)
 * and extracts function declarations and type definitions to add to the symbol table.
 * 
 * Similar to StandardLibrary.java but for user-defined libraries.
 */
public class CustomLibraryResolver {
    private Map<String, CustomFunction> functions;
    private Map<String, String> typedefs;
    private Map<String, CustomStruct> structs;
    private Map<String, Integer> defineConstants;
    private Set<String> macroNames;   // non-integer #defines (expressions, strings, etc.)
    private Map<String, String> externVariables;
    private Set<String> usedFunctions;
    private Set<String> usedTypes;
    private Set<String> processedHeaders;
    private String sourceDirectory;
    private List<String> extraSearchDirs;
    
    public CustomLibraryResolver(String sourceDirectory) {
        this.functions = new HashMap<>();
        this.typedefs = new HashMap<>();
        this.structs = new HashMap<>();
        this.defineConstants = new HashMap<>();
        this.macroNames = new HashSet<>();
        this.externVariables = new HashMap<>();
        this.usedFunctions = new HashSet<>();
        this.usedTypes = new HashSet<>();
        this.processedHeaders = new HashSet<>();
        this.sourceDirectory = sourceDirectory;
        this.extraSearchDirs = new ArrayList<>();
    }
    
    /**
     * Extract custom includes from a C source file
     * Looks for #include "filename.h" patterns
     */
    public List<String> extractCustomIncludes(String filePath) {
        List<String> customIncludes = new ArrayList<>();
        Pattern includePattern = Pattern.compile("#include\\s+\"([^\"]+)\"");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = includePattern.matcher(line.trim());
                if (matcher.find()) {
                    String headerFile = matcher.group(1);
                    customIncludes.add(headerFile);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath + " - " + e.getMessage());
        }
        
        return customIncludes;
    }

    /**
     * Extract custom includes from content string (for transitive header resolution)
     */
    private List<String> extractCustomIncludesFromContent(String content) {
        List<String> includes = new ArrayList<>();
        Pattern includePattern = Pattern.compile("#include\\s+\"([^\"]+)\"");
        for (String line : content.split("\\r?\\n")) {
            Matcher matcher = includePattern.matcher(line.trim());
            if (matcher.find()) {
                includes.add(matcher.group(1));
            }
        }
        return includes;
    }

    /**
     * Parse a header file and extract all declarations
     */
    public void parseHeaderFile(String headerFileName) {
        // Avoid processing the same header twice
        if (processedHeaders.contains(headerFileName)) {
            return;
        }
        processedHeaders.add(headerFileName);
        
        // Try to find the header file
        String headerPath = findHeaderFile(headerFileName);
        if (headerPath == null) {
            System.err.println("Warning: Could not find header file: " + headerFileName);
            return;
        }
        
        System.out.println("Parsing custom header: " + headerFileName);
        
        try {
            String rawContent = new String(Files.readAllBytes(Paths.get(headerPath)));

            // Follow transitive includes from this header (before comment removal)
            List<String> nestedIncludes = extractCustomIncludesFromContent(rawContent);
            for (String nested : nestedIncludes) {
                parseHeaderFile(nested);
            }

            // Remove comments
            String content = removeComments(rawContent);
            
            // Extract typedefs
            extractTypedefs(content);
            
            // Extract struct definitions
            extractStructs(content);
            
            // Extract function declarations
            extractFunctionDeclarations(content);
            
            // Extract extern variable declarations (e.g. extern struct redisServer server;)
            extractExternDeclarations(content);
            
            // Extract #define integer constants (e.g. AL_START_HEAD, AL_START_TAIL)
            extractDefines(content);

            // Extract enum constants (e.g. BLOCKED_NONE, BLOCKED_LIST from typedef enum { ... })
            extractEnums(content);
            
        } catch (IOException e) {
            System.err.println("Error reading header file: " + headerPath + " - " + e.getMessage());
        }
    }

    /**
     * Extract #define macros and enum constants from preprocessor lines already
     * captured by LexAn during lexical analysis of the main source file.
     * Each entry is a raw directive string such as "#define FOO 42".
     * This avoids re-reading the file and reuses the lexer's own output.
     */
    public void extractDefinesFromPreprocessorLines(List<String> lines) {
        // Join into a single string so extractDefines/extractEnums (MULTILINE mode) works.
        String content = String.join("\n", lines);
        extractDefines(content);
        extractEnums(content);
    }

    /**
     * Register an additional directory to search for header files.
     * Useful for -I include paths passed on the command line.
     */
    public void addSearchDirectory(String dir) {
        if (dir != null && !dir.isEmpty() && !extraSearchDirs.contains(dir)) {
            extraSearchDirs.add(dir);
        }
    }

    /**
     * Find header file in source directory, extra search dirs, or their subdirectories.
     */
    private String findHeaderFile(String headerFileName) {
        // Collect all candidate root directories: sourceDirectory + extras
        List<String> searchRoots = new ArrayList<>();
        searchRoots.add(sourceDirectory);
        searchRoots.addAll(extraSearchDirs);

        String[] subdirs = {".", "include", "src"};
        for (String root : searchRoots) {
            for (String subdir : subdirs) {
                File dir = subdir.equals(".") ? new File(root) : new File(root, subdir);
                File candidate = new File(dir, headerFileName);
                if (candidate.exists()) return candidate.getAbsolutePath();
            }
        }
        return null;
    }
    
    /**
     * Extract extern variable declarations from header files.
     * Pattern: extern [struct/union] type_name [*] var_name [[]...] ;
     */
    private void extractExternDeclarations(String content) {
        Pattern externPattern = Pattern.compile(
            "^\\s*extern\\s+(.+?)\\s*;",
            Pattern.MULTILINE
        );
        Matcher matcher = externPattern.matcher(content);
        while (matcher.find()) {
            String decl = matcher.group(1).trim();

            // Skip function declarations (contain parentheses)
            if (decl.contains("(")) continue;

            // Tokenize the declaration (e.g. "struct redisServer server" or "int *count")
            String[] tokens = decl.split("\\s+");
            if (tokens.length < 2) continue; // need at least type + name

            // Last token is the variable name (may be prefixed with pointer stars)
            String lastName = tokens[tokens.length - 1];
            String varName = lastName.replaceAll("^\\*+", "").replaceAll("\\[.*", "").trim();
            if (!varName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) continue;

            // Build type from all tokens except the last
            StringBuilder typeBuilder = new StringBuilder();
            for (int i = 0; i < tokens.length - 1; i++) {
                if (i > 0) typeBuilder.append(" ");
                typeBuilder.append(tokens[i]);
            }
            // Count pointer stars that were attached to the variable name token
            int ptrCount = 0;
            for (char c : lastName.toCharArray()) {
                if (c == '*') ptrCount++;
            }
            String varType = typeBuilder.toString().trim();
            for (int i = 0; i < ptrCount; i++) {
                varType += "*";
            }

            externVariables.put(varName, varType);
        }
    }

    /**
     * Register all extern variables parsed from headers into the symbol table.
     * These are globally visible variables declared in other translation units.
     */
    public void registerExternVariables(SymbolTable symbolTable) {
        for (Map.Entry<String, String> entry : externVariables.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();
            if (symbolTable.lookup(name) == null) {
                symbolTable.addSymbol(name, type, "variable");
            }
        }
    }

    /**
     * Extract enum constants from all enum blocks in content.
     * Handles:
     *   typedef enum { A = 0, B, C } TypeName;
     *   enum Name { A, B, C };
     *   enum { A = 1, B, C };
     * Each enumerator name is registered in macroNames (as int).
     */
    private void extractEnums(String content) {
        // Find each 'enum ... {' opening, then balance braces to get the body.
        Pattern enumStart = Pattern.compile(
            "\\benum\\b[^{;]*\\{",
            Pattern.MULTILINE
        );
        Matcher m = enumStart.matcher(content);
        while (m.find()) {
            int openBrace = m.end() - 1;
            // Balance braces to find the closing '}'
            int depth = 0, endPos = -1;
            for (int i = openBrace; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) { endPos = i; break; } }
            }
            if (endPos == -1) continue;

            String body = content.substring(openBrace + 1, endPos);
            // Each enumerator is an identifier optionally followed by = value, separated by commas
            Pattern enumerator = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=[^,}]*)?" );
            Matcher em = enumerator.matcher(body);
            while (em.find()) {
                String name = em.group(1).trim();
                if (!name.isEmpty()) {
                    macroNames.add(name);
                }
            }
        }
    }

    /**
     * Extract #define constants.
     * Handles:
     *   - Positive integers:  #define FOO 42
     *   - Negative integers:  #define C_ERR -1
     *   - Octal literals:     #define FOO 0000002
     *   - Hex literals:       #define FLAG 0xFF
     *   - Expressions / other scalar values: registered by name only (as int)
     * Function-like macros (#define FOO(x) ...) are intentionally skipped.
     */
    private void extractDefines(String content) {
        // Strip block comments before parsing so trailing /* ... */ don't break matching
        content = content.replaceAll("(?s)/\\*.*?\\*/", "");

        // Match any non-function-like #define: name must NOT be immediately followed by '('
        Pattern definePattern = Pattern.compile(
            "^#define\\s+([A-Za-z_][A-Za-z0-9_]*)(?!\\()[ \\t]+(.+)$",
            Pattern.MULTILINE
        );
        Matcher matcher = definePattern.matcher(content);
        while (matcher.find()) {
            String name  = matcher.group(1);
            String value = matcher.group(2).trim();
            // Strip trailing // comment (safety)
            value = value.replaceAll("//.*$", "").trim();
            if (value.isEmpty()) continue;

            // Try negative decimal: -1, -2, ...
            if (value.matches("-\\d+")) {
                try { defineConstants.put(name, Integer.parseInt(value)); } catch (NumberFormatException e) { macroNames.add(name); }
            // Try positive decimal (includes octal literals like 0000002 — stored as int)
            } else if (value.matches("\\d+")) {
                try { defineConstants.put(name, (int) Long.parseUnsignedLong(value)); } catch (NumberFormatException e) { macroNames.add(name); }
            // Try hex: 0x... or 0X...
            } else if (value.matches("0[xX][0-9a-fA-F]+")) {
                try {
                    long lv = Long.parseUnsignedLong(value.substring(2), 16);
                    defineConstants.put(name, (int) lv);
                } catch (NumberFormatException e) {
                    macroNames.add(name);
                }
            // Anything else (expressions, string literals, references to other defines, etc.)
            } else {
                macroNames.add(name);
            }
        }
    }

    /**
     * Register all known #define integer constants and scalar macro names into the symbol
     * table as int variables, so they don't trigger "not declared" warnings.
     */
    public void registerConstants(SymbolTable symbolTable) {
        for (String name : defineConstants.keySet()) {
            if (symbolTable.lookup(name) == null) {
                symbolTable.addSymbol(name, "int", "variable");
            }
        }
        for (String name : macroNames) {
            if (symbolTable.lookup(name) == null) {
                symbolTable.addSymbol(name, "int", "variable");
            }
        }
    }
    
    /**
     * Remove C-style comments from code
     */
    private String removeComments(String content) {
        // IMPORTANT: Block comments MUST be removed before line comments.
        // If a block comment contains a URL like "https://..." on the line that
        // also holds the closing "*/", then removing "//" first would delete the
        // "*/" and the block comment would then span across real code.

        // Remove multi-line comments first ((?s) enables DOTALL so . matches newlines)
        content = content.replaceAll("(?s)/\\*.*?\\*/", "");

        // Now remove single-line comments safely ((?m) makes $ match end of each line)
        content = content.replaceAll("(?m)//.*?$", "");

        // Strip GCC __attribute__((...)): remove attribute specifiers so the
        // function/variable declarations following them can be parsed normally.
        // e.g. __attribute__((malloc,alloc_size(1),noinline)) void *zmalloc(...);
        content = content.replaceAll("(?s)__attribute__\\s*\\(\\(.*?\\)\\)", "");

        return content;
    }
    
    /**
     * Extract typedef declarations
     * Pattern: typedef <type> <newname>;
     */
    private void extractTypedefs(String content) {
        // Simple typedef pattern: typedef <existing_type> <new_name>;
        Pattern simpleTypedef = Pattern.compile(
            "typedef\\s+([^;]+?)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*;",
            Pattern.MULTILINE
        );
        
        Matcher matcher = simpleTypedef.matcher(content);
        while (matcher.find()) {
            String baseType = matcher.group(1).trim();
            String newName = matcher.group(2).trim();
            
            // Skip struct/union typedefs (handled separately)
            if (!baseType.startsWith("struct") && !baseType.startsWith("union")) {
                typedefs.put(newName, baseType);
            }
        }
        
        // Struct typedef pattern: typedef struct name { ... } name;
        // or typedef struct { ... } name;
        // Uses manual brace-depth tracking to support nested braces inside the struct body.
        Pattern structTypedefStart = Pattern.compile(
            "typedef\\s+struct\\s+([a-zA-Z_][a-zA-Z0-9_]*)?\\s*\\{",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        Matcher stMatcher = structTypedefStart.matcher(content);
        while (stMatcher.find()) {
            String structName = stMatcher.group(1); // may be null
            int openBrace = stMatcher.end() - 1;

            // Balance braces
            int depth = 0, endPos = -1;
            for (int i = openBrace; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') { depth--; if (depth == 0) { endPos = i; break; } }
            }
            if (endPos == -1) continue;

            // After the closing brace, expect optional whitespace then typedef name then ';'
            Pattern afterBrace = Pattern.compile(
                "\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*;",
                Pattern.MULTILINE | Pattern.DOTALL
            );
            Matcher abMatcher = afterBrace.matcher(content.substring(endPos + 1));
            if (!abMatcher.find() || abMatcher.start() != 0) continue;
            String typedefName = abMatcher.group(1).trim();

            if (structName != null && !structName.trim().isEmpty()) {
                typedefs.put(typedefName, "struct " + structName.trim());
            } else {
                typedefs.put(typedefName, "struct " + typedefName);
            }
        }

        // Function-type typedef: typedef returntype name(params);
        // e.g. typedef void aeFileProc(struct aeEventLoop *eventLoop, int fd, ...);
        Pattern funcTypedef = Pattern.compile(
            "typedef\\s+[^(;]+?\\s+(\\w+)\\s*\\([^)]*\\)\\s*;",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        matcher = funcTypedef.matcher(content);
        while (matcher.find()) {
            String typedefName = matcher.group(1).trim();
            typedefs.put(typedefName, "function");
        }
    }
    
    /**
     * Extract struct definitions.
     * Uses manual brace-depth tracking so that large structs containing nested
     * anonymous struct/union fields (e.g. redisServer.inst_metric) are captured
     * in full instead of stopping at the first inner '}'.
     */
    private void extractStructs(String content) {
        // Match "struct Name {" — just the opening, then balance braces manually
        Pattern structStart = Pattern.compile(
            "struct\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\{",
            Pattern.MULTILINE | Pattern.DOTALL
        );

        Matcher matcher = structStart.matcher(content);
        while (matcher.find()) {
            String structName = matcher.group(1).trim();
            // matcher.end() points one past '{', so the '{' is at matcher.end()-1
            int openBrace = matcher.end() - 1;

            // Find the matching closing brace by counting depth
            int depth = 0;
            int endPos = -1;
            for (int i = openBrace; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { endPos = i; break; }
                }
            }
            if (endPos == -1) continue; // unbalanced — skip

            // Strip nested brace blocks so field-level ';' splitting works correctly.
            // E.g. "struct { int a; int b; } inst_metric[N];" becomes " inst_metric[N];"
            // preserving the outer field declaration after the anonymous struct.
            String rawFields = content.substring(openBrace + 1, endPos);
            String fieldsBlock = stripNestedBraces(rawFields).trim();

            CustomStruct struct = new CustomStruct(structName);

            // Parse fields - split by semicolon
            String[] lines = fieldsBlock.split(";");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Check if it's a function pointer: type (*name)(params)
                if (line.contains("(*") && line.contains(")(")) {
                    // Function pointer field - extract the name
                    Pattern fpPattern = Pattern.compile("\\(\\*([a-zA-Z_][a-zA-Z0-9_]*)\\)");
                    Matcher fpMatcher = fpPattern.matcher(line);
                    if (fpMatcher.find()) {
                        String name = fpMatcher.group(1);
                        // Store function pointer as "void*" for simplicity
                        struct.addField(name, "void*");
                    }
                    continue;
                }

                // Regular field: type name or type *name or struct type *name
                // Remove array brackets if present
                line = line.replaceAll("\\[[^\\]]*\\]", "");

                // Check for multi-variable declaration: robj *a, *b, *c (common in C structs)
                if (line.contains(",")) {
                    String[] parts = line.split(",");
                    String firstPart = parts[0].trim();
                    String[] firstTokens = firstPart.split("\\s+");
                    if (firstTokens.length >= 2) {
                        // Find the name in the first part (last valid identifier)
                        String firstName = null;
                        int firstNameIdx = -1;
                        for (int i = firstTokens.length - 1; i >= 0; i--) {
                            String tok = firstTokens[i].replaceAll("[^a-zA-Z0-9_]", "");
                            if (!tok.isEmpty() && tok.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                                firstName = tok;
                                firstNameIdx = i;
                                break;
                            }
                        }
                        if (firstName != null && firstNameIdx > 0) {
                            // Base type = tokens before name, stars stripped
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < firstNameIdx; i++) {
                                String tok = firstTokens[i].replaceAll("\\*", "").trim();
                                if (!tok.isEmpty()) {
                                    if (sb.length() > 0) sb.append(" ");
                                    sb.append(tok);
                                }
                            }
                            String baseType = sb.toString().trim();
                            // Count stars for first element (from all tokens)
                            int firstStars = 0;
                            for (String t : firstTokens) for (char c : t.toCharArray()) if (c == '*') firstStars++;
                            String firstFieldType = baseType;
                            for (int i = 0; i < firstStars; i++) firstFieldType += "*";
                            struct.addField(firstName, firstFieldType);
                            // Remaining comma-separated items: "*name" or "name"
                            for (int pi = 1; pi < parts.length; pi++) {
                                String part = parts[pi].trim();
                                if (part.isEmpty()) continue;
                                int stars = 0;
                                for (char c : part.toCharArray()) if (c == '*') stars++;
                                String name = part.replaceAll("[^a-zA-Z0-9_]", "").trim();
                                if (!name.isEmpty() && name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                                    String fieldType = baseType;
                                    for (int i = 0; i < stars; i++) fieldType += "*";
                                    struct.addField(name, fieldType);
                                }
                            }
                        }
                    }
                    continue; // skip single-var parsing below
                }

                // Split into tokens
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length < 2) continue;

                // Find where the field name is (last token that's a valid identifier)
                String fieldName = null;
                int nameIndex = -1;
                for (int i = tokens.length - 1; i >= 0; i--) {
                    String token = tokens[i].replaceAll("\\*", "");
                    if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        fieldName = token;
                        nameIndex = i;
                        break;
                    }
                }

                if (fieldName == null || nameIndex == 0) continue;

                // Build the type from all tokens before the name
                StringBuilder typeBuilder = new StringBuilder();
                for (int i = 0; i < nameIndex; i++) {
                    if (i > 0) typeBuilder.append(" ");
                    typeBuilder.append(tokens[i]);
                }

                String fieldType = typeBuilder.toString();

                // Count pointers in the last token (might be attached to name like *name)
                String lastToken = tokens[nameIndex];
                int ptrCount = 0;
                for (char c : lastToken.toCharArray()) {
                    if (c == '*') ptrCount++;
                }

                // Add pointers to type
                for (int i = 0; i < ptrCount; i++) {
                    fieldType += "*";
                }

                struct.addField(fieldName, fieldType);
            }

            structs.put(structName, struct);
        }
    }

    /**
     * Remove all content inside nested brace blocks, keeping only the
     * outermost level text.  This lets field parsing via ';' splitting
     * work correctly even when a struct contains anonymous struct/union
     * members (e.g. "struct { int a; } x;").  The declaration after the
     * nested '}' (the field name, e.g. "x") is preserved.
     */
    private String stripNestedBraces(String content) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (depth == 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * Extract function declarations
     * Pattern: <return_type> <function_name>(<parameters>);
     */
    private void extractFunctionDeclarations(String content) {
        // Pattern for function declarations.
        //  Group 1: return type base  – one or more identifier tokens separated by single spaces
        //           e.g. "void", "unsigned long", "const char"
        //  Group 2: pointer stars     – zero or more '*' between the return type and the name
        //  Group 3: function name
        //  Group 4: parameter string
        //
        // This three-part split avoids the previous bug where [a-zA-Z0-9_\s]* in group 1
        // greedily consumed trailing whitespace and failed to match "void *funcname(...)".
        // Use atomic groups (?>...) for each word token so the engine can only give back
        // whole words during backtracking, not split e.g. "zfree" into "zfre" + "e".
        Pattern funcPattern = Pattern.compile(
            "((?>[a-zA-Z_][a-zA-Z0-9_]*)(?:\\s+(?>[a-zA-Z_][a-zA-Z0-9_]*))*)" +  // group 1: return base type
            "\\s*(\\*+)?\\s*" +                                                     // group 2: optional pointer stars
            "([a-zA-Z_][a-zA-Z0-9_]*)\\s*" +                                       // group 3: function name
            "\\(([^()]*)\\)\\s*;",                                                  // group 4: params (no nested parens)
            Pattern.MULTILINE
        );

        Matcher matcher = funcPattern.matcher(content);
        while (matcher.find()) {
            String returnBase = matcher.group(1).trim();
            String ptrStars    = matcher.group(2) != null ? matcher.group(2).trim() : "";
            String funcName    = matcher.group(3).trim();
            String paramsStr   = matcher.group(4).trim();

            // Build full return type
            String returnType = ptrStars.isEmpty() ? returnBase : returnBase + ptrStars;

            // Skip obvious non-function matches (typedefs, macros, etc.)
            if (returnType.isEmpty() || funcName.isEmpty()) continue;
            if (returnType.contains("(") || funcName.contains("(")) continue;
            // Skip typedef function-type declarations (e.g. typedef void proc(params);)
            if (returnBase.startsWith("typedef")) continue;

            // Skip C keywords mistakenly captured as function names
            if (funcName.equals("if") || funcName.equals("while") || funcName.equals("for")
                    || funcName.equals("return") || funcName.equals("switch")
                    || funcName.equals("else") || funcName.equals("do")) continue;

            // Skip matches where the "return type" is actually a C statement keyword.
            // This happens when a return/if/while/for statement slips through and the
            // regex matches: e.g. "return sizeof(username);" → returnBase="return", funcName="sizeof"
            // overwriting the StandardLibrary's correct "sizeof" → "size_t" entry.
            String firstWord = returnBase.split("\\s+")[0];
            if (firstWord.equals("return") || firstWord.equals("if") || firstWord.equals("while")
                    || firstWord.equals("for") || firstWord.equals("switch") || firstWord.equals("else")
                    || firstWord.equals("do") || firstWord.equals("case") || firstWord.equals("break")
                    || firstWord.equals("continue") || firstWord.equals("goto")) continue;
            
            // Parse parameters
            List<String> paramTypes = new ArrayList<>();
            if (!paramsStr.isEmpty() && !paramsStr.equals("void")) {
                String[] params = paramsStr.split(",");
                for (String param : params) {
                    param = param.trim();
                    if (param.isEmpty()) continue;
                    
                    // Extract type from parameter (type name or just type)
                    String[] parts = param.trim().split("\\s+");
                    if (parts.length > 0) {
                        StringBuilder typeBuilder = new StringBuilder();
                        // Take all but the last part (which is usually the parameter name)
                        // But if there's only one part, it's the type
                        int typeParts = parts.length > 1 ? parts.length - 1 : 1;
                        for (int i = 0; i < typeParts; i++) {
                            if (i > 0) typeBuilder.append(" ");
                            typeBuilder.append(parts[i]);
                        }
                        String paramType = typeBuilder.toString();
                        
                        // Check for pointer in the last part (parameter name might have *)
                        if (parts.length > 1 && parts[parts.length - 1].startsWith("*")) {
                            paramType += "*";
                        }
                        
                        paramTypes.add(paramType);
                    }
                }
            }
            
            CustomFunction func = new CustomFunction(funcName, returnType, paramTypes);
            functions.put(funcName, func);
        }
    }
    
    /**
     * Scan the AST to determine which custom symbols are actually used
     */
    public void scanForUsedSymbols(ProgramNode program) {
        for (ASTNode node : program.declarations) {
            scanNode(node);
        }
    }
    
    private void scanNode(ASTNode node) {
        if (node == null) return;
        
        if (node instanceof FuncDeclNode) {
            FuncDeclNode funcDecl = (FuncDeclNode) node;
            scanType(funcDecl.retType);
            for (VarDeclNode param : funcDecl.args) {
                scanNode(param);
            }
            scanNode(funcDecl.body);
        }
        else if (node instanceof VarDeclNode) {
            VarDeclNode varDecl = (VarDeclNode) node;
            scanType(varDecl.type);
            scanNode(varDecl.initExpr);
        }
        else if (node instanceof BlockNode) {
            BlockNode block = (BlockNode) node;
            for (ASTNode stmt : block.statements) {
                scanNode(stmt);
            }
        }
        else if (node instanceof IfStmtNode) {
            IfStmtNode ifStmt = (IfStmtNode) node;
            scanNode(ifStmt.condition);
            scanNode(ifStmt.thenBranch);
            scanNode(ifStmt.elseBranch);
        }
        else if (node instanceof WhileStmtNode) {
            WhileStmtNode whileStmt = (WhileStmtNode) node;
            scanNode(whileStmt.condition);
            scanNode(whileStmt.body);
        }
        else if (node instanceof ReturnStmtNode) {
            ReturnStmtNode retStmt = (ReturnStmtNode) node;
            scanNode(retStmt.expr);
        }
        else if (node instanceof BinaryExprNode) {
            BinaryExprNode binExpr = (BinaryExprNode) node;
            scanNode(binExpr.left);
            scanNode(binExpr.right);
        }
        else if (node instanceof UnaryExprNode) {
            UnaryExprNode unaryExpr = (UnaryExprNode) node;
            scanNode(unaryExpr.expr);
        }
        else if (node instanceof FuncCallNode) {
            FuncCallNode funcCall = (FuncCallNode) node;
            usedFunctions.add(funcCall.name);
            for (ASTNode arg : funcCall.args) {
                scanNode(arg);
            }
        }
        else if (node instanceof TernaryExprNode) {
            TernaryExprNode ternary = (TernaryExprNode) node;
            scanNode(ternary.condition);
            scanNode(ternary.thenExpr);
            scanNode(ternary.elseExpr);
        }
        else if (node instanceof CastExprNode) {
            CastExprNode cast = (CastExprNode) node;
            scanType(cast.castType);
            scanNode(cast.expr);
        }
        else if (node instanceof TypedefDeclNode) {
            TypedefDeclNode typedef = (TypedefDeclNode) node;
            scanType(typedef.baseType);
        }
        else if (node instanceof StructDeclNode) {
            StructDeclNode struct = (StructDeclNode) node;
            for (VarDeclNode field : struct.fields) {
                scanNode(field);
            }
        }
    }
    
    private void scanType(String type) {
        if (type == null) return;
        
        // Extract base type name (remove pointers and qualifiers)
        String baseType = type.replaceAll("\\*", "").trim();
        baseType = baseType.replaceAll("\\bconst\\b", "").trim();
        baseType = baseType.replaceAll("\\bvolatile\\b", "").trim();
        baseType = baseType.replaceAll("\\brestrict\\b", "").trim();
        baseType = baseType.replaceAll("\\bstruct\\b", "").trim();
        baseType = baseType.replaceAll("\\bunion\\b", "").trim();
        baseType = baseType.replaceAll("\\benum\\b", "").trim();
        
        // Check if it's a typedef we know about
        if (typedefs.containsKey(baseType)) {
            usedTypes.add(baseType);
        }
        
        // Check if it's a struct we know about
        if (structs.containsKey(baseType)) {
            usedTypes.add(baseType);
        }
    }
    
    /**
     * Register used custom symbols into the symbol table
     */
    public void registerUsedSymbols(SymbolTable symbolTable) {
        // Register used typedefs
        for (String typeName : usedTypes) {
            if (typedefs.containsKey(typeName)) {
                String baseType = typedefs.get(typeName);
                symbolTable.addSymbol(typeName, baseType, "typedef");
            }
        }
        
        // Register used structs
        for (String structName : usedTypes) {
            if (structs.containsKey(structName)) {
                CustomStruct struct = structs.get(structName);
                // Add struct definition to symbol table
                symbolTable.addSymbol(structName, "struct " + structName, "struct");
            }
        }
        
        // Register used functions
        for (String funcName : usedFunctions) {
            if (functions.containsKey(funcName)) {
                CustomFunction func = functions.get(funcName);
                Symbol symbol = new Symbol(funcName, func.returnType, "function", 0);
                symbol.returnType = func.returnType;
                symbol.paramTypes.addAll(func.paramTypes);
                symbolTable.addSymbol(symbol);
            }
        }
    }
    
    /**
     * Get struct definition for semantic analysis
     */
    public CustomStruct getStruct(String structName) {
        return structs.get(structName);
    }
    
    /**
     * Check if a function is from custom library
     */
    public boolean isCustomFunction(String name) {
        return functions.containsKey(name);
    }
    
    /**
     * Check if a type is from custom library
     */
    public boolean isCustomType(String name) {
        return typedefs.containsKey(name) || structs.containsKey(name);
    }
    
    /**
     * Get all typedef names (for parser type recognition)
     */
    public Set<String> getTypedefNames() {
        return new HashSet<>(typedefs.keySet());
    }
    
    /**
     * Get all struct names (for parser type recognition)
     */
    public Set<String> getStructNames() {
        return new HashSet<>(structs.keySet());
    }
    
    /**
     * Print information about custom library symbols
     */
    public void printUsedSymbols() {
        if (!usedFunctions.isEmpty() || !usedTypes.isEmpty()) {
            System.out.println("\n=== Custom Library Symbols Used ===");
        }
        
        if (!usedTypes.isEmpty()) {
            System.out.println("\nTypes:");
            for (String type : usedTypes) {
                if (typedefs.containsKey(type)) {
                    System.out.println("  " + type + " = " + typedefs.get(type));
                } else if (structs.containsKey(type)) {
                    System.out.println("  struct " + type);
                }
            }
        }
        
        if (!usedFunctions.isEmpty()) {
            System.out.println("\nFunctions:");
            for (String func : usedFunctions) {
                CustomFunction customFunc = functions.get(func);
                if (customFunc != null) {
                    System.out.println("  " + customFunc);
                }
            }
        }
    }
    
    // Helper class to store custom function information
    private static class CustomFunction {
        String name;
        String returnType;
        List<String> paramTypes;
        
        CustomFunction(String name, String returnType, List<String> paramTypes) {
            this.name = name;
            this.returnType = returnType;
            this.paramTypes = paramTypes;
        }
        
        @Override
        public String toString() {
            String params = paramTypes.isEmpty() ? "void" : String.join(", ", paramTypes);
            return returnType + " " + name + "(" + params + ")";
        }
    }
    
    // Helper class to store struct information
    public static class CustomStruct {
        String name;
        Map<String, String> fields; // field name -> field type
        
        CustomStruct(String name) {
            this.name = name;
            this.fields = new LinkedHashMap<>();
        }
        
        void addField(String fieldName, String fieldType) {
            fields.put(fieldName, fieldType);
        }
        
        public String getFieldType(String fieldName) {
            return fields.get(fieldName);
        }
        
        public boolean hasField(String fieldName) {
            return fields.containsKey(fieldName);
        }
        
        @Override
        public String toString() {
            return "struct " + name + " { " + fields.size() + " fields }";
        }
    }
}
