# PHY-8 / L2 — Le Falcon Heavy éclaté, à f = 1 — conception

Lot **L2** du découpage ([`01-decoupage.md`](01-decoupage.md) §5), au-dessus du mécanisme livré par
[`03-conception-L1.md`](03-conception-L1.md) et mesuré contre
[`02-baseline-L0.md`](02-baseline-L0.md). Ce que le lot rend vrai : **le catalogue décrit trois
étages là où il en décrivait deux, et la fusée vole exactement pareil.**

Le lot est plus petit que `L1` — le mécanisme existe, il ne reste qu'à l'utiliser — mais il contient
une pièce que le découpage ne nomme pas et nomme même à l'envers. Le §2.1 la mesure, le §7 la
consigne.

---

## 1. Périmètre

**Dans `L2`** : l'entrée `FALCON_HEAVY` du catalogue, éclatée en `[propulseurs ×2, corps, S2]` à
étranglement 1,0 ; le repliement du bloc pour le bilan ΔV de `PropellantBudget` ; la poussée au
décollage sur la carte du wizard ; et la reprise des tableaux de charges écrits à la main.

**Hors `L2`** : l'étranglement réel (`L3`), l'Ariane 64 (`L4`), les hauteurs et la taille dessinée
(`L5`), les charges utiles (`L6`). Rien de la couche de rendu : la table de `LauncherAssets` associe
un maillage entier à un **id de lanceur**, pas à un étage, donc éclater l'entrée ne la touche pas.

---

## 2. Les mesures qui décident

### 2.1 `sizeTopStage` modélise les étages bas en série, et l'éclatement le trompe

C'est la mesure qui décide du lot, et elle contredit le §5 `L2` du découpage.

Les six points d'entrée du budget passent tous par `sizeTopStage`, qui met **tous** les étages bas à
leur capacité et ne dimensionne que celui du haut (`02-baseline-L0.md` §3, point 1). Avec des
capacités de 822 t et 411 t — elles-mêmes dans le rapport 2 des débits — le prorata 2/3 – 1/3 que le
découpage demande de calculer est **déjà là**, gratuitement, dans les chiffres du catalogue.

Ce qui casse est ailleurs. `sizeTopStage` évalue le ΔV des étages bas par une **boucle de
Tsiolkovsky en série**, chaque étage larguant sa masse sèche avant que le suivant n'allume. Éclater
le S1 en deux entrées lui fait croire à deux largages là où il n'y en a qu'un, et il crédite du ΔV
qui n'existe pas :

```
Falcon Heavy, LEO 400 km, charge utile 10 t

  série (aujourd'hui) :  charges = 1 233 000 / 1 962,5      ΔV des étages bas =  8 056 m/s
  éclatée sans rien    :  charges =  822 000 / 411 000 / 0   ΔV des étages bas = 10 167 m/s
                                                             écart            = +2 111 m/s
```

**La charge de l'étage supérieur s'effondre.** Les trois cellules Falcon de `L0` §4.1, chacune avec
son ΔV total reconstitué depuis la charge que `L0` a mesurée :

| Cellule `L0` | charge utile | ΔV total | S2 aujourd'hui | S2 éclaté sans rien | S2 replié |
|---|---|---|---|---|---|
| `falcon-heavy-leo-400` | 10 000 kg | 8 465 m/s | 1 963,1 kg | **0 kg** | 1 963,1 kg |
| `falcon-heavy-lunar-flyby` | 2 000 kg | 11 602 m/s | 12 518,0 kg | **2 063,2 kg** | 12 518,0 kg |
| `falcon-heavy-lunar-orbit` | 2 658 kg | 11 602 m/s | 14 394,4 kg | **2 604,5 kg** | 14 394,4 kg |

La cellule GEO est la seule à survivre, et par accident : sa charge est déjà écrêtée à la capacité
de l'étage (107 500 kg), donc une baisse du ΔV restant ne la déplace pas.

**Le travail de `L2` sur `PropellantBudget` est donc l'inverse de celui que le §5 annonce** : non pas
répartir une charge — il n'y a rien à répartir — mais **replier les deux entrées en une** pour le
bilan ΔV.

### 2.2 Aucun des quatre épinglages n'exerce `PropellantBudget`

| Épinglage | Charges volées |
|---|---|
| `MissionPolylineBaselineTest` | écrites à la main, `{600 000, 100 000}` |
| `CentralBodyBaselineTest` | écrites à la main, `{600 000, 100 000}` |
| `AscentBaselineN2Test` | écrites à la main, `{600 000, 100 000}` |
| `EarthOrbitNonRegressionTest` | `LaunchConfiguration.fullyLoaded`, donc les capacités |

