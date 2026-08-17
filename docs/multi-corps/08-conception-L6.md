# PHY-4 / L6 — Un arc lunaire de bout en bout

Lot **L6** du découpage (`01-decoupage.md` §4), le dernier. Il demande tous les autres et il
hérite de cinq dettes écrites : le verdict visuel de `07-conception-L5.md` §7.7, le
`MissionLoadEvaluator.objectiveMet` que L3, L4 et L5 se sont passé, les sites Terre-en-dur de
`03-conception-L1.md` §4.1, l'écart plan/vol de `04-conception-L2.md` §4.2 et le coût de
conversion non mesuré de L5 §12.2 écart 5.

**Propriété rendue vraie.** PHY-4 tient sur un cas réel.

**Ce que ce lot ne fait pas.** Pas une mission au sens du wizard, pas d'optimiseur de
trajectoire, pas de `TLIBurnStage` de production ni de `LunarOrbitObjective` — c'est `MIS-4` /
`MIS-5` et le §1 du découpage le met hors périmètre. Pas de fenêtre de lancement non plus, et
pas par chance : le §4.1 **construit** l'orbite de parking pour qu'elle convienne, au lieu de
chercher quand elle conviendrait.

> **Le lot qui n'ajoute presque que.** Les deux gates restent verts pour une raison plus forte
> que la mesure : les deux seules modifications de code existant sont d'une ligne chacune, l'une
> identique par identité d'une constante, l'autre par unicité de l'arc (§5).

---

## 1. Inventaire mesuré

Relevé au commit `08ac325`, après le merge de L5. Les chiffres de physique viennent d'une sonde
jetable lancée sur ce commit, dans le style du §5.2 de L2 : mesurer d'abord, épingler ensuite.

### 1.1 Le découpage se contredit sur L6, et c'est ce qui se tranche en premier

Le §4 demande « un TLI injecté analytiquement (seed patched-conic) depuis une orbite de
parking », alors que le §1 met `TLIBurnStage` **hors** PHY-4. Et la fermeture exige « le tracé
visible à l'écran des deux côtés de la bascule », donc une mission que l'application sait
charger, pendant que le même paragraphe écrit « pas une mission au sens du wizard ».

Les trois ne tiennent ensemble que par une porte précise, et **elle existe déjà**.
`MissionEntry(Mission)` — le constructeur legacy sans spec — n'a **aucun appelant dans
`src/main`** ; le wizard (`MissionWizardAppState:166`) est le seul producteur d'entrées. Mais
`MissionPlanOptimizer:71` écrit `entry.spec().map(this::minimizedLoadPlanner)
.orElseGet(this::fixedLoadPlanner)` et son javadoc nomme explicitement « a legacy entry with no
spec ». Une mission construite à la main calcule et se rend donc par l'orchestrateur existant
**sans toucher** au wizard, à `MissionType`, à `MissionSpec` ni à `MissionComposer`.

### 1.2 Deux dettes ne sont pas où on les croyait

**`MissionLoadEvaluator.objectiveMet` n'est pas sur le chemin de L6.** Un seul appelant de
production (`:253`), sur la branche PRECISE, atteignable seulement avec une spec. Une mission
sans spec ne le traverse jamais. La dette est réelle et se ferme quand même (§5.2), mais elle ne
bloque rien ici.

**Ce qui casse vraiment sur un arc lunaire, c'est deux sites et non trente.** Le §2-A du
découpage annonce « une trentaine de sites » ; il en reste 19 occurrences dans 14 fichiers après
L1. Sur le chemin qu'un vol L6 parcourt réellement, il y en a **deux** : `AchievedOrbit.of`
(§5.1) et `objectiveMet` (§5.2). Ne sont **pas** sur le chemin :
`Physics.hohmannTransferDuration` et `sunSynchronousInclination`, `LaunchPlane`,
`EarthMission.getInitialState`, `PropellantBudget`, `StageEndStateDiagnostic`,
`DynamicParameters`, les trois classes de problème CMA-ES, et `MissionHorizon.Revolutions`
(§11).

### 1.3 Le seed patched-conic n'est pas à écrire

Orekit 13.1.1 embarque `org.orekit.control.heuristics.lambert` :

| classe | ce qu'elle donne |
|---|---|
| `LambertSolver(mu).solve(posigrade, nRev, conditions)` | les deux vitesses aux bornes du transfert képlérien |
| `LambertBoundaryConditions(t₁, r₁, t₂, r₂, frame)` | l'énoncé du problème aux limites |
| `LambertDifferentialCorrector.solve(propagateur, cible)` | le **même** problème résolu sous le propagateur numérique, par matrice de transition |

La troisième ligne est ce qui fait tomber la mesure que L2 §4.2 lègue à ce lot comme un
sous-produit : l'écart entre le plan képlérien et le vol perturbé est la différence entre les
`v₁` que les deux rendent, sans expérience dédiée.

### 1.4 Aucune poussée impulsionnelle n'existe dans le dépôt, mais Orekit en a une

Les cinq classes de `maneuver/` et les six étages analytiques construisent tous des
`ConstantThrustManeuver`. Et le dépôt a déjà le patron du changement d'état instantané :
`StageSeparationStage.enter()` fait chuter la masse puis laisse un court coast de stabilisation.
C'est ce patron que le §3.3 reprend, et non `ImpulseManeuver` : à impulsion, Tsiolkovsky est
**exact** et non une approximation, donc appliquer `Δm = m(1 − e^{−Δv/(Isp·g₀)})` dans `enter()`
n'approxime rien et évite un détecteur de déclenchement pour un instant unique.

### 1.5 Le véhicule ne demande pas le catalogue

`Spacecraft` (masse sèche, ergols, propulsion) implémente `Vehicle` à lui seul et
`resolveActiveStage` fonctionne dessus. Une mission L6 porte donc un unique étage translunaire
sans `LaunchConfiguration` ni `PropellantBudget` — ce qui **retire** un site Terre-en-dur de la
liste de L1 §4.1 au lieu de le réveiller.

