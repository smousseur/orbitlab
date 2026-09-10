# docs/dette-technique.md — état de la dette technique

Photographie de la qualité du code au **2026-08-10**, commit `b027d1d`. Ce
document est un état des lieux mesuré, pas une roadmap : les items qui méritent
d'être planifiés doivent être promus dans une roadmap de version — voir
`roadmap/00-index.md`.

**Complément du 2026-09-10.** `DT-19` à `DT-21` viennent de la clôture de `PHY-8`
([`etagement/07-cloture.md`](etagement/07-cloture.md) §5), et sont tous trois des
**mesures**, pas des relectures : chacun a été trouvé en faisant voler la chose.

**Complément du 2026-09-09.** `DT-18` vient d'une mesure de `PHY-8 / L5` sur les
maillages de lanceurs, et succède à `DT-12` que le même lot ferme.

**Complément du 2026-08-30.** `DT-12` à `DT-17` viennent d'une revue des
documents de conception par chantier, pas d'une nouvelle passe de mesure
statique sur `src/` — la méthode du §1 ne les couvre donc pas. Les items
purement comportementaux ou de scope trouvés à cette occasion sont dans
`bugs.md` (`BUG-9` à `BUG-18`) et [`reliquats.md`](reliquats.md).

**Convention.** `DT-n` dans l'ordre de priorité décroissante à la date de
rédaction, jamais réattribué. Un item sort d'ici corrigé, ou requalifié en
« accepté » avec la raison. Comme dans `bugs.md`, chaque fiche sépare ce qui est
**mesuré** de ce qui est **inféré**.

---

## 1. Périmètre et méthode

| Grandeur | Valeur |
|---|---|
| Classes `src/main` | 266 |
| SLOC `src/main` (hors lignes vides) | 32 920 |
| SLOC `src/test` | 12 738 |
| Ratio test/main | 0,39 |

> **`J0-D`, re-mesuré le 2026-09-02.** Le même instrument, passé sur `b027d1d`,
> y retrouve **266 / 32 920 / 12 738** : les écarts ci-dessous sont donc des
> écarts réels et non une différence de méthode.
>
> | Grandeur | 2026-08-10 | 2026-09-02 | |
> |---|---:|---:|---|
> | Classes `src/main` | 266 | **387** | +45 % |
> | SLOC `src/main` | 32 920 | **56 854** | +73 % |
> | SLOC `src/test` | 12 738 | **31 000** | +143 % |
> | Ratio test/main | 0,39 | **0,55** | la couverture a monté plus vite que le code |
>
> **Le dépôt a presque doublé depuis la photographie.** Toute fiche de ce document
> dont la mesure n'a pas été re-vérifiée ci-dessous décrit un `src/main` qui faisait
> **58 %** de celui d'aujourd'hui, et un `src/test` qui en faisait 41 %.

**Aucun analyseur statique n'est branché sur le build** — `build.gradle` ne
déclare que le plugin `java`, et il n'y a pas de CI. Les chiffres ci-dessous ont
donc été reconstruits à la main :

