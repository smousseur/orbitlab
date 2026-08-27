# MIS-4 / L5 — Le wizard

Lot **L5** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur ce que
[`L2`](04-conception-L2.md) et [`L4`](06-conception-L4.md) lèguent nommément. Il rend vraie une seule
propriété : **la mission lunaire se crée au wizard**.

C'est la surface utilisateur. Une carte de plus au premier step, un panneau de paramètres à un champ,
une timeline de fenêtre, une charge utile au catalogue, et les trois refus que `L4` a posés qui
tombent. Rien de la physique ne bouge : `MissionSpec.Lunar`, `MissionComposer.composeLunar`,
`LunarFlybyMission` et `LunarLaunchWindowProblem` sont livrés et volés.

**Quatre énoncés écrits ailleurs sont corrigés ici**, rassemblés au §9. Deux tombent d'un défaut
mesuré dans le code, un d'une mesure faite par `L4` le jour même où ce document est écrit, et le
quatrième d'une décision de `L2` que le découpage n'avait pas vue venir.

---

## 1. Ce que le code dit avant qu'on y touche

Six relevés, tous faits avant qu'une ligne bouge. Trois d'entre eux sont des **défauts** et non du
câblage : ils existent aujourd'hui, ils sont invisibles aujourd'hui, et ils deviennent visibles au
moment exact où ce lot ajoute sa constante.

### 1.1 — La timeline lunaire ne peut pas être branchée telle quelle

Le découpage §4 écrit « le step planning branché sur la fenêtre lunaire de `L2` ». La transposition
directe est mesurablement impossible, pour deux raisons indépendantes.

**Le coût.** `PlanningModel.refresh` est mémoïsé sur ses entrées mais appelé **à chaque frame**, sur
le thread de rendu. Côté terrestre `EarthLaunchWindowPlanner.nextOpportunities` est en forme close :
4–9 ms. Côté lunaire, `evaluate` reste en microsecondes, mais `confirm()` est un
`TranslunarInjectionPlan.solve()` — **4,5 s confirmé, 3,9 s refusé** (L0 §6), 2 à 3 confirmations par
recherche (L2 §3.4). Une timeline à trois opportunités coûterait **10 à 15 s par recalcul**.

**Les entrées.** `LunarLaunchWindowProblem` réclame un `Vehicle` et une `massAtInjection`
(`:133`), **pour `confirm()` seul** — son propre javadoc le dit. Or le step planning vit sur
`PARAMETERS` (index 2) et le lanceur n'est choisi qu'à `LAUNCHER` (index 3). Le critère `evaluate`,
lui, ne demande que le pas de tir, l'altitude de parking et la périlune : trois choses connues au
step 2.

Les deux raisons pointent au même endroit, et le §4 y va.

### 1.2 — Le planner terrestre a deux méthodes de nature différente

Ce qui décide de la plomberie, et qui n'est pas visible du dehors.

| Méthode | Chemin | Forme |
|---|---|---|
| `nextOpportunity` (singulier) | validation d'une mission | triple **gelé** en dur : 26 h, 50 m/s, 5 candidats, « ne doit pas bouger » |
| `nextOpportunities` (pluriel) | la timeline du wizard | **déjà générique** : horizon dérivé de `problem.recurrence()` via `LaunchWindowSearch.forOpportunities`, ne lit du problème que `recurrence`, `coarseStep` et `refinementPrecision` |

Seule la constante `MARGIN` est écrite en dur dans le pluriel. **Généraliser la timeline ne touche
donc pas au triple gelé**, et l'inquiétude que son javadoc porte ne s'applique pas à ce lot.

### 1.3 — `MissionProfile.of(spec)` rend `GEO` pour une spec lunaire

`MissionProfile.of` (`:301-303`) ouvre sur `if (!(spec instanceof MissionSpec.EarthOrbit)) return
GEO;`. L4 §1.2 range ce site parmi les trois `instanceof` « qui traversent sans rien casser ». **Le
verdict ne tient que parce que `WizardPrefill` jette avant d'y arriver** (`:88`). Dès que ce lot
remplit `WizardPrefill`, rouvrir une mission lunaire allume la carte GEO — une réponse fausse rendue
en silence, là où un `switch` sur la scellée ferait pointer le compilateur.

### 1.4 — `earthOrbitProfiles()` filtre `!= GEO`, pas « est une orbite terrestre »

