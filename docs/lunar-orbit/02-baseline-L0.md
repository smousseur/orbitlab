# MIS-5 / L0 — Baseline mesurée

Lot **L0** du découpage ([`01-decoupage.md`](01-decoupage.md) §4). Ce document ne contient aucune
décision et aucune ligne de production : il **consigne des chiffres**, mesurés avant que quoi que ce
soit ne bouge, pour que les lots suivants soient conçus sur des mesures et non sur des
extrapolations.

Le découpage demandait six mesures. Il y en a six, dans l'ordre où il les numérote — l'ordre
d'exécution, lui, est au §1.

**Sept énoncés écrits ailleurs sont démentis par ces chiffres.** Ils sont rassemblés au §8. Deux
prédictions du découpage sont au contraire confirmées au chiffre près, ce qui mérite d'être dit
aussi.

---

## 1. Conditions de mesure

| | |
|---|---|
| Date | 2026-08-28, 18 h 46 – 18 h 47 |
| Commit | `25c52bb` « MIS5: roadmap » — arbre propre, plus la sonde non suivie |
| JDK | GraalVM 21.0.5, assertions actives (`-ea`) |
| Lancement | IntelliJ, une JVM, ordre d'exécution **4 → 1/2/3 → 5** ; la mesure 6 ne s'exécute pas |
| Sonde | `LunarOrbitBaselineProbeTest`, `src/test/.../mission/operation/`, opt-in `orbitlab.probe` |
| Époque du balayage | `2026-03-31T00:00:00Z` + 0, 6, 12, 18, 24 j — les cinq de `MIS-4 / L0` |
| Époque du vol GEO | `2026-01-01T12:00:00Z`, graine **42** explicite |
| Durée totale | **48,9 s** de paroi |

**La sonde est jetable et doit être supprimée à la fermeture du lot.** Elle n'assère rien, elle
imprime. Le §4 du découpage l'exige : rien n'est commité dans `src/main`, et cette contrainte a été
tenue.

**Le plan de parking est celui que `MIS-5` volera, pas celui de la démo.** `i = 28,562°` (Canaveral,
tir due east) et **400 km**, contre les 30° / 185 km sur lesquels `TranslunarInjectionPlan` est
calibré. C'est délibéré : la mesure 1 existe précisément pour savoir quelle inclinaison la latitude
du site délivre, et la mesurer sur une inclinaison arbitraire n'aurait rien mesuré. Le prix est que
les tableaux des §2 à §4 **ne se comparent pas ligne à ligne** à ceux de `MIS-4 / L0`, qui volait
l'autre configuration.

Une seule chose a été dupliquée depuis la production : le corps de
`TranslunarInjectionPlan.parkingState` et de `transferPlaneNormal`, dont l'inclinaison et l'altitude
sont des constantes. C'est la même duplication que `MIS-4 / L0` s'était autorisée, et pour le même
motif : élargir une signature de production pour une sonde qui disparaît est le mauvais sens.

**Le désalignement de plan est nul.** Le plan de parking contient exactement la direction d'arrivée
(`plane misalignment = 0,00°` sur les quatre vols). Les mesures 1 à 3 sont donc la géométrie
idéale ; un tir réel passe par la fenêtre de lancement, qui approche ce plan sans l'atteindre —
0,664° sur le vol de `MIS-4 / L4`.

---

## 2. Mesure 1 — l'inclinaison lunaire délivrée

Cinq époques d'une lunaison. Pour chacune : la déclinaison lunaire à l'arrivée, puis l'inclinaison
de l'hyperbole au périlune, lue **deux fois** — dans le repère sélénocentrique à axes ICRF, qui est
celui de l'état et donc celui qu'`OrbitElements` rapporterait, et au-dessus de l'équateur lunaire,
qui est celle qu'un sélénographe utilise.

| jour | δ_Lune à l'arrivée | **i (ICRF sélénocentrique)** | i / équateur lunaire | RAAN |
|---|---|---|---|---|
| 0 | −17,527° | **150,046°** | 171,393° | 174,068° |
| 6 | −26,226° | **131,127°** | 152,254° | −165,676° |
| 12 | +4,373° | *refusé — voir §4* | — | — |
| 18 | +27,934° | **153,437°** | 174,899° | 175,788° |
| 24 | +0,604° | **151,232°** | 172,068° | 171,855° |

**Verdict : « subie » est une propriété acceptable, pas une loterie.** La dispersion mesure
**22,3°** en ICRF (131,1° à 153,4°) et 22,6° au-dessus de l'équateur lunaire. Ce n'est pas le « 0 à
90° selon l'époque » que le §5 du découpage redoutait comme risque principal. `L5` la journalise et
c'est fini.

