package edu.fit.assist.translator.soar;
import java.util.ArrayList;
import java.util.List;
import edu.fit.assist.translator.gen.*;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

public class Visitor<Object> extends AbstractParseTreeVisitor<Object> implements SoarVisitor<Object> {
    Rule currentRule;
    String currentContext = "";
    boolean negateCondition = false;

    public SoarRules rules;
    String currentActionContextVar = "";
    /**
     * Visit a parse tree produced by {@link SoarParser#soar}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSoar(SoarParser.SoarContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#soar_production}.
     * Runs when Parser encounters a New Production
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSoar_production(SoarParser.Soar_productionContext ctx) {

        //Create a new rule
        String ruleName = ctx.sym_constant().getText();
        boolean currentIsElaboration;
//        System.out.println(ctx.sym_constant().Sym_constant().getText());
        currentIsElaboration = ruleName.contains("elaborate");
        // System.out.println(ruleName);
        rules.createNewRule(ruleName);


        // Get all the statements that precede the arrow (-->)
        SoarParser.Condition_sideContext conditions = ctx.condition_side();

        currentRule = rules.getRuleByName(ruleName);
        currentRule.isElaboration = currentIsElaboration;
        // sp{ ruleName
        visit(ctx.condition_side());
        // -->
        visit(ctx.action_side());
        //}
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#flags}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitFlags(SoarParser.FlagsContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#condition_side}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitCondition_side(SoarParser.Condition_sideContext ctx) {

        visit(ctx.state_imp_cond());
        // Visit the rest of the guards (if any)
        List<SoarParser.CondContext> guards = ctx.cond();
        for(SoarParser.CondContext guard : guards){
            visit(guard);
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#state_imp_cond}.
     * Assumes first guard starts with (state <s> ...)
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitState_imp_cond(SoarParser.State_imp_condContext ctx) {

        String stateKeyword = ctx.STATE().getSymbol().getText();
        currentContext = stateKeyword;
        // get '<s>'
        String var = (String)visit(ctx.id_test().test().simple_test());

//        System.out.println(var);
        currentRule.addContext(var, currentContext);

        for(SoarParser.Attr_value_testsContext attribute : ctx.attr_value_tests()){
            visit(attribute);
        }

        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#cond}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitCond(SoarParser.CondContext ctx) {

        negateCondition = ctx.Negative_pref() != null;
        visit(ctx.positive_cond());
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#positive_cond}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitPositive_cond(SoarParser.Positive_condContext ctx) {

        visit(ctx.conds_for_one_id());
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#conds_for_one_id}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitConds_for_one_id(SoarParser.Conds_for_one_idContext ctx) {

        // get '<var>'
        String var = (String)visit(ctx.id_test().test().simple_test());
        currentContext = currentRule.getContext(var);
//        System.out.println(var);


        for(SoarParser.Attr_value_testsContext attribute : ctx.attr_value_tests()){
            visit(attribute);
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#id_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitId_test(SoarParser.Id_testContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#attr_value_tests}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitAttr_value_tests(SoarParser.Attr_value_testsContext ctx) {

        String line = ctx.getText();
//        System.out.println(line);
        String variable = ctx.attr_test(0).getText();
        variable = currentContext+"_"+variable;
        variable = cleanVariableName(variable);
//        System.out.println(currentContext+"_"+variable);


        // get value check
        List<SoarParser.Value_testContext> values = ctx.value_test();
        boolean existsCheck = values.size()==0;

        if (!variable.equals("state_io") && !variable.equals("state_operator")){
            currentRule.addVariable(variable);
            rules.addVariableValue(variable, "nil");
        }

        // Attribute has no value, so guard is checking for existance
        if(existsCheck){
            if(ctx.Negative_pref() != null){ // check for not existing
                currentRule.addGuard(variable+" = nil");
            }else{ // check for presence
                currentRule.addGuard(variable+" != nil");
            }
        }else{ // Attribute has a value
            String value = (String)visit(values.get(0));

//            System.out.println(variable + " " + value);

            // Handle null value case
            if (value == null) {
                // Skip processing if value is null
                return null;
            }

            // Check is value is in the format: <x>
            String[] valueList =  value.split(" ");
            String attributePartOfValue = valueList[valueList.length-1];
//            if(currentRule.ruleName.startsWith("top-state*elaborate*error-info*warn-condition*low")){
//                //System.err.println(attributePartOfValue +"; "+attributePartOfValue+"E");
//            }
            if(!((value.startsWith("<") && value.endsWith(">") && !value.contains(" "))|| attributePartOfValue.contains("*"))){
                //if(value.contains("*")){
                //System.err.println(value +"; "+attributePartOfValue+"E");
                //}
                // Removes the conditional check from value
                String onlyValue = value.replaceAll("<= ", "").replaceAll(">= ", "").replaceAll("= ", "");
                onlyValue = onlyValue.replaceAll("< ", "").replaceAll("> ", "");

                // Don't add inequalities to the list of values a variable can have
                if(onlyValue.equals(value)){
                    rules.addVariableValue(variable, onlyValue);
                }else{
                    // It is an inequality, hence we can use it for setting Types
                    // Example: ^var1 <= <v2>, where <v2> is ^var2
                    // Hence, type of ^var1 is the same as ^var2
                    String leftSide = variable;
                    // if value is a number directly add the type instead of adding it to the graph

                    try {
                        int rightValue = Integer.parseInt(onlyValue);
                        rules.addVariableValue(variable, onlyValue);
                    }catch(Exception e){}

                    try {
                        double rightValue = Double.parseDouble(onlyValue);
                        rules.addVariableValue(variable, onlyValue);
                    }catch(Exception e2){}

                    String rightSide = currentRule.getContext(onlyValue);
//                    System.out.println(leftSide);
//                    System.out.println(rightSide);
                    rules.addTypeNode(leftSide, rightSide);
                    rules.addTypeNode(rightSide, leftSide);

                }

                for(String key : currentRule.contextMap.keySet()){
                    if(value.contains(key)){
                        value = value.replaceAll(key, currentRule.contextMap.get(key));
                        break;
                    }
                }

                if(value.contains("<") || value.contains(">")){
                    currentRule.addGuard(variable+" "+value);
                }else{
                    if(ctx.Negative_pref() != null ^ negateCondition){
                        currentRule.addGuard(variable+" != "+value);

                        if(rules.variables.containsKey(value)){
                            String leftSide = variable;
                            String rightSide = value;
                            //System.out.println(leftSide);
                            //System.out.println(rightSide);
                            rules.addTypeNode(leftSide, rightSide);
                            rules.addTypeNode(rightSide, leftSide);
                        }


                    }else{
                        currentRule.addGuard(variable+" = "+value);

                    }
                }
            }else{

                // check for negation

                // Negate if one negation exists, if two exist, then don't
                /*if(ctx.Negative_pref() != null ^ negateCondition){

                    value = "!" + value;
                    currentRule.addContext(value, variable);
                }else{
                    currentRule.addContext(value, variable);
                }*/
                if(!currentRule.contextMap.containsKey(value)){
                    // assignment
                    currentRule.addContext(value, variable);
                }else{
                    // equality check

                    String leftSide = variable;
                    value = currentRule.getContext(value);
                    String rightSide = value;
                    //System.out.println(leftSide);
                    //System.out.println(rightSide);
                    rules.addTypeNode(leftSide, rightSide);
                    rules.addTypeNode(rightSide, leftSide);


                    if(ctx.Negative_pref() != null ^ negateCondition){
                        currentRule.addGuard(variable+" != "+value);
                    }else{
                        currentRule.addGuard(variable+" = "+value);
                    }

                }
            }
        }
        return null;
    }

    public String cleanVariableName(String s){
        s=s.replaceAll("\\_output\\-link", "");
        s=s.replaceAll("\\_input\\-link", "");
        s=s.replaceAll("\\_systemdata", "");
        return s;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#attr_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitAttr_test(SoarParser.Attr_testContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#value_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitValue_test(SoarParser.Value_testContext ctx) {

        SoarParser.TestContext testContext = ctx.test();
        if(testContext == null){
            return null;
        }
        return visit(testContext);

    }

    /**
     * Visit a parse tree produced by {@link SoarParser#test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitTest(SoarParser.TestContext ctx) {

        // Case 1: it is a 'simple test'
        if(ctx.simple_test() != null){
            return visit(ctx.simple_test());
        }
        // Case 2: it is a 'Conjunctive test'
        if(ctx.conjunctive_test() != null){
            return visit(ctx.conjunctive_test());
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#conjunctive_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitConjunctive_test(SoarParser.Conjunctive_testContext ctx) {

        //System.err.println("Conjunctive test not supported in the following "+ctx.getText());
        for(SoarParser.Simple_testContext temp:ctx.simple_test()){
            return visitChildren(temp);
        }
        return visitChildren(ctx);

    }

    /**
     * Visit a parse tree produced by {@link SoarParser#simple_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSimple_test(SoarParser.Simple_testContext ctx) {

        if(ctx.relational_test() != null){
            return visit(ctx.relational_test());
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#multi_value_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitMulti_value_test(SoarParser.Multi_value_testContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#disjunction_test}.
     * Extracts values from << value1 value2 ... >> syntax
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitDisjunction_test(SoarParser.Disjunction_testContext ctx) {
        // Extract all constants from << ... >>
        List<SoarParser.ConstantContext> constants = ctx.constant();
        if (constants != null && !constants.isEmpty()) {
            StringBuilder result = new StringBuilder();
            result.append("<< ");
            for (int i = 0; i < constants.size(); i++) {
                result.append(constants.get(i).getText());
                if (i < constants.size() - 1) {
                    result.append(" ");
                }
            }
            result.append(" >>");
            return (Object) result.toString();
        }
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#relational_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitRelational_test(SoarParser.Relational_testContext ctx) {

        String output = "";
        if(ctx.relation() != null){
            output += (String)visit(ctx.relation());
        }


        if(ctx.single_test() != null){
            output += (String)visit(ctx.single_test());
        }


        return (Object)output;

    }

    /**
     * Visit a parse tree produced by {@link SoarParser#relation}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitRelation(SoarParser.RelationContext ctx) {

        return (Object)(ctx.getText()+" ");
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#single_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSingle_test(SoarParser.Single_testContext ctx) {

        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#variable}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitVariable(SoarParser.VariableContext ctx) {

        // return variable name
        return (Object)("<"+ctx.sym_constant().Sym_constant().getSymbol().getText()+">");
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#constant}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitConstant(SoarParser.ConstantContext ctx) {
//        System.out.println(ctx.getText());
        if (ctx.sym_constant() != null){
            return (Object)ctx.sym_constant().Sym_constant().getSymbol().getText();
        }
        if(ctx.Int_constant() != null){
            return (Object)ctx.Int_constant().getSymbol().getText();
        }
        if(ctx.Float_constant() != null){
            return (Object)ctx.Float_constant().getSymbol().getText();
        }
        if(ctx.Print_string() != null){
            return (Object)ctx.Print_string().getSymbol().getText();
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#action_side}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitAction_side(SoarParser.Action_sideContext ctx) {

        if(ctx.action() != null){
            for(SoarParser.ActionContext action : ctx.action()){
                visit(action);
            }
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#action}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitAction(SoarParser.ActionContext ctx) {
        // context
        String contextVar = (String)visit(ctx.variable());
        currentActionContextVar = contextVar;
//        System.out.println(contextVar);
        currentContext = currentRule.getContext(contextVar);
        List<SoarParser.Attr_value_makeContext> attributes = ctx.attr_value_make();
        for(SoarParser.Attr_value_makeContext attribute : attributes){
            visit(attribute);
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#print}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitPrint(SoarParser.PrintContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#func_call}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitFunc_call(SoarParser.Func_callContext ctx) {

        String operation = (String)visit(ctx.func_name());
        String operand1 = (String)visit(ctx.value(0));
        String operand2 = (String)visit(ctx.value(1));
        String infixValue = operand1 + " " + operation + " "+ operand2;

        return (Object)infixValue;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#func_name}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitFunc_name(SoarParser.Func_nameContext ctx) {

        return (Object)ctx.getText();

    }

    /**
     * Visit a parse tree produced by {@link SoarParser#value}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitValue(SoarParser.ValueContext ctx) {
//        System.out.println(ctx.getText());
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#attr_value_make}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitAttr_value_make(SoarParser.Attr_value_makeContext ctx) {
        String attributeName = (String)visit(ctx.variable_or_sym_constant(0).sym_constant());
        String variable = currentContext + "_" + attributeName;
        variable = cleanVariableName(variable);
        SoarParser.Value_makeContext value = ctx.value_make();
        currentContext = variable;
        String val = (String)visit(value);

        // Store operator name in valueMap for later extraction
        if (attributeName.equals("name") && currentActionContextVar.equals("<o>") && val != null) {
            // Store in valueMap with full context so Translate.java can find it
            String operatorNameKey = currentContext.replace("state_", "") + "_operator_name";
            currentRule.valueMap.put(operatorNameKey, val);
        }

        if (val == null) {
            return null;
        }

        // Handle increment expression (+ 1 <var>) by storing in valueMap
        if (val != null && val.matches("\\(\\+ 1 <.*>\\)")) {
            // Extract variable from expression like (+ 1 <lc>)
            String innerVar = val.replaceAll("[()<>]", "")
                    .replace("+ 1 ", "").trim();
            String prismVar = cleanVariableName(currentContext);

            if (prismVar != null) {
                // Store increment info in valueMap as a special marker
                currentRule.valueMap.put(prismVar, innerVar + "_INCREMENT");
            } else {
                System.out.println("WARNING: Missing context for 1 + <" + innerVar + "> in rule " + currentRule.ruleName);
            }

            return null; // skip rest
        }

        // Handle symbolic variable references
        if (val.startsWith("<") && val.endsWith(">")) {
            if (currentRule.contextMap.containsKey(val) && !currentRule.getContext(val).equals(variable)) {
                String resolved = currentRule.getContext(val);
                currentRule.addAttrValue(variable, resolved);
                rules.addVariableValue(variable, "^VAR" + resolved);
                rules.addTypeNode(variable, resolved);
                rules.addTypeNode(resolved, variable);
            } else {
                currentRule.addContext(val, variable);
            }
            return null;
        }

        // Handle substitutions like 'val = 1+<x>' or 'val = X'
        if (val.contains("<") && val.contains(">")) {
            String attribute = val.replaceAll("(<[a-zA-Z0-9*]+>).*", "$1");
            String replacement = currentRule.contextMap.get(attribute);

            if (replacement != null) {
                val = val.replace(attribute, replacement);
            } else {
                System.out.println("WARNING: Missing context for " + attribute + " in rule " + currentRule.ruleName);
            }
        }

        // Store actual value
        currentRule.addAttrValue(variable, val);
        rules.addVariableValue(variable, val);

        // Track initial value if elaboration rule
        if (currentRule.isElaboration && currentRule.guards.size() == 1 &&
                currentRule.guards.get(0).equals("state_superstate = nil")) {
            rules.variables.get(variable).initialValue = val;
        }

        return null;
    }


    /**
     * Visit a parse tree produced by {@link SoarParser#variable_or_sym_constant}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitVariable_or_sym_constant(SoarParser.Variable_or_sym_constantContext ctx) {
        return visitChildren(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#value_make}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitValue_make(SoarParser.Value_makeContext ctx) {
//        System.out.println(ctx.getText());
        String value = "";
        String pref_specifier = null;
        value = (String)visit(ctx.value(0));
        // get the value of the attribute Example: <o> in (<s> ^operator <o> +)
        if(ctx.pref_specifier(2) != null){

            pref_specifier = (String)visit(ctx.pref_specifier(2));
//            System.out.println(pref_specifier);
        }


        // visit the preference specifier Example = 0.0 in (<s> ^opetator <o> = 0.0)
        if(ctx.pref_specifier(0) != null){
            pref_specifier = (String)visit(ctx.pref_specifier(0));
//            System.out.println(currentContext);
//            System.out.println(value +" " +pref_specifier);
            // check for second preference specifier Example = in (<s> ^opetator <o> >,=)
            if(ctx.pref_specifier(1) != null){
                pref_specifier += ","+(String)visit(ctx.pref_specifier(1));
            }


        }

        if(currentContext.equals("state_operator") && pref_specifier != null){
            // Check if rule is a learning Rule (<s> ^operator <o> = 0.0)
            //System.out.println(pref_specifier);
            // check if rule is a learning rule
            if(pref_specifier.contains("=")){
                currentRule.isLearningRule = true;
            }
            // if it is a learning rule, get the priority
            if(pref_specifier.startsWith("=") && pref_specifier.length() > 1){


                // set priority value
                try{
                    currentRule.priority = Double.parseDouble(pref_specifier.substring(1));
                }catch(Exception e){e.printStackTrace();}
                // Search for the name of the operator of which we are setting the preference.
                String op_name = "";
                for(String guard : currentRule.guards){

                    if(guard.startsWith("state_operator_name = ")){
                        op_name = guard.substring("state_operator_name = ".length());
                    }
                }
                currentRule.addAttrValue("state_operator_name", op_name);
                // check if rule has max priority
            }else if(pref_specifier.contains(">")){
                // set priority value
                currentRule.priority = 10_000;
                // Search for the name of the operator of which we are setting the preference.
                String op_name = "";
                for(String guard : currentRule.guards){

                    if(guard.startsWith("state_operator_name = ")){
                        op_name = guard.substring("state_operator_name = ".length());
                    }
                }
                currentRule.addAttrValue("state_operator_name", op_name);
            }
        }


        // get the value of the attribute when using a compressed notation Example: new_val in (<s> ^value old_val - new_val +)
        if(ctx.value(1) != null){
            value = (String)visit(ctx.value(1));
        }
        if(pref_specifier !=null && pref_specifier.equals("-")){
            value = "nil";
        }


        return (Object)value;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#pref_specifier}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitPref_specifier(SoarParser.Pref_specifierContext ctx) {
        if (ctx.unary_pref() != null){
            return visit(ctx.unary_pref());
        }
        if (ctx.unary_or_binary_pref() != null){
            String pref = (String)visit(ctx.unary_or_binary_pref());

            if(ctx.value() != null){
                String val = (String)visit(ctx.value());
                return (Object)(pref + " " + val);
            }
            return (Object)pref;
        }


        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#unary_pref}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitUnary_pref(SoarParser.Unary_prefContext ctx) {

        if(ctx.Negative_pref() != null){
            return (Object)(ctx.Negative_pref().getSymbol().getText());
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#unary_or_binary_pref}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitUnary_or_binary_pref(SoarParser.Unary_or_binary_prefContext ctx) {

//        System.out.println(ctx.getText() + " Why?");
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#sym_constant}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSym_constant(SoarParser.Sym_constantContext ctx) {
//        System.out.println(ctx.Sym_constant().getText());
        return (Object)(ctx.Sym_constant().getSymbol().getText());
    }
}