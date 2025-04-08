package edu.fit.assist.translator.soar;

import java.util.ArrayList;
import java.util.List;
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

    private String generateModules() {
        StringBuilder output = new StringBuilder();

        output.append("module user\n");

        for (Rule rule : rules.rules) {
            String guard = rule.formatGuard();
            if (guard.contains("state_name = nil") || guard.contains("state_superstate")) {
                continue;
            }
            else {
                String rhs = rule.formatRHS();
                output.append("    [] ").append(guard).append(" -> ").append(rhs).append(";\n");
            }
        }

        output.append("endmodule\n\n");
        return output.toString();
    }

    private String sanitizeName(String name) {
        return name.replace("-", "_").replace("'", "_prime").replace("^", "");
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

