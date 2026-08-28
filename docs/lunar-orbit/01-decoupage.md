# MIS-5 — Mise en orbite lunaire (LOI) — découpage

Item roadmap : `MIS-5` (★5 ◆3 M), phase 5. Ce document ne conçoit pas en détail : il
**découpe**. Chaque lot y est défini par la propriété qu'il rend vraie, par ce qu'il consomme,
par ce qu'il produit et par le test qui le ferme.

Il fait suite à [`lunar-flyby/01-decoupage.md`](../lunar-flyby/01-decoupage.md), dont le §7 lègue
ce chantier en une phrase — « il ne reste qu'un étage et l'extension de `FlybyObjective` ». Le §2
ci-dessous corrige cette phrase sur quatre points, et la cotation ◆3 de la fiche avec.

---

## 1. Périmètre

**Dans `MIS-5`** — ce que les huit lots du §4 rendent vrai :

- le coast borné par la sphère d'influence, dans les deux passes ;
- les deux mesures qui supposent la Terre en dur — le rayon soustrait à une apside, le µ d'une
  période ;
- un orbiteur lunaire **propulsé** au catalogue, et son budget d'ergols ;
- le coast d'approche sélénocentrique et la combustion d'insertion finie centrée ;
- la mission du produit, volant **du sol à une orbite lunaire circulaire** ;
- des onglets au premier écran du wizard, sans quoi la carte de la mission ne rentre pas ;
- sa création au wizard ;
- la calibration de ε sur la **première trajectoire capturée du dépôt**, échéance nommée par
  `PHY-4 / L6` §5.5 et léguée ici.

**Hors `MIS-5`**, et à ne pas y laisser glisser :

1. **Le ciblage en plan B**, donc l'inclinaison lunaire visée (§2.2 pt 2).
2. **L'orbite lunaire elliptique.** Un deuxième paramètre et une deuxième bande de mérite, pour
   une capture que la combustion unique atteint déjà.
3. **La LOI fractionnée.** LRO, Chandrayaan-2 et Danuri découpent la leur en trois à cinq
   combustions parce que leur moteur est petit ; l'orbiteur du catalogue en porte un dimensionné
   pour une seule, et c'est un choix de catalogue écrit comme tel (§6 pt 4).
4. **Le champ de gravité lunaire non sphérique.** `OrekitService` ne monte un champ harmonique que
   pour la Terre, et son javadoc dit pourquoi — demander un champ lunaire à `GravityFieldFactory`
   rendrait silencieusement un champ terrestre.
5. **L'optimisation CMA-ES**, pour une raison plus forte que celle de `MIS-4` : le Δv d'insertion
   est entièrement déterminé par l'hyperbole d'arrivée et par l'orbite visée. Il n'y a pas de
   degré de liberté à explorer, seulement une sécante à faire converger.
6. **La réparation de `CoastingStage`.** Le refus de `MIS-4 / L4` §1.1 tient : tous les coasts du
   dépôt s'effondrent de la même façon en passe optimiseur, et les déplacer déplacerait les
   références d'ascension réenregistrées par `MIS-7`. `MIS-5` répare **un** coast — le sien — et
   par sa frontière.
7. **La généralisation du seed de Lambert.** Toujours `MIS-6` : ce chantier n'apporte pas plus que
   `MIS-4` le deuxième consommateur qui en fixerait la forme.
8. **Le retour vers la Terre** (`MIS-11`) et la descente vers le sol lunaire.

**La fenêtre de lancement est hors périmètre parce qu'elle est déjà bonne.**
`LunarLaunchWindowProblem` prend `(site, parkingAltitude, periluneAltitude, vehicle,
massAtInjection)` : le périlune visé *est* l'altitude d'orbite, et son `confirm()` vole
l'injection pour vérifier qu'un tir à cette date atteint ce périlune — exactement le verdict dont
`MIS-5` a besoin. Elle est réutilisée **sans une ligne**, seule la masse à l'injection change
puisqu'elle inclut désormais les ergols de l'orbiteur.

---

## 2. État des lieux

### 2.1 Ce que `MIS-4` laisse, et qui vole

| Brique | Fichier | Mesure |
|---|---|---|
| Mission du sol au survol | `mission/operation/LunarFlybyMission.java` | 204 l., parking 400 km → périlune 100 km, horizon 7 j |
| Plan d'injection (Lambert, visée, calibration finie) | `mission/maneuver/TranslunarInjectionPlan.java` | 1 370 l., ToF 4 j, angle 170°, bissection à ±1 km |
| Combustion d'injection centrée | `mission/stage/TLIBurnStage.java` | 200 l., poussée finie |
| Coast de parking s'arrêtant à l'allumage | `mission/stage/ParkingCoastStage.java` | 111 l. |
| Coast translunaire avec bascule de sphère | `mission/stage/TranslunarCoastStage.java` | 38 l. |
| Fenêtre lunaire | `mission/window/problem/Lunar*` | problème + planificateur + requête |
| Objectif de survol et son évaluateur | `mission/objective/FlybyObjective.java`, `runtime/ObjectiveEvaluator.java` | sélection **par corps** |
| Wizard lunaire | `ui/mission/wizard/MissionProfile.java` (`LUNAR`), `catalog/Payloads.java` (`LUNAR_PROBE`) | carte `WINDOWED`, `PayloadDomain.LUNAR` |
| Vols épinglés | `LunarFlybyFlightTest.java` | 419 l., périlune **100,2 km** (profil plein) et **99,8 km** (profil dimensionné) |

