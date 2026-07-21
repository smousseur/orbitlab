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
│   ├── app/                          # Application layer (context, config, clock)
│   │   ├── converters/               # Time/value converters
│   │   └── view/                     # View-mode + render-frame abstractions
│   │                                 #   (FocusView, RenderContext, RenderFrame,
│   │                                 #    RenderTransform, ViewMode, AxisConvention)
│   ├── core/                         # Domain models (SolarSystemBody enum, custom exceptions)
│   ├── engine/                       # JME3 integration (assets, engine/camera config)
│   │   ├── events/                   # EventBus for asynchronous inter-state communication
│   │   ├── scene/
│   │   │   ├── body/                 # Generic body rendering (BodyView, BodyRenderConfig, LodView)
│   │   │   │   └── lod/              # LOD implementations (BillboardIconView, Model3dView)
│   │   │   ├── graph/                # SceneGraph and GuiGraph roots
│   │   │   ├── planet/               # Planet presenter (MVC)
│   │   │   └── spacecraft/           # Spacecraft presenter
│   │   └── view/                     # JME-specific view adapters (JmeVectorAdapter)
│   ├── states/                       # JME3 AppState implementations
│   │   ├── camera/                   # Camera states (orbit, floating origin, view mode, near sync)
│   │   ├── ephemeris/                # Celestial body position computation state
│   │   ├── fx/                       # Visual effects (lighting)
│   │   ├── mission/                  # Mission orchestration, trajectory rendering, list panel,
│   │   │                             #   display panel, telemetry widget and wizard states
│   │   ├── orbits/                   # Orbit visualization states (init + runtime)
│   │   ├── scene/                    # Scene management (solar system, planet pose, HUD markers)
│   │   └── time/                     # Clock and timeline widget states
│   ├── simulation/                   # Core orbital mechanics
│   │   ├── OrekitService.java        # Orekit singleton (propagators, frames)
│   │   ├── Physics.java              # Orbital mechanics utilities
│   │   ├── ephemeris/                # Ephemeris buffering and workers
│   │   │   ├── config/               # Ephemeris/sliding-window configs
│   │   │   └── service/              # EphemerisService + registry (per-body services)
│   │   ├── orbit/                    # Orbit path, cache, policy, runtime slot, snapshot
│   │   │   └── config/               # OrbitWindowConfig
│   │   ├── source/                   # Ephemeris data sources (dataset reader, Orekit PV,
│   │   │                             #   prefetching, LRU cache, V1 file format)
│   │   └── mission/                  # Mission model
│   │       ├── attitude/             # Attitude providers (gravity-turn, zenith-thrust)
│   │       ├── context/              # MissionContext + MissionEntry (active mission tracking)
│   │       ├── detector/             # Event detectors (mass depletion, min-altitude tracker,
│   │       │                         #   fail-fast DepletionGuard/DepletionStopTrigger)
│   │       ├── ephemeris/            # Mission-trajectory ephemeris (point + generator)
│   │       ├── maneuver/             # Maneuver implementations (gravity-turn, transfer-2)
│   │       ├── objective/            # Mission objectives (orbit insertion)
│   │       ├── operation/            # Concrete missions (LEOMission, GEOMission) + MissionFactory
│   │       ├── optimizer/            # CMA-ES trajectory optimization
│   │       │   └── problems/         # Concrete trajectory problems (gravity-turn, transfer-2)
│   │       ├── runtime/              # MissionOptimizer + compute/optimizer/performance results
│   │       ├── stage/                # Mission phase implementations (coasting, stage separation,
│   │       │   │                     #   analytic GTO injection/apogee circularization/Hohmann
│   │       │   │                     #   transfer/parking insertion/trim burn/plane trim at node,
│   │       │   │                     #   transfer-2 maneuver)
│   │       │   └── ascent/           # Vertical ascent + gravity turn stages
│   │       └── vehicle/              # Spacecraft, launch vehicle, propulsion, vehicle stack,
│   │           │                     #   Launchers/Payloads catalogs, LaunchConfiguration,
│   │           │                     #   PropellantBudget
│   │           └── model/            # Catalog data model (LauncherModel, PayloadModel,
│   │               │                 #   AscentProfile)
│   │               └── stage/        # Stage model (StageModel, StageCapabilities, StageRole,
│   │                                 #   PropellantType, IgnitionMode, ShutdownMode)
│   ├── ui/                           # Lemur-based GUI widgets (AppStyles, UiKit)
│   │   ├── form/                     # Form/modal styling (FormStyles, ModalBackdrop)
│   │   ├── mission/
│   │   │   ├── component/            # Shared mission-UI widgets (PaginationBar)
│   │   │   ├── display/              # Mission trajectory display panel (rows, header/footer)
│   │   │   ├── panel/                # Mission list panel (rows, header/footer, triggers)
│   │   │   └── wizard/               # Mission creation wizard
│   │   │       ├── component/        # Reusable widgets (Badge, PopupList, ProgressBar, …)
│   │   │       └── step/             # Wizard steps (mission type, launcher, site, parameters)
│   │   │           └── params/       # Per-mission-type dynamic parameter fields (LEO, GEO)
│   │   ├── telemetry/                # Telemetry widget
│   │   └── timeline/                 # Timeline widget
│   │       └── components/           # Clock display, scrubber, transport controls, speed stepper
│   └── tools/                        # Standalone utilities
│       ├── ephemerisgen/             # Ephemeris dataset generator
│       └── orbitgen/                 # Orbit dataset generator
└── test/java/com/smousseur/orbitlab/
    ├── app/                          # Unit tests for clock, converters
    │   └── view/                     # FocusView and RenderTransform tests
    ├── core/                         # SolarSystemBody tests
    ├── engine/scene/spacecraft/      # SpacecraftPresenter tests
    ├── simulation/
    │   ├── ephemeris/                # Ephemeris buffer and worker tests
    │   ├── mission/
    │   │   ├── attitude/             # Attitude provider tests
    │   │   ├── detector/             # DepletionGuard/DepletionStopTrigger tests
    │   │   ├── maneuver/             # Maneuver tests
    │   │   ├── operation/            # MissionFactory tests
    │   │   ├── optimizer/            # Trajectory optimizer tests (LEO, GEO, sweeps, convergence)
    │   │   │   └── problems/         # Per-problem optimizer tests (gravity-turn)
    │   │   ├── stage/                # Mission stage tests (stage separation, transfer-2)
    │   │   └── vehicle/              # Vehicle/propulsion/catalog tests (launchers, payloads,
    │   │                             #   propellant budget, launch configuration)
    │   ├── orbit/                    # Orbit path, cache, policy, snapshot, runtime slot tests
    │   └── source/                   # Source-layer tests (LRU cache)
    ├── states/mission/               # Mission display panel rules tests
    ├── tools/ephemerisgen/           # Smoke tests for ephemeris datasets
    └── ui/mission/                   # Mission color palette tests
