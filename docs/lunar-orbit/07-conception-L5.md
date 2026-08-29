# MIS-5 / L5 — La mission du produit

Lot **L5** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur la baseline de
[`02-baseline-L0.md`](02-baseline-L0.md), sur la frontière de [`03-conception-L1.md`](03-conception-L1.md),
sur les deux mesures réparées par [`04-conception-L2.md`](04-conception-L2.md), sur l'orbiteur de
[`05-conception-L3.md`](05-conception-L3.md) et sur les deux étages de
[`06-conception-L4.md`](06-conception-L4.md). Il rend vraie **une** propriété :

> **Une mission vole du sol à une orbite lunaire circulaire, et le produit la compose.**

C'est le lot qui **branche** : les quatre précédents ont écrit des briques que rien n'appelait.

**Neuf faits mesurés contredisent le découpage**, et cinq changent ce que le lot livre : où il
compile, ce que l'objectif porte, ce que le vol coûte en points, ce qu'il fait de la dette ε, et
lesquels des refus ouverts lui appartiennent. Ils sont au §1.2, avec le site que la lecture
avait manqué et que la compilation a désigné.

---

## 1. Inventaire mesuré

### 1.1 Ce que le lot touche

| Fichier | Ce qui bouge |
|---|---|
| `operation/LunarOrbitMission` | **neuf** — la mission du produit, douze étages |
| `operation/MissionSpec` | un quatrième record, `LunarOrbit` |
| `operation/MissionComposer` | la quatrième branche du `switch`, sans argument de mode |
| `stage/CoastingStage` | un corps d'arc optionnel, nul par défaut (§3.1) |
| `window/problem/LunarLaunchWindowPlanner` | un cœur privé partagé, deux entrées publiques |
| `states/mission/MissionWizardAppState` | un cas : la fenêtre lunaire |
| `ui/mission/MissionTargetOrbit` | un cas : `Optional.empty()` |
| `ui/mission/wizard/MissionProfile` | un cas : un refus nommant `L7` |
| `ui/mission/wizard/WizardPrefill` | un cas : un refus nommant `L7` (§5.1) |
| `runtime/MissionOptimizer` | deux lignes dans `resolveTargetAltitude` |
| `scenario/model/ScenarioMission` | un javadoc faux (fait 9) |
| `operation/MissionFactory`, `scenario/ScenarioMapper` | deux chaînes : le lot nommé passe de `L5` à `L7` |

**Un fichier neuf, onze modifiés** — l'inverse de la forme de `L4`, et c'est normal : `L4` écrivait
des briques que rien n'appelait, `L5` est le lot qui les branche. La non-régression n'est donc plus
structurelle par absence de modification ; elle l'est **par valeur par défaut**, en trois endroits :

- le corps d'arc nul de `CoastingStage`, que prennent les six sites existants ;
- le `EARTH` des trois seuls constructeurs d'`OrbitInsertionObjective` du dépôt ;
- le fait qu'aucune mission existante ne construit un `MissionSpec.LunarOrbit`.

### 1.2 Neuf faits que le découpage ne connaît pas, et un dixième que le compilateur a trouvé

**1. Quatre `switch` exhaustifs hors périmètre cassent à la compilation.** Ajouter une quatrième
variante au `MissionSpec` scellé casse
[`MissionWizardAppState:214`](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionWizardAppState.java),
[`MissionTargetOrbit:42`](../../src/main/java/com/smousseur/orbitlab/ui/mission/MissionTargetOrbit.java),
[`MissionProfile:346`](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/MissionProfile.java)
— dont le commentaire annonce d'ailleurs l'événement, « *It is the compiler that must point here at
the fourth spec type* » — et
[`WizardPrefill:77`](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/WizardPrefill.java).

**La lecture n'en avait trouvé que trois ; c'est le compilateur qui a désigné le quatrième**, et
`L3` §5 avait posé la règle pour ce cas exact : « un cinquième site serait un fait à consigner ici,
pas à contourner ». Le voici consigné. `WizardPrefill` est le seul des quatre qui soit doublement
mort : il appelle `MissionProfile.of` en tête de méthode, donc il refuse avant même d'atteindre son
`switch`.

Aucun des quatre n'est **atteignable** en `L5` — sans carte au wizard, rien ne crée ni ne rouvre une
mission d'orbite lunaire. **La colonne « Ce qu'il touche » du §4 du découpage est incomplète de
`states/` et de `ui/`.**

**2. La septième carte interdit de remplir `MissionProfile.of` pour de vrai.**
[`StepMissionType:80`](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/StepMissionType.java)
rend `MissionProfile.values()` à trois cartes par rangée : ajouter la constante mettrait à l'écran
la septième carte que le §2.3 pt 6 mesure à 108–141 px de débordement, dans une fenêtre fixe de
880 × 660. La carte appartient à `L7`, et les onglets à `L6`.

