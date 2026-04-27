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

| | | |
|:---:|:---:|:---:|
| ![Solar system view](https://github.com/user-attachments/assets/aa06d343-acc0-436c-b09c-caae5cf67feb) | ![Planet and orbits](https://github.com/user-attachments/assets/a2cbb0dd-e39f-483e-aa79-8b5215eb307c) | ![Close-up view](https://github.com/user-attachments/assets/a18981f4-fa01-49fb-aac5-27f733df8fab) |

---

## 🛸 Space Mission Simulation

Create, configure, and visualize complete space missions — from launch vehicle design to orbital insertion.

- **Create** custom missions: define your launch vehicle, target orbit, and mission profile
- **Configure** every parameter: stage masses, thrust, ISP, payload, target altitude, and more
- **Visualize** the resulting trajectory in 3D, with full playback controls to step through every phase of the flight

Under the hood, OrbitLab uses **CMA-ES trajectory optimization** to find the optimal flight profile for your target orbit, and a **high-fidelity physics model** (including Earth's gravitational oblateness) to make the resulting trajectory realistic. Once optimized, missions are deterministic and can be replayed and analyzed in 3D.

| | | |
|:---:|:---:|:---:|
| ![Mission setup](https://github.com/user-attachments/assets/34a932b2-425d-4ad6-97b0-b1b746eb84ea) | ![Mission in flight](https://github.com/user-attachments/assets/8cf01fbd-ba35-4e17-90b9-40cf83e316db) | ![Orbit insertion](https://github.com/user-attachments/assets/a17ff4a5-b8ee-43ab-a2df-4e36c6efb1b7) |

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
