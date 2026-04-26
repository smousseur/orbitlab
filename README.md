# 🚀 OrbitLab

**A real-time 3D solar system explorer and space mission simulator.**

OrbitLab lets you fly through an accurate, living model of the solar system — and plan, optimize, and replay rocket missions from liftoff to orbit.

---

## ✨ What OrbitLab Does

OrbitLab is built around two core experiences:

| | |
|---|---|
| 🪐 **Explore the Solar System** | Navigate a real-time 3D model of all planets, driven by actual ephemeris data |
| 🛸 **Simulate Space Missions** | Design multi-stage rockets, optimize trajectories, and watch missions unfold |

---

## 🪐 3D Solar System Visualization

Fly through a physically accurate, animated solar system — for the sheer joy of it.

- **All 10 solar system bodies** rendered as detailed 3D models (Sun → Pluto), scaled to their real physical sizes
- **Live orbital motion** — planet positions are computed from real ephemeris data and updated in real-time
- **Orbit paths** traced for every body, visible at solar and planetary scales simultaneously
- **Simulation clock** you can speed up, slow down, or rewind — watch years of orbital motion in seconds

> The rendering engine handles the extreme scale of space (Mercury's orbit vs. Pluto's) without any floating-point precision artifacts, keeping the view crisp at every zoom level.

| | |
|:---:|:---:|
| ![Solar system overview](https://github.com/user-attachments/assets/34a932b2-425d-4ad6-97b0-b1b746eb84ea) | ![Planet close-up with orbit path](https://github.com/user-attachments/assets/8cf01fba-ba35-4e17-90b9-40cf83e316db) |

---

## 🛸 Space Mission Simulation

Design and simulate rocket missions from launchpad to orbit — with automatic trajectory optimization.

OrbitLab simulates a complete LEO launch campaign through five sequential phases:

| Phase | Screenshot |
|---|:---:|
| **Vertical ascent** — burn straight up to clear the atmosphere | ![Vertical ascent](https://github.com/user-attachments/assets/a18981f4-fa01-49fb-aac5-27f733df8fab) |
| **Gravity turn** — pitch over and let gravity shape the trajectory | ![Gravity turn](https://github.com/user-attachments/assets/a2cbb0dd-e39f-483e-aa79-8b5215eb307c) |
| **Coasting arc** — follow a ballistic arc to apoapsis | ![Coasting arc](https://github.com/user-attachments/assets/aa06d343-acc0-436c-b09c-caae5cf67feb) |
| **Circularization burn** — final burn to achieve circular orbit | ![Circularization burn](https://github.com/user-attachments/assets/a17ff4a5-b8ee-43ab-a2df-4e36c6efb1b7) |

**Key features:**
- **Multi-stage rocket definition** — model your vehicle's mass, thrust, and ISP for each stage
- **Automatic trajectory optimization** — OrbitLab uses CMA-ES to find the optimal gravity-turn profile for your target orbit altitude
- **High-fidelity physics** — Earth's gravitational field (including oblateness effects) is modeled for realistic trajectory computation
- **Deterministic replay** — once optimized, missions are replayed step-by-step and visualized in 3D

### Roadmap

OrbitLab's mission simulation is actively expanding:

- **GTO (Geostationary Transfer Orbit)** — coming soon
- **GEO insertion** — high-energy missions to geostationary orbit
- **Interplanetary missions** — Hohmann transfers and beyond (Mars, Venus, ...)
- **More vehicle types** — upper stages, orbital tugs

---

## 🛠️ Getting Started

### Prerequisites

- **Java 17+** (the codebase uses records and sealed interfaces)
- **Gradle** (wrapper included — no installation needed)
- **Orekit data** — place `orekit-data.zip` in `src/main/resources/` (required for ephemeris and physics; not included in the repo due to size)

> Download `orekit-data.zip` from the [Orekit data repository](https://gitlab.orekit.org/orekit/orekit-data).

### Build & Run

```bash
# Clone
git clone https://github.com/smousseur/orbitlab.git
cd orbitlab

# Build
./gradlew build

# Run all tests
./gradlew test

# Package as JAR
./gradlew jar
```

---

## 🏗️ Tech Stack

| Component | Library |
|---|---|
| 3D Rendering | JMonkeyEngine 3 |
| Orbital Mechanics | Orekit |
| GUI | Lemur |
| Async / Reactive | Reactor Core |
| Logging | Log4j 2 |
| Testing | JUnit 5 |