`MissionProfile.earthOrbitProfiles` (`:281`) est documenté « the profiles backed by {@code
MissionSpec.EarthOrbit} » et implémenté par une exclusion nominative de `GEO`. Une sixième constante
y tomberait, et `StepParameters:247` lui construirait un `EarthOrbitDynamicParameters` — un panneau
de périgée/apogée pour un survol lunaire.

### 1.5 — Le badge `CONSTRAINED` code en dur une phrase MEO

`StepMissionType.badgeFor` (`:139`) commute sur `Availability` et rend littéralement
`Badge("LONG-COAST STAGE OR AKM", WARNING)` pour `CONSTRAINED`. Le découpage §4 demande
`LUNAR_FLYBY` en `CONSTRAINED`, « le statut que MEO porte déjà » : la carte lunaire afficherait un
énoncé faux.

Et le motif lui-même a été démenti deux fois depuis que le découpage l'a écrit. Les deux lanceurs
exécutent le TLI avec une marge confortable (découpage §6 pt 8), et **personne ne refuse Kourou**
(L2 §1.3). Le javadoc de `CONSTRAINED` dit d'ailleurs « the catalog constrains it. Only the MEO ».
**Rien dans le catalogue ne contraint une mission lunaire** ; ce qui la contraint est la fenêtre.

### 1.6 — Le filtre de charge utile n'a qu'un axe, et ce n'est pas celui-là

`Payloads.forMissionType` ne filtre que sur `MissionType.requiresPayloadPropulsion()`. `LUNAR_FLYBY`
valant `false`, il rend **tout le catalogue** : « Cargo module », « Earth observation satellite » et
« GEO communications satellite ». Deux des trois sont incohérents pour un survol lunaire, et la
réciproque le sera : une sonde lunaire offerte à une mission LEO est l'incohérence en miroir.
`hasAkm()` ne peut pas porter le second axe, la sonde étant **inerte** (découpage §6 pt 8).

---

## 2. Le profil, la carte et l'icône

### 2.1 — La constante

**`MissionProfile.LUNAR`**, nommée court comme les cinq autres parce que `defaultMissionName`
compose `%s-%03d` sur `profile.name()` : `LUNAR-001` et non `LUNAR_FLYBY-001`.

| Champ | Valeur | Raison |
|---|---|---|
| `missionType` | `LUNAR_FLYBY` | le deuxième profil à ne pas être un LEO |
| `title` / `subtitle` | `LUNAR` / `Lunar Flyby` | |
| `value` | `perilune 100 km` | la ligne de valeur nomme le paramètre, comme `35 786 km` le fait pour GEO |
| `altitudes` | `AltitudeRange(50, 500, 100)` | l'altitude de périlune, §2.2 |
| `circular` | `false` | sans consommateur : le panneau lunaire n'a pas de couple périgée/apogée |
| `inclinationMode` | `NONE` | `i = φ` en plein est ; `MissionSpec.Lunar` ne porte pas d'inclinaison |
| `availability` | `WINDOWED` | §2.3 |

**La grille passe de 3 + 2 à 3 + 3.** `CARDS_PER_ROW` vaut 3 et `padRow(row, 6 % 3 = 0)` ne pose rien :
la sixième carte remplit la grille exactement, là où cinq profils laissaient un rang court.

### 2.2 — La bande de périlune, et ce qu'on ne sait pas d'elle

`TranslunarInjectionPlan` n'écrit **aucun plancher ni plafond** sur la périlune demandée : la visée
bracketise et refuse si elle n'atteint pas la cible (`:434`). Le seul chiffre jamais volé est
**100 km**, avec la bande de mérite de ±10 km que `LunarFlybyMission.PERILUNE_TOLERANCE` porte.

**Décidé : `(50, 500, 100)` en kilomètres.** Le plancher à 50 km tient la bande de ±10 km à distance
de l'impact — le rayon lunaire vaut 1 737 km, et une périlune demandée à 10 km serait satisfaite par
un vol à 0. Le plafond à 500 km reste un survol.

**Ce que le curseur ne propose pas n'est pas ce que la géométrie refuse**, et c'est écrit en
limitation (§8 pt 3) : le domaine réel dépend de l'époque autant que de la valeur demandée — L0 §4 a
mesuré un refus dont le meilleur périlune atteignable valait **1 873 km**. Le refus existe donc quelle
que soit la bande du curseur, et il se manifeste aujourd'hui en mission `FAILED` après une compute
complète.

