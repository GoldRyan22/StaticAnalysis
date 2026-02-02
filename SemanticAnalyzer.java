import java.util.*;

// Semantic Error representation
class SemanticError {
    String message;
    String severity; // "error", "warning"
    int line;
    
    public SemanticError(String message, String severity) {
        this.message = message;
        this.severity = severity;
        this.line = 0;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s", severity.toUpperCase(), message);
    }
}

// Semantic Analyzer - performs type checking and semantic validation
public class SemanticAnalyzer {
    private SymbolTable symbolTable;
    private AliasTable aliasTable;
    private List<SemanticError> errors;
    private List<SemanticError> warnings;
    private String currentFunction;
    private Map<String, StructDeclNode> structDefinitions;
    
    public SemanticAnalyzer() {
        this.symbolTable = new SymbolTable();
        this.aliasTable = new AliasTable();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.currentFunction = null;
        this.structDefinitions = new HashMap<>();
    }
    
    public void analyze(ProgramNode program) {
        // First pass: collect all declarations (functions, typedefs, global vars, structs)
        for (ASTNode node : program.declarations) {
            if (node instanceof StructDeclNode) {
                analyzeStructDeclaration((StructDeclNode) node);
            } else if (node instanceof TypedefDeclNode) {
                analyzeTypedef((TypedefDeclNode) node);
            } else if (node instanceof FuncDeclNode) {
                analyzeFunctionDeclaration((FuncDeclNode) node);
            } else if (node instanceof VarDeclNode) {
                analyzeGlobalVariable((VarDeclNode) node);
            }
        }
        
        // Second pass: analyze function bodies
        for (ASTNode node : program.declarations) {
            if (node instanceof FuncDeclNode) {
                analyzeFunctionBody((FuncDeclNode) node);
            }
        }
    }
    
    private void analyzeStructDeclaration(StructDeclNode node) {
        if (structDefinitions.containsKey(node.name)) {
            addError("Struct '" + node.name + "' already declared");
            return;
        }
        structDefinitions.put(node.name, node);
    }
    
    private void analyzeTypedef(TypedefDeclNode node) {
        symbolTable.addSymbol(node.newTypeName, node.baseType, "typedef");
    }
    
    private void analyzeFunctionDeclaration(FuncDeclNode node) {
        // Check for duplicate function declaration
        if (symbolTable.lookupInCurrentScope(node.name) != null) {
            addError("Function '" + node.name + "' already declared");
            return;
        }
        
        Symbol funcSymbol = new Symbol(node.name, node.retType, "function", 0);
        funcSymbol.returnType = node.retType;
        
        // Add parameter types
        for (VarDeclNode param : node.args) {
            funcSymbol.paramTypes.add(param.type);
        }
        
        symbolTable.addSymbol(funcSymbol);
    }
    
    private void analyzeGlobalVariable(VarDeclNode node) {
        if (symbolTable.lookupInCurrentScope(node.name) != null) {
            addError("Variable '" + node.name + "' already declared in global scope");
            return;
        }
        
        symbolTable.addSymbol(node.name, node.type, "variable");
        
        // Track pointers
        if (node.type.contains("*")) {
            aliasTable.addPointer(node.name, 0);
            if (node.type.startsWith("void")) {
                aliasTable.markVoidPointer(node.name);
            }
        }
        
        // Check initialization
        if (node.initExpr != null) {
            String exprType = inferType(node.initExpr);
            if (!isTypeCompatible(node.type, exprType)) {
                addError("Type mismatch in initialization of '" + node.name + 
                        "': expected " + node.type + " but got " + exprType);
            }
        }
    }
    
    private void analyzeFunctionBody(FuncDeclNode node) {
        currentFunction = node.name;
        symbolTable.enterScope();
        
        // Add parameters to scope
        for (VarDeclNode param : node.args) {
            if (symbolTable.lookupInCurrentScope(param.name) != null) {
                addError("Parameter '" + param.name + "' already declared");
            } else {
                symbolTable.addSymbol(param.name, param.type, "parameter");
                
                // Track pointer parameters
                if (param.type.contains("*")) {
                    aliasTable.addPointer(param.name, symbolTable.getCurrentLevel());
                    if (param.type.startsWith("void")) {
                        aliasTable.markVoidPointer(param.name);
                    }
                }
            }
        }
        
        // Analyze function body
        if (node.body != null) {
            analyzeStatement(node.body, node.retType);
        }
        
        symbolTable.exitScope();
        currentFunction = null;
    }
    
