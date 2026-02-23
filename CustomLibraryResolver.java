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
    private Set<String> usedFunctions;
    private Set<String> usedTypes;
    private Set<String> processedHeaders;
    private String sourceDirectory;
    
    public CustomLibraryResolver(String sourceDirectory) {
        this.functions = new HashMap<>();
        this.typedefs = new HashMap<>();
        this.structs = new HashMap<>();
        this.usedFunctions = new HashSet<>();
        this.usedTypes = new HashSet<>();
        this.processedHeaders = new HashSet<>();
        this.sourceDirectory = sourceDirectory;
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
            String content = new String(Files.readAllBytes(Paths.get(headerPath)));
            
            // Remove comments
            content = removeComments(content);
            
            // Extract typedefs
            extractTypedefs(content);
            
            // Extract struct definitions
            extractStructs(content);
            
            // Extract function declarations
            extractFunctionDeclarations(content);
            
        } catch (IOException e) {
            System.err.println("Error reading header file: " + headerPath + " - " + e.getMessage());
        }
    }
    
    /**
     * Find header file in source directory or its subdirectories
     */
    private String findHeaderFile(String headerFileName) {
        // First try the source directory
        File headerFile = new File(sourceDirectory, headerFileName);
        if (headerFile.exists()) {
            return headerFile.getAbsolutePath();
        }
        
        // Try looking in common subdirectories
        String[] subdirs = {"include", "src", "."};
        for (String subdir : subdirs) {
            headerFile = new File(new File(sourceDirectory, subdir), headerFileName);
            if (headerFile.exists()) {
                return headerFile.getAbsolutePath();
            }
        }
        
        return null;
    }
    
    /**
     * Remove C-style comments from code
     */
    private String removeComments(String content) {
        // Remove single-line comments
        content = content.replaceAll("//.*?$", "");
        
        // Remove multi-line comments
        content = content.replaceAll("/\\*.*?\\*/", "");
        
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
                System.out.println("  Found typedef: " + newName + " = " + baseType);
            }
        }
        
        // Struct typedef pattern: typedef struct name { ... } name;
        // or typedef struct { ... } name;
        Pattern structTypedef = Pattern.compile(
            "typedef\\s+struct\\s+([a-zA-Z_][a-zA-Z0-9_]*)?\\s*\\{[^}]*\\}\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*;",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        
        matcher = structTypedef.matcher(content);
        while (matcher.find()) {
            String structName = matcher.group(1); // may be null
            String typedefName = matcher.group(2).trim();
            
            // If there's a struct name, use it; otherwise use typedef name
            if (structName != null && !structName.trim().isEmpty()) {
                typedefs.put(typedefName, "struct " + structName.trim());
            } else {
                typedefs.put(typedefName, "struct " + typedefName);
            }
            System.out.println("  Found struct typedef: " + typedefName);
        }
    }
    
    /**
     * Extract struct definitions
     */
    private void extractStructs(String content) {
        // Pattern: struct name { fields... };
        Pattern structPattern = Pattern.compile(
            "struct\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\{([^}]*)\\}",
            Pattern.MULTILINE | Pattern.DOTALL
        );
        
        Matcher matcher = structPattern.matcher(content);
        while (matcher.find()) {
            String structName = matcher.group(1).trim();
            String fieldsBlock = matcher.group(2).trim();
            
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
                line = line.replaceAll("\\[\\d*\\]", "");
                
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
            System.out.println("  Found struct: " + structName + " with " + struct.fields.size() + " fields");
        }
    }
    
    /**
     * Extract function declarations
     * Pattern: <return_type> <function_name>(<parameters>);
     */
    private void extractFunctionDeclarations(String content) {
        // Pattern for function declarations
        // Handles: return_type function_name(param_type param_name, ...);
        // Updated to better handle pointer return types like "list *funcname(...)"
        Pattern funcPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_\\s]*\\s*\\**?)\\s+\\*?\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)\\s*;",
            Pattern.MULTILINE
        );
        
        Matcher matcher = funcPattern.matcher(content);
        while (matcher.find()) {
            String returnTypePart = matcher.group(1).trim();
            String funcName = matcher.group(2).trim();
            String paramsStr = matcher.group(3).trim();
            
            // Check if there's a * before function name (pointer return type)
            String fullMatch = matcher.group(0);
            String beforeFuncName = fullMatch.substring(0, fullMatch.indexOf(funcName)).trim();
            String returnType = returnTypePart;
            
            // Count asterisks in the return type section
            int pointerCount = 0;
            for (char c : beforeFuncName.toCharArray()) {
                if (c == '*') pointerCount++;
            }
            if (pointerCount > 0) {
                returnType = returnTypePart + "*".repeat(pointerCount);
            }
            
            // Skip if this looks like a macro or declaration we should ignore
            if (returnType.isEmpty() || funcName.isEmpty()) {
                continue;
            }
            
            // Skip if it contains certain keywords that indicate it's not a simple function
            if (returnType.contains("(") || funcName.contains("(")) {
                continue;
            }
            
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
            System.out.println("  Found function: " + func);
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
