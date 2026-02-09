import java.util.ArrayList;
import java.util.List;

class KW_List
{
   List<String> kwList= new ArrayList<>();

   public KW_List() 
   {
      kwList.add("auto");
      kwList.add("break");
      kwList.add("case");
      kwList.add("char");
      kwList.add("const");
      kwList.add("continue");
      kwList.add("default");
      kwList.add("do");
      kwList.add("double");
      kwList.add("else");
      kwList.add("enum");
      kwList.add("extern");
      kwList.add("float");
      kwList.add("for");
      kwList.add("goto");
      kwList.add("if");
      kwList.add("inline");
      kwList.add("int");
      kwList.add("long");
      kwList.add("NULL");
      kwList.add("register");
      kwList.add("restrict");
      kwList.add("return");
      kwList.add("short");
      kwList.add("signed");
      kwList.add("sizeof");
      kwList.add("static");
      kwList.add("struct");
      kwList.add("switch");
      kwList.add("typedef");
      kwList.add("union");
      kwList.add("unsigned");
      kwList.add("void");
      kwList.add("volatile");
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