**Les quatre orbites sont rétrogrades**, et proches de l'équateur lunaire : 152° à 175° au-dessus de
lui, soit 5° à 28° d'inclinaison si on compte dans l'autre sens. Une orbite polaire lunaire — ce que
volent LRO, Chandrayaan et Danuri — n'est **pas** atteignable par cette chaîne. Ce n'est pas une
limitation nouvelle, c'est la conséquence chiffrée du §2.2 pt 2.

**Trois des quatre valent 180° − φ du site à 2° près.** `180° − 28,562° = 151,438°` ; le jour 0
tombe 1,39° en dessous, le jour 24 à 0,21°, le jour 18 à 2,00° au-dessus. **Le jour 6 s'en écarte de
20,3°**, et c'est celui dont la déclinaison est la plus négative. La règle existe donc, mais elle
rate une époque sur quatre par un montant qui n'est pas décoratif.

**Conséquence sur la question ouverte du §6 pt 10 — ce que l'objectif porte comme inclinaison.**
Les deux issues étaient « la valeur que la géométrie délivre, calculée à la composition » et « un
marqueur d'absence que l'affichage sait lire », et elles ne s'arbitraient pas avant de savoir si la
première était calculable avant le vol. Elle l'est **approximativement** : `180° − φ`, juste à 2°
trois fois sur quatre et fausse de 20° la quatrième. Un nombre d'aspect crédible et occasionnellement
faux de 20° est pire qu'aucun nombre. Sur quatre points — échantillon mince, et c'est dit — cela
penche vers le marqueur d'absence. **La décision reste à `L5`.**

Le coût d'une erreur y est faible, ce qui est vérifié plutôt que supposé :
`MissionLoadEvaluator.objectiveMet`
([:385-391](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionLoadEvaluator.java))
ne compare que périgée et apogée. Une inclinaison fausse ne déplace aucune faisabilité ; elle ne se
voit qu'aux trois sites d'affichage.

**Deux nombres pour la même orbite, écartés de 21,2°.** L'écart entre la lecture ICRF et la lecture
équatoriale lunaire vaut +21,35°, +21,13°, +21,46° et +20,84° sur les quatre vols — une
quasi-constante, qui est l'obliquité relative des deux équateurs. `OrbitElements` rapporte l'ICRF.
`L5` et `L7` doivent choisir laquelle ils montrent **et le dire**, faute de quoi l'écran affichera
150° pour une orbite qu'un document de mission appellerait 171°.

---

## 3. Mesure 2 — `v∞` et le Δv d'insertion réels

Mesurés sur l'hyperbole d'arrivée volée, au périlune, masse 1 700 kg à l'injection.

| jour | `v∞` à la sphère | `v∞` au périlune | `v_hyp` | `v_circ` | **Δv d'insertion** | `e` | périlune volé | période |
|---|---|---|---|---|---|---|---|---|
| 0 | 830,8 | 840,6 | 2 458,3 | 1 633,5 | **824,8 m/s** | 1,2648 | 100,10 km | 7 068 s |
| 6 | 866,2 | 872,5 | 2 469,4 | 1 633,5 | **835,9 m/s** | 1,2853 | 99,96 km | 7 067 s |
| 18 | 844,8 | 861,6 | 2 466,0 | 1 633,8 | **832,2 m/s** | 1,2781 | 99,27 km | 7 063 s |
| 24 | 812,7 | 825,8 | 2 452,7 | 1 633,1 | **819,6 m/s** | 1,2557 | 100,96 km | 7 073 s |

**Le Δv d'insertion vaut 819,6 à 835,9 m/s**, soit 828,1 en moyenne et **16,3 m/s d'étalement sur la
lunaison** (2,0 %). Le §2.4 du découpage annonçait ≈ 930 m/s : il est **haut de 11,0 %**.

Le tableau §2.4 ligne à ligne, contre la mesure :

| grandeur | §2.4 (calculé) | mesuré | écart |
|---|---|---|---|
| `v∞` | ≈ 1 100 m/s | 825,8 – 872,5 | **−21 à −25 %** |
| `v_hyp` au périlune | 2 566 m/s | 2 452,7 – 2 469,4 | −3,8 à −4,4 % |
| `v_circ` à 100 km | 1 634 m/s | 1 633,1 – 1 633,8 | exact |
| **Δv d'insertion** | **≈ 930 m/s** | **819,6 – 835,9** | **−10,1 à −11,9 %** |
| Période lunaire à 100 km | 7 067 s | 7 063 – 7 073 | exact |

