# PHY-5 / L1 — La machinerie multi-objets, un débris inerte — conception

Lot L1 du découpage [`01-decoupage.md`](01-decoupage.md) §5, sur la baseline
[`02-baseline-L0.md`](02-baseline-L0.md). L1 monte la **machinerie multi-objets** de
bout en bout — N éphémérides, N vues — et la prouve avec **un seul débris**, inerte :
un objet largué au premier largage, propagé sous traînée jusqu'au plancher, dessiné.
Pas d'impulsion, pas de multiplicité, pas de maigrissement du primaire, pas
d'identité par débris — tout cela est à `L2`+.

Les décisions de conception sont prises en conversation (2026-09-15) ; ce document
les enregistre.

---

## 1. Objet du lot

Faire passer, de bout en bout, la colonne « un objet par mission » (baseline §2.1)
à « N objets » — mais avec **N = 2** (le primaire + un débris), pour isoler la
machinerie de tout le reste. Ce que L1 doit prouver :

- une **`List<DebrisTrack>`** portée par l'entrée et le résultat de calcul ;
- une **génération de débris** branchée au seul point de replay, hors CMA-ES ;
- un **renderer qui fanne** sur une liste de vues, l'objet primaire et le débris
  tirant chacun sa propre éphéméride ;
- la **borne compute** de D5 (plancher + horizon), mesurée affordable en L0.

`DT-17` (perf du ruban, N rubans) est mordu ici pour la première fois — un ruban de
plus par mission — et mesuré (§6).

---

## 2. Décisions de conception

### 2.1 C1 — Le modèle de donnée : `DebrisTrack`, porté par l'entrée et le résultat

- **`DebrisTrack`** = record `{ MissionEphemeris ephemeris, String modelPath,
  ColorRGBA color }`. Une trajectoire (réutilise `MissionEphemeris` tel quel) plus
  ce qu'il faut pour la dessiner. Le **label** par débris est différé à `L2`.
- **`MissionEntry`** gagne `volatile List<DebrisTrack> debris` (défaut : liste vide),
  posé à côté de `ephemeris`, avec `getDebris()` / `setDebris(List)`. Volatile pour
  la même raison que `ephemeris` : écrit par le thread d'optimisation, lu par le
  thread JME.
- **`MissionComputeResult`** gagne un composant `List<DebrisTrack> debris` (jamais
  null, vide par défaut). `MissionOptimizer` le remplit ; l'orchestrateur fait
  `entry.setDebris(result.debris())` exactement là où il fait déjà
  `entry.setEphemeris(result.ephemeris())`.
- **Rien n'est sauvé en scénario** : les débris sont recalculés au replay (découpage
  §3.1). Un `publish()`/recomposition d'entrée les vide comme le reste.

### 2.2 C2 — La capture du largage, dans le replay, hors CMA-ES

Le `Collector` de `MissionEphemerisGenerator` est déjà un `StageListener` qui voit
chaque `StageRun` dans l'ordre. On l'étend :

- il **retient le `finalState` de l'étage précédent** (l'état pré-largage : position,
  vitesse, masse) ;
- quand `onStageEnd` reçoit un `StageSeparationStage`, il **émet un `JettisonEvent`**
  `{ date, position, velocity }` de l'état pré-largage, plus la **masse larguée**
  (`prevMass − run.entryState().getMass()`) et l'**aéro larguée**
  (`mission.getVehicle().resolveActiveStage(prevMass).aerodynamics()` — l'étage actif
  à la masse pré-largage est précisément celui qu'on largue).

`StageSeparationStage` **reste inchangé** : la capture est un concern du chemin de
replay, lue de l'extérieur. Position et vitesse sont continues au largage (seule la
masse change), donc l'état pré-largage est le bon état initial du débris.

**Périmètre L1** : le `Collector` n'émet que **le premier** `JettisonEvent` (le
largage des boosters). La boucle sur tous les largages est `L2`.

### 2.3 C3 — Le `DebrisGenerator` : une propagation d'affichage par événement

Après le replay, pour chaque `JettisonEvent`, le `DebrisGenerator` :