### 2.3 — Une troisième disponibilité, dont le motif est la fenêtre

**Décidé : `Availability.WINDOWED`**, badge `WARNING`, libellé `LAUNCH WINDOW REQUIRED`.

Pas `CONSTRAINED`, dont le motif est le catalogue et dont le libellé est vrai pour le MEO (§1.5). Pas
`AVAILABLE` non plus : la carte existe pour prévenir, et une mission lunaire a bien quelque chose à
dire — sa date n'est pas libre. `badgeFor` reste un `switch` à un libellé par constante, chacun
restant vrai, et le compilateur désigne le site à la quatrième disponibilité.

**Le découpage §4 est corrigé sur le statut**, après l'avoir été sur le motif par L0 §7 pt 2 puis sur
le comportement par L2 §1.3.

### 2.4 — L'icône

48×48 RGBA, au gabarit exact des cinq autres — contour noir épais, aplats saturés, anti-aliasing dur.
Aucun générateur n'est commité dans le dépôt ; celle-ci est produite par un rasteriseur suréchantillonné
en Python pur, PIL n'étant pas installé.

**Motif : deux corps.** Terre bleu/vert **réduite** en bas à gauche, Lune grise cratérée en haut à
droite, arc de trajectoire blanc cerclé de noir partant de la Terre, contournant la Lune et repartant,
avec le point cyan de l'engin sur l'arc. C'est la seule carte du wizard dont l'icône montre deux corps,
et c'est exactement ce qui la distingue des cinq autres — toutes des variations d'un anneau autour d'un
globe.

### 2.5 — Les deux réparations que la constante rend nécessaires

Elles ne sont pas du câblage : ce sont les défauts du §1.3 et du §1.4.

- **`earthOrbitProfiles()` filtre sur `missionType() == MissionType.LEO`**, c'est-à-dire sur ce que
  son nom dit. `MissionProfileTest:145` fige le `4` et **continue de valoir 4** : que le test tienne
  sans changer de valeur est le signe que la réparation est juste.
- **`MissionProfile.of(spec)` devient un `switch` sur la scellée `MissionSpec`.** Le quatrième type
  fera pointer le compilateur ici, au lieu de rendre `GEO`.

---

## 3. Le panneau de paramètres

`LunarDynamicParameters`, sur le patron de `GEODynamicParameters` — la classe la plus courte du
paquet, et la seule qui n'ait pas de couple périgée/apogée.

- **Un curseur**, `PERILUNE ALTITUDE`, sur l'`AltitudeRange` du profil. Rien d'autre : pas
  d'inclinaison (`InclinationMode.NONE`), pas d'altitude de parking — L4 §4.1 a décidé de ne pas
  l'exposer.
- **Une clé neuve**, `FormField.LUNAR_PERILUNE_ALT`, en kilomètres comme toutes les altitudes du
  wizard.
- `validateTargetPlane()` et `hasRejection()` gardent leurs défauts vides : il n'y a pas de plan à
  refuser.

**`defaultHorizonDays()` rend `7.0` en dur, et c'est une contrainte héritée et non un raccourci.**
`DynamicParameters.revolutionDays` (`:135`) porte un µ terrestre, et L0 §7 pt 4 a écrit qu'il ne reste
hors chemin **que tant que le profil lunaire garde un horizon `FixedDuration`** — L4 §11 le lègue
explicitement à ce lot. Le nombre est donc posé là avec sa raison, et non dérivé.

---

## 4. Le step planning

### 4.1 — Ce que la timeline lunaire calcule

`LunarLaunchWindowRequest(latitude, longitude, altitude, parking, périlune)` →
`LunarLaunchWindowProblem` **en mode criblage**, construit par une fabrique `screening(...)` sans
véhicule, dont `confirm()` rend le candidat inchangé.

**Ce n'est pas un contournement, c'est la lecture littérale du contrat.** Le champ `vehicle` est
documenté « for `confirm()` alone », et `LaunchWindowProblem.confirm` a un **défaut no-op** sur
l'interface (`:111`), dont le javadoc dit qu'il est « the honest answer for a problem whose evaluate
is already the truth ». Pas de véhicule, pas de verdict — et c'est exactement ce que le step 2 a à
dire, le lanceur n'étant choisi qu'au step 3.