```

---

## Key Architectural Concepts

### JME3 AppState Pattern
All runtime subsystems are implemented as `AppState` classes and registered in `OrbitLabApplication`. Each `AppState` has `initialize()`, `update(float tpf)`, and `cleanup()` lifecycle methods. AppStates communicate through `ApplicationContext` and the `EventBus` (in `engine/events`).

### ApplicationContext
`ApplicationContext` is the central dependency container. It holds:
- `SimulationClock`, `SimulationConfig`, `EngineConfig`
- `EventBus`
- `SceneGraph`, `GuiGraph`
- `FocusView` (current view mode and focus body)
- `MissionContext` (active missions) and per-mission `MissionRenderer` registry
- Planet presenter mappings, near-viewport camera

Pass `ApplicationContext` (not individual services) to AppStates and constructors.

> **Rule: No `getState()`** — AppStates must NEVER use `getState(Class)` to communicate with each other. All inter-state communication goes through `ApplicationContext`. If a state needs data from another state, that data must be exposed via a shared object in `ApplicationContext`.

### OrekitService (Singleton)
Access via `OrekitService.get()`. It provides:
- Three propagator types: **Simple** (Newtonian), **Optimization** (8×8 gravity, fast), **Default** (50×50 gravity, accurate)
- Reference frames: ICRF, ITRF, GCRF
- Requires `orekit-data.zip` on classpath

**Integrator max-step sizing (late-ignition invariant, bilan 08 §3.1):** a burn igniting after a coast can drive the mass negative on the integrator's first trial step, which Orekit throws *before* any detector (cutoff, depletion guard) can react. The `create*Propagator(double maxStep)` overloads plus `burnLimitedMaxStep(BurnSpec…)` size the step from the burns that will actually fire — `COAST_MAX_STEP` for burn-free coasts, capped at `SAFE_MAX_STEP` (30 s) when a burn is present. Stages advertise their step via `MissionStage.maxStepSeconds`. This keeps the calibrated Falcon Heavy stepping unchanged while auto-tightening for a lighter (I7) load. **Never** hand a raw large max step to a propagator that will host a burn.

### SimulationClock
Thread-safe, event-driven simulation time manager. Subscribe to clock events (time changes, speed changes, play state changes) via `subscribe(Consumer<ClockEvent>)` which returns `AutoCloseable` — always close subscriptions in `cleanup()`.

Supports time speed multipliers and reverse playback (negative speed = rewind).

### Mission System
```
Mission (abstract; operation/LEOMission, operation/GEOMission — MissionType enum)
  └── MissionStage[] (sequential phases)
        ├── ascent/VerticalAscentStage
        ├── ascent/GravityTurnStage
        ├── ascent/ConstantThrustStage
        ├── CoastingStage
        ├── StageSeparationStage
        ├── TransfertTwoManeuverStage
        └── analytic stages (GEO): AnalyticGtoInjectionStage, AnalyticApogeeCircularizationStage,
            AnalyticHohmannTransferStage, AnalyticParkingInsertionStage, AnalyticTrimBurnStage,
            AnalyticPlaneTrimAtNodeStage (node-targeted plane trim, bilan 08 §3.5)
