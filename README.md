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

---

## 🛸 Space Mission Simulation

Design and simulate rocket missions from launchpad to orbit — with automatic trajectory optimization.

### Current capability: Low Earth Orbit (LEO)

OrbitLab can simulate a complete LEO launch campaign:

```
  Liftoff
    │
    ▼ Vertical ascent          (burn straight up to clear the atmosphere)
    │
    ▼ Gravity turn             (pitch over and let gravity shape the trajectory)
    │
    ▼ Stage separation         (jettison spent booster stages)
    │
    ▼ Coasting arc             (follow a ballistic arc to apoapsis)
    │
    ▼ Circularization burn     (final burn to achieve circular orbit)
    │
    ▼ 🛰️ On station — LEO achieved!
```

**Key features:**
- **Multi-stage rocket definition** — model your vehicle's mass, thrust, and ISP for each stage
- **Automatic trajectory optimization** — OrbitLab searches for the optimal gravity-turn profile to hit your target orbit altitude
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

---

## 🗺️ Project Structure (abridged)

```
src/main/java/com/smousseur/orbitlab/
├── OrbitLabApplication.java   # Entry point
├── app/                       # Configuration and clock
├── engine/                    # 3D rendering (planets, scene, camera)
├── states/                    # Application states (scene, camera, time, FX...)
├── simulation/
│   ├── ephemeris/             # Real-time body position computation
│   ├── orbit/                 # Orbit path visualization
│   └── mission/               # Mission model, stages, optimizer, player
└── ui/                        # GUI widgets (timeline...)
```
