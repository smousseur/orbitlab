# Baseline N2 — référence de non-régression avant le découpage des séparations

> **Statut** : relevé de référence (étape 0 du plan de migration de
> [`01-separations-implicites.md`](01-separations-implicites.md) §8).
> **Code de référence** : `1d53e83` — état pré-découpage, aucune modification de
> production.
> **Date du relevé** : 2026-08-03. **Graine CMA-ES** : 42 (`TEST_SEED`).
> **Machine** : Windows 10, GraalVM JDK 21.0.5 (les seuls chiffres sensibles à la
> machine sont les temps de calcul, cf. §7).

## 0. À quoi sert ce fichier

Le découpage `Gravity turn → Gravity turn (S1) → S1 separation → Gravity turn (S2)`
redémarre l'intégrateur adaptatif à chaque nouvelle frontière de phase : la
séquence de pas change, donc l'arithmétique flottante aussi. **« Iso-trajectoire »
ne peut donc pas vouloir dire bit-identique** — la non-régression se mesure par
des tolérances sur des grandeurs relevées *avant* le refactor.

Ce fichier est ce relevé. Il fixe les valeurs de référence des critères **N2** de
§7.1 du document 01, plus les valeurs « avant » des grandeurs dont §7.2 annonce
qu'elles vont bouger légitimement (ΔV total, résidus par étage, nombre de points
d'éphéméride) — pour que le changement soit constaté, pas subi.

Les mêmes valeurs sont écrites dans le code, seul endroit où elles sont
réellement vérifiées :

| Où | Ce qui y est figé |
|---|---|
| `AscentBaselineN2Test` (`simulation/mission/optimizer/`) | Les deux profils complets, optimisation CMA-ES comprise : MECO, orbite finale, `transitionTime` retenu |
| `GravityTurnReplayConsistencyTest#gravityTurnExit_matchesTheRecordedPreSplitBaseline` | L'état de sortie du gravity turn **à variables figées**, sans optimiseur : le calage des dates de largage et l'état MECO |

## 1. Comment relever et comparer

Le relevé complet (les deux profils, ~30 s au total sur la machine de référence) :

```bash
./gradlew test --tests "*AscentBaselineN2Test" -Dorbitlab.slowTests=true
```

