package edu.fit.assist.translator.soar;

import java.util.*;
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
        output += generateModules();
        return output;
    }


    private String generateMergedTransitions() {
        StringBuilder output = new StringBuilder();

        // Cache variables initialized in apply*initialize
        LinkedHashMap<String, String> initAssignments = new LinkedHashMap<>();
        Set<String> matched = null;
        Set<String> matchedProposeVals = new LinkedHashSet<>();
        Set<String> matchedApplyGuards = new LinkedHashSet<>();
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                initAssignments.putAll(rule.valueMap);
            }
        }

        for (Rule proposeRule : rules.rules) {
            if (!proposeRule.ruleName.startsWith("propose*")) continue;
            String baseName = proposeRule.ruleName.substring("propose*".length());
            List<Rule> matchingApplyRules = rules.rules.stream()
                    .filter(r -> r.ruleName.equals("apply*" + baseName))
                    .toList();


            if (proposeRule.guards.stream().anyMatch(guard -> guard.contains("state_superstate"))) {
                continue;
            }
            for (Rule applyRule : matchingApplyRules) {
                System.out.println("Looking to merge: propose*" + baseName + " + apply*" + baseName);
                System.out.println("    Propose Guards: " + proposeRule.guards);
                System.out.println("    Propose ValueMap: " + proposeRule.valueMap);
                System.out.println("    Apply Guards: " + applyRule.guards);
                System.out.println("    Apply ValueMap: " + applyRule.valueMap);


                for (String proposeVal : proposeRule.valueMap.values()) {
                    for (String applyGuard : applyRule.guards) {
                        if (applyGuard.contains(proposeVal)) {
                            matchedProposeVals.add(proposeVal);
                            matchedApplyGuards.add(applyGuard);
                        }
                    }
                }

                System.out.println("Matched propose values (" + matchedProposeVals.size() + "): "
                        + matchedProposeVals);
                System.out.println("Matched apply guards  (" + matchedApplyGuards.size() + "): "
                        + matchedApplyGuards);

                System.out.print("Apply ValueMap for this rule:");
                for (Map.Entry<String, String> e : applyRule.valueMap.entrySet()) {
                    System.out.println("  " + e.getKey() + " = " + e.getValue());
                }


                String probVar = null;
                // Check alias pattern (^operator = <alias>)
                String aliasValue = null;
                for (String guard : proposeRule.guards) {
                    if (guard.contains("^operator") && guard.contains("=")) {
                        String[] parts = guard.split("\\s+");
                        System.out.println(Arrays.toString(parts));
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

                // Fallback to values in initialize
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
                for (String proposeVal : proposeRule.valueMap.values()) {
                    for (String applyGuard : applyRule.guards) {
                        if (applyGuard.contains(proposeVal)) {
                            matchedProposeVals.add(proposeVal);
                            break;
                        }
                    }
                }

                if (matchedProposeVals.isEmpty()) continue;

                System.out.println("Matched propose values (" + matchedProposeVals.size() + "): " + matchedProposeVals);

                // 🔍 Dynamically determine target variable from applyRule.valueMap
                if (applyRule.valueMap.size() != 1) {
                    System.out.println("Skipping apply*" + baseName + " because it modifies multiple or no variables.");
                    continue;
                }

                Map.Entry<String, String> entry = applyRule.valueMap.entrySet().iterator().next();
                String targetVar = entry.getKey();
                String targetVal = entry.getValue();
                String fallbackVal = targetVal;

                // Build guard and transition
                String guard = proposeRule.formatGuard();

                output.append("    [] ").append(guard).append(" -> ")
                        .append(probVar).append(": (").append(targetVar).append("'=").append(targetVal).append(") \n" );
//                + "+ ")
//                        .append("state_stay_low_prob: (").append(targetVar).append("'=").append(fallbackVal).append(");\n");

                System.out.println(" Merged: [] " + guard + " -> " +
                        probVar + ": (" + targetVar + "'=" + targetVal + ") + state_stay_low_prob: (" + targetVar + "'=" + fallbackVal + ");");
            }
        }
        return output.toString();
    }


    private String getComplementValue(String val) {
        return switch (val) {
            case "1" -> "0";
            case "0" -> "1";
            case "true" -> "false";
            case "false" -> "true";
            case "yes" -> "no";
            case "no" -> "yes";
            default -> val + "_alt";
        };
    }



    private String generateModules() {
        StringBuilder output = new StringBuilder();

        output.append("module user\n");

        String merged = generateMergedTransitions();
        if (!merged.isBlank()) {
            output.append(merged);
        }

//        String other = generateOtherRules();
//        if (!other.isBlank()) {
//            output.append(other);
//        }

        output.append("endmodule\n\n");
        return output.toString();
    }


    private String sanitizeName(String name) {
        return name.replace("-", "_").replace("'", "_prime").replace("^", "");
    }




    private StringBuilder generateVariableDeclarations() {
        StringBuilder output = new StringBuilder();


        // Generate types for all variables
        for(Variable var : rules.variables.values()){
            var.generateType();
            rules.mapNameToType.put(var.name, var.varType);
        }


        for(String startNode : rules.mapNameToType.keySet()){
            if(rules.mapNameToType.get(startNode) == Variable.INVALID){
                continue;
            }
            int startNodeType = rules.mapNameToType.get(startNode);
            ArrayList<String> queue = new ArrayList<String>();
            queue.add(startNode);
            while(!queue.isEmpty()){
                String currentNode = queue.removeFirst();
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