Coût : `evaluate` en microsecondes, `recurrence()` valant un demi-jour sidéral, trois opportunités
font ~37 h de balayage horaire. Le même ordre que le chemin terrestre, sur le thread de rendu, en
boucle scrutée, **sans toucher une constante du triple gelé** (§1.2).

### 4.2 — La requête devient scellée

`LaunchWindowRequest`, interface scellée à un seul membre — `toProblem()` —, implémentée par
`EarthLaunchWindowRequest` (inchangé par ailleurs) et `LunarLaunchWindowRequest`.
`nextOpportunities` monte au générique ; `nextOpportunity` reste terrestre.

**Les deux requêtes étant des records, l'égalité par valeur qui porte la mémoïsation de
`PlanningModel` ne bouge pas** — c'est la raison qui écarte de porter le `LaunchWindowProblem`
directement dans `PlanningInputs` : les implémentations de problème ne sont pas des records, et la
mémoïsation d'une page appelée à chaque frame cesserait de mordre.

### 4.3 — Trois changements dans l'UI, tous localisés

**`hasTargetNode()` devient `hasLaunchWindow()`.** Le prédicat vaut `selectedProfile != GEO` et
**continue de valoir exactement ça** : la lunaire a bien une fenêtre. C'est le nom qui devenait faux,
pas la valeur — une mission lunaire a une fenêtre sans avoir de nœud.

**`PlanningPage` gagne un mode sans nœud.** Un interrupteur, deux conséquences qui sont le même
énoncé : le champ RAAN disparaît, et `withNodeGap` (`:237`) cesse de substituer. Sans lui le défaut
est fatal — cette méthode écrase **inconditionnellement** les entrées que le step a assemblées, donc
un champ RAAN vide rendrait `Gap.NO_NODE` et la timeline lunaire resterait éteinte pour toujours.

**L'assemblage de la requête descend dans le panneau.** `StepParameters.currentWindowInputs` (`:818`)
garde le pas de tir — le seul écart qu'il soit seul à connaître, `Gap.NO_SITE` — et délègue le reste
à `dynamicParameters.windowInputs(site, raanDeg)`, qui rend un `PlanningInputs` complet, écart ou
requête. Le javadoc actuel justifie l'assemblage dans le step par « le pas de tir vient du step site
et le plan du panneau à l'écran » : **rendre au panneau la moitié qui est la sienne rend cet énoncé
plus vrai, pas moins.** Le panneau terrestre y déplace son code tel quel ; le panneau lunaire construit
sa requête et ignore le nœud.

---

## 5. Le catalogue et le budget d'ergols

### 5.1 — La sonde

`Payloads.LUNAR_PROBE`, « Lunar probe », **inerte** — 2 000 kg à vide, l'ordre de LRO (1 846 kg) et de
Luna-25 (1 750 kg), et le même chiffre que `GEO_SAT` à sec. Section supposée 2,0 × 2,0 m, soit
`B = 227 kg/m²` : une convention de plus, énoncée comme les trois autres le sont, et qui élargit vers
le bas l'encadrement des 455 kg/m² de la table de PHY-2 sans avoir été ajustée dessus.

Un record de plus au catalogue. **Le véhicule qui *exécuterait* le TLI reste hors périmètre**
(découpage §6 pt 8) : c'est l'étage supérieur qui l'exécute, et une charge utile propulsée à ce niveau
ajouterait une seconde façon de faire ce qui est déjà fait.

### 5.2 — Le domaine

`PayloadDomain { EARTH, LUNAR, ANY }`, composant de `PayloadModel`.

| Modèle | Domaine | Raison |
|---|---|---|
| `CARGO_MODULE` | `ANY` | rien dans un module cargo ne parle de la Terre |
| `EARTH_OBSERVATION_SAT` | `EARTH` | |
| `GEO_SAT` | `EARTH` | |
| `LUNAR_PROBE` | `LUNAR` | |

`ANY` veut dire « partout, définitivement » : un quatrième `MissionType` ne fait pas mentir le
catalogue, là où un ensemble de types éligibles obligerait à rouvrir `CARGO_MODULE` pour y dire une
chose qui n'a pas changé.

