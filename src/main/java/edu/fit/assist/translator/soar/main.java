package edu.fit.assist.translator.soar;
import edu.fit.assist.translator.gen.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import java.io.*;
import java.util.Scanner;

public class main{
    public static String debugPath = "D:\\ICS_SOAR\\load.soar";

    public static void main(String[] args){
        try{
            String loadPath = (args.length > 0) ? args[0] : debugPath;
            String configPath = (args.length > 1) ? args[1] : null;

            // Read all Soar files recursively
            String fullSoarText = Input.getSoarRules(loadPath);
            
            // Parse all rules together
            ANTLRInputStream input = new ANTLRInputStream(fullSoarText);
            SoarLexer lexer = new SoarLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            SoarParser parser = new SoarParser(tokens);
            SoarParser.SoarContext tree = parser.soar();

            Visitor visitor = new Visitor();
            visitor.rules = new SoarRules();
            visitor.visit(tree);

            // Check if this is a time-based model
            boolean isTimeBasedModel = hasTimeBasedRules(visitor.rules);

            String translatedText;
            if (isTimeBasedModel) {
                // Use TimeBasedTranslator for time-window models
                TimeBasedTranslator timeTranslator;
                if (configPath != null) {
                    System.err.println("INFO: Using configuration file: " + configPath);
                    timeTranslator = new TimeBasedTranslator(visitor.rules, configPath);
                } else {
                    timeTranslator = new TimeBasedTranslator(visitor.rules);
                }
                translatedText = timeTranslator.translateToTimeBased();
            } else {
                // Use general translator
                Translate translatorFormatter = new Translate(visitor.rules);
                translatedText = translatorFormatter.translateSoarToPrismGeneral();
            }

            System.out.println(translatedText);
            PrintWriter pw = new PrintWriter(new File("output1.pm"));
            pw.println(translatedText);
            pw.flush();
            pw.close();

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Check if this is a time-based model by looking for time-related variables
     */
    private static boolean hasTimeBasedRules(SoarRules rules) {
        for (Rule rule : rules.rules) {
            // Check for time-related variables
            if (rule.valueMap.containsKey("time-counter") ||
                    rule.valueMap.containsKey("total-time") ||
                    rule.ruleName.contains("time")) {
                return true;
            }
        }
        return false;
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