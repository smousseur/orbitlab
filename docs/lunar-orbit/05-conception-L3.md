# MIS-5 / L3 — L'orbiteur au catalogue et son budget

Lot **L3** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur la baseline de
[`02-baseline-L0.md`](02-baseline-L0.md). Il rend vraie **une** propriété :

> **Un orbiteur lunaire propulsé existe au catalogue, et se dimensionne.**

Rien d'autre. Le lot est **additif** : il n'ôte aucune ligne d'un chemin existant, ne change aucune
trajectoire et ne compose aucune mission. Ce qu'il ajoute est une donnée de catalogue, une forme
close et un type de mission dont deux consommateurs sur quatre refusent encore de le traiter.

`L3` précède `L4`, qui volera les étages sur cette donnée, et `L5`, qui composera la mission.

**Quatre faits mesurés contredisent le découpage.** Ils sont au §1.2, et deux d'entre eux changent
ce que le lot livre : la forme du record, et le nombre de sites à remplir.

---

## 1. Inventaire mesuré

### 1.1 Ce que le lot touche

| Fichier | Taille | Ce qui bouge |
|---|---|---|
| [`Payloads`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/catalog/Payloads.java) | 127 l. | un modèle de plus, un cas de `domainOf` |
| [`MissionType`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/MissionType.java) | 45 l. | une constante |
| [`PropellantBudget`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/PropellantBudget.java) | 373 l. | un record, une méthode publique, deux formes closes, deux constantes |
| [`MissionHorizon`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/MissionHorizon.java) | 279 l. | une constante, un cas de `defaultFor` |
| [`MissionFactory`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/MissionFactory.java) | 320 l. | un refus explicite |
| [`ScenarioMapper`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/scenario/ScenarioMapper.java) | 290 l. | un refus explicite |

Six fichiers, dont quatre d'une à trois lignes. Aucun n'est un chemin de vol.

### 1.2 Quatre faits que le découpage ne connaît pas

**1. Aucune constante calibrée n'est nécessaire : le patched-conic naïf suffit.** Le `v∞` d'arrivée
se dérive du **même Hohmann** que `translunarInjectionDeltaV` calcule déjà — la vitesse du véhicule
à l'apogée du transfert vers `LUNAR_DISTANCE_M`, retranchée de la vitesse circulaire de la Lune :

```
v_apogée du transfert =   189,57 m/s
v_Lune                =  1 018,30 m/s
v∞ (naïf)             =    828,74 m/s     [L0 mesuré : 825,8 – 872,5, moyenne 850,1]
```

Le Δv d'insertion qui en découle, contre les 819,6 – 835,9 m/s que `L0` §3 a mesurés à 100 km :

| altitude | Δv de LOI |
|---|---|
| 50 km | 828,3 |
| **100 km** | **820,8** — 0,9 % sous la moyenne mesurée, 1,8 % sous le pire cas |
| 200 km | 806,7 |
| 500 km | 771,2 |

L'écart est du même ordre que les 1,3 % que la classe **assume déjà** sur
`translunarInjectionDeltaV`, et il est absorbé cinq fois par la marge de 10 %. Et l'étape
`v_hyp = √(v∞² + 2µM/r)` est **exacte** : nourrie des `v∞` mesurés de `L0`, elle rend 819,8 / 828,1 /
835,9 contre 819,6 / 828,1 / 835,9. Toute l'incertitude est dans `v∞`, et nulle part ailleurs.

**2. La classe ne devient pas contextuelle.** Le découpage écrit « `MU` et `RE` y sont terrestres »
comme si c'était le défaut à réparer. Ils sont **justes** : l'ascension et le TLI sont géocentriques,
et ce sont quatre cinquièmes du Δv de la mission. Seule la LOI a besoin de µM et RM.
`PropellantBudget` gagne donc **deux constantes lunaires à côté des terrestres**, ce qui n'est pas la
même chose qu'un paramètre de contexte. Son propre commentaire — « *It becomes contextual when a
mission has to be sized around another body* » — vise un cas qui n'arrive pas : cette mission est
dimensionnée autour de la Terre pour son ascension et son injection, et autour de la Lune pour une
combustion.

