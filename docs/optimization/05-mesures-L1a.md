# OPT-1 / L1a — mesures

Sous-lot `L1a` du découpage [`01-decoupage.md`](01-decoupage.md), conçu en
[`04-conception-L1.md`](04-conception-L1.md) : fork `ParallelCMAESOptimizer` + holder
`OptimizerThreadPool`, génération évaluée en parallèle **au raffinement** (non imbriqué).
Baseline de comparaison : [`03-baseline-L0.md`](03-baseline-L0.md). Même banc, même machine (12
threads), drag-on, seed 42.

**Livré le 2026-09-13.** Gates verts ; verdict inchangé confirmé par la mesure.

---

## 1. Verdict inchangé (l'acceptation)

λ par étage, orbite atteinte (au mètre), ΔV total et résidu sont **identiques à L0** sur les
quatre cellules qui volent :

| Cellule | λ S2 | Orbite moy. | ΔV | Résidu | vs L0 |
|---|---|---|---:|---:|---|
| FH FAST | 0,630 | 409 674 × 409 916 m | 9 074 | 420 kg | identique |
| FH BALANCED | 0,630 | 409 922 × 412 364 m | 8 604 | 2 547 kg | identique |
| FH PRECISE | 0,311 | 409 894 × 435 058 m | 8 569 | 179 kg | identique |
| Ariane FAST | 0,019 | 409 670 × 409 919 m | 8 811 | 170 kg | identique |

Le nombre d'évaluations varie de < 0,4 % (3 556 vs 3 487, etc.) : c'est l'arrêt croisé, **déjà**
non déterministe, sans effet sur le verdict. Gates verts + verdict identique = `L1a`
verdict-neutre, comme promis.

---

## 2. Accélération

| Cellule | L0 (s) | L1a (s) | Δ |
|---|---:|---:|---:|
| FH FAST | 64,8 | 53,7 | −17 % (variance ; FAST ne raffine pas) |
| **FH BALANCED** | 560,0 | **254,5** | **−55 %** |
| **FH PRECISE** | 1 879,0 | **1 011,3** | **−46 %** |
| Ariane FAST | 242,7 | 252,5 | +4 % (bruit) |
| GEO FAST | échec | échec | [`BUG-26`](../bugs.md) inchangé |

Le gain est **là où le raffinement pèse** (BALANCED/PRECISE), nul là où il n'y en a pas
(FAST/Ariane, aux variances de mesure près) — exactement ce que le modèle de L0 prédisait.

**Le raffinement de transfert BALANCED : 399 s → 109 s (−73 %, ~3,65×)** (timeline :
`REFINEMENT` à t=145 s, fin à 254 s). PRECISE, dont chaque évaluation de λ raffine, tombe de
1 879 à 1 011 s.

---

## 3. Première lecture de la contention (pour L1b)

Le raffinement parallélise une génération de **8 candidats** (transfert, n=4). **~3,65× pour
8-wide, soit ~46 % d'efficacité** : le reste part en surcoût d'orchestration et en **contention
du cache Orekit** (baseline §4). C'est mesuré sur le cas *non imbriqué* et *peu large* (8 ≤ 11
threads). `L1b` attaque le cas plus dur — l'exploration passe de 4 à ~11 threads **imbriqués** —
où la contention pèsera davantage : ce 46 % est le point de départ, pas une garantie.

---

## 4. Ce que L1a a fait, et ce qui reste à L1b

**Fait** : le fork (bit-identique par construction — comptage mono-thread, réduction par index,
mode séquentiel = amont), le holder `OptimizerThreadPool` (daemon, `procs−1`, threads
`cmaes-eval-N`), le raffinement branché dessus. Banc : `classifyThread` reconnaît maintenant les
threads `cmaes-eval` (le run ci-dessus les rangeait dans « other » — les 58 % de BALANCED sont le
pool de raffinement, pas du bruit).

**Reste à L1b** :

- **A2 pleinement** : l'exploration crée encore son propre `Executors.newFixedThreadPool` par
  phase (non-daemon). L1b la bascule sur le holder — c'est ce que l'imbrication exige de toute
  façon — et ferme du même coup le pool non-daemon qui retient le process ([`REL-21`](../reliquats.md)).
- **Le refactor de l'état mission** (conception §3) : `CoastingStage`/`ConstantThrustStage`
  relisent `mission.getCurrentState()` dans `configure` ; à faire lire l'état passé avant de
  paralléliser l'exploration imbriquée.
- **Le gain FAST** : c'est l'exploration, donc L1b. `L1a` ne l'a pas touché (et ne le devait pas).