Le repliement — c'est-à-dire tout le code neuf du lot — ne serait gardé par **rien** de ce que le
découpage désigne pour fermer `L2`. Le §5.2 ajoute la preuve qui manque.

### 2.3 `IspProxyDebtTest` resterait vert en rapportant un chiffre faux

`debtOf` calcule `burnOutMass = liftOffMass − loads[0]`, puis
`g₀·(Isp_vide − Isp_proxy)·ln(m₀/m_f)`. Éclaté, `loads[0]` ne vaut plus que les propulseurs :

```
lift-off 1 314 963 kg
  bloc entier   : 1 314 963 / (1 314 963 − 1 233 000) = 16,04  →  408 m/s
  loads[0] seul : 1 314 963 / (1 314 963 −   822 000) =  2,67  →  144 m/s
```

Le test n'assère que la positivité de la dette, donc il resterait vert. Les **408 m/s** que la fiche
roadmap, le §3.4 du découpage et le §3 de `L0` citent tous les trois deviendraient silencieusement
faux.

### 2.4 Huit tableaux de charges écrits à la main, dans sept fichiers

| Fichier | Charges |
|---|---|
| `MissionPolylineBaselineTest:274` | `{600 000, 100 000}` |
| `CentralBodyBaselineTest:1243` | `{600 000, 100 000}` |
| `AscentBaselineN2Test:191` | `{600 000, 100 000}` |
| `LEOMissionOptimizationTest:59` | `{600 000, 100 000}` |
| `EarthOrbitValidationTest:175` | `{1 233 000, 107 500}` |
| `LaunchConfigurationTest:33` | `{600 000, 50 000}` |
| `LaunchersTest:143` | `{600 000, 50 000}` |
| `LaunchConfigurationTest:16` | `{600 000}`, un test de rejet |

`LaunchersTest.falconHeavy_knownFigures` épingle en plus `assertEquals(2, stages.size())` et les six
chiffres du S1. Ce n'est pas un des quatre gates.

### 2.5 La carte du wizard annoncerait 15,2 MN

`StepLauncher:100` lit `stages().getFirst().propulsion().thrust()` sous le libellé `"S1 thrust"`.
Éclaté, `getFirst()` est l'entrée des propulseurs : la carte passerait de 22,8 à 15,2 MN sans
qu'aucune fusée n'ait changé.

### 2.6 Ce que `LauncherAssets` dit de faux, et qui n'est pas de `L2`

La table pointe déjà `models/vehicles/ariane_64/ariane_64.gltf` pour `ARIANE_62`, mais son Javadoc
annonce toujours que le maillage est *« an Ariane 5 »*. `AST-1` a remplacé l'asset sans reprendre la
prose. `DT-12` est donc à moitié close dans le code, et c'est la documentation qui ment — à relever
avant que `L4` ne parte de la fiche.

---

## 3. Décisions de conception

### 3.1 Le repliement vit sur `StagingPlan`

**Décision : `StagingPlan.foldParallelBlock(List<StageModel>, AscentProfile)`**, à côté de
`checkStructure` — qui prend déjà des `StageModel` et un `AscentProfile` sans avoir besoin des
charges, exactement ce dont `sizeTopStage` dispose. Il rend la liste des étages avec la paire
`BOOSTER` + `CORE` remplacée par un étage équivalent : masse sèche sommée, capacité sommée,
`Isp_eff = ΣF/Σ(F/Isp)`, poussée sommée.

La notion « ces deux entrées sont une seule combustion » reste ainsi définie **à un seul endroit**,
celui que `L1` a créé pour elle. La placer dans `PropellantBudget` en aurait fait une seconde
définition, dans une classe de 544 lignes qui n'en savait rien ; l'exposer comme une seconde liste
d'étages sur `LauncherModel` aurait obligé chaque appelant à choisir entre deux vues du même
catalogue — un choix qu'on ne peut pas rater aujourd'hui parce qu'il n'existe pas.

**Sur le Falcon éclaté, l'étage équivalent vaut 66 000 kg / 1 233 000 kg / 296 s / 22,8 MN** :
littéralement les chiffres du `S1` d'aujourd'hui. `L1` a mesuré que l'`Isp_eff` retombe sur 296,0
exactement (§2.4 de sa conception). `sizeTopStage` voit donc la même liste qu'avant — mêmes entrées,
mêmes bits — et rend les mêmes charges.

