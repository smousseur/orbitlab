# Mission Wizard — Spec Technique d'Implémentation (Phase 1)

> **Réf. visuelle :** `specs/wizard/mission-wizard-visual-spec.md`
> **Réf. palette :** `specs/wizard/palette/palette.md`
> **Scope :** Visual-only — pas de validation, pas de binding modèle, pas de navigation active (sauf Cancel + debug F8)

---

## 1. Palette de couleurs du Wizard

La palette du wizard est **dédiée et distincte** de la palette globale `ICE_*` de `AppStyles.java`.
Les tokens `WIZARD_*` sont déclarés dans `MissionWizardStyles.java`.

### 1.1 Référence complète des tokens

#### Fonds

| Token | Hex | RGBA (0-1) | Usage |
|---|---|---|---|
| `WIZARD_BG_DEEP` | `#0B1420` | `(0.043, 0.078, 0.125, 0.95)` | Fond principal du panneau wizard |
| `WIZARD_BG_CARD` | `#0F1E2E` | `(0.059, 0.118, 0.180, 0.85)` | Fond des cards / inputs / text fields |
| `WIZARD_BG_CARD_HOVER` | `#142838` | `(0.078, 0.157, 0.220, 0.90)` | Survol des cards et éléments interactifs |
| `WIZARD_SELECTED` | `#0D3A5C` | `(0.051, 0.227, 0.361, 0.90)` | Card sélectionnée (LEO, Falcon Heavy) |

#### Accents

| Token | Hex | RGBA | Usage |
|---|---|---|---|
| `WIZARD_ACCENT` | `#00D4FF` | `(0.00, 0.831, 1.00, 1.0)` | Cyan vif — titres actifs, bouton Next, slider, stepper actif |
| `WIZARD_ACCENT_DIM` | `#1A8FAF` | `(0.102, 0.561, 0.686, 0.80)` | Bordures sélection, accent atténué |

#### Bordures

| Token | Hex | RGBA | Usage |
|---|---|---|---|
| `WIZARD_BORDER` | `#1C3448` | `(0.110, 0.204, 0.282, 0.70)` | Bordures cards, séparateurs, grille mini-map |
| `WIZARD_BORDER_GLOW` | `#00A8CC` | `(0.00, 0.659, 0.80, 0.60)` | Bordure lumineuse des éléments sélectionnés |

#### Texte

| Token | Hex | RGBA | Usage |
|---|---|---|---|
| `WIZARD_TEXT_PRIMARY` | `#E8F4F8` | `(0.91, 0.957, 0.973, 1.0)` | Texte principal, valeurs, titres |
| `WIZARD_TEXT_SECONDARY` | `#5A8BA0` | `(0.353, 0.545, 0.627, 1.0)` | Labels, helpers, commentaires `//` |
| `WIZARD_TEXT_ACCENT` | `#00D4FF` | alias de `WIZARD_ACCENT` | Titres actifs, liens |
| `WIZARD_TEXT_DISABLED` | `#2E4A5C` | `(0.180, 0.290, 0.361, 0.60)` | Texte des options désactivées (SOON) |

#### Sémantiques

| Token | Hex | RGBA | Usage |
|---|---|---|---|
| `WIZARD_SUCCESS` | `#00E878` | `(0.00, 0.910, 0.471, 1.0)` | Badge AVAILABLE, bouton Create, stepper done |
| `WIZARD_WARNING` | `#FFB830` | `(1.00, 0.722, 0.188, 1.0)` | Badge IN PROGRESS, banner Tsiolkovsky |
| `WIZARD_DANGER` | `#FF4D6A` | `(1.00, 0.302, 0.416, 1.0)` | Bouton Cancel, bouton remove payload |
| `WIZARD_INFO` | `#00D4FF` | alias de `WIZARD_ACCENT` | Banner info (Step 2) |

#### Backdrop

| Token | Hex | RGBA | Usage |
|---|---|---|---|
| `WIZARD_BACKDROP` | `#000000` | `(0.0, 0.0, 0.0, 0.60)` | Overlay plein écran derrière le modal |

### 1.2 Mapping tokens ancien vers nouveau

| Ancien (visual-spec) | Nouveau (tech-spec) | Usage |
|---|---|---|
| `ICE_PANEL_BG` | `WIZARD_BG_DEEP` | Fond fenêtre, popup, mini-map |
| `ICE_PANEL_BG_LIGHT` | `WIZARD_BG_CARD` | Fond cards, inputs, bouton Previous, ghost button |
| *(n/a)* | `WIZARD_BG_CARD_HOVER` | Hover sur cards et éléments interactifs |
| `ICE_ROW_SELECTED` | `WIZARD_SELECTED` | Card sélectionnée |
| `ICE_ACCENT` | `WIZARD_ACCENT` | Bouton Next, slider, stepper actif, dot mini-map |
| *(n/a)* | `WIZARD_ACCENT_DIM` | Bordures atténuées |
| `ICE_BORDER` | `WIZARD_BORDER` | Bordures, séparateurs, grille, stepper pending |
| *(n/a)* | `WIZARD_BORDER_GLOW` | Glow sélection (futur) |
| `ICE_TEXT_PRIMARY` | `WIZARD_TEXT_PRIMARY` | Texte principal |
| `ICE_TEXT_SECONDARY` | `WIZARD_TEXT_SECONDARY` | Labels, helpers, commentaires |
| *(n/a)* | `WIZARD_TEXT_DISABLED` | Texte SOON / désactivé |
| `ICE_DANGER` | `WIZARD_DANGER` | Cancel, remove |
| `ICE_SUCCESS` | `WIZARD_SUCCESS` | Create mission, Available, stepper done |
| `ICE_WARNING` | `WIZARD_WARNING` | In Progress, Tsiolkovsky |
| Inline `(0,0,0,0.55)` | `WIZARD_BACKDROP` | Backdrop modal (60% alpha) |

---

## 2. Typographie

### 2.1 Fonts à générer

| Font source (TTF) | Licence | Tailles (.fnt) | Fichier asset |
|---|---|---|---|
| **Rajdhani SemiBold** | SIL OFL 1.1 | 10, 12, 14, 16, 18, 20, 28 | `fonts/rajdhani-semibold-{size}.fnt` |
| **Share Tech Mono Regular** | SIL OFL 1.1 | 10, 12, 14 | `fonts/share-tech-mono-{size}.fnt` |

### 2.2 Procédure de conversion

Utiliser **Hiero** (JME SDK) ou **BMFont** (AngelCode) :

1. Charger le `.ttf`, sélectionner la taille cible
2. Character set : ASCII étendu + `°Ωω×▾≈±·Δ✓←→`
3. Padding 1px, spacing 1px
4. Export : AngelCode BMFont (`.fnt` + `.png`)
5. Destination : `src/main/resources/fonts/`

### 2.3 Matrice typographique

| Rôle UI | Police | Taille | Couleur token |
|---|---|---|---|
| Titre marque `ORBITLAB` | Rajdhani | 18px | `WIZARD_TEXT_PRIMARY` |
| Sous-titre `MISSION WIZARD v2.1` | Rajdhani | 18px | `WIZARD_TEXT_SECONDARY` |
| En-tête étape `PARAMETERS — LEO` | Rajdhani | 20px | `WIZARD_TEXT_PRIMARY` |
| Commentaire `// target orbit config` | Share Tech Mono | 12px | `WIZARD_TEXT_SECONDARY` |
| Label champ `TARGET ALTITUDE` | Rajdhani | 12px | `WIZARD_TEXT_SECONDARY` |
| Valeur input / body | Share Tech Mono | 14px | `WIZARD_TEXT_PRIMARY` |
| Grosse valeur slider `550` | Rajdhani | 28px | `WIZARD_ACCENT` |
| Unité `km` | Rajdhani | 14px | `WIZARD_TEXT_SECONDARY` |
| Numéro stepper | Rajdhani | 14px | par état |
| Label stepper | Rajdhani | 12px | par état |
| Badge `AVAILABLE` | Rajdhani | 10px | `WIZARD_TEXT_PRIMARY` |
| Helper text | Share Tech Mono | 10px | `WIZARD_TEXT_SECONDARY` |
| Boutons nav | Rajdhani | 14px | `WIZARD_TEXT_PRIMARY` |

### 2.4 Structure assets
```

src/main/resources/
├── fonts/
│   ├── rajdhani-semibold-10.fnt   + .png
│   ├── rajdhani-semibold-12.fnt   + .png
│   ├── rajdhani-semibold-14.fnt   + .png
│   ├── rajdhani-semibold-16.fnt   + .png
│   ├── rajdhani-semibold-18.fnt   + .png
│   ├── rajdhani-semibold-20.fnt   + .png
│   ├── rajdhani-semibold-28.fnt   + .png
│   ├── share-tech-mono-10.fnt    + .png
│   ├── share-tech-mono-12.fnt    + .png
│   └── share-tech-mono-14.fnt    + .png
└── icons/
└── wizard/
├── brand-globe.png        (24x24, placeholder)
├── info.png               (16x16)
├── mission-leo.png        (48x48)
├── ...                    (voir visual-spec icon slots)
└── check.png             (16x16)
```
> **Phase 1 :** Les PNGs sont des placeholders. L'`IconComponent` Lemur rend un carré vide si le fichier est absent.

