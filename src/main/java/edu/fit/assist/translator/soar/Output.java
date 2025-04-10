package edu.fit.assist.translator.soar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Output {
    SoarRules rules;
    public Output(SoarRules rules){
        this.rules = rules;
    }
    public String generateOutput(){
        String output = "\ndtmc\n\n";
        rules.parseVariableValuePass();
        output += generateVariableDeclarations();
        output += generateModules();  // <--- this already calls generateMergedTransitions + OtherRules + wraps in module
        return output;
    }


    private String generateOtherRules() {
        StringBuilder output = new StringBuilder();

        for (Rule rule : rules.rules) {
            if (rule.ruleName.startsWith("propose*") || rule.ruleName.startsWith("apply*")) continue;

            String guard = rule.formatGuard();
            if (guard.contains("state_name = nil") || guard.contains("state_superstate")) continue;

            String rhs = rule.formatRHS();
            output.append("    [] ").append(guard).append(" -> ").append(rhs).append(";\n");
        }

        return output.toString();
    }


    private double getProbFromVariable(String varName) {
        // First, sanitize the name (if it's already sanitized elsewhere, skip this step)
        String sanitized = varName.replace("state_", "").replace("-", "_");

        Variable var = rules.variables.get(sanitized);

        if (var == null || var.values == null || var.values.isEmpty()) {
            System.err.println("// Warning: Variable " + varName + " not found or has no values.");
            return 1.0; // Fallback to safe value
        }

        try {
            // Assume first value is the probability
            return Double.parseDouble(var.values.get(0));
        } catch (NumberFormatException e) {
            System.err.println("// Error: Cannot parse probability from variable " + varName);
            return 1.0;
        }
    }
    private String generateMergedTransitions() {
        StringBuilder output = new StringBuilder();
        Pattern aliasPattern = Pattern.compile("<.*?>");

        // Cache variables initialized in apply*initialize
        LinkedHashMap<String, String> initAssignments = new LinkedHashMap<>();
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                initAssignments.putAll(rule.valueMap);
            }
        }

        for (Rule proposeRule : rules.rules) {
            if (!proposeRule.ruleName.startsWith("propose*")) continue;
            String baseName = proposeRule.ruleName.substring("propose*".length());

            Rule applyRule = rules.rules.stream()
                    .filter(r -> r.ruleName.equals("apply*" + baseName))
                    .findFirst().orElse(null);
            if (applyRule == null) continue;

            System.out.println("Looking to merge: propose*" + baseName + " + apply*" + baseName);
            System.out.println("    Propose Guards: " + proposeRule.guards);
            System.out.println("    Propose ValueMap: " + proposeRule.valueMap);
            System.out.println("    Apply ValueMap: " + applyRule.valueMap);

            String probVar = null;

            // Check alias pattern (^operator = <alias>)
            String aliasValue = null;
            for (String guard : proposeRule.guards) {
                if (guard.contains("^operator") && guard.contains("=")) {
                    String[] parts = guard.split("\\s+");
                    if (parts.length == 3 && parts[1].contains("operator") && parts[2].startsWith("<")) {
                        aliasValue = parts[2];
                        break;
                    }
                }
            }

            if (aliasValue != null) {
                for (String guard : proposeRule.guards) {
                    if (guard.contains(aliasValue)) {
                        String[] parts = guard.trim().split("\\s+");
                        if (parts.length == 3) {
                            String lhs = parts[0].replace("-", "_");
                            if (lhs.startsWith("state_")) {
                                probVar = lhs;
                            } else {
                                probVar = "state_" + lhs;
                            }
                            break;
                        }
                    }
                }
            }

            // If not found, check apply valueMap directly
            if (probVar == null) {
                String bestMatch = null;
                for (String var : applyRule.valueMap.keySet()) {
                    String clean = var.toLowerCase();
                    if (clean.contains("prob") && !clean.contains("log")) {
                        bestMatch = var;
                        break;
                    } else if (clean.contains("log") && bestMatch == null) {
                        bestMatch = var;
                    }
                }
                if (bestMatch != null) {
                    probVar = "state_" + bestMatch.replace("state_", "").replace("-", "_");
                }
            }

            // STEP 3: Fallback to values in initialize
            if (probVar == null) {
                for (String key : initAssignments.keySet()) {
                    if (key.contains("prob") && !key.contains("log")) {
                        probVar = "state_" + key.replace("state_", "").replace("-", "_");
                        break;
                    }
                }
            }

            if (probVar == null) {
                System.out.println("No probability found in guards or aliases for propose*" + baseName);
                continue;
            }

            String ssqVal = applyRule.valueMap.get("state_ssq");
            if (ssqVal == null) {
                System.out.println("No state_ssq in apply*" + baseName);
                continue;
            }

            String fallbackVal = getComplementValue(ssqVal);
            String guard = proposeRule.formatGuard();

            output.append("    [] ").append(guard).append(" -> ")
                    .append(probVar).append(": (state_ssq'=").append(ssqVal).append(") + ")
                    .append("state_stay_low_prob: (state_ssq'=").append(fallbackVal).append(");\n");

            System.out.println(" Merged: [] " + guard + " -> " +
                    probVar + ": (state_ssq'=" + ssqVal + ") + state_stay_low_prob: (state_ssq'=" + fallbackVal + ");");
        }

        return output.toString();
    }


    private String getComplementValue(String val) {
        if (val.equals("1")) return "0";
        if (val.equals("0")) return "1";
        if (val.equals("true")) return "false";
        if (val.equals("false")) return "true";
        if (val.equals("yes")) return "no";
        if (val.equals("no")) return "yes";
        return val + "_alt"; // Fallback
    }



    private String generateModules() {
        StringBuilder output = new StringBuilder();

        output.append("module user\n");

        String merged = generateMergedTransitions();
        if (!merged.isBlank()) {
            output.append(merged);
        }

        String other = generateOtherRules();
        if (!other.isBlank()) {
            output.append(other);
        }

        output.append("endmodule\n\n");
        return output.toString();
    }


    private String sanitizeName(String name) {
        return name.replace("-", "_").replace("'", "_prime").replace("^", "");
    }




    private StringBuilder generateVariableDeclarations() {
        StringBuilder output = new StringBuilder();


        // Generate types for all variables :D
        for(Variable var : rules.variables.values()){
            var.generateType();
            rules.mapNameToType.put(var.name, var.varType);
        }


        for(String startNode : rules.mapNameToType.keySet()){
            if(rules.mapNameToType.get(startNode) == Variable.INVALID){
                continue;
            }
            String node = startNode;
            int startNodeType = rules.mapNameToType.get(startNode);
            ArrayList<String> queue = new ArrayList<String>();
            queue.add(node);
            while(queue.size()>0){
                String currentNode = queue.remove(0);
                if(!rules.typeGraph.containsKey(currentNode)){
                    continue;
                }
                for(String child : rules.typeGraph.get(currentNode)){
                    if(!rules.mapNameToType.containsKey(child)){
                        continue;
                    }
                    if(rules.mapNameToType.get(child) == Variable.INVALID){
                        rules.mapNameToType.put(child,startNodeType);
                        queue.add(child);
                    }
                }
            }
        }

        for(String varName : rules.variables.keySet()){
            Variable var = rules.variables.get(varName);
            var.varType = rules.mapNameToType.get(var.name);
            if(var.varType == Variable.INVALID){
                var.varType = Variable.S_CONST;
            }
            rules.variables.put(varName,var);
        }

        for(Variable var : rules.variables.values()){
            //var.generateType();
            if(var.varType== Variable.INVALID){
                var.varType = Variable.S_CONST;
            }


            if(var.varType == Variable.S_CONST){
            }else if(var.varType == Variable.INT){
                output.append("const integer ");
                List<Integer> filtered = var.values.stream()
                        .filter(s -> !s.equalsIgnoreCase("nil"))
                        .map(Integer::parseInt)
                        .distinct()
                        .sorted()
                        .toList();

                if (filtered.size() > 1) {
                    int min = filtered.get(0);
                    int max = filtered.get(filtered.size() - 1);
                    int init = filtered.get(0);
                    output.append(var.name.replace("-", "_")).append(": [")
                            .append(min).append("..").append(max)
                            .append("] init ").append(init).append(";\n");
                } else if (filtered.size() == 1) {
                    output.append(sanitizeName(var.name)).append(" = ").append(filtered.get(0)).append(";\n");
                } else {
                    System.err.println("// No valid values found for " + (var.name) + "\n");
                }

            }else if(var.varType == Variable.FLOAT){
                output.append("const double ");
                output.append(sanitizeName(var.name)).append(" = ").append(var.values.get(0)).append("\n");
            }else{
                output.append("TYPE ERROR");
                System.err.println("TYPE ERROR with variable "+var.name);
            }

        }
        return new StringBuilder(output + "\n\n");
    }
//        return output;
    }

