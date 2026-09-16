# PHY-5 / L4 — La LEO livre sa charge utile — conception

Redirection du 2026-09-15, en cours de L4. L'utilisateur :

> *« Une LEO devrait mettre sa payload en orbite (c'est sa mission après
> tout). Il faut donc une séparation supplémentaire. La payload doit être
> équipée d'une propulsion qui est responsable de la circularisation voire du
> trim. »*

Choix tranché en conversation : la charge utile fait **le trim final seul** (le
S2 garde la circularisation optimisée). Ce lot **scinde l'ancien L4** du
découpage [`01-decoupage.md`](01-decoupage.md) §5 :

- **L4 (ce document)** — la **physique** : une LEO à charge utile propulsée
  largue son S2 après le transfert, et la charge utile fait son trim.
- **L5** (ex-L4) — le **rendu** : le primaire maigrit jusqu'au maillage de la
  charge utile (absorbe `PHY-6`). Décrit plus tard.

Un changement de comportement à la fois (découpage §4) : L4 change la
**trajectoire** d'une classe de missions, L5 changera le **maillage dessiné**.

---

## 1. Objet du lot

Faire livrer, par une mission LEO **dont la charge utile porte de l'ergol
utilisable**, sa charge utile : le S2 se sépare après le transfert, et la charge
utile fait son trim final avec sa propre propulsion. C'est **exactement ce que
le GEO fait déjà** — largage `UPPER` puis combustion de la charge utile (l'AKM) —
transposé à un trim au lieu d'une combustion d'apogée.

---

## 2. État des lieux *(mesuré)*

- **Chaîne LEO** ([`EarthOrbitMission`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/EarthOrbitMission.java)) :
  `Ascension → GT(S1) → sép. S1 → GT(S2) → Transfert → Trim → [Plane trim] → Coast`.
  **Une seule séparation** (S1, dans l'ascension) ; le **S2 fait transfert +
  trim + plane trim** ; la charge utile voyage en masse inerte.
- **Le GEO le fait déjà** ([`GEOMission`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/GEOMission.java) l. 229-241) :
  `… GTO injection → StageSeparationStage(UPPER) → Circularization(AKM) → Trim →
  Plane trim`. Patron réutilisé tel quel ; `StageRole.UPPER` porte un garde qui
  refuse de tirer si le S2 n'est pas l'étage actif.
- **Les quatre gates volent `Spacecraft.LEGACY` (0 ergol) en LEO** :
  `EarthOrbitNonRegressionTest`, `MissionPolylineBaselineTest`,
  `AscentBaselineN2Test`, `CentralBodyBaselineTest` (l. 1513). Les deux tests
  d'optim LEO (`LEOMissionOptimizationTest`, `…OptimizedTransferTest`) chargent
  la charge utile à `0.0`.
- **La charge utile catalogue LEO est déjà chargée** de son budget ΔV :
  `PropellantBudget.loadsForLeo(PayloadModel…)` calcule un `payloadLoad` depuis
  `deltaVBudget` (15 m/s pour `EARTH_OBS_SAT`), et le Javadoc le dit —
  *« ride to orbit where the payload starts spending it »*. Le trim LEO mesuré
  est **~6 m/s** (PHY-8 / L0). **Aucun changement de budget nécessaire** : la
  combustion existait en intention, on la branche.
- **Point d'entrée unique** :
  [`MissionComposer.composeEarthOrbit`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/MissionComposer.java)
  (l. 141/165/173) passe par les trois fabriques `EarthOrbitMission`. Le wizard
  est couvert par une seule modification.

---

## 3. Décisions de conception

### 3.1 D1 — Déclenchement conditionnel : de l'ergol utilisable

`Spacecraft.hasUsablePropellant()` = `propulsion != null && propellantLoad > 0`.
La LEO largue le S2 et confie le trim à la charge utile **si et seulement si**
ce prédicat est vrai.

- Charge **inerte / legacy** (`Spacecraft.LEGACY`, ou une charge catalogue
  chargée à 0) → prédicat faux → **chaîne d'aujourd'hui au bit près** → les
  quatre gates ne bougent pas → **invariant préservé, aucune re-baseline**.
- Charge **propulsée** (une charge catalogue du wizard, chargée > 0) → nouvelle
  chaîne (§3.2).

C'est ce prédicat, et non un drapeau de `MissionType`, qui fait la bascule : il
laisse `MissionType.LEO.requiresPayloadPropulsion()` à `false` (une LEO inerte
reste légale) et rend le changement **invisible à tout ce qui vole une charge
inerte**.

### 3.2 D2 — La chaîne propulsée

`… Transfert(S2) → [Plane trim(S2) si plan commandé] → S2 separation(UPPER) →
Trim(charge utile) → Coast`.

- `new StageSeparationStage(StageNames.UPPER_SEPARATION,
  profile.interstageCoastDuration(), StageRole.UPPER)`, garde de rôle compris —
  identique au GEO.
- **Le `Transfert` optimisé reste sur le S2 → CMA-ES intouché.** Seules la queue
  analytique (le trim) et la chute de masse S2 changent, et **uniquement pour
  une charge propulsée** (jamais gatée).
- Après le largage, `VehicleStack.resolveActiveStage(masse)` rend la propulsion
  de la charge utile — le même mécanisme, fondé sur la masse, qui fait tirer
  l'AKM en GEO.

### 3.3 D3 — Le plane trim reste sur le S2 *(correction d'une proposition)*

Proposition initiale (calque GEO) : « plane trim sur la charge utile ». **Mesure
contraire** : un résidu de plan LEO, même faible (0,1–0,25°), coûte **13–33 m/s**
à 7,6 km/s — **au-delà du budget de 15 m/s** de la charge utile ; l'ajouter au
trim (~6 m/s) la mettrait à sec (le `DepletionGuard` tirerait). GEO pouvait le
lui confier parce que son AKM porte **2 000 kg** d'ergol, la charge LEO en porte
**100**.

Donc la charge utile ne porte **que** le `AnalyticTrimBurnStage` (~6 m/s) ; le
`AnalyticPlaneTrimAtNodeStage` reste sur le **S2**, **avant** la séparation.

- **Conséquence** : dans la branche propulsée à plan commandé, le plane trim
  passe **avant** le trim final (réordonnancement local `Transfert → Plane trim →
  sép. → Trim`). La branche inerte garde `Transfert → Trim → [Plane trim]`
  **inchangée** — c'est ce qui garde les gates saufs.

### 3.4 D4 — Portée et risque assumé

- **Cas vedette** : une charge d'observation en orbite héliosynchrone
  (landsat-like, polaire) commande un plan → le plane trim existe, et il est sur
  le S2.
- **Risque non gaté, à vérifier en propagation** : réordonner plane-trim / trim
  et trimmer au moteur de la charge utile (400 N, plus faible que le S2) décale
  la queue de trajectoire et l'orbite atteinte de *cette* mission ; la sûreté
  numérique du réordonnancement analytique reste à confirmer. Vérification par
  les tests d'optim LEO (lancés par l'utilisateur) + un test propulsé neuf.

---

## 4. L'invariant et la vérification

- **Invariant préservé.** La branche inerte est byte-identique à aujourd'hui →
  les quatre gates à tolérance zéro restent verts **sans re-baseline** ; le
  transfert CMA-ES n'est pas touché. `L4` relance `gateTest` pour l'acter.
- **TDD (rapide, sans propagation)** :
  - `Spacecraft.hasUsablePropellant()` — legacy faux, catalogue chargé vrai,
    catalogue à 0 faux ;
  - composition de chaîne `EarthOrbitMission` — charge propulsée : une
    `S2 separation` apparaît entre `Transfert` et `Trim` (et **après** le plane
    trim quand un plan est commandé) ; charge inerte : **aucune** séparation, la
    chaîne est celle d'aujourd'hui.
- **Propagation** (la charge utile trimme et atteint l'orbite) → tests d'optim
  LEO de l'utilisateur + un test propulsé neuf.

---

## 5. Ce que L4 lègue à L5 *(rendu)*

Une séparation S2 en LEO fait apparaître, en LEO aussi, l'objet « charge utile
qui continue » après le dernier largage : le maigrissement primaire → charge
utile de L5 y devient visible (`goes` en GEO, `landsat8` / `lro` en LEO), et le
double-dessin S2 se ferme partout, pas seulement en GEO.
