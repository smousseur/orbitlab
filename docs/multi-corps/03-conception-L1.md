# PHY-4 / L1 — Le corps central devient explicite

Lot **L1** du découpage (`01-decoupage.md` §4), le premier qui touche du code de
production. Il se mesure contre `02-baseline-L0.md`.

**Propriété rendue vraie.** Le corps central d'une propagation est une donnée
portée par l'étage, plus une constante lue au fond d'une fabrique.

**Ce que ce lot ne fait pas.** Aucun étage ne déclare autre chose que la Terre.
Aucune force nouvelle, aucun changement de repère, aucun chiffre déplacé. C'est
un refactor pur, et sa réussite se démontre par une **égalité stricte** avec L0,
pas par une revue.

> **Le lot le plus risqué du chantier**, dit le §6 du découpage : vingt sites
> touchés sans le droit de rien changer. Toute la conception ci-dessous est
> ordonnée par cette contrainte — d'où le choix systématique de la forme qui
> transforme un oubli en erreur de compilation plutôt qu'en dérive silencieuse.

---

## 1. Inventaire mesuré

Le découpage annonçait « seize sites de construction ». Le compte réel, relevé au
commit `be7a320` :

| | `src/main` | `src/test` |
|---|---|---|
| Appels à `create*Propagator` | **20** | 13 |
| dont dans les six étages analytiques | 12 | — |
| `NodeDetector(gcrf())` | 3 | — |

Les vingt sites de `main` :

```
CircularizationBurnResolver:104          AnalyticGtoInjectionStage:149, 420, 433
GravityTurnManeuver:281                  AnalyticHohmannTransferStage:131, 292
TransferManeuver:104, 185                AnalyticParkingInsertionStage:145
TransfertTwoManeuver:125                 AnalyticPlaneTrimAtNodeStage:120, 198
StageChainRunner:192                     AnalyticTrimBurnStage:131, 228
AnalyticApogeeCircularizationStage:101, 260
ConstantThrustStage:97                   GravityTurnBurnStage:159
```

**Les douze sites analytiques sont la majorité du lot.** C'est le fait qui
gouverne la conception du test (§5) : un gate qui ne volerait qu'une ascension en
manquerait plus de la moitié.

**Chaque manœuvre est construite par un étage** — `GravityTurnFirstBurnStage:204`
→ `GravityTurnManeuver`, `TransfertTwoManeuverStage:122` → `TransfertTwoManeuver`,
`TransfertManeuverStage:120` → `TransferManeuver`, et `CircularizationBurnResolver`
est appelé depuis `TransfertTwoManeuver`. Le contexte n'a donc jamais à franchir
une frontière : il descend étage → manœuvre par le constructeur.

---

## 2. L'objet

```java
package com.smousseur.orbitlab.simulation.gravity;

/** The central body a propagation is flown around: everything a force model, an
 *  altitude or a node detector needs, and nothing else. */
public record GravitationalContext(
    SolarSystemBody body,
    double mu,
    Frame inertialFrame,
    Frame bodyFixedFrame,
    OneAxisEllipsoid shape) {

  public static GravitationalContext earth() { return Holder.EARTH; }
}
```

Pour la Terre : `EARTH`, `Constants.WGS84_EARTH_MU`, GCRF, ITRF, ellipsoïde WGS84
— exactement ce que les sites lisent aujourd'hui, au même endroit, dans le même
ordre.

### 2.1 Résolution paresseuse, pas de `static final EARTH`

Le contexte contient un `Frame` ITRF et un `OneAxisEllipsoid`, tous deux
construits par Orekit. Une constante `static final` les résoudrait à
l'initialisation de classe, c'est-à-dire potentiellement **avant**
`OrekitService.get().initialize()`, que les tests appellent en `@BeforeAll`.
Holder pattern — la convention du dépôt — donc résolution à la première lecture.