Les deux lignes « exactes » le sont parce qu'elles ne dépendent que de µ et du rayon lunaire. Les
trois autres dépendaient de la recalibration sur Apollo, et c'est elle qui était haute.

**Le §2.4 recalculé sur la mesure**, pour un orbiteur de 2 t sec à Isp 320 — **c'est un calcul, pas
une mesure**, exactement comme la table qu'il remplace, et c'est `L3` qui le tranchera :

| | §2.4 | recalculé sur 828,1 m/s | sur le pire cas, 835,9 m/s |
|---|---|---|---|
| Ergols | 690 kg | **604 kg** | 610 kg |
| Masse au feu | 2 690 kg | **2 604 kg** | 2 610 kg |
| Durée avec l'AKM du catalogue (400 N) | 5 450 s — 77 % d'une révolution | **4 738 s — 67 %** | 4 788 s — 68 % |
| Poussée pour tenir sous 5 % d'une révolution | ≈ 6,2 kN (2,3 m/s²) | **≈ 5,4 kN (2,06 m/s²)** | 5,4 kN |

**La conclusion du §6 pt 4 ne bouge pas, seuls ses nombres bougent.** 67 % d'une révolution reste
inutilisable comme combustion « quasi impulsionnelle » : l'orbiteur a besoin d'un moteur de catalogue
plus gros, écrit comme une donnée. L'accélération requise tombe à **2,06 m/s²**, ce qui est à 1,5 %
près celle d'Apollo (2,03) et de Chang'e-3 (1,98) — la cible que le §2.4 revendiquait sans
l'atteindre.

**Ce que la mesure ne couvre pas** : elle est **impulsionnelle**. `MIS-4 / L6` a mesuré que la perte
de poussée finie vaut 1,7 à 5 fois l'estimation en sinc ; sur une combustion qui dure 67 % d'une
révolution, ce terme n'est pas petit et il n'est pas ici. `L3` doit dimensionner avec une marge, et
`L4` la mesurera.

---

## 4. Mesure 3 — l'approche sélénocentrique

De la traversée de la sphère d'influence au périlune : ce que `LunarApproachCoastStage` volera.

| jour | entrée de sphère | rayon de sphère | **approche** | points à 60 s | points à 1 s |
|---|---|---|---|---|---|
| 0 | 3,071 j | 68 321 km | **18,27 h** | 1 096 | 65 766 |
| 6 | 3,090 j | 69 415 km | **18,10 h** | 1 086 | 65 170 |
| 18 | 3,148 j | 62 693 km | **16,32 h** | 979 | 58 751 |
| 24 | 3,115 j | 66 748 km | **18,03 h** | 1 082 | 64 892 |

**L'approche mesure 16,32 à 18,27 h.** Le découpage §2.3 pt 5 l'estimait à « 16–18 h » comme moitié
entrante du séjour de 32 à 36 h : **l'estimation est juste**, à un quart d'heure près sur le haut de
la plage.

**Les ~64 800 points annoncés à 1 s sont mesurés à 58 751 – 65 766**, moyenne 63 645 : **juste à 2 %
près**. Ce sont les deux seules prédictions arithmétiques du découpage que la mesure confirme, et
elles fondent la coupure du §2.3 pt 5 — l'approche seule, à la seconde, coûterait **4,1 à 4,5 fois
le vol `MIS-4` entier** (14 467 points).

**À 60 s l'approche coûte ~1 100 points**, ce qui est négligeable : moins que le coast terminal d'une
mission GEO d'un jour (1 438). La coupure du §2.3 pt 5 n'a donc pas de contrepartie à chiffrer.

**Le rayon de sphère respire de 62 693 à 69 415 km** sur la lunaison, 10,7 % d'amplitude. La date
d'entrée, elle, ne bouge que de 1,8 h (3,071 à 3,148 j).

**L'entrée à 3,071 j sort du plancher de 3,08 j de `MIS-4 / L0`.** Ce n'est pas une contradiction :
cette sonde part d'un parking à 400 km / 28,562°, l'autre de 185 km / 30°. C'est la démonstration que
la plage 3,08–3,16 j est celle d'**une** configuration de parking, pas une propriété du transfert.

**Un refus sur cinq époques, au jour 12.** La visée plafonne à **1 173 km** de périlune contre les
100 km demandés, et refuse — comportement voulu. Deux choses le rendent intéressant pour `L5` et
`L7` :

- **Le jour 12 est celui dont la déclinaison est la plus petite** (+4,373° contre une inclinaison de
  parking de 28,562°). C'est l'époque la plus confortable au regard du critère de fenêtre de
  `MIS-4 / L0` §5, et c'est celle qui ne vole pas. **Le critère de fenêtre et la convergence de la
  visée sont indépendants** : une date peut passer le premier et rester involable. Sur cet
  échantillon la corrélation est même inversée.
