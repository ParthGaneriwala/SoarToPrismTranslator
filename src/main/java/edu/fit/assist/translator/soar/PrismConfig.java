package edu.fit.assist.translator.soar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Loads and manages PRISM model configuration from external JSON file.
 * This configuration is typically exported from a CyberSicknessModel C# class
 * and contains probability distributions, model parameters, and module definitions.
 */
public class PrismConfig {
    public static final String DEFAULT_TIME_VARIABLE = "time-counter";
    public static final String DEFAULT_TOTAL_TIME_KEY = "total-time";

    private String modelType = "dtmc";
    private double timeScale = 1.0;
    private String timeUnits = "steps";
    private int maxErrorCount = 5;
    private double modelResolution = 1.0;
    private int experimentDuration = 1200;
    private int sicknessSamplingInterval = 300;
    private int sicknessLevels = 2;
    private int responseDuration = 60;
    private String timeVariable = DEFAULT_TIME_VARIABLE;
    
    private Map<String, Object> constants = new LinkedHashMap<>();
    private Map<String, Double> sicknessProbabilityTable = new LinkedHashMap<>();
    private Map<String, Distribution> responseSelect = new LinkedHashMap<>();
    private Map<String, Distribution> responseDecide = new LinkedHashMap<>();
    private Map<String, ErrorDistribution> decisionErrorDistributions = new LinkedHashMap<>();
    private List<ModuleConfig> modules = new ArrayList<>();
    private Map<String, ClockConfig> clocks = new LinkedHashMap<>();
    private Map<String, OperatorConfig> operators = new LinkedHashMap<>();
    private Map<String, StateConfig> states = new LinkedHashMap<>();
    private PtaSemanticsConfig ptaSemantics = new PtaSemanticsConfig();
    
    public static class Distribution {
        public String type;
        public int states;
        public List<StateProb> probabilities = new ArrayList<>();
        
        public static class StateProb {
            public int state;
            public double probability;
        }
    }
    
    public static class ErrorDistribution {
        public double correctProbability;
        public double errorProbability;
    }
    
    public static class ModuleConfig {
        public String name;
        public String type;
        public List<String> soarRulePatterns = new ArrayList<>();
        public List<VariableConfig> variables = new ArrayList<>();
        
