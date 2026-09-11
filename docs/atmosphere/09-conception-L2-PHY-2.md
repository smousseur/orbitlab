# PHY-2 / L2 — Isp recalibrée (`DT-13`) — conception

Conception du lot `L2` de `PHY-2`, découpé en
[`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) §5. Le lot sort la traînée de l'Isp
« moyenne de trajectoire » du catalogue et la rend explicite. Premier lot de physique de
`PHY-2` : il **change les trajectoires drag-off** (re-baseline) et prépare la bascule de
`L5`.

> **Statut : conception et mesure livrées ; implémentation repliée sur `L3`.** Un banc jetable
> (écrit puis supprimé) a **mesuré la traînée réelle par lanceur**, et le résultat a **corrigé le
> cadrage initial du lot** — la décision `B` uniforme et la prémisse « le proxy baked ~la traînée »
> sont toutes deux tombées (§7). Les valeurs d'Isp ne sont pas figées ici, et le catalogue reste à
> 296 : poser l'Isp et re-baseliner se fait **à `L3`**, la mesure ayant montré que la vérification
> drag-on du FH exige d'abord son hand-off redressé (§6).

---

## 1. Périmètre du lot

**Dans `L2`.** Re-poser les deux Isp qui portent un proxy « moyenne de trajectoire » et rendre
la traînée explicite :

| Étage | Isp actuelle | Sort en L2 |
|---|---|---|
| FH **Boosters** + **Core** | `296` (×2), bracket [282, 311] | re-posé (relocalise sa traînée) |
| Ariane **Vulcain** | `360`, bracket [320, 431] | re-posé à son lapse physique |

**Hors `L2`.** Les étages **sans marge d'Isp ni dette d'Isp** — P120C (278,5, Isp de vide
d'un solide), FH S2 (348), Vinci (457). Et une Isp par pression : `PropulsionSystem` est
`(isp, thrust)` figé, `L2` pose **un nombre** par étage.

---

## 2. État des lieux mesuré

### 2.1 Deux proxys, calibrés sur un ratio, pas en absolu

Le catalogue compense les pertes d'ascension par un Isp « moyen trajectoire », et ses Isp ne
sont **pas** des valeurs absolues : elles sont réglées pour que le **ratio de capacité
FH/Ariane = 2,10** ([`Launchers` §130-136](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/catalog/Launchers.java)),
la capacité absolue du catalogue étant « not trustworthy » (Ariane 62 plaçait 20 t en LEO
contre ~10,3 t réels).

### 2.2 Ce que « 396 / 64 » sont vraiment — le déficit d'Isp, pas la traînée

`DT-13` (séance `J2`) parlait d'une « perte de traînée implicite » de **396 m/s** (FH) et
**64 m/s** (Vulcain). Mais [`05-conception-L2.md` §4.3](05-conception-L2.md) est explicite :
ces nombres sont un **calcul de catalogue sans propagation**, `Δv(Isp vide) − Δv(Isp moyen) =
g₀·ΔIsp·ln R` — le **déficit d'Isp** entre le vide et le proxy — et il **avertit** que « le
proxy paie **davantage que la seule traînée**, la différence devant être attribuée — pilotage,
gravité, ou marge ». Donc 396/64 ne sont **pas** la traînée.

### 2.3 La traînée réelle, mesurée — petite pour le FH, grande pour l'Ariane, **inversée**

Le banc a intégré `∫|a_drag| dt` (densité NRLMSISE) sur l'ascension **drag-off** de chaque
lanceur — la traînée pure sur la trajectoire volée, isolée de l'optim et de la faisabilité :

| | traînée mesurée | déficit d'Isp (§2.2) | surface frontale bloc bas |
|---|---|---|---|
| **Falcon Heavy** | **50,7 m/s** | 396 | 31,5 m² (3 corps) |
| **Ariane 64** | **230,3 m/s** | 64 | **59,22 m²** (4 boosters + Vulcain) |

Physiquement cohérent, et **inversé** par rapport aux déficits : le FH est lourd et élancé
(coefficient balistique élevé → peu de traînée) ; l'Ariane est plus légère et **large** (59 m²
de front présenté au décollage, documenté au catalogue) → beaucoup plus de traînée.
**Premier ordre** : mesure sur la trajectoire drag-off (la vraie drag-on est un peu plus
haute) ; l'inversion, elle, est robuste — pilotée par la surface et la masse.

### 2.4 La traînée Ariane est sur les boosters, qui n'ont aucune marge d'Isp

Les **36 des 59 m²** frontaux sont les quatre P120C — des **solides à leur Isp de vide**
(278,5), donc **zéro marge** pour relocaliser leur traînée dans l'Isp. Le seul étage à marge
est le Vulcain, et sa marge totale (360 → son vide 431) rend **exactement son déficit, 64 m/s**
(`DT-13`). Contre 230 m/s de traînée, **166 m/s sont donc incompensables par l'Isp**. Le FH,
lui, a ~400 m/s de marge Isp (296 → 311 sur un rapport de masse 16) pour seulement 51 m/s de
traînée.

---

## 3. Décisions de conception

### 3.1 `A`/`B` **par lanceur** — imposé par la physique, pas choisi

La décision `B` (préserver la capacité en relocalisant la traînée dans l'Isp) supposait que la
traînée *rentre* dans la marge d'Isp. §2.4 le tranche par lanceur :

- **FH → `B`.** Marge Isp ~400 m/s ≫ traînée 51 → on relocalise : Isp montée juste assez pour
  que drag-on (Isp neuve + traînée explicite) rende la même capacité que drag-off au proxy.
  Capacité préservée.
- **Ariane → `A`.** Marge Isp 64 m/s ≪ traînée 230, et la traînée est sur les boosters solides
  (0 marge). **La capacité ne peut pas être préservée** : la rendre explicite la fait **baisser
  d'environ 166 m/s**, qu'aucun Isp ne restaure. Ce n'est pas un échec de calibration — c'est
  que le catalogue Ariane était **drag-optimiste** (la traînée des boosters n'était bakée nulle
  part, masquée par le calage-ratio), et l'expliciter *corrige* ce fantôme. Le ratio 2,10, qui
  était aveugle à la traînée, se **décale** en conséquence.

### 3.2 Comment chaque Isp est posée

- **FH (`B`).** `lapse-Isp` relevée de ~51 m/s d'équivalent-ΔV sur le bloc bas — de `296` vers
  **~298** (premier ordre ; affiné à la re-baseline). Boosters et core prennent la **même**
  valeur (même moteur).
- **Ariane (`A`).** Le Vulcain va à son **lapse physique** (sa moyenne thrust-weighted sol/vide,
  dans [320, 431] — à poser en volant, sans plus la tirer vers le bas pour le ratio) ; les P120C
  restent à leur Isp de vide (déjà physique) ; la traînée est explicite. Aucune compensation
  d'Isp de la baisse de capacité — c'est le sens de `A`.

**La calibration reste bon marché.** La traînée se lit sur une ascension nominale (le banc), pas
sur une optimisation ; le coût ×7,5 de l'optim drag-on ne touche ni la mesure ni la re-baseline
drag-off.

### 3.3 L'invariant drag-off est retiré, et la baisse Ariane se matérialise à `L5`

Après `L2`, un vol **drag-off** (le défaut jusqu'à `L5`) vole aux Isp neuves sans traînée : les
baselines se re-enregistrent (attribuable à l'Isp seule). La **baisse de capacité de l'Ariane
n'apparaît qu'au drag-on** — donc à la re-baseline drag-on et surtout à la bascule de `L5`. `L2`
lui-même ne casse rien : il re-pinne les baselines drag-off et pose la physique que `L5`
allumera.

---

## 4. Sorties

- **FH bloc bas** : une Isp neuve (~298, posée en volant), traînée explicite. Capacité drag-on
  ≈ capacité drag-off actuelle.
- **Ariane Vulcain** : son lapse physique (posé en volant) ; P120C inchangés ; traînée explicite.
  Capacité drag-on **physique** (≈ −166 m/s vs le catalogue drag-blind).
- **La traînée réelle consignée** : 51 m/s (FH) / 230 m/s (Ariane), et l'écart au déficit d'Isp
  (396/64) — l'entrée corrigée de `L3` et `L5`.
- **Gates + `AscentBaselineN2Test` re-baselinés** drag-off aux Isp neuves.

---

## 5. Fermeture — par lanceur

1. **FH — capacité préservée.** Sur le profil de référence, le vol drag-on à l'Isp neuve rend
   la même capacité que drag-off au proxy, à la tolérance de calibration. C'est la preuve de `B`.
2. **Ariane — capacité physique, ratio drag-aware.** Le vol drag-on à l'Isp physique + traînée
   explicite **aboutit** (mesuré : il vole, avec une marge réduite — résidu Vinci 78 % → 67 %),
   et sa capacité est la physique. On **ne** vérifie **pas** un ratio 2,10 préservé : le ratio se
   décale, et c'est le résultat attendu, à consigner.
3. **Re-baseline.** Les 4 gates re-enregistrés drag-off aux Isp neuves, `cleanTest` + gates
   isolés (`BUG-7`), décalage **attribuable à l'Isp seule**.

---

## 6. Ordonnancement — l'implémentation se replie sur `L3`

`L2` **avant `L3`** en raisonnement : recalibrer les poids d'ascension (`L3`) sur une Isp encore
non re-posée obligerait à tout re-régler. Mais **la mesure a montré que l'implémentation de
l'Isp doit se faire à `L3`**, pas avant :

- le B-check (§5.1) a révélé que le FH **ne vole pas drag-on** tant que `L3` n'a pas redressé le
  hand-off bas du gravity turn — donc la valeur d'Isp **n'est pas vérifiable drag-on avant `L3`** ;
- et `L3` **re-enregistre de toute façon les baselines FH** en changeant l'ascension, donc
  re-baseliner à un `298` provisoire que `L3` écraserait serait de l'effort jeté.

`L2` **livre donc la conception et la mesure** (traînée 51/230, `A`/`B` par lanceur, la valeur
FH ≈ 298). Le catalogue **reste à 296** ; `L3` pose l'Isp (298, affiné) et re-baseline **en une
passe**, drag-on vérifiable et hand-off redressé. Les deux lots restent **attribuables en
raisonnement** (Isp vs autorité) ; seule leur implémentation fusionne. Dépend de `L1`.

---

## 7. Ce que la mesure a corrigé au cadrage initial

Deux choses posées avant la mesure sont tombées, et sont consignées pour que le raccourci ne se
rejoue pas :

| Posé initialement | Ce que le banc a mesuré |
|---|---|
| `396/64` = perte de traînée implicite (`DT-13`, `J2`) | déficit d'Isp, **pas** traînée ; la vraie traînée est **51 / 230**, inversée (`§4.3` l'avertissait) |
| `B` uniforme (préserver la capacité des deux lanceurs) | `B` **infaisable pour l'Ariane** (traînée 230 sur boosters solides sans marge d'Isp) → **`A`/`B` par lanceur** |

---

*Document rédigé le 2026-09-11 (séance de conception), réécrit le même jour après la mesure du
banc `IspCalibrationBenchTest`.*