> **La condition de validité, écrite noir sur blanc.** Le repliement est exact **exactement quand le
> largage est groupé**. La formule série suppose que la masse sèche d'un étage part d'un coup ; c'est
> vrai à `f = 1`, où propulseurs et corps s'éteignent et se larguent ensemble, et faux dès `L3`, où
> les 44 t de propulseurs partent 30 s avant les 22 t du corps. Le repliement sous-estimera alors le
> ΔV des étages bas. `L3` mesurera l'écart et décidera ; `L2` n'a pas à le deviner, et ne doit
> surtout pas prétendre l'avoir traité.

### 3.2 Le dépliage ne demande aucun prorata

**Décision : après le repliement, `sizeTopStage` réécrit ses charges sur la vraie liste** —
`loads[i] = capacité(i)` pour chaque étage bas, la valeur dimensionnée sur le dernier.

Aucune répartition n'est calculée, et c'est le point : les étages bas volant toujours pleins, la
charge d'un bloc *est* la somme des capacités de ses deux entrées, et la scinder revient à réécrire
ces deux capacités. Le « prorata 2/3 – 1/3 » du découpage n'a pas été déplacé ailleurs, il n'a jamais
eu de travail à faire — il est une propriété des capacités du catalogue, pas d'un algorithme.

### 3.3 La dette Isp reste définie sur le bloc

**Décision : `IspProxyDebtTest` somme les charges des entrées allumées au sol**, ce qui redonne
exactement les 408 m/s : le bloc brûle les mêmes 1 233 t entre les mêmes deux masses.

**Pourquoi pas une dette par entrée**, que le §3.4 du découpage semble appeler en promettant à
`PHY-2` « une entrée à reprendre par lanceur » : **un rapport de masses présuppose une combustion en
série**. Les deux réservoirs du bloc se vident en même temps ; ni les propulseurs ni le corps n'ont
de `m₀/m_f` propre, seul le bloc en a un. La question ne se posera qu'en `L4`, où les deux Isp
diffèrent réellement et où le largage est en deux temps.

### 3.4 La carte du wizard annonce la poussée au décollage

**Décision : `"Lift-off thrust"`, somme des entrées en `IgnitionMode.GROUND`.**

Elle affiche 22,8 MN sur le Falcon éclaté — le chiffre d'aujourd'hui, inchangé — et 9,96 MN sur
l'Ariane 62. Le libellé devient vrai pour les deux lanceurs, la grandeur affichée est celle qu'un
utilisateur compare réellement d'un lanceur à l'autre, et elle survivra telle quelle à `L4`.

Garder `getFirst()` en le renommant « Booster thrust » aurait été honnête sur ce qui est lu, mais la
carte aurait comparé des choses différentes selon le lanceur — des propulseurs sur le Falcon, un S1
agrégé sur l'Ariane tant que `L4` n'est pas passé.

### 3.5 Le catalogue

```
Boosters (2 side cores)  ×2   22 t / 411 t / 7,6 MN / Isp 296 / 10,5 m² / Cd 0,4   BOOSTER, GROUND
Core                          22 t / 411 t / 7,6 MN / Isp 296 / 10,5 m² / Cd 0,4   CORE,    GROUND
S2 (Merlin Vacuum)            inchangé                                              UPPER
coreThrottle                  1,0
```

Les composants sont **par exemplaire** (`L1` §3.4), donc les agrégats se lisent 44 t / 822 t /
15,2 MN / 21,0 m² côté propulseurs, et 66 t / 1 233 t / 22,8 MN au niveau du bloc.

**Un chiffre bouge : la section, de 31,6 à 31,5 m².** Le catalogue arrondissait son agrégat à 31,6 ;
trois exemplaires de 10,5 en donnent 31,5, et la valeur exacte est 3 × π·1,83² = 31,56 — donc 31,5
est l'arrondi le plus proche des deux. Rien ne la lit : aucune mission de production ne déclare
d'atmosphère, et la traînée est de `PHY-2`.

Les deux entrées gardent `PropellantType.CRYOGENIC` : ce sont des blocs kérolox identiques, et la
convention de charge variable du catalogue s'applique aux trois.

---

## 4. Ce que le lot touche

| Fichier | Ce qui change |
|---|---|
| `vehicle/catalog/Launchers` | `FALCON_HEAVY` en trois entrées (§3.5) |
| `vehicle/StagingPlan` | `foldParallelBlock` (§3.1) |
| `vehicle/PropellantBudget` | `sizeTopStage` replie puis déplie (§3.1, §3.2) |
| `ui/mission/wizard/step/StepLauncher` | poussée au décollage (§3.4) |
| 7 fichiers de test | les huit tableaux de charges (§2.4) |
| `LaunchersTest` | les chiffres du Falcon, trois étages |
| `IspProxyDebtTest` | la dette sur le bloc (§3.3) |
| *nouveau* | la non-régression arithmétique du budget (§5.2) |

