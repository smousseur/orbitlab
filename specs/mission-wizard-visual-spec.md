# Mission Wizard — Visual Specification (Phase 1)

## Context

OrbitLab currently exposes mission management through `MissionPanelWidget`
(`src/main/java/com/smousseur/orbitlab/ui/mission/MissionPanelWidget.java`). Its
`onCreate()` handler is a stub (`logger.info("Create mission not yet
implemented")`). Creating a mission today requires hand-building a `LEOMission`
in code.

We want an interactive, modal **Mission Wizard** that walks the user through
four configuration steps:

1. **Mission** — target orbit family (LEO / GTO / SSO / MEO / GEO / TLI)
2. **Parameters** — orbital elements + launch date
3. **Site** — cosmodrome selection + ground coordinates
4. **Launcher** — vehicle selection + payload

This first phase specifies **only the visual layout** of the wizard so the UI
can be built with Lemur/JME3 and iterated on, decoupled from the eventual form
validation and mission-building logic.

**Explicit non-goals for this phase:**
- No click handlers beyond "Cancel closes the modal".
- No field validation, no value binding to a domain model.
- No navigation wiring (Next / Previous / step jumping) — the spec describes
  what each step looks like; steps will be statically switchable via a test
  hook for visual QA only.
- No icon assets committed; we only reserve icon slots with placeholder paths.

## Scope mapping — mockup → code

All labels in the final UI will be **in English** (the French mockups are the
visual reference only). Translation of every label appears inline in each step
section below.

| Mockup element | Realisation in Lemur |
|---|---|
| Modal overlay (darkens scene, blocks clicks) | New `modalNode` in `GuiGraph`, full-screen backdrop quad + `uiWantsMouse` hook |
| Window card (rounded blue panel) | `Container` with `MissionPanelStyles.createGradient(ICE_PANEL_BG)` background |
| Stepper (1 → 2 → 3 → 4) | Custom composite: circle Labels + separator quads |
| Card grid (mission types, launchers) | `Container` grid of clickable `Container` rows using `MouseEventControl` |
| Text input | `TextField` (Lemur native) |
| Slider with value | `Slider` (Lemur native) + Label bound via `VersionedReference` |
| Dropdown (cosmodrome, payload type) | Custom popup list (see §7.2) |
| Segmented control (2 BURNS / DIRECT / HOHMANN, AUTO / ISA / MANUAL) | `Container` of `Button`s, toggled-state driven background |
| Badge / chip ("AVAILABLE", "IN PROGRESS", "SOON") | `Label` with small gradient background |
| Info banner (blue box at bottom of Step 2) | `Container` with `ICE_PANEL_BG_LIGHT` + accent bar |
| Progress bar (bottom-left) | Two stacked quads: track + fill |
| Mini-map placeholder (Step 3) | `Container` with grid lines + single `IconComponent` dot |

## Design tokens

All wizard colours **reuse the existing palette** in
`src/main/java/com/smousseur/orbitlab/ui/AppStyles.java`. No new colour
constants are introduced.

| Purpose | Token |
|---|---|
| Window background | `ICE_PANEL_BG` (0.08, 0.12, 0.20, 0.75) |
| Step content / row bg | `ICE_PANEL_BG_LIGHT` (0.12, 0.18, 0.28, 0.60) |
| Selected card / active step | `ICE_ROW_SELECTED` (0.20, 0.45, 0.70, 0.80) |
| Active step ring, Next button, titles, accents | `ICE_ACCENT` (0.30, 0.65, 0.90) |
| Cancel button | `ICE_DANGER` (0.90, 0.35, 0.35) |
| "Create mission" final button | `ICE_SUCCESS` (0.30, 0.80, 0.55) |
| "In progress" badge | `ICE_WARNING` (0.95, 0.75, 0.30) |
| Primary text | `ICE_TEXT_PRIMARY` |
| Secondary / helper text | `ICE_TEXT_SECONDARY` |
| Borders, separators | `ICE_BORDER` |
| Backdrop | Black, alpha = 0.55 (new inline constant in `MissionWizardStyles`) |

Fonts use the Lemur default (bitmap). Font sizes:

| Role | Size |
|---|---|
| Brand / window title ("ORBITLAB / MISSION WIZARD v2.1") | 18 |
| Step header ("MISSION TYPE", "PARAMETERS — LEO", …) | 20 |
| Section sub-header (`// select the target orbit`) | 12, `ICE_TEXT_SECONDARY` |
| Field label (above inputs) | 12, `ICE_TEXT_SECONDARY` |
| Input / body text | 14, `ICE_TEXT_PRIMARY` |
| Stepper number | 14 |
| Stepper label ("MISSION", "PARAMETERS", …) | 12 |
| Badge text | 10 |

## Window geometry

