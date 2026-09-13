# OPT-1 — clôture

Chantier `OPT-1` (temps de calcul des trajectoires), découpé dans [`01-decoupage.md`](01-decoupage.md).
**Clos le 2026-09-14.** Ce document récapitule ce qui a été livré, ce qui a été abandonné, et le
reliquat remis à **`OPT-2`**.

---

## 1. Ce qui a été livré

| Lot | Ce qu'il a fait | Gain mesuré | Verdict |
|---|---|---|---|
| **L0** | Banc `tools/optbench` + baseline mesurée, les trois modes | — (instrument) | — |
| **L1a** | Fork `ParallelCMAESOptimizer` + holder, génération parallèle **au raffinement** | BALANCED **−55 %**, PRECISE **−46 %** | verdict inchangé |
| **C1** | Tolérances de l'intégrateur d'optim `1e-8/1e-10 → 1e-5/1e-7` | FAST **−58 %**, BALANCED **−66 %**, PRECISE **−63 %** | verdict-neutre (PRECISE bit-identique) |
| **B2** | Plancher de convergence GT `100 → 50` | FAST ~**−15 %** wall / −14 % évals | verdict-neutre |
| **D2** | Amorçage du GT entre passes de dimensionnement | FAST ~**−13 %** évals | bit-identique off/on |

**Cumul FAST** (la cible primaire) : d'environ **55 s** (baseline L0 sur la machine) à **~18 s** après
C1+B2+D2 — les trois leviers se composant (≈ −67 %). C1 en porte l'essentiel ; B2 et D2 sont petits
mais gratuits et verdict-neutres. BALANCED/PRECISE ont eu leur grand gain par **L1a** (raffinement
parallèle), C1 s'y ajoutant.

Chaque lot à comportement modifié a été mesuré au banc et jugé **verdict-neutre** (`REL-18`, ~19 km) ;
gates re-baselinés là où la trajectoire bougeait au bit près (C1, B2). D2, vivant dans le planner que
les gates contournent, ne les a pas touchés.

**Instrument réutilisable** : le banc `OptBenchMain` porte trois balayages de tuning
(`--tolSweep`/`--floorSweep`/`--seedSweep`) et les overrides système correspondants, défauts
inchangés en production. Réutilisables tels quels par `OPT-2`.

---

## 2. Ce qui a été abandonné

- **L1b — exploration parallèle** ([`06-conception-L1b.md`](06-conception-L1b.md), [`REL-33`](../reliquats.md)).
  Paralléliser la génération **à l'exploration** casse la bit-identité sous l'arrêt croisé :
  **bit-identité + arrêt croisé + exploration parallèle sont incompatibles**. Reverté à L1a. Une
  reprise déterministe (îlots synchronisés, ou abandon de l'arrêt croisé) est possible **hors du
  barreau tolérance-zéro** — remise à `OPT-2` via `REL-33`.

---

## 3. Le reliquat → `OPT-2`

Le découpage d'OPT-1 (§5.3) définissait un **backlog priorisé** dont seuls `C1`, `B2`, `D2` (et `A1`
via L1a) ont été joués. Le reste part en `OPT-2` — voir [`roadmap/02-roadmap-v2.md`](../roadmap/02-roadmap-v2.md)
§4 `OPT-2`. En résumé, surtout des **leviers du transfert BALANCED/PRECISE** (là où OPT-1 a le moins
mordu, le transfert dominant leur wall) :

- **`B1`** — rendre la convergence du transfert atteignable (le seuil est inatteignable, REL-30/32,
  le budget se dépense en entier). Probablement le premier levier BALANCED/PRECISE.
- **`C2`** — Harris-Priester au transfert seul (candidat *c* de PHY-2, l'ascension reste NRLMSISE).
- **`C4`** — découper le transfert en coast + poussées (coast à `COAST_MAX_STEP`). **Toujours
  valable** : C1 a mesuré le pas **tol-borné**, donc relâcher le plafond de coast reste un levier
  distinct.
- **`D1`** — PRECISE : amorcer le transfert d'un λ sur le λ précédent (la plomberie de graine par clé
  de D2 est réutilisable).
- **`B3`** — retirer la passe 3 de raffinement / conditionner la passe 2, **avec** le contrefactuel de
  verdict que L0 n'a pas pu jouer.
- **`B4`/`B5`/`B6`** — nombre de runs d'exploration / élargir la première exploration / ce que 40 000
  évals achètent contre 8 000.
- **`A3`** — paralléliser la boucle λ externe de PRECISE (dispute les cœurs à A1 — en dernier).
- **`REL-33`** — reprise éventuelle de l'exploration parallèle, déterministe, hors tolérance-zéro.

**Hors périmètre optimisation, non résolu** : [`BUG-26`](../bugs.md) — l'échec d'intégrateur de la
génération d'éphéméride GEO sous traînée. Reste un `BUG`, pas un lot d'`OPT-2` ; la baseline GEO du
banc reste en attente de sa correction.
