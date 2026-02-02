import java.util.*;
import java.io.*;

public class CFGVisualizer 
{
    public static void main(String[] args) 
    {
        String filename = args.length > 0 ? args[0] : "test_complex.c";
        
        LexAn lex = new LexAn();
        List<Token> tokens = lex.LexicalAnalysis(filename);

        try 
        {
            Parser parser = new Parser(tokens);
            ProgramNode tree = (ProgramNode) parser.parse();
            
            // Build Control Flow Graphs
            List<ControlFlowGraph> cfgs = CFGBuilder.buildCFGsFromProgram(tree);
            
            // Generate DOT files for each function
            for (ControlFlowGraph cfg : cfgs) 
            {
                String dotFilename = "cfg_" + cfg.functionName + ".dot";
                try (PrintWriter writer = new PrintWriter(dotFilename)) 
                {
                    writer.print(cfg.toDot());
                    System.out.println("Generated: " + dotFilename);
                } 
                catch (IOException e) 
                {
                    System.err.println("Error writing " + dotFilename + ": " + e.getMessage());
                }
            }
            
            System.out.println("\n=== Cyclomatic Complexity Summary ===");
            System.out.println("Function Name           | Complexity | Interpretation");
            System.out.println("------------------------|------------|---------------------------");
            for (ControlFlowGraph cfg : cfgs) 
            {
                int complexity = cfg.calculateCyclomaticComplexity();
                String interpretation = getComplexityInterpretation(complexity);
                System.out.printf("%-23s | %-10d | %s\n", cfg.functionName, complexity, interpretation);
            }
            
            System.out.println("\nTo visualize the CFG, use Graphviz:");
            System.out.println("  dot -Tpng cfg_<function_name>.dot -o cfg_<function_name>.png");
            
        } 
        catch (Exception e) 
        {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String getComplexityInterpretation(int complexity) 
    {
        if (complexity <= 5) {
            return "Low (Simple)";
        } else if (complexity <= 10) {
            return "Moderate";
        } else if (complexity <= 20) {
            return "High (Complex)";
        } else {
            return "Very High (Untestable)";
        }
    }
}