**La correspondance type → domaine reste un `switch` dans `Payloads.forMissionType`**, et non un
accesseur sur `MissionType` : `Payloads` importe déjà `MissionType`, et l'inverse fabriquerait un
cycle entre `simulation.mission` et `simulation.mission.vehicle.model`. Le `switch` étant exhaustif
sur l'énumération, le compilateur désigne ce site au quatrième type. Le filtre croise le domaine et
l'AKM ; `forMissionType(LUNAR_FLYBY)` rend `{CARGO_MODULE, LUNAR_PROBE}`.

### 5.3 — `loadsForLunar`

Quatrième entrée de `PropellantBudget`, et **plus simple que `loadsForGeo`** : ascension jusqu'au
parking, puis **une** injection, rien à déléguer à une charge utile inerte. Même Tsiolkovsky inverse
descendante, même `SAFETY_MARGIN` de 10 %.

Elle rend `LunarLoads(double[] launcherLoads, double massAtInjection)`, sur le patron de `GeoLoads`.
**Le second composant n'est pas un supplément** : c'est exactement le chiffre que
`LunarLaunchWindowProblem.confirm()` réclame (§1.1), et le dimensionnement top-down le connaît déjà.
Une seule définition, deux consommateurs.

**Le Δv d'injection est pris en forme close** — Hohmann du rayon de parking à la distance lunaire —
et non en constante, parce que l'altitude de parking est un composant de la spec et qu'une constante
y figerait une valeur dans une classe dont tout l'idiome est la forme close.

**Et il faut écrire l'écart plutôt que le laisser découvrir.** La forme close rend **3 082 m/s** à
400 km là où L4 §4.1 a **mesuré 3 124 m/s** : 42 m/s de moins, 1,3 %, l'angle de transfert de 170° et
l'offset de visée n'étant pas dans la formule. Les 10 % de marge valent 312 m/s à cette échelle :
l'écart est absorbé trois fois, et les 50 m/s de marge d'acceptation de la fenêtre le sont aussi.

### 5.4 — Ce que la sonde `PRECISE` dit à ce lot, et ce qu'elle ne dit pas

`LunarPrecisePathProbeTest`, courue le **2026-08-27** — la mesure que L4 §5 promettait et adressait
nommément à L5.

| | mesuré |
|---|---|
| Temps de paroi d'un `PRECISE` lunaire | **189,7 s**, 3,2 min, neuf évaluations en deux passes |
| λ\* | `[1,0000 ; 0,5844]` |
| Charge d'étage supérieur retenue | **44 680 kg**, 41,6 % de la capacité, pour 1 t de charge utile |
| Ce qui mord à la marge | le **plancher de résidu d'extinction**, pas le Δv du transfert |

Le javadoc de la sonde le dit : la dernière évaluation refusée atteignait le survol avec un résidu de
0,869 % contre le plancher de 1 %.

**Ça ne remet pas en cause la forme close du §5.3.** `PropellantBudget` est explicitement une
heuristique de départ, et c'est d'elle que part le balayage λ. Mais **ça déplace le risque** : un
budget qui sous-dimensionne ne se voit pas en `PRECISE`, où le balayage rattrape ; il se voit en
**`FAST`**, qui est le mode par défaut de toute mission créée au wizard (`MissionEntry:42`). C'est ce
qui justifie le vol du §7.

---

## 6. La fabrique, le préremplissage et la sauvegarde

### 6.1 — Le 400 km trouve un domicile

`LunarFlybyMission.DEFAULT_PARKING_ALTITUDE = 400_000.0`. C'est la source unique que L4 §4.1 réclame
— la fenêtre, la chaîne et le budget doivent s'accorder — et elle n'existait pas : le vol de L4 passe
`400_000` en dur depuis son propre test.

### 6.2 — Les trois refus tombent

| Site | Ce qui le remplace |
|---|---|
| `MissionFactory:147` | lit `LUNAR_PERILUNE_ALT`, appelle `loadsForLunar`, monte la charge utile à `akmLoad = 0` — le type ne requiert pas de propulsion —, rend un `MissionSpec.Lunar` |
| `WizardPrefill:88` | écrit la seule clé que le wizard possède, la périlune |
| `ScenarioMapper:111` | `ScenarioMission.Lunar(…, periluneKm)`, l'entrée `@JsonSubTypes.Type(name = "LUNAR_FLYBY")`, et les deux branches du round-trip |

**Le parking n'est pas réécrit par `WizardPrefill`**, la fabrique le reprenant de la constante du
§6.1. Conséquence assumée au §8 pt 5 : une spec bâtie ailleurs à une autre altitude ne revient pas
telle quelle du wizard.

