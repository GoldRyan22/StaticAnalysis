import java.util.*;

public class TestSemantics 
{
    public static void main(String[] args) 
    {
        String filename = args.length > 0 ? args[0] : "test_tiny.c";
        
        LexAn lex = new LexAn();
        List<Token> tokens = lex.LexicalAnalysis(filename);

        try 
        {
            Parser parser = new Parser(tokens);
            ProgramNode tree = (ProgramNode) parser.parse();
            
            System.out.println("=== Abstract Syntax Tree ===");
            System.out.println(tree.toString(0));
            
            // Perform semantic analysis
            System.out.println("\n========================================");
            System.out.println("  SEMANTIC ANALYSIS");
            System.out.println("========================================");
            
            SemanticAnalyzer analyzer = new SemanticAnalyzer();
            analyzer.analyze(tree);
            analyzer.printResults();
            
            if (analyzer.hasErrors()) {
                System.out.println("\n✗ Semantic analysis failed.");
                System.exit(1);
            } else {
                System.out.println("\n✓ Semantic analysis passed.");
            }
            
        } 
        catch (Exception e) 
        {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
