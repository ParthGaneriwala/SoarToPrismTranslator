package edu.fit.assist.translator.soar;
import edu.fit.assist.translator.gen.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import java.io.*;
import java.util.regex.Pattern;

public class main{
    public static String debugPath = "D:\\ICS_SOAR\\load.soar";
    private static final Pattern TIME_WORD_PATTERN = Pattern.compile("\\btime\\b", Pattern.CASE_INSENSITIVE);

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
                TimeBasedTranslator timeTranslator = (config != null)
                        ? new TimeBasedTranslator(visitor.rules, config)
                        : new TimeBasedTranslator(visitor.rules);
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
        final String timeVar = (config != null)
                ? config.getEffectiveTimeVariable()
                : PrismConfig.DEFAULT_TIME_VARIABLE;

        for (Rule rule : rules.rules) {
            // Check for time-related variables
            boolean hasTimeReference = false;
            for (String key : rule.valueMap.keySet()) {
                if (TranslatorUtils.containsNameVariant(key, timeVar)) {
                    hasTimeReference = true;
                    break;
                }
            }
            if (!hasTimeReference) {
                for (String guard : rule.guards) {
                    if (TranslatorUtils.containsNameVariant(guard, timeVar)) {
                        hasTimeReference = true;
                        break;
                    }
                }
            }
            boolean hasTotalTime = rule.valueMap.keySet().stream()
                    .anyMatch(key -> TranslatorUtils.containsNameVariant(key, PrismConfig.DEFAULT_TOTAL_TIME_KEY));
            boolean hasTimeInRuleName = TranslatorUtils.containsNameVariant(rule.ruleName, timeVar) ||
                    TIME_WORD_PATTERN.matcher(rule.ruleName).find();

            if (hasTimeReference || hasTotalTime || hasTimeInRuleName) {
                return true;
            }
        }
        return false;
    }

}