**Le trou de `ScenarioMapper` était réclamé par aucun lot** — L4 §10 pt 5 l'a signalé sans le combler,
en notant qu'« il faudra bien que quelqu'un le remplisse ». Il entre ici parce que la propriété que ce
lot rend vraie est « elle se crée au wizard », et qu'une mission qu'on crée puis qui disparaît à la
sauvegarde ne la rend pas vraie. Le refus se déclencherait d'ailleurs sans geste explicite :
l'utilisateur sauvegarde son scénario, pas sa mission lunaire. Quarante lignes, aucune décision de
physique.

### 6.3 — La date, à la validation

`MissionWizardAppState.scheduledDateFor` commute sur la spec. Pour une lunaire, il appelle
`LunarLaunchWindowPlanner.nextOpportunity`, qui construit cette fois le problème **avec** véhicule et
masse à l'injection — les deux connus, le lanceur ayant été choisi. Le `massAtInjection` est recalculé
en appelant `loadsForLunar` sur les entrées relues de la spec : déterministe, en forme close, des
microsecondes, et une seule définition du chiffre (§5.3).

**C'est là que le `confirm()` se paie**, une fois, au lieu d'à chaque frappe. Le prix est écrit au §8
pt 2.

---

## 7. Les tests

**Aucune classe de test neuve.** Les huit points d'accroche existent tous, et L5 les étend.

| Classe | Ce qu'elle ajoute |
|---|---|
| `MissionProfileTest` | `LUNAR` porte `LUNAR_FLYBY` ; `of(spec lunaire)` rend `LUNAR` **et non `GEO`** (§1.3) ; `earthOrbitProfiles()` vaut **toujours 4** et exclut `LUNAR` (§1.4) |
| `PayloadsTest` | `forMissionType(LUNAR_FLYBY)` rend `{CARGO_MODULE, LUNAR_PROBE}` ; `LEO` et `GEO` n'offrent pas la sonde |
| `PropellantBudgetTest` | `loadsForLunar` : monotonie en masse de charge utile, charges dans les capacités des deux lanceurs, `massAtInjection` au-dessus du plancher d'épuisement |
| `MissionFactoryTest` | une `MissionSpec.Lunar` bâtie depuis des valeurs de wizard : périlune en mètres, parking à 400 km, charge utile inerte |
| `WizardPrefillTest` | aller-retour spec → valeurs → spec, périlune conservée |
| `ScenarioMapperTest`, `ScenarioRoundTripTest` | l'aller-retour lunaire par le DTO |
| `PlanningModelTest` | une requête lunaire produit des opportunités ; le problème de criblage ne confirme jamais |
| `LunarFlybyFlightTest` | **un second vol** (§7.1) |

### 7.1 — Le second vol, et pourquoi il n'écrase pas le premier

La même chaîne que le vol de L4, mais configurée par `loadsForLunar` au lieu de
`LaunchConfiguration.fullyLoaded`. **Le vol de L4 reste intact** : L4 §11 le lègue à `L6` comme la
référence impulsionnelle contre laquelle mesurer la poussée finie, et le modifier déplacerait la
base de comparaison du lot suivant.

Ce second vol garde le budget par ce que le budget prétend permettre : un vol. Il coûte une ascension
CMA-ES et sept jours de propagation.

**Ce qui n'est pas testable unitairement**, et qui est écrit plutôt que contourné : le libellé du
badge. `StepMissionType` ne se construit pas headless, et le §2.3 garde le texte dans son `switch`.

**Contrainte de méthode**, rappelée du découpage §3 : les vols sont lents, et **c'est l'utilisateur
qui les lance**. Ce lot ne se ferme sur aucune exécution faite par l'assistant, et le découpage §4 y
ajoute un **essai manuel**.

---

## 8. Limitations assumées

Huit, toutes décidées pendant la conception.

**Ce qui est plus étroit qu'il n'y paraît**

1. **La timeline lunaire ne confirme pas.** Elle dessine le critère de criblage. Un plancher de
   périlune ou d'épuisement peut refuser la date qu'elle propose, et le refus n'arrive qu'à la
   validation — où la date se déplace — ou en mission `FAILED`. C'est le prix du §4.1, et il est le
   seul qui garde la page à son coût terrestre.