### 2.2 `OneAxisEllipsoid`, pas `BodyShape`

Une sphère est un `OneAxisEllipsoid` d'aplatissement nul : le type concret couvre
déjà le cas lunaire de L4 sans qu'on invente une abstraction que rien ne demande.
C'est aussi le type que `TopocentricFrame` et `Mission.computeAltitudeMeters`
consomment aujourd'hui.

### 2.3 Le piège des deux µ — à écrire dans le javadoc du champ

`OrbitElements.mean()` rebase délibérément l'orbite sur le µ **du fournisseur de
potentiel**, pas sur `WGS84_EARTH_MU`, et son commentaire dit pourquoi : mélanger
les deux décale les éléments d'environ un mètre, **ce qui se lit comme du J2**
(spec `orbit-reporting/01` §3.3).

Le `mu` du contexte est celui du propagateur. **L1 ne s'en sert pas pour unifier
les deux.** Ce serait exactement le genre d'amélioration gratuite qui
invaliderait la baseline sans qu'on sache l'attribuer.

---

## 3. Le câblage

### 3.1 Deux déclarations, sur le modèle de `maxStepSeconds`

`MissionStage.maxStepSeconds(entryState, mission)` est le précédent exact : un
étage y déclare déjà une propriété de propagation, avec un défaut hérité. Le
contexte prend la même forme, ce qui n'ajoute aucun concept à l'architecture.

```java
// Mission
public GravitationalContext gravitationalContext() { return GravitationalContext.earth(); }

// MissionStage — hérite de la mission, surchargeable par étage.
// Personne ne surcharge en L1 : c'est la définition du lot.
public GravitationalContext gravitationalContext(Mission mission) {
  return mission.gravitationalContext();
}
```

### 3.2 Les fabriques prennent le contexte, et les surcharges Terre-implicites disparaissent

```java
createOptimizationPropagator(GravitationalContext ctx, double maxStep)
createTestPropagator(GravitationalContext ctx, double maxStep)
```

**Supprimer les variantes sans contexte est le point qui donne sa valeur à toute
l'approche.** Tant qu'elles existent, un site oublié compile et vole la Terre en
silence ; une fois supprimées, le compilateur énumère les vingt sites à votre
place. Coût : les 13 appels de test à mettre à jour, mécaniquement.

### 3.3 Le cache du modèle de gravité

Le champ `gravityModel` d'`OrekitService` (double-checked locking, un seul modèle
8×8 terrestre en ITRF) devient un `ConcurrentHashMap<SolarSystemBody, ForceModel>`
peuplé par `computeIfAbsent`.

**Invariant à préserver explicitement :** le champ 8×8 terrestre reste **la même
instance partagée** qu'aujourd'hui. C'est ce qui rend l'égalité au bit près
atteignable ; une instance recréée par appel donnerait les mêmes chiffres, mais
on perdrait le droit d'exiger `0.0` de tolérance et donc l'essentiel du gate.

Le cas lunaire — il n'y a pas de champ 8×8 lunaire dans `orekit-data.zip` —
se résoudra **dans ce cache**, pas chez les appelants. C'est une décision de L4 ;
L1 se contente de mettre le point de décision au bon endroit.

### 3.4 Les sites qui basculent

- les **20** appels à `create*Propagator` de `main` (argument de contexte) ;
- `StageChainRunner:192`, qui lit `stage.gravitationalContext(mission)` juste à
  côté du `stage.maxStepSeconds(...)` qu'il lit déjà ;
- les **3** `NodeDetector(OrekitService.get().gcrf())` →
  `new NodeDetector(ctx.inertialFrame())` — `AnalyticGtoInjectionStage:437`,
  `AnalyticPlaneTrimAtNodeStage:206`, `CoastingStage:48` ;
- `Mission.computeAltitudeMeters(state)` → `ctx.shape()` ;
- `ReentryGuard.arm` / `armQuiet` → rayon équatorial du contexte.

