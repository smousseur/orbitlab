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

- **All 11 solar system bodies** — the Sun, the eight planets, Pluto and the Moon — rendered as detailed 3D models, scaled to their real physical sizes
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
- **Earth's atmosphere** is simulated: the vehicle feels aerodynamic drag
- **Stage jettisoning**: spent stages separate from the vehicle and fall back as debris
- **Controlled deorbiting**: an optional deorbit burn at the end of the mission brings the payload back through atmospheric reentry

Under the hood, OrbitLab uses **CMA-ES trajectory optimization** to find the optimal flight profile for your target orbit, and a **high-fidelity physics model** (including Earth's gravitational oblateness) to make the resulting trajectory realistic. Once optimized, missions are deterministic and can be replayed and analyzed in 3D.

| | | |
|:---:|:---:|:---:|
| ![Mission setup](https://github.com/user-attachments/assets/34a932b2-425d-4ad6-97b0-b1b746eb84ea) | ![Mission in flight](https://github.com/user-attachments/assets/ed7e4722-5dc5-4831-bea5-8363ad3aca28) | ![Orbit insertion](https://github.com/user-attachments/assets/a17ff4a5-b8ee-43ab-a2df-4e36c6efb1b7) |

---

## 🛠️ Getting Started

### ✅ Prerequisites

#### Running the released bundles (recommended)

| Requirement | Details |
|---|---|
| **OS** | Windows 10+, Linux x86_64 (glibc 2.31+), macOS 12+ |
| **GPU / OpenGL** | **OpenGL 3.2 core profile** or newer — the shaders are `GLSL150`. Up-to-date GPU drivers required |
| **Disk space** | **~7.6 GB free in your user HOME** for the data downloaded at first launch, plus ~360 MB for the extracted bundle |
| **RAM** | **4 GB minimum, 8 GB recommended** — the application uses up to about 2 GB |
| **CPU** | 4 cores minimum; CMA-ES optimization is multi-threaded and scales with core count |
| **Network** | Internet access at first launch only (~7.6 GB from GitHub). A system proxy is used if one is configured |
| **Java** | **None.** Every archive embeds its own Java 21 runtime (Temurin) |

> ⚠️ **Software / remote OpenGL** (RDP, plain VNC, `llvmpipe`, some VMs without GPU passthrough) usually
> exposes only OpenGL 2.1 and will fail to start. A physical display with a real GPU is expected.

#### Building from sources

- **JDK 21+** — the Gradle toolchain targets Java 21
- **Gradle** — wrapper included, no installation needed

##### Dataset maintenance

The data downloaded at first launch is produced and published with Gradle tasks, for maintainers:

| Task | What it does |
|---|---|
| `./gradlew ephemerisGen` | Computes the ephemeris dataset into `~/.orbitlab/dataset/ephemeris` (about two hours) |
| `./gradlew orbitGen` | Computes the orbit paths into `~/.orbitlab/dataset/orbits`, from the ephemeris dataset |
| `./gradlew datasetPack -PdatasetTag=<tag>` | Prepares the files of a dataset release and writes the embedded manifest, `src/main/resources/dataset-manifest.json` |
| `./gradlew datasetVerify` | Downloads every file of the published dataset release and checks it against the embedded manifest |

> The generators write into the folder the application checks at start-up: a regenerated file whose
> size differs from the embedded manifest is replaced by the release's copy at the next launch.

---

### 🚀 Quick start (released bundle)

Grab the archive for your platform from the
**[Releases page](https://github.com/smousseur/orbitlab/releases/latest)**
(`orbitlab-vX.Y.Z-windows.zip`, `-linux.zip` or `-macos.zip`), extract it, then run `Orbitlab`:

| Platform | Executable |
|---|---|
| **Windows** | `Orbitlab\Orbitlab.exe` |
| **Linux** | `Orbitlab/bin/Orbitlab` |
| **macOS** | `Orbitlab.app/Contents/MacOS/Orbitlab` |

#### First launch

On first launch, OrbitLab downloads its data — ephemerides and orbit paths, about 7.6 GB — into
`~/.orbitlab/dataset`. A start-up screen shows the file being downloaded, the progress,
the speed and the time left: at 5 MB/s, it takes about 25 minutes. The simulation starts as soon as
the data is complete.

- An interrupted download **resumes** where it stopped. **Cancel** closes the application and keeps
  what was already downloaded.
- If the download fails, the screen gives the cause, with **Retry** and **Quit**.
- **Later launches** start straight away, without network.
- **Upgrading from an earlier version**, which generated its data locally: the files already in
  `~/.orbitlab/dataset` are checked once, in a few seconds, kept when identical and downloaded again
  otherwise.

The log is written to `~/.orbitlab/logs/orbitlab.log`.

<details>
<summary><b>macOS:</b> Gatekeeper blocks the launch (binaries are ad-hoc signed only)</summary>

```bash
xattr -dr com.apple.quarantine /path/to/Orbitlab.app
```

</details>

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
