# MIS-7 — Mission Terre paramétrable · P1 : simulation et tests unitaires

> Statut : spec proposée, 2026-08-16.
> Fiche roadmap : [`docs/roadmap/01-roadmap.md`](../roadmap/01-roadmap.md) §6, `MIS-7` — ★4 ◆2 M.
> Découpage : **P1** (cette spec) = physique, modèle et TU, sans une ligne d'UI.
> **P2** = cartes du wizard, catalogue de sites, champs de paramètres.

---

## 0. Où passe la ligne P1 / P2

La couture existe déjà et elle est nette : `MissionFactory.specFromWizardValues()` traduit une
`Map<String, Object>` du wizard en `MissionSpec`, et `MissionComposer` traduit le spec en `Mission`.
**Tout ce qui est en aval du spec est P1 ; tout ce qui est en amont est P2.**

| | P1 (cette spec) | P2 |
|---|---|---|
| Modèle | `MissionSpec.EarthOrbit`, validation, règles de faisabilité | — |
| Composition | `MissionComposer` choisit la chaîne d'étages | — |
| Physique | pilotage du plan pendant l'ascension, azimut, budget propergol | — |
| Formules | inclinaison SSO, azimut corrigé, branche de nœud | — |
| Entrée | specs construits **à la main** dans les TU | cartes wizard, champs, catalogue de sites |
| Sortie | orbite atteinte vérifiée par assertion | affichage panneau détail |

Conséquence pratique : à la fin de P1, une orbite polaire ou SSO **vole** et est mesurée par un test,
mais reste inaccessible depuis l'application. C'est voulu — et c'est exactement la promesse de la
fiche roadmap (« les cartes wizard sont essentiellement de la saisie de paramètres »), à condition
que P1 la rende vraie plutôt que de la supposer.

---

## 1. L'état des lieux — ce qui existe déjà, et ce qui dort

L'inventaire est meilleur que ce que la fiche laisse croire sur un point, et bien pire sur un autre.

| Brique | Où | État |
|---|---|---|
| Formule d'azimut | `Physics.getLaunchAzimuth(lat, i)` | existe, deux bugs (§1.1) |
| Kick de tangage azimuté | `Physics.applyPitchKick(state, kick, azimut)` | existe, sans autorité (§2) |
| Ascension paramétrée | `AscentSequence.gravityTurn(profile, constraints, lat, i)` | **existe, jamais appelée** |
| Terme d'inclinaison dans le coût | `TransferProblem` (`errI`, `W_INC`) | existe et actif |
| Angle hors-plan CMA-ES | `beta1`, variable [3] de `TransferTwoManeuverProblem` | existe et actif |
| Visée de plan analytique | `AnalyticHohmannTransferStage`, `AnalyticTrimBurnStage`, `AnalyticApogeeCircularizationStage` | existe |
| Trim de plan au nœud | `AnalyticPlaneTrimAtNodeStage` | existe, utilisé par GEO |

Les deux surcharges de `AscentSequence.gravityTurn` racontent l'histoire : la version à quatre
arguments prend `launchLatitude` et `targetInclination`, la version à deux arguments appelle la
première avec `(0.0, 0.0)`, et **`LEOMission` comme `GEOMission` appellent la version à deux
arguments**. Le paramétrage a été câblé, jamais branché.

Partout ailleurs, l'inclinaison cible est écrite `FastMath.toRadians(latitude)` : le code ne demande
pas une inclinaison, il constate celle qu'un tir plein est donne gratuitement depuis Kourou.

### 1.1 Deux défauts dans `Physics.getLaunchAzimuth`, inertes aujourd'hui

```java
double result = FastMath.PI / 2;                       // 90° = plein est
if (launchLatitude != 0 && targetInclination != 0) {   // (a)
  result = FastMath.asin(FastMath.cos(targetInclination) / cosLat);  // (b)
}
```

**(a) La garde est fausse.** Un tir polaire depuis l'équateur (`lat = 0`, `i = 90°`) sort par la
branche « plein est » et renvoie 90°, alors que la réponse est 0° (plein nord). La condition doit
disparaître : la formule couvre déjà les deux cas, `cos(0)/cos(0) = 1 → 90°`.

