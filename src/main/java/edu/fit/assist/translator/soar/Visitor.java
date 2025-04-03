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
        //            System.out.println(ruleName);
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

            System.out.println(variable + " " + value);

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
                    //System.out.println(leftSide);
                    //System.out.println(rightSide);
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
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#simple_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSimple_test(SoarParser.Simple_testContext ctx) {
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
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#disjunction_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitDisjunction_test(SoarParser.Disjunction_testContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#relational_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitRelational_test(SoarParser.Relational_testContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#relation}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitRelation(SoarParser.RelationContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#single_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSingle_test(SoarParser.Single_testContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#variable}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitVariable(SoarParser.VariableContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#constant}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitConstant(SoarParser.ConstantContext ctx) {
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
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#func_call}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitFunc_call(SoarParser.Func_callContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#func_name}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitFunc_name(SoarParser.Func_nameContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#value}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitValue(SoarParser.ValueContext ctx) {

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
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#value_make}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitValue_make(SoarParser.Value_makeContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#pref_specifier}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitPref_specifier(SoarParser.Pref_specifierContext ctx) {
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
        return null;
    }
}