**Critical:** the modal is composed of **two distinct spatials** with
different sizing rules. Only the backdrop covers the full viewport; the
window card itself is a fixed-size panel centered on screen. The wizard
**must never** stretch to fill the viewport.

| Spatial | Size | Position | Purpose |
|---|---|---|---|
| `ModalBackdrop` | `camera.getWidth()` × `camera.getHeight()` | `(0, 0)` | Dims the scene behind and eats clicks |
| `MissionWizardWidget` root | **Fixed 880 × 640 px** | Centered | The visible wizard card |

**Window card constants** — declared as `private static final float` at the
top of `MissionWizardWidget`:

| Property | Value |
|---|---|
| `WINDOW_WIDTH` | 880 px |
| `WINDOW_HEIGHT` | 640 px |
| `MIN_VIEWPORT_MARGIN` | 32 px (guaranteed gap between window edge and viewport edge) |
| Outer padding (inside the window) | 24 px (left/right/top/bottom) |
| Header height | 88 px (brand row + stepper) |
| Footer height | 72 px (progress bar + nav buttons) |
| Content region | `WINDOW_HEIGHT - HEADER - FOOTER - 2×outerPadding` |
| Inter-section spacing | 16 px |
| Inter-row spacing (form) | 12 px |
| Card grid gutter | 12 px |

**Centering rule** — in `MissionWizardWidget.layout(int screenWidth, int
screenHeight)` (called on init and whenever the camera resizes):

```java
float x = Math.round((screenWidth  - WINDOW_WIDTH)  / 2f);
float y = Math.round((screenHeight + WINDOW_HEIGHT) / 2f);
// JME3 GUI origin is bottom-left; translate so the top-left of the
// window sits at (x, y) and the window extends downward.
root.setLocalTranslation(x, y, 1f);  // z=1 above the backdrop (z=0)
```

The size is set once via `root.setPreferredSize(new Vector3f(WINDOW_WIDTH,
WINDOW_HEIGHT, 0))` and **never** bound to the viewport dimensions.

**Minimum viewport sanity check** — at init time the widget asserts:

```java
if (screenWidth  < WINDOW_WIDTH  + 2*MIN_VIEWPORT_MARGIN
 || screenHeight < WINDOW_HEIGHT + 2*MIN_VIEWPORT_MARGIN) {
    logger.warn("Viewport {}x{} is smaller than wizard minimum {}x{}; "
        + "the wizard will clip.", screenWidth, screenHeight,
        WINDOW_WIDTH + 2*MIN_VIEWPORT_MARGIN,
        WINDOW_HEIGHT + 2*MIN_VIEWPORT_MARGIN);
}
```

The wizard does **not** adopt responsive scaling in this phase; a warning
log is enough for visual QA.

**Backdrop sizing** — `ModalBackdrop.update(cam)` is the only place that
reads the camera dimensions; it sets its own preferred size to
`(cam.getWidth(), cam.getHeight(), 0)` every frame (cheap; only triggers a
layout when the camera resizes). The window card ignores camera size
entirely apart from the centering offset.

## Architecture overview

### New files

```
src/main/java/com/smousseur/orbitlab/ui/mission/wizard/
├── MissionWizardWidget.java        # Root widget (owns modal + shell + current step)
├── MissionWizardStyles.java        # Lemur style "mission-wizard", gradient helpers
├── MissionWizardStep.java          # enum { MISSION, PARAMETERS, SITE, LAUNCHER }
├── ModalBackdrop.java              # Full-screen quad + pick-blocker
├── WizardStepper.java              # 1 → 2 → 3 → 4 breadcrumb component
├── WizardFooter.java               # Progress bar + Cancel / Previous / Next buttons
├── component/
│   ├── SelectableCard.java         # Clickable card with icon slot + title + subtitle
│   ├── SegmentedControl.java       # Horizontal group of mutually-exclusive buttons
│   ├── PopupList.java              # Single-selection dropdown replacement
│   ├── Badge.java                  # Small rounded pill label
│   ├── InfoBanner.java             # Blue info box with accent bar
│   ├── LabeledField.java           # Helper: label + input cell
│   └── ProgressBar.java            # Two-quad progress indicator
└── step/
    ├── StepMissionType.java        # Step 1 content
    ├── StepParameters.java         # Step 2 content
    ├── StepLaunchSite.java         # Step 3 content
    └── StepLauncher.java           # Step 4 content

src/main/java/com/smousseur/orbitlab/states/mission/
└── MissionWizardAppState.java      # AppState controlling widget lifecycle
```

### Modified files

| File | Change |
|---|---|
| `engine/scene/graph/GuiGraph.java` | Add `modalNode` (last child of `guiFrame` → highest z-order); expose `getModalNode()` |
| `ui/AppStyles.java` | Call `MissionWizardStyles.init(assetManager)` from `init()` |
| `OrbitLabApplication.java` | Instantiate and attach `MissionWizardAppState` (disabled by default) |
| `states/camera/OrbitCameraAppState.java` | Wire `uiWantsMouse` supplier (currently `() -> false`, line ~92) to the wizard's visibility state |