        public boolean matchesRule(String ruleName) {
            if (soarRulePatterns.isEmpty()) {
                return false;
            }
            for (String pattern : soarRulePatterns) {
                if (ruleName.matches(pattern)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    public static class VariableConfig {
        public String name;
        public String type;
        public String range;
        public String init;
    }

    public static class ClockConfig {
        public String name;
        public Double minDuration;
        public Double maxDuration;
        public Double lowerBound;
        public Double upperBound;
        public Double bound;
        public String units;
        public String label;
    }

    public static class OperatorConfig {
        public String name;
        public Double minTime;
        public Double maxTime;
        public Boolean resetsClock;
        public Double probability;
        public List<String> soarRulePatterns = new ArrayList<>();
        public List<String> resetClocks = new ArrayList<>();
    }

    public static class StateConfig {
        public String name;
        public String variable;
        public String clock;
        public Double timeBound;
        public Double lowerBound;
        public Double upperBound;
    }

    public static class PtaSemanticsConfig {
        public boolean useInitPhase = true;
        public String initPhaseVar = "init_phase";
        public String initializeLabel = "initialize";
        public String examineLabelPrefix = "examine";
        public String deadlineLabel = "deadline_reached";
        public String missionCompleteUntaggedLabel = "mission_complete_untagged";

        public String deadlineConstName = "deadline";
        public String useBoxConstName = "use_box";
        public String boxAccuracyConstName = "box_accuracy";

        public String attackerIdVar = "state_io_scenario_attacker_id";
        public String currentPersonIdVar = "state_current_person_id";
        public String wallClockVar = "state_wall_clock";

        public String missRewriteFrom = "state_io_scenario_attacker_id < state_current_person_id";
        public String missRewriteTo = "state_io_scenario_attacker_id != state_current_person_id";
        public boolean applyMissRewrite = true;
        public boolean runtimeAttackerSampling = true;
    }
    
    /**
     * Load configuration from JSON file exported from CyberSicknessModel
     */
    public static PrismConfig loadFromFile(String filePath) throws IOException {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(filePath)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            PrismConfig config = new PrismConfig();

            if (root.has("model_type")) {
                config.modelType = root.get("model_type").getAsString();
            }
            
            // Load model settings (from CyberSicknessModel properties)
            if (root.has("model")) {
                JsonObject model = root.getAsJsonObject("model");
                if (model.has("type")) {
                    config.modelType = model.get("type").getAsString();
                }
                if (model.has("maxErrorCount")) {
                    config.maxErrorCount = model.get("maxErrorCount").getAsInt();
                }
                if (model.has("modelResolution")) {
                    config.modelResolution = model.get("modelResolution").getAsDouble();
                }
                if (model.has("experimentDuration")) {
                    config.experimentDuration = model.get("experimentDuration").getAsInt();
                }
                if (model.has("sicknessSamplingInterval")) {
                    config.sicknessSamplingInterval = model.get("sicknessSamplingInterval").getAsInt();
                }
                if (model.has("sicknessLevels")) {
                    config.sicknessLevels = model.get("sicknessLevels").getAsInt();
                }
                if (model.has("responseDuration")) {
                    config.responseDuration = model.get("responseDuration").getAsInt();
                }
                if (model.has("timeVariable")) {
                    config.timeVariable = model.get("timeVariable").getAsString();
                }
            }

            if (root.has("time_scale")) {
                config.timeScale = root.get("time_scale").getAsDouble();
            }
            if (root.has("time_units")) {
                config.timeUnits = root.get("time_units").getAsString();
            }
            
            // Load constants
            if (root.has("constants")) {
                JsonObject constants = root.getAsJsonObject("constants");
                for (String key : constants.keySet()) {
                    JsonElement value = constants.get(key);
                    if (value.isJsonPrimitive()) {
                        if (value.getAsJsonPrimitive().isNumber()) {
                            try {
                                config.constants.put(key, value.getAsDouble());
                            } catch (Exception e) {
                                config.constants.put(key, value.getAsInt());
                            }
                        } else {
                            config.constants.put(key, value.getAsString());
                        }
                    }
                }
            }
            
            // Load sickness probability table (3D: time, currentLevel, nextLevel -> probability)
            if (root.has("sicknessProbabilityTable")) {
                JsonObject probTable = root.getAsJsonObject("sicknessProbabilityTable");
                for (String key : probTable.keySet()) {
                    config.sicknessProbabilityTable.put(key, probTable.get(key).getAsDouble());
                }
            }
            
            // Load response select distributions
            if (root.has("responseSelect")) {
                JsonObject responseSelect = root.getAsJsonObject("responseSelect");
                for (String key : responseSelect.keySet()) {
                    JsonObject distObj = responseSelect.getAsJsonObject(key);
                    Distribution dist = new Distribution();
                    dist.type = distObj.get("type").getAsString();
                    dist.states = distObj.get("states").getAsInt();
                    
                    if (distObj.has("probabilities")) {
                        JsonArray probs = distObj.getAsJsonArray("probabilities");
                        for (JsonElement probElem : probs) {
                            JsonObject probObj = probElem.getAsJsonObject();
                            Distribution.StateProb sp = new Distribution.StateProb();
                            sp.state = probObj.get("state").getAsInt();
                            sp.probability = probObj.get("probability").getAsDouble();
                            dist.probabilities.add(sp);
                        }
                    }
                    
                    config.responseSelect.put(key, dist);
                }
            }
            
            // Load response decide distributions
            if (root.has("responseDecide")) {
                JsonObject responseDecide = root.getAsJsonObject("responseDecide");
                for (String key : responseDecide.keySet()) {
                    JsonObject distObj = responseDecide.getAsJsonObject(key);
                    Distribution dist = new Distribution();
                    dist.type = distObj.get("type").getAsString();
                    dist.states = distObj.get("states").getAsInt();
                    
                    if (distObj.has("probabilities")) {
                        JsonArray probs = distObj.getAsJsonArray("probabilities");
                        for (JsonElement probElem : probs) {
                            JsonObject probObj = probElem.getAsJsonObject();
                            Distribution.StateProb sp = new Distribution.StateProb();
                            sp.state = probObj.get("state").getAsInt();
                            sp.probability = probObj.get("probability").getAsDouble();
                            dist.probabilities.add(sp);
                        }
                    }
                    
                    config.responseDecide.put(key, dist);
                }
            }
            
            // Load decision error distributions
            if (root.has("decisionErrorDistributions")) {
                JsonObject errorDists = root.getAsJsonObject("decisionErrorDistributions");
                for (String key : errorDists.keySet()) {
                    JsonObject errObj = errorDists.getAsJsonObject(key);
                    ErrorDistribution ed = new ErrorDistribution();
                    ed.correctProbability = errObj.get("correctProbability").getAsDouble();
                    ed.errorProbability = errObj.get("errorProbability").getAsDouble();
                    config.decisionErrorDistributions.put(key, ed);
                }
            }
            
            // Load modules
            if (root.has("modules")) {
                JsonArray modulesArray = root.getAsJsonArray("modules");
                for (JsonElement moduleElement : modulesArray) {
                    JsonObject moduleObj = moduleElement.getAsJsonObject();
                    ModuleConfig module = new ModuleConfig();
                    
                    module.name = moduleObj.get("name").getAsString();
                    module.type = moduleObj.get("type").getAsString();
                    
                    // Load Soar rule patterns
                    if (moduleObj.has("soarRulePatterns")) {
                        JsonArray patterns = moduleObj.getAsJsonArray("soarRulePatterns");
                        for (JsonElement pattern : patterns) {
                            module.soarRulePatterns.add(pattern.getAsString());
                        }
                    }
                    
                    // Load variables
                    if (moduleObj.has("variables")) {
                        JsonArray variables = moduleObj.getAsJsonArray("variables");
                        for (JsonElement varElement : variables) {
                            JsonObject varObj = varElement.getAsJsonObject();
                            VariableConfig var = new VariableConfig();
                            var.name = varObj.get("name").getAsString();
                            var.type = varObj.get("type").getAsString();
                            var.range = varObj.get("range").getAsString();
                            var.init = varObj.get("init").getAsString();
                            module.variables.add(var);
                        }
                    }
                    
                    config.modules.add(module);
                }
            }

                                if (root.has("clocks")) {
                                    JsonObject clocksObj = root.getAsJsonObject("clocks");
                                    for (String key : clocksObj.keySet()) {
                                        JsonObject clockObj = clocksObj.getAsJsonObject(key);
                                        ClockConfig clock = new ClockConfig();
                                        clock.name = key;
                                        if (clockObj.has("min_duration")) {
                                            clock.minDuration = clockObj.get("min_duration").getAsDouble();
                                        }
                                        if (clockObj.has("max_duration")) {
                                            clock.maxDuration = clockObj.get("max_duration").getAsDouble();
                                        }
                                        if (clockObj.has("lower_bound")) {
                                            clock.lowerBound = clockObj.get("lower_bound").getAsDouble();
                                        }
                                        if (clockObj.has("upper_bound")) {
                                            clock.upperBound = clockObj.get("upper_bound").getAsDouble();
                                        }
                                        if (clockObj.has("bound")) {
                                            clock.bound = clockObj.get("bound").getAsDouble();
                                        }
                                        if (clockObj.has("units")) {
                                            clock.units = clockObj.get("units").getAsString();
                                        }
                                        if (clockObj.has("label")) {
                                            clock.label = clockObj.get("label").getAsString();
                                        }
                                        config.clocks.put(key, clock);
                                    }
                                }

                                if (root.has("operators")) {
                                    JsonObject operatorsObj = root.getAsJsonObject("operators");
                                    for (String key : operatorsObj.keySet()) {
                                        JsonObject operatorObj = operatorsObj.getAsJsonObject(key);
                                        OperatorConfig operator = new OperatorConfig();
                                        operator.name = key;
                                        if (operatorObj.has("min_time")) {
                                            operator.minTime = operatorObj.get("min_time").getAsDouble();
                                        }
                                        if (operatorObj.has("max_time")) {
                                            operator.maxTime = operatorObj.get("max_time").getAsDouble();
                                        }
                                        if (operatorObj.has("resets_clock")) {
                                            operator.resetsClock = operatorObj.get("resets_clock").getAsBoolean();
                                        }
                                        if (operatorObj.has("probability")) {
                                            operator.probability = operatorObj.get("probability").getAsDouble();
                                        }
                                        if (operatorObj.has("soarRulePatterns")) {
                                            JsonArray patterns = operatorObj.getAsJsonArray("soarRulePatterns");
                                            for (JsonElement pattern : patterns) {
                                                operator.soarRulePatterns.add(pattern.getAsString());
                                            }
                                        }
                                        if (operatorObj.has("rule_patterns")) {
                                            JsonArray patterns = operatorObj.getAsJsonArray("rule_patterns");
                                            for (JsonElement pattern : patterns) {
                                                operator.soarRulePatterns.add(pattern.getAsString());
                                            }
                                        }
                                        if (operatorObj.has("reset_clocks")) {
                                            JsonArray resetClocks = operatorObj.getAsJsonArray("reset_clocks");
                                            for (JsonElement clockName : resetClocks) {
                                                operator.resetClocks.add(clockName.getAsString());
                                            }
                                        }
                                        config.operators.put(key, operator);
                                    }
                                }

                                if (root.has("states")) {
                                    JsonObject statesObj = root.getAsJsonObject("states");
                                    for (String key : statesObj.keySet()) {
                                        JsonObject stateObj = statesObj.getAsJsonObject(key);
                                        StateConfig state = new StateConfig();
                                        state.name = key;
                                        if (stateObj.has("variable")) {
                                            state.variable = stateObj.get("variable").getAsString();
                                        }
                                        if (stateObj.has("clock")) {
                                            state.clock = stateObj.get("clock").getAsString();
                                        }
                                        if (stateObj.has("time_bound")) {
                                            state.timeBound = stateObj.get("time_bound").getAsDouble();
                                        }
                                        if (stateObj.has("lower_bound")) {
                                            state.lowerBound = stateObj.get("lower_bound").getAsDouble();
                                        }
                                        if (stateObj.has("upper_bound")) {
                                            state.upperBound = stateObj.get("upper_bound").getAsDouble();
                                        }
                                        config.states.put(key, state);
                                    }
                                }

                                if (root.has("pta_semantics")) {
                                    JsonObject ptaObj = root.getAsJsonObject("pta_semantics");
                                    PtaSemanticsConfig semantics = new PtaSemanticsConfig();
                                    if (ptaObj.has("use_init_phase")) {
                                        semantics.useInitPhase = ptaObj.get("use_init_phase").getAsBoolean();
                                    }
                                    if (ptaObj.has("init_phase_var")) {
                                        semantics.initPhaseVar = ptaObj.get("init_phase_var").getAsString();
                                    }
                                    if (ptaObj.has("initialize_label")) {
                                        semantics.initializeLabel = ptaObj.get("initialize_label").getAsString();
                                    }
                                    if (ptaObj.has("examine_label_prefix")) {
                                        semantics.examineLabelPrefix = ptaObj.get("examine_label_prefix").getAsString();
                                    }
                                    if (ptaObj.has("deadline_label")) {
                                        semantics.deadlineLabel = ptaObj.get("deadline_label").getAsString();
                                    }
                                    if (ptaObj.has("mission_complete_untagged_label")) {
                                        semantics.missionCompleteUntaggedLabel = ptaObj.get("mission_complete_untagged_label").getAsString();
                                    }

                                    if (ptaObj.has("deadline_const")) {
                                        semantics.deadlineConstName = ptaObj.get("deadline_const").getAsString();
                                    }
                                    if (ptaObj.has("use_box_const")) {
                                        semantics.useBoxConstName = ptaObj.get("use_box_const").getAsString();
                                    }
                                    if (ptaObj.has("box_accuracy_const")) {
                                        semantics.boxAccuracyConstName = ptaObj.get("box_accuracy_const").getAsString();
                                    }

                                    if (ptaObj.has("attacker_id_var")) {
                                        semantics.attackerIdVar = ptaObj.get("attacker_id_var").getAsString();
                                    }
                                    if (ptaObj.has("current_person_id_var")) {
                                        semantics.currentPersonIdVar = ptaObj.get("current_person_id_var").getAsString();
                                    }
                                    if (ptaObj.has("wall_clock_var")) {
                                        semantics.wallClockVar = ptaObj.get("wall_clock_var").getAsString();
                                    }

                                    if (ptaObj.has("miss_rewrite_from")) {
                                        semantics.missRewriteFrom = ptaObj.get("miss_rewrite_from").getAsString();
                                    }
                                    if (ptaObj.has("miss_rewrite_to")) {
                                        semantics.missRewriteTo = ptaObj.get("miss_rewrite_to").getAsString();
                                    }
                                    if (ptaObj.has("apply_miss_rewrite")) {
                                        semantics.applyMissRewrite = ptaObj.get("apply_miss_rewrite").getAsBoolean();
                                    }
                                    if (ptaObj.has("runtime_attacker_sampling")) {
                                        semantics.runtimeAttackerSampling = ptaObj.get("runtime_attacker_sampling").getAsBoolean();
                                    }
                                    config.ptaSemantics = semantics;
                                }
            
            return config;
        }
    }
    
    // Getters
    public String getModelType() { return modelType; }
    public boolean isPtaModel() { return "pta".equalsIgnoreCase(modelType); }
    public double getTimeScale() { return timeScale; }
    public String getTimeUnits() { return timeUnits; }
    public int getMaxErrorCount() { return maxErrorCount; }
    public double getModelResolution() { return modelResolution; }
    public int getExperimentDuration() { return experimentDuration; }
    public int getTotalTime() { return experimentDuration; } // Alias for compatibility
    public int getSicknessSamplingInterval() { return sicknessSamplingInterval; }
    public int getSicknessLevels() { return sicknessLevels; }
    public int getResponseDuration() { return responseDuration; }
    public String getTimeVariable() { return timeVariable; }
    public String getEffectiveTimeVariable() {
        return (timeVariable != null && !timeVariable.isEmpty()) ? timeVariable : DEFAULT_TIME_VARIABLE;
    }
    public Map<String, Object> getConstants() { return constants; }
    public Map<String, Double> getSicknessProbabilityTable() { return sicknessProbabilityTable; }
    public Map<String, Distribution> getResponseSelect() { return responseSelect; }
    public Map<String, Distribution> getResponseDecide() { return responseDecide; }
    public Map<String, ErrorDistribution> getDecisionErrorDistributions() { return decisionErrorDistributions; }
    public List<ModuleConfig> getModules() { return modules; }
    public Map<String, ClockConfig> getClocks() { return clocks; }
    public Map<String, OperatorConfig> getOperators() { return operators; }
    public Map<String, StateConfig> getStates() { return states; }
    public PtaSemanticsConfig getPtaSemantics() { return ptaSemantics; }

    public boolean hasPtaConfiguration() {
        return isPtaModel() || !clocks.isEmpty() || !operators.isEmpty() || !states.isEmpty();
    }
    
    public ModuleConfig getModuleForRule(String ruleName) {
        for (ModuleConfig module : modules) {
            if (module.matchesRule(ruleName)) {
                return module;
            }
        }
        return null;
    }
    
    /**
     * Get sickness transition probability for (time, currentLevel, nextLevel)
     */
    public double getSicknessProbability(int time, int currentLevel, int nextLevel) {
        String key = time + "," + currentLevel + "," + nextLevel;
        return sicknessProbabilityTable.getOrDefault(key, 0.0);
    }
    
    /**
     * Get time windows based on sampling interval
     */
    public List<Integer> getTimeWindows() {
        List<Integer> windows = new ArrayList<>();
        for (int t = 0; t <= experimentDuration; t += sicknessSamplingInterval) {
            windows.add(t);
        }
        return windows;
    }
    
    /**
     * Get commit times (one before next window)
     */
    public List<Integer> getCommitTimes() {
        List<Integer> commits = new ArrayList<>();
        for (int t = 0; t < experimentDuration; t += sicknessSamplingInterval) {
            commits.add(t + sicknessSamplingInterval - 1);
        }
        return commits;
    }
}
