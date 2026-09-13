# OPT-1 — Temps de calcul des trajectoires — découpage

Item roadmap : `OPT-1` (★4 ◆3 L), v2, **prioritaire, sans dépendance dure**. Ce document
ne conçoit pas en détail : il **découpe**. Chaque lot y est défini par la propriété qu'il
rend vraie et par ce qui la prouve.

La fiche est dans [`roadmap/02-roadmap-v2.md`](../roadmap/02-roadmap-v2.md) §OPT-1 ; elle
consigne des **pistes** et, dans ses propres termes, « n'en tranche aucune ». Le présent
document tranche. Les mesures de référence vivent dans
[`optimization/bilan.md`](bilan.md) et [`optimization/run 550km.log`](run%20550km.log). Le
§2.2 corrige la fiche sur trois points, dont deux touchent à ce que l'item vise.

**Décidé le 2026-09-13, en ouverture du chantier :**

- **Cible primaire : le calcul FAST par défaut** (~192 s drag-on). C'est l'attente payée à
  chaque mission par l'utilisateur *et* à chaque itération de développement des items de v2
  qui suivent — l'argument même qui met OPT-1 en tête de version. Ce choix ordonne les lots
  à comportement modifié et fixe les cibles chiffrées.
- **Les trois modes sont traités.** FAST n'ordonne que la priorité ; les leviers propres à
  BALANCED et PRECISE restent au périmètre, séquencés après les leviers FAST.
- **Barreau d'acceptation : neutre au bruit près — vitesse seule.** Un lot à comportement
  modifié doit laisser le **verdict** (λ\*, résidu en kg, faisabilité) dans le bruit de
  comparaison `REL-18` (~19 km) de sa baseline. Les bits de trajectoire peuvent bouger (d'où
  le re-baselining des gates), pas le verdict physique. Un levier qui *améliorerait* le
  verdict est reporté à un chantier de **qualité** d'optimisation distinct : OPT-1 ne change
  aucune réponse, seulement le temps.

---

## 1. Périmètre

**Dans `OPT-1`** :

- **L0** — une référence mesurée sur le code actuel, hors jacoco, drag-on, pour les trois
  modes, produite par un outil de banc réutilisable.
- **L1** — le parallélisme intra-génération (`A1`) sur un pool unique borné et partagé
  (`A2`), **à résultats inchangés**.
- **Un backlog priorisé** de lots à changement unique (§5.3), tiré FAST-d'abord :
  `B2`, `D2`, `C1`, puis `B1`, `B3`, `C2`, `C4`, `D1`, `B4`/`B5`/`B6`, et `A3` en dernier.
  L'ordre exact des lots `C` est **fixé par le profil JFR de L0**.

**Hors `OPT-1`**, et à ne pas y laisser glisser :

1. **Toute amélioration du verdict.** Le barreau est verdict-neutre. Un levier qui trouve un
   meilleur transfert, une meilleure orbite ou un meilleur λ appartient à un chantier de
   qualité d'optimisation, pas ici. La raison est l'attribution : chaque lot d'OPT-1 doit se
   lire comme un gain de temps pur, jugé au banc, avec le verdict pour invariant.
2. **Le modèle atmosphérique et sa calibration.** C'est `PHY-2`, livré. `C2` (Harris-Priester
   au transfert seul) n'est pas une re-calibration : c'est le candidat *c* de `PHY-2`, « fermé,
   disponible » ([`atmosphere/13-cloture-PHY-2.md`](../atmosphere/13-cloture-PHY-2.md) §5.4),
   qui laisse l'ascension en NRLMSISE (seul modèle valide à 0 km).
3. **Le replay différé du gravity turn.** Écarté par `PHY-2 / L4` (dangereux sur lanceur
   léger). `D2` livre un **amorçage** — une graine, pas un replay.