### 1.6 La visée n'a pas besoin de traverser la SOI, et c'est L4 qui le prouve

L4 §11.2 a mesuré **9,55 m** entre le vol multi-arcs et le même vol en une seule propagation
géocentrique. Le plan peut donc chercher son périlune par `min |r − r_Lune|` dans **une**
propagation géocentrique, et la machinerie de bascule n'est exercée que par la passe
échantillonnée. L'écart entre le plan et le vol est alors les dix mètres déjà chiffrés, pas un
inconnu.

### 1.7 La géométrie, mesurée

Sonde jetable, 2026-03-01 + 400 jours :

| | mesuré |
|---|---|
| Déclinaison géocentrique de la Lune | **[−28,40° ; +28,32°]** |
| Distance Terre-Lune | 356 779 – 406 570 km (identique à L4 §11.2) |
| Δv TLI coplanaire impulsionnel depuis 185 km | **3 135,6 m/s** (7 793,2 → 10 928,8 m/s) |
| Temps de vol d'un demi-Hohmann vers la distance lunaire | **5,02 jours** |
| Chemin parcouru par la Lune pendant ce vol | **411 732 km**, soit **62,9° balayés** |
| Rayon de SOI à cette date | 66 539 km |

Deux conséquences. Les 62,9° chiffrent ce que L4 §11.3 écart 5 avait découvert à ses dépens : la
visée doit **précéder** la Lune de plus de soixante degrés, ce qu'aucune visée sur la position
courante ne peut approcher et ce que Lambert vers `r_Lune(t₁+TOF)` construit d'emblée.

Et **le plan de parking doit contenir la direction de la Lune à l'arrivée**, sans quoi Lambert
facture une composante hors plan très chère : cela impose `i ≥ |δ_Lune|`. Une inclinaison à
28,5° (latitude du Cap) passe **de 0,1°** contre le maximum mesuré de 28,40°, ce qui est
exactement le genre de marge qui casse en silence. **30°** laisse 1,4° même contre le maximum de
tout le cycle de 18,6 ans (28,6°), et c'est cette marge-là — petite — qui justifie la garde du
§7.1.

### 1.8 Le coût d'une propagation translunaire, mesuré

Le test L4 `SoiRoundTripFlightTest.roundTripMatchesTheSingleFrameFlight` — un vol de trois jours
à travers la boucle de tronçons **plus** deux propagations géocentriques de référence — coûte
**0,70 s** dans une suite déjà chaude. Lancé seul, le même test affiche 10,47 s : les 9,8 s
d'écart sont l'initialisation d'`orekit-data.zip` et le préchauffage JIT, pas la propagation.

C'est la mesure qui rend abordable la visée itérée du §4, là où on l'aurait crue trop chère.

### 1.9 Un critère de la fermeture n'a pas de dents, et il faut le dire

Le découpage cite « trajectoire complète (`MissionEphemeris.isComplete()`) ». Sur la forme de
mission de ce lot, **ce critère ne voit pas une troncature**.
`StageChainRunner.endDateResolver` rend `EndDate(…, isStageCutoff = false)` pour le dernier étage
quand la durée vient de l'horizon, et `StageRun.shortfallSeconds:105` rend alors `0.0` sans
regarder l'état. Un coast lunaire coupé — garde de rentrée déclenchée, plafond
`MAX_LEGS_PER_STAGE` atteint — sortirait `isComplete() == true`.

Le critère reste dans le test parce qu'il attrape la levée d'exception. La vraie dent est
ailleurs et le §7.2 la nomme : le dernier échantillon doit être daté à `T_start + horizon`, et la
suite d'arcs doit valoir exactement `[EARTH, MOON]`.

---

## 2. Ce que L6 décide

Cinq décisions, prises dans cet ordre, chacune conditionnant la suivante.

1. **Le vol part d'une orbite de parking injectée, pas du pas de tir**, et atteint l'écran par la
   porte legacy du §1.1. Ni ascension, ni CMA-ES, ni `PropellantBudget` — le découpage écrit
   « depuis une orbite de parking » et cela se lit au sens littéral.
2. **Le périlune est une cible, pas un constat.** Lambert vise un point **décalé** du centre
   lunaire et une sécante sur ce décalage amène le périlune **volé** sur une altitude annoncée
   d'avance. L'assertion du test devient alors un énoncé indépendant de la formule testée, la
   discipline que L4 §7.2 s'est imposée en refusant d'asserter « la date vaut X ».
3. **Le TLI est impulsionnel**, appliqué dans `enter()` sur le patron de
   `StageSeparationStage`. Un seul bouton reste à la sécante, donc une seule boucle.
4. **L'inclinaison de parking est écrite en dur (30°) et seule la ligne des nœuds se dérive de la
   Lune.** Le **plan** se construit donc à n'importe quelle époque sans recherche de fenêtre. **Ce
   n'est pas une fenêtre de lancement déguisée** — il n'y a pas de site au sol — donc le « PHY-4
   n'en dépend pas » du découpage §1 tient. Le **périlune**, lui, n'est pas servi par toutes les
   géométries : §12.4 écart 4 mesure un plancher de 135 km à une époque du mois, et la mission
   refuse celles-là au lieu de voler un plan sous la surface.
5. **La porte d'affichage est une clé de propriété**, `mission.lunarDemo`, et non une entrée de
   menu : zéro surface UI livrée dans le produit, et rien à retirer le jour où MIS-4 donnera au
   transfert lunaire sa vraie porte.

Les alternatives écartées et leurs raisons sont au §8.

---

## 3. La mission, ses deux étages, son véhicule

