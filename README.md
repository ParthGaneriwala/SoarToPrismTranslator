# Soar to PRISM Translator

This project provides a tool for translating cognitive models written in the [Soar cognitive architecture](https://soar.eecs.umich.edu/) into equivalent models in the [PRISM model checker](https://www.prismmodelchecker.org/) formalism. The translation is designed to support formal verification of cognitive models using PRISM’s probabilistic model checking capabilities.

---

## Motivation

The purpose of this translator is to enable:
- Formal analysis of Soar agent behavior in safety-critical domains.
- Integration of cognitive architectures with model checking pipelines.
- Automated reasoning over decision-making policies using probabilistic semantics.

---

## Project Structure

```bash
soar-to-prism-translator/
├── src/
│   └── Soar.g4             # ANTLR4 grammar for parsing Soar rules
├── translator/
│   ├── Main.java           # Entry point for the translation tool
│   ├── SoarToPrismVisitor.java # Translation logic using ANTLR visitor pattern
│   └── ...
├── models/
│   ├── soar_model.soar     # Sample Soar input file
│   └── expected_output.pm  # Corresponding PRISM output
├── README.md
└── pom.xml  # Build file (Maven)
```

---

## ⚙️ Requirements

- Java 11+
- [ANTLR4](https://www.antlr.org/)
- Gradle or Maven (for building the project)

[//]: # (---)

[//]: # ()
[//]: # (## 🚀 Getting Started)

[//]: # ()
[//]: # (### 1. Clone the Repository)

[//]: # (```bash)

[//]: # (git clone https://github.com/yourusername/soar-to-prism-translator.git)

[//]: # (cd soar-to-prism-translator)

[//]: # (```)

[//]: # ()
[//]: # (### 2. Generate the ANTLR4 Parser)

[//]: # (If you're using Gradle:)

[//]: # (```bash)

[//]: # (./gradlew generateGrammarSource)

[//]: # (```)

[//]: # ()
[//]: # (Or use ANTLR manually:)

[//]: # (```bash)

[//]: # (antlr4 -Dlanguage=Java grammar/Soar.g4 -o src/gen)

[//]: # (```)

[//]: # ()
[//]: # (### 3. Build the Project)

[//]: # (```bash)

[//]: # (./gradlew build)

[//]: # (```)

[//]: # ()
[//]: # (### 4. Run the Translator)

[//]: # (```bash)

[//]: # (java -cp build/libs/soar-to-prism-translator.jar \)

[//]: # (     soar.Main examples/soar_model.soar output/generated_model.pm)

[//]: # (```)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## 🧪 Example)

[//]: # ()
[//]: # (### Input &#40;Soar&#41;:)

[//]: # (```soar)

[//]: # (sp {decide*init)

[//]: # (   &#40;state <s> ^superstate nil&#41;)

[//]: # (-->)

[//]: # (   &#40;<s> ^operator <o>&#41;)

[//]: # (   &#40;<o> ^name explore&#41;)

[//]: # (})

[//]: # (```)

[//]: # ()
[//]: # (### Output &#40;PRISM&#41;:)

[//]: # (```prism)

[//]: # (module SoarModel)

[//]: # (  s : [0..2] init 0;)

[//]: # ()
[//]: # (  [] s=0 -> 1.0 : &#40;s'=1&#41;; // explore)

[//]: # (  [] s=1 -> 1.0 : &#40;s'=2&#41;; // next action)

[//]: # (endmodule)

[//]: # (```)

[//]: # (---)

[//]: # (## 🛠️ Features)

[//]: # ()
[//]: # (- ✅ Supports production rule parsing &#40;`sp` rules&#41;)

[//]: # (- ✅ Handles state-operator-goal structures)

[//]: # (- ✅ Supports conditional logic and rule prioritization &#40;in progress&#41;)

[//]: # (- 🔜 Plans for handling numeric preferences and probabilistic transitions)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## 📖 Using Your Own Soar Models)

[//]: # ()
[//]: # (Place your `.soar` file in the `examples/` folder, then run:)

[//]: # (```bash)

[//]: # (java -cp build/libs/... soar.Main examples/your_model.soar output/your_model.pm)

[//]: # (```)

[//]: # ()
[//]: # (---)

## Architecture

- Uses **ANTLR4** to parse Soar models.
- Implements a **Visitor** or **Listener** pattern to walk the parse tree.
- Outputs equivalent **PRISM modules**, assuming a deterministic or DTMC structure.

---

## Limitations

- Currently supports only basic production rules and deterministic transitions.
- Does not yet support numeric preference comparisons or impasses.
- Translation assumes one-to-one rule-to-transition mapping.

---

## 👥 Authors

- Parth Ganeriwala (@ParthGaneriwala)

---

## 🔗 References

- [Soar Cognitive Architecture](https://soar.eecs.umich.edu/)
- [PRISM Model Checker](https://www.prismmodelchecker.org/)
- [ANTLR4 Documentation](https://github.com/antlr/antlr4)

```