---

## 3. Architecture des fichiers

### 3.1 Nouveaux fichiers
```

src/main/java/com/smousseur/orbitlab/
├── ui/mission/wizard/
│   ├── MissionWizardWidget.java
│   ├── MissionWizardStyles.java
│   ├── MissionWizardStep.java
│   ├── ModalBackdrop.java
│   ├── WizardStepper.java
│   ├── WizardFooter.java
│   ├── component/
│   │   ├── SelectableCard.java
│   │   ├── SegmentedControl.java
│   │   ├── PopupList.java
│   │   ├── Badge.java
│   │   ├── InfoBanner.java
│   │   ├── LabeledField.java
│   │   └── ProgressBar.java
│   └── step/
│       ├── StepMissionType.java
│       ├── StepParameters.java
│       ├── StepLaunchSite.java
│       └── StepLauncher.java
└── states/mission/
└── MissionWizardAppState.java
```
### 3.2 Fichiers modifiés

| Fichier | Modification |
|---|---|
| `engine/scene/graph/GuiGraph.java` | Ajout `modalNode` + `getModalNode()` |
| `engine/events/EventBus.java` | Ajout `UiNavigation` enum + queue |
| `ui/AppStyles.java` | Appel `MissionWizardStyles.init(am)` |
| `OrbitLabApplication.java` | Attach `MissionWizardAppState` + wire `uiWantsMouse` |
| `ui/mission/MissionPanelWidget.java` | Hook `onCreate()` via EventBus |

---

## 4. Modifications des fichiers existants

### 4.1 GuiGraph.java

```java
// ... existing code ...
  private final Node missionPanelNode = new Node("missionPanelNode");
  private final Node modalNode = new Node("modalNode");

  public GuiGraph() {
    guiRoot.attachChild(guiFrame);
    guiFrame.attachChild(timelineNode);
    guiFrame.attachChild(planetBillboardsNode);
    guiFrame.attachChild(telemetryNode);
    guiFrame.attachChild(missionPanelNode);
    guiFrame.attachChild(modalNode);  // topmost
  }
// ... existing code ...

  /**
   * Returns the node for modal overlays (wizard, dialogs).
   * Renders on top of all other GUI nodes.
   */
  public Node getModalNode() {
    return modalNode;
  }
}
```

### 4.2 EventBus.java

```java
// ... existing code ...
  // -------------------------------------------------------------------------
  // UI navigation events
  // -------------------------------------------------------------------------

  public enum UiNavigation {
    OPEN_MISSION_WIZARD
  }

  private final ConcurrentLinkedQueue<UiNavigation> uiNavigationQueue =
      new ConcurrentLinkedQueue<>();

  public void publishUiNavigation(UiNavigation nav) {
    uiNavigationQueue.add(Objects.requireNonNull(nav));
  }

  public UiNavigation pollUiNavigation() {
    return uiNavigationQueue.poll();
  }
// ... existing code ...
```

### 4.3 AppStyles.java

```java
// ... existing code ...
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
// ... existing code ...
  public static void init(AssetManager assetManager) {
    TimelineStyles.init(assetManager);
    TelemetryStyles.init(assetManager);
    MissionPanelStyles.init(assetManager);
    MissionWizardStyles.init(assetManager);
  }
// ... existing code ...
```

### 4.4 OrbitLabApplication.java

```java
// ... existing code ...
import com.smousseur.orbitlab.states.mission.MissionWizardAppState;
// ... existing code ...
    stateManager.attach(new MissionPanelWidgetAppState(applicationContext));
    stateManager.attach(new LightningAppState(applicationContext));

    MissionWizardAppState wizardState = new MissionWizardAppState(applicationContext);
    wizardState.setEnabled(false);
    stateManager.attach(wizardState);

    flyCam.setEnabled(false);

    OrbitCameraAppState orbitCam =
        new OrbitCameraAppState(
            applicationContext,
            () -> Vector3f.ZERO,
            () -> wizardState.isEnabled() && wizardState.isWizardVisible());
    stateManager.attach(orbitCam);
// ... existing code ...
```

### 4.5 MissionPanelWidget.java

```java
// ... existing code ...
  private void onCreate() {
    eventBus.publishUiNavigation(EventBus.UiNavigation.OPEN_MISSION_WIZARD);
  }
// ... existing code ...
```

---

## 5. Nouveaux fichiers

### 5.1 MissionWizardStep.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

public enum MissionWizardStep {
    MISSION(0, "MISSION"),
    PARAMETERS(1, "PARAMETERS"),
    SITE(2, "SITE"),
    LAUNCHER(3, "LAUNCHER");

    private final int index;
    private final String label;

    MissionWizardStep(int index, String label) {
        this.index = index;
        this.label = label;
    }

    public int index() { return index; }
    public String label() { return label; }
    public static final int COUNT = values().length;

    public MissionWizardStep next() {
        int i = ordinal() + 1;
        MissionWizardStep[] v = values();
        return i < v.length ? v[i] : null;
    }

    public MissionWizardStep previous() {
        int i = ordinal() - 1;
        return i >= 0 ? values()[i] : null;
    }
}
```

### 5.2 MissionWizardStyles.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MissionWizardStyles {
    private static final Logger logger = LogManager.getLogger(MissionWizardStyles.class);

    public static final String STYLE = "mission-wizard";

    // =================================================================
    //  WIZARD COLOUR PALETTE
    // =================================================================

    // --- Backgrounds ---
    public static final ColorRGBA WIZARD_BG_DEEP =
        new ColorRGBA(0.043f, 0.078f, 0.125f, 0.95f);
    public static final ColorRGBA WIZARD_BG_CARD =
        new ColorRGBA(0.059f, 0.118f, 0.180f, 0.85f);
    public static final ColorRGBA WIZARD_BG_CARD_HOVER =
        new ColorRGBA(0.078f, 0.157f, 0.220f, 0.90f);
    public static final ColorRGBA WIZARD_SELECTED =
        new ColorRGBA(0.051f, 0.227f, 0.361f, 0.90f);

    // --- Accents ---
    public static final ColorRGBA WIZARD_ACCENT =
        new ColorRGBA(0.00f, 0.831f, 1.00f, 1.0f);
    public static final ColorRGBA WIZARD_ACCENT_DIM =
        new ColorRGBA(0.102f, 0.561f, 0.686f, 0.80f);

    // --- Borders ---
    public static final ColorRGBA WIZARD_BORDER =
        new ColorRGBA(0.110f, 0.204f, 0.282f, 0.70f);
    public static final ColorRGBA WIZARD_BORDER_GLOW =
        new ColorRGBA(0.00f, 0.659f, 0.80f, 0.60f);

    // --- Text ---
    public static final ColorRGBA WIZARD_TEXT_PRIMARY =
        new ColorRGBA(0.91f, 0.957f, 0.973f, 1.0f);
    public static final ColorRGBA WIZARD_TEXT_SECONDARY =
        new ColorRGBA(0.353f, 0.545f, 0.627f, 1.0f);
    public static final ColorRGBA WIZARD_TEXT_ACCENT = WIZARD_ACCENT;
    public static final ColorRGBA WIZARD_TEXT_DISABLED =
        new ColorRGBA(0.180f, 0.290f, 0.361f, 0.60f);

    // --- Semantic ---
    public static final ColorRGBA WIZARD_SUCCESS =
        new ColorRGBA(0.00f, 0.910f, 0.471f, 1.0f);
    public static final ColorRGBA WIZARD_WARNING =
        new ColorRGBA(1.00f, 0.722f, 0.188f, 1.0f);
    public static final ColorRGBA WIZARD_DANGER =
        new ColorRGBA(1.00f, 0.302f, 0.416f, 1.0f);
    public static final ColorRGBA WIZARD_INFO = WIZARD_ACCENT;

    // --- Backdrop ---
    public static final ColorRGBA WIZARD_BACKDROP =
        new ColorRGBA(0f, 0f, 0f, 0.60f);

    // =================================================================
    //  FONT HELPERS
    // =================================================================

    private static final String GRADIENT_TEXTURE =
        "com/simsilica/lemur/icons/bordered-gradient.png";
    private static final String FONT_RAJDHANI = "fonts/rajdhani-semibold-%d.fnt";
    private static final String FONT_MONO = "fonts/share-tech-mono-%d.fnt";

    private static Texture gradientTex;
    private static AssetManager assetManager;

    private MissionWizardStyles() {}

    public static BitmapFont rajdhani(int size) {
        return loadFontSafe(String.format(FONT_RAJDHANI, size));
    }

    public static BitmapFont mono(int size) {
        return loadFontSafe(String.format(FONT_MONO, size));
    }

    private static BitmapFont loadFontSafe(String path) {
        try {
            return assetManager.loadFont(path);
        } catch (AssetNotFoundException e) {
            logger.debug("Font not found: {}, using Lemur default", path);
            return GuiGlobals.getInstance()
                .loadFont("Interface/Fonts/Default.fnt");
        }
    }

    // =================================================================
    //  GRADIENT BACKGROUND FACTORY
    // =================================================================

    public static TbtQuadBackgroundComponent createGradient(ColorRGBA color) {
        TbtQuadBackgroundComponent bg = TbtQuadBackgroundComponent.create(
            gradientTex, 1, 1, 1, 126, 126, 1f, false);
        bg.setColor(color);
        return bg;
    }

    // =================================================================
    //  STYLE REGISTRATION
    // =================================================================

    public static void init(AssetManager am) {
        assetManager = am;
        gradientTex = am.loadTexture(GRADIENT_TEXTURE);

        Styles styles = GuiGlobals.getInstance().getStyles();
        styles.applyStyles(STYLE, "glass");

        Attributes c = styles.getSelector("container", STYLE);
        c.set("background", createGradient(WIZARD_BG_DEEP));
        c.set("insets", new Insets3f(0, 0, 0, 0));

        Attributes l = styles.getSelector("label", STYLE);
        l.set("color", WIZARD_TEXT_PRIMARY);
        l.set("fontSize", 14);

        Attributes b = styles.getSelector("button", STYLE);
        b.set("background", createGradient(WIZARD_BG_CARD));
        b.set("color", WIZARD_TEXT_PRIMARY);
        b.set("fontSize", 14);
        b.set("insets", new Insets3f(6, 10, 6, 10));

        Attributes tf = styles.getSelector("textField", STYLE);
        tf.set("background", createGradient(WIZARD_BG_CARD));
        tf.set("color", WIZARD_TEXT_PRIMARY);
        tf.set("fontSize", 14);
        tf.set("insets", new Insets3f(6, 8, 6, 8));

        Attributes s = styles.getSelector("slider", STYLE);
        s.set("background", createGradient(WIZARD_BORDER));
    }
}
```

