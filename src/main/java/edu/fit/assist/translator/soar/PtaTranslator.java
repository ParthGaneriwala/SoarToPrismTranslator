package edu.fit.assist.translator.soar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.fit.assist.translator.soar.TranslatorUtils.extractGlobalConstants;

/**
 * Generic PTA translator that derives a PRISM PTA model from Soar productions
 * and optional PTA configuration metadata.
 *
 * The translator deliberately avoids scenario-specific names. It infers a
 * primary symbolic location variable, encodes symbolic values when needed, and
 * uses Soar operator names and rule bodies to build guarded PTA transitions.
 */
public class PtaTranslator {
    private static final Pattern RULE_KEY_PATTERN = Pattern.compile("^[^*]+\\*(.+)$");
    private static final Pattern SIMPLE_GUARD_PATTERN = Pattern.compile("^([^\\s]+)\\s*(=|!=|<=|>=|<|>)\\s*(.+)$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");

    private final SoarRules rules;
    private final PrismConfig config;
    private final Map<String, VariableInfo> variables = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, Integer>> symbolicEncoding = new LinkedHashMap<>();
    private final Map<String, Rule> proposeRulesByLabel = new LinkedHashMap<>();
    private final Map<String, Rule> applyRulesByLabel = new LinkedHashMap<>();
    private final Map<String, Integer> operatorIds = new LinkedHashMap<>();  // operator name -> numeric ID
    private String locationVariable;
    private int nextOperatorId = 0;

    private LinkedHashMap<String, String> sourceConstantCache;

    private LinkedHashMap<String, String> sourceConstants() {
        if (sourceConstantCache == null) {
            sourceConstantCache = extractGlobalConstants(rules);
        }
        return sourceConstantCache;
    }

    private String semanticsInitPhaseVar() {
        return resolveSemanticName("init_phase", config != null ? config.getPtaSemantics().initPhaseVar : null);
    }

    private String semanticsInitializeLabel() {
        return sanitizeLabel(resolveSemanticName("initialize", config != null ? config.getPtaSemantics().initializeLabel : null));
    }

    private String semanticsExaminePrefix() {
        return sanitizeLabel(resolveSemanticName("examine", config != null ? config.getPtaSemantics().examineLabelPrefix : null));
    }

    private String semanticsDeadlineLabel() {
        return sanitizeLabel(resolveSemanticName("deadline_reached", config != null ? config.getPtaSemantics().deadlineLabel : null));
    }

    private String semanticsMissionCompleteUntaggedLabel() {
        return sanitizeLabel(resolveSemanticName("mission_complete_untagged", config != null ? config.getPtaSemantics().missionCompleteUntaggedLabel : null));
    }

    private String semanticsDeadlineConst() {
        return resolveSemanticName("deadline", config != null ? config.getPtaSemantics().deadlineConstName : null);
    }

    private String semanticsUseBoxConst() {
        return resolveSemanticName("use_box", config != null ? config.getPtaSemantics().useBoxConstName : null);
    }

    private String semanticsBoxAccuracyConst() {
        return resolveSemanticName("box_accuracy", config != null ? config.getPtaSemantics().boxAccuracyConstName : null);
    }

    private String semanticsAttackerIdVar() {
        return resolveSemanticName("state_io_scenario_attacker_id", config != null ? config.getPtaSemantics().attackerIdVar : null);
    }

    private String semanticsCurrentPersonIdVar() {
        return resolveSemanticName("state_current_person_id", config != null ? config.getPtaSemantics().currentPersonIdVar : null);
    }

    private String semanticsWallClockVar() {
        return resolveSemanticName("state_wall_clock", config != null ? config.getPtaSemantics().wallClockVar : null);
    }

    private String resolveSemanticName(String preferredSoarName, String configFallback) {
        String sourceMatch = findMatchingSourceConstant(preferredSoarName);
        if (sourceMatch != null) {
            return sourceMatch;
        }
        if (configFallback != null && !configFallback.isEmpty()) {
            return normalizeIdentifier(configFallback);
        }
        return normalizeIdentifier(preferredSoarName);
    }

    private String findMatchingSourceConstant(String preferredSoarName) {
        String normalizedPreferred = normalizeIdentifier(preferredSoarName);
        if (normalizedPreferred == null || normalizedPreferred.isEmpty()) {
            return null;
        }

        if (sourceConstants().containsKey(normalizedPreferred)) {
            return normalizedPreferred;
        }

        for (String key : sourceConstants().keySet()) {
            if (normalizeIdentifier(key).equals(normalizedPreferred)) {
                return normalizeIdentifier(key);
            }
        }

        if (variables.containsKey(normalizedPreferred)) {
            return normalizedPreferred;
        }

        return null;
    }

    private boolean semanticsUseInitPhase() {
        return config == null || config.getPtaSemantics().useInitPhase;
    }