    private void analyzeStatement(ASTNode node, String expectedReturnType) {
        if (node == null) return;
        
        if (node instanceof BlockNode) {
            BlockNode block = (BlockNode) node;
            for (ASTNode stmt : block.statements) {
                analyzeStatement(stmt, expectedReturnType);
            }
        }
        else if (node instanceof VarDeclNode) {
            VarDeclNode varDecl = (VarDeclNode) node;
            
            if (symbolTable.lookupInCurrentScope(varDecl.name) != null) {
                addError("Variable '" + varDecl.name + "' already declared in this scope");
            } else {
                symbolTable.addSymbol(varDecl.name, varDecl.type, "variable");
                
                // Track pointers
                if (varDecl.type.contains("*")) {
                    aliasTable.addPointer(varDecl.name, symbolTable.getCurrentLevel());
                    if (varDecl.type.startsWith("void")) {
                        aliasTable.markVoidPointer(varDecl.name);
                    }
                }
                
                // Check initialization
                if (varDecl.initExpr != null) {
                    String exprType = inferType(varDecl.initExpr);
                    if (!isTypeCompatible(varDecl.type, exprType)) {
                        addError("Type mismatch in initialization of '" + varDecl.name + 
                                "': expected " + varDecl.type + " but got " + exprType);
                    }
                }
            }
        }
        else if (node instanceof IfStmtNode) {
            IfStmtNode ifStmt = (IfStmtNode) node;
            
            String condType = inferType(ifStmt.condition);
            if (!condType.equals("int") && !condType.equals("bool")) {
                addWarning("Condition should be boolean or integer type, got: " + condType);
            }
            
            symbolTable.enterScope();
            analyzeStatement(ifStmt.thenBranch, expectedReturnType);
            symbolTable.exitScope();
            
            if (ifStmt.elseBranch != null) {
                symbolTable.enterScope();
                analyzeStatement(ifStmt.elseBranch, expectedReturnType);
                symbolTable.exitScope();
            }
        }
        else if (node instanceof WhileStmtNode) {
            WhileStmtNode whileStmt = (WhileStmtNode) node;
            
            String condType = inferType(whileStmt.condition);
            if (!condType.equals("int") && !condType.equals("bool")) {
                addWarning("Loop condition should be boolean or integer type, got: " + condType);
            }
            
            symbolTable.enterScope();
            analyzeStatement(whileStmt.body, expectedReturnType);
            symbolTable.exitScope();
        }
        else if (node instanceof ReturnStmtNode) {
            ReturnStmtNode retStmt = (ReturnStmtNode) node;
            
            if (retStmt.expr != null) {
                String returnType = inferType(retStmt.expr);
                if (!isTypeCompatible(expectedReturnType, returnType)) {
                    addError("Return type mismatch in function '" + currentFunction + 
                            "': expected " + expectedReturnType + " but got " + returnType);
                }
            } else if (!expectedReturnType.equals("void")) {
                addError("Function '" + currentFunction + "' should return " + expectedReturnType);
            }
        }
        else if (node instanceof BinaryExprNode) {
            BinaryExprNode binExpr = (BinaryExprNode) node;
            analyzeBinaryExpression(binExpr);
        }
        else if (node instanceof UnaryExprNode) {
            UnaryExprNode unaryExpr = (UnaryExprNode) node;
            analyzeUnaryExpression(unaryExpr);
        }
        else if (node instanceof FuncCallNode) {
            FuncCallNode funcCall = (FuncCallNode) node;
            analyzeFunctionCall(funcCall);
        }
    }
    
    private void analyzeBinaryExpression(BinaryExprNode node) {
        String leftType = inferType(node.left);
        String rightType = inferType(node.right);
        
        // Assignment operator - check for pointer aliasing
        if (node.operator.equals("=")) {
            if (node.left instanceof IdNode) {
                String varName = ((IdNode) node.left).name;
                Symbol symbol = symbolTable.lookup(varName);
                
                if (symbol != null && symbol.isPointer) {
                    // Track pointer assignment
                    if (node.right instanceof UnaryExprNode) {
                        UnaryExprNode unary = (UnaryExprNode) node.right;
                        if (unary.operator.equals("&") && unary.expr instanceof IdNode) {
                            String targetVar = ((IdNode) unary.expr).name;
                            aliasTable.addAlias(varName, targetVar);
                        }
                    } else if (node.right instanceof IdNode) {
                        String sourcePtr = ((IdNode) node.right).name;
                        // Copy aliases
                        Set<String> aliases = aliasTable.getPointsToSet(sourcePtr);
                        for (String alias : aliases) {
                            aliasTable.addAlias(varName, alias);
                        }
                    }
                }
            }
            
            // Type compatibility check for assignment
            if (!isTypeCompatible(leftType, rightType)) {
                addWarning("Type mismatch in assignment: " + leftType + " = " + rightType);
            }
            return;  // Done with assignment handling
        }
        
        // Check for pointer arithmetic (not assignment)
        if (leftType.contains("*") || rightType.contains("*")) {
            if (!node.operator.equals("+") && !node.operator.equals("-")) {
                addError("Invalid pointer arithmetic with operator: " + node.operator);
            }
        }
        
        // Type compatibility check for other operations
        if (!isTypeCompatible(leftType, rightType)) {
            addWarning("Type mismatch in expression: " + leftType + " " + node.operator + " " + rightType);
        }
    }
    
