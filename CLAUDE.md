# CLAUDE.md — OrbitLab

This file provides context for AI assistants working in this repository.

## Project Overview

OrbitLab is a desktop 3D orbital mechanics simulation and mission planning application. It combines:
- **JMonkeyEngine 3 (JME3)**: Real-time 3D rendering
- **Orekit**: Orbital mechanics, ephemeris, and propagation
- **CMA-ES optimization**: Trajectory optimization for missions (e.g., gravity turn, LEO insertion)

The application visualizes the solar system, computes spacecraft orbits, and simulates and optimizes launch missions.

---

## Working method

- **Measure before claiming.** Never state a diagnosis, a number, or a behavior claim
  without a measurement behind it — a probe test, added instrumentation, or the actual
  log/output. If it cannot be measured yet, say so explicitly rather than hand-computing a
  value or guessing. Facts carry a `file:line` reference; a claim that contradicts a
  ticket or a design doc is the most valuable thing to surface, and it is surfaced
  *before* any fix or question.
- **Name the layer, and the revert, before editing.** Before a fix that touches
  rendering, propagation/physics, or mission planning, state which layer it belongs in and
  why, the exact files, and how to revert if it makes things worse — then act (see
  *Layering rules*).
- **Enumerate the blast radius.** After a change, say what adjacent behavior it might have
  broken, so the visual/runtime re-check is targeted rather than a surprise.

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
- Orekit requires `orekit-data.zip` on the runtime classpath; it is tracked at `src/main/resources/orekit-data.zip` (only the test copy is gitignored)

---

## Directory Structure

