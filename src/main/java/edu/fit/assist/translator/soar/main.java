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

            // Parse all rules together
            ANTLRInputStream input = new ANTLRInputStream(fullSoarText);
            SoarLexer lexer = new SoarLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            SoarParser parser = new SoarParser(tokens);
            SoarParser.SoarContext tree = parser.soar();

            Visitor visitor = new Visitor();
            visitor.rules = new SoarRules();
            visitor.visit(tree);

            PrismConfig config = null;
            if (configPath != null) {
                try {
                    config = PrismConfig.loadFromFile(configPath);
                    System.err.println("INFO: Using configuration file: " + configPath);
                } catch (IOException e) {
                    System.err.println("Warning: Could not load config file " + configPath + ": " + e.getMessage());
                }
            }

            // Check if this is a time-based model
            boolean isTimeBasedModel = hasTimeBasedRules(visitor.rules, config);

            String translatedText;
            if (isTimeBasedModel) {
                // Use TimeBasedTranslator for time-window models
                TimeBasedTranslator timeTranslator;
                if (config != null) {
                    timeTranslator = new TimeBasedTranslator(visitor.rules, config);
                } else if (configPath != null) {
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
    private static boolean hasTimeBasedRules(SoarRules rules, PrismConfig config) {
        final String timeVar = (config != null && config.getTimeVariable() != null && !config.getTimeVariable().isEmpty())
                ? config.getTimeVariable()
                : "time-counter";

        for (Rule rule : rules.rules) {
            // Check for time-related variables
            boolean hasTimeInValues = rule.valueMap.keySet().stream()
                    .anyMatch(k -> TranslatorUtils.containsNameVariant(k, timeVar));
            boolean hasTimeInGuards = rule.guards.stream()
                    .anyMatch(g -> TranslatorUtils.containsNameVariant(g, timeVar));

            if (hasTimeInValues || hasTimeInGuards ||
                    rule.valueMap.containsKey("total-time") ||
                    rule.ruleName.contains("time")) {
                return true;
            }
        }
        return false;
    }

}
