import java.util.*;

public class TestTypedef 
{
    public static void main(String[] args) 
    {
        LexAn lex = new LexAn();
        List<Token> tokens = lex.LexicalAnalysis("test_typedef.c");

        try 
        {
            Parser parser = new Parser(tokens);
            ProgramNode tree = (ProgramNode) parser.parse();
            
            System.out.println("=== Abstract Syntax Tree ===");
            System.out.println(tree.toString(0));
            
            System.out.println("\n=== Function Declarations ===");
            for(String funcDecl : parser.getFuncs())
            {
                System.out.println(funcDecl);
            }
            
            // Build Control Flow Graphs
            System.out.println("\n=== Control Flow Graphs ===");
            List<ControlFlowGraph> cfgs = CFGBuilder.buildCFGsFromProgram(tree);
            
            for (ControlFlowGraph cfg : cfgs) 
            {
                cfg.printCFG();
            }
            
            // Summary of complexities
            System.out.println("\n=== Cyclomatic Complexity Summary ===");
            for (ControlFlowGraph cfg : cfgs) 
            {
                System.out.println(cfg.functionName + ": " + cfg.calculateCyclomaticComplexity());
            }
        } 
        catch (Exception e) 
        {
            System.err.println("Parser Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