```
src/
├── main/java/com/smousseur/orbitlab/
│   ├── OrbitLabApplication.java      # Main entry point (extends JME3 SimpleApplication)
│   ├── app/                          # Application layer (context, config, clock,
│   │   │                             #   HudSurface/HudSurfaces — the ESC dismissal registry)
│   │   ├── converters/               # Time/value converters
│   │   └── view/                     # View-mode + render-frame abstractions
│   │                                 #   (FocusView, RenderContext, RenderFrame,
│   │                                 #    RenderTransform, ViewMode, AxisConvention)
│   ├── core/                         # Domain models (SolarSystemBody enum, custom exceptions)
│   ├── engine/                       # JME3 integration (assets, engine/camera config)
│   │   ├── events/                   # EventBus for asynchronous inter-state communication
│   │   ├── scene/
│   │   │   ├── body/                 # Generic body rendering (BodyView, BodyRenderConfig, LodView,
│   │   │   │                         #   CoronaView — geometric star glow)
│   │   │   │   └── lod/              # LOD implementations (BillboardIconView, Model3dView)
│   │   │   ├── calibration/          # Globe calibration aids (graticule, texture painting)
│   │   │   ├── graph/                # SceneGraph and GuiGraph roots
│   │   │   ├── mesh/                 # Planet mesh conformance and calibration, ring alignment, atmosphere shell
│   │   │   ├── planet/               # Planet presenter (MVC)
│   │   │   └── spacecraft/           # Spacecraft presenter
│   │   └── view/                     # JME-specific view adapters (JmeVectorAdapter)
│   ├── states/                       # JME3 AppState implementations
│   │   ├── camera/                   # Camera states (orbit, floating origin, view mode, near sync)
│   │   ├── ephemeris/                # Celestial body position computation state
│   │   ├── fx/                       # Visual effects (lighting, post-processing chain)
│   │   ├── mission/                  # Mission orchestration, trajectory rendering, list panel,
│   │   │                             #   display panel, app-menu model, telemetry widget and
│   │   │                             #   wizard states
│   │   ├── orbits/                   # Orbit visualization states (init + runtime)
│   │   ├── scene/                    # Scene management (solar system, planet pose, HUD markers)
│   │   └── time/                     # Clock and timeline widget states
│   ├── simulation/                   # Core orbital mechanics
│   │   ├── OrekitService.java        # Orekit singleton (propagators, frames)
│   │   ├── Physics.java              # Orbital mechanics utilities
│   │   ├── ephemeris/                # Ephemeris buffering and workers
│   │   │   ├── config/               # Ephemeris/sliding-window configs
│   │   │   └── service/              # EphemerisService + registry (per-body services)
│   │   ├── flight/                   # Flight context: gravity + drag (FlightContext, DragContext, AtmosphereModel)
│   │   ├── gravity/                  # Central-body contexts, spheres of influence, arc transitions
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
│   │       ├── operation/            # Concrete missions (EarthOrbitMission, GEOMission, LunarFlybyMission,
│   │       │                         #   LunarOrbitMission) + MissionSpec, MissionFactory, MissionComposer
│   │       ├── optimizer/            # CMA-ES trajectory optimization
│   │       │   └── problems/         # Concrete trajectory problems (gravity-turn, transfer-2)
│   │       ├── planner/              # Production computation: MissionPlanOptimizer.compute() picks the
│   │       │                         #   planner (MeasuredLoad, FixedLoad, MinimizedLoad, Replay)
│   │       ├── progress/             # Optimization progress events and listeners
│   │       ├── runtime/              # MissionOptimizer + compute/optimizer/performance results
│   │       ├── scenario/             # Scenario save/load (codec, store, mapper, session)
│   │       ├── stage/                # Mission phase implementations (coasting, stage separation,
│   │       │   │                     #   analytic GTO injection/apogee circularization/Hohmann
│   │       │   │                     #   transfer/parking insertion/trim burn/plane trim at node,
│   │       │   │                     #   transfer-2 maneuver)
│   │       │   └── ascent/           # Vertical ascent + gravity turn stages
│   │       ├── window/               # Launch window search and solver
│   │       └── vehicle/              # Spacecraft, launch vehicle, propulsion, vehicle stack,
│   │           │                     #   LaunchConfiguration, PropellantBudget
│   │           ├── catalog/          # Launchers and Payloads catalogs
│   │           └── model/            # Catalog data model (LauncherModel, PayloadModel,
│   │               │                 #   AscentProfile)
│   │               └── stage/        # Stage model (StageModel, StageCapabilities, StageRole,
│   │                                 #   PropellantType, IgnitionMode, ShutdownMode)
│   ├── ui/                           # Lemur-based GUI widgets (AppStyles, UiKit, UiLayers)
│   │   ├── breadcrumb/               # Breadcrumb widget
│   │   ├── form/                     # Form/modal styling (FormStyles, ModalBackdrop,
│   │   │                             #   ConfirmDialog, WindowDragHandler)
│   │   ├── menu/                     # Top-left application menu (AppMenu, AppMenuItem)
│   │   ├── mission/
│   │   │   ├── component/            # Shared mission-UI widgets (PaginationBar)
│   │   │   ├── detail/               # Mission detail view (stage table)
│   │   │   ├── display/              # Mission trajectory display panel (rows, header/footer)
│   │   │   ├── panel/                # Mission list panel (rows, header/footer, triggers)
│   │   │   ├── scenario/             # Scenario browser widget
│   │   │   └── wizard/               # Mission creation wizard
│   │   │       ├── component/        # Reusable widgets (Badge, PopupList, ProgressBar, …)
│   │   │       └── step/             # Wizard steps (mission type, launcher, site, parameters)
│   │   │           ├── params/       # Per-mission-type dynamic parameter fields (LEO, GEO)
│   │   │           └── planning/     # Launch-window planning page (timeline, RAAN entry)
│   │   ├── telemetry/                # Telemetry widget
│   │   └── timeline/                 # Timeline widget
│   │       └── components/           # Clock display, scrubber, transport controls, speed stepper
│   └── tools/                        # Standalone utilities
│       ├── ephemerisgen/             # Ephemeris dataset generator
│       ├── meshprobe/                # Planet mesh probe
│       ├── optbench/                 # Optimizer benchmark (runs MissionPlanOptimizer)
│       └── orbitgen/                 # Orbit dataset generator
└── test/java/com/smousseur/orbitlab/
    ├── app/                          # Unit tests for clock, converters, HudSurfaces registry
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
    ├── states/mission/               # Mission display panel rules and app-menu model tests
    ├── tools/ephemerisgen/           # Smoke tests for ephemeris datasets
    └── ui/
        ├── form/                     # WindowDragHandler clamp tests (headless GuiControl)
        └── mission/                  # Mission color palette tests
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
- Two propagator factories: `createOptimizationPropagator` — the **production** one (8×8 gravity via Holmes-Featherstone, third-body perturbers, optional drag), on which every mission is optimized, replayed **and** measured (there is no higher-fidelity re-check); and `createTestPropagator` — a **test-only** point-mass (Newtonian) variant. Both take an integrator max step (see below). The optimization propagator's scalar tolerances are `DEFAULT_OPT_ABS_TOL`/`DEFAULT_OPT_REL_TOL` (`1e-5`/`1e-7`)
- Reference frames: ICRF, ITRF, GCRF
- Requires `orekit-data.zip` on classpath

**Integrator max-step sizing (late-ignition invariant):** a burn igniting after a coast can drive the mass negative on the integrator's first trial step, which Orekit throws *before* any detector (cutoff, depletion guard) can react. The `create*Propagator(double maxStep)` overloads plus `burnLimitedMaxStep(BurnSpec…)` size the step from the burns that will actually fire — `COAST_MAX_STEP` for burn-free coasts, capped at `SAFE_MAX_STEP` (30 s) when a burn is present. Stages advertise their step via `MissionStage.maxStepSeconds`. This keeps the calibrated Falcon Heavy stepping unchanged while auto-tightening for a lighter propellant load. **Never** hand a raw large max step to a propagator that will host a burn.

### SimulationClock
Thread-safe, event-driven simulation time manager. Subscribe to clock events (time changes, speed changes, play state changes) via `subscribe(Consumer<ClockEvent>)` which returns `AutoCloseable` — always close subscriptions in `cleanup()`.

Supports time speed multipliers and reverse playback (negative speed = rewind).

### Mission System
```
Mission (abstract; operation/EarthOrbitMission, GEOMission, LunarFlybyMission, LunarOrbitMission — MissionType enum)
  └── MissionStage[] (sequential phases)
        ├── ascent/VerticalAscentStage
        ├── ascent/GravityTurnFirstBurnStage, GravityTurnSecondBurnStage, GravityTurnCoreBurnStage
        ├── ascent/ConstantThrustStage
        ├── CoastingStage
        ├── StageSeparationStage
        ├── TransfertTwoManeuverStage
        └── analytic stages (GEO): AnalyticGtoInjectionStage, AnalyticApogeeCircularizationStage,
            AnalyticHohmannTransferStage, AnalyticParkingInsertionStage, AnalyticTrimBurnStage,
            AnalyticPlaneTrimAtNodeStage (node-targeted plane trim)
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