---

## 4. La couture : la propagation, pas le lancement

Le §2-A du découpage énumère une trentaine de sites Terre-en-dur. Les basculer
tous ferait entrer un paramètre de contexte dans des utilitaires statiques et
jusque dans la couche UI, pour un besoin qui n'arrive qu'en L6. **La couture de
L1 passe donc sur ce qui propage ou juge un état pendant le vol** — c'est
exactement ce que L4 fera tourner sur un arc lunaire. Le reste attend.

### 4.1 Ce qui reste Terre-en-dur, et pourquoi

| Site | Raison |
|---|---|
| `EarthMission.getInitialState:44-56` | Construit l'état **initial** d'un tir depuis un sol qui tourne. PHY-4 ne décolle jamais de la Lune ; l'arc qui change de corps central est en aval. |
| `LaunchPlane:157` | Même raison, et c'est une méthode `static` sans mission en portée. Un plan de tir est défini par un site de lancement terrestre. |
| `GravityTurnProblem:260`, `GravityTurnConstraints:70`, `TransferProblem:545` | Classes de problème CMA-ES. Le §1 du découpage met l'**optimisation multi-arcs hors PHY-4**. |
| `AchievedOrbit:61`, `OrbitElements` | Reporting d'orbite atteinte. Voir §2.3 : c'est là que vit le piège des deux µ, et L1 n'y touche pas. |
| `PropellantBudget:40`, `MissionHorizon:138`, `StageEndStateDiagnostic:20`, `Physics:206,247` | Dimensionnement et diagnostic hors vol. |
| `DynamicParameters:96` (UI) | Période affichée dans le wizard. |

**Chaque site laissé porte un javadoc disant quel lot le réveillera**, pour qu'un
lecteur de L4 ne le prenne pas pour un oubli. C'est la seule chose que L1 ajoute
à ces fichiers.

### 4.2 Le plancher de la garde de rentrée reste une constante

`ReentryGuard.SUBSURFACE_FLOOR = −50 km` existe parce que l'altitude *sphérique*
d'un pas de tir terrestre est déjà négative — la Terre est aplatie de 21,4 km. Le
**rayon** devient contextuel en L1 ; le **plancher** reste `−50 km`. Sur un corps
quasi sphérique il sera trop généreux, ce qui est sans danger et relève de L4.

---

## 5. Le test qui ferme

**`CentralBodyBaselineTest`**, dans `simulation/mission/operation/`, à côté
d'`EarthOrbitNonRegressionTest`.

`EarthOrbitNonRegressionTest` compare **deux chemins dans le même run** (spec
contre constructeur direct). Cette forme ne se transpose pas à L1 : après le
refactor, l'ancien chemin n'existe plus, il n'y a plus de B pour le A. Le gate de
L1 doit comparer **à travers un commit**, donc épingler des valeurs.

### 5.1 Il épingle chaque frontière d'étage, pas seulement le MECO

C'est le choix de conception qui compte le plus. Un gate qui n'épingle que l'état
final dit « ça a bougé » ; un gate qui épingle les douze frontières d'étage dit
« ça a bougé à l'injection GTO ». Sur un refactor de vingt sites, c'est la
différence entre une bissection et une lecture.

### 5.2 Quatre profils, choisis pour la couverture des sites

| Profil | Variables figées | Ce que ça couvre |
|---|---|---|
| **LEO-400** | `{307.193166 ; 0.127161}` | ascension + chaîne analytique Hohmann + trim |
| **GEO** | `{329.124209 ; 0.177424}` | les 6 étages analytiques, dont 2 `NodeDetector` |
| **MEO** | `{378.663107 ; 0.131995}` | 12 étages : parking, GTO, circularisation, trim, plane trim |
| **Polaire** | burn2 `250 s` ; `0.32` | le seul profil reproductible au bit près en L0 |

