# MIS-4 — Survol lunaire (TLI + flyby) — découpage

Item roadmap : `MIS-4` (★5 ◆4 L), phase 5. Ce document ne conçoit pas en détail : il
**découpe**. Chaque lot y est défini par la propriété qu'il rend vraie, par ce qu'il
consomme, par ce qu'il produit et par le test qui le ferme.

Il n'y a pas de conception amont à laquelle il renverrait : la fiche du §6 du
[roadmap](../roadmap/01-roadmap-v1.md) et le §8 de
[`brainstorm/missions.md`](../brainstorm/missions.md) sont antérieurs à `PHY-4`, et le §2
ci-dessous les corrige sur cinq points. Ce découpage est donc **la** porte d'entrée du
chantier.

---

## 1. Périmètre

**Dans `MIS-4`** — les sept lots du §4 :

- l'injection translunaire depuis un plan de parking **imposé** par le tir, et non fabriqué
  pour convenir ;
- la fenêtre de lancement lunaire, troisième implémentation de `LaunchWindowProblem` ;
- un objectif de survol, et la note qui va avec ;
- la mission du produit — `MissionType`, `MissionSpec`, composer, chaîne d'étages — volant
  **du sol au survol** ;
- sa création au wizard, avec une charge utile qui ait du sens ;
- le remplacement de l'impulsion par une combustion finie.

**Hors `MIS-4`**, et à ne pas y laisser glisser : l'insertion en orbite lunaire (`MIS-5`),
le retour vers la Terre (`MIS-11`), tout véhicule du catalogue capable d'exécuter le TLI
lui-même (§6 pt 8), l'optimisation CMA-ES de la trajectoire (§6 pt 5) et la généralisation
du seed de Lambert (§6 pt 7). Les raisons sont écrites au §6, une par une, parce que
plusieurs contredisent la fiche du roadmap.

---

## 2. État des lieux

### 2.1 Ce que `PHY-4 / L6` a laissé, et qui vole

| Brique | Fichier | Mesure |
|---|---|---|
| Mission translunaire complète | `mission/operation/LunarTransferMission.java` | 133 l., parking 185 km → périlune 100 km, horizon 4,5 j |
| Plan d'injection (Lambert + visée) | `mission/maneuver/TranslunarInjectionPlan.java` | 712 l., ToF 4 j, angle de transfert 170°, bissection sur le périlune à ±1 km |
| Étage d'injection | `mission/stage/TranslunarInjectionStage.java` | 110 l., **impulsionnel** |
| Coast avec bascule de sphère | `mission/stage/TranslunarCoastStage.java` | 38 l. |
| Socle de fenêtre de lancement | `mission/window/` | interface + solveur + 2 implémentations |
| Vol épinglé | `LunarTransferFlightTest.java` | 242 l., arcs `[EARTH, MOON]`, périlune 100 km ± 10 km |

Cette mission passe par la porte `MissionEntry(Mission)` *legacy*, derrière la propriété
`mission.lunarDemo` : ni `MissionType`, ni `MissionSpec`, ni wizard, ni fenêtre de lancement.

### 2.2 Cinq points où la fiche du roadmap est fausse ou périmée

1. **« `TLIBurnStage` depuis l'apogée d'une orbite de parking ».** L'injection part d'une
   orbite de parking **circulaire** (`PARKING_ALTITUDE = 185 km`) à un point résolu par le
   plan. « Injecter à l'apogée » est l'idiome de la chaîne GEO, pas du translunaire.

2. **« Le seed Lambert devient une brique partagée ici, parce qu'il a ici son premier
   consommateur de production ».** Les deux appels du dépôt sont `solve(true, 0, conditions)`
   (`TranslunarInjectionPlan:480` et `:671`). Un `TLIBurnStage` de production demanderait
   **exactement les mêmes valeurs** : l'extraction n'aurait toujours qu'un seul consommateur,
   et n'aurait pas plus d'information ici qu'elle n'en avait dans `MIS-3`. Le consommateur
   qui fixerait la forme est le phasing multi-révolution de `MIS-6`.

