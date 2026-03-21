# CLAUDE.md — OrbitLab

This file provides context for AI assistants working in this repository.

## Project Overview

OrbitLab is a desktop 3D orbital mechanics simulation and mission planning application. It combines:
- **JMonkeyEngine 3 (JME3)**: Real-time 3D rendering
- **Orekit**: Orbital mechanics, ephemeris, and propagation
- **CMA-ES optimization**: Trajectory optimization for missions (e.g., gravity turn, LEO insertion)

The application visualizes the solar system, computes spacecraft orbits, and simulates and optimizes launch missions.

---

## Build System

**Tool:** Gradle (use the wrapper)

```bash
./gradlew build       # Compile + run all tests
./gradlew test        # Run tests only
./gradlew classes     # Compile only
./gradlew clean       # Delete build/
./gradlew jar         # Package JAR
```

- Java is required (modern version; the code uses records and sealed interfaces → Java 17+)
- No environment variables are required; configuration is hardcoded or embedded in classpath resources
- Orekit requires `orekit-data.zip` to be present in the runtime classpath (excluded from git via `.gitignore` on `src/main/resources/`)

---

## Directory Structure

```
src/
├── main/java/com/smousseur/orbitlab/
│   ├── OrbitLabApplication.java      # Main entry point (extends JME3 SimpleApplication)
│   ├── app/                          # Application layer (context, config, clock, converters)
│   ├── core/                         # Domain models (SolarSystemBody enum, custom exceptions)
│   ├── engine/                       # JME3 integration (assets, camera, scene graph, event bus)
│   │   └── scene/
│   │       ├── planet/               # Planet rendering (MVC: Presenter, View, LOD system)
│   │       └── graph/
│   ├── states/                       # JME3 AppState implementations
│   │   ├── camera/                   # Camera states (orbit, floating origin, view mode)
│   │   ├── ephemeris/                # Celestial body position computation state
│   │   ├── orbits/                   # Orbit visualization states
│   │   ├── scene/                    # Scene management states
│   │   ├── fx/                       # Visual effects (lighting)
│   │   └── time/                     # Clock and timeline widget states
│   ├── simulation/                   # Core orbital mechanics
│   │   ├── OrekitService.java        # Orekit singleton (propagators, frames)
│   │   ├── Physics.java              # Orbital mechanics utilities
│   │   ├── ephemeris/                # Ephemeris buffering and workers
│   │   ├── orbit/                    # Orbit path, cache, policy
│   │   └── mission/                  # Mission model, stages, optimizer, player
│   │       ├── vehicle/              # Spacecraft and launch vehicle models
│   │       ├── stage/                # Mission phase implementations
│   │       ├── attitude/             # Attitude providers
│   │       ├── maneuver/             # Maneuver implementations
│   │       ├── objective/            # Mission objectives
│   │       ├── optimizer/            # CMA-ES trajectory optimization
│   │       └── runtime/              # MissionOptimizer, MissionPlayer
│   ├── ui/                           # GUI widgets (Lemur-based timeline)
│   └── tools/                        # Standalone utilities (ephemeris/orbit generation)
└── test/java/com/smousseur/orbitlab/
    ├── app/                          # Unit tests for clock, converters, transforms
    ├── simulation/
    │   ├── ephemeris/                # Ephemeris buffer and worker tests
    │   ├── mission/optimizer/        # Integration tests (LEO mission optimization)
    │   └── orbit/                    # Orbit path, cache, policy unit tests
    └── tools/ephemerisgen/           # Smoke tests for ephemeris datasets
```

---

## Key Architectural Concepts

### JME3 AppState Pattern
All runtime subsystems are implemented as `AppState` classes and registered in `OrbitLabApplication`. Each `AppState` has `initialize()`, `update(float tpf)`, and `cleanup()` lifecycle methods. AppStates communicate through `ApplicationContext` and the `OrbitEventBus`.

### ApplicationContext
`ApplicationContext` is the central dependency container. It holds:
- `SimulationClock`, `SimulationConfig`
- `OrbitEventBus`
- `SceneGraph`, `GuiGraph`
- Planet presenter mappings

Pass `ApplicationContext` (not individual services) to AppStates and constructors.

> **Rule: No `getState()`** — AppStates must NEVER use `getState(Class)` to communicate with each other. All inter-state communication goes through `ApplicationContext`. If a state needs data from another state, that data must be exposed via a shared object in `ApplicationContext`.

### OrekitService (Singleton)
Access via `OrekitService.get()`. It provides:
- Three propagator types: **Simple** (Newtonian), **Optimization** (8×8 gravity, fast), **Default** (50×50 gravity, accurate)
- Reference frames: ICRF, ITRF, GCRF
- Requires `orekit-data.zip` on classpath

