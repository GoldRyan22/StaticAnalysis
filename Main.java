import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Main 
{
    public static void main(String[] args) 
    {
        if (args.length == 0) {
            System.out.println("Usage: java Main <source_file.c>");
            System.out.println("\nAvailable options:");
            System.out.println("  --ast          Show AST only");
            System.out.println("  --semantic     Show semantic analysis only");
            System.out.println("  --cfg          Show CFG only");
            System.out.println("  --all          Show everything (default)");
            return;
        }
        
        String filename = args[0];
        boolean showAst = hasFlag(args, "--ast");
        boolean showSemantic = hasFlag(args, "--semantic");
        boolean showCfg = hasFlag(args, "--cfg");
        boolean showAll = hasFlag(args, "--all") || (!showAst && !showSemantic && !showCfg);
        
        try 
        {
            // Lexical Analysis
            LexAn lex = new LexAn();
            List<Token> tokens = lex.LexicalAnalysis(filename);
            
            // Syntactic Analysis
            Parser parser = new Parser(tokens);
            ProgramNode tree = (ProgramNode) parser.parse();
            
            System.out.println("  STATIC ANALYSIS TOOL FOR C   ");
         
            
            // Show AST
            if (showAll || showAst) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("  ABSTRACT SYNTAX TREE");
                System.out.println("=".repeat(60));
                System.out.println(tree.toString(0));
            }
            
            // Semantic Analysis
            boolean hasSemanticErrors = false;
            if (showAll || showSemantic) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("  SEMANTIC ANALYSIS");
                System.out.println("=".repeat(60));
                
                SemanticAnalyzer analyzer = new SemanticAnalyzer();
                analyzer.analyze(tree);
                analyzer.printResults();
                
                hasSemanticErrors = analyzer.hasErrors();
            }
            
            // Control Flow Graph Analysis
            if (showAll || showCfg) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("  CONTROL FLOW GRAPH ANALYSIS");
                System.out.println("=".repeat(60));
                
                List<ControlFlowGraph> cfgs = CFGBuilder.buildCFGsFromProgram(tree);
                
                for (ControlFlowGraph cfg : cfgs) {
                    cfg.printCFG();

                    String dotFilename = "cfg_" + cfg.functionName + ".dot";
                    String pngFilename = "cfg_" + cfg.functionName + ".png";
                    
                    try (PrintWriter writer = new PrintWriter(dotFilename)) 
                    {
                        writer.print(cfg.toDot());
                        System.out.println("Generated: " + dotFilename);
                    } 
                    catch (IOException e) 
                    {
                        System.err.println("Error writing " + dotFilename + ": " + e.getMessage());
                    }
                    
                    // Generate PNG from DOT file
                    try {
                        ProcessBuilder pb = new ProcessBuilder("dot", "-Tpng", dotFilename, "-o", pngFilename);
                        Process process = pb.start();
                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            System.out.println("Generated: " + pngFilename);
                        } else {
                            System.err.println("Warning: Failed to generate " + pngFilename + " (exit code: " + exitCode + ")");
                        }
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Warning: Could not generate PNG (is Graphviz installed?): " + e.getMessage());
                    }

                }

                
                
                // Summary table
                System.out.println("\n" + "=".repeat(60));
                System.out.println("  CYCLOMATIC COMPLEXITY SUMMARY");
                System.out.println("=".repeat(60));
                System.out.println(String.format("%-30s | %-12s | %s", "Function", "Complexity", "Risk Level"));
                System.out.println("-".repeat(60));
                
                for (ControlFlowGraph cfg : cfgs) {
                    int complexity = cfg.calculateCyclomaticComplexity();
                    String risk = getRiskLevel(complexity);
                    System.out.println(String.format("%-30s | %-12d | %s", 
                        cfg.functionName, complexity, risk));
                }
                
                System.out.println("\n✓ Total functions analyzed: " + cfgs.size());
            }
            
            System.out.println("\n" + "=".repeat(60));
            if (hasSemanticErrors) {
                System.out.println("⚠ Analysis complete - Semantic errors found but CFG generated");
            } else {
                System.out.println("✓ Analysis complete - No critical errors found");
            }
            System.out.println("=".repeat(60));
            
        } 
        catch (Exception e) 
        {
            System.err.println("\n✗ Analysis failed:");
            System.err.println("  " + e.getMessage());
            if (hasFlag(args, "--debug")) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) return true;
        }
        return false;
    }
    
    private static String getRiskLevel(int complexity) {
        if (complexity <= 5) return "Low (Simple)";
        if (complexity <= 10) return "Moderate";
        if (complexity <= 20) return "High";
        return "Very High";
    }
}