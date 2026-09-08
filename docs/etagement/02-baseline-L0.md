# PHY-8 / L0 — Baseline mesurée

Lot **L0** du découpage ([`01-decoupage.md`](01-decoupage.md) §5). Ce document ne contient
aucune décision de conception : il **consigne des chiffres**, mesurés avant que le catalogue
ne bouge, pour que `L2` et `L3` puissent prouver qu'ils n'ont rien déplacé et que `L4`
puisse dire exactement ce qu'il a déplacé.

Il corrige aussi le découpage sur huit points, dont deux qui touchent à ce que `L1` et `L2`
pourront invoquer comme preuve. Ils sont au §6.

**Aucun fichier de `src/main` n'a été touché**, ce qui est le critère de clôture du lot.

---

## 1. Conditions de mesure

| | |
|---|---|
| Date | 2026-09-08 |
| Commit | `8beec1a` « PHY-8: technical specifications of the booster/core split » |
| Arbre | propre, hors `docs/v2-preparation/__pycache__/` non suivi |
| JDK | GraalVM 21.0.5, assertions actives (`-ea`) |
| Graine CMA-ES | 42 |
| Budget d'évaluations | 40 000 |
| Époque des profils terrestres | `2026-01-01T12:00:00.000Z` |
| Époque des profils lunaires | `2026-03-31T16:25:25.000Z` (§4.6) |

Trois instruments, et la distinction compte :

- **`./gradlew gateTest`** — les quatre épinglages, une JVM par classe. Tâche créée par ce
  lot ; §2 dit pourquoi.
- **`./gradlew test --tests "*BoosterSplitBaselineTest*" -Dorbitlab.slowTests=true`** — le
  harnais de mesure créé par ce lot, six cellules, un rapport par cellule dans
  `build/baseline/phy8/`.
- **Le catalogue seul** — la moitié de ce que `L0` doit relever ne demande aucune
  propagation (§3).

---

## 2. Les quatre épinglages

### 2.1 Ce qu'ils étaient au début du lot

| Épinglage | État mesuré au commit `8beec1a` | Exécuté par `./gradlew test` ? |
|---|---|---|
| `EarthOrbitNonRegressionTest` | 4 tests, actifs | oui |
| `MissionPolylineBaselineTest` | 1 test, actif, littéraux à tolérance zéro | oui |
| `CentralBodyBaselineTest` | **`@Disabled("To be run only standalone")`** depuis `960f168` | **non** |
| `AscentBaselineN2Test` | 2 tests, `@EnabledIfSystemProperty("orbitlab.slowTests")` | **non** par défaut |

Un `./gradlew test` nu n'exécutait donc que **deux des quatre**, soit cinq méthodes. Le
découpage les traite tous les quatre comme des gates vivants (§2.2, §5 `L1`/`L2`, Risque 1
du §6).

### 2.2 `CentralBodyBaselineTest` n'était pas rouge

L'hypothèse naturelle — désactivé parce qu'il échouait — est fausse, et c'est mesuré.
Lancé seul, annotation retirée :

```
tests="4" skipped="0" failures="0" errors="0"      41 s, cleanTest inclus
```

Effet de bord utile : les 62 frontières à égalité stricte de `double` **ont survécu à
`5419f63`** (« PMD check main files », qui touche `OrekitService`, `StageChainRunner`,
`MultiStageLoadOptimizer`) **et à `2cde109`** (« Fixes J0-B/C/D », qui touche
`EarthOrbitMission`, `GEOMission`, `PropellantLoadOptimizer`, `MissionLoadEvaluator`).
Ces deux commits sont postérieurs à la désactivation ; personne n'avait vérifié depuis le
2026-08-31 qu'ils étaient numériquement neutres. Ils le sont.

### 2.3 Ce qui est rouge, c'est la sélection partielle

