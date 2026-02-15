package edu.fit.assist.translator.soar;

import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Utility methods to support the data-driven translation from Soar rules to PRISM code.
 */
public class TranslatorUtils {

    /**
     * Finds an apply rule corresponding to the given base name.
     *
     * For example, if baseName is "ssq-low-to-med", this looks for a rule whose name
     * is exactly "apply*ssq-low-to-med" inside the SoarRules.
     *
     * @param baseName  the operator base name (e.g., "ssq-low-to-med")
     * @param soarRules the SoarRules instance containing all rules
     * @return the matching apply rule, or null if no match is found.
     */
    public static Rule findApplyRuleFor(String baseName, SoarRules soarRules) {
        for (Rule rule : soarRules.rules) {
            if (rule.ruleName.equals("apply*" + baseName)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Extracts global constants from the apply*initialize rule.
     *
     * This method iterates through the rules in the given SoarRules object and,
     * when it finds a rule named "apply*initialize", returns a new copy of its valueMap.
     *
     * @param soarRules the SoarRules instance containing all rules
     * @return a LinkedHashMap of constants, or an empty map if none are found.
     */
    public static LinkedHashMap<String, String> extractGlobalConstants(SoarRules soarRules) {
        for (Rule rule : soarRules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                // Return a copy to avoid unintended modifications.
                return new LinkedHashMap<>(rule.valueMap);
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * Checks whether the provided text contains the given name or its dash/underscore variants.
     */
    public static boolean containsNameVariant(String text, String name) {
        if (text == null || name == null) return false;
        String altDash = name.replace('_', '-');
        String altUnderscore = name.replace('-', '_');
        return text.contains(name) || text.contains(altDash) || text.contains(altUnderscore);
    }

    /**
     * Extracts the probability expression (variable) from the given propose rule and its
     * matching apply rule. The extraction is done in several steps:
     *
     * 1. Check if any of the propose rule's guards contains an alias pattern for the operator.
     * 2. If not, search the apply rule's valueMap for a key that contains "prob" (ignoring any keys that contain "log").
     * 3. Finally, if no probability variable is found, fall back to checking the global constants.
     *
     * The result is converted into a valid PRISM variable by replacing hyphens with underscores
     * and prepending "state_" if necessary.
     *
     * @param proposeRule    the propose rule instance.
     * @param applyRule      the corresponding apply rule instance.
     * @param globalConstants a map containing global constant definitions.
     * @return a string representing the probability expression for the transition.
     */
    public static String extractProbabilityExpression(Rule proposeRule, Rule applyRule,
                                                      LinkedHashMap<String, String> globalConstants) {
        String probabilityExpr = null;

        // 1. Check alias pattern in propose rule's guards.
        String aliasValue = null;
//        System.out.println(proposeRule.contextMap);
        for (String guard : proposeRule.contextMap.values()) {

            if (guard.contains("state_log")) {
//                System.out.println(guard);
                String[] parts = guard.split("\\s+");
//                System.out.println(Arrays.toString(parts));
                // Check that we have at least three tokens and that the third token is an alias (e.g., "<something>")
                if (parts.length >= 3 && parts[1].contains("operator") && parts[2].startsWith("<")) {
                    aliasValue = parts[2];
                    break;
                } else if (parts.length == 1) {

                    aliasValue = parts[0];
                }

            }
        }
        if (aliasValue != null) {
            for (String guard : proposeRule.contextMap.values()) {
//                System.out.println(guard + ": " + aliasValue);
                if (guard.contains(aliasValue)) {
                    String[] parts = guard.trim().split("\\s+");
                    System.out.println(Arrays.toString(parts));
                    // Normalize the variable name: replace hyphens with underscores, and ensure it starts with "state_"
                    String lhs = parts[0].replace("-", "_").replace("_log","");
                    probabilityExpr = lhs.startsWith("state_") ? lhs : "state_" + lhs;
                    break;
                }
            }
        }

        // 2. If not found via alias, check the apply rule's valueMap for a key with "prob" (ignoring "log" keys).
        if (probabilityExpr == null) {
            String bestMatch = null;
            for (String key : applyRule.valueMap.keySet()) {
                String lowerKey = key.toLowerCase();
                if (lowerKey.contains("prob") && !lowerKey.contains("log")) {
                    bestMatch = key;
                    break;
                }
            }
            if (bestMatch != null) {
                probabilityExpr = "state_" + bestMatch.replace("state_", "").replace("-", "_");
            }
        }

        // 3. As a final fallback, check the global constants.
        if (probabilityExpr == null) {
            for (String key : globalConstants.keySet()) {
                if (key.contains("prob") && !key.contains("log")) {
                    probabilityExpr = "state_" + key.replace("state_", "").replace("-", "_");
                    break;
                }
            }
        }

        return probabilityExpr;
    }
}