    private boolean semanticsApplyMissRewrite() {
        return config != null && config.getPtaSemantics().applyMissRewrite;
    }

    private boolean semanticsRuntimeAttackerSampling() {
        return config == null || config.getPtaSemantics().runtimeAttackerSampling;
    }

    private static class VariableInfo {
        final String name;
        final LinkedHashSet<String> rawValues = new LinkedHashSet<>();
        Double minNumeric;
        Double maxNumeric;
        String initRaw;

        VariableInfo(String name) {
            this.name = normalizeIdentifier(name);
        }

        boolean isSymbolic() {
            return !rawValues.isEmpty() && symbolicCount() > 0;
        }

        int symbolicCount() {
            int count = 0;
            for (String value : rawValues) {
                if (!isNumericValue(value)) {
                    count++;
                }
            }
            return count;
        }
    }

    public PtaTranslator(SoarRules rules, PrismConfig config) {
        this.rules = rules;
        this.config = config;
        extractRuleLabels();
        extractVariables();
        locationVariable = detectLocationVariable();
    }

    public String translateToPta() {
        StringBuilder output = new StringBuilder();
        output.append("pta\n\n");
        output.append(generateConstants());
        output.append(generateModule());
        return output.toString();
    }

    private void extractRuleLabels() {
        for (Rule rule : rules.rules) {
            if (rule.ruleName.startsWith("propose*")) {
                String label = inferRuleLabel(rule, "propose*");
                proposeRulesByLabel.putIfAbsent(label, rule);
            } else if (rule.ruleName.startsWith("apply*")) {
                String label = inferRuleLabel(rule, "apply*");
                applyRulesByLabel.put(label, rule);
            }
        }
    }

    private void extractVariables() {
        Rule initializeRule = null;
        for (Rule rule : rules.rules) {
            if (rule.ruleName.equals("apply*initialize")) {
                initializeRule = rule;
                break;
            }
        }

        if (initializeRule != null) {
            for (Map.Entry<String, String> entry : initializeRule.valueMap.entrySet()) {
                String variableName = normalizeIdentifier(entry.getKey());
                String value = entry.getValue();
                if (isIgnoredKey(variableName)) {
                    continue;
                }
                VariableInfo info = variables.computeIfAbsent(variableName, VariableInfo::new);
                info.initRaw = value;
                addObservedValue(info, value, true);
            }
        }

        for (Rule rule : rules.rules) {
            for (String guard : rule.guards) {
                observeGuard(guard);
            }
            for (Map.Entry<String, String> entry : rule.valueMap.entrySet()) {
                String variableName = normalizeIdentifier(entry.getKey());
                if (isIgnoredKey(variableName)) {
                    continue;
                }
                VariableInfo info = variables.computeIfAbsent(variableName, VariableInfo::new);
                addObservedValue(info, entry.getValue(), false);
            }
        }
    }

    private void observeGuard(String guard) {
        String[] parts = guard.split(" & ");
        for (String part : parts) {
            Matcher matcher = SIMPLE_GUARD_PATTERN.matcher(part.trim());
            if (!matcher.matches()) {
                continue;
            }
            String variableName = normalizeIdentifier(matcher.group(1));
            if (isIgnoredKey(variableName)) {
                continue;
            }
            String value = matcher.group(3).trim();
            if (value.startsWith("<") && value.endsWith(">")) {
                continue;
            }
            VariableInfo info = variables.computeIfAbsent(variableName, VariableInfo::new);
            addObservedValue(info, value, false);
            if (NUMERIC_PATTERN.matcher(value).matches()) {
                updateNumericRange(info, value);
            }
        }
    }

    private void addObservedValue(VariableInfo info, String rawValue, boolean fromInit) {
        if (rawValue == null) {
            return;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return;
        }
        info.rawValues.add(value);
        if (fromInit && info.initRaw == null) {
            info.initRaw = value;
        }
        if (NUMERIC_PATTERN.matcher(value).matches()) {
            updateNumericRange(info, value);
        }
    }

    private void updateNumericRange(VariableInfo info, String rawValue) {
        try {
            double numericValue = Double.parseDouble(rawValue) * configScale();
            if (info.minNumeric == null || numericValue < info.minNumeric) {
                info.minNumeric = numericValue;
            }
            if (info.maxNumeric == null || numericValue > info.maxNumeric) {
                info.maxNumeric = numericValue;
            }
        } catch (NumberFormatException ignored) {
            // Keep symbolic handling for values that cannot be parsed reliably.
        }
    }