1. construit le **`FlightContext` du débris** : la gravité de la mission (Terre) +
   une `DragContext(aéro larguée, atmosphère **de la mission**)`. Si la mission a
   volé **drag-off** (`AtmosphereModel.NONE`), pas de `DragContext` — le débris est
   balistique (un booster plonge quand même, un débris orbital est borné par
   l'horizon) ;
2. monte le propagateur par **`createOptimizationPropagator(context, COAST_MAX_STEP)`**
   — le propagateur de production (8×8 + perturbeurs + `DragForce`), **réutilisé** :
   L0 §5.2 a mesuré que le coût est bon (~28 ms/débris) et que la tolérance ne change
   presque rien, donc aucune fabrique neuve. Pas de poussée ;
3. arme les **deux bornes de D5** (re-cadré) : détecteur d'**altitude géodésique
   0 km** (`STOP`) et un **horizon temporel** (l'horizon de restitution de la
   mission) pour le débris en orbite haute qui ne rentre pas ;
4. **échantillonne** la propagation en un `MissionEphemeris` via un collecteur léger
   (un `OrekitStepHandler` à pas fixe qui produit des `MissionEphemerisPoint`, arc =
   la Terre), et emballe le tout en `DebrisTrack`.

L'**invariant** : ceci ne tourne qu'au replay (`MissionOptimizer` l. 327), jamais
dans la boucle CMA-ES. La passe d'optimisation et les quatre gates à tolérance zéro
sont intacts.

### 2.4 C4 — Le renderer fanne : extraction de `TrackedObjectView`

On extrait de `MissionRenderer` (400 lignes, deux responsabilités) un
**`TrackedObjectView`** : une `LodView` + un `MissionTrajectoryRenderer` + un
`updateFromPoint(point, trail, upTo, cam, tpf)`. `MissionRenderer` devient un
coordinateur :

- une **vue primaire**, qui garde les concerns de niveau mission (clic /
  `onSpacecraftSelected`, occulteur d'éclipse, échelle) ;
- une **`List<TrackedObjectView>` débris**, allégées : maillage fixe + ruban, ni
  clic, ni occulteur.

Par frame, l'orchestrateur pilote la mission comme aujourd'hui ; `MissionRenderer`
met à jour la vue primaire, puis **chaque vue débris depuis sa propre éphéméride** :
`debris.ephemeris().displayPointAt(now)` + `displayTrail()`. Un débris est **caché
tant que `now < son ephemeris.startDate`** — il n'existe pas avant sa séparation, et
réapparaît/disparaît par lui-même quand l'horloge est scrubbée, parce que c'est une
fonction de la date et non un événement. La **visibilité est partagée** avec la
mission (un débris suit `setVisible` du renderer). Le registre
`Map<MissionId, MissionRenderer>` est **inchangé** — les débris vivent dans la
mission, `MissionId` reste la clé unique de caméra / télémétrie / focus.

---

## 3. Le flux de bout en bout

```
optimize (winning mission)
   └─ MissionEphemerisGenerator.generate            [replay, hors CMA-ES]
        ├─ Collector → MissionEphemeris (primaire)
        └─ Collector → List<JettisonEvent>           (1 en L1 : boosters)
   └─ DebrisGenerator(events, mission)
        └─ par événement : propagateur drag + plancher/horizon
             → MissionEphemeris → DebrisTrack
   → MissionComputeResult { …, ephemeris, debris:List<DebrisTrack> }

orchestrateur : entry.setEphemeris(...) ; entry.setDebris(...)
   └─ MissionRenderer
        ├─ TrackedObjectView primaire   (depuis entry.ephemeris)
        └─ TrackedObjectView débris[i]  (depuis entry.debris[i].ephemeris)
```

---

## 4. Ce que L1 s'interdit *(→ lots suivants)*

- **La multiplicité M** : un largage de bloc reste un seul événement ; l'éclatement
  en M boosters est `L2`.
- **L'impulsion de séparation (D4)** : le débris hérite de l'état pré-largage sans
  Δv d'écartement ; `L2`.
- **Les largages au-delà du premier** : cœur, S2 ; `L2`.
- **Le maigrissement du primaire** (`after_boosters` / `after_s1` / charge utile) :
  `L3` / `L4`.
- **L'identité par débris** (label, couleur propre) : `L2`. En L1 le débris porte une
  couleur par défaut et le maillage constant `booster1`.
- **La sélection / le focus d'un débris** : hors périmètre du chantier (découpage §1).

---

## 5. L'invariant et la vérification

- **Optimize + quatre gates à tolérance zéro intacts** : les débris naissent au
  replay, `StageSeparationStage` est inchangé, aucun chemin CMA-ES n'est touché. `L1`
  relance `gateTest` et vérifie le vert — c'est là que l'invariant se prouve.
- **La borne compute** : L0 §5.2 l'a mesurée (≤ ~330 pas, ~28 ms/débris) ; L1 la
  consomme sans la re-mesurer.
- **`DT-17`** : un ruban de plus par mission. Mesurer le coût par frame d'un second
  `MissionTrajectoryRenderer` (allocation, écriture du buffer) ; traiter seulement
  s'il mord (sévérité mineure).

---

## 6. Détails d'implémentation à fixer *(au plan / à l'implémentation)*

- **Le maillage `booster1`** : dériver son chemin du dossier du lanceur
  (`…/heavy_falcon/heavy_falcon-booster1.gltf`) à partir de
  `LauncherAssets.modelPath(launcherId)`. La **table par pièce** générale est `L2`.
- **Le rayon dessiné** du débris : depuis la hauteur de la pièce au catalogue (`PHY-8`
  §3.8) si disponible, sinon un défaut ; il pilote l'échelle et le seuil LOD comme
  pour le primaire.
- **Le pas d'échantillonnage** du débris : un pas fixe modeste (le débris n'a pas de
  `MissionStage` pour advertise le sien) — assez fin pour une rentrée de ~160-360 s,
  assez grossier pour ne pas gonfler le ruban ; à régler.
- **La couleur par défaut** du débris en L1 (une teinte neutre) ; la couleur propre
  est `L2`.

---

## 7. Ce que L1 lègue à L2

La machinerie complète : `DebrisTrack`, `List` sur l'entrée et le résultat, le
`JettisonEvent` + `DebrisGenerator`, la vue par objet et le fan-out du renderer. `L2`
n'ajoute que du **contenu** dans ces cadres : boucler sur tous les largages, éclater
un bloc en M, appliquer l'impulsion, câbler la table de maillage par pièce, donner à
chaque débris couleur et label. Le delta y est de la donnée, pas de la structure.