```
LunarTransferMission extends Mission
  getInitialState(date)   → parking circulaire 185 km, i = 30°, RAAN dérivé de r_Lune(date+TOF)
  gravitationalContext()  → earth().withPerturbers(MOON, SUN)
  getVehicle()            → un Spacecraft (masse sèche, ergols, propulsion) — pas de stack
  getObjective()          → OrbitInsertionObjective(MOON, périlune, périlune, i)
  horizon                 → FixedDuration(4,5 j)   (§3.4)
  stages                  → [ TranslunarInjectionStage, TranslunarCoastStage ]
```

### 3.1 Trois choses tombent gratuitement

`Mission.gravitationalContext()` est déjà surchargeable, donc les perturbateurs Lune + Soleil se
déclarent **une fois sur la mission** et les deux étages en héritent : aucune classe pour ça.

`Spacecraft` implémente `Vehicle` seul (§1.5).

`MissionObjective` est scellée sur le seul `OrbitInsertionObjective`, qui est **déjà** paramétré
par un corps : `body = MOON` suffit et **`LunarOrbitObjective` reste à MIS-5**, comme le
découpage le veut. Il le faut d'ailleurs : `MissionDisplayPanelWidget:216` fait
`getObjective().body().name()` sans garde, donc un objectif nul y lèverait — et avec celui-ci le
panneau affiche `MOON`, ce qui est le premier signe visuel que le lot marche.

### 3.2 `TranslunarCoastStage` n'a qu'un override, et c'est un prix écrit

`soiTransitions → {MOON}`. L4 §3.1 a écarté explicitement de dériver cette déclaration des
perturbateurs — « arme un détecteur sur une mission GEO qui voudrait seulement la perturbation
lunaire » — donc elle ne peut pas venir de la mission et un étage doit la porter. La classe
existe pour cette seule ligne, et c'est délibéré.

### 3.3 `TranslunarInjectionStage` prend la forme de `StageSeparationStage`

Son `enter()` calcule la visée (§4), applique le Δv et la chute de masse de Tsiolkovsky, exacte
pour une impulsion. Son `configure()` pose un `DateDetector` à 60 s de stabilisation. Trois
conséquences :

- `propagateStandalone` **hérite du défaut** `enter(currentState, mission)` et n'est pas à
  écrire — la passe d'optimisation et la passe d'éphéméride appliquent la même impulsion par le
  même code ;
- le `configuredEndDate` existe, donc le contrôle de troncature de `onStageEnd` a prise sur cet
  étage, seul endroit du vol où il en ait (§1.9) ;
- `isPropulsive() = true` fait dériver le ΔV du delta de masse dans le rapport de performance,
  ce qui est le comportement voulu.

L'étage ne déclare aucune transition, donc l'interdiction de `StageLegRunner:143` — un étage
propulsif ne peut pas basculer — n'est pas approchée.

### 3.4 L'horizon vaut 4,5 jours, et le nombre n'est pas prudentiel

L4 §11.2 a chiffré **54 h de séjour** dans la sphère lunaire. Avec un TOF de 4 j, l'entrée en SOI
tombe vers 3,25 j (66 500 km à ~1 km/s de vitesse relative, soit ~18 h avant le périlune) et une
sortie ne peut pas avoir lieu avant ~5,5 j. Un horizon de 6 j rendrait donc la suite d'arcs
`[EARTH, MOON, EARTH]` ou `[EARTH, MOON]` **selon la géométrie du tir** — une assertion instable
pour rien.

À 4,5 j la suite vaut exactement `[EARTH, MOON]`, le périlune (≈ 4,0 j) est franchement à
l'intérieur, et « propagé jusqu'au périlune » se lit au sens littéral.

**Bénéfice de passage** : 4,5 j à `COAST_SAMPLE_STEP` font ~6 545 points contre un budget de
8 192, donc **le polyline ne décime pas**. La trace affichée est la trace volée, sommet pour
sommet, ce qui n'est vrai d'aucune mission existante — L3 §10.2 a mesuré 9 992 points sur la
LEO-400.

---

## 4. La visée : le seed Lambert, la sécante, la lecture du périlune

### 4.1 La construction, dans l'ordre

Le plan de transfert se construit **avant** l'orbite de parking, et c'est ce qui fait qu'aucune
fenêtre n'est cherchée.

1. **TOF** est une constante écrite de la fixture : **4 jours**. Le demi-Hohmann exact vaut 5,02 j
   (§1.7) et un TLI réel vise ~3 j sur une ellipse dont l'apogée dépasse la Lune ; 4 j est le
   compromis entre le Δv et la durée de propagation, et c'est un nombre **à recalibrer** si le Δv
   sort de la fourchette du §7.1.
2. `t₂ = t₁ + TOF`. Le **plan de transfert** est celui qui contient `r₁` et `r_Lune(t₂)`, calculé
   **une fois, à décalage nul**, de sorte que le décalage de visée ne fasse jamais bouger le plan.
3. **L'orbite de parking se déduit de ce plan** : inclinaison 30° écrite en dur, ligne des nœuds
   prise pour que le plan contienne `r_Lune(t₂)`, point d'injection à **l'antipode de la
   projection de `r_Lune(t₂)`** dans ce plan. La géométrie devient quasi-Hohmann, donc le Δv reste
   les ~3 136 m/s mesurés. Aucun degré de liberté de phasage ne subsiste, et c'est voulu.
4. **Point de visée** = `r_Lune(t₂) + δ · n̂`, avec `n̂` unitaire **dans le plan de transfert** et
   **perpendiculaire à la vitesse relative d'arrivée**, orienté du côté du mouvement lunaire. Le
   survol reste dans le plan de transfert et la géométrie reste lisible à deux dimensions ; un
   décalage hors plan aurait basculé le passage vers un survol polaire lunaire sans rien acheter.

   *Ce point disait d'abord « perpendiculaire à `r_Lune(t₂)` », et c'était faux : à certaines
   géométries la vitesse relative est presque parallèle à cette direction, le décalage ne pilote
   alors plus la distance de passage, et le périlune acquiert un plancher — mesuré 3 176 km.
   Corrigé ici, §12.4 écart 3 dit comment on l'a appris.*