- Le refus coûte **6,1 s**, dont 3,1 s de bissections sur un intervalle de largeur nulle (§9).

---

## 5. Mesure 4 — le coût actuel de l'échantillonnage à 1 s

Un vol GEO complet, du sol à l'orbite, graine 42, horizon de restitution 1 j.
**120 317 points, 8 034 sommets de polyligne, `complete=true`, 15,5 s de paroi.**

| étage | points | durée | dont en poussée | à 60 s |
|---|---|---|---|---|
| Vertical Ascent | 9 | 7 s | 100 % | 1 |
| Gravity turn (S1) | 151 | 150 s | 100 % | 3 |
| S1 separation | 2 | 2 s | *coast* | 1 |
| Gravity turn (S2) | 179 | 177 s | 100 % | 4 |
| Parking | 2 670 | 2 668 s | **0,29 %** (7,7 s) | 45 |
| Coasting parking | 20 | 1 124 s | *coast* | 20 |
| GTO injection | 2 852 | 2 850 s | **3,8 %** (110 s) | 49 |
| S2 separation | 2 | 2 s | *coast* | 1 |
| Circularization | 24 405 | 24 403 s | 44,5 % (10 869 s) | 408 |
| **Trim** | **67 582** | **67 580 s** | **1,6 %** (1 106 s) | 1 127 |
| Plane trim | 21 007 | 21 005 s | **0,12 %** (26 s) | 351 |
| Coasting | 1 438 | 86 164 s | *coast* | 1 437 |
| **total** | **120 317** | **206 132 s** | **6,0 %** | **3 447** |

**Réponse à la question posée : oui, le profil GEO a un problème, et ce chantier n'a pas à le
régler.** Trois chiffres suffisent à le dire.

**1. 6 % du vol pousse, 98,8 % des points sont facturés au tarif de la poussée.** 12 451 s de
combustion sur 206 132 s de vol, et 118 855 points sur 120 317 produits par des étages marqués
propulsifs. La cause n'est pas un étage : c'est que
[`MissionStage.isPropulsive()`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/MissionStage.java)
rend **`true` par défaut** et que seuls `CoastingStage` et `StageSeparationStage` le contredisent.
« Propulsif » y signifie donc « ni coast ni largage », pas « en train de brûler ».

**2. Le pire étage n'est pas celui que le découpage désigne.** Le §2.3 pt 5 vise la circularisation
et ses « ~5 h de coast vers son apogée » : mesurée, elle en possède 6,78 et produit 24 405 points.
Mais **`Trim` en possède 18,77 h et produit 67 582 points** — 2,8 fois plus — pour 1 106 s de
combustion, soit 1,6 % de sa durée. `Plane trim` en ajoute 21 007 pour 26 s de combustion, 0,12 %.
À eux trois, ces étages font **112 994 points, 93,9 % du vol**.

**3. 93 % des points sont produits, tenus en mémoire, puis jetés.** `TrajectoryPolyline.MAX_POINTS`
vaut 8 192 et la trace en retient 8 034 : la décimation est de **15:1**. Le vol `MIS-4` mesuré par
son `L0` — 14 467 points pour 7 236 sommets — était à 2:1. Le profil GEO est donc au plafond de la
polyligne depuis longtemps, et tout point supplémentaire est du travail pur.

**Ce que coûterait une règle correcte.** À 1 s pendant les combustions et à 60 s partout ailleurs :
~15 700 points, facteur **7,7**. Tout à 60 s : 3 447 points, facteur 34,9. Le premier chiffre est la
forme que le §2.3 pt 5 impose à `MIS-5` — un coast d'approche à 60 s, puis une combustion centrée à
1 s — et il est atteignable sans toucher au socle, par la découpe en deux étages.

**Le temps de paroi**, pour le quatrième risque du découpage §5 : 15,5 s pour un vol GEO complet,
dont **9,2 s de CMA-ES** sur le virage gravitationnel et 2,9 s de passe d'éphéméride. C'est ce que
`L5` paiera **en plus** des deux `solve()` du TLI (4,5 s chacun, `MIS-4 / L0` §6) et des deux passes
de coast translunaire.

---

## 6. Mesure 5 — la tenue de l'orbite lunaire