No existing widget file is rewritten — the wizard is additive.

### Runtime composition

```
MissionWizardAppState
 └─ MissionWizardWidget  (attaches to guiGraph().getModalNode())
     ├─ ModalBackdrop   (full-screen, behind the window)
     └─ root Container (880×640, centered, "mission-wizard" style)
         ├─ Header Container
         │   ├─ Brand row (ORBITLAB / MISSION WIZARD v2.1)
         │   └─ WizardStepper
         ├─ Content Container (swaps children by active step)
         │   ├─ StepMissionType   (active when step == MISSION)
         │   ├─ StepParameters    (active when step == PARAMETERS)
         │   ├─ StepLaunchSite    (active when step == SITE)
         │   └─ StepLauncher      (active when step == LAUNCHER)
         └─ WizardFooter
             ├─ ProgressBar
             └─ Nav buttons (Cancel / Previous / Next | Create mission)
```

Only one step container is attached at a time; inactive steps are fully
removed from the scene graph (not just culled) to keep picking cheap.

## Modal infrastructure

### 1. `modalNode` z-ordering

`GuiGraph` gains a new child `modalNode`, attached **after** every other
node in its constructor so it renders on top of the timeline, mission panel,
and telemetry widgets. `GuiGraph.attachTo(guiNode)` requires no change.

```java
// GuiGraph.java — constructor addition
guiFrame.attachChild(timelineNode);
guiFrame.attachChild(planetBillboardsNode);
guiFrame.attachChild(telemetryNode);
guiFrame.attachChild(missionPanelNode);
guiFrame.attachChild(modalNode);        // NEW — topmost
```

### 2. `ModalBackdrop`

A single `Container` sized to the full viewport, stacked **before** (below)
the wizard window in the modal node:

- Background: solid black `ColorRGBA(0, 0, 0, 0.55f)` via
  `QuadBackgroundComponent`.
- Preferred size bound to `camera.getWidth()` and `camera.getHeight()` each
  frame (via `MissionWizardWidget.update()`).
- Carries a `DefaultMouseListener` that **consumes** every click event
  without invoking any action. This is what prevents clicks from reaching
  3D-scene pickables underneath.
- Placed so its local Z is lower than the window card, so mouse picks on the
  window itself still work normally.

### 3. Input blocking for camera / scene

`OrbitCameraAppState` already accepts a `BooleanSupplier uiWantsMouse` (line
~96) but it's wired to `() -> false` in `OrbitLabApplication` (line ~92, with
a TODO). The wizard widget exposes a simple `boolean isVisible()` method and
`MissionWizardAppState` publishes that supplier through
`ApplicationContext`. `OrbitLabApplication` then reads it when constructing
`OrbitCameraAppState`.

**Effect while modal is open:**
- Mouse drag over the scene → no camera rotation (blocked by `uiWantsMouse`).
- Clicks on any non-wizard widget → absorbed by the backdrop quad.
- Wheel / keyboard → also gated on `uiWantsMouse`.

### 4. Visibility lifecycle

- `MissionWizardAppState.setEnabled(true)` → creates `MissionWizardWidget` and
  attaches it under `modalNode`. Currently only reachable from a debug hook or
  a future `MissionPanelWidget.onCreate()` invocation.
- `MissionWizardAppState.setEnabled(false)` → calls `widget.close()`, detaches
  the root, and releases the `uiWantsMouse` lock.
- The **Cancel** button's only wired action in this phase is to call
  `MissionWizardAppState.setEnabled(false)`. Next / Previous / Create are
  placeholder stubs that log at DEBUG and do nothing.

## Wizard shell

### Header (88 px tall)

```
┌─ 24px ──────────────────────────────────────── 24px ─┐
│  [globe]  ORBITLAB  /  MISSION WIZARD v2.1          │  ← 32 px brand row
│                                                      │
│    ①──────  ──②──────  ──③──────  ──④              │  ← 44 px stepper
│   MISSION   PARAMETERS   SITE      LAUNCHER         │
│     (accent colour under active step)               │
└──────────────────────────────────────────────────────┘
```

**Brand row**
- Left: `IconComponent` slot `assets/icons/wizard/brand-globe.png` (24×24).
- Label: `"ORBITLAB"` in `ICE_TEXT_PRIMARY`, size 18.
- Separator `" / "` in `ICE_TEXT_SECONDARY`.
- Label: `"MISSION WIZARD v2.1"` in `ICE_TEXT_SECONDARY`, size 18.

**Stepper** — see `WizardStepper` below.

### `WizardStepper`