5. **`LambertSolver(µ_Terre).solve(true, 0, LambertBoundaryConditions(t₁, r₁, t₂, cible, gcrf))`**
   donne `v₁` ; `Δv = v₁ − v_parking(r₁)`. **C'est ce seed qui est volé, tel quel.**
6. **`LambertDifferentialCorrector.solve(propagateur, cible)`** est appelé **une seule fois, après
   convergence**, et uniquement pour livrer la mesure de L2 §4.2. Il n'est **pas** dans la boucle :
   le point de visée est un paramètre libre que le point 7 accorde sur le périlune *volé*, donc
   atteindre exactement une cible intermédiaire n'achète rien — §12.4 écart 1 chiffre ce que cela
   coûtait.
7. **Encadrement puis bissection sur `δ`** jusqu'à ce que le périlune **volé** atteigne
   `R_Lune + h_visée`, et **refus** si la tolérance n'est pas atteinte. Une sécante a été essayée
   d'abord et elle a rendu un périlune de −53 km, c'est-à-dire un impact présenté comme un plan :
   §12.4 écart 2.

### 4.2 Le coût, à mesurer et non à supposer

La visée vit dans `enter()`, donc elle est calculée **deux fois** — passe d'optimisation puis
passe d'éphéméride — comme tous les étages analytiques du dépôt. Chaque itération de sécante
coûte un solve du correcteur (lui-même quelques propagations de 4 j) plus une propagation de
4,5 j pour lire le périlune. À 0,23 s les trois jours du §1.8, l'ordre de grandeur est **une
dizaine de secondes au total** — et c'est une estimation, que le §7.2 relève au lieu de la
supposer. L5 §10 avait demandé une mesure du même genre et L5 §12.2 écart 5 a dû écrire qu'elle
n'avait pas été prise ; ce lot ne refait pas ça.

### 4.3 Le périlune se lit dans l'éphéméride, et c'est ce qui donne sa valeur à l'assertion

Pour l'**assertion du test** — pas pour la boucle de visée : minimum de `altitudeMeters()` sur
les points dont `arc().body() == MOON`. Une seule assertion prouve alors trois choses à la fois :
que les arcs portent le bon corps, que l'altitude est mesurée contre la forme lunaire (L3 §3.4,
L4 §3.6), et que l'échantillonnage couvre réellement le périlune. Une propagation séparée
n'aurait prouvé que la physique.

**Le prix de ce choix est chiffrable et il est écrit.** Le coast échantillonne à 60 s. Au
périlune, `r̈ = v_p²/r_p − µ_L/r_p²` vaut 2,0 × 10⁻³ km/s² pour `r_p = 1 837 km` et
`v_p = 2,5 km/s`, donc un échantillon décalé de 30 s lit **0,9 km trop haut**. Le minimum
échantillonné **surestime** le périlune de ~0,9 km au pire, et la fourchette annoncée l'absorbe
au lieu de le découvrir : cible **100 km**, fourchette **± 10 km**, un ordre de grandeur au-dessus
du biais.

La **boucle de visée** n'a pas ce biais : elle lit son périlune sur sa propre propagation, avec
un raffinement parabolique sur les trois points encadrant le minimum, exact au mètre.

---

## 5. Les cinq dettes héritées, réglées une par une

### 5.1 `AchievedOrbit.of` — une ligne, et une non-régression par identité

`Constants.WGS84_EARTH_MU` devient `state.getOrbit().getMu()`.

`GravitationalContext.earth()` porte `Constants.WGS84_EARTH_MU` (`:158`) et
`createOptimizationPropagator` fait `setOrbitType(CARTESIAN)` puis `setMu(body.mu())`
(`OrekitService:269-270`). Pour toute mission terrestre c'est donc **le même `double`** et aucun
chiffre existant ne peut bouger — le même argument structurel que l'ensemble vide de
perturbateurs en L2 §4.1 et que le tableau unique en L5 §3.4. Le patron existe déjà dans le
dépôt : `AnalyticGtoInjectionStage:293` lit déjà `state.getOrbit().getMu()`. Le `CARTESIAN`
garantit que `getOrbit()` ne lève pas sur nos états, et la garde « ne lève jamais » de la méthode
reste le filet.

**La mise en garde du javadoc reste vraie et reste écrite** : ce µ est celui du **propagateur**,
`OrbitElements.mean()` rebase délibérément sur celui du fournisseur de potentiel, et rendre l'un
contextuel ne doit pas faire croire que l'autre l'est.

### 5.2 `objectiveMet` — deux lignes, et une garde plutôt qu'un javadoc

Le balayage se restreint aux points du coast final **dont l'arc est celui du dernier point**, au
lieu de tous. Sur une mission mono-arc le corps du dernier point est le seul qu'il y ait :
identique par construction.

C'est préférable à un javadoc de mise en garde. « Un filet silencieux se découvre des mois plus
tard » est la leçon que L4 §4.4 a écrite pour son plafond de tronçons, et une dette documentée
sans garde est ce filet-là. Sans ce correctif, un coast final à deux arcs mélangerait des
altitudes géocentriques (~380 000 km) et sélénocentriques (~1 000 km) : le prédicat ne serait pas
approximatif, il serait absurde.

### 5.3 L'écart plan képlérien / vol perturbé (L2 §4.2)

Sous-produit du correcteur différentiel, §4.1 point 6. Aucune expérience dédiée.

### 5.4 Le coût de la conversion à la construction (L5 §12.2 écart 5)

L6 est **la première trajectoire à deux arcs du dépôt**, donc le premier cas où
`TrajectoryPolyline` construit deux tableaux et appelle Orekit par sommet. Nombre d'appels et
temps relevés (§7.2). L5 avait raison de ne pas mesurer sur une trace synthétique, et tort de
laisser la ligne sans échéance.

