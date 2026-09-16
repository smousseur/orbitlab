# PHY-5 / L0 — Baseline mesurée

Lot L0 du découpage [`01-decoupage.md`](01-decoupage.md) §5. L0 **mesure et ne
change rien** : aucun fichier de `src/main` n'est touché. Il pose la référence sur
laquelle les lots suivants sont jugés, et il dimensionne le seul risque du chantier
— le coût compute de K débris.

Deux natures de mesure, séparées volontairement :

- **Statique** — relevée depuis le code / le catalogue, **faite dans ce document**
  (§2 à §4).
- **Runtime** — exige un vol ou une propagation, **spécifiée ici et exécutée par
  l'auteur** (§5), les cellules `⟨à mesurer⟩` restant à remplir. Aucune de ces
  mesures n'est une optimisation CMA-ES : un replay (§5.1) et une propagation
  ballistique isolée (§5.2), toutes deux courtes.

---

## 1. L'invariant : les quatre gates, référence intacte

PHY-5 ne touche ni la passe d'optimisation ni le résultat volé : les débris sont
produits au seul point de replay, hors boucle CMA-ES, et le maigrissement du
primaire ne change que le maillage dessiné (découpage §4). Les **quatre gates à
tolérance zéro** de la tâche `gateTest` (`build.gradle` l. 84-87, `forkEvery = 1`)
sont donc la référence que tout le chantier doit laisser verte :

| Gate | Ce qu'il épingle |
|---|---|
| `EarthOrbitNonRegressionTest` | Deux compositions de la même mission dans le même run — survit à n'importe quel catalogue |
| `AscentBaselineN2Test` | Ascension de référence, tolérances mesurées, mode capture (`build/baseline/`) |
| `MissionPolylineBaselineTest` | Polyligne de mission, générateur imprimant ses constantes |
| `CentralBodyBaselineTest` | `Boundary` à égalité stricte de `double` (exclu de `test`, inclus dans `gateTest`) |

**L0 ne les re-enregistre pas** : il les déclare référence. `L1` relance
`gateTest` et vérifie qu'ils restent verts, une fois la machinerie montée — c'est
là que l'invariant se prouve, pas ici. *(Piège de mesure, OPT-1 : `gateTest`
tourne sous jacoco, aucune durée de test n'est une référence de temps.)*

---

## 2. Inventaire statique des largages *(mesuré depuis le code)*

La forme de l'ascension est décidée en un seul endroit,
[`AscentSequence.chain`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/ascent/AscentSequence.java)
(l. 138-172) : **3 phases** pour un lanceur série, **5** pour un bloc parallèle
non groupé —

```
série     : Gravity turn (S1) → S1 separation → Gravity turn (S2)
parallèle : Gravity turn (S1) → Booster separation → Gravity turn (core)
            → S1 separation → Gravity turn (S2)
```

`grouped = coreLeft ≤ ε` ([`StagingPlan`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/StagingPlan.java)
l. 218) : vrai seulement si le cœur se vide en même temps que les boosters. Ni le
Falcon Heavy (cœur étranglé, `PHY-8`) ni l'Ariane 64 (P120C ~130 s, Vulcain
~8 min) ne le sont — **les deux ont deux largages à l'ascension**. Le profil GEO
ajoute une **`UPPER_SEPARATION`** (S2) avant l'AKM
([`GEOMission`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/GEOMission.java)
l. 229) ; le profil LEO n'en a **aucune** — la charge utile n'y est jamais larguée.

Multiplicité au catalogue ([`Launchers`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/catalog/Launchers.java)) :
Falcon Heavy = **2** propulseurs latéraux (trois corps identiques), Ariane 64 =
**4** P120C. Sous la décision « M objets propagés » (découpage §1), un largage de
bloc produit M débris.

**Le tableau des débris**, avec le fan-out M :

| Profil | Largages (`StageSeparationStage`) | Débris (K) | Silhouette du primaire |
|---|---|:-:|---|
| Falcon Heavy LEO-400 | Booster (M=2), S1/core (1) | **3** | `full → after_boosters → after_s1` *(reste S2+charge)* |
| Ariane 64 LEO | Booster (M=4), core (1) | **5** | `full → after_boosters → after_s1` |
| Falcon Heavy GEO | Booster (2), core (1), UPPER/S2 (1) | **4** | `full → after_boosters → after_s1 → payload` |
| Ariane 64 GEO | Booster (4), core (1), UPPER/S2 (1) | **6** | `full → after_boosters → after_s1 → payload` |

**Deux conséquences pour la suite.**

- **Le multiplicateur compute est K, et son maximum est 6** (Ariane 64 GEO). Le
  coût que `L1` doit borner est K × (une rentrée de débris sous traînée), mesuré en
  §5.2.
