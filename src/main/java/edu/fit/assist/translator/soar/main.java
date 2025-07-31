package edu.fit.assist.translator.soar;
import edu.fit.assist.translator.gen.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import java.io.*;
import java.util.Scanner;

public class main{
    public static String debugPath = "models/soar_models/load.soar";

    public static void main(String[] args){
        try{
            String loadPath = (args.length > 0) ? args[0] : debugPath;

            // Read all Soar files recursively
            String fullSoarText = Input.getSoarRules(loadPath);
            String[] perRuleBlocks = fullSoarText.split("(?=sp\\s\\*\\s)"); // split by "sp *"

            Visitor visitor = new Visitor();
            visitor.rules = new SoarRules();

            for (String ruleBlock : perRuleBlocks) {
                try {
                    ANTLRInputStream input = new ANTLRInputStream(ruleBlock);
                    SoarLexer lexer = new SoarLexer(input);
                    CommonTokenStream tokens = new CommonTokenStream(lexer);
                    SoarParser parser = new SoarParser(tokens);
                    SoarParser.SoarContext tree = parser.soar();

                    if (tree == null) {
                        System.err.println("warning: skipping rule block due to null tree:\n" + ruleBlock);
                        continue;
                    }

                    visitor.visit(tree);

                } catch (Exception e) {
                    System.err.println("\n ERROR while processing rule:\n" + ruleBlock);
                    e.printStackTrace();
                }
            }

            // Translate to PRISM
            Translate translatorFormatter = new Translate(visitor.rules);
            String translatedText = translatorFormatter.translateSoarToPrismGeneral();
            System.out.println(translatedText);
            PrintWriter pw = new PrintWriter(new File("output1.pm"));
            pw.println(translatedText);
            pw.flush();
            pw.close();

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static String readInputFile(String path) throws FileNotFoundException{
        File soarFile = new File(path);
        Scanner sc = new Scanner(soarFile);
        String text = "";
        while(sc.hasNextLine()){
            String s = sc.nextLine();
            if(s.startsWith("echo")){
                System.out.println(s.substring(5));
            }else if(s.startsWith("source")){
                String filePath = soarFile.getParent() + "/" + s.substring(7).replaceAll("\"", "");
                text += readInputFile(filePath);
            }else if(s.contains("(write (crlf)")){

            }else{
                text += s+"\n";
            }

        }
        return text;
    }

    public static String cleanText(String s){
        String keywordBlackList[] = {"waitsnc --on", "rl --set learning on", "decide indifferent-selection --softmax"};
        for(int i=0; i< keywordBlackList.length; i++){
            s = s.replaceAll(keywordBlackList[i], "");
        }
        // replace the "." in variable names with "_" but don't replace the "." in floating point numbers
        s=s.replaceAll("([a-z|A-Z])\\.","$1\\_");

        return s;
    }



}