**Ce que `MIS-5` hérite est donc une chaîne complète jusqu'au périlune**, volée deux fois sur deux
profils de masse, avec sa fenêtre, son objectif et son wizard. Ce qui manque n'est pas le voyage :
c'est l'arrivée.

### 2.2 Quatre points où la fiche du roadmap est fausse ou périmée

La fiche `MIS-5` du roadmap tient en trois lignes :

> `LunarInsertionStage` (burn rétrograde à l'arrivée), `LunarOrbitObjective` (altitude de périlune,
> inclinaison lunaire). L'essentiel du coût est dans `MIS-4` ; ici on ajoute un stage et un
> objectif.

1. **`LunarOrbitObjective` n'existera pas**, ce que le découpage `MIS-4` §2.2 pt 3 annonçait déjà —
   `OrbitInsertionObjective` prend un `SolarSystemBody`. Mais la raison est plus forte que
   l'économie d'un record : `MissionLoadEvaluator.objectiveMet` note le min et le max d'altitude du
   coast terminal **restreints à l'arc dans lequel il finit** (`finalCoastArcBody`). Sur une
   mission capturée cet arc est `MOON`, donc un objectif d'insertion note nativement le périlune et
   l'apolune. Et la fiche a le sens exactement inversé quand elle parle d'« étendre
   `FlybyObjective` » : `ObjectiveEvaluator.flybyMet` **refuse** quand
   `lastOnTheArc <= closestApproach` — une garde écrite pour attraper un survol tronqué, et une
   orbite circulaire est à son propre minimum pour toujours. Un `FlybyObjective` est, par
   construction, incapable de noter une capture.

2. **L'inclinaison lunaire n'est pas atteignable.**
   [`TranslunarInjectionPlan:1320`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/maneuver/TranslunarInjectionPlan.java)
   construit sa direction de visée comme `cross(normal, relative)`, où `normal` est la normale au
   **plan de transfert**. Le vecteur de visée est donc contraint dans ce plan : la visée a **un
   seul degré de liberté scalaire**, intégralement dépensé sur l'altitude de périlune. L'hyperbole
   d'arrivée est par conséquent quasiment dans le plan de transfert, et l'inclinaison de l'orbite
   lunaire est ce que la géométrie délivre — fixée par la latitude du site (`i = φ`, tir due east)
   et par l'époque. La viser demande un second degré de liberté, c'est-à-dire un ciblage en plan B,
   et cela ferait de la classe la plus délicate du dépôt le cœur du chantier.
   **Décidé : l'inclinaison est subie, mesurée et rapportée.**

3. **« Un stage et un objectif » sous-estime de beaucoup.** Il faut aussi une charge utile lunaire
   **propulsée** (§2.3 pt 1), l'arc porté à travers les frontières d'étage (§2.3 pt 2) et une
   marche d'étages qui traverse la sphère (§2.3 pt 3). Aucun des trois n'est optionnel, et deux
   touchent le socle partagé par toutes les missions. **La cotation ◆3 est optimiste ; ◆4 est
   juste.**

4. **« L'essentiel du coût est dans `MIS-4` » est vrai du voyage et faux du reste.** Le transfert
   est acquis. Ce qui ne l'est pas, et que `MIS-4` n'avait aucune raison de construire, est tout ce
   qui se passe **après** une bascule de sphère d'influence : aucune mission du dépôt n'a jamais eu
   d'étage après elle.

### 2.3 Six mesures qui décident du découpage

**1. Aucun étage lanceur ne peut exécuter la LOI.** `maxCoastDuration` vaut **7 200 s** pour le S2
Falcon Heavy et **21 600 s** pour l'ULPM d'Ariane 62. Le coast translunaire jusqu'au périlune
mesure ~3,9 j, soit **337 000 s** — 47 fois la limite du S2, 16 fois celle de l'ULPM. Ce n'est pas
un détail de modèle : `MissionComposer:208` applique déjà ce refus (`canCoastFor`) sur la chaîne
haute, et un ergol cryogénique ne tient pas quatre jours. **Conséquence : la LOI revient à la
charge utile**, exactement comme la circularisation GEO, et `MissionType.LUNAR_ORBIT` doit déclarer
`requiresPayloadPropulsion = true`. `Payloads.LUNAR_PROBE` étant **inerte** (2 t sec, pas de
moteur), elle ne peut pas voler cette mission : il faut un modèle de plus.