**3. La fenêtre lunaire n'est pas réutilisable « sans une ligne ».** Le §1 du découpage l'écrit
ainsi. Le **problème** l'est : le constructeur confirmant de `LunarLaunchWindowProblem` prend sept
scalaires, tous disponibles. Le **planificateur** ne l'est pas : sa signature porte sur
`MissionSpec.Lunar`. Son corps, en revanche, ne change pas d'une ligne (§5.2).

**4. Le vol produit ~10 500 points, pas 7 235, et la trace reste décimée.** Le §7 donne le décompte
poste par poste. Aucun choix d'horizon ne passe sous le budget de 8 192 sommets.

**5. ε n'est pas lu par cette chaîne.** La seule frontière déclarée l'est depuis le contexte
terrestre, donc `SoiCrossingDetector.crossingFrom` rend `scale = 1,0` et la bande morte n'entre dans
aucun `g` (§8).

**6. Personne ne lit `OrbitInsertionObjective.inclination()`** — ni dans `main`, ni dans les tests.
Le composant est écrit par trois constructeurs et lu par zéro.

**7. Le §6 pt 10 du découpage nomme les mauvais sites.** Il écrit que « trois sites d'affichage »
montrent l'inclinaison de l'objectif, en citant `MissionDetailView:177`, `MissionResultText:67` et
`PanelFooter:278`. Ces trois-là lisent `MissionTargetOrbit` — construit depuis le **spec** — et
`OrbitElements`, l'orbite atteinte. La question ouverte portait sur un composant invisible.

**8. Sans déclaration d'arc, le coast terminal est reconverti vers GCRF.**
[`StageLegRunner:189`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/StageLegRunner.java)
fait `ArcTransition.convert(stageEntry, context.gravity())` en tête de `fly`, et `convert` compare
les repères **par référence** : un état sélénocentrique remis dans le contexte terrestre de la
mission est réellement transformé. La panne sortirait loin de sa cause — `finalCoastArcBody` rendrait
`EARTH`, `objectiveMet` comparerait ~380 000 km à 100 km, et `MissionLoadEvaluator` traduirait en
« aucune charge d'ergols faisable ».

**9. Les deux refus de `L3` §5 tiennent ou tombent ensemble, et le javadoc de `ScenarioMission`
promet une garde qu'il n'a pas.** `ScenarioMapper.toMissionValues` est un `switch` exhaustif sur
`ScenarioMission` : ajouter le record `LunarOrbit` oblige à une branche qui **écrit** une clé de
valeurs wizard, exactement comme `MissionFactory` doit en **lire** une. Ce qui bloque les deux n'est
donc pas la forme du spec, c'est la clé de champ — qui appartient à `L7`. Au passage, le javadoc de
`ScenarioMission` affirme que « *the day a new `MissionSpec` branch appears, the compilation fails
until the matching record exists here* » : c'est faux, le `switch` de `toScenarioMission` est sur
`MissionType`, et `L3` l'a déjà absorbé.

### 1.3 Ce qui existe déjà et qu'on n'écrira pas

- **Le contexte lunaire dérivé** : `ArcTransition.across(mission.gravitationalContext(), MOON)`,
  mesuré par `L4` §1.3 égal à `moon().withPerturbers(EARTH, SUN)`, même instance de repère.
- **Le prédicat de l'objectif** : `MissionLoadEvaluator.objectiveMet` sélectionne déjà par
  `finalCoastArcBody`, réparé par `PHY-4 / L6` §5.2, et rendra `MOON`. Le nom `"Coasting"` de
  l'étage terminal est la seule chaîne porteuse de la chaîne.
- **L'horizon** : `defaultFor(LUNAR_ORBIT)` rend `Revolutions(12)` depuis `L3`, et `L2` l'a rendu
  honnête — 7 067 s au lieu de 783.
- **Le dimensionnement** : `PropellantBudget.loadsForLunarOrbit`, refus compris.
- **La branche de faisabilité** : `MissionPlanOptimizer.minimizedLoadPlanner` tombe dans le cas
  « objectif de la mission + tolérance par défaut », soit ±7 km sur 100. Rien à ajouter — large
  devant les ±0,5 km d'insertion mesurés par `L4` §5 et les 0,08 km de respiration de `L0` mesure 5.

---

## 2. `MissionSpec.LunarOrbit`

Un quatrième record frère — pas une sous-interface partagée avec `Lunar` (§10 pt 9) :

```java
record LunarOrbit(
    String name, LaunchConfiguration configuration,
    double parkingAltitude, double orbitAltitude,
    String siteName, double latitude, double longitude, double altitude,
    MissionHorizon horizon, AtmosphereModel atmosphere) implements MissionSpec
```

