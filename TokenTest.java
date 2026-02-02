import java.util.*;
public class TokenTest {
    public static void main(String[] args) {
        LexAn lex = new LexAn();
        List<Token> tokens = lex.LexicalAnalysis("test_minimal.c");
        for (Token t : tokens) {
            System.out.println(t);
        }
    }
}