La classe est opt-in (`@EnabledIfSystemProperty`, même convention que les autres
boucles d'intégration) : sans le flag elle est *skippée*, pas verte. Elle écrit à
chaque exécution le relevé mesuré dans `build/baseline/leo-400-n2.txt` et
`build/baseline/geo-n2.txt`, ce qui permet de diffuser deux étapes directement :

```bash
diff build/baseline/geo-n2.txt /chemin/vers/le/relevé/précédent.txt
```

La référence à variables figées, rapide (~10 s), sans optimiseur :

```bash
./gradlew test --tests "*GravityTurnReplayConsistencyTest"
```

**Re-relevé après un changement de comportement assumé** (étape 5 : retrait de la
pénalité d'étagement) : remettre la constante `LEO_400_BASELINE` / `GEO_BASELINE`
concernée à `null` dans `AscentBaselineN2Test`, relancer — le test passe en mode
capture, journalise le relevé sans comparer — puis recopier les nouvelles valeurs
et **documenter l'écart ici** (§8).

## 2. Fixtures

Ce sont exactement les missions que les critères N1 font déjà voler, pour que N1
et N2 parlent du même vol :

| Profil | Construction | Source |
|---|---|---|
| **LEO 400 km** | `LEOMission("Falcon Heavy", LaunchConfiguration(FALCON_HEAVY, {600 000, 100 000} kg, Spacecraft.LEGACY), 400 000)` | `LEOMissionOptimizationTest#testFalconHeavy` |
| **GEO** | `GEOMission("GTO mission", 400 000, 35 786 000)` — FH pleine charge + `Payloads.GEO_SAT` AKM plein | `GEOMissionOptimizationTest#testGEOMission` |

Époque de lancement commune : `2026-01-01T12:00:00.000Z`. Budget d'évaluations :
40 000 par étage optimisable. Graine : 42.

## 3. Baseline LEO 400 km (Falcon Heavy)

### 3.1 Critères N2

| Grandeur | Valeur de référence |
|---|---|
| `transitionTime` retenu (CMA-ES) | **307,851992 s** |
| `exponent` retenu (CMA-ES) | 0,127477 |
| Coût / évaluations | 8,6 × 10⁻⁵ / 2 063 |
| MECO — date depuis le décollage | **t + 314,851992 s** |
| MECO — masse | **36 178,699 kg** |
| MECO — position (m, GCRF) | −3 065 843,366118 ; −5 677 509,391909 ; 577 855,801484 |
| MECO — vitesse (m/s) | 6 964,499845 ; −3 779,273106 ; −188,095349 |
| Orbite finale — périgée | **381 147,9 m** |
| Orbite finale — apogée | **419 094,3 m** |
| Orbite finale — inclinaison | **5,304766°** |

Le MECO est identique au bit près entre la passe d'optimisation et la passe
éphéméride (Δpos = 0,000 m, Δvel = 0,00000 m/s, Δmasse = 0,000 kg) — c'est le
point le plus exposé par le découpage, cf. §8.1.

### 3.2 Grandeurs qui vont bouger (référence « avant » de §7.2)

| Grandeur | Valeur de référence |
|---|---|
| ΔV total du rapport | **5 959,197 m/s** |
| Masse finale (éphéméride) | 35 175,116 kg |
| Points d'éphéméride | **94 719** (complete = true) |
| Phases du rapport | 5 (`Vertical Ascent`, `Gravity turn`, `Transfert`, `Trim`, `Coasting`) |

Comptabilité par phase :

| Phase | massIn (kg) | massOut (kg) | Propergol (kg) | ΔV (m/s) |
|---|---|---|---|---|
| Vertical Ascent | 770 150,0 | 715 175,9 | 54 974,1 | 215,0 |
| Gravity turn | 715 175,9 | 36 178,7 | 612 997,2 | **5 648,2** |
| Transfert | 36 178,7 | 35 199,4 | 979,3 | 93,6 |
| Trim | 35 199,4 | 35 175,1 | 24,3 | 2,4 |
| Coasting | 35 175,1 | 35 175,1 | 0,0 | 0,0 |

Propergol par étage de la pile : `[0]` chargé 600 000 kg / consommé 600 000 kg /
résidu **0 kg** ; `[1]` chargé 100 000 kg / consommé 68 974,9 kg / résidu
31 025,1 kg ; `[2]` 0 kg.

> Le ΔV de la ligne `Gravity turn` (5 648,2 m/s) est celui que le découpage rend
> exact : il agrège aujourd'hui les deux burns sous le seul Isp de S1 (296 s).
> C'est la ligne qui se scindera en trois et dont la somme augmentera (§7.2 du
> document 01).

## 4. Baseline GEO (Falcon Heavy, parking 400 km → 35 786 km)

### 4.1 Critères N2

| Grandeur | Valeur de référence |
|---|---|
| `transitionTime` retenu (CMA-ES) | **329,529709 s** |
| `exponent` retenu (CMA-ES) | 0,177536 |
| Coût / évaluations | 8,6 × 10⁻⁵ / 2 203 |
| MECO — date depuis le décollage | **t + 336,529709 s** |
| MECO — masse | **64 463,304 kg** |
| MECO — position (m, GCRF) | −2 970 466,097849 ; −5 651 430,525919 ; 569 881,183242 |
| MECO — vitesse (m/s) | 7 056,973316 ; −3 729,500415 ; −198,078380 |
| Orbite finale — périgée | **35 784 683,4 m** |
| Orbite finale — apogée | **35 789 627,6 m** |
| Orbite finale — inclinaison | **0,000034°** |

MECO optimisation vs éphéméride : Δpos = 0,000 m, Δvel = 0,00000 m/s,
Δmasse = 0,000 kg.

### 4.2 Grandeurs qui vont bouger (référence « avant » de §7.2)

| Grandeur | Valeur de référence |
|---|---|
| ΔV total du rapport | **10 951,926 m/s** |
| Masse finale (éphéméride) | 2 470,425 kg |
| Points d'éphéméride | **206 152** (complete = true) |
| Phases du rapport | 10 |

Comptabilité par phase :

| Phase | massIn (kg) | massOut (kg) | Propergol (kg) | ΔV (m/s) |
|---|---|---|---|---|
| Vertical Ascent | 1 414 500,0 | 1 359 525,9 | 54 974,1 | 115,1 |
| Gravity turn | 1 359 525,9 | 64 463,3 | 1 229 062,6 | **6 803,5** |
| Parking | 64 463,3 | 62 262,1 | 2 201,2 | 118,6 |
| Coasting parking | 62 262,1 | 62 262,1 | 0,0 | 0,0 |
| GTO injection | 62 262,1 | 30 802,5 | 31 459,6 | 2 401,7 |
| S2 separation | 30 802,5 | 4 000,0 | 0,0 | 0,0 |
| Circularization | 4 000,0 | 2 614,0 | 1 386,0 | 1 335,0 |
| Trim | 2 614,0 | 2 473,1 | 140,9 | 173,9 |
| Plane trim | 2 473,1 | 2 469,8 | 3,3 | 4,2 |
| Coasting | 2 469,8 | 2 469,8 | 0,0 | 0,0 |

Propergol par étage : `[0]` chargé 1 233 000 kg / consommé 1 233 000 kg / résidu
**0 kg** ; `[1]` chargé 107 500 kg / consommé 84 697,5 kg / résidu **22 802,5 kg**
(largué avec S2, capturé par `captureJettisonedResidual`) ; `[2]` (AKM) chargé
2 000 kg / consommé 1 530,2 kg / résidu 469,8 kg.

> Le résidu de l'indice `[0]` est **0 kg par construction du modèle de masse**, pas
> par mesure : le largage S1 n'étant pas une `StageSeparationStage`, rien ne le
> mesure (S3 du document 01). Après le découpage, ce sera une mesure — c'est la
> case à surveiller à l'étape 4.

## 5. Référence à variables figées (sans optimiseur)

Profil Falcon Heavy GEO, entrée = état réel post-ascension verticale,
`variables = {stagingCompleteTime + 2 s ; 0,32}` — c'est-à-dire le cas serré qui
exerce la séquence complète au plus court : burn 1 jusqu'à la panne sèche, largage,
coast inter-étage de 2 s, puis un **burn 2 de 2 s** avant le MECO. Il ne reste que
4 s de marge au-dessus de `burn1Duration` (149,98 s), seuil sous lequel la
propagation s'arrêterait avant le tir du détecteur de largage — le mode de
défaillance de S1. Aucun optimiseur, aucune graine : ce relevé est
reproductible indépendamment de CMA-ES et c'est celui qui vérifie le calage des
dates du §4.3 du document 01.

| Grandeur | Valeur de référence |
|---|---|
| `burn1Duration` | **149,979660 s** |
| `stagingCompleteTime` (= burn 1 + coast inter-étage 2 s) | **151,979660 s** |
| `transitionTime` de l'essai | 153,979660 s |
| Sortie GT — date depuis l'entrée GT | 153,979660 s |
| Sortie GT — masse | **17 360,267 kg** |
| Sortie GT — position (m) | −3 949 439,705160 ; −5 013 991,353815 ; 589 230,976026 |
| Sortie GT — vitesse (m/s) | 6 221,798395 ; −5 155,808070 ; −48,249486 |

## 6. Tolérances de comparaison

Reprises telles quelles de §7.1 du document 01, appliquées par les deux tests :

| Grandeur | Seuil |
|---|---|
| Date MECO | < 1 ms |
| Masse MECO | < 1 kg |
| Position MECO | < 10 m |
| Vitesse MECO | < 0,05 m/s |
| Périgée / apogée finaux | écart relatif < 0,1 % |
| Inclinaison finale | < 0,01° |
| `transitionTime` retenu (graine 42) | < 0,5 s |

## 7. N3 — temps de calcul (surveillé, non assertif)

| Profil | Optimisation complète |
|---|---|
| LEO 400 km | **8,3 s** |
| GEO | **11,5 s** |

Ces durées sont journalisées et comparées (ratio affiché) mais **aucune assertion
ne porte dessus** : elles dépendent trop de la machine. Le critère de §7.1 (pas de
dégradation > 20 %) se lit à l'œil sur le ratio, sur la même machine.

## 8. Observations relevées au passage

### 8.1 Les deux passes coïncident aujourd'hui exactement au MECO

Sur les deux profils, l'état MECO de la passe d'optimisation et celui de la passe
éphéméride sont identiques (Δ = 0 exactement), et les deux fixtures de
`GravityTurnReplayConsistencyTest` qui pinnaient l'écart de modèle de gravité de
l'ascension verticale mesurent désormais Δpos = 0 (cf. §8.3).

**C'est précisément ce que le découpage met en jeu** : §5.4 du document 01 exige
qu'une seule séquence serve aux deux passes (`StageChainRunner`), faute de quoi
les deux passes ne verraient plus la même suite de redémarrages d'intégrateur.
Le relevé ci-dessus donne le point de comparaison : après chaque étape, l'écart
`MECO optimize↔ephemeris` du fichier `build/baseline/*.txt` doit **rester nul**,
pas seulement rester dans les tolérances N2.

### 8.2 Écart masse finale rapport vs éphéméride sur GEO

Le rapport de performance clôture à 2 469,8 kg (passe d'optimisation) alors que le
dernier point d'éphéméride vaut 2 470,425 kg : **0,6 kg d'écart**, en aval du
gravity turn (dans les stages analytiques). Antérieur au découpage, sans rapport
avec lui, mais à ne pas confondre avec une régression si on le redécouvre à
l'étape 4. Sur LEO l'écart est du même ordre (35 175,1 vs 35 175,116 kg).

### 8.3 Deux fixtures périmées retournées (fait, étape 0)

Sur `1d53e83`, deux fixtures de `GravityTurnReplayConsistencyTest` étaient rouges
**avant toute modification liée à ce chantier** : elles exigeaient un écart
point-masse vs 8×8 sur l'ascension verticale et mesuraient Δpos = 0,0. Elles
pinnaient la cause racine du bilan 11 §3.9 — l'ascension verticale volait en masse
ponctuelle côté optimisation et en 8×8 côté éphéméride. **Cette cause a depuis été
corrigée** : `ConstantThrustStage.propagateStandalone` construit désormais lui aussi
un `createOptimizationPropagator` (8×8). Les fixtures étaient périmées, pas cassées :
elles décrivaient un bug qui n'existe plus.

Elles ont été retournées pour tenir la fermeture au lieu du bug, javadoc historique
conservée :

| Avant | Après | Ce qui est tenu |
|---|---|---|
| `verticalAscentGravityModelMismatch_movesThePostAscentState` | `verticalAscent_fliesTheSameGravityModelInBothPasses` | Δpos = 0 (< 1e-6 m) et Δvel = 0 (< 1e-9 m/s) entre les deux passes ; rouge le jour où un propagateur masse-ponctuelle réapparaît sur le chemin d'optimisation |
| `differentPostAscentStates_makeTheGravityTurnExitDiverge_sameConfig` | `postAscentEntryDifference_isAmplifiedByTheGravityTurn` | L'amplification, désormais démontrée par une perturbation **injectée** de la magnitude historique (0,4 m / 0,1 m/s) : la sortie GT bouge de **15,3 m** |

Ce second point est aussi ce qui donne son sens aux tolérances N2 : les 10 m
autorisés au MECO ne sont pas du mou, c'est le budget d'une différence d'entrée
mille fois plus petite.

Les 5 fixtures de la classe sont vertes, y compris la nouvelle référence du §5.

## 9. Journal des relevés

À compléter à chaque étape de §8 du document 01 (1, 2, 3 et 5 exigent un relevé) :

| Étape | Date | Commit | LEO : écarts N2 | GEO : écarts N2 | Écarts §7.2 constatés |
|---|---|---|---|---|---|
| 0 — baseline | 2026-08-03 | `1d53e83` | référence | référence | — |
| 1 — `AscentPlan` + `StageChainRunner` | | | | | |
| 2 — découpage en 3 phases | | | | | |
| 3 — branchement LEO/GEO | | | | | |
| 5 — retrait de la pénalité d'étagement | | | | | |