### 4.2 EventBus.java

```java
// ... existing code ...
  // -------------------------------------------------------------------------
  // UI navigation events
  // -------------------------------------------------------------------------

  public enum UiNavigation {
    OPEN_MISSION_WIZARD
  }

  private final ConcurrentLinkedQueue<UiNavigation> uiNavigationQueue =
      new ConcurrentLinkedQueue<>();

  public void publishUiNavigation(UiNavigation nav) {
    uiNavigationQueue.add(Objects.requireNonNull(nav));
  }

  public UiNavigation pollUiNavigation() {
    return uiNavigationQueue.poll();
  }
// ... existing code ...
```


### 4.3 AppStyles.java

```java
// ... existing code ...
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
// ... existing code ...
  public static void init(AssetManager assetManager) {
    TimelineStyles.init(assetManager);
    TelemetryStyles.init(assetManager);
    MissionPanelStyles.init(assetManager);
    MissionWizardStyles.init(assetManager);
  }
// ... existing code ...
```


### 4.4 OrbitLabApplication.java

```java
// ... existing code ...
import com.smousseur.orbitlab.states.mission.MissionWizardAppState;
// ... existing code ...
    stateManager.attach(new MissionPanelWidgetAppState(applicationContext));
    stateManager.attach(new LightningAppState(applicationContext));

    MissionWizardAppState wizardState = new MissionWizardAppState(applicationContext);
    wizardState.setEnabled(false);
    stateManager.attach(wizardState);

    flyCam.setEnabled(false);

    OrbitCameraAppState orbitCam =
        new OrbitCameraAppState(
            applicationContext,
            () -> Vector3f.ZERO,
            () -> wizardState.isEnabled() && wizardState.isWizardVisible());
    stateManager.attach(orbitCam);
// ... existing code ...
```


### 4.5 MissionPanelWidget.java

```java
// ... existing code ...
  private void onCreate() {
    eventBus.publishUiNavigation(EventBus.UiNavigation.OPEN_MISSION_WIZARD);
  }
// ... existing code ...
```


---

## 5. Nouveaux fichiers

### 5.1 MissionWizardStep.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

public enum MissionWizardStep {
    MISSION(0, "MISSION"),
    PARAMETERS(1, "PARAMETERS"),
    SITE(2, "SITE"),
    LAUNCHER(3, "LAUNCHER");

    private final int index;
    private final String label;

    MissionWizardStep(int index, String label) {
        this.index = index;
        this.label = label;
    }

    public int index() { return index; }
    public String label() { return label; }
    public static final int COUNT = values().length;

    public MissionWizardStep next() {
        int i = ordinal() + 1;
        MissionWizardStep[] v = values();
        return i < v.length ? v[i] : null;
    }

    public MissionWizardStep previous() {
        int i = ordinal() - 1;
        return i >= 0 ? values()[i] : null;
    }
}
```


### 5.2 MissionWizardStyles.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MissionWizardStyles {
    private static final Logger logger = LogManager.getLogger(MissionWizardStyles.class);

    public static final String STYLE = "mission-wizard";

    // =================================================================
    //  WIZARD COLOUR PALETTE
    // =================================================================

    // --- Backgrounds ---
    public static final ColorRGBA WIZARD_BG_DEEP =
        new ColorRGBA(0.043f, 0.078f, 0.125f, 0.95f);
    public static final ColorRGBA WIZARD_BG_CARD =
        new ColorRGBA(0.059f, 0.118f, 0.180f, 0.85f);
    public static final ColorRGBA WIZARD_BG_CARD_HOVER =
        new ColorRGBA(0.078f, 0.157f, 0.220f, 0.90f);
    public static final ColorRGBA WIZARD_SELECTED =
        new ColorRGBA(0.051f, 0.227f, 0.361f, 0.90f);

    // --- Accents ---
    public static final ColorRGBA WIZARD_ACCENT =
        new ColorRGBA(0.00f, 0.831f, 1.00f, 1.0f);
    public static final ColorRGBA WIZARD_ACCENT_DIM =
        new ColorRGBA(0.102f, 0.561f, 0.686f, 0.80f);

    // --- Borders ---
    public static final ColorRGBA WIZARD_BORDER =
        new ColorRGBA(0.110f, 0.204f, 0.282f, 0.70f);
    public static final ColorRGBA WIZARD_BORDER_GLOW =
        new ColorRGBA(0.00f, 0.659f, 0.80f, 0.60f);

    // --- Text ---
    public static final ColorRGBA WIZARD_TEXT_PRIMARY =
        new ColorRGBA(0.91f, 0.957f, 0.973f, 1.0f);
    public static final ColorRGBA WIZARD_TEXT_SECONDARY =
        new ColorRGBA(0.353f, 0.545f, 0.627f, 1.0f);
    public static final ColorRGBA WIZARD_TEXT_ACCENT = WIZARD_ACCENT;
    public static final ColorRGBA WIZARD_TEXT_DISABLED =
        new ColorRGBA(0.180f, 0.290f, 0.361f, 0.60f);

    // --- Semantic ---
    public static final ColorRGBA WIZARD_SUCCESS =
        new ColorRGBA(0.00f, 0.910f, 0.471f, 1.0f);
    public static final ColorRGBA WIZARD_WARNING =
        new ColorRGBA(1.00f, 0.722f, 0.188f, 1.0f);
    public static final ColorRGBA WIZARD_DANGER =
        new ColorRGBA(1.00f, 0.302f, 0.416f, 1.0f);
    public static final ColorRGBA WIZARD_INFO = WIZARD_ACCENT;

    // --- Backdrop ---
    public static final ColorRGBA WIZARD_BACKDROP =
        new ColorRGBA(0f, 0f, 0f, 0.60f);

    // =================================================================
    //  FONT HELPERS
    // =================================================================

    private static final String GRADIENT_TEXTURE =
        "com/simsilica/lemur/icons/bordered-gradient.png";
    private static final String FONT_RAJDHANI = "fonts/rajdhani-semibold-%d.fnt";
    private static final String FONT_MONO = "fonts/share-tech-mono-%d.fnt";

    private static Texture gradientTex;
    private static AssetManager assetManager;

    private MissionWizardStyles() {}

    public static BitmapFont rajdhani(int size) {
        return loadFontSafe(String.format(FONT_RAJDHANI, size));
    }

    public static BitmapFont mono(int size) {
        return loadFontSafe(String.format(FONT_MONO, size));
    }

    private static BitmapFont loadFontSafe(String path) {
        try {
            return assetManager.loadFont(path);
        } catch (AssetNotFoundException e) {
            logger.debug("Font not found: {}, using Lemur default", path);
            return GuiGlobals.getInstance()
                .loadFont("Interface/Fonts/Default.fnt");
        }
    }

    // =================================================================
    //  GRADIENT BACKGROUND FACTORY
    // =================================================================

    public static TbtQuadBackgroundComponent createGradient(ColorRGBA color) {
        TbtQuadBackgroundComponent bg = TbtQuadBackgroundComponent.create(
            gradientTex, 1, 1, 1, 126, 126, 1f, false);
        bg.setColor(color);
        return bg;
    }

    // =================================================================
    //  STYLE REGISTRATION
    // =================================================================

    public static void init(AssetManager am) {
        assetManager = am;
        gradientTex = am.loadTexture(GRADIENT_TEXTURE);

        Styles styles = GuiGlobals.getInstance().getStyles();
        styles.applyStyles(STYLE, "glass");

        Attributes c = styles.getSelector("container", STYLE);
        c.set("background", createGradient(WIZARD_BG_DEEP));
        c.set("insets", new Insets3f(0, 0, 0, 0));

        Attributes l = styles.getSelector("label", STYLE);
        l.set("color", WIZARD_TEXT_PRIMARY);
        l.set("fontSize", 14);

        Attributes b = styles.getSelector("button", STYLE);
        b.set("background", createGradient(WIZARD_BG_CARD));
        b.set("color", WIZARD_TEXT_PRIMARY);
        b.set("fontSize", 14);
        b.set("insets", new Insets3f(6, 10, 6, 10));

        Attributes tf = styles.getSelector("textField", STYLE);
        tf.set("background", createGradient(WIZARD_BG_CARD));
        tf.set("color", WIZARD_TEXT_PRIMARY);
        tf.set("fontSize", 14);
        tf.set("insets", new Insets3f(6, 8, 6, 8));

        Attributes s = styles.getSelector("slider", STYLE);
        s.set("background", createGradient(WIZARD_BORDER));
    }
}
```


