# Time-Based Soar to PRISM Translation Assumptions

## Overview
This translator converts temporal Soar cognitive models to PRISM DTMC format. It makes specific assumptions about the structure of the Soar code to enable automatic translation.

## Core Assumptions

### 1. Time Management
- **Assumption**: A Soar file with a variable named `time-counter` or `time_counter` represents a temporal model
- **Extraction**: Total time is extracted from `apply*initialize` rule's `total-time` attribute
- **PRISM Output**: Generates a `time` module with synchronized counter (0 to TOTAL_TIME)

### 2. State Variables
- **Assumption**: State variables initialized in `apply*initialize` rule are persistent model state
- **Extraction**: All `^attribute value +` patterns in RHS of initialize rule
- **PRISM Output**: Constants for numeric values, module variables for state tracking

### 3. Probabilistic Transitions
- **Assumption**: Any variable starting with `pdf` (e.g., pdf1, pdf2, pdf3) represents a probability
- **Extraction**: From `apply*initialize` or input-link references
- **PRISM Output**: Probabilistic choice branches with extracted probabilities

### 4. State Machines (Propose/Apply Pattern)
- **Assumption**: `propose*X` + `apply*apply-X` rule pairs define state transitions
- **Pattern Recognition**:
  - `propose*X`: Guards define preconditions (FROM state)
  - `apply*apply-X`: Actions define postconditions (TO state)
- **PRISM Output**: Transition modules with guards and state updates

### 5. Monitor Modules (Name-based Detection)
- **Assumption**: Rules containing "monitor" in their name define monitoring modules
- **Pattern**: `switch-monitor`, `check*`, `sickness-monitor`, `mission-monitor`
- **PRISM Output**: Modules with conditional logic based on state

### 6. Sampling Windows (Time-based Guards)
- **Assumption**: Multiple rules with `time-counter = X` guards indicate windowed sampling
- **Detection**: Extract all numeric comparisons with time-counter
- **Algorithm**: Use GCD to find interval pattern
- **PRISM Output**: Synchronized transitions at specific time points

### 7. State Variable Types
- **Numeric Ranges**: Variables with comparison guards (`< X`, `> X`) → integer ranges
- **Binary Flags**: Variables with `yes/no` or `0/1` values → [0..1]
- **Enumerated**: Variables with multiple symbolic values → mapped to integers

### 8. Synchronization
- **Assumption**: All modules operate in lock-step (DTMC synchronized)
- **PRISM Output**: All transitions use `[sync]` label for global synchronization

## Translation Strategy

### Phase 1: Analysis
1. Parse Soar file using ANTLR grammar
2. Identify temporal constructs (time-counter references)
3. Extract initialization values
4. Identify state machine patterns (propose/apply pairs)
5. Detect probability variables (pdf* pattern)
6. Analyze time-based guards to infer windows

### Phase 2: Module Generation
1. **Time Module**: Always generated for temporal models
2. **State Modules**: Generate from monitor/check patterns
3. **Transition Modules**: Generate from propose/apply pairs
4. **Action Tracker**: Generate if state machine transitions exist

### Phase 3: Guard Synthesis
1. Extract conditions from Soar LHS (guards)
2. Convert to PRISM boolean expressions
3. Add mutex conditions to prevent overlaps
4. Generate else/default cases for completeness

### Phase 4: Probabilistic Mapping
1. Identify probabilistic choice points
2. Extract probability values from pdf variables
3. Generate PRISM probabilistic branches
4. Use uniform distribution if probability unavailable

## Supported Soar Patterns

### ✅ Supported
- Temporal models with time-counter
- Propose/apply state machine transitions
- Probabilistic decisions with pdf variables
- Binary and integer state variables
- Monitor switching patterns
- Window-based sampling

### ⚠️ Limited Support
- Complex nested conditions (simplified)
- Mathematical expressions (basic arithmetic only)
- String/symbolic values (mapped to integers)

### ❌ Not Supported
- Non-temporal models (no time-counter)
- Substate elaborations
- Operator preferences beyond binary
- Dynamic operator creation
- Chunking/learning

## Extensibility

The framework can be extended for:
1. Additional module patterns (new rule naming conventions)
2. Custom probability distributions (config file)
3. Alternative synchronization models (remove [sync])
4. Complex guard expressions (enhanced parser)

## Example Mappings

### Soar Pattern → PRISM Output

```soar
sp {apply*initialize
    ...
    (<s> ^time-counter 1 +)
    (<s> ^total-time 1200 +)
    (<s> ^action 3 +)
}
```
→
```prism
const int TOTAL_TIME = 1200;

module time
  time_counter : [0..TOTAL_TIME] init 1;
  [sync] time_counter < TOTAL_TIME -> (time_counter' = time_counter + 1);
endmodule

module action_state  
  action : [0..3] init 3;
  ...
endmodule
```

### Probabilistic Transition

```soar
sp {propose*check-sick
    (<s> ^sick_thres <st>)
    (<sd> ^pdf1 { < <st> })
    ...
}
```
→
```prism
const double pdf1 = 0.50;  // from sick_thres

[sync] ... ->
  pdf1 : (sick' = 0) + (1-pdf1) : (sick' = 1);
```

## Configuration Override

While the translator works autonomously, a configuration file can override:
- Probability distributions (for empirical data)
- Time windows (if GCD inference fails)
- Module mappings (for custom patterns)

See `CONFIG_GUIDE.md` for details.