**3. Deux composantes ne suffisent pas, il en faut trois.** Le découpage écrit
« `loadsForLunarOrbit` rend `(launcherLoads, insertionLoad)` façon `GeoLoads` ». Mais
[`LunarLaunchWindowPlanner:90`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/window/problem/LunarLaunchWindowPlanner.java)
a besoin de `massAtInjection` pour bâtir son `LunarLaunchWindowProblem`, et le §4 / `L7` branche la
mission d'orbite lunaire sur cette fenêtre existante. `LunarLoads` porte déjà ce champ ; `GeoLoads`
n'a rien d'équivalent, parce qu'une GEO n'a pas de fenêtre à confirmer. La forme est donc
`(launcherLoads, massAtInjection, insertionLoad)`.

**4. Quatre switches exhaustifs cassent, pas deux.** Le découpage en nomme deux — `Payloads.domainOf`
et `MissionHorizon.defaultFor`. La lecture en trouve deux de plus, tous deux sans `default` :
`MissionFactory.specFromWizardValues:88` et `ScenarioMapper:76`. Le second est scellé « *sur la même
hiérarchie que `MissionSpec`* », son javadoc disant en toutes lettres que « *compilation fails until
the matching record exists here* » — c'est un dispositif voulu, pas un oubli. (`PlanningPage:331` n'y
fait qu'une allusion en javadoc et `MissionProfileTest:150` switche sur `MissionProfile`, avec un
`default`.)

### 1.3 Ce qui existe déjà et qu'on n'écrira pas

- **Les deux constantes lunaires.** Orekit publie `Constants.MOON_EQUATORIAL_RADIUS = 1 737 400,0` —
  **bit pour bit** ce que `PlanetRadius.radiusFor(MOON)`, et donc
  `GravitationalContext.moon().equatorialRadius()`, rendent — et `Constants.JPL_SSD_MOON_GM`, qui
  diffère du `getGM()` du propagateur de **4·10⁻⁷ en relatif** : mesuré, le Δv de LOI vaut 820,77
  m/s des deux côtés, l'écart étant sous le millimètre par seconde.
- **Le mécanisme de refus au wizard.** `MissionWizardWidget:281-293` compose **à blanc** et
  transforme toute `RuntimeException` en refus affiché, formulé par son message. Rien à écrire pour
  qu'un refus de budget soit présenté comme un refus.
- **La chaîne de dimensionnement.** `sizeTopStage` et son point fixe, la marge, l'écrêtage à
  capacité et l'ordre « la charge utile d'abord, le lanceur ensuite » que `loadsForGeo` établit.

---

## 2. L'orbiteur au catalogue

```java
public static final PayloadModel LUNAR_ORBITER =
    new PayloadModel("LUNAR_ORBITER", "Lunar orbiter",
        2_000, 800, new PropulsionSystem(320, 5_500),
        new AerodynamicProperties(4.0, 2.2), PayloadDomain.LUNAR);
```

### 2.1 D'où vient chaque nombre

**2 000 kg à sec, Isp 320** : la configuration sur laquelle `L0` §3 a recalculé son tableau, et celle
des deux autres modèles à deux tonnes du catalogue. Rien n'est gagné à en changer, et beaucoup à
garder les chiffres de `L0` lisibles dans ceux du lot.

**5 500 N**, et c'est le seul chiffre rond qui atteint **les deux** cibles de `L0` à la fois :

| poussée | 50 km (plancher) | 100 km (défaut) | a₀ |
|---|---|---|---|
| 5 000 N | 5,59 % | 5,31 % | 1,88 m/s² |
| **5 500 N** | **5,08 %** | **4,83 %** | **2,06 m/s²** |
| 6 000 N | 4,66 % | 4,42 % | 2,26 m/s² |
| 400 N — l'AKM du catalogue | 69,9 % | **66,4 %** | 0,15 m/s² |