3. **`LunarOrbitObjective`, annoncé en `MIS-5`, n'a probablement pas lieu d'être.**
   `OrbitInsertionObjective` prend déjà un `SolarSystemBody`, et `LunarTransferMission:87`
   l'instancie **avec `MOON`**.

4. **La fenêtre translunaire livrée ne produit pas de fenêtre.** Son propre javadoc le dit :
   14 m/s de variation sur 3 182 au long d'un mois, « une hiérarchie et pas une fenêtre », et
   l'intervalle rendu est la plage cherchée elle-même.

5. **Le critère à relief existe déjà, et il est terrestre.** `EarthLaunchWindowProblem` prend
   `(site, LaunchPlane, targetRaan, sma)` et rend une vraie fenêtre : **3 min 52 s, une fois
   par jour sidéral**, avec 12 020 m/s d'amplitude. Ce qui manque à `MIS-4` n'est donc pas une
   mécanique de fenêtre mais **la dérivation du plan de parking à partir de la direction de la
   Lune à l'arrivée**.

### 2.3 Trois mesures qui décident du découpage

**La passe d'optimisation ne vole pas le coast terminal — pas seulement la sphère
d'influence, le coast tout court.** `CoastingStage` ne redéfinit pas `propagateStandalone`,
donc la marche d'étages de `MissionOptimizer:311` s'arrête à la fin du dernier étage propulsé ;
le code le dit dans son propre commentaire. Sur LEO/GEO c'est sans conséquence, le mérite étant
l'orbite d'insertion. Sur un survol, le mérite est au bout de quatre jours de coast et de
l'autre côté d'une bascule. **Conséquence : `MIS-4` n'optimise rien** (§6 pt 5).

**`objectiveMet` est faux par construction sur un survol.** `MissionLoadEvaluator` note une
mission sur le **min et le max d'altitude** des échantillons du coast terminal, arc final. Sur
un arc lunaire le min est bien le périlune, mais le max est l'altitude d'entrée dans la
sphère — de l'ordre de 60 000 km. La faille ne se voit pas aujourd'hui parce qu'une mission
**sans spec ne traverse jamais `objectiveMet`** (L6 §1.2) ; elle s'ouvrira au premier vol avec
spec. **Conséquence : un lot d'objectif, avant la mission** (`L3`).