### Layering rules

Rendering concerns — offsets, seats, scale, orientation, LOD, colour — stay in the
**render layer**. Never modify propagation/physics or mission-planning code to achieve a
visual result: the propagated CoM trajectory is the mission's answer and is pinned by the
zero-tolerance gates. If a visual bug seems to require a physics change, **stop and ask**.
(A separation offset between stack pieces, for instance, is a render concern: it lives in the
render-side `StackSeat`, not in propagation.)

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

- **Language**: All code comments and Javadoc are written in **English**, without exception — including
  design rationale, which is often long in this codebase. Only the design documents are written in
  French, in the Confluence space **OrbitLab** (`OL`), with historical copies in the local,
  untracked `docs/` folder. Do not infer the code's language from the docs': they differ deliberately.
- **Inline comments are the exception, not the habit.** This rule covers comments *inside* method
  bodies (`//` and inline `/* */`); it does **not** cover Javadoc on classes, methods, records or
  fields, which stays welcome and is where long rationale belongs. An inline comment is written only
  when the code cannot carry the information by itself:
    - a **why** that is invisible locally — a non-obvious ordering, a deliberate deviation from the
      obvious implementation, a guard whose reason lives in another class;
    - the **provenance of a magic value** — where a constant, a tolerance or a threshold comes from,
      given as the reasoning or the measurement itself, **never** a `docs/` path or a ticket id;
    - a **workaround** for a library or driver defect, with what it works around;
    - an **invariant or precondition** the reader must hold to follow the next lines.

  Everything else is noise and must not be written: restating what the line already says, narrating
  the change being made (`// fixed the sign`, `// now uses the cache`, `// added for MIS-2`), marking
  the shape of the code (`// loop over the stages`, `// getters`), or leaving commented-out code.
  A comment that only exists because the code is unclear is a request to rename or extract, not to
  comment. And when a change *is* worth explaining, its place is the commit message or the design
  record in Confluence/Jira, never a scar left in the source — and in particular never a `docs/`
  path or a ticket id carried in a comment, a Javadoc or an exception message.