### 5.3 ModalBackdrop.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;

public class ModalBackdrop {

    private final Container backdrop;
    private int lastWidth;
    private int lastHeight;

    public ModalBackdrop() {
        backdrop = new Container();
        backdrop.setBackground(
            new QuadBackgroundComponent(MissionWizardStyles.WIZARD_BACKDROP));
        backdrop.setLocalTranslation(0, 0, 0);

        MouseEventControl.addListenersToSpatial(backdrop, new DefaultMouseListener() {
            @Override
            public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
                event.setConsumed();
            }
            @Override
            public void mouseButtonEvent(
                    MouseButtonEvent event, Spatial target, Spatial capture) {
                event.setConsumed();
            }
            @Override
            public void mouseEntered(
                    MouseMotionEvent event, Spatial target, Spatial capture) {
                event.setConsumed();
            }
            @Override
            public void mouseMoved(
                    MouseMotionEvent event, Spatial target, Spatial capture) {
                event.setConsumed();
            }
        });
    }

    public Container getNode() { return backdrop; }

    public void update(Camera cam) {
        int w = cam.getWidth();
        int h = cam.getHeight();
        if (w != lastWidth || h != lastHeight) {
            lastWidth = w;
            lastHeight = h;
            backdrop.setPreferredSize(new Vector3f(w, h, 0));
            backdrop.setLocalTranslation(0, h, 0);
        }
    }
}
```


### 5.4 WizardStepper.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;

public class WizardStepper {

    private static final float STEPPER_HEIGHT = 44f;
    private static final float CIRCLE_SIZE = 28f;
    private static final float DASH_WIDTH = 6f;
    private static final float DASH_HEIGHT = 1f;
    private static final float DASH_GAP = 4f;
    private static final int DASH_COUNT = 3;

    private final Container root;
    private final Container[] stepNodes = new Container[MissionWizardStep.COUNT];

    public WizardStepper() {
        root = new Container(
            new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
        root.setPreferredSize(new Vector3f(0, STEPPER_HEIGHT, 0));
        root.setBackground(null);

        for (int i = 0; i < MissionWizardStep.COUNT; i++) {
            if (i > 0) root.addChild(buildDashSeparator());
            MissionWizardStep step = MissionWizardStep.values()[i];
            Container node = buildStepNode(step);
            stepNodes[i] = node;
            root.addChild(node);
        }
    }

    public Container getNode() { return root; }

    public void setActiveStep(MissionWizardStep activeStep) {
        for (MissionWizardStep step : MissionWizardStep.values()) {
            Container node = stepNodes[step.index()];
            if (step.index() < activeStep.index()) {
                applyDoneState(node, step);
            } else if (step == activeStep) {
                applyActiveState(node, step);
            } else {
                applyPendingState(node, step);
            }
        }
    }

    private Container buildStepNode(MissionWizardStep step) {
        Container col = new Container(new BoxLayout(Axis.Y, FillMode.None));
        col.setBackground(null);

        Container circle = new Container();
        circle.setPreferredSize(new Vector3f(CIRCLE_SIZE, CIRCLE_SIZE, 0));
        Label number = circle.addChild(
            new Label(String.valueOf(step.index() + 1), MissionWizardStyles.STYLE));
        number.setFont(MissionWizardStyles.rajdhani(14));
        number.setTextHAlignment(HAlignment.Center);
        col.addChild(circle);

        Label label = col.addChild(
            new Label(step.label(), MissionWizardStyles.STYLE));
        label.setFont(MissionWizardStyles.rajdhani(12));
        label.setTextHAlignment(HAlignment.Center);

        return col;
    }

    private Container buildDashSeparator() {
        Container sep = new Container(new BoxLayout(Axis.X, FillMode.None));
        sep.setBackground(null);
        sep.setPreferredSize(new Vector3f(
            DASH_COUNT * DASH_WIDTH + (DASH_COUNT - 1) * DASH_GAP,
            DASH_HEIGHT, 0));
        for (int i = 0; i < DASH_COUNT; i++) {
            Container dash = new Container();
            dash.setPreferredSize(new Vector3f(DASH_WIDTH, DASH_HEIGHT, 0));
            dash.setBackground(
                new QuadBackgroundComponent(MissionWizardStyles.WIZARD_BORDER));
            sep.addChild(dash);
        }
        return sep;
    }

    private void applyDoneState(Container node, MissionWizardStep step) {
        Container circle = (Container) node.getChild(0);
        circle.setBackground(
            MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_SUCCESS));
        ((Label) circle.getChild(0)).setText("v");
        ((Label) circle.getChild(0)).setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
        ((Label) node.getChild(1)).setColor(MissionWizardStyles.WIZARD_SUCCESS);
    }

    private void applyActiveState(Container node, MissionWizardStep step) {
        Container circle = (Container) node.getChild(0);
        circle.setBackground(
            MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_ACCENT));
        ((Label) circle.getChild(0)).setText(String.valueOf(step.index() + 1));
        ((Label) circle.getChild(0)).setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
        ((Label) node.getChild(1)).setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
    }

    private void applyPendingState(Container node, MissionWizardStep step) {
        Container circle = (Container) node.getChild(0);
        circle.setBackground(
            MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BORDER));
        ((Label) circle.getChild(0)).setText(String.valueOf(step.index() + 1));
        ((Label) circle.getChild(0)).setColor(
            MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        ((Label) node.getChild(1)).setColor(
            MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    }
}
```


### 5.5 WizardFooter.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.component.ProgressBar;

public class WizardFooter {

    private static final float FOOTER_HEIGHT = 72f;
    private static final float BUTTON_HEIGHT = 36f;

    private final Container root;
    private final ProgressBar progressBar;
    private final Button cancelButton;
    private final Button previousButton;
    private final Button nextButton;

    private Runnable onCancel = () -> {};

    public WizardFooter() {
        root = new Container(
            new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
        root.setPreferredSize(new Vector3f(0, FOOTER_HEIGHT, 0));
        root.setBackground(null);

        // Left — progress
        Container leftCol = root.addChild(
            new Container(new BoxLayout(Axis.Y, FillMode.None)));
        leftCol.setBackground(null);
        Label progressLabel = leftCol.addChild(
            new Label("PROGRESSION", MissionWizardStyles.STYLE));
        progressLabel.setFont(MissionWizardStyles.rajdhani(10));
        progressLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        progressBar = new ProgressBar(200f, 4f);
        leftCol.addChild(progressBar.getNode());

        // Spacer
        Container spacer = root.addChild(new Container());
        spacer.setBackground(null);
        spacer.setPreferredSize(new Vector3f(200, 0, 0));

        // Right — buttons
        Container buttonRow = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        buttonRow.setBackground(null);

        cancelButton = buttonRow.addChild(
            new Button("x  Cancel", MissionWizardStyles.STYLE));
        cancelButton.setBackground(
            MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_DANGER));
        cancelButton.setFont(MissionWizardStyles.rajdhani(14));
        cancelButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));
        cancelButton.addClickCommands(src -> onCancel.run());

        previousButton = buttonRow.addChild(
            new Button("<  Previous", MissionWizardStyles.STYLE));
        previousButton.setBackground(
            MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));
        previousButton.setFont(MissionWizardStyles.rajdhani(14));
        previousButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));

        nextButton = buttonRow.addChild(
            new Button("Next  >", MissionWizardStyles.STYLE));
        nextButton.setBackground(
            MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_ACCENT));
        nextButton.setFont(MissionWizardStyles.rajdhani(14));
        nextButton.setPreferredSize(new Vector3f(0, BUTTON_HEIGHT, 0));
    }

    public Container getNode() { return root; }
    public void setOnCancel(Runnable action) { this.onCancel = action; }

    public void setStep(MissionWizardStep step) {
        progressBar.setProgress(
            (step.index() + 1) / (float) MissionWizardStep.COUNT);

        previousButton.setColor(step.index() == 0
            ? MissionWizardStyles.WIZARD_TEXT_SECONDARY
            : MissionWizardStyles.WIZARD_TEXT_PRIMARY);

        if (step == MissionWizardStep.LAUNCHER) {
            nextButton.setText("v  Create mission");
            nextButton.setBackground(MissionWizardStyles.createGradient(
                MissionWizardStyles.WIZARD_SUCCESS));
        } else {
            nextButton.setText("Next  >");
            nextButton.setBackground(MissionWizardStyles.createGradient(
                MissionWizardStyles.WIZARD_ACCENT));
        }
    }
}
```


### 5.6 MissionWizardWidget.java

```java
package com.smousseur.orbitlab.ui.mission.wizard;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.ui.mission.wizard.step.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.Map;

