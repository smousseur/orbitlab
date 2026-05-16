# Spec technique — Mission Display Panel

Spec de référence fonctionnelle : [01-mission-display-panel.md](01-mission-display-panel.md).
Ce document décrit l'implémentation concrète : fichiers à créer/modifier,
signatures, ordre des changements et critères de compilation.

## Sommaire

1. [Architecture cible](#1-architecture-cible)
2. [Modifications du modèle de données](#2-modifications-du-modèle-de-données)
3. [Palette de couleurs](#3-palette-de-couleurs)
4. [EventBus — nouveaux events](#4-eventbus--nouveaux-events)
5. [Nouveau widget — MissionDisplayPanelWidget](#5-nouveau-widget--missiondisplaypanelwidget)
6. [Nouvel AppState — MissionDisplayPanelAppState](#6-nouvel-appstate--missiondisplaypanelappstate)
7. [Modifications des composants existants](#7-modifications-des-composants-existants)
8. [Assets à ajouter](#8-assets-à-ajouter)
9. [Wiring OrbitLabApplication](#9-wiring-orbitlabapplication)
10. [Plan d'implémentation séquencé](#10-plan-dimplémentation-séquencé)
11. [Critères de validation](#11-critères-de-validation)

---

## 1. Architecture cible

```
                        ┌────────────────────────────────────┐
                        │      OrbitLabApplication           │
                        │  (simpleInitApp registers states)  │
                        └─────────────────┬──────────────────┘
                                          │
       ┌──────────────────────────────────┼─────────────────────────────────┐
       │                                  │                                 │
       ▼                                  ▼                                 ▼
┌────────────────┐         ┌──────────────────────────────┐     ┌─────────────────────┐
│ MissionOrches  │         │ MissionDisplayPanelAppState  │     │ MissionPanelWidget  │
│ tratorAppState │  reads  │  (NEW)                       │     │ AppState            │
│                ◄─────────┤  - owns MissionPanelTrigger  │     │  (modified)         │
│  - renderers   │ entries │  - owns DisplayPanelWidget   │     │  - opens modal on   │
│  - status      │         │  - polls status transitions  │     │    OpenMissionMgmt  │
│    transitions │         │  - applies R1..R10 rules     │     └──────────┬──────────┘
└──────┬─────────┘         │  - publishes mission actions │                │
       │                   └──────────────┬───────────────┘                │
       │                                  │                                │
       │                                  ▼                                ▼
       │                          MissionContext                MissionPanelWidget
       │                          - missions[]                  (modal, simplified)
       │                          - telemetryFocusMissionName   - no eye action
       │                          - colors via                  - badge on telemetered
       │                            MissionColorPalette           row
       │                                  ▲
       ▼                                  │
MissionRenderer                  TelemetryWidgetAppState
  - uses entry.color             - reads telemetryFocusMissionName
  - per-mission trajectory       - widget visible iff:
  - per-mission spacecraft         focus + READY + visible + ephemeris
```

**Sources de vérité :**

- `MissionContext.missions` : liste persistante des entries (chaque entry porte sa couleur, sa visibilité, son statut).
- `MissionContext.telemetryFocusMissionName` : nom de la mission télémétrée (ou `null`).
- Tous les widgets et AppStates **lisent** ces sources et publient des intentions via `EventBus`.

---

## 2. Modifications du modèle de données

### 2.1 `MissionEntry` — ajout d'un champ couleur

Fichier : [MissionEntry.java](../../src/main/java/com/smousseur/orbitlab/simulation/mission/context/MissionEntry.java)

```java
// nouveau champ (volatile pour cohérence avec les autres champs mutables)
private volatile ColorRGBA color;

// nouveau getter / setter
public ColorRGBA getColor() {
  return color;
}

public void setColor(ColorRGBA color) {
  this.color = color;
}
```

Choix : champ `volatile` mutable plutôt que constructeur paramétré pour ne pas
casser l'API existante (`new MissionEntry(mission)` reste valide ; la couleur
est attribuée par `MissionContext` au moment de `addMission`).

### 2.2 `MissionContext` — état télémètrie + injection couleur

Fichier : [MissionContext.java](../../src/main/java/com/smousseur/orbitlab/simulation/mission/context/MissionContext.java)

```java
// nouveau champ
private volatile String telemetryFocusMissionName;

// nouveaux accesseurs
public String getTelemetryFocusMissionName() {
  return telemetryFocusMissionName;
}

public void setTelemetryFocusMissionName(String name) {
  this.telemetryFocusMissionName = name;
}

public Optional<MissionEntry> getTelemetryFocusMission() {
  String name = telemetryFocusMissionName;
  if (name == null) return Optional.empty();
  return findMission(name);
}
```

Les deux overloads `addMission` sont modifiés pour **assigner une couleur**
avant l'ajout à la liste :

```java
public void addMission(Mission mission) {
  MissionEntry entry = new MissionEntry(mission);
  assignColor(entry);
  missions.add(entry);
}

public void addMission(MissionEntry entry) {
  if (entry.getColor() == null) {
    assignColor(entry);
  }
  missions.add(entry);
}

private void assignColor(MissionEntry entry) {
  List<ColorRGBA> inUse =
      missions.stream().map(MissionEntry::getColor).filter(Objects::nonNull).toList();
  entry.setColor(MissionColorPalette.pickFree(inUse));
}
```

`selectedMissionName` reste en place (utilisé par la modal pour son footer
details), conformément à la décision #4 (cf. discussion technique).

### 2.3 `MissionStatus` — pas de changement

Les valeurs existantes (`DRAFT`, `COMPUTING`, `READY`, `FAILED`) suffisent.

---

## 3. Palette de couleurs

### 3.1 Nouveau fichier — `MissionColorPalette`

Fichier nouveau : `src/main/java/com/smousseur/orbitlab/ui/mission/MissionColorPalette.java`

```java
package com.smousseur.orbitlab.ui.mission;

import com.jme3.math.ColorRGBA;
import java.util.Collection;
import java.util.List;

/**
 * Cyclic palette of 8 distinct trajectory colors used to identify missions
 * across the Display Panel, the modal and the 3D scene.
 *
 * <p>Picking strategy: first color of {@link #PALETTE} that is not currently
 * used by any active mission. If all 8 are used, recycle in round-robin from
 * index 0 (visual collision accepted).
 */
public final class MissionColorPalette {

  public static final List<ColorRGBA> PALETTE = List.of(
      new ColorRGBA(0.30f, 0.65f, 0.90f, 1f), // Cyan (matches ICE_ACCENT)
      new ColorRGBA(0.85f, 0.35f, 0.75f, 1f), // Magenta
      new ColorRGBA(0.55f, 0.85f, 0.30f, 1f), // Lime
      new ColorRGBA(0.95f, 0.60f, 0.25f, 1f), // Orange
      new ColorRGBA(0.95f, 0.85f, 0.30f, 1f), // Yellow
      new ColorRGBA(0.65f, 0.45f, 0.95f, 1f), // Violet
      new ColorRGBA(0.95f, 0.55f, 0.55f, 1f), // Salmon
      new ColorRGBA(0.25f, 0.80f, 0.75f, 1f)  // Teal
  );

  private static int recycleCursor = 0;

  private MissionColorPalette() {}

  /**
   * Returns the first palette color not present in {@code inUse}, or, if
   * all colors are used, the next color in round-robin order.
   */
  public static synchronized ColorRGBA pickFree(Collection<ColorRGBA> inUse) {
    for (ColorRGBA c : PALETTE) {
      if (!containsColor(inUse, c)) return c;
    }
    ColorRGBA c = PALETTE.get(recycleCursor % PALETTE.size());
    recycleCursor++;
    return c;
  }

  private static boolean containsColor(Collection<ColorRGBA> list, ColorRGBA c) {
    for (ColorRGBA x : list) {
      if (x.r == c.r && x.g == c.g && x.b == c.b && x.a == c.a) return true;
    }
    return false;
  }
}
```

`ColorRGBA` n'a pas de `equals` réflexif fiable côté JME3 ; la comparaison se
fait composante par composante.

### 3.2 Suppression de l'ancienne palette

Dans [MissionOrchestratorAppState.java:40-47](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionOrchestratorAppState.java#L40-L47) :
- supprimer `TRAJECTORY_PALETTE` et `colorIndex` ;
- supprimer l'attribution dans `createRenderer` (la couleur vient désormais de `entry.getColor()`).

---

## 4. EventBus — nouveaux events

Fichier : [EventBus.java](../../src/main/java/com/smousseur/orbitlab/engine/events/EventBus.java)

### 4.1 Étendre `UiNavigationEvent` avec `OpenMissionManagement`

```java
public sealed interface UiNavigationEvent
    permits UiNavigationEvent.OpenMissionWizard,
            UiNavigationEvent.CreateMission,
            UiNavigationEvent.OpenMissionManagement {

  record OpenMissionWizard() implements UiNavigationEvent {}

  record CreateMission(Map<String, Object> values) implements UiNavigationEvent {
    public CreateMission {
      Objects.requireNonNull(values, "values");
      values = Map.copyOf(values);
    }
  }

  /** Request to open the mission management modal. */
  record OpenMissionManagement() implements UiNavigationEvent {}
}
```

### 4.2 Nouveau record et file dédiée pour la télémètrie

Décision #1 (option B) : record dédié avec `missionName` **nullable** (null = clear).

```java
// -------------------------------------------------------------------------
// Mission telemetry focus events
// -------------------------------------------------------------------------

/**
 * Request to change the telemetry focus.
 *
 * @param missionName the mission to follow, or {@code null} to clear the focus
 */
public record MissionTelemetryFocusRequest(String missionName) {
  // missionName may be null (clear); no Objects.requireNonNull
}

private final ConcurrentLinkedQueue<MissionTelemetryFocusRequest> telemetryFocusQueue =
    new ConcurrentLinkedQueue<>();

public void publishTelemetryFocus(String missionName) {
  telemetryFocusQueue.add(new MissionTelemetryFocusRequest(missionName));
}

public MissionTelemetryFocusRequest pollTelemetryFocus() {
  return telemetryFocusQueue.poll();
}
```

### 4.3 Inchangés

`MissionActionRequest(name, MissionAction.{OPTIMIZE,TOGGLE_VISIBLE,DELETE})` :
sémantique inchangée. `TOGGLE_VISIBLE` perd seulement son effet de bord singleton
côté orchestrateur (cf. §7.1).

---

## 5. Nouveau widget — `MissionDisplayPanelWidget`

Fichier nouveau : `src/main/java/com/smousseur/orbitlab/ui/mission/display/MissionDisplayPanelWidget.java`

### 5.1 Responsabilités

- Construire un panneau Lemur ancré haut-gauche, non-modal.
- Refléter `MissionContext` filtré sur `MissionStatus.READY`.
- Exposer trois actions par ligne : **set telemetry**, **clear telemetry**, **toggle visibility**.
- Publier deux types d'événements via `EventBus` :
  - `MissionActionRequest(name, TOGGLE_VISIBLE)` pour l'œil.
  - `MissionTelemetryFocusRequest(nameOrNull)` pour l'icône télémètrie.
- Exposer un callback `onManageClicked` pour le bouton "Manage" du header.
- Exposer un callback `onCreateClicked` pour le bouton "Create mission" en état vide.

### 5.2 Squelette de classe

```java
package com.smousseur.orbitlab.ui.mission.display;

public final class MissionDisplayPanelWidget implements AutoCloseable {

  private static final float WINDOW_WIDTH = 320f;
  private static final float HEADER_HEIGHT = 36f;
  private static final float FOOTER_HEIGHT = 30f;
  private static final float ROW_HEIGHT = 36f;
  private static final int MAX_VISIBLE_ROWS = 8;
  private static final float MARGIN_PX = AppStyles.HUD_MARGIN_PX;

  private final ApplicationContext context;
  private final Container root;
  private final Container listContainer;     // holds the rows
  private final Container emptyState;        // shown when no READY mission
  private final Container footer;
  private final Label counterLabel;          // "N visible / M total"
  private final Button hideAllButton;

  private boolean visible = true;
  private List<RowSnapshot> lastSnapshot = List.of();

  private Runnable onManageClicked = () -> {};
  private Runnable onCreateClicked = () -> {};

  public MissionDisplayPanelWidget(ApplicationContext context) { ... }

  public void attachTo(Node parent) { parent.attachChild(root); visible = true; }

  @Override public void close() { root.removeFromParent(); visible = false; }

  public boolean isVisible() { return visible; }
  public void setVisible(boolean v) { ... }     // attach/detach + flag

  public void setOnManageClicked(Runnable r) { ... }
  public void setOnCreateClicked(Runnable r) { ... }

  /** Called every frame; refreshes only if {@link #buildSnapshot} differs. */
  public void update(float tpf, Camera cam) { ... }

  /** Layout top-left like the trigger. */
  public void layoutTopLeft(int screenWidth, int screenHeight) { ... }

  // --- internals --------------------------------------------------------

  private record RowSnapshot(
      String name, MissionStatus status, ColorRGBA color,
      boolean visible, boolean telemetered, String subtitle) {}

  private List<RowSnapshot> buildSnapshot() { ... }   // diff key
  private void rebuildList(List<RowSnapshot> snapshot) { ... }
}
```

### 5.3 Structure des fichiers compagnons

Le widget délègue la construction des sous-éléments à des classes locales au
package `ui/mission/display/`, sur le même pattern que la modal
(`PanelHeader`, `PanelFooter`, `MissionListView`, `MissionRow`) :

| Classe | Rôle |
|---|---|
| `DisplayPanelHeader` | Titre "MISSIONS" + bouton "Manage" |
| `DisplayPanelFooter` | Compteur + bouton "Hide all" |
| `DisplayRow` | Une ligne de mission (color swatch + telemetry icon + name + subtitle + eye) |
| `DisplayRowIcons` | Helpers pour les icônes télémètrie/visibilité (équivalent de `RowActionIcons` côté modal) |
| `DisplayPanelEmptyState` | Bloc "No mission computed yet" + bouton "Create mission" |

### 5.4 Construction d'une ligne

```java
final class DisplayRow {

  static final float HEIGHT = 36f;

  private final Container root;

  DisplayRow(RowSnapshot s, RowListener listener) {
    root = new Container(new BoxLayout(Axis.X, FillMode.None), FormStyles.STYLE);
    root.setPreferredSize(new Vector3f(MissionDisplayPanelWidget.WINDOW_WIDTH, HEIGHT, 0));
    root.setInsetsComponent(new InsetsComponent(new Insets3f(4, 10, 4, 10)));
    root.setBackground(buildRowBackground(s.telemetered()));

    // 1. Color swatch (12×12)
    Container swatch = new Container();
    swatch.setPreferredSize(new Vector3f(12, 12, 0));
    swatch.setBackground(new QuadBackgroundComponent(s.color()));
    root.addChild(swatch);
    root.addChild(UiKit.hSpacer(8));

    // 2. Telemetry icon (16×16) — clickable toggle
    root.addChild(DisplayRowIcons.telemetryIconButton(
        s.telemetered(),
        () -> listener.onToggleTelemetry(s.name(), s.telemetered())));
    root.addChild(UiKit.hSpacer(8));

    // 3. Name + subtitle (flex)
    Container labels = new Container(new BoxLayout(Axis.Y, FillMode.None));
    labels.setBackground(null);
    labels.setPreferredSize(new Vector3f(/* flex */, HEIGHT, 0));
    Label nameLabel = labels.addChild(new Label(s.name(), FormStyles.STYLE));
    nameLabel.setFont(UiKit.sora(14));
    nameLabel.setColor(FormStyles.TEXT_PRIMARY);
    Label subtitleLabel = labels.addChild(new Label(s.subtitle(), FormStyles.STYLE));
    subtitleLabel.setFont(UiKit.sora(12));
    subtitleLabel.setColor(FormStyles.TEXT_SECONDARY);
    root.addChild(labels);

    // 4. Visibility eye (16×16)
    root.addChild(DisplayRowIcons.visibilityIconButton(
        s.visible(),
        () -> listener.onToggleVisibility(s.name())));
  }

  interface RowListener {
    void onToggleTelemetry(String missionName, boolean currentlyTelemetered);
    void onToggleVisibility(String missionName);
  }
}
```

### 5.5 Icônes — `DisplayRowIcons`

Reproduit le pattern de `RowActionIcons.visualizeIconButton` (textures
`normal`/`hover`/`disabled`) :

```java
static Container telemetryIconButton(boolean active, Runnable onClick) {
  String idleTex = active ? "icon-action-telemetry" : "icon-action-telemetry-disabled";
  String hoverTex = "icon-action-telemetry-hover";
  return buildToggleIcon(idleTex, hoverTex, onClick);
}

static Container visibilityIconButton(boolean on, Runnable onClick) {
  String idleTex = on ? "icon-action-view" : "icon-action-view-disabled";
  String hoverTex = "icon-action-view-hover";
  return buildToggleIcon(idleTex, hoverTex, onClick);
}
```

`buildToggleIcon` est un helper identique au pattern existant dans
[RowActionIcons.java:73-109](../../src/main/java/com/smousseur/orbitlab/ui/mission/panel/RowActionIcons.java#L73-L109).

### 5.6 Header — bouton Manage

```java
final class DisplayPanelHeader {
  DisplayPanelHeader(float width, Runnable onManage) {
    // "MISSIONS" label à gauche, sora(14), TEXT_PRIMARY
    // bouton à droite : icon-action-manage + label "Manage"
    //   au clic : onManage.run()
  }
}
```

L'event `OpenMissionManagement` est publié par `MissionDisplayPanelAppState`
au clic, pas directement par le widget — le widget signale via callback
(`setOnManageClicked`).

### 5.7 Footer — compteur + Hide all

```java
final class DisplayPanelFooter {
  void refresh(int visibleCount, int totalCount, Runnable onHideAll) {
    counterLabel.setText(visibleCount + " visible / " + totalCount + " total");
    hideAllButton.setVisible(visibleCount > 0);
    hideAllButton.setOnClick(onHideAll);
  }
}
```

### 5.8 État vide

`DisplayPanelEmptyState` remplace `listContainer` quand
`snapshot.isEmpty()`. Contient le texte "No mission computed yet." et un
bouton "+ Create mission" qui appelle `onCreateClicked`. Le footer est
masqué (`footer.setVisible(false)`).

### 5.9 Layout

```java
public void layoutTopLeft(int screenWidth, int screenHeight) {
  // top-left, sous le trigger button
  float triggerHeight = 28f;     // hauteur visuelle du trigger
  float triggerGap = 8f;
  float y = screenHeight - MARGIN_PX - triggerHeight - triggerGap;
  root.setLocalTranslation(MARGIN_PX, y, 0f);
}
```

---

## 6. Nouvel AppState — `MissionDisplayPanelAppState`

Fichier nouveau : `src/main/java/com/smousseur/orbitlab/states/mission/MissionDisplayPanelAppState.java`

### 6.1 Responsabilités

1. Posséder le `MissionPanelTrigger` (haut-gauche).
2. Posséder le `MissionDisplayPanelWidget` (persistant ; show/hide via le trigger).
3. À chaque frame :
   - Faire un snapshot des entrées et de leurs statuts.
   - Détecter les transitions de statut (`previous → current`) pour appliquer R1, R9.
   - Détecter les disparitions d'entries (R10).
   - Mettre à jour le widget (`update(tpf, cam)`).
4. Sur action UI (callbacks du widget) :
   - **Telemetry toggle** : publier `MissionTelemetryFocusRequest`.
   - **Visibility toggle** : publier `MissionActionRequest(name, TOGGLE_VISIBLE)`.
   - **Hide all** : itérer les entries et publier des `TOGGLE_VISIBLE` ; publier ensuite `MissionTelemetryFocusRequest(null)` si la mission télémétrée était visible.
   - **Manage click** : publier `OpenMissionManagement`.
   - **Create mission click (état vide)** : publier `OpenMissionWizard`.
5. Drainer la file `pollTelemetryFocus()` et appliquer les règles R3/R4 sur `MissionContext`.

### 6.2 Squelette

```java
package com.smousseur.orbitlab.states.mission;

public final class MissionDisplayPanelAppState extends BaseAppState {

  private final ApplicationContext context;
  private MissionPanelTrigger trigger;
  private MissionDisplayPanelWidget widget;

  /** Last known status per mission name, used to detect transitions for R1/R9. */
  private final Map<String, MissionStatus> previousStatuses = new HashMap<>();

  public MissionDisplayPanelAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    int sw = app.getCamera().getWidth();
    int sh = app.getCamera().getHeight();

    trigger = new MissionPanelTrigger(context);
    trigger.layoutTopLeft(sw, sh);
    trigger.setOnClick(this::togglePanel);

    widget = new MissionDisplayPanelWidget(context);
    widget.layoutTopLeft(sw, sh);
    widget.setOnManageClicked(this::publishOpenManagement);
    widget.setOnCreateClicked(this::publishOpenWizard);
    widget.setRowListener(buildRowListener());
    widget.setOnHideAll(this::handleHideAll);
    widget.attachTo(context.guiGraph().getMissionPanelNode());

    trigger.setEnabled(true); // panel is visible by default
  }

  @Override
  public void update(float tpf) {
    drainTelemetryFocusEvents();
    applyStatusTransitionRules();   // R1, R9, R10
    widget.update(tpf, getApplication().getCamera());
  }

  @Override
  protected void cleanup(Application app) {
    if (widget != null) widget.close();
    if (trigger != null) trigger.close();
  }

  // --- handlers ---------------------------------------------------------

  private void togglePanel() {
    widget.setVisible(!widget.isVisible());
  }

  private void publishOpenManagement() {
    context.eventBus().publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionManagement());
  }

  private void publishOpenWizard() {
    context.eventBus().publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard());
  }

  private MissionDisplayPanelWidget.RowListener buildRowListener() {
    EventBus bus = context.eventBus();
    return new MissionDisplayPanelWidget.RowListener() {
      @Override
      public void onToggleTelemetry(String name, boolean currentlyOn) {
        bus.publishTelemetryFocus(currentlyOn ? null : name);
      }

      @Override
      public void onToggleVisibility(String name) {
        bus.publishMissionAction(name, EventBus.MissionAction.TOGGLE_VISIBLE);
      }
    };
  }

  private void handleHideAll() {
    MissionContext mc = context.missionContext();
    for (MissionEntry entry : mc.getMissions()) {
      if (entry.mission().getStatus() == MissionStatus.READY && entry.isVisible()) {
        context.eventBus()
            .publishMissionAction(entry.mission().getName(), EventBus.MissionAction.TOGGLE_VISIBLE);
      }
    }
    // R8: clear telemetry on hide-all
    context.eventBus().publishTelemetryFocus(null);
  }

  // --- telemetry focus pipeline ----------------------------------------

  private void drainTelemetryFocusEvents() {
    EventBus.MissionTelemetryFocusRequest req;
    while ((req = context.eventBus().pollTelemetryFocus()) != null) {
      applyTelemetryFocus(req.missionName());
    }
  }

  /**
   * Applies a new telemetry focus value, enforcing R3 (force visible) and
   * the exclusivity invariant. {@code missionName} may be null to clear.
   */
  private void applyTelemetryFocus(String missionName) {
    MissionContext mc = context.missionContext();
    if (missionName == null) {
      mc.setTelemetryFocusMissionName(null);
      return;
    }
    Optional<MissionEntry> target = mc.findMission(missionName);
    if (target.isEmpty() || target.get().mission().getStatus() != MissionStatus.READY) {
      return; // ignore request for non-existing or non-READY mission
    }
    MissionEntry entry = target.get();
    if (!entry.isVisible()) {
      entry.setVisible(true); // R3: force visible
    }
    mc.setTelemetryFocusMissionName(missionName);
  }

  // --- status transition rules R1 / R9 / R10 ---------------------------

  private void applyStatusTransitionRules() {
    MissionContext mc = context.missionContext();
    Set<String> currentNames = new HashSet<>();

    for (MissionEntry entry : mc.getMissions()) {
      String name = entry.mission().getName();
      currentNames.add(name);
      MissionStatus current = entry.mission().getStatus();
      MissionStatus prev = previousStatuses.get(name);

      if (prev != current) {
        onStatusTransition(entry, prev, current, mc);
        previousStatuses.put(name, current);
      }
    }

    // R10: detect deleted missions (present last frame, absent now)
    Iterator<Map.Entry<String, MissionStatus>> it = previousStatuses.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, MissionStatus> e = it.next();
      if (!currentNames.contains(e.getKey())) {
        if (e.getKey().equals(mc.getTelemetryFocusMissionName())) {
          mc.setTelemetryFocusMissionName(null);
        }
        it.remove();
      }
    }
  }

  private void onStatusTransition(
      MissionEntry entry, MissionStatus prev, MissionStatus current, MissionContext mc) {
    String name = entry.mission().getName();

    // R9: telemetered mission leaves READY
    if (prev == MissionStatus.READY && current != MissionStatus.READY) {
      if (name.equals(mc.getTelemetryFocusMissionName())) {
        mc.setTelemetryFocusMissionName(null);
      }
    }

    // R1: mission enters READY and no telemetry is set → auto-on (and visible)
    if (current == MissionStatus.READY && mc.getTelemetryFocusMissionName() == null) {
      entry.setVisible(true);
      mc.setTelemetryFocusMissionName(name);
    }
    // R2 is implicit: if telemetry is already set, no auto-on, and the entry
    // stays with its current visibility (false by default for new entries).
  }

  @Override protected void onEnable() {}
  @Override protected void onDisable() {}
}
```

### 6.3 Couplage avec la visibilité (règle R5)

R5 ("cacher la mission télémétrée désactive la télémètrie") n'est pas
appliquée ici directement, parce que `TOGGLE_VISIBLE` est consommé par
`MissionOrchestratorAppState`. Deux options :

- **Option α** : déplacer R5 dans `MissionOrchestratorAppState.pollMissionActions`
  (au point où la visibilité bascule).
- **Option β** : laisser `MissionOrchestratorAppState` faire son travail, et
  `MissionDisplayPanelAppState` détecte ensuite (frame suivante) que la
  mission télémétrée est devenue invisible et clear le focus.

→ **Choix : option α**. R5 est sémantiquement liée à `TOGGLE_VISIBLE`, et
le délai d'une frame en option β est observable par le widget télémètrie.

Implémentation dans `MissionOrchestratorAppState.pollMissionActions`,
branche `TOGGLE_VISIBLE` :

```java
case TOGGLE_VISIBLE ->
    context.missionContext().findMission(name).ifPresent(entry -> {
      if (entry.mission().getStatus() != MissionStatus.READY) return;
      boolean turningOn = !entry.isVisible();
      entry.setVisible(turningOn);
      // R5: hiding the telemetered mission clears telemetry
      if (!turningOn && name.equals(context.missionContext().getTelemetryFocusMissionName())) {
        context.missionContext().setTelemetryFocusMissionName(null);
      }
    });
```

Le bloc `for` qui cachait les autres missions (lignes 142-147) est **supprimé**.

---

## 7. Modifications des composants existants

### 7.1 `MissionOrchestratorAppState`

Fichier : [MissionOrchestratorAppState.java](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionOrchestratorAppState.java)

| Modification | Lignes actuelles |
|---|---|
| Supprimer `TRAJECTORY_PALETTE` et `colorIndex` | 40-47, 52 |
| Dans `createRenderer`, remplacer l'attribution par `entry.getColor()` | 196-205 |
| Dans `pollMissionActions` / `TOGGLE_VISIBLE`, supprimer le `for` singleton et ajouter R5 | 131-149 |

Après modification, `createRenderer` devient :

```java
private MissionRenderer createRenderer(MissionEntry entry) {
  RenderContext renderContext = RenderContext.planet(entry.mission().getObjective().body());
  ColorRGBA color = entry.getColor();
  if (color == null) color = ColorRGBA.Cyan; // safety net (should never happen)
  MissionRenderer renderer = new MissionRenderer(entry, context, renderContext, color);
  renderer.initialize();
  return renderer;
}
```

### 7.2 `MissionRenderer`

Fichier : [MissionRenderer.java](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionRenderer.java)

Aucun changement de signature : la couleur reste un paramètre du constructeur.
L'orchestrateur la passe désormais depuis `entry.getColor()` au lieu de la palette interne.

### 7.3 `TelemetryWidgetAppState`

Fichier : [TelemetryWidgetAppState.java](../../src/main/java/com/smousseur/orbitlab/states/mission/TelemetryWidgetAppState.java)

Dans `update(float tpf)`, remplacer la lecture de `getSelectedMission()` par
`getTelemetryFocusMission()` :

```java
@Override
public void update(float tpf) {
  MissionContext mc = context.missionContext();
  Optional<MissionEntry> focus = mc.getTelemetryFocusMission();

  if (focus.isEmpty()
      || focus.get().mission().getStatus() != MissionStatus.READY
      || !focus.get().isVisible()) {
    widget.setVisible(false);
    return;
  }

  MissionEntry entry = focus.get();
  MissionEphemeris eph = entry.getEphemeris().orElse(null);
  if (eph == null) {
    widget.setVisible(false);
    return;
  }

  widget.setVisible(true);
  widget.updateFromEphemeris(eph, context.clock().now(), entry.mission());
}
```

### 7.4 `MissionPanelTrigger`

Fichier : [MissionPanelTrigger.java](../../src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionPanelTrigger.java)

**Aucun changement de classe.** Seul son consommateur (`MissionDisplayPanelAppState`)
change : `setOnClick` route désormais vers `togglePanel()` du HUD au lieu de la modal.

### 7.5 `MissionPanelWidgetAppState` (modal)

Fichier : [MissionPanelWidgetAppState.java](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionPanelWidgetAppState.java)

Cet AppState devient l'**opener on-demand de la modal** :

- Ne possède plus `MissionPanelTrigger` (déplacé dans `MissionDisplayPanelAppState`).
- Garde la création de la mission de bootstrap (`new LEOMission("LEO", 400_000)`) ou la déplace dans `InitAppState` si plus cohérent (à laisser tel quel pour minimiser le diff).
- Drain `pollUiNavigation()` chaque frame ; ouvre la modal sur `OpenMissionManagement`.

```java
public final class MissionPanelWidgetAppState extends BaseAppState {

  private final ApplicationContext context;
  private MissionPanelWidget panel;

  public MissionPanelWidgetAppState(ApplicationContext context) {
    this.context = context;
    context.missionContext().addMission(new LEOMission("LEO", 400_000));
  }

  @Override protected void initialize(Application app) {}

  @Override
  public void update(float tpf) {
    // Drain navigation events (other AppStates may also drain different event types).
    // NOTE: we share the navigation queue with MissionWizardAppState. Both AppStates
    // poll it and ignore events that are not for them. This is the existing pattern.
    EventBus.UiNavigationEvent ev;
    while ((ev = context.eventBus().pollUiNavigation()) != null) {
      switch (ev) {
        case EventBus.UiNavigationEvent.OpenMissionManagement open -> openPanel();
        default -> { /* re-queue or ignore; see migration note */ }
      }
    }
    if (panel != null) {
      panel.update(tpf, getApplication().getCamera());
    }
  }

  @Override
  protected void cleanup(Application app) {
    closePanel();
  }

  @Override protected void onEnable() {}
  @Override protected void onDisable() {}

  private void openPanel() {
    if (panel != null) return;
    panel = new MissionPanelWidget(context);
    panel.setOnClose(this::closePanel);
    panel.setOnNewMission(() -> {
      closePanel();
      context.eventBus().publishUiNavigation(new EventBus.UiNavigationEvent.OpenMissionWizard());
    });
    panel.attachTo(context.guiGraph().getModalNode());
  }

  private void closePanel() {
    if (panel != null) {
      panel.close();
      panel = null;
    }
  }
}
```

> ⚠️ **Coexistence sur la file `uiNavigationQueue`** : aujourd'hui, seuls
> `MissionWizardAppState` consomme `OpenMissionWizard` et `CreateMission`,
> et il les poll en boucle. Si `MissionPanelWidgetAppState` poll
> indépendamment, un event consommé par l'un n'est plus visible par l'autre.
> **Vérification à faire en phase d'implémentation** : ajouter un dispatcher
> (chaque AppState n'enlève que les events qui le concernent) ou bien
> bifurquer chaque type d'event sur sa propre file (comme on le fait pour
> `MissionTelemetryFocusRequest`). Recommandation : créer trois files
> distinctes dans `EventBus` (`openWizardQueue`, `createMissionQueue`,
> `openManagementQueue`) plutôt que de partager un sealed type sur une seule
> file. Refactor neutre côté API publique des publishers.

### 7.6 `MissionPanelWidget` (modal)

Fichier : [MissionPanelWidget.java](../../src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionPanelWidget.java)

- Retirer le champ `selectedMissionName` du couplage avec `MissionContext` (lignes 65, 128, 169-170, 183) : la sélection reste **locale** au widget. La lecture de `missionContext.getSelectedMissionName()` peut rester pour récupérer la dernière sélection à l'ouverture, mais l'écriture (`setSelectedMissionName`) doit disparaître pour ne pas piloter la télémètrie.
- L'event `onToggleVisible` de la `RowListener` est **conservé** côté code, mais l'icône œil est retirée de la modal (cf. §7.7), donc la branche devient morte. Choix : la supprimer aussi du `RowListener` pour cohérence (modification du contrat).

### 7.7 `MissionRow` (modal) — retirer l'icône œil + ajouter le swatch et le badge

Fichier : [MissionRow.java](../../src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionRow.java)

1. Dans `populateActions(...)`, supprimer le bloc `visualizeIconButton` (lignes 126-131). Adapter `MissionListView.ColumnLayout.actions()` si la largeur dépend du nombre d'icônes.
2. Dans le constructeur de `MissionRow`, **avant** le `Label nameLabel`, insérer un swatch couleur de 12×12 px lu depuis `entry.getColor()`. Et un badge "telemetry active" (12×12 px) à droite du swatch si `entry.mission().getName().equals(missionContext.getTelemetryFocusMissionName())`. Le badge utilise l'asset `badge-telemetry-active.png` ; sinon, le slot reste un spacer transparent de même largeur pour aligner verticalement les rangées.

Pour accéder à `MissionContext` depuis `MissionRow`, soit :
- passer le booléen `telemetered` dans le constructeur (depuis `MissionListView.refresh`),
- soit injecter le `MissionContext` (déjà le pattern de `MissionRow`).

Le plus simple : passer un booléen `telemetered` dans le constructeur, et faire calculer le mapping `name → telemetered` dans `MissionPanelWidget.refresh()` (qui a déjà `missionContext`).

### 7.8 `MissionListView` (modal) — propager `telemetered`

Ajouter un paramètre à `RowListener` est complexe ; plus simple : injecter
le nom de la mission télémétrée dans `refresh()` :

```java
public void refresh(List<MissionEntry> entries, String selectedName, String telemeteredName) {
  // ...
  for (MissionEntry entry : entries) {
    boolean telemetered = entry.mission().getName().equals(telemeteredName);
    MissionRow row = new MissionRow(entry, cols, selected, telemetered, rowListener);
    // ...
  }
}
```

`MissionPanelWidget.refresh()` passe désormais
`missionContext.getTelemetryFocusMissionName()` comme troisième argument et
inclut cette valeur dans `buildSnapshot()` pour déclencher un refresh
quand elle change.

---

## 8. Assets à ajouter

Dossier : `src/main/resources/interface/wizard/` (excluded from git ; à fournir séparément lors de l'intégration).

| Fichier | Dimensions | Description | État |
|---|---|---|---|
| `icon-action-telemetry.png` | 32×32 | Icône télémètrie pleine — état actif | TODO |
| `icon-action-telemetry-hover.png` | 32×32 | Hover | TODO |
| `icon-action-telemetry-disabled.png` | 32×32 | Icône télémètrie atténuée — état inactif | TODO |
| `icon-action-manage.png` | 32×32 | Engrenage — bouton "Manage" du header HUD | TODO |
| `icon-action-manage-hover.png` | 32×32 | Hover | TODO |
| `icon-action-manage-disabled.png` | 32×32 | Disabled (probablement inutilisé en pratique) | TODO |
| `badge-telemetry-active.png` | 12×12 | Badge compact pour la ligne télémétrée dans la modal | TODO |

> Note : les 32×32 sont chargés via `UiKit.wizardFlat(name)` dans des
> conteneurs de 20×20 (cf. `RowActionIcons.ICON_SIZE`) ; le scaling
> automatique de JME3 gère la mise à l'échelle.

Tant que les icônes ne sont pas fournies, `UiKit.loadWizardTexture` log un
warning et `wizardFlat` produit un fond `DarkGray`. Le code compile et
tourne ; les actions restent cliquables, juste en gris.

---

## 9. Wiring `OrbitLabApplication`

Fichier : [OrbitLabApplication.java](../../src/main/java/com/smousseur/orbitlab/OrbitLabApplication.java)

Insérer le nouvel AppState **avant** `MissionPanelWidgetAppState` (ordre
d'attachement = ordre d'init ; le panel d'affichage doit exister avant la
modal pour cohérence visuelle, mais l'ordre fonctionnel est sans impact car
ils communiquent via les events) :

```java
stateManager.attach(new TelemetryWidgetAppState(applicationContext));
stateManager.attach(new MissionDisplayPanelAppState(applicationContext));  // NEW
stateManager.attach(new MissionPanelWidgetAppState(applicationContext));
```

---

## 10. Plan d'implémentation séquencé

Chaque étape laisse le projet compilable (`./gradlew classes`).

### Étape 1 — Modèle de données

1. Ajouter `MissionColorPalette` (fichier nouveau).
2. Ajouter le champ `color` et ses accesseurs dans `MissionEntry`.
3. Modifier `MissionContext.addMission(...)` pour assigner la couleur.
4. Ajouter `telemetryFocusMissionName` + accesseurs dans `MissionContext`.

**Checkpoint** : `./gradlew classes`.

### Étape 2 — EventBus

1. Ajouter le record `OpenMissionManagement` dans le sealed interface.
2. Ajouter le record `MissionTelemetryFocusRequest` et sa file.
3. **(Refactor préventif recommandé)** scinder `uiNavigationQueue` en
   files séparées par type, pour éviter les conflits entre
   `MissionPanelWidgetAppState` et `MissionWizardAppState` (cf. §7.5).
   Si on saute ce refactor, vérifier en exécution que les events de wizard
   sont toujours traités correctement.

**Checkpoint** : `./gradlew classes`.

### Étape 3 — Orchestrateur

1. Supprimer `TRAJECTORY_PALETTE` et `colorIndex`.
2. `createRenderer` lit `entry.getColor()`.
3. Supprimer le bloc singleton dans `pollMissionActions / TOGGLE_VISIBLE`.
4. Ajouter R5 (clear telemetry quand la mission télémétrée est cachée) dans la même branche.

**Checkpoint** : `./gradlew classes`. À ce stade, le rendu multi-mission
est fonctionnel mais aucun nouveau widget ne le pilote (l'œil reste dans
la modal pour le moment).

### Étape 4 — Telemetry source

1. `TelemetryWidgetAppState` lit `getTelemetryFocusMission()` au lieu de `getSelectedMission()`.

**Checkpoint** : `./gradlew classes`. À ce stade, plus aucun chemin ne
peuple `telemetryFocusMissionName` → la télémètrie est masquée en
permanence. Normal jusqu'à l'étape 6.

### Étape 5 — Widget HUD + AppState

1. Créer le package `ui/mission/display/`.
2. Créer `DisplayRowIcons`, `DisplayRow`, `DisplayPanelHeader`,
   `DisplayPanelFooter`, `DisplayPanelEmptyState`,
   `MissionDisplayPanelWidget`.
3. Créer `MissionDisplayPanelAppState` avec snapshot diff, drain des
   events télémètrie, transitions de statut.

**Checkpoint** : `./gradlew classes`. Le HUD n'est pas encore attaché.

### Étape 6 — Wiring + retrait du couplage trigger/modal

1. Retirer le `MissionPanelTrigger` de `MissionPanelWidgetAppState.initialize`.
2. `MissionPanelWidgetAppState` consomme uniquement `OpenMissionManagement` ;
   son rôle de "opener on demand" est complet.
3. Attacher `MissionDisplayPanelAppState` dans `OrbitLabApplication`
   (cf. §9).

**Checkpoint** : `./gradlew classes` puis exécution manuelle. Le HUD
apparaît, la première mission `READY` active la télémètrie, etc.

### Étape 7 — Modal — nettoyage cosmétique

1. Retirer l'icône œil dans `MissionRow.populateActions`.
2. Ajouter le swatch couleur et le badge "telemetry active" dans `MissionRow`.
3. Adapter `MissionListView.refresh` pour propager `telemeteredName`.
4. Adapter `MissionPanelWidget.refresh` et son `buildSnapshot` (clé inclut le `telemeteredName`).
5. Supprimer les écritures de `selectedMissionName` côté `MissionContext` dans `MissionPanelWidget` (laisser uniquement la lecture initiale).

**Checkpoint** : `./gradlew classes`.

---

## 11. Critères de validation

> Conformément à la consigne, **aucun test automatisé n'est exécuté**. Seule
> la compilation est validée à chaque étape.

### 11.1 Compilation

À chaque checkpoint des étapes 1 à 7 :

```bash
./gradlew classes
```

Aucune erreur de compilation attendue. Les warnings de Lemur (assets
manquants) sont acceptables jusqu'à l'arrivée des icônes finales.

### 11.2 Vérification manuelle (post-intégration des assets)

Reproduction des scénarios S1 à S11 de [01-mission-display-panel.md §9](01-mission-display-panel.md#9-scénarios-utilisateur). Liste de contrôle minimale :

- [ ] Au démarrage : HUD visible, état vide, modal fermée.
- [ ] Création d'une mission via "+ Create mission" (HUD) → wizard, puis statut `DRAFT` (modal seulement, pas dans le HUD).
- [ ] Lancement de l'optim → `COMPUTING` → `READY` : auto-visible + auto-télémétrée (R1).
- [ ] Deux missions `READY`, deux couleurs distinctes, deux trajectoires affichées simultanément (validation du multi-display).
- [ ] Toggle œil sur la mission télémétrée → trajectoire masquée + widget télémètrie masqué + icône télémètrie repasse "inactive" (R5).
- [ ] Bouton "Hide all" → toutes cachées + télémètrie clear (R8).
- [ ] Suppression de la mission télémétrée → télémètrie clear, sa couleur redevient libre pour la prochaine création (R10 + §3 round-robin).
- [ ] Recompute d'une mission télémétrée → télémètrie clear pendant `COMPUTING`, ré-armée à la fin (R9 + R1).
- [ ] Clic sur le trigger → HUD s'ouvre/se ferme ; les états de visibilité/télémètrie sont préservés.

### 11.3 Régression à surveiller

- L'unique `LEOMission("LEO", 400_000)` du bootstrap doit toujours apparaître dans la modal avec son swatch couleur (statut `DRAFT`).
- Le wizard doit toujours créer des missions assignées d'une couleur.
- Le widget télémètrie doit afficher exactement les mêmes valeurs qu'avant (les champs `ephemeris` et `clock` ne changent pas).
- `MissionTrajectoryRenderer` doit recevoir la même couleur tout au long de la vie de la mission (pas de re-attribution après création).

---

## 12. Hors-scope (rappel)

- Persistance inter-sessions de `telemetryFocusMissionName` et de la
  couleur par mission.
- Migration automatique de la télémètrie en cas de désactivation
  (`R5`, `R9`, `R10` → `null`, sans heuristique de remplacement).
- Tabs télémètrie multi-missions.
- Drag & drop / réordonnancement des lignes.
- Tests automatisés (cf. consigne explicite ; ajout possible dans une
  livraison séparée).
