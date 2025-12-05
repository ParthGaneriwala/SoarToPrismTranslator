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
     * Always extract from Soar rules first, then supplement with config if available
     */
    private void extractConfiguration() {
        Integer timeInterval = null;
        
        // ALWAYS extract from Soar rules first - this is the primary source
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                // Extract total time from Soar rules (unless overridden by config)
                String totalTimeStr = rule.valueMap.get("total-time");
                if (totalTimeStr != null && config == null) {
                    try {
                        totalTime = Integer.parseInt(totalTimeStr);
                    } catch (NumberFormatException e) {
                        System.err.println("Could not parse total-time: " + totalTimeStr);
                    }
                }
                
                // Store all initialization values as constants from Soar
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
        
        // After extracting from Soar, supplement with config if available
        // Config provides probability distributions and other data NOT in Soar
        if (config != null) {
            // Use config for time interval if available
            timeInterval = config.getSicknessSamplingInterval();
            // Supplement constants with config values (don't replace)
            for (Map.Entry<String, Object> entry : config.getConstants().entrySet()) {
                // Only add if not already present from Soar rules
                if (!constantValues.containsKey(entry.getKey())) {
                    constantValues.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        // If time-interval not found in config or rules, try to infer from rule guards
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
        
        // Generate action state module
        output.append(generateActionStateModule());
        output.append("\n");
        
        // Generate sickness module
        output.append(generateSicknessModule());
        output.append("\n");
        
        // Generate action transition modules
        output.append(generateActionModules());
        output.append("\n");
        
        // Generate rewards
        output.append(generateRewards());
        
        return output.toString();
    }
    
    /**
     * Generate constant definitions
     * Prioritizes constants extracted from Soar rules, supplements with config
     */
    private String generateConstants() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("const int TOTAL_TIME = %d;\n", totalTime));
        
        // Extract module name constants from Soar rules
        // Look for name values in initialize rule
        boolean foundMissionMonitor = false;
        boolean foundSicknessMonitor = false;
        
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                String nameValue = rule.valueMap.get("name");
                if (nameValue != null) {
                    // Found name initialization, use it to define constant
                    if (nameValue.contains("mission") && !foundMissionMonitor) {
                        sb.append(String.format("const int mission_monitor  = %d;\n", MISSION_MONITOR));
                        foundMissionMonitor = true;
                    }
                }
            }
            // Check for monitor switching rules to find sickness_monitor
            if (rule.ruleName.contains("switch-monitor") || rule.ruleName.contains("sickness")) {
                if (!foundSicknessMonitor) {
                    sb.append(String.format("const int sickness_monitor = %d;\n", SICKNESS_MONITOR));
                    foundSicknessMonitor = true;
                }
            }
        }
        
        // If not found in Soar rules, use defaults
        if (!foundMissionMonitor) {
            sb.append(String.format("const int mission_monitor  = %d;\n", MISSION_MONITOR));
        }
        if (!foundSicknessMonitor) {
            sb.append(String.format("const int sickness_monitor = %d;\n", SICKNESS_MONITOR));
        }
        
        // Add probability constants from Soar rules first
        double pdf1 = findProbabilityValue("pdf1", -1.0);
        if (pdf1 < 0) {
            // If pdf1 not found, try using complement of sick_thres from Soar
            double sickThres = findProbabilityValue("sick_thres", -1.0);
            if (sickThres >= 0) {
                // sick_thres is a threshold, pdf1 would be complement
                pdf1 = 1.0 - sickThres;
            }
        }
        
        // If still not found in Soar, check config
        if (pdf1 < 0 && config != null && config.getConstants().containsKey("pdf1")) {
            Object pdf1Obj = config.getConstants().get("pdf1");
            if (pdf1Obj instanceof Number) {
                pdf1 = ((Number)pdf1Obj).doubleValue();
            }
        }
        
        // Use default only if not found anywhere
        if (pdf1 < 0) {
            pdf1 = 0.9;
        }
        
        sb.append(String.format("const double pdf1 = %.2f;\n", pdf1));
        
        // Add any additional constants from config that aren't already defined
        if (config != null && !config.getConstants().isEmpty()) {
            for (Map.Entry<String, Object> entry : config.getConstants().entrySet()) {
                String name = entry.getKey();
                // Skip if already defined (mission_monitor, sickness_monitor, pdf1, TOTAL_TIME)
                if (name.equals("mission_monitor") || name.equals("sickness_monitor") || 
                    name.equals("pdf1") || name.equals("TOTAL_TIME")) {
                    continue;
                }
                
                Object value = entry.getValue();
                if (value instanceof Double || value instanceof Float) {
                    sb.append(String.format("const double %s = %.2f;\n", name, ((Number)value).doubleValue()));
                } else if (value instanceof Integer || value instanceof Long) {
                    sb.append(String.format("const int %s = %d;\n", name, ((Number)value).intValue()));
                } else {
                    sb.append(String.format("const %s = %s;\n", name, value));
                }
            }
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
     * Generate action state module
     * Tracks the current action state (Scan-and-Select, Deciding, Decided)
     */
    private String generateActionStateModule() {
        StringBuilder sb = new StringBuilder();
        sb.append("module action_state\n");
        
        // Extract initial action value from initialize rule
        int initAction = 3; // default from Soar code
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                String actionVal = rule.valueMap.get("action");
                if (actionVal != null) {
                    try {
                        initAction = Integer.parseInt(actionVal);
                    } catch (NumberFormatException e) {
                        // Use default
                    }
                }
            }
        }
        
        // Action: 0=Deciding, 1=Decided, 2=Scan-and-Select (from DD), 3=Scan-and-Select (initial)
        sb.append(String.format("  action : [0..3] init %d;\n", initAction));
        sb.append("\n");
        
        // Default transition - action doesn't change unless explicitly modified by transition modules
        sb.append("  [sync] true -> (action' = action);\n");
        
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
    /**
     * Generate action modules based on Soar transition rules
     * Extracts SS-transition, D-transition, DD-transition modules
     */
    private String generateActionModules() {
        StringBuilder sb = new StringBuilder();
        
        // Find transition rules and generate modules
        List<TransitionInfo> transitions = extractTransitionRules();
        
        if (transitions.isEmpty()) {
            sb.append("// No action transition modules found in Soar rules\n");
            return sb.toString();
        }
        
        // Generate a module for each unique transition type
        for (TransitionInfo transition : transitions) {
            sb.append(generateTransitionModule(transition));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Extract transition information from Soar rules
     */
    private List<TransitionInfo> extractTransitionRules() {
        List<TransitionInfo> transitions = new ArrayList<>();
        
        for (Rule rule : rules.rules) {
            // Look for apply* transition rules
            if (rule.ruleName.startsWith("apply*apply-") && rule.ruleName.contains("-transition")) {
                TransitionInfo info = new TransitionInfo();
                info.ruleName = rule.ruleName;
                
                // Extract transition name (e.g., "SS", "D", "DD")
                String transName = rule.ruleName.replace("apply*apply-", "").replace("-transition", "");
                info.transitionName = transName;
                
                // Extract TO action value from RHS lines
                // Look for pattern like "(<s> ^action 0 +)"
                for (String rhsLine : rule.rhsLines) {
                    if (rhsLine.contains("action") && rhsLine.contains("+") && !rhsLine.contains("-")) {
                        // Extract the action value
                        // Pattern: "(<s> ^action 0 +)" or similar
                        String cleaned = rhsLine.replaceAll("[<>()^+\\s]", " ").trim();
                        String[] parts = cleaned.split("\\s+");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("action") && i + 1 < parts.length) {
                                try {
                                    info.toAction = Integer.parseInt(parts[i + 1]);
                                    break;
                                } catch (NumberFormatException e) {
                                    // Not a number, continue
                                }
                            }
                        }
                    }
                }
                
                // If still not found, try valueMap
                if (info.toAction < 0 && rule.valueMap.containsKey("action")) {
                    try {
                        info.toAction = Integer.parseInt(rule.valueMap.get("action"));
                    } catch (NumberFormatException e) {
                        System.err.println("Could not parse action value: " + rule.valueMap.get("action"));
                    }
                }
                
                // Fallback: infer from transition name based on common patterns
                if (info.toAction < 0) {
                    if (transName.equals("SS")) {
                        info.toAction = 0; // SS -> Deciding (action 0)
                    } else if (transName.equals("D")) {
                        info.toAction = 1; // D -> Decided (action 1)
                    } else if (transName.equals("DD")) {
                        info.toAction = 2; // DD -> Scan-and-Select (action 2)
                    }
                }
                
                // Find corresponding propose rule to get FROM action
                String proposeRuleName = "propose*" + transName + "-transition";
                for (Rule proposeRule : rules.rules) {
                    if (proposeRule.ruleName.equals(proposeRuleName)) {
                        // Extract FROM action from guards
                        for (String guard : proposeRule.guards) {
                            if (guard.contains("action")) {
                                // Parse guards like "action = 0" or "action { << 3 2 >> }"
                                if (guard.contains("<<")) {
                                    // Multiple values like "action { << 3 2 >> }"
                                    String actionPart = guard.substring(guard.indexOf("<<") + 2, guard.indexOf(">>"));
                                    String[] actions = actionPart.trim().split("\\s+");
                                    info.fromActions = new ArrayList<>();
                                    for (String a : actions) {
                                        try {
                                            info.fromActions.add(Integer.parseInt(a));
                                        } catch (NumberFormatException e) {
                                            // Ignore
                                        }
                                    }
                                } else if (guard.contains("=")) {
                                    // Single value like "action = 0"
                                    String[] parts = guard.split("=");
                                    if (parts.length > 1) {
                                        try {
                                            info.fromAction = Integer.parseInt(parts[1].trim());
                                        } catch (NumberFormatException e) {
                                            // Ignore
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Extract PDF value from input-link references
                        // Look for patterns like "(<sd> ^pdf2 <pdf2>)" in conditions
                        for (String var : proposeRule.variables) {
                            if (var.startsWith("pdf")) {
                                info.pdfName = var;
                                break;
                            }
                        }
                    }
                }
                
                // Extract event name from RHS lines
                for (String rhsLine : rule.rhsLines) {
                    if (rhsLine.contains("event") && rhsLine.contains("+") && !rhsLine.contains("-")) {
                        // Extract event value  
                        // Pattern: "(<out> ^event Deciding +)" or similar
                        String cleaned = rhsLine.replaceAll("[<>()^+]", " ").trim();
                        String[] parts = cleaned.split("\\s+");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("event") && i + 1 < parts.length) {
                                info.eventName = parts[i + 1];
                                break;
                            }
                        }
                    }
                }
                
                transitions.add(info);
            }
        }
        
        return transitions;
    }
    
    /**
     * Generate a PRISM module for a transition
     */
    private String generateTransitionModule(TransitionInfo info) {
        StringBuilder sb = new StringBuilder();
        
        // Module name based on transition
        String moduleName = info.transitionName.toLowerCase() + "_transition";
        
        sb.append(String.format("module %s\n", moduleName));
        sb.append(String.format("  %s_done : [0..1] init 0;\n", moduleName));
        sb.append(String.format("  %s_ing  : [0..1] init 0;\n", moduleName));
        sb.append("\n");
        
        // Generate transition rules
        // Guard: action matches from state, not done, not in progress
        String actionGuard = "";
        if (info.fromActions != null && !info.fromActions.isEmpty()) {
            // Multiple source actions (e.g., action=3 or action=2 for SS)
            List<String> actionParts = new ArrayList<>();
            for (int action : info.fromActions) {
                actionParts.add("action=" + action);
            }
            actionGuard = "(" + String.join(" | ", actionParts) + ")";
        } else if (info.fromAction >= 0) {
            actionGuard = "action=" + info.fromAction;
        }
        
        // Start transition
        if (!actionGuard.isEmpty()) {
            sb.append(String.format("  [sync] time_counter < TOTAL_TIME & %s & %s_done=0 & %s_ing=0 ->\n",
                actionGuard, moduleName, moduleName));
            sb.append(String.format("    (%s_ing' = 1);\n", moduleName));
            sb.append("\n");
        }
        
        // Complete transition (probabilistic if PDF available)
        if (info.pdfName != null && !info.pdfName.isEmpty()) {
            // Look up PDF value
            double pdfValue = findProbabilityValue(info.pdfName, -1.0);
            if (pdfValue < 0 && config != null && config.getConstants().containsKey(info.pdfName)) {
                Object pdfObj = config.getConstants().get(info.pdfName);
                if (pdfObj instanceof Number) {
                    pdfValue = ((Number)pdfObj).doubleValue();
                }
            }
            
            if (pdfValue > 0) {
                sb.append(String.format("  [sync] %s_ing=1 ->\n", moduleName));
                sb.append(String.format("    %.6f : (%s_done' = 1) & (%s_ing' = 0) & (action' = %d)\n",
                    pdfValue, moduleName, moduleName, info.toAction));
                sb.append(String.format("  + %.6f : (%s_ing' = 0);\n", 1.0 - pdfValue, moduleName));
                sb.append("\n");
            }
        } else {
            // Deterministic transition
            sb.append(String.format("  [sync] %s_ing=1 ->\n", moduleName));
            sb.append(String.format("    (%s_done' = 1) & (%s_ing' = 0) & (action' = %d);\n",
                moduleName, moduleName, info.toAction));
            sb.append("\n");
        }
        
        // Reset done flag
        sb.append(String.format("  [sync] %s_done=1 -> (%s_done' = 0);\n", moduleName, moduleName));
        sb.append("\n");
        
        // Else clause - no change
        sb.append(String.format("  [sync] !(%s_done=1) & !(%s_ing=1) & !(time_counter < TOTAL_TIME & %s & %s_done=0 & %s_ing=0) ->\n",
            moduleName, moduleName, actionGuard.isEmpty() ? "true" : actionGuard, moduleName, moduleName));
        sb.append(String.format("    (%s_done' = %s_done) & (%s_ing' = %s_ing);\n",
            moduleName, moduleName, moduleName, moduleName));
        
        sb.append("endmodule\n");
        
        return sb.toString();
    }
    
    /**
     * Helper class to store transition information
     */
    private static class TransitionInfo {
        String ruleName;
        String transitionName;
        int fromAction = -1;
        List<Integer> fromActions = null;
        int toAction = -1;
        String eventName;
        String pdfName;
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
