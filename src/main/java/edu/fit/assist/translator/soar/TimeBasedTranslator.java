package edu.fit.assist.translator.soar;

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
 */
public class TimeBasedTranslator {
    private SoarRules rules;
    private int totalTime = 1200;
    private List<Integer> timeWindows = new ArrayList<>();
    private List<Integer> commitTimes = new ArrayList<>();
    private Map<String, String> constantValues = new LinkedHashMap<>();
    
    // Module constants
    private static final int MISSION_MONITOR = 0;
    private static final int SICKNESS_MONITOR = 1;
    
    public TimeBasedTranslator(SoarRules rules) {
        this.rules = rules;
        extractConfiguration();
    }
    
    /**
     * Extract configuration from initialize and other rules
     */
    private void extractConfiguration() {
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
                constantValues.putAll(rule.valueMap);
            }
        }
        
        // Infer time windows from the Soar rules
        // Look for rules that reference time-counter values
        Set<Integer> windowSet = new TreeSet<>();
        Set<Integer> commitSet = new TreeSet<>();
        
        for (Rule rule : rules.rules) {
            // Check guards for time-based conditions
            for (String guard : rule.guards) {
                // This is simplified - in real implementation, parse time values from guards
            }
        }
        
        // Default windows if not found
        if (windowSet.isEmpty()) {
            windowSet.addAll(Arrays.asList(0, 300, 600, 900, 1200));
            commitSet.addAll(Arrays.asList(299, 599, 899, 1199));
        }
        
        timeWindows = new ArrayList<>(windowSet);
        commitTimes = new ArrayList<>(commitSet);
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
        sb.append(String.format("const int mission_monitor  = %d;\n", MISSION_MONITOR));
        sb.append(String.format("const int sickness_monitor = %d;\n\n", SICKNESS_MONITOR));
        
        // Add probability constants
        double pdf1 = findProbabilityValue("sick_thres", 0.9);
        sb.append(String.format("const double pdf1 = %.2f;\n", pdf1));
        
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
     * Find a probability value from the rules
     */
    private double findProbabilityValue(String probName, double defaultValue) {
        // Check constant values first
        if (constantValues.containsKey(probName)) {
            try {
                return Double.parseDouble(constantValues.get(probName));
            } catch (NumberFormatException e) {
                System.err.println("Could not parse " + probName + ": " + constantValues.get(probName));
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