Les pourcentages sont ceux d'une révolution lunaire à l'altitude visée. `a₀ = 2,06 m/s²` est
exactement la valeur que `L0` §3 désignait comme cible, entre Apollo (2,03) et Chang'e-3 (1,98). Et
les 66,4 % de l'AKM actuel confirment par une autre route les 67 % que `L0` annonçait : c'est bien
un moteur de plus qu'il faut, écrit comme une donnée de catalogue.

**800 kg de capacité.** La charge d'insertion vaut 658 kg à 100 km et 664 kg au plancher de la
bande. Ce qui décide du reste est la marge de masse sèche que le wizard laisse avant refus :

| capacité | masse sèche maximale saisissable |
|---|---|
| 700 kg | 2 129 kg — **+6,4 %** |
| **800 kg** | **2 433 kg — +21,6 %** |
| 900 kg | 2 737 kg — +36,8 % |

Le voisin le plus proche, `GEO_SAT`, offre 2 347 kg pour 2 000 kg à sec, soit **+17,4 %**. 800 kg
est le chiffre rond qui s'en approche par le haut.

**La section aérodynamique** reprend le bus supposé de la sonde (2,0 × 2,0 m), donnant
B = 302 kg/m² à la masse au décollage — entre les 291 de `GEO_SAT` et les 227 de la sonde, sans y avoir
été ajustée, comme la note de tête de `Payloads` l'exige. Elle est **pratiquement décorative** : une
charge utile n'est l'étage actif qu'après largage, donc dans le vide. Le catalogue la déclare
pour chaque modèle, et une exception coûterait plus qu'elle ne dirait.

### 2.2 Un effet de bord assumé, et il rend un test rouge

`LUNAR_ORBITER` étant de domaine `LUNAR`, `Payloads.forMissionType(LUNAR_FLYBY)` l'offre **aussi** :
un survol n'exige pas de propulsion, donc il n'en exclut pas. C'est cohérent avec ce que le javadoc
de `MissionType.LEO` dit déjà mot pour mot — « *an AKM-equipped payload simply flies with an empty
tank* ». Un utilisateur peut envoyer un orbiteur en survol, réservoir plein et moteur éteint, et
c'est physiquement ce qui se passerait.

**`PayloadsTest.forMissionType_lunarFlyby_keepsTheProbeAndTheUniversalModule:67` passe donc au
rouge.** Il se réécrit, il ne se contourne pas : ajouter un troisième axe d'éligibilité pour
l'empêcher coûterait un mécanisme entier pour interdire quelque chose de licite.

Dans l'autre sens, `forMissionType(LUNAR_ORBIT)` rend **exactement un modèle**, les trois autres
étant refusés pour trois raisons différentes : `CARGO_MODULE` est universel mais inerte,
`LUNAR_PROBE` est lunaire mais inerte, `GEO_SAT` est propulsé mais terrestre. C'est plus fort que
« la sonde refusée » que le découpage demandait, et le test du §6 le dit ainsi.

---

## 3. Le Δv d'insertion en forme close

Deux méthodes de paquet, dans la forme des cinq que la classe porte déjà :

```java
static double lunarArrivalExcessVelocity(double parkingAltitude)
static double lunarInsertionDeltaV(double parkingAltitude, double lunarOrbitAltitude)
```

La première rend le `v∞` du §1.2 pt 1 ; la seconde `√(v∞² + 2µM/r) − √(µM/r)` au rayon
`RM + lunarOrbitAltitude`.

**Une forme close et non une constante**, pour la raison que `translunarInjectionDeltaV` écrit déjà
pour elle-même : l'altitude d'orbite lunaire est un champ du wizard (§4 / `L7`), et une constante en
figerait une valeur dans une classe dont tout l'idiome est la forme close. Ici l'argument est même
plus fort qu'ailleurs, parce que **le Δv décroît avec l'altitude** : geler 100 km sous-estimerait de
6 % une orbite à 500 km et surestimerait le plancher.

### 3.1 D'où viennent µM et RM

De `org.orekit.utils.Constants`, ni d'un littéral, ni de `GravitationalContext`. Trois raisons :

1. **La classe lit des constantes, pas des contextes.** Elle prend déjà `Constants.WGS84_EARTH_MU` et
   non `GravitationalContext.earth().mu()`, bien que ce soit le même double — et son commentaire dit
   pourquoi : le dimensionnement est hors vol et ne voit jamais d'arc.
