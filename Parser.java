import java.util.*;



public class Parser 
{
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) 
    {
        this.tokens = tokens;
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
        return check("INT") || check("DOUBLE") || check("CHAR") || check("VOID") || check("STRUCT");
    }

    private RuntimeException error(Token token, String message) 
    {
        return new RuntimeException("Line " + token.line + ": " + message);
    }

    // --- Declarations ---

     private List<ASTNode> parseDeclaration() {
        // 1. Structs
        if (check("STRUCT")) {
            List<ASTNode> list = new ArrayList<>();
            list.add(parseStructDecl());
            return list;
        }

        String type = parseType();
        Token nameTk = consume("ID", "Expect identifier after type.");
        
        // 2. Functions (Start with parenthesis)
        if (check("LPAR")) {
            List<ASTNode> list = new ArrayList<>();
            list.add(parseFuncDecl(type, nameTk.value.toString()));
            return list;
        } 
        // 3. Variables (Start with comma, assign, semicolon, or bracket)
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

    // Refactored: Returns a list of nodes because "int a, b;" creates two VarDeclNodes
    private List<ASTNode> parseVarDecl(String type, String firstName) {
        List<ASTNode> vars = new ArrayList<>();
        
        // 1. Parse the first variable (the ID was already consumed by the caller)
        vars.add(parseOneVar(type, firstName));

        // 2. Loop while there are commas (e.g., int a [, b, c] ;)
        while (match("COMMA")) {
            Token nextId = consume("ID", "Expect variable name after comma");
            vars.add(parseOneVar(type, nextId.value.toString()));
        }

        consume("SEMICOLON", "Expect ; after variable declaration");
        return vars;
    }

    // Helper: Handles array brackets and initialization for a single variable
    private VarDeclNode parseOneVar(String baseType, String name) {
        String currentType = baseType;

        // Check for Array: int a[10]
        if (match("LBRACKET")) {
             Token size = consume("CT_INT", "Expect array size");
             consume("RBRACKET", "Expect ]");
             currentType += "[" + size.value + "]";
        }

        // Check for Initialization: = 5
        ASTNode init = null;
        if (match("ASSIGN")) {
            init = parseExpression();
        }
        
        return new VarDeclNode(currentType, name, init);
    }

   

    private String parseType() 
    {
        if (match("INT")) return "int";
        if (match("DOUBLE")) return "double";
        if (match("CHAR")) return "char";
        if (match("VOID")) return "void";
        if (match("STRUCT")) 
        {
            Token t = consume("ID", "Expect struct name");
            return "struct " + t.value;
        }
        throw error(peek(), "Expect type.");
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
        
        ASTNode expr = parseExpression();
        consume("SEMICOLON", "Expect ; after expression");
        return expr;
    }

    private ASTNode parseBlock() {
        consume("LACC", "Expect {");
        BlockNode block = new BlockNode();
        while (!check("RACC") && !isAtEnd()) {
            if (checkTypeStart()) {
                String t = parseType();
                String n = consume("ID", "Expect ID").value.toString();

                block.statements.addAll(parseVarDecl(t, n));
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
        while (check("ADD") || check("SUB")) 
        {
            String op = advance().code.equals("ADD") ? "+" : "-";
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
        if (check("NOT") || check("SUB")) 
        {
            String op = advance().code.equals("NOT") ? "!" : "-";
            ASTNode right = parseUnary();
            return new UnaryExprNode(op, right);
        }
        return parsePrimary();
    }

    private ASTNode parsePrimary() {
        if (match("LPAR")) {
            ASTNode expr = parseExpression();
            consume("RPAR", "Expect ')' after expression.");
            return expr;
        }
        
        if (check("CT_INT") || check("CT_REAL") || check("CT_CHAR") || check("CT_STRING")) 
        {
            Token t = advance();
            return new LiteralNode(t.code, t.value);
        }
        
        if (check("ID")) 
        {
            Token t = advance();
            
            if (match("LPAR")) 
            {
                List<ASTNode> args = new ArrayList<>();
                if (!check("RPAR")) 
                {
                    do 
                    {
                        args.add(parseExpression());
                    } while (match("COMMA"));
                }
                consume("RPAR", "Expect )");
                return new FuncCallNode(t.value.toString(), args);
            }
            
            ASTNode node = new IdNode(t.value.toString());
            while (match("LBRACKET")) 
            {
                ASTNode index = parseExpression();
                consume("RBRACKET", "Expect ]");
                node = new BinaryExprNode(node, "[", index);
            }
            
            return node;
        }
        
        throw error(peek(), "Expect expression.");
    }
}