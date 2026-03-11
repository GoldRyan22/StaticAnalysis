import java.util.*;

public class Parser 
{
    private final List<Token> tokens;
    private int current = 0;
    private final Set<String> typedefNames;

    public Parser(List<Token> tokens) 
    {
        this.tokens = tokens;
        this.typedefNames = new HashSet<>();
    }

    public Parser(List<Token> tokens, Set<String> externalTypes) 
    {
        this.tokens = tokens;
        this.typedefNames = new HashSet<>(externalTypes);
    }

    private List<String> FuncsList;

    public List<String> getFuncs()
    {
        return this.FuncsList;
    }


    public ProgramNode parse() 
    {
        ProgramNode program = new ProgramNode();
        while (!isAtEnd()) 
        {
            int savedPos = current;
            try {
                program.declarations.addAll(parseDeclaration());
            } catch (RuntimeException e) {
                // Error recovery: ensure progress, then skip to next declaration boundary
                if (current == savedPos) advance();
                while (!isAtEnd()) {
                    if (check("LACC")) {
                        // Skip a braced block
                        advance();
                        int depth = 1;
                        while (!isAtEnd() && depth > 0) {
                            if (check("LACC")) { depth++; advance(); }
                            else if (check("RACC")) { depth--; advance(); }
                            else advance();
                        }
                        break;
                    } else if (check("SEMICOLON")) {
                        advance();
                        break;
                    } else {
                        advance();
                    }
                }
            }
            this.FuncsList = program.getFunctionsDecl();
        }
        return program;
    }

    private boolean check(String code) 
    {
        if (isAtEnd()) return false;
        return peek().code.equals(code);
    }