    private void analyzeUnaryExpression(UnaryExprNode node) {
        String exprType = inferType(node.expr);
        
        // Dereference operator
        if (node.operator.equals("*")) {
            if (!exprType.contains("*")) {
                addError("Cannot dereference non-pointer type: " + exprType);
            } else if (node.expr instanceof IdNode) {
                String ptrName = ((IdNode) node.expr).name;
                aliasTable.checkDereference(ptrName, 0);
            }
        }
        
        // Address-of operator
        if (node.operator.equals("&")) {
            if (!(node.expr instanceof IdNode)) {
                addError("Cannot take address of non-lvalue");
            }
        }
    }
    
    private void analyzeFunctionCall(FuncCallNode node) {
        Symbol funcSymbol = symbolTable.lookup(node.name);
        
        if (funcSymbol == null) {
            addError("Function '" + node.name + "' not declared");
            return;
        }
        
        if (!funcSymbol.kind.equals("function")) {
            addError("'" + node.name + "' is not a function");
            return;
        }
        
        // Check argument count
        if (node.args.size() != funcSymbol.paramTypes.size()) {
            addError("Function '" + node.name + "' expects " + funcSymbol.paramTypes.size() + 
                    " arguments but got " + node.args.size());
            return;
        }
        
        // Check argument types
        for (int i = 0; i < node.args.size(); i++) {
            String argType = inferType(node.args.get(i));
            String paramType = funcSymbol.paramTypes.get(i);
            
            if (!isTypeCompatible(paramType, argType)) {
                addError("Argument " + (i + 1) + " of function '" + node.name + 
                        "': expected " + paramType + " but got " + argType);
            }
        }
    }
    
    private String inferType(ASTNode node) {
        if (node == null) return "void";
        
        if (node instanceof LiteralNode) {
            LiteralNode lit = (LiteralNode) node;
            if (lit.type.equals("CT_INT")) return "int";
            if (lit.type.equals("CT_REAL")) return "double";
            if (lit.type.equals("CT_CHAR")) return "char";
            if (lit.type.equals("CT_STRING")) return "char*";
            return "unknown";
        }
        else if (node instanceof IdNode) {
            IdNode id = (IdNode) node;
            Symbol symbol = symbolTable.lookup(id.name);
            if (symbol != null) {
                return symbol.type;
            }
            addError("Variable '" + id.name + "' not declared");
            return "unknown";
        }
        else if (node instanceof BinaryExprNode) {
            BinaryExprNode binExpr = (BinaryExprNode) node;
            
            // Handle member access operators
            if (binExpr.operator.equals("->")) {
                return handleArrowOperator(binExpr);
            }
            if (binExpr.operator.equals(".")) {
                return handleDotOperator(binExpr);
            }
            
            String leftType = inferType(binExpr.left);
            String rightType = inferType(binExpr.right);
            
            // Pointer arithmetic
            if (leftType.contains("*")) return leftType;
            if (rightType.contains("*")) return rightType;
            
            // Comparison operators return int (C doesn't have bool)
            if (binExpr.operator.equals("==") || binExpr.operator.equals("!=") ||
                binExpr.operator.equals("<") || binExpr.operator.equals(">") ||
                binExpr.operator.equals("<=") || binExpr.operator.equals(">=")) {
                return "int";
            }
            
            // Promote to double if either operand is double
            if (leftType.equals("double") || rightType.equals("double")) {
                return "double";
            }
            
            return "int";
        }
        else if (node instanceof UnaryExprNode) {
            UnaryExprNode unaryExpr = (UnaryExprNode) node;
            String exprType = inferType(unaryExpr.expr);
            
            if (unaryExpr.operator.equals("*")) {
                // Dereference: remove one level of pointer
                if (exprType.contains("*")) {
                    return exprType.replaceFirst("\\*", "");
                }
                return exprType;
            }
            if (unaryExpr.operator.equals("&")) {
                // Address-of: add pointer level
                return exprType + "*";
            }
            
            return exprType;
        }
        else if (node instanceof FuncCallNode) {
            FuncCallNode funcCall = (FuncCallNode) node;
            Symbol funcSymbol = symbolTable.lookup(funcCall.name);
            if (funcSymbol != null) {
                return funcSymbol.returnType;
            }
            return "unknown";
        }
        
        return "unknown";
    }
    