**2. L'arc ne survit pas à une frontière d'étage.** `MissionStage.gravitationalContext(mission)`
rend le contexte **de la mission** — terrestre — et `StageLegRunner.fly` y convertit l'état
d'entrée de chaque étage. En `MIS-4` la bascule a lieu *dans le dernier étage*, donc rien ne l'a
jamais montré. En `MIS-5` l'étage d'insertion et le coast terminal sont tous deux après la
bascule : sans déclaration lunaire explicite, l'état repart en GCRF et la mission finit mesurée
contre la Terre. **Conséquence : les trois derniers étages déclarent le contexte lunaire**, ce qui
suffit — `ArcTransition.convert`, appelé en tête de `fly`, fait la conversion sans une ligne de
plus.

**3. La marche d'étages ne vole pas le coast translunaire du tout.** `CoastingStage` ne redéfinit
pas `propagateStandalone` — la dette que le découpage `MIS-4` §6 pt 6 lègue nommément ici.
Inoffensive tant que le coast est le dernier étage ; en `MIS-5` l'étage d'insertion serait entré
**à la coupure du TLI** : géocentrique, quatre jours trop tôt, 400 000 km trop loin.
**Conséquence : c'est le blocage structurel du chantier**, et il n'est pas séparable du point
suivant.

**4. Un `propagateStandalone` de coast translunaire n'a de sens que si le coast a une fin, et sa
fin est la frontière.** Sur `MIS-4` le coast est terminal et ouvert : il n'y a rien à voler. Les
deux passes s'arrêtant au même endroit sont **une seule propriété**, et font un seul lot (`L1`).
Le mécanisme manque : `StageLegRunner` détecte la traversée, coupe la jambe, puis **poursuit sa
boucle** dans le nouveau contexte. Et le drapeau `endDateIsStageCutoff` doit suivre, sans quoi un
coast borné à J+5 mais arrêté à J+3,1 tombe dans `shortfallSeconds()` et déclare l'éphéméride
entière incomplète — ce que `MissionLoadEvaluator` traduit en « aucune charge d'ergols faisable ».

**5. L'échantillonnage interdit à l'étage d'insertion de posséder son approche.**
`MissionStage.sampleStepSeconds` rend `BURN_SAMPLE_STEP = 1 s` pour tout étage propulsif, et
**rien ne le redéfinit dans le dépôt**. Un étage d'insertion bâti sur le patron de
`AnalyticApogeeCircularizationStage` — qui possède déjà ~5 h de coast vers son apogée — serait
échantillonné à la seconde sur l'approche sélénocentrique, estimée à 16–18 h (la moitié entrante
du séjour de 32 à 36 h mesuré par `MIS-4 / L0`, que la mesure 3 de `L0` confirmera) :
**~64 800 points**, contre 14 467 pour le vol `MIS-4` entier. Redescendre l'étage à 60 s
réglerait l'approche et
ruinerait la combustion (~290 s → 5 points). **Conséquence : la coupure est obligatoire**, et elle
rend la chaîne symétrique de celle du TLI — un coast qui s'arrête à l'allumage, puis une
combustion centrée.

**6. La grille du premier écran du wizard est pleine, exactement.**
[`StepMissionType`](../../src/main/java/com/smousseur/orbitlab/ui/mission/wizard/step/StepMissionType.java)
pose `CARDS_PER_ROW = 3` cartes de 256 px avec 16 px de gouttière : **800 px pour 816 px de
contenu**, soit 16 px de marge — 6 % d'une carte, donc pas de quatrième colonne. En hauteur, deux
rangées de 176 px et leurs trois gouttières de 12 px font 388 px certains, ~421 avec le titre et
le sous-titre, sur les **468** disponibles ; une troisième rangée en ajoute 188. **La septième
carte déborde**, d'au moins 108 px et d'environ 141 — la conclusion ne dépend pas des métriques
exactes des deux libellés — et la fenêtre du wizard est fixe à 880 × 660. Le javadoc du champ le
dit sans le savoir : « six profiles therefore lay out as 3 + 3, and the grid comes out square ».
**Conséquence : un lot d'onglets, avant la carte.**

### 2.4 Le Δv et la combustion, calculés

Depuis un transfert de 4 j, `v∞ ≈ 1,1 km/s` — recalé sur Apollo 11, dont la LOI-1 a coûté 889 m/s
vers 111 × 314 km. Au périlune de 100 km, `v_hyp = 2,566 km/s` contre `v_circ = 1,634` :