Douze révolutions d'une orbite circulaire à 100 km, fabriquée plutôt que capturée — pour isoler la
tenue du modèle de la dispersion d'arrivée. Masse ponctuelle lunaire perturbée par la Terre et le
Soleil (`GravitationalContext.moon().withPerturbers(EARTH, SUN)`). Quatre inclinaisons encadrant ce
que la mesure 1 délivre. **157 ms de paroi pour les quatre.**

| `i` | altitude volée | **respiration** | périgée osc. | apogée osc. | `e` finale (max) | dérive de `i` |
|---|---|---|---|---|---|---|
| 0° | 99,94 – 100,02 km | **0,08 km** | 99,89 | 100,07 | 4,05e−06 (3,49e−05) | +0,0022° |
| 30° | 99,94 – 100,02 km | **0,08 km** | 99,89 | 100,07 | 4,97e−06 (3,53e−05) | +0,0020° |
| 60° | 99,94 – 100,02 km | **0,08 km** | 99,89 | 100,06 | 3,85e−06 (3,44e−05) | +0,0057° |
| 90° | 99,94 – 100,01 km | **0,07 km** | 99,90 | 100,06 | 1,07e−06 (3,42e−05) | +0,0078° |

Douze révolutions valent **23,6 h**, période **7 067 s**, 1 414 points à 60 s.

**Verdict : le deuxième risque du §5 du découpage n'en est pas un.** Le seuil qu'il posait était
« si le périlune respire de 20 km en un jour » ; il respire de **0,08 km**, soit **250 fois moins**.
La bande de mérite de `L5` n'est contrainte par rien de ce côté : elle peut être aussi serrée que la
précision d'insertion le permet. Et `Revolutions(12)` montrera une orbite plate, pas une orbite qui
se dégrade.

**Il n'y a aucune dérive séculaire.** Le relevé tour par tour donne le même 99,94 – 100,02 km aux
douze révolutions, sans marche. L'excentricité oscille — maximum 3,4e−05 en cours de route, moins de
5e−06 à la fin — plutôt qu'elle ne croît. Un `a·e` de 3,4e−05 vaut 63 m sur un rayon de 1 837 km,
soit exactement la respiration observée : **la respiration mesurée *est* l'excentricité induite par
le troisième corps**, et rien d'autre.

**La dérive d'inclinaison croît avec l'inclinaison** — 0,0022° à l'équateur, 0,0078° au polaire — ce
qui est la signature du couple terrestre. Sur un jour, elle est sans conséquence.

**Ce que ce chiffre ne dit pas, et c'est l'essentiel.** Le contexte lunaire est **une masse
ponctuelle**, son propre javadoc le dit (« *point-mass gravity, [...] the shape is a sphere* ») :
aucun champ de gravité lunaire, donc ni `J2` ni mascons. Une orbite lunaire basse réelle est
instable pour cette raison précise. Ces 0,08 km décrivent la tenue **de la simulation**, pas celle
d'un orbiteur. Le §6 pt 8 l'assume déjà ; ce relevé le chiffre.

---

## 7. Mesure 6 — les sites Terre-en-dur sur le chemin de `MIS-5`

Lecture du graphe d'appels, sans exécution. La chaîne à ranger : ascension → insertion en parking →
coast de parking → TLI → coast translunaire → **bascule de sphère** → approche sélénocentrique →
LOI → coast lunaire terminal.

Trois catégories, comme en `MIS-4 / L0` : **hors chemin**, **sur le chemin et légitimement
terrestre**, et **sur le chemin et appliqué à un arc lunaire** — seule catégorie qui déplacerait du
travail.

| Site | Sur le chemin ? | Arc | Verdict |
|---|---|---|---|
| `EarthMission:65` — µ de l'état initial | oui | terrestre | **Terre légitime** |
| `LaunchPlane:155`, `launchAzimuth:171` | oui | terrestre | **Terre légitime** — trigonométrie sur une sphère terrestre, aucune constante WGS84 |
| `GravityTurnConstraints:70-73` | oui | terrestre | **Terre légitime** |
| `GravityTurnProblem:252, 263-266` | oui | terrestre | **Terre légitime** |
| `TransferProblem:45` | **non** | — | hors chemin : `MIS-5` n'a pas d'étage de transfert-2 |
| `Physics:206, 249-250, 281` | **non** | — | hors chemin |
| `AchievedOrbit:59` — µ | oui | lunaire | **déjà réparé** par `PHY-4 / L6` : lit le µ de l'état |
| `StageEndStateDiagnostic:23-24` | oui | terrestre par sa garde | **Terre légitime**, mais empoisonné en aval (§6 pt 11) |
| `PropellantBudget:47-48` | oui | **lunaire** | **arc lunaire** — déjà décidé, §4 / `L3` |
| `OrbitElements:44 → :129` — `RE` | oui | **lunaire** | **arc lunaire** → `L2` |
| `MissionHorizon:155` — µ | oui | **lunaire** | **arc lunaire** → `L2` |
| `DynamicParameters:158` — µ | oui | **lunaire** | **arc lunaire** → `L2` |