**`orbitAltitude` et non `periluneAltitude`**, alors que c'est le même nombre — le §6 pt 1 du
découpage l'écrit (« l'altitude d'orbite lunaire, qui est aussi le périlune visé par le TLI »). Le
spec porte l'**intention** de l'utilisateur ; c'est la mission qui la traduit en périlune visé pour
le `TLIBurnStage`.

**L'altitude de parking est un composant**, comme sur `Lunar` et pour la même raison : trois choses
doivent s'accorder dessus — la fenêtre, la chaîne et le budget — et une constante mettrait le même
nombre dans trois fichiers. Sa valeur vient de `LunarFlybyMission.DEFAULT_PARKING_ALTITUDE`, dont le
javadoc dit déjà qu'elle est celle « *every lunar mission built from the wizard leaves from* » : une
deuxième constante serait le doublon que celle-ci a été écrite pour empêcher.

**Le constructeur compact normalise `horizon` et `atmosphere` et ne valide rien d'autre**, comme
`Lunar`. Le refus qui compte est celui de `loadsForLunarOrbit` quand le réservoir de l'orbiteur ne
tient pas l'insertion, et il est déjà écrit ; une borne d'altitude ici serait une deuxième vérité
sur la plage que l'`AltitudeRange` du profil `L7` portera.

**Pas de composant d'inclinaison** — elle est subie (§2.2 pt 2 du découpage ; dispersion **22,3°**
mesurée par `L0`). **Pas de tolérance** — c'est la bande du prédicat, pas un choix d'appelant.
**Pas de date de tir** — aucun spec du dépôt n'en porte, elle vit sur `MissionEntry`.

---

## 3. `LunarOrbitMission` et sa chaîne

`extends EarthMission` — elle part d'un pas de tir, donc elle doit les trois accesseurs de site.
Contexte de mission `earth().withPerturbers(MOON, SUN)`, celui dont `ArcTransition.across` dérive le
lunaire.

**Douze étages**, quatre de plus que le survol :

| # | étage | contexte | échant. | frontières | fin |
|---|---|---|---|---|---|
| 1 | `Vertical Ascent` | mission | 1 s | — | durée du profil |
| 2–4 | `AscentSequence.gravityTurn`, plan due east `i = φ` | mission | 1 s / 60 s | — | CMA-ES |
| 5 | `Parking` (400 km) | mission | 1 s | — | insertion analytique |
| 6 | `Parking coast` | mission | 60 s | — | l'allumage du TLI |
| 7 | `Translunar injection` (visée = `orbitAltitude`) | mission | 1 s | — (propulsif) | coupure + tassement |
| 8 | `S2 separation` (indice de pile 1) | mission | 60 s | — | coast d'inter-étage |
| 9 | `Translunar coast` (borne 5 j) | mission | 60 s | `{MOON}`, **termine** | la sphère |
| 10 | `Lunar approach` | **lunaire dérivé** | 60 s | — | l'allumage de la LOI |
| 11 | `Lunar orbit insertion` | **lunaire dérivé** | 1 s | — (refusé : propulsif) | coupure + 60 s |
| 12 | `Coasting` | **lunaire dérivé** | 60 s | — | horizon, 12 tours |

**Un seul de ces douze noms est porteur** : l'étage 12 doit s'appeler `"Coasting"`, que
`MissionLoadEvaluator.FINAL_COAST_STAGE` compare. Les onze autres ne sont lus par rien.

**Le largage est placé juste après le TLI**, exactement où `GEOMission` place le sien après
l'injection GTO, et pour la même raison : `resolveActiveStage` résout par la masse, donc rien ne rend
la propulsion de la charge utile active avant. L'indice de pile 1 est déclaré plutôt que laissé
libre, ce qui fait refuser le largage si le virage gravitationnel a laissé de l'ergol dans S1 — la
garde que `GEOMission` a payée une fois.

### 3.1 Le coast terminal et son arc

`CoastingStage` gagne un troisième constructeur et une redéfinition :

```java
public CoastingStage(String name, Double maxTime, SolarSystemBody arcBody)

@Override
public GravitationalContext gravitationalContext(Mission mission) {
  return arcBody == null
      ? super.gravitationalContext(mission)
      : ArcTransition.across(mission.gravitationalContext(), arcBody);
}
```

Les six `CoastingStage` du dépôt passent par les deux constructeurs existants, donc `arcBody == null`
et **la redéfinition rend l'expression héritée elle-même** — `super.gravitationalContext(mission)`,
pas une reconstruction équivalente. Le test l'asserte à deux niveaux : égalité de valeur du contexte,
et **identité de référence du repère**, qui est ce qu'`ArcTransition.convert` compare pour décider de
ne rien transformer. Le
site de composition dit `new CoastingStage("Coasting", null, SolarSystemBody.MOON)`, et la règle
« ce qu'est un arc lunaire » reste écrite une seule fois, dans `ArcTransition.across`.