    private boolean match(String... codes) 
    {
        for (String code : codes) 
        {
            if (check(code)) 
            {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(String code, String message) 
    {
        if (check(code)) return advance();
        throw error(peek(), message);
    }

    private Token advance() 
    {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() 
    {
        return current >= tokens.size() || peek().code.equals("END");
    }

    private Token peek() 
    {
        if(current >= tokens.size()) return tokens.get(tokens.size()-1); 
        return tokens.get(current);
    }

    private Token previous() 
    {
        return tokens.get(current - 1);
    }
    
    private boolean checkTypeStart() 
    {
        if (check("UNSIGNED") || check("SIGNED") || check("INT") || check("DOUBLE") || check("FLOAT") ||
            check("CHAR") || check("VOID") || check("LONG") || check("SHORT") || check("STRUCT") ||
            check("CONST") || check("VOLATILE") || check("INLINE") || check("RESTRICT")) {
            return true;
        }
        // Check for custom typedef names
        if (check("ID") && typedefNames.contains(peek().value.toString())) {
            return true;
        }
        return false;
    }

    private RuntimeException error(Token token, String message) 
    {
        return new RuntimeException("Line " + token.line + ": " + message);
    }

    // --- Declarations ---

     private List<ASTNode> parseDeclaration() {
        // Skip function attribute macros: unknown uppercase identifiers before the return type
        while (check("ID") && !typedefNames.contains(peek().value.toString())) {
            int lookahead = current + 1;
            if (lookahead < tokens.size()) {
                String nextCode = tokens.get(lookahead).code;
                boolean nextIsType = nextCode.equals("INT") || nextCode.equals("VOID") ||
                    nextCode.equals("CHAR") || nextCode.equals("LONG") || nextCode.equals("UNSIGNED") ||
                    nextCode.equals("STRUCT") || nextCode.equals("STATIC") || nextCode.equals("CONST") ||
                    nextCode.equals("VOLATILE") || nextCode.equals("DOUBLE") || nextCode.equals("FLOAT") ||
                    nextCode.equals("SHORT") || nextCode.equals("SIGNED") || nextCode.equals("INLINE") ||
                    (nextCode.equals("ID") && typedefNames.contains(tokens.get(lookahead).value.toString()));
                if (nextIsType) {
                    advance(); // skip the attribute macro ID
                    // Also skip argument list if present: MACRO(args)
                    if (check("LPAR")) {
                        advance();
                        int depth = 1;
                        while (!isAtEnd() && depth > 0) {
                            if (check("LPAR")) { depth++; advance(); }
                            else if (check("RPAR")) { depth--; advance(); }
                            else advance();
                        }
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        // Handle storage class specifiers (static, extern, etc.)
        boolean isStatic = false;
        if (check("STATIC")) {
            isStatic = true;
            advance(); // consume 'static'
        }
        
        // 1. Typedef
        if (check("TYPEDEF")) {
            advance(); // consume 'typedef'
            List<ASTNode> list = new ArrayList<>();
            list.add(parseTypedefDecl());
            return list;
        }

        // 2. Struct declarations (struct Name { ... })
        // Check if it's a struct definition vs struct type usage
        if (check("STRUCT")) {
            int savedPos = current;
            advance(); // consume STRUCT
            if (check("ID")) {
                advance(); // consume struct name
                if (check("LACC")) {
                    // It's a struct declaration: struct Name { ... }
                    current = savedPos; // reset position
                    List<ASTNode> list = new ArrayList<>();
                    list.add(parseStructDecl());
                    return list;
                }
            }
            // Not a struct declaration, reset and parse as type
            current = savedPos;
        }

        String type = parseType();
        Token nameTk = consume("ID", "Expect identifier after type.");
        
        if (check("LPAR")) {
            List<ASTNode> list = new ArrayList<>();
            list.add(parseFuncDecl(type, nameTk.value.toString()));
            return list;
        } 
        else {
            return parseVarDecl(type, nameTk.value.toString());
        }
    }

    private ASTNode parseStructDecl() 
    {
        consume("STRUCT", "Expect struct");
        String name = consume("ID", "Expect struct name").value.toString();
        consume("LACC", "Expect {");
        
        List<VarDeclNode> fields = new ArrayList<>();
        while (!check("RACC") && !isAtEnd()) 
        {
            String type = parseType();
            String fieldName = consume("ID", "Expect field name").value.toString();
            consume("SEMICOLON", "Expect ;");
            fields.add(new VarDeclNode(type, fieldName, null));
        }
        consume("RACC", "Expect }");
        consume("SEMICOLON", "Expect ;");
        return new StructDeclNode(name, fields);
    }

    private ASTNode parseTypedefDecl() 
    {
        // Handle: typedef struct/union Name { ... } TypeName;
        if (check("STRUCT") || check("UNION")) {
            boolean isUnion = check("UNION");
            advance(); // consume STRUCT or UNION
            String structName = "";
            if (check("ID")) {
                structName = advance().value.toString();
            }
            
            consume("LACC", "Expect { after struct/union in typedef");
            
            List<VarDeclNode> fields = new ArrayList<>();
            while (!check("RACC") && !isAtEnd()) {
                // Handle anonymous struct/union members: struct { ... }; or union { ... };
                if (check("STRUCT") || check("UNION")) {
                    advance();
                    if (check("LACC")) {
                        advance(); int d = 1;
                        while (!isAtEnd() && d > 0) {
                            if (check("LACC")) { d++; advance(); }
                            else if (check("RACC")) { d--; if (d > 0) advance(); else break; }
                            else advance();
                        }
                        consume("RACC", "Expect } to close anonymous struct/union");
                        if (check("ID")) consume("ID", ""); // optional member name
                        if (check("SEMICOLON")) advance();
                        continue;
                    } else if (check("ID")) {
                        // named nested struct used as a field type - parse as field
                        current--; // put STRUCT/UNION back
                    }
                }
                if (check("RACC")) break;
                String type = parseType();
                if (check("RACC") || check("SEMICOLON")) {
                    if (check("SEMICOLON")) advance(); // anonymous field type
                    continue;
                }
                // Handle function pointer fields: type (*name)(params);
                if (check("LPAR")) {
                    // skip function pointer - consume to semicolon
                    while (!check("SEMICOLON") && !check("RACC") && !isAtEnd()) advance();
                    if (check("SEMICOLON")) advance();
                    continue;
                }
                String fieldName = consume("ID", "Expect field name").value.toString();
                // Skip optional array brackets (loop for multi-dimensional arrays)
                while (check("LBRACKET")) {
                    while (!check("RBRACKET") && !isAtEnd()) advance();
                    advance(); // consume ]
                }
                if (check("SEMICOLON")) advance();
                fields.add(new VarDeclNode(type, fieldName, null));
            }
            consume("RACC", "Expect }");
            
            // After }, there might be: TypeName; or TypeName[]; or varName[] = {...};
            String newTypeName = "";
            if (check("ID")) {
                newTypeName = advance().value.toString();
            }
            // Skip optional array brackets and initializer (loop for multi-dimensional)
            while (check("LBRACKET")) {
                while (!check("RBRACKET") && !isAtEnd()) advance();
                advance(); // consume ]
            }
            if (check("ASSIGN")) {
                advance(); // consume =
                if (check("LACC")) {
                    advance(); int d = 1;
                    while (!isAtEnd() && d > 0) {
                        if (check("LACC")) { d++; advance(); }
                        else if (check("RACC")) { d--; if (d > 0) advance(); else break; }
                        else advance();
                    }
                    consume("RACC", "Expect } to close initializer");
                }
            }
            consume("SEMICOLON", "Expect ; after typedef");
            
            if (!newTypeName.isEmpty()) {
                typedefNames.add(newTypeName);
            }
            String keyword = isUnion ? "union" : "struct";
            String baseType = keyword + " " + (structName.isEmpty() ? newTypeName : structName);
            return new TypedefDeclNode(baseType, newTypeName.isEmpty() ? baseType : newTypeName);
        }
        
        // Handle: typedef existing_type new_type;
        String baseType = parseType();
        // Handle function typedef: typedef type (*name)(params);
        if (check("LPAR")) {
            // Function pointer typedef: typedef type (*name)(params);
            advance(); // consume (
            while (check("MUL")) advance(); // skip *
            String newTypeName = check("ID") ? advance().value.toString() : "";
            consume("RPAR", "Expect ) in function pointer typedef");
            // Skip params
            consume("LPAR", "Expect ( for function pointer params");
            while (!check("RPAR") && !isAtEnd()) advance();
            consume("RPAR", "Expect ) after function pointer params");
            consume("SEMICOLON", "Expect ; after typedef");
            if (!newTypeName.isEmpty()) typedefNames.add(newTypeName);
            return new TypedefDeclNode(baseType, newTypeName);
        }
        Token newTypeNameTk = consume("ID", "Expect new type name after base type in typedef");
        String newTypeName = newTypeNameTk.value.toString();
        // Skip optional array brackets for typedef'd array types (loop for multi-dimensional)
        while (check("LBRACKET")) {
            while (!check("RBRACKET") && !isAtEnd()) advance();
            advance();
        }
        consume("SEMICOLON", "Expect ; after typedef");
        
        // Register the new type name
        typedefNames.add(newTypeName);
        
        return new TypedefDeclNode(baseType, newTypeName);
    }

    private ASTNode parseFuncDecl(String type, String name) 
    {
        consume("LPAR", "Expect (");
        List<VarDeclNode> args = new ArrayList<>();
        if (!check("RPAR")) {
            do {
                // Handle variadic parameter: ...
                if (check("DOT")) {
                    while (check("DOT")) advance(); // consume all three dots
                    // Record the variadic marker so the symbol table sees this as variadic.
                    // SemanticAnalyzer checks paramTypes.last().equals("...") to detect variadic.
                    args.add(new VarDeclNode("...", "...", null));
                    break;
                }
                String argType = parseType();
                // Handle (void) as empty parameter list (C convention)
                if (argType.equals("void") && check("RPAR")) {
                    break;
                }
                // Parameter may omit name (e.g., forward declaration)
                if (check("RPAR") || check("COMMA")) {
                    args.add(new VarDeclNode(argType, "_anon", null));
                    continue;
                }
                String argName = consume("ID", "Expect argument name").value.toString();
                // Skip optional array brackets in parameter (loop for multi-dimensional)
                while (check("LBRACKET")) {
                    while (!check("RBRACKET") && !isAtEnd()) advance();
                    advance(); // consume ]
                }
                args.add(new VarDeclNode(argType, argName, null));
            } while (match("COMMA"));
        }
        consume("RPAR", "Expect )");
        
        // Forward declaration: ends with ';' instead of a body block
        if (check("SEMICOLON")) {
            advance();
            return new FuncDeclNode(type, name, args, null);
        }
        
        ASTNode body = parseBlock();
        return new FuncDeclNode(type, name, args, body);
    }
    
    private List<ASTNode> parseVarDecl(String type, String firstName) {
        List<ASTNode> vars = new ArrayList<>();
        
        vars.add(parseOneVar(type, firstName));

        // Strip pointer stars from type to get the base type for comma-separated declarations.
        // e.g. "listNode*" → "listNode" so that "*next" correctly uses "listNode" + "*" = "listNode*"
        String baseTypeOnly = type.replaceAll("\\*+$", "").trim();

        while (match("COMMA")) {
            // Consume any pointer stars before the identifier,
            // e.g. "listNode *current, *next;" — the second name starts with '*'
            String extraPtrs = "";
            while (match("MUL")) {
                extraPtrs += "*";
            }
            Token nextId = consume("ID", "Expect variable name after comma");
            vars.add(parseOneVar(baseTypeOnly + extraPtrs, nextId.value.toString()));
        }

        consume("SEMICOLON", "Expect ; after variable declaration");
        return vars;
    }
    private VarDeclNode parseOneVar(String baseType, String name) {
        String currentType = baseType;

        // Loop to handle multi-dimensional arrays: T name[D1][D2][D3]...
        while (match("LBRACKET")) {
            // Empty brackets [] means unsized array
            if (match("RBRACKET")) {
                currentType += "[]";
            } else {
                // Try to parse a constant size
                if (check("CT_INT")) {
                    Token size = advance();
                    currentType += "[" + size.value + "]";
                } else {
                    // Non-literal size (e.g. macro constant): consume until ]
                    StringBuilder sb = new StringBuilder("[");
                    while (!check("RBRACKET") && !check("END")) {
                        sb.append(advance().value);
                    }
                    sb.append("]");
                    currentType += sb.toString();
                }
                consume("RBRACKET", "Expect ]");
            }
        }

        ASTNode init = null;
        if (match("ASSIGN")) {
            init = parseExpression();
        }
        
        return new VarDeclNode(currentType, name, init);
    }

   

    private String parseType() 
    {
        String baseType;

        // Consume optional type qualifiers / storage class specifiers
        while (check("CONST") || check("VOLATILE") || check("RESTRICT") || check("REGISTER") ||
               check("INLINE") || check("EXTERN") || check("AUTO")) {
            advance();
        }
        
        if (match("INT")) 
        {
            baseType = "int";
            if (match("SHORT")) baseType = "short";
            else if (match("LONG")) {
                baseType = "long";
                if (match("LONG")) {
                    baseType = "long long";
                }
            }

        }
        else if (match("UNSIGNED")) 
        {
            baseType = "unsigned";
            if (match("SHORT")) baseType = "unsigned short";
            else if(match("CHAR")) baseType = "unsigned char";
            else if (match("INT")) baseType = "unsigned int";
            else
            {
                if (match("LONG")) {
                    baseType += " long";
                    if (match("LONG")) {
                        baseType += " long";
                    }
                }
            }    
        }
        else if (match("CHAR")) baseType = "char";
        else if (match("DOUBLE")) baseType = "double";
        else if (match("FLOAT")) baseType = "float";
        else if (match("VOID")) baseType = "void";
        else if (match("SHORT")) {
            baseType = "short";
            if (match("INT")) {} // short int = short
        }
        else if (match("SIGNED")) {
            baseType = "int"; // signed defaults to signed int
            if (match("CHAR")) baseType = "signed char";
            else if (match("SHORT")) baseType = "short";
            else if (match("INT")) {}
            else if (match("LONG")) {
                baseType = "long";
                if (match("LONG")) baseType = "long long";
            }
        }
        else if (match("LONG")) {
            baseType = "long";
            if (match("LONG")) baseType = "long long";
        }
        else if (match("STRUCT")) 
        {
            Token t = consume("ID", "Expect struct name");
            baseType = "struct " + t.value;
        }
        // Check for custom typedef names
        else if (check("ID") && typedefNames.contains(peek().value.toString())) 
        {
            Token t = advance();
            baseType = t.value.toString();
        }
        else {
            throw error(peek(), "Expect type.");
        }
        
        // Handle pointer types (int*, char**, etc.)
        while (match("MUL")) {
            baseType += "*";
        }
        
        return baseType;
    }

    // --- Statements ---

    private ASTNode parseStatement() 
    {
        if (match("IF")) return parseIf();
        if (match("WHILE")) return parseWhile();
        if (match("FOR")) return parseFor(); 
        if (match("RETURN")) return parseReturn();
        if (match("BREAK")) { consume("SEMICOLON", "Expect ;"); return new LiteralNode("BREAK", "break"); }
        if (match("CONTINUE")) { consume("SEMICOLON", "Expect ;"); return new LiteralNode("CONTINUE", "continue"); }
        if (match("GOTO")) {
            // skip the label identifier and semicolon
            if (check("ID")) advance();
            consume("SEMICOLON", "Expect ; after goto");
            return new LiteralNode("GOTO", "goto");
        }
        if (match("DO")) return parseDoWhile();
        if (match("SWITCH")) return parseSwitch();
        // case/default labels (inside switch bodies)
        if (match("CASE")) {
            parseExpression();
            consume("COLON", "Expect ':' after case value");
            return new LiteralNode("CASE_LABEL", "case");
        }
        if (match("DEFAULT")) {
            consume("COLON", "Expect ':' after default");
            return new LiteralNode("DEFAULT_LABEL", "default");
        }
        if (check("LACC")) return parseBlock();
        
        // Handle empty statement (just a semicolon)
        if (match("SEMICOLON")) {
            return new LiteralNode("EMPTY", "");
        }

        // Labeled statement: IDENTIFIER ':' statement
        if (check("ID") && current + 1 < tokens.size() && tokens.get(current + 1).code.equals("COLON")) {
            advance(); // consume label name
            advance(); // consume ':'
            return parseStatement(); // parse the labeled statement
        }
        
        ASTNode expr = parseExpression();
        consume("SEMICOLON", "Expect ; after expression");
        return expr;
    }

    private ASTNode parseBlock() {
        consume("LACC", "Expect {");
        BlockNode block = new BlockNode();
        while (!check("RACC") && !isAtEnd()) {
            if (checkTypeStart()) {
                // Look ahead to distinguish variable declaration from expression statement
                int savedPos = current;
                String t = parseType();
                
                // After parsing type, check if next is ID (variable name)
                if (check("ID")) {
                    // This looks like a declaration
                    String n = advance().value.toString();
                    block.statements.addAll(parseVarDecl(t, n));
                } else {
                    // Not a declaration (e.g., list->field), reset and parse as expression
                    current = savedPos;
                    block.statements.add(parseStatement());
                }
            } else {
                block.statements.add(parseStatement());
            }
        }
        consume("RACC", "Expect }");
        return block;
    }

    private ASTNode parseIf() 
    {
        consume("LPAR", "Expect (");
        ASTNode condition = parseExpression();
        consume("RPAR", "Expect )");
        ASTNode thenBranch = parseStatement();
        ASTNode elseBranch = null;
        if (match("ELSE")) 
        {
            elseBranch = parseStatement();
        }
        return new IfStmtNode(condition, thenBranch, elseBranch);
    }

    private ASTNode parseWhile() 
    {
        consume("LPAR", "Expect (");
        ASTNode condition = parseExpression();
        consume("RPAR", "Expect )");
        ASTNode body = parseStatement();
        return new WhileStmtNode(condition, body);
    }
    
    private ASTNode parseFor() 
    {
        consume("LPAR", "Expect (");

        // Init: can be empty, a declaration, or an expression
        ASTNode init = new LiteralNode("EMPTY", "");
        if (!check("SEMICOLON")) {
            if (checkTypeStart()) {
                int savedPos = current;
                String t = parseType();
                if (check("ID")) {
                    String n = advance().value.toString();
                    // parseVarDecl consumes the semicolon itself
                    List<ASTNode> decls = parseVarDecl(t, n);
                    init = decls.isEmpty() ? new LiteralNode("EMPTY", "") : decls.get(0);
                    // semicolon already consumed by parseVarDecl, fall through to cond
                    ASTNode cond = check("SEMICOLON") ? new LiteralNode("CT_INT", 1) : parseExpression();
                    consume("SEMICOLON", "Expect ;");
                    ASTNode step = check("RPAR") ? new LiteralNode("EMPTY", "") : parseExpression();
                    consume("RPAR", "Expect )");
                    ASTNode body = parseStatement();
                    BlockNode forBlock = new BlockNode();
                    forBlock.statements.add(init);
                    BlockNode whileBody = new BlockNode();
                    whileBody.statements.add(body);
                    whileBody.statements.add(step);
                    forBlock.statements.add(new WhileStmtNode(cond, whileBody));
                    return forBlock;
                } else {
                    current = savedPos;
                    init = parseExpression();
                    consume("SEMICOLON", "Expect ;");
                }
            } else {
                init = parseExpression();
                consume("SEMICOLON", "Expect ;");
            }
        } else {
            consume("SEMICOLON", "Expect ;");
        }

        ASTNode cond = check("SEMICOLON") ? new LiteralNode("CT_INT", 1) : parseExpression();
        consume("SEMICOLON", "Expect ;");
        ASTNode step = check("RPAR") ? new LiteralNode("EMPTY", "") : parseExpression();
        consume("RPAR", "Expect )");
        ASTNode body = parseStatement();
        
        BlockNode forBlock = new BlockNode();
        forBlock.statements.add(init);
        
        BlockNode whileBody = new BlockNode();
        whileBody.statements.add(body);
        whileBody.statements.add(step);
        
        forBlock.statements.add(new WhileStmtNode(cond, whileBody));
        return forBlock;
    }

    private ASTNode parseReturn() 
    {
        ASTNode value = null;
        if (!check("SEMICOLON")) 
        {
            value = parseExpression();
        }
        consume("SEMICOLON", "Expect ;");
        return new ReturnStmtNode(value);
    }

    private ASTNode parseDoWhile() {
        ASTNode body = parseStatement();
        consume("WHILE", "Expect 'while' after do body");
        consume("LPAR", "Expect '('");
        ASTNode cond = parseExpression();
        consume("RPAR", "Expect ')'");
        consume("SEMICOLON", "Expect ';' after do-while");
        return new WhileStmtNode(cond, body);
    }

    private ASTNode parseSwitch() {
        consume("LPAR", "Expect '('");
        ASTNode expr = parseExpression();
        consume("RPAR", "Expect ')'");
        // Parse the body (contains case/default labels handled in parseStatement)
        ASTNode body = parseBlock();
        // Model switch as a while-false so CFG captures all branches
        return new WhileStmtNode(expr, body);
    }

    // --- Expressions

    private ASTNode parseExpression() 
    {
        return parseAssignment();
    }

    private ASTNode parseAssignment() 
    {
        ASTNode expr = parseTernary();
        if (match("ASSIGN")) 
        {
            ASTNode value = parseAssignment();
            return new BinaryExprNode(expr, "=", value);
        }
        // Handle compound assignments: +=, -=, *=, /=, &=, |=
        // The lexer produces two tokens (e.g. ADDFinal then ASSIGN), so peek ahead
        String[] compoundOps = {"ADDFinal", "ADD", "SUBFinal", "SUB", "MUL", "DIV", "BITAND", "BITOR", "LSHIFT", "RSHIFT"};
        String[] opSymbols   = {"+",        "+",   "-",        "-",   "*",   "/",   "&",      "|",     "<<",     ">>"    };
        for (int i = 0; i < compoundOps.length; i++) {
            if (check(compoundOps[i]) && current + 1 < tokens.size() && tokens.get(current + 1).code.equals("ASSIGN")) {
                String op = opSymbols[i];
                advance(); // consume OP token
                advance(); // consume ASSIGN token
                ASTNode value = parseAssignment();
                // desugar: expr OP= value  →  expr = expr OP value
                return new BinaryExprNode(expr, "=", new BinaryExprNode(expr, op, value));
            }
        }
        return expr;
    }

    private ASTNode parseTernary() {
        ASTNode cond = parseOr();
        if (match("QUESTION")) {
            ASTNode thenExpr = parseExpression();
            consume("COLON", "Expect ':' in ternary expression");
            ASTNode elseExpr = parseTernary();
            return new TernaryExprNode(cond, thenExpr, elseExpr);
        }
        return cond;
    }

    private ASTNode parseOr() 
    {
        ASTNode expr = parseAnd();
        while (match("OR")) 
        {
            ASTNode right = parseAnd();
            expr = new BinaryExprNode(expr, "||", right);
        }
        return expr;
    }

    private ASTNode parseAnd() 
    {
        ASTNode expr = parseBitOr();
        while (match("AND")) 
        {
            ASTNode right = parseBitOr();
            expr = new BinaryExprNode(expr, "&&", right);
        }
        return expr;
    }

    private ASTNode parseBitOr() {
        ASTNode expr = parseBitAnd();
        while (check("BITOR") && !(current + 1 < tokens.size() && tokens.get(current + 1).code.equals("ASSIGN"))) {
            advance();
            ASTNode right = parseBitAnd();
            expr = new BinaryExprNode(expr, "|", right);
        }
        return expr;
    }

    private ASTNode parseBitAnd() {
        ASTNode expr = parseEquality();
        while (check("BITAND") && !(current + 1 < tokens.size() && tokens.get(current + 1).code.equals("ASSIGN"))) {
            advance();
            ASTNode right = parseEquality();
            expr = new BinaryExprNode(expr, "&", right);
        }
        return expr;
    }

    private ASTNode parseEquality() 
    {
        ASTNode expr = parseRelational();
        while (check("EQUAL") || check("NOTEQ")) 
        {
            String op = advance().code.equals("EQUAL") ? "==" : "!=";
            ASTNode right = parseRelational();
            expr = new BinaryExprNode(expr, op, right);
        }
        return expr;
    }

    private ASTNode parseRelational() 
    {
        ASTNode expr = parseShift();
        while (check("LESS") || check("LESSEQ") || check("GREATER") || check("GREATEREQ")) 
        {
            Token opTk = advance();
            String op = "";
            if(opTk.code.equals("LESS")) op = "<";
            if(opTk.code.equals("LESSEQ")) op = "<=";
            if(opTk.code.equals("GREATER")) op = ">";
            if(opTk.code.equals("GREATEREQ")) op = ">=";
            ASTNode right = parseShift();
            expr = new BinaryExprNode(expr, op, right);
        }
        return expr;
    }

    private ASTNode parseShift()
    {
        ASTNode expr = parseAddSub();
        while ((check("LSHIFT") || check("RSHIFT"))
               && !(current + 1 < tokens.size() && tokens.get(current + 1).code.equals("ASSIGN")))
        {
            String op = advance().code.equals("LSHIFT") ? "<<" : ">>";
            ASTNode right = parseAddSub();
            expr = new BinaryExprNode(expr, op, right);
        }
        return expr;
    }

    private ASTNode parseAddSub() 
    {
        ASTNode expr = parseTerm();
        while ((check("ADD") || check("ADDFinal") || check("SUB") || check("SUBFinal"))
               && !(current + 1 < tokens.size() && tokens.get(current + 1).code.equals("ASSIGN"))) 
        {
            Token opToken = advance();
            String op = (opToken.code.equals("ADD") || opToken.code.equals("ADDFinal")) ? "+" : "-";
            ASTNode right = parseTerm();
            expr = new BinaryExprNode(expr, op, right);
        }
        return expr;
    }

    private ASTNode parseTerm() 
    {
        ASTNode expr = parseUnary();
        while ((check("MUL") || check("DIV") || check("MOD"))
               && !(current + 1 < tokens.size() && tokens.get(current + 1).code.equals("ASSIGN"))) 
        {
            String op = advance().code.equals("MUL") ? "*" : (previous().code.equals("DIV") ? "/" : "%");
            ASTNode right = parseUnary();
            expr = new BinaryExprNode(expr, op, right);
        }
        return expr;
    }

    private ASTNode parseUnary() 
    {
        if (check("NOT") || check("SUB") || check("SUBFinal") || check("MUL") || check("AND") || check("BITAND") || check("INC") || check("DEC")) 
        {
            Token op = advance();
            String operator;
            if (op.code.equals("NOT")) operator = "!";
            else if (op.code.equals("SUB") || op.code.equals("SUBFinal")) operator = "-";
            else if (op.code.equals("MUL")) operator = "*";  // Dereference
            else if (op.code.equals("AND") || op.code.equals("BITAND")) operator = "&";  // Address-of
            else if (op.code.equals("INC")) operator = "++";
            else if (op.code.equals("DEC")) operator = "--";
            else operator = op.code;
            
            ASTNode right = parseUnary();
            return new UnaryExprNode(operator, right);
        }
        return parsePostfix();
    }

    private ASTNode parsePostfix() {
        ASTNode expr = parsePrimary();
        
        while (true) {
            if (match("ARROW")) {
                // ptr->member
                Token member = consume("ID", "Expect member name after '->'.");
                expr = new BinaryExprNode(expr, "->", new IdNode(member.value.toString()));
            }
            else if (match("DOT")) {
                // struct.member
                Token member = consume("ID", "Expect member name after '.'.");
                expr = new BinaryExprNode(expr, ".", new IdNode(member.value.toString()));
            }
            else if (match("LBRACKET")) {
                // array[index]
                ASTNode index = parseExpression();
                consume("RBRACKET", "Expect ']'");
                expr = new BinaryExprNode(expr, "[", index);
            }
            else if (match("LPAR")) {
                // function call: can be direct call or through function pointer
                // expr can be an IdNode (simple call) or BinaryExprNode (member access like ptr->func)
                List<ASTNode> args = new ArrayList<>();
                if (!check("RPAR")) {
                    do {
                        args.add(parseExpression());
                    } while (match("COMMA"));
                }
                consume("RPAR", "Expect ')'");
                
                // Create a function call node with the expression as the function
                // For simple calls, expr is IdNode("funcName")
                // For member access, expr is BinaryExprNode(ptr, "->", "member")
                if (expr instanceof IdNode) {
                    expr = new FuncCallNode(((IdNode)expr).name, args);
                } else {
                    // For complex expressions like ptr->func(args), we need a way to represent this
                    // For now, create a special function call with the full expression
                    expr = new FuncCallNode(expr.toString(0), args);
                }
            }
            else if (match("INC")) {
                // postfix ++
                expr = new UnaryExprNode("++", expr, true);
            }
            else if (match("DEC")) {
                // postfix --
                expr = new UnaryExprNode("--", expr, true);
            }
            else {
                break;
            }
        }
        
        return expr;
    }

    private ASTNode parsePrimary() {
        if (match("LPAR")) {
            // Check for C-style cast: (type) expr
            // A cast starts with a type keyword or a known typedef name
            if (isCastExpression()) {
                String castType = parseType();
                consume("RPAR", "Expect ')' after cast type");
                ASTNode inner = parseUnary();
                return new CastExprNode(castType, inner);
            }
            ASTNode expr = parseExpression();
            consume("RPAR", "Expect ')' after expression.");
            return expr;
        }
        
        // sizeof(expr) or sizeof(type) - registered in StandardLibrary as returning size_t
        if (match("SIZEOF")) {
            consume("LPAR", "Expect '(' after sizeof");
            List<ASTNode> sizeofArgs = new ArrayList<>();
            // If it starts with a type keyword or known typedef, skip tokens (type operand)
            if (check("STRUCT") || check("INT") || check("CHAR") || check("VOID")
                    || check("DOUBLE") || check("FLOAT") || check("UNSIGNED") || check("LONG")
                    || (check("ID") && typedefNames.contains(peek().value.toString()))) {
                // Consume all type tokens until closing paren
                while (!check("RPAR") && !check("END")) {
                    advance();
                }
            } else {
                // Expression operand - parse it and pass as argument so type is available
                sizeofArgs.add(parseExpression());
            }
            consume("RPAR", "Expect ')' after sizeof operand");
            // Emit FuncCallNode so semantic analyzer resolves return type as size_t
            return new FuncCallNode("sizeof", sizeofArgs);
        }
        
        if (check("CT_INT") || check("CT_REAL") || check("CT_CHAR") || check("CT_STRING") || check("HEX") || check("OCT") || check("FLOAT") || check("NULL")) 
        {
            Token t = advance();
            return new LiteralNode(t.code, t.value);
        }
        
        if (check("ID")) 
        {
            Token t = advance();
            return new IdNode(t.value.toString());
        }

        // Initializer list: { expr, expr, ... }
        if (match("LACC")) {
            int depth = 1;
            while (depth > 0 && !isAtEnd()) {
                if (check("LACC")) { depth++; advance(); }
                else if (check("RACC")) { depth--; if (depth > 0) advance(); else break; }
                else advance();
            }
            consume("RACC", "Expect '}' to close initializer list");
            return new LiteralNode("INIT_LIST", "{}");
        }
        
        throw error(peek(), "Expect expression.");
    }
    
    /**
     * Look ahead to determine if the current position (after consuming LPAR) is a C-style cast.
     * A cast has the form: (type-keywords [*]*) followed by an expression token.
     * We peek without consuming.
     */
    private boolean isCastExpression() {
        // Save position
        int saved = current;
        try {
            // Must start with a type keyword or typedef name
            if (!check("INT") && !check("CHAR") && !check("VOID") && !check("DOUBLE")
                    && !check("FLOAT") && !check("UNSIGNED") && !check("LONG")
                    && !check("SHORT") && !check("SIGNED") && !check("STRUCT")
                    && !(check("ID") && typedefNames.contains(peek().value.toString()))) {
                return false;
            }
            // Skip type tokens
            while (check("INT") || check("CHAR") || check("VOID") || check("DOUBLE")
                    || check("FLOAT") || check("UNSIGNED") || check("LONG") || check("SHORT")
                    || check("SIGNED") || check("STRUCT")
                    || (check("ID") && typedefNames.contains(peek().value.toString()))) {
                advance();
            }
            // Skip pointer stars
            while (check("MUL")) advance();
            // Must close with RPAR
            if (!check("RPAR")) return false;
            return true;
        } finally {
            current = saved;
        }
    }
}
