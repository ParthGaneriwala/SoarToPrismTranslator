# 🔁 SoarToPrismTranslator

This repository contains an ongoing effort to **translate cognitive models developed in Soar into probabilistic models for PRISM**, a formal verification tool for analyzing discrete-time stochastic systems.

The goal of this project is to explore how symbolic cognitive reasoning (e.g., via Soar) can be mapped to probabilistic formalisms (e.g., DTMCs in PRISM) to enable **formal analysis, safety verification**, and **behavioral validation** of cognitive agents.

---

## 🔍 Contents

### 🔬 `Attention/`
Contains original, hand-authored PRISM models representing attentional cognitive mechanisms. These were written directly in the PRISM modeling language using DTMC modules.

### 🩺 `Physiology/`
Includes PRISM models focused on physiological or affective states — for instance, cybersickness (`sick.pm`) and its relationship to user state transitions such as SSQ levels, gender susceptibility, and headset characteristics.

### 🔁 `TranslatedSoarModels/`
Contains PRISM models **manually translated** from Soar cognitive agents. These translations are based on agents designed to replicate the logic of the `Physiology` models. The current translation is derived from the `sick.pm` model and its corresponding Soar agent.

> ✅ **Link to original Soar agent:**  
> [ICS_SOAR — prism-sick branch](https://github.com/CandiceChambers1/ICS_SOAR/tree/prism-sick)

---

## 🧠 Why This Matters

This project supports:
- **Cognitive modeling + verification**: bridging symbolic agent reasoning with formal analysis.
- **Model checking of human-like agents**: PRISM models can now validate safety properties of Soar-based cognitive behaviors.
- **Foundations for automation**: Manual translations pave the way for future automated tools that convert Soar → PRISM for use in tools like nuXmv or UPPAAL.

---

## 🚧 Future Work

- Automate translation from Soar working memory + productions → PRISM modules.
- Extend coverage to Soar’s goal stack, chunking behavior, or RL mechanisms.
- Validate translation semantics using trace comparison between Soar and PRISM simulations.
- Incorporate full toolchain for agent + environment + property specification.

---




