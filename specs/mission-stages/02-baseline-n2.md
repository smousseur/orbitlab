# Baseline N2 — référence de non-régression avant le découpage des séparations

> **Statut** : relevés de non-régression du plan de migration de
> [`01-separations-implicites.md`](01-separations-implicites.md) §8. §3 à §7
> tiennent la référence **pré-découpage** (étape 0, commit `1d53e83`) ; le
> journal §9 suit chaque étape, et §11–§12 consignent les deux endroits où le
> plan a dû être corrigé par la mesure.
> **Date des relevés** : 2026-08-03. **Graine CMA-ES** : 42 (`TEST_SEED`).
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

**Re-relevé après un changement de comportement assumé** : remettre la constante
`LEO_400_BASELINE` / `GEO_BASELINE` concernée à `null` dans
`AscentBaselineN2Test`, relancer — le test passe en mode capture, journalise le
relevé sans comparer — puis recopier les nouvelles valeurs et **documenter
l'écart** dans le journal §9. Les constantes actuelles datent de l'étape 3
(cf. §9.3 pour la justification du re-relevé).

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
| **`date MECO − transitionTime`** (cf. §11) | < 1 ms |
| Masse MECO | < 1 kg |
| Position MECO | < 10 m |
| Vitesse MECO | < 0,05 m/s |
| Périgée / apogée finaux | écart relatif < 0,1 % |
| Inclinaison finale | < 0,01° |
| `transitionTime` retenu (graine 42) | < 0,5 s |

La première ligne remplace « date MECO < 1 ms », qui ne pouvait pas coexister
avec la dernière — voir §11. Les trois lignes de masse/position/vitesse MECO ne
sont comparables que **tant que les variables retenues bougent peu** ; la fidélité
du découpage à variables figées est tenue séparément, par
`GravityTurnReplayConsistencyTest`.

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
| 1 — `AscentPlan` + `StageChainRunner` | 2026-08-03 | `2ebcfa6` | **0 sur toutes les grandeurs** | **0 sur toutes les grandeurs** | **aucun** (ΔV total, points d'éphéméride et comptabilité par phase inchangés) |
| 2 — découpage en 3 phases | 2026-08-03 | *(ce commit)* | non exercé (missions encore sur `GravityTurnStage`) | idem | **aucun** — les missions ne volent pas encore la chaîne |
| 3 — branchement LEO/GEO | 2026-08-03 | *(ce commit)* | orbite finale ≤ 1 m ; `transitionTime` +0,159559 s (budget 0,5) ; **MECO opt↔éph nul** | orbite finale ≤ 0,5 m ; `transitionTime` +0,030238 s ; **MECO opt↔éph nul** | tous ceux de §7.2, aucun autre — détail en §9.3 |
| 4 — comptabilité | 2026-08-03 | *(ce commit)* | — (pas de relevé requis) | — | constatés = attendus, cf. §9.3 |
| 5 — retrait de la pénalité d'étagement | 2026-08-03 | *(ce commit)* | retrait **mesuré puis annulé** — baseline étape 3 inchangée | idem | aucun (retour à l'état étape 3/4) |
| 6 — nettoyage des séparations fantômes | 2026-08-03 | *(ce commit)* | **identique chiffre pour chiffre** | **identique chiffre pour chiffre** | aucun |

### 9.1 Étape 1 — relevé du 2026-08-03

Le refactor de l'étape 1 est **interne** : la liste des stages ne bouge pas, donc
l'intégrateur ne subit aucun redémarrage supplémentaire et le résultat n'est pas
seulement « dans les tolérances », il est identique. Les deux relevés reproduisent
la baseline sur **toutes** les décimales imprimées — MECO (date, masse, position,
vitesse), orbite finale, `transitionTime` et `exponent` retenus, coût, masse
finale, et jusqu'aux grandeurs que §7.2 annonce comme *devant* bouger plus tard :

| Grandeur | LEO 400 km | GEO |
|---|---|---|
| ΔV total du rapport | 5 959,197 m/s (= baseline) | 10 951,926 m/s (= baseline) |
| Points d'éphéméride | 94 719, complete (= baseline) | 206 152, complete (= baseline) |
| Comptabilité par phase | identique ligne à ligne | identique ligne à ligne |

