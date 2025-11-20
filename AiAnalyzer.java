import java.io.*;
import java.util.List;

public class AiAnalyzer 
{
    public static void analyzeFunctions(List<String> functions, String query)
    {
        try 
        {

            ProcessBuilder pb = new ProcessBuilder("python", "FuncAnalisys2.py", query);
            pb.redirectErrorStream(true); 
            Process process = pb.start();


            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            for (String func : functions) 
            {
                writer.write(func);
                writer.newLine(); 
            }
            writer.flush();
            writer.close(); 

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) 
                {
                output.append(line);
            }


            System.out.println("--- AI Similarity Analysis ---");
            System.out.println("Response: " + output.toString());
            
            
            int exitCode = process.waitFor();
            if (exitCode != 0) 
            {
                System.err.println("Python script exited with error code: " + exitCode);
            }

        } catch (Exception e) 
        {
            e.printStackTrace();
        }
    }
}