**Aucune charge utile du catalogue ne peut exécuter le TLI.** Le seul modèle propulsé est
`GEO_SAT` (2 t sec + 2 t d'ergols, Isp 320 s, 400 N) : son Δv maximal vaut
`3 138 · ln 2 = 2 175 m/s`, contre ~3 120 m/s demandés. **Conséquence : le TLI revient à
l'étage supérieur du lanceur**, et la délégation façon GEO est hors sujet.

---

## 3. Principe du découpage

Quatre règles, dont les trois premières sont reprises de `PHY-1` et `UI-3` parce qu'elles y
ont tenu du premier au dernier lot.

1. **Un changement de comportement à la fois.**
2. **Chaque lot se ferme sur un test exécutable**, pas sur une revue.
3. **Rien n'est branché avant d'être testé seul.** `L1` à `L3` n'ajoutent aucun geste
   utilisateur ; `L4` les branche, `L5` les expose.
4. **La physique avant la mission, la mission avant l'UI.** C'est l'ordre de `PHY-4`.

**Ce que `MIS-4` ajoute n'est pas de la physique.** L6 a volé l'arc. Ce qui manque est un plan
d'injection depuis un plan imposé, une fenêtre qui date le tir, un objectif qui note un survol,
et une mission du produit.

**Contrainte de méthode**, valable pour tout le chantier : les tests d'optimisation et les vols
de quatre jours sont lents, et **c'est l'utilisateur qui les lance**. Aucun lot ne se ferme sur
une exécution de `./gradlew test` faite par l'assistant.

---

## 4. Les lots

| Lot | Ce qu'il rend vrai | Change le comportement ? | Ce qu'il touche |
|---|---|---|---|
| **L0** | La baseline est mesurée | non — zéro ligne de production | rien |
| **L1** | L'injection part d'un plan **imposé** | non (additif) | `mission/maneuver/` |
| **L2** | Le tir est **daté** | non (aucun appelant) | `mission/window/problem/` |
| **L3** | Un survol est **notable** | oui, ferme une faille latente | `mission/objective/`, `MissionLoadEvaluator` |
| **L4** | La mission vole **du sol au survol** | oui, c'est la livraison | `MissionType`, `MissionSpec`, `MissionComposer`, `operation/`, `stage/` |
| **L5** | Elle se crée **au wizard** | oui, surface utilisateur | `ui/mission/wizard/`, `Payloads` |
| **L6** | Le TLI **brûle vraiment** | oui, sur une référence existante | `mission/stage/` |

### L0 — Baseline mesurée

Quatre mesures, aucune ligne de production. Elles décident de `L1` et de `L4`.

1. **La visée converge-t-elle depuis 400 km ?** `TranslunarInjectionPlan` est calibré à
   `PARKING_ALTITUDE = 185 km`, alors que `MissionComposer.parkingAltitudeFor` plafonne à
   400 km. Si elle converge, la chaîne lunaire n'a pas besoin d'une constante à elle ; sinon
   elle en a une, et c'est écrit plutôt que découvert au vol.
2. **Combien de sites Terre-en-dur `MIS-4` traverse-t-il ?** L6 en avait deux sur son chemin
   parce qu'il ne décollait pas. `MIS-4` décolle : `PropellantBudget`, `LaunchPlane`,
   `EarthMission.getInitialState` et `StageEndStateDiagnostic` deviennent tous atteignables.
   C'est le renversement le plus net par rapport à L6, et il se mesure avant d'être subi.
3. **La sortie de sphère arrive-t-elle, et quand ?** L6 a mesuré l'entrée à 3,25 j et le
   périlune à 4,0 j, puis extrapolé « pas de sortie avant 5,5 j ». Personne ne l'a volée.
4. **Le temps de paroi** d'un `solve()` et d'un vol complet, pour borner ce que `L2` peut se
   permettre dans son `confirm()`.

**Ferme sur** : une sonde jetable et un relevé écrit, dans le style du §5.2 de
`multi-corps/04-conception-L2.md`. Rien n'est commité dans `src/main`.

### L1 — L'injection depuis un plan de parking imposé

`TranslunarInjectionPlan.parkingState` **fabrique** aujourd'hui l'orbite de parking pour
qu'elle contienne la Lune à l'arrivée. Ce lot ajoute la fonction inverse : étant donné une
orbite de parking **subie**, trouver le point d'injection — à 170° en arrière de la direction
d'arrivée, dans le plan de parking — et donc la durée du coast de parking qui y mène.

La bissection sur le périlune, le correcteur différentiel et la garde de déclinaison ne bougent
pas. `parkingState` reste, pour la démo et pour les tests unitaires du plan.

**Ferme sur** : un test d'injection depuis un plan arbitraire imposé, et
`LunarTransferFlightTest` inchangé au chiffre près.

### L2 — La fenêtre lunaire

`LunarLaunchWindowProblem`, troisième implémentation de `LaunchWindowProblem`. À chaque date
candidate `t` : le plan atteignable depuis le pas de tir à `t` (azimut dérivé de `i = φ`, comme
le fait `EarthLaunchWindowProblem`), le plan qui contient la Lune à
`t + coast de parking + ToF`, et le coût `2·v·sin(θ/2) + Δv d'injection Lambert`. Le terme de
plan porte le relief, le terme de Lambert départage.

`confirm()` vole l'injection, pour qu'un plancher de périlune ou d'épuisement refuse une date
que la forme close acceptait — exactement la raison pour laquelle
`TranslunarInjectionPlanWindowProblem` a un `confirm()`.

**Kourou est refusé à la construction**, comme `EarthLaunchWindowProblem` refuse déjà une
inclinaison inatteignable (§6 pt 3).

**Ferme sur** : la structure de la fenêtre (une opportunité par jour sidéral), le refus de
Kourou, et le relief mesuré et logué.

### L3 — L'objectif de survol

`FlybyObjective(body, altitude de périlune visée, tolérance)` dans la hiérarchie scellée
`MissionObjective`, dont le javadoc invite précisément à cela, et la branche correspondante dans
`MissionLoadEvaluator.objectiveMet` : **minimum d'altitude sur l'arc lunaire, maximum ignoré**.
C'est ce lot qui ferme la faille du §2.3.

**Ferme sur** : un test sur l'éphéméride de la démo lunaire, sans mission neuve — l'objectif se
note sur des points, pas sur un vol.

### L4 — La mission du produit

`MissionType.LUNAR_FLYBY`, `MissionSpec.Lunar`, la branche du `switch` de
`MissionComposer.compose`, et la chaîne :

```
ascension → AnalyticParkingInsertionStage(185 km, i = φ du site)
          → coast de parking jusqu'au point d'injection (L1)
          → TranslunarInjectionStage (impulsionnel, réutilisé tel quel)
          → TranslunarCoastStage
          → horizon au-delà de la sortie de sphère (~7 j)
```

La chaîne haute existante est déjà paramétrée — `GEOMission` prend son altitude de parking et
son plan en arguments — donc la partie ascension n'est pas à réécrire.

La mission est construite depuis une `MissionSpec.Lunar` bâtie en test : **aucun wizard**.

**Le cas de capture accidentelle est traité ici**, explicitement : une géométrie où la sortie
n'arrive pas avant l'horizon doit lever ou étendre, jamais rendre un survol silencieusement
tronqué.

**Ferme sur** : un vol du sol au survol, arcs `[EARTH, MOON, EARTH]`, périlune dans la
tolérance, `isComplete()`, et le `FlybyObjective` de `L3` satisfait.

### L5 — Le wizard

`MissionProfile.LUNAR_FLYBY` en `Availability.CONSTRAINED` — le statut que `MEO` porte déjà —
un `DynamicParameters` à **un seul champ** (l'altitude de périlune, sur l'`AltitudeRange` du
profil), le step planning branché sur la fenêtre lunaire de `L2`, et le refus de Kourou présenté
comme un refus et non comme une exception.

**Une charge utile lunaire au catalogue.** Les trois modèles de `Payloads` sont nommés pour la
Terre ; proposer « satellite d'observation de la Terre » pour un survol lunaire est incohérent.
Un `PayloadModel` **inerte** de plus — sonde ou module lunaire — et le filtre du step charge
utile. Un record et un filtre : de la donnée, pas une capacité. Le véhicule qui *exécuterait* le
TLI reste hors périmètre (§6 pt 8).

**Ferme sur** : les tests de modèle du wizard, plus un essai manuel.

### L6 — La poussée finie

`TLIBurnStage`, combustion centrée sur le point d'injection — le patron de
`AnalyticGtoInjectionStage`, qui centre déjà la sienne sur l'apogée. Mesuré contre la référence
impulsionnelle que `L4` a laissée.

Ce que l'impulsion coûte, pour une combustion étalée sur un arc `θ` centré, vaut
`1 − sinc(θ/2)` :

| Étage supérieur | Combustion | Arc | Perte contre l'impulsionnel |
|---|---|---|---|
| Falcon Heavy S2 (348 s, 981 kN) | ~47 s | 3,2° | **~0,4 m/s** |
| Ariane 62 ULPM (457 s, 180 kN) | ~275 s | 19° | **~14 m/s** |

Quatorze m/s, c'est tout le bénéfice mensuel de la fenêtre de lancement. C'est ce qui fait de ce
lot une livraison et non un raffinement — mais il vient après, parce que le wizard laisse choisir
le lanceur et que l'erreur dépend donc d'un choix utilisateur.

Les deux contraintes d'étage tiennent par ailleurs, et ce n'est pas ce lot qui les crée : le
coast de parking avant injection vaut au plus une révolution (5 292 s à 185 km), sous les 7 200 s
du S2 comme sous les 21 600 s de l'ULPM, et le `restartCount` des deux étages (2 et 4) laisse la
place au deuxième allumage.

**Ferme sur** : le même vol que `L4`, avec l'écart au plan impulsionnel mesuré et logué.

---

## 5. Ordonnancement et risques

```
L0 → L1 → ┬─ L2 ─┬→ L4 → ┬─ L5
          └─ L3 ─┘       └─ L6
```

`L2` et `L3` sont indépendants l'un de l'autre ; `L5` et `L6` aussi.

**Le risque principal est le temps de paroi**, et c'est pourquoi `L0` le mesure en premier. Un
`TranslunarInjectionPlan.solve()` coûte une trentaine de propagations de quatre jours ; `L2` en
appelle un par candidat confirmé, `L4` en appelle un par vol. Un chantier qui découvrirait ce
chiffre à `L4` n'aurait plus de marge de conception.

**Le deuxième risque est le §2.3, point 2** : combien de sites Terre-en-dur se réveillent quand
la mission décolle. La réponse est inconnue et elle peut déplacer du travail de `L4` vers un lot
antérieur.

**Le troisième est la sortie de sphère.** L6 ne l'a jamais volée. Si elle n'arrive pas où
l'extrapolation la place, l'horizon de `L4` et le demi-bénéfice de `L6` de `PHY-4` sur la bande
morte ε bougent ensemble.

**Ce qui n'est pas un risque** : la physique multi-arcs, la bascule de sphère, le rendu aux deux
échelles et le solveur de Lambert. Les quatre sont livrés, volés et épinglés par `PHY-4`.

---

## 6. Limitations assumées

Douze, toutes identifiées pendant la conception de ce découpage. Elles sont écrites ici pour
qu'aucune ne soit redécouverte comme un défaut.

**Ce qui est figé alors qu'il pourrait ne pas l'être**

1. **Un seul paramètre utilisateur** : l'altitude de périlune. `TIME_OF_FLIGHT = 4 j`,
   `PARKING_ALTITUDE = 185 km` et `TRANSFER_ANGLE = 170°` restent des constantes **couplées**,
   et leur domaine de convergence conjoint n'est pas mesuré. Exposer le temps de vol demande
   d'abord de balayer ce domaine, ou de dériver l'angle du ToF.
2. **L'inclinaison de parking vaut la latitude du site**, en tir due east. Elle **pourrait être
   adaptative**, de deux façons distinctes : saisie par l'utilisateur (`i ≥ φ`), ou azimut
   variable façon Apollo — auquel cas le plan est celui qui contient le pas de tir et la Lune, il
   existe à chaque instant, et le critère à relief n'est plus l'alignement de plan mais le coût
   d'ascension en fonction de l'azimut, que le dépôt ne modélise pas (`LaunchPlane` dérive
   l'azimut de l'inclinaison, jamais l'inverse).
3. **Kourou est refusé pour le lunaire.** Conséquence directe du point 2 : 5,24° ne contient la
   Lune que quelques heures par mois. Canaveral (28,56°, le choix d'Apollo) et Baïkonour (45,97°)
   passent.
4. **Le TLI est impulsionnel jusqu'à `L6`** — ~0,4 m/s d'erreur sur Falcon Heavy, ~14 m/s sur
   Ariane 62.

**Ce qui est promis ailleurs et que `MIS-4` ne livre pas**

5. **Aucune optimisation CMA-ES.** La visée converge à ±1 km ; le seul reste optimisable est
   14 m/s sur 3 182, que la fenêtre capte déjà. La fiche du roadmap promet une « correction
   CMA-ES » : elle est **corrigée par la mesure, pas livrée**.
6. **`propagateStandalone` ne traverse toujours pas la sphère d'influence**, et le coast terminal
   n'est pas volé du tout en passe d'optimisation (§2.3). Sans effet ici puisque rien n'est
   optimisé ; la dette passe à `MIS-5` / `MIS-6`.
7. **Le seed de Lambert reste enfermé** dans `TranslunarInjectionPlan`, avec
   `posigrade = true, nRev = 0`. Contrairement à ce qu'annonce la fiche du roadmap, `MIS-4`
   n'apporte pas le deuxième consommateur qui fixerait la forme de l'API — c'est `MIS-6`. Le
   multi-révolution reste natif et non vérifié.
8. **Aucun véhicule du catalogue n'exécute le TLI**, et ce n'est pas un manque. L'étage supérieur
   y arrive largement :

   | Chaîne | Ergols pour le TLI | Capacité de l'étage |
   |---|---|---|
   | Falcon Heavy S2 + `GEO_SAT` (4 t) | 12,0 t | 107,5 t |
   | Falcon Heavy S2 + `CARGO_MODULE` (15 t) | 28,4 t | 107,5 t |
   | Ariane 62 ULPM + `GEO_SAT` (4 t) | 10,1 t | 31 t |
   | Ariane 62 ULPM + `CARGO_MODULE` (15 t) | 21,1 t (+ ~7 t d'ascension) | 31 t, de justesse |

   Une charge utile propulsée au niveau du TLI ajouterait **une seconde façon de faire ce qui est
   déjà fait**, et exigerait de router la combustion vers la charge utile via
   `requiresPayloadPropulsion` — mécanisme qui n'existe que pour GEO et que rien ici ne force à
   exercer. C'est `MIS-11` (Artemis) qui en aura besoin. Le modèle **inerte** de `L5`, lui, est
   dans le périmètre : il corrige une incohérence de catalogue, pas une capacité.

**Ce qui reste ouvert dans le socle**

9. **`MissionHorizon.Revolutions` garde son µ terrestre** (L6 §11). Hors chemin : `MIS-4` vole un
   `FixedDuration`.
10. **Les huit sites Terre-en-dur** de L6 §11. Combien sont sur le chemin d'un vol `MIS-4` est une
    **mesure de `L0`**, pas une affirmation.
11. **ε, la bande morte de frontière, n'est éprouvé qu'à moitié.** Deux franchissements au lieu
    d'un, ce qui est mieux que L6 — mais toujours pas de trajectoire **capturée**, qui reste
    l'échéance nommée par L6 §5.5 et appartient à `MIS-5`.
12. **La trace redevient décimée** : ~10 000 points à sept jours contre le budget de 8 192 de
    `TrajectoryPolyline`. L6 était la seule mission du dépôt dessinée sommet pour sommet ; `MIS-4`
    perd cette propriété.

---

## 7. Ce que `MIS-4` lègue

- **À `MIS-5`** : la chaîne complète jusqu'au périlune. Il ne reste qu'un étage
  (`LunarInsertionStage`) et l'extension de `FlybyObjective` — pas un troisième type d'objectif
  (§2.2 pt 3). C'est aussi `MIS-5` qui volera la trajectoire **capturée** que réclame la
  calibration de ε.
- **À `MIS-11`** : la branche aller, et le besoin — cette fois réel — d'un véhicule capable
  d'exécuter ses propres combustions.
- **À `MIS-6`** : rien de neuf, et c'est le point. Le seed de Lambert reste à généraliser là,
  parce que c'est là qu'un deuxième consommateur en fixera enfin la forme.