public class MissionWizardWidget implements AutoCloseable {
    private static final Logger logger =
        LogManager.getLogger(MissionWizardWidget.class);

    private static final float WINDOW_WIDTH = 880f;
    private static final float WINDOW_HEIGHT = 640f;
    private static final float MIN_VIEWPORT_MARGIN = 32f;
    private static final float OUTER_PADDING = 24f;
    private static final float HEADER_HEIGHT = 88f;

    private final ModalBackdrop backdrop;
    private final Container root;
    private final WizardStepper stepper;
    private final WizardFooter footer;
    private final Container content;

    private final Map<MissionWizardStep, Container> stepPanels =
        new EnumMap<>(MissionWizardStep.class);
    private MissionWizardStep currentStep = MissionWizardStep.MISSION;
    private boolean visible = false;

    public MissionWizardWidget(ApplicationContext context) {
        backdrop = new ModalBackdrop();

        root = new Container(
            new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
        root.setPreferredSize(new Vector3f(WINDOW_WIDTH, WINDOW_HEIGHT, 0));
        root.setBackground(
            MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_DEEP));
        root.setInsetsComponent(new InsetsComponent(new Insets3f(
            OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING)));

        // Header
        Container header = root.addChild(
            new Container(new BoxLayout(Axis.Y, FillMode.None)));
        header.setBackground(null);
        header.setPreferredSize(new Vector3f(0, HEADER_HEIGHT, 0));

        Container brandRow = header.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        brandRow.setBackground(null);

        Label brandName = brandRow.addChild(
            new Label("ORBITLAB", MissionWizardStyles.STYLE));
        brandName.setFont(MissionWizardStyles.rajdhani(18));
        brandName.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

        Label brandSep = brandRow.addChild(
            new Label("  /  ", MissionWizardStyles.STYLE));
        brandSep.setFont(MissionWizardStyles.rajdhani(18));
        brandSep.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        Label brandVersion = brandRow.addChild(
            new Label("MISSION WIZARD v2.1", MissionWizardStyles.STYLE));
        brandVersion.setFont(MissionWizardStyles.rajdhani(18));
        brandVersion.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        stepper = new WizardStepper();
        header.addChild(stepper.getNode());

        content = root.addChild(
            new Container(new BoxLayout(Axis.Y, FillMode.None)));
        content.setBackground(null);

        footer = new WizardFooter();
        root.addChild(footer.getNode());

        stepPanels.put(MissionWizardStep.MISSION,
            new StepMissionType().getNode());
        stepPanels.put(MissionWizardStep.PARAMETERS,
            new StepParameters().getNode());
        stepPanels.put(MissionWizardStep.SITE,
            new StepLaunchSite().getNode());
        stepPanels.put(MissionWizardStep.LAUNCHER,
            new StepLauncher().getNode());

        showStep(currentStep);
    }

    public void attachTo(Node modalNode) {
        modalNode.attachChild(backdrop.getNode());
        modalNode.attachChild(root);
        visible = true;
    }

    @Override
    public void close() {
        backdrop.getNode().removeFromParent();
        root.removeFromParent();
        visible = false;
    }

    public boolean isVisible() { return visible; }

    public void update(float tpf, Camera cam) {
        if (!visible) return;
        backdrop.update(cam);
        centerOnScreen(cam.getWidth(), cam.getHeight());
    }

    void showStep(MissionWizardStep step) {
        currentStep = step;
        content.clearChildren();
        Container panel = stepPanels.get(step);
        if (panel != null) content.addChild(panel);
        stepper.setActiveStep(step);
        footer.setStep(step);
        logger.debug("Wizard: showing step {}", step);
    }

    void cycleStep() {
        MissionWizardStep next = currentStep.next();
        showStep(next != null ? next : MissionWizardStep.MISSION);
    }

    public void setOnCancel(Runnable action) {
        footer.setOnCancel(action);
    }

    private void centerOnScreen(int screenWidth, int screenHeight) {
        if (screenWidth < WINDOW_WIDTH + 2 * MIN_VIEWPORT_MARGIN
                || screenHeight < WINDOW_HEIGHT + 2 * MIN_VIEWPORT_MARGIN) {
            logger.warn("Viewport {}x{} smaller than wizard minimum",
                screenWidth, screenHeight);
        }
        float x = Math.round((screenWidth - WINDOW_WIDTH) / 2f);
        float y = Math.round((screenHeight + WINDOW_HEIGHT) / 2f);
        root.setLocalTranslation(x, y, 1f);
    }
}
```


### 5.7 MissionWizardAppState.java

```java
package com.smousseur.orbitlab.states.mission;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.engine.events.EventBus;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardWidget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MissionWizardAppState
        extends BaseAppState implements ActionListener {
    private static final Logger logger =
        LogManager.getLogger(MissionWizardAppState.class);
    private static final String ACTION_DEBUG_CYCLE = "wizard.debug.cycle";

    private final ApplicationContext context;
    private MissionWizardWidget widget;
    private InputManager inputManager;

    public MissionWizardAppState(ApplicationContext context) {
        this.context = context;
    }

    public boolean isWizardVisible() {
        return widget != null && widget.isVisible();
    }

    @Override
    protected void initialize(Application app) {
        this.inputManager = app.getInputManager();
    }

    @Override
    protected void cleanup(Application app) {
        closeWizard();
        this.inputManager = null;
    }

    @Override
    protected void onEnable() {
        openWizard();
        inputManager.addMapping(
            ACTION_DEBUG_CYCLE, new KeyTrigger(KeyInput.KEY_F8));
        inputManager.addListener(this, ACTION_DEBUG_CYCLE);
    }

    @Override
    protected void onDisable() {
        closeWizard();
        if (inputManager.hasMapping(ACTION_DEBUG_CYCLE)) {
            inputManager.deleteMapping(ACTION_DEBUG_CYCLE);
        }
        inputManager.removeListener(this);
    }

    @Override
    public void update(float tpf) {
        if (!isEnabled()) {
            EventBus.UiNavigation nav =
                context.eventBus().pollUiNavigation();
            if (nav == EventBus.UiNavigation.OPEN_MISSION_WIZARD) {
                setEnabled(true);
            }
        }
        if (widget != null) {
            widget.update(tpf, getApplication().getCamera());
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (ACTION_DEBUG_CYCLE.equals(name) && isPressed && widget != null) {
            widget.cycleStep();
        }
    }

    private void openWizard() {
        if (widget != null) return;
        widget = new MissionWizardWidget(context);
        widget.setOnCancel(() -> setEnabled(false));
        widget.attachTo(context.guiGraph().getModalNode());
        logger.info("Mission Wizard opened");
    }

    private void closeWizard() {
        if (widget != null) {
            widget.close();
            widget = null;
            logger.info("Mission Wizard closed");
        }
    }
}
```


---

## 6. Composants (component/)

### 6.1 ProgressBar.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class ProgressBar {

    private final float trackWidth;
    private final float trackHeight;
    private final Container root;
    private final Container fill;

    public ProgressBar(float width, float height) {
        this.trackWidth = width;
        this.trackHeight = height;

        root = new Container();
        root.setPreferredSize(new Vector3f(width, height, 0));
        root.setBackground(new QuadBackgroundComponent(new ColorRGBA(
            MissionWizardStyles.WIZARD_BORDER.r,
            MissionWizardStyles.WIZARD_BORDER.g,
            MissionWizardStyles.WIZARD_BORDER.b, 0.50f)));

        fill = new Container();
        fill.setPreferredSize(new Vector3f(0, height, 0));
        fill.setBackground(new QuadBackgroundComponent(
            MissionWizardStyles.WIZARD_ACCENT));
        root.attachChild(fill);
    }

    public Container getNode() { return root; }

    public void setProgress(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        fill.setPreferredSize(
            new Vector3f(trackWidth * clamped, trackHeight, 0));
    }
}
```


### 6.2 Badge.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.simsilica.lemur.*;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class Badge {

    public enum Variant { SUCCESS, WARNING, MUTED }

    private final Container root;

    public Badge(String text, Variant variant) {
        root = new Container(MissionWizardStyles.STYLE);

        ColorRGBA bg;
        ColorRGBA fg;
        switch (variant) {
            case SUCCESS -> {
                bg = new ColorRGBA(
                    MissionWizardStyles.WIZARD_SUCCESS.r,
                    MissionWizardStyles.WIZARD_SUCCESS.g,
                    MissionWizardStyles.WIZARD_SUCCESS.b, 0.80f);
                fg = MissionWizardStyles.WIZARD_TEXT_PRIMARY;
            }
            case WARNING -> {
                bg = new ColorRGBA(
                    MissionWizardStyles.WIZARD_WARNING.r,
                    MissionWizardStyles.WIZARD_WARNING.g,
                    MissionWizardStyles.WIZARD_WARNING.b, 0.80f);
                fg = MissionWizardStyles.WIZARD_TEXT_PRIMARY;
            }
            default -> {
                bg = MissionWizardStyles.WIZARD_BG_CARD;
                fg = MissionWizardStyles.WIZARD_TEXT_SECONDARY;
            }
        }

        root.setBackground(MissionWizardStyles.createGradient(bg));
        Label label = root.addChild(
            new Label(text, MissionWizardStyles.STYLE));
        label.setFont(MissionWizardStyles.rajdhani(10));
        label.setColor(fg);
    }

    public Container getNode() { return root; }
}
```


### 6.3 SelectableCard.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class SelectableCard {

    public enum State { IDLE, HOVER, SELECTED, DISABLED }

    private final Container root;
    private State state;

    public SelectableCard(float width, float height,
                          String title, String subtitle, String value,
                          Badge badge, State initial) {
        this.state = initial;

        root = new Container(
            new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
        root.setPreferredSize(new Vector3f(width, height, 0));

        Container iconSlot = root.addChild(new Container());
        iconSlot.setPreferredSize(new Vector3f(48, 48, 0));
        iconSlot.setBackground(null);

        Label titleLabel = root.addChild(
            new Label(title, MissionWizardStyles.STYLE));
        titleLabel.setFont(MissionWizardStyles.rajdhani(16));
        titleLabel.setTextHAlignment(HAlignment.Center);

        Label subtitleLabel = root.addChild(
            new Label(subtitle, MissionWizardStyles.STYLE));
        subtitleLabel.setFont(MissionWizardStyles.rajdhani(11));
        subtitleLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        subtitleLabel.setTextHAlignment(HAlignment.Center);

        if (value != null) {
            Label valueLabel = root.addChild(
                new Label(value, MissionWizardStyles.STYLE));
            valueLabel.setFont(MissionWizardStyles.mono(11));
            valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
            valueLabel.setTextHAlignment(HAlignment.Center);
        }

        if (badge != null) {
            root.addChild(badge.getNode());
        }

        applyState(initial);

        if (initial != State.DISABLED) {
            MouseEventControl.addListenersToSpatial(
                    root, new DefaultMouseListener() {
                @Override
                public void mouseEntered(MouseMotionEvent event,
                        Spatial target, Spatial capture) {
                    if (state != State.SELECTED) applyState(State.HOVER);
                }
                @Override
                public void mouseExited(MouseMotionEvent event,
                        Spatial target, Spatial capture) {
                    if (state != State.SELECTED) applyState(State.IDLE);
                }
                @Override
                public void click(MouseButtonEvent event,
                        Spatial target, Spatial capture) {
                    applyState(State.SELECTED);
                }
            });
        }
    }

    public Container getNode() { return root; }

    public void applyState(State newState) {
        this.state = newState;
        switch (newState) {
            case IDLE -> root.setBackground(
                MissionWizardStyles.createGradient(
                    MissionWizardStyles.WIZARD_BG_CARD));
            case HOVER -> root.setBackground(
                MissionWizardStyles.createGradient(
                    MissionWizardStyles.WIZARD_BG_CARD_HOVER));
            case SELECTED -> root.setBackground(
                MissionWizardStyles.createGradient(
                    MissionWizardStyles.WIZARD_SELECTED));
            case DISABLED -> root.setBackground(
                MissionWizardStyles.createGradient(new ColorRGBA(
                    MissionWizardStyles.WIZARD_BG_CARD.r,
                    MissionWizardStyles.WIZARD_BG_CARD.g,
                    MissionWizardStyles.WIZARD_BG_CARD.b, 0.30f)));
        }
    }
}
```


