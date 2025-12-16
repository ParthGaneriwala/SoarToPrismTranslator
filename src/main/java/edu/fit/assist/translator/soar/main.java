package edu.fit.assist.translator.soar;
import edu.fit.assist.translator.gen.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import java.io.*;

public class main{
    public static String debugPath = "D:\\ICS_SOAR\\load.soar";

    public static void main(String[] args){
        try{
            String loadPath = (args.length > 0) ? args[0] : debugPath;
            String configPath = (args.length > 1) ? args[1] : null;

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

}
