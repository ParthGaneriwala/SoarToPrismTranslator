package edu.fit.assist.translator.soar;

import java.util.*;
import java.util.stream.Collectors;

import static edu.fit.assist.translator.soar.TranslatorUtils.*;

public class Translate {
    SoarRules rules;
    public Translate(SoarRules rules){
        this.rules = rules;
    }

    /**
     * Build a PRISM DTMC model for non time-based Soar rules by:
     * 1) declaring globals for constants and discovered state variables
     * 2) generating propose/apply transitions with sequential operator ids
     * 3) emitting operator mappings for traceability
     */
    String translateSoarToPrismGeneral() {
        StringBuilder output = new StringBuilder();

        LinkedHashMap<String, String> globalConstants = extractGlobalConstants(rules);

        output.append("dtmc\n\n");
        output.append("global phase : [0..1] init 0;      // 0 = propose, 1 = apply\n");

        Map<String, Integer> operatorIdsApply = new LinkedHashMap<>();
        for (Rule rule : rules.rules) {
            if (rule.ruleName.startsWith("apply*")) {
                collectAssignmentsFromApplyRule(rule);
            }
        }

        LinkedHashMap<String, Integer> sequentialOperatorIds = new LinkedHashMap<>();
        List<String> opMappings = new ArrayList<>();
        int operatorIdCounter = 0;

        sequentialOperatorIds.put("initialize", operatorIdCounter++);
        opMappings.add("0 = initialize");

        Set<String> declaredStateVars = variableInitMap.keySet();
        for (Map.Entry<String, String> entry : globalConstants.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            if (declaredStateVars.contains(key)) continue;

            String prismVar = toPrismVariable(key);

            if (val.equals("yes") || val.equals("no")) {
                output.append("global ").append(prismVar)
                        .append(" : bool init ")
                        .append(val.equals("yes") ? "true" : "false").append(";\n");
                continue;
            }

            if (val.equals("none") || !val.matches("^-?\\d+(\\.\\d+)?$")) {
                output.append("global ").append(prismVar)
                        .append(" : [0..2] init 0;\n");
                continue;
            }

            int maxVal;
            try {
                double d = Double.parseDouble(val);
                maxVal = (int) Math.ceil(d) + 1;
            } catch (Exception e) {
                maxVal = 5;
            }
            output.append("global ").append(prismVar)
                    .append(" : [0..").append(maxVal).append("] init 0;\n");
        }

        for (String key : variableValueMap.keySet()) {
            Set<Integer> values = variableValueMap.get(key);
            if (values.isEmpty()) continue;

            int min = 0;
            int max = Collections.max(values);
            int init = variableInitMap.getOrDefault(key, 0);

            String prismVar = toPrismVariable(key);
            output.append("global ").append(prismVar)
                    .append(" : [").append(min).append("..").append(max).append("] init ").append(init).append(";\n");
        }

        output.append("global state_operator_name : [0.." + (rules.rules.size() - 1) + "] init 0;\n\n");

        output.append("module user\n");
        for (Rule proposeRule : rules.rules) {
            if (!proposeRule.ruleName.startsWith("propose*")) continue;

            String baseNameForId = extractOperatorNameFromPropose(proposeRule);

            if (!sequentialOperatorIds.containsKey(baseNameForId)) {
                sequentialOperatorIds.put(baseNameForId, operatorIdCounter++);
                opMappings.add(sequentialOperatorIds.get(baseNameForId) + " = " + baseNameForId);
            }

            if (proposeRule.ruleName.equals("propose*initialize")) continue;

            String guard = generateGuard(proposeRule);
            guard = Arrays.stream(guard.split(" & "))
                    .filter(g -> !g.matches(".* = state_.*") && !g.matches("state_name = .*"))
                    .collect(Collectors.joining(" & "));
            if (guard == null || guard.isEmpty()) {
                System.out.println("Empty guard for rule: " + proposeRule.ruleName);
                continue;
            }
            if (guard.contains("state_superstate")) {
                System.out.println("Skipping superstate guard in: " + proposeRule.ruleName);
                continue;
            }

            int opId = sequentialOperatorIds.get(baseNameForId);
            output.append("    [] phase=0 & state_name=0 & ").append(guard)
                    .append(" -> 1.0 : (state_operator_name' = ").append(opId)
                    .append(") & (phase' = 1);\n");
        }

        output.append("\n// Apply transitions\n");
        for (Rule applyRule : rules.rules) {
            if (!applyRule.ruleName.startsWith("apply*")) continue;
            if (applyRule.ruleName.equals("apply*initialize")) continue;

            String baseName = applyRule.ruleName.substring("apply*".length()).trim();

            System.out.println("DEBUG: Processing apply rule: " + applyRule.ruleName);
            System.out.println("  mapped to baseName = " + baseName);

            Integer opId = sequentialOperatorIds.get(baseName);
            if (opId == null) {
                System.out.println("  NO MATCH FOR: " + baseName + " in " + sequentialOperatorIds.keySet());
                continue;
            }
            System.out.println("  matched ID = " + opId);

            List<String> assigns = new ArrayList<>();
            for (Map.Entry<String, String> entry : applyRule.valueMap.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();

                if (v.equals("yes")) {
                    assigns.add(toPrismVariable(k) + "' = true");
                } else if (v.equals("no")) {
                    assigns.add(toPrismVariable(k) + "' = false");
                } else if (v.matches("\\(\\+ 1 <.*>\\)")) {
                    String var = v.replace("(", "").replace(")", "").replace("+ 1 <", "").replace(">", "").trim();
                    assigns.add(toPrismVariable(var) + "' = " + toPrismVariable(var) + " + 1");
                }
            }
            assigns.add("phase' = 0");

            output.append("    [] phase=1 & state_operator_name=").append(opId)
                    .append(" -> 1.0 : ")
                    .append(String.join(" & ", assigns)).append(";\n");
        }

        output.append("endmodule\n\n");
        output.append("// Operator mappings: \n// ").append(String.join(", ", opMappings)).append("\n");
        return output.toString();
    }

