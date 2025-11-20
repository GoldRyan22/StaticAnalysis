import java.util.*;


public class Main 
{
    public static void main(String[] args) 
    {
        LexAn lex = new LexAn();

        List<Token> tokens = lex.LexicalAnalysis("../tests/6.c");

        try 
        {
            Parser parser = new Parser(tokens);
            ASTNode tree = parser.parse();
            
            System.out.println(" Abstract Syntax Tree ");
            System.out.println(tree.toString(0));
            for(String funcDecl : parser.getFuncs())
            {
                 System.out.println(funcDecl);
            }

        List<String> funcDecl = new ArrayList<>();
        funcDecl = parser.getFuncs();

        AiAnalyzer.analyzeFunctions(funcDecl, "int main(void)");
    
        System.out.println("\n");

        } catch (Exception e) 
        {
            System.err.println("Parser Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}