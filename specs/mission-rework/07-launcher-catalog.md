# Spec technique — Catalogue de lanceurs typé par capacités

> Périmètre : ce document est **technique et implémentable**. Il implémente le point
> **2.2 de [05-launcher-driven-profile-conception.md](05-launcher-driven-profile-conception.md)**
> (« le lanceur = une pile d'étages + leurs capacités physiques ») : il étoffe la classe
> `Launchers` et le modèle Falcon Heavy en un **catalogue de modèles de lanceurs** dont
> chaque étage porte un **descripteur de capacités**. C'est un incrément **pur modèle de
> données** : aucune modification des missions, manœuvres, stages ou de l'optimiseur —
> **zéro changement de trajectoire attendu**.
>
> Position dans la roadmap : démarre l'incrément **I1** de
> [06-launcher-driven-profile-2.md](06-launcher-driven-profile-2.md) (dont il précise le
> §4.1) et couvre la partie « modèle » du lot **L2** de
> [04-mission-realism.md](04-mission-realism.md). Le câblage du profil dans les missions
> (refactor `GravityTurnManeuver`, suppression d'`ASCENSION_DURATION`) reste dans 06 I1.

---

## 1. Objectif

1. Introduire le **descripteur de capacités d'étage** (`StageCapabilities`) qui formalise
   la table du §2.2 de 05 : allumage, rallumage, mode d'extinction, nature d'ergols,
   durée de coast maximale, rôle. C'est la **donnée d'entrée du futur dérivateur de
   profil** (05 §2.4) — le dérivateur lui-même est hors périmètre.
2. Transformer `Launchers` d'une factory ad hoc
   ([Launchers.java](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/Launchers.java))
   en **catalogue de `LauncherModel` nommés**, interrogeable par id (préparation du
   câblage wizard, 06 I2).
3. Compléter le modèle **Falcon Heavy** : capacités par étage, `AscentProfile` (données
   seulement), capacité S2 corrigée.
4. Démontrer que le descripteur **exprime déjà Ariane 5 ECA et un AKM** sans refactor
   futur (réponse à la question ouverte « granularité » de 05 §7).

---

## 2. État de départ (commit `227c50d`)

### 2.1 Acquis

| Élément | Fichier | État |
|---|---|---|
| `propellantLoad` sur `Vehicle` (`getMass() = dry + load`), `LaunchVehicle`, `Spacecraft`, `VehicleStack` | [Vehicle.java](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/Vehicle.java), etc. | ✅ fait (L1) |
| D1 de 06 §2.2 (durées de burn sur la capacité) | [GravityTurnManeuver.java:90](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/GravityTurnManeuver.java), [ConstantThrustStage.java:49](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/ascent/ConstantThrustStage.java) | ✅ corrigé — les deux sites utilisent `remainingFuel(vehicle.getMass())` |
| D2 de 06 §2.2 (charge passée dans le paramètre capacité) | [Launchers.java:14-16](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/Launchers.java) | ✅ corrigé — ctor 4 args, capacités figées (1 233 t / 107 t) |
| Test d'intégration FH partiel | [LEOMissionOptimizationTest.java:34](../../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/LEOMissionOptimizationTest.java) | ✅ `testFalconHeavy` (loads 600 t / 50 t, LEO 400 km) |

### 2.2 Manques traités par cette spec

- **Aucune notion de capacités** : rien n'exprime « S2 est rallumable », « un solide vole
  plein », « un cryo ne survit pas à un coast de 5 h ». Tout est implicite.
- `Launchers.FalconHeavy(...)` retourne un `VehicleStack` **anonyme** : le modèle de
  lanceur n'est pas une entité — pas d'id (wizard), pas de nom d'étage (diagnostics),
  pas de paramètres de profil (`AscentProfile`).
- Capacité S2 = 107 000 kg au lieu de **107 500 kg** (chiffre retenu par 04 §4.1 et 06 §4.1).

### 2.3 Hors périmètre (rappel)

Refactor `GravityTurnManeuver` / consommation d'`AscentProfile` (06 I1), catalogue de
payloads et câblage wizard (06 I2), `PropellantBudget` (06 I3), `MassDepletionDetector`
(06 I4), dérivateur de profil (05 §2.4), entrée Ariane 5 au catalogue (06 I8).

---

## 3. Décisions de conception

**C1 — Granularité du descripteur** (tranche la question ouverte 05 §7-1) : un record à
6 champs — 4 enums + 2 scalaires. Critère de suffisance : exprimer les trois
architectures cibles (Falcon Heavy, Ariane 5 ECA, spacecraft + AKM) sans champ mort ni
refactor prévisible (démonstration §4.5). Rejeté : modèle riche (courbes de poussée,
throttle continu, contraintes thermiques) — sur-ingénierie pour 2–3 lanceurs.

**C2 — « Throttleable » non modélisé.** La simulation n'a que des poussées constantes
(`ConstantThrustManeuver` Orekit) ; aucun consommateur. « Coupable » de la table 05 §2.2
est absorbé par `ShutdownMode.COMMANDED`. À réintroduire si un besoin réel apparaît.

**C3 — `maxCoastDuration` explicite, pas dérivée de l'ergol.** 05 §2.2 dit « cryo ⇒
coast limité » ; on garde la causalité comme **invariant de validation** (CRYOGENIC exige
une durée finie) mais la valeur est une donnée par étage : un kérolox subcooled (Falcon)
et un LH2/LOX (ESC-A) n'ont pas la même tenue en orbite. C'est cette valeur — pas l'enum —
que le dérivateur comparera au coast requis pour décider la délégation à l'AKM.

**C4 — Charge variable dérivée, pas stockée.** `variableLoad()` ≡ `propellant != SOLID`
(règle de 05 §2.2, dernière ligne). Un champ séparé pourrait se désynchroniser de l'ergol.

**C5 — `AscentProfile` porté par `LauncherModel`** (décision S2 de 06, option (c)),
introduit ici **comme donnée uniquement**. Sa consommation par `VerticalAscentStage` /
`GravityTurnStage` est le cœur de 06 I1 et n'est pas touchée : les missions continuent
d'utiliser leurs constantes (`ASCENSION_DURATION = 10`, kick 3°).

**C6 — Valeurs Falcon Heavy.** ISP étage 1 **311 s conservée** : cette spec est un
refactor structurel à trajectoires identiques ; la bascule 311 → 296 s (recommandation
06 §S1) est un point de calibration de 06 I1, à ne pas mélanger avec le refactor.
Capacité S2 corrigée **107 000 → 107 500 kg** : sans effet sur les trajectoires (aucune
durée de burn ne dépend de la capacité depuis la correction D1 — elles dérivent de
`remainingFuel`, donc de la charge).

**C7 — `LaunchVehicle.name` différé.** 06 §4.1 prévoit un champ `name` sur
`LaunchVehicle` (diagnostics). Ajouter un composant à un record casse tous les
call-sites ; on le fera dans 06 I1 avec le refactor de signature. Ici le nom vit dans
`StageModel`, ce qui suffit au catalogue.

**C8 — Enums top-level**, un fichier par type (convention du projet, cf. `SolarSystemBody`).
Delta assumé vs 06 §4.1 : `PropellantType.LIQUID` y devient **`STORABLE` / `CRYOGENIC`**
(nécessaire pour C3) ; `StageModel` gagne `capabilities` au lieu du seul `propellantType`.
06 n'étant pas implémenté, il n'y a aucun call-site à migrer ; 06 §4.1 est réputé
remplacé par le présent §4.

---

## 4. Architecture cible

Tous les nouveaux types vivent dans `simulation/mission/vehicle/`. Records immuables,
`Objects.requireNonNull` dans les constructeurs compacts (conventions CLAUDE.md).

### 4.1 Enums

```java
/** How a stage's engines are ignited. */
public enum IgnitionMode {
  GROUND,    // lit on the pad — candidate for the initial ascent phase
  AIRSTART   // lit in flight — upper stage or kick stage
}

/** How a stage's burn ends. */
public enum ShutdownMode {
  COMMANDED,          // engine can be cut on command (DateDetector-style termination)
  BURN_TO_DEPLETION   // burns until flame-out (MassDepletionDetector-style termination)
}

/** Propellant nature; constrains load variability and coast endurance. */
public enum PropellantType {
  SOLID,      // fixed load (= capacity), burn to depletion, no restart
  STORABLE,   // liquid, storable on orbit (hypergolics, RP-1 alone)
  CRYOGENIC   // liquid with cryogenic components — bounded coast duration
}

/** Intended role of the stage in a flight profile (hint for profile derivation). */
public enum StageRole {
  BOOSTER,   // strap-on, jettisoned mid-ascent
  CORE,      // main/sustainer stage, ground-lit
  UPPER,     // orbital stage
  KICK       // payload-integrated apogee motor (AKM)
}
```

### 4.2 `StageCapabilities`

```java
/**
 * Physical capabilities of a launcher stage. Formalizes what a stage CAN do in a flight
 * profile (05 §2.2); input of the future profile derivation (05 §2.4).
 *
 * @param ignition how the stage is ignited
 * @param restartCount number of relights after first ignition (0 = not restartable)
 * @param shutdown how burns terminate
 * @param propellant propellant nature
 * @param maxCoastDuration max time (s) the stage can stay shut down between two burns;
 *     POSITIVE_INFINITY if unlimited; meaningless (use 0) when restartCount == 0
 * @param role intended role in the flight profile
 */
public record StageCapabilities(
    IgnitionMode ignition,
    int restartCount,
    ShutdownMode shutdown,
    PropellantType propellant,
    double maxCoastDuration,
    StageRole role) {

  public StageCapabilities {
    // + requireNonNull sur les 4 enums
    if (restartCount < 0) throw new IllegalArgumentException(...);
    if (Double.isNaN(maxCoastDuration) || maxCoastDuration < 0)
      throw new IllegalArgumentException(...);
    if (propellant == PropellantType.SOLID
        && (shutdown != ShutdownMode.BURN_TO_DEPLETION || restartCount != 0))
      throw new IllegalArgumentException("solid stages burn to depletion and cannot restart");
    if (propellant == PropellantType.CRYOGENIC && Double.isInfinite(maxCoastDuration))
      throw new IllegalArgumentException("cryogenic stages must declare a finite max coast");
  }

  /** Solids fly full (load == capacity); liquid loads are mission-sizable (05 §2.2). */
  public boolean variableLoad() { return propellant != PropellantType.SOLID; }

  /** True if the stage survives a shutdown of the given duration between two burns. */
  public boolean canCoastFor(double duration) { return duration <= maxCoastDuration; }
}
```

Les deux invariants encodent les deux seules dérivations « dures » de la table 05 §2.2 ;
le reste (affectation étage→phase) est du ressort du dérivateur, pas de la validation.

### 4.3 `StageModel`

```java
/** Static description of one stage of a launcher model (design data, not an instance). */
public record StageModel(
    String name,                   // "S1 (3 cores aggregated)" — diagnostics et logs
    double dryMass,                // kg
    double propellantCapacity,     // kg — tank size, fixed by design
    PropulsionSystem propulsion,
    StageCapabilities capabilities) {

  public StageModel { /* requireNonNull, dryMass > 0, propellantCapacity >= 0 */ }

  /** Instantiates this stage with a mission-specific propellant load (kg). */
  public LaunchVehicle toVehicle(double propellantLoad) {
    // 0 <= load <= capacity (le ctor LaunchVehicle re-vérifie la borne haute)
    // !capabilities.variableLoad() && load != capacity -> IllegalArgumentException
    return new LaunchVehicle(dryMass, propellantCapacity, propellantLoad, propulsion);
  }

  public LaunchVehicle toVehicleFullyLoaded() { return toVehicle(propellantCapacity); }
}
```

### 4.4 `AscentProfile` et `LauncherModel`

Repris de 06 §4.1 tels quels (données non consommées avant 06 I1) :

```java
/** Flight-profile parameters imposed by the launcher (launcher-driven profile). */
public record AscentProfile(
    double verticalAscentDuration,    // s   — > 0
    double pitchKickAngleDeg,         // °   — dans (0, 90)
    double interstageCoastDuration) { // s   — >= 0
  public AscentProfile { /* bornes ci-dessus */ }
}

/** Named launcher model: stages + flight profile. Single source of truth of the catalog. */
public record LauncherModel(
    String id,                 // "FALCON_HEAVY" — future clé wizard (FormField.LAUNCHER_TYPE)
    String displayName,        // "Falcon Heavy"
    List<StageModel> stages,   // bottom -> top
    AscentProfile ascentProfile) {

  public LauncherModel { /* requireNonNull, stages non vide, stages = List.copyOf(stages) */ }

  /** Instantiates the stack with per-stage loads (kg, same order as stages). */
  public VehicleStack instantiate(double[] propellantLoads, Spacecraft payload) {
    // propellantLoads.length == stages.size() sinon IllegalArgumentException
    // vehicles = stages[i].toVehicle(loads[i]) bottom->top, puis payload en dernier
  }

  /** Loads = capacities (historical behaviour). */
  public VehicleStack instantiateFullyLoaded(Spacecraft payload) { ... }
}
```

La validation des charges (bornes, solides pleins) est **déléguée à
`StageModel.toVehicle`** — un seul point de vérité, `instantiate` ne vérifie que la
longueur du tableau.

### 4.5 Refonte de `Launchers` — catalogue

```java
public final class Launchers {
  private Launchers() {}

  public static final LauncherModel FALCON_HEAVY =
      new LauncherModel(
          "FALCON_HEAVY",
          "Falcon Heavy",
          List.of(
              new StageModel(
                  "S1 (3 cores aggregated)", 66_000, 1_233_000,
                  new PropulsionSystem(311, 22_800_000),
                  new StageCapabilities(
                      IgnitionMode.GROUND, 0, ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC, 0.0, StageRole.CORE)),
              new StageModel(
                  "S2 (Merlin Vacuum)", 4_000, 107_500,
                  new PropulsionSystem(348, 981_000),
                  new StageCapabilities(
                      IgnitionMode.AIRSTART, 2, ShutdownMode.COMMANDED,
                      PropellantType.CRYOGENIC, 7_200.0, StageRole.UPPER))),
          new AscentProfile(7.0, 3.0, 2.0));

  public static LauncherModel byId(String id) { ... }  // IllegalArgumentException si inconnu
  public static List<LauncherModel> all() { ... }      // pour le wizard (06 I2)
}
```

La surcharge `FalconHeavy(double, double, Spacecraft)` est **supprimée** (call-site
unique : `testFalconHeavy`, migré en E4). Les factories legacy
`LaunchVehicle.getLauncherStage1Vehicle()` etc. restent intactes — elles portent les
missions historiques et disparaîtront en 06 I1.

**Justification des valeurs FH** (sources : 04 §3.1 et 06 §4.1) :

| Donnée | Valeur | Justification |
|---|---|---|
| S1 : dry 66 t, capacité 1 233 t, poussée 22.8 MN | 04 §3.1 | 3 cœurs agrégés (décision S1 de 06 : pas de staging parallèle avant I8) |
| S1 : ISP 311 s | conservée | C6 — bascule 296 s = calibration 06 I1 |
| S1 : `restartCount = 0`, `maxCoast = 0` | modèle expendable | pas de relight modélisé ; un étage non rallumable ne coast jamais éteint |
| S2 : capacité 107 500 kg | 04 §4.1 | corrige le 107 000 actuel (C6, sans effet trajectoire) |
| S2 : `restartCount = 2` | MVac multi-relight | le profil GEO cible (06 I5) exige 1 rallumage (insertion parking → injection GTO) ; +1 de marge |
| S2 : `maxCoast = 7 200 s` (2 h) | choix de modélisation | > coast parking max (~45 min) et < coast GTO→apogée (~5 h 15) : **force la délégation de la circularisation à l'AKM** (04 §4.3 E5). Les vols FH réels « long coast » (~6 h) existent ; on modélise l'architecture AKM retenue par 04/06 — valeur calibrable |
| Kérolox → `CRYOGENIC` | LOX subcooled | c'est la tenue au coast (boil-off) qui compte, cf. C3 |
| `AscentProfile(7.0, 3.0, 2.0)` | 06 §4.1 | vertical ~7 s, kick 3° (valeur actuelle des missions), coast inter-étage 2 s |

### 4.6 Preuve d'expressivité — Ariane 5 ECA et AKM (spec seulement)

Ces descripteurs valident C1 ; ils ne sont **pas** ajoutés au catalogue (`VehicleStack`
ne supporte pas le staging parallèle des EAP — 06 I8 ; le KICK relève du catalogue de
payloads — 06 I2). Un test unitaire (§6) construit les `StageModel` Ariane 5 pour
verrouiller l'expressivité.

| Étage | ignition | restarts | shutdown | propellant | maxCoast | role |
|---|---|---|---|---|---|---|
| 2× EAP P241 | GROUND | 0 | BURN_TO_DEPLETION | SOLID | 0 | BOOSTER |
| EPC (Vulcain 2) | GROUND | 0 | COMMANDED | CRYOGENIC | 0 | CORE |
| ESC-A (HM7B) | AIRSTART | **0** | COMMANDED | CRYOGENIC | ~3 600 | UPPER |
| AKM bipergol (payload GEO) | AIRSTART | 1 | COMMANDED | STORABLE | +∞ | KICK |

L'ESC-A non rallumable (`restartCount = 0` avec rôle UPPER) est le cas qui justifie de
distinguer `restartCount` du rôle : c'est lui qui interdit le profil « parking puis
réallumage » et impose l'injection GTO directe — exactement l'exemple B de 05 §2.4.

---

## 5. Plan d'implémentation

Quatre étapes, chacune compile et passe `./gradlew test`.

| # | Contenu | Nouveaux fichiers |
|---|---|---|
| E1 | Enums + `StageCapabilities` + tests d'invariants | `IgnitionMode`, `ShutdownMode`, `PropellantType`, `StageRole`, `StageCapabilities` (+ test) |
| E2 | `StageModel.toVehicle` + tests (bornes, solide plein) | `StageModel` (+ test) |
| E3 | `AscentProfile` + `LauncherModel.instantiate` + tests | `AscentProfile`, `LauncherModel` (+ test) |
| E4 | Refonte `Launchers` en catalogue, migration `testFalconHeavy`, test d'équivalence | `Launchers` refondu, `LaunchersTest` |

Migration E4 du call-site unique :

```java
// avant
Launchers.FalconHeavy(600_000, 50_000, Spacecraft.getSpacecraft())
// après
Launchers.FALCON_HEAVY.instantiate(new double[] {600_000, 50_000}, Spacecraft.getSpacecraft())
```

---

## 6. Tests et critères de sortie

**Unitaires** (nouveaux, `src/test/.../simulation/mission/vehicle/`) :

- `StageCapabilitiesTest` : SOLID + COMMANDED → rejet ; SOLID + restart > 0 → rejet ;
  CRYOGENIC + maxCoast infini → rejet ; restart/coast négatifs → rejet ;
  `variableLoad()` faux seulement pour SOLID ; `canCoastFor` aux bornes.
- `StageModelTest` : `toVehicle` — load < 0 et load > capacité → rejet ; étage SOLID avec
  load ≠ capacité → rejet ; `toVehicleFullyLoaded` ⇒ `getMass() = dry + capacité`.
- `LauncherModelTest` : `instantiate` — longueur de tableau incorrecte → rejet ; ordre du
  stack = étages bottom→top puis payload en dernier ; masses du stack conformes ;
  `instantiateFullyLoaded` ≡ `instantiate(capacités)` ; les trois `StageModel` Ariane 5
  ECA du §4.6 se construisent sans exception (verrou d'expressivité).
- `LaunchersTest` : `byId("FALCON_HEAVY")` retourne la constante ; `byId` inconnu →
  `IllegalArgumentException` ; `all()` contient FH ; chiffres FH verrouillés
  (dry 66 t / 4 t, capacités 1 233 t / 107,5 t, ISP 311 / 348).

**Équivalence de comportement** (dans `LaunchersTest`) : le stack
`FALCON_HEAVY.instantiate({600_000, 50_000}, getSpacecraft())` a exactement les mêmes
`dryMass`, `propellantLoad`, `getMass` et propulsions par étage que l'ancien
`FalconHeavy(600_000, 50_000, …)` — seule la capacité S2 diffère (+500 kg, C6), dont
aucune durée de burn ne dépend depuis la correction D1.

**Critères de sortie :**

1. `./gradlew test` vert, `testFalconHeavy` converge comme avant (mêmes masses, même
   propulsion ⇒ mêmes trajectoires).
2. Plus aucune surcharge `Launchers.FalconHeavy(...)` ; le catalogue est interrogeable
   par id (`byId`/`all`) — prérequis de 06 I2.
3. Le descripteur exprime FH, Ariane 5 ECA et un AKM sans champ inutilisé (test §4.6).

---

## 7. Suites (pointeurs)

- **06 I1** : consommation d'`AscentProfile` par les missions, refactor
  `GravityTurnManeuver` (suppression d'`usedAscensionPropellant`), calibration ISP 296 s,
  `LaunchConfiguration`, champ `name` sur `LaunchVehicle` (C7).
- **06 I2** : `Payloads` + wizard (`Launchers.all()` alimente `StepLauncher`).
- **05 §2.4** : le dérivateur de profil consommera `StageCapabilities`
  (`canCoastFor`, `variableLoad`, `shutdown`, `role`).

---

## 8. Risques

| Risque | Impact | Mitigation |
|---|---|---|
| Sur-ingénierie du descripteur (question 05 §7) | complexité morte | 6 champs, 2 invariants, 2 helpers ; chaque champ a un consommateur identifié dans 05 §2.4 / 06 I4-I5 ; pas de presets ni de hiérarchie |
| Divergence avec 06 §4.1 (`PropellantType.LIQUID`, `StageModel` sans capacités) | confusion de specs | C8 : deltas explicites, 06 §4.1 remplacé par §4 du présent doc ; 06 non implémenté, zéro call-site |
| `maxCoast` S2 = 2 h contredit les vols FH long-coast réels | réalisme perçu | choix d'architecture assumé (délégation AKM, 04 §4.3 E5), valeur calibrable dans le catalogue sans refactor |
| Correction capacité S2 107 → 107,5 t | régression cachée | aucune durée de burn ne dépend de la capacité (post-D1) ; test d'équivalence §6 le verrouille |
