# OPT-1 / L1b — exploration parallèle imbriquée — conception

Sous-lot `L1b` du découpage [`01-decoupage.md`](01-decoupage.md) §5.2, suite de `L1a`
([`04-conception-L1.md`](04-conception-L1.md), mesures [`05-mesures-L1a.md`](05-mesures-L1a.md)).
`L1a` a livré le fork `ParallelCMAESOptimizer` et le holder `OptimizerThreadPool`, employés **au
raffinement** (non imbriqué). `L1b` les emploie **aussi à l'exploration** — le cas imbriqué
(runs × candidats) — pour le gain FAST. **Résultats inchangés** : L1b ne change pas un verdict, il
répartit l'évaluation d'une génération sur plus de cœurs.

> **STATUT : tenté le 2026-09-13, mesuré rouge, ABANDONNÉ** (décision : *revert + backlog FAST*).
> Le périmètre minimal ci-dessous a été implémenté et a **cassé la bit-identité** des gates
> (`leo400`, `geo` rouges) : l'exploration parallèle est incompatible avec l'arrêt croisé sous
> bit-identité (§5, cause racine). Le code a été reverté à L1a. Le gain BALANCED/PRECISE de L1a
> reste acquis ; le gain FAST passe désormais par le backlog (`B2`, `D2`, `C1`). L'expérience est
> conservée pour une amélioration ultérieure ([`REL-33`](../reliquats.md)) — les §1-§4 disent ce qui
> a été tenté, le §5 pourquoi ça n'a pas tenu et les voies possibles.

**Périmètre retenu (minimal, décidé le 2026-09-13).** Migration de l'exploration sur le holder +
génération d'exploration parallèle + mesure. **Pas** de refactor d'état mission (§0). Un seul
changement de comportement. C'est ce périmètre qui a été implémenté puis reverté.

---

## 0. La correction qui réduit le périmètre

La conception L1 initiale ([`04-conception-L1.md`](04-conception-L1.md) §3) et le découpage §2.2
portaient un « refactor d'état mission » réputé **bloquant** pour L1b : plusieurs `configure` de
candidats en vol simultané liraient `mission.getCurrentState()` fraîchement écrit par un autre
thread. Le ré-audit conduit en ouvrant L1b l'a **démenti** :

- La chaîne GT volée en parallèle est `FirstBurn → StageSeparationStage → SecondBurn`. Son
  interstage n'est **pas** un `CoastingStage` (l'erreur du §3) mais un `StageSeparationStage`, dont
  `configure` lit **déjà** `propagator.getInitialState()` et non l'état mission — commentaire
  `StageSeparationStage.java` l. 142-146, écrit exprès pour l'exploration parallèle. Les burns GT
  lisent l'état du propagateur dans `prepare`.
- Le transfert — exploration **comme** raffinement — ne passe pas par `StageChainRunner` :
  `TransferProblem.propagate` (l. 576) vole depuis un `initialState` figé.
- Les deux `configure` qui relisent `mission.getCurrentState()` (`CoastingStage` l. 132,
  `ConstantThrustStage` l. 59) ne sont sur **aucune** chaîne volée en parallèle : atteints seulement
  sur le chemin séquentiel d'échantillonnage, et en production même pas là (la branche lisante de
  `CoastingStage` — `maxTime != null` — n'est jamais construite ; `ConstantThrustStage` est
  test-seulement).
- `MissionOptimizer` (l. 187-197) capte `entryState` avant l'optimiseur et le **restaure après** :
  l'écriture parallèle est morte sur le chemin `plain`.

**Donc l'écriture n'est jamais relue par une évaluation, et L1b n'introduit aucune nouvelle classe
de course** — le même socle de bit-identité qu'à L1a. Le refactor est abandonné ; `04-conception-L1.md`
§3 est corrigé, découpage §2.2 sharpené.

---

## 1. Le seul changement de code

Tout tient dans le bloc d'exploration de `CMAESTrajectoryOptimizer` (l. 436-496). Le fork
`ParallelCMAESOptimizer` **ne change pas** : `evaluateGeneration` (l. 988) soumet déjà via
`pool.invokeAll` puis `futures.get(k).get()` (l. 1008-1011), et ce chemin fonctionne **depuis un
worker** du pool aussi bien que depuis un thread externe (§2).

Trois retouches :