---

## 5. Ce qui ferme le lot

### 5.1 Les quatre épinglages, et une précision que le découpage ne fait pas

`L1` fermait sur « les quatre épinglages verts **sans avoir été modifiés** ». `L2` ne le peut pas :
`MissionPolylineBaselineTest` et `CentralBodyBaselineTest` écrivent leurs charges à la main et
passent de deux cases à trois.

Ce qui reste intouché, et c'est ce qui compte, ce sont **les valeurs épinglées** : la table de 1 296
lignes de frontières à égalité stricte, la liste de sommets et `RAW_POINTS`, les tolérances mesurées
d'`AscentBaselineN2Test`. Une seule ligne bouge par fichier, et elle bouge vers le prorata exact
— `{600 000, 100 000}` → `{400 000, 200 000, 100 000}` — que `L1` a déjà volé à tolérance zéro
contre le catalogue non éclaté.

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew gateTest
```

**Mesuré, 3 min 38 s :**

| Épinglage | Résultat |
|---|---|
| `EarthOrbitNonRegressionTest` | `tests=4 skipped=0 failures=0 errors=0` |
| `MissionPolylineBaselineTest` | `tests=1 skipped=0 failures=0 errors=0` |
| `CentralBodyBaselineTest` | `tests=4 skipped=0 failures=0 errors=0` |
| `AscentBaselineN2Test` | `tests=2 skipped=0 failures=0 errors=0` |

Et le diff des deux fichiers à tolérance zéro tient la promesse au pied de la lettre : **une ligne
chacun, et c'est la ligne des charges.**

```
-        Launchers.FALCON_HEAVY, new double[] {600_000, 100_000}, Spacecraft.LEGACY);
+        Launchers.FALCON_HEAVY, new double[] {400_000, 200_000, 100_000}, Spacecraft.LEGACY);
```

### 5.2 La non-régression arithmétique du budget

Le catalogue éclaté contre une **fixture séquentielle figée dans le test**, qui reproduit le Falcon
d'aujourd'hui : sur les six points d'entrée de `PropellantBudget`, les charges rendues doivent être
identiques à **tolérance zéro**. Aucune propagation, quelques millisecondes.

C'est le miroir exact du test qui a fermé `L1`, et il est délibérément bâti sur une référence plutôt
que sur des littéraux : `L0` a retenu du jalon `J0` que ce qui pourrit dans un registre, ce sont les
chiffres. Celui-ci survivra à `L3` et à `L4` sans être touché.

Sept cellules, toutes vertes. La septième est le témoin du défaut lui-même : elle exige que la charge
de l'étage supérieur dépasse une tonne, ce qui échoue à zéro sans le repliement.

**Et le vol iso-trajectoire a changé de côté.** `ParallelBlockAscentTest`, qui a fermé `L1` en
comparant une fixture éclatée au catalogue séquentiel, compare désormais le **catalogue éclaté** à une
fixture qui reproduit le Falcon d'avant `L2`. La preuve est la même, la référence a traversé :

```
Catalog (split) vs aggregated Falcon Heavy at MECO: Δpos 0.000e+00 m, Δvel 0.000e+00 m/s, Δmass 0.000e+00 kg
```

Conséquence à savoir : **le Falcon Heavy à premier étage agrégé n'existe plus que dans deux fixtures
de test** — celle-ci et celle du §5.2. C'est délibéré, et c'est ce qui permet aux deux preuves de
survivre à `L3` et `L4`.

**Suite complète** : 1 404 tests, 30 sautés, 0 échec, 0 erreur sur 205 classes, en 4 min 23 s —
contre 1 388 sur 203 à la clôture de `L1`. `pmdMain`, `pmdTest` et `spotlessCheck` passent.

### 5.3 Les six cellules de `L0`, à la main

`BoosterSplitBaselineTest` relancé et ses rapports diffés contre `build/baseline/phy8/`, ce que `L0`
§4 désigne comme la méthode de `L2`, `L3` et `L4`. Dix minutes derrière `orbitlab.slowTests`, donc
hors de la boucle courte. Les trois cellules Falcon doivent rendre les mêmes charges, la même masse
au décollage, le même T/W, la même extinction d'étage 0 et le même MECO ; leur ligne
`stage 0 section (m2)` passe de 31,600 à 31,500, et la ligne `propellant [i]` en compte une de plus.

---

## 6. Limitations assumées

- **Le repliement cesse d'être exact en `L3`** (§3.1, encadré). Il sous-estimera le ΔV des étages
  bas dès que le largage se fera en deux temps ; l'écart est à mesurer, pas à deviner.
- **La section perd 0,1 m²** (§3.5), inerte tant que `PHY-2` n'est pas passé.
- **La dette Isp par entrée n'existe pas** pour un bloc parallèle (§3.3), et `PHY-2` devra composer
  avec un seul chiffre par bloc jusqu'à ce que `L4` sépare des Isp réellement différentes.
- **`L2` livre un état que personne ne fera voler** : un Falcon éclaté non étranglé. C'est le prix
  que le §4 du découpage assume pour que le re-baseline de `L3` soit attribuable à une cause unique.

---

## 7. Ce que la conception corrige au découpage

1. **Le §5 `L2` a sa phrase à l'envers.** Il demande à `PropellantBudget` de « répartir la charge du
   S1 au prorata 2/3 – 1/3 ». Mesuré : il n'y a rien à répartir — les étages bas volent pleins et les
   capacités sont déjà dans le rapport des débits — et il y a un repliement à écrire, que le
   découpage ne nomme pas. Sans lui, la charge de l'étage supérieur tombe à zéro sur la LEO et perd
   83 % sur les deux profils lunaires (§2.1).
2. **Les quatre épinglages ne gardent pas le code neuf du lot** (§2.2). Aucun ne passe par
   `PropellantBudget`. Le critère de clôture du §5 est donc insuffisant tel quel.
3. **`L2` ne peut pas fermer « sans avoir modifié les épinglages »** (§5.1). Deux d'entre eux
   écrivent leurs charges à la main ; ce qui reste intouché, ce sont les valeurs épinglées, pas les
   fichiers.
4. **`IspProxyDebtTest` rapporterait 144 m/s au lieu de 408 en restant vert** (§2.3). Les 408 m/s
   sont cités par la fiche roadmap, par le §3.4 du découpage et par le §3 de `L0`.
5. **`LauncherAssets` n'est pas concerné par l'éclatement**, et son Javadoc est déjà faux sur
   l'Ariane (§2.6) : `DT-12` est plus petit que la fiche `L4` ne le croit.

**Ce que l'implémentation a ajouté.**

- **Le §2.4 comptait huit tableaux de charges dans sept fichiers ; il en a fallu dix dans huit.**
  Manquaient à l'inventaire les deux tableaux de `MissionProfileTest` (un test d'IHM, où le Falcon
  est instancié par `Launchers.byId`) et le `withLauncherLoads({1 000, 500})` d'`EarthOrbitValidationTest`,
  qui n'est pas une charge de vol mais une sonde de re-dimensionnement. Le grep du §2.4 cherchait
  `FALCON_HEAVY, new double[]` ; ces trois-là ne s'écrivent pas ainsi.
- **Cinq tests indexaient l'étage supérieur par `[1]`.** `PropellantBudgetTest`, `MissionFactoryTest`
  et les fixtures de `LaunchConfigurationTest` lisaient `loads[1]` ou `vehicles.get(1)` comme « le S2 » ;
  c'est le corps depuis `L2`. Tous ont échoué franchement plutôt que de rendre un chiffre plausible,
  parce que les grandeurs comparées sont d'ordres différents — mais rien ne le garantissait.
- **La prémisse du gate de `L1` s'est inversée**, ce que le §4 n'avait pas prévu : sa référence
  séquentielle était le catalogue, et le catalogue est maintenant l'éclaté. Le test a été retourné
  plutôt que dupliqué, et le §5.2 le consigne.

---

## 8. Ce que `L2` lègue

- À **`L3`** : un catalogue déjà éclaté et prouvé iso-trajectoire, un seul nombre à poser, et un
  repliement dont la condition de validité est écrite — donc un écart à mesurer plutôt qu'une
  surprise à découvrir.
- À **`L4`** : la forme exacte que l'Ariane 64 prendra, et la question du repliement d'un bloc dont
  les deux Isp diffèrent, posée mais non tranchée.
- À **`PHY-2`** : une dette Isp toujours à 408 m/s sur le Falcon, mesurée sur le bloc, et une section
  déclarée par exemplaire (10,5 m²) qu'il suffira de multiplier.
- À **`PHY-5`** : la masse sèche et la section d'**un** propulseur Falcon — 22 t et 10,5 m² — lisibles
  directement au catalogue.