**Sans cette déclaration, rien ne lèverait** : c'est le fait 8, et c'est le troisième des « trois
derniers étages » du §2.3 pt 2 du découpage, que `L4` §7 pt 2 lègue nommément ici.

**Le paramètre est générique et non lunaire**, ce qui est délibéré : `MIS-11` reprendra le même coast
pour un arc de retour, et une classe `LunarCoastStage` aurait porté la **quatrième** copie d'une
déclaration que `L4` §3.2 avait déjà refusé de centraliser à deux.

**Ce coast ne redéfinit pas `propagateStandalone`**, et c'est ce qui rend l'horizon résoluble :
`MissionOptimizer` lit `getCurrentState()` après la marche d'étages, y trouve l'état sélénocentrique
publié par `LunarInsertionStage`, et `Revolutions.keplerianPeriodOf` y lit le µ lunaire — 7 067 s, ce
que `L2` a rendu vrai. Le refus du §1 pt 6 du découpage (« ne pas réparer `CoastingStage` ») et ce
lot ne se rencontrent donc jamais.

### 3.2 L'objectif, et ce qu'il ne porte pas

```java
OrbitInsertionObjective.circular(SolarSystemBody.MOON, orbitAltitude, Double.NaN)
```

`NaN` est le **marqueur d'absence** que la mesure 1 de `L0` recommandait, et il ne coûte rien : aucun
lecteur de `inclination()` n'existe (fait 6). L'alternative — « la valeur que la géométrie délivre »,
soit `180° − φ` — est juste à 2° trois fois sur quatre et fausse de **20,3°** la quatrième, et `L0`
conclut mot pour mot qu'« un nombre d'aspect crédible et occasionnellement faux de 20° est pire
qu'aucun nombre ».

L'inclinaison est donc **subie, mesurée et journalisée** par le vol de clôture — en nommant son
repère, faute de quoi l'écart quasi constant de 21° entre la lecture ICRF sélénocentrique
qu'`OrbitElements` rapporte et la lecture au-dessus de l'équateur lunaire ferait lire 150° là où un
document de mission écrirait 171°.

`finalCoastArcBody` rendra `MOON` : tous les échantillons de l'étage 12 sont sur l'arc lunaire,
l'étage n'en traversant aucun.

**`MissionTargetOrbit.of` rend `Optional.empty()`** pour ce spec. Le motif n'est pas celui du survol,
qui n'a aucune cible d'orbite : depuis `L2`, `OrbitElements` compte ses apsides sur le corps de l'arc,
donc une cible `(100 km, 100 km)` **serait** comparable. Ce qui tranche est que
`MissionResultText.formatMiss` imprime l'écart d'altitude et l'écart d'inclinaison dans une seule
chaîne : montrer le premier obligerait à apprendre l'absence à un formateur partagé par LEO et GEO,
dans un lot où aucun écran ne peut être regardé. `L7` amènera la carte, l'écran et quelqu'un pour le
lire ; le choix reste ouvert pour lui.

### 3.3 La borne du coast translunaire

```java
TRANSLUNAR_COAST_BOUND = TranslunarInjectionPlan.TIME_OF_FLIGHT_SECONDS * 1.25   // 5 j
```

`L1` §11 pt 3 laissait explicitement la valeur à ce lot. Elle est **dérivée** de la constante du plan
plutôt qu'un nombre neuf, et elle est sans ambiguïté ni le filet de 7 200 s de `StageChainRunner`
(0,083 j) ni la traversée (3,071–3,148 j mesurés par `L0`) : son atteinte se lit comme une panne, ce
qu'une borne doit être. C'est aussi la valeur qu'a employée le vol de clôture de `L1`.

---

## 4. La quatrième branche du composer

```java
case MissionSpec.LunarOrbit lunarOrbit -> composeLunarOrbit(lunarOrbit);
```

**Sans argument de mode**, pour la raison que `composeLunar` et `composeGeo` donnent déjà : aucun
étage de la moitié lunaire de la chaîne n'a de contrepartie CMA-ES, donc tous les modes rendent la
même composition. Ce que la mission optimise est son **ascension**, comme toute mission terrestre, et
le mode continue de la différencier sur l'axe des charges dans `MissionPlanOptimizer`.

`compose` pose ensuite l'horizon et l'atmosphère du spec, comme pour les trois autres — il en est le
seul écrivain, et c'est ce qui les fait survivre à une recomposition.

---

## 5. Les quatre sites qui doivent compiler

### 5.1 Les trois `switch`