2. **`MOON_EQUATORIAL_RADIUS` est l'identité** relevée au §1.3 : la même valeur, au bit près, que le
   contexte lunaire porte.
3. **Un `static final` sur `GravitationalContext.moon()` serait un piège documenté.** Le javadoc de
   `earth()` le décrit : une résolution à l'initialisation de classe, possiblement avant
   `OrekitService.initialize()`, laissant la classe définitivement inutilisable.

L'écart de `JPL_SSD_MOON_GM` au µ que le propagateur intègre est de 4·10⁻⁷ en relatif, soit moins
d'un millimètre par seconde sur le Δv. Il est écrit ici pour qu'il ne soit pas redécouvert comme une
incohérence.

### 3.2 La marge ne change pas, et c'est une décision

`SAFETY_MARGIN` reste à 10 %. `L0` §3 a prévenu que la mesure du Δv est **impulsionnelle**, et que
`MIS-4 / L6` a trouvé la perte de poussée finie 1,7 à 5 fois l'estimation en sinc — sur une
combustion qui dure 5 % d'une révolution, ce terme n'est pas nul et il n'est pas ici.

En inventer une seconde marge maintenant serait poser un chiffre avant de l'avoir mesuré, ce que la
classe refuse déjà en toutes lettres pour la perte de pilotage de l'ascension : « *No value is
hard-coded until it is measured.* » **C'est `L4` qui mesurera si les 10 % suffisent**, sur une
hyperbole fabriquée en test — et c'est le seul chiffre de ce lot qui reste une hypothèse.

---

## 4. `loadsForLunarOrbit`

```java
public record LunarOrbitLoads(
    double[] launcherLoads, double massAtInjection, double insertionLoad) {}

public static LunarOrbitLoads loadsForLunarOrbit(
    LauncherModel launcher,
    PayloadModel payload,
    double payloadDryMass,
    double parkingAltitude,
    double lunarOrbitAltitude,
    double launchLatitudeDeg,
    double launchAzimuth)
```

Trois composantes (§1.2 pt 3). La signature prend un `PayloadModel` et une masse sèche, comme
`loadsForHighOrbit`, et non un `Spacecraft` comme `loadsForLunar` : la charge d'insertion doit être
**calculée** avant que la charge utile puisse être instanciée avec elle.

### 4.1 L'ordre du calcul, et ce qu'une inversion casserait

Celui de `loadsForGeo` : la charge d'insertion **d'abord**, depuis la masse sèche et le Δv de LOI ;
puis les charges lanceur sur `payloadDryMass + insertionLoad`. Inverser dimensionnerait le lanceur
pour une charge utile privée de ses 658 kg d'ergols — un quart de sa masse — **sans rien lever** :
les charges resteraient plausibles et la mission partirait sous-alimentée. C'est ce que le test 6 du
§6 pince.

### 4.2 Le refus, contre la politique des voisins

```java
if (raw > payload.akmPropellantCapacity()) throw new IllegalArgumentException(...)
```

`loadsForHighOrbit` écrête (`min(raw, capacity)`), et le javadoc de la classe en fait une politique :
« *loads are clamped to capacity (an infeasibility diagnostic is a later increment)* ». **Ce lot en
dévie, et l'asymétrie est physique** : une GEO écrêtée rend une orbite basse — fausse, visible,
mais une orbite ; une LOI écrêtée ne capture pas, et la sonde passe à côté de la Lune. Il n'y a pas
de mission dégradée à montrer.

Le refus est sûr à trois titres, tous vérifiés :

- il sort à la **composition à blanc** du wizard, qui le présentera comme un refus (§1.3) ;
- `PropellantBudget` n'est appelé ni depuis `runtime/` ni depuis `optimizer/` — un `RuntimeException`
  levé là ne peut donc pas être avalé en « lambda infaisable » par `MissionLoadEvaluator` ;
- la méthode est neuve et n'est sur le chemin d'aucune mission existante.

Le message nomme les deux nombres, la charge requise et la capacité : c'est ce que l'utilisateur
lira.