Horizontal row, 44 px tall, 4 step nodes separated by short dashed lines.
Each step node is a vertical stack:

1. **Circle badge** (28×28): `Container` with a circular-looking background
   (a small Lemur gradient quad with high inset radius; Lemur has no true
   circle — approximate with a square bordered gradient). Three visual
   states:
   - **Done**: filled `ICE_SUCCESS` background, check-mark glyph `"v"` inside,
     label below in `ICE_SUCCESS`.
   - **Active**: hollow, 2 px ring in `ICE_ACCENT`, step number inside in
     `ICE_ACCENT`, label below in `ICE_TEXT_PRIMARY`, plus a 2 px underline in
     `ICE_ACCENT` spanning the width of the label.
   - **Pending**: hollow, 1 px ring in `ICE_BORDER`, step number inside in
     `ICE_TEXT_SECONDARY`, label below in `ICE_TEXT_SECONDARY`.

2. **Step label** below the circle: 12 px, uppercase, centered on the circle.

Between consecutive step nodes, a horizontal separator built from **three
short quads** (6 px wide, 1 px tall, 4 px gap between them) coloured
`ICE_BORDER` to mimic the dashed line in the mockup.

### Content region

A single `Container` named `content` with `BoxLayout(Y, None)` that swaps
its child on step change:

```java
content.clearChildren();
content.addChild(stepPanelFor(currentStep));
```

Each step panel is built once on widget construction, stored in a `Map<Step,
Container>`, and attached on demand. This keeps picking simple and avoids
`setCullHint` fighting with focus.

### Footer (72 px tall)

```
┌─ 24px ───────────────────────────────────────────────── 24px ─┐
│                                                                │
│  PROGRESSION                                                   │
│  ▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░    [Cancel] [Previous] [Next]        │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

- `ProgressBar` on the left, width 200 px, height 4 px, `ICE_ACCENT` fill
  over `ICE_BORDER` track. A 10 px label `"PROGRESSION"` sits above it.
- Nav buttons grouped on the right:
  - **Cancel** — `ICE_DANGER` background, `×` glyph + "Cancel".
  - **Previous** — `ICE_PANEL_BG_LIGHT` background, `<` glyph + "Previous".
    Appears visually disabled (opacity via
    `ICE_TEXT_SECONDARY` label) on Step 1.
  - **Next** — `ICE_ACCENT` background, "Next" + `>` glyph. Replaced by
    **Create mission** (`ICE_SUCCESS`, check + "Create mission") on Step 4.
- All buttons: 36 px tall, 10 px inner padding, 8 px gap between siblings.

## Custom components

### `SelectableCard`

Used in Step 1 (mission types) and Step 4 (launchers). Internally:

```
Container (BoxLayout.Y, fixed size 256×152)
 ├─ Icon row (centered)              ← IconComponent 48×48
 ├─ Title label (size 16, primary)
 ├─ Subtitle label (size 11, secondary)
 ├─ Value label (size 11, secondary) ← optional, e.g. "160 – 2000 km"
 └─ Badge slot (bottom)              ← optional Badge