### 5.5 ε, la bande morte (L4 §11.3 écart 7) — L6 ne la calibre pas non plus

La trajectoire ne traverse la frontière **qu'une fois** (§3.4), donc elle ne met pas plus la bande
morte à l'épreuve que la fixture rasante de L4. La dette reste ouverte pour MIS-4, avec une
échéance nommée cette fois : une trajectoire **capturée**, celle qui reste dans la sphère.

---

## 6. Ce que L6 ne touche pas

| Site | Raison |
|---|---|
| Tous les étages existants, les manœuvres, les vingt sites de construction | L6 n'ajoute que deux étages neufs |
| Le wizard, `MissionType`, `MissionSpec`, `MissionComposer` | §1.1 : la porte legacy dispense de les toucher |
| `PropellantBudget`, `LaunchConfiguration` | §1.5 : un `Spacecraft` seul est un `Vehicle` |
| `MissionHorizon.Revolutions` et son µ terrestre | §11 : L6 vole un `FixedDuration`, le site n'est pas sur son chemin |
| `Physics`, `LaunchPlane`, `EarthMission`, `StageEndStateDiagnostic`, `DynamicParameters` | §1.2 : hors du chemin d'un vol L6 |
| Les trois classes de problème CMA-ES | Optimisation multi-arcs hors PHY-4 (découpage §1) |
| Le rendu, `TrajectoryPolyline`, `FloatingOriginAppState`, la règle de visibilité | L5 les a livrés ; L6 les **exerce** |
| `nearPlane`, `FAR_MIN`, le troisième viewport | L5 §5.1 et §5.3 ; L6 est ce qui met leur mesure à l'épreuve (§11) |
| `createTestPropagator` | L4 §1.4 : aucun appelant dans `main` |

---

## 7. Les tests

### 7.1 `TranslunarInjectionPlanTest` — unité, sans propagation

- le plan de parking contient `r_Lune(t₂)` ;
- son inclinaison vaut 30° ;
- le point d'injection est à l'antipode de la projection ;
- le Δv rendu par la géométrie quasi-Hohmann vaut **~3 136 m/s à ± 150 m/s**. Assertion
  **indépendante de la formule testée** : elle vient de vis-viva et non de Lambert, ce qui est la
  discipline de L4 §7.2 ;
- une déclinaison lunaire fabriquée au-delà de 30° **lève**. La garde existe parce que la marge
  est de 1,4° sur le cycle de 18,6 ans (§1.7), pas parce que le cas est atteignable en 2026.

### 7.2 `LunarTransferFlightTest` — le test du lot

Dans `simulation/mission/operation/`, à côté de `CentralBodyBaselineTest`. Il vole la mission par
`MissionOptimizer` puis `MissionEphemerisGenerator`, comme l'application, et assert :

- la suite d'arcs vaut exactement `[EARTH, MOON]` ;
- le périlune, lu sur l'arc lunaire, vaut **100 km ± 10 km** (§4.3) ;
- le dernier échantillon est daté à `T_start + 4,5 j` — **la vraie dent** du §1.9 ;
- `isComplete()`, dont le javadoc du test dit qu'il n'attrape que la levée, pour qu'un lecteur ne
  lui prête pas plus ;
- la continuité à la frontière au millimètre, cette fois **à travers l'éphéméride** — donc à
  travers `MissionEphemerisPoint.arc()` et la double émission de L4 §5 — là où L4 §7.4 la
  prouvait à travers le sampler ;
- l'absence de décimation (§3.4).

Il **logue sans asserter** : le Δv du TLI, l'écart plan/vol du correcteur (§5.3), le nombre
d'appels Orekit de la conversion et son temps (§5.4). Discipline de L2 §5.2 — séparer les
contributions rend visible un câblage à moitié faux qu'une tolérance unique avalerait.

### 7.3 Les deux sites corrigés

`AchievedOrbitTest` : le µ lu sur l'état, identique sur un état terrestre, lunaire sur un état
lunaire. `MissionLoadEvaluatorTest` : le cas à deux arcs ajouté, les cas mono-arc existants
inchangés.

### 7.4 Les deux gates

`CentralBodyBaselineTest` à `0.0` sur ses 62 frontières et `MissionPolylineBaselineTest` à
l'identique, à chaque étape. **L6 est le premier lot dont la non-régression est structurelle
parce qu'il n'ajoute presque que** : les deux seules modifications de code existant sont les deux
lignes du §5, l'une identique par identité de la constante, l'autre par unicité de l'arc.

### 7.5 Le verdict visuel

Capture en vue SPACECRAFT et en vue PLANET, des deux côtés de la bascule. C'est le critère que
L5 §7.7 a délibérément reporté ici.

**Il est rendu, et il est concluant** (§12.5).

---

## 8. Les alternatives écartées

**Le vol complet depuis le pas de tir.** Réutiliserait la chaîne d'ascension et exercerait son
CMA-ES avec une queue lunaire. Écarté : fait entrer `PropellantBudget` qu'il faudrait dimensionner
pour un Δv de TLI, coûte des minutes de CMA-ES par exécution, tire tout l'appareillage
`MissionType`/`MissionSpec`/composer/wizard que le découpage exclut, et surtout introduit le
**phasage** — la Lune doit être là quand le TLI s'allume — c'est-à-dire une fenêtre de lancement,
donc `MIS-2`, dont le découpage §1 dit que PHY-4 ne dépend pas.

**Le test seul, verdict visuel reporté à MIS-4.** Le moins cher. Écarté parce que L5 §7.7 a déjà
reporté ce verdict *à L6* en écrivant que c'était une décision et non un oubli : reporté deux
fois, il ne se fait jamais, et PHY-4 se fermerait sans avoir jamais montré une trajectoire
lunaire à l'écran.

