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
| ![Mission setup](https://github.com/user-attachments/assets/34a932b2-425d-4ad6-97b0-b1b746eb84ea) | ![Mission in flight](https://github.com/user-attachments/assets/ed7e4722-5dc5-4831-bea5-8363ad3aca28) | ![Orbit insertion](https://github.com/user-attachments/assets/a17ff4a5-b8ee-43ab-a2df-4e36c6efb1b7) |

### Roadmap

OrbitLab's mission simulation is actively expanding:

- **Moon flyby**
- **Moon orbiting**
- **Eclipses/Inter-body penumbra**

---

## 🛠️ Getting Started

### ✅ Prerequisites

#### Running the released bundles (recommended)

| Requirement | Details |
|---|---|
| **OS** | Windows 10+, Linux x86_64 (glibc 2.31+), macOS 12+ |
| **GPU / OpenGL** | **OpenGL 3.2 core profile** or newer — the shaders are `GLSL150`. Up-to-date GPU drivers required |
| **Disk space** | **~10 GB free in your user HOME** for the generated datasets, plus ~400 MB for the extracted bundle |
| **RAM** | **8 GB minimum, 16 GB recommended** — the ephemeris generator runs with `-Xmx6g`, the orbit generator with `-Xmx4g` |
| **CPU** | 4 cores minimum; generation and CMA-ES optimization are multi-threaded and scale with core count |
| **Java** | **None.** Every archive embeds its own Java 21 runtime (Temurin) |

> ⚠️ **Software / remote OpenGL** (RDP, plain VNC, `llvmpipe`, some VMs without GPU passthrough) usually
> exposes only OpenGL 2.1 and will fail to start. A physical display with a real GPU is expected.

#### Building from sources

- **JDK 21+** — the Gradle toolchain targets Java 21
- **Gradle** — wrapper included, no installation needed

---

### 🚀 Quick start (released bundle)

Grab the archive for your platform from the
**[Releases page](https://github.com/smousseur/orbitlab/releases/latest)**
(`orbitlab-vX.Y.Z-windows.zip`, `-linux.zip` or `-macos.zip`), extract it, then run the three
executables **in this exact order**:

| # | Executable | What it does                                                                                                     | Output |
|:-:|---|------------------------------------------------------------------------------------------------------------------|---|
| 1️⃣ | `ephemeris-generator` | Computes the ephemeris dataset (position/velocity + rotation of all bodies, 1989 → 2100) ≈ 2 hours of processing | `~/.orbitlab/dataset/ephemeris` |
| 2️⃣ | `orbits-generator` | Computes the pre-traced orbit paths, **from the ephemeris dataset** ≈ few seconds of processing                  | `~/.orbitlab/dataset/orbits` |
| 3️⃣ | `Orbitlab` | The application itself                                                                                           | — |

> The order matters: `orbits-generator` consumes what step 1 produced, and `Orbitlab` renders an
> **empty scene** if either dataset is missing. Both generators are console applications — keep the
> window open, they log their progress and total elapsed time.

Executable locations after extraction:

| Platform | Main app | Generators |
|---|---|---|
| **Windows** | `Orbitlab\Orbitlab.exe` | `Orbitlab\ephemeris-generator.exe`, `Orbitlab\orbits-generator.exe` |
| **Linux** | `Orbitlab/bin/Orbitlab` | `Orbitlab/bin/ephemeris-generator`, `Orbitlab/bin/orbits-generator` |
| **macOS** | `Orbitlab.app/Contents/MacOS/Orbitlab` | `Orbitlab.app/Contents/MacOS/ephemeris-generator`, `.../orbits-generator` |

<details>
<summary><b>macOS:</b> Gatekeeper blocks the launch (binaries are ad-hoc signed only)</summary></details>

```bash
xattr -dr com.apple.quarantine /path/to/Orbitlab.app
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
