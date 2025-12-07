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
    
    // Extracted variable information from Soar
    private Map<String, VariableInfo> stateVariables = new LinkedHashMap<>();
    private Map<String, Integer> monitorConstants = new LinkedHashMap<>();
    private int minAction = Integer.MAX_VALUE;
    private int maxAction = Integer.MIN_VALUE;
    private int selectActionTrigger = -1;
    private int decideActionTrigger = -1;
    private int maxResponseState = 60;
    
    // Variable name references - dynamically discovered from Soar
    private String actionVarName = null;
    private String sickVarName = null;
    private String nameVarName = null;
    private String tsVarName = null;
    private String sicknessCheckedVarName = null;
    
    // Module constants (fallback if not extracted)
    private static final int MISSION_MONITOR = 0;
    private static final int SICKNESS_MONITOR = 1;
    
    /**
     * Internal class to store variable metadata extracted from Soar
     */
    private static class VariableInfo {
        String name;
        int minValue;
        int maxValue;
        int initValue;
        String type; // "int" or "boolean" inferred from range
        
        VariableInfo(String name, int minValue, int maxValue, int initValue) {
            // Normalize variable name for PRISM compatibility (replace dashes with underscores)
            this.name = normalizePrismVariableName(name);
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.initValue = initValue;
            this.type = (maxValue == 1) ? "boolean" : "int";
        }
        
        String getPrismDeclaration() {
            return String.format("%s : [%d..%d] init %d", name, minValue, maxValue, initValue);
        }
    }
    
    /**
     * Normalize variable names for PRISM compatibility
     * PRISM syntax does not allow dashes in variable names, only underscores
     */
    private static String normalizePrismVariableName(String name) {
        if (name == null) return null;
        return name.replace('-', '_');
    }
    
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
            throw new IllegalStateException("Cannot infer time interval from Soar rules. " +
                "No time-based guards or operations found. " +
                "Please ensure your Soar file contains time-counter comparisons or provide a configuration file.");
        }
        
        // Extract state variables and their metadata from Soar
        extractStateVariables();
        
        // Extract action range from transitions
        extractActionRange();
        
        // Extract action triggers for response module
        extractActionTriggers();
    }
    
    /**
     * Extract all state variables from apply*initialize rule
     * Builds metadata including name, range, and initial value
     */
    private void extractStateVariables() {
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                // Extract state variables from valueMap
                for (Map.Entry<String, String> entry : rule.valueMap.entrySet()) {
                    String varName = entry.getKey();
                    String varValue = entry.getValue();
                    
                    // Skip special variables
                    if (varName.equals("total-time") || varName.equals("time-counter")) {
                        continue;
                    }
                    
                    try {
                        int initValue = parseInitValue(varValue);
                        
                        // Infer range based on variable name and init value
                        int minValue = 0;
                        int maxValue = inferMaxValue(varName, initValue);
                        
                        // Store variable info with normalized name as key
                        String normalizedName = normalizePrismVariableName(varName);
                        stateVariables.put(normalizedName, new VariableInfo(varName, minValue, maxValue, initValue));
                        
                        // Special handling for monitor constants
                        if (varName.equals("name")) {
                            extractMonitorConstants(rule);
                        }
                        
                    } catch (NumberFormatException e) {
                        // Not a numeric variable, skip
                        System.out.println("DEBUG: Skipping non-numeric variable: " + varName + " = " + varValue);
                    }
                }
                break;
            }
        }
        
        // After extraction, discover variable name mappings
        discoverVariableNames();
    }
    
    /**
     * Discover which extracted variables correspond to common Soar patterns
     * Maps generic concepts (action, sickness, etc.) to actual variable names used in this Soar model
     */
    private void discoverVariableNames() {
        // Find action variable - look for variables used in transition rules
        for (String varName : stateVariables.keySet()) {
            if (varName.contains("action")) {
                actionVarName = varName;
            }
            if (varName.contains("sick") && !varName.contains("checked")) {
                sickVarName = varName;
            }
            if (varName.contains("name")) {
                nameVarName = varName;
            }
            if (varName.contains("ts") || (varName.contains("temp") && varName.contains("sick"))) {
                tsVarName = varName;
            }
            if (varName.contains("checked") || (varName.contains("sickness") && varName.contains("check"))) {
                sicknessCheckedVarName = varName;
            }
        }
        
        // Fallbacks - if not found by name pattern, use first variables of appropriate type
        if (actionVarName == null) {
            // Look for integer variable used in transition guards
            for (String varName : stateVariables.keySet()) {
                VariableInfo var = stateVariables.get(varName);
                if (var.maxValue > 1) {  // Multi-valued variable
                    actionVarName = varName;
                    break;
                }
            }
        }
        
        System.out.println("INFO: Discovered variable mappings:");
        System.out.println("  action -> " + actionVarName);
        System.out.println("  sick -> " + sickVarName);
        System.out.println("  name -> " + nameVarName);
        System.out.println("  ts -> " + tsVarName);
        System.out.println("  sickness_checked -> " + sicknessCheckedVarName);
    }
    
    /**
     * Get the PRISM variable name for action state
     */
    private String getActionVarName() {
        return actionVarName != null ? actionVarName : "action";
    }
    
    /**
     * Get the PRISM variable name for sickness state
     */
    private String getSickVarName() {
        return sickVarName != null ? sickVarName : "sick";
    }
    
    /**
     * Get the PRISM variable name for monitor name
     */
    private String getNameVarName() {
        return nameVarName != null ? nameVarName : "name";
    }
    
    /**
     * Get the PRISM variable name for temporary sickness state
     */
    private String getTsVarName() {
        return tsVarName != null ? tsVarName : "ts";
    }
    
    /**
     * Get the PRISM variable name for sickness checked flag
     */
    private String getSicknessCheckedVarName() {
        return sicknessCheckedVarName != null ? sicknessCheckedVarName : "sickness_checked";
    }
    
    /**
     * Parse initialization value from Soar (handles "yes", "no", numbers, etc.)
     */
    private int parseInitValue(String value) throws NumberFormatException {
        if (value == null) throw new NumberFormatException("null value");
        
        // Handle boolean-like values
        if (value.equals("yes") || value.equals("true")) return 1;
        if (value.equals("no") || value.equals("false")) return 0;
        
        // Handle monitor names
        if (value.equals("mission-monitor")) return 0;
        if (value.equals("sickness-monitor")) return 1;
        
        // Parse as number
        return Integer.parseInt(value.trim());
    }
    
    /**
     * Infer maximum value for a variable based on its name and init value
     */
    private int inferMaxValue(String varName, int initValue) {
        // Check if variable appears in rules to find max value
        int maxFound = initValue;
        
        for (Rule rule : rules.rules) {
            // Check guards and valueMap for this variable
            for (String guard : rule.guards) {
                if (guard.contains(varName)) {
                    // Try to extract comparison values
                    String[] parts = guard.split("[=<>]");
                    for (String part : parts) {
                        try {
                            int val = Integer.parseInt(part.trim());
                            maxFound = Math.max(maxFound, val);
                        } catch (NumberFormatException e) {
                            // Not a number
                        }
                    }
                }
            }
            
            // Check valueMap
            if (rule.valueMap.containsKey(varName)) {
                try {
                    int val = parseInitValue(rule.valueMap.get(varName));
                    maxFound = Math.max(maxFound, val);
                } catch (NumberFormatException e) {
                    // Not a number
                }
            }
        }
        
        // Default to 1 for boolean-like variables
        if (maxFound == 0 || maxFound == 1) return 1;
        
        return maxFound;
    }
    
    /**
     * Extract monitor constant values from init rule
     */
    private void extractMonitorConstants(Rule rule) {
        // Find monitor constant definitions
        for (String guard : rule.guards) {
            if (guard.contains("mission-monitor") || guard.contains("sickness-monitor")) {
                // Extract from guards or valueMap
            }
        }
        
        // Use standard values if not found
        monitorConstants.put("mission_monitor", 0);
        monitorConstants.put("sickness_monitor", 1);
    }
    
    /**
     * Extract action range from transition rules
     */
    private void extractActionRange() {
        List<TransitionInfo> transitions = extractTransitionRules();
        
        for (TransitionInfo trans : transitions) {
            if (trans.toAction >= 0) {
                minAction = Math.min(minAction, trans.toAction);
                maxAction = Math.max(maxAction, trans.toAction);
            }
            if (trans.fromAction >= 0) {
                minAction = Math.min(minAction, trans.fromAction);
                maxAction = Math.max(maxAction, trans.fromAction);
            }
            if (trans.fromActions != null) {
                for (int action : trans.fromActions) {
                    minAction = Math.min(minAction, action);
                    maxAction = Math.max(maxAction, action);
                }
            }
        }
        
        // Also check init value if action variable exists
        if (stateVariables.containsKey("action")) {
            int initAction = stateVariables.get("action").initValue;
            minAction = Math.min(minAction, initAction);
            maxAction = Math.max(maxAction, initAction);
        }
        
        // If no transitions found, fall back to extracted state variable
        if (minAction == Integer.MAX_VALUE || maxAction == Integer.MIN_VALUE) {
            minAction = 0;
            maxAction = stateVariables.containsKey("action") ? stateVariables.get("action").maxValue : 3;
        }
    }
    
    /**
     * Extract action triggers for response module
     * Determines which actions trigger select vs decide responses
     */
    private void extractActionTriggers() {
        // Extract max response state from config distributions
        if (config != null) {
            for (PrismConfig.Distribution dist : config.getResponseSelect().values()) {
                if (dist != null && dist.probabilities != null) {
                    for (PrismConfig.Distribution.StateProb sp : dist.probabilities) {
                        maxResponseState = Math.max(maxResponseState, sp.state);
                    }
                }
            }
            for (PrismConfig.Distribution dist : config.getResponseDecide().values()) {
                if (dist != null && dist.probabilities != null) {
                    for (PrismConfig.Distribution.StateProb sp : dist.probabilities) {
                        maxResponseState = Math.max(maxResponseState, sp.state);
                    }
                }
            }
        }
        
        // Extract action triggers from transition guards
        // Look for SS-transition (scan-and-select) and D-transition (deciding)
        List<TransitionInfo> transitions = extractTransitionRules();
        
        // Default: use initial action state for selecting
        selectActionTrigger = maxAction; 
        
        for (TransitionInfo trans : transitions) {
            String name = trans.transitionName.toLowerCase();
            
            // SS-transition: fromActions tells us which states are "selecting" states
            // Agent is in Scan-and-Select mode BEFORE this transition
            if ((name.startsWith("ss") || name.contains("scan")) && trans.fromActions != null && trans.fromActions.length > 0) {
                // Use the first (typically highest) fromAction as the primary select trigger
                // In Soar: propose*SS-transition has ^action { << 3 2 >> } meaning select happens when action=3 or action=2
                selectActionTrigger = trans.fromActions[0];  // Typically 3 (initial state)
            }
            
            // D-transition: toAction tells us the state AFTER deciding starts
            // But we want the state WHERE deciding happens (the fromAction)
            if ((name.startsWith("d-") || name.equals("d")) && !name.contains("dd") && trans.fromAction >= 0) {
                decideActionTrigger = trans.fromAction;  // D transition: deciding happens when action=0
            }
        }
        
        System.out.println("DEBUG: Extracted action triggers - select=" + selectActionTrigger + ", decide=" + decideActionTrigger);
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
        
        // Extract transitions first (needed by action_state module)
        List<TransitionInfo> transitions = extractTransitionRules();
        
        // Generate action state module (must be before transition modules)
        output.append(generateActionStateModule(transitions));
        output.append("\n");
        
        // Generate sickness module
        output.append(generateSicknessModule());
        output.append("\n");
        
        // Generate action transition modules
        output.append(generateActionModules(transitions));
        output.append("\n");
        
        // Generate response time module (if distributions available)
        String responseModule = generateResponseTimeModule();
        if (!responseModule.isEmpty()) {
            output.append(responseModule);
            output.append("\n");
        }
        
        // Generate decision error module (if distributions available)
        String errorModule = generateDecisionErrorModule();
        if (!errorModule.isEmpty()) {
            output.append(errorModule);
            output.append("\n");
        }
        
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
        
        // If still not found in Soar, check config constants
        if (pdf1 < 0 && config != null && config.getConstants().containsKey("pdf1")) {
            Object pdf1Obj = config.getConstants().get("pdf1");
            if (pdf1Obj instanceof Number) {
                pdf1 = ((Number)pdf1Obj).doubleValue();
            }
        }
        
        // If still not found, try to extract from sickness probability table
        // pdf1 represents P(healthy stays healthy) = P(sick=0 | sick'=0, time=0)
        if (pdf1 < 0 && config != null && !config.getSicknessProbabilityTable().isEmpty()) {
            // Try to get the probability for staying healthy at the first time window
            Double prob = config.getSicknessProbabilityTable().get("0,0,0");
            if (prob != null) {
                pdf1 = prob;
            } else {
                // Try other time windows if time=0 not available
                for (Map.Entry<String, Double> entry : config.getSicknessProbabilityTable().entrySet()) {
                    String[] parts = entry.getKey().split(",");
                    if (parts.length == 3 && parts[1].equals("0") && parts[2].equals("0")) {
                        pdf1 = entry.getValue();
                        break;
                    }
                }
            }
        }
        
        // Fail fast if pdf1 not found
        if (pdf1 < 0) {
            throw new IllegalStateException("Cannot find pdf1 or sick_thres value in Soar rules or configuration. " +
                "Please ensure your Soar file contains sick_thres initialization, or provide a configuration file with either:\n" +
                "  - 'pdf1' in the constants section, OR\n" +
                "  - sicknessProbabilityTable with entries like '0,0,0' (time,currentLevel,nextLevel)");
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
                // Check if it's an integer type first, before checking floating point
                if (value instanceof Integer || value instanceof Long) {
                    sb.append(String.format("const int %s = %d;\n", name, ((Number)value).intValue()));
                } else if (value instanceof Double || value instanceof Float) {
                    sb.append(String.format("const double %s = %.2f;\n", name, ((Number)value).doubleValue()));
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
    private String generateActionStateModule(List<TransitionInfo> transitions) {
        StringBuilder sb = new StringBuilder();
        sb.append("module action_state\n");
        
        // Extract initial action value from multiple sources
        Integer initAction = null;
        
        // 1. Try to extract from Soar apply*initialize rule
        if (actionVarName != null && stateVariables.containsKey(actionVarName)) {
            initAction = stateVariables.get(actionVarName).initValue;
            System.out.println("INFO: Found initial " + actionVarName + "=" + initAction + " in apply*initialize rule");
        } else {
            for (Rule rule : rules.rules) {
                if (rule.ruleName.equals("apply*initialize")) {
                    String actionVal = rule.valueMap.get(actionVarName != null ? actionVarName : "action");
                    if (actionVal != null) {
                        try {
                            initAction = Integer.parseInt(actionVal);
                            System.out.println("INFO: Found initial action=" + initAction + " in apply*initialize rule");
                            break;
                        } catch (NumberFormatException e) {
                            // Keep looking
                        }
                    }
                }
            }
        }
        
        // 2. Try to extract from config file constants
        if (initAction == null && config != null) {
            Object actionObj = config.getConstants().get("initialAction");
            if (actionObj != null) {
                try {
                    initAction = ((Number) actionObj).intValue();
                    System.out.println("INFO: Found initial action=" + initAction + " in config constants");
                } catch (Exception e) {
                    // Keep looking
                }
            }
        }
        
        // 3. Infer from transition rules - find highest action state and add 1
        if (initAction == null && !transitions.isEmpty()) {
            int maxAction = -1;
            for (TransitionInfo trans : transitions) {
                if (trans.toAction >= 0 && trans.toAction > maxAction) {
                    maxAction = trans.toAction;
                }
                if (trans.fromActions != null) {
                    for (Integer from : trans.fromActions) {
                        if (from > maxAction) {
                            maxAction = from;
                        }
                    }
                }
                if (trans.fromAction >= 0 && trans.fromAction > maxAction) {
                    maxAction = trans.fromAction;
                }
            }
            if (maxAction >= 0) {
                initAction = maxAction + 1;  // Initial state is one beyond all transition states
                System.out.println("INFO: Inferred initial action=" + initAction + " from transition rules (max action + 1)");
            }
        }
        
        // Fail fast if action not found
        if (initAction == null) {
            throw new IllegalStateException("Cannot find initial action value. Tried:\n" +
                "  1. Soar apply*initialize rule (valueMap['action'])\n" +
                "  2. Config file constants section (initialAction)\n" +
                "  3. Inference from transition rules (max action + 1)\n" +
                "Please provide initial action in one of these locations.");
        }
        
        // Use extracted action range instead of hardcoded [0..3]
        int actionMin = (minAction != Integer.MAX_VALUE) ? minAction : 0;
        int actionMax = (maxAction != Integer.MIN_VALUE) ? maxAction : 3;
        
        // Ensure action range includes the init value
        if (initAction > actionMax) {
            actionMax = initAction;
        }
        if (initAction < actionMin) {
            actionMin = initAction;
        }
        
        // Generate action variable declaration with extracted/inferred range
        String actionVar = getActionVarName();
        sb.append(String.format("  %s : [%d..%d] init %d;\n", actionVar, actionMin, actionMax, initAction));
        sb.append("\n");
        
        // Generate transitions for each action module - action_state listens for signals
        // Use mutually exclusive guards to avoid overlaps
        for (int i = 0; i < transitions.size(); i++) {
            TransitionInfo info = transitions.get(i);
            String moduleName = info.transitionName.toLowerCase() + "_transition";
            
            // Build guard that excludes other transitions
            StringBuilder guard = new StringBuilder();
            guard.append(String.format("%s_ing=1", moduleName));
            
            for (int j = 0; j < transitions.size(); j++) {
                if (i != j) {
                    String otherModule = transitions.get(j).transitionName.toLowerCase() + "_transition";
                    guard.append(String.format(" & !(%s_ing=1)", otherModule));
                }
            }
            
            sb.append(String.format("  [sync] %s -> (%s' = %d);\n", guard.toString(), actionVar, info.toAction));
        }
        sb.append("\n");
        
        // Else clause - keep action unchanged when no transition is active
        StringBuilder elseGuard = new StringBuilder();
        for (int i = 0; i < transitions.size(); i++) {
            if (i > 0) elseGuard.append(" & ");
            String moduleName = transitions.get(i).transitionName.toLowerCase() + "_transition";
            elseGuard.append(String.format("!(%s_ing=1)", moduleName));
        }
        
        sb.append(String.format("  [sync] %s -> (%s' = %s);\n", elseGuard.toString(), actionVar, actionVar));
        
        sb.append("endmodule\n");
        return sb.toString();
    }
    
    /**
     * Generate the sickness monitoring module
     */
    /**
     * Generate the sickness monitoring module using extracted variable information
     */
    private String generateSicknessModule() {
        StringBuilder sb = new StringBuilder();
        sb.append("module sickness\n");
        
        // State variables - use extracted info instead of hardcoded
        // Get variable names from extracted metadata
        String nameVar = getNameVarName();
        String sickVar = getSickVarName();
        String tsVar = getTsVarName();
        String sicknessCheckedVar = getSicknessCheckedVarName();
        
        // Generate variable declarations from extracted state variables
        String[] varReferences = {nameVar, sickVar, tsVar, sicknessCheckedVar};
        for (String varRef : varReferences) {
            // varRef is already normalized (from getters), use it as key
            if (stateVariables.containsKey(varRef)) {
                VariableInfo var = stateVariables.get(varRef);
                // var.name is already PRISM-compatible (normalized in constructor)
                sb.append(String.format("  %-16s : [%d..%d] init ", var.name, var.minValue, var.maxValue));
                
                // Handle init value - special case for 'name' which should init to mission_monitor
                if (var.name.equals(nameVar)) {
                    sb.append("mission_monitor;\n");
                } else {
                    sb.append(String.format("%d;\n", var.initValue));
                }
            } else {
                // Fallback to hardcoded if variable not found in Soar
                System.err.println("WARNING: Variable '" + varRef + "' not found in Soar, using defaults");
                if (varRef.equals(nameVar)) {
                    sb.append(String.format("  %-16s : [0..1] init mission_monitor;\n", varRef));
                } else {
                    sb.append(String.format("  %-16s : [0..1] init 0;\n", varRef));
                }
            }
        }
        sb.append("\n");
        
        sb.append("  // ---- switch to sickness monitor at window starts ----\n");
        // Switch from mission_monitor to sickness_monitor at each window start
        for (int window : timeWindows) {
            sb.append(String.format("  [sync] time_counter = %4d & %s=mission_monitor -> (%s' = sickness_monitor) & (%s' = %s) & (%s' = %s) & (%s' = %s);\n",
                window, nameVar, nameVar, sickVar, sickVar, tsVar, tsVar, sicknessCheckedVar, sicknessCheckedVar));
        }
        
        sb.append("\n  // ---- commit at window end ----\n");
        // Generate commit transitions at window ends - use dynamic variable names
        for (int commitTime : commitTimes) {
            sb.append(String.format("  [sync] time_counter = %4d -> (%s' = %s);\n", 
                commitTime, sickVar, tsVar));
        }
        
        sb.append("\n  // ---- sample at window start (one check per visit) ----\n");
        // Generate sampling transitions at window starts - use dynamic variable names
        for (int window : timeWindows) {
            // Sample when not sick
            sb.append(String.format("  [sync] time_counter = %4d & %s=sickness_monitor & %s=0 & %s=0 ->\n",
                window, nameVar, sicknessCheckedVar, sickVar));
            sb.append(String.format("        pdf1     : (%s'=0) & (%s'=1)\n", tsVar, sicknessCheckedVar));
            sb.append(String.format("      + (1-pdf1) : (%s'=1) & (%s'=1);\n", tsVar, sicknessCheckedVar));
            
            // Sample when sick (stays sick)
            sb.append(String.format("  [sync] time_counter = %4d & %s=sickness_monitor & %s=0 & %s=1 ->\n",
                window, nameVar, sicknessCheckedVar, sickVar));
            sb.append(String.format("        1 : (%s'=1) & (%s'=1);\n\n", tsVar, sicknessCheckedVar));
        }
        
        // Switch back to mission monitor - use dynamic variable names
        sb.append(String.format("  [sync] %s = sickness_monitor & %s = 1 &\n", nameVar, sicknessCheckedVar));
        // Dynamically generate window exclusions
        sb.append("  ");
        for (int i = 0; i < timeWindows.size(); i++) {
            if (i > 0) sb.append(" & ");
            sb.append(String.format("time_counter != %d", timeWindows.get(i)));
        }
        sb.append(" &\n");
        // Dynamically generate commit time exclusions
        sb.append("  ");
        for (int i = 0; i < commitTimes.size(); i++) {
            if (i > 0) sb.append(" & ");
            sb.append(String.format("time_counter != %d", commitTimes.get(i)));
        }
        sb.append("\n");
        sb.append("->\n");
        sb.append(String.format("  (%s' = mission_monitor) & (%s' = 0);\n\n", nameVar, sicknessCheckedVar));
        
        // Generate default/else transition - use dynamic variable names
        sb.append("  [sync]\n");
        // Dynamically generate commit time exclusions for else guard
        sb.append("    ");
        for (int i = 0; i < commitTimes.size(); i++) {
            if (i > 0) sb.append(" & ");
            sb.append(String.format("time_counter != %d", commitTimes.get(i)));
        }
        sb.append(" &\n");
        // Dynamically generate window check exclusion
        sb.append("    !((");
        for (int i = 0; i < timeWindows.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(String.format("time_counter = %4d", timeWindows.get(i)));
        }
        sb.append(") &\n");
        sb.append(String.format("      %s = sickness_monitor & %s = 0) &\n", nameVar, sicknessCheckedVar));
        sb.append(String.format("    !(%s = sickness_monitor & %s = 1)\n", nameVar, sicknessCheckedVar));
        sb.append("  ->\n");
        sb.append(String.format("    (%s' = %s) & (%s' = %s) & (%s' = %s) & (%s' = %s);\n", 
            sickVar, sickVar, tsVar, tsVar, sicknessCheckedVar, sicknessCheckedVar, nameVar, nameVar));
        
        sb.append("endmodule\n");
        return sb.toString();
    }
    
    /**
     * Generate response time module using loaded distributions
     * Integrates responseSelect and responseDecide distributions from config
     * Extracts max response state and action triggers dynamically
     */
    private String generateResponseTimeModule() {
        if (config == null || 
            (config.getResponseSelect().isEmpty() && config.getResponseDecide().isEmpty())) {
            return ""; // No response distributions available
        }
        
        // Get dynamic variable names
        String actionVar = getActionVarName();
        String sickVar = getSickVarName();
        
        // Use class variables for action triggers and max response state (already extracted in extractActionTriggers)
        StringBuilder sb = new StringBuilder();
        sb.append("\n// ---- Response Time Modeling ----\n");
        sb.append("module response_time\n");
        sb.append(String.format("  response_state : [0..%d] init 0;  // 0 = idle, 1-%d = responding\n", 
            maxResponseState, maxResponseState));
        sb.append("  response_type  : [0..2] init 0;   // 0 = none, 1 = select, 2 = decide\n\n");
        
        // Generate select response transitions
        if (!config.getResponseSelect().isEmpty()) {
            sb.append("  // ---- Scan-and-Select Response Distribution ----\n");
            sb.append(String.format("  // Triggered when %s=%d (selecting state)\n", actionVar, selectActionTrigger));
            
            // Use sickness level 0 (healthy) distribution as example
            PrismConfig.Distribution selectDist = config.getResponseSelect().get("sickness0");
            if (selectDist != null && selectDist.probabilities != null) {
                // Normalize probabilities to sum to exactly 1.0
                double totalProb = 0.0;
                for (PrismConfig.Distribution.StateProb sp : selectDist.probabilities) {
                    totalProb += sp.probability;
                }
                
                sb.append(String.format("  [sync] %s=%d & response_state=0 & %s=0 ->\n", 
                    actionVar, selectActionTrigger, sickVar));
                double accumulatedProb = 0.0;
                for (int i = 0; i < selectDist.probabilities.size(); i++) {
                    PrismConfig.Distribution.StateProb sp = selectDist.probabilities.get(i);
                    double normalizedProb;
                    if (i == selectDist.probabilities.size() - 1) {
                        // Last probability: ensure exact sum to 1.0 by taking complement
                        normalizedProb = 1.0 - accumulatedProb;
                    } else {
                        normalizedProb = sp.probability / totalProb;
                        accumulatedProb += normalizedProb;
                    }
                    // Use %.16f for maximum precision to avoid rounding issues
                    sb.append(String.format("    %.16f : (response_state'=%d) & (response_type'=1)",
                        normalizedProb, sp.state));
                    if (i < selectDist.probabilities.size() - 1) {
                        sb.append(" +\n");
                    } else {
                        sb.append(";\n");
                    }
                }
                sb.append("\n");
            }
            
            // Sick agent has different distribution
            PrismConfig.Distribution selectSickDist = config.getResponseSelect().get("sickness1");
            if (selectSickDist != null && selectSickDist.probabilities != null) {
                // Normalize probabilities to sum to exactly 1.0
                double totalProb = 0.0;
                for (PrismConfig.Distribution.StateProb sp : selectSickDist.probabilities) {
                    totalProb += sp.probability;
                }
                
                sb.append(String.format("  [sync] %s=%d & response_state=0 & %s=1 ->\n",
                    actionVar, selectActionTrigger, sickVar));
                double accumulatedProb = 0.0;
                for (int i = 0; i < selectSickDist.probabilities.size(); i++) {
                    PrismConfig.Distribution.StateProb sp = selectSickDist.probabilities.get(i);
                    double normalizedProb;
                    if (i == selectSickDist.probabilities.size() - 1) {
                        // Last probability: ensure exact sum to 1.0 by taking complement
                        normalizedProb = 1.0 - accumulatedProb;
                    } else {
                        normalizedProb = sp.probability / totalProb;
                        accumulatedProb += normalizedProb;
                    }
                    // Use %.16f for maximum precision to avoid rounding issues
                    sb.append(String.format("    %.16f : (response_state'=%d) & (response_type'=1)",
                        normalizedProb, sp.state));
                    if (i < selectSickDist.probabilities.size() - 1) {
                        sb.append(" +\n");
                    } else {
                        sb.append(";\n");
                    }
                }
                sb.append("\n");
            }
        }
        
        // Generate decide response transitions
        if (!config.getResponseDecide().isEmpty()) {
            sb.append("  // ---- Decision Response Distribution ----\n");
            sb.append("  // Triggered when action transitions to deciding state\n");
            
            // Healthy agent decision response
            PrismConfig.Distribution decideDist = config.getResponseDecide().get("sickness0");
            if (decideDist != null && decideDist.probabilities != null) {
                // Normalize probabilities to sum to exactly 1.0
                double totalProb = 0.0;
                for (PrismConfig.Distribution.StateProb sp : decideDist.probabilities) {
                    totalProb += sp.probability;
                }
                
                sb.append(String.format("  [sync] %s=%d & response_state=0 & %s=0 ->\n",
                    actionVar, decideActionTrigger, sickVar));
                double accumulatedProb = 0.0;
                for (int i = 0; i < decideDist.probabilities.size(); i++) {
                    PrismConfig.Distribution.StateProb sp = decideDist.probabilities.get(i);
                    double normalizedProb;
                    if (i == decideDist.probabilities.size() - 1) {
                        // Last probability: ensure exact sum to 1.0 by taking complement
                        normalizedProb = 1.0 - accumulatedProb;
                    } else {
                        normalizedProb = sp.probability / totalProb;
                        accumulatedProb += normalizedProb;
                    }
                    // Use %.16f for maximum precision to avoid rounding issues
                    sb.append(String.format("    %.16f : (response_state'=%d) & (response_type'=2)",
                        normalizedProb, sp.state));
                    if (i < decideDist.probabilities.size() - 1) {
                        sb.append(" +\n");
                    } else {
                        sb.append(";\n");
                    }
                }
                sb.append("\n");
            }
            
            // Sick agent decision response
            PrismConfig.Distribution decideSickDist = config.getResponseDecide().get("sickness1");
            if (decideSickDist != null && decideSickDist.probabilities != null) {
                // Normalize probabilities to sum to exactly 1.0
                double totalProb = 0.0;
                for (PrismConfig.Distribution.StateProb sp : decideSickDist.probabilities) {
                    totalProb += sp.probability;
                }
                
                sb.append(String.format("  [sync] %s=%d & response_state=0 & %s=1 ->\n",
                    actionVar, decideActionTrigger, sickVar));
                double accumulatedProb = 0.0;
                for (int i = 0; i < decideSickDist.probabilities.size(); i++) {
                    PrismConfig.Distribution.StateProb sp = decideSickDist.probabilities.get(i);
                    double normalizedProb;
                    if (i == decideSickDist.probabilities.size() - 1) {
                        // Last probability: ensure exact sum to 1.0 by taking complement
                        normalizedProb = 1.0 - accumulatedProb;
                    } else {
                        normalizedProb = sp.probability / totalProb;
                        accumulatedProb += normalizedProb;
                    }
                    // Use %.16f for maximum precision to avoid rounding issues
                    sb.append(String.format("    %.16f : (response_state'=%d) & (response_type'=2)",
                        normalizedProb, sp.state));
                    if (i < decideSickDist.probabilities.size() - 1) {
                        sb.append(" +\n");
                    } else {
                        sb.append(";\n");
                    }
                }
                sb.append("\n");
            }
        }
        
        // Response completion - response state decrements each time step
        sb.append("  // ---- Response Progress ----\n");
        sb.append(String.format("  [sync] response_state > 0 & !(%s=%d & response_state=0) & !(%s=%d & response_state=0) -> (response_state' = response_state - 1);\n\n", 
            actionVar, selectActionTrigger, actionVar, decideActionTrigger));
        
        // Idle state - reset response type when done
        sb.append("  // ---- Idle State ----\n");
        sb.append(String.format("  [sync] response_state = 0 & response_type > 0 & %s != %d & %s != %d ->\n", 
            actionVar, selectActionTrigger, actionVar, decideActionTrigger));
        sb.append("    (response_state' = 0) & (response_type' = 0);\n\n");
        
        sb.append("  // ---- Default ----\n");
        sb.append(String.format("  [sync] response_state = 0 & response_type = 0 & %s != %d & %s != %d ->\n", 
            actionVar, decideActionTrigger, actionVar, selectActionTrigger));
        sb.append("    (response_state' = response_state) & (response_type' = response_type);\n");
        
        sb.append("endmodule\n");
        return sb.toString();
    }
    
    /**
     * Generate decision error tracking module using loaded error distributions
     * Models decision correctness based on sickness level
     */
    private String generateDecisionErrorModule() {
        if (config == null || config.getDecisionErrorDistributions().isEmpty()) {
            return ""; // No error distributions available
        }
        
        // Get dynamic variable names
        String actionVar = getActionVarName();
        String sickVar = getSickVarName();
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n// ---- Decision Error Modeling ----\n");
        sb.append("module decision_errors\n");
        sb.append("  decision_correct : [0..1] init 1;  // 1 = correct, 0 = error\n");
        sb.append("  error_count      : [0..10] init 0; // Track cumulative errors\n\n");
        
        // Get error distributions for different sickness levels
        PrismConfig.ErrorDistribution healthyDist = config.getDecisionErrorDistributions().get("sickness0");
        PrismConfig.ErrorDistribution sickDist = config.getDecisionErrorDistributions().get("sickness1");
        
        sb.append("  // ---- Decision Correctness Sampling ----\n");
        sb.append(String.format("  // Sample when deciding (%s=%d) and response completes\n\n", 
            actionVar, decideActionTrigger));
        
        if (healthyDist != null) {
            sb.append("  // Healthy agent decision correctness\n");
            sb.append(String.format("  [sync] %s=%d & response_state=1 & %s=0 ->\n", 
                actionVar, decideActionTrigger, sickVar));
            sb.append(String.format("    %.10f : (decision_correct'=1) +\n", healthyDist.correctProbability));
            sb.append(String.format("    %.10f : (decision_correct'=0) & (error_count'=min(error_count+1,10));\n\n",
                healthyDist.errorProbability));
        }
        
        if (sickDist != null) {
            sb.append("  // Sick agent decision correctness\n");
            sb.append(String.format("  [sync] %s=%d & response_state=1 & %s=1 ->\n", 
                actionVar, decideActionTrigger, sickVar));
            sb.append(String.format("    %.10f : (decision_correct'=1) +\n", sickDist.correctProbability));
            sb.append(String.format("    %.10f : (decision_correct'=0) & (error_count'=min(error_count+1,10));\n\n",
                sickDist.errorProbability));
        }
        
        sb.append("  // ---- Default State Maintenance ----\n");
        sb.append(String.format("  [sync] !(%s=%d & response_state=1) ->\n", actionVar, decideActionTrigger));
        sb.append("    (decision_correct' = decision_correct) & (error_count' = error_count);\n");
        
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
    private String generateActionModules(List<TransitionInfo> transitions) {
        StringBuilder sb = new StringBuilder();
        
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
                        System.err.println("DEBUG: Processing propose rule: " + proposeRuleName);
                        System.err.println("DEBUG: Guards found: " + proposeRule.guards);
                        // Extract FROM action from guards
                        for (String guard : proposeRule.guards) {
                            if (guard.contains("action")) {
                                System.err.println("DEBUG: Found action guard in " + proposeRuleName + ": " + guard);
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
                                            System.err.println("DEBUG: Could not parse action value: " + a);
                                        }
                                    }
                                    System.err.println("DEBUG: Extracted fromActions: " + info.fromActions);
                                } else if (guard.contains("=")) {
                                    // Single value like "action = 0"
                                    String[] parts = guard.split("=");
                                    if (parts.length > 1) {
                                        try {
                                            info.fromAction = Integer.parseInt(parts[1].trim());
                                            System.err.println("DEBUG: Extracted fromAction: " + info.fromAction);
                                        } catch (NumberFormatException e) {
                                            System.err.println("DEBUG: Could not parse action value: " + parts[1]);
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
        String actionVar = getActionVarName();
        String actionGuard = "";
        if (info.fromActions != null && !info.fromActions.isEmpty()) {
            // Multiple source actions (e.g., action=3 or action=2 for SS)
            List<String> actionParts = new ArrayList<>();
            for (int action : info.fromActions) {
                actionParts.add(actionVar + "=" + action);
            }
            actionGuard = "(" + String.join(" | ", actionParts) + ")";
        } else if (info.fromAction >= 0) {
            actionGuard = actionVar + "=" + info.fromAction;
        }
        
        // Start transition - always generate if we have action info
        if (!actionGuard.isEmpty()) {
            sb.append(String.format("  [sync] time_counter < TOTAL_TIME & %s & %s_done=0 & %s_ing=0 ->\n",
                actionGuard, moduleName, moduleName));
            sb.append(String.format("    (%s_ing' = 1);\n", moduleName));
            sb.append("\n");
        } else {
            // Fallback: generate with true guard (should not normally happen)
            System.err.println("Warning: No action guard found for " + moduleName);
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
                sb.append(String.format("    %.6f : (%s_done' = 1) & (%s_ing' = 0)\n",
                    pdfValue, moduleName, moduleName));
                sb.append(String.format("  + %.6f : (%s_ing' = 0);\n", 1.0 - pdfValue, moduleName));
                sb.append("\n");
            }
        } else {
            // Deterministic transition
            sb.append(String.format("  [sync] %s_ing=1 ->\n", moduleName));
            sb.append(String.format("    (%s_done' = 1) & (%s_ing' = 0);\n",
                moduleName, moduleName));
            sb.append("\n");
        }
        
        // Reset done flag
        sb.append(String.format("  [sync] %s_done=1 & !(%s_ing=1) -> (%s_done' = 0);\n", moduleName, moduleName, moduleName));
        sb.append("\n");
        
        // Else clause - no change when none of the above conditions hold
        StringBuilder elseGuard = new StringBuilder();
        elseGuard.append(String.format("!(%s_done=1 & !(%s_ing=1))", moduleName, moduleName));
        elseGuard.append(String.format(" & !(%s_ing=1)", moduleName));
        if (!actionGuard.isEmpty()) {
            elseGuard.append(String.format(" & !(time_counter < TOTAL_TIME & %s & %s_done=0 & %s_ing=0)",
                actionGuard, moduleName, moduleName));
        }
        
        sb.append(String.format("  [sync] %s ->\n", elseGuard.toString()));
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
     * Provides rewards for mission completion, decision quality, and time efficiency
     */
    private String generateRewards() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n// ---- Reward Structures ----\n");
        
        // Mission completion reward
        sb.append("rewards \"mission_completion\"\n");
        sb.append("  time_counter = TOTAL_TIME : 1;\n");
        sb.append("endrewards\n\n");
        
        // Decision quality reward (if error tracking is enabled)
        if (config != null && !config.getDecisionErrorDistributions().isEmpty()) {
            sb.append("rewards \"decision_quality\"\n");
            sb.append("  decision_correct = 1 : 1;\n");
            sb.append("  decision_correct = 0 : 0;\n");  // Changed from -1 to avoid PRISM negative reward error
            sb.append("endrewards\n\n");
            
            sb.append("rewards \"error_penalty\"\n");
            sb.append("  decision_correct = 0 : 10;\n");
            sb.append("endrewards\n\n");
        }
        
        // Time efficiency reward (penalize time spent)
        sb.append("rewards \"time_cost\"\n");
        sb.append("  time_counter < TOTAL_TIME : 1;\n");
        sb.append("endrewards\n\n");
        
        // Response time reward (if response tracking is enabled)
        if (config != null && !config.getResponseSelect().isEmpty()) {
            sb.append("rewards \"response_efficiency\"\n");
            sb.append("  response_state > 0 : 1;\n");
            sb.append("endrewards\n\n");
        }
        
        // Sickness penalty
        sb.append("rewards \"sickness_penalty\"\n");
        sb.append("  sick = 1 : 1;\n");
        sb.append("endrewards\n");
        
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