Les trois premières lignes donnent un `transitionTime` absolu, **arrondi à ce que
la baseline imprime** ; le polaire donne une durée de second allumage, parce que
c'est sous cette forme que sa fixture la fige déjà
(`transitionTime = stagingCompleteTime + burn2`). Ces valeurs sont les *entrées*
choisies ; les états attendus, eux, se mesurent — §5.3.

Aucun CMA-ES : les variables sont injectées par
`firstBurn.applyOptimization(new OptimizationResult(variables, …))`, le mécanisme
que `EarthOrbitNonRegressionTest.flyAscent` emploie déjà. Les valeurs sont les
optimums de L0, recopiés en dur.

**Seul le virage gravitationnel reçoit des variables figées.**
`AnalyticHohmannTransferStage` et `AnalyticParkingInsertionStage` implémentent
aussi `OptimizableMissionStage`, mais ils *stockent un plan qu'ils calculent
eux-mêmes* — le vecteur CMA-ES est à deux dimensions sur les quatre profils. Ils
se replanifient donc à l'intérieur de la passe du gate, ce qui est déterministe
dans une passe donnée (§6 de la baseline) et constitue précisément l'anomalie
§5.2 entre deux passes différentes. D'où l'exigence du §5.4 ci-dessous.

Ce sont des **chaînes complètes**, pas des ascensions : les douze sites
analytiques sont la majorité du lot (§1).

### 5.3 Les littéraux ne se recopient pas depuis la baseline

`02-baseline-L0.md` imprime `transitionTime : 307.193166` — six décimales d'un
`double` que CMA-ES a trouvé plus précis. Revoler à la valeur **arrondie** donne
un MECO légèrement différent de celui qui est logué.

Les valeurs d'or sont donc **mesurées par le premier run du test sur le commit
actuel, avant tout refactor**. Les chiffres de la baseline servent de
contre-vérification au mètre près — si le test épinglé s'écarte de L0 de plus que
la troncature ne l'explique, c'est le test qui est faux, pas la baseline.

### 5.4 Chaque profil déclare sa passe

§5.2 de la baseline : sur MEO, la passe d'optimisation et la passe d'éphéméride
divergent **de façon reproductible** (3 m/s au trim), parce que les étages
analytiques replanifient au lieu de rejouer. Sans déclaration de passe, un futur
lecteur comparera deux passes différentes et attribuera l'écart au corps central.

**Le gate vole `StageChainRunner.sampling(null, 0.0, recorder)`**, et non
`plain()` : `plain()` n'accepte aucun listener, donc ne permet pas d'enregistrer
les frontières d'étage qui sont l'objet même du §5.1. Les deux runners ne
diffèrent que par `abortOnFailure` — sur un profil nominal, où aucun étage ne
lève, ils volent la même trajectoire au bit près. Un sampler `null` n'ajoute
aucun multiplexeur, et un `lastStageCoastSeconds` de `0` borne le dernier étage
par sa propre coupure, comme `plain()`.

Le gate construit une mission **fraîche** et la vole **une fois**. Ses nombres ne
sont donc comparables ni à une passe d'optimisation ni à une passe d'éphéméride
de la baseline — seulement à eux-mêmes, d'un commit à l'autre. C'est exactement
ce qu'on lui demande.

### 5.5 Tolérance `0.0`, et l'exception s'écrit

Même constante, mêmes instances de repères, même modèle 8×8 partagé : les
opérations flottantes sont dans le même ordre, l'égalité stricte est atteignable.
Le découpage l'exige explicitement — « si elle n'est pas atteignable, la raison
doit être écrite, pas absorbée par une tolérance ». Un site qui ne l'atteint pas
fait l'objet d'un javadoc nommant la cause, pas d'un `delta`.

### 5.6 Ce que le test exclut

Le **coast final ouvert** (2 037 556 s, 48 révolutions sur MEO) : son point
d'arrivée est un choix d'horizon, pas de la physique. Le gate s'arrête au dernier
étage propulsif.

