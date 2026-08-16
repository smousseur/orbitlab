# MIS-7 — Mission Terre paramétrable · P1 : simulation et tests unitaires

> Statut : spec proposée, 2026-08-16. **`T0` exécuté le 2026-08-16 : §2 est confirmé, la
> construction peut commencer** (`AscentAzimuthAuthorityTest`, 6 fixtures vertes — mesures reportées
> en §2 et §9).
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
| Formule d'azimut | `Physics.getLaunchAzimuth(lat, i)` | existe, deux défauts (§1.1a, §1.1b) |
| Kick de tangage azimuté | `Physics.applyPitchKick(state, kick, azimut)` | existe, sans autorité (§2), base est/ouest inversée (§1.1c) |
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

### 1.1 Trois défauts dans le chemin de l'azimut, invisibles aujourd'hui

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

**(c) `applyPitchKick` a l'est et l'ouest inversés.** Trouvé par `T0`, pas par lecture. La base
horizontale locale y est construite `east = zenith × north`, qui vaut **l'ouest** : à l'équateur
`r̂ = x̂`, `n̂ = ẑ`, et `x̂ × ẑ = −ŷ` alors que l'est géographique est `+ŷ = ẑ × r̂`.

Les azimuts du kick sont donc comptés **dans le sens trigonométrique** depuis le nord : 0° = nord et
180° = sud restent justes (le miroir est sur le seul axe est-ouest), mais **90° pointe plein
ouest**. L'azimut standard que renvoie `getLaunchAzimuth` est donc mirroré à la seconde où on le
donne au kick : `A → −A`.

Mesure `T0.5` : un kick commandé plein est imprime −2,456 m/s le long de l'est géographique, sur
2,457 m/s d'impulsion totale. C'est bien l'ouest, à 0,001 m/s près.

(a) et (b) n'ont **aucun** effet sur les trajectoires actuelles : tous les appelants passent par
`(0, 0)`, où ces deux branches ne sont pas empruntées. Ils sont dans P1 par hygiène.

**(c) est un prérequis dur de §4** — sans la correction, un plan commandé volerait le miroir de tout
plan prograde incliné — et c'est le seul des trois qui mord aujourd'hui.

### 1.1.1 Corriger (c) est une recalibration, pas une correction de bug

`T0.6` la chiffre sans toucher au code de production, en se servant du miroir lui-même : puisque la
base a l'est et l'ouest inversés, commander −90° aujourd'hui produit exactement le kick qu'une base
correcte produirait pour une commande plein est. On vole les deux et on différencie.

| Écart à MECO entre la trajectoire volée et sa version corrigée | Mesuré | Tolérance N2 |
|---|---|---|
| Position | **1 964,5 m** | 10 m |
| Vitesse | **4,6235 m/s** | 0,05 m/s |
| Inclinaison | 0,0000° | — |

196 fois la tolérance de position, 92 fois celle de vitesse. Les références figées par
`AscentBaselineN2Test` et `GravityTurnReplayConsistencyTest` **bougeront**. L'inclinaison, elle, ne
bouge pas : le miroir est symétrique par rapport au méridien du site, il change le nœud et pas le
plan — ce qui est précisément pourquoi aucune assertion existante ne l'a jamais vu.

**Décision.** (c) est corrigé dans un **pas dédié et isolé** (`P1.a-bis`, §11), dont le seul contenu
est la correction de la base et le ré-enregistrement des références, à **tolérances inchangées**.
Deux choses à ne pas confondre, et §9 en dépend : ré-enregistrer une valeur de référence mesurée
après un changement assumé est légitime et se documente ; élargir une tolérance pour faire passer un
test est l'échec que §9 proscrit.

> **L'alternative écartée.** On pouvait laisser la base fausse et nier l'azimut (`A → −A`) à
> l'entrée du kick : zéro trajectoire déplacée, zéro référence à refaire. Rejeté — cela fige une
> convention fausse dans le code au moment précis où MIS-7 la rend structurante, et le prochain
> lecteur de `applyPitchKick` n'aurait aucun moyen de savoir laquelle des deux erreurs compense
> l'autre.

---

## 2. La découverte : l'azimut n'a aucune autorité sur le plan orbital

C'est le point qui invalide l'estimation ◆2 de la fiche. La fiche dit « il ne manque que
`launchAzimuth` et `targetInclination` comme paramètres ». Le paramètre existe déjà — et **le passer
ne change pas le plan de l'orbite atteinte**.