### SimulationClock
Thread-safe, event-driven simulation time manager. Subscribe to clock events (time changes, speed changes, play state changes) via `subscribe(Consumer<ClockEvent>)` which returns `AutoCloseable` — always close subscriptions in `cleanup()`.

Supports time speed multipliers and reverse playback (negative speed = rewind).

### Mission System
```
Mission (abstract)
  └── MissionStage[] (sequential phases)
        ├── VerticalAscentStage
        ├── GravityTurnStage
        ├── CoastingStage
        ├── JettisonStage
        └── TransfertTwoManeuverStage
```
- `MissionOptimizer`: Finds optimal parameters using CMA-ES (with backup multi-try to escape local minima)
- `MissionPlayer`: Executes a pre-optimized mission deterministically
- `OptimizableMissionStage<T>`: Stores optimization results for replay

### Ephemeris System
`SlidingWindowEphemerisBuffer` caches celestial body positions in a sliding time window. `EphemerisWorker` computes positions ahead of the simulation clock. `EphemerisAppState` drives this in the JME3 update loop.

### Rendering: Dual Viewport
The application renders two stacked viewports:
- **Far view**: Solar system scale (frustum 1–50000)
- **Near view**: Planet/spacecraft scale (frustum 0.1–20000)

`FloatingOriginAppState` keeps the camera near the world origin to avoid floating-point precision issues at large scales.

---

## Naming Conventions

| Suffix / Pattern | Meaning |
|---|---|
| `AppState` | JME3 application state |
| `Presenter` | MVC presenter (manages a View) |
| `View` | Rendering component |
| `Service` | Service object (usually singleton) |
| `Config` | Immutable configuration (often a Java record) |
| `Worker` | Background computation unit |
| `Stage` | A phase in a Mission |
| `Buffer` | In-memory data buffer |

---

## Code Style and Patterns

- **Records**: Prefer Java records for immutable data (`SimulationConfig`, clock events, `BodySample`, etc.)
- **Sealed interfaces**: Used for type-safe event hierarchies (e.g., `ClockEvent`)
- **Singletons**: Use the holder pattern (`private static final class Holder { static final T INSTANCE = new T(); }`)
- **Null safety**: Use `Objects.requireNonNull` in constructors; avoid nullable fields in core classes
- **Thread safety**: `SimulationClock` uses explicit synchronization and atomic updates; use concurrent collections where shared across threads
- **Subscriptions**: Event subscriptions return `AutoCloseable`; always unsubscribe in `cleanup()` or `close()`
- **Logging**: Use Log4j 2 (`LogManager.getLogger(ClassName.class)`)

---

## Testing

**Framework:** JUnit 5 (Jupiter)

```bash
./gradlew test
```

**Test categories:**
- **Unit tests**: Clock, converters, transforms, orbit path/cache/policy, ephemeris buffer
- **Integration tests**: `LEOMissionOptimizationTest` — runs a full LEO mission with gravity turn optimization; validates orbit insertion within ±7% of 400 km target altitude
- **Smoke tests**: `EphemerisDatasetSmokeTest`, `EphemerisDatasetFileSmokeTest` — validate ephemeris dataset integrity

**Test logging:** Configured via `src/test/resources/Log4j2-test.xml` at INFO level.

**Base test class:** `AbstractTrajectoryOptimizerTest` provides `propagateMission()` with 16 ms timesteps and a `TestMission` implementation. Extend it for new mission optimization tests.

---

## Key Dependencies

| Library | Version | Purpose |
|---|---|---|
| Orekit | 13.1.1 | Orbital mechanics, ephemeris, propagation |
| JMonkeyEngine 3 | 3.9.0-beta1 | 3D rendering engine |
| Lemur + Lemur Proto | 1.16.0 / 1.13.0 | JME3 GUI framework |
| Reactor Core | 3.7.8 | Reactive/async programming |
| Guava | 33.4.8-jre | Utility collections and helpers |
| Log4j 2 | 2.24.3 | Logging |
| Zstd-JNI | 1.5.6-6 | Zstandard compression |
| Groovy | 2.4.12 | Scripting support |
| JUnit Jupiter | 5.10.0 | Unit testing |

---

## Git Workflow

- **Branch naming**: Feature branches follow `feature_<name>` or `claude/<description>-<id>` conventions
- **Commit messages**: Imperative mood, descriptive (e.g., `Add backup computations to avoid local minimums in CMAES optimization`)
- **Merge strategy**: Feature branches are merged via pull requests
- **Excluded from git** (see `.gitignore`): `build/`, `.gradle/`, `gradle/`, `docs/`, `dataset/`, `src/main/resources/` (contains large ephemeris/asset files)

---

## What Is Not in This Repo

- `src/main/resources/` — Runtime assets and `orekit-data.zip` (excluded from version control; must be provided separately)
- `dataset/` — Mission and test data files
- `docs/` — Generated documentation