Et, par construction, **aucun compte d'évaluations, aucun coût d'exploration
perdante, aucun temps** — la règle du §6 de la baseline.

---

## 6. Ordre d'exécution

L'ordre est une partie de la conception, pas une commodité : il est ce qui rend
une dérive attribuable.

1. **Écrire `CentralBodyBaselineTest` et le rendre vert sur le commit actuel.**
   Aucun code de production ne bouge. Commit séparé — c'est le filet, il doit
   exister avant la chute.
2. **Introduire `GravitationalContext` et le cache par corps**, sans changer
   aucun appelant : les surcharges existantes délèguent à `earth()`. Le gate doit
   rester vert. Rien n'a encore le droit de bouger.
3. **Basculer les 20 sites + les 3 `NodeDetector` + `computeAltitudeMeters` +
   `ReentryGuard`**, puis supprimer les surcharges Terre-implicites. Le
   compilateur ferme la liste.
4. **Mettre à jour les 13 appels de test.**
5. **Rejouer le gate** (rapide, sans CMA-ES), puis les tests d'optimisation, qui
   doivent retomber sur les chiffres de L0.

L'étape 2 est délibérément inutile prise seule — elle existe pour que, si l'étape
3 fait bouger un chiffre, on sache déjà que ce n'est ni l'objet ni le cache.

---

## 7. Risques identifiés

**Le cache par corps.** Passer d'un double-checked locking à un
`ConcurrentHashMap` est le seul endroit où la concurrence change. Les
explorations CMA-ES tournent en parallèle (`CMAESTrajectoryOptimizer:314`) :
il faut donc que le cache garantisse une instance unique par corps, et pas
seulement une valeur cohérente.

**`computeIfAbsent` la garantit** : il est atomique, et la fonction de mapping
est évaluée **au plus une fois par clé** — c'est précisément l'invariant du §3.3.
Aucun pré-chauffage n'est nécessaire, et aucun verrou explicite ne doit être
réintroduit. Ce paragraphe existe pour que le plan ne « corrige » pas ce point.

La contrainte réelle de `computeIfAbsent` est ailleurs : la fonction de mapping
ne doit pas modifier la carte. Construire un `HolmesFeatherstoneAttractionModel`
ne la touche pas — mais si L4 ajoute une résolution lunaire qui retombe sur un
autre corps, ce serait un blocage. À vérifier à ce moment-là, pas maintenant.

**L'ordre des forces.** `createOptimizationPropagator` appelle `setOrbitType`,
puis `setMu`, puis `addForceModel`. Cet ordre ne doit pas changer : Orekit n'y
est pas indifférent partout, et une permutation « plus propre » est précisément
le type de changement gratuit qui coûterait la baseline.

**La surface du diff.** Vingt sites plus treize tests font un diff large. Il est
mécanique et relisable, mais il ne doit contenir *que* la bascule : aucun
renommage, aucun reformatage, aucune correction de javadoc non liée. Ce qui se
lit en diagonale doit pouvoir l'être.

---

## 8. Ce que L1 laisse ouvert

- **L2** ajoutera `ThirdBodyAttraction` à un étage qui le déclare. Le contexte de
  L1 n'a pas de champ pour ça : le troisième corps est une force, pas un corps
  central. La question « où vit la liste des perturbateurs » se tranche en L2.
- **L3** rendra le repère explicite dans l'éphéméride. Le contexte de L1 porte
  déjà `inertialFrame`, ce qui est la moitié de la réponse à la question ouverte
  n° 1 du découpage.
- **L4** fera déclarer un autre corps par un étage. C'est le premier moment où
  `gravitationalContext(mission)` sera surchargé, et où le cache du §3.3 devra
  répondre pour la Lune.
- Les sites du §4.1 se réveillent **quand un arc non terrestre a besoin d'eux**,
  pas avant.
