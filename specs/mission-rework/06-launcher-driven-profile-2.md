# Spec technique — Profil de mission piloté par le lanceur (launcher-driven profile)

> Périmètre : ce document est **technique et implémentable**. Il généralise le couple
> profil de mission ↔ lanceur/charge utile pour obtenir des missions réalistes, en
> déclinant les recommandations fonctionnelles de [04-mission-realism.md](04-mission-realism.md).
> Hypothèse conservée : **l'atmosphère est négligée**.
>
> État de départ : le working tree contient déjà un début d'implémentation du lot L1
> (`propellantLoad` sur `Vehicle`/`LaunchVehicle`/`Spacecraft`/`VehicleStack`) et un embryon
> de L2 (`Launchers.FalconHeavy(...)`). Cette spec part de cet état, pas de `main`.

---

## 1. Objectif

Trois généralisations, dans l'ordre de dépendance :

1. **Lanceur** : remplacer les constantes globales (`LaunchVehicle.getLauncherStage1Vehicle()`)
   par un **catalogue de modèles de lanceurs nommés** portant à la fois les caractéristiques
   massiques/propulsives des étages **et les paramètres de profil de vol** (durée d'ascension
   verticale, pitch kick, coast inter-étage). C'est le cœur du concept *launcher-driven profile* :
   le profil découle du lanceur, pas de la mission.
2. **Charge utile** : catalogue de charges utiles paramétrables (masse sèche saisie au wizard,
   moteur d'apogée AKM pour les satellites GEO), câblé de bout en bout depuis `StepLauncher`
   jusqu'à la construction de la mission (aujourd'hui `LAUNCHER_TYPE`, `PAYLOAD_TYPE` et
   `PAYLOAD_MASS` sont collectés puis **ignorés** par `MissionWizardAppState.createMission()`,
   [MissionWizardAppState.java:64-94](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionWizardAppState.java)).
