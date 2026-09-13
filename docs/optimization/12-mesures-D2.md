# OPT-1 / D2 — mesures

Lot `D2` conçu en [`11-conception-D2.md`](11-conception-D2.md). Amorçage du GT d'une passe de
dimensionnement sur la solution GT de la précédente (graine, pas replay). A/B au banc via
`--seedSweep` (off/on même-session), **sur C1+B2** (tol 1e-5/1e-7, plancher 50), drag-on, graine 42.

**Défaut retenu : `orbitlab.opt.seedAcrossPasses = true`** (`MeasuredLoadPlanner`).

**Clos le 2026-09-14** : petit gain FAST mesuré, verdict **bit-identique** off/on, gates inchangés
(D2 ne passe pas par leur chemin — voir §3).

---

## 1. A/B FAST (`d2-seedsweep-ARIANE64_LEO400_FAST-FH_LEO400_FAST.md`)

| Cellule | | Wall (s) | Évals | λ S2 | Orbite moy. |
|---|---|---:|---:|---|---|
| **FH FAST** (2 vols) | off | 31,7\* | 3 013 | 0,631 | 409673 × 409916, e=1.785e-05 |
| | on | 18,7 | **2 618** | 0,631 | 409673 × 409916, e=1.787e-05 |
| **Ariane FAST** (6 vols) | off | 64,4 | 8 902 | 0,013 | 409671 × 409919, e=1.823e-05 |
| | on | 59,4 | **7 768** | 0,013 | 409671 × 409919, e=1.828e-05 |

- **Verdict bit-identique off/on** : λ inchangé, orbite **identique au mètre** sur les deux cellules
  (e frémit au 8ᵉ chiffre). La graine **accélère la convergence vers le même optimum GT**, elle ne
  le déplace pas — neutralité plus forte que C1/B2.
- **Gain : ~−13 % d'évaluations** (FH 3 013→2 618, Ariane 8 902→7 768). Wall −8 % Ariane ; le FH
  \*off est gonflé par le warmup (première ligne, JVM à froid).

---

## 2. Correction à L0 §6 : l'Ariane n'est pas le standout

L0 §6 pariait sur l'Ariane (6 vols) comme « le plus rentable ». **Démenti** : elle donne le **même
−13 %** que le FH (2 vols). Cause mesurée : la boucle de dimensionnement Ariane **diverge** (loads
qui swinguent d'un ordre de grandeur, ~18 t → ~0,25 t sur les passes de bracket), donc la graine
d'une passe est loin de l'optimum GT de la suivante et est le plus souvent **gaspillée** — le filet
analytique rattrape (« graine, pas replay »). Le gain n'est donc **pas proportionnel au nombre de
vols** quand les loads divergent ; il vaut ~−13 %, comparable à `B2`. Un lanceur dont le
dimensionnement converge doucement (loads stables) en profiterait davantage par passe — mais le FH
converge déjà en 2 vols.

---

## 3. Pourquoi les gates ne bougent pas (et n'ont pas été re-baselinés)

Contrairement à C1 (tolérance de `createOptimizationPropagator`) et B2 (plancher dans
`AdaptiveConvergenceChecker`) — tous deux dans le cœur d'optimisation que les gates exercent — **D2
vit dans `MeasuredLoadPlanner`**, la boucle de dimensionnement multi-passes. Or les quatre gates
**contournent le planner** : `EarthOrbitNonRegressionTest` vole via `StageChainRunner.plain()`,
`AscentBaselineN2Test` via `MissionOptimizer` direct, `CentralBodyBaselineTest` via `PropellantBudget`
+ direct, `MissionPolylineBaselineTest` de même. Et `MissionOptimizer` / `FixedLoadPlanner` /
`CMAESTrajectoryOptimizer` avec `seeds`/`externalSeed` nuls se comportent **exactement** comme avant
D2. **Donc D2 est transparent aux gates** : ils restent verts sans re-baseline. Le verdict de D2 est
validé par le **banc** (§1, bit-identique off/on), pas par les gates. Vérifié : `gateTest` vert après
la bascule du défaut.

---

## 4. Bilan

D2 vaut ~−13 % d'évaluations sur FAST, verdict bit-identique, gratuit et sans risque (graine, pas
replay). Petit — comme B2, et pour une raison structurelle (arrêt croisé +, ici, loads divergents
qui gaspillent la graine). Retenu. La plomberie (graine par clé à travers 4 couches, défaut off puis
on) reste disponible pour un futur amorçage inter-λ (`D1`, PRECISE).