    private String handleArrowOperator(BinaryExprNode node) {
        // ptr->member: left should be pointer to struct
        String leftType = inferType(node.left);
        
        // Get member name from right side
        if (!(node.right instanceof IdNode)) {
            addError("Right side of -> must be a member name");
            return "unknown";
        }
        String memberName = ((IdNode) node.right).name;
        
        // Left should be a pointer to struct
        if (!leftType.contains("*")) {
            addError("Left side of -> must be a pointer");
            return "unknown";
        }
        
        // Remove pointer to get base struct type
        String baseType = leftType.replaceFirst("\\*", "").trim();
        
        return getMemberType(baseType, memberName);
    }
    
    private String handleDotOperator(BinaryExprNode node) {
        // struct.member: left should be struct
        String leftType = inferType(node.left);
        
        // Get member name from right side
        if (!(node.right instanceof IdNode)) {
            addError("Right side of . must be a member name");
            return "unknown";
        }
        String memberName = ((IdNode) node.right).name;
        
        return getMemberType(leftType, memberName);
    }
    
    private String getMemberType(String structType, String memberName) {
        // Extract struct name from "struct StructName" format
        String structName = structType.replaceFirst("^struct\\s+", "").trim();
        
        StructDeclNode structDef = structDefinitions.get(structName);
        if (structDef == null) {
            return "unknown";
        }
        
        // Find the member in struct fields
        for (VarDeclNode field : structDef.fields) {
            if (field.name.equals(memberName)) {
                return field.type;
            }
        }
        
        addError("Struct '" + structName + "' has no member named '" + memberName + "'");
        return "unknown";
    }
    
    private boolean isTypeCompatible(String expected, String actual) {
        // Resolve typedefs
        String resolvedExpected = resolveTypedef(expected);
        String resolvedActual = resolveTypedef(actual);
        
        if (resolvedExpected.equals(resolvedActual)) return true;
        
        // Allow implicit conversions
        if (resolvedExpected.equals("double") && resolvedActual.equals("int")) return true;
        if (resolvedExpected.equals("int") && resolvedActual.equals("char")) return true;
        
        // Pointer compatibility
        if (resolvedExpected.contains("*") && resolvedActual.contains("*")) {
            // void* is compatible with any pointer
            if (resolvedExpected.startsWith("void*") || resolvedActual.startsWith("void*")) {
                return true;
            }
        }
        
        return false;
    }
    
    private String resolveTypedef(String type) {
        // Keep resolving until we reach a base type
        String resolved = type;
        int maxDepth = 10; // Prevent infinite loops
        int depth = 0;
        
        while (depth < maxDepth) {
            Symbol symbol = symbolTable.lookup(resolved.replace("*", "").trim());
            if (symbol != null && symbol.kind.equals("typedef")) {
                // Extract pointer level from original type
                int ptrLevel = 0;
                for (char c : type.toCharArray()) {
                    if (c == '*') ptrLevel++;
                }
                
                // Replace base type, keep pointers
                String pointers = "*".repeat(ptrLevel);
                resolved = symbol.type + pointers;
                depth++;
            } else {
                break;
            }
        }
        
        return resolved;
    }
    
    private void addError(String message) {
        errors.add(new SemanticError(message, "error"));
    }
    
    private void addWarning(String message) {
        warnings.add(new SemanticError(message, "warning"));
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public void printResults() {
        symbolTable.print();
        aliasTable.print();
        
        if (!errors.isEmpty() || !warnings.isEmpty()) {
            System.out.println("\n=== Semantic Analysis Results ===");
        }
        
        if (!warnings.isEmpty()) {
            System.out.println("\nWarnings:");
            for (SemanticError warning : warnings) {
                System.out.println("  " + warning);
            }
        }
        
        if (!errors.isEmpty()) {
            System.out.println("\nErrors:");
            for (SemanticError error : errors) {
                System.out.println("  " + error);
            }
        }
        
        if (errors.isEmpty() && warnings.isEmpty()) {
            System.out.println("\n✓ No semantic errors or warnings found!");
        }
        
        System.out.println("\nSummary: " + errors.size() + " error(s), " + warnings.size() + " warning(s)");
    }
    
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
    
    public AliasTable getAliasTable() {
        return aliasTable;
    }
}