C'est attendu : le ΔV agrégé sous le seul Isp de S1 et les résidus non mesurés
restent tels quels tant que le gravity turn est **une** phase. Ces cases bougeront
à l'étape 2, pas avant.

Le critère de §8.1 est tenu : `MECO optimize↔ephemeris` vaut **Δpos = 0,000 m,
Δvel = 0,00000 m/s, Δmasse = 0,000 kg** sur les deux profils — nul, pas seulement
dans les tolérances. C'est ce que le passage du générateur d'éphéméride par
`StageChainRunner` devait préserver.

**N3** : LEO 7,2 s (baseline 8,3 s), GEO 9,4 s (baseline 11,5 s) — aucune
dégradation ; l'écart est du bruit machine, pas un gain à revendiquer.

**Une seule grandeur bouge** : le nombre d'évaluations CMA-ES (LEO 2 063 → 2 079,
GEO 2 203 → 2 185), alors que le coût et les variables retenues sont identiques.
Ce compteur n'est asserté nulle part et n'a pas d'effet sur la trajectoire ; il
varie avec l'ordonnancement de l'exploration parallèle. À surveiller uniquement
s'il devait dériver d'un ordre de grandeur.

### 9.2 Étape 2 — relevé du 2026-08-03

L'étape 2 construit les trois phases (`GravityTurnFirstBurnStage`,
`StageSeparationStage`, `GravityTurnSecondBurnStage`), la fabrique
`AscentSequence`, `advancesByReplay()` et le câblage de la passe d'optimisation
sur `StageChainRunner` — **sans brancher les missions**, qui volent encore
`GravityTurnStage`. C'est le découpage exigé par §8 du document 01 (« chaque
étape est committable et vérifiable seule ») : `LEOMission`/`GEOMission` basculent
à l'étape 3, et c'est là que N1/N2 seront réellement exercés. Les tests
d'optimisation ne sont donc **pas** requis ici, conformément à l'objectif de
minimiser les points de contrôle qui les exigent.

Ce que l'étape 2 mesure à la place, à variables figées (§5, aucun optimiseur),
sur le profil Falcon Heavy GEO — c'est-à-dire exactement le cas serré qui exerce
burn 1 jusqu'à la panne sèche, le largage, le coast de 2 s et un burn 2 de 2 s :

| Grandeur | Écart chaîne 3 phases vs baseline pré-découpage | Seuil N2 |
|---|---|---|
| Date MECO | 0 s (t + 153,979660 s) | < 1 ms |
| Masse MECO | 0 kg (17 360,267 kg) | < 1 kg |
| Position MECO | **0,001 m** | < 10 m |
| Vitesse MECO | **0,00018 m/s** | < 0,05 m/s |

Trois ordres de grandeur sous le budget : le redémarrage de l'intégrateur aux
deux nouvelles frontières coûte un millimètre. C'est la contrepartie du calage
exact des dates par `AscentPlan` (§4.3 du document 01) — les phases ne
recalculent rien, elles lisent.

Et le critère de §8.1, transposé à la chaîne : **passe d'optimisation et passe
éphéméride donnent Δpos = 0, Δvel = 0, Δmasse = 0** — identiques au bit, parce
que les deux empruntent le même `StageChainRunner` sur les mêmes trois phases.
C'est la propriété que §5.4 du document 01 exigeait ; elle est désormais tenue
par construction et non par coïncidence.

Fixtures : `GravityTurnReplayConsistencyTest#threePhaseAscent_reproducesThePreSplitBaseline`
et `#threePhaseAscent_optimizeAndReplayFlyTheSameChain`, plus `AscentSequenceTest`
pour la structure (largage = phase d'index attendu 0 entre les deux burns, et une
phase de burn sans plan refuse de se configurer).

**Écarts au document 01 assumés à cette étape :**

