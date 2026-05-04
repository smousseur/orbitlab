# Spec — Transitions de caméra entre vues

## 1. Contexte

Aujourd'hui, les changements de vue dans OrbitLab sont **instantanés** :

- Touche `R` → `ViewModeAppState.onAction()`
  (`states/camera/ViewModeAppState.java:64-69`) appelle `focusView.reset()`
  qui repasse en `SOLAR` + Soleil + distance par défaut.
- Clic sur une planète → `PlanetPoseAppState.onSelectPlanet()`
  (`states/scene/PlanetPoseAppState.java:93-99`) règle la distance puis
  appelle `focusView.viewPlanet(body)`.
- Clic sur une mission → `MissionRenderer.onSpacecraftSelected()`
  (`states/mission/MissionRenderer.java:113-118`) règle la distance puis
  appelle `focusView.viewSpacecraft(...)`.

Dans tous les cas, le `FocusView` mute immédiatement et
`FloatingOriginAppState`
(`states/camera/FloatingOriginAppState.java:50-98`) re-centre la scène
en un seul frame sur le nouveau corps. Visuellement c'est un cut sec,
sans contexte spatial pour l'utilisateur — il n'a aucun repère sur
"d'où" il vient ni "où" est le nouveau corps dans le système.

L'objectif est d'introduire une **transition fluide** d'environ 2,5 s
qui anime le pivot et la distance de la caméra, conserve l'orientation
courante, bloque les commandes utilisateur pendant l'animation, et
reste paramétrable.

---

## 2. Décisions de conception

| Sujet | Décision |
|---|---|
| Orientation pendant la transition | **Conservée** (`yawRad`/`pitchRad` non animés). Seuls le pivot et la distance s'animent. |
| Re-trigger pendant transition | **Ignoré** — toutes les sources d'action (R, clic planète, clic mission) sont bloquées. |
| Horloge de simulation | **Continue d'avancer**. Le pivot cible est ré-évalué chaque frame depuis la position courante du corps. |
| Durée par défaut | **2,5 s** (configurable via `CameraTransitionConfig`). |
| Easing par défaut | `SMOOTHSTEP` (`t*t*(3-2t)`), interface laissée extensible. |

---

## 3. Architecture

### 3.1 Nouveau record `CameraTransitionConfig`

Fichier : `engine/CameraTransitionConfig.java`

```java
public record CameraTransitionConfig(float durationSec, Easing easing) {
  public CameraTransitionConfig {
    if (!Float.isFinite(durationSec) || durationSec <= 0f)
      throw new IllegalArgumentException("durationSec must be > 0");
    Objects.requireNonNull(easing, "easing");
  }

  public static CameraTransitionConfig defaults() {
    return new CameraTransitionConfig(2.5f, Easing.SMOOTHSTEP);
  }
}
```

Imbriqué dans `EngineConfig` comme champ supplémentaire (cf. pattern
existant `OrbitCameraConfig` dans `engine/EngineConfig.java:12-35`) :

```java
public record EngineConfig(
    float systemRadiusWorldUnits,
    OrbitCameraConfig orbitCamera,
    CameraTransitionConfig cameraTransition) { ... }
```

`EngineConfig.defaultSolarSystem()` renvoie
`new EngineConfig(systemRadius, ..., CameraTransitionConfig.defaults())`.

### 3.2 Enum `Easing`

Fichier : `engine/Easing.java`

```java
public enum Easing {
  LINEAR     { public float apply(float t) { return t; } },
  SMOOTHSTEP { public float apply(float t) { return t*t*(3f - 2f*t); } },
  COSINE     { public float apply(float t) { return 0.5f - 0.5f*(float)Math.cos(Math.PI*t); } };

  public abstract float apply(float t);
}
```

### 3.3 Cible de transition typée

Fichier : `app/view/TransitionTarget.java`

Sealed interface qui factorise les trois types de cibles existants :

```java
public sealed interface TransitionTarget {
  record Solar() implements TransitionTarget {}
  record Planet(SolarSystemBody body) implements TransitionTarget {}
  record Spacecraft(String missionName, SolarSystemBody parentBody)
      implements TransitionTarget {}
}
```

### 3.4 État de transition actif

Fichier : `states/camera/CameraTransition.java` (package-private)

Classe (non-record car mutable sur `elapsedSec`) qui tient les snapshots,
les fournisseurs de pivot vivants, et l'avancement :

