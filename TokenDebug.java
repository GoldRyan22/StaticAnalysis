import java.util.*;

public class TokenDebug 
{
    public static void main(String[] args) 
    {
        if (args.length == 0) {
            System.out.println("Usage: java TokenDebug <source_file.c>");
            return;
        }
        
        String filename = args[0];
        
        try 
        {
            // Lexical Analysis
            LexAn lex = new LexAn();
            List<Token> tokens = lex.LexicalAnalysis(filename);
            
            System.out.println("=== TOKENS ===");
            for (Token t : tokens) {
                System.out.println("Line " + t.line + ": [" + t.code + "] = '" + t.value + "'");
            }
            
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