| | valeur |
|---|---|
| Δv d'insertion, circularisation directe à 100 km | **≈ 930 m/s** (Apollo total : 937) |
| Période lunaire à 100 km | **7 067 s** (1 h 58) |
| Ergols pour un orbiteur de 2 t sec à Isp 320 | **≈ 690 kg**, masse au feu 2 690 kg |
| Durée avec un moteur d'AKM du catalogue (400 N) | **5 450 s — 77 % d'une révolution** |
| Poussée pour tenir la combustion sous 5 % d'une révolution (353 s) | **≈ 6,2 kN**, soit 2,3 m/s² |

Le dernier chiffre est le rapport poussée-masse d'Apollo (2,03 m/s²) et celui de Chang'e-3
(7,5 kN pour 3 780 kg, 1,98 m/s²). Il n'est pas celui d'un orbiteur lunaire moderne, et le §6 pt 4
l'écrit comme un choix. **Ces cinq lignes sont un calcul, pas une mesure** : la mesure 2 de `L0`
les remplace.

---

## 3. Principe du découpage

Les quatre règles de `MIS-4`, reprises telles quelles parce qu'elles y ont tenu du premier au
dernier lot.

1. **Un changement de comportement à la fois.**
2. **Chaque lot se ferme sur un test exécutable**, pas sur une revue.
3. **Rien n'est branché avant d'être testé seul.**
4. **La physique avant la mission, la mission avant l'UI.**

**Ce que `MIS-5` ajoute n'est ni le voyage ni la physique multi-arcs.** C'est ce qui se passe
**après** une bascule de sphère d'influence — un domaine où aucune mission du dépôt n'a jamais eu
d'étage.

**Le socle vient en premier, et c'est un arbitrage assumé.** `L1` livre un crochet dont le premier
consommateur de production n'arrive qu'en `L5`. L'alternative — un chantier de socle séparé — a été
écartée pour une raison écrite dans le dépôt : `StageLegRunner.BOUNDARY_STOP_TOLERANCE` a été fixé
à `1,0e-6` sur une fixture synthétique mesurée à **51 ps**, et le premier vol translunaire réel a
mesuré **524 µs** — sept ordres de grandeur, une bascule silencieusement manquée, un coast qui se
déclarait complet. Ce que `L1` a et qu'un chantier de socle n'aurait pas, c'est de se fermer sur un
**vol réel** : la chaîne `MIS-4` déclarant sa frontière est littéralement les trois premiers jours
de la chaîne `MIS-5`, avec les chiffres épinglés de `MIS-4` en amont pour dire que rien n'a bougé.

**Contrainte de méthode**, valable pour tout le chantier : les tests d'optimisation et les vols de
plusieurs jours sont lents, et **c'est l'utilisateur qui les lance**. Aucun lot ne se ferme sur une
exécution de `./gradlew test` faite par l'assistant.

---

## 4. Les lots

| Lot | Ce qu'il rend vrai | Change le comportement ? | Ce qu'il touche |
|---|---|---|---|
| **L0** | La baseline est mesurée | non — zéro ligne de production | rien |
| **L1** | Le coast **s'arrête à la sphère**, dans les deux passes | oui, sur déclaration seulement | `runtime/`, `MissionStage`, `stage/` |
| **L2** | Une apside et une période se lisent **sur le corps de l'arc** | non (identique sur état géocentrique) | `OrbitElements`, `MissionHorizon`, `DynamicParameters` |
| **L3** | Un orbiteur lunaire **existe et se dimensionne** | non (additif) | `catalog/`, `vehicle/`, `MissionType` |
| **L4** | Les deux étages volent **seuls** | non (additif) | `stage/` |
| **L5** | La mission vole **du sol à l'orbite lunaire** | oui, c'est la livraison | `MissionSpec`, `MissionComposer`, `operation/` |
| **L6** | Le premier écran du wizard **passe à l'échelle** | oui, surface utilisateur | `ui/mission/wizard/step/` |
| **L7** | La mission **se crée au wizard** | oui, surface utilisateur | `ui/mission/wizard/` |

### L0 — Baseline mesurée

Six mesures, aucune ligne de production. Chacune décide d'un lot.

1. **L'inclinaison lunaire délivrée**, sur les cinq époques d'une lunaison que `MIS-4 / L0` a déjà
   balayées. Décide si « subie » est une propriété acceptable ou une loterie : quelques degrés de
   dispersion, `L5` la journalise et c'est fini ; 0 à 90°, il faut l'écrire au wizard comme une
   conséquence de la date.
2. **`v∞` et le Δv d'insertion réels.** Les 930 m/s du §2.4 sont un calcul recalé sur Apollo, pas
   une mesure sur cette chaîne. Dimensionne l'orbiteur en `L3`.
3. **La durée entrée-de-sphère → périlune.** `MIS-4 / L0` donne le séjour (32 à 36 h) et l'entrée
   (3,08 à 3,16 j), jamais la moitié entrante. C'est ce que `LunarApproachCoastStage` volera, et
   donc le nombre de points qu'il produit à 60 s.