**(b) Les unités se contredisent d'un fichier à l'autre.** `Physics` consomme des radians ;
les javadoc de `AscentSequence.gravityTurn` et de `GravityTurnFirstBurnStage` annoncent
`@param targetInclination the target orbit inclination (degrees)`. L'un des deux ment. Comme les
seuls appelants passent `0.0`, personne ne l'a vu.

Ces deux corrections sont gratuites et n'ont **aucun** effet sur les trajectoires actuelles, tous
les appelants passant par la branche `(0, 0)`. Elles sont dans P1 par hygiène, pas par nécessité.

---

## 2. La découverte : l'azimut n'a aucune autorité sur le plan orbital

C'est le point qui invalide l'estimation ◆2 de la fiche. La fiche dit « il ne manque que
`launchAzimuth` et `targetInclination` comme paramètres ». Le paramètre existe déjà — et **le passer
ne change pas le plan de l'orbite atteinte**.

### 2.1 Le kick ne tourne presque rien

`applyPitchKick` décompose la vitesse en radiale et tangentielle, puis **ne fait tourner que la
composante radiale** vers l'azimut demandé. La composante tangentielle — l'entraînement terrestre —
est reconduite telle quelle.

Ordres de grandeur, Falcon Heavy depuis Kourou (`AscentProfile(7,0 s ; 3° ; 2 s)`) :

| Quantité | Valeur |
|---|---|
| Poussée / poids au décollage | 22,8 MN / 13,84 MN = 1,65 |
| Accélération nette pendant la montée verticale | ≈ 6,4 m/s² |
| Vitesse radiale à la fin des 7 s | ≈ 45 m/s |
| Composante horizontale créée par le kick de 3° | ≈ **2,3 m/s** |
| Entraînement terrestre à 5,23° de latitude | ≈ **463 m/s** |
| Déviation de cap obtenue | ≈ **0,3°** |

Un azimut commandé à 0° (plein nord) déplace le cap réel de moins d'un tiers de degré.

### 2.2 Et ce qui suit le fige

`GravityTurnAttitudeProvider` interpole entre le zénith et `vTangential.normalize()` : la poussée
est en permanence dans le plan `(r, v)`. Un plan orbital n'est modifié que par une poussée
**hors** de ce plan — donc l'attitude actuelle est structurellement incapable de changer de plan.
Le plan est figé à l'instant du kick, et le kick le fixe à « plein est » quoi qu'on lui demande.

**L'inclinaison atteinte vaut donc toujours ≈ la latitude du site.** C'est cohérent avec le code
appelant, qui écrit `targetInclination = toRadians(latitude)` partout : ce n'est pas une convention,
c'est un constat.

### 2.3 Ce que coûterait de ne rien changer

Rattraper le plan après coup, à l'insertion : un changement de 85° (Kourou → polaire) sur une
orbite circulaire à 400 km coûte `2 · 7,67 · sin(42,5°) ≈ 10,4 km/s`. Hors de portée de n'importe
quel lanceur du catalogue. Le plan **doit** être établi pendant l'ascension.

### 2.4 Ce paragraphe est une hypothèse, et le premier test la falsifie

Tout ce qui précède est du raisonnement sur le code plus des ordres de grandeur analytiques, pas
une mesure. **Le premier travail de P1 est le test `T0` (§9), écrit avant toute modification** : il
mesure l'inclinaison atteinte pour un azimut commandé à 90° puis à 0°, sur le code actuel.

- écart < 1° → §2 est confirmé, §4 est nécessaire, on construit ;
- écart significatif → §4 tombe, MIS-7 redevient le « juste un paramètre » de la fiche, et cette
  spec est corrigée avant d'écrire la moindre ligne.

---

## 3. Le modèle

### 3.1 Ce que l'utilisateur donne : une inclinaison, pas un azimut

L'azimut est un moyen, l'inclinaison est l'intention. Deux azimuts donnent la même inclinaison
(`A` et `180° − A`, nœud ascendant ou descendant), et le bon azimut dépend de la latitude et de la
rotation terrestre — trois raisons de le dériver plutôt que de le demander.

```java
record LaunchPlane(double targetInclination, NodeBranch branch)   // simulation/mission/operation
enum NodeBranch { ASCENDING, DESCENDING }
```

`LaunchPlane` porte la dérivation, le choix de branche et la validation. C'est le seul endroit du
code où l'on écrit `asin(cos i / cos φ)`.