### 2.1 Le kick ne tourne presque rien

`applyPitchKick` décompose la vitesse en radiale et tangentielle, puis **ne fait tourner que la
composante radiale** vers l'azimut demandé. La composante tangentielle — l'entraînement terrestre —
est reconduite telle quelle.

Falcon Heavy depuis Kourou (`AscentProfile(7,0 s ; 3° ; 2 s)`). Colonne « estimé » = le calcul de
coin de table qui a motivé `T0` ; colonne « mesuré » = `T0.1` et `T0.5`, 2026-08-16.

| Quantité | Estimé | Mesuré |
|---|---|---|
| Vitesse à l'entrée du gravity turn | ≈ 465 m/s | **465,5 m/s** |
| Entraînement terrestre, composante est | ≈ 463 m/s | **463,2 m/s** |
| Impulsion horizontale créée par le kick de 3° | ≈ 2,3 m/s | **2,457 m/s** |
| Déviation de cap entre azimut 90° et 0° | ≈ 0,3° | **0,304°** |
| Écart d'inclinaison correspondant | — | **0,0022°** |

Un azimut commandé à 0° (plein nord) déplace le cap réel de trois dixièmes de degré, et
l'inclinaison de deux millièmes.

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

### 2.4 Ce paragraphe était une hypothèse — `T0` l'a mesurée

Tout ce qui précède était du raisonnement sur le code plus des ordres de grandeur, pas une mesure.
`AscentAzimuthAuthorityTest` (§9) l'a mesuré sur le code actuel, à variables fixes, sans optimiseur,
l'azimut étant la seule différence entre deux runs. Inclinaison atteinte à MECO :

| Azimut commandé | Atteignable si autorité | Obtenu | Autorité |
|---|---|---|---|
| 90° — est, ce que volent les missions | 5,23° | 5,2964° | référence |
| 0° — nord, commande polaire | 90,00° | 5,2971° | **0,00 %** |
| 180° — sud, branche descendante | 90,00° | 5,3131° | **0,02 %** |
| −8,22° — SSO 700 km | 98,19° | 5,2970° | **0,00 %** |

Et par la surcharge paramétrée d'`AscentSequence`, chaîne à trois phases complète (`T0.3`) : 0,0007°
d'écart pour une commande polaire.

**§2 est confirmé : l'autorité est nulle à la résolution de la mesure.** §4 est donc nécessaire, et
MIS-7 n'est pas le changement « juste un paramètre » de la fiche roadmap.

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

### 3.4 Dans quel repère l'inclinaison est-elle exprimée ?

Question sans objet tant qu'on ne visait rien ; elle en a un dès qu'on vise. **Aujourd'hui la
réponse est « GCRF », par accident** : c'est le repère des états propagés, et l'inclinaison lue
l'est donc aussi.

L'ennui est que l'équateur GCRF est celui de J2000, pas celui de la date. Mesuré au 2026-01-01 : le
pôle ITRF vu depuis GCRF est incliné de **0,1454°**. Une mission dont la géométrie de lancement est
rigoureusement identique voit donc son inclinaison GCRF varier avec la date de lancement, sur une
plage crête-à-crête de 0,29°, uniquement parce que son nœud tourne sous un équateur de référence
décalé.

Vérifié : deux lancements à 6 h d'écart (90,286° de RAAN) donnent **0,2060°** d'écart d'inclinaison,
contre `0,1454 · √2 = 0,2056°` prédits ; à un jour sidéral d'écart, l'écart retombe à 0,0000°. Rien
n'a bougé physiquement — c'est le repère de lecture qui a tourné.

| Cible | Sensibilité à 0,145° d'erreur de repère |
|---|---|
| LEO, GEO | invisible : rien ne visait l'inclinaison, la latitude servait de valeur constatée |
| Polaire | invisible : 0,145° sur 90°, sans effet fonctionnel |
| **SSO** | **1,8 % d'erreur sur la précession du nœud, soit ~6°/an de dérive** |

**Décision.** `LaunchPlane` déclare son repère, et l'inclinaison visée comme celle mesurée sont
exprimées dans le **même**. Le choix (équateur de la date, ou GCRF assumé avec la correction portée
par la formule SSO) se tranche en `P1.c`, avec `T4` — la précession mesurée — comme arbitre : c'est
la seule des deux lectures qui a un sens physique observable. Jusque-là, `T3` vérifie la formule,
pas le repère : ses ±0,02° portent sur l'arithmétique de `sunSynchronousInclination`, et il serait
malhonnête de les lire comme une précision d'inclinaison volée tant que §3.4 n'est pas tranché.

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