1. **La phase de séparation ne porte pas le fournisseur d'attitude du gravity
   turn** (§5.3 le suggérait). `StageSeparationStage` reste inchangée, comme §10
   le demande par ailleurs ; le coast est non propulsif et le propagateur
   d'optimisation n'a aucune force dépendant de l'attitude, donc l'effet
   dynamique est nul. Les 0,001 m mesurés ci-dessus le confirment.
2. **Le max step de la phase de séparation** reste le défaut `COAST_MAX_STEP`
   (§5.5 laissait le point « à vérifier à l'étape 2 »). Sur 2 s de coast,
   l'intégrateur adaptatif borne de toute façon son pas à l'intervalle restant :
   le paramètre est sans effet observable. Les deux burns, eux, portent bien
   `AscentPlan.maxStepSeconds()`.
3. **`GravityTurnProblem` reçoit sa façon de voler un candidat par injection**
   (`AscentPropagation`) au lieu de passer inconditionnellement par le runner.
   C'est ce qui permet aux missions de rester sur l'ancien chemin pendant que le
   nouveau est construit et testé — donc de ne pas changer structure et
   trajectoire dans le même commit. L'injection disparaîtra avec
   `GravityTurnStage` à l'étape 3.

**Deux corrections de sûreté nécessaires au câblage** (iso-comportement sur le
chemin actuel, indispensables sur le nouveau) :

- `MissionOptimizer` relève l'`entryState` **avant** `optimize()` et le restaure
  après. Un problème qui vole de vraies phases avance l'état de la mission
  partagée — et le fait depuis les fils d'exploration parallèles de CMA-ES —,
  donc `mission.getCurrentState()` n'est plus l'entrée du stage au retour.
- `StageSeparationStage.configure` mesure son coast depuis l'état initial du
  propagateur au lieu de `mission.getCurrentState()`. Même valeur (le runner
  pose les deux), mais l'une est privée au vol en cours.
- `StageChainRunner.plain()` interrompt la chaîne sur échec et rend l'état
  d'entrée, au lieu de lire l'état de la mission : c'est le contrat de pénalité
  dont dépendent les fonctions de coût (un candidat qui n'a pas avancé est noté
  comme échoué), et cela évite une lecture concurrente. Le mode `sampling()`
  (éphéméride) garde son comportement historique de trajectoire partielle.

### 9.3 Étape 3 — bascule des missions (relevé N2 à faire)

Les **quatre** sites de construction sont passés sur `AscentSequence` :
`LEOMission.buildStages` (Hohmann analytique), `LEOMission
.circularWithOptimizedTransfer`, `LEOMission.ellipticWithOptimizedTransfer` et
`GEOMission.buildStages`. `GravityTurnStage` est **supprimée**.

Séquences résultantes :

```
LEO : Vertical Ascent → Gravity turn (S1) → S1 separation → Gravity turn (S2)
      → Transfert → Trim → Coasting

GEO : Vertical Ascent → Gravity turn (S1) → S1 separation → Gravity turn (S2)
      → Parking → Coasting parking → GTO injection → S2 separation
      → Circularization → Trim → Plane trim → Coasting
```

GEO gagne la symétrie annoncée en §4.2 du document 01 : `S1 separation` et
`S2 separation` sont deux instances de la même classe, d'index attendu 0 et 1.

`MissionAscentWiringTest` (rapide, sans propagation) tient la propriété que les
fixtures numériques ne peuvent pas tenir : **tous** les profils volent bien les
trois phases, pas seulement celui qu'un test exerce. Un profil resté sur un
gravity turn monolithique optimiserait et volerait sans rien signaler.

Le chemin pré-découpage n'est pas supprimé pour autant :
`GravityTurnManeuver.configure`/`propagateForOptimization` restent la référence
numérique du §5 ci-dessus — aucune mission ne les emprunte, mais c'est contre
elles que la non-régression est définie, et l'étape 5 a encore un changement de
comportement à mesurer depuis ce point.

#### Relevé du 2026-08-03

**Le critère de §8.1 est tenu sur les deux profils** : `MECO optimisation ↔
éphéméride` = Δpos 0,000 m, Δvel 0,00000 m/s, Δmasse 0,000 kg. Nul, pas
seulement dans les tolérances — c'est ce que le passage des deux passes par le
même `StageChainRunner` devait garantir, et c'était le signal prioritaire.

**L'orbite finale — ce sur quoi la mission est jugée — ne bouge pas :**

| | périgée | apogée | inclinaison |
|---|---|---|---|
| LEO 400 km | Δ 0,1 m (2,6 × 10⁻⁷) | Δ 1,0 m (2,4 × 10⁻⁶) | Δ 7 × 10⁻⁶ ° |
| GEO | Δ 0,5 m (1,4 × 10⁻⁸) | **identique** | **identique** |

Pour des budgets de 0,1 % et 0,01°.

**Le `transitionTime` retenu bouge, dans son budget** : +0,159559 s (LEO) et
+0,030238 s (GEO), pour 0,5 s autorisées. Les deux runs convergent ~500 sous le
coût acceptable (8,8 × 10⁻⁵ contre 4,8 × 10⁻²) : CMA-ES retient donc le **premier
candidat assez bon**, pas un optimum unique, et tout déplacement millimétrique du
paysage change lequel c'est. Ce n'est pas un optimum dégradé — le coût est le
même — c'est un arrêt anticipé ailleurs dans un bassin plat.

**Le critère « date MECO » de §7.1 était contradictoire avec lui-même** et a été
corrigé (voir §11). `date MECO = décollage + ascension verticale + transitionTime`
algébriquement : exiger 1 ms sur la date revient à exiger 1 ms sur une variable à
laquelle le même tableau accorde 0,5 s. Le relevé le montre au chiffre près —
tout l'écart de date **est** l'écart de `transitionTime` :

| | Δ `transitionTime` | Δ date MECO | Δ masse MECO | Δt × débit S2 (287,46 kg/s) |
|---|---|---|---|---|
| LEO | 0,159559 s | **0,159559 s** | 45,865 kg | **45,87 kg** |
| GEO | 0,030238 s | **0,030238 s** | 8,692 kg | **8,69 kg** |

Il ne reste rien à expliquer : le burn 2 a tourné plus longtemps parce que
l'optimiseur a demandé un turn plus long. La fidélité du découpage lui-même se
lit **à variables figées**, où elle vaut 0,001 m (§9.2).

**Écarts de §7.2, tous constatés :**

| Grandeur | LEO 400 km | GEO |
|---|---|---|
| ΔV total du rapport | 5 959,197 → **8 088,485 m/s** (+35,7 %) | 10 951,926 → **11 983,542** (+9,4 %) |
| dont ligne(s) d'ascension | 5 648,2 → 4 167,9 + 3 612,8 = **7 780,7** | 6 803,5 → 5 845,1 + 1 990,7 = **7 835,8** |
| Résidu étage `[0]` | 0,0 kg, désormais **mesuré** | 0,0 kg, désormais **mesuré** |
| Phases du rapport | 5 → **7** | 10 → **12** |
| Points d'éphéméride | 94 719 → **94 723**, complete | 206 152 → **206 153**, complete |
| Masse finale | 35 175,116 → 35 163,757 kg | 2 470,425 → 2 470,362 kg |
| N3 (optimisation complète) | 8,3 → **6,7 s** | 11,5 → **9,2 s** |

Le saut de ΔV est **plus gros que ce que §7.2 laissait attendre**, et pour une
raison qui mérite d'être écrite : ce n'était pas seulement « le burn 2 crédité à
296 s au lieu de 348 ». Tsiolkovsky n'est pas additif à travers une chute de
masse — l'ancienne ligne unique appliquait une équation qui n'a pas de sens sur
un intervalle enjambant un largage de 66 t, et le résultat n'était pas « un peu
bas », il était hors sujet. Les deux lignes actuelles sont le ΔV étagé correct.

Le largage devient aussi lisible directement dans le rapport : `S1 separation`
affiche 170 150 → 104 150 kg (LEO) et 181 500 → 115 500 kg (GEO), soit les
66 000 kg de masse sèche de S1 du catalogue, et `[0]` sort à 600 000 / 600 000 /
0 kg — burn 1 va bien jusqu'à la panne sèche.

### 9.5 Étape 6 — les séparations fantômes (2026-08-03)

Quatre sites résolvaient un « étage 2 » depuis la masse prédite après leur
premier burn, comme si un étagement pouvait survenir en cours de phase :
`AnalyticParkingInsertionStage` (plan + `addBurns`) et
`AnalyticHohmannTransferStage` (idem). Ils utilisent désormais l'étage déjà
résolu pour le burn 1, avec un commentaire renvoyant à l'invariant.

L'invariant est écrit à sa source, sur `VehicleStack.resolveActiveStage` :
**l'étage actif ne change que par largage explicite**. Une combustion ne peut pas
le faire, si longue soit-elle : un étage cesse de pousser à son plancher de
déplétion, `dryMass_i + massAbove[i]`, strictement au-dessus du seuil
`massAbove[i]` qui déclenche le changement — strictement, tant que l'étage a une
masse sèche non nulle, ce qui est le cas de tous les étages catalogués.

**Vérification.** Le relevé N2 après ce nettoyage reproduit celui de l'étape 3
**chiffre pour chiffre** sur les deux profils : `transitionTime`, MECO,
orbite finale, points d'éphéméride, et jusqu'au ΔV total (LEO 8 088,485 m/s,
GEO 11 983,542 m/s). C'est la confirmation empirique de l'invariant : si `stage2`
avait jamais différé de `stage1`, la propulsion du second burn aurait changé et
ces chiffres auraient bougé.

Le code était donc bien « mort-né mais trompeur », comme S4 l'annonçait — aucun
chemin d'exécution, mais une affirmation fausse sur le modèle qu'un lecteur
pouvait prendre pour argent comptant.

## 10. Marche à suivre du relevé d'étape 3

```bash
./gradlew test --tests "*AscentBaselineN2Test" -Dorbitlab.slowTests=true
```

Puis, pour les critères N1 :

```bash
./gradlew test --tests "*LEOMissionOptimizationTest" --tests "*GEOMissionOptimizationTest" --tests "*LEOMissionOptimizedTransferTest" --tests "*PropellantLoadOptimizerIntegrationTest" -Dorbitlab.slowTests=true
```

### 10.1 Ce qui doit rester dans les clous (rouge = régression)

`AscentBaselineN2Test` assertent exactement ces grandeurs, sur les deux profils :

| Grandeur | Seuil |
|---|---|
| MECO passe d'optimisation — date / masse / position / vitesse | 1 ms / 1 kg / 10 m / 0,05 m/s |
| MECO passe éphéméride — idem | idem |
| Périgée et apogée finaux | < 0,1 % relatif |
| Inclinaison finale | < 0,01° |
| `transitionTime` retenu (graine 42) | < 0,5 s |

Plus le critère de §8.1, à lire dans `build/baseline/*.txt` : l'écart
**MECO optimisation ↔ éphéméride doit rester nul**, pas seulement dans les
tolérances. C'est ce que le passage des deux passes par le même
`StageChainRunner` garantit ; s'il devient non nul, les deux passes ont recommencé
à diverger et c'est le vrai signal d'alarme, avant même les seuils.

### 10.2 Ce qui doit bouger (vert = attendu, à consigner en étape 4)

Ces grandeurs sont écrites dans `build/baseline/*.txt` mais **assertées nulle
part** — précisément pour que le découpage puisse les faire bouger :

1. **ΔV total du rapport, en hausse.** La ligne `Gravity turn` se scinde en trois
   et le burn 2 est enfin crédité à l'Isp de S2 (348 s) au lieu de celui de S1
   (296 s). Références « avant » : LEO **5 959,197 m/s**, GEO **10 951,926 m/s**.
2. **Résidu de l'étage `[0]`, désormais mesuré.** `captureJettisonedResidual` se
   déclenche sur `S1 separation`, qui est maintenant une vraie
   `StageSeparationStage`. La valeur restera proche de 0 kg tant que burn 1 tourne
   jusqu'à la panne sèche — mais c'est une mesure, plus une conséquence du modèle
   de masse (S3 du document 01, fermé).
3. **Trois lignes de rapport au lieu d'une** pour l'ascension, et trois noms de
   phase en télémétrie. Références « avant » : 5 phases sur LEO, 10 sur GEO →
   7 et 12.
4. **Deux points d'éphéméride de plus par frontière de phase.** Références
   « avant » : LEO **94 719**, GEO **206 152**. Les points ajoutés sont
   co-localisés dans le temps, le rendu de trajectoire n'en est pas affecté.

### 10.3 `PropellantLoadOptimizerIntegrationTest` — non vérifié, coût à établir

**Le seul point du chantier resté sans mesure.** Cette classe (4 boucles de
dimensionnement de charge, chacune enchaînant des optimisations de mission
complètes) est opt-in et se documente elle-même à « ~15 min par boucle », soit
**~1 h attendue**. Elle n'a été exécutée à aucun moment pendant les étapes 2 à 5 :
les runs de vérification rapides ne passaient pas `-Dorbitlab.slowTests=true`.

Un run lancé après l'étape 5 a été **interrompu à 4 h 16** sans qu'aucune classe
n'ait terminé — soit 4× le coût annoncé. On ne peut donc conclure ni dans un sens
ni dans l'autre :

- ce peut être son coût normal sur cette machine (la documentation « ~15 min »
  n'est pas datée, et plusieurs démons Gradle tournaient en parallèle) ;
- ce peut être un effet du découpage, à travers la boucle de dimensionnement qui
  rejoue des missions entières.

Rien dans les mesures faites ne suggère une régression — une optimisation de
mission unitaire est *plus rapide* qu'avant (LEO 8,3 → 6,7 s, GEO 11,5 → 9,2 s) —
mais l'absence de signal n'est pas une preuve. **À établir par un run dédié**,
idéalement chronométré aussi sur `1d53e83` pour avoir le point de comparaison :

```bash
./gradlew test --tests "*PropellantLoadOptimizerIntegrationTest" -Dorbitlab.slowTests=true
```

Ne pas l'inclure dans un filtre par paquet avec `-Dorbitlab.slowTests=true` : le
motif `com.smousseur.orbitlab.simulation.mission.runtime.*` l'attrape.

### 10.4 Deux fixtures N1 déjà rouges *avant* le chantier

À ne pas attribuer au découpage. Les deux échouent à l'identique sur `1d53e83`
(le commit de référence de l'étape 0, avant toute modification de ce chantier),
vérifié en relançant `LEOMissionOptimizationTest` sur cet état :

| Fixture | Message | `1d53e83` | après étape 3 |
|---|---|---|---|
| `testEllipticMissions(200 km, 1 000 km)` | `Min coast altitude 183 094 m not within 14 000 m of target 200 000 m` | rouge, 183 094 m | rouge, **183 094 m** (inchangé) |
| `testFalconHeavyBudgetLoads` | `S2 residual … exceeds 15% of its sized load 2 844 kg` | rouge, 1 045 kg | rouge, **1 037 kg** |

Les 7 autres fixtures de la classe sont vertes. La bonne lecture de ces deux-là
après l'étape 3 est donc **« même échec, même ordre de grandeur »**, pas
« vert » : le premier reproduit le chiffre au mètre près, le second bouge de 8 kg
(0,8 %), ce qui est l'ordre de grandeur attendu d'une boucle de dimensionnement
re-jouée sur une trajectoire décalée de quelques millimètres.

Elles pointent deux sujets réels et distincts du découpage — une insertion
elliptique haute qui rate son périgée de 17 km, et un étage supérieur
sur-dimensionné de 21 % sur le profil budgété. À traiter pour eux-mêmes, hors de
ce chantier.

### 10.5 Ce qui invaliderait le découpage

- un `S1 separation` qui **lève** au lieu de larguer : le message dirait quel
  étage est actif et combien de propergol reste à bord. Cela voudrait dire que
  burn 1 ne consomme plus S1 comme prévu — pas un bug du découpage, mais un
  dimensionnement de charge qui ne tient plus ;
- une éphéméride marquée `complete = false` : une phase s'est arrêtée avant son
  échéance, en pratique le garde-fou de déplétion sur un burn à sec ;
- `MECO optimisation ↔ éphéméride` non nul (cf. §10.1).

### 9.4 Étape 4 — comptabilité (2026-08-03)

Les écarts de §7.2 sont constatés et chiffrés en §9.3 : ils correspondent aux
écarts attendus, et il n'y en a pas d'autre.

**Aucun test ne figeait un `ΔV total`.** Vérifié : la seule lecture de
`MissionPerformanceReport.totalDeltaV()` hors journalisation est celle
d'`AscentBaselineN2Test`, qui l'écrit dans son relevé sans l'asserter — c'est
précisément pour cela que la grandeur pouvait bouger. Les assertions de
`LEOMissionOptimizationTest` et `PropellantLoadOptimizerIntegrationTest` portent
sur des **résidus de masse**, pas sur des ΔV, et ne sont donc pas concernées.
L'étape 4 n'a donc rien eu à ajuster côté tests.

Ce qu'elle a corrigé, c'est une **affirmation devenue fausse dans le code** :
`MissionOptimizer.buildStagePerformance` annonçait en javadoc que « le ΔV utilise
l'Isp de l'étage d'entrée, une approximation pour les stages enjambant un
largage ». Plus aucun stage n'enjambe un largage : chaque largage est sa propre
phase non-propulsive, donc l'Isp d'entrée est le seul Isp brûlé et le calcul est
**exact**, pas approché. La javadoc dit désormais cela, avec le chiffre qui le
justifie (5 648 → 7 781 m/s sur l'ascension LEO) et l'avertissement qu'un futur
stage larguant de la masse en cours de combustion réintroduirait silencieusement
l'erreur. Même famille que S4 : une ligne de code qui écrivait quelque chose de
faux sur le modèle.

## 11. Correction d'un critère N2 contradictoire (étape 3)

Le tableau de §7.1 du document 01 exigeait simultanément :

- date MECO à **1 ms** près ;
- `transitionTime` retenu à **0,5 s** près.

Or `date MECO = décollage + durée d'ascension verticale + transitionTime`. La
première exigence est donc la seconde, 500 fois plus stricte : les deux ne
peuvent pas être vraies ensemble. Tant que le refactor ne touchait pas le
paysage de coût (étapes 1 et 2), CMA-ES retombait sur les mêmes variables et la
contradiction restait invisible. Elle est apparue à l'étape 3, la première où les
missions volent réellement la chaîne découpée.

**Ce qui remplace le critère.** La date MECO n'est plus comparée directement ;
c'est `date MECO − transitionTime` qui l'est, à 1 ms. Cette quantité vaut la
durée d'ascension verticale (7,000000 s sur les quatre relevés, avant et après,
LEO et GEO) et teste exactement ce que le découpage pourrait casser : **que
l'ascension se termine au MECO qu'on lui a demandé**. Elle est insensible à
l'endroit où CMA-ES s'arrête.

Un largage manqué, une phase qui déborde, un burn 2 qui s'allume au mauvais
moment déplaceraient tous cette différence. Un optimiseur qui s'arrête ailleurs,
non. C'est le critère que §7.1 aurait dû écrire.

**Ce qui reste fragile, et pourquoi c'est acceptable.** Masse, position et
vitesse au MECO sont encore comparées en absolu, donc encore sensibles au
déplacement des variables. On les garde parce qu'elles attrapent une régression
franche, et les messages d'échec indiquent désormais de combien
`transitionTime` a bougé et quelle part de l'écart cela explique — un écart
entièrement expliqué appelle un re-relevé, pas une enquête. La fidélité du
découpage à variables **figées**, elle, est tenue ailleurs et sans optimiseur :
`GravityTurnReplayConsistencyTest#threePhaseAscent_reproducesThePreSplitBaseline`,
0,001 m et 0,00018 m/s.

**Les valeurs de référence ont été re-relevées** dans `AscentBaselineN2Test`
(§9.3 pour les chiffres et la justification). Les valeurs pré-découpage restent
lisibles en §3 et §4 de ce fichier : c'est contre elles que l'étape 5 devra
mesurer son changement de comportement assumé.

## 12. Étape 5 — le retrait de la pénalité d'étagement, mesuré puis annulé

§6 du document 01 annonçait deux garde-fous devenus sans objet une fois le
largage transformé en phase, et demandait de les retirer dans un commit isolé
« avec re-run des tests d'optimisation ». Le retrait a été fait, mesuré, et
**annulé sur la foi de la mesure**. C'est le résultat de l'étape 5.

### 12.1 Ce que le retrait a produit

Deux exécutions de `AscentBaselineN2Test` après retrait, comparées à l'état
étape 3 :

| Grandeur | avant retrait | après retrait (2 runs) |
|---|---|---|
| LEO `transitionTime` | 308,011551 s (× 2, **bit-reproductible**) | 307,735618 / 307,742711 — **non reproductible** |
| LEO évaluations | 2 311 | 3 416 / 3 384 (**+47 %**) |
| LEO temps d'optimisation | 6,7 / 6,8 s | 10,7 / 10,7 s (**+57 %**) |
| LEO coût | 8,8 × 10⁻⁵ | 8,5 × 10⁻⁵ |
| GEO (tout) | — | inchangé, reproductible |

Invariants tenus dans les deux cas : `MECO optimisation ↔ éphéméride` = **0** sur
les deux profils, et orbite finale inchangée (LEO périgée Δ 0,2 m, apogée
Δ 0,8 m, inclinaison Δ 1,6 × 10⁻⁵ ° ; GEO périgée Δ 0,2 m, apogée et inclinaison
identiques).

Le critère N3 de §7.1 — « pas de dégradation > 20 % » — est donc **franchi sur
LEO** (+57 %), pour un gain de trajectoire nul.

### 12.2 Pourquoi

Sous le plancher d'étagement, `transitionTime` **ne contrôle plus rien** : tous
les candidats y volent la même ascension et se terminent au même coast de
largage. La région est un plateau de solutions équivalentes et médiocres.

La pénalité de 1e3 les rendait derniers sans ambiguïté. Sans elle ils
deviennent « recevables mais mauvais », et CMA-ES y dépense du budget. Pire, la
perte de reproductibilité vient de l'arrêt anticipé inter-runs
(`CMAESTrajectoryOptimizer`, `crossRunStop` : le premier run à passer sous le
coût acceptable interrompt les autres). Avec plusieurs runs devenus compétitifs,
lequel gagne dépend de l'ordonnancement des threads — donc la solution retenue
aussi.

### 12.3 Ce qui a été conservé, et avec quelle justification

Les deux garde-fous sont restés, **avec une raison d'être différente** :

- **La pénalité de `GravityTurnProblem`** n'est plus un garde-fou de sûreté —
  elle **régularise le paysage de recherche**. Le commentaire du code le dit
  ainsi désormais, avec le chiffre qui le justifie. C'est un renversement
  d'argumentaire, pas un statu quo : le §6 du document 01 avait raison sur le
  fond (le mode de défaillance a disparu) et tort sur la conclusion (il ne
  s'ensuit pas qu'il faille retirer le terme).
- **L'exception de `GravityTurnFirstBurnStage`** devient une assertion qui ne
  doit jamais tirer. La pénalité garantit que les solutions retenues sont
  au-dessus du plancher ; y arriver signifie qu'un plan vient d'ailleurs
  (résultat injecté à la main, replay d'une optimisation périmée, appelant
  contournant le problème). La situation est dégénérée plutôt que dangereuse,
  mais la voler en silence masquerait sa provenance.

### 12.4 Ce que l'épisode dit du chantier

C'est le seul point du plan où la mesure a contredit la spécification, et il
vaut d'être retenu : **« ce garde-fou ne garde plus rien » ne suffit pas à
conclure « il faut le retirer »**. Un terme de coût introduit pour une raison
peut en servir une autre, et seul le re-run le dit. C'est exactement ce que la
procédure de §8 du document 01 — commit isolé, re-run complet, écart documenté —
était censée permettre de constater ; elle a fonctionné.
