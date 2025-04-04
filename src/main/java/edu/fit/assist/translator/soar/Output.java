package edu.fit.assist.translator.soar;

import java.util.ArrayList;

public class Output {
    SoarRules rules;
    public Output(SoarRules rules){
        this.rules = rules;
    }
    public String generateOutput(){
        String output = "dtmc\n\n";
        rules.parseVariableValuePass();
        output += generateVariableDeclarations();
        output += generateModules();
        return output;
    }

    private String generateModules() {
        String output ="module ";
        output += "\nendmodule";
        return output;
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

            output.append("const ").append(var.name).append(" = ");
            if(var.varType == Variable.S_CONST){
//                output.append("{");
                boolean first = true;
                for(String varVal : var.values){
                    if(!first){
                        output.append(",");
                    }else{
                        first = false;
                    }
                    output.append(varVal);
                }
            }else if(var.varType == Variable.INT){
                output.append("integer;\n");
            }else if(var.varType == Variable.FLOAT){
                output.append("double;\n");
            }else{
                output.append("TYPE ERROR");
                System.err.println("TYPE ERROR with variable "+var.name);
            }
            output.append("\n");
        }
        return new StringBuilder(output + "\n\n");
    }
//        return output;
    }

