# Summary of Changes

## Overview

This PR implements a specialized time-based translator that converts Soar cognitive models into PRISM dtmc format with synchronized modules and window-based sampling, matching the format specified in the requirements.

## What Was Implemented

### 1. TimeBasedTranslator Class
A new translator specifically designed for temporal models with:
- **Time Module Generation**: Creates a global time counter that progresses from 0 to TOTAL_TIME
- **Sickness Module Generation**: Implements windowed probabilistic sampling with:
  - Sampling at window starts (0, 300, 600, 900, 1200)
  - Probabilistic state transitions based on pdf1
  - Commits at window ends (299, 599, 899, 1199)
  - Name-based module switching (mission_monitor ↔ sickness_monitor)
  - Proper else/default transition handling

### 2. Automatic Translator Selection
Modified `main.java` to automatically detect time-based models by checking for:
- `time-counter` variables
- `total-time` specifications
- Sickness-related or time-related rule names

### 3. Test Infrastructure
Created `TestTimeTranslator.java` for testing without JSoar dependencies.

### 4. Documentation
Added `TRANSLATOR_GUIDE.md` with:
- Usage instructions
- Output format examples
- Extensibility guide for adding modules
- Limitations and future enhancements

## Example Output

The translator now generates PRISM code like this:

```prism
dtmc
//PRISM model generated from Soar cognitive model
//Total time: 1200

const int TOTAL_TIME = 1200;
const int mission_monitor  = 0;
const int sickness_monitor = 1;

const double pdf1 = 0.90;

module time
  time_counter : [0..TOTAL_TIME] init 0;
  [sync] time_counter <  TOTAL_TIME -> (time_counter' = time_counter + 1);
  [sync] time_counter =  TOTAL_TIME -> (time_counter' = time_counter);
endmodule

module sickness
  name             : [0..1] init mission_monitor;
  sick             : [0..1] init 0;
  ts               : [0..1] init 0;
  sickness_checked : [0..1] init 0;

  // ---- commit at window end ----
  [sync] time_counter =  299 -> (sick' = ts);
  [sync] time_counter =  599 -> (sick' = ts);
  [sync] time_counter =  899 -> (sick' = ts);
  [sync] time_counter = 1199 -> (sick' = ts);

  // ---- sample at window start (one check per visit) ----
  [sync] time_counter =    0 & name=sickness_monitor & sickness_checked=0 & sick=0 ->
        pdf1     : (ts'=0) & (sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (sickness_checked'=1);
  [sync] time_counter =    0 & name=sickness_monitor & sickness_checked=0 & sick=1 ->
        1 : (ts'=1) & (sickness_checked'=1);
  
  // ... (additional window samples)
  
  [sync] name = sickness_monitor & sickness_checked = 1 &
  time_counter != 0   & time_counter != 300 & time_counter != 600 &
  time_counter != 900 & time_counter != 1200 &
  time_counter != 299 & time_counter != 599 &
  time_counter != 899 & time_counter != 1199
->
  (name' = mission_monitor) & (sickness_checked' = 0);

  [sync]
    time_counter != 299 & time_counter != 599 & time_counter != 899 & time_counter != 1199 &
    !((time_counter =    0 | time_counter =  300 | time_counter =  600 | time_counter =  900 | time_counter = 1200) &
      name = sickness_monitor & sickness_checked = 0) &
    !(name = sickness_monitor & sickness_checked = 1)
  ->
    (sick' = sick) & (ts' = ts) & (sickness_checked' = sickness_checked) & (name' = name);
endmodule
```

## Key Features Implemented

✅ Synchronized transitions with `[sync]` labels
✅ Time-based progression
✅ Window-based probabilistic sampling
✅ Proper else/default transitions
✅ Commit points for state updates
✅ Module name switching
✅ Constants extraction from Soar initialization rules
✅ Probability value handling (pdf1)

## Quality Checks

- ✅ Code compiles without errors
- ✅ Code review completed, all feedback addressed
- ✅ Security scan (CodeQL) passed with 0 alerts
- ✅ Test harness validates output format
- ✅ Documentation provided

## Limitations and Future Work

The current implementation focuses on the TIME and SICKNESS modules. Additional modules (select, decide, error) mentioned in the requirements would require:

1. **Distribution Data**: The extensive probability distributions shown in the target PRISM code (60 states with specific probabilities) need to be:
   - Computed from statistical models
   - Read from external data files
   - Or manually specified

2. **State Machine Extraction**: Automatically extracting action state machines from Soar transition rules would enhance the translator's capabilities.

3. **Customization**: Time windows are currently hardcoded but could be inferred from Soar guards in future versions.

## How to Use

```bash
# Build
mvn clean package

# Run on a time-based Soar model
mvn exec:java -Dexec.mainClass="edu.fit.assist.translator.soar.main" \
  -Dexec.args="path/to/your/model.soar"

# Test
mvn compile exec:java -Dexec.mainClass="edu.fit.assist.translator.soar.TestTimeTranslator"
```

## Files Changed

### New Files
- `src/main/java/edu/fit/assist/translator/soar/TimeBasedTranslator.java`
- `src/main/java/edu/fit/assist/translator/soar/TestTimeTranslator.java`
- `TRANSLATOR_GUIDE.md`

### Modified Files
- `src/main/java/edu/fit/assist/translator/soar/main.java`
- `pom.xml` (Java version 21→17)

### Generated Files (for testing)
- `test_output.pm`
- `output1.pm`