    private String toPrismVariable(String key) {
        key = key.replace("-", "_");
        if (key.startsWith("state_")) return key;
        if (key.startsWith("state")) return "state_" + key.substring(5);
        return "state_" + key;
    }

    private String extractOperatorNameFromPropose(Rule rule) {
        System.out.println("DEBUG: Extracting operator name from propose rule: " + rule.ruleName);

        // Check valueMap for operator name entries (state_operator_name or similar)
        for (Map.Entry<String, String> entry : rule.valueMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.out.println("  Checking valueMap entry: " + key + " = " + value);

            // Look for operator name in valueMap entries
            if (key.contains("operator") && key.contains("name")) {
                String raw = value.replaceAll("[()<>]", "").trim();
                System.out.println("    Raw extracted operator name: '" + raw + "'");
                String[] tokens = raw.split("\\s+");
                if (tokens.length > 0) {
                    System.out.println("    Operator name: '" + tokens[0] + "'");
                    if (tokens[0].startsWith("apply-")) {
                        System.out.println("    -> Matched operator name: '" + tokens[0] + "'");
                        return tokens[0];
                    }
                }
            }
        }

        String fallback = rule.ruleName.substring("propose*".length());
        System.out.println("  Fallback to rule-derived name: " + fallback);
        return fallback;
    }

    private String generateGuard(Rule proposeRule) {
        List<String> conditions = new ArrayList<>();
        for (String cond : proposeRule.guards) {
            String plain = cond.replace("(", "").replace(")", "").trim();
            plain = plain.replace("^", "").replace("<s>", "").trim();
            String[] parts = plain.split(" ", 3);
            if (parts.length == 3) {
                String var = toPrismVariable(parts[0]);
                String op = parts[1];
                String val = parts[2];
                if (val.equals("yes")) val = "true";
                if (val.equals("no")) val = "false";
                conditions.add(var + " " + op + " " + val);
            } else {
                conditions.add(toPrismVariable(plain));
            }
        }
        return String.join(" & ", conditions);
    }

    private final Map<String, Set<Integer>> variableValueMap = new HashMap<>();
    private final Map<String, Integer> variableInitMap = new HashMap<>();

    void collectAssignmentsFromApplyRule(Rule applyRule) {
        for (Map.Entry<String, String> e : applyRule.valueMap.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (!val.matches("^-?\\d+$")) continue;
            int intVal = Integer.parseInt(val);
            variableValueMap.computeIfAbsent(key, k -> new TreeSet<>()).add(intVal);
            if (applyRule.ruleName.equals("apply*initialize")) {
                variableInitMap.put(key, intVal);
            }
        }
    }

    String generateStateDeclarations() {
        StringBuilder sb = new StringBuilder();
        for (String key : variableValueMap.keySet()) {
            Set<Integer> values = variableValueMap.get(key);
            if (values.isEmpty()) continue;
            int min = 0;
            int max = Collections.max(values);
            int init = variableInitMap.getOrDefault(key, 0);
            String prismVar = toPrismVariable(key);
            sb.append("    ").append(prismVar)
                    .append(": [").append(min).append("..").append(max).append("] init ").append(init).append(";\n");
        }
        return sb.toString();
    }
}