**Viser le centre de la Lune.** Le plus court à écrire, mais c'est une trajectoire d'impact : le
périlune képlérien vaut zéro et ce qu'on mesurerait serait une quantité hypersensible sans
signification de mission.

**Viser un point décalé fixe, périlune constaté et épinglé.** Un solve, une propagation, zéro
itération. Écarté : la fourchette annoncée serait la recopie de ce que l'implémentation produit —
le test ne dirait plus que le vol atteint une cible, mais qu'il reproduit son résultat d'hier.

**Poussée finie.** Le patron d'`AnalyticGtoInjectionStage` mot pour mot, aucun mécanisme neuf, et
un vrai véhicule. Écarté parce qu'une poussée finie sous-livre : elle rate la Lune **en énergie
autant qu'en distance**, donc il faudrait deux boutons imbriqués (magnitude et décalage), ~20
propagations par passe et deux passes, plus une sensibilité à la poussée du moteur choisi que la
fixture devrait justifier. Le lot n'a rien à prouver sur la modélisation d'un moteur, et beaucoup
à prouver sur la bascule de SOI.

**Poussée finie sur un étage du catalogue.** Le plus réaliste, mais ramène
`LaunchConfiguration`/`PropellantBudget` dans un lot qui peut s'en passer, et exigerait de
vérifier qu'un lanceur du catalogue tient les ~3 136 m/s depuis 185 km.

**Plan de parking entièrement dérivé de la Lune** (inclinaison **et** nœud). Le plus robuste —
aucune marge à surveiller — mais l'inclinaison de parking cesse d'être un nombre lisible et
devient une sortie de calcul, ce qui rend la fixture moins parlante.

**Époque et éléments entièrement figés.** Le plus reproductible bit à bit, mais la mission
affichée ne volerait correctement qu'à la date programmée — or l'orchestrateur planifie sur
l'horloge de simulation courante (`MissionOrchestratorAppState:194`).

**Une entrée de menu « Lunar transfer (demo) ».** Découvrable sans documentation, ce qui compte si
le verdict visuel doit être reproduit dans six mois. Écarté : livre une entrée de démonstration
en permanence dans le menu du produit (`MissionDisplayPanelAppState:51`, plus la ligne
d'`AppMenuModelTest:125` qui épingle la liste d'ids), que MIS-4 devrait retirer.

**Un `main()` d'outil sous `tools/`.** Le produit resterait intact, mais c'est un troisième point
d'entrée applicatif à maintenir, et il devrait dupliquer tout l'amorçage JME — les deux `main`
existants sont des outils hors rendu, pas des applications 3D.

---

## 9. Ordre d'exécution

1. **Les deux corrections d'une ligne du §5**, seules dans leur commit, gates verts. Aucun code
   lunaire.
2. **`TranslunarInjectionPlan`** — géométrie et seed Lambert, sans mission ni étage. Test §7.1.
   Aucun appelant.
3. **Les deux étages et la mission** (§3). Compilent, aucun appelant en production.
4. **Le test du lot** (§7.2). C'est là que la sécante se calibre et que le périlune entre dans sa
   fourchette.
5. **La porte** : la clé `mission.lunarDemo` et les lignes d'`OrbitLabApplication`.
6. **Le verdict visuel** (§7.5).
7. Suite complète.

L'étape 1 est seule pour la raison qui a tenu à L1, L3, L4 et L5 : si un chiffre bouge, savoir
déjà que ce n'est pas la lecture du µ.

---

## 10. Risques identifiés

**Le seul risque numérique est la convergence de la sécante.** Le périlune est le point focal
d'une hyperbole, donc très sensible au décalage de visée. Si ± 10 km n'est pas atteint en cinq
pas, la réponse n'est pas plus d'itérations mais un décalage initial dérivé du facteur de
focalisation `1 + 2µ_L/(r_p·v_∞²)` — à écrire dans le code, pas à découvrir.

*Le risque s'est réalisé et le remède annoncé n'était pas le bon. Ce n'était pas l'amorce mais la
méthode : une sécante est remplacée par un encadrement et une bissection, et la direction de
décalage a dû changer (§12.4 écarts 2 et 3). Le facteur de focalisation n'entre nulle part —
Lambert fixe la position à `t₂`, pas une asymptote.*

**Le correcteur différentiel peut ne pas converger** sur quatre jours à travers deux
perturbateurs. Repli sur le `v₁` képlérien avec l'écart logué : la sécante extérieure absorbe le
décalage de périlune, donc le repli est bénin — mais il doit être **explicite**, jamais un
silence.

**La visée est calculée deux fois**, passe d'optimisation puis passe d'éphéméride, sur le même
état d'entrée. Elle rendra le même résultat parce qu'elle est déterministe ; si un jour non, plan
et vol divergeraient sans erreur. Le §7.2 compare les deux.

**La marge de 1,4°** sur l'inclinaison de parking : la garde du §7.1 est ce qui empêche le cas
hors marge de rendre un plan faux plutôt que de lever.

**Le trou de `propagateStandalone`** (L4 §9) : la passe d'optimisation ne traverse aucune SOI.
Sans effet ici — le seul étage qui traverse est le coast, qui n'a pas de `propagateStandalone`.
Le trou reste pour `MIS-4`.

**Ce que L6 n'a pas comme risque** : aucun étage existant, aucune force, aucun propagateur, aucun
des vingt sites de construction.

---

## 11. Ce que L6 laisse ouvert

- **ε** reste à calibrer sur une trajectoire **capturée** (§5.5) → `MIS-4`.
- **`MissionHorizon.Revolutions`** garde son µ terrestre. La recette est désormais connue — lire
  le µ sur l'état, §5.1 — et l'appliquer serait un changement hors besoin, ce qu'interdit la
  règle 1 du découpage.