```

Visual states:

| State | Background | Border |
|---|---|---|
| Idle | `ICE_PANEL_BG_LIGHT` | 1 px `ICE_BORDER` |
| Hover | `ICE_PANEL_BG_LIGHT` lightened +0.05α | 1 px `ICE_ACCENT` |
| Selected | `ICE_ROW_SELECTED` | 2 px `ICE_ACCENT` |
| Disabled ("SOON") | `ICE_PANEL_BG_LIGHT` alpha 0.30 | 1 px `ICE_BORDER` alpha 0.30, text in `ICE_TEXT_SECONDARY` |

Borders are emulated with a `TbtQuadBackgroundComponent` (same technique as
`MissionPanelStyles.createGradient`) — there is no real stroke in Lemur.

Click behaviour (wired but inert for this phase): hover and select state
toggles are applied locally; no event is published.

### `SegmentedControl`

Horizontal `Container` with `BoxLayout(X, None)`, two or three child
`Button`s sharing a common height. Active button uses `ICE_ACCENT`
background; inactive buttons use `ICE_PANEL_BG_LIGHT`. All buttons use
`ICE_TEXT_PRIMARY`.

Construction:
```java
new SegmentedControl("2 BURNS", "DIRECT", "HOHMANN").select(0);
```

Selection is a local state; no callbacks in this phase.

### `PopupList`

The dropdown replacement (user-chosen option). Two parts:

1. **Trigger row** — a `Container` styled like a `TextField`: 1 px
   `ICE_BORDER`, `ICE_PANEL_BG_LIGHT` fill, containing a `Label` with the
   current value and a right-aligned chevron glyph `"▾"` (or `"v"` if the
   default font can't render the triangle).
2. **Popup panel** — a child `Container` attached on click, positioned just
   below the trigger (same width), listing options as rows. Each row is a
   `Container` with `MouseEventControl` hover + click; the popup is closed
   by clicking outside (backdrop listener) or by selecting an item.

The popup panel is attached under the wizard's own root (not the modal
backdrop) so it inherits window z-order. The popup's background uses
`ICE_PANEL_BG` and a 1 px `ICE_ACCENT` border to match the mockup.

For Step 3 the cosmodrome options are the three literal strings:

```
Kourou (CSG) — French Guiana
Cape Canaveral (CCSFS) — Florida, USA
Baikonur — Kazakhstan
```

For Step 4 the payload type options are:

```
Communication satellite
Earth observation satellite
Scientific probe
Cargo module
```

These are hard-coded strings in the step files for this visual phase; no
domain model is wired.

### `Badge`

A small `Label` wrapped in a `Container` with a gradient pill background.
Three canned styles:

| Variant | BG | Text | Example |
|---|---|---|---|
| Success | `ICE_SUCCESS` alpha 0.80 | `ICE_TEXT_PRIMARY` | `v AVAILABLE` |
| Warning | `ICE_WARNING` alpha 0.80 | `ICE_TEXT_PRIMARY` | `o IN PROGRESS` |
| Muted | `ICE_PANEL_BG_LIGHT` | `ICE_TEXT_SECONDARY` | `SOON` |

Leading glyph is a single-character ASCII proxy for now (`v`, `o`) until
icon assets are added.

### `InfoBanner`

Horizontal container, height 48 px, used at the bottom of Step 2 for the
"Iterative altitude correction enabled…" message.

```
┌──┬───────────────────────────────────────────────────────┐
│ i│ Iterative altitude correction enabled: the mean       │
│  │ altitude over a full period (J2 filter) is used …     │
└──┴───────────────────────────────────────────────────────┘
```

- Left bar: 4 px wide, full-height, `ICE_ACCENT`.
- Fill: `ICE_PANEL_BG_LIGHT`.
- `"i"` icon slot (`assets/icons/wizard/info.png`, 16×16) followed by a
  multi-line `Label` in `ICE_TEXT_SECONDARY` size 12.

### `LabeledField`

Helper that stacks a 12 px secondary-colour label on top of an input
(`TextField`, `Slider`, `PopupList`, `SegmentedControl`, …). Standardises
spacing: 4 px between label and input, optional 12 px helper text below in
`ICE_TEXT_SECONDARY` size 10.

### `ProgressBar`

Two stacked quads in a 200×4 container:
- Track: `ICE_BORDER` alpha 0.50.
- Fill: `ICE_ACCENT`, width = `trackWidth * (stepIndex + 1) / 4`.

### Icon slots

All icons are reserved as `IconComponent` slots loaded from
`assets/icons/wizard/`. The spec lists the expected file names; **no PNG is
committed in this phase**. If the file is missing at runtime, the widget
falls back to an empty square of the same size (the `IconComponent` simply
renders nothing and keeps the layout stable).

| Slot | File |
|---|---|
| Brand globe | `brand-globe.png` |
| Info banner | `info.png` |
| Mission type LEO | `mission-leo.png` |
| Mission type GTO | `mission-gto.png` |
| Mission type SSO | `mission-sso.png` |
| Mission type MEO | `mission-meo.png` |
| Mission type GEO | `mission-geo.png` |
| Mission type TLI | `mission-tli.png` |
| Parameters field pencil | `field-pencil.png` |
| Parameters altitude | `field-altitude.png` |
| Parameters tolerance | `field-tolerance.png` |
| Parameters inclination | `field-inclination.png` |
| Parameters RAAN | `field-raan.png` |
| Parameters arg. perigee | `field-perigee.png` |
| Parameters strategy | `field-strategy.png` |
| Parameters launch date | `field-clock.png` |
| Site cosmodrome | `field-building.png` |
| Site latitude | `field-globe-lat.png` |
| Site longitude | `field-globe-lon.png` |
| Site ground altitude | `field-mountain.png` |
| Site launch heading | `field-compass.png` |
| Site atmospheric pressure | `field-pressure.png` |
| Launcher Falcon Heavy | `launcher-falcon-heavy.png` |
| Launcher Ariane 5 ECA | `launcher-ariane5.png` |
| Launcher OrbitLab Custom | `launcher-custom.png` |
| Launcher Custom (wrench) | `launcher-wrench.png` |
| Payload icon | `payload.png` |
| Tsiolkovsky check icon | `check.png` |

## Step 1 — `StepMissionType`

**Header block**
- Title: `"MISSION TYPE"` (size 20, `ICE_TEXT_PRIMARY`).
- Subtitle: `"// select the target orbit"` (size 12, `ICE_TEXT_SECONDARY`).

**Grid** — 3 columns × 2 rows of `SelectableCard`, 12 px gutter, cards 256×152.
Implemented as two stacked horizontal `Container`s.

| # | Title | Subtitle | Value | Badge | State |
|---|---|---|---|---|---|
| 1 | `LEO` | Low Earth Orbit | `160 – 2000 km` | `AVAILABLE` (success) | Selected by default |
| 2 | `GTO` | Geostationary Transfer | `200 × 35 786 km` | `IN PROGRESS` (warning) | Idle |
| 3 | `SSO` | Sun-Synchronous Orbit | `600 – 800 km` | `SOON` (muted) | Disabled |
| 4 | `MEO` | Medium Earth Orbit | `2000 – 35 786 km` | `SOON` | Disabled |
| 5 | `GEO` | Geostationary Orbit | `35 786 km` | `SOON` | Disabled |
| 6 | `TLI` | Trans-Lunar Injection | `Cislunar` | `SOON` | Disabled |

Grid layout:
```
row1 (X, None, gap 12):  [LEO] [GTO] [SSO]
row2 (X, None, gap 12):  [MEO] [GEO] [TLI]
```

The LEO card is constructed with the "Selected" visual state so Step 1
matches the reference screenshot 1:1 even without click wiring.

## Step 2 — `StepParameters`

**Header block**
- Title: `"PARAMETERS — LEO"`.
- Subtitle: `"// target orbit configuration"`.

**Form** — two-column layout (left column ≈ 400 px, right column ≈ 400 px,
24 px gutter). Each field is a `LabeledField`.

**Row 1** (full width, single column)
- Label: `"MISSION NAME"` with pencil icon.
- Input: `TextField` pre-filled `"ORBITLAB-LEO-001"`, full width.

**Row 2**
- **Left**: Label `"TARGET ALTITUDE"` + altitude icon.
  - `Slider` with visual range 160 → 2000 (labels underneath, size 10,
    `ICE_TEXT_SECONDARY`: `"160 km"` left, `"2000 km"` right).
  - Big value label to the right of the slider: `"550"` with tiny unit
    `"km"` next to it.
- **Right**: Label `"ALTITUDE TOLERANCE"` + tolerance icon.
  - `TextField` pre-filled `"1"`.
  - Helper text below: `"± km · CMA-ES convergence"`.

**Row 3**
- **Left**: Label `"INCLINATION"` + inclination icon.
  - `TextField` pre-filled `"51.6"`.
  - Helper: `"degrees · 0° = equatorial"`.
- **Right**: Label `"RAAN (Ω)"` + RAAN icon.
  - `TextField` pre-filled `"0.0"`.
  - Helper: `"degrees · ascending node"`.

**Row 4**
- **Left**: Label `"ARGUMENT OF PERIGEE (ω)"` + perigee icon.
  - `TextField` pre-filled `"0.0"`.
  - Helper: `"degrees"`.
- **Right**: Label `"INSERTION STRATEGY"` + strategy icon.
  - `SegmentedControl("2 BURNS", "DIRECT", "HOHMANN").select(0)`.
  - Helper: `"2 burns: gravity turn + circularisation"`.

**Row 5** (full width, single column)
- Label: `"LAUNCH DATE"` + clock icon.
- `TextField` pre-filled `"2026-06-15T06:00:00Z"`, fixed width 320 px.
- Helper: `"UTC · Orekit epoch"`.

**Footer of step 2** (before the shell footer)
- `InfoBanner` with text: `"Iterative altitude correction enabled: the mean
  altitude over a full period (J2 filter) is used as reference. Convergence
  in 2–3 iterations · tolerance 500 m."`