4. **Une nouvelle variable ou fonction de coût CMA-ES.** OPT-1 ne touche ni les bornes
   (Hipparchus normalise l'espace de recherche par la largeur de la boîte, donc déplacer une
   borne re-encode chaque candidat et perturbe des missions qu'elle ne contraint pas) ni les
   poids de coût.

---

## 2. État des lieux

### 2.1 Ce que le code fait aujourd'hui

| Brique | Fichier | Mesure |
|---|---|---|
| Orchestration par étage | `mission/runtime/MissionOptimizer.java` | boucle séquentielle sur les étages ; `buildProblem` lit `mission.getCurrentState()` **une fois, sur le thread appelant** (l. 194), l'état d'entrée est figé dans le problème |
| Optimiseur CMA-ES adaptatif | `mission/optimizer/CMAESTrajectoryOptimizer.java` | exploration **déjà parallèle** (pool de runs, l. 451) ; **raffinement mono-thread** (une passe = un `executor.execute`, l. 535-583) ; exploration = `0,4 × maxEval` (l. 387), raffinement en 3 tiers (l. 533) |
| Pool d'exploration | `CMAESTrajectoryOptimizer.java:438-451` | `Executors.newFixedThreadPool(min(runs, procs−1))` **recréé par phase d'exploration** ; non nommé, non daemon ; un cœur réservé au rendu |
| Exécution d'un run | `mission/optimizer/CMAESRunExecutor.java` | `checkFeasableCount = 0` (l. 173-174) → Hipparchus tire **toute la génération** avant la boucle d'évaluation ; `crossRunStop` (arrêt croisé, instant déjà non déterministe) |
| Convergence | `mission/optimizer/AdaptiveConvergenceChecker.java` | jamais avant **100 générations** (l. 21) ; pas avant **500** tant que `cost > acceptableCost` (l. 66) |
| Réglage transfert | `mission/optimizer/problems/TransferTuning.java:51` | `acceptableCost = 3e-3` — **inatteignable** (plancher mesuré ~2,64e-3 orbital + ~1,4e-3 propergol, [`bilan.md`](bilan.md) piste 2) |
| État par évaluation | `GravityTurnManeuver:66`, `GravityTurnProblem:223`, `TransferProblem:51`, `AscentChainPropagation:33` | `ThreadLocal` — les runs parallèles ne s'écrasent pas |
| Chaîne d'étages | `mission/runtime/StageChainRunner.java:196,244` | `run()` **écrit `mission.setCurrentState()`** à chaque frontière, y compris sur le chemin `plain` (optimize) — donc depuis les threads d'évaluation |
| Propagateur d'optim | `simulation/OrekitService.java:333-334` | `absTol = 1e-8`, `relTol = 1e-10` **scalaires** sur les 7 composantes cartésiennes, masse comprise |
| Pas d'intégration | `OrekitService.java:228,236,270` | `COAST_MAX_STEP = 300`, `SAFE_MAX_STEP = 30`, `burnLimitedMaxStep` plafonne à 30 s dès qu'une combustion est présente |
| Sélection de planner | `mission/planner/MissionPlanOptimizer.java:102-113` | FAST/BALANCED + EarthOrbit → `MeasuredLoadPlanner` ; PRECISE → `MinimizedLoadPlanner` ; sinon → `FixedLoadPlanner` |
| Dimensionnement mesuré | `mission/planner/MeasuredLoadPlanner.java:82,109` | `MAX_SIZING_PASSES = 3` + `MAX_BRACKET_PASSES = 3` ; **chaque vol re-optimise de zéro** (`fly` → `FixedLoadPlanner` → `MissionOptimizer`) ; le Falcon Heavy converge au **2ᵉ** vol (l. 243) |
| Budgets d'évaluation | `mission/runtime/MissionLoadEvaluator.java:91,120` | `DEFAULT_OPTIMIZER_MAX_EVALUATIONS = 40 000` (FAST/BALANCED, par vol) ; `DEFAULT_SIZING_MAX_EVALUATIONS = 8 000` (PRECISE, par vol de λ) |
| Composition par mode | `mission/OptimizationType.java:39-53` | **FAST = transfert analytique** (closed-form) ; BALANCED/PRECISE = transfert CMA-ES deux-poussées. Le GT est CMA-ES quel que soit le mode |
| Gates | `build.gradle:73-89` | quatre gates, chacune dans sa JVM (`forkEvery = 1`) ; `CentralBodyBaselineTest` exclu de `test` (l. 68), inclus dans `gateTest` (l. 85) |

**L'anatomie d'un calcul FAST** (Falcon Heavy + 10 t, LEO 400, drag-on, ~192 s) se déduit du
tableau : wizard → FAST + EarthOrbit → `MeasuredLoadPlanner` → **2 vols** (le FH converge au
2ᵉ ; un profil qui diverge type Ariane 64 en paie jusqu'à 6). Chaque vol re-optimise le
**gravity turn** en CMA-ES et **propage** un transfert analytique. Les pistes de raffinement
et de retry, qui dominent PRECISE, **ne tournent pas en FAST** — il n'y a pas de transfert
CMA-ES à raffiner.

### 2.2 Corrections à la fiche

**1. La prémisse de `A1` sur l'état de la mission est fausse dans les deux sens.** La fiche
dit *« aucune classe de la chaîne d'ascension ne lit ni n'écrit l'état courant de la mission —
le commentaire de `MissionOptimizer.optimize()` qui affirme le contraire est à vérifier »*.
Mesuré :

- Le chemin d'évaluation **ne lit pas** `mission.getCurrentState()` : l'état d'entrée est
  capté une fois, sur le thread appelant, dans `GravityTurnFirstBurnStage.buildProblem`
  (l. 119) et figé dans le problème ; `AscentChainPropagation.propagate` vole depuis cet état
  figé.
- Mais il **écrit** `mission.setCurrentState()` — via `StageChainRunner.run` (l. 196 et 244),
  depuis les threads d'exploration parallèles. Le commentaire de `MissionOptimizer` (l. 187-192)
  est donc **correct**, pas « à vérifier », et le Javadoc d'`AscentChainPropagation`
  (*« only reads immutable state from the mission »*) est trompeur.
- Cette écriture est **bénigne**, et le reste sous L1 : **aucune évaluation CMA-ES ne relit
  `mission.getCurrentState()`**. La chaîne GT volée en parallèle (`AscentChainPropagation` →
  `StageChainRunner.plain`) ne lit que l'état figé du problème (capté sur le thread appelant,
  `GravityTurnFirstBurnStage.buildProblem` l. 119) et des `ThreadLocal` ; son seul étage
  susceptible de relire l'état lit **déjà** `propagator.getInitialState()` et non l'état mission
  (`StageSeparationStage.configure` l. 142-146, commentaire écrit exprès pour l'exploration
  parallèle). Le transfert — exploration **comme** raffinement — ne passe même pas par
  `StageChainRunner` (`TransferProblem.propagate` vole depuis un `initialState` figé). Les seuls
  relecteurs de l'écriture, `CoastingStage.configure` (l. 132) et `ConstantThrustStage.configure`
  (l. 59), ne sont sur **aucune** chaîne volée en parallèle : atteints uniquement sur le chemin
  **séquentiel** d'échantillonnage/éphéméride — et en production même pas là (la branche lisante
  de `CoastingStage` — `maxTime != null` — n'est jamais construite, `ConstantThrustStage` non
  plus : test seulement). `MissionOptimizer` capte `entryState` avant l'optimiseur et le restaure
  juste après (l. 192, 197). C'est *pourquoi* les gates à graine fixe sont bit-identiques malgré
  l'exploration parallèle **existante**, et pourquoi `A1`/L1 n'introduit **aucune** nouvelle
  classe de course. Le « retrait de l'écriture du chemin `plain` » que §5.2 rattache à L1 n'est
  donc **pas** un correctif de course, mais une hygiène — à repositionner à la conception de L1b.

  > **Correction (2026-09-13, en concevant L1b).** L'audit de [`04-conception-L1.md`](04-conception-L1.md)
  > §3 avait cru *démentir* ce « jamais relue » en attribuant à la chaîne GT un `CoastingStage`
  > lisant `mission.getCurrentState()`. C'est faux : l'interstage GT est un `StageSeparationStage`,
  > **déjà** immunisé (l. 142-146). Le présent point était **correct** ; c'est le §3 de la
  > conception L1 qui l'était de travers, et le refactor d'état mission qu'il rendait bloquant pour
  > L1b ne l'est pas.