- **Records**: Prefer Java records for immutable data (`SimulationConfig`, clock events, `BodySample`, etc.)
- **Sealed interfaces**: Used for type-safe event hierarchies (e.g., `ClockEvent`)
- **Singletons**: Use the holder pattern (`private static final class Holder { static final T INSTANCE = new T(); }`)
- **Null safety**: Use `Objects.requireNonNull` in constructors; avoid nullable fields in core classes
- **`Optional` is a return type only.** Never a field, never a record component, never a method
  parameter. When a value may legitimately be absent, hold it as a nullable field and expose the
  absence through the API — a `hasX()` predicate, a formatting helper, or an `Optional`-returning
  method. `AchievedOrbit` is the reference example: nullable components, `hasOsculating()` /
  `hasMean()`, and `formatOsculating()` / `formatMean()`. A helper that has to handle absence takes
  the value type and null-checks it, not an `Optional` parameter.
- **Thread safety**: `SimulationClock` uses explicit synchronization and atomic updates; use concurrent collections where shared across threads
- **Subscriptions**: Event subscriptions return `AutoCloseable`; always unsubscribe in `cleanup()` or `close()`
- **Logging**: Use Log4j 2 (`LogManager.getLogger(ClassName.class)`)

---

## Editing conventions

- **Use the `Edit`/`Write` tools, never `perl`/`sed`** for source or docs: shell
  substitution has more than once mangled apostrophes and introduced CRLF. Preserve each
  file's existing line endings (`.java` and `bugs.md` are CRLF, some roadmaps are LF —
  detect per file, never presume) and accented/apostrophe characters exactly.
- **`spotlessApply` reformats the whole repo, not just your files.** After running it,
  `git diff -w` to confirm it only touched files in your change set, and
  `git checkout -- <file>` any it reformatted out of scope. Check PMD with
  `pmdMain pmdTest` — **not** `check`, which runs the slow tests.
- **Fix all N.** When asked to fix N issues, fix the N; do not silently ship a subset.
- **Gradle needs JDK 21**: prefix with
  `JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew …` (the default env JDK 17 fails).

---

## Design Documents

Design work is recorded as pages in the Confluence space **OrbitLab** (`OL`), one tree per
*chantier*, each page linked to its Jira **Story** — the chantier itself, grouped under a family **Epic** (`MIS`, `PHY`, …) in project **OBL**. Historical v1 documents may still
be found in the local, untracked `docs/<chantier>/` folder (`01-decoupage.md`, `02-baseline-L0.md`, `03-conception-L1.md`, …).

> **Rule: work the design out in the conversation first, then write the document.**
> A generated design doc is hard to review — the reader has to reconstruct the reasoning
> and the alternatives from prose. The same content is easy to review when the decisions
> were made one at a time in chat and the document merely records them.

The exchange, in order:

1. **Explore the code first and bring measured facts, not impressions.** Counts, file
   and line references, existing patterns to imitate. Facts that contradict the spec —
   "the découpage says sixteen sites, the real count is twenty" — are the most valuable
   thing to surface, and they must be surfaced before any question is asked.
2. **One question at a time**, multiple choice where the options are genuinely distinct,
   with a recommendation and its reasoning. Never a list of questions in one message.
3. **Propose 2–3 approaches with trade-offs** before settling on one.
4. **Present the design section by section**, asking after each whether it holds. This is
   where a proposal that narrows or widens what was previously agreed gets raised
   explicitly, as a question — not folded silently into the document.
5. **Write the page only once the sections are agreed.** The document introduces no
   decision that was not made in the conversation. If writing it reveals a new question,
   ask it rather than resolving it in the text.
6. **Report corrections made while writing**, especially any statement that was wrong in
   the conversation and is right in the document.

This applies to design and specification pages. Short factual notes and measurement logs are written
directly; bugs and technical-debt items go straight to Jira (project **OBL**) rather than to a file.

---

## Workflow: chantiers and lots

Work is organised into numbered *chantiers*, each split into *lots* — `L0` a measured
baseline, then `L1…Ln` one behaviour change at a time. A lot's design is worked out in the
conversation first (see *Design Documents*), then recorded. **In Jira (project `OBL`) this is a three-level hierarchy:** the classification family (`MIS`, `PHY`, `FX`, `OPT`, `UI`, `RND`, `NAV`, `SEL`, `AST`) is an **Epic**; each *chantier* is a **Story** under its family Epic; each *lot* is a **Sub-task** of its chantier Story. Lot Sub-tasks are created only for the chantier actually being worked — not backfilled across closed history.

