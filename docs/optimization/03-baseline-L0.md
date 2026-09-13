# OPT-1 / L0 — baseline mesurée

Lot `L0` du découpage [`01-decoupage.md`](01-decoupage.md), conçu en
[`02-conception-L0.md`](02-conception-L0.md). Mesures produites par
`tools/optbench/OptBenchMain`, brut dans `optbench-out/baseline-L0.md`, `.jfr` par cellule à
côté. Ce document **cure** le brut : il retient les faits qui décident du backlog et corrige
ce que le modèle dérivé (découpage §3) avait faux.

**Conditions.** 12 processeurs logiques, GraalVM JDK 21.0.5, Windows 10, drag-on NRLMSISE
(défaut `MissionFactory`), époque 2026-01-01T12:00 UTC, graine CMA-ES 42. Cellules en
séquence, un `.jfr` (échantillonnage 10 ms) par cellule.

---

## 1. Résultats bruts

| Cellule | Mode | Wall | Vols | Évals | Résidu | Orbite atteinte (moy.) | CPU pool / calc |
|---|---|---:|---:|---:|---:|---|---|
| `FH_LEO400_FAST` | FAST | **64,8 s** | 2 | 3 487 | 0,0 % | 409 674 × 409 916 m | 98 % / 2 % |
| `FH_LEO400_BALANCED` | BALANCED | **560,0 s** | 3 | 13 656 | 0,2 % | 409 922 × 412 364 m | 61 % / 39 % |
| `FH_LEO400_PRECISE` | PRECISE | **1 879,0 s** | — | 61 981 | 0,0 % | 409 894 × 435 058 m | 75 % / 25 % |
| `GEO_SAT_FAST` | FAST | **échec 26,7 s** | — | 1 713 | — | — | 96 % / — |
| `ARIANE64_LEO400_FAST` | FAST | **242,7 s** | 6 | 18 058 | 0,0 % | 409 670 × 409 919 m | 99 % / 1 % |

- **λ S2** : FAST 0,630 · BALANCED 0,630 · PRECISE 0,311 · Ariane 0,019. PRECISE et l'Ariane
  minimisent l'étage haut jusqu'au fil ; le résidu tombe à 179 kg (PRECISE) et 170 kg (Ariane).