```
- `MissionFactory` (`operation/`): Builds a `Mission` from the wizard's raw form values — resolves launcher/payload from the catalogs and sizes propellant via `PropellantBudget`.
- `MissionContext` / `MissionEntry` (`context/`): Tracks active missions and their lifecycle status (`MissionStatus`).
- `MissionOptimizer` (`runtime/`): Finds optimal parameters using CMA-ES (with backup multi-try / plateau detection to escape local minima); returns a `MissionOptimizerResult`/`MissionComputeResult`/`MissionPerformanceReport`.
- `OptimizableMissionStage<T>`: Stores optimization results for replay.
- `optimizer/problems/`: Concrete `TrajectoryProblem` implementations (gravity-turn, I6-optimized two-maneuver transfer with depletion-aware bounds) plus their constraints.
- `detector/`: Orekit event detectors (mass depletion, minimum altitude tracking) used during propagation, plus `DepletionGuard`/`DepletionStopTrigger` — fail-fast propellant monitoring armed on propagators across stages, maneuvers and vehicle modeling.
- `ephemeris/`: Mission-trajectory sampling (`MissionEphemeris`, `MissionEphemerisGenerator`) used by renderers.
- `vehicle/`: `LaunchConfiguration` assembles a `LauncherModel` (from the `Launchers` catalog) with per-stage propellant loads and a `Spacecraft` payload (from the `Payloads` catalog) into a `VehicleStack`; `PropellantBudget` sizes loads analytically (inverse Tsiolkovsky, top-down from the payload) instead of always flying fully loaded. `vehicle/model/` and `vehicle/model/stage/` hold the underlying catalog data model.

### Ephemeris System
`SlidingWindowEphemerisBuffer` caches celestial body positions in a sliding time window. `EphemerisWorker` computes positions ahead of the simulation clock. `EphemerisAppState` drives this in the JME3 update loop. Ephemeris data is sourced through `simulation/source/` (`OrekitPvSource`, `DatasetEphemerisSource`, `PrefetchingEphemerisSource`, with `LruCache`), and `EphemerisServiceRegistry` exposes per-body services.

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

> **AI assistants: do not run `./gradlew test` (or any test task) after code changes on your own initiative.** There is no CI pipeline in this repo (no `.github/workflows`), and the user runs tests manually. Only run tests when the user explicitly asks, or when you need to investigate/debug something to complete the feature you're actively working on.

**Test categories:**
- **Unit tests**: Clock, converters, transforms, orbit path/cache/policy, ephemeris buffer, vehicle/launcher/payload catalogs, propellant budgeting, depletion guard/stop trigger, mission stages
- **Integration tests**: `LEOMissionOptimizationTest` — runs a full LEO mission with gravity turn optimization; validates orbit insertion within ±7% of 400 km target altitude. `GEOMissionOptimizationTest` — runs a full GEO mission (GTO injection through apogee circularization).
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
