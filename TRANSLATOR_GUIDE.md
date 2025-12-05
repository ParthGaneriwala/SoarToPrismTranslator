# Using the Soar to PRISM Translator

## Overview

This translator converts Soar cognitive models into PRISM dtmc format. It now supports two translation modes:

1. **General Translation**: For standard Soar models (existing functionality)
2. **Time-Based Translation**: For temporal models with time-based sampling and window logic

## Time-Based Translation

### When to Use

The time-based translator is automatically selected when your Soar model includes:
- Time counters (`time-counter` variables)
- Sickness monitoring or other temporal state tracking
- Window-based sampling patterns

### Features

The time-based translator generates:

1. **Time Module**: Manages global time progression
   - Counter that increments from 0 to TOTAL_TIME
   - Synchronized transitions

2. **Sickness Module**: Monitors state with windowed sampling
   - Sampling at specific time windows (0, 300, 600, 900, 1200)
   - Commits at window ends (299, 599, 899, 1199)
   - Probabilistic state transitions
   - Name switching between mission-monitor and sickness-monitor

3. **Constants**: Extracted from initialization rules
   - TOTAL_TIME
   - Module identifiers (mission_monitor, sickness_monitor)
   - Probability values (pdf1, etc.)

### Example Output

```prism
dtmc
//PRISM model generated from Soar cognitive model
//Total time: 1200

const int TOTAL_TIME = 1200;
const int mission_monitor  = 0;
const int sickness_monitor = 1;

const double pdf1 = 0.50;

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
  ...
endmodule
```

## Usage

### Command Line

```bash
# Build the project
mvn clean package

# Run on a Soar file
mvn exec:java -Dexec.mainClass="edu.fit.assist.translator.soar.main" \
  -Dexec.args="path/to/your/model.soar"
```

### Testing

A test harness is included that bypasses JSoar loading:

```bash
mvn compile exec:java -Dexec.mainClass="edu.fit.assist.translator.soar.TestTimeTranslator"
```

## Extending the Translator

### Time Window Configuration

Time windows are now **automatically inferred** from the Soar rules:

1. **Total Time**: Extracted from `apply*initialize` rule's `total-time` value
2. **Time Interval**: Inferred by analyzing:
   - Time-counter comparisons in rule guards
   - Time-counter operations in rule actions
   - GCD (Greatest Common Divisor) of time differences found in rules

3. **Default Behavior**: If no interval can be inferred, defaults to 300

The translator automatically generates:
- **Time windows**: [0, interval, 2*interval, ..., total_time]
- **Commit times**: [interval-1, 2*interval-1, ..., total_time-1]

Example: With `total_time=1200` and inferred `interval=300`:
- Windows: [0, 300, 600, 900, 1200]
- Commits: [299, 599, 899, 1199]

### Adding Action Modules

To add support for additional modules (select, decide, error, etc.):

1. Analyze the Soar rules to identify state machines
2. Extract probabilistic distributions from rules or external data
3. Implement module generation in `TimeBasedTranslator.generateActionModules()`

Example pattern:
```java
private String generateSelectModule() {
    StringBuilder sb = new StringBuilder();
    sb.append("module select\n");
    sb.append("  select_dist : [0..60] init 60;\n");
    sb.append("  selecting : [0..1] init 0;\n");
    sb.append("  select_done : [0..1] init 0;\n");
    // ... generate transitions
    sb.append("endmodule\n");
    return sb.toString();
}
```

### Customizing Time Windows (Advanced)

While time windows are automatically inferred, you can understand the inference process:

1. The translator scans all parsed Soar rules for time-related values
2. It extracts numeric values from guards and actions involving `time-counter`
3. It calculates the GCD of differences between these values
4. This GCD becomes the time interval

If you need to override this behavior, you can:
- Ensure your Soar rules contain explicit time-counter comparisons
- Use consistent time intervals throughout your rules
- The inference algorithm will find the common interval pattern

## Limitations

1. **Distribution Data**: Complex probability distributions (like those in select/decide modules) may need to be provided separately or computed from external sources.

2. **Module Interactions**: Complex inter-module dependencies may require manual adjustment.

3. **State Abstraction**: The translator makes simplifying assumptions about state representation.

## Future Enhancements

- Automatic extraction of action state machines from Soar transition rules
- Support for reading probability distributions from external files
- More sophisticated guard condition translation
- Integration with PRISM model checker for validation