- **PRECISE atterrit plus lâche** : 409 894 × **435 058** m moyen (apogée +35 km) contre le
  400 circulaire de FAST — c'est le compromis assumé du mode (le plus *efficace*, pas le plus
  *précis* ; `OptimizationType`). À vérifier tout de même que +8,75 % d'apogée moyen reste dans
  la tolérance de faisabilité (l'osculateur, 419 × 425 km, est à +6,25 %).
- **Vols** : FAST 2, Ariane 6 (le cas divergent — 3 passes + 3 de bracket, comme prévu),
  BALANCED 3 (2 vols FAST de dimensionnement + 1 vol BALANCED final), PRECISE via balayage λ
  (0 `StageEntered`, les optim internes étant en `evaluationsOnly`).

---

## 2. Ce que la mesure corrige au modèle dérivé

**Le split FAST ~55/45 du découpage §3 est faux. Mesuré : ~98 / 2.** La propagation *post-recherche*
— transfert analytique + génération d'éphéméride, sur le thread `calc` — ne pèse que **2 %** du CPU
d'un calcul FAST, pas ~45 %. Le GT search en pèse 98 %.

**Mais « propagation 2 % » ne veut pas dire « les pistes C n'aident pas FAST ».** La propagation
*par candidat* — la vraie physique de chaque évaluation GT — vit **dans** le bucket recherche (les
threads du pool ne font que ça), et elle est **dominée par NRLMSISE** (§4). Autrement dit :

- le bucket `calc` 2 % = le déterministe *après* la recherche (transfert analytique + éphéméride),
  négligeable → les C n'ont rien à y gagner ;
- le bucket `pool` 98 % = les propagations de candidats, **liées à NRLMSISE** → une piste C qui
  réduit le coût par évaluation (moins de pas, modèle moins cher) y gagne, sur tous les modes.

Le modèle dérivé avait confondu les deux propagations. La conclusion pratique s'inverse : **C1
aide FAST**, non par le bucket `calc`, mais parce que chaque évaluation de la recherche est liée
NRLMSISE.

**Le wall FAST n'est pas 183-192 s (cloture PHY-2 §4) mais 64,8 s ici.** Écart ~3× sur une machine
différente ; l'exploration GT tire 4 runs (`numExplorationRuns = 4`) quels que soient les cœurs
au-delà de 4, donc l'écart est de la vitesse par cœur / du build, pas du parallélisme. **La
baseline d'OPT-1 est celle mesurée ici** ; c'est précisément pourquoi L0 re-mesure.

---

## 3. Le point chaud qui décide de L1 : le raffinement mono-thread

Il n'apparaît qu'avec un transfert CMA-ES (BALANCED/PRECISE), et il est massif :

- **BALANCED** : sur les 560 s, le vol final se décompose en GT (~22 s) + exploration de transfert
  (~86 s, sur le pool) + **raffinement de transfert ~399 s (71 % du calcul entier), mono-thread sur
  `calc`**. La timeline le montre : `StepStarted REFINEMENT` à t=160,7 s, fin à 560 s.
- **PRECISE** : le raffinement est dans le bucket `calc` 25 %, réparti sur les 8 optim du balayage.

**Attention à ne pas lire le split CPU comme du wall.** Le raffinement est mono-thread : 399 s de
**wall** mais, en parts d'échantillons CPU, seulement 39 % (l'exploration, elle, accumule du CPU
sur 4 threads en moins de wall). **Le split CPU sous-estime le poids wall d'une phase mono-thread**
— donc le gain wall de `A1` (paralléliser la génération, raffinement compris) est *plus grand* que
les 39 % ne le suggèrent. `A1` est validé comme le premier levier BALANCED/PRECISE.

---

## 4. Profil d'une évaluation (JFR) → ordre des lots C

`jfr view hot-methods`, méthodes les plus échantillonnées :

| Poste | FAST (ascension) | PRECISE |
|---|---:|---:|
| **NRLMSISE** (`densu`, `spline`, `splini`, `globe7`, `glob7s`) | ~17 % | ~11,5 % |
| **Transcendantes** (`cos`, `exp`, `Split.pow`, `sinQ`, `log`, `pow`) — en grande partie pilotées par NRLMSISE | ~34 % | ~27 % |
| **Repères / Vector3D** (`crossProduct`, `Transform`, `StaticTransform`, `Rotation`) | ~5 % | ~15 % |
| **Cache Orekit** (`GenericTimeStampedCache.getNeighbors`) + verrou `ReentrantReadWriteLock.tryAcquireShared` | ~2 % | ~2,3 % |
| Interpolateur DP853 | ~1 % | ~1,4 % |

**Ordre des lots C que ce profil dicte :**