### 3.2 `MissionSpec.Leo` devient `MissionSpec.EarthOrbit`

L'interface scellée passe de `permits Leo, Geo` à `permits EarthOrbit, Geo`. Le record gagne deux
composants :

```java
record EarthOrbit(
    String name, LaunchConfiguration configuration,
    double perigeeAltitude, double apogeeAltitude,
    double targetInclination,          // rad — NOUVEAU
    NodeBranch nodeBranch,             // NOUVEAU
    String siteName, double latitude, double longitude, double altitude,
    MissionHorizon horizon)
```

Trois décisions à l'appui :

**L'excentricité reste implicite.** La fiche parle de `targetEccentricity` ; le couple
(périgée, apogée) la porte déjà, `MissionComposer` s'en sert pour distinguer circulaire et
elliptique (`CIRCULAR_TOLERANCE_M`). Ajouter un troisième paramètre redondant serait une source
d'incohérence, pas une généralisation.

**Aucun nouveau `MissionType` en P1.** `LEO` et `GEO` restent les deux valeurs. Polaire et SSO sont
des `EarthOrbit` avec une autre inclinaison — même chaîne d'étages, mêmes défauts d'horizon.
Le MEO est traité en §6.

**Le constructeur compact valide.** Une inclinaison inatteignable est refusée à la construction du
spec, pas découverte à la propagation (§8).

### 3.3 `LEOMission` devient `EarthOrbitMission`

Renommage, pas réécriture : la classe garde ses fabriques (`circularWithOptimizedTransfer`,
`ellipticWithOptimizedTransfer`, la chaîne analytique), en propageant `LaunchPlane` au lieu de
recalculer `toRadians(latitude)`. Douze appels dans le main, une trentaine dans les tests.

> **Attention au voisinage de noms.** `EarthMission` (abstraite, géométrie du pas de tir) existe
> déjà ; `EarthOrbitMission` en hérite. Les deux noms se ressemblent et désignent deux niveaux
> différents — la javadoc de la nouvelle classe doit l'énoncer.

Le point d'entrée historique reste disponible : une fabrique `dueEast(site)` construit le
`LaunchPlane` d'inclinaison égale à la latitude, qui est le comportement d'aujourd'hui.

---

## 4. Le pilotage du plan pendant l'ascension

### 4.1 Principe

Le plan cible est défini une fois, à la date du kick, à partir de la position du site `r̂₀` et de
l'azimut commandé `A` :

```
û_A = cos A · n̂ + sin A · ê          (direction horizontale commandée)
ĥ   = (r̂₀ × û_A).normalize()         (normale au plan cible)
```

`GravityTurnAttitudeProvider` interpole aujourd'hui `zénith → vTangential`. En mode plan commandé,
il interpole `zénith → û_h` avec :

```
û_h = (ĥ × r̂).normalize()            (prograde horizontal DANS le plan cible)
```

La différence tient en une ligne, et elle est essentielle : `vTangential` suit le plan **subi**,
`û_h` vise le plan **voulu**. La poussée acquiert une composante hors du plan courant tant que les
deux diffèrent — c'est ce qui donne enfin de l'autorité.

### 4.2 Règle de non-régression : plan non commandé ⇒ trajectoire identique au bit près

Même contrainte que celle imposée à `PHY-1` pour le drag, et pour la même raison : la calibration
Falcon Heavy / Ariane 62 tient sur des trajectoires mesurées, et une dérive silencieuse coûterait
plus cher que la feature.

`û_h` et `vTangential.normalize()` ne sont **pas** numériquement identiques même quand les plans
coïncident. Le mode plan commandé est donc **opt-in** : `GravityTurnAttitudeProvider` conserve son
constructeur et son code actuels, et le mode piloté est un second constructeur, choisi uniquement
quand le spec déclare un plan explicite. `T2` (§9) garde la porte.

### 4.3 Le résidu, et qui le nettoie

Piloter l'attitude ne garantit pas l'inclinaison au dixième de degré : la vitesse d'entraînement
initiale n'est pas dans le plan cible, et une poussée finie ne l'y ramène pas instantanément. Le
résidu attendu se corrige à deux niveaux, tous deux **déjà présents** :