### 4.3 Les deux lanceurs portent l'orbiteur, et c'est Ariane qui contraint

Mesuré, en dimensionnant le lanceur pour la masse au TLI :

| charge utile au TLI | Falcon Heavy S2 | Ariane 62 S2 |
|---|---|---|
| 2 000 kg — la sonde de `MIS-4` | 12 518 / 107 500 — 12 % | 22 286 / 31 000 — 72 % |
| **2 658 kg — l'orbiteur** | 14 394 / 107 500 — 13 % | **25 110 / 31 000 — 81 %** |
| 3 500 kg | 16 976 / 107 500 — 16 % | 28 976 / 31 000 — 93 % |

**Le plafond du réservoir mord avant celui du lanceur.** La capacité de 800 kg refuse au-delà de
2 433 kg à sec, soit 3 233 kg au TLI, où Ariane 62 est encore sous 90 %. Le refus que l'utilisateur
verra parlera donc de l'orbiteur, jamais d'Ariane — ce qui est le bon message, puisque c'est le
moteur d'insertion qui est le facteur limitant de cette mission.

---

## 5. Les quatre switches : deux remplis, deux refusés

`MissionType.LUNAR_ORBIT("LUNAR ORBIT", true)`. Le `true` est ce qui fait travailler
`Payloads.forMissionType` : sans lui, la sonde inerte resterait éligible à une mission qui exige une
combustion d'insertion.

**Remplis pour de vrai**, la réponse étant connue et n'appartenant à aucun lot ultérieur :

| Site | Cas ajouté |
|---|---|
| `Payloads.domainOf` | `PayloadDomain.LUNAR` |
| `MissionHorizon.defaultFor` | `new Revolutions(DEFAULT_LUNAR_ORBIT_REVOLUTIONS)`, la constante valant **12** |

Les douze tours sont le chiffre que le découpage §4 / `L5` a déjà choisi, et que `L2` vient de
rendre honnête : 23,6 h d'orbite lunaire, et non les 0,93 h que le µ terrestre rendait.

**Refusés, en nommant le lot qui les remplira** — parce qu'ils doivent rendre un `MissionSpec` et un
`ScenarioMission`, tous deux scellés, dont la variante `LunarOrbit` n'arrive qu'en `L5` :

| Site | Ce qu'il ne peut pas rendre |
|---|---|
| `MissionFactory.specFromWizardValues:88` | `MissionSpec.LunarOrbit` — `L5` |
| `ScenarioMapper:76` | `ScenarioMission.LunarOrbit` — `L5` |

Ni l'un ni l'autre n'est atteignable avant `L7`, faute de carte au wizard. Le refus est un
`UnsupportedOperationException` et non l'`IllegalArgumentException` que `MissionFactory` lève pour
une GEO sans AKM : **ce n'est pas une erreur de l'utilisateur, c'est un lot qui n'existe pas
encore**, et les deux ne doivent pas se lire pareil dans un journal.

Quatre est ce que la lecture trouve. **C'est le compilateur qui tranchera** à l'implémentation, et un
cinquième site serait un fait à consigner ici, pas à contourner.

---

## 6. Les tests qui ferment

Huit cas neufs et un réécrit, dans trois fichiers. Aucun ne propage.

### 6.1 `PayloadsTest` — deux neufs, un réécrit

1. **`forMissionType_lunarOrbit_offersOnlyTheOrbiter`** — exactement un modèle, et les trois refus
   **pour trois raisons différentes**, ce que le test dit explicitement : universel mais inerte,
   lunaire mais inerte, propulsé mais terrestre. Un test qui n'assertait que le compte passerait
   encore si les deux axes du filtre se confondaient.
2. **`lunarOrbiter_isPropelledAndPlacedByItsDomain`** — le miroir de
   `lunarProbe_isInertAndPlacedByItsDomain:80`.
3. **Réécrit** : `forMissionType_lunarFlyby_keepsTheProbeAndTheUniversalModule:67` accueille
   l'orbiteur, avec la raison dans son nom — un survol n'exige pas de propulsion, donc il n'en
   exclut pas (§2.2).