**Réponse à la question du découpage : aucun travail ne se déplace de `L5` vers `L2` ou `L3`.** Les
quatre sites appliqués à un arc lunaire sont déjà dans le périmètre de `L2` et de `L3`, tels que le
§4 les écrit. La baseline confirme le découpage au lieu de le corriger.

**Les deux verdicts conditionnels de `MIS-4 / L0` cassent, et la sonde chiffre les deux.** Ce ne sont
plus des extrapolations : les quatre vols du §2 impriment ce que ces sites rendraient sur l'état
d'arrivée réel.

- **`OrbitElements`** soustrait le rayon **terrestre** aux apsides
  ([:44](../../src/main/java/com/smousseur/orbitlab/simulation/OrbitElements.java), appliqué en
  `elementsOf:129`). Sur les quatre états : périlune rapporté à **−4 541 km** au lieu de 100,10 /
  99,96 / 99,27 / 100,96 km. Le décalage est `RE − RM = 4 640,737 km`, constant au mètre près. Le
  `MIS-4 / L4` §11 annonçait 4 641 km : **juste**.
- **`MissionHorizon.Revolutions.keplerianPeriodOf`** construit avec `Constants.WGS84_EARTH_MU`
  ([:155](../../src/main/java/com/smousseur/orbitlab/simulation/mission/MissionHorizon.java)) :
  période lunaire rendue à **783–784 s** au lieu de 7 063–7 073 s. Le rapport mesuré vaut **9,015 à
  9,017**, contre le facteur 9,0 annoncé par le §4 / `L2` et le `√(µE/µM) = 9,017` arithmétique :
  **juste**.
- **`DynamicParameters.revolutionDays`**
  ([:158](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/params/DynamicParameters.java))
  fait le même calcul pour le libellé du wizard, avec le même facteur.

**Trois conditions cassent, pas deux.** Chacun de ces javadocs porte sa propre condition de réveil,
et `MIS-5` les casse toutes :

> `MissionHorizon` — « *Wake it when an arc can be non-terrestrial — L3/L4.* »
> `DynamicParameters` — « *It becomes contextual when the wizard can target another body, which no
> PHY-4 lot delivers.* »

Le découpage §4 / `L0` mesure 6 écrit « deux verdicts ». Le compte est faux, **le périmètre de `L2`
est juste** : les trois fichiers y sont déjà listés. Rien ne bouge.

Ce qui casse la condition de `MissionHorizon` est le choix de `L5` lui-même : le §5 du découpage
écrit « `Revolutions(12)` montre une orbite qui se dégrade », donc `MIS-5` vole un horizon
`Revolutions` et non le `FixedDuration` que `LunarDynamicParameters:35` fige à 7 j pour le profil de
survol — dont le javadoc cite nommément `MIS-4 / L0` §7 pt 4 comme la raison de le figer.

**Une correction au recensement lui-même.** Le tableau `multi-corps/03-conception-L1.md` §4.1 nomme
**douze** fichiers, pas onze : le découpage `MIS-5` en annonce onze et `MIS-4 / L0` n'en avait rangé
que dix, ayant omis `TransferProblem`. Les douze sont rangés ci-dessus. L'écart ne change aucun
verdict.

**Un site que le §4.1 ne recense pas**, vu en passant : `MinAltitudeTracker:81` soustrait le rayon
terrestre. Il n'est armé que par `GravityTurnManeuver`, `TransferManeuver` et
`AscentChainPropagation` — des arcs terrestres. **Terre légitime**, et hors du périmètre de `L2`.

---

## 8. Ce que ces chiffres corrigent

Sept énoncés écrits ailleurs, que la mesure dément. Ils sont ici pour qu'aucun ne soit recopié.

**1. « `v∞ ≈ 1,1 km/s` » et « Δv ≈ 930 m/s » — découpage §2.4.** Mesuré : `v∞` de 825,8 à 872,5 m/s
(**21 à 25 % plus bas**) et Δv d'insertion de 819,6 à 835,9 m/s (**11 % plus bas**). La recalibration
sur Apollo 11 était haute ; les deux lignes qui ne dépendent que de µ et du rayon lunaire — `v_circ`
et la période — sont exactes.
**Décidé : le §2.4 est remplacé par le tableau du §3, et `L3` dimensionne dessus.** L'orbiteur
descend de 690 à ~604 kg d'ergols. La conclusion du §6 pt 4 — il faut un moteur plus gros — ne bouge
pas : 67 % d'une révolution reste inutilisable.

