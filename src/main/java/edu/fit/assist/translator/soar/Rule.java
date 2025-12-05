package edu.fit.assist.translator.soar;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;

public class Rule {
    String ruleName;
    HashMap<String, String> contextMap; // Example: Maps <s> to state or <o> to state_operator
    ArrayList<String> variables; // Stores all variables used by this rule
    ArrayList<String> guards; // Stores all guards for this rule
    LinkedHashMap<String, Variable> variableMap;
    LinkedHashMap<String, String> valueMap; // Stores the value to apply in the right side of the rule. Maps Variable name to new value
    LinkedHashMap<Double, ArrayList<String>> groupedAssignments;
    boolean isLearningRule = false;
    double priority = 0.0;
    boolean isElaboration = false;
    LinkedHashMap<String, Double> valueProbs;

    // Holds the RHS (action) lines of the rule
    public ArrayList<String> rhsLines;

    public Rule(String name, LinkedHashMap<String, Variable> map) {
        this.ruleName = name;
        this.contextMap = new HashMap<>();
        this.variables = new ArrayList<>();
        this.guards = new ArrayList<>();
        this.variableMap = map;
        this.valueMap = new LinkedHashMap<>();
        this.valueProbs = new LinkedHashMap<>();
        this.groupedAssignments = new LinkedHashMap<>();
        this.rhsLines = new ArrayList<>();
    }

    public String formatGuard() {
        String output = "";
        boolean first = true;
        ArrayList<String> newGuards = new ArrayList<>();
        for (String guard : guards) {
            String variableName = guard.split(" ")[0];
            if (variableMap.get(variableName) == null) {
                continue;
            }
            int type = variableMap.get(variableName).varType;
            if (type == Variable.INT) {
                guard = guard.replaceAll(" != nil", "_exists = yes");
                guard = guard.replaceAll(" = nil", "_exists = no");
            } else if (type == Variable.FLOAT) {
                guard = guard.replaceAll(" != nil", "_exists = yes");
                guard = guard.replaceAll(" = nil", "_exists = no");
            }
            if (!first) {
                output += " & ";
            } else {
                first = false;
            }
            output += guard;
            newGuards.add(guard);
        }
        this.guards = newGuards;

        // Add negation of action side of elaborations
        if (this.isElaboration) {
            for (String var : valueMap.keySet()) {
                if (!first) {
                    output += " & ";
                } else {
                    first = false;
                }
                output += var + " != " + valueMap.get(var);
            }
        }
        return output;
    }

    public void addAttrValue(String var, String val) {
        valueMap.put(var, val);
    }

    public void addGuard(String guard) {
        guards.add(guard);
    }

    public void addVariable(String var) {
        variables.add(var);
    }

    public void addContext(String key, String value) {
        if (!key.startsWith("<")) {
            key = "<" + key + ">";
        }
        value = value.replace("-", "_"); // normalize to match PRISM
        contextMap.put(key, value);
    }

    public String getContext(String var) {
        if (!var.startsWith("<")) {
            var = "<" + var + ">";
        }
        return contextMap.get(var);
    }

    // Add RHS line
    public void addRHSLine(String line) {
        rhsLines.add(line);
    }

    public ArrayList<String> incrementAssignments = new ArrayList<>();

    public void addIncrementAssignment(String variable, String originalVar) {
        incrementAssignments.add(variable + "' = " + variable + " + 1");
    }
}