### 6.2 `PropellantBudgetTest` — cinq neufs

4. **`lunarInsertionDeltaV_matchesTheMeasuredArrival`** — la forme close à 100 km contre la bande
   mesurée par `L0`. L'assertion est une **borne** — sous 2 % du pire cas — et non une égalité : la
   forme close est un modèle plus simple que le vol, et un test qui prétendrait l'égalité mentirait
   sur ce qu'il vérifie.
5. **`lunarInsertionDeltaV_fallsWithAltitude`** — 50 → 500 km. Le cas non évident du lot : **le pire
   cas est le plancher de la bande, pas son plafond**, contre l'intuition qu'une orbite haute coûte
   plus cher. C'est aussi ce qui justifie que la capacité du §2.1 soit vérifiée à 50 km.
6. **`loadsForLunarOrbit_sizesTheInsertionThenTheLauncher`** — les trois composantes, et l'ordre : la
   charge lanceur répond à `dryMass + insertionLoad` (§4.1).
7. **`loadsForLunarOrbit_refusesWhenTheTankIsTooSmall`** — le refus, et les deux nombres dans le
   message.
8. **`loadsForLunarOrbit_staysInsideCapacityOnBothLaunchers`** — le miroir de
   `loadsForLunar_staysInsideCapacityOnBothLaunchers:200`, Ariane 62 à 81 %.

### 6.3 `MissionHorizonTest` — un neuf

9. **`defaults_areRevolutionsPerMissionType:256`** gagne le cas lunaire et épingle les douze tours.
   Il ne couvre aujourd'hui ni `LUNAR_FLYBY` ni rien d'autre que LEO et GEO ; ce lot n'y ajoute que
   son propre cas, combler le reste étant le travail de personne ici.

### 6.4 Ce qui ne casse pas à la compilation

`MissionProfileTest:150` switche sur `MissionProfile` et porte un `default` ;
`defaults_areRevolutionsPerMissionType` n'énumère pas. Le seul test que le lot rend rouge est celui
du §2.2, et c'est par une propriété voulue.

---

## 7. Ce que `L3` ne fait pas

1. **Aucune mission ne se compose.** `MissionSpec.LunarOrbit`, `LunarOrbitMission` et la branche de
   `MissionComposer` sont `L5` ; les deux switches refusent d'ici là.
2. **Aucun étage.** `LunarApproachCoastStage` et `LunarInsertionStage` sont `L4` — et c'est `L4` qui
   mesurera si la marge de 10 % couvre la perte de poussée finie (§3.2).
3. **Aucune carte au wizard**, ni onglets : `L6` et `L7`.
4. **Une mission d'orbite lunaire ne se sauvegarde pas.** `ScenarioMission.LunarOrbit` n'existe pas —
   mais elle ne se crée pas non plus, donc rien n'est perdu tant que `L5` n'a pas fixé la forme du
   spec que ce record devra refléter.
5. **`loadsForLunar` ne bouge pas.** Le survol garde son dimensionnement à deux composantes ; ce lot
   en ajoute un troisième à côté, il n'en généralise pas un. La généralisation attendrait un
   troisième profil lunaire, et `MIS-11` est le premier candidat.
6. **L'écrêtage des autres méthodes reste l'écrêtage.** Le refus du §4.2 est local à la LOI et ne
   change rien à `loadsForGeo` ni à `sizeTopStage` : le diagnostic d'infaisabilité général reste le
   chantier que le javadoc de la classe annonce.

---

## 8. Risques

**Le seul risque du lot est le chiffre qui reste une hypothèse** : la marge de 10 % face à une
perte de poussée finie non mesurée sur une combustion de 5 % d'une révolution (§3.2). S'il manque,
`L4` le verra sur son hyperbole fabriquée, avant qu'aucune mission ne vole — et la réponse sera de
mesurer puis de poser un chiffre, pas d'élargir en aveugle.

**Ce qui n'est pas un risque** : le Δv en forme close, dont l'écart au vol est mesuré et absorbé
cinq fois par la marge ; la non-régression, le lot étant additif et ne touchant aucun chemin de vol ;
et les deux refus, inatteignables avant `L7`.

