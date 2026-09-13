# OPT-1 / B2 — mesures

Lot `B2` conçu en [`09-conception-B2.md`](09-conception-B2.md). Balayage du plancher de convergence
GT (`MIN_ITERS_BEFORE_CONVERGE`) au banc, même machine (12 threads), drag-on, graine 42, **sur la
tolérance C1** (`1e-5/1e-7`). Références : [`08-mesures-C1.md`](08-mesures-C1.md).

**Valeur retenue : `MIN_ITERS_BEFORE_CONVERGE = 50`** (figée dans `AdaptiveConvergenceChecker`).

**Clos le 2026-09-13** : petit gain FAST mesuré, verdict-neutre, gates re-baselinés et **verts**.

---

## 1. Knee net à 50 sur FAST (`b2-floorsweep-FH_LEO400_FAST.md`)

| Plancher | Wall (s) | Évals | λ S2 | Orbite moy. |
|---|---:|---:|---|---|
| 100 (défaut) | 33,3\* | 3 496 | 0,630 | 409674 × 409916, e=1.778e-05 |
| **50** | **19,6** | **3 022** | 0,631 | 409673 × 409916, e=1.785e-05 |
| 30 | 19,7 | 3 025 | 0,631 | identique |
| 20 | 21,3 | 3 009 | 0,631 | identique |
| 10 | 20,5 | 3 018 | 0,631 | identique |

- **Knee à 50, puis plateau** : tout le gain se prend de 100→50 ; 30/20/10 n'apportent rien (±bruit).
  Les runs GT stagnent naturellement vers ~gén. 40-50 ; descendre le plancher plus bas ne les fait pas
  converger plus tôt.
- **Signal propre = les évals : −14 %** (3 496 → 3 022). \*Le wall brut 33,3→19,6 (−41 %)
  **surestime** : la ligne plancher-100 est la première du run (warmup JVM à froid), les lignes ≤50
  sont à chaud. Le vrai gain wall FAST est **~−15 %** (19,6 contre le ~23,7 à chaud de C1).
- **Garde anti-graine : OK.** Les évals restent ~3 000 à **tous** les planchers → une vraie recherche
  a lieu même à 10, pas de retour graine (le GT n'est pas amorcé sous son seuil, contrairement au
  transfert).

---

## 2. Verdict-neutre sur tous les modes (`b2-floorsweep-FH_LEO400_BALANCED-FH_LEO400_PRECISE.md`)

Plancher 50 comparé au plancher 100 de C1 (même tol) :

| Mode | Wall (s) | Évals | λ\* | Orbite moy. | vs C1 (plancher 100) |
|---|---:|---:|---|---|---|
| BALANCED | 195,1 | 11 553 | 0,631 | 409919 × 412348 | wall ≈, évals −11 %, apogée −68 m |
| PRECISE | 668,3 | 56 373 | **0,311** | 409896 × 433632 | wall −3 %, évals −9 %, apogée −1 426 m |

- **λ\* PRECISE identique** (0,311), résidus inchangés (0,2 % / 0,0 %). Le plus grand mouvement de
  verdict de tout C1+B2 est l'apogée PRECISE (**−1 426 m**, ~7 % de REL-18) — sous le bruit, et vers
  le circulaire (pas de perte de faisabilité).
- **Le gain B2 sur BALANCED/PRECISE est en évals (−9 à −11 %), pas en wall** : ces modes sont dominés
  par le raffinement de transfert (mono-thread, 71 % du wall BALANCED) que B2 ne touche pas. Le wall
  est donc quasi inchangé là.

---

## 3. Bilan : petit levier, propre

B2 est **modeste** : ~−14 % d'évals / **~−15 % wall sur FAST**, quasi rien en wall sur
BALANCED/PRECISE (transfert-bound), verdict-neutre. C'est exactement ce que L0 §6 laissait présager —
l'arrêt croisé masque une partie du plancher, et le transfert domine les modes lents. Gratuit et sans
risque (plateau + garde anti-graine + verdict dans REL-18), donc retenu, mais sans illusion sur son
ampleur.

---

## 4. Ce qui reste

- **Re-baseline des gates** : fait le 2026-09-13 (`-Dorbitlab.recordBaseline=true` sur `gateTest`),
  gates verts. La trajectoire bouge au bit près (λ GT 0,630→0,631) ; le verdict était validé neutre au
  banc (§1-§2).
- **`D2`** (amorce GT à travers les passes de dimensionnement) reste le levier FAST/BALANCED suivant,
  et le plus rentable sur l'Ariane (6 vols). Indépendant de B2.
