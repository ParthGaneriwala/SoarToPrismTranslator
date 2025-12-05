package edu.fit.assist.translator.soar;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Specialized translator for time-based Soar models that generates PRISM code
 * with synchronized modules for temporal progression and probabilistic state transitions.
 * 
 * This translator is designed to handle Soar models with:
 * - Time-based progression (time counters)
 * - Window-based sampling (e.g., sickness checks at specific time windows)
 * - Probabilistic state transitions
 * - Synchronized module interactions
 * 
 * Can optionally use external configuration file for:
 * - Module definitions and mappings
 * - PDF values and constants
 * - Time windows and intervals
 */
public class TimeBasedTranslator {
    private SoarRules rules;
    private int totalTime = 1200;
    private List<Integer> timeWindows = new ArrayList<>();
    private List<Integer> commitTimes = new ArrayList<>();
    private Map<String, Object> constantValues = new LinkedHashMap<>();
    private PrismConfig config = null;
    
    // Module constants
    private static final int MISSION_MONITOR = 0;
    private static final int SICKNESS_MONITOR = 1;
    
    public TimeBasedTranslator(SoarRules rules) {
        this.rules = rules;
        extractConfiguration();
    }
    
    /**
     * Constructor with external configuration file
     */
    public TimeBasedTranslator(SoarRules rules, String configPath) {
        this.rules = rules;
        try {
            this.config = PrismConfig.loadFromFile(configPath);
            if (config != null) {
                // Use config values to override defaults
                totalTime = config.getTotalTime();
                constantValues.putAll(config.getConstants());
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load config file " + configPath + ": " + e.getMessage());
            System.err.println("Falling back to rule-based extraction");
        }
        extractConfiguration();
    }
    
    /**
     * Extract configuration from initialize and other rules
     */
    private void extractConfiguration() {
        Integer timeInterval = null;
        
        // If we have a config, use its values
        if (config != null) {
            timeInterval = config.getSicknessSamplingInterval();
            timeWindows = config.getTimeWindows();
            commitTimes = config.getCommitTimes();
            return; // Config provides everything we need
        }
        
        // Otherwise extract from rules
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                // Extract total time
                String totalTimeStr = rule.valueMap.get("total-time");
                if (totalTimeStr != null) {
                    try {
                        totalTime = Integer.parseInt(totalTimeStr);
                    } catch (NumberFormatException e) {
                        System.err.println("Could not parse total-time: " + totalTimeStr);
                    }
                }
                
                // Store all initialization values as constants
                for (Map.Entry<String, String> entry : rule.valueMap.entrySet()) {
                    constantValues.put(entry.getKey(), entry.getValue());
                }
            }
            
            // Look for time-interval in elaborate rules
            if (rule.ruleName.contains("elaborate") && rule.ruleName.contains("time-interval")) {
                // Check valueMap for ti or time-interval
                for (Map.Entry<String, String> entry : rule.valueMap.entrySet()) {
                    if (entry.getKey().contains("ti") || entry.getKey().contains("time-interval")) {
                        String intervalVal = entry.getValue();
                        // The value might be a variable reference like <ti>, check if it's in guards
                        if (intervalVal.startsWith("<") && intervalVal.endsWith(">")) {
                            // It's a variable, we need to look at guards or context
                            continue;
                        }
                    }
                }
            }
        }
        
        // If time-interval not found in rules, try to infer from rule guards
        // by finding time-counter comparisons
        if (timeInterval == null) {
            timeInterval = inferTimeIntervalFromRules();
        }
        