1. `beta1`, l'angle hors-plan de la manœuvre de transfert, avec le terme `errI` du coût de
   `TransferProblem` qui le tire vers l'inclinaison cible ;
2. `AnalyticPlaneTrimAtNodeStage`, la brûlure hors-plan courte au nœud, écrite pour le résidu de
   ~0,25° de la circularisation GEO. Elle entre dans la composition `EarthOrbit` dès que le plan est
   commandé, et pas avant (elle coûte du propergol pour rien sur un tir plein est).

### 4.4 Ce qu'on ne fait pas : optimiser le plan

`GravityTurnProblem` reste **indifférent au plan**. Ses deux variables (`transitionTime`,
`exponent`) et ses contraintes (`GravityTurnConstraints` : apogée, vitesse tangentielle, pente)
sont scalaires et sans orientation. Le plan est *commandé* par l'attitude, pas *cherché* par
CMA-ES : la dimension du problème ne bouge pas, et la calibration de l'optimiseur non plus.

C'est la simplification qui rend P1 tenable. Elle a un prix, énoncé en §10.

---

## 5. SSO — l'inclinaison est calculée, pas saisie

```
cos i = − (a^{7/2} · n_prec) / (3/2 · J2 · Re² · √µ)      avec, pour e ≠ 0, a^{7/2} → a^{7/2}(1−e²)²
n_prec = 2π / 365,2422 j = 1,99106·10⁻⁷ rad/s
```

`Physics.sunSynchronousInclination(a, e)` porte la formule. Valeurs de référence (orbite
circulaire), qui servent de vecteurs de test à `T3` :

| Altitude | Inclinaison |
|---|---|
| 600 km | 97,79° |
| 700 km | 98,19° |
| 800 km | 98,61° |

Une SSO est donc, dans le modèle, un `EarthOrbit` circulaire dont l'inclinaison est dérivée de
l'altitude. **Aucun type de mission dédié, aucun objectif dédié** : le `SSOMissionObjective`
qu'évoquait `missions.md` n'a pas lieu d'être, l'objectif d'insertion existant porte déjà
(périgée, apogée, inclinaison).

Ce qui distingue vraiment une SSO d'une polaire, c'est la **précession du nœud**, qui n'est
vérifiable que par la propagation. D'où `T4` : mesurer la dérive du RAAN sur l'horizon de mission
(48 révolutions ≈ 3,2 j) et la comparer à 0,9856°/jour. Le propagateur par défaut est en 50×50, J2
est donc bien présent.

---

## 6. MEO — la fiche dit « gratuit », le catalogue dit non

Le MEO (GPS/Galileo : 20 200 km, i ≈ 55°) n'est pas un LEO à l'altitude plus haute. Il demande un
transfert de Hohmann long, et **la durée de ce transfert dépasse la capacité de coast de l'étage
supérieur du Falcon Heavy** :

| Grandeur | Valeur |
|---|---|
| Demi-grand axe du transfert (400 km → 20 200 km) | 16 678 km |
| Durée du transfert (demi-période) | **≈ 2 h 58 min** |
| Coast max, Falcon Heavy S2 (Merlin Vacuum) | **2 h 00** ❌ |
| Coast max, Ariane 62 S2 (ULPM/Vinci) | **6 h 00** ✅ |

Trois conséquences :