> **`û_A` se construit sur la même base locale `(n̂, ê)` que le kick — donc le défaut §1.1c doit
> être corrigé d'abord.** Tant que `ê` vaut l'ouest, un plan commandé à azimut 45° serait volé à
> −45° : plan miroir, inclinaison correcte, nœud à l'opposé. L'erreur ne se verrait sur aucune
> assertion d'inclinaison — d'où l'ordre imposé en §11 (§1.1c en P1.a, autorité en P1.b) et le
> contrôle de signe dans `T1`.

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

`T0` est écrit **avant** toute modification — c'est fait. `T2` est la porte de non-régression et doit
rester verte à chaque étape.

### 9.1 `T0` — livré et vert (2026-08-16)

`AscentAzimuthAuthorityTest`, six fixtures, variables fixes, aucun optimiseur : le seul paramètre
qui varie d'un run à l'autre est l'azimut commandé.

| Fixture | Ce qu'elle mesure | Résultat |
|---|---|---|
| `T0.1` | le kick seul, sans propagation (§2.1) | 0,304° de cap, 0,0022° d'inclinaison pour 90° commandés |
| `T0.2` | le gravity turn entier jusqu'à MECO (§2.2) | **≤ 0,02 % d'autorité** sur quatre azimuts |
| `T0.3` | la même question via la surcharge paramétrée d'`AscentSequence` | 0,0007° pour une commande polaire |
| `T0.4` | les deux défauts de `getLaunchAzimuth` (§1.1a, §1.1b) | confirmés |
| `T0.5` | la base horizontale locale du kick (§1.1c) | est/ouest inversés, −2,456 m/s sur 2,457 |
| `T0.6` | le prix de la correction de §1.1c sur les références N2 | 1 964,5 m et 4,6235 m/s à MECO |

**Ces fixtures sont écrites pour mourir.** Elles caractérisent l'ascension telle qu'elle vole
*aujourd'hui* : `T0.1`–`T0.3` passent au rouge quand P1.b donne l'autorité, `T0.4` et `T0.5` quand
P1.a et P1.a-bis corrigent les défauts. Le rouge est le signal de fin d'étape, et chaque fixture le
dit dans son message d'échec — elles sont alors supprimées, remplacées par `T1`. `T0.6` n'assère
rien : elle produit un chiffre pour §1.1.1 et disparaît avec P1.a-bis.

### 9.2 Le reste de P1

| # | Test | Assertion | Tolérance |
|---|---|---|---|
| **T1** | `AscentPlaneControlTest` | inclinaison atteinte en fin d'ascension pour i ∈ {28,5° ; 51,6° ; 90° ; 98,19°} depuis Kourou, **et signe du nœud** (§4.1) | ≤ 1° avant trim |
| **T1b** | idem, après insertion complète | inclinaison de l'orbite atteinte | ≤ 0,1° |
| **T2** | `EarthOrbitNonRegressionTest` | un spec plein est reproduit la trajectoire `LEOMission` actuelle (4 cas circulaires, 3 elliptiques) | éléments orbitaux identiques |
| **T3** | `SunSynchronousInclinationTest` | formule contre les trois valeurs de référence §5 — arithmétique seule, pas le repère (§3.4) | ± 0,02° |
| **T4** | `SunSynchronousPrecessionTest` | dérive du RAAN sur l'horizon de mission — **arbitre du repère de §3.4** | 0,9856°/j, tolérance fixée sur la mesure `T1` |
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
| **P1.0** ✔ | `T0` sur le code actuel | **fait** — §2 confirmé, autorité ≤ 0,02 % |
| **P1.a** | `LaunchPlane`, `MissionSpec.EarthOrbit`, `EarthOrbitMission`, corrections §1.1a/b, `T2`, `T6` | renommage livré, aucune trajectoire déplacée |
| **P1.a-bis** | correction §1.1c seule + ré-enregistrement des références N2, tolérances inchangées | 1 964 m / 4,62 m/s de déplacement assumé et documenté |
| **P1.b** | Attitude à plan commandé, trim de plan dans la composition, budget azimuté, `T1`, `T1b`, `T5`, `T7` | **polaire vole** |
| **P1.c** | `sunSynchronousInclination`, arbitrage du repère (§3.4), `T3`, `T4` | **SSO vole** |
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