## Step 3 — `StepLaunchSite`

**Header block**
- Title: `"LAUNCH SITE"`.
- Subtitle: `"// cosmodrome selection"`.

**Row 1** (full width)
- Label: `"COSMODROME"` with building icon.
- `PopupList` trigger showing `"Kourou (CSG) — French Guiana"`.

**Row 2** (three columns, 33%/33%/33%)
- `"LATITUDE"` + globe-lat icon → `TextField` `"5.236"` · helper
  `"decimal degrees · N positive"`.
- `"LONGITUDE"` + globe-lon icon → `TextField` `"-52.769"` · helper
  `"decimal degrees · E positive"`.
- `"GROUND ALTITUDE"` + mountain icon → `TextField` `"14"` · helper
  `"meters MSL"`.

**Row 3** (two columns, 50%/50%)
- `"LAUNCH HEADING"` + compass icon → `TextField` `"90.0"` · helper
  `"azimuth · 90° = East"`.
- `"ATMOSPHERIC PRESSURE"` + pressure icon →
  `SegmentedControl("AUTO", "ISA", "MANUAL").select(0)` · helper
  `"ground atmospheric model"`.

**Row 4** — **Mini-map placeholder**
A `Container` 780×160 px with:
- Background: `ICE_PANEL_BG` deep blue.
- A grid pattern drawn with thin 1 px `ICE_BORDER` quads (e.g. 6 vertical
  + 3 horizontal lines, evenly spaced). Implemented as plain child
  containers with fixed dimensions and `QuadBackgroundComponent`.