2. **10 à 15 s de gel au clic `CREATE`**, sur le thread de rendu, contre 40 ms pour le chemin
   terrestre. Payé une fois plutôt qu'à chaque frappe (§6.3).
3. **La bande de périlune 50–500 km n'est pas mesurée.** Seul le 100 km a volé, et le domaine réel
   dépend de l'époque autant que de la valeur demandée : L0 §4 a mesuré un refus dont le plancher
   atteignable valait 1 873 km (§2.2).
4. **Le Δv en forme close est 42 m/s sous le mesuré**, absorbé trois fois par la marge de 10 %
   (§5.3).
5. **Le parking 400 km n'est pas exposé**, décision de L4 §4.1 : une spec bâtie ailleurs à une autre
   altitude ne revient pas telle quelle du wizard (§6.2).
6. **Le libellé du badge n'est pas testable unitairement** (§7).

**Héritées, non touchées**

7. **`DynamicParameters:135` reste hors chemin conditionnellement** — L0 §7 pt 4 : le jour où le
   profil lunaire prend un horizon en révolutions, le µ terrestre y revient. Et
   **`MissionHorizon.Revolutions` reste offert** sur une mission lunaire, où il compterait des
   révolutions de 12,1 j (L4 §4.2) — sans effet, le défaut étant `FixedDuration` et le wizard
   n'offrant qu'une durée.
8. **ToF à 4 j, angle de transfert à 170° et parking à 400 km restent des constantes couplées**
   (découpage §6 pt 1) ; **le seed de Lambert reste enfermé** à `nRev = 0` (pt 7) ; **rien n'est
   optimisé sur la trajectoire translunaire** (pt 5) ; **la sonde est inerte** et aucun véhicule du
   catalogue n'exécute le TLI lui-même (pt 8).

---

## 9. Ce que `L5` corrige

Quatre énoncés écrits ailleurs, que ce lot dément.

**1. « Le refus de Kourou présenté comme un refus et non comme une exception » — découpage §4.** Il
n'y a plus de refus à présenter : L2 §1.3 a décidé que personne ne refuse Kourou, le coût hors fenêtre
étant déjà dans le critère. Ce que L5 montre est « aucune opportunité sur l'horizon », qui est vrai
quand ça l'est et faux 3,4 jours par lunaison. L2 §7 l'annonçait déjà à ce lot ; il est consigné ici
parce que c'est ici qu'il devient visible.

**2. « `MissionProfile.LUNAR_FLYBY` en `Availability.CONSTRAINED` » — découpage §4.** Le statut est
faux et son libellé l'est aussi (§1.5). Rien dans le catalogue ne contraint une mission lunaire ; ce
qui la contraint est la fenêtre, d'où `WINDOWED`.

**3. « Les trois `instanceof` traversent sans rien casser » — L4 §1.2.** Vrai pour deux d'entre eux.
`MissionProfile:302` rend une réponse **fausse** pour une spec lunaire, et le verdict de L4 ne tenait
que parce que `WizardPrefill` jetait avant (§1.3).

**4. « Le step planning branché sur la fenêtre lunaire de `L2` » — découpage §4.** Branché sur son
`evaluate`, pas sur son `confirm()`. Le découpage ne pouvait pas le savoir : les 4,5 s du `solve()`
sont une mesure de L0 §6, et le besoin d'un véhicule est une décision de L2 §3.

---

## 10. Ce que `L5` lègue

**À `L6`** — rien qu'il n'ait déjà. La référence impulsionnelle de L4 est laissée intacte (§7.1), et
le second vol lui donne au passage un deuxième point de comparaison, dimensionné celui-là.

**À la clôture du chantier** — trois choses. La **restriction de la timeline** (§8 pt 1), qui
disparaîtra le jour où une page de planning saura calculer en arrière-plan. Le **gel de 10 à 15 s**
à la création (§8 pt 2), qui a la même issue. Et la **bande de périlune non mesurée** (§8 pt 3), qui
demande un balayage — une dizaine de `solve()`, moins d'une minute — et non une décision.

**À `MIS-5`** — un wizard qui sait déjà offrir une mission lunaire, un catalogue qui sait déjà dire
qu'une charge utile est lunaire, et un `PropellantBudget` qui a désormais un cas translunaire à
étendre vers l'insertion. Plus le rappel de la sonde `PRECISE` : ce qui dimensionne cette chaîne est
le résidu que l'étage supérieur doit garder, pas le Δv que le transfert demande.