1. **`C1` (tolérances)** — la propagation est liée NRLMSISE, appelé une fois par pas d'intégration :
   moins de pas ⇒ moins d'appels. Le plus haut levier **si** les pas sont limités par la tolérance
   et non par le plafond de 30 s / la détection d'événements — ce que le profil ne tranche pas seul
   (l'interpolateur ne pèse que ~1 %). À mesurer en premier dans le lot C.
2. **`C2` (Harris-Priester au transfert seul)** — remplace NRLMSISE (le poste n°1) par un modèle
   bon marché sur le transfert ; BALANCED/PRECISE. Le transfert vole surtout au-dessus de l'air,
   donc gain plus modéré que sur l'ascension, mais direct.
3. **`C4` (coast à `COAST_MAX_STEP`)** — moins de pas sur les coasts ⇒ moins de NRLMSISE ;
   conditionné au même constat que C1.

**Deux réserves lues dans le profil, hors périmètre C :**

- **Contention `A1`.** `GenericTimeStampedCache` (EOP/repères) est derrière un `ReentrantReadWriteLock` ;
  `tryAcquireShared` sort déjà à ~1-2 % **avant** `A1`. Plus de threads d'évaluation ⇒ plus de
  contention sur ce verrou : le speedup de `A1` sera probablement **sous-linéaire**. À surveiller au
  banc en L1.
- Les repères pèsent 3× plus en PRECISE (~15 %) qu'en FAST (~5 %) : le transfert fait plus de
  transformations de repère (coasts longs, apogée). Confirme que C2/C4 visent le bon mode.

---

## 5. L'échec GEO, tranché → `BUG-26`

`GEO_SAT_FAST` échoue : `OrekitException: minimal step size (1.00E-03) reached, integration needs
9.01E-04` (parking 250 km), `9.68E-04` (parking **400 km**, rejoué le 2026-09-13).

Le rejeu à 400 km l'a **localisé** : toutes les étapes d'optimisation passent — circularisation à
**35 786 km (GEO)**, trim, plane trim, coasting *done* — et la panne arrive 0,4 s après, dans la
**passe d'éphéméride** (`MissionEphemerisGenerator`, re-propagation complète pour l'affichage), pas
dans l'optimisation. Reproduit à 250 **et** 400 km : ce n'est **pas** la config du banc mais la
traînée sur la longue coast de restitution GEO. La mission GEO **s'optimise mais ne se restitue
pas**. **Hors périmètre OPT-1** → consigné en
[`BUG-26`](../bugs.md#bug-26--génération-déphéméride-geo-en-échec-dintégrateur-sous-traînée) ; la
ligne de baseline GEO FAST reste en attente de sa correction.

---

## 6. Conséquences pour le backlog

- **`A1` (L1)** confirmé premier levier : il attaque le raffinement mono-thread (71 % du wall
  BALANCED) et l'exploration sous-utilisée. Réserve : contention du cache Orekit (§4) → gain
  sous-linéaire, à mesurer.
- **`B2` (plancher 100 gén.)** : sur FAST, l'arrêt croisé masque déjà une partie du plancher (3 487
  évals pour 2 vols, soit < 4 runs × 600). Le gain net de B2 est donc à mesurer contre l'arrêt
  croisé, pas à présumer.
- **`D2` (amorçage GT)** : FAST re-cherche le GT 2 fois (Ariane 6 fois) ; chaque GT ~27 s (FAST) à
  ~40 s (Ariane). Amorcer les vols 2+ sur le vol 1 est un gain proportionnel au nombre de vols —
  **le plus rentable sur l'Ariane** (6 vols).
- **`C1` puis `C2`/`C4`** : ordre dicté par le profil (§4) ; C1 aide **tous** les modes (évaluation
  liée NRLMSISE), contrairement à ce que le split `calc` 2 % laissait croire.
- **PRECISE** : le balayage λ a convergé en **8 optim de 45 budget** (1 879 s) ; `B1`/`B3`/`D1`
  agissent sur le coût *d'une* de ces optim, `A1` sur son raffinement.

---

## 7. Réserves de mesure

- Split CPU par thread = parts d'**échantillons d'exécution**, pas de wall (§3) : il sous-estime le
  mono-thread. Le wall par phase se lit sur la timeline, pas sur le split.
- Un `.jfr` = un échantillonnage à 10 ms ; les pourcentages de §4 sont statistiques (±1-2 pts).
- Le contrefactuel « sans passe 3 » (`B3`) n'est pas mesuré ici — il demande une manette réservée à
  `B3` ([`02-conception-L0.md`](02-conception-L0.md) §3.3). L0 n'en donne que le **coût** (le
  raffinement, §3).
- `GEO_SAT_FAST` en échec : sa ligne de baseline est en attente (§5).