- A single `IconComponent` dot (white circle 8×8) centered in the container
  → marks the selected cosmodrome.
- Bottom-right Label (size 10, `ICE_TEXT_SECONDARY`):
  `"CSG · KOUROU · 5.236°N 52.769°W"`.

This is intentionally a static visual approximation; no projection math.

## Step 4 — `StepLauncher`

**Header block**
- Title: `"LAUNCHER & PAYLOAD"`.
- Subtitle: `"// vehicle configuration"`.

**Vehicle grid** — 2 columns × 2 rows of `SelectableCard`, slightly taller
than Step 1 cards (300×112). Each card has a small left-aligned icon (40×40)
and multi-line body text.

| # | Title | Body | State |
|---|---|---|---|
| 1 | `FALCON HEAVY` | `S1 thrust: 22.8 MN` / `Isp S2: 348 s · LEO payload: 63.8 t` | Selected |
| 2 | `ARIANE 5 ECA` | `S1 thrust: 7.6 MN` / `Isp S2: 431 s · LEO payload: 21 t` | Idle |
| 3 | `ORBITLAB CUSTOM` | `S1: ~8.4 MN · S2: ~980 kN` / `Isp S2: 348 s · project config` | Idle |
| 4 | `CUSTOM` | `Define S1 & S2 parameters manually` | Idle |

Grid layout:
```
row1: [FALCON HEAVY]  [ARIANE 5 ECA]
row2: [ORBITLAB CUSTOM] [CUSTOM]
```

**Payload block** (full width, below the vehicle grid, 16 px spacing)
- Label: `"PAYLOAD"` + payload icon.
- Row container (X, None, 8 px gap):
  - `PopupList` trigger `"Communication satellite"`, 520 px wide.
  - `TextField` `"15000"`, 140 px wide.
  - `Label` `"kg"`, 40 px wide, `ICE_TEXT_SECONDARY`.
  - `Button` `"×"` (remove row), `ICE_DANGER` background, 32×32.

**Add-payload row**
A full-width "ghost" button (dashed-border look via 1 px `ICE_BORDER`
gradient, `ICE_PANEL_BG_LIGHT` fill): `"+ Add payload"`. In this phase it is
visual only (click ignored).

**Footer of step 4** (before the shell footer)
- `InfoBanner` (warning variant: left bar `ICE_WARNING` instead of
  `ICE_ACCENT`) containing: `"Tsiolkovsky check: required Δv ≈ 9 500 m/s ·
  available Δv S2 ≈ 11 200 m/s · v Feasibility confirmed"`.

**Shell footer change on Step 4**
- The Next button is replaced by `"v Create mission"` with `ICE_SUCCESS`
  background. Previous remains as is. Cancel remains as is.

## `MissionWizardStyles`

Mirrors the pattern of `MissionPanelStyles`:

```java
public static final String STYLE = "mission-wizard";

public static void init(AssetManager assetManager) {
    gradientTex = assetManager.loadTexture(
        "com/simsilica/lemur/icons/bordered-gradient.png");
    Styles styles = GuiGlobals.getInstance().getStyles();
    styles.applyStyles(STYLE, "glass");

    // Window container
    Attributes c = styles.getSelector("container", STYLE);
    c.set("background", createGradient(AppStyles.ICE_PANEL_BG));
    c.set("insets", new Insets3f(0, 0, 0, 0));

    // Label
    Attributes l = styles.getSelector("label", STYLE);
    l.set("color", AppStyles.ICE_TEXT_PRIMARY);
    l.set("fontSize", 14);

    // Button
    Attributes b = styles.getSelector("button", STYLE);
    b.set("background", createGradient(AppStyles.ICE_PANEL_BG_LIGHT));
    b.set("color", AppStyles.ICE_TEXT_PRIMARY);
    b.set("fontSize", 14);
    b.set("insets", new Insets3f(6, 10, 6, 10));

    // TextField
    Attributes tf = styles.getSelector("textField", STYLE);
    tf.set("background", createGradient(AppStyles.ICE_PANEL_BG_LIGHT));
    tf.set("color", AppStyles.ICE_TEXT_PRIMARY);
    tf.set("fontSize", 14);
    tf.set("insets", new Insets3f(6, 8, 6, 8));

    // Slider
    Attributes s = styles.getSelector("slider", STYLE);
    s.set("background", createGradient(AppStyles.ICE_BORDER));
}
```

The helper `createGradient(ColorRGBA)` is a near-duplicate of the one in
`MissionPanelStyles` — kept local to the wizard package rather than pulled
into `AppStyles` (the existing convention puts gradient helpers next to
their style class).