| Site | Ce que `L5` écrit | Atteignable en `L5` ? |
|---|---|---|
| `MissionComposer.compose` | la branche du §4 | **oui**, c'est la livraison |
| `MissionWizardAppState.scheduledDateFor` | la fenêtre lunaire | non avant `L7` |
| `MissionTargetOrbit.of` | `Optional.empty()` (§3.2) | non avant `L7` |
| `MissionProfile.of` | un refus nommant `L7` | non avant `L7` |
| `WizardPrefill.toValues` | un refus nommant `L7` | non avant `L7` |

Les quatre derniers n'ont aucun chemin d'appel avant que `L7` ne pose la carte. **L'obligation est de
compiler sans mentir**, et le refus de `MissionProfile.of` est un `UnsupportedOperationException` de
la même veine que les deux que `L3` a écrits — pas une erreur d'utilisateur, un lot qui n'existe pas
encore. Les deux alternatives ont été pesées et écartées : rendre `LUNAR` allumerait la carte du
survol pour une mission d'orbite, ce qui est exactement le repli dont le commentaire de `of` dit
qu'il a déjà coûté un verdict faux ; et ajouter la constante d'enum ferait déborder le premier écran
(fait 2).

**`WizardPrefill` prend le même refus, et pour la raison du §6** : sa branche devrait *écrire* la
clé de champ que `MissionFactory` devrait *lire*, et cette clé est un nom persisté autant qu'un
libellé. Il rejoint donc les deux refus de `L3` : quatre sites attendent la même chose, et c'est
`L7` qui l'apportera.

### 5.2 Le planificateur de fenêtre

Trois méthodes : un cœur privé prenant les sept scalaires du problème plus `earliest`, et deux
entrées publiques, une par spec lunaire.

Le cœur prend la `LaunchConfiguration`, le site, les deux altitudes et `earliest` — et non les sept
scalaires du problème, qu'il construit lui-même : les deux entrées n'auraient sinon partagé que
l'appel au solveur, chacune répétant le dimensionnement.

**Les deux corps sont identiques**, et c'est une correction à ce que la conception avait d'abord
supposé. Le planificateur passe `spec.configuration().payload()` — le `Spacecraft` **tel qu'il
vole** — et `Vehicle.getMass()` rend `dryMass + propellantLoad`. Sur un spec d'orbite lunaire, cette
charge utile porte déjà ses ergols d'insertion, puisque c'est ainsi que `L7` la construira ;
`loadsForLunar` rend donc la bonne masse à l'injection **sans rien savoir du profil**. C'est
exactement ce que `loadsForLunarOrbit` fait en interne, qui délègue à `loadsForLunar` avec
`payload.toSpacecraft(dryMass, insertionLoad)`. Ce qui bloquait la réutilisation était la signature,
et elle seule — le §1 du découpage avait donc raison sur le fond (« réutilisée sans une ligne ») et
tort sur la lettre.

---

## 6. Deux réparations à coût nul, et deux chaînes

**`MissionOptimizer.resolveTargetAltitude`** rend `NaN` quand `insertion.body() != EARTH`, ce qui
fait tomber le diagnostic de virage gravitationnel dans la branche neutre qui existe déjà — celle où
`MIS-4` tombe parce qu'un `FlybyObjective` n'est pas une insertion. Sans cela, le journal du premier
vol lunaire comparerait la sortie du virage à un transfert de Hohmann idéal vers une orbite
**terrestre de 100 km** : un nombre qui a l'air d'une mesure. Non-régression structurelle — les trois
seuls constructeurs d'`OrbitInsertionObjective` du dépôt passent `EARTH`.

**Le §6 pt 11 du découpage quitte donc la liste des limitations.** Il l'écrivait comme une « ligne de
journal fausse, introduite par ce chantier » ; un défaut introduit par un lot se répare dans ce lot,
et il coûte deux lignes. Sa correction adjacente tient : `StageEndStateDiagnostic` reste légitimement
terrestre, sa garde étant `problem instanceof GravityTurnProblem`, ce que la mesure 6 de `L0`
confirme.

**Le javadoc de `ScenarioMission`** est corrigé sur le fait 9 : la garde se déclenche à l'ajout d'un
`MissionType`, pas d'une branche de `MissionSpec`.

**Deux chaînes de refus** — `MissionFactory:175` et `ScenarioMapper:130` — passent de « lands in
MIS-5 / L5 » à `L7`, avec la raison. `L3` §5 les attribuait à `L5` en supposant que la forme du spec
les bloquait ; c'est la clé de champ qui les bloque (fait 9), et elle appartient au lot qui aura le
champ. Rien n'est perdu : aucune mission d'orbite lunaire ne se crée avant `L7`, donc aucune ne se
sauvegarde.

---

## 7. Le compte de points, et une promesse du découpage qui tombe

