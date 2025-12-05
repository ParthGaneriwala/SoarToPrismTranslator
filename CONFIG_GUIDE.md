# External Configuration Support

## Overview

The Soar to PRISM translator now supports external configuration files that provide probability distributions, model parameters, and module mappings. This allows you to supplement the Soar code with data from external models like the C# CyberSicknessModel.

## Configuration File Format

The configuration file should be in JSON format. It is used to **supplement** information extracted from Soar rules with data that cannot be derived from the rules themselves (like probability distributions).

### Important: Extraction Priority

1. **Primary Source: Soar Rules** - Constants, module names, variables, and structure are extracted from Soar rules
2. **Supplemental: Config File** - Probability distributions, response distributions, and other statistical data not present in Soar

The config file should contain only:
- Probability distributions (sickness tables, response distributions)
- Model parameters (sampling intervals, durations)
- Error distributions
- Additional constants NOT present in Soar rules

Do NOT duplicate information already in Soar rules (like module names or basic constants).

### Model Parameters
```json
{
  "model": {
    "type": "dtmc",
    "maxErrorCount": 5,
    "modelResolution": 1.0,
    "experimentDuration": 1200,
    "sicknessSamplingInterval": 300,
    "sicknessLevels": 2,
    "responseDuration": 60
  }
}
```

These parameters correspond to properties from the C# `CyberSicknessModel` class and provide timing/structural information.

### Constants (Optional - Only for Supplemental Data)
```json
{
  "constants": {
    "pdf2": 0.8,
    "pdf3": 0.7,
    "pdf4": 0.6
  }
}
```

**NOTE**: Only include constants here that are **NOT** in your Soar rules. Constants like `mission_monitor`, `sickness_monitor`, and `pdf1` are automatically extracted from Soar `apply*initialize` and related rules.

### Sickness Probability Table
```json
{
  "sicknessProbabilityTable": {
    "0,0,0": 0.9,
    "0,0,1": 0.1,
    "300,0,0": 0.9,
    "300,0,1": 0.1
  }
}
```

3D probability table with keys in format `"time,currentLevel,nextLevel"` mapping to transition probabilities.

### Response Distributions
```json
{
  "responseSelect": {
    "sickness0": {
      "type": "discrete",
      "states": 61,
      "probabilities": [
        {"state": 0, "probability": 0.048770575499285984},
        {"state": 1, "probability": 0.0463920064647545}
      ]
    }
  },
  "responseDecide": {
    "sickness0": {
      "type": "discrete",
      "states": 61,
      "probabilities": [...]
    }
  }
}
```

Probability distributions for response selection and decision making at different sickness levels.

### Error Distributions
```json
{
  "decisionErrorDistributions": {
    "sickness0": {
      "correctProbability": 0.999,
      "errorProbability": 0.001
    },
    "sickness1": {
      "correctProbability": 0.998,
      "errorProbability": 0.002
    }
  }
}
```

Error probabilities for decision making based on sickness level.

### Module Definitions (Optional - For Complex Mappings)
```json
{
  "modules": [
    {
      "name": "sickness",
      "type": "state_machine",
      "soarRulePatterns": [
        ".*sickness.*",
        ".*notsick.*",
        "propose\\*check\\*.*"
      ]
    }
  ]
}
```

**NOTE**: Module structure and variables are automatically extracted from Soar rules. Only define modules in config if you need:
- Custom regex patterns for rule-to-module mapping
- Probability distributions associated with specific modules
- Additional metadata not derivable from Soar

The translator automatically:
- Identifies modules from Soar rule names (e.g., rules containing "sickness" → sickness module)
- Extracts variables from `apply*initialize` rule
- Infers variable ranges from usage patterns

## Usage

### Command Line

```bash
# With configuration file
mvn exec:java -Dexec.mainClass="edu.fit.assist.translator.soar.main" \
  -Dexec.args="path/to/model.soar path/to/config.json"

# Without configuration file (uses rule inference)
mvn exec:java -Dexec.mainClass="edu.fit.assist.translator.soar.main" \
  -Dexec.args="path/to/model.soar"
```

### Configuration Priority

The translator follows this priority order:

**1. Soar Rules (Primary Source):**
- Module names extracted from rule names
- Constants from `apply*initialize` rule
- Variables from initialization and usage
- State transitions from propose/apply rules
- Time values from guards and actions

**2. Config File (Supplemental Data):**
- Probability distributions (sickness tables, response distributions)
- Model parameters (sampling intervals, durations)
- Error distributions
- Additional constants not in Soar

**3. Inference (Fallback):**
- Time intervals inferred from rule patterns using GCD algorithm
- Default values when neither Soar nor config provide data

**What the config should contain:**
- ✅ Probability distributions from statistical models
- ✅ Response time distributions
- ✅ Error rate distributions  
- ✅ Model parameters (sampling intervals, max error counts)

**What the config should NOT contain:**
- ❌ Module names (extracted from Soar rule names)
- ❌ Basic constants (extracted from `apply*initialize`)
- ❌ Variable definitions (extracted from Soar rules)
- ❌ State machine structure (derived from Soar transitions)

Without a configuration file:
1. All information extracted from Soar rules
2. Time intervals inferred from patterns
3. Default probability values used
4. Basic modules generated

## Exporting from C# CyberSicknessModel

To export your C# model to this JSON format, you can create a serialization method:

```csharp
public string ExportToJson()
{
    var config = new {
        model = new {
            type = "dtmc",
            maxErrorCount = MaxErrorCount,
            modelResolution = ModelResolution,
            experimentDuration = ExperimentDuration,
            sicknessSamplingInterval = SicknessSamplingInterval,
            sicknessLevels = SicknessLevels,
            responseDuration = ResponseDuration
        },
        sicknessProbabilityTable = SicknessProbabilityTable?
            .ToDictionary(
                kvp => $"{kvp.Key.Item1},{kvp.Key.Item2},{kvp.Key.Item3}",
                kvp => kvp.Value
            ),
        responseSelect = ResponseSelect?
            .ToDictionary(
                kvp => $"sickness{kvp.Key}",
                kvp => new {
                    type = "discrete",
                    states = kvp.Value.States,
                    probabilities = kvp.Value.Probabilities
                        .Select((p, i) => new { state = i, probability = p })
                        .ToArray()
                }
            ),
        // ... similar for responseDecide and decisionErrorDistributions
    };
    
    return JsonSerializer.Serialize(config, new JsonSerializerOptions { 
        WriteIndented = true 
    });
}
```

## Benefits

1. **Separation of Concerns**: Keep Soar cognitive model separate from probabilistic model data
2. **Flexibility**: Update probabilities without changing Soar code
3. **Data Sources**: Integrate data from experiments, simulations, or statistical models
4. **Precision**: Use full-precision probabilities instead of rounded values
5. **Modularity**: Define complex modules with rich probability distributions

## Example

See `config_example.json` for a complete example configuration file.