- **`L4` (charge utile) ne s'applique qu'à GEO et lunaire.** En LEO, aucune
  `UPPER_SEPARATION` n'existe : le primaire reste `after_s1` (S2 + charge) jusqu'au
  bout, la charge utile n'est jamais révélée comme objet distinct. Le « Falcon Heavy
  en GEO » est un problème **GEO/lunaire**, pas LEO. Révéler la charge utile en LEO
  demanderait un **nouvel événement de séparation** — donc une masse larguée en plus,
  donc un changement de physique et une re-baseline : hors invariant. `L4` tranchera
  s'il l'accepte (S2+charge en LEO) ou non ; **L0 le mesure, ne le décide pas.**

---

## 3. Le rendu mono-objet actuel *(référence)*

Ce que « N objets » remplace, déjà relevé au découpage §2.1 : une `LodView` /
`SpacecraftPresenter` + un `MissionTrajectoryRenderer` (un ruban) par mission,
piloté par un point unique (`eph.displayPointAt`) et un renderer unique par
`MissionId`. C'est la colonne « un objet par mission » que L1 fait passer à N.

---

## 4. La donnée d'un débris, disponible sans nouveau catalogue *(référence)*

Confirmé au découpage §2.3 : à `StageSeparationStage.enter`, la masse larguée et la
section propre de la pièce (`ActiveStageInfo.aerodynamics()`) sont en main ; les
maillages par pièce (`booster<i>`, `core`, `S2`, `after_*`) et par charge utile
(`goes`/`landsat8`/`lro`) existent. **L0 ne mesure rien de neuf ici** — il acte que
le montant d'une propagation ballistique de débris est réuni.

---

## 5. Les mesures

### 5.1 Coût du replay actuel, par profil *(mesuré le 2026-09-15)*

Mesuré par le bench
[`Phy5ReplayCostBench`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/bench/Phy5ReplayCostBench.java)
(`-Dorbitlab.slowTests=true`, un `@Test` par profil), qui chronomètre le replay
**isolé** (`MissionEphemerisGenerator.generate`) après l'optimize — best of 3, sous
jacoco (comme §5.2, donc les ratios §5.1/§5.2 sont cohérents). GEO par défaut =
Falcon Heavy + GEO_SAT.

| Profil | Replay (best of 3) | Points |
|---|---|---|
| Falcon Heavy LEO-400 (LEGACY) | 345 ms | 10 008 |
| Ariane 64 LEO-400 | 219 ms | 10 299 |
| Falcon Heavy GEO | 2 395 ms | 120 348 |
| Ariane 64 GEO | *non joué* (config GEO+Ariane à assembler) | — |

Le GEO coûte **~7×** le LEO et **12×** les points : sa fenêtre de restitution
échantillonne un long transfert. À noter pour `DT-17` (N rubans) que L1 mesurera.

### 5.2 L'expérience de rentrée d'un débris — la validation de D5 *(faite — FEU VERT)*