**2. « Si la dispersion couvre 0 à 90° selon l'époque » — découpage §5, risque principal.** Mesuré :
**22,3°**, et toujours rétrograde, toujours à moins de 28° de l'équateur lunaire. Le risque principal
du chantier n'existe pas sous la forme redoutée.
**Décidé : « subie » tient, `L5` journalise.** Ce qui reste vrai est plus étroit et doit être écrit
comme tel en `L7` : **la chaîne ne délivre que des orbites rétrogrades quasi équatoriales**, jamais
une orbite polaire.

**3. « Si le périlune respire de 20 km en un jour » — découpage §4 / `L0` mesure 5 et §5, deuxième
risque.** Mesuré : **0,08 km**, sans dérive séculaire sur douze révolutions, aux quatre
inclinaisons.
**Décidé : le deuxième risque est clos.** La bande de mérite de `L5` est libre. À condition de se
rappeler que le modèle est une masse ponctuelle (§6, dernier paragraphe).

**4. « Un étage de circularisation qui possède déjà ~5 h d'approche à 1 s » — découpage §2.3 pt 5 et
§4 / `L0` mesure 4.** Mesuré : 6,78 h et 24 405 points pour la circularisation, mais **18,77 h et
67 582 points pour `Trim`**, que le découpage ne mentionne jamais. Le diagnostic vise le mauvais
étage ; la cause est `isPropulsive()` qui rend `true` par défaut.
**Décidé : consigné, pas corrigé.** La coupure du §2.3 pt 5 reste la bonne réponse pour `MIS-5` — et
elle le reste *par la même mesure*, puisque l'approche à 1 s coûterait bien 58 751 à 65 766 points.
Le profil GEO est un chantier séparé, écrit au §10.

**5. « La fenêtre du wizard est fixe à 880 × 660 » — découpage §2.3 pt 6.** Mesuré :
`MissionWizardWidget:35-36` pose **880 × 680**.
**Décidé : consigné, sans effet.** L'arithmétique de débordement du §2.3 pt 6 repose sur
`FormStyles.CONTENT_HEIGHT = 468`, une constante indépendante de la hauteur de fenêtre. Le
débordement de la septième carte tient inchangé, et `L6` aussi.

**6. « Onze sites » — découpage §4 / `L0` mesure 6, et « deux verdicts conditionnels ».** Le tableau
`multi-corps/03-conception-L1.md` §4.1 nomme **douze** fichiers, et **trois** de leurs verdicts
cassent, pas deux — `MissionHorizon` en plus d'`OrbitElements` et de `DynamicParameters`.
**Décidé : le périmètre de `L2` est déjà juste**, les trois fichiers y sont listés. Seul le compte
était faux.

**7. « The map offset → perilune is monotone » —
[`TranslunarInjectionPlan:444-446`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/TranslunarInjectionPlan.java).**
Le jour 12 le dément dans le journal : offset 1 837 km → périlune 1 173 km, offset 3 675 km →
**826 km**, offset 7 350 km → 1 456 km. La carte a un **minimum intérieur**, elle n'est pas monotone.
**Décidé : consigné.** La conséquence est au §9 : le refus annonce un plancher plus haut que le plus
bas point qu'il a lui-même évalué.

**Deux prédictions que la mesure confirme**, ce qui mérite d'être dit autant que les corrections :
l'approche à **16–18 h** et son coût de **~64 800 points** à la seconde (§4), tous deux justes à 2 %.
La coupure du §2.3 pt 5 repose sur des chiffres qui tiennent.

**Dérives de lignes**, pour que les renvois du découpage soient rectifiés au prochain passage :

| écrit | réel |
|---|---|
| `TranslunarInjectionPlan:1320` | `:1334` (visée), la bissection en `:441-453` |
| `DynamicParameters:135` | `:158` |
| `MissionHorizon:138` / `:141` | `:155` |
| `OrbitElements:130` | `:44` déclaré, `:129` appliqué |
| `PropellantBudget:40` / `:44-45` | `:47-48` |
| `StageEndStateDiagnostic:20` / `:22-23` | `:23-24`, et le fichier est dans `optimizer/`, pas `runtime/` |
| `GravityTurnProblem:260` | `:252`, `:263-266` |
| `TransferProblem:545` | `:45` |
| `LaunchPlane:157` | `:155` (javadoc), `:171` (azimut) |

---

## 9. Observations gratuites

Quatre choses vues dans les journaux, qui ne changent rien à `L0` mais qui ont un lecteur ailleurs.