    private String generateConstants() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.ROOT, "// PTA generated from Soar rules using time scale %.3f%n", configScale()));
        builder.append(String.format(Locale.ROOT, "const double time_scale = %.3f;%n", configScale()));
        if (config != null && config.getTimeUnits() != null && !config.getTimeUnits().isEmpty()) {
            builder.append(String.format(Locale.ROOT, "// time units: %s%n", config.getTimeUnits()));
        }

        LinkedHashMap<String, String> constants = new LinkedHashMap<>(sourceConstants());
        if (config != null && config.getConstants() != null) {
            for (Map.Entry<String, Object> entry : config.getConstants().entrySet()) {
                constants.putIfAbsent(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        Set<String> declaredConstants = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : constants.entrySet()) {
            String key = normalizeIdentifier(entry.getKey());
            if (isIgnoredKey(key) || variables.containsKey(key)) {
                continue;
            }
            String value = entry.getValue();
            if (isNumericValue(value)) {
                builder.append(String.format(Locale.ROOT, "const double %s = %s;%n", key, value));
                declaredConstants.add(key);
            }
        }

        // Runtime inputs passed by Soar host code should be experiment constants in PRISM.
        String deadlineConst = semanticsDeadlineConst();
        String useBoxConst = semanticsUseBoxConst();
        String boxAccuracyConst = semanticsBoxAccuracyConst();
        if (!declaredConstants.contains(deadlineConst)) {
            builder.append("const int ").append(deadlineConst).append(";\n");
        }
        if (!declaredConstants.contains(useBoxConst)) {
            builder.append("const int ").append(useBoxConst).append(";\n");
        }
        if (!declaredConstants.contains(boxAccuracyConst)) {
            builder.append("const double ").append(boxAccuracyConst).append(";\n");
        }

        builder.append('\n');
        return builder.toString();
    }

    private String generateModule() {
        StringBuilder builder = new StringBuilder();

        Map<String, List<VariableInfo>> grouped = groupVariablesByDomain();
        List<String> order = List.of("user", "tagged", "attacker", "deadline_clock", "environment", "context");

        for (String moduleName : order) {
            List<VariableInfo> moduleVars = grouped.get(moduleName);
            if (moduleVars == null || moduleVars.isEmpty()) {
                continue;
            }
            builder.append(generateDomainModule(moduleName, moduleVars));
            builder.append("\n");
        }

        return builder.toString();
    }

    private Map<String, List<VariableInfo>> groupVariablesByDomain() {
        Map<String, List<VariableInfo>> grouped = new LinkedHashMap<>();
        grouped.put("user", new ArrayList<>());
        grouped.put("tagged", new ArrayList<>());
        grouped.put("attacker", new ArrayList<>());
        grouped.put("deadline_clock", new ArrayList<>());
        grouped.put("environment", new ArrayList<>());
        grouped.put("context", new ArrayList<>());

        for (VariableInfo info : variables.values()) {
            grouped.get(moduleForVariable(info.name)).add(info);
        }
        return grouped;
    }

    private String moduleForVariable(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (key.contains("tagged") || key.contains("attacker")) {
            return "tagged";
        }
        if (key.contains("deadline") || key.contains("wall_clock") || key.contains("satisfy_deadline")) {
            return "deadline_clock";
        }
        if (key.contains("current_person") || key.contains("start_mission") || key.contains("action") || key.equals("state_name")) {
            return "user";
        }
        if (key.contains("env") || key.contains("crowd") || key.contains("scenario") || key.contains("io_")) {
            return "environment";
        }
        if (key.contains("operator") || key.contains("select")) {
            return "attacker";
        }
        return "context";
    }

    private String generateDomainModule(String moduleName, List<VariableInfo> moduleVars) {
        StringBuilder builder = new StringBuilder();
        builder.append("module ").append(moduleName).append("\n");

        if ("context".equals(moduleName) && semanticsUseInitPhase()) {
            builder.append("    ").append(semanticsInitPhaseVar()).append(" : [0..1] init 0;\n");
        }

        for (VariableInfo info : moduleVars) {
            builder.append("    ").append(info.name).append(" : ");
            builder.append(generateDeclaration(info)).append(";\n");
        }

        for (Map.Entry<String, PrismConfig.ClockConfig> entry : clocksByName().entrySet()) {
            String clockName = normalizeIdentifier(entry.getKey());
            if (moduleOwnsClock(moduleName, clockName)) {
                builder.append("    ").append(clockName).append(" : clock;\n");
            }
        }

        if (!moduleVars.isEmpty() || hasClockInModule(moduleName)) {
            builder.append('\n');
        }

        if ("deadline_clock".equals(moduleName)) {
            boolean hasDeadlineCounter = moduleVars.stream().anyMatch(v -> "state_wall_clock".equals(v.name));
            if (hasDeadlineCounter) {
                builder.append("    invariant\n");
                builder.append("    (wall_clock <= ").append(semanticsDeadlineConst()).append(")\n");
                builder.append("    endinvariant\n\n");
            }
        }

        for (Map.Entry<String, Rule> entry : applyRulesByLabel.entrySet()) {
            String label = entry.getKey();
            Rule applyRule = entry.getValue();
            String transition = generateModuleTransition(label, applyRule, moduleVars, moduleName);
            if (!transition.isEmpty()) {
                builder.append(transition).append('\n');
            }
        }

        builder.append("endmodule\n");
        return builder.toString();
    }

    private boolean moduleOwnsClock(String moduleName, String clockName) {
        if ("deadline_clock".equals(moduleName) && clockName.contains("wall")) {
            return true;
        }
        if ("attacker".equals(moduleName) && clockName.contains("init")) {
            return true;
        }
        if ("user".equals(moduleName) && (clockName.contains("t1") || clockName.contains("reaction"))) {
            return true;
        }
        return "context".equals(moduleName) && !clockName.contains("wall") && !clockName.contains("init") && !clockName.contains("t1");
    }

    private boolean hasClockInModule(String moduleName) {
        for (String clock : clocksByName().keySet()) {
            if (moduleOwnsClock(moduleName, normalizeIdentifier(clock))) {
                return true;
            }
        }
        return false;
    }

    private String generateModuleTransition(String label, Rule applyRule, List<VariableInfo> moduleVars, String moduleName) {
        StringBuilder builder = new StringBuilder();
        String transitionLabel = sanitizeLabel(label);

        Set<String> owned = new LinkedHashSet<>();
        for (VariableInfo info : moduleVars) {
            owned.add(info.name);
        }

        StringBuilder guard = new StringBuilder();
        boolean hasGuard = false;
        if (!applyRule.guards.isEmpty()) {
            for (String guardClause : applyRule.guards) {
                String translated = translateGuard(guardClause);
                if (translated != null && !translated.isEmpty()) {
                    if (hasGuard) {
                        guard.append(" & ");
                    }
                    guard.append(translated);
                    hasGuard = true;
                }
            }
        }

        // Allow initialize transitions only before mission starts to avoid reset loops.
        if (semanticsUseInitPhase()) {
            if (semanticsInitializeLabel().equals(transitionLabel)) {
                if (hasGuard) {
                    guard.append(" & ");
                }
                guard.append("state_start_mission = 0 & ").append(semanticsInitPhaseVar()).append(" = 0");
                hasGuard = true;
            } else {
                // All operational transitions require init phase completion.
                if (hasGuard) {
                    guard.append(" & ");
                }
                guard.append(semanticsInitPhaseVar()).append(" = 1");
                hasGuard = true;
            }
        }

        // Deadline gating for examination actions (mirrors simplified PTA discipline).
        String wallClockVar = semanticsWallClockVar();
        String deadlineConst = semanticsDeadlineConst();
        if (transitionLabel.startsWith(semanticsExaminePrefix()) && variables.containsKey(wallClockVar)) {
            if (hasGuard) {
                guard.append(" & ");
            }
            guard.append(wallClockVar).append(" < ").append(deadlineConst);
            hasGuard = true;
        }

        // Only allow deadline terminal action when the deadline has actually been reached.
        if (semanticsDeadlineLabel().equals(transitionLabel) && variables.containsKey(wallClockVar)) {
            if (hasGuard) {
                guard.append(" & ");
            }
            guard.append(wallClockVar).append(" >= ").append(deadlineConst);
            hasGuard = true;
        }

        // Only allow untagged completion after all people have been examined.
        String currentPersonVar = semanticsCurrentPersonIdVar();
        if (semanticsMissionCompleteUntaggedLabel().equals(transitionLabel) && variables.containsKey(currentPersonVar)) {
            if (hasGuard) {
                guard.append(" & ");
            }
            guard.append(currentPersonVar).append(" >= ").append(configuredNumPersons());
            hasGuard = true;
        }

        // Preserve Soar miss semantics: attacker-id not equal to current person.
        if (transitionLabel.contains("miss") && semanticsApplyMissRewrite()) {
            String guardText = guard.toString();
            String wrong = config != null ? config.getPtaSemantics().missRewriteFrom : null;
            String replacement = config != null ? config.getPtaSemantics().missRewriteTo : null;
            if (wrong != null && replacement != null && guardText.contains(wrong)) {
                guard = new StringBuilder(guardText.replace(wrong, replacement));
                hasGuard = guard.length() > 0;
            }
        }

        

        StringBuilder update = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : applyRule.valueMap.entrySet()) {
            String varName = normalizeIdentifier(entry.getKey());
            String value = entry.getValue();
            if (!isIgnoredKey(varName) && owned.contains(varName) && variables.containsKey(varName)) {
                String assignment = translateAssignment(varName, value);
                if (assignment == null || assignment.isEmpty()) {
                    continue;
                }
                if (!first) {
                    update.append(" & ");
                }
                update.append(assignment);
                first = false;
            }
        }

        if ("context".equals(moduleName) && semanticsUseInitPhase() && semanticsInitializeLabel().equals(transitionLabel)) {
            if (!first) {
                update.append(" & ");
            }
            update.append("(").append(semanticsInitPhaseVar()).append("' = 1)");
            first = false;
        }

        for (String clockName : clocksByName().keySet()) {
            String normalized = normalizeIdentifier(clockName);
            if (moduleOwnsClock(moduleName, normalized) && shouldResetClock(transitionLabel, normalized)) {
                if (!first) {
                    update.append(" & ");
                }
                update.append("(").append(normalized).append("' = 0)");
                first = false;
            }
        }

        if (first) {
            return "";
        }

        // Runtime input semantics from host code: scenario attacker id is sampled by box_accuracy.
        String attackerIdVar = semanticsAttackerIdVar();
        if (semanticsRuntimeAttackerSampling()
                && "tagged".equals(moduleName)
                && semanticsInitializeLabel().equals(transitionLabel)
                && owned.contains(attackerIdVar)) {
            String common = update.toString();
            int people = Math.max(2, configuredNumPersons());
            String branchTrue = common + " & (" + attackerIdVar + "' = 0)";
            builder.append("    [").append(transitionLabel).append("] ");
            builder.append(hasGuard ? guard.toString() : "true");
            builder.append(" -> ").append(semanticsBoxAccuracyConst()).append(" : ").append(branchTrue);
            String otherProbability = stripTrailingZeros(1.0 / (people - 1));
            for (int attackerId = 1; attackerId < people; attackerId++) {
                String branch = common + " & (" + attackerIdVar + "' = " + attackerId + ")";
                builder.append(" + (1-").append(semanticsBoxAccuracyConst()).append(")*").append(otherProbability).append(" : ").append(branch);
            }
            builder.append(";");
            return builder.toString();
        }

        builder.append("    [").append(transitionLabel).append("] ");
        builder.append(hasGuard ? guard.toString() : "true");
        builder.append(" -> 1.0 : ").append(update).append(";");
        return builder.toString();
    }

    private boolean shouldResetClock(String transitionLabel, String clockName) {
        if (config == null || config.getOperators().isEmpty()) {
            return false;
        }
        for (PrismConfig.OperatorConfig operator : config.getOperators().values()) {
            String operatorName = sanitizeLabel(operator.name);
            if (!operatorName.equals(transitionLabel)) {
                continue;
            }
            if (Boolean.TRUE.equals(operator.resetsClock)) {
                return true;
            }
            for (String configuredClock : operator.resetClocks) {
                if (normalizeIdentifier(configuredClock).equals(clockName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String generateDeclaration(VariableInfo info) {
        if (semanticsAttackerIdVar().equals(info.name)) {
            int people = Math.max(2, configuredNumPersons());
            return String.format(Locale.ROOT, "[0..%d] init 0", people - 1);
        }

        if (semanticsCurrentPersonIdVar().equals(info.name) || "state_io_current_person_id".equals(info.name)) {
            int people = Math.max(2, configuredNumPersons());
            return String.format(Locale.ROOT, "[0..%d] init 0", people);
        }

        if (semanticsWallClockVar().equals(info.name)) {
            return String.format(Locale.ROOT, "[0..%d] init 0", configuredDeadlineUpperBound());
        }

        if ("state_current_person_boxed".equals(info.name) || "state_io_current_person_boxed".equals(info.name)) {
            int min = info.minNumeric != null ? (int) Math.floor(info.minNumeric) : 0;
            int max = info.maxNumeric != null ? (int) Math.ceil(info.maxNumeric) : Math.max(min, 2);
            if (max == min) {
                max = min + 1;
            }
            return String.format(Locale.ROOT, "[%d..%d] init %s", min, max, semanticsUseBoxConst());
        }

        if (info.isSymbolic()) {
            LinkedHashMap<String, Integer> encoding = symbolicEncoding.computeIfAbsent(info.name, key -> buildEncoding(info.rawValues));
            String init = encodeValue(info.name, info.initRaw, encoding);
            if (init == null || init.isEmpty()) {
                init = "0";
            }
            int upper = Math.max(1, encoding.size() - 1);
            return String.format(Locale.ROOT, "[%d..%d] init %s", 0, upper, init);
        }

        int min = info.minNumeric != null ? (int) Math.floor(info.minNumeric) : 0;
        int max = info.maxNumeric != null ? (int) Math.ceil(info.maxNumeric) : Math.max(min, 1);
        if (info.initRaw != null && NUMERIC_PATTERN.matcher(info.initRaw.trim()).matches()) {
            int init = scaledInteger(info.initRaw);
            min = Math.min(min, init);
            max = Math.max(max, init);
            if (max == min) {
                max = min + 1;
            }
            return String.format(Locale.ROOT, "[%d..%d] init %d", min, max, init);
        }
        if (max == min) {
            max = min + 1;
        }
        return String.format(Locale.ROOT, "[%d..%d] init %d", min, max, min);
    }

    private String generateInvariants() {
        if (config == null || config.getStates().isEmpty() || locationVariable == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        List<String> invariants = new ArrayList<>();
        for (PrismConfig.StateConfig stateConfig : config.getStates().values()) {
            String boundClock = resolveClockName(stateConfig);
            String stateVariable = resolveStateVariable(stateConfig);
            if (boundClock == null || stateVariable == null) {
                continue;
            }
            String encodedState = encodeStateValue(stateVariable, stateConfig.name);
            if (encodedState == null) {
                continue;
            }
            List<String> clauses = new ArrayList<>();
            clauses.add(String.format(Locale.ROOT, "%s = %s", normalizeIdentifier(stateVariable), encodedState));
            if (stateConfig.timeBound != null) {
                clauses.add(String.format(Locale.ROOT, "%s <= %s", normalizeIdentifier(boundClock), stripTrailingZeros(stateConfig.timeBound * configScale())));
            }
            if (stateConfig.lowerBound != null) {
                clauses.add(String.format(Locale.ROOT, "%s >= %s", normalizeIdentifier(boundClock), stripTrailingZeros(stateConfig.lowerBound * configScale())));
            }
            if (!clauses.isEmpty()) {
                String consequence = clauses.size() > 1 ? String.join(" & ", clauses.subList(1, clauses.size())) : "true";
                invariants.add(String.format(Locale.ROOT, "    (%s) => %s", clauses.get(0), consequence));
            }
        }

        if (invariants.isEmpty()) {
            return "";
        }

        builder.append("    invariant\n");
        for (int i = 0; i < invariants.size(); i++) {
            builder.append(invariants.get(i)).append(i < invariants.size() - 1 ? " &\n" : "\n");
        }
        builder.append("    endinvariant\n");
        return builder.toString();
    }

    private String generateTransition(String label, Rule applyRule) {
        StringBuilder builder = new StringBuilder();
        String transitionLabel = sanitizeLabel(label);
        List<String> guards = new ArrayList<>();
        for (String guard : applyRule.guards) {
            String translated = translateGuard(guard);
            if (translated != null && !translated.isEmpty()) {
                guards.add(translated);
            }
        }

        List<String> assignments = new ArrayList<>();
        for (Map.Entry<String, String> entry : applyRule.valueMap.entrySet()) {
            String key = normalizeIdentifier(entry.getKey());
            if (isIgnoredKey(key) || key.endsWith("_operator_name")) {
                continue;
            }
            String translated = translateAssignment(key, entry.getValue());
            if (translated != null && !translated.isEmpty()) {
                assignments.add(translated);
            }
        }

        applyClockResets(transitionLabel, assignments);
        if (assignments.isEmpty()) {
            assignments.add("true");
        }

        String probabilityExpression = null;
        Rule proposeRule = proposeRulesByLabel.get(label);
        if (proposeRule != null) {
            probabilityExpression = TranslatorUtils.extractProbabilityExpression(proposeRule, applyRule, extractGlobalConstants(rules));
        }
        if (probabilityExpression == null) {
            probabilityExpression = "1.0";
        }

        builder.append(String.format(Locale.ROOT, "    [%s] ", transitionLabel));
        if (!guards.isEmpty()) {
            builder.append(String.join(" & ", guards)).append(" -> ");
        } else {
            builder.append("true -> ");
        }
        builder.append(probabilityExpression).append(" : ");
        builder.append(String.join(" & ", assignments)).append(";\n");
        return builder.toString();
    }

    private void applyClockResets(String label, List<String> assignments) {
        if (config == null || config.getOperators().isEmpty()) {
            return;
        }

        for (PrismConfig.OperatorConfig operator : config.getOperators().values()) {
            String operatorName = sanitizeLabel(operator.name);
            if (!operatorName.equals(label)) {
                continue;
            }
            if (Boolean.TRUE.equals(operator.resetsClock)) {
                for (String clockName : clocksByName().keySet()) {
                    String normalizedClock = normalizeIdentifier(clockName);
                    addResetAssignment(assignments, normalizedClock);
                }
            }
            for (String clockName : operator.resetClocks) {
                addResetAssignment(assignments, normalizeIdentifier(clockName));
            }
        }
    }

    private void addResetAssignment(List<String> assignments, String clockName) {
        String reset = String.format(Locale.ROOT, "(%s' = 0)", clockName);
        if (!assignments.contains(reset)) {
            assignments.add(reset);
        }
    }

    private String translateGuard(String guard) {
        String translated = guard.trim();
        Matcher matcher = SIMPLE_GUARD_PATTERN.matcher(translated);
        if (!matcher.matches()) {
            return sanitizeExpression(translated);
        }

        String variable = normalizeIdentifier(matcher.group(1));
        if (isIgnoredKey(variable) || variable.contains("operator_name") || variable.contains("operatorname")) {
            // Operator-name guards are represented by numeric operator IDs, not string comparisons.
            return null;
        }
        String operator = matcher.group(2);
        String value = matcher.group(3).trim();
        if (value.startsWith("<") && value.endsWith(">")) {
            return String.format(Locale.ROOT, "%s %s %s", variable, operator, resolveReferenceIdentifier(value.substring(1, value.length() - 1)));
        }
        return String.format(Locale.ROOT, "%s %s %s", variable, operator, translateRawValue(variable, value));
    }

    private String translateAssignment(String variable, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }

        if (value.endsWith("_INCREMENT")) {
            String incrementSource = value.substring(0, value.length() - "_INCREMENT".length());
            return String.format(Locale.ROOT, "(%s' = %s + 1)", variable, resolveReferenceIdentifier(incrementSource));
        }

        if (value.matches("\\(\\+\\s+<.*>\\s+1\\)")) {
            String source = value.replaceAll("[()<>]", "").replace("+ ", "").replace(" 1", "").trim();
            return String.format(Locale.ROOT, "(%s' = %s + 1)", variable, resolveReferenceIdentifier(source));
        }

        // Also handle forms like "<person-id> + 1" directly.
        if (value.matches("<[^>]+>\\s*\\+\\s*1")) {
            String source = value.replace("<", "").replace(">", "").replace("+", "").replace("1", "").trim();
            return String.format(Locale.ROOT, "(%s' = %s + 1)", variable, resolveReferenceIdentifier(source));
        }

        if (value.startsWith("<") && value.endsWith(">")) {
            return String.format(Locale.ROOT, "(%s' = %s)", variable, resolveReferenceIdentifier(value.substring(1, value.length() - 1)));
        }

        return String.format(Locale.ROOT, "(%s' = %s)", variable, translateRawValue(variable, value));
    }

    private String translateRawValue(String variable, String rawValue) {
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return value;
        }
        LinkedHashMap<String, Integer> encoding = symbolicEncoding.get(normalizeIdentifier(variable));
        if (encoding != null) {
            return encodeValue(variable, value, encoding);
        }
        if (value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true")) {
            return "1";
        }
        if (value.equalsIgnoreCase("no") || value.equalsIgnoreCase("false")) {
            return "0";
        }
        if (NUMERIC_PATTERN.matcher(value).matches()) {
            return stripTrailingZeros(Double.parseDouble(value) * configScale());
        }
        return resolveReferenceIdentifier(value);
    }

    private String encodeStateValue(String variable, String rawValue) {
        LinkedHashMap<String, Integer> encoding = encodingFor(variable);
        if (encoding == null) {
            return null;
        }
        return encodeValue(variable, rawValue, encoding);
    }

    private String resolveReferenceIdentifier(String reference) {
        String normalized = normalizeIdentifier(reference);
        if (normalized == null || normalized.isEmpty()) {
            return normalized;
        }
        if (variables.containsKey(normalized)) {
            return normalized;
        }

        String prefixed = "state_" + normalized;
        if (variables.containsKey(prefixed)) {
            return prefixed;
        }

        // Common Soar placeholders used in rule bodies.
        if ("person_id".equals(normalized) && variables.containsKey("state_current_person_id")) {
            return "state_current_person_id";
        }
        if ("wall_clock".equals(normalized) && variables.containsKey("state_wall_clock")) {
            return "state_wall_clock";
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("use_box") || lower.contains("usebox")) {
            return semanticsUseBoxConst();
        }
        if (lower.contains("deadline")) {
            return semanticsDeadlineConst();
        }
        if (lower.contains("box_accuracy") || lower.contains("boxaccuracy")) {
            return semanticsBoxAccuracyConst();
        }
        return normalized;
    }

    private String encodeValue(String variable, String rawValue, LinkedHashMap<String, Integer> encoding) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (encoding == null) {
            encoding = encodingFor(variable);
        }
        if (encoding == null) {
            return sanitizeLabel(value);
        }
        Integer encoded = encoding.get(value);
        if (encoded == null) {
            encoded = encoding.size();
            encoding.put(value, encoded);
        }
        return String.valueOf(encoded);
    }

    private LinkedHashMap<String, Integer> encodingFor(String variable) {
        String normalized = normalizeIdentifier(variable);
        return symbolicEncoding.computeIfAbsent(normalized, key -> buildEncoding(variables.getOrDefault(normalized, new VariableInfo(normalized)).rawValues));
    }

    private LinkedHashMap<String, Integer> buildEncoding(Collection<String> values) {
        LinkedHashMap<String, Integer> encoding = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!encoding.containsKey(value)) {
                encoding.put(value, encoding.size());
            }
        }
        if (encoding.isEmpty()) {
            encoding.put("0", 0);
        }
        return encoding;
    }

    private String detectLocationVariable() {
        String bestVariable = null;
        int bestScore = -1;
        Set<String> stateNames = config != null ? config.getStates().keySet() : Collections.emptySet();

        for (VariableInfo info : variables.values()) {
            int score = info.symbolicCount();
            if (stateNames.contains(info.name)) {
                score += stateNames.size();
            }
            if (score > bestScore) {
                bestScore = score;
                bestVariable = info.name;
            }
        }
        return bestVariable;
    }

    private String resolveStateVariable(PrismConfig.StateConfig stateConfig) {
        if (stateConfig.variable != null && !stateConfig.variable.isEmpty()) {
            String requested = normalizeIdentifier(stateConfig.variable);
            if (variables.containsKey(requested)) {
                return requested;
            }
            String prefixed = normalizeIdentifier("state_" + requested);
            if (variables.containsKey(prefixed)) {
                return prefixed;
            }
            return null;
        }
        return variables.containsKey(locationVariable) ? locationVariable : null;
    }

    private String resolveClockName(PrismConfig.StateConfig stateConfig) {
        if (stateConfig.clock != null && !stateConfig.clock.isEmpty()) {
            return stateConfig.clock;
        }
        if (config != null && config.getClocks().size() == 1) {
            return config.getClocks().keySet().iterator().next();
        }
        return null;
    }

    private Map<String, PrismConfig.ClockConfig> clocksByName() {
        return config != null ? config.getClocks() : Collections.emptyMap();
    }

    private String inferRuleLabel(Rule rule, String prefix) {
        for (Map.Entry<String, String> entry : rule.valueMap.entrySet()) {
            String key = normalizeIdentifier(entry.getKey());
            if (key.contains("operator_name") || key.contains("operatorname")) {
                return sanitizeLabel(entry.getValue());
            }
        }

        if (config != null && !config.getOperators().isEmpty()) {
            for (PrismConfig.OperatorConfig operator : config.getOperators().values()) {
                for (String pattern : operator.soarRulePatterns) {
                    if (rule.ruleName.matches(pattern)) {
                        return sanitizeLabel(operator.name);
                    }
                }
            }
        }

        Matcher matcher = RULE_KEY_PATTERN.matcher(rule.ruleName);
        if (matcher.matches()) {
            return sanitizeLabel(matcher.group(1));
        }
        return sanitizeLabel(rule.ruleName.replace(prefix, ""));
    }

    private boolean isIgnoredKey(String key) {
        if (key == null) {
            return true;
        }
        return key.equals("total_time") || key.equals("total-time") || key.equals("time_counter") || key.equals("time-counter") || key.endsWith("_operator_name");
    }

    private double configScale() {
        return config != null ? config.getTimeScale() : 1.0;
    }

    private int configuredNumPersons() {
        if (config == null || config.getConstants() == null) {
            return 20;
        }

        Integer fromN = constantAsInt("N");
        if (fromN != null && fromN > 1) {
            return fromN;
        }

        Integer fromNumPersons = constantAsInt("num_persons");
        if (fromNumPersons != null && fromNumPersons > 1) {
            return fromNumPersons;
        }

        Integer fromNumPeople = constantAsInt("num_people");
        if (fromNumPeople != null && fromNumPeople > 1) {
            return fromNumPeople;
        }

        return 20;
    }

    private Integer constantAsInt(String key) {
        Object raw = config.getConstants().get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int configuredDeadlineUpperBound() {
        if (config == null || config.getConstants() == null) {
            return 20;
        }

        Integer fromMaxDeadline = constantAsInt("max_deadline");
        if (fromMaxDeadline != null && fromMaxDeadline > 0) {
            return fromMaxDeadline;
        }

        Integer fromDeadlineUpper = constantAsInt("deadline_upper_bound");
        if (fromDeadlineUpper != null && fromDeadlineUpper > 0) {
            return fromDeadlineUpper;
        }

        Integer fromDeadline = constantAsInt("deadline");
        if (fromDeadline != null && fromDeadline > 0) {
            return fromDeadline;
        }

        return 20;
    }

    private static String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').replace('.', '_');
        if (normalized.startsWith("<") && normalized.endsWith(">")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.replaceAll("[^A-Za-z0-9_]+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized;
    }

    private static String sanitizeLabel(String value) {
        String normalized = normalizeIdentifier(value);
        if (normalized == null || normalized.isEmpty()) {
            return "transition";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String sanitizeExpression(String value) {
        return value.replace("<", "").replace(">", "").trim();
    }

    private static boolean isNumericValue(String value) {
        return value != null && NUMERIC_PATTERN.matcher(value.trim()).matches();
    }

    private int scaledInteger(String value) {
        return (int) Math.round(Double.parseDouble(value) * configScale());
    }

    private String stripTrailingZeros(double value) {
        if (Math.floor(value) == value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}