4. **Le coût actuel de l'échantillonnage à 1 s**, compté sur un vol GEO dont l'étage de
   circularisation possède déjà ~5 h d'approche à la seconde. Chiffre ce que la coupure du §2.3
   pt 5 évite — et dit si le profil GEO a un problème que ce chantier n'a pas à régler.
5. **La tenue de l'orbite lunaire** sur douze révolutions, en masse ponctuelle perturbée par la
   Terre et le Soleil. Si le périlune respire de 20 km en un jour, la bande de mérite de `L5` ne
   peut pas être plus serrée.
6. **Les sites Terre-en-dur sur le chemin de `MIS-5`.** Lecture du graphe d'appels, sans exécution,
   à la façon de la mesure 2 de `MIS-4 / L0` : reprendre les onze sites recensés par
   `multi-corps/03-conception-L1.md` §4.1 et les ranger pour cette chaîne — hors chemin, terrestre
   légitime, ou appliqué à un arc lunaire. **Deux verdicts de `MIS-4 / L0` étaient conditionnels et
   `MIS-5` casse leurs conditions** ; trois entrées sont déjà faites, écrites au §4 / `L3` et au §6
   pt 11. Peut déplacer du travail de `L5` vers `L2` ou `L3`, ce qu'une baseline sert précisément à
   découvrir tôt.

**Ferme sur** : une sonde jetable et un relevé écrit, dans le style de
[`lunar-flyby/02-baseline-L0.md`](../lunar-flyby/02-baseline-L0.md). Rien n'est commité dans
`src/main`.

### L1 — Le coast borné par la sphère

Une seule propriété : **la traversée d'une sphère d'influence termine l'étage qui l'a déclarée, et
les deux passes s'arrêtent au même endroit.**

- `MissionStage` gagne un prédicat — une traversée déclarée termine-t-elle l'étage, ou le
  coupe-t-elle seulement en jambes ? **Faux par défaut**, donc `MIS-4` et tout le reste du dépôt
  sont inchangés par construction, et non par mesure.
- `StageLegRunner` rend la main au lieu de poursuivre sa boucle, et marque
  `endDateIsStageCutoff = false` : voir §2.3 pt 4 pour ce qui arrive sinon.
- `TranslunarCoastStage.propagateStandalone` s'arrête à la même frontière, avec le même détecteur.

**Ferme sur** : un vol depuis le sol qui s'arrête à la frontière à 3,08–3,16 j ; les deux passes
d'accord à `BOUNDARY_STOP_TOLERANCE` ; `isComplete()` vrai ; et `LunarFlybyFlightTest` inchangé au
chiffre près, puisque `MIS-4` ne déclare pas le prédicat.

### L2 — Les deux mesures terrestres en dur

- `OrbitElements.elementsOf` soustrait `RE` à ses apsides. Le rayon vient du corps de l'arc. Sans
  quoi une orbite lunaire est rapportée à **4 641 km** près — `MIS-4 / L4` §11 l'annonçait
  exactement.
- `MissionHorizon.Revolutions.keplerianPeriodOf` construit avec `Constants.WGS84_EARTH_MU` : une
  révolution lunaire en sortirait courte d'un facteur **9,0**. Le µ vient de l'état, quand
  `SpacecraftState.isOrbitDefined()` (vérifié présent en Orekit 13.1.1), avec repli sur la
  constante — ce qui couvre le cas PVA absolu que le javadoc actuel cite comme motif de ne pas lire
  `getOrbit()`.
- `DynamicParameters:135` fait le même calcul pour le libellé du wizard.

**La non-régression est structurelle et non mesurée** : `GravitationalContext.earth()` porte
`Constants.WGS84_EARTH_MU` **lui-même**, donc lire le µ de l'état rend la même valeur au bit près
sur tout état géocentrique. C'est ce qui distingue ce lot d'un refactor à mesurer.

**Ferme sur** : des tests unitaires sur un état sélénocentrique — apside comptée depuis 1 737 km,
période 7 067 s — et l'égalité exacte sur un état géocentrique.

### L3 — L'orbiteur au catalogue et son budget

- `PayloadModel LUNAR_ORBITER` : masse sèche, capacité d'ergols et propulsion dimensionnées sur la
  mesure 2 de `L0`, avec une combustion visée sous ~5 % d'une révolution lunaire.
  `PayloadDomain.LUNAR`, comme la sonde inerte.
- `MissionType.LUNAR_ORBIT` déclare `requiresPayloadPropulsion = true`, ce qui fait travailler
  `Payloads.forMissionType` : la sonde inerte refusée, l'orbiteur retenu. Les deux `switch`
  exhaustifs sur `MissionType` — `Payloads.domainOf` et `MissionHorizon.defaultFor` — signalent
  eux-mêmes qu'il faut les compléter, ce que leurs javadocs annoncent depuis `MIS-4`.