## Cross-cutting behaviours (visual only)

- **Hover** — every `SelectableCard`, `Button`, `PopupList` row, stepper
  circle and nav button installs a `DefaultMouseListener` that swaps its
  background to a slightly brighter variant on
  `mouseEntered`/`mouseExited`. No callbacks beyond that.
- **Focus** — not handled in this phase. `TextField`s use Lemur's default
  focus behaviour; we do not request focus programmatically.
- **Window drag** — the window is fixed-center; not draggable.
- **Resize** — `MissionWizardWidget.update(tpf)` re-centers the window and
  resizes the modal backdrop to current camera width/height each frame
  (cheap: just two `setLocalTranslation`/`setPreferredSize` calls guarded by
  a dirty flag).
- **Debug step switcher** — because Next/Previous are stubs in this phase,
  `MissionWizardWidget` exposes a package-private
  `showStep(MissionWizardStep step)` method. A key binding (e.g. `F8`,
  registered in `MissionWizardAppState`) cycles through steps so all four
  screens can be visually reviewed without writing interaction logic.

## File-by-file summary

| File | Status | Purpose |
|---|---|---|
| `engine/scene/graph/GuiGraph.java` | **Modified** | Add `modalNode` + `getModalNode()` |
| `ui/AppStyles.java` | **Modified** | Call `MissionWizardStyles.init(am)` |
| `OrbitLabApplication.java` | **Modified** | Attach `MissionWizardAppState`; wire `uiWantsMouse` |
| `states/camera/OrbitCameraAppState.java` | **Modified** | (only if `uiWantsMouse` is currently inlined) read from context |
| `ui/mission/wizard/MissionWizardStyles.java` | **New** | Lemur style tokens |
| `ui/mission/wizard/MissionWizardStep.java` | **New** | Enum MISSION/PARAMETERS/SITE/LAUNCHER |
| `ui/mission/wizard/MissionWizardWidget.java` | **New** | Modal root + shell + step swap |
| `ui/mission/wizard/ModalBackdrop.java` | **New** | Full-screen backdrop + pick eater |
| `ui/mission/wizard/WizardStepper.java` | **New** | Header breadcrumb |
| `ui/mission/wizard/WizardFooter.java` | **New** | Progress bar + nav buttons |
| `ui/mission/wizard/component/SelectableCard.java` | **New** | Clickable card |
| `ui/mission/wizard/component/SegmentedControl.java` | **New** | Button-group toggle |
| `ui/mission/wizard/component/PopupList.java` | **New** | Dropdown replacement |
| `ui/mission/wizard/component/Badge.java` | **New** | Status pill |
| `ui/mission/wizard/component/InfoBanner.java` | **New** | Accent-bar info box |
| `ui/mission/wizard/component/LabeledField.java` | **New** | Label + input helper |
| `ui/mission/wizard/component/ProgressBar.java` | **New** | Track + fill quads |
| `ui/mission/wizard/step/StepMissionType.java` | **New** | Step 1 content |
| `ui/mission/wizard/step/StepParameters.java` | **New** | Step 2 content |
| `ui/mission/wizard/step/StepLaunchSite.java` | **New** | Step 3 content |
| `ui/mission/wizard/step/StepLauncher.java` | **New** | Step 4 content |
| `states/mission/MissionWizardAppState.java` | **New** | Lifecycle + key binding |

## Verification

Because this phase produces no domain-model changes and no actions, the spec
is verified **visually**:

1. `./gradlew classes` — compile clean.
2. Launch OrbitLab: `./gradlew run` (or main class
   `OrbitLabApplication`).
3. Open the wizard via the debug hook (see below) or by temporarily hooking
   `MissionPanelWidget.onCreate()` to
   `MissionWizardAppState.setEnabled(true)`.
4. Confirm, for each of the four steps:
   - Layout matches the reference screenshots at 1920×1080 (window centered,
     stepper top, footer bottom).
   - Hover states on cards, stepper circles and buttons visibly change
     background.
   - The scene behind the modal is dimmed (backdrop visible).
   - Clicking through the backdrop on a planet does **not** select it
     (input blocked).
   - Dragging the mouse over the scene does **not** rotate the camera
     (camera lock active).
   - Clicking **Cancel** closes the modal, re-enables camera input, and the
     mission panel behind it becomes clickable again.
5. Press `F8` (debug step switcher) repeatedly → wizard cycles through
   Mission → Parameters → Site → Launcher → Mission. All four steps render
   without errors in the JME log.
6. Run existing tests to guard against regression in unrelated AppStates:
   ```
   ./gradlew test
   ```

**Out-of-scope checks** (deferred to later phases): form validation, value
binding, navigation button behaviour, Create-mission wiring, icon assets,
localisation.
