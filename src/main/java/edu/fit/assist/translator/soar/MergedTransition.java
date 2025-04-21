package edu.fit.assist.translator.soar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MergedTransition {
    String guard;
    String probabilityExpr;  // Derived dynamically, e.g., "state_low_to_med_prob"
    String assignmentString; // Composite assignments for next-state: "(var1' = val1) & (var2' = val2)"

    public MergedTransition(String guard, String probabilityExpr, String assignmentString) {
        this.guard = guard;
        this.probabilityExpr = probabilityExpr;
        this.assignmentString = assignmentString;
    }
}