**2. Le « paradoxe » de budget n'en est pas un.** La fiche présente `800 = 100 gén. au budget
PRECISE (8 000)` et `4 000 = 500 gén. au budget BALANCED (40 000)` sans dire que ce sont des
budgets **par vol**. Un BALANCED à 5× le budget d'un PRECISE serait contre-intuitif ; en
réalité `DEFAULT_OPTIMIZER_MAX_EVALUATIONS = 40 000` est le budget **par vol** de FAST et
BALANCED (peu de vols : 2-3), tandis que `DEFAULT_SIZING_MAX_EVALUATIONS = 8 000` est le
budget **par vol de λ** de PRECISE, qui en **enveloppe de nombreux** dans le balayage. Le
total PRECISE dépasse donc largement BALANCED. Aucune anomalie ; la correspondance mode →
(planner, budget par vol, nombre de vols) est un livrable de L0.

**3. Le split de coût FAST était estimé ; L0 l'a mesuré.** La répartition ~55 % recherche /
~45 % propagation était **dérivée** de chiffres de la fiche. **Mesurée : ~98 / 2** (§3, baseline
[`03-baseline-L0.md`](03-baseline-L0.md)).

---

## 3. Le modèle de coût FAST — mesuré par L0

**Mesuré**, pas dérivé (baseline : [`03-baseline-L0.md`](03-baseline-L0.md)). Le split ~55/45
que ce paragraphe portait est **démenti** : sur `FH_LEO400_FAST` (64,8 s, 12 threads), la
propagation *post-recherche* — transfert analytique + éphéméride, sur le thread `calc` — ne pèse
que **~2 %** du CPU ; le GT search en pèse **~98 %**.

**Mais « 2 % de propagation » ne démonte pas les pistes C.** La propagation *par candidat* — la
physique de chaque évaluation GT — vit **dans** les 98 % de recherche (les threads du pool ne
font que ça), et le profil JFR la montre **dominée par NRLMSISE** (~17 % en direct + le gros des
transcendantes qu'il pilote). Deux propagations distinctes :

| Bucket | Part FAST | Contenu | Leviers |
|---|---:|---|---|
| recherche (pool) | ~98 % | propagations de candidats, **liées NRLMSISE** | `A1` (parallélise), `B2`/`D2`/`B4` (moins d'évaluations), **`C1`/`C4`** (chaque évaluation moins chère) |
| calc (post-recherche) | ~2 % | transfert analytique + éphéméride | aucun levier utile |

**Conséquences pour un chantier FAST-primaire :**

- `A1` **n'aide finalement pas FAST** : il aurait fallu paralléliser l'exploration GT, ce que
  `L1b` a tenté et abandonné (incompatible avec l'arrêt croisé sous bit-identité —
  [`06-conception-L1b.md`](06-conception-L1b.md) §5, [`REL-33`](../reliquats.md)). `A1` reste le
  levier n°1 de BALANCED/PRECISE via le **raffinement** (mono-thread, **71 % du wall** d'un calcul
  BALANCED, mesuré §3) — livré par `L1a`. Pour FAST, ce sont `B2`/`D2`/`C1` qui portent le gain.
- **`C1` aide FAST**, contrairement à ce que le split `calc` 2 % laissait croire : chaque
  évaluation de la recherche est liée NRLMSISE (correction majeure de L0). L'ordre des lots `C`
  — `C1` puis `C2`/`C4` — est fixé par le profil JFR (baseline §4).
- `D2` reste un levier de premier plan : le GT est re-cherché **2 fois** (FH) à **6 fois**
  (Ariane) de zéro par le dimensionnement.

**Réserves mesurées** : le split CPU sous-estime le mono-thread (raffinement = 71 % du wall mais
39 % du CPU) ; et le cache Orekit (`GenericTimeStampedCache`) est derrière un verrou RW dont la
contention (déjà ~1-2 %) rendra probablement `A1` **sous-linéaire** — à surveiller en L1
(baseline §4).

---

## 4. Structure du découpage

**`L0` et `L1` sont figés ; la suite est un backlog.** La raison était le §3 : tant que le split
recherche/propagation de FAST et le profil JFR d'une évaluation n'étaient pas mesurés, l'ordre
des lots `C` ne pouvait pas être fixé sans deviner. **L0 les a depuis mesurés** (§3 ; baseline
[`03-baseline-L0.md`](03-baseline-L0.md) §4) : l'ordre `C` est `C1` → `C2`/`C4`. Le document fige
les deux premiers lots et définit un backlog priorisé — fidèle à « mesurer d'abord » et à « un
changement de comportement à la fois ».

**Le protocole d'acceptation est transverse à tous les lots à comportement modifié :**

1. **Un lot = un changement.** Jamais deux leviers dans le même lot.
2. **Mesuré au banc L0** pour l'accélération (le gain est le livrable).
3. **Jugé sur le verdict** — λ\*, résidu en kg, faisabilité — sur les missions de référence
   des trois modes.
4. **`REL-18` (~19 km) est le plancher de bruit** de la comparaison de verdict. Un écart sous
   le bruit est un « inchangé » ; un écart au-dessus est un changement de verdict.
5. **Verdict-neutre** (§0) : un lot qui dégrade le verdict au-delà du bruit est **annulé**
   (précédent : `PHY-2 / L2` étape 5, « mesurée puis annulée ») ; un lot qui l'améliore est
   hors périmètre.
6. **Gates re-baselinés** là où la trajectoire bouge au bit près (`B2`, `C1`, …), par l'outil
   de re-enregistrement que chaque gate porte déjà, sauf `CentralBodyBaselineTest`
   (1 296 lignes de `Boundary` à égalité stricte de `double`) dont le re-baseline est le seul
   coûteux.

---

## 5. Les lots

### L0 — référence mesurée

> **Livré le 2026-09-13.** Banc `tools/optbench`, conception [`02-conception-L0.md`](02-conception-L0.md),
> mesures [`03-baseline-L0.md`](03-baseline-L0.md). Deux résultats déplacent ce découpage : le split
> FAST mesuré (§3) et l'échec GEO promu en [`BUG-26`](../bugs.md).

**Rend vrai :** on connaît, à froid et hors jacoco, où part le temps de chaque mode, et on tient
le profil et le contrefactuel de coût qui ordonnent le backlog.

**Livrables :**

- Un **outil de banc autonome** sous `src/main/.../tools/` (comme `ephemerisgen`,
  `orbitgen`) : un `main()` qui construit la ou les missions de référence, lance le planner,
  chronomètre par phase via les événements `MissionProgressListener` existants (`AttemptStarted`,
  `StepStarted(EXPLORATION/REFINEMENT)`, `StageEntered`, `onEvaluation`) plus un wrapper autour
  de la génération d'éphéméride, et déclenche un enregistrement JFR. Lancé hors Gradle → pas de
  jacoco. **Devient le banc de régression** réutilisé par L1 et chaque lot du backlog.
- **Horodatages par phase, les trois modes**, sur Falcon Heavy LEO-400 drag-on (FAST, BALANCED,
  PRECISE) + un **GEO FAST**.
- **Le split FAST** (§3) — **mesuré ~98 % recherche / ~2 % propagation post-recherche**, le ~55/45
  dérivé démenti ; la propagation par candidat (liée NRLMSISE) est **dans** la recherche.
- **Les vols du dimensionnement** : combien `MeasuredLoadPlanner` en paie (2 FH, 6 Ariane), coût
  GT par vol (chiffre `D2`).
- **Profil JFR** d'une évaluation GT drag-on et d'une évaluation de transfert : NRLMSISE,
  détecteurs (`MinAltitudeTracker`, `DepletionGuard`, `ReentryGuard`), conversions géodésiques.
  **C'est lui qui ordonne les lots `C`.**
- **La correspondance mode → (planner, budget par vol, nombre de vols)** documentée (§2.2).
- **Le coût de la passe 3** (préparation de `B3`), depuis la timeline. Le **contrefactuel de
  verdict** (PRECISE sans passe 3, jugé λ\*/résidu/faisabilité) **n'est pas dans L0** — il demande
  une manette (`REFINEMENT_PASSES` est `private static final`), donc un changement de `src/main` que
  L0 s'interdit ; il rejoint `B3`.
- **L'assurance de `L1`** : `gateTest` lancé N fois, vérifier que les quatre gates sont
  bit-identiques d'un run à l'autre. Elles le sont déjà sous un ordonnancement non déterministe
  ; le confirmer dé-risque `A1` (§5.2) avant de l'écrire.

**Ne fait pas :** aucun changement de `src/main` qui bouge un résultat. Instrumentation et
mesure seulement.

**Preuve (obtenue le 2026-09-13) :** le banc a tourné ; FAST mesuré **64,8 s** (et non les
183-192 s de la cloture — écart machine/build, pas parallélisme), et le JFR imprime la
décomposition par méthode (baseline §4). `GEO_SAT_FAST` en échec → [`BUG-26`](../bugs.md).

---

### L1 — A1 + A2 : parallélisme intra-génération, résultats inchangés

> **Scindé en `L1a` (raffinement, non imbriqué) puis `L1b` (exploration, imbriqué), détaillé dans
> [`04-conception-L1.md`](04-conception-L1.md)** (décidé le 2026-09-13). Le pool y devient un
> **holder singleton daemon**, révisant le « une fois par calcul, enfilé depuis le planner » du
> paragraphe `A2` ci-dessous.
>
> **Bilan (2026-09-13) :** `L1a` **livré** (gain BALANCED −55 %, PRECISE −46 %,
> [`05-mesures-L1a.md`](05-mesures-L1a.md)). `L1b` **tenté et abandonné** : paralléliser
> l'exploration casse la bit-identité sous l'arrêt croisé — **bit-identité + arrêt croisé +
> exploration parallèle sont incompatibles** ([`06-conception-L1b.md`](06-conception-L1b.md) §5,
> [`REL-33`](../reliquats.md)). Le paragraphe `A1` ci-dessous, qui promettait le contraire, est
> donc **faux pour l'exploration** (il tient pour le raffinement, `crossRunStop = null`). **Le gain
> FAST est reporté sur le backlog** (`B2`, `D2`, `C1`, §5.3), qui n'a pas ce conflit.

**Rend vrai :** une évaluation CMA-ES qui utilise tous les cœurs disponibles au lieu d'un
seul, **sans changer un bit** des quatre gates.

**`A1` — évaluer la génération en parallèle.** Hipparchus (Apache 2.0) tire toute la génération
avant la boucle d'évaluation (`checkFeasableCount = 0`). On **fork `CMAESOptimizer` dans le
dépôt** — un subclass ne suffit pas, `doOptimize` référence des champs privés — et on évalue
les 6-8 candidats sur un pool. **Bit-identité** : mêmes candidats, mêmes valeurs (chaque
propagation est déterministe et indépendante), **réduction dans l'ordre d'index candidat** →
résultat identique quel que soit l'ordre d'exécution. Deux cas-limites à reproduire à
l'identique : la génération partielle quand `MaxEval` s'épuise, et `crossRunStop`.

**Modèle de parallélisme — imbriqué, et c'est forcé par « résultats inchangés ».** Les runs
d'exploration doivent **rester concurrents** (les rendre séquentiels changerait la sémantique
de l'arrêt croisé → ce ne serait plus « inchangé »), et chaque run parallélise **en plus** sa
génération. Cette imbrication (runs × candidats) sur un pool fixe classique **famine/deadlock**
(un thread contrôleur bloqué sur `Future.get` retient un worker). Solution : un **pool à vol de
travail (`ForkJoinPool`)**, où un worker qui `join` vole d'autres tâches. C'est aussi ce qui
fait gagner FAST : l'exploration GT passe de 4-wide (4 runs, 7 cœurs oisifs sur la machine
6c/12t) à ~11-wide, compounded ×2 par le dimensionnement.

**`A2` — un pool unique, borné, partagé, nommé + daemon.** Un `ForkJoinPool` exposé par un
**holder** (motif `OrekitService`), taille `availableProcessors − 1` (cœur de rendu réservé), pris
directement par `CMAESRunExecutor` et le fork — **zéro plomberie** à travers planners et
`MissionOptimizer`. Répare le `Executors.newFixedThreadPool` recréé par phase et les pools
non-daemon qui retiennent le process à la fermeture ([`REL-21`](../reliquats.md)). Justification du
holder contre l'enfilage : [`04-conception-L1.md`](04-conception-L1.md) §2.

**L'écriture d'état mission (§2.2) n'est pas une course — audit clos.** Le prérequis « auditer
qu'aucun étage volé en parallèle ne lit `mission.getCurrentState()` » est **levé** (ré-audit du
2026-09-13, §2.2) : la chaîne GT ne le lit pas (`StageSeparationStage.configure` lit **déjà**
`propagator.getInitialState()`, l. 142-146) ; le transfert ne passe pas par `StageChainRunner` ;
`CoastingStage`/`ConstantThrustStage` ne sont sur aucune chaîne parallèle. Retirer l'écriture morte
du chemin `plain` reste une **hygiène** possible (gardée sur `!abortOnFailure`), **hors du périmètre
minimal** retenu pour L1b ([`06-conception-L1b.md`](06-conception-L1b.md) §4). Le Javadoc de
`AscentChainPropagation` est déjà exact (il parle de lecture, pas d'écriture).

**L'argument de bit-identité tient sur un fait vérifié en L0** : les quatre gates sont **déjà
déterministes aujourd'hui** sous un ordonnancement non déterministe des 4 runs et de l'arrêt
croisé — sinon elles seraient instables. Leur résultat est donc déjà immunisé contre l'instant
d'abort ; `A1` ne fait qu'élargir cette variance de timing.

**Acceptation :** les quatre gates **bit-identiques** (tolérance zéro) + une accélération
mesurée au banc L0 (exploration GT pour FAST, raffinement pour PRECISE, où
`CMAESRunExecutor.execute` est aujourd'hui mono-thread).

**Preuve :** `gateTest` vert à tolérance zéro ; le banc montre l'exploration GT et le
raffinement de transfert passer de 1 à N threads.

---

### 5.3 Le backlog priorisé (ordre des lots C fixé par le JFR de L0)

Chaque entrée est un lot à changement unique, soumis au protocole §4.

**Leviers FAST (en tête) :**

- **`B2` — plancher `MIN_ITERS_BEFORE_CONVERGE = 100`.** Coûte ~600 évals/run même quand le
  seuil GT est atteint plus tôt ; sur **toute** mission, chaque vol FAST le paie ×2. Tous
  modes. Re-baseline des gates.
- **`D2` — amorcer le GT à travers les passes de `MeasuredLoadPlanner`.** Aujourd'hui
  re-cherché de zéro à chaque vol. Une **graine, pas un replay** (le replay est écarté, §1).
  FAST/BALANCED.
- **`C1` — tolérances de l'intégrateur.** **Mesuré et tranché le 2026-09-13**
  ([`07-conception-C1.md`](07-conception-C1.md), [`08-mesures-C1.md`](08-mesures-C1.md)) : le pas
  était **tol-borné** (la réserve « ou cap-borné ? » est levée). Scalaire desserré de `1e-8/1e-10` à
  **`1e-5/1e-7`** → **−58 % FAST / −66 % BALANCED / −63 % PRECISE** au banc, **verdict-neutre**
  (PRECISE bit-identique, λ\* et orbite dans REL-18 partout). Le risque de bruit PRECISE ne s'est pas
  matérialisé. **Clos le 2026-09-13** : gates re-baselinés et verts.

**Leviers BALANCED/PRECISE (ensuite) :**

- **`B1` — rendre la convergence du transfert atteignable.** La règle « pas avant 500
  générations au-dessus du seuil » + le seuil inatteignable font dépenser tout le budget.
  Comparer la seule partie orbitale au seuil ([`bilan.md`](bilan.md) piste 2, sans rouvrir
  l'extinction sèche que la barrière vient de corriger), ou un critère de stagnation sur une
  fenêtre. « Probablement le premier levier en BALANCED. »
- **`B3` — retirer la passe 3, conditionner la passe 2** — porte **sa propre manette** (`REFINEMENT_PASSES`)
  et mesure ici le **contrefactuel de verdict** (λ\*/résidu/faisabilité) que L0 ne pouvait pas jouer ;
  L0 n'en a donné que le coût.
- **`C2` — Harris-Priester au transfert seul** (candidat *c* de `PHY-2`, §1). L'ascension
  reste NRLMSISE.
- **`C4` — découper le transfert en coast + poussées** (coast à `COAST_MAX_STEP`).
  **Conditionné au constat de `C1`** : sans effet si le pas est limité par la tolérance.
  L'invariant d'allumage tardif (`CLAUDE.md`, `OrekitService.burnLimitedMaxStep`) doit tenir.
- **`D1` — PRECISE : amorcer le transfert d'un λ sur le λ précédent** (`seededStartPoints`
  existe).
- **`B4` / `B5` / `B6`** — nombre de runs d'exploration GT (temps mur inchangé via l'arrêt
  croisé, mais le temps CPU compte une fois `A1` là) / élargir la première exploration plutôt
  que payer cascade + retry / mesurer ce que 40 000 évals achètent contre 8 000 sur un
  transfert.

**En dernier :**

- **`A3` — paralléliser la boucle λ externe de PRECISE** (plusieurs λ à la fois). Change le
  chemin de la bissection et **dispute les cœurs à `A1`** — d'où sa place en queue.

---

## 6. Registres liés

- [`REL-18`](../reliquats.md) — bruit de comparaison (~19 km) : **critère d'acceptation** de
  tous les lots à comportement modifié (§4).
- [`REL-21`](../reliquats.md) — annulation d'un calcul : son drapeau vit là où vit
  `crossRunStop`, que `A1` réécrit ; le pool nommé + daemon de `A2` touche la même fermeture.
- [`REL-30`](../reliquats.md) et [`REL-32`](../reliquats.md) — plancher de coût qui rend
  `acceptableCost` inatteignable : `B1` touche la même chose.
- [`REL-31`](../reliquats.md) — exemption de la règle de retry : `B3` et `B5`.

---

## 7. Corrections faites en écrivant

- En conversation, j'ai présenté le budget PRECISE 8 000 / BALANCED 40 000 comme
  « contre-intuitif ». Le document le corrige (§2.2 point 2) : ce sont des budgets **par vol**,
  PRECISE en enveloppe de nombreux via le balayage λ, donc son total dépasse BALANCED — aucune
  anomalie.