- `PropellantBudget.loadsForLunarOrbit` rend `(launcherLoads, insertionLoad)` façon `GeoLoads`.
  **C'est le premier dimensionnement non terrestre de cette classe**, et son propre commentaire
  l'annonce mot pour mot : « *It becomes contextual when a mission has to be sized around another
  body* ». `MU` et `RE` y sont terrestres.

**Ferme sur** : tests de catalogue (la sonde refusée, l'orbiteur retenu) et de budget (la charge
d'insertion contre le Δv en forme close, et le refus quand la capacité du moteur ne suffit pas).

### L4 — Les deux étages, testés seuls

- **`LunarApproachCoastStage`**, jumeau de `ParkingCoastStage` : sélénocentrique, non propulsif,
  il s'arrête à l'allumage. Le temps au périastre se lit sur l'anomalie hyperbolique de son état
  d'entrée ; l'avance à l'allumage est la demi-durée de combustion, en forme close depuis
  Tsiolkovski et la poussée de l'étage actif. Comme son jumeau, il redéfinit `propagateStandalone`.
- **`LunarInsertionStage`** : propulsif, combustion à poussée constante rétrograde centrée sur le
  périlune, **calibrée par sécante sur ce qui sera réellement volé** — la leçon que `MIS-4 / L6` a
  payée quatre fois, et le patron de `AnalyticApogeeCircularizationStage`, qui itère sur une
  échelle de la vitesse visée et mesure l'orbite d'après.

Tous deux déclarent le contexte lunaire par `gravitationalContext(mission)`, ce qui suffit :
`StageLegRunner.fly` convertit déjà l'état d'entrée (§2.3 pt 2).

**Ferme sur** : une hyperbole sélénocentrique **fabriquée en test** — aucune mission neuve, aucun
vol de quatre jours. L'avance à l'allumage contre le périastre réellement volé ; le Δv commandé
contre l'impulsionnel, et l'écart journalisé ; les apsides atteintes dans la bande que la mesure 5
de `L0` fixe ; et le refus explicite d'une hyperbole dont le périastre est sous la surface.

### L5 — La mission du produit

`MissionSpec.LunarOrbit`, la branche du `switch` de `MissionComposer.compose`, `LunarOrbitMission`
et sa chaîne :

```
ascension → AnalyticParkingInsertionStage(400 km)
          → ParkingCoastStage        → TLIBurnStage
          → StageSeparationStage(S2)          ← active la propulsion de la charge utile
          → TranslunarCoastStage     (finit à la frontière de la sphère)
          → LunarApproachCoastStage  (sélénocentrique, finit à l'allumage)
          → LunarInsertionStage      (combustion centrée sur le périlune)
          → CoastingStage            (sélénocentrique, terminal)
```

Contexte lunaire déclaré sur les trois derniers étages. Horizon `Revolutions`, douze tours par
défaut : 23,6 h d'orbite lunaire après le transfert, soit un vol total de ~5 j ≈ **7 235 points** à
la cadence mesurée par `MIS-4 / L0` — **sous le budget de 8 192 sommets de `TrajectoryPolyline`**.
`MIS-5` récupère donc la trace sommet pour sommet que `MIS-4` avait perdue (son §6 pt 12).

Le largage du S2 est placé juste après le TLI, exactement où `GEOMission` place le sien après
l'injection GTO, et pour la même raison : `resolveActiveStage` résout par la masse, donc rien ne
rend la propulsion de la charge utile active avant.

Objectif : `OrbitInsertionObjective.circular(MOON, altitude, i)`. Ce que `i` porte est une question
ouverte, tranchée ici sur la mesure 1 de `L0` — voir §6 pt 10.

La mission est construite depuis une `MissionSpec.LunarOrbit` bâtie en test : **aucun wizard**.

**Ferme sur** : un vol du sol à l'orbite lunaire, arcs `[EARTH, MOON]` — deux et non trois, la
mission ne ressortant plus de la sphère ; `isComplete()` ; l'objectif satisfait ; et **ε calibré
sur la première trajectoire capturée du dépôt**, l'échéance que `PHY-4 / L6` §5.5 attend depuis
deux chantiers.

### L6 — Les onglets du step type

Le premier écran du wizard passe d'une grille de six cartes à des onglets par domaine, **à
catalogue de profils inchangé** : six cartes, deux onglets — Terrestre 5, Lunaire 1. L'onglet
terrestre tient ses cinq cartes en 3 + 2, c'est-à-dire dans la hauteur exacte de la mise en page
d'aujourd'hui. Aucune mission nouvelle, aucun changement dans `simulation/`.

Deux points relevés, à arbitrer au moment de coder :

- **`Lemur` fournit `com.simsilica.lemur.TabbedPanel`** (vérifié dans le jar 1.16.0), mais le dépôt
  n'en a jamais utilisé et construit ses propres composants stylés — `SelectableCard`, `Badge`,
  `PopupList`, `ProgressBar`. Une bande d'onglets maison au style `FormStyles` est plus probable
  que l'adoption du widget brut.
- **La taxonomie existe déjà.** `Payloads.domainOf(MissionType)` répond exactement à « où vole une
  mission de ce type » et rend `PayloadDomain.EARTH` ou `LUNAR` ; son javadoc explique même
  pourquoi elle vit là plutôt que sur `MissionType` (un cycle de paquets). Elle est privée : la
  rendre publique et faire lire `MissionProfile` dessus donne **une seule source de vérité**,
  plutôt qu'un troisième endroit où l'on écrit qu'une mission lunaire est lunaire.

**Deux alternatives écartées.** Un défilement vertical du step est moins de travail, mais un type
de mission sous la ligne de flottaison est un type que l'utilisateur ne sait pas qu'il existe — et
c'est le premier écran du wizard. Rétrécir la carte à 192 px pour quatre par rangée
(4 × 192 + 3 × 16 = 816, exactement le contenu) tiendrait huit cartes en deux rangées, mais le
badge le plus long du dépôt, `LONG-COAST STAGE OR AKM`, tient aujourd'hui dans 256 px — et c'est un
correctif à usage unique que la neuvième carte casse à nouveau.

**Ferme sur** : les tests de modèle du step — quel onglet contient quel profil, quel onglet s'ouvre
selon le profil initial, et ce que devient le verrouillage en édition (les cartes des autres
onglets restent visibles et inertes, comme elles le sont aujourd'hui dans la grille) — plus un
essai manuel.

### L7 — La carte d'orbite lunaire

La septième carte arrive dans un step qui sait déjà l'accueillir. `MissionProfile.LUNAR_ORBIT` en
`Availability.WINDOWED` comme sa voisine, un `DynamicParameters` à **un seul champ** — l'altitude
d'orbite lunaire, sur l'`AltitudeRange` du profil — le step planning branché sur la fenêtre lunaire
existante, le filtre de charge utile qui ne propose que l'orbiteur, et le refus de Kourou présenté
comme un refus.

**Ferme sur** : les tests de modèle du wizard, plus un essai manuel.

---

## 5. Ordonnancement et risques

```
L0 → ┬ L1 ────────┐
     ├ L2 ────────┤
     ├ L3 → L4 ───┴→ L5 ──┐
     └ L6 ────────────────┴→ L7
```

`L6` est indépendant de tout le reste et peut être fait à n'importe quel moment avant `L7`, y
compris en premier.

**Le risque principal est l'inclinaison subie**, et c'est pourquoi `L0` la mesure en premier. Si la
dispersion couvre 0 à 90° selon l'époque, « subie » reste techniquement correct mais devient
difficile à présenter : l'utilisateur choisit une date dans la fenêtre et reçoit une orbite polaire
ou équatoriale sans l'avoir demandée. Le remède est hors périmètre ; ce qui bouge alors est
l'écriture du wizard en `L7`.

**Le deuxième est la tenue de l'orbite dans le modèle.** Si le périlune respire sous la
perturbation terrestre, la bande de mérite de `L5` s'élargit et `Revolutions(12)` montre une orbite
qui se dégrade. Ce n'est pas un défaut — c'est de la physique — mais cela change ce que la mission
a l'air de faire.

**Le troisième est que le crochet de `L1` traverse le socle partagé.** Le prédicat étant faux par
défaut, la non-régression de trajectoire est structurelle. Mais `endDateIsStageCutoff` alimente
`shortfallSeconds()`, qui décide de `isComplete()`, qui décide de la faisabilité dans
`MissionLoadEvaluator` : une erreur là se manifesterait comme « aucune charge d'ergols faisable »
sur une mission qui n'a rien à voir avec la Lune. **C'est le seul endroit du chantier où un défaut
peut sortir loin de sa cause.**

**Le quatrième est le temps de paroi de `L5`** : ascension CMA-ES plus cinq jours de propagation,
deux fois — et la marche d'étages vole désormais le coast translunaire, ce qu'elle ne faisait pas.
`MIS-4 / L0` mesure 9,2 à 11,8 s pour dix jours sans ascension ; la mesure 4 de `L0` donne le
chiffre réel.

**Ce qui n'est pas un risque** : la bascule de sphère, la conversion de repère, le rendu aux deux
échelles, la fenêtre de lancement, la visée du TLI et la combustion finie centrée. Les six sont
livrés, volés et épinglés par `PHY-4` et `MIS-4`.

---

## 6. Limitations assumées

Onze, toutes identifiées pendant la conception de ce découpage. Elles sont écrites ici pour
qu'aucune ne soit redécouverte comme un défaut.

**Ce qui est figé alors qu'il pourrait ne pas l'être**

1. **Un seul paramètre utilisateur** : l'altitude d'orbite lunaire, qui est aussi le périlune visé
   par le TLI. Les constantes couplées de `MIS-4` — `TIME_OF_FLIGHT = 4 j`,
   `PARKING_ALTITUDE = 400 km`, `TRANSFER_ANGLE = 170°` — ne bougent pas.
2. **L'inclinaison lunaire est subie** (§2.2 pt 2), faute d'un second degré de liberté à la visée.
3. **L'orbite est circulaire.** Une cible elliptique demanderait un second paramètre et une seconde
   bande de mérite.
4. **Le moteur de l'orbiteur est dimensionné pour une combustion unique**, ce qu'aucun orbiteur
   lunaire réel ne fait — LRO, Chandrayaan-2 et Danuri fractionnent leur LOI en trois à cinq
   combustions parce que leur moteur est petit. C'est une donnée de catalogue, écrite comme telle,
   dans la même veine que les trois cœurs de Falcon Heavy agrégés en un étage.

**Ce qui est promis ailleurs et que `MIS-5` ne livre pas**

5. **Aucune optimisation CMA-ES**, et pour une raison plus forte que celle de `MIS-4` : il n'y a
   pas de degré de liberté à explorer.
6. **`CoastingStage` n'est toujours pas réparé.** `MIS-5` répare un coast — le sien — par sa
   frontière, pas par la classe de base.
7. **Le seed de Lambert reste enfermé** dans `TranslunarInjectionPlan`. Toujours `MIS-6`.

**Ce qui reste ouvert dans le socle**

8. **La Lune est une masse ponctuelle.** Une orbite lunaire basse réelle est instable sous les
   mascons ; celle-ci ne l'est pas.
9. **Le S2 sort du modèle au largage**, juste après le TLI. Le S-IVB d'Apollo continuait jusqu'à la
   Lune. C'est la convention de `GEOMission`, pas une décision de ce chantier.
10. **Ce que l'objectif porte comme inclinaison est une question ouverte**, tranchée en `L5` sur la
    mesure 1 de `L0`. `OrbitInsertionObjective` en exige une ; **aucun prédicat de faisabilité ne la
    lit** — `MissionLoadEvaluator.objectiveMet` ne compare que périgée et apogée — et trois sites
    d'affichage la montrent (`MissionDetailView:177`, `MissionResultText:67`, `PanelFooter:278`).
    Les deux issues — la valeur que la géométrie délivre, calculée à la composition, ou un marqueur
    d'absence que l'affichage sait lire — ne s'arbitrent pas avant de savoir si la première est
    calculable avant le vol.
11. **`MissionOptimizer.resolveTargetAltitude` rendra une altitude lunaire pour un diagnostic
    d'ascension terrestre.** Il rend `NaN` sur `MIS-4` parce qu'un `FlybyObjective` n'est pas un
    `OrbitInsertionObjective`, ce qui fait tomber le diagnostic dans sa branche neutre. `MIS-5`
    porte un objectif d'insertion : la fonction rendra 100 km, et `StageEndStateDiagnostic`
    comparera l'ascension terrestre à un transfert de Hohmann idéal vers une orbite **terrestre**
    de 100 km. Une ligne de journal fausse, introduite par ce chantier.

    Au passage, une correction : `StageEndStateDiagnostic` reste **légitimement terrestre**, contre
    ce que craint son propre commentaire (« *Wake it when a stage end-state can sit on a
    non-terrestrial arc — L4* »). Son unique site d'appel est gardé par
    `problem instanceof GravityTurnProblem` : il ne voit jamais que la sortie du virage
    gravitationnel, qui est géocentrique.

---

## 7. Ce que `MIS-5` lègue

- **À `MIS-11`** (Artemis) : la branche aller complète, un véhicule capable d'exécuter ses propres
  combustions — le besoin que le §7 de `MIS-4` annonçait comme « cette fois réel » — et une bascule
  de sphère qui termine un étage, dont le retour aura besoin dans l'autre sens.
- **À `MIS-6`** : les deux mesures terrestres réparées, et rien d'autre. Le seed de Lambert reste à
  généraliser là, parce que c'est là qu'un deuxième consommateur en fixera enfin la forme.
- **À `MIS-10`** : `OrbitElements` comptant ses apsides sur le corps de l'arc, et un précédent de
  réparation à coût nul pour les sites terrestres qui restent.
- **Au wizard** : un premier écran qui passe à l'échelle, et donc un obstacle de moins sur le
  chemin de toute mission future vers un autre corps.
- **À la clôture du chantier** : le §6 pt 10 si `L5` ne le tranche pas proprement, et le §6 pt 11.