**Mesuré le 2026-09-15** par le bench
[`Phy5ReentryStepSweepTest`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/bench/Phy5ReentryStepSweepTest.java)
(`-Dorbitlab.probe=true`, gate `orbitlab.probe`, ne tourne pas dans la suite).
Propagation **traînée seule** isolée (8×8 + perturbeurs + `DragForce`, NRLMSISE,
`maxStep = COAST_MAX_STEP`), détecteur d'arrêt **altitude géodésique 0 km**,
balayage de tolérance en intra-JVM (`orbitlab.opt.*Tol`). États **représentatifs**
(pas extraits d'un optimize), aéro réelle du booster Falcon Heavy au catalogue
(**10,5 m² / Cd 0,4 / 22 t**, soit BC ≈ 5 200 kg/m² — et non le P120C de mon
protocole d'origine), plus un cas faible-BC pour borner le pire.

| Régime (BC kg/m²) | Fin | pas `1e-8` → `1e-1` | mur (ms) |
|---|---|---|---|
| Booster suborbital 70 km / 2,3 km/s (5 238) | sol à 161 s | 34 → 9 | 1 116 → 27 |
| Core suborbital 150 km / 5,5 km/s (5 238) | sol à 356 s | 48 → 11 | 245 → 28 |
| Dense décroissant 130 km circ. (5 238), horizon 4 h | 115 km (pas rentré) | 104 → 53 | 260 → 74 |
| Faible-BC 130 km circ. (27), extrême | sol à 2 682 s (45 min) | 328 → 73 | 552 → 129 |
| Dense jusqu'au sol, horizon 2 j (5 238) | **sol à 30 256 s (8,4 h)** | 246 → 109 | 338 → 86 |

**Verdict : FEU VERT pour `L1`.** Tout régime de débris de lanceur coûte
**≤ ~330 pas d'intégration** et rentre en **≤ 8,4 h**. Chiffré en temps contre le
replay mesuré (§5.1), au tolérance d'affichage `1e-1`, ~28 ms par débris suborbital
(~86 ms pour un S2 orbital) : FH LEO **+24 %**, Ariane 64 LEO **+64 %** (replay bon
marché × K=5), FH GEO **+7 %**. En fraction du replay ce n'est **pas** négligeable
— la première rédaction de ce paragraphe l'a écrit à tort en confondant *pas* et
*temps* — mais en termes utilisateur c'est imperceptible : +0,08 à 0,17 s ajoutés à
un replay unique de 0,2–2,4 s, précédé d'un optimize de ~18 s+. Le chantier monte
la machinerie.

**Trois corrections mesurées à D5** (le découpage sur-estimait le risque) :

- **L'explosion de `PHY-1` (452 → 982 497 pas) ne se reproduit pour aucun débris de
  lanceur** — ni suborbital, ni même en orbite décroissante propagée *jusqu'au sol*
  (~250 pas, 8,4 h). Un débris de lanceur est trop dense (BC élevé) pour flotter
  dans l'air dense ; il plonge ou décroît net. L'explosion suppose un objet à très
  faible BC propagé sur une décroissance de plusieurs jours — pas ce régime.
- **D5.2 (plancher géodésique 0 km) validé** : l'intégrateur atteint le sol à
  *toutes* les tolérances, aucun `THREW`. La mort de `BUG-10` (−9 / −30 km) ne
  survient pas — le plancher `STOP` coupe avant le sous-sol.
- **D5.1 (tolérance grossière) était sur-vendu** : effet ≤ 4,5× et tous les comptes
  minuscules. C'est une assurance bon marché, **pas** le garde-fou structurant. Le
  garde-fou reste **D5.3 (horizon temporel)**, mais pour une autre raison que
  l'explosion : borner un débris en orbite **haute** (S2 en GTO, débris à 400 km)
  qui ne rentrerait pas avant des semaines. À re-cadrer dans le découpage (§3.5).

### 5.3 Confirmer les défauts D4 / D5

- **D4 — impulsion ~1 m/s** : régler la valeur sur l'écartement visuel des M
  boosters une fois `L2` capable de les dessiner ; L0 n'en fixe que l'ordre de
  grandeur.
- **D5 — plancher 0 km géodésique** : **confirmé** en §5.2 — l'intégrateur atteint
  le plancher à toutes les tolérances, aucune mort d'intégrateur (`BUG-10` ne se
  produit pas). L'emphase de D5 change en revanche : le garde-fou structurant est
  l'horizon (D5.3), pas la tolérance (D5.1) — voir §5.2.

---

## 6. Ce que L0 clôt et ouvre

**Clôt.**
- L'invariant : les quatre gates sont la référence, PHY-5 n'en touche aucun (§1).
- L'inventaire des largages : K par profil, M par bloc, la chronologie de silhouette
  du primaire (§2) — mesuré, définitif.
- La donnée d'un débris est réunie sans nouveau catalogue (§4).

**Ouvre.**
- `L1` : la machinerie, avec K connu et le coût d'une rentrée **mesuré et borné**
  (§5.2, ≤ ~330 pas par débris) — feu vert donné.
- `L4` : le drapeau « pas de révélation de charge utile en LEO » (§2), à trancher
  quand le lot arrive.

**Corrige.** Le découpage §3.5 (D5) sur-estimait le risque compute et faisait de la
tolérance grossière (D5.1) le garde-fou central contre une explosion qui **ne se
reproduit pas** pour un débris de lanceur (§5.2). À re-cadrer : D5.2 (plancher)
validé, D5.1 rétrogradé en assurance, D5.3 (horizon) conservé pour borner un débris
en orbite haute — pas pour dompter une explosion.

**Mesuré aussi (§5.1).** Le replay mono-objet coûte 345 ms (FH LEO), 219 ms
(Ariane LEO), 2 395 ms (FH GEO). Le coût des débris (§5.2) en fraction : +24 % à
+64 % en LEO, +7 % en GEO — imperceptible en temps utilisateur, mais pas
« négligeable » comme §5.2 le disait d'abord. L'Ariane 64 GEO n'a pas été jouée.

**Reste suspendu à un run** (§5) : le coût du replay actuel, l'effondrement de pas
sous tolérance grossière, et le dimensionnement K × rentrée — les trois cellules qui
décident du feu vert de `L1`.