---

## 9. Ce que `L3` lègue

- **À `L4`** : un orbiteur qui existe, avec une poussée et un Isp à faire voler, et un Δv de
  référence à comparer à ce que la combustion finie coûte réellement.
- **À `L5`** : `MissionType.LUNAR_ORBIT`, l'horizon à douze tours, la charge d'insertion et la masse
  à l'injection — et deux refus explicites qui lui montrent exactement où écrire.
- **À `L7`** : un catalogue qui n'offre qu'un seul modèle pour cette mission, donc un filtre de
  charge utile qui n'a rien à décider.
- **À `PropellantBudget`** : le premier dimensionnement non terrestre, et la démonstration qu'il
  n'appelait pas un paramètre de contexte mais deux constantes de plus.

---

## 10. Fermeture — `L3` est implémenté

**Verdict : la propriété du §1 est vraie, et les huit cas neufs la tiennent.** Suite du lot et de ses
voisins — catalogue, budget, horizon, factory, scénarios, wizard : **354 tests, 0 échec**, sur 37
classes. Six fichiers de `main` et trois de `test` ont bougé.

### 10.1 Les chiffres du document, mesurés sur le code livré

Les valeurs du §1.2, du §2.1 et du §4.3 avaient été établies sur un modèle Python et sur une sonde
équivalente. Relevées sur `loadsForLunarOrbit` lui-même :

| | annoncé | mesuré sur le code |
|---|---|---|
| `v∞` d'arrivée depuis 400 km | 828,74 m/s | **828,74** |
| Δv d'insertion à 100 km | 820,8 m/s | **820,77** |
| charge d'insertion, orbiteur 2 t sec | ~658 kg | **657,7** |
| masse au décollage | 2 658 kg | **2 658** |
| étage haut Falcon Heavy | 13 % | **13 %** |
| étage haut Ariane 62 | 81 % | **81 %** |

Deux nombres du §4.3 étaient hauts d'un kilogramme — 14 395 et 25 111 contre 14 394 et 25 110 — la
sonde de conception ayant utilisé une charge utile ronde de 2 658,0 kg là où l'orbiteur en fait
2 657,7. Le tableau est corrigé.

### 10.2 Trois écarts au plan

1. **`loadsForLunarOrbit` délègue à `loadsForLunar`** une fois la charge d'insertion connue, au lieu
   de recopier le dimensionnement du lanceur. Le §4 ne le disait pas, et c'est mieux ainsi : une
   seule définition de l'ascension et de l'injection, deux profils qui la partagent. La charge utile
   passée est celle qui volera — `toSpacecraft(dryMass, insertionLoad)` — ce qui est aussi la
   manière la plus directe de rendre l'ordre du §4.1 impossible à inverser.
2. **Un second refus, non prévu** : la méthode rejette une charge utile inerte avant toute
   arithmétique. `loadsForHighOrbit` se contente d'un `if (capacity > 0)` et rend un AKM à zéro,
   parce qu'une GEO sans AKM est déjà refusée en amont par `MissionFactory` ; ici une charge
   d'insertion nulle n'a aucun sens, et le refus dit lequel des deux problèmes on a.
3. **Deux javadocs devenus faux, corrigés en passant** dans `Payloads` : « *The four payload
   sections* » (elles sont cinq) et « *the compiler points at this site the day a fourth type
   appears* » — c'est exactement ce mécanisme qui a désigné ce site, et le javadoc le dit
   maintenant.

### 10.3 Deux rouges préexistants, non attribuables au lot

- **`ScenarioReplayTest` ignore ses deux cas**, sur un `Assumptions.assumeTrue` de son `:57`.
  Mesuré par remisage : **2 tests, 2 ignorés sur l'arbre propre aussi**.
- **`spotlessCheck` échoue sur les huit fichiers de `L1`**, comme au lot précédent
  ([`04-conception-L2.md`](04-conception-L2.md) §11.2). `spotlessApply` les reformate ; le
  reformatage a de nouveau été **annulé** pour ne pas le plier dans ce diff. Aucun des neuf fichiers
  de `L3` n'est en violation.