```java
final class CameraTransition {
  final TransitionTarget target;
  final Supplier<Vector3f> srcPivotWorld;     // ré-évalué chaque frame
  final Supplier<Vector3f> dstPivotWorld;     // ré-évalué chaque frame
  final float srcDistance;
  final float dstDistance;
  final float durationSec;
  final Easing easing;
  private float elapsedSec;

  void advance(float tpf) {
    elapsedSec = Math.min(elapsedSec + tpf, durationSec);
  }
  boolean isFinished() { return elapsedSec >= durationSec; }
  float easedProgress() { return easing.apply(elapsedSec / durationSec); }
}
```

### 3.5 Nouvel AppState `CameraTransitionAppState`

Fichier : `states/camera/CameraTransitionAppState.java`

**Responsabilités** :

1. Exposer `requestSolar()`, `requestPlanet(body)`,
   `requestSpacecraft(mission, parent)`.
2. Si une transition est déjà active → ignorer la requête (silencieux +
   log debug).
3. Si la cible == état courant → ignorer (no-op).
4. Construire un `CameraTransition` avec :
   - `srcPivotWorld` = supplier qui renvoie chaque frame le pivot rendu
     courant (cf. §3.6).
   - `dstPivotWorld` = supplier qui renvoie chaque frame la position
     rendue de la cible (cf. §3.6).
   - `srcDistance` = `focusView.getCameraDistance()` au démarrage.
   - `dstDistance` = distance dépendante du corps cible (cf. §4).
5. **Pendant la transition** (`update(tpf)`) :
   - Avancer `elapsedSec`.
   - Calculer pivot interpolé : `lerp(src, dst, easing(t))` et l'imposer
     à l'orbit camera via `orbitCam.setTarget(() -> pivotInterp)`.
   - Calculer distance interpolée :
     `lerp(srcDistance, dstDistance, easing(t))` puis
     `focusView.setCameraDistance(...)`.
   - **Ne pas toucher au mode/body de `FocusView`** (cf. §3.7).