- **Huit sites Terre-en-dur** attendent leur besoin (§1.2). Le §2-A du découpage annonçait « une
  trentaine de sites » à basculer ; la mesure finale de PHY-4 est que **deux** l'ont été en L6, et
  que les autres ne sont pas sur le chemin d'un vol.
- **L'optimisation multi-arcs**, un `TLIBurnStage` de production, `LunarInsertionStage`,
  `LunarOrbitObjective`, la fenêtre de lancement : `MIS-2` / `MIS-4` / `MIS-5`, hors PHY-4.
- **Le troisième viewport reste écarté, et ce n'est plus un pari.** L5 §5.3 l'avait écarté sur un
  calcul de budget de profondeur, sans aucune trajectoire lunaire à lui opposer ; le verdict visuel
  du §12.5 **confirme la mesure sur le premier cas réel**. La question ne rouvre qu'avec deux
  globes dans le même cadre — rendez-vous lunaire, `MIS-6`.

---

## 12. Fermeture — L6 est implémenté

Mesuré le **2026-08-17**, branche `feature_phy4_l6_lunar_arc`, GraalVM 21.0.5.

### 12.1 Le verdict

| | |
|---|---|
| Suite par défaut | **808 tests, 0 échec, 0 erreur**, 17 sautés, 114 classes |
| Diff de production | **10 fichiers, +1 110 / −11** |
| Diff de test | 4 fichiers, +541 |
| Gate L1 `CentralBodyBaselineTest` | vert, 62 frontières à `0.0` |
| Gate L3 `MissionPolylineBaselineTest` | vert à l'identique |
| Étages de production déclarant une transition | **1** — le premier du dépôt |

### 12.2 La physique, mesurée

| | mesuré |
|---|---|
| Δv d'injection | **3 152 – 3 178 m/s** selon l'époque (référence vis-viva 3 139 m/s) |
| Périlune visé / **volé** | 100 km / **100,4 km** |
| Suite d'arcs | **`[EARTH, MOON]`**, une seule traversée |
| Traversée de SOI | **74 h** après l'injection |
| Discontinuité à la frontière, en repère commun | **0,000000 m** |
| Points d'éphéméride / sommets de tracé | **6 545 / 6 545** — aucune décimation |
| Temps de calcul d'un vol complet (deux passes) | **10,8 s** |
| Écart plan képlérien / vol perturbé (L2 §4.2) | **26 320 km** de manque au point de visée, soit **257 m/s** sur la vitesse d'injection |

Le §7.2 est vert dans son intégralité. La conversion à la construction de l'éphéméride (§5.4) n'a **aucun** coût mesurable : les 6 545 sommets tiennent en deux tableaux dont l'un est le tableau existant par identité, et le second n'est construit qu'une fois — il ne ressort pas du bruit des 10,8 s du vol.

### 12.3 Un défaut de L4 que seule une vraie trajectoire pouvait montrer

`StageLegRunner` comparait la date rendue au handler du détecteur et celle que `propagate()` retourne à **`1,0 × 10⁻⁶ s`**, valeur écrite par L4 après avoir mesuré 51 ps sur sa fixture synthétique. Le premier vol translunaire mesure **5,239 × 10⁻⁴ s** — trois ordres au-dessus — et la traversée était alors lue comme une fin de tronçon ordinaire : **un arc au lieu de deux, aucun avertissement, et un coast qui s'arrêtait à 3,3 jours en se déclarant complet** (§1.9 explique pourquoi `isComplete()` ne le voyait pas).

La tolérance est désormais le double du seuil de convergence que le détecteur déclare lui-même, `SoiCrossingDetector.DATE_CONVERGENCE_SECONDS`. La raison est structurelle et non prudentielle : les deux états sont pris à la même racine mais **ré-interpolés séparément**, donc rien ne peut les rapprocher plus que la précision à laquelle la racine est connue. Une tolérance sans rapport avec ce qui fixe l'écart ne peut pas le borner, aussi généreuse qu'elle paraisse.

Le chemin corrigé était **mort** avant ce lot — aucun étage de production ne déclarait de transition — ce qui est exactement pourquoi L4 et L5 pouvaient être verts en le portant.

### 12.4 Six écarts au plan

1. **Le correcteur différentiel est sorti de la boucle de visée.** Le §4.1 point 6 le faisait reconverger `v₁` à chaque essai. Mesuré : il est **redondant** — la boucle extérieure ferme sur le périlune *volé*, donc atteindre exactement un point de visée intermédiaire n'achète rien — il coûtait l'essentiel du temps de calcul, et Orekit lève `NullPointerException` sur certaines géométries. Il ne tourne plus qu'une fois, sur le plan convergé, uniquement pour la mesure de L2 §4.2. Le temps d'un vol est passé de 50,8 s à 10,8 s.

2. **La sécante est devenue un encadrement puis une bissection**, et c'est l'application qui l'a exigé. Chargée à la date de sa propre horloge, la sécante du §4.1 point 7 a rendu un périlune de **−53 km** : un impact calculé et volé comme s'il était un plan. Sa pente varie d'un ordre de grandeur avec l'époque et une sécante amorcée sur une pente unitaire s'égare. La bissection sur un encadrement ne peut pas faire cela ; elle est plus lente par chiffre et elle converge toujours. **Et l'aim refuse désormais** un plan hors tolérance au lieu de le rendre.

3. **La direction de décalage a dû devenir une direction de plan B.** Le §4.1 point 4 la voulait perpendiculaire à la direction Terre-Lune. C'est faux : quand la vitesse relative d'arrivée est presque parallèle à cette direction, glisser le point de visée le long d'elle ne change presque plus la distance de passage, et le périlune acquiert un **plancher** que la visée ne peut pas franchir — mesuré **3 176 km** à une époque. Perpendiculaire à la **vitesse relative**, le décalage *est* la distance de passage au premier ordre, ce qui est la définition d'un point de visée en plan B.