**Closing a lot ends with three things — none skipped, and none reported done until it
actually exists:**
1. The **measurement / verification run against the production path** — the real production
   computation (`MissionPlanOptimizer.compute()`, which selects the planner the mission actually
   uses — `MeasuredLoadPlanner` only for an Earth orbit in FAST/BALANCED) and the production propagator, not a fixture stand-in — with the
   measured before/after numbers. The zero-tolerance gates run via `gateTest`
   (`forkEvery=1`); confirm they are untouched, or that a re-baseline was a deliberate,
   stated decision.
2. **Closure documentation** as a Confluence page (French) in the chantier's tree, linked to its
   Jira **Story** (the chantier): scope, measured before/after, known limitations.
3. **Jira updates** (project **OBL**): resolved issues transitioned to *Terminé* (label
   `resolution:fixed|wontdo|moved`), any new Bug/Task/Story created; the roadmap is the Jira
   backlog and the native releases (v1–v4 / fixVersion).

The user runs the slow flights and commits; leave both to them.

---

## Testing

**Framework:** JUnit 5 (Jupiter)

```bash
./gradlew test
```

> **AI assistants: do not run `./gradlew test` (or any test task) after code changes on your own initiative.** Nothing runs the tests on push or pull request: the only workflow, `.github/workflows/release.yml`, fires on a `vX.Y.Z` tag (or manual dispatch) and runs `check` — which includes the test task — before `shadowJar` and `jpackage`. The user runs tests manually. Only run tests when the user explicitly asks, or when you need to investigate/debug something to complete the feature you're actively working on.

**Slow suites and long runs.** The full/slow suite (mission-optimization flights) takes
**2–5 hours** — never launch it unprompted, and never as an opaque foreground call.
Default to the fast unit tests. When a long run is genuinely needed: ask first; make sure
`git status` is clean (everything committed or stashed) so a stall cannot cost the working
tree; run it in the **background** with progress that can be tailed. The user runs these
flights themselves by default — offer, don't assume. Re-running the same `--tests` filter
after a success does nothing (UP-TO-DATE); a reproducibility measurement goes through
`cleanTest`.

**Test categories:**
- **Unit tests**: Clock, converters, transforms, orbit path/cache/policy, ephemeris buffer, vehicle/launcher/payload catalogs, propellant budgeting, depletion guard/stop trigger, mission stages
- **Integration tests**: `LEOMissionOptimizationTest` — runs a full LEO mission with gravity turn optimization; validates orbit insertion within ±7% of 400 km target altitude. `GEOMissionOptimizationTest` — runs a full GEO mission (GTO injection through apogee circularization).
- **Smoke tests**: `EphemerisDatasetSmokeTest`, `EphemerisDatasetFileSmokeTest` — validate ephemeris dataset integrity

**Test logging:** Configured via `src/test/resources/Log4j2-test.xml` at INFO level.

**Base test class:** `AbstractTrajectoryOptimizerTest` provides `testMission(mission, perigee, apogee)`, which flies the mission through `MissionOptimizer.optimize()` (40 000 evaluations, `TEST_SEED` = 42) and asserts the flown altitude band of the `Coasting` stage against the targets within `ORBIT_MARGIN_RATIO` (±7 %), plus `extractMinMaxAltitudes(ephemeris, stageName)`. Extend it for new mission optimization tests.

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
- **Excluded from git** (see `.gitignore`): `build/`, `.gradle/`, `gradle/`, `docs/`, `dataset/**`. Note that `src/main/resources/` is **not** excluded at all — shaders, fonts, UI textures, the skybox, `orekit-data.zip` **and the GLTF planet models under `models/`** are all tracked. `docs/` is untracked: design docs live in the Confluence space `OL`, and the local folder holds historical copies, recoverable from git history

---

## What Is Not in This Repo

- `dataset/` — Mission and test data files
- `docs/` — Historical design docs (current ones are in the Confluence space `OL`) plus locally generated implementation plans (`docs/superpowers/`); kept on disk but untracked