Le §4 / `L5` annonce « ~7 235 points à la cadence mesurée par `MIS-4 / L0` — **sous le budget de
8 192 sommets de `TrajectoryPolyline`** », et en conclut que « `MIS-5` récupère la trace sommet pour
sommet que `MIS-4` avait perdue ». **Les deux affirmations sont fausses.**

Le chiffre vient d'appliquer 1 447 points/jour — la cadence d'un coast à 60 s — aux cinq jours
entiers. Poste par poste, sur les mesures de `L0` §5 (les étages partagés, relevés un par un sur un
vol GEO **au sol**), de `L0` §4 (l'approche), de `L0` §6 (la période lunaire) et de `MIS-4 / L6` §9
(la durée de la combustion TLI) :

| poste | durée | pas | points |
|---|---|---|---|
| Vertical Ascent | 7 s | 1 s | 9 |
| Virage gravitationnel (S1, largage, S2) | ~330 s | 1 s / 60 s | ~332 |
| Parking | 2 668 s | 1 s | ~2 670 |
| Parking coast | ≤ 1 révolution | 60 s | 0–93 |
| Translunar injection | 48–130 s + tassement | 1 s | ~110–190 |
| S2 separation | 2 s | 60 s | 2 |
| Translunar coast | 3,071–3,148 j | 60 s | 4 423–4 534 |
| Lunar approach | 16,32–18,27 h | 60 s | 979–1 096 |
| Lunar orbit insertion | 350,1 s + 60 s | 1 s | ~411 |
| Coasting, 12 tours | 84 800 s | 60 s | 1 414 |
| **total** | **~5,05 j** | | **~10 350 – 10 750** |

**La trace reste décimée d'un facteur ~1,3.** `TrajectoryPolyline.MAX_POINTS` est un budget de
trajectoire entière — une seule foulée sur le tableau échantillonné, les tables par corps en étant
extraites — donc les deux arcs ne disposent pas de 8 192 sommets chacun.

**Et aucun choix d'horizon n'y change rien** : tout ce qui précède le coast terminal fait déjà
**~9 100 points**, soit 11 % au-dessus du budget. Même une orbite lunaire montrée pendant zéro
seconde déborderait. Le §6 pt 12 de `MIS-4` — « la trace redevient décimée » — n'est donc pas payé
par ce chantier, et ne pouvait pas l'être.

**Le coupable n'est pas lunaire.** Le poste `Parking` pèse à lui seul 2 670 points pour 7,7 s de
poussée : c'est le défaut que la mesure 4 de `L0` a diagnostiqué — `MissionStage.isPropulsive()` rend
`true` par défaut, donc « propulsif » y signifie « ni coast ni largage » — et dont elle conclut mot
pour mot que « ce chantier n'a pas à le régler ». `L5` le constate, le chiffre, et le laisse à
`MIS-9`.

**Une note de méthode**, parce que l'erreur vaut d'être écrite : le préfixe terrestre avait d'abord
été déduit du vol `MIS-4` par soustraction (14 467 − 10 130 = 4 337 points), ce qui supposait que ce
vol partait du sol. Il n'en partait pas — `lunar-flyby/02-baseline-L0.md` §4 le mesure sur 10 j
d'horizon, et 10 × 1 447 = 14 470, donc du coast presque pur. Un vol au sol avec ce préfixe aurait
rendu ~17 450 points. Le préfixe se lit sur le vol GEO au sol, pas par différence.

---

## 8. ε : la dette close par un verdict

`PHY-4 / L6` §5.5 attendait une **trajectoire capturée** pour calibrer la bande morte, et le §4 /
`L5` du découpage en fait une condition de fermeture de ce lot. `MIS-5` produit bien cette
trajectoire, et **elle ne calibre rien** — pour une raison structurelle, pas conjoncturelle.

[`SoiCrossingDetector.crossingFrom:95`](../../src/main/java/com/smousseur/orbitlab/simulation/gravity/SoiCrossingDetector.java)
met l'échelle à `1 + ε` **seulement quand le contexte de vol est celui du corps de la sphère** :

```java
double scale = from.body() == soi.body() ? 1.0 + EXIT_DEAD_BAND : 1.0;
```

Dans cette chaîne, le seul étage qui déclare une frontière est le coast translunaire, et il la
déclare depuis le contexte **terrestre** : `scale = 1,0`, ε n'entre dans aucun `g`. Les deux étages
sélénocentriques n'en déclarent aucune (`L4` §3.3 et §4.2), le coast terminal non plus, et
`StageLegRunner:162` n'arme que ce qu'un étage déclare.

**Et même en armant la sortie sur l'arc lunaire, il n'y aurait rien à mesurer** : une orbite à
1 837 km de rayon face à une sphère de 62 700 à 69 400 km laisse `g ≈ −65 000 km` pendant les
vingt-quatre heures du coast terminal. Une capture démontre l'**absence de bavardage** ; elle ne
dimensionne pas la bande morte. Le seul montage qui l'exigerait — armer la frontière sur l'étage qui
**démarre sur la sphère** — a été pesé et écarté : ce serait changer le comportement d'un étage que
`L4` vient de livrer, pour confirmer une arithmétique que le javadoc de `EXIT_DEAD_BAND` porte déjà
(« *some ten seconds of dwell at transfer speed* »).

**La dette est donc close comme non payable par une capture**, avec sa raison, et rouverte pour
`MIS-11` : un retour vers la Terre ressort de la sphère depuis un contexte lunaire, arme donc un
détecteur à `R(1+ε)`, et le fait **tirer**. Ce sera le premier vol du dépôt où ε décidera de quelque
chose.

Le vol de clôture journalise la distance à la frontière sur l'arc lunaire, ce qui rend le verdict
vérifiable plutôt que raisonné. **La condition de fermeture du §4 / `L5` est remplacée par ce
verdict**, et le §7 du découpage gagne une ligne pour `MIS-11`.

---

## 9. Les tests

**Un vol lent, cinq classes rapides.**

### 9.1 `LunarOrbitFlightTest` — le vol du lot

`@EnabledIfSystemProperty(named = "orbitlab.slowTests", matches = "true")`. Un vol du sol
(Canaveral), sur la **configuration dimensionnée** par `loadsForLunarOrbit`, à la date que
`LunarLaunchWindowPlanner.nextOpportunity` résout — ce qui exerce l'entrée neuve pour de vrai.

**Un seul vol, et c'est une décision.** `MIS-4` en vole deux, dont un pleinement chargé, parce que
`L4` en avait besoin comme référence impulsionnelle pour `L6`. Ici aucun lot ultérieur n'en a besoin,
et depuis `L3` la configuration pleinement chargée n'est plus une configuration que le produit
propose : l'orbiteur y emporterait 800 kg au lieu des ~658 dimensionnés, donc une masse à
l'injection différente, donc un TLI différent, donc une arrivée différente — sans exercer un seul
mécanisme que le vol dimensionné n'exerce pas. La non-régression de `MIS-4` reste tenue par
`LunarFlybyFlightTest` **inchangé**, ce que `L1` a déjà vérifié.

**Affirme** : les arcs sont `[EARTH, MOON]` — deux et non trois, la mission ne ressortant plus de la
sphère ; `isComplete()` ; `ObjectiveEvaluator.met` à la tolérance par défaut.

**Journalise** : les apsides atteintes, l'inclinaison **en nommant son repère**, la distance à la
frontière sur l'arc lunaire (§8), le compte de points contre les ~10 500 attendus (§7), et le temps
de paroi. Ces cinq-là sont journalisés et non épinglés, sur la règle de `MIS-4 / L4` §8.3 : les
épingler figerait dans ce lot des nombres qu'il existe précisément pour découvrir. C'est aussi ce que
`L4` §7 pt 6 lègue ici — « rien n'est mesuré sur une vraie arrivée ».

### 9.2 Les cinq classes rapides

| classe | ce qu'elle tient |
|---|---|
| `LunarOrbitMissionTest` | les douze étages dans l'ordre et dans les trois modes, l'étage terminal nommé `"Coasting"` et déclarant l'arc lunaire, l'objectif `circular(MOON, altitude, NaN)`, l'horizon posé, la borne dérivée |
| `CoastingStageTest` | corps d'arc nul → le contexte de la mission, **par identité de référence** ; `MOON` → mesuré égal à `moon().withPerturbers(EARTH, SUN)` |
| `MissionTargetOrbitTest` | un spec `LunarOrbit` rend `Optional.empty()` |
| `MissionProfileTest` | `of` refuse en nommant `L7` |

`LunarOrbitMissionTest` porte aussi les assertions de composition — douze étages, horizon posé,
composition identique dans les trois modes — **et pas un `MissionComposerTest` neuf** : le dépôt n'en
a aucun, chaque branche du composer étant assertée à côté de la mission qu'elle construit
(`LunarFlybyMissionTest` est le précédent). Une classe par branche disperserait la même fixture sur
deux fichiers.

### 9.3 Ce qui n'est pas testé, et pourquoi c'est écrit

La réparation de `resolveTargetAltitude` (§6) est une méthode **privée** dont le seul effet
observable est une ligne de journal. La rendre visible pour un test coûterait plus que ce qu'elle
protège ; le vol de clôture la montre dans son journal, et c'est le seul endroit où elle se voit.

---

## 10. Ce que `L5` ne fait pas

1. **Aucun wizard** — ni onglets (`L6`), ni carte, ni champ, ni filtre de charge utile (`L7`).
2. **Les deux refus restent**, requalifiés `L7` : rien ne se crée ni ne se sauvegarde d'ici là.
3. **ε n'est pas calibré** — un verdict, pas une mesure (§8).
4. **Aucune optimisation CMA-ES** de la moitié lunaire : il n'y a pas de degré de liberté à explorer,
   le Δv d'insertion étant entièrement déterminé par l'hyperbole d'arrivée et l'orbite visée.
5. **`CoastingStage.propagateStandalone` n'est toujours pas réparé.** `L5` n'en a pas besoin (§3.1),
   et le §1 pt 6 du découpage tient : `MIS-5` répare un coast — le sien — par sa frontière.
6. **Les trois copies de la déclaration d'arc lunaire ne sont pas consolidées.**
   `LunarApproachCoastStage` pourrait désormais appeler `super(name, null, MOON)`, ce qui serait
   identique au bit près — mais c'est modifier un étage livré sans changement de comportement, et
   `LunarInsertionStage`, qui n'est pas un `CoastingStage`, garderait la sienne. La consolidation
   serait partielle.
7. **L'inclinaison n'est pas visée**, elle est subie, journalisée, et absente de l'objectif.
8. **La trace n'est pas dé-décimée** (§7) : le §6 pt 12 de `MIS-4` reste ouvert, et son coupable est
   terrestre.
9. **La hiérarchie scellée n'est pas restructurée.** Une sous-interface `MissionSpec.LunarLeg`
   partagée par les deux profils lunaires aurait replié deux `switch` sur une branche, mais elle
   aurait centralisé la seule chose qui diffère — le profil — en laissant décentralisé ce qui est
   commun, et elle aurait touché un miroir scellé et un format de fichier.

---

## 11. Risques

**Le temps de paroi du vol de clôture**, quatrième risque du découpage §5 : ascension CMA-ES, deux
`solve()` du TLI à ~4,5 s, et cinq jours de propagation sur les **deux** passes — la marche d'étages
volant désormais le coast translunaire, ce qu'elle ne faisait pas avant `L1`. `L1` a mesuré 29,2 s
pour trois étages depuis le parking. L'ordre de grandeur attendu est la minute ou deux ; le vol le
dira, et c'est l'utilisateur qui le lance.

**La seule panne qui ne lève pas** reste celle de `L4` §8 : un état géocentrique intégré avec un µ
lunaire au centre ne produit ni exception ni avertissement, `createOptimizationPropagator` prenant
son repère de l'état initial. Trois choses la bornent — la conversion en tête d'`enter` sur les deux
étages de `L4`, la déclaration d'arc du coast terminal (§3.1), et l'assertion `[EARTH, MOON]` du vol,
qui est précisément ce qu'un arc manqué casserait.

**`CoastingStage` est partagé par toutes les missions.** Une erreur sur le corps d'arc sortirait
comme « aucune charge d'ergols faisable » sur une mission sans rapport avec la Lune —
`MissionLoadEvaluator` attrapant toute `RuntimeException` par évaluation de λ. Elle est bornée par la
valeur par défaut nulle, que prennent les six sites existants, et par le test d'identité de
`CoastingStageTest`.

**Ce qui n'est pas un risque** : la traversée de sphère, la conversion de repère, la fenêtre de
lancement, la visée du TLI, la combustion finie centrée et l'insertion elle-même. Les six sont
livrés, volés et épinglés par `PHY-4`, `MIS-4` et les lots `L1` à `L4`.

---

## 12. Ce que `L5` lègue

- **À `L7`** : quatre refus qui nomment leur lot — `MissionFactory`, `ScenarioMapper`,
  `WizardPrefill` et `MissionProfile.of` — une clé de champ à choisir — c'est un nom de format
  de fichier autant qu'un libellé — le spec dont `ScenarioMission.LunarOrbit` devra être le miroir,
  et `MissionProfile.LUNAR_ORBIT` à créer une fois les onglets posés. Plus une question laissée
  ouverte devant l'écran : ce que `MissionTargetOrbit` montre d'une cible lunaire (§3.2).
- **À `L6`** : rien. Le lot reste indépendant, et `L5` ne touche pas au premier écran.
- **À `MIS-11`** : la branche aller complète, un véhicule qui exécute sa propre combustion, un coast
  paramétré par son corps d'arc, et **la dette ε avec son échéance** — le retour est le premier vol
  où le détecteur de sortie tirera.
- **À `MIS-9`** : le compte de points d'un vol lunaire complet, et la mesure que le préfixe terrestre
  en coûte ~3 200 pour cinquante minutes de vol.
- **À la clôture du chantier** : le §6 pt 10 tranché — l'objectif ne porte pas d'inclinaison — et le
  §6 pt 11 réparé plutôt qu'assumé.
