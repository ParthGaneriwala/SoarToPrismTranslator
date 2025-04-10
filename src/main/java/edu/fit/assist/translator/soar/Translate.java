package edu.fit.assist.translator.soar;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.fit.assist.translator.soar.TranslatorUtils.*;

public class Translate {
    SoarRules rules;
    public Translate(SoarRules rules){
        this.rules = rules;
    }
    private String translateSoarToPrismGeneral() {
        StringBuilder output = new StringBuilder();

        // Extract global constants from the apply*initialize rule.
        LinkedHashMap<String, String> globalConstants = extractGlobalConstants(rules);

        // Emit dtmc header and constants (automatically converted).
        output.append("dtmc\n\n");
        for (Map.Entry<String, String> entry : globalConstants.entrySet()) {
            String prismVar = toPrismVariable(entry.getKey());
            String val = entry.getValue();
            // Distinguish between integers and doubles
            if (val.matches("^-?\\d+$")) {
                output.append("const integer ").append(prismVar).append(" = ").append(val).append(";\n");
            } else {
                output.append("const double ").append(prismVar).append(" = ").append(val).append(";\n");
            }
        }
        output.append("\n");

        // Process propose rules, find matching apply rules, and group outcomes by guard.
        Map<String, List<MergedTransition>> transitionsByGuard = new LinkedHashMap<>();
        for (Rule proposeRule : rules.rules) {
            if (!proposeRule.ruleName.startsWith("propose*")) continue;

            // Extract the base operator name dynamically.
            String baseName = proposeRule.ruleName.substring("propose*".length());
            Rule applyRule = findApplyRuleFor(baseName, rules);
            if (applyRule == null) continue; // no matching rule

            // Derive the probability expression generically.
            String probabilityExpr = extractProbabilityExpression(proposeRule, applyRule, globalConstants);

            // Generate the next-state assignment string by iterating over all assignments.
            // This is a generic routine that processes every key in applyRule.valueMap.
            String assignmentString = generateAssignmentString(applyRule.valueMap);

            // Generate a generic guard from the propose rule.
            String guard = generateGuard(proposeRule);

            // Store this outcome under the extracted guard.
            transitionsByGuard
                    .computeIfAbsent(guard, k -> new ArrayList<>())
                    .add(new MergedTransition(probabilityExpr, assignmentString));
        }

        // Construct the PRISM module using the grouped transitions.
        output.append("module user\n");

        // For each guard, merge outcomes:
        for (Map.Entry<String, List<MergedTransition>> entry : transitionsByGuard.entrySet()) {
            String guard = entry.getKey();
            List<MergedTransition> outcomes = entry.getValue();

            output.append("    [] ").append(guard).append(" -> ");
            for (int i = 0; i < outcomes.size(); i++) {
                MergedTransition outcome = outcomes.get(i);
                output.append(outcome.probabilityExpr)
                        .append(": ").append(outcome.assignmentString);
                if (i < outcomes.size() - 1) {
                    output.append(" + ");
                }
            }
            output.append(";\n");
        }
        output.append("endmodule\n");

        return output.toString();
    }

    // Converts any Soar key to a valid Prism variable name.
    // For example, ^low-to-med-prob might become state_low_to_med_prob.
    private String toPrismVariable(String key) {
        String clean = key.replace("-", "_");
        return clean.startsWith("state_") ? clean : "state_" + clean;
    }

    // Given a valueMap (from an apply rule), generate an assignment string
    // that assigns each state variable. For example:
    // { "ssq": "1", "last-transition": "ssq-low-to-med" }  →  "(state_ssq' = 1) & (state_last_transition' = ssq_low_to_med)"
    private String generateAssignmentString(Map<String, String> valueMap) {
        List<String> assignments = new ArrayList<>();
        for (Map.Entry<String, String> entry : valueMap.entrySet()) {
            // Filter out keys that are metadata, such as operator names.
            if (entry.getKey().contains("operator") || entry.getKey().contains("name"))
                continue;
            String prismVar = toPrismVariable(entry.getKey());
            assignments.add("(" + prismVar + "' = " + entry.getValue() + ")");
        }
        return assignments.isEmpty() ? "" : String.join(" & ", assignments);
    }

    // Generic guard generator that converts propose rule conditions to a PRISM guard.
    // For example, converting (state <s> ^gender 1) to 'state_gender = 1'.
    private String generateGuard(Rule proposeRule) {
        List<String> conditions = new ArrayList<>();
        for (String cond : proposeRule.guards) {
            // Remove extra symbols and reformat; this implementation should be extended
            // to properly handle all Soar syntactic variants.
            String plain = cond.replace("(", "").replace(")", "").trim();
            plain = plain.replace("^", "state_").replace("<s>", "").trim();
            conditions.add(plain);
        }
        return String.join(" & ", conditions);
    }
}
