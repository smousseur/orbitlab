# Plan: Affichage du Spacecraft dans la NearView

## Context

OrbitLab n'a actuellement aucune visualisation de spacecraft. Les missions produisent un `SpacecraftState` via `Mission.update()` mais cette donnée n'est jamais rendue. L'objectif est d'afficher le spacecraft (position + trajectoire) dans la **nearView** en mode **PLANET uniquement**, en réutilisant le pattern MVC existant des planètes (LOD, icône billboard, modèle 3D GLTF).

## Architecture

### Principe: Réutiliser `PlanetView` comme interface générique

L'interface `PlanetView` est déjà suffisamment abstraite (`spatial()`, `setPositionWorld()`, `updateScreen()`, etc.). Le spacecraft l'implémentera directement, comme le fait `PlanetLodView`.

### Fichiers à créer

#### 1. `engine/scene/spacecraft/SpacecraftDescriptor.java` (record)
- Équivalent de `PlanetDescriptor` pour le spacecraft
- Champs: `String name`, `ColorRGBA color`, `double radiusMeters` (ex: 10m pour la sphère)

#### 2. `engine/scene/spacecraft/SpacecraftLodView.java` (implements PlanetView)
- Équivalent de `PlanetLodView` pour le spacecraft
- Combine un `Spacecraft3dView` + un `SpacecraftIconView` (billboard)
- Anchor node attaché au **nearBodiesNode** (pas farBodiesNode)
- LOD switching: distance caméra < rayon × multiplier → modèle 3D, sinon → icône
- Pour l'icône: réutiliser le même pattern que `PlanetIconView` (Lemur Container + dot texture)

#### 3. `engine/scene/spacecraft/Spacecraft3dView.java`
- Même pattern que `Planet3dView`: charge un modèle GLTF depuis `models/spacecraft/{name}/{name}.gltf`
- Le fichier GLTF sera fourni par l'utilisateur dans les ressources
- Chargement asynchrone via `AssetFactory.get().loadModel()` + `CompletableFuture`
- Échelle: rayon en unités de rendu Planet (`rayon_m × RenderContext.planet(body).unitsPerMeter()`)
- Callback `onModelLoaded()` pour attacher sur le JME render thread

#### 4. `engine/scene/spacecraft/SpacecraftIconView.java`
- Pattern identique à `PlanetIconView`
- Pas de click pour navigation (pas de `FocusView.viewPlanet()`)
- Billboard avec nom + dot coloré

#### 5. `engine/scene/spacecraft/SpacecraftPresenter.java` (record)
- Champs: `String id`, `PlanetView view`
- Méthode `updatePose(Vector3D positionPlanetRelativeIcrf)`:
  - Reçoit la position **déjà en coordonnées planétocentriques ICRF** (mètres)
  - Transforme en JME units via `RenderTransform.toRenderUnitsJmeAxes(pos, null, RenderContext.planet(body))`
  - Applique au view via `setPositionWorld()`

#### 6. `states/spacecraft/SpacecraftDisplayAppState.java` (extends BaseAppState)
- AppState principal qui orchestre tout
- **initialize():**
  - Crée `SpacecraftDescriptor`, `SpacecraftLodView`, `SpacecraftPresenter`
  - Attache l'anchor spatial au `nearBodiesNode` du SceneGraph
  - Attache l'icône au `guiGraph.getPlanetBillboardsNode()`
  - Enregistre le presenter dans `ApplicationContext`
- **update(float tpf):**
  - Vérifie que le mode est PLANET (sinon hide)
  - Appelle `mission.update(clock.now())` pour propager l'état
  - Récupère `mission.getCurrentState()` → `SpacecraftState`
  - Convertit la position de GCRF en ICRF héliocentric puis en planétocentric:
    1. `state.getPosition()` dans le frame du state (GCRF)
    2. Transformer en ICRF via `state.getFrame().getTransformTo(OrekitService.get().getIcrfFrame(), date)`
    3. Récupérer la position du corps focalisé en ICRF via `EphemerisService.trySampleHelioIcrf(body, t)`
    4. Position relative = `pos_spacecraft_ICRF - pos_body_ICRF`
  - Appelle `presenter.updatePose(posRelativeIcrf)`
  - Appelle `view.updateScreen(cam)` pour le LOD
  - Ajoute la position courante au buffer de trajectoire

#### 7. `states/spacecraft/SpacecraftTrajectoryAppState.java` (extends BaseAppState)
- Gère la trajectoire (ligne) dans `nearOrbitsNode`
- Maintient un buffer circulaire de positions planétocentriques
- Chaque N frames, ajoute la position courante du spacecraft
- Reconstruit le LineStrip mesh dans nearOrbitsNode avec les positions en Planet render units
- Utilise une `Geometry` avec `Mesh.Mode.LineStrip` et un material Unshaded coloré

### Fichiers à modifier

#### 8. `app/ApplicationContext.java`
- Ajouter un champ `SpacecraftPresenter spacecraftPresenter` (nullable/Optional)
- Ajouter `setSpacecraftPresenter()` / `getSpacecraftPresenter()`
- Ou: ajouter une `Mission activeMission` pour rendre la mission accessible

#### 9. `engine/scene/graph/SceneGraph.java`
- Ajouter `nearBodiesNode()` accessor (actuellement pas exposé)
- Ajouter `nearOrbitsNode()` accessor (actuellement pas exposé)

#### 10. `OrbitLabApplication.java`
- Enregistrer les nouveaux AppStates: `SpacecraftDisplayAppState`, `SpacecraftTrajectoryAppState`

## Flux de données par frame (mode PLANET)

```
SimulationClock.now()
  ↓
SpacecraftDisplayAppState.update()
  ├→ mission.update(clock.now())
  ├→ SpacecraftState state = mission.getCurrentState()
  ├→ Vector3D posIcrf = transformToIcrf(state)
  ├→ Vector3D posBodyIcrf = ephemerisService.trySampleHelioIcrf(focusBody, t)
  ├→ Vector3D posRelative = posIcrf - posBodyIcrf
  ├→ SpacecraftPresenter.updatePose(posRelative)
  │   └→ RenderTransform → view.setPositionWorld()
  ├→ view.updateScreen(cam)  // LOD switch
  └→ SpacecraftTrajectoryAppState.addPosition(posRelative)
        └→ Update LineStrip geometry in nearOrbitsNode
```

## Hiérarchie scene graph nearRoot (après modification)

```
nearRoot
  └─ nearFrame
       ├─ nearOrbitsNode
       │   └─ SpacecraftTrajectory (Geometry LineStrip)
       └─ nearBodiesNode
            └─ SpacecraftAnchor
                 └─ SpacecraftModel (GLTF)
```

## Vérification

1. **Compilation:** `./gradlew classes`
2. **Tests existants:** `./gradlew test` — vérifier qu'aucun test existant ne casse
3. **Vérification manuelle:** L'intégration visuelle nécessite l'application desktop avec les assets (non disponibles en CI), mais la structure du code peut être validée par compilation
4. **Point d'attention:** `FloatingOriginAppState` ne translate que `farFrame`, pas `nearFrame`. En mode PLANET, nearRoot est déjà dans le bon référentiel (planétocentric), donc pas besoin de floating origin — les positions sont directement relatives au corps focalisé et l'échelle est en km, pas en Gm