### 6.4 SegmentedControl.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class SegmentedControl {

    private final Container root;
    private final Button[] buttons;
    private int selectedIndex = -1;

    public SegmentedControl(String... labels) {
        root = new Container(
            new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
        root.setBackground(null);
        buttons = new Button[labels.length];

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button btn = new Button(labels[i], MissionWizardStyles.STYLE);
            btn.setFont(MissionWizardStyles.rajdhani(14));
            btn.setBackground(MissionWizardStyles.createGradient(
                MissionWizardStyles.WIZARD_BG_CARD));
            btn.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);
            btn.addClickCommands(src -> select(idx));
            buttons[i] = btn;
            root.addChild(btn);
        }
    }

    public Container getNode() { return root; }

    public SegmentedControl select(int index) {
        selectedIndex = index;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setBackground(MissionWizardStyles.createGradient(
                i == index
                    ? MissionWizardStyles.WIZARD_ACCENT
                    : MissionWizardStyles.WIZARD_BG_CARD));
        }
        return this;
    }
}
```


### 6.5 PopupList.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

import java.util.List;

public class PopupList {

    private final Container root;
    private final Label valueLabel;
    private final Container popup;
    private boolean open = false;
    private String selectedValue;

    public PopupList(float width, List<String> options, String defaultValue) {
        this.selectedValue = defaultValue;

        root = new Container(new BoxLayout(Axis.Y, FillMode.None));
        root.setBackground(null);

        Container trigger = root.addChild(new Container(
            new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE));
        trigger.setPreferredSize(new Vector3f(width, 32, 0));
        trigger.setBackground(MissionWizardStyles.createGradient(
            MissionWizardStyles.WIZARD_BG_CARD));

        valueLabel = trigger.addChild(
            new Label(defaultValue, MissionWizardStyles.STYLE));
        valueLabel.setFont(MissionWizardStyles.mono(14));
        valueLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

        Label chevron = trigger.addChild(
            new Label("v", MissionWizardStyles.STYLE));
        chevron.setFont(MissionWizardStyles.mono(14));
        chevron.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        chevron.setTextHAlignment(HAlignment.Right);

        popup = new Container(
            new BoxLayout(Axis.Y, FillMode.None), MissionWizardStyles.STYLE);
        popup.setPreferredSize(new Vector3f(width, 0, 0));
        popup.setBackground(MissionWizardStyles.createGradient(
            MissionWizardStyles.WIZARD_BG_DEEP));

        for (String option : options) {
            Container row = popup.addChild(new Container(
                new BoxLayout(Axis.X, FillMode.None)));
            row.setPreferredSize(new Vector3f(width, 28, 0));
            row.setBackground(MissionWizardStyles.createGradient(
                MissionWizardStyles.WIZARD_BG_DEEP));

            Label optLabel = row.addChild(
                new Label(option, MissionWizardStyles.STYLE));
            optLabel.setFont(MissionWizardStyles.mono(14));
            optLabel.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

            MouseEventControl.addListenersToSpatial(
                    row, new DefaultMouseListener() {
                @Override
                public void mouseEntered(MouseMotionEvent evt,
                        Spatial t, Spatial c) {
                    row.setBackground(MissionWizardStyles.createGradient(
                        MissionWizardStyles.WIZARD_BG_CARD));
                }
                @Override
                public void mouseExited(MouseMotionEvent evt,
                        Spatial t, Spatial c) {
                    row.setBackground(MissionWizardStyles.createGradient(
                        MissionWizardStyles.WIZARD_BG_DEEP));
                }
                @Override
                public void click(MouseButtonEvent evt,
                        Spatial t, Spatial c) {
                    selectedValue = option;
                    valueLabel.setText(option);
                    closePopup();
                }
            });
        }

        MouseEventControl.addListenersToSpatial(
                trigger, new DefaultMouseListener() {
            @Override
            public void click(MouseButtonEvent evt,
                    Spatial t, Spatial c) {
                if (open) closePopup(); else openPopup();
            }
        });
    }

    public Container getNode() { return root; }
    public String getSelectedValue() { return selectedValue; }

    private void openPopup() {
        if (!open) { root.addChild(popup); open = true; }
    }

    private void closePopup() {
        if (open) { root.removeChild(popup); open = false; }
    }
}
```


