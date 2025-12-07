# Soar to PRISM Translator: Current Capabilities and Future Enhancements

## Executive Summary

This document provides a comprehensive overview of the current time-based Soar to PRISM translator implementation and outlines planned enhancements for future development.

---

## Current Translation Capabilities

### 1. **Time Module Generation**
**What it does:**
- Generates a synchronized time counter module that progresses from 0 to TOTAL_TIME
- Extracts total time from Soar `apply*initialize` rule or config file
- Creates synchronized transitions that all modules must follow
- Implements time-based progression with guard conditions

**Example Output:**
```prism
module time
  time_counter : [0..TOTAL_TIME] init 0;
  [sync] time_counter < TOTAL_TIME -> (time_counter' = time_counter + 1);
  [sync] time_counter = TOTAL_TIME -> (time_counter' = time_counter);
endmodule
```

**Limitations:**
- Only supports linear time progression (no time jumps or non-uniform intervals)
- Cannot handle multiple independent time counters

---

### 2. **Sickness/Monitor Module Generation**
**What it does:**
- Generates window-based sampling modules for cyber sickness monitoring
- Creates probabilistic state transitions (healthy ↔ sick)
- Implements temporal windowing with commit and sample points
- Extracts probability distributions from config file or Soar rules
- Supports monitor switching between mission and sickness monitoring

**Example Output:**
```prism
module sickness
  name : [0..1] init mission_monitor;
  sick : [0..1] init 0;
  ts : [0..1] init 0;
  sickness_checked : [0..1] init 0;
  
  // Commit at window end
  [sync] time_counter = 299 -> (sick' = ts);
  
  // Sample at window start
  [sync] time_counter = 0 & name=sickness_monitor & sickness_checked=0 & sick=0 ->
    pdf1 : (ts'=0) & (sickness_checked'=1) +
    (1-pdf1) : (ts'=1) & (sickness_checked'=1);
endmodule
```

**Limitations:**
- Hardcoded to binary sickness states (0=healthy, 1=sick)
- Only supports single sickness variable (not multi-level sickness severity)
- Window boundaries are fixed based on time intervals

---

### 3. **Action State Machine Generation**
**What it does:**
- Automatically detects propose/apply rule pairs for state transitions
- Generates action state tracking module
- Creates individual transition modules (SS-transition, D-transition, DD-transition)
- Implements proper PRISM variable ownership (only action_state modifies action)
- Uses signaling pattern (_ing flags) for synchronized transitions
- Extracts initial action from Soar, config, or infers from transitions

**Example Output:**
```prism
module action_state
  action : [0..3] init 3;
  [sync] ss_transition_ing=1 -> (action' = 0);
  [sync] d_transition_ing=1 -> (action' = 1);
  [sync] dd_transition_ing=1 -> (action' = 2);
  [sync] !(ss_transition_ing=1) & ... -> (action' = action);
endmodule

module ss_transition
  ss_transition_done : [0..1] init 0;
  ss_transition_ing : [0..1] init 0;
  [sync] time_counter < TOTAL_TIME & action=3 & ... -> (ss_transition_ing' = 1);
  [sync] ss_transition_ing=1 -> (ss_transition_done' = 1) & (ss_transition_ing' = 0);
endmodule
```

**Limitations:**
- Only generates basic transition structure
- Does not extract response time distributions from config
- Does not model decision errors or error detection
- Does not handle probabilistic action transitions beyond basic guards

---

### 4. **Configuration File Support**
**What it does:**
- Loads external JSON configuration files
- Extracts model parameters (duration, intervals, levels)
- Reads sickness probability tables (3D: time × currentLevel × nextLevel)
- Loads response select/decide distributions
- Reads decision error distributions
- Provides supplemental data not in Soar rules

