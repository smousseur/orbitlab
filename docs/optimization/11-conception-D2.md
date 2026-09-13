# OPT-1 / D2 — amorçage du gravity turn entre passes — conception

Lot `D2` du backlog [`01-decoupage.md`](01-decoupage.md) §5.3, levier FAST/BALANCED. Soumis au
protocole §4 : un changement unique, mesuré au banc, jugé **verdict-neutre** (`REL-18`, ~19 km),
gates re-baselinés.

**Rend vrai :** la recherche GT d'une passe part de la solution GT de la passe précédente (une
**graine**, pas un replay), au lieu de repartir de la graine analytique à chaque vol — donc converge
en moins d'évaluations quand le load est stable d'une passe à l'autre.

---

## 0. Ce que le code établit

- `MeasuredLoadPlanner.plan()` vole une boucle de passes de dimensionnement (2 FH, jusqu'à 6 Ariane),
  puis un vol final dans le mode demandé si ≠ FAST. Chaque `fly()` compose une **mission fraîche** et
  lance `FixedLoadPlanner → MissionOptimizer → CMAESTrajectoryOptimizer`, qui **re-cherche le GT
  depuis la graine analytique** à chaque fois.
- **Le GT est mode-indépendant** : le dimensionnement tourne en FAST précisément parce que le bon
  load ne dépend pas du mode, et le problème GT est le même quel que soit le mode. Donc le GT du
  dernier vol de dimensionnement (FAST) est une **graine forte** pour le GT du vol final
  BALANCED/PRECISE — même load, même problème.
- Les résultats par stage sont exposés par clé :
  `plan.computation().optimizerResult().resultsByStageKey()` → `Map<String, OptimizationResult>`, chaque
  `OptimizationResult` portant `bestVariables()` et `evaluations()`. La graine est donc **capturable**.
- L'exploration porte déjà des **graines de départ** (`CMAESTrajectoryOptimizer.buildSeededStartPoints` :
  graine analytique + `initialGuess` en tête des runs). D2 en **prépose une de plus**. C'est distinct
  du **replay** (`MissionSolutions`, évals = 0, écarté §1).
- **En FAST, le seul stage CMA-ES est le GT** (transfert analytique). Amorcer **par clé de stage**
  revient donc en pratique à amorcer le GT — sans coder « GT » en dur, et ça amorce aussi le GT du vol
  final BALANCED/PRECISE.

---

## 1. Le changement

Faire porter à chaque `fly()` une graine par clé (`Map<String, double[]>`) = les `bestVariables()` de
la passe précédente (celles dont `evaluations() > 0`, donc réellement optimisées, pas replayées).
Plomberie additive à travers quatre couches, chacune un paramètre optionnel dont le défaut reproduit
le comportement actuel :

1. **`MeasuredLoadPlanner`** : après chaque `fly`, capture la carte {clé → bestVars} ; la passe
   suivante la reçoit. Le vol final (mode ≠ FAST) reçoit la graine du dernier vol de dimensionnement.
2. **`FixedLoadPlanner`** : nouveau paramètre `Map<String, double[]> seeds`, passé au `MissionOptimizer`.
3. **`MissionOptimizer`** : champ `stageSeeds` ; dans `solveStage`, la graine du stage (par
   `optimizationKey`) est passée à l'optimiseur. Distinct de `solutions` (replay).
4. **`CMAESTrajectoryOptimizer`** : setter `withExternalSeed(double[])` ; la graine est **préposée au
   run 0** de l'attempt 0 (la graine analytique reste le filet aux runs suivants) et **clampée aux
   bornes** — le load a changé, la graine peut sortir de la boîte.

**Graine, pas replay** : les autres runs (analytique, perturbés) explorent toujours. Si la graine est
périmée (load très différent — cas Ariane divergent 18 t → 0,25 t), ils rattrapent. **Aucune
régression possible** : au pire la graine est ignorée, au mieux elle fait converger vite.

**Gate `orbitlab.opt.seedAcrossPasses` (défaut false)** : quand il est absent, `MeasuredLoadPlanner`
ne capture ni ne passe rien → `src/main` **strictement inchangé**, gates verts pendant la mesure. On
fige le défaut à `true` au commit de clôture (et re-baseline).

---

## 2. Mesure

Banc, property ON comparé au recordé post-B2 (A/B même-session sur les cellules FAST, cheap) :

- **`ARIANE64_LEO400_FAST`** — le meilleur cas (6 vols, GT re-cherché 6×), et **`FH_LEO400_FAST`**
  (2 vols).
- **BALANCED / PRECISE** — gain plus petit (le GT est une petite part du wall, dominé par le
  raffinement de transfert), mais le GT du vol final est bien amorcé.
- Lecture : baisse des **évaluations** (le signal propre ; le wall FAST est bruité par le warmup) et
  du wall ; verdict (λ\*, orbite) dans `REL-18`.

---

## 3. Acceptation et preuve

- **Gain** : baisse des évals/wall au banc, surtout sur l'Ariane (6 vols) et FAST.
- **Verdict-neutre** : λ\*, résidu, faisabilité dans `REL-18` sur les quatre missions de gate.
- **Gates re-baselinés** (la trajectoire bouge au bit près, protocole §4 point 6).

---

## 4. Ce que D2 ne fait pas

- **Pas de replay** : c'est une graine, le run explore autour (§1) ; le replay différé du GT est
  écarté (découpage §1, dangereux sur lanceur léger — PHY-2 / L4).
- **Ne touche pas le transfert** : il n'atteint jamais son `acceptableCost` (REL-30/32) et n'est
  cherché qu'au vol final (le dimensionnement est analytique en FAST) — l'amorçage du transfert d'un
  λ au suivant est `D1` (PRECISE).
- Aucun changement de verdict recherché ; une valeur qui *améliorerait* le verdict au-delà du bruit
  serait aussi hors barreau (verdict-neutre, découpage §0).