1. **Le pool de runs bascule sur le holder.** Aujourd'hui l'exploration crée un
   `Executors.newFixedThreadPool(poolSize)` par phase (l. 451). L1b soumet les N run-tasks
   directement à `OptimizerThreadPool.get()` — le `ForkJoinPool` déjà utilisé par le raffinement.
   Le thread appelant (mission-optimizer) soumet les N runs et bloque sur `get()` **hors** pool ;
   chaque run tourne sur un worker.
2. **La génération d'exploration devient parallèle.** Le flag passé à `executor.execute(...)`
   (l. 470) passe de `parallelGeneration = false` à `true`. Chaque run évalue alors ses `λ`
   candidats via `invokeAll` sur **le même** `ForkJoinPool` → arbre `runs × λ`, vol de travail.
3. **Le cap de taille disparaît.** `availableForOptimizer` / `poolSize` (l. 438-439) ne servent
   plus : le parallélisme du holder (`procs − 1`) est le plafond, et le cœur de rendu reste réservé
   par le dimensionnement du holder. Le thread appelant reste bloqué-oisif (11 workers + 1 appelant
   oisif + 1 rendu sur 12 logiques).

Le reste du bloc — graines de run pré-tirées (l. 442-445), `crossRunStop` (l. 450), comptabilité
des évaluations et test de consensus (l. 474-526) — est **inchangé**.

**Pourquoi le `ForkJoinPool` et pas deux pools.** L'imbrication `runs × candidats` sur un pool fixe
classique famine : un run-task bloqué sur `Future.get` retient un thread sans participer. Sur le
`ForkJoinPool`, un worker qui `join` **vole** d'autres tâches (les candidats de sa propre génération,
poussés dans son deque local, puis ceux des autres runs). Mettre runs **et** candidats sur le même
pool est ce qui laisse le vol de travail couvrir tout l'arbre et saturer les cœurs — 4-wide (4 runs,
7 cœurs oisifs) → ~11-wide.

---

## 2. Bit-identité sous imbrication — même argument qu'à L1a

L'imbrication change l'**ordre** d'exécution, jamais les **valeurs** :

- **Réduction dans l'ordre d'index.** Le meilleur coût est retenu par le wrapper objectif de
  `CMAESRunExecutor` et par la collecte `futures.get(k)` du fork, dans l'ordre d'index candidat —
  pas d'ordre d'arrivée. Chaque `problem.propagate(candidate)` est déterministe et indépendant
  (état par évaluation `ThreadLocal`, découpage §2.1).
- **Runs déterministes quel que soit l'ordre.** Les sous-graines de run sont pré-tirées
  séquentiellement sur le thread appelant (l. 442-445), donc chaque run reproduit sa suite
  aléatoire indépendamment de l'ordre d'exécution.
- **`crossRunStop` déjà non déterministe.** L'instant d'arrêt croisé varie déjà à 4 runs
  concurrents ; les gates y sont immunisés (bit-identiques run à run, vérifié). L1b ne fait
  qu'élargir cette variance de timing. Un candidat qui lève `RunAbortedException` annule sa
  génération et se propage comme sur le chemin séquentiel (`evaluateGeneration` l. 1015-1025).
- **Pas de nouvelle classe de course** (§0) : aucune évaluation ne lit d'état mission partagé.

**Acceptation :** les **quatre gates bit-identiques** (tolérance zéro), comme à L1a.

---

## 3. La réserve mesurée est le livrable — contention du cache Orekit

C'est le cas dur annoncé (découpage §5, conception L1 §5). L'exploration passe de **4-wide à
~11-wide imbriqué** sur le verrou lecture/écriture du cache Orekit
(`GenericTimeStampedCache.getNeighbors` + `ReentrantReadWriteLock`, ~1-2 % déjà à 4-wide, baseline
§4). Plus de threads d'évaluation ⇒ plus de contention sur ce verrou partagé.

`L1a` a mesuré **~46 % d'efficacité** à 8-wide **non imbriqué** (raffinement, ~3,65× sur 8) : c'est
le **point de départ**, pas une garantie. L1b **mesure** le speedup réel du cas imbriqué et lit la
sous-linéarité. La décision — s'en contenter, ou attaquer la contention (cache par thread,
pré-chargement) — se prend **sur la mesure**, pas ici ; si elle plafonne trop, elle ouvre une piste
neuve hors du périmètre A1.

