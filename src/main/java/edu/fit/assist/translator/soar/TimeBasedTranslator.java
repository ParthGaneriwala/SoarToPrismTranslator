package edu.fit.assist.translator.soar;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Specialized translator for time-based Soar models that generates PRISM code
 * with synchronized modules for temporal progression and probabilistic state transitions.
 */
public class TimeBasedTranslator {
    private SoarRules rules;
    private int totalTime = 1200;
    private int[] timeWindows = {0, 300, 600, 900, 1200};
    private int[] commitTimes = {299, 599, 899, 1199};
    
    public TimeBasedTranslator(SoarRules rules) {
        this.rules = rules;
        extractTimeConfiguration();
    }
    
    /**
     * Extract time configuration from initialize rule
     */
    private void extractTimeConfiguration() {
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                String totalTimeStr = rule.valueMap.get("total-time");
                if (totalTimeStr != null) {
                    try {
                        totalTime = Integer.parseInt(totalTimeStr);
                    } catch (NumberFormatException e) {
                        System.err.println("Could not parse total-time: " + totalTimeStr);
                    }
                }
                
                String intervalStr = rule.valueMap.get("time-interval");
                if (intervalStr != null) {
                    try {
                        int interval = Integer.parseInt(intervalStr);
                        // Generate windows based on interval
                        List<Integer> windows = new ArrayList<>();
                        List<Integer> commits = new ArrayList<>();
                        for (int t = 0; t <= totalTime; t += interval) {
                            windows.add(t);
                            if (t + interval - 1 < totalTime) {
                                commits.add(t + interval - 1);
                            }
                        }
                        timeWindows = windows.stream().mapToInt(Integer::intValue).toArray();
                        commitTimes = commits.stream().mapToInt(Integer::intValue).toArray();
                    } catch (NumberFormatException e) {
                        System.err.println("Could not parse time-interval: " + intervalStr);
                    }
                }
            }
        }
    }
    
    /**
     * Main translation method that generates PRISM code
     */
    public String translateToTimeBased() {
        StringBuilder output = new StringBuilder();
        
        output.append("dtmc\n");
        output.append("// PRISM model generated from Soar rules\n\n");
        
        // Generate time module
        output.append(generateTimeModule());
        output.append("\n");
        
        // Generate sickness module
        output.append(generateSicknessModule());
        output.append("\n");
        
        // Generate action modules (select, decide, error, etc.)
        output.append(generateActionModules());
        output.append("\n");
        
        // Generate rewards
        output.append(generateRewards());
        
        return output.toString();
    }
    
    /**
     * Generate the time module that increments time counter
     */
    private String generateTimeModule() {
        StringBuilder sb = new StringBuilder();
        sb.append("module time\n");
        sb.append(String.format("  sn : [0..%d] init 0;\n", totalTime));
        sb.append(String.format("  [sync] sn < %d -> (sn' = sn + 1);\n", totalTime));
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
        sb.append("  s : [0..1] init 0;  // current sickness state\n");
        sb.append("  ts: [0..1] init 0;  // temporary sickness sample\n");
        
        // Find probability for sickness
        double pdf1 = findProbability("pdf1", 0.9);
        
        // Generate sampling transitions at window starts
        for (int window : timeWindows) {
            // Sample when not sick
            sb.append(String.format("  [sync] sn = %d & s = 0 -> %.1f:(ts' = 0) + %.1f:(ts' = 1);\n",
                window, pdf1, 1.0 - pdf1));
            
            // Sample when sick (stays sick)
            sb.append(String.format("  [sync] sn = %d & s = 1 -> 1:(ts' = 1);\n", window));
        }
        
        // Generate commit transitions at window ends
        for (int commitTime : commitTimes) {
            sb.append(String.format("  [sync] sn = %d & ts = 0 -> (s' = ts);\n", commitTime));
            sb.append(String.format("  [sync] sn = %d & ts = 1 -> (s' = ts);\n", commitTime));
        }
        
        // Generate default/else transition
        sb.append("  [sync] ");
        List<String> exclusions = new ArrayList<>();
        for (int t : timeWindows) {
            exclusions.add(String.format("sn != %d", t));
        }
        for (int t : commitTimes) {
            exclusions.add(String.format("sn != %d", t));
        }
        sb.append(String.join(" & ", exclusions));
        sb.append(" -> (s' = s) & (ts' = ts);\n");
        
        sb.append("endmodule\n");
        return sb.toString();
    }
    
    /**
     * Generate action modules (select, decide, error, etc.)
     */
    private String generateActionModules() {
        StringBuilder sb = new StringBuilder();
        
        // This is a placeholder - actual implementation would analyze
        // the Soar rules to extract action patterns
        
        // For now, generate a simple placeholder module
        sb.append("// Action modules would be generated here based on Soar rules\n");
        sb.append("// Examples: select module, decide module, error module\n");
        
        return sb.toString();
    }
    
    /**
     * Generate reward structures
     */
    private String generateRewards() {
        StringBuilder sb = new StringBuilder();
        sb.append("rewards \"total_decisions\"\n");
        sb.append(String.format("  sn < %d: 1;\n", totalTime));
        sb.append("endrewards\n");
        return sb.toString();
    }
    
    /**
     * Find a probability value from the rules
     */
    private double findProbability(String probName, double defaultValue) {
        for (Rule rule : rules.rules) {
            if (rule.valueMap.containsKey(probName)) {
                try {
                    return Double.parseDouble(rule.valueMap.get(probName));
                } catch (NumberFormatException e) {
                    System.err.println("Could not parse probability " + probName);
                }
            }
        }
        return defaultValue;
    }
}
