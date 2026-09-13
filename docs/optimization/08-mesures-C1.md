# OPT-1 / C1 — mesures

Lot `C1` conçu en [`07-conception-C1.md`](07-conception-C1.md). Balayage de la tolérance scalaire de
`createOptimizationPropagator` au banc, même machine (12 threads), drag-on, graine 42. Baseline de
comparaison : L0 ([`03-baseline-L0.md`](03-baseline-L0.md)).

**Valeur retenue : `absTol = 1e-5`, `relTol = 1e-7`** (figée dans `OrekitService`).

**Clos le 2026-09-13** : gain mesuré, verdict-neutre au banc, gates re-baselinés et **verts**.

---

## 1. La question de L0 est tranchée : le pas est tol-borné

Le wall chute de façon **monotone** quand la tolérance se desserre — donc le pas d'intégration était
limité par la **tolérance**, pas par le plafond (30 s combustion / 300 s coast) ni par la détection
d'événements. La réserve de L0 est levée par la mesure. Balayage FAST
(`c1-tolsweep-FH_LEO400_FAST.md`) :

| absTol/relTol | Wall (s) | Δ vs défaut | Évals | Orbite atteinte (moy.) |
|---|---:|---:|---:|---|
| 1e-8/1e-10 (défaut) | 55,8 | — | 3 537 | 409674 × 409916, e=1.778e-05 |
| 1e-7/1e-9 | 35,5 | −36 % | 3 512 | identique |
| 1e-6/1e-8 | 28,4 | −49 % | 3 398 | identique |
| **1e-5/1e-7** | **23,7** | **−58 %** | 3 442 | identique |
| 1e-4/1e-6 | 17,8 | −68 % | 3 211 | e=1.776e-05 (frémit) |

Les évaluations sont quasi constantes : le gain vient de **pas moins nombreux** (moins d'appels
NRLMSISE par évaluation), pas de moins d'évaluations — exactement le mécanisme visé (L0 §4).

---

## 2. Verdict-neutre sur tous les modes, PRECISE compris

Le risque annoncé (coût bruité gênant le raffinement PRECISE à σ×0,01) **ne s'est pas matérialisé**.
Confirmation BALANCED + PRECISE (`c1-tolsweep-FH_LEO400_BALANCED-FH_LEO400_PRECISE.md`), verdict
comparé aux valeurs L0 :

| Cellule | Tol | Wall (s) | Δ vs L0 | λ | Orbite moy. | vs L0 |
|---|---|---:|---:|---|---|---|
| BALANCED | L0 1e-8/1e-10 | 560,0 | — | .630 | 409922 × 412364 | ref |
| BALANCED | **1e-5/1e-7** | **192,8** | **−66 %** | .630 | 409922 × 412416 | +52 m apogée |
| BALANCED | 1e-4/1e-6 | 151,3 | −73 % | .630 | 409918 × 412455 | +91 m apogée |
| PRECISE | L0 1e-8/1e-10 | 1 879,0 | — | .311 | 409894 × 435058 | ref |
| PRECISE | **1e-5/1e-7** | **685,9** | **−63 %** | .311 | 409894 × 435058 | **identique au mètre** |
| PRECISE | 1e-4/1e-6 | 630,6 | −66 % | .311 | 409894 × 435053 | −5 m apogée |

À **1e-5/1e-7** : λ\* identique partout, PRECISE **bit-identique** (le mode le plus dur), BALANCED à
+52 m d'apogée (~365× sous REL-18 = 19 km), FAST bit-identique. Résidu inchangé (0,2 % / 0,0 %).
Convergence intacte (évals du même ordre : PRECISE 61 698 vs 62 056 L0).

---

## 3. Le choix de la valeur

**1e-5/1e-7 est le *sweet spot*.** Il prend l'essentiel du gain (−58 % / −66 % / −63 %) avec un
verdict **bit-identique sur PRECISE** et à quelques dizaines de mètres ailleurs (erreur locale ~0,7 m,
~27 000× sous REL-18). Le cran agressif 1e-4/1e-6 ne rapporte que **+3 à +11 %** de plus (surtout sur
PRECISE : −66 % contre −63 %, marginal) pour **10× l'erreur de troncature locale** (7 m) et une dérive
qui commence à se voir (e frémit en FAST, apogée BALANCED +91 m). Comme ce propagateur est **la seule
source de fidélité** en production (§0 — pas de re-vérification 50×50), la marge vaut mieux que ces
quelques pour-cent.

---

## 4. Ce qui reste

- **Re-baseline des gates** : **fait le 2026-09-13**. La trajectoire bouge au bit près → les quatre
  gates ré-enregistrées (`-Dorbitlab.recordBaseline=true` sur `gateTest`) puis **vertes**. Le verdict
  avait été validé neutre au banc (§2) ; le re-baseline n'a fait qu'enregistrer les nouveaux bits.
- **`C4`** (coast à `COAST_MAX_STEP`) était conditionné au constat de C1 : le pas étant tol-borné (et
  non cap-borné), `C4` **garde du sens** — desserrer la tolérance et relâcher le plafond de coast sont
  deux leviers distincts sur le nombre de pas. À réexaminer à son tour.
- **`BUG-26`** (échec `minStep` éphéméride GEO) : non rejoué ici. Des pas plus grands *pourraient*
  l'avoir déplacé, mais C1 ne le chasse pas ; GEO reste hors baseline.