6. **À la fin** (`isFinished()`) :
   - Appliquer le mode/body cible *atomiquement* sur `FocusView`
     (`reset()` pour Solar, `viewPlanet(body)` ou `viewSpacecraft(...)`).
   - Forcer `focusView.setCameraDistance(dstDistance)` (la valeur exacte,
     pas l'interpolée — pour éviter le drift float).
   - Appeler `orbitCam.clearTarget()` pour rebrancher le fallback
     (= `Vector3f.ZERO`, qui correspond au nouveau corps après que
     `FloatingOriginAppState` aura re-centré la scène au frame suivant).
   - Mettre `active = null`.
7. Méthode publique `boolean isActive()` consommée par les autres
   systèmes pour bloquer leurs entrées.

### 3.6 Calcul des pivots dans le repère **rendu** (point clé)

Le repère monde de la caméra orbitale dépend du mode courant à cause
de `FloatingOriginAppState` :

- En `SOLAR` : `solarRoot.translation = (0,0,0)` → les corps sont à
  leur position héliocentrique (en unités solaires).
- En `PLANET` : `solarRoot.translation = -planetSpatial.localTranslation`
  → la planète focus est ramenée à l'origine ; les autres corps sont
  décalés de `-planetLocal`.
- En `SPACECRAFT` : même décalage que `PLANET` côté solar root.

Comme la décision §2 est de **garder l'horloge active** et de **ne pas
toucher au mode `FocusView` pendant la transition**,
`FloatingOriginAppState` continue d'opérer dans le mode source pendant
toute la transition. Donc `spatial.getWorldTranslation()` renvoie
chaque frame une coordonnée **dans le même repère** que la caméra →
on peut interpoler dessus sans correction.

**Formules concrètes pour les suppliers** :

| Cible | `srcPivotWorld` | `dstPivotWorld` |
|---|---|---|
| Tous modes source | `() -> Vector3f.ZERO` (le corps source est toujours à l'origine rendue grâce à FloatingOrigin ; en SOLAR initial, l'origine est le Soleil — qui est aussi à `(0,0,0)` car le solar root n'est pas shifté). | — |
| `Solar` | — | `() -> sunSpatial.getWorldTranslation().clone()` (en SOLAR source : `(0,0,0)` ; en PLANET source : `-planetLocal`). |
| `Planet(body)` | — | `() -> sceneGraph.getBodySpatial(body).getWorldTranslation().clone()`. |
| `Spacecraft(mission, parent)` | — | `() -> sceneGraph.getBodySpatial(parent).getWorldTranslation().clone()` (le spacecraft est à <1 km du parent ; en unités solaires (1 unit ≈ 10⁹ m) la différence est sub-pixel). |

Cas spécial : si le supplier renvoie un `Vector3f` non fini ou null,
fallback `Vector3f.ZERO` (cf. logique existante dans
`OrbitCameraAppState.computePivotWorld()` lignes 421-445).

### 3.7 Continuité visuelle au moment du switch de mode

À la fin de la transition, on bascule le mode `FocusView`.
`FloatingOriginAppState` va alors recentrer la scène sur le nouveau
corps : il translate `solarRoot` de `oldShift → newShift = -newBodyLocal`.
Le delta vaut `newShift - oldShift = -(newBodyLocal - oldBodyLocal)`.

Ce shift se produit **côté scène**, pas côté caméra : la caméra reste
où elle est en coordonnées monde. Or, juste avant le switch, le pivot
interpolé valait précisément `newBody.getWorldTranslation()` (= position
du nouveau corps dans le repère rendu source) et la caméra était à
`pivot + offset`. Après que `FloatingOrigin` a re-shifté de
`-(newBodyLocal - oldBodyLocal)`, le nouveau corps est désormais à
l'origine et la caméra apparaît exactement à `offset` autour de lui →
**continuité visuelle exacte**, à condition que `clearTarget()` (qui
remet le fallback `Vector3f.ZERO`) soit appelé au même frame que le
switch de mode.

⚠ Il faut s'assurer que `CameraTransitionAppState.update()` s'exécute
**avant** `FloatingOriginAppState.update()` dans l'ordre des AppStates.
Ils sont attachés dans `OrbitLabApplication.simpleInitApp()` ; il faut
attacher le nouvel AppState juste après `ViewModeAppState`
(ligne 76) et **avant** `FloatingOriginAppState` (ligne 78). JME
exécute les AppStates dans l'ordre d'attachement.

### 3.8 Blocage des entrées utilisateur

Trois sources d'entrée à bloquer pendant la transition :

| Source | Mécanisme actuel | Modification |
|---|---|---|
| Souris (drag/orbit/pan/wheel) sur l'orbit cam | `uiWantsMouse` BooleanSupplier (`OrbitCameraAppState.java:222,240,295`) | Composer ce supplier avec `cameraTransition.isActive()` au point de construction (`OrbitLabApplication.java:94-96`) : `() -> wizardState.isWizardVisible() \|\| cameraTransition.isActive()`. |
| Touche `R` (reset) | `ViewModeAppState.onAction()` (`states/camera/ViewModeAppState.java:64-69`) | Refactor : remplacer l'appel direct `focusView.reset()` par `cameraTransition.requestSolar()`. La méthode `requestSolar()` ignore la requête si une transition est active. |
| Clic planète | `PlanetPoseAppState.onSelectPlanet()` (`states/scene/PlanetPoseAppState.java:93-99`) | Idem : passer par `cameraTransition.requestPlanet(body)`. |
| Clic mission (spacecraft) | `MissionRenderer.onSpacecraftSelected()` (`states/mission/MissionRenderer.java:113-118`) | Idem : `cameraTransition.requestSpacecraft(mission, parent)`. |

L'avantage : le blocage est **centralisé** dans `requestXxx()`, qui
applique aussi la garde "déjà actif". Les call-sites n'ont rien à
savoir sur la transition.

---

## 4. Distance cible par corps

Conserver **exactement** la logique actuelle (les valeurs n'apparaissent
plus dans les call-sites mais sont calculées dans
`CameraTransitionAppState.computeTargetDistance(target)`) :

| Cible | Formule | Source actuelle |
|---|---|---|
| `Solar()` | `engineConfig.orbitCamera().defaultDistance()` | `FocusView.reset()` ligne 38 |
| `Planet(body)` | `PlanetRadius.radiusFor(body) * 5 / RenderContext.SOLAR_METERS_PER_UNIT` | `PlanetPoseAppState.onSelectPlanet()` lignes 95-96 |
| `Spacecraft(...)` | `MissionRenderer.SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS` | `MissionRenderer.onSpacecraftSelected()` ligne 116 |

À la fin de la transition la distance est forcée à cette valeur exacte
pour éviter tout drift float résiduel.

---

## 5. Wiring dans `ApplicationContext` et `OrbitLabApplication`

### 5.1 `ApplicationContext`

Ajouter un champ `cameraTransition` exposé via getter, alimenté par
`OrbitLabApplication` après l'instantiation de l'AppState :

```java
private CameraTransitionAppState cameraTransition;
public CameraTransitionAppState cameraTransition() { return cameraTransition; }
public void setCameraTransition(CameraTransitionAppState t) {
  this.cameraTransition = t;
}
```

(Pattern existant pour `setNearCamera` lignes 229-231 de
`ApplicationContext.java`.)

### 5.2 `OrbitLabApplication.simpleInitApp()` (`OrbitLabApplication.java:62-130`)

```java
CameraTransitionAppState cameraTransition =
    new CameraTransitionAppState(applicationContext);
applicationContext.setCameraTransition(cameraTransition);
stateManager.attach(cameraTransition);   // entre ViewModeAppState et FloatingOriginAppState

// modifier la construction de l'orbit cam ligne 93-96
OrbitCameraAppState orbitCam = new OrbitCameraAppState(
    applicationContext,
    () -> Vector3f.ZERO,
    () -> wizardState.isWizardVisible() || cameraTransition.isActive());
```

---

## 6. Fichiers à créer / modifier

**Nouveaux** :

- `src/main/java/com/smousseur/orbitlab/engine/CameraTransitionConfig.java`
- `src/main/java/com/smousseur/orbitlab/engine/Easing.java`
- `src/main/java/com/smousseur/orbitlab/app/view/TransitionTarget.java`
- `src/main/java/com/smousseur/orbitlab/states/camera/CameraTransition.java`
  (package-private)
- `src/main/java/com/smousseur/orbitlab/states/camera/CameraTransitionAppState.java`

**Modifiés** :

- `engine/EngineConfig.java` — ajouter le champ `cameraTransition`.
- `app/ApplicationContext.java` — getter/setter `cameraTransition`.
- `OrbitLabApplication.java` — instancier l'AppState, modifier le
  supplier `uiWantsMouse` de l'orbit cam.
- `states/camera/ViewModeAppState.java` — déléguer le reset à
  `cameraTransition.requestSolar()`.
- `states/scene/PlanetPoseAppState.java` — `onSelectPlanet()` délègue
  à `cameraTransition.requestPlanet(body)`.
- `states/mission/MissionRenderer.java` — `onSpacecraftSelected()`
  délègue à `cameraTransition.requestSpacecraft(mission, parent)`.

**Tests à ajouter** :

- `src/test/java/com/smousseur/orbitlab/states/camera/CameraTransitionTest.java`
  — tests unitaires pure-Java (sans JME) :
  - `advance` jusqu'à `durationSec` puis `isFinished()` true.
  - `easedProgress` à `t=0`, `t=duration/2`, `t=duration` avec
    `SMOOTHSTEP`.
  - Interpolation linéaire d'un pivot fixe → vérifier la valeur à
    mi-parcours.

Un test d'intégration JME complet n'est pas nécessaire (pas de calcul
mission affecté).

---

## 7. Plan de vérification

1. `./gradlew build` — compile + lance les tests existants et le nouveau
   `CameraTransitionTest`.
2. Lancer l'application (`./gradlew run` ou point d'entrée principal
   `OrbitLabApplication`) :
   - **Golden path SOLAR → PLANET** : démarrer en vue solaire, cliquer
     sur Terre. Vérifier que la caméra zoome de manière fluide (~2,5 s)
     vers la Terre, sans cut visuel à la fin.
   - **Golden path PLANET → SOLAR** (touche `R`) : depuis la vue Terre,
     presser `R`. Vérifier le dézoom fluide jusqu'à la vue système solaire.
   - **PLANET → PLANET** : depuis la vue Terre, cliquer sur Mars.
     Vérifier la trajectoire fluide entre les deux planètes (le pivot
     traverse l'espace héliocentrique).
   - **PLANET → SPACECRAFT** : avec une mission active, cliquer sur le
     spacecraft. Vérifier le zoom fluide.
   - **Blocage entrées** : pendant une transition, vérifier que :
     - drag souris RMB est ignoré ;
     - molette est ignorée ;
     - `R` est ignoré ;
     - clic planète/spacecraft est ignoré.
   - **Re-trigger ignoré** : pendant une transition, cliquer rapidement
     sur deux planètes différentes — la deuxième requête doit être
     ignorée (la première transition se termine normalement).
   - **Cible == courant** : double-cliquer sur la même planète — pas
     de transition fantôme.
   - **Horloge active** : pendant une transition vers la Terre, la
     position de la Terre continue d'évoluer ; le pivot interpolé suit
     la cible mouvante (vérifier en mode rapide : vitesse simu × 1000).
3. Vérifier visuellement qu'**aucun frame de cut** n'apparaît à la
   transition de mode (continuité au moment du switch §3.7). Si un
   sursaut est observé : l'ordre des AppStates est probablement
   inversé — vérifier que `CameraTransitionAppState` est attaché avant
   `FloatingOriginAppState` (§3.7).

---

## 8. Questions ouvertes / extensions futures (hors périmètre v1)

- Animation de l'orientation (yaw/pitch) : laissé pour une v2 si demandé.
- Easing par cible (différent pour zoom-in vs zoom-out) : config
  `CameraTransitionConfig` peut être étendue avec
  `Map<TargetKind, Easing>`.
- Skip de transition (touche pour passer en mode instantané, ex.
  `Shift+R`) : non prioritaire.
- Son/feedback haptique pendant la transition : hors périmètre.
