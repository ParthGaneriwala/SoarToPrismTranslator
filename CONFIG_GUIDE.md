# External Configuration Support

## Overview

The Soar to PRISM translator now supports external configuration files that provide probability distributions, model parameters, and module mappings. This allows you to supplement the Soar code with data from external models like the C# CyberSicknessModel.

## Configuration File Format

The configuration file should be in JSON format and can include:

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

These parameters correspond to properties from the C# `CyberSicknessModel` class.

### Constants
```json
{
  "constants": {
    "mission_monitor": 0,
    "sickness_monitor": 1,
    "pdf1": 0.9
  }
}
```

Define numeric and string constants used in the PRISM model.

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

### Module Definitions
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
      ],
      "variables": [
        {
          "name": "sick",
          "type": "int",
          "range": "[0..1]",
          "init": 0
        }
      ]
    }
  ]
}
```

Define PRISM modules and map them to Soar production rules using regex patterns.

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

When a configuration file is provided:
1. **Model parameters** (total time, intervals) are taken from the config
2. **Constants** are loaded from the config
3. **Time windows** are calculated from `sicknessSamplingInterval`
4. **Probability distributions** are available for generating modules
5. **Module mappings** link Soar rules to PRISM modules

Without a configuration file:
1. Model parameters are extracted from Soar `apply*initialize` rule
2. Time intervals are inferred from rule patterns
3. Constants use default values
4. Only basic modules are generated

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