> **Depuis le 2026-09-02 ce n'est plus vrai** — PMD et Spotless sont branchés et
> bloquants, voir [`DT-1`](#dt-1--aucune-analyse-statique-dans-le-build). Les
> mesures ci-dessous restent celles du 2026-08-10, faites à la main.

- longueur de méthodes et de classes par comptage d'accolades ;
- complexité cognitive **approximée** (mots-clés de branchement pondérés par la
  profondeur d'indentation) — l'ordre de grandeur est fiable, la valeur absolue
  ne l'est pas ;
- code mort par référencement croisé sur `src/main` **et** `src/test`, en
  incluant les références de méthode `::` ;
- familles de règles Sonar classiques par recherche ciblée.

La conséquence pratique : ces mesures ne sont pas reproductibles automatiquement
aujourd'hui. C'est le sujet de [`DT-1`](#dt-1--aucune-analyse-statique-dans-le-build).

---

## 2. Synthèse

| ID | Item | Sévérité | Effort | Risque de régression | État |
|---|---|---|---|---|---|
| [`DT-1`](#dt-1--aucune-analyse-statique-dans-le-build) | Aucune analyse statique dans le build | Critique (structurel) | Faible | Nul | **Corrigé le 2026-09-02** |
| [`DT-2`](#dt-2--squelette-dupliqué-sur-les-six-analyticstage) | Squelette dupliqué sur les six `Analytic*Stage` | Majeur | Moyen | **Élevé** | Ouvert |
| [`DT-3`](#dt-3--complexité-cognitive-de-multistageloadoptimizerminimize) | Complexité de `MultiStageLoadOptimizer.minimize()` | Critique | Moyen | **Élevé** | Ouvert |
| [`DT-4`](#dt-4--singleton-statique-mutable-orbitlabapplicationapp) | Singleton statique mutable `OrbitLabApplication.app` | Critique | Faible | Faible | **Corrigé le 2026-09-02** |
| [`DT-5`](#dt-5--classes-et-méthodes-trop-longues) | Classes et méthodes trop longues | Majeur | Élevé | Variable | Ouvert |
| [`DT-6`](#dt-6--gestion-dexception-trop-large) | Gestion d'exception trop large | Majeur | Faible | Faible | **Corrigé le 2026-09-02** |
| [`DT-7`](#dt-7--trou-de-couverture-sur-simulationmissionplanner) | Trou de couverture sur `simulation/mission/planner` | Majeur | Moyen | Nul | Ouvert |
| [`DT-8`](#dt-8--violations-de-la-règle-de-langue) | Violations de la règle de langue (français dans le code) | Mineur | Trivial | Nul | **Corrigé le 2026-09-02** |
| [`DT-9`](#dt-9--code-mort) | Code mort | Mineur | Trivial | Nul | **Corrigé le 2026-09-02** |
| [`DT-10`](#dt-10--commentaires-redondants) | Commentaires redondants | Mineur | Faible | Nul | Ouvert |
| [`DT-11`](#dt-11--littéraux-dupliqués-et-todo-non-tracés) | Littéraux dupliqués et TODO non tracés | Mineur | Faible | Nul | **Partiel le 2026-09-02** |
| [`DT-12`](#dt-12--mesh-ariane-6-absent-ariane-5-utilisé-à-la-place) | Mesh Ariane 6 absent (Ariane 5 utilisé à la place) | Mineur | Faible* | Nul | **Corrigé le 2026-09-09** |
| [`DT-13`](#dt-13--isp-catalogue-déjà-en-double-comptage-latent-avec-la-traînée-à-venir) | Isp catalogue déjà en double-comptage latent avec la traînée à venir | Majeur | Moyen | **Élevé pour `PHY-2`** | **Tranché en J2** (2026-09-10) ; calibration → `PHY-2` |
| [`DT-14`](#dt-14--écart-harris-priester--nrlmsise-00-non-arbitré) | Écart Harris-Priester / NRLMSISE-00 non arbitré | Mineur | Faible | Nul | **Tranché en J2** (2026-09-10) ; câblage + mesure → `PHY-2` |
| [`DT-15`](#dt-15--cd-catalogue-s2-hors-domaine-de-validité-déclaré) | `Cd` catalogue S2 hors domaine de validité déclaré | Mineur | Faible | Nul | **Tranché en J2** (2026-09-10) ; re-vérif → `PHY-2` |
| [`DT-16`](#dt-16--nrev-du-solveur-de-lambert-figé-à-0-partout) | `nRev` du solveur de Lambert figé à 0 partout | Mineur | Moyen | Nul aujourd'hui | Ouvert |
| [`DT-17`](#dt-17--performance-du-ruban-rnd-4-jamais-mesurée) | Performance du ruban (`RND-4`) jamais mesurée | Mineur | Faible | Nul | Ouvert |
| [`DT-18`](#dt-18--propulseurs-de-lariane-64-surdimensionnés-dans-le-maillage) | Propulseurs de l'Ariane 64 surdimensionnés dans le maillage | Mineur | Faible* | Nul aujourd'hui | Ouvert, **dû avant `PHY-5`** |
| [`DT-19`](#dt-19--réserve-dinsertion-universelle-sur-létage-supérieur) | Réserve d'insertion universelle sur l'étage supérieur | Majeur | Moyen | Moyen pour `PHY-2` | Ouvert |
| [`DT-20`](#dt-20--lascension-na-aucune-prise-sur-son-corps-central) | L'ascension n'a aucune prise sur son corps central | Majeur | Élevé | Moyen | Ouvert |
| [`DT-21`](#dt-21--w_apogee_overshoot-calibré-hors-de-son-domaine) | `W_APOGEE_OVERSHOOT` calibré hors de son domaine | Mineur | Moyen | **Élevé pour `PHY-2`** | Ouvert |

`*` Faible côté code — bloqué par la disponibilité d'un maillage externe, pas
par du travail de développement.

Aucun item de sévérité **Bloquant** n'a été trouvé.

---

## 3. Fiches

### DT-1 — Aucune analyse statique dans le build

**Corrigé le 2026-09-02.** Le build porte Spotless (`googleJavaFormat`, imports
triés) et PMD 7.16 avec `ignoreFailures = false` (`build.gradle:109`), sur un
ruleset maison : `errorprone` et `bestpractices` entières, moins 15 règles
écartées et 2 reconfigurées. `main` et `test` sont au vert (`5419f63`,
`4cf19e1`). Le « Mesuré » ci-dessous décrit l'état d'avant.

**Un résidu, documentaire.** `config/pmd/ruleset.xml` ne porte **aucun
commentaire de raison** sur ses 15 exclusions. Or trois d'entre elles ont été
écartées après qu'un lot les eut appliquées à la lettre et **cassé le code** :
`CompareObjectsWithEquals` a introduit 3 NPE, `CloseResource` a fermé un
`FileChannel` emprunté, `AvoidCatchingThrowable` a rendu l'éphéméride
silencieusement mortelle. Sans la raison à côté de la balise, la prochaine revue
de ruleset les réactivera.

**Mesuré.** `build.gradle` déclare `plugins { id 'java' }` et rien d'autre. Pas
de Spotless, pas d'ErrorProne, pas de PMD, pas de SonarQube. Pas de
`.github/workflows` non plus (`CLAUDE.md` le confirme : les tests sont lancés à
la main).

**Conséquence mesurée.** 208 lignes dépassent 100 caractères alors que le style
google-java-format est suivi partout ailleurs (indentation 2, enveloppement des
paramètres) : le style est respecté par discipline, pas par outil, donc il
dérive. La moitié des items **Mineur** de ce document seraient interdits à la
compilation par un outil coûtant vingt lignes de configuration.

**Inféré.** C'est l'item le plus rentable du document, parce qu'il est le seul
qui empêche les autres de revenir. Tous les autres corrigent le passé ; celui-ci
corrige le futur.

Détail du correctif proposé en [§6](#6-conseils--ne-pas-augmenter-la-dette).

---

### DT-2 — Squelette dupliqué sur les six `Analytic*Stage`

**`J0-D` 2026-09-02 — confirmé, à peine bougé.** Cumul re-mesuré à **2 059
lignes** contre 2 002 (`AnalyticGtoInjectionStage` 477, `AnalyticHohmannTransferStage`
432, `AnalyticParkingInsertionStage` 315, `AnalyticApogeeCircularizationStage` 298,
`AnalyticTrimBurnStage` 276, `AnalyticPlaneTrimAtNodeStage` 261). Le squelette
dupliqué est intact, et `AnalyticPlaneTrimAtNodeStage` — la sixième, la plus
récente — l'a recopié une fois de plus.

**Mesuré.** 2 002 lignes cumulées sur six classes de
`simulation/mission/stage/` : `AnalyticGtoInjectionStage` (469),
`AnalyticHohmannTransferStage` (423), `AnalyticParkingInsertionStage` (312),
`AnalyticApogeeCircularizationStage` (289), `AnalyticTrimBurnStage` (272),
`AnalyticPlaneTrimAtNodeStage` (237).

Les six exposent le même squelette, dans le même ordre :

```
configure(propagator, mission)
  → computeXxxPlan(state, vehicle)      // le seul vrai point de variation
  → addBurn(s)(propagator, state, plan, vehicle)
  → DateDetector(endDate) + handler { transitionToNextStage(s); STOP }

propagateStandalone(currentState, mission)
  → computeXxxPlan(...)                 // recalculé à l'identique
  → createOptimizationPropagator(burnLimitedMaxStep(...))
  → setInitialState + ReentryGuard.armQuiet
  → addBurn(s)(...)
  → propagate(fin du plan)
```

Les corps de `configure()` d'`AnalyticApogeeCircularizationStage`,
`AnalyticParkingInsertionStage` et `AnalyticTrimBurnStage` sont **identiques à un
nom de record près**. Les commentaires de `propagateStandalone()` sont
eux-mêmes recopiés (« 8×8 gravity, matching the ephemeris generator (bilan 11
§3.9)… », deux fois mot pour mot).

**Inféré.** Le correctif est un *template method* : une base
`AbstractAnalyticBurnStage<P>` portant `configure`, `propagateStandalone` et
l'installation du `DateDetector`, avec deux points d'extension — `computePlan()`
et `addBurns()` — plus la tolérance d'un `plan == null` signifiant « étape
sautée », cas que `AnalyticTrimBurnStage` et `AnalyticPlaneTrimAtNodeStage`
gèrent déjà chacun de leur côté. Gain estimé : 250 à 350 lignes.

**Le vrai gain n'est pas le volume.** C'est que l'invariant de pas d'intégration
documenté dans `CLAUDE.md` (« ne jamais passer un grand max step brut à un
propagateur qui hébergera une poussée ») est aujourd'hui réimplémenté six fois.
Il n'y a aujourd'hui aucune garantie mécanique que les six copies restent
d'accord.

**Risque.** Élevé : ces classes sont le cœur des trajectoires GEO. À ne pas
entreprendre sans baseline de non-régression préalable, conformément à la règle
« un changement de comportement à la fois ».

---

### DT-3 — Complexité cognitive de `MultiStageLoadOptimizer.minimize()`

**`J0-D` 2026-09-02 — aggravé, mesuré.** `minimize()` fait aujourd'hui
**245 lignes** (`MultiStageLoadOptimizer:229-473`) contre 225 à la photographie.
La méthode a grandi de 20 lignes pendant que la fiche qui la signale attendait.
Sa surcharge courte (`:210`) ne fait que déléguer.

**Mesuré.** `simulation/mission/runtime/MultiStageLoadOptimizer.java:204` —
225 lignes, complexité cognitive approximée à ~99 (le seuil Sonar par défaut est
15). C'est le pire point du dépôt, avec un facteur ~2 sur le suivant.

La méthode enchaîne : validation des arguments, sonde du point heuristique,
journalisation de démarrage, boucle de passes, balayage top-down des
coordonnées, critère d'arrêt sur tolérance, et construction du résultat.

**Les autres dépassements notables**, par complexité approximée décroissante :

| Méthode | Complexité ≈ | Lignes |
|---|---|---|
| `MultiStageLoadOptimizer.minimize()` | 99 | 225 |
| `MissionOrchestratorAppState.pollMissionActions()` | 35 | 33 |
| `CMAESTrajectoryOptimizer:297` | 38 | 197 |
| `MissionOptimizer.optimize()` | 26 | 199 |
| `StepLauncher` (constructeur) | 30 | 108 |

`pollMissionActions()` mérite d'être noté à part : 35 pour 33 lignes, c'est la
densité de branchement la plus forte du dépôt, dans une méthode appelée à chaque
frame.

**Inféré.** Découpage naturel de `minimize()` en `probeHeuristic()` /
`sweepPass()` / `refineCoordinate()`, sans changement de comportement. Le
découpage est purement structurel, mais la méthode pilote le dimensionnement des
ergols : baseline obligatoire.

---

### DT-4 — Singleton statique mutable `OrbitLabApplication.app`

**Corrigé le 2026-09-02.** Le champ n'existe plus. `Model3dAttacher`
(`engine/scene/body/lod/`) porte le seul besoin — l'`enqueue` sur le thread JME —
`OrbitLabApplication` l'implémente, `ApplicationContext` l'expose, et
`Model3dView` le reçoit par constructeur au lieu de le prendre au global.

**Une interface, et pas l'application passée en paramètre.** C'est le point de la
correction, et il dépasse la suppression du champ : donner le `SimpleApplication`
à une vue ferait dépendre `engine/` — la moitié générique — de la seule classe
qui assemble tout. L'abstraction est donc déclarée **du côté du consommateur**
(`engine/scene/body/lod/`) et implémentée par l'application. Vérifié après coup :
plus aucune classe d'`engine/` ne référence `OrbitLabApplication`, et les seules
dépendances d'`engine/` vers `app/` sont les objets-valeurs `app.view.*`
(`RenderContext`, `RenderTransform`, `AxisConvention`).

**Mesuré.** `OrbitLabApplication.java:49` déclare `public static
OrbitLabApplication app;`, affecté ligne 77. Un seul consommateur dans tout le
dépôt : `engine/scene/body/lod/Model3dView.java:57`, qui s'en sert uniquement
pour `enqueue(...)`.

**Pourquoi ça compte ici.** `CLAUDE.md` pose une règle explicite — toute
communication inter-états passe par `ApplicationContext`, jamais par
`getState()`. Ce champ est la même faille par une autre porte : un accès global
mutable qui court-circuite le conteneur. Il est d'autant moins justifiable qu'il
sert un seul besoin, très étroit.

**Correctif.** Exposer un point d'`enqueue` (ou un `Executor` du thread JME) sur
`ApplicationContext`, injecter dans `Model3dView`, supprimer le champ. Un seul
appelant à reprendre, risque faible.

---

### DT-5 — Classes et méthodes trop longues

**`J0-D` 2026-09-02 — la table ci-dessous est périmée, la conclusion tient.**
Le même instrument (SLOC hors commentaires) qui retrouve exactement 427 / 398 /
393 / 336 / 324 sur `b027d1d` mesure aujourd'hui :

| Classe | 2026-08-10 | 2026-09-02 |
|---|---:|---:|
| `TranslunarInjectionPlan` | *absente de la table* | **714** |
| `ui/…/step/StepParameters` | 393 | **619** |
| `ui/timeline/mission/MissionTimelineWidget` | *absente* | **514** |
| `optimizer/problems/TransferProblem` | 427 | **473** |
| `optimizer/CMAESTrajectoryOptimizer` | 398 | **464** |
| `ui/…/scenario/ScenarioBrowserWidget` | *absente* | **446** |
| `runtime/MultiStageLoadOptimizer` | 336 | 552 |
| `states/camera/OrbitCameraAppState` | 324 | sortie du haut de table |

**Trois des six têtes actuelles ne figuraient pas dans la table**, et la nouvelle
première — `TranslunarInjectionPlan`, née de `MIS-4` — pèse à elle seule 1,7 fois
l'ancienne. En revanche le nombre de méthodes de plus de 70 lignes n'a **pas**
bougé (21 à la photographie, 20 aujourd'hui, au même instrument) : le code a
grossi en largeur, pas en longueur de méthode. `OrbitLabApplication.simpleInitApp()`,
le cas à part de la fiche, est passé de 129 à 95 lignes.

**Mesuré.** 25 méthodes dépassent 70 lignes. Les classes les plus lourdes, par
SLOC hors commentaires :

| Classe | SLOC | Méthodes | Champs | Imports | Diagnostic |
|---|---|---|---|---|---|
| `optimizer/problems/TransferProblem` | 427 | 34 | **44** | 18 | Mélange configuration, bornes et fonction de coût pondérée. Extraire les poids dans un record, isoler le calcul de coût. |
| `optimizer/CMAESTrajectoryOptimizer` | 398 | 35 | 16 | 12 | Deux méthodes de 197 et 100 lignes. Les phases exploration/raffinement sont déjà nommées en bannières de commentaires — signe qu'elles veulent être des méthodes. |
| `ui/…/step/StepParameters` | 393 | **44** | 34 | 30 | Classe la plus couplée de l'UI. Le patron `params/DynamicParameters` existe déjà : y pousser davantage. |
| `runtime/MultiStageLoadOptimizer` | 336 | 35 | 13 | 6 | Voir [`DT-3`](#dt-3--complexité-cognitive-de-multistageloadoptimizerminimize). |
| `states/camera/OrbitCameraAppState` | 324 | 39 | 25 | 20 | Bindings d'entrée + machine d'état caméra + application de pose dans une seule classe. Sortir le mapping input. |

**Cas à part : `OrbitLabApplication.simpleInitApp()`**, 129 lignes. Ce n'est pas
une classe trop longue mais une séquence de câblage dont **l'ordre est
critique** — la contrainte est aujourd'hui portée par un commentaire (ligne 105 :
« Before FloatingOriginAppState, and that is a requirement, not a preference »).
Un découpage en `initViewports()` / `initStates()` / `initGui()` rendrait cet
ordre lisible dans le code au lieu de le confier à un commentaire que rien ne
protège.

**Couplage efférent** — plafond à 35 imports (`OrbitLabApplication`, légitime
pour un *composition root*), puis `MissionOptimizer` et `OrbitRuntimeAppState` à
34, ce qui l'est moins. Globalement sain : la discipline `ApplicationContext`
tient.

---

### DT-6 — Gestion d'exception trop large

**Corrigé le 2026-09-02**, les trois sites, chacun comme la fiche le demandait :
`BodyFile` ferme son canal sur un `catch (IOException | RuntimeException)` avec
`addSuppressed`, au lieu du transtypage qui serait devenu faux le jour où le
parser aurait déclaré une autre exception vérifiée ; `DatasetEphemerisSource`
journalise chaque échec de fermeture au lieu de l'avaler ; et le
`catch (Throwable)` du worker d'éphéméride est **conservé**, sa raison écrite en
Javadoc et un `@SuppressWarnings("PMD.AvoidCatchingThrowable")` posé pour que
l'analyseur ne le signale pas hors contexte — ce que la fiche prévoyait mot pour
mot.

**Mesuré.** 16 `catch (Exception e)` et 1 `catch (Throwable t)`. Trois sites
méritent une action, les autres sont des gardes de tâche asynchrone
défendables :

| Site | Problème |
|---|---|
| `source/BodyFile.java:85` | `catch (Exception e)` suivi de `if (e instanceof IOException ioe) throw ioe; throw (RuntimeException) e;`. **Correct aujourd'hui** — `EphemerisV1Parser` ne déclare que `IOException` — mais le jour où le parser déclare une autre exception vérifiée, une erreur de format se transforme en `ClassCastException`. Le pattern idiomatique est un drapeau `boolean ok` + `finally`. |
| `source/DatasetEphemerisSource.java:176` | `catch (Exception ignored) { // ignore }` dans `close()`. Un `logger.debug` suffit à rendre l'échec observable. |
| `ephemeris/EphemerisWorker.java:103` | `catch (Throwable t)` — **justifié**, c'est la garde de la boucle du worker, et elle journalise. À conserver, noté pour éviter qu'un futur analyseur ne le signale sans contexte. |

**Également mesuré, sévérité faible** : 12 `throws Exception` génériques,
intégralement concentrés dans `tools/ephemerisgen` et `tools/orbitgen` — code
outil hors application, hors du chemin de rendu et d'optimisation. Deux
`System.err.println` dans les `main` de ces mêmes outils, qui sont des messages
d'usage CLI et non de la journalisation détournée.

---

### DT-7 — Trou de couverture sur `simulation/mission/planner`

**`J0-D` 2026-09-02 — deux chiffres à corriger, dont un dans le bon sens.**
`simulation/mission/planner` compte **7 classes** et non 6, et **l'une d'elles est
désormais testée** (`PropellantSizingTest`) — le « aucun test » de la fiche est
faux depuis. Le trou reste réel sur les six autres. Côté UI : `ui/mission/panel`
compte **8** classes (et non 9), `ui/timeline/components` 5 et `ui/mission/display`
5, les deux derniers conformes.

**Mesuré.** Ratio test/main global de 0,39, correct pour ce type de projet. Mais
la répartition est très inégale :

- **`simulation/mission/planner` — 6 classes, aucun test.** C'est de la logique
  métier pure et récente.
- Toute l'UI Lemur est non testée (`ui/mission/panel` 9 classes,
  `ui/timeline/components` 5, `ui/mission/display` 5).

**Inféré.** Le second point est un arbitrage défendable — tester du rendu Lemur
coûte cher pour peu de valeur, et les règles métier de l'UI qui *méritaient*
d'être testées l'ont été (`MissionDisplayPanelRules`, `MissionPhaseShading`).
Le premier ne l'est pas : `planner` est exactement le genre de code que la suite
de tests existante sait couvrir, puisqu'elle couvre déjà `runtime`, `stage` et
`optimizer`.

---

### DT-8 — Violations de la règle de langue

`CLAUDE.md` : tout commentaire, toute Javadoc et toute chaîne de code sont en
anglais, sans exception ; seul `docs/` est en français.

**Corrigé le 2026-09-02 — et l'énumération ci-dessous sous-comptait.** Des quatre
sites listés, trois étaient déjà traités (`PlanetPresenter` et les deux messages
de log) et le quatrième, `WizardStepper:137`, avait été supprimé plutôt que
traduit. **Cinq sites que la fiche n'avait jamais relevés** restaient :

| Site | Traitement |
|---|---|
| `ui/mission/wizard/MissionWizardWidget.java:40` | Javadoc traduite |
| `ui/mission/wizard/WizardFooter.java:42` | Javadoc traduite |
| `ui/mission/wizard/WizardStepper.java:34` | commentaire traduit |
| `ui/…/wizard/step/params/DynamicParameters.java:258` | commentaire traduit |
| `ui/…/wizard/step/StepLaunchSite.java:219` | **supprimé** — il paraphrasait le `col.addChild(field)` qu'il précédait |

S'y ajoutait `under-dotée`, mot français dans de la prose anglaise, dans quatre
classes (`MinimizedLoadPlanner`, `MissionLoadEvaluator`, `PropellantLoadOptimizer`
et son test) — dont un **message d'exception**, donc une chaîne de code et pas un
commentaire. Remplacé par `under-resourced`.

**Laissé tel quel :** `ui/form/FormStyles.java:149` cite en français une phrase du
document de conception qu'il référence. C'est une citation attribuée, pas du code
écrit en français.

**Ce qu'aucun outil ne garde.** PMD n'a pas de règle de langue. Cette fiche
rouvrira sans avertissement, et c'est le seul item du document que `DT-1` ne
protège en rien — contrairement à ce qu'annonçait le §5.

| Site | Contenu |
|---|---|
| `engine/scene/planet/PlanetPresenter.java:56` | `// Convertir en JME units/axes selon le contexte SOLAR` |
| `ui/mission/wizard/WizardStepper.java:137` | `// Spacer du haut ≈ (CIRCLE_SIZE - CONNECTOR_HEIGHT) / 2 pour centrer verticalement` |
| `runtime/MultiStageLoadOptimizer.java:234` | message de log : `"…infeasible — mission under-dotée, nothing to shrink; aborting"` |
| `runtime/PropellantLoadOptimizer.java:169` | même message, dupliqué |

Les deux derniers sont aussi un cas de [`DT-11`](#dt-11--littéraux-dupliqués-et-todo-non-tracés).

---

### DT-9 — Code mort

**Corrigé le 2026-09-02**, et sans passe dédiée : `nearOrbitLayer` n'existe plus,
et `publishOpenWizard()` est appelée par le menu
(`MissionDisplayPanelAppState:201`). `UnusedPrivateField` et
`UnusedPrivateMethod` sont actives et bloquantes depuis `DT-1` : **c'est la seule
fiche de ce document dont l'outil interdit vraiment le retour.**

**Mesuré**, par référencement croisé sur `src/main` + `src/test`, références de
méthode `::` incluses :

- `states/mission/MissionDisplayPanelAppState.java:97` — `publishOpenWizard()`,
  méthode privée jamais appelée ni référencée.
- `engine/scene/graph/SceneGraph.java:52` — champ `nearOrbitLayer`, initialisé
  et jamais lu.

Aucune constante privée inutilisée. Huit autres candidats détectés
automatiquement se sont révélés être des références de méthode `::` — écartés
après vérification.

---

### DT-10 — Commentaires redondants

**`J0-D` 2026-09-02 — non re-mesuré, et c'est délibéré.** Le comptage des
paraphrases (42 sur 1 051) demande un jugement ligne à ligne qu'aucun instrument
ne rend ; le re-faire à la main sur un dépôt qui a grossi de moitié coûterait plus
que ce que la fiche vaut. Les trois exemples cités sont vérifiés : `ScrubberTrack:89`
(`// Playhead`) et `DisplayRow:46` sont exacts, `SimulationClockAppState:123`
(`// Reset speed`) aussi ; en revanche `OrbitCameraAppState:205` et
`MissionEphemerisGenerator:133` ne portent plus les commentaires cités.

**Mesuré, et c'est le point rassurant du document.** Sur 1 051 lignes de
commentaire situées dans des corps de méthode, **42 seulement** paraphrasent la
ligne de code qui suit. La densité globale est élevée — jusqu'à 66 % sur
`Vehicle.java`, 64 % sur `MissionStage.java` — mais elle porte du *rationale*,
exactement ce que `CLAUDE.md` demande. **Il ne faut pas y toucher.**

Les 42 se répartissent en deux familles :

1. **Étiquettes de section dans de longs constructeurs UI** (~30). Exemples :
   `// Playhead` devant `playhead = new Panel(...)` (`ScrubberTrack:89`),
   `// Color swatch` devant `Container swatch = ...` (`DisplayRow:46`),
   `// Divider 1/2/3` (`TimelineWidget:73,81,89`), `// Content pane` /
   `// Footer strip` (`MissionWizardWidget:130,136`).
   **Le correctif n'est pas de les effacer** : dans `ScrubberTrack` (99 lignes)
   et `ClockDisplay` (116 lignes) ils servent de repères de navigation. C'est
   d'extraire les blocs en méthodes nommées, le nom de méthode remplaçant le
   commentaire.
2. **Paraphrases pures**, supprimables telles quelles :
   `// Apply initial pose immediately` devant `applyCameraPose();`
   (`OrbitCameraAppState:205`), `// Add the final state of this stage as a
   sample point` devant `points.add(pointOf(...))`
   (`MissionEphemerisGenerator:133`), `// Reset speed` devant
   `addMapping(ACTION_SPEED_RESET, ...)` (`SimulationClockAppState:123`).

---

### DT-11 — Littéraux dupliqués et TODO non tracés

**Noms d'étapes : corrigé le 2026-09-02.**
`simulation/mission/stage/StageNames` porte `TERMINAL_COAST` et
`UPPER_SEPARATION` ; les six sites de `"Coasting"` et les deux de
`"S2 separation"` de `src/main` y renvoient. **Les littéraux des tests restent
littéraux** : dans un test, la chaîne *est* l'épinglage, et la remplacer par la
constante ferait passer un test qui n'assure plus rien.

**Deux mesures de la table ci-dessous sont fausses.** `"Coasting"` était compté à
7 occurrences ; il y en avait 8, dont **2 littéraux vivants** seulement — les six
autres étaient de la Javadoc, ou déjà des constantes privées, dans trois classes
qui ne se connaissaient pas (`LunarFlybyMission.FINAL_COAST_NAME`,
`LunarOrbitMission.FINAL_COAST_NAME`, `MissionLoadEvaluator.FINAL_COAST_STAGE`).
La dette n'était donc pas « en faire une constante » mais « trois constantes
existent et ne partagent pas de source ». Et `"S2 separation"`, présenté comme un
risque d'appariement, **n'est lu par personne** : ses deux sites vivants ne
servent qu'à l'affichage. Le seul nom réellement porteur est le coast terminal,
que `MissionLoadEvaluator` compare pour choisir les échantillons notés — une
faute de frappe n'y échouerait pas, elle noterait la mission sur zéro échantillon.

**Le reste de la table n'est gardé par rien.** `AvoidDuplicateLiterals` est
écartée du ruleset (bruit sur les API publiques) : `"background"`,
`"btn-primary"` et `"stepSeconds must be finite and > 0"` restent ouverts, et
`DT-1` n'y change rien.

**TODO** — `OrbitLineFactory:131` est désormais tracé `TODO(DT-11)`. Les deux
autres ne le sont pas.

**Littéraux dupliqués** (règle S1192) :

| Littéral | Occurrences | Remarque |
|---|---|---|
| `"background"` | 8 | Style Lemur |
| `"stepSeconds must be finite and > 0"` | 7 | Message de validation |
| `"Coasting"` | 7 | **Nom d'étape** |
| `"btn-primary"` | 6 | Style Lemur |
| `"S2 separation"` | 5 | **Nom d'étape** |

Les noms d'étape sont les seuls qui portent un vrai risque : ils servent à
l'appariement d'étapes, et une faute de frappe y échouerait silencieusement.
Ils devraient être des constantes.

**TODO non tracés** (3) — aucun ne porte de référence de roadmap :

- `engine/scene/OrbitLineFactory.java:125` — « call sampleIcrfSafe instead of sampleIcrf »
- `simulation/orbit/OrbitPathCache.java:103` — « only propagate from the last ephemeris point » (gain de performance potentiel)
- `states/camera/FloatingOriginAppState.java:65` — « Get camera from context » (dette `ApplicationContext`, même famille que [`DT-4`](#dt-4--singleton-statique-mutable-orbitlabapplicationapp))

---

### DT-12 — Mesh Ariane 6 absent (Ariane 5 utilisé à la place)

**Corrigé le 2026-09-09, en deux temps.** `AST-1` a livré le maillage Ariane 64
manquant ; `PHY-8 / L4` a fait pointer `LauncherAssets` dessus en remplaçant
`ARIANE_62` par `ARIANE_64` au catalogue ; `PHY-8 / L5` a nettoyé la Javadoc qui
décrivait encore le défaut. Il n'y a plus d'Ariane 5 nulle part.

**Le second volet est fermé aussi, et par une mesure.** La fiche notait que la
convention de maillage — nez sur `+Y`, échelle « ~1 unité » — n'avait jamais été
vérifiée, seulement supposée par la chaîne de chargement. Elle l'est désormais :
`LauncherMeshProportionTest` mesure les deux piles à **1,0000 unité exactement,
base à l'origine**, et c'est ce qui autorise `L5` à ne porter qu'**un seul
nombre par lanceur** — sa hauteur — pour que chaque pièce du lot sorte à sa
fraction juste.

**Ce qui reste n'est plus ce que cette fiche décrit** : le maillage est bien une
Ariane 6, mais ses propulseurs sont trop gros. Cela s'ouvre en
[`DT-18`](#dt-18--propulseurs-de-lariane-64-surdimensionnés-dans-le-maillage),
avec le chiffre.

---

### DT-13 — Isp catalogue déjà en double-comptage latent avec la traînée à venir

**Mesuré.** [`atmosphere/05-conception-L2.md`](atmosphere/05-conception-L2.md)
§4.3 : les Isp « moyenne de trajectoire » du catalogue (296 s Falcon Heavy S1,
300 s Ariane 62 S1) absorbent déjà une perte de traînée implicite chiffrée à
**408 m/s** et **671 m/s** respectivement — au-dessus des 100-300 m/s que
l'étude d'impact originale de l'atmosphère prévoyait comme plage réelle.

> **Les deux chiffres ont changé, et pas du même montant** (`PHY-8`,
> [`etagement/07-cloture.md`](etagement/07-cloture.md) §6). Ce qu'il faut lire
> aujourd'hui : **396 m/s** sur le Falcon Heavy, **64 m/s** sur l'Ariane 64.
>
> Côté Ariane, `L4` a dissous l'essentiel : les 671 m/s étaient surtout l'artefact
> d'un solide honnête et d'un cryogénique endetté fondus dans un même proxy de
> 300 s. Éclatés, les quatre P120C volent leur Isp de vide et ne portent **rien** ;
> le Vulcain cède 71 s sur les 21 % du débit qu'il détient, et c'est toute la dette.
> Elle est donc **localisée** au lieu d'être diluée — ce que le §3.4 du découpage
> annonçait — et dix fois plus petite qu'écrit ici.
>
> Côté Falcon, rien n'a été dissous : ses deux entrées déclarent le même moteur à
> 296 s, l'éclatement ne pouvait rien y localiser. Les 12 m/s perdus viennent
> d'ailleurs — la réserve d'insertion de [`DT-19`](#dt-19) alourdit l'étage
> supérieur, donc le rapport de masses du premier étage. C'est de l'arithmétique
> sur une pile plus lourde, pas un changement de ce que le proxy cache.
>
> **La dette est donc devenue asymétrique entre les deux lanceurs**, dans un rapport
> de six. `PHY-2` ne peut plus les calibrer d'une seule passe, et `J2` arbitrera sur
> ces chiffres-ci.

**Pourquoi c'est critique pour `PHY-2`, pas pour aujourd'hui.** Tant que la
traînée reste **off** par défaut (`PHY-1`), cette dette est invisible. Le
jour où `PHY-2` l'active par défaut, la traînée réelle s'ajoutera à une Isp
qui la compense déjà partiellement — double-comptage garanti si personne ne
l'arbitre avant.

**Inféré.** Ré-étalonnage du catalogue à faire **avant** ou **pendant**
`PHY-2`, pas après : c'est exactement le genre de correction qui doit
précéder le recalibrage global de l'optimiseur que `PHY-2` prévoit déjà,
plutôt que de s'y ajouter comme un second passage.

**Tranché en J2 le 2026-09-10 — proxy conservé, re-calibré « lapse seule ».** Le
catalogue garde une Isp moyenne unique sur les deux étages qui la portent encore (bloc
bas Falcon Heavy 296 s, Vulcain 360 s), mais sa **valeur** ne représentera plus que la
baisse d'Isp par contre-pression — le lapse sol/vide, physique et réelle — la traînée
devenant explicite au lieu d'être cachée dans l'Isp. Retenu contre « Isp de vide
partout », qui fausse le Vulcain (spread [320, 431] = 111 s, contre 29 s pour le bloc
FH), et contre une Isp dépendante de la pression — correcte mais qui demande un modèle
de poussée neuf, `PropulsionSystem` étant un `record (isp, thrust)` figé, soit un item à
part. Les pertes gravitationnelles et de pilotage restent portées par la propagation,
pas par l'Isp : pas de double-comptage de ce côté. **Le nombre reste à poser par
`PHY-2`**, traînée en main : J2 tranche la direction, pas la valeur, le vol drag-on
étant infaisable aujourd'hui ([`atmosphere/05-conception-L2.md`](atmosphere/05-conception-L2.md) §4.2).

---

### DT-14 — Écart Harris-Priester / NRLMSISE-00 non arbitré

**Mesuré.** [`atmosphere/05-conception-L2.md`](atmosphere/05-conception-L2.md) :
22,6 % d'écart de densité mesuré entre les deux modèles d'atmosphère déjà
implémentés par `PHY-1`, sur les cas testés. Aucun des deux n'a été retenu ou
écarté comme référence.

**Inféré.** Sans arbitrage écrit, le choix du modèle par défaut au moment de
`PHY-2` sera fait dans l'instant plutôt que sur la base de cette mesure déjà
disponible.

**Tranché en J2 le 2026-09-10 — un modèle par usage, l'optim toujours HP.** L'optimiseur
utilise **toujours Harris-Priester** quand la traînée est allumée — bon marché (HP
×1,35–1,92 contre NRLMSISE ×3,73–4,03 par propagation, sur une optim déjà lente) — et le
**runtime** vole le modèle de la mission. La règle, par valeur : `NONE` → rien des deux
côtés, ce qui **préserve l'invariant** `PHY-1` « drag off ⇒ identique au bit » via le
chemin `!hasDrag()` existant ; `HARRIS_PRIESTER` → HP à l'optim et au runtime ;
`NRLMSISE` → **HP à l'optim, NRLMSISE au runtime**. Défaut runtime de `PHY-2` : NRLMSISE
(le palier « Réaliste » du sélecteur PHY-3), HP disponible en « Statique ».

**Correction au cadrage de la roadmap.** L'atmosphère **n'est pas** choisie par type de
propagateur aujourd'hui : optim et runtime lisent tous deux `context.drag().model()`
(`OrekitService.addDrag`). « Cohérent avec le 8×8/50×50 déjà en place » est donc faux au
niveau du câblage. **`PHY-2`
hérite** : une ligne neuve — substituer NRLMSISE→HP dans la *factory* d'optimisation,
active **uniquement** si `hasDrag()` — plus la mesure du biais directionnel de 22,6 %
(l'optim HP, moins sévère, sous-provisionne face au runtime NRLMSISE ; ordre de 22–68 m/s
sur ~9 400, dans la tolérance ±7 %) et une marge à l'optim si nécessaire.

---

### DT-15 — `Cd` catalogue S2 hors domaine de validité déclaré

**Mesuré.** [`atmosphere/05-conception-L2.md`](atmosphere/05-conception-L2.md)
§4.2 : le coefficient de traînée catalogue des seconds étages (`Cd = 2,2`,
régime d'écoulement libre-moléculaire) n'est déclaré valable qu'au-dessus de
70 km. Le seul profil réel testé allume S2 à **58 km**, en écoulement
continu — hors de ce domaine.

**Inféré.** Sans conséquence tant que la traînée reste off par défaut ; à
traiter avant que `PHY-2` fasse voler ce coefficient en production.

**Tranché en J2 le 2026-09-10 — assumer 2,2, re-vérifier en `PHY-2`.** Le `Cd = 2,2` est
conservé : il est **correct pour le domaine réel** de l'étage supérieur — sa vie en
orbite, en écoulement libre-moléculaire, où la décroissance compte. Le passer en 0,4
continu « pour régler le 58 km » la sous-estimerait d'un facteur ~5,5, contre l'objet
même du chantier atmosphère (MIS-10). Et le seul airstart bas mesuré (58 km) vient d'une
**ascension cassée** ([L2 §4.2](atmosphere/05-conception-L2.md) : budget non
redimensionné, le S2 s'allume trop bas faute d'atteindre l'orbite). **`PHY-2` hérite** :
re-mesurer l'altitude d'airstart du S2 sur l'ascension reprovisionnée — celle que produit
la recalibration `DT-19`/`DT-20`/`DT-21` — et n'escalader vers un **Cd par régime** (0,4
continu sous ~90 km, 2,2 au-dessus) **que si** l'airstart y reste en continu. Ne rien
construire avant de savoir que c'est nécessaire.

---

### DT-16 — `nRev` du solveur de Lambert figé à 0 partout

**`J0-D` 2026-09-02 — vrai sur le fond, faux sur la forme.** Le mot `nRev`
**n'existe nulle part** dans le dépôt : le zéro est un argument positionnel, à
**un seul site**, `TranslunarInjectionPlan:1131` (`.solve(posigrade, 0, conditions)`).
« Figé à 0 partout » suggère une dispersion qui n'existe pas — c'est un littéral
unique, ce qui rend la fiche plus facile à traiter qu'elle ne le dit.

**Mesuré.** [`lunar-flyby/01-decoupage.md`](lunar-flyby/01-decoupage.md) §6,
répété à l'identique dans
[`lunar-orbit/01-decoupage.md`](lunar-orbit/01-decoupage.md) §6 :
`TranslunarInjectionPlan` et les autres appelants figent `nRev = 0` en dur.
Le paramètre est natif au solveur mais aucun chemin de production ni aucun
test n'exerce `nRev ≥ 1`.

**Inféré.** Code mort en pratique côté multi-révolution. Risque nul tant que
rien ne demande une trajectoire multi-révolution — pertinent si `MIS-6`
(rendezvous) ou une mission ultérieure en a besoin.

---

### DT-17 — Performance du ruban (`RND-4`) jamais mesurée

**Mesuré.** [`graphics-effects/ribbon-lines.md`](graphics-effects/ribbon-lines.md)
§13 : les chiffres d'allocation et de coût par frame avancés pour le
matériau `Ribbon` sont de l'arithmétique sur des tailles de buffer supposées,
pas un profiling réel en conditions d'usage.

**Inféré.** Faible risque immédiat — la session de 24 minutes citée par
`RND-4` n'a montré aucune exception ni ralentissement perçu — mais aucun
chiffre ne garantit que ça tienne à un nombre de trajectoires simultanées
plus élevé (typiquement, plusieurs missions actives à la fois).

---

### DT-18 — Propulseurs de l'Ariane 64 surdimensionnés dans le maillage

**Successeur de [`DT-12`](#dt-12--mesh-ariane-6-absent-ariane-5-utilisé-à-la-place)**,
qui est fermé : le maillage *est* une Ariane 6. Ce sont ses proportions qui ne
le sont pas.

**Mesuré, et sans aucune source externe.** Les sections du catalogue donnent les
vrais diamètres — 9,08 m² pour un P120C et 22,9 m² pour le LLPM, soit 3,40 m et
5,40 m, un rapport de **0,630**. Le maillage donne 0,0798 et 0,0925 en unités
normalisées, soit **0,863**. Le propulseur est donc **37 % trop large**.

Le même contrôle passe sur le Falcon Heavy — 0,995 mesuré contre 1,000 déclaré,
ses trois corps étant identiques — ce qui dit que la méthode est bonne et que le
défaut est local.

**En longueur aussi.** Le propulseur court sur 0,3624 de la pile, soit **22,8 m**
à 63 m, là où un P120C fait 13,5 m. Aucune hauteur ne réconcilie les deux :
atteindre 13,5 m demanderait une Ariane de **37 m**. Ce n'est donc pas une
erreur d'échelle mais de modèle, et `L5` ne pouvait pas la corriger en
choisissant une hauteur.

**Ce qui tombe juste**, et qui délimite le défaut : la coiffe (0,3264 → 20,6 m
contre 20 m réels), le corps (0,0925 → 5,83 m contre 5,40 m déclarés, l'excès
étant ce qui pend autour), et tout le lot Falcon.

**Épinglé.** `LauncherMeshProportionTest` fige l'écart **et non l'accord** : il
passe au vert sur la valeur fausse. Remplacer le maillage le rend rouge, ce qui
oblige à revenir mettre à jour cette fiche.

**Inféré — pourquoi c'est dû avant `PHY-5`.** Aujourd'hui la pile est dessinée
d'un bloc et l'erreur se voit à peine. `PHY-5` fera voler un propulseur largué
**à côté** du corps : un cylindre de 22,8 m sur 4,95 m flottant près d'un corps
de 36 m, quand il devrait faire 13,5 sur 3,40. Corriger après coup demanderait
de re-régler ce que `PHY-5` aura calibré autour de la mauvaise taille.

**Bloqué par un actif externe** — un ré-export du maillage — pas par du code.

---

### DT-19 — Réserve d'insertion universelle sur l'étage supérieur

**Mesuré.** `PropellantBudget.sizeTopStage` ajoute **1 300 m/s** de capacité au
sommet de ce que la chaîne de ΔV idéal lui donne. Le nombre est le **pire cas**
observé : ce que le transfert réclame sur un Falcon Heavy au corps étranglé, à la
plus petite charge d'étage supérieur qui ferme la mission. Un profil qui remet
correctement les commandes dépense **430 m/s** au transfert et **6** au trim.

**Pourquoi elle existe quand même.** Sans elle, l'étage supérieur du profil
`falcon-heavy-leo-400` portait 1 963 kg, soit **448 m/s**, contre les 436 que la
mission dépense — douze mètres par seconde de marge, et seulement parce que la
trajectoire tombait juste. `L0` avait mesuré la cause sans en tirer la conséquence :
sur ce profil, l'étage 0 fait **100 %** de l'ascension et l'étage supérieur ne
s'allume jamais.

**Pourquoi additive et non plancher.** Un `max(raw, plancher)` a été mesuré d'abord et
rejeté : il clampe tout profil sous le plancher sur un même nombre, et les
dimensionnements **polaire et plein-est** de la même mission sortaient identiques —
`8 373,838899728531` kg des deux côtés. C'est exactement la propriété que `MIS-7` a
construite, dont le Javadoc chiffre une erreur de 529 m/s à des tonnes de charge.

**Le coût, chiffré.** Toutes les missions budgétées paient le pire cas : +18 à +66 %
de charge d'étage supérieur. Effets de bord mesurés — la dette d'Isp du Falcon Heavy
que `DT-13` porte tombe de **408 à 396 m/s** (le rapport de masses du premier étage
change), et le profil MEO de `CentralBodyBaselineTest` a dû être ré-enregistré.

**Voie de sortie.** Le nombre juste est le ΔV que l'insertion de *cette* mission
demandera, et le dimensionnement ne connaît pas l'état de remise des commandes. Un
dimensionnement en **deux passes** — dimensionner, voler, redimensionner — le donnerait
exactement et supprimerait cette fiche.

---

### DT-20 — L'ascension n'a aucune prise sur son corps central

**Mesuré, dans `GravityTurnManeuver.plan()`.** Des trois durées du plan d'ascension,
une seule dépend d'une variable d'optimisation :

```
burn1Duration    = getBurn1Duration()      // jusqu'à épuisement des propulseurs
coreBurnDuration = getCoreBurnDuration()   // jusqu'à épuisement du corps
burn2Duration    = max(0, transitionTime − stagingCompleteTime)
```

Le corps central du Falcon Heavy est déclaré `ShutdownMode.COMMANDED` au catalogue, et
le code le brûle **toujours** jusqu'au plancher de déplétion. Sur le profil budgété,
l'optimiseur se pose exactement sur `stagingCompleteTime` — `181,8066` contre `181,83`
— avec `burn2 = 0` : il ne lui reste **qu'une variable effective**, l'exposant de
tangage. Le Javadoc de `stagingCompleteTime` le nomme déjà : *« it is now the edge of a
useless plateau »*.

**Et une barrière interdit la région utile.** `computeCost` ajoute
`STAGING_PENALTY_BASE = 1e3` dès que `transitionTime < stagingCompleteTime`, contre un
coût nominal de 73,76 — donc la région est inatteignable. Or c'est là que sont les
bonnes remises : `transitionTime = 170` avec un exposant de `0,634` rend un apogée de
**407,9 km**, la cible, contre 4 596 km à l'optimum retenu. La barrière est légitime
tant que le corps n'est pas commandable — sous le plancher, le véhicule volait la même
trajectoire — et cesse de l'être dès qu'il le devient.

**Ce qui a été mesuré, et ne suffit pas seul.** Rendre le corps commandable **sans**
lever la barrière ne change rien : coût `73,76361806179528` contre `73,7636180617952`,
même optimum. Lever la barrière **avec** le corps commandable fait cesser la levée mais
n'insère pas — `72 551 × 400 178 m`, étage supérieur vide, trim à zéro. Il manque un
rééquilibrage de la fonction de coût, ce qui renvoie à [`DT-21`](#dt-21).

**Inféré.** Aucune urgence propre : le chantier a fermé ses six cellules par le
dimensionnement ([`DT-19`](#dt-19)) sans toucher à l'ascension. Mais tout lot qui
voudra faire mieux qu'un étage supérieur sur-provisionné passera par ici.

**Correction du 2026-09-10 — « aucune urgence propre » ne vaut que pour le profil
budgété.** Le dimensionnement compense l'**analytique** (les six cellules) ; il ne
compense **pas** le **transfert optimisé** (`OptimizationType.BALANCED`/`PRECISE`), que le
chantier n'a jamais lancé — son test est hors-gate (`orbitlab.slowTests`). Vol réel du
2026-09-10 : `LEOMissionOptimizedTransferTest.testFalconHeavyOptimizedTransfer` rend
**416 × 1271 km** pour une cible 400 circulaire (l'ascension sur-délivre, le transfert
prograde ne rabaisse pas l'apogée). C'est une **casse dure**, pas une dette qui dégrade
doucement — [`BUG-25`](bugs.md#bug-25--falcon-heavy-ne-vole-plus-en-transfert-optimisé-depuis-phy-8).
L'urgence de ce chemin n'est donc plus « aucune ». Le traitement reste néanmoins avec
`PHY-2` (le rééquilibrage de [`DT-21`](#dt-21) se refait de toute façon traînée en main),
et la mesure **confirme** le verdict ci-dessus : ni le cœur commandable ni le poids ne
suffisent seuls.

---

### DT-21 — `W_APOGEE_OVERSHOOT` calibré hors de son domaine

**Mesuré.** Le poids d'un apogée au-dessus de la fenêtre vaut **0,5**, contre **8,0**
pour un apogée en dessous. Le Javadoc justifie l'asymétrie ainsi : *« an apogee past it
is absorbed by the trim burn at the next apside for nothing measurable »*, et la mesure
citée porte sur des remises **distantes de 91 km** en apogée, arrivant toutes deux à
400,128 km.

À **4 596 km** d'apogée — ce que le Falcon Heavy étranglé rend sur le profil budgété —
on est **cinquante fois** hors de cette mesure, et la prémisse est fausse : aucun trim
n'absorbe onze fois la cible.

**Conséquence observée.** L'optimiseur préfère un dépassement massif à tout déficit,
puisqu'un apogée court est facturé seize fois plus cher. Rendu libre de couper son
corps central, il **refuse de le faire** pour cette raison.

**Inféré, et pourquoi ce n'est pas une correction à faire à la légère.** Le même
Javadoc consigne ce qui s'est passé quand le poids valait 3,0 : le plafond a surenchéri
sur le terme de pente et acheté une remise **148 km sous la cible**. Ce poids touche
tous les profils du dépôt, et `PHY-2` va de toute façon rouvrir la calibration de
l'ascension — c'est là qu'il faut le reprendre, avec les mesures de traînée en main
plutôt qu'avant.
---

## 4. Ce qui est sain

À consigner autant que le reste, pour ne pas dégrader ce qui tient :

- **La discipline `ApplicationContext` tient.** Aucun `getState(Class)` de
  communication inter-états. Le couplage efférent plafonne à 34 imports hors
  *composition root*.
- **La règle `Optional` de `CLAUDE.md` est respectée sans exception.** Les deux
  seules occurrences du dépôt sont des types de retour.
- **La densité de commentaires est un actif, pas une dette** (cf. `DT-10`).
- **Aucun item bloquant** : pas de fuite de ressource, pas de `catch` vide, pas
  de comparaison de `String` par `==`, pas de ternaire imbriqué, pas de champ
  public mutable hors le cas unique de `DT-4`.
- **Les décisions non évidentes sont documentées avec leur mesure** (références
  « bilan 08 §3.1 », « bilan 11 §3.9»…). C'est ce qui rend ce dépôt reprenable.

**Faux positifs écartés après vérification**, à ne pas re-signaler : le seul
`catch (InterruptedException)` restaure bien le flag d'interruption ; les
`ExecutorService` de `MissionRenderer` et `PlanetPoseAppState` sont *empruntés*
à `AssetFactory` et non possédés, donc leur absence de `shutdown` est correcte ;
les 3 comparaisons `double ==` sont des tests « exactement zéro » sur des
sentinelles.

---

## 5. Ordre d'attaque proposé

Par ratio impact/risque décroissant :

1. **`DT-1`** — **fait le 2026-09-02**, et l'annonce était trop large : PMD n'a
   aucune règle de langue, donc `DT-8` n'est gardé par rien, et
   `AvoidDuplicateLiterals` a dû être écartée, donc `DT-11` non plus. Il fige
   `DT-9`, et Spotless fige les 208 dépassements de largeur.
2. **`DT-9`, `DT-8`, `DT-4`** — **faits le 2026-09-02**.
3. **`DT-6`** — **fait le 2026-09-02**, les trois sites.
4. **`DT-7`** — tests sur `planner`, aucun risque de régression par
   construction, et prérequis utile aux deux suivants.
5. **`DT-2`** puis **`DT-3`** — les deux gros chantiers, à faire **après** avoir
   établi une baseline de non-régression, un changement de comportement à la
   fois.

> **`J0-D` 2026-09-02.** Les fiches `DT-13`, `DT-14`, `DT-15` et `DT-17` n'ont pas
> été re-mesurées : elles portent sur des arbitrages d'atmosphère et une mesure de
> performance qui n'existent pas encore dans le code, donc rien n'a pu y dériver.
> `DT-16` l'a été et sa forme est corrigée dans sa fiche.

`DT-5`, `DT-10` et le reste de `DT-11` se traitent opportunément, au fil des
passages dans les fichiers concernés (cf. règle de la trace en
[§6](#6-conseils--ne-pas-augmenter-la-dette)).

**`DT-12` à `DT-17` ne rentrent pas dans ce classement** — ils viennent d'une
revue documentaire, pas de la même mesure de code, et leur urgence dépend
d'un chantier pas encore commencé plutôt que d'un ratio impact/risque
immédiat. Repère simple : `DT-13`, `DT-14`, `DT-15` sont à trancher **avant
ou pendant `PHY-2`** (ils s'aggravent silencieusement sinon) ; `DT-16` n'a
aucune urgence propre ; `DT-17` se vérifie au premier profiling venu, sans
chantier dédié. `DT-12` est fermé.

**`DT-18` est le seul à porter une échéance nommée.** Il ne vient pas de la
revue documentaire mais d'une mesure faite par `PHY-8 / L5`, et il est **dû
avant `PHY-5`** : c'est ce lot qui fera voler un propulseur largué à côté de son
corps, et le calibrer autour d'une pièce trop grosse coûterait un second
réglage.

**`DT-19` à `DT-21` se traitent avec `PHY-2`, et dans cet ordre.** `DT-21` d'abord,
parce que `PHY-2` rouvre de toute façon la calibration de l'ascension et que ce poids
s'y reprend avec les mesures de traînée en main. `DT-19` ensuite : la réserve
sur-provisionne, mais elle tient et son remplacement — un dimensionnement en deux
passes — est un travail à part entière. `DT-20` en dernier : rien ne le presse tant
que le dimensionnement compense, et il ne se traite pas seul, la barrière d'étagement
et le poids de `DT-21` étant du même arbitrage.

---

## 6. Conseils : ne pas augmenter la dette

Cette section est la plus importante du document. Les sections précédentes
décrivent un passé rattrapable ; celle-ci décide si le même état sera à
réécrire dans six mois.

### 6.1 Rendre les règles mécaniques plutôt qu'intentionnelles

Le constat central de `DT-1` : **ce dépôt suit déjà de bonnes conventions, mais
aucune n'est vérifiée par une machine.** Le style google-java-format est
respecté partout — et dérive quand même sur 208 lignes. Toute règle qui repose
sur la seule vigilance humaine finit par produire exactement ce type de résidu.

Trois paliers, du moins cher au plus complet :

**Palier 1 — le formatage (quinze minutes, aucun risque).** Ajouter Spotless
avec `googleJavaFormat()` à `build.gradle`, lancer `spotlessApply` une fois, et
commiter le résultat en un commit isolé « formatage seul » pour qu'il ne pollue
aucune revue. Ensuite `spotlessCheck` échoue sur toute dérive.

**Palier 2 — les bugs réels (une heure).** ErrorProne, en mode avertissement
d'abord pour mesurer le bruit, puis erreur sur les catégories qui font
consensus. Il attrape des choses qu'aucune relecture ne voit de façon fiable.

**Palier 3 — la dette structurelle.** SonarQube ou PMD, avec des seuils fixés à
ce que le code fait *aujourd'hui* plutôt qu'à l'idéal — un seuil qu'on ne peut
pas atteindre est un seuil qu'on désactive. Le but n'est pas d'atteindre zéro,
c'est d'interdire la progression.

Vérifier les versions au moment de l'ajout ; ce document ne les fige pas
volontairement.

### 6.2 Le seuil qui compte : le ratio, pas la valeur absolue

Un plafond dur du type « pas plus de 300 lignes par classe » se contourne en
coupant arbitrairement, ce qui produit deux classes incohérentes au lieu d'une
longue. La règle utile est différente :

> Quand une classe dépasse **300 SLOC** ou **25 méthodes**, ce n'est pas une
> erreur — c'est une **question** : cette classe a-t-elle encore une seule
> responsabilité ? Si la réponse est oui, on documente pourquoi. Si elle est
> non, on découpe **avant** d'ajouter la fonctionnalité qui a déclenché la
> question.

`TransferProblem` avec ses 44 champs et `StepParameters` avec ses 44 méthodes
sont passées par ce seuil sans que personne pose la question.

### 6.3 La règle des trois copies

`DT-2` n'est pas né d'une décision : il est né de six décisions raisonnables
prises isolément. Copier `AnalyticParkingInsertionStage` pour écrire
`AnalyticTrimBurnStage` était le bon choix la première fois. La sixième, non.

> À la **troisième** copie d'un squelette, on extrait la base avant d'écrire la
> troisième. Pas après.

Le signal concret à surveiller : quand on se surprend à **copier aussi le
commentaire** — comme le bloc « 8×8 gravity, matching the ephemeris generator
(bilan 11 §3.9) » recopié mot pour mot — c'est que l'abstraction manquante est
déjà identifiée, elle n'est simplement pas encore écrite.

### 6.4 Le commentaire qui dit *quoi* est un nom de méthode qui manque

Le dépôt écrit d'excellents commentaires de *rationale* — le **pourquoi**, ce
qu'aucun nom ne peut porter. C'est un actif, il faut le préserver tel quel. La
distinction opérationnelle :

| Le commentaire dit… | Alors… |
|---|---|
| **pourquoi** (contrainte physique, décision mesurée, piège Orekit) | on l'écrit, on le développe, on cite la mesure. C'est ce que fait déjà ce dépôt. |
| **quoi** (« Apply initial pose », « Color swatch », « Reset speed ») | c'est un nom de méthode ou de variable qui manque. On extrait au lieu de commenter. |

Corollaire pour les constructeurs UI : une bannière de section dans un
constructeur de 100 lignes signale une méthode privée `buildXxx()` en attente.

### 6.5 Un accès global n'entre jamais « juste pour ce cas »

`DT-4` est un champ `public static` justifié par un seul besoin étroit —
`enqueue` depuis `Model3dView`. C'est toujours ainsi que ça commence, et c'est
pourquoi l'exception ne doit pas être accordée : le coût d'ajouter le point
d'entrée sur `ApplicationContext` est de quelques lignes, le coût de retirer un
accès global qui a essaimé se compte en jours.

> Aucun nouveau `static` mutable, aucun nouveau `getState(Class)`. Si
> `ApplicationContext` n'expose pas ce dont on a besoin, on l'ajoute à
> `ApplicationContext`.

### 6.6 Un TODO sans identifiant est un TODO qui ne sera pas fait

Les trois TODO du dépôt sont anonymes et non datés. Deux décrivent un vrai
travail (`OrbitPathCache:103` est un gain de performance identifié).

> Un `TODO` porte une référence de roadmap ou de bug (`// TODO(RND-4): …`), ou
> il n'existe pas. S'il ne vaut pas un item, il ne vaut pas une ligne.

### 6.7 Tester ce qui décide, pas ce qui affiche

L'arbitrage implicite du dépôt est bon et mérite d'être explicite :

> La logique métier pure — `simulation/**`, et les règles extraites de l'UI
> comme `MissionDisplayPanelRules` — arrive **avec** ses tests. Le rendu Lemur
> et JME n'en a pas ; en contrepartie, toute règle de décision qui s'y glisse
> est extraite dans une classe testable.

`simulation/mission/planner` (`DT-7`) est le contre-exemple : six classes de
décision pure livrées sans test. C'est le seul endroit où la règle a été
enfreinte, ce qui prouve qu'elle est tenable.

### 6.8 La règle de la trace

Les items `DT-5`, `DT-10` et une partie de `DT-11` ne justifient pas de chantier
dédié. Ils se traitent ainsi :

> Quand on modifie un fichier pour une autre raison, on corrige au passage la
> dette **mineure** qu'il contient — paraphrase, littéral à extraire, ligne trop
> longue — et **rien d'autre**. Une correction structurelle, elle, mérite son
> propre commit.

C'est ce qui empêche un document de dette de rester un inventaire.

### 6.9 Baseline avant tout refactoring de trajectoire

Règle déjà appliquée dans ce dépôt, consignée ici parce qu'elle conditionne
`DT-2` et `DT-3` : sur le chemin optimisation/propagation, **on mesure avant, on
change une chose, on remesure**. Un refactoring « purement structurel » sur ce
chemin ne l'est jamais tout à fait — un ordre d'opérations flottantes qui change
suffit à déplacer un optimum.

---

## 7. Entretien de ce document

Ce document est daté et le restera : il photographie un commit. Le relancer
demande de rejouer les mesures de [§1](#1-périmètre-et-méthode), ce qui n'est
pas automatisable en l'état — encore une raison de traiter `DT-1` en premier.
Une fois l'outillage en place, la majorité de ce document devient un tableau de
bord généré, et il ne restera ici que ce qu'aucun outil ne sait dire : `DT-2`,
`DT-7` et la [§6](#6-conseils--ne-pas-augmenter-la-dette).