**Supported Config Sections:**
- `model`: Basic parameters (experimentDuration, sicknessSamplingInterval, etc.)
- `constants`: Initial values (initialAction, pdf1, etc.)
- `sicknessProbabilityTable`: Time-dependent sickness transitions
- `responseSelect`: Response time distributions for scan-and-select
- `responseDecide`: Response time distributions for decisions
- `decisionErrorDistributions`: Error rates by sickness level

**Limitations:**
- Response distributions are loaded but **not used in generation**
- Error distributions are loaded but **not used in generation**
- No validation of probability distributions (don't check if they sum to 1.0)
- Cannot specify custom module structures in config

---

### 5. **Pattern-Based Extraction**
**What it does:**
- Dynamically extracts time windows using GCD algorithm
- Infers time intervals from rule guards
- Detects propose/apply rule pairs by naming convention
- Extracts PDF values from Soar initialization or config
- Identifies module names from rule patterns
- Fail-fast validation with clear error messages

**Extraction Priority:**
1. Soar parse tree (primary source)
2. Configuration file (supplemental)
3. Inference from patterns (fallback)
4. Fail with clear error message (no hardcoded defaults)

**Limitations:**
- Assumes specific naming conventions (propose*, apply*apply-*, etc.)
- Cannot handle arbitrary rule naming schemes
- Limited to patterns documented in TRANSLATION_ASSUMPTIONS.md

---

## Future Enhancements Roadmap

### Phase 1: Critical Missing Features (High Priority)

#### 1.1 **Reward Structure Generation**
**Status:** Not Implemented (placeholder comments only)

**What needs to be added:**
- Parse reward definitions from Soar rules or config
- Generate PRISM reward structures for:
  - Mission completion (e.g., `time_counter = TOTAL_TIME`)
  - Correct decisions (based on action states)
  - Error penalties (based on decision errors)
  - Time-based rewards/penalties
  
**Implementation Plan:**
```prism
// Example reward structures to generate:
rewards "mission_completion"
  [sync] time_counter = TOTAL_TIME : 100;
endrewards

rewards "decision_quality"
  [sync] action=1 & sick=0 : 1;  // Correct decision while healthy
  [sync] action=1 & sick=1 : 0.5;  // Decision while sick (reduced reward)
endrewards

rewards "time_penalty"
  [sync] time_counter < TOTAL_TIME : -0.1;  // Small penalty per time step
endrewards
```

**Data Sources:**
- Config file: `rewards` section (new)
- Soar rules: Extract from preferences or operator rewards
- Default: Basic completion reward

---

#### 1.2 **Response Time Distribution Integration**
**Status:** Config loaded but not used in generation

**What needs to be added:**
- Use `responseSelect` distributions from config
- Use `responseDecide` distributions from config
- Generate probabilistic response time transitions
- Model state-dependent response variations

**Implementation Plan:**
```prism
module response_time
  response_state : [0..60] init 0;  // 60 possible response states
  
  // Use distributions from config
  [sync] action=3 & sick=0 ->
    0.001: (response_state'=0) +
    0.005: (response_state'=1) +
    0.020: (response_state'=2) +
    ... // All 61 states from config
  
  [sync] action=3 & sick=1 ->
    // Different distribution when sick
    ...
endmodule
```

**Data Sources:**
- Config: `responseSelect` and `responseDecide` sections
- Currently has 122 state distributions (61 states × 2 sickness levels)

---

#### 1.3 **Decision Error Modeling**
**Status:** Config loaded but not used in generation

**What needs to be added:**
- Integrate `decisionErrorDistributions` from config
- Generate error detection module
- Model correct vs. error decisions probabilistically
- Track error counts and error rates

**Implementation Plan:**
```prism
module error_detection
  error_occurred : [0..1] init 0;
  error_count : [0..MAX_ERRORS] init 0;
  
  // Probabilistic decision correctness
  [sync] action=1 & sick=0 ->  // Deciding while healthy
    0.999: (error_occurred'=0) +  // 99.9% correct
    0.001: (error_occurred'=1) & (error_count'=min(error_count+1, MAX_ERRORS));
  
  [sync] action=1 & sick=1 ->  // Deciding while sick
    0.995: (error_occurred'=0) +  // 99.5% correct
    0.005: (error_occurred'=1) & (error_count'=min(error_count+1, MAX_ERRORS));
endmodule
```

**Data Sources:**
- Config: `decisionErrorDistributions` section
- Currently defines error rates for 2 sickness levels

---

### Phase 2: Generalization Improvements (Medium Priority)

#### 2.1 **Multi-Level Sickness Support**
**Current:** Binary (0=healthy, 1=sick)
**Needed:** Support arbitrary sickness levels (0, 1, 2, ..., N)

**Implementation Requirements:**
- Read `sicknessLevels` from config (currently ignored)
- Generate sickness state variable: `sick : [0..N] init 0`
- Create probability transitions for all level combinations
- Support sickness progression/regression

---

#### 2.2 **Custom Module Templates**
**Current:** Hardcoded module structures (time, sickness, action_state, transitions)
**Needed:** Configurable module generation based on templates

**Implementation Requirements:**
- Define module templates in config
- Support custom variable declarations
- Allow flexible transition patterns
- Enable module composition

**Config Example:**
```json
{
  "modules": [
    {
      "name": "custom_module",
      "variables": [
        {"name": "var1", "range": "[0..10]", "init": "0"},
        {"name": "var2", "range": "[0..1]", "init": "1"}
      ],
      "transitions": [
        {
          "guard": "time_counter < TOTAL_TIME & var1 < 10",
          "action": "var1' = var1 + 1"
        }
      ]
    }
  ]
}
```

---

#### 2.3 **Probabilistic Transition Extraction**
**Current:** Only extracts basic pdf1, pdf2, etc. from constants
**Needed:** Extract complex probabilistic transitions from Soar rules

**Implementation Requirements:**
- Parse Soar operator preferences with probabilities
- Extract indifferent selection probabilities
- Generate weighted probabilistic branches
- Support dynamic probability calculations

---

#### 2.4 **Formula Generation**
**Status:** Not implemented

**What needs to be added:**
- Generate PRISM property formulas for model checking
- Common properties:
  - Reachability: `P=? [F time_counter=TOTAL_TIME]`
  - Safety: `P=? [G error_count <= MAX_ERRORS]`
  - Expected rewards: `R{"mission_completion"}=? [C<=TOTAL_TIME]`
  - Probabilistic bounds: `P>=0.95 [F<=TOTAL_TIME action=1]`

**Data Sources:**
- Config: `properties` section (new)
- Auto-generate common properties
- Command-line arguments

---

### Phase 3: Advanced Features (Lower Priority)

#### 3.1 **Multi-Agent Support**
**Current:** Single agent only
**Needed:** Multiple synchronized agents

**Implementation Requirements:**
- Module renaming/cloning for each agent
- Agent-specific state variables
- Inter-agent communication/synchronization
- Shared resources and coordination

---

#### 3.2 **Hierarchical State Machines**
**Current:** Flat action state transitions
**Needed:** Nested/hierarchical state structures

**Implementation Requirements:**
- Support sub-states within main states
- Hierarchical transition rules
- State composition and decomposition
- Parent-child state relationships

---

#### 3.3 **Continuous Time Models (CTMC)**
**Current:** Discrete time (DTMC) only
**Needed:** Support continuous-time Markov chains

**Implementation Requirements:**
- Generate CTMC model type
- Use rate-based transitions instead of probabilities
- Time-dependent transition rates
- Exponential distributions

---

#### 3.4 **Parametric Models**
**Current:** Fixed constant values
**Needed:** Parametric model checking support

**Implementation Requirements:**
- Generate parametric PRISM models
- Replace constants with parameters
- Support parameter ranges
- Enable sensitivity analysis

---

#### 3.5 **Partial Observability (POMDP)**
**Current:** Fully observable (MDP)
**Needed:** Partially observable Markov decision processes

**Implementation Requirements:**
- Generate POMDP model type
- Define observations separate from states
- Observation probability distributions
- Belief state tracking

---

### Phase 4: Tooling and Usability (Ongoing)

#### 4.1 **Validation and Testing**
**Needed:**
- Probability distribution validation (sum to 1.0)
- PRISM syntax validation before output
- Automated testing suite
- Example model library
- Regression tests

---

#### 4.2 **Documentation**
**Current:**
- TRANSLATION_ASSUMPTIONS.md
- CONFIG_GUIDE.md
- README examples

**Needed:**
- Complete API documentation
- Tutorial with step-by-step examples
- Best practices guide
- Troubleshooting guide
- Video demonstrations

---

#### 4.3 **Debugging and Diagnostics**
**Needed:**
- Verbose mode with detailed extraction logs
- Visualization of extracted state machines
- Diff tool for comparing Soar vs. PRISM semantics
- Interactive extraction mode
- Extraction report generation

---

#### 4.4 **Performance Optimization**
**Needed:**
- State space reduction techniques
- Symmetry detection and reduction
- Module abstraction
- Compositional verification support
- Parallel model construction

---

## Implementation Priority Matrix

| Feature | Priority | Complexity | Impact | Dependencies |
|---------|----------|------------|--------|--------------|
| Reward structures | **Critical** | Low | High | None |
| Response time distributions | **Critical** | Medium | High | None |
| Decision error modeling | **Critical** | Medium | High | None |
| Multi-level sickness | High | Medium | Medium | None |
| Formula generation | High | Low | High | None |
| Validation/testing | High | Medium | High | None |
| Custom modules | Medium | High | Medium | None |
| Probabilistic extraction | Medium | High | Medium | Parser updates |
| Documentation | Medium | Low | Medium | None |
| Multi-agent support | Low | High | Low | Custom modules |
| Hierarchical states | Low | Very High | Low | Custom modules |
| CTMC support | Low | High | Low | Major refactor |
| Parametric models | Low | Medium | Low | PRISM 4.6+ |
| POMDP support | Low | Very High | Low | Major refactor |

---

## Quick Start Guide for Contributors

### Adding a New Feature

1. **Update this document** - Add your feature to the appropriate phase
2. **Create design doc** - Detail the implementation approach
3. **Update TRANSLATION_ASSUMPTIONS.md** - Document any new patterns
4. **Implement extraction** - Add parsing logic in TimeBasedTranslator.java
5. **Add config support** - Update PrismConfig.java if needed
6. **Write tests** - Create test Soar files and expected PRISM output
7. **Update examples** - Add to test_config.json if applicable
8. **Document** - Update CONFIG_GUIDE.md and README

### Testing Your Changes

```bash
# Build
mvn clean compile

# Test with example
mvn exec:java -Dexec.mainClass="edu.fit.assist.translator.soar.main" \
  -Dexec.args="models/test_sickness.soar test_config.json"

# Validate PRISM output
prism output.pm

# Run verification
prism output.pm -pf properties.pctl
```

---

## Conclusion

The current Soar to PRISM translator provides a **solid foundation** for temporal model translation with:
- ✅ Time-based progression
- ✅ Probabilistic state transitions  
- ✅ Action state machines
- ✅ Configuration file integration
- ✅ Pattern-based extraction

The **critical next steps** are:
1. **Implement reward structures** - Essential for meaningful verification
2. **Integrate response distributions** - Use the data already loaded from config
3. **Model decision errors** - Complete the error detection pipeline

These three enhancements will transform the translator from a **structural converter** into a **complete verification-ready** model generator, enabling:
- Performance analysis
- Reliability verification
- Quality-of-service guarantees
- Probabilistic safety properties

**Estimated effort for Phase 1 completion:** 2-3 weeks of focused development.
