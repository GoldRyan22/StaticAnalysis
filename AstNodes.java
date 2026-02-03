import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


abstract class ASTNode 
{
    public abstract String toString(int indent);
    
    protected String getIndent(int indent) 
    {
        return "  ".repeat(indent);
    }
}


class ProgramNode extends ASTNode 
{
    List<ASTNode> declarations = new ArrayList<>();

    @Override
    public String toString(int indent) 
    {
        StringBuilder sb = new StringBuilder();
        for (ASTNode node : declarations) 
        {
            sb.append(node.toString(indent)).append("\n");
        }
        return sb.toString();
    }

    public List<String> getFunctionsDecl()
    {
        List<String> funcDeclList = new ArrayList<>();
        for (ASTNode node : declarations)
        {
            if(node instanceof FuncDeclNode)
            {
                funcDeclList.add(((FuncDeclNode)node).toString());
            }
        }

        return funcDeclList;
    }
}

class VarDeclNode extends ASTNode 
{
    String type;
    String name;
    ASTNode initExpr;

    public VarDeclNode(String type, String name, ASTNode initExpr) 
    {
        this.type = type;
        this.name = name;
        this.initExpr = initExpr;
    }

    @Override
    public String toString(int indent) 
    {
        return getIndent(indent) + "VarDecl: " + type + " " + name + 
               (initExpr != null ? " = " + initExpr.toString(0) : "");
    }

}

class FuncDeclNode extends ASTNode 
{
    String retType;
    String name;
    List<VarDeclNode> args;
    ASTNode body;

    public FuncDeclNode(String retType, String name, List<VarDeclNode> args, ASTNode body) 
    {
        this.retType = retType;
        this.name = name;
        this.args = args;
        this.body = body;
    }

    @Override
    public String toString(int indent) 
    {
        String argStr = args.stream().map(a -> a.type + " " + a.name).collect(Collectors.joining(", "));
        return getIndent(indent) + "FuncDecl: " + retType + " " + name + "(" + argStr + ")\n" +
               body.toString(indent + 1);
    }

    @Override
    public String toString()
    {
        String argStr = args.stream().map(a -> a.type + " " + a.name).collect(Collectors.joining(", "));
        return  "" + retType + " " + name + "(" + argStr + ")";
    }
}

class StructDeclNode extends ASTNode 
{
    String name;
    List<VarDeclNode> fields;

    public StructDeclNode(String name, List<VarDeclNode> fields) 
    {
        this.name = name;
        this.fields = fields;
    }

    @Override
    public String toString(int indent) 
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("StructDecl: ").append(name).append("\n");
        for(VarDeclNode f : fields) sb.append(f.toString(indent + 1)).append("\n");
        return sb.toString();
    }
}

class TypedefDeclNode extends ASTNode 
{
    String baseType;
    String newTypeName;

    public TypedefDeclNode(String baseType, String newTypeName) 
    {
        this.baseType = baseType;
        this.newTypeName = newTypeName;
    }

    @Override
    public String toString(int indent) 
    {
        return getIndent(indent) + "TypedefDecl: typedef " + baseType + " " + newTypeName;
    }
}

// --- Statements ---

class BlockNode extends ASTNode 
{
    List<ASTNode> statements = new ArrayList<>();

    @Override
    public String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(getIndent(indent)).append("Block {\n");
        for (ASTNode s : statements) {
            sb.append(s.toString(indent + 1)).append("\n");
        }
        sb.append(getIndent(indent)).append("}");
        return sb.toString();
    }
}

class IfStmtNode extends ASTNode 
{
    ASTNode condition;
    ASTNode thenBranch;
    ASTNode elseBranch;

    public IfStmtNode(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch) 
    {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    @Override
    public String toString(int indent) 
    {
        String s = getIndent(indent) + "If (" + condition.toString(0) + ")\n" + thenBranch.toString(indent + 1);
        if (elseBranch != null) {
            s += "\n" + getIndent(indent) + "Else\n" + elseBranch.toString(indent + 1);
        }
        return s;
    }
}

class WhileStmtNode extends ASTNode 
{
    ASTNode condition;
    ASTNode body;

    public WhileStmtNode(ASTNode condition, ASTNode body) 
    {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public String toString(int indent) 
    {
        return getIndent(indent) + "While (" + condition.toString(0) + ")\n" + body.toString(indent + 1);
    }
}

class ReturnStmtNode extends ASTNode 
{
    ASTNode expr;

    public ReturnStmtNode(ASTNode expr) 
    {
        this.expr = expr;
    }

    @Override
    public String toString(int indent) 
    {
        return getIndent(indent) + "Return " + (expr != null ? expr.toString(0) : "");
    }
}

// --- Expressions ---

class BinaryExprNode extends ASTNode 
{
    ASTNode left;
    String operator;
    ASTNode right;

    public BinaryExprNode(ASTNode left, String operator, ASTNode right) 
    {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public String toString(int indent) 
    {
        return "(" + left.toString(0) + " " + operator + " " + right.toString(0) + ")";
    }
}

class UnaryExprNode extends ASTNode 
{
    String operator;
    ASTNode expr;
    boolean isPostfix;  // true for a++, false for ++a

    public UnaryExprNode(String operator, ASTNode expr) {
        this.operator = operator;
        this.expr = expr;
        this.isPostfix = false;  // default to prefix
    }

    public UnaryExprNode(String operator, ASTNode expr, boolean isPostfix) {
        this.operator = operator;
        this.expr = expr;
        this.isPostfix = isPostfix;
    }

    @Override
    public String toString(int indent) 
    {
        if (isPostfix) {
            return "(" + expr.toString(0) + operator + ")";
        } else {
            return "(" + operator + expr.toString(0) + ")";
        }
    }
}

class LiteralNode extends ASTNode 
{
    String type; // INT, REAL, STRING
    Object value;

    public LiteralNode(String type, Object value) 
    {
        this.type = type;
        this.value = value;
    }

    @Override
    public String toString(int indent) 
    {
        return value.toString();
    }
}

class IdNode extends ASTNode 
{
    String name;

    public IdNode(String name) 
    {
        this.name = name;
    }

    @Override
    public String toString(int indent) 
    {
        return name;
    }
}

class FuncCallNode extends ASTNode 
{
    String name;
    List<ASTNode> args;

    public FuncCallNode(String name, List<ASTNode> args) {
        this.name = name;
        this.args = args;
    }

    @Override
    public String toString(int indent) 
    {
        return name + "(" + args.stream().map(a -> a.toString(0)).collect(Collectors.joining(", ")) + ")";
    }
}