### 6.6 InfoBanner.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class InfoBanner {

    public enum Variant { INFO, WARNING }

    private final Container root;

    public InfoBanner(String text, Variant variant) {
        ColorRGBA barColor = (variant == Variant.INFO)
            ? MissionWizardStyles.WIZARD_ACCENT
            : MissionWizardStyles.WIZARD_WARNING;

        root = new Container(
            new BoxLayout(Axis.X, FillMode.None), MissionWizardStyles.STYLE);
        root.setPreferredSize(new Vector3f(0, 48, 0));
        root.setBackground(MissionWizardStyles.createGradient(
            MissionWizardStyles.WIZARD_BG_CARD));

        Container bar = root.addChild(new Container());
        bar.setPreferredSize(new Vector3f(4, 48, 0));
        bar.setBackground(new QuadBackgroundComponent(barColor));

        Container body = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        body.setBackground(null);

        Label icon = body.addChild(
            new Label("i", MissionWizardStyles.STYLE));
        icon.setFont(MissionWizardStyles.rajdhani(14));
        icon.setColor(barColor);

        Label msg = body.addChild(
            new Label(text, MissionWizardStyles.STYLE));
        msg.setFont(MissionWizardStyles.mono(12));
        msg.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    }

    public Container getNode() { return root; }
}
```


### 6.7 LabeledField.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.component;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;

public class LabeledField {

    private final Container root;

    public LabeledField(String labelText, Panel input, String helperText) {
        root = new Container(new BoxLayout(Axis.Y, FillMode.None));
        root.setBackground(null);

        Label label = root.addChild(
            new Label(labelText, MissionWizardStyles.STYLE));
        label.setFont(MissionWizardStyles.rajdhani(12));
        label.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        root.addChild(input);

        if (helperText != null) {
            Label helper = root.addChild(
                new Label(helperText, MissionWizardStyles.STYLE));
            helper.setFont(MissionWizardStyles.mono(10));
            helper.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        }
    }

    public Container getNode() { return root; }
}
```


---

## 7. Steps (step/)

### 7.1 StepMissionType.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.Badge;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;

public class StepMissionType {

    private static final float CARD_W = 256f;
    private static final float CARD_H = 152f;

    private final Container root;

    public StepMissionType() {
        root = new Container(new BoxLayout(Axis.Y, FillMode.None));
        root.setBackground(null);

        Label title = root.addChild(
            new Label("MISSION TYPE", MissionWizardStyles.STYLE));
        title.setFont(MissionWizardStyles.rajdhani(20));
        title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

        Label subtitle = root.addChild(new Label(
            "// select the target orbit", MissionWizardStyles.STYLE));
        subtitle.setFont(MissionWizardStyles.mono(12));
        subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        Container row1 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        row1.setBackground(null);

        row1.addChild(new SelectableCard(CARD_W, CARD_H,
            "LEO", "Low Earth Orbit", "160 - 2 000 km",
            new Badge("v AVAILABLE", Badge.Variant.SUCCESS),
            SelectableCard.State.SELECTED).getNode());
        row1.addChild(new SelectableCard(CARD_W, CARD_H,
            "GTO", "Geostationary Transfer", "200 x 35 786 km",
            new Badge("o IN PROGRESS", Badge.Variant.WARNING),
            SelectableCard.State.IDLE).getNode());
        row1.addChild(new SelectableCard(CARD_W, CARD_H,
            "SSO", "Sun-Synchronous Orbit", "600 - 800 km",
            new Badge("SOON", Badge.Variant.MUTED),
            SelectableCard.State.DISABLED).getNode());

        Container row2 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        row2.setBackground(null);

        row2.addChild(new SelectableCard(CARD_W, CARD_H,
            "MEO", "Medium Earth Orbit", "2 000 - 35 786 km",
            new Badge("SOON", Badge.Variant.MUTED),
            SelectableCard.State.DISABLED).getNode());
        row2.addChild(new SelectableCard(CARD_W, CARD_H,
            "GEO", "Geostationary Orbit", "35 786 km",
            new Badge("SOON", Badge.Variant.MUTED),
            SelectableCard.State.DISABLED).getNode());
        row2.addChild(new SelectableCard(CARD_W, CARD_H,
            "TLI", "Trans-Lunar Injection", "Cislunar",
            new Badge("SOON", Badge.Variant.MUTED),
            SelectableCard.State.DISABLED).getNode());
    }

    public Container getNode() { return root; }
}
```


### 7.2 StepParameters.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.*;

public class StepParameters {

    private static final float COL_WIDTH = 400f;
    private final Container root;

    public StepParameters() {
        root = new Container(new BoxLayout(Axis.Y, FillMode.None));
        root.setBackground(null);

        Label title = root.addChild(new Label(
            "PARAMETERS — LEO", MissionWizardStyles.STYLE));
        title.setFont(MissionWizardStyles.rajdhani(20));
        title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

        Label subtitle = root.addChild(new Label(
            "// target orbit configuration", MissionWizardStyles.STYLE));
        subtitle.setFont(MissionWizardStyles.mono(12));
        subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        // Row 1 — Mission Name
        root.addChild(new LabeledField(
            "MISSION NAME", monoField("ORBITLAB-LEO-001"), null).getNode());

        // Row 2 — Altitude + Tolerance
        Container row2 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        row2.setBackground(null);

        Container altCol = col(COL_WIDTH);
        Slider altSlider = new Slider(
            new DefaultRangedValueModel(160, 2000, 550),
            Axis.X, MissionWizardStyles.STYLE);
        altCol.addChild(new LabeledField("TARGET ALTITUDE", altSlider,
            "160 km                         2 000 km").getNode());
        Label altValue = altCol.addChild(
            new Label("550 km", MissionWizardStyles.STYLE));
        altValue.setFont(MissionWizardStyles.rajdhani(28));
        altValue.setColor(MissionWizardStyles.WIZARD_ACCENT);
        row2.addChild(altCol);

        Container tolCol = col(COL_WIDTH);
        tolCol.addChild(new LabeledField("ALTITUDE TOLERANCE",
            monoField("1"), "+/- km · CMA-ES convergence").getNode());
        row2.addChild(tolCol);

        // Row 3 — Inclination + RAAN
        root.addChild(twoColRow(
            new LabeledField("INCLINATION", monoField("51.6"),
                "degrees · 0° = equatorial"),
            new LabeledField("RAAN (Ω)", monoField("0.0"),
                "degrees · ascending node")));

        // Row 4 — Arg Perigee + Strategy
        Container row4 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        row4.setBackground(null);

        Container perigeeCol = col(COL_WIDTH);
        perigeeCol.addChild(new LabeledField(
            "ARGUMENT OF PERIGEE (ω)",
            monoField("0.0"), "degrees").getNode());
        row4.addChild(perigeeCol);

        Container stratCol = col(COL_WIDTH);
        SegmentedControl strat = new SegmentedControl(
            "2 BURNS", "DIRECT", "HOHMANN").select(0);
        stratCol.addChild(new LabeledField("INSERTION STRATEGY",
            strat.getNode(),
            "2 burns: gravity turn + circularisation").getNode());
        row4.addChild(stratCol);

        // Row 5 — Launch Date
        TextField dateField = monoField("2026-06-15T06:00:00Z");
        dateField.setPreferredSize(new Vector3f(320, 0, 0));
        root.addChild(new LabeledField(
            "LAUNCH DATE", dateField, "UTC · Orekit epoch").getNode());

        // Info Banner
        root.addChild(new InfoBanner(
            "Iterative altitude correction enabled: the mean altitude "
            + "over a full period (J2 filter) is used as reference. "
            + "Convergence in 2-3 iterations · tolerance 500 m.",
            InfoBanner.Variant.INFO).getNode());
    }

    public Container getNode() { return root; }

    private TextField monoField(String value) {
        TextField f = new TextField(value, MissionWizardStyles.STYLE);
        f.setFont(MissionWizardStyles.mono(14));
        return f;
    }

    private Container col(float w) {
        Container c = new Container(
            new BoxLayout(Axis.Y, FillMode.None));
        c.setBackground(null);
        c.setPreferredSize(new Vector3f(w, 0, 0));
        return c;
    }

    private Container twoColRow(LabeledField left, LabeledField right) {
        Container row = new Container(
            new BoxLayout(Axis.X, FillMode.None));
        row.setBackground(null);
        Container l = col(COL_WIDTH);
        l.addChild(left.getNode());
        row.addChild(l);
        Container r = col(COL_WIDTH);
        r.addChild(right.getNode());
        row.addChild(r);
        return row;
    }
}
```


### 7.3 StepLaunchSite.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.*;

import java.util.List;

public class StepLaunchSite {

    private static final float COL3_W = 260f;
    private static final float COL2_W = 400f;
    private static final float MAP_W = 780f;
    private static final float MAP_H = 160f;

    private final Container root;