**Preuve :** `gateTest` vert à tolérance zéro ; `optBench` sur `FH_LEO400_FAST` et
`ARIANE64_LEO400_FAST` — les deux cellules qui **explorent sans raffiner**, donc où le gain de L1b
se lit isolé (FAST à 2 vols, Ariane à 6). Comparaison contre la colonne L1a de
[`05-mesures-L1a.md`](05-mesures-L1a.md).

---

## 4. Ce que L1b ne fait pas

- **Pas de refactor d'état mission** (§0) : il n'y a pas de course à supprimer.
- **REL-21 est fermé par la migration elle-même.** Plus de `Executors.newFixedThreadPool` recréé par
  phase ni de threads non-daemon retenant le process à la fermeture : l'exploration passe au holder
  daemon, comme le raffinement. C'est un effet de bord de la bascule, pas un lot séparé.
- **Le retrait de l'écriture morte du chemin `plain` n'est pas fait** : hygiène possible (gardée sur
  `!abortOnFailure`), hors du périmètre minimal retenu.
- **Aucun changement de verdict**, aucune réduction du **nombre** d'évaluations (`B2`/`D2`/`B4`) ni
  du **coût** d'une évaluation (`C1`/`C2`/`C4`), aucun changement du **nombre de runs**
  (`B4`/`B5`/`B6`). L1b ne touche qu'au parallélisme de l'exploration.

---

## 5. Résultat : rouge, et pourquoi (le §2 était faux)

Le périmètre §1 a été implémenté (bascule sur le holder + `parallelGeneration = true` à
l'exploration) et **a cassé la bit-identité** : gates `leo400` et `geo` **rouges**. Le §2
(« bit-identité, même argument qu'à L1a ») était **faux** — il a négligé l'interaction avec l'arrêt
croisé.

**Cause racine.** Les runs d'exploration partagent `crossRunStop` : le premier qui termine ≤
`acceptableCost` fait avorter les autres à leur **prochaine** évaluation
(`CMAESRunExecutor:136`). Le meilleur retenu d'un run (`runBestVars`, mis à jour par candidat
évalué) dépend donc de **quels** candidats ce run a eu le temps d'évaluer avant le flip :

- **séquentiel** (L1a, avant) : candidats évalués **dans l'ordre d'index**, arrêt au premier qui
  voit le drapeau → exactement `0..k−1` ;
- **parallèle** (L1b) : `invokeAll` lance les λ candidats ; l'ensemble qui **démarre avant** le flip
  dépend de l'**ordonnancement**, différent de `0..k−1`.

Ce meilleur-là peut **gagner la phase** → orbite/λ différents → gate rouge. Le résultat de
l'exploration dépend silencieusement de l'instant d'arrêt croisé ; le séquentiel le masquait
(timing machine constant). **Bit-identité + arrêt croisé + exploration parallèle : incompatibles.**
Le §2 aurait dû le voir ; l'assurance L0 « lancer `gateTest` N fois » (jamais jouée) l'aurait révélé.

Pourquoi L1a ne l'a pas vu : son unique parallélisme était le **raffinement**, une passe unique à
`crossRunStop = null`. Le raffinement (GT **et** transfert) prouve que la génération parallèle est
bit-identique **hors** arrêt croisé — le conflit est propre à l'exploration.

**Voies pour une reprise ultérieure** ([`REL-33`](../reliquats.md)), toutes hors du barreau
tolérance-zéro actuel (re-baseline unique requis) :

1. **Îlots synchronisés (lockstep).** Tous les runs avancent génération par génération ensemble ; la
   décision d'arrêt croisé se prend à une **frontière de génération commune**. Chaque génération est
   complète (bit-identique, réduction par index) et le nombre de générations avant arrêt devient
   **déterministe** → déterministe + arrêt croisé + génération parallèle. Coût : piloter CMA-ES
   génération par génération à travers les runs (gros refactor de l'exécuteur), + re-baseline.
2. **Abandon de l'arrêt croisé à l'exploration.** Runs indépendants jusqu'à convergence →
   déterministe et simple ; mais perd le levier d'arrêt croisé, change les budgets aval
   (`remainingEvals` du raffinement) et **risque d'améliorer le verdict** (runs plus longs),
   contraire au barreau verdict-neutre.

**Décision (2026-09-13) : revert.** Le code revient à L1a-vert. Le gain FAST est pris par le backlog
qui n'a **pas** ce conflit (`B2`, `D2`, `C1`), A1 restant cantonné au raffinement livré par L1a.