**Le refus du jour 12 annonce un plancher qu'il n'a pas mesuré.** Le message dit « *best is 1173 km
after bracketing [1837, 1837] km* », alors que le bracketing avait évalué **826 km à l'offset
3 675 km** — 42 % plus bas — et l'avait jeté. Le refus reste juste (826 km est très au-dessus des
100 demandés), mais le nombre affiché n'est pas le meilleur connu. C'est la conséquence directe de
la non-monotonie du §8 pt 7 : le bracket ne retient que ses bornes.

**Le bracket dégénéré coûte encore 20 bissections pour rien.** `MIS-4 / L0` §8 l'avait signalé sur
`[1 738, 1 738] km` ; ici c'est `[1 837, 1 837] km`, et les vingt bissections réaffichent le même
1 173 km pendant **3,1 s des 6,1 s** du refus. Un test de largeur nulle rendrait le refus quasi
instantané. Le lecteur est `L7` : un wizard qui explore des dates paiera ce refus souvent.

**Le plancher de périlune atteignable dépend de la configuration de parking.** `MIS-4 / L0` §8 laisse
ouverte une contradiction entre 1 873 km mesurés au jour 12 et les « 135 km » d'un javadoc. Ce relevé
ajoute un troisième chiffre — **1 173 km au même jour 12**, depuis 400 km / 28,562° au lieu de
185 km / 30°. Il ne tranche pas la contradiction : il montre que le plancher n'est pas une propriété
de l'époque seule, ce qui explique qu'on puisse en trouver trois.

**L'écart plan / vol du correcteur différentiel** vaut **24 704 à 27 784 km** sur les quatre vols,
pour 184 à 1 217 m/s de correction sur la vitesse d'injection. `MIS-4 / L0` §2 mesurait 26 320 à
26 359 km depuis un parking à 30° : la position est du même ordre, mais **l'étalement passe de 39 km
à 3 080 km**, et le coût de fermeture du jour 6 (1 217 m/s) est six fois celui des trois autres. Rien
ne s'en sert — c'est une ligne de diagnostic — mais qui outillera `MIS-6` autour du seed de Lambert
voudra ce chiffre.

Pour référence, les quatre plans convergés : Δv **3 123,1 à 3 134,9 m/s**, offsets de visée **6 838 à
10 615 km**, masse 1 700 → **586–588 kg**, désalignement de plan 0,00° partout.

---

## 10. Ce que cette baseline ne dit pas

- **Rien sur une trajectoire capturée.** Les quatre vols passent au périlune et repartent ; aucun
  n'est freiné. Le Δv du §3 est celui qu'il **faudrait** dépenser, lu sur l'hyperbole, pas celui
  qu'une combustion a coûté. `L4` mesurera la différence, et `MIS-4 / L6` dit qu'elle n'est pas
  petite.
- **Rien sur un vol `MIS-5` complet.** Il n'existe pas avant `L5`. Les 15,5 s du §5 sont celles d'un
  vol **GEO**, et les 5 à 9 s du §2 celles d'un transfert qui **part déjà en orbite de parking**.
  Le temps de paroi de `L5` — quatrième risque du découpage §5 — reste non mesuré : il additionnera
  une ascension CMA-ES, deux `solve()` de TLI et deux passes de coast translunaire que la marche
  d'étages vole désormais.
- **Rien sur la physique de l'orbite lunaire.** Masse ponctuelle, pas de champ de gravité, pas de
  mascons. La tenue du §6 est celle du modèle.
- **Rien sur un plan désaligné.** Les quatre vols partent d'un plan qui contient exactement la
  direction d'arrivée. Ce que le désalignement réel de la fenêtre coûte à l'inclinaison délivrée du
  §2 n'est pas mesuré, et c'est la première chose que `L5` verra bouger.
- **Rien sur une autre altitude que 100 km.** Tout est mesuré à la cible unique du §6 pt 1. Le seul
  paramètre utilisateur du chantier n'a pas été balayé.
- **Rien sur la reproductibilité.** Une exécution, une JVM. Les temps de paroi sont *observés*,
  jamais assertés, et le rodage JIT est payé par la mesure 4 puis par le premier vol du balayage
  (9,3 s contre 5,3–6,8 s en régime).
- **Rien sur la correction du profil GEO.** Le §5 le chiffre et s'arrête là. Le remède —
  `sampleStepSeconds` sur la fenêtre de poussée plutôt que sur `isPropulsive()`, ou le découpage des
  étages analytiques en coast + combustion — touche le socle de toutes les missions et n'est pas de
  ce chantier.