3. **Profil de mission** : les listes de stages de `LEOMission`/`GEOMission` deviennent
   dérivées du modèle de lanceur + type de mission ; la charge propergol est dimensionnée par
   mission (heuristique Tsiolkovsky inverse), le MECO devient piloté par l'épuisement
   (`MassDepletionDetector`), et le profil GEO est refondu (l'étage 2 s'arrête après
   l'injection GTO, le spacecraft circularise avec son AKM).

---

## 2. État de départ et défauts à corriger immédiatement

### 2.1 Ce qui est déjà fait (working tree, non commité)

| Élément | Fichier | État |
|---|---|---|
| `propellantLoad` sur l'interface `Vehicle`, `getMass() = dry + load` | `vehicle/Vehicle.java` | ✅ fait |
| `LaunchVehicle`/`Spacecraft` : champ `propellantLoad` + garde `load ≤ capacity` + ctor de compat `load = capacity` | `vehicle/LaunchVehicle.java`, `vehicle/Spacecraft.java` | ✅ fait |
| `VehicleStack.propellantLoad()`, `ActiveStageInfo.propellantLoad()` | `vehicle/VehicleStack.java`, `vehicle/ActiveStageInfo.java` | ✅ fait |
| Ctor `LEOMission(String, Vehicle, …)` | `operation/LEOMission.java` | ✅ fait |
| `Launchers.FalconHeavy(...)` (agrégé 2 étages, ISP 311/348, poussée 22.8 MN/981 kN) | `vehicle/Launchers.java` | ⚠️ embryon, sémantique à corriger (§2.2) |
| Correction incohérence `VehicleTest` (dry stage 2 = 5 000 kg) | `test/.../VehicleTest.java` | ✅ fait |
| Test `testFalconHeavy` (LEO 400 km) | `test/.../LEOMissionOptimizationTest.java` | ⚠️ passe des *loads* dans le paramètre *capacity* (§2.2) |

### 2.2 Défauts bloquants introduits ou révélés par L1 (à corriger en I0)

**D1 — Durées de burn calculées sur la capacité, pas sur la charge.** Deux sites brûlent du
propergol potentiellement inexistant dès que `load < capacity` :

- [GravityTurnManeuver.java:90](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java) (`decode()`)
  et [GravityTurnManeuver.java:215](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java) (`getBurn1Duration()`) :
  `burn1Duration = (activeStage.propellantCapacity() − usedAscensionPropellant) / massFlow1`.
- [ConstantThrustStage.java:48](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/ascent/ConstantThrustStage.java) (`enter()`, cas `duration == 0`) :
  `duration = activeStage.propellantCapacity() / mDot`.

Conséquence : `ConstantThrustManeuver` d'Orekit intègre le débit massique sans plancher — la
masse de l'état descendrait **sous** la dry mass de l'étage, et `resolveActiveStage()` basculerait
d'étage trop tôt dans les stages suivants. Correctif : utiliser
`ActiveStageInfo.remainingFuel(currentMass)` (déjà correct puisque basé sur la masse réelle de
l'état) à la place de `propellantCapacity()`. Voir §5, incrément I0.

**D2 — `Launchers.FalconHeavy(double stage1Propellant, …)` confond capacité et charge.**
La surcharge actuelle passe les quantités dans le paramètre `propellantCapacity` du ctor à
3 arguments (donc `load = capacity` avec un réservoir *rétréci*). Le paramètre variable par
mission est la **charge**, la capacité est fixée par la conception du lanceur (1 233 t / 107 t).
Le test `testFalconHeavy(600_000, 50_000, …)` crée donc aujourd'hui un « petit Falcon Heavy »
au lieu d'un Falcon Heavy partiellement rempli — même masse au décollage, mais les bornes de
l'optimiseur ([TransferProblem.java:260-263](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferProblem.java))
et la future boucle externe (§4.4) dépendent de la distinction.

**D3 — Le wizard n'injecte ni lanceur ni payload.** `StepLauncher` produit
`LAUNCHER_TYPE`/`PAYLOAD_TYPE`/`PAYLOAD_MASS`, `createMission()` n'en lit aucun ; le
catalogue de payloads est une liste locale au widget ([StepLauncher.java:40-43](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/StepLauncher.java)).

---

## 3. Étude des solutions proposées

Cette section évalue les options ouvertes par 04-mission-realism et tranche pour chacune.

### S1 — Granularité du modèle Falcon Heavy : agrégé vs étage 0

Décision de 04 §3.1 confirmée : **étage 1 agrégé** (3 cœurs fusionnés : dry 66 t, capacité
1 233 t, poussée 22.8 MN) pour toutes les itérations de cette spec. Le jettison des side
boosters à mi-burn (étage 0) exige que `VehicleStack.resolveActiveStage()` supporte le
*staging parallèle* (deux vehicles consommés simultanément puis largués séparément), ce qui
casse l'invariant « la masse détermine l'étage actif de façon monotone »
([VehicleStack.java:47-69](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/VehicleStack.java)).
On le repousse à l'itération Ariane 5 (I8), qui l'impose de toute façon pour les EAP.
Le champ `PropellantType` (§4.1) est en revanche introduit dès maintenant : coût quasi nul,
et il fige le contrat « étage solide ⇒ `load == capacity`, pas d'extinction » avant que la
boucle externe (I7) n'existe.

Point de calibration : le working tree utilise ISP 311 s (vide) pour l'étage 1 agrégé ;
04 §4.1 recommande 282 s (sol) ou 296 s (moyenne). Sans atmosphère ni variation de poussée
avec l'altitude, l'ISP moyen **296 s** est le meilleur proxy des pertes réelles — à ajuster
en I1 si le test LEO 400 km ne converge pas (l'ISP 311 s sous-estime le propergol consommé
d'environ 5 %).

### S2 — Où faire vivre les paramètres de profil de vol

Trois options examinées pour `verticalAscentDuration`, `pitchKickAngle`, `interstageCoast` :

| Option | Pour | Contre |
|---|---|---|
| (a) Constantes de mission (statu quo, `ASCENSION_DURATION = 10` dans [LEOMission.java:39](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/LEOMission.java)) | zéro travail | contredit l'objectif : comparer des lanceurs exige des profils par lanceur |
| (b) Variables d'optimisation CMA-ES | « optimal » | dimension inutile : ces valeurs sont des contraintes opérationnelles (dégagement de tour), pas des degrés de liberté ; 04 §4.3 les exclut explicitement |
| (c) **Record `AscentProfile` porté par le modèle de lanceur** | profil = donnée du lanceur, testable, extensible | néant |

**Retenu : (c).** En corollaire, le couplage `GravityTurnStage(ascensionDuration=10)` →
`usedAscensionPropellant` calculé par durée
([GravityTurnManeuver.java:61](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java))
disparaît : le manœuvre lit la **masse réelle de l'état d'entrée** (qui reflète déjà le
propergol consommé pendant l'ascension verticale, puisque `VerticalAscentStage` propage un
vrai burn). C'est à la fois plus simple et plus juste — l'écart E1 de 04 est résolu par
suppression du paramètre, pas par sa paramétrisation.

### S3 — Condition d'arrêt des burns : durée Tsiolkovsky vs `MassDepletionDetector`

Contrainte Orekit : `ConstantThrustManeuver` s'active sur une **fenêtre de dates** fixe ; le
détecteur de masse ne coupe pas le moteur, il permet seulement de déclencher un événement
(STOP ou RESET_STATE) quand `m(t)` atteint un plancher. Deux stratégies compatibles :

- **(a) Garde-fou** : conserver la durée déterministe (`remainingFuel / massFlow` ou
  Tsiolkovsky [Physics.java:46](../../src/main/java/com/smousseur/orbitlab/simulation/Physics.java)) comme fenêtre du maneuver, et ajouter le détecteur
  avec plancher `depletionFloor = dryMass(étage actif) + massAbove` en **assertion physique**
  (si le détecteur se déclenche avant la fin de fenêtre, c'est un bug de comptabilité massique).
- **(b) Primaire (flame-out)** : fenêtre de burn volontairement majorée (durée d'épuisement
  + marge 5 %), le détecteur déclenche MECO/jettison/transition à `g(m) = 0`. C'est la
  sémantique réelle (extinction par épuisement), et c'est ce qui permet à la boucle externe
  (S5) d'explorer des charges variables sans recalculer les fenêtres.

**Retenu : (a) puis (b), dans cet ordre, par incréments séparés** (I4). (a) valide le plancher
massique sans changer une seule trajectoire (test de régression parfait) ; (b) bascule ensuite
la sémantique stage par stage : d'abord le MECO étage 1 du gravity turn (remplace le
`DateDetector` de jettison à [GravityTurnManeuver.java:142-158](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java)),
ensuite les stages de transfert via une variante « burn until depletion ». Les coasts restent
sur `DateDetector`/`NodeDetector`/`ApsideDetector`.

### S4 — Jettison : implicite (résolution par masse) vs stage explicite

Aujourd'hui le jettison est implicite : un `RESET_STATE` enfoui dans `GravityTurnManeuver`,
et `resolveActiveStage(mass)` fait le reste. Ça fonctionne pour l'ascension, mais la refonte
GEO (E5) exige une séparation **entre deux stages de mission** (étage 2 largué après
l'injection GTO, avant le coast de 5 h vers l'apogée). Options :

- (a) Continuer en implicite : faire porter le RESET_STATE par le dernier burn de l'étage 2.
  Fragile — le stage suivant (coast spacecraft) devrait « savoir » que la masse a changé.
- (b) **Stage explicite `StageSeparationStage`** : `enter()` retourne
  `state.withMass(info.massAfterJettison())`, `configure()` programme un coast court
  (durée = `interstageCoastDuration` du profil). Lisible dans la liste de stages, hookable
  par le renderer (largage visible), testable unitairement.

**Retenu : (b)** pour les séparations inter-stages (GEO I5, coast inter-étage E2 en I4).
Le jettison intra-gravity-turn (burn1 → burn2 dans une seule propagation) reste implicite :
le découper casserait l'optimisation CMA-ES du gravity turn qui propage les deux burns d'un
seul tenant ([GravityTurnManeuver.java:175-194](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java)).

### S5 — Dimensionnement du propergol : heuristique, puis boucle externe

**Initialisation (I3)** : Tsiolkovsky inverse descendant (du haut de la pile vers le bas),
conformément à 04 §4.2. Pour un budget ΔV par phase `ΔV_i` attribué à l'étage `i` :

```
m_prop_i = m_above_i · (exp(ΔV_i / (Isp_i · g0)) − 1) · (1 + marge)     marge = 10 %
load_i   = min(m_prop_i, capacity_i)
```

où `m_above_i` = masse totale au-dessus de l'étage `i` (chargée, calculée de haut en bas).
Budgets ΔV nominaux (constantes nommées, calibrables par test) :

| Phase | ΔV nominal | Étage |
|---|---|---|
| Ascension → LEO (h) | `√(μ/r) + PERTES_ASCENSION − 465·cos(lat)` avec `PERTES_ASCENSION = 1 600 m/s` | S1 + S2 (part S2 = résidu après S1 plein) |
| LEO → GTO (injection périgée) | `√(μ/r_LEO)·(√(2·r_GEO/(r_LEO+r_GEO)) − 1)` ≈ 2 440 m/s | S2 |
| GTO → GEO (circ + plan, apogée) | vis-viva + inclinaison combinées ≈ 1 500–1 800 m/s selon latitude | AKM spacecraft |

En v1, **l'étage 1 est chargé plein** (`load = capacity`) : le gravity turn le consomme
intégralement de toute façon (MECO par épuisement), et dimensionner S1 sans modèle de pertes
fin serait de la fausse précision. Seuls S2 et l'AKM sont dimensionnés par la formule.

**Boucle externe (I7)** : 04 §4.5 propose CMA-ES imbriqué. Étude :

| Approche | Dimension | Coût (évals internes) | Remarques |
|---|---|---|---|
| CMA-ES externe sur `load_i` | 2–3 | ~10 pop × ~15 gén = 150 optimisations internes | général, gère le couplage inter-étages |
| **Recherche scalaire monotone** : bisection sur un facteur global `λ` appliqué aux loads heuristiques (`load_i = λ · load_i^heuristique`, clampé) | 1 | ~8–12 optimisations internes | exploite la monotonie de `succès(λ)` ; ne peut pas ré-arbitrer entre étages |
| Bisection par étage, séquentielle du haut vers le bas | 1 × n | ~10·n | quasi-optimal si le couplage est faible (il l'est : l'AKM ne dépend que de sa phase) |

**Retenu : bisection scalaire en v1 de I7, CMA-ES externe en v2 seulement si le résidu de
propergol après bisection reste > 5 % de la capacité sur un étage.** Rationale : la boucle
interne coûte déjà ~40 000 évaluations CMA-ES par stage optimisable ; diviser par 10 le
nombre d'appels externes vaut plus que l'optimalité fine de la répartition. Les étages
`SOLID` sont exclus de la variable (load figé = capacity) dans les deux variantes. Le
warm-start de la boucle interne (seed = meilleur résultat de l'évaluation externe précédente)
reste applicable aux deux via le paramètre `seed`/`initialGuess` existant de
`CMAESTrajectoryOptimizer`.

### S6 — Signature des missions : `Vehicle` brut vs configuration de lancement

Le ctor `LEOMission(String, Vehicle, …)` du working tree ne suffit pas : `buildStages()` a
besoin des paramètres de profil du lanceur (S2), que `Vehicle` ne porte pas. Plutôt que
d'enrichir `Vehicle` (qui est une abstraction massique/propulsive, pas opérationnelle), on
introduit un record d'assemblage :

```java
public record LaunchConfiguration(
    LauncherModel launcher, double[] propellantLoads, Spacecraft payload) {
  public VehicleStack toVehicleStack() { … }          // valide loads ≤ capacities, SOLID ⇒ load = capacity
  public AscentProfile ascentProfile() { return launcher.ascentProfile(); }
}
```

Les missions prennent `LaunchConfiguration` ; le ctor `Vehicle` du working tree est conservé
comme voie basse (tests unitaires, profil par défaut) mais documenté comme tel.

---

## 4. Architecture cible

### 4.1 Couche vehicle — nouveaux types

Package `simulation/mission/vehicle/`. Tous des records immuables (conventions CLAUDE.md).

```java
/** Nature du propergol ; conditionne l'extinction et la variabilité de la charge. */
public enum PropellantType {
  LIQUID,   // éteignable, load variable ∈ [0, capacity]
  SOLID     // non éteignable, load == capacity imposé
}

/** Description statique d'un étage d'un modèle de lanceur (conception, pas instance). */
public record StageModel(
    String name,                     // "S1 (3 cores aggregated)"
    double dryMass,                  // kg
    double propellantCapacity,       // kg — taille du réservoir, fixe
    PropulsionSystem propulsion,
    PropellantType propellantType) {}

/** Paramètres de profil de vol imposés par le lanceur (launcher-driven profile). */
public record AscentProfile(
    double verticalAscentDuration,   // s — FH ≈ 7, A5 ≈ 6
    double pitchKickAngleDeg,        // ° — remplace le 3.0 en dur des missions
    double interstageCoastDuration)  // s — 1–3 s entre MECO et allumage étage suivant
    {}

/** Modèle de lanceur nommé : étages + profil. Source unique de vérité du catalogue. */
public record LauncherModel(
    String id,                       // "FALCON_HEAVY" — clé wizard (FormField.LAUNCHER_TYPE)
    String displayName,              // "Falcon Heavy"
    List<StageModel> stages,         // bottom → top
    AscentProfile ascentProfile) {

  /** Instancie la pile avec des charges par étage (kg, même ordre que stages). */
  public VehicleStack instantiate(double[] propellantLoads, Spacecraft payload) { … }

  /** Charges = capacités (comportement historique). */
  public VehicleStack instantiateFullyLoaded(Spacecraft payload) { … }
}
```

`Launchers` (existant) est refondu en catalogue de constantes + résolution par id :

```java
public final class Launchers {
  public static final LauncherModel FALCON_HEAVY =
      new LauncherModel(
          "FALCON_HEAVY", "Falcon Heavy",
          List.of(
              new StageModel("S1 (aggregated)", 66_000, 1_233_000,
                  new PropulsionSystem(296, 22_800_000), PropellantType.LIQUID),
              new StageModel("S2", 4_000, 107_500,
                  new PropulsionSystem(348, 981_000), PropellantType.LIQUID)),
          new AscentProfile(7.0, 3.0, 2.0));

  // I8 : ARIANE_5_ECA (EAP SOLID + EPC + ESC-A) — hors périmètre de cette spec, voir §5 I8.

  public static LauncherModel byId(String id) { … }   // IllegalArgumentException si inconnu
  public static List<LauncherModel> all() { … }       // pour le wizard
}
```

`LaunchVehicle` gagne un champ `name` (diagnostics/logs par étage) mais reste le type
d'instance runtime — `StageModel.toVehicle(double load)` produit un `LaunchVehicle`.

### 4.2 Catalogue de charges utiles

Nouveau `Payloads` (même package), qui remplace la liste locale de
[StepLauncher.java:40-43](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/StepLauncher.java) et
le `Spacecraft.getSpacecraft()` (150 kg dry / 0 kg propergol / 3 kN inutilisable,
[Spacecraft.java:29-31](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/Spacecraft.java)) :

```java
public record PayloadModel(
    String id, String displayName,
    double defaultDryMass,           // pré-remplit le champ masse du wizard
    double akmPropellantCapacity,    // 0 pour un payload inerte
    PropulsionSystem akmPropulsion)  // null si inerte
{
  /** Instancie avec la masse sèche saisie au wizard et une charge AKM (0 par défaut). */
  public Spacecraft toSpacecraft(double dryMass, double akmLoad) { … }
}

public final class Payloads {
  public static final PayloadModel CARGO_MODULE =
      new PayloadModel("CARGO_MODULE", "Cargo module", 15_000, 0, null);
  public static final PayloadModel EARTH_OBSERVATION_SAT =
      new PayloadModel("EARTH_OBS_SAT", "Earth observation satellite", 10_000, 0, null);
  public static final PayloadModel GEO_SAT =
      new PayloadModel("GEO_SAT", "GEO communications satellite",
          2_000, 2_000, new PropulsionSystem(320, 400));   // AKM bipergol 400 N
  public static PayloadModel byId(String id) { … }
  public static List<PayloadModel> all() { … }
}
```

Ordre de grandeur AKM validant la capacité : pour dry 2 000 kg et ΔV apogée 1 800 m/s à
ISP 320 s, `m_prop = 2000·(e^{1800/3138} − 1) ≈ 1 550 kg` → capacité 2 000 kg = marge ~30 %.

### 4.3 Dimensionnement du propergol

Nouveau `simulation/mission/vehicle/PropellantBudget.java` (utilitaire statique, formules §S5) :

```java
public final class PropellantBudget {
  public static final double SAFETY_MARGIN = 0.10;
  public static final double ASCENT_LOSSES_MS = 1_600.0;  // pertes gravité+pilotage, calibrable

  /** Charges par étage pour une mission LEO circulaire à targetAltitude. */
  public static double[] loadsForLeo(LauncherModel launcher, Spacecraft payload,
      double targetAltitude, double launchLatitudeDeg) { … }

  /** Charges lanceur + charge AKM pour une mission GEO (parking → GTO → GEO). */
  public static GeoLoads loadsForGeo(LauncherModel launcher, PayloadModel payload,
      double payloadDryMass, double parkingAltitude, double launchLatitudeDeg) { … }

  public record GeoLoads(double[] launcherLoads, double akmLoad) {}
}
```

Contrat testable : `loadsForLeo(FALCON_HEAVY, sat 10 t, 400 km)` doit donner
`load_S2 < 0.5 × capacity_S2` ; `loadsForGeo(…)` doit donner `load_S2` proche de la capacité
(critères de fin L3 de 04 §5).

### 4.4 Généralisation du profil de mission

`LEOMission`/`GEOMission` conservent leur rôle (assemblage du profil propre au type de
mission) mais leurs `buildStages()` deviennent paramétrés par `AscentProfile` :

```java
// LEOMission — ctor principal cible
public LEOMission(String name, LaunchConfiguration config,
    double perigeeAltitude, double apogeeAltitude,
    double latitude, double longitude, double altitude) {
  super(name, config.toVehicleStack(),
      buildStages(config.ascentProfile(), perigeeAltitude, apogeeAltitude, latitude),
      buildObjective(perigeeAltitude, apogeeAltitude, latitude));
  …
}

private static List<MissionStage> buildStages(AscentProfile profile,
    double perigeeAltitude, double apogeeAltitude, double latitude) {
  return List.of(
      new VerticalAscentStage("Vertical Ascent", profile.verticalAscentDuration()),
      new GravityTurnStage("Gravity turn",
          profile,                                   // remplace (ascensionDuration, pitchKick) — voir I1
          GravityTurnConstraints.forTarget(perigeeAltitude)),
      new AnalyticHohmannTransferStage("Transfert", perigeeAltitude, apogeeAltitude,
          FastMath.toRadians(latitude)),
      new AnalyticTrimBurnStage("Trim", perigeeAltitude, FastMath.toRadians(latitude)),
      new CoastingStage("Coasting", null));
}
```

Les ctors historiques (`LEOMission(String, double, …)` sans vehicle) restent et délèguent à
`FALCON_HEAVY` chargé plein + payload par défaut : **aucun call-site existant ne casse**.

**Refactor `GravityTurnManeuver` (clé de voûte de I1)** — signature cible :

```java
public GravityTurnManeuver(Vehicle vehicle, SpacecraftState entryState,
    double pitchKickAngleRad, double launchAzimuth, double interstageCoastDuration) {
  this.activeStage = vehicle.resolveActiveStage(entryState.getMass());   // masse RÉELLE
  this.nextStage   = vehicle.resolveActiveStage(activeStage.massAfterJettison());
  this.entryMass   = entryState.getMass();
  // usedAscensionPropellant : SUPPRIMÉ — déjà reflété dans entryMass
}

public double getBurn1Duration() {
  return activeStage.remainingFuel(entryMass) / massFlow1;   // load-aware par construction
}
```

et dans `configure()` : `burn2Start = jettisonDate + interstageCoastDuration` (E2 — le coast
inter-étage vit dans la même propagation que les deux burns, pas de nouveau stage ici).
`GravityTurnStage.createManeuver(mission)` devient `createManeuver(mission, entryState)` —
appelants : `buildProblem()` (state = `mission.getCurrentState()`), `enter()`
(state = `previousState`), `configure()` (state = `mission.getCurrentState()`).

**Refonte GEO (I5)** — liste de stages cible :

```
1. VerticalAscentStage(profile.verticalAscentDuration)
2. GravityTurnStage(profile, …)                          — S1 épuisé, S2 partiel
3. AnalyticParkingInsertionStage(parking)                — S2
4. CoastingStage("Coasting parking", stopAtNode = true)
5. AnalyticGtoInjectionStage(GEO_ALTITUDE)               — S2, burn périgée seul   [NOUVEAU]
6. StageSeparationStage("S2 separation")                 — jettison S2             [NOUVEAU]
7. CoastingStage("GTO coast", stopAtApogee = true)       — spacecraft seul, ~5 h   [variante NOUVELLE]
8. AnalyticApogeeCircularizationStage(GEO, inclination)  — AKM : circ + plan       [NOUVEAU]
9. AnalyticTrimBurnStage("Trim", GEO, inclination)
10. CoastingStage("Coasting", null)
```

Les stages 5 et 8 sont extraits de `AnalyticHohmannTransferStage` (qui fait aujourd'hui
burn1 + coast + burn2 d'un seul tenant, [AnalyticHohmannTransferStage.java:139-246](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/AnalyticHohmannTransferStage.java)) :

- `AnalyticGtoInjectionStage` = l'itération de Newton sur `r2Aim` + burn 1 (lignes 156-196),
  fin de stage sur `DateDetector(burnEnd)`.
- `AnalyticApogeeCircularizationStage` = détection d'apogée + `computeTargetVelocityAtApogee()`
  (déjà package-private réutilisable, ligne 305) + burn 2 en attitude inertielle fixe.
- `CoastingStage` gagne un mode `stopAtApogee` (troisième déclencheur à côté de `maxTime` et
  `stopAtNode`, via `ApsideDetector` filtré `!increasing` — même patron que
  [CoastingStage.java:40-60](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/CoastingStage.java)).
- `StageSeparationStage` (§S4) :

```java
public class StageSeparationStage extends MissionStage {
  @Override public SpacecraftState enter(SpacecraftState prev, Mission mission) {
    ActiveStageInfo info = mission.getVehicle().resolveActiveStage(prev.getMass());
    return prev.withMass(info.massAfterJettison());
  }
  @Override public void configure(NumericalPropagator p, Mission mission) {
    // coast de separationDuration puis transition (DateDetector, patron CoastingStage)
  }
}
```

`AnalyticHohmannTransferStage` **reste inchangé** pour le profil LEO (les deux burns y sont
sur le même étage physique — pas de séparation à modéliser).

### 4.5 Câblage `MassDepletionDetector`

Helper sur `ActiveStageInfo` :

```java
/** Masse plancher : tout le propergol de l'étage actif consommé. */
public double depletionFloor() { return stage.dryMass() + massAbove; }
```

Mode garde-fou (I4a) — dans `GravityTurnManeuver.configure()` et les stages analytiques :

```java
propagator.addEventDetector(
    new MassDepletionDetector(info.depletionFloor())
        .withHandler((s, d, inc) -> {
          logger.error("Propellant depleted before scheduled cutoff at {}", s.getDate());
          return Action.STOP;   // fail fast : la comptabilité massique est fausse
        }));
```

Mode primaire (I4b) — MECO étage 1 du gravity turn : la fenêtre de burn1 passe à
`durée_épuisement × 1.05`, le `DateDetector` de jettison
([GravityTurnManeuver.java:142](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java))
est remplacé par le `MassDepletionDetector` avec le même handler `RESET_STATE →
withMass(massAfterJettison)`, décalé de `interstageCoastDuration` pour l'allumage de burn2.
Variante transfert : flag `burnUntilDepletion` sur les stages analytiques — la durée du burn
devient `min(durée_Tsiolkovsky, remainingFuel/massFlow)` et le détecteur clôt le stage si
l'épuisement survient d'abord (mission sous-dotée → échec propre remonté à l'objectif,
pas une exception).

### 4.6 Câblage wizard → mission

`MissionWizardAppState.createMission()` ([MissionWizardAppState.java:64-94](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionWizardAppState.java)) :

```java
LauncherModel launcher = Launchers.byId(String.valueOf(values.get("LAUNCHER_TYPE")));
PayloadModel payloadModel = Payloads.byId(String.valueOf(values.get("PAYLOAD_TYPE")));
double payloadMass = Double.parseDouble(values.get("PAYLOAD_MASS").toString());

if (missionContext.getSelectedMissionType() == MissionType.LEO) {
  double[] loads = PropellantBudget.loadsForLeo(launcher,
      payloadModel.toSpacecraft(payloadMass, 0), apogeeAlt, latitude);
  LaunchConfiguration config = new LaunchConfiguration(launcher, loads,
      payloadModel.toSpacecraft(payloadMass, 0));
  mission = new LEOMission(name, config, perigeeAlt, apogeeAlt, latitude, longitude, altitude);
} else { /* GEO : loadsForGeo → akmLoad injecté dans toSpacecraft(payloadMass, akmLoad) */ }
```

Côté UI : `StepLauncher` itère `Launchers.all()` et `Payloads.all()` au lieu de ses listes
locales ; `PAYLOAD_TYPE` porte l'`id` (pas le `displayName`). `FormField` existant inchangé.

### 4.7 Métriques de performance (04 §6.3)

`MissionComputeResult` gagne un `MissionPerformanceReport` :

```java
public record StagePerformance(String stageName, double massIn, double massOut,
    double propellantConsumed, double deltaV) {}
public record MissionPerformanceReport(List<StagePerformance> stages,
    double totalDeltaV, double totalPropellantLoaded, double totalPropellantResidual) {}
```

Collecté dans `MissionOptimizer.optimize()` qui voit déjà passer les états d'entrée/sortie
de chaque stage ([MissionOptimizer.java:94-192](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java)) ;
`deltaV = Isp·g0·ln(massIn/massOut)` par stage propulsé. C'est l'instrument de validation
de L3/L7 (propergol résiduel) et l'exposition future au panneau mission.

---

## 5. Plan d'implémentation incrémental

Chaque incrément compile, passe `./gradlew test`, et livre de la valeur seul. La colonne
« Lot 04 » fait le lien avec la roadmap fonctionnelle de 04-mission-realism §5.

| # | Contenu | Lot 04 | Dépend de |
|---|---|---|---|
| I0 | Consolidation load vs capacity (corrige D1/D2) | L1 (fin) | — |
| I1 | `LauncherModel` + `AscentProfile` + refactor `GravityTurnManeuver` | L2 + E1 | I0 |
| I2 | Catalogue payloads + câblage wizard (corrige D3) | (L9 payload, avancé) | I1 |
| I3 | `PropellantBudget` : charges différenciées LEO/GEO + métriques | L3 | I2 |
| I4 | `MassDepletionDetector` garde-fou (a) puis primaire (b) + coast inter-étage | L4 + E2 | I1 |
| I5 | Refonte profil GEO (split lanceur/spacecraft, AKM) | L5 + E5/E6 | I2, I4 |
| I6 | Mode « burn until depletion » dans l'optimisation trajectoire | L6 + E3 | I4 |
| I7 | Boucle externe propergol (bisection, puis CMA-ES si besoin) | L7 | I3, I6 |
| I8 | (futur) Ariane 5 : étages SOLID, staging parallèle | L8 | I7 |

### I0 — Consolidation load vs capacity *(petit, à faire avant tout commit du working tree)*

- `GravityTurnManeuver` : `decode()` et `getBurn1Duration()` basculent de
  `propellantCapacity() − usedAscensionPropellant` vers `activeStage.remainingFuel(masse
  d'entrée)`. À ce stade, la masse d'entrée disponible sans refactor de signature est
  `vehicle.getMass() − usedAscensionPropellant` (le vrai refactor arrive en I1) — le point
  essentiel est que `getMass()` retourne désormais dry + **load**.
- `ConstantThrustStage.enter()` : `duration = activeStage.remainingFuel(previousState.getMass()) / mDot`.
- `Launchers.FalconHeavy(double stage1Load, double stage2Load, Spacecraft)` : construit
  `new LaunchVehicle(66_000, 1_233_000, stage1Load, …)` (capacité fixe, charge variable).
- Javadoc des records `LaunchVehicle`/`Spacecraft` : documenter le paramètre `propellantLoad`.
- **Tests** : `VehicleTest` — cas `load < capacity` (`getMass`, `remainingFuel`,
  `resolveActiveStage` avec pile partiellement chargée) ; `testFalconHeavy` inchangé mais
  vérifie maintenant la sémantique corrigée ; régression `testLEOMissionsSequential`
  (missions historiques : `load = capacity`, trajectoires bit-identiques, seed 42).
- **Critère de sortie** : `./gradlew test` vert ; un stage chargé à 50 % brûle exactement
  50 % du propergol.

### I1 — LauncherModel + AscentProfile + refactor GravityTurnManeuver

- Nouveaux fichiers : `PropellantType`, `StageModel`, `AscentProfile`, `LauncherModel`,
  `LaunchConfiguration` (§4.1, §S6). Refonte de `Launchers` en catalogue (constante
  `FALCON_HEAVY`, `byId`, `all`).
- Refactor `GravityTurnManeuver` (§4.4) : signature avec `entryState`, suppression de
  `usedAscensionPropellant` et du paramètre `ascensionDuration` ; ajout
  `interstageCoastDuration` (branché mais valeur 0.0 par défaut jusqu'à I4 pour isoler la
  régression). `GravityTurnStage` : ctor à base d'`AscentProfile`, `createManeuver(mission,
  state)`.
- `LEOMission`/`GEOMission` : ctor `LaunchConfiguration`, `buildStages(profile, …)`,
  suppression de `ASCENSION_DURATION` et du `3.0` en dur ; ctors historiques → délégation
  `FALCON_HEAVY` plein.
- Choix ISP étage 1 : passer de 311 s à 296 s (§S1) — si le test LEO 400 km échoue, garder
  311 s et ouvrir un point de calibration (les bornes de `GravityTurnProblem`
  [GravityTurnProblem.java:70-88](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/GravityTurnProblem.java)
  ont été réglées pour l'ancien lanceur ; TWR décollage FH ≈ 1.6 vs ≈ 1.9 aujourd'hui).
- **Tests** : unitaires `LauncherModel.instantiate` (validation loads, SOLID) ;
  `testFalconHeavy` réécrit sur `LaunchConfiguration` ; sweep LEO existant conservé sur le
  lanceur legacy via ctors historiques (qui pointent alors FH — adapter les tolérances si
  besoin, cf. Risques §6).
- **Critère de sortie** : optim LEO 400 km avec `FALCON_HEAVY` dans ±7 % ; plus aucune
  constante de profil dans les classes mission.

### I2 — Catalogue payloads + câblage wizard

- Nouveaux : `PayloadModel`, `Payloads` (§4.2). Suppression de `Spacecraft.getSpacecraft()`
  au profit de `Payloads.…toSpacecraft(…)` (les call-sites tests migrent).
- `StepLauncher` : cartes lanceur et liste payloads pilotées par les catalogues ; le champ
  masse pré-rempli par `defaultDryMass`.
- `MissionWizardAppState.createMission()` : câblage complet (§4.6). En attendant I3,
  `loads = capacities` (chargé plein).
- **Tests** : unitaire de résolution `Launchers.byId`/`Payloads.byId` ; test du chemin
  `createMission` si un harness UI existe, sinon extraction de la logique de construction
  dans une factory testable `MissionFactory.fromWizardValues(Map<String,Object>, MissionType)`
  (recommandé : ça sort la logique métier de l'AppState, conforme à la règle « pas de
  logique dans les states »).
- **Critère de sortie** : créer une mission LEO au wizard avec FH + sat 10 t produit une
  mission dont `getVehicle().getMass()` reflète le payload saisi.

### I3 — PropellantBudget + métriques

- Nouveau `PropellantBudget` (§4.3) ; `MissionFactory` l'appelle pour LEO et GEO.
- `MissionPerformanceReport` (§4.7) collecté dans `MissionOptimizer`, loggé en fin d'optim.
- **Tests** : unitaires des formules (cas analytiques connus : ΔV GTO ≈ 2 440 m/s depuis
  200 km) ; intégration : LEO 400 km avec loads heuristiques converge et
  `report.totalPropellantResidual / totalPropellantLoaded < 0.15` sur S2 ;
  `load_S2(LEO 400) < 0.5 · capacity` et `load_S2(GEO) > 0.9 · capacity` (critères L3).
- **Critère de sortie** : les deux assertions de charge différenciée passent.

### I4 — MassDepletionDetector + coast inter-étage

- (a) `ActiveStageInfo.depletionFloor()` ; détecteur garde-fou dans `GravityTurnManeuver`,
  `ConstantThrustStage`, stages analytiques (§4.5). Aucun changement de trajectoire attendu
  → test de régression strict (mêmes coûts d'optim à seed fixe).
- (b) MECO/jettison du gravity turn sur épuisement ; `interstageCoastDuration` activé
  (burn2 décalé). Les bornes du problème gravity turn (`transitionTime`) sont revalidées :
  le temps de jettison bouge de ~2 s.
- Stages analytiques : durée plafonnée par `remainingFuel/massFlow` (mission sous-dotée
  s'éteint proprement au lieu de brûler du propergol négatif).
- **Tests** : un stage volontairement sous-doté (load = 50 % du besoin) déclenche le
  détecteur à l'instant analytique attendu (±1 s) et la mission se termine en échec propre ;
  sweep LEO complet re-passe.
- **Critère de sortie** : critères L4 de 04 §5.

### I5 — Refonte profil GEO

- Nouveaux stages : `AnalyticGtoInjectionStage`, `StageSeparationStage`,
  `AnalyticApogeeCircularizationStage` ; mode `stopAtApogee` de `CoastingStage` (§4.4).
- `GEOMission.buildStages()` réécrit sur la séquence cible ; payload `GEO_SAT` avec AKM
  dimensionné par `PropellantBudget.loadsForGeo`.
- Vérifier `resolveActiveStage` sur la frontière S2 vide → spacecraft : après séparation,
  l'étage actif doit être le spacecraft avec `remainingFuel = akmLoad`.
- **Tests** : masse finale GEO ≈ dry spacecraft (± marge AKM résiduelle) ; inclinaison et
  rayon GEO dans les tolérances du test GEO existant ; le renderer affiche la séparation
  (vérification visuelle via `OrbitLabApplication`).
- **Critère de sortie** : critères L5 de 04 §5.

### I6 — Optimisation avec épuisement

- `TransferProblem`/`TransferTwoManeuverProblem` : mode où `dt1` est plafonné par
  l'épuisement effectif (borne dure = `remainingFuel/massFlow`, plus le ×0.90 arbitraire de
  [TransferProblem.java:263](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferProblem.java)) ;
  seed inchangé (résultat analytique).
- **Critère de sortie** : convergence LEO 400 km de qualité équivalente (±7 %), critères L6.

### I7 — Boucle externe propergol

- Nouveau `runtime/PropellantLoadOptimizer` : bisection sur `λ` (§S5), bornes
  `[λ_min = 0.3, 1.0]`, tolérance 2 % ; chaque évaluation = `MissionOptimizer.optimize()`
  complet sur une mission reconstruite avec `loads(λ)` ; succès = objectif dans tolérance.
  Étages `SOLID` et AKM hors λ (l'AKM garde son dimensionnement analytique, déjà serré).
- Budget : ≤ 10 évaluations externes ; warm-start du CMA-ES interne par les
  `bestVariables` de l'évaluation précédente.
- **Critère de sortie** : sur LEO 400 km, `Σ load(λ*) < Σ load_heuristique` et mission
  réussie ; critères L7. Si résidu > 5 % sur un étage : ouvrir la v2 CMA-ES externe
  (nouvelle spec courte).

### I8 — (futur, hors périmètre détaillé) Ariane 5

Pré-requis identifiés dès maintenant pour ne pas se peindre dans un coin :
`PropellantType.SOLID` (fait en I1), `StageModel` par étage nommé (fait), staging parallèle
dans `VehicleStack` (EAP largués pendant le burn EPC — nécessite de casser l'hypothèse
« un seul étage actif » de `resolveActiveStage`, probablement via un
`CompositeStage(List<StageModel>)` vu comme un seul étage à deux jettisons). À spécifier
dans un document dédié le moment venu.

---

## 6. Risques et points de calibration

| Risque | Impact | Mitigation |
|---|---|---|
| Bornes de `GravityTurnProblem` réglées pour l'ancien lanceur (TWR, `transitionTimeMax`, fenêtres FPA) — FH a un burn S1 de ~160 s et un TWR décollage plus faible | I1 : test LEO qui ne converge plus | Garder le sweep d'altitudes comme harnais ; ne toucher qu'un paramètre à la fois (ISP 296↔311 d'abord) ; les specs `optimizer/01-03` documentent la méthodologie de re-réglage |
| Le passage MECO date → épuisement (I4b) déplace le jettison de quelques secondes et perturbe le seed du problème gravity turn | I4 : coûts d'optim dégradés | Étape (a) garde-fou d'abord (zéro changement), bascule (b) mesurée au sweep complet, seed CMA-ES fixe pour comparer |
| `resolveActiveStage` avec S2 quasi vide au moment de la séparation GEO : la frontière `currentMass > massAbove[i]` est à ε près | I5 : mauvais étage actif → mauvaise propulsion AKM | Test unitaire dédié de la frontière ; `StageSeparationStage.enter()` force la masse exacte `massAfterJettison` |
| Boucle externe : coût CPU (chaque éval = optim complète ~minutes) | I7 : temps de test CI | Bisection (≤ 10 évals), warm-start, et test I7 marqué lent/nightly |
| Heuristique `ASCENT_LOSSES_MS = 1600` non calibrée pour ce modèle sans atmosphère | I3 : charges initiales trop justes → échecs internes | La marge 10 % absorbe ; sinon calibrer avec le `MissionPerformanceReport` (ΔV réel mesuré du gravity turn) |
| `Spacecraft.getSpacecraft()` utilisé par des tests existants | I2 : compilation tests | Migration mécanique vers `Payloads` dans le même incrément |

---

## 7. Questions ouvertes (reprises de 04 §7, avec position technique)

- **Tolérances d'orbite** : ±7 % conservé pour tous les incréments ; le durcissement est un
  réglage de `ORBIT_MARGIN_RATIO` dans `AbstractTrajectoryOptimizerTest`, à ne considérer
  qu'après I7 (il conditionne le λ* atteignable).
- **Propergol résiduel minimal** : à imposer en contrainte de I7 (succès ⇔ objectif atteint
  **et** résiduel ≥ 1 % de la charge par étage liquide). Trivial à ajouter dans le prédicat
  de bisection.
- **Catalogue spacecraft** : couvert par `Payloads` dès I2 ; le GEO lourd vs léger = deux
  entrées `PayloadModel` de plus, pas de refactor.
- **Nom du paramètre wizard `PAYLOAD_TYPE`** : il transporte aujourd'hui le `displayName` ;
  cette spec le fait transporter l'`id`. Vérifier qu'aucune persistance de mission
  n'enregistre l'ancienne valeur (a priori non : les valeurs wizard sont éphémères).
