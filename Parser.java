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
            program.declarations.addAll(parseDeclaration());
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
        if (check("UNSIGNED") || check("INT") || check("DOUBLE") || check("FLOAT") || check("CHAR") || check("VOID") || check("STRUCT")) {
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
        // Handle: typedef struct Name { ... } TypeName;
        if (check("STRUCT")) {
            advance(); // consume STRUCT
            String structName = "";
            if (check("ID")) {
                structName = advance().value.toString();
            }
            
            consume("LACC", "Expect { after struct in typedef");
            
            List<VarDeclNode> fields = new ArrayList<>();
            while (!check("RACC") && !isAtEnd()) {
                String type = parseType();
                String fieldName = consume("ID", "Expect field name").value.toString();
                consume("SEMICOLON", "Expect ;");
                fields.add(new VarDeclNode(type, fieldName, null));
            }
            consume("RACC", "Expect }");
            
            Token newTypeNameTk = consume("ID", "Expect type name after struct definition");
            String newTypeName = newTypeNameTk.value.toString();
            consume("SEMICOLON", "Expect ; after typedef");
            
            // Register the new type name
            typedefNames.add(newTypeName);
            
            // Create both struct declaration and typedef
            // For simplicity, we'll just return typedef and assume struct is implicitly defined
            String baseType = "struct " + (structName.isEmpty() ? newTypeName : structName);
            return new TypedefDeclNode(baseType, newTypeName);
        }
        
        // Handle: typedef existing_type new_type;
        String baseType = parseType();
        Token newTypeNameTk = consume("ID", "Expect new type name after base type in typedef");
        String newTypeName = newTypeNameTk.value.toString();
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
                String argType = parseType();
                String argName = consume("ID", "Expect argument name").value.toString();
                args.add(new VarDeclNode(argType, argName, null));
            } while (match("COMMA"));
        }
        consume("RPAR", "Expect )");
        
        ASTNode body = parseBlock();
        return new FuncDeclNode(type, name, args, body);
    }
    
    private List<ASTNode> parseVarDecl(String type, String firstName) {
        List<ASTNode> vars = new ArrayList<>();
        
        vars.add(parseOneVar(type, firstName));

        while (match("COMMA")) {
            Token nextId = consume("ID", "Expect variable name after comma");
            vars.add(parseOneVar(type, nextId.value.toString()));
        }

        consume("SEMICOLON", "Expect ; after variable declaration");
        return vars;
    }
    private VarDeclNode parseOneVar(String baseType, String name) {
        String currentType = baseType;

        if (match("LBRACKET")) {
             Token size = consume("CT_INT", "Expect array size");
             consume("RBRACKET", "Expect ]");
             currentType += "[" + size.value + "]";
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
        
        if (match("INT")) baseType = "int";
        else if (match("UNSIGNED")) 
            {
                baseType = "unsigned";
                if (match("LONG")) {
                    baseType += " long";
                    if (match("LONG")) {
                        baseType += " long";
                    }
                }
            }
        else if (match("DOUBLE")) baseType = "double";
        else if (match("FLOAT")) baseType = "float";
        else if (match("VOID")) baseType = "void";
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
        if (check("LACC")) return parseBlock();
        
        // Handle empty statement (just a semicolon)
        if (match("SEMICOLON")) {
            return new LiteralNode("EMPTY", "");
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
        ASTNode init = parseExpression(); 
        consume("SEMICOLON", "Expect ;");
        ASTNode cond = parseExpression();
        consume("SEMICOLON", "Expect ;");
        ASTNode step = parseExpression();
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

    // --- Expressions

    private ASTNode parseExpression() 
    {
        return parseAssignment();
    }

    private ASTNode parseAssignment() 
    {
        ASTNode expr = parseOr();
        if (match("ASSIGN")) 
        {
            ASTNode value = parseAssignment();
            return new BinaryExprNode(expr, "=", value);
        }
        return expr;
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
        ASTNode expr = parseEquality();
        while (match("AND")) 
        {
            ASTNode right = parseEquality();
            expr = new BinaryExprNode(expr, "&&", right);
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
        ASTNode expr = parseAddSub();
        while (check("LESS") || check("LESSEQ") || check("GREATER") || check("GREATEREQ")) 
        {
            Token opTk = advance();
            String op = "";
            if(opTk.code.equals("LESS")) op = "<";
            if(opTk.code.equals("LESSEQ")) op = "<=";
            if(opTk.code.equals("GREATER")) op = ">";
            if(opTk.code.equals("GREATEREQ")) op = ">=";
            ASTNode right = parseAddSub();
            expr = new BinaryExprNode(expr, op, right);
        }
        return expr;
    }

    private ASTNode parseAddSub() 
    {
        ASTNode expr = parseTerm();
        while (check("ADD") || check("ADDFinal") || check("SUB") || check("SUBFinal")) 
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
        while (check("MUL") || check("DIV")) 
        {
            String op = advance().code.equals("MUL") ? "*" : "/";
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
            ASTNode expr = parseExpression();
            consume("RPAR", "Expect ')' after expression.");
            return expr;
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
        
        throw error(peek(), "Expect expression.");
    }
}

