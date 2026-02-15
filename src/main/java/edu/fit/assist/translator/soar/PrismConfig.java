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

    private String modelType = "dtmc";
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
    
    /**
     * Load configuration from JSON file exported from CyberSicknessModel
     */
    public static PrismConfig loadFromFile(String filePath) throws IOException {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(filePath)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            PrismConfig config = new PrismConfig();
            
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
            
            return config;
        }
    }
    
    // Getters
    public String getModelType() { return modelType; }
    public int getMaxErrorCount() { return maxErrorCount; }
    public double getModelResolution() { return modelResolution; }
    public int getExperimentDuration() { return experimentDuration; }
    public int getTotalTime() { return experimentDuration; } // Alias for compatibility
    public int getSicknessSamplingInterval() { return sicknessSamplingInterval; }
    public int getSicknessLevels() { return sicknessLevels; }
    public int getResponseDuration() { return responseDuration; }
    public String getTimeVariable() { return timeVariable; }
    public Map<String, Object> getConstants() { return constants; }
    public Map<String, Double> getSicknessProbabilityTable() { return sicknessProbabilityTable; }
    public Map<String, Distribution> getResponseSelect() { return responseSelect; }
    public Map<String, Distribution> getResponseDecide() { return responseDecide; }
    public Map<String, ErrorDistribution> getDecisionErrorDistributions() { return decisionErrorDistributions; }
    public List<ModuleConfig> getModules() { return modules; }
    
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
