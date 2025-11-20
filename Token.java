import java.util.ArrayList;
import java.util.List;

enum code 
{
   ID("ID"), BREAK("BREAK"), CHAR("CHAR"), DOUBLE("DOUBLE"), ELSE("ELSE"), FOR("FOR"), IF("IF"), INT("INT"), RETURN("RETURN"), STRUCT("STRUCT"), VOID("VOID"), WHILE("WHILE"), CT_INT("CT_INT"), CT_REAL("CT_REAL"), CT_CHAR("CT_CHAR"), CT_STRING("CT_STRING"), COMMA("COMMA"), SEMICOLON("SEMICOLON"), LPAR("LPAR"), RPAR("RPAR"), LBRACKET("LBRACKET"), RBRACKET("RBRACKET"), LACC("LACC"), RACC("RACC"), ADD("ADD"), SUB("SUB"), MUL("MUL"), DIV("DIV"), DOT("DOT"), AND("AND"), OR("OR"), NOT("NOT"), ASSIGN("ASSIGN"), EQUAL("EQUAL"), NOTEQ("NOTEQ"), LESS("LESS"), LESSEQ("LESSEQ"), GREATER("GREATER"), GREATEREQ("GREATEREQ");

   String codeName;

   code(String codeName)
   {
      this.codeName = codeName;
   }

}

class KW_List
{
   List<String> kwList= new ArrayList<>();

   public KW_List() 
   {
      kwList.add("break");
      kwList.add("char");
      kwList.add("double");
      kwList.add("else");
      kwList.add("for");
      kwList.add("if");
      kwList.add("int");
      kwList.add("return");
      kwList.add("struct");
      kwList.add("void");
      kwList.add("while");
   }
}

public class Token 
{
   //code code;

   KW_List kwList = new KW_List();

   String code;

   public Object value;

   int line;

   Token(int line, String value, String codeName)
   {
      this.line = line;
      this.code = codeName;

      if(codeName.equals("CT_INT"))
      {
         this.value = Integer.valueOf(value);
         //this.code = code.CT_INT;
      }
      else if(codeName.equals("HEX"))
      {
         this.value = Integer.decode(value);
         this.code = "CT_INT";
      }
      else if(codeName.equals("CT_REAL"))
      {
         this.value = Double.valueOf(value);
         //this.code = code.CT_REAL;
      }
      else if(codeName.equals("CT_CHAR"))
      {
         this.value = value + "\'";
      }
      else if(codeName.equals("CT_STRING"))
      {
         this.value = value + "\"";
      }
      else
      {
         if(codeName.equals("IDKEY"))
         {
            if(kwList.kwList.contains(value))
            {
               this.code = value.toUpperCase();
            }
            else
            {
               this.code = "ID";
            }
         }
         this.value = value;
      }
   }

    @Override
    public String toString() 
    {
        return this.code + " " + this.value.toString() +  " " + this.line;
    }

   
}