    public StepLaunchSite() {
        root = new Container(new BoxLayout(Axis.Y, FillMode.None));
        root.setBackground(null);

        Label title = root.addChild(
            new Label("LAUNCH SITE", MissionWizardStyles.STYLE));
        title.setFont(MissionWizardStyles.rajdhani(20));
        title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

        Label subtitle = root.addChild(new Label(
            "// cosmodrome selection", MissionWizardStyles.STYLE));
        subtitle.setFont(MissionWizardStyles.mono(12));
        subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        PopupList cosmodrome = new PopupList(800f, List.of(
            "Kourou (CSG) — French Guiana",
            "Cape Canaveral (CCSFS) — Florida, USA",
            "Baikonur — Kazakhstan"
        ), "Kourou (CSG) — French Guiana");
        root.addChild(new LabeledField(
            "COSMODROME", cosmodrome.getNode(), null).getNode());

        Container row2 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        row2.setBackground(null);
        row2.addChild(fieldCol(COL3_W, "LATITUDE",
            "5.236", "decimal degrees · N positive"));
        row2.addChild(fieldCol(COL3_W, "LONGITUDE",
            "-52.769", "decimal degrees · E positive"));
        row2.addChild(fieldCol(COL3_W, "GROUND ALTITUDE",
            "14", "meters MSL"));

        Container row3 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        row3.setBackground(null);
        row3.addChild(fieldCol(COL2_W, "LAUNCH HEADING",
            "90.0", "azimuth · 90° = East"));

        Container pressCol = new Container(
            new BoxLayout(Axis.Y, FillMode.None));
        pressCol.setBackground(null);
        pressCol.setPreferredSize(new Vector3f(COL2_W, 0, 0));
        SegmentedControl pressCtrl = new SegmentedControl(
            "AUTO", "ISA", "MANUAL").select(0);
        pressCol.addChild(new LabeledField("ATMOSPHERIC PRESSURE",
            pressCtrl.getNode(),
            "ground atmospheric model").getNode());
        row3.addChild(pressCol);

        // Mini-map
        Container map = root.addChild(new Container());
        map.setPreferredSize(new Vector3f(MAP_W, MAP_H, 0));
        map.setBackground(new QuadBackgroundComponent(
            MissionWizardStyles.WIZARD_BG_DEEP));

        for (int i = 0; i < 6; i++) {
            Container vLine = new Container();
            vLine.setPreferredSize(new Vector3f(1, MAP_H, 0));
            vLine.setBackground(new QuadBackgroundComponent(
                MissionWizardStyles.WIZARD_BORDER));
            vLine.setLocalTranslation(
                MAP_W / 7f * (i + 1), MAP_H, 0.1f);
            map.attachChild(vLine);
        }
        for (int i = 0; i < 3; i++) {
            Container hLine = new Container();
            hLine.setPreferredSize(new Vector3f(MAP_W, 1, 0));
            hLine.setBackground(new QuadBackgroundComponent(
                MissionWizardStyles.WIZARD_BORDER));
            hLine.setLocalTranslation(
                0, MAP_H / 4f * (i + 1), 0.1f);
            map.attachChild(hLine);
        }

        Container dot = new Container();
        dot.setPreferredSize(new Vector3f(8, 8, 0));
        dot.setBackground(new QuadBackgroundComponent(
            MissionWizardStyles.WIZARD_ACCENT));
        dot.setLocalTranslation(
            MAP_W / 2f - 4f, MAP_H / 2f + 4f, 0.2f);
        map.attachChild(dot);

        Label caption = root.addChild(new Label(
            "CSG · KOUROU · 5.236°N 52.769°W",
            MissionWizardStyles.STYLE));
        caption.setFont(MissionWizardStyles.mono(10));
        caption.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        caption.setTextHAlignment(HAlignment.Right);
    }

    public Container getNode() { return root; }

    private Container fieldCol(float w, String label,
            String value, String helper) {
        Container col = new Container(
            new BoxLayout(Axis.Y, FillMode.None));
        col.setBackground(null);
        col.setPreferredSize(new Vector3f(w, 0, 0));
        TextField f = new TextField(value, MissionWizardStyles.STYLE);
        f.setFont(MissionWizardStyles.mono(14));
        col.addChild(
            new LabeledField(label, f, helper).getNode());
        return col;
    }
}
```


### 7.4 StepLauncher.java

```java
package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.*;

import java.util.List;

public class StepLauncher {

    private static final float CARD_W = 400f;
    private static final float CARD_H = 112f;

    private final Container root;

    public StepLauncher() {
        root = new Container(new BoxLayout(Axis.Y, FillMode.None));
        root.setBackground(null);

        Label title = root.addChild(new Label(
            "LAUNCHER & PAYLOAD", MissionWizardStyles.STYLE));
        title.setFont(MissionWizardStyles.rajdhani(20));
        title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

        Label subtitle = root.addChild(new Label(
            "// vehicle configuration", MissionWizardStyles.STYLE));
        subtitle.setFont(MissionWizardStyles.mono(12));
        subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        Container vRow1 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        vRow1.setBackground(null);
        vRow1.addChild(new SelectableCard(CARD_W, CARD_H,
            "FALCON HEAVY", "S1 thrust: 22.8 MN",
            "Isp S2: 348 s · LEO payload: 63.8 t",
            null, SelectableCard.State.SELECTED).getNode());
        vRow1.addChild(new SelectableCard(CARD_W, CARD_H,
            "ARIANE 5 ECA", "S1 thrust: 7.6 MN",
            "Isp S2: 431 s · LEO payload: 21 t",
            null, SelectableCard.State.IDLE).getNode());

        Container vRow2 = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        vRow2.setBackground(null);
        vRow2.addChild(new SelectableCard(CARD_W, CARD_H,
            "ORBITLAB CUSTOM", "S1: ~8.4 MN · S2: ~980 kN",
            "Isp S2: 348 s · project config",
            null, SelectableCard.State.IDLE).getNode());
        vRow2.addChild(new SelectableCard(CARD_W, CARD_H,
            "CUSTOM", "Define S1 & S2 parameters",
            "manually",
            null, SelectableCard.State.IDLE).getNode());

        Label payloadLabel = root.addChild(
            new Label("PAYLOAD", MissionWizardStyles.STYLE));
        payloadLabel.setFont(MissionWizardStyles.rajdhani(12));
        payloadLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

        Container payloadRow = root.addChild(
            new Container(new BoxLayout(Axis.X, FillMode.None)));
        payloadRow.setBackground(null);

        PopupList payloadType = new PopupList(520f, List.of(
            "Communication satellite",
            "Earth observation satellite",
            "Scientific probe",
            "Cargo module"
        ), "Communication satellite");
        payloadRow.addChild(payloadType.getNode());

        TextField massField = new TextField(
            "15000", MissionWizardStyles.STYLE);
        massField.setFont(MissionWizardStyles.mono(14));
        massField.setPreferredSize(new Vector3f(140, 0, 0));
        payloadRow.addChild(massField);

        Label kgLabel = payloadRow.addChild(
            new Label("kg", MissionWizardStyles.STYLE));
        kgLabel.setFont(MissionWizardStyles.mono(14));
        kgLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        kgLabel.setPreferredSize(new Vector3f(40, 0, 0));

        Button removeBtn = payloadRow.addChild(
            new Button("x", MissionWizardStyles.STYLE));
        removeBtn.setBackground(MissionWizardStyles.createGradient(
            MissionWizardStyles.WIZARD_DANGER));
        removeBtn.setPreferredSize(new Vector3f(32, 32, 0));

        Button addPayloadBtn = root.addChild(
            new Button("+ Add payload", MissionWizardStyles.STYLE));
        addPayloadBtn.setBackground(MissionWizardStyles.createGradient(
            MissionWizardStyles.WIZARD_BG_CARD));
        addPayloadBtn.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
        addPayloadBtn.setFont(MissionWizardStyles.rajdhani(14));

        root.addChild(new InfoBanner(
            "Tsiolkovsky check: required Δv ≈ 9 500 m/s · "
            + "available Δv S2 ≈ 11 200 m/s · v Feasibility confirmed",
            InfoBanner.Variant.WARNING).getNode());
    }

    public Container getNode() { return root; }
}
```


---

## 8. Plan de vérification

| # | Vérification | Attendu |
|---|---|---|
| 1 | `./gradlew classes` | Compile sans erreur |
| 2 | "Create" dans MissionPanel | Modal centré, fond `WIZARD_BG_DEEP` |
| 3 | Backdrop visible | Scene assombrie `WIZARD_BACKDROP` |
| 4 | Clic scene a travers le backdrop | Aucun planet sélectionné |
| 5 | Drag souris scene | Camera bloquée |
| 6 | Step 1 : LEO sélectionnée | Card bg `WIZARD_SELECTED` |
| 7 | Step 1 : GTO idle | Card bg `WIZARD_BG_CARD` |
| 8 | Step 1 : SSO disabled | Card bg `WIZARD_BG_CARD` @ 0.30 alpha |
| 9 | Hover sur card | Fond `WIZARD_BG_CARD_HOVER` |
| 10 | F8 : Steps 2/3/4 | Layouts conformes aux mockups |
| 11 | Step 4 : bouton Create | Fond `WIZARD_SUCCESS` |
| 12 | Cancel | Modal fermé, camera ré-activée |
| 13 | `./gradlew test` | Tests existants passent |

---

## 9. Résumé des dépendances

```
MissionWizardAppState
  ├── ApplicationContext (existant)
  │   ├── GuiGraph.getModalNode() (modifié)
  │   └── EventBus.UiNavigation (ajouté)
  ├── MissionWizardWidget (nouveau)
  │   ├── ModalBackdrop (nouveau)
  │   ├── WizardStepper (nouveau)
  │   ├── WizardFooter (nouveau)
  │   │   └── ProgressBar (nouveau)
  │   └── step/* (4 nouveaux)
  │       └── component/* (7 nouveaux)
  └── MissionWizardStyles (nouveau)
      └── Palette WIZARD_* (self-contained)
```