1. Le MEO se compose comme le GEO (parking → coast → injection → séparation → circularisation à
   l'apogée → trim → trim de plan), pas comme le LEO, avec `finalInclination = 55°` au lieu de 0.
   Toute la chaîne analytique existe.
2. Il est **réservé à Ariane 62**, ou délégué à un AKM comme le GEO. Ce n'est pas une limitation à
   contourner, c'est une propriété du catalogue qu'il faut faire respecter (`T6`).
3. Il n'est donc **pas gratuit** contrairement à ce qu'annonce la fiche roadmap. Il est en P1 comme
   **lot optionnel `P1c`**, coupable sans rien casser du reste.

### 6.1 Le choix de composition devient une règle explicite

`MissionComposer` choisit aujourd'hui entre circulaire et elliptique sur `CIRCULAR_TOLERANCE_M`.
Il gagne une seconde règle, du même type — une décision lisible plutôt qu'un nouveau
`MissionType` :

| Condition | Chaîne composée |
|---|---|
| Apogée cible atteignable dans le coast de l'étage supérieur | ascension + transfert direct (chaîne LEO actuelle) |
| Sinon, et charge utile avec AKM ou étage à long coast | chaîne parking + injection + circularisation (chaîne GEO) |
| Sinon | refus explicite, message nommant l'étage et la durée manquante |

C'est cette règle qui rend vraie la promesse « P2 = saisie de paramètres » : le wizard n'aura pas à
savoir quelle chaîne voler.

---

## 7. Le budget propergol — l'erreur la plus grosse de la liste

`PropellantBudget` crédite l'ascension de l'entraînement terrestre :

```java
- EQUATORIAL_ROTATION_MS * FastMath.cos(FastMath.toRadians(launchLatitudeDeg));   // 465 · cos φ
```

Correct pour un tir plein est, faux dès qu'on change d'azimut. Le crédit réel est **signé et projeté
sur l'azimut** :

```
assist = 465 · cos φ · sin A
```

| Mission depuis Kourou | Azimut | Crédit actuel | Crédit correct | Erreur |
|---|---|---|---|---|
| LEO plein est | 90° | +463 m/s | +463 m/s | 0 |
| Polaire (i = 90°) | 0° | +463 m/s | **0** | 463 m/s |
| SSO 700 km (i = 98,19°) | −8,2° | +463 m/s | **−66 m/s** | **529 m/s** |

Une erreur de 529 m/s sur un budget dimensionné par Tsiolkovsky inverse n'est pas un détail de
marge : c'est plusieurs tonnes d'écart sur le chargement de l'étage supérieur. Le commentaire de
`LEOMissionOptimizationTest` documente déjà l'effet d'une erreur de 140 m/s sur ce même terme
(charge S2 2 844 → 1 963 kg).

**Décision.** `loadsForLeo` prend l'azimut (ou le `LaunchPlane`) et applique l'assistance signée.
La perte de pilotage de §4.1 — le propergol dépensé à tourner le plan pendant la montée — n'est pas
analytique : elle est absorbée par `SAFETY_MARGIN` en P1, **mesurée** par `T1`, et reportée dans le
bilan. Si elle dépasse la marge, elle devient un terme explicite ; on ne devine pas sa valeur ici.

---

## 8. Faisabilité : refuser tôt et fort

Toutes ces règles vivent dans `LaunchPlane` / le constructeur compact de `MissionSpec.EarthOrbit`,
et lèvent une `OrbitlabException` nommant la valeur atteignable.

| Règle | Motif |
|---|---|
| `i < φ` ou `i > 180° − φ` | inatteignable sans changement de plan ; le message donne le minimum atteignable (= φ) |
| `cos φ ≈ 0` | azimut indéfini au pôle ; garde déjà présente dans `Physics`, à conserver |
| `i ∉ [0°, 180°]` | valeur hors domaine |
| apogée < périgée | déjà implicite, à rendre explicite |
| coast de l'étage < durée de transfert | §6, refus nommant l'étage |

Ces refus sont des exceptions et non des clamps : un paramètre irréalisable silencieusement corrigé
produit une mission qui vole autre chose que ce qui a été demandé — exactement le défaut que MIS-7
existe pour supprimer.

---

## 9. Les tests — le livrable de P1

`T0` est écrit **avant** toute modification. `T2` est la porte de non-régression et doit rester
verte à chaque étape.

| # | Test | Assertion | Tolérance |
|---|---|---|---|
| **T0** | `AscentAzimuthAuthorityTest` | inclinaison atteinte avec azimut commandé 90° puis 0°, sur le code actuel | mesure, pas de seuil — falsifie ou confirme §2 |
| **T1** | `AscentPlaneControlTest` | inclinaison atteinte en fin d'ascension pour i ∈ {28,5° ; 51,6° ; 90° ; 98,19°} depuis Kourou | ≤ 1° avant trim |
| **T1b** | idem, après insertion complète | inclinaison de l'orbite atteinte | ≤ 0,1° |
| **T2** | `EarthOrbitNonRegressionTest` | un spec plein est reproduit la trajectoire `LEOMission` actuelle (4 cas circulaires, 3 elliptiques) | éléments orbitaux identiques |
| **T3** | `SunSynchronousInclinationTest` | formule contre les trois valeurs de référence §5 | ± 0,02° |
| **T4** | `SunSynchronousPrecessionTest` | dérive du RAAN sur l'horizon de mission | 0,9856°/j, tolérance fixée sur la mesure `T1` |
| **T5** | `PolarCoverageTest` | latitude max de la trace au sol d'une orbite i = 90° | ≥ 89° |
| **T6** | `EarthOrbitValidationTest` | chaque règle §8 lève, avec le bon message | — |
| **T7** | `PropellantBudgetAzimuthTest` | assistance signée ; une SSO depuis Kourou est dimensionnée plus lourde qu'un LEO plein est de même altitude | signe et ordre |
| **T8** | `MeoMissionTest` *(P1c)* | insertion à 20 200 km, i = 55°, sur Ariane 62 | ± 7 % altitude, ± 0,5° inclinaison |

**La vraie porte de non-régression n'est pas dans ce tableau** : ce sont
`LEOMissionOptimizationTest`, `GEOMissionOptimizationTest`, `Ariane62MissionTest`,
`AscentBaselineN2Test` et `GravityTurnFloorProbeTest`, qui doivent rester verts **sans que leurs
tolérances soient touchées**. Une tolérance élargie pendant P1 est un échec de P1, pas un ajustement.

---

## 10. Risques

**Le plus sérieux : le gravity turn peut ne pas converger hors du plein est.** `GravityTurnConstraints`
dérive apogée, vitesse tangentielle minimale et fenêtre de pente de la seule altitude cible. Un tir
polaire perd les 463 m/s d'assistance et dépense en plus du propergol à tourner le plan : la même
vitesse tangentielle minimale devient plus difficile à tenir avec le même chargement. Symptôme
attendu : CMA-ES qui plafonne, ou insertion sous la cible. Réponse graduée — d'abord vérifier que le
budget corrigé (§7) suffit, ensuite seulement toucher aux contraintes, et en le documentant.

**L'inconnue mesurable : la perte de pilotage.** Elle n'est pas analytique et conditionne §7. `T1`
la donne ; tant qu'elle n'est pas mesurée, aucune valeur ne doit être écrite en dur.

**Le piège de calibration : `T2`.** La tentation, en refactorant `LEOMission` en `EarthOrbitMission`,
sera d'unifier au passage des chemins qui se ressemblent. Le mode plan commandé est opt-in
précisément pour éviter ça ; toute unification qui déplace une trajectoire plein est est hors
périmètre P1.

**Le débordement probable : `P1c` (MEO).** Chaîne différente, contrainte de lanceur, budget à
revalider. Il est isolé pour pouvoir être coupé sans toucher au reste.

---

## 11. Ordre d'exécution

| Lot | Contenu | Sortie |
|---|---|---|
| **P1.0** | `T0` sur le code actuel | confirme ou infirme §2 — **point de décision** |
| **P1.a** | `LaunchPlane`, `MissionSpec.EarthOrbit`, `EarthOrbitMission`, corrections §1.1, `T2`, `T6` | renommage livré, aucune trajectoire déplacée |
| **P1.b** | Attitude à plan commandé, trim de plan dans la composition, budget azimuté, `T1`, `T1b`, `T5`, `T7` | **polaire vole** |
| **P1.c** | `sunSynchronousInclination`, `T3`, `T4` | **SSO vole** |
| **P1.d** *(optionnel)* | Règle de composition §6.1, `T8` | **MEO vole** |

P2 démarre après `P1.b` sans attendre `P1.c` : à ce stade le spec est stable et le wizard a tout ce
qu'il lui faut.

---

## 12. Ce que P1 ne fait pas

Cartes wizard polaire / SSO / MEO ; catalogue de sites de lancement (P1 passe des coordonnées à la
main, `siteName` reste nullable comme aujourd'hui) ; champs de saisie d'inclinaison et affichage de
l'inclinaison SSO dérivée ; choix de branche de nœud dans l'UI ; nouveaux `MissionType` et leurs
défauts d'horizon ; libellés et validation côté formulaire.

Et hors des deux phases : les fenêtres de lancement (`MIS-2`, qui consomme ce que P1 produit), le
RAAN cible, et le drag (`PHY-1`) — une SSO réelle décroît, la nôtre non.