`BUG-7` se reproduit à l'identique au commit `8beec1a`, en 20 s :

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew cleanTest test --tests "*SoiCrossingDetectorTest*" --tests "*CentralBodyBaselineTest*" --tests "*MissionPolylineBaselineTest*"
```

Quatre échecs sur dix tests : `CentralBodyBaselineTest` geo/meo/leo400 — le profil polaire
tient — **plus `MissionPolylineBaselineTest`, qui n'est pas désactivé**. Les écarts sont
ceux que la fiche `BUG-7` consigne, au bit près :

| Grandeur | Attendu | Obtenu |
|---|---|---|
| `t` (LEO-400, frontière `Trim`) | 8 546,404 567 668 **282** s | 8 546,404 567 668 **333** s |
| `x` (même frontière) | 3 129 196,368 272 **18** m | 3 129 196,368 271 **845** m |

### 2.4 La tâche `gateTest`, et pourquoi elle était nécessaire

L'explication écrite dans `bugs.md` est **fausse** : la fiche dit que la suite complète
passe *« parce qu'elle fork plusieurs JVM et sépare les protagonistes »*. Il n'y a ni
`forkEvery` ni `maxParallelForks` dans `build.gradle`, et pas de `gradle.properties` du
tout — Gradle exécute les 197 classes de test dans **une seule JVM**. La suite complète
passe par chance d'ordonnancement.

`L0` ferme donc `BUG-7` par l'issue 1 de sa propre liste : une tâche `gateTest` à
`forkEvery = 1`, une JVM par classe, qui ne partage plus aucun cache temporel Orekit.
`CentralBodyBaselineTest` y retourne, et sort de `test` — dont le comportement est donc
**inchangé** par rapport à l'état sous `@Disabled`.

**Re-capture des quatre épinglages, `./gradlew gateTest`, 2 min 58 s :**

| Épinglage | Résultat |
|---|---|
| `EarthOrbitNonRegressionTest` | `tests=4 skipped=0 failures=0 errors=0` |
| `MissionPolylineBaselineTest` | `tests=1 skipped=0 failures=0 errors=0` |
| `CentralBodyBaselineTest` | `tests=4 skipped=0 failures=0 errors=0` |
| `AscentBaselineN2Test` | `tests=2 skipped=0 failures=0 errors=0` |

C'est la commande que `L1` et `L2` relancent pour fermer.

La suite rapide reste verte à côté : `./gradlew cleanTest test` rend **1 318 tests, 30
sautés, 0 échec, 0 erreur** sur 197 classes, en 2 min 57 s. `pmdMain`, `pmdTest` et
`spotlessCheck` passent.

---

## 3. Ce que le catalogue donne sans propager

`PropellantBudget.sizeTopStage` initialise **tous** les étages à leur capacité puis ne
redimensionne que le dernier — son propre Javadoc l'écrit : *« the ΔV left over by the
fully-loaded lower stages »*. Trois conséquences :

1. **La durée de combustion du premier étage est une constante de lanceur** sur toute
   mission budgétée : 157,0 s Falcon Heavy, 128,2 s Ariane 62. Elle ne varie que sur un
   profil dont les charges sont écrites à la main.
2. **Le T/W au décollage ne varie que par la charge de l'étage du haut et la charge utile.**
3. **Les trois grandeurs se calculent hors JVM**, et le calcul est vérifié : réimplémenté,
   il rend 6 699 kg pour l'ULPM de l'Ariane — les « 6,7 t » que le Javadoc de `Launchers`
   annonce — et il reproduit au chiffre près les 408 et 671 m/s de dette Isp
   qu'`IspProxyDebtTest` enregistre, qui volent les deux mêmes configurations.

| Profil | Charges (kg) | Masse au décollage (kg) | T/W | Combustion étage 0 |
|---|---|---|---|---|
| FH LEO 400, charge utile 10 t | 1 233 000 / 1 963 | 1 314 963 | **1,768** | 157,0 s |
| FH LEO-400 `LEGACY`, charges à la main | 600 000 / 100 000 | 770 150 | **3,019** | 76,4 s |
| FH GEO, pleine charge | 1 233 000 / 107 500 | 1 414 500 | **1,644** | 157,0 s |
| A62 LEO 400, charge utile 5 t | 434 000 / 6 699 | 487 699 | **2,083** | 128,2 s |

Débits : Falcon Heavy 7 854,6 kg/s (2 618,2 par corps), Ariane 62 3 385,5 kg/s.

Les deux dernières colonnes des lignes `LEGACY` et GEO sont **dérivées du rapport
`AscentBaselineN2Test`** et non re-volées : la masse au décollage y est le `massIn` de
l'étape `Vertical Ascent`, et les ergols du premier étage la somme des `propellant` de
`Vertical Ascent` et `Gravity turn (S1)`.

> **Le 157,0 s est juste partout sauf là où ça compte.** Toute mission budgétée vole un S1
> plein — c'est le point 1 ci-dessus, vérifié sur les six cellules du §4 — donc les 157,0 s
> que citent §2.3, §4 et le contrôle physique de `L3` y sont exacts. L'exception est le
> profil de référence LEO-400 `LEGACY`, dont les charges sont **écrites à la main** :
> `{600 000, 100 000}`, un S1 aux deux tiers, éteint à **76,4 s**, et un T/W de 3,02 au lieu
> de 1,64.
>
> Or c'est exactement ce profil-là que volent `MissionPolylineBaselineTest`
> ([l. 273](../../src/test/java/com/smousseur/orbitlab/simulation/mission/ephemeris/MissionPolylineBaselineTest.java))
> et `CentralBodyBaselineTest`
> ([l. 1242](../../src/test/java/com/smousseur/orbitlab/simulation/mission/operation/CentralBodyBaselineTest.java)),
> les deux épinglages à tolérance zéro. La condition « la charge du S1 se répartit au prorata
> 2/3 – 1/3 » du §4 du découpage n'est donc pas une précaution de rédaction : c'est sur les
> deux gates les plus stricts qu'elle sera exercée, et sur eux seuls. À prorata, les
> propulseurs et le corps s'éteignent au même instant **quelle que soit la charge** — 76,4 s
> ici, 157,0 s ailleurs — mais un prorata approximatif à 1 kg près y sera visible.

---

## 4. Les cellules mesurées

Six cellules, toutes vertes, 10 min 1 s. Un rapport complet par cellule dans
`build/baseline/phy8/<cellule>.txt` : c'est le fichier que `L2`, `L3` et `L4` diffent, pas
ce tableau, qui n'en est que la vue de tête.

### 4.1 Vue d'ensemble

| Cellule | Charges (kg) | Décollage (kg) | T/W | Extinction ét. 0 | MECO | ΔV total (m/s) |
|---|---|---|---|---|---|---|
| `falcon-heavy-leo-400` | 1 233 000 / 1 963 | 1 314 963,1 | 1,7681 | 156,98 s | t+158,98 s, 15 963,1 kg | 8 492,1 |
| `ariane-62-leo-400` | 434 000 / 6 699 | 487 698,8 | 2,0825 | 128,20 s | t+247,19 s, 13 120,6 kg | 8 062,2 |
| `ariane-62-meo` | 434 000 / 18 338 | 497 579,6 | 2,0412 | 128,20 s | t+384,66 s, 17 479,9 kg | 11 734,1 |
| `ariane-62-geo` | 434 000 / 20 257 | 499 571,9 | 2,0330 | 128,20 s | t+390,53 s, 19 236,4 kg | 11 916,9 |
| `falcon-heavy-lunar-flyby` | 1 233 000 / 12 518 | 1 317 518,0 | 1,7646 | 156,98 s | t+160,37 s, 18 119,3 kg | 11 417,9 |
| `falcon-heavy-lunar-orbit` | 1 233 000 / 14 394 | 1 320 052,1 | 1,7613 | 156,98 s | t+162,22 s, 20 121,9 kg | 12 262,3 |

**L'extinction volée de l'étage 0 égale sa durée calculée sur les six cellules** — 156,98 s
et 128,20 s, au centième. La colonne « catalogue » du §3 n'est donc pas une estimation : le
vol la reproduit.

### 4.2 Précision d'insertion

| Cellule | Orbite atteinte (osculatrice) | Inclinaison | Cible |
|---|---|---|---|
| `falcon-heavy-leo-400` | 400 115 × 419 337 m | 5,2957° | 400 km circulaire |
| `ariane-62-leo-400` | 400 117 × 419 351 m | 5,2909° | 400 km circulaire |
| `ariane-62-meo` | 19 641 725 × 20 203 913 m | 55,0020° | 20 200 km / 55° |
| `ariane-62-geo` | 35 788 590 × 35 793 095 m | 0,0000° | 35 786 km / 0° |
| `falcon-heavy-lunar-flyby` | 517 083 × 431 560 200 m | 31,8305° | périlune 100 km |
| `falcon-heavy-lunar-orbit` | 99 103 × 99 366 m (sélénocentrique) | 127,6693° | 100 km circulaire |

En éléments moyens, trois cellules seulement en ont : 409 693 × 409 915 m (FH LEO),
409 693 × 409 917 m (A62 LEO) — les deux à 2 m l'une de l'autre — et 19 640 192 ×
20 203 760 m (A62 MEO). Les trois autres n'ont **pas** de ligne moyenne, et c'est
structurel : `OrbitElements.mean` passe par Eckstein-Hechler, qui refuse les deux états
sélénocentriques (son Javadoc le dit et `OrbitElementsTest` l'épingle) et sort de son
domaine à l'altitude géostationnaire.

Deux contrôles croisés valident le harnais contre des mesures déjà au dépôt :

- `ariane-62-meo` reproduit **au mètre près** la ligne MEO de
  [`multi-corps/02-baseline-L0.md`](../multi-corps/02-baseline-L0.md) — mêmes 19 641 725 ×
  20 203 913 m, même 55,0020°, même ΔV total 11 734 m/s, même masse finale plancher ;
- `falcon-heavy-lunar-orbit` rend 99,103 × 99,366 km et 127,6693° contre les 99,071 ×
  99,366 km et 127,659° que `LunarOrbitFlightTest` a logués le même jour — l'écart de 32 m
  au périsélène tient à ce que la fixture lit la bande sur le coast terminal et le harnais
  l'état d'insertion.

### 4.3 Le résultat principal : sur Falcon Heavy, le premier étage fait tout

C'est le chiffre que `L2` et `L3` doivent avoir sous les yeux, parce que c'est exactement
l'étage qu'ils réécrivent.

| Cellule | ΔV ascension étage 0 | ΔV ascension étage 1 | part de l'étage 0 |
|---|---|---|---|
| `falcon-heavy-leo-400` | 8 056,1 m/s | **0,0 m/s** | **100 %** |
| `falcon-heavy-lunar-flyby` | 7 972,6 m/s | 74,3 m/s | 99,1 % |
| `falcon-heavy-lunar-orbit` | 7 892,4 m/s | 154,2 m/s | 98,1 % |
| `ariane-62-leo-400` | 6 490,9 m/s | 1 341,4 m/s | 82,9 % |
| `ariane-62-geo` | 5 974,0 m/s | 1 927,2 m/s | 75,6 % |
| `ariane-62-meo` | 6 053,0 m/s | 2 043,7 m/s | 74,8 % |

Sur `falcon-heavy-leo-400`, l'étage supérieur **ne s'allume pas du tout** pendant le virage
gravitationnel :

```
stage 'Gravity turn (S2)'  massIn=15963.1 kg, massOut=15963.1 kg, propellant=0.0 kg, dV=0.0 m/s, duration=2.0 s
```

Le MECO y est l'extinction du premier étage, à 2 s près. Ses 1 963 kg d'ergols partent
ensuite dans le `Transfert` (1 891,4 kg) et le `Trim` (23,4 kg). Ce n'est écrit nulle part
au dépôt : le Javadoc de `testFalconHeavyBudgetLoads` parle de « mesurer S2 seul », mais
c'est du résidu qu'il parle, pas de l'ascension.

La cause est le §3 : le S1 vole plein quelle que soit la charge utile, et un S1 de Falcon
Heavy plein est très surdimensionné pour 10 t à 400 km. La conséquence pour `PHY-8` est
directe — l'étranglement de `L3` allonge une combustion qui porte **98 à 100 %** du ΔV
d'ascension sur les trois profils Falcon, et rien en aval ne l'amortit.

### 4.4 Résidus de l'étage dimensionné

| Cellule | Chargé (kg) | Résiduel (kg) | Part |
|---|---|---|---|
| `falcon-heavy-leo-400` | 1 963,1 | 48,2 | 2,5 % |
| `falcon-heavy-lunar-orbit` | 14 394,4 | 839,3 | 5,8 % |
| `falcon-heavy-lunar-flyby` | 12 518,0 | 747,6 | 6,0 % |
| `ariane-62-geo` | 20 256,7 | 1 661,9 | 8,2 % |
| `ariane-62-meo` | 18 338,5 | 1 645,2 | 9,0 % |
| `ariane-62-leo-400` | 6 698,8 | 1 464,6 | 21,9 % |

Le 21,9 % de l'Ariane 62 en LEO retrouve les « 21.7 % » que le Javadoc de `Launchers`
annonce depuis le 2026-08-09.

### 4.5 L'Ariane 62 boucle une GEO

La cellule créée par ce lot vole : 35 788 590 × 35 793 095 m à 0,0000° d'inclinaison, avec
1 661,9 kg d'ULPM (8,2 %) et 54,5 kg d'AKM (4,1 %) de reste. C'est l'« avant » contre lequel
`L4` lira son Ariane 64.

### 4.6 Les époques lunaires sont épinglées, et il fallait le faire

Les deux chaînes budgétées volent `2026-03-31T16:25:25.000Z`, l'époque que la fenêtre leur
offre à 3 124 m/s. Le harnais la fige plutôt que de la rechercher, et la mesure du même jour
montre pourquoi : sur la **même** recherche, le profil de survol pleine charge — une autre
masse à l'injection — s'est vu **refuser** cette date par la chaîne,

```
[TLI] the aim did not reach the 100 km perilune: best is 132 km after bracketing
[1837, 1837] km and 20 bisections
```

et a volé `2026-03-31T22:31:30Z`. La date de fenêtre est donc une propriété du véhicule, et
le véhicule est ce que `PHY-8` change. Un lot qui obtient ce refus a mesuré quelque chose ;
il ne doit pas y répondre en cherchant une autre date, parce que deux MECO à deux époques ne
se comparent pas.

---

## 5. Le ΔV du trim LEO

Le découpage (§2.3) écrit qu'il n'est *« chiffré nulle part »* et que `L0` doit le mesurer.
Il est en fait **déjà journalisé et déjà rapporté** : `AnalyticTrimBurnStage` logue
`Trim burn plan: dv=…` ou `residual ΔV=… below threshold, skipping` (seuil 1 m/s), et le
`StagePerformance` de l'étape `Trim` le porte — donc `build/baseline/leo-400-n2.txt` l'écrit
depuis toujours. Ce qui manquait, c'est sa présence dans un document.

**Profil LEO-400 de référence (Falcon Heavy, `LEGACY`, charges à la main) :**

```
stage 'Trim'   massIn=35369.6 kg, massOut=35306.8 kg, propellant=62.8 kg, ΔV=6.1 m/s
```

**6,1 m/s pour 62,8 kg.** Et les deux cellules LEO budgétées du §4 le confirment sur d'autres
charges et sur l'autre lanceur :

| Profil | ΔV du trim | Ergols |
|---|---|---|
| FH LEO-400 `LEGACY`, charges à la main | 6,1 m/s | 62,8 kg |
| FH LEO 400, charge utile 10 t | 5,7 m/s | 23,4 kg |
| A62 LEO 400, charge utile 5 t | 5,9 m/s | 16,5 kg |

**Le trim LEO vaut donc ~6 m/s, stable à 0,4 m/s près sur deux lanceurs et trois charges.**
Pour situer, sur les mêmes rapports : le trim GEO vaut 173,9 m/s pour 140,9 kg (FH) et
168,6 m/s pour 113,5 kg (A62), et le plane trim GEO 4,2 m/s pour 3,3 kg (FH) — ce dernier
retrouvé **à l'identique** contre
[`multi-corps/02-baseline-L0.md`](../multi-corps/02-baseline-L0.md), sur le profil qui
l'avait produit.

> **Le trim MEO, lui, a bougé et personne ne l'avait vu.** Le découpage (§2.3) écrit que
> *« le dépôt donne le trim MEO à 57,8 m/s (passe d'optimisation) et 60,6 m/s (passe
> d'éphéméride) »*, en citant cette même baseline du 2026-08-16. Mesuré ici le 2026-09-08 sur
> la même fixture : **37,0 m/s pour 23,7 kg**, et le plane trim MEO tombe sous le seuil de
> 1 m/s et se saute. L'écart date d'entre les deux dates ; ce lot le constate et ne
> l'explique pas — aucun changement de `PHY-8` n'est en cause, `L0` ne touche pas
> `src/main`. C'est à relever avant que `L4` n'attribue un déplacement de la MEO à l'Ariane 64.

C'est le témoin que réclame la décision §3.7 : le budget ΔV déclaré par une charge utile
devra couvrir ce résidu, et l'ordre de grandeur à couvrir en orbite basse est la **poignée
de mètres par seconde**, pas la dizaine. Deux ordres de grandeur séparent le trim LEO du
trim GEO ; un budget unique pour les deux serait soit gaspilleur, soit insuffisant.

---

## 6. Ce que la mesure corrige au découpage

1. **`CentralBodyBaselineTest` était désactivé** (§2.1). Le §2.2 du découpage le décrit comme
   *« le seul vrai coût »* des quatre épinglages, et `L1`/`L2` ferment sur *« les quatre
   épinglages restent verts sans avoir été modifiés »*. Aucun des deux n'était vérifiable
   avant ce lot. Ça l'est maintenant, par `gateTest`.
2. **`AscentBaselineN2Test` est derrière un drapeau** (§2.1) : `./gradlew test` n'en exécutait
   que deux sur quatre.
3. **Le T/W au décollage est une propriété du profil, pas du lanceur** (§3), et l'écart va de
   1,64 à 3,02 sur le seul Falcon Heavy. Le §5 `L0` le demande « par lanceur » ; il ne se
   relève que par cellule.
4. **Le profil que les deux gates à tolérance zéro épinglent est le seul qui ne vole pas un
   S1 plein** (§3, encadré). Le découpage traite les 157,0 s comme une caractéristique du
   lanceur ; ils le sont pour toute mission budgétée, et pas pour le profil sur lequel `L2`
   devra prouver son iso-trajectoire.
5. **Le ΔV du trim LEO n'était pas à mesurer, mais à consigner** (§5). Il vaut ~6 m/s, mesuré
   sur trois profils.
6. **`sizeTopStage` ne dimensionne que l'étage du haut.** Conséquence pour `L4`, pas pour
   `L0` : avec `[P120C ×4, LLPM, ULPM]`, les propulseurs **et** le corps sont des étages bas,
   donc tous deux volés pleins, et `variableLoad()` n'est consulté que sur l'étage du haut.
   Le *« les solides volent pleins et le corps prend le reste »* du §5 `L4` demandera donc un
   changement de `PropellantBudget` que le découpage ne nomme pas.
7. **Le trim MEO ne vaut plus 57,8 / 60,6 m/s mais 37,0 m/s** (§5, encadré). Le découpage
   cite ces deux chiffres comme courants ; ils datent du 2026-08-16 et ont bougé depuis,
   pour une cause que `L0` ne cherche pas.
8. **Le Risque 3 se lit déjà à l'envers sur le Falcon** (§4.3). Le découpage craint qu'*« un
   étage supérieur plus léger arrête la turn avant que le S1 ne soit sec »*. Sur les trois
   profils Falcon mesurés c'est le contraire qui se produit : la turn va jusqu'au S1 sec et
   l'étage supérieur ne fournit que 0 à 154 m/s — rien du tout sur `falcon-heavy-leo-400`.
   La marge que ce garde-fou surveille est donc largement du côté opposé sur ce lanceur, et
   c'est l'Ariane, où l'étage supérieur porte 17 à 25 % de l'ascension, qu'il faudra
   surveiller en `L4`.

---

## 7. Ce qui reste vide, et pourquoi

Le §5 `L0` demande la mesure *« par lanceur et par profil (LEO 400, GEO, MEO, lunaire) »*.
La matrice a huit cases ; quatre avaient une fixture, `L0` en ajoute une, trois restent vides.

| | Falcon Heavy | Ariane 62 |
|---|---|---|
| **LEO 400** | mesuré (2 profils) | mesuré |
| **GEO** | mesuré | **mesuré — cellule créée par `L0`** |
| **MEO** | *vide* | mesuré |
| **lunaire** | mesuré (survol + orbite) | *vide* |

`Ariane 62 → GEO` a été ajoutée parce que `L4` remplace l'A62 par une A64, qui est un
lanceur géostationnaire : c'est la seule case vide dont `PHY-8` produira un « après », et
sans « avant » ce résultat-là serait ininterprétable.

Les trois autres restent vides délibérément : aucun lot de `PHY-8` ne les re-volera, et une
ligne qu'aucun lot ne relira est une ligne qui périme sans que personne s'en aperçoive.

---

## 8. Reproduire

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew gateTest
```

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew cleanTest test --tests "*BoosterSplitBaselineTest*" -Dorbitlab.slowTests=true
```

Le `cleanTest` n'est pas décoratif : relancer le même filtre `--tests` après un succès rend
`> Task :test UP-TO-DATE` et n'exécute **rien**, en affichant un BUILD SUCCESSFUL vert.