4. **Le plan ne demande pas de fenêtre, le périlune parfois oui.** Le §2 décision 4 et le §4.1 disent qu'aucune fenêtre n'est cherchée, et c'est vrai du **plan de parking** : il se construit pour contenir la Lune à n'importe quelle époque, et le §7.1 le vérifie sur vingt-huit dates. Ce ne l'est pas du **périlune** : même avec le point de visée sur la surface lunaire, le passage au plus près garde un plancher, mesuré à **135 km** à une époque du mois contre une cible de 100 km. Sur cinq époques réparties sur un mois lunaire, **quatre convergent et une refuse proprement**. La mission refuse ces géométries plutôt que de voler un plan sous la surface, et la porte de démonstration avance jusqu'à la première date acceptée — ce qui n'est toujours pas une fenêtre de lancement au sens de MIS-2, faute de site au sol, mais ce n'est plus « aucune fenêtre » et le document ne peut pas le laisser dire.

5. **L'horizon vaut 4,5 jours et non 6**, corrigé en cours d'écriture (§3.4) sur la mesure de séjour de L4 : à 6 jours la suite d'arcs aurait dépendu de la géométrie du tir.

6. **ε n'est toujours pas calibré**, comme le §5.5 l'annonçait. La trajectoire ne traverse qu'une fois.

### 12.5 Le verdict visuel, rendu et concluant

**Verdict rendu le 2026-08-18, application lancée avec `-Dmission.lunarDemo=true` : le tracé se lit
des deux côtés de la bascule.** C'est le critère que L5 §7.7 avait délibérément reporté à ce lot, et
c'est le dernier de la fermeture du découpage à tomber.

Ce qui a été **relevé instrumentalement** en session :

- la porte legacy fonctionne de bout en bout — la mission est chargée, calculée (6 545 points, `complete=true`) et un renderer est créé, sans wizard ni spec ;
- le panneau de missions affiche **« Translunar transfer / MOON »**, donc `OrbitInsertionObjective(MOON, …)` traverse toute la couche UI (§3.1) ;
- et le signe le plus parlant : la télémétrie affiche **66 112 km** d'altitude à MET 04:48 sur l'arc terrestre, puis **11 276 km** à MET 94:35 — après la traversée de 74 h. **L'altitude affichée a changé de corps de référence à la frontière d'arc**, dans l'application qui tourne. C'est la chaîne L3 §3.4 → L4 §3.6 → L5 vérifiée sur un vol réel et non sur une fixture.

Ce qui n'a **pas** été obtenu par l'automate : les deux cadrages eux-mêmes. Amener la caméra en vue SPACECRAFT puis en vue PLANET demande un pilotage interactif qui n'a pas pu être conduit de façon fiable en aveugle, et les captures prises ici montrent l'application et sa télémétrie plutôt que le ruban dans le cadre voulu. **Le cadrage et le jugement sont ceux de l'utilisateur**, et la distinction est écrite pour qu'un lecteur sache lequel des deux relevés est instrumenté et lequel est un verdict de l'œil.

**Conséquence non prévue, et qui dépasse ce lot.** Le §11 laissait ouvert que cette capture « confirme ou dément » la mesure de L5 §5.3 — laquelle avait écarté le troisième viewport sur un calcul de budget de profondeur, en l'absence de toute trajectoire lunaire à lui opposer. Le verdict étant concluant, **cette mesure est confirmée sur le premier cas réel** : un globe et un trait tiennent dans la near viewport à l'échelle Terre-Lune, et ni reverse-Z, ni depth log, ni troisième viewport n'ont été nécessaires. La question ne rouvrira qu'avec deux globes dans le même cadre — rendez-vous lunaire, `MIS-6` — exactement là où L5 §5.3 l'avait placée.

### 12.6 Une fragilité héritée, trouvée et non corrigée ici

Les deux gates échouent **de façon déterministe** quand `SoiCrossingDetectorTest` tourne avant eux dans le même JVM : trois assertions rouges, écarts de dernier bit (~3 × 10⁻⁷ m en position, ~5 × 10⁻¹⁴ s en temps) là où L1 §5.5 exige `0.0`. Reproduit **sur `main` au commit `08ac325`**, donc antérieur à ce lot. Invisible dans la suite complète, qui fork plusieurs JVM et les sépare.

Le mécanisme probable est la réorganisation des caches temporels partagés d'Orekit (éphéméride JPL, historique EOP) par une propagation lunaire de six jours, après quoi une propagation terrestre interpole depuis un autre voisinage. **Ce n'est pas de la physique et ce n'est pas à L6 de le corriger** — c'est la fiabilité de l'instrument de mesure, et la corriger ici violerait la règle 1 du découpage. Ouvert en [`BUG-7`](../bugs.md#bug-7--les-gates-de-non-régression-tombent-quand-un-test-lunaire-les-précède-dans-le-même-jvm), avec la commande de reproduction et les trois issues possibles.

Piège de méthode au passage, qui vaut d'être écrit : relancer `./gradlew test --tests …` avec le même filtre après un succès rend **`UP-TO-DATE`** et n'exécute rien. Plusieurs « verts » de rassurance n'étaient que cela. Toute mesure de reproductibilité doit passer par `cleanTest`.

### 12.7 Ce que L6 n'a pas eu besoin de faire

Aucun étage existant, aucune manœuvre existante, aucun des vingt sites de construction de L1, aucun code de rendu. Le wizard, `MissionType`, `MissionSpec` et `MissionComposer` n'ont pas bougé d'une ligne. Les deux seules modifications de code existant hors correctif de L4 sont les deux du §5, l'une identique par identité de la constante, l'autre par unicité de l'arc — et les deux gates le prouvent.

**PHY-4 est fermé.** Les six lots sont livrés, les deux gates sont verts, la trajectoire lunaire
existe, elle est juste, et elle se voit.
