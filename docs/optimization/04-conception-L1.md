# OPT-1 / L1 — Parallélisme intra-génération (A1 + A2) — conception

Lot `L1` du découpage [`01-decoupage.md`](01-decoupage.md) §5.2, informé par la baseline
[`03-baseline-L0.md`](03-baseline-L0.md). **Résultats inchangés** : L1 ne change pas un verdict,
il répartit l'évaluation d'une génération CMA-ES sur plusieurs cœurs.

**Découpé en deux sous-lots, ordonnés par le risque** (décidé le 2026-09-13) :

- **`L1a` — raffinement.** Fork de `CMAESOptimizer` + pool `A2`, génération évaluée en parallèle
  **au raffinement**. **Non imbriqué** : une seule passe tourne à la fois (thread `calc`), donc un
  contrôleur + le pool. Livre le gros gain BALANCED/PRECISE — le raffinement de transfert vaut
  **71 % du wall** d'un calcul BALANCED (baseline §3).
- **`L1b` — exploration.** Même fork, génération évaluée en parallèle **aussi à l'exploration**.
  **Imbriqué** : N runs concurrents × leurs candidats → vol de travail obligatoire. Livre le gain
  FAST (l'exploration GT), sous la réserve de contention mesurée (§5).

FAST étant la cible primaire, `L1a` ne lui apporte rien (FAST ne raffine pas) ; il est premier
parce qu'il valide le fork et le pool sur le cas simple et **mesure la contention avant** le cas
imbriqué. Le gain FAST vient en `L1b`, juste après.

---

## 1. A1 — le fork de `CMAESOptimizer`

**Pourquoi un fork, et pas un subclass.** Hipparchus 4.0.2 tire toute la génération avant la
boucle d'évaluation (`checkFeasableCount = 0`, passé par `CMAESRunExecutor:173`). Le point
d'insertion est donc la boucle qui évalue les `λ` candidats d'une génération. Elle est dans
`doOptimize`, `protected`, mais qui référence ~40 champs **privés** (`lambda`, `xmean`, `B`, `D`,
`BD`, `C`, `sigma`, `pc`, `ps`, `weights`, `diagD`… — vérifié `javap`). Un override ne compilerait
pas. On **copie la classe dans le dépôt** (Apache 2.0), sous
`simulation/mission/optimizer/ParallelCMAESOptimizer`, et on n'y change **qu'une chose**.

**Le seul changement.** La boucle `for (k = 0 … λ)` qui appelle `function.value(...)` sur chaque
colonne de la population devient un **batch soumis au pool** : chaque candidat est évalué sur un
thread, les `fitness[k]` sont collectés **dans l'ordre d'index `k`**, puis la mise à jour de la
distribution (tri, `xmean`, covariance, `sigma`) reprend **inchangée**. Le reste du fichier est
byte-pour-byte l'amont.

**Bit-identité.** Chaque `function.value(candidate)` est déterministe et indépendant (l'état par
évaluation est déjà `ThreadLocal`, découpage §2.1) ; le meilleur coût est réduit par
`CMAESRunExecutor` dans l'ordre d'index, pas par ordre d'arrivée. Donc mêmes candidats, mêmes
valeurs, même sélection — **indépendamment de l'ordre d'exécution**.

**Deux cas-limites à reproduire à l'identique.**

- **Budget épuisé en cours de génération.** L'amont incrémente un compteur par évaluation et
  `TooManyEvaluationsException` est levée par la `(max+1)`-ième **avant** de calculer : les
  candidats `0 … R−1` (R = budget restant) sont évalués, le suivant lève, et
  `CMAESRunExecutor` rend le meilleur-jusque-là (que sa boucle a suivi). Le fork doit donc
  **plafonner le batch à `min(λ, R)`, dans l'ordre d'index**, puis lever comme l'amont — sinon il
  évaluerait des candidats que l'amont n'a jamais vus et pourrait rendre un meilleur différent.
- **Arrêt croisé (`crossRunStop`).** La `MultivariateFunction` de `CMAESRunExecutor:134` lève
  `RunAbortedException` quand un run concurrent a fini sous le coût acceptable. En parallèle, un
  candidat qui lève **annule le reste du batch et propage**, comme l'amont. L'instant d'abort est
  **déjà non déterministe** (4 runs concurrents aujourd'hui) et les gates y sont **immunisés**
  (baseline : ils sont bit-identiques run à run) — donc pas de reproduction exacte à viser, juste
  la même propagation.

---

## 2. A2 — le pool

**Un `ForkJoinPool` unique, nommé, daemon**, taille `availableProcessors − 1` (un cœur réservé au
rendu JME, comportement actuel), exposé par un **holder** à la `OrekitService.get()` — nouveau
`simulation/mission/optimizer/OptimizerThreadPool`. `CMAESRunExecutor` (et le fork) le prennent
directement : **zéro plomberie** à travers les planners et `MissionOptimizer`. Un seul calcul
tourne à la fois (l'orchestrateur `MissionOrchestratorAppState` est mono-thread), donc pas de
contention inter-calcul.

**Ce qu'il répare** (découpage §2.1) : le `Executors.newFixedThreadPool` **recréé à chaque phase
d'exploration** (`CMAESTrajectoryOptimizer:451`), et les threads **non-daemon** qui retiennent le
process à la fermeture pendant une exploration ([`REL-21`](../reliquats.md)).

**`ForkJoinPool` et pas un pool fixe**, dès `L1a` : `L1b` en aura besoin (l'imbrication exige le
vol de travail, sinon un contrôleur bloqué sur `join` affame un worker). Le prendre dès `L1a`
évite un remplacement, et il sert le cas non imbriqué aussi bien.

**Révision assumée du découpage §5.2**, qui disait « créé une fois par calcul de mission (par le
planner / MissionOptimizer) ». La plomberie à travers ~6 constructeurs n'apporte rien ici : un
holder daemon est le motif que le dépôt emploie déjà pour `OrekitService`, et il ferme le mode
d'échec du pool non-daemon d'un coup.

---

## 3. L'écriture d'état mission : pas de course (§3 corrigé le 2026-09-13)

> **Correction.** La version d'origine de ce §3 affirmait que « l'audit a démenti la prémisse
> *jamais relue* » et attribuait à la chaîne GT un `CoastingStage` lisant `mission.getCurrentState()`,
> d'où un « refactor bloquant pour L1b ». **C'était faux.** Le ré-audit conduit en concevant L1b
> (voir [`06-conception-L1b.md`](06-conception-L1b.md) §0 et découpage [`01-decoupage.md`](01-decoupage.md)
> §2.2) rétablit le fait : la prémisse « jamais relue » était **correcte**. Ce qui suit est la
> version juste.

`StageChainRunner.run` écrit `mission.setCurrentState()` (l. 196 et 244) depuis les threads
d'évaluation. **Aucune évaluation CMA-ES ne relit cette écriture** :

- L'interstage de la chaîne GT volée en parallèle n'est **pas** un `CoastingStage` mais un
  `StageSeparationStage`, dont `configure` lit **déjà** `propagator.getInitialState()` et non l'état
  mission (l. 142-146, commentaire écrit exprès pour l'exploration parallèle). Les burns GT lisent
  eux aussi l'état du propagateur (`prepare`, l. 142-143).
- Le transfert (exploration **comme** raffinement) ne passe pas par `StageChainRunner` :
  `TransferProblem.propagate` (l. 576) vole depuis un `initialState` figé.
- Les deux `configure` qui relisent `mission.getCurrentState()` — `CoastingStage` (l. 132) et
  `ConstantThrustStage` (l. 59) — ne sont sur **aucune** chaîne volée en parallèle : ils ne sont
  atteints que sur le chemin séquentiel d'échantillonnage/éphéméride, et en production même pas là
  (la branche lisante de `CoastingStage` n'est jamais construite ; `ConstantThrustStage` est
  test-seulement).
- `MissionOptimizer` capte `entryState` avant l'optimiseur et le restaure après (l. 192, 197).

**Conséquence.** L'écriture est morte sur le chemin `plain` volé en parallèle ; elle n'est vive que
sur le chemin séquentiel d'échantillonnage, où elle est le canal légitime entre `run` et
`stage.configure`. **L1b n'a donc pas de refactor d'état mission à faire** : il n'introduit aucune
nouvelle classe de course (même argument de bit-identité qu'à L1a, sur le même fait vérifié).
Retirer l'écriture morte du chemin `plain` reste une **hygiène** possible, hors du périmètre minimal
retenu pour L1b.

---

## 4. Ce que chaque sous-lot livre, et sa preuve

**`L1a`** — fork `ParallelCMAESOptimizer`, holder `OptimizerThreadPool`, employé **au
raffinement** (`CMAESTrajectoryOptimizer` cascade l. 535-583). L'exploration garde
`parallelGeneration = false` (inchangée). Pas de changement de `StageChainRunner` (§3).

- **Acceptation** : les **quatre gates bit-identiques** (tolérance zéro) ; et au banc L0, le
  raffinement de transfert passe de mono-thread à N — cible : entamer les **399 s** de raffinement
  du BALANCED de référence (génération de transfert ≈ 8 candidats → jusqu'à ~8-wide).
- **Preuve** : `gateTest` vert à tolérance zéro ; `optBench` sur `FH_LEO400_BALANCED` et
  `FH_LEO400_PRECISE` montre la chute du wall de raffinement.

**`L1b`** — même fork employé **à l'exploration** : le pool de runs (l. 451) bascule sur le holder
`ForkJoinPool` et chaque run évalue sa génération sur ce **même** pool (vol de travail), imbriqué.
**Pas de refactor d'état mission** (§3 corrigé : aucune course). Conçu en détail dans
[`06-conception-L1b.md`](06-conception-L1b.md).

- **Acceptation** : quatre gates bit-identiques ; au banc, l'exploration GT passe de 4-wide à
  ~11-wide, gain FAST — **mesuré contre la contention** (§5), pas présumé.
- **Preuve** : `gateTest` vert ; `optBench` sur `FH_LEO400_FAST` et `ARIANE64_LEO400_FAST`.

**Assurance commune, à obtenir avant d'écrire le fork** (livrable L0 non encore joué) : lancer
`gateTest` **N fois** et vérifier que les quatre gates sont bit-identiques **run à run**. Elles le
sont attendu — sinon l'argument de bit-identité de A1 tomberait — mais c'est à **confirmer**, pas
à supposer.

---

## 5. La réserve mesurée : contention du cache Orekit

Le profil L0 (baseline §4) montre `GenericTimeStampedCache.getNeighbors` +
`ReentrantReadWriteLock.tryAcquireShared` à **~1-2 % déjà à 4-wide** (EOP / repères). Plus de
threads d'évaluation ⇒ plus de contention sur ce verrou partagé :

- **`L1a`** (raffinement, 1→~8 threads) l'aggrave peu et le **mesure** — première lecture propre
  de l'effet.
- **`L1b`** (exploration, 4→~11 threads) est le cas où le speedup peut devenir **sous-linéaire**.
  La décision — s'en contenter, ou attaquer la contention (cache par thread, pré-chargement) —
  est prise **sur la mesure de L1a puis L1b**, pas ici. Si elle plafonne trop, elle ouvre une
  piste neuve hors du périmètre A1.

---

## 6. Ce que L1 ne fait pas

- Aucun changement de verdict : c'est le contrat « résultats inchangés ». Tout écart d'un gate est
  un bug de L1, pas un compromis.
- Aucune réduction du **nombre** d'évaluations (c'est `B2`/`D2`/`B4`) ni du **coût** d'une
  évaluation (c'est `C1`/`C2`/`C4`). L1 ne touche qu'au **parallélisme**.
- Ne résout pas la contention du cache Orekit : il la mesure et la borne (§5).
