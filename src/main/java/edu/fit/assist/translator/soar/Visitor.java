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
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#soar_production}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitSoar_production(SoarParser.Soar_productionContext ctx) {
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
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#condition_side}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitCondition_side(SoarParser.Condition_sideContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#state_imp_cond}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitState_imp_cond(SoarParser.State_imp_condContext ctx) {
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
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#attr_value_tests}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitAttr_value_tests(SoarParser.Attr_value_testsContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#attr_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitAttr_test(SoarParser.Attr_testContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#value_test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitValue_test(SoarParser.Value_testContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by {@link SoarParser#test}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Object visitTest(SoarParser.TestContext ctx) {
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
        return null;
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