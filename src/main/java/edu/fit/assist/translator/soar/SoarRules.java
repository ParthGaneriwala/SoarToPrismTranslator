package edu.fit.assist.translator.soar;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class SoarRules {
    ArrayList<Rule> rules;
    LinkedHashMap<String, Variable> variables;
    LinkedHashMap<String, Integer> mapNameToType;
    LinkedHashMap<String, ArrayList<String>> typeGraph;

    public SoarRules(){
        rules = new ArrayList<Rule>();
        variables = new LinkedHashMap<String, Variable>();
        mapNameToType = new LinkedHashMap<String, Integer>();
        typeGraph = new LinkedHashMap<String, ArrayList<String>>();
    }

    public void addTypeNode(String name1, String name2){
        if (!typeGraph.containsKey(name1)){
            ArrayList<String> nodes = new ArrayList<String>();
            nodes.add(name2);
            typeGraph.put(name1,nodes);
        }else{
            ArrayList<String> nodes = typeGraph.get(name1);
            if(!nodes.contains(name2)){
                nodes.add(name2);
            }
            typeGraph.put(name1,nodes);
        }
    }

    public void parseVariableValuePass(){
        for (String varName: variables.keySet()){
            Variable var = variables.get(varName);

            for(int i=0; i<var.values.size(); i++){
                String s = var.values.get(i);
                if (s.startsWith("^VAR")){
                    var.values.remove(i);
                    i--;
                    s = s.substring(4);
                    for (String val : variables.get(s).values){
                        if(!var.values.contains(val)){
                            var.values.add(val);
                        }
                    }
                }
            }
        }

    }
    public void removeRule(String ruleName){

        for(int i=0; i < rules.size(); i++){
            if(rules.get(i).ruleName.equals(ruleName)){
                rules.remove(i);
                break;
            }
        }

    }

    public void addVariableValue(String varName, String varValue){
        if(variables.containsKey(varName)){
            variables.get(varName).addValue(varValue);
        }else{
            Variable v = new Variable(varName);
            v.addValue(varValue);
            variables.put(varName, v);
        }
    }


    public void createNewRule(String name){
        rules.add(new Rule(name, variables));
    }

    public Rule getRuleByName(String name){
        for(Rule rule : rules){
            if(rule.ruleName.equals(name)){
                return rule;
            }
        }
        return null;
    }
}