        // Generate time windows based on interval
        if (timeInterval != null && timeInterval > 0) {
            generateTimeWindows(timeInterval);
        } else {
            // Fallback: use default interval of 300
            generateTimeWindows(300);
        }
    }
    
    /**
     * Infer time interval by analyzing guards and time-counter operations in rules
     */
    private Integer inferTimeIntervalFromRules() {
        Set<Integer> timeValues = new TreeSet<>();
        
        // Scan all rules for time-counter related operations
        for (Rule rule : rules.rules) {
            // Check guards for time-counter comparisons
            for (String guard : rule.guards) {
                if (guard.contains("time-counter") || guard.contains("time_counter")) {
                    // Try to extract numeric values from the guard
                    String[] parts = guard.split("\\s+");
                    for (String part : parts) {
                        try {
                            int value = Integer.parseInt(part.replaceAll("[^0-9]", ""));
                            if (value > 0 && value <= totalTime) {
                                timeValues.add(value);
                            }
                        } catch (NumberFormatException e) {
                            // Not a number, skip
                        }
                    }
                }
            }
            
            // Check valueMap for time-counter updates
            for (Map.Entry<String, String> entry : rule.valueMap.entrySet()) {
                if (entry.getKey().contains("time-counter") || entry.getKey().contains("time_counter")) {
                    String value = entry.getValue();
                    // Check for increment operations like (+ <pdf2> <tc>) or numeric values
                    if (value.matches(".*\\d+.*")) {
                        try {
                            // Extract all numbers from the value
                            String nums = value.replaceAll("[^0-9]", "");
                            if (!nums.isEmpty()) {
                                int val = Integer.parseInt(nums);
                                if (val > 0 && val <= totalTime) {
                                    timeValues.add(val);
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Not a simple number
                        }
                    }
                }
            }
        }
        
        // If we found time values, calculate the interval
        if (!timeValues.isEmpty()) {
            List<Integer> sortedValues = new ArrayList<>(timeValues);
            Collections.sort(sortedValues);
            
            // Calculate GCD of differences to find the interval
            if (sortedValues.size() >= 2) {
                List<Integer> differences = new ArrayList<>();
                for (int i = 1; i < sortedValues.size(); i++) {
                    int diff = sortedValues.get(i) - sortedValues.get(i-1);
                    if (diff > 0) {
                        differences.add(diff);
                    }
                }
                
                if (!differences.isEmpty()) {
                    // Find GCD of all differences
                    int gcd = differences.get(0);
                    for (int i = 1; i < differences.size(); i++) {
                        gcd = gcd(gcd, differences.get(i));
                    }
                    return gcd;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Calculate GCD of two numbers
     */
    private int gcd(int a, int b) {
        if (b == 0) return a;
        return gcd(b, a % b);
    }
    
    /**
     * Generate time windows and commit times based on interval
     */
    private void generateTimeWindows(int interval) {
        List<Integer> windows = new ArrayList<>();
        List<Integer> commits = new ArrayList<>();
        
        // Generate windows from 0 to totalTime with given interval
        for (int t = 0; t <= totalTime; t += interval) {
            windows.add(t);
            // Commit time is one before the next window (except for the last window)
            if (t + interval <= totalTime) {
                commits.add(t + interval - 1);
            }
        }
        
        timeWindows = windows;
        commitTimes = commits;
    }
    
    /**
     * Main translation method that generates PRISM code
     */
    public String translateToTimeBased() {
        StringBuilder output = new StringBuilder();
        
        output.append("dtmc\n");
        output.append("//PRISM model generated from Soar cognitive model\n");
        output.append(String.format("//Total time: %d\n\n", totalTime));
        
        // Generate constants
        output.append(generateConstants());
        output.append("\n");
        
        // Generate time module
        output.append(generateTimeModule());
        output.append("\n");
        
        // Generate sickness module
        output.append(generateSicknessModule());
        output.append("\n");
        
        // Generate action modules
        output.append(generateActionModules());
        output.append("\n");
        
        // Generate rewards
        output.append(generateRewards());
        
        return output.toString();
    }
    
    /**
     * Generate constant definitions
     */
    private String generateConstants() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("const int TOTAL_TIME = %d;\n", totalTime));
        
        // Generate constants from config if available
        if (config != null && !config.getConstants().isEmpty()) {
            for (Map.Entry<String, Object> entry : config.getConstants().entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Double || value instanceof Float) {
                    sb.append(String.format("const double %s = %.2f;\n", name, ((Number)value).doubleValue()));
                } else if (value instanceof Integer || value instanceof Long) {
                    sb.append(String.format("const int %s = %d;\n", name, ((Number)value).intValue()));
                } else {
                    sb.append(String.format("const %s = %s;\n", name, value));
                }
            }
        } else {
            // Fallback to default constants
            sb.append(String.format("const int mission_monitor  = %d;\n", MISSION_MONITOR));
            sb.append(String.format("const int sickness_monitor = %d;\n", SICKNESS_MONITOR));
            
            // Add probability constants - look for pdf1 or sick_thres
            // pdf1 is typically 0.9 (probability of staying healthy)
            double pdf1 = findProbabilityValue("pdf1", 0.9);
            if (pdf1 == 0.9) {
                // If pdf1 not found, try using complement of sick_thres
                double sickThres = findProbabilityValue("sick_thres", 0.5);
                if (sickThres != 0.5) {
                    // sick_thres is a threshold, pdf1 would be complement
                    pdf1 = 1.0 - sickThres;
                }
            }
            sb.append(String.format("const double pdf1 = %.2f;\n", pdf1));
        }
        
        sb.append("\n");
        return sb.toString();
    }
    
    /**
     * Generate the time module that increments time counter
     */
    private String generateTimeModule() {
        StringBuilder sb = new StringBuilder();
        sb.append("module time\n");
        sb.append(String.format("  time_counter : [0..TOTAL_TIME] init 0;\n"));
        sb.append(String.format("  [sync] time_counter <  TOTAL_TIME -> (time_counter' = time_counter + 1);\n"));
        sb.append(String.format("  [sync] time_counter =  TOTAL_TIME -> (time_counter' = time_counter);\n"));
        sb.append("endmodule\n");
        return sb.toString();
    }
    
    /**
     * Generate the sickness monitoring module
     */
    private String generateSicknessModule() {
        StringBuilder sb = new StringBuilder();
        sb.append("module sickness\n");
        
        // State variables
        sb.append("  name             : [0..1] init mission_monitor;\n");
        sb.append("  sick             : [0..1] init 0;\n");
        sb.append("  ts               : [0..1] init 0;\n");
        sb.append("  sickness_checked : [0..1] init 0;\n\n");
        
        sb.append("  // ---- commit at window end ----\n");
        // Generate commit transitions at window ends
        for (int commitTime : commitTimes) {
            sb.append(String.format("  [sync] time_counter = %4d -> (sick' = ts);\n", commitTime));
        }
        
        sb.append("\n  // ---- sample at window start (one check per visit) ----\n");
        // Generate sampling transitions at window starts
        for (int window : timeWindows) {
            // Sample when not sick
            sb.append(String.format("  [sync] time_counter = %4d & name=sickness_monitor & sickness_checked=0 & sick=0 ->\n",
                window));
            sb.append("        pdf1     : (ts'=0) & (sickness_checked'=1)\n");
            sb.append("      + (1-pdf1) : (ts'=1) & (sickness_checked'=1);\n");
            
            // Sample when sick (stays sick)
            sb.append(String.format("  [sync] time_counter = %4d & name=sickness_monitor & sickness_checked=0 & sick=1 ->\n",
                window));
            sb.append("        1 : (ts'=1) & (sickness_checked'=1);\n\n");
        }
        
        // Switch back to mission monitor
        sb.append("  [sync] name = sickness_monitor & sickness_checked = 1 &\n");
        sb.append("  time_counter != 0   & time_counter != 300 & time_counter != 600 &\n");
        sb.append("  time_counter != 900 & time_counter != 1200 &\n");
        sb.append("  time_counter != 299 & time_counter != 599 &\n");
        sb.append("  time_counter != 899 & time_counter != 1199\n");
        sb.append("->\n");
        sb.append("  (name' = mission_monitor) & (sickness_checked' = 0);\n\n");
        
        // Generate default/else transition
        sb.append("  [sync]\n");
        sb.append("    time_counter != 299 & time_counter != 599 & time_counter != 899 & time_counter != 1199 &\n");
        sb.append("    !((time_counter =    0 | time_counter =  300 | time_counter =  600 | time_counter =  900 | time_counter = 1200) &\n");
        sb.append("      name = sickness_monitor & sickness_checked = 0) &\n");
        sb.append("    !(name = sickness_monitor & sickness_checked = 1)\n");
        sb.append("  ->\n");
        sb.append("    (sick' = sick) & (ts' = ts) & (sickness_checked' = sickness_checked) & (name' = name);\n");
        
        sb.append("endmodule\n");
        return sb.toString();
    }
    
    /**
     * Generate action modules (placeholder for extensibility)
     */
    private String generateActionModules() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("// Additional action modules can be generated here\n");
        sb.append("// Examples based on Soar rules:\n");
        sb.append("// - SS-transition module\n");
        sb.append("// - D-transition module\n");
        sb.append("// - DD-transition module\n");
        sb.append("// - error detection module\n");
        
        return sb.toString();
    }
    
    /**
     * Generate reward structures
     */
    private String generateRewards() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Reward structures can be added here\n");
        sb.append("// Example:\n");
        sb.append("// rewards \"mission_completion\"\n");
        sb.append("//   time_counter = TOTAL_TIME: 1;\n");
        sb.append("// endrewards\n");
        return sb.toString();
    }
    
    /**
     * Find a probability value from the rules or config
     */
    private double findProbabilityValue(String probName, double defaultValue) {
        // Check constant values first (from config)
        if (constantValues.containsKey(probName)) {
            Object value = constantValues.get(probName);
            if (value instanceof Number) {
                return ((Number)value).doubleValue();
            }
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                System.err.println("Could not parse " + probName + ": " + value);
            }
        }
        
        // Check all rules
        for (Rule rule : rules.rules) {
            if (rule.valueMap.containsKey(probName)) {
                try {
                    return Double.parseDouble(rule.valueMap.get(probName));
                } catch (NumberFormatException e) {
                    System.err.println("Could not parse " + probName);
                }
            }
        }
        return defaultValue;
    }
}
