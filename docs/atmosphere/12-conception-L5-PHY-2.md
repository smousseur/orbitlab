# PHY-2 / L5 — Bascule du défaut + REL-22 — conception

Conception détaillée du lot `L5` de `PHY-2`, découpé en
[`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) §5. `L5` est **le moment « on »** : la
traînée devient le défaut, une mission vole sous atmosphère sauf opt-out explicite, et un
scénario dont l'atmosphère n'est pas `NONE` se restaure enfin.

> **Statut : bascule livrée et le vol drag-on boucle** (2026-09-12). Le placement
> `MissionFactory` (§3.1) et `REL-22` (§3.5) sont livrés ; la conception **corrige le découpage sur
> quatre points** (§6). Le vol de mesure, d'abord rouge, est **vert** après un paquet de trois
> termes de coût drag-conditionnels consigné en **§7** — qui corrige aussi §2.1 (le plancher qui
> compte est celui du gravity-turn, pas celui de `TransferProblem`) et §3.4 (le découpage demandait
> de *relever* un plancher qu'il fallait *retirer* sous traînée).
>
> **Mesuré :** drag-on `399,5 × 420,3 km` contre drag-off `399,6 × 420,4 km`, **ratio de coût
> ×2,4** — pas le ×7,5 redouté, donc **pas d'escalade** vers le levier de `L1` §6 (§7.7).
>
> **Vérifié :** compile (main + test), `pmdMain pmdTest` et `spotlessApply` verts ; fixtures
> rapides vertes (`GravityTurnProblemTest`, `MissionFactoryTest`, `ScenarioSessionTest`, le mapper,
> `AtmosphereChoiceTest`) ; **les quatre gates drag-off à tolérance `0.0` verts** (`gateTest`,
> `cleanTest` d'abord, 11 tests). Ils l'ont d'abord été **avant** le correctif `L4` de §8.2, qui
> change les charges retenues ; **relancés après par l'utilisateur, ils sont verts**
> (2026-09-12), `DefaultAtmosphereCostFlightTest` compris, et l'essai manuel runtime est
> concluant. **Restent côté utilisateur :** les profils GEO et elliptique drag-on (§7.7).
>
> **Le chantier est clos** — bilan, réserves et restes dans
> [`13-cloture-PHY-2.md`](13-cloture-PHY-2.md), dont le §5.1 relève un livrable déclaré de `L2`
> que ni `L3` ni `L5` n'ont porté (le lapse physique du Vulcain).
>
> **§8 est la vérification d'après-coup** : deux sondes écrites pour répondre à « a-t-on calibré sur
> un seul lanceur ? ». Réponse mesurée : non — mais elles ont sorti un défaut de `L4`.

---

## 1. Périmètre du lot

**Dans `L5`.**

- la **bascule du défaut** `MissionSpec` `NONE → NRLMSISE`, portée par le **spec seul** ;
- **`REL-22`** — la restauration d'un scénario, honorant l'atmosphère *sauvée* (y compris
  `NONE`) ;
- le **retrait de l'invariant « au bit près » au niveau du défaut** (§3.6) — une mission par
  défaut n'est plus vide ;
- l'**épinglage `NONE` explicite** des tests chemin-composer que la bascule touche, pour que
  la suite de non-régression reste drag-off (§3.2).

**Hors `L5`, et justifié par la mesure** (§2) — non par omission :

- le **`periapsisFloor`** : le découpage demandait de le relever ; la mesure impose de le
  **retirer sous traînée**, où il bloquait la seule solution qui vole (§3.4, mesure en §7.4) ;
- l'**absorption des pertes dans `dt1MaxPhysical`** : la borne est déjà l'enveloppe maximale.
  L'énoncé ne mord pas. **Documenté, aucun code** (§3.4) ;
- le **levier « atmosphère bon marché à l'optim, NRLMSISE au runtime »** que `L1` §6 nommait
  comme condition possible de la bascule : **différé**. `L5` assume le ×7,5, le mesure sur un
  vol représentatif, et n'escalade vers le levier que si la mesure fait mal (§3.3).

**La contrainte du lot.** `L5` allume, mais **ne fait pas basculer la suite lourde d'optim
en drag-on**. C'est le point que le découpage lisait de travers (§6) : les gros gates
contournent le défaut, donc la bascule ne les re-baseline pas. `L5` bascule la *production*
et **protège** la suite drag-off par épinglage.

**Entrées.** `L4` ([`11-conception-L4-PHY-2.md`](11-conception-L4-PHY-2.md)) — le
dimensionnement final avant d'allumer — et le socle `L1` (l'arrêt d'altitude drag-conditionnel
qui rend une propagation drag-on terminable, et le câblage `DT-14` : l'optim monte le modèle
de la mission).

---

## 2. État des lieux mesuré

L'exploration du code a produit cinq faits. Quatre **corrigent** le découpage ; le cinquième
est le risque que `L1` a explicitement légué à ce lot.

### 2.1 Le `periapsisFloor` déjà relevé n'est pas celui qui compte

**Correction du 2026-09-12.** Cette section affirmait le livrable « `periapsisFloor` relevé »
déjà satisfait en citant
[`TransferProblem.java:405-408`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferProblem.java)
(`PERIAPSIS_FLOOR_MIN = 120 km`, plancher adaptatif à 200 km pour une cible à 400 km). **C'est
la mauvaise classe.** Un LEO circulaire en mode `FAST` — le mode que la production emploie, et
celui du vol de mesure — est composé par
[`MissionComposer.composeEarthOrbit`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/MissionComposer.java)
en `EarthOrbitMission` + `AnalyticHohmannTransferStage` : **`TransferProblem` n'est jamais
instancié dans cette chaîne**. Le seul problème optimisé y est le gravity-turn, et son plancher
de périapsis est celui de
[`GravityTurnProblem.trajectoryCost` §6](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/GravityTurnProblem.java),
`periFloor = −200 000 m`, écrit en dur.

Le découpage §5 visait donc bien un plancher réel et non relevé. Ce que la mesure de §7 établit,
c'est qu'il ne peut pas l'être : le relever **ne répare rien**, pour une raison de structure et
non de calibrage.

### 2.2 « Absorber dans `dt1MaxPhysical` » ne mord pas

[`TransferProblem.java:346`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferProblem.java)
: `dt1MaxPhysical = availablePropellant / massFlow` — c'est **déjà l'enveloppe maximale** (le
temps exact jusqu'à extinction), et
[`:389`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferProblem.java)
`dt1Max = max(K·guessDt1, dt1MaxPhysical)` laisse déjà burn 1 courir à extinction. Il n'y a
rien à « élargir ». La vraie inquiétude de la roadmap
([`01-impacts-fonctionnels-techniques.md`](01-impacts-fonctionnels-techniques.md) §87) était
le **check de faisabilité a priori** (`dvHohmannTotal > dvAvailable` qui jette, sans réserve
drag, [`:361`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferProblem.java))
— un autre mécanisme, et un fix optionnel qui n'est ni promis ni nécessaire tant qu'une
mesure ne le réclame pas.

### 2.3 Basculer le défaut du spec ne re-baseline pas les gros gates d'optim

Le défaut vit à **deux endroits** :
[`Mission.java:41`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/Mission.java)
(`atmosphere = NONE`) **et** la normalisation des quatre constructeurs compacts de
[`MissionSpec`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/operation/MissionSpec.java)
(`if (atmosphere == null) atmosphere = NONE`). Le test
[`AtmosphereChoiceTest`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/AtmosphereChoiceTest.java)
l'écrit noir sur blanc (ligne 70) : la double écriture est **nécessaire** parce qu'« une
mission assemblée sans passer par `MissionComposer` (la classe de base des tests d'optim, les
fixtures) ne voit jamais la normalisation du spec ».

Or les gros tests lents que l'utilisateur lance — `LEOMissionOptimizedTransferTest`,
`LEOMissionOptimizationTest`, `GEOMissionOptimizationTest` — construisent la `Mission`
**en direct** (`EarthOrbitMission.circularWithOptimizedTransfer(...)`,
[`LEOMissionOptimizedTransferTest.java:59`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/LEOMissionOptimizedTransferTest.java))
et ne touchent jamais l'atmosphère → ils restent au défaut **`Mission.NONE` = drag-off**,
quel que soit le défaut du spec. Le critère de fermeture du découpage — « suite complète
re-baselinée drag-on » — est donc **faux** : la bascule du spec les laisse drag-off.

Ce que la bascule touche vraiment, ce sont les tests **chemin-composer**, et certains
**cassent** plutôt qu'ils ne « re-baselinent » :

- [`EarthOrbitNonRegressionTest.theFlownAscent_isIdenticalThroughBothPaths`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/operation/EarthOrbitNonRegressionTest.java)
  affirme *spec-path == direct-path* ; après la bascule, spec-path est drag-on et direct-path
  drag-off → l'égalité tombe ;
- [`CentralBodyBaselineTest`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/operation/CentralBodyBaselineTest.java)
  : ses entrées `leo400`/`geo` sont construites en direct (inchangées), mais `meo`/`polar`
  passent par le composer (`compose(..., FAST)`) → elles bougeraient drag-on.

Cf. [[gates-contournent-le-planner]].

### 2.4 Le ×7,5 que `L1` a légué à ce lot

[`08-conception-L1-PHY-2.md`](08-conception-L1-PHY-2.md) §6 : le vol drag-on d'une ascension
coûte **×7,5** (52,8 s vs 7 s sur le Falcon Heavy LEO-400 analytique), **intrinsèque** (NRLMSISE
est le seul modèle valide à 0 km, et l'ascension traverse l'air dense en pas nombreux), non
abaissé par la règle `a`/`c` ni par un pas-max. Et comme
[`FixedLoadPlanner.plan()`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/planner/FixedLoadPlanner.java)
lance `MissionOptimizer.optimize()` **dans tous les modes** (FAST et BALANCED ne diffèrent
que par la composition des étages, pas par l'exécution — le gravity-turn est optimisé partout),
basculer le défaut fait payer ×7,5 à **tout calcul de production**, y compris les passes FAST
de dimensionnement en deux passes de `L4`. `L1` l'écrivait : « défaut-on, chaque optim paierait
le ×7,5 … demanderait un levier neuf … du design **avant `L5`** ».

### 2.5 REL-22 : le champ existe, la couture de restauration est neuve

Le champ `atmosphere()` est sur `MissionSpec` depuis PHY-1, et le **save** l'écrit
([`ScenarioMapper.java:70`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/scenario/ScenarioMapper.java),
`entry.spec().map(spec -> spec.atmosphere().name())`). Mais la restauration :

- **refuse** explicitement une atmosphère non-`NONE`
  ([`ScenarioSession.requireFlyableAtmosphere`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/scenario/ScenarioSession.java),
  lignes 179-194) ;
- **perd** l'atmosphère : `toMissionValues` ne la porte pas dans la map de valeurs, et
  `MissionFactory.specFromWizardValues` reconstruit tout au défaut du spec ;
- n'a **pas de couture** pour la ré-appliquer : pas de `withAtmosphere`, et l'atmosphère
  n'est pas un champ wizard.

Donc « rien à inventer » est optimiste : retirer le refus est trivial, mais **ré-appliquer la
valeur sauvée est du câblage neuf**. Cela se couple à la bascule : la restauration doit honorer
la valeur *sauvée*, sinon un scénario pré-PHY-2 (sauvé `NONE`) deviendrait silencieusement
drag-on sous le nouveau défaut.

---

## 3. Décisions de conception

### 3.1 La bascule vit dans `MissionFactory`, l'unique origine d'un spec de production

Le défaut de production `NRLMSISE` est appliqué à **un seul endroit** :
`MissionFactory.specFromWizardValues`, en sortie de switch, par
`spec.withAtmosphere(DEFAULT_ATMOSPHERE)`. Ni le champ `Mission.atmosphere` ni la
normalisation des quatre constructeurs `MissionSpec` ne bougent : ils restent **`NONE`**. Le
clivage est net :

- **production** (wizard → `MissionFactory` → `MissionComposer`) = **drag-on** : `MissionFactory`
  est la seule origine d'un spec de production (création wizard et restauration passent par
  `specFromWizardValues` ; une recomposition et `withLauncherLoads` réutilisent le spec existant,
  qui porte déjà son atmosphère). C'est là que « défaut on » devient vrai ;
- **spec assemblé à la main** (les fixtures, `AbstractTrajectoryOptimizerTest`, `BareMission`) =
  **drag-off** sauf opt-in explicite, exactement comme `AscentDragTerminationTest` monte déjà sa
  traînée à la main.

**Pourquoi `MissionFactory` et non la normalisation du spec — mesuré à l'implémentation.**
Basculer la normalisation des quatre constructeurs aurait fait basculer **tout spec monté par
un constructeur de commodité**, ce qui inclut ~7 tests de vol chemin-composer
(`EarthOrbitNonRegressionTest`, `CentralBodyBaselineTest` meo/polar, `MeoMissionTest`,
`BoosterSplitBaselineTest`, `LunarFlybyFlightTest`, `LunarOrbitFlightTest`,
`MeasuredLoadPlannerFlightTest`) — dont quatre lents — à épingler `NONE` sous peine de payer le
×7,5 dans la suite gatée, plus quatre assertions d'atmosphère à inverser. Le placement
`MissionFactory` les laisse **tous** drag-off (ils montent leur spec à la main → `NONE`
inchangé) : la bascule touche ce qu'un utilisateur crée, pas ce qu'un test assemble. C'est un
resserrement du choix « spec seul » de la séance, décidé après avoir mesuré ce rayon (§6).

### 3.2 Presque rien à épingler, la bascule prouvée sans vol lourd

Le placement `MissionFactory` (§3.1) évite l'essentiel des épinglages : les spec montés à la
main restent `NONE`, donc aucun des ~7 tests de vol chemin-composer ne bascule. Deux seuls
tests passent par la factory **et** sont sensibles :

- `ScenarioReplayTest` **vole** (il rejoue puis compare à l'optimisation qui a produit les
  vecteurs) et monte son spec via `specFromWizardValues` → il basculerait drag-on (le test
  resterait vert, la comparaison étant relative, mais paierait le ×7,5). On lui **épingle
  `NONE`** : son objet est la fidélité du replay, pas la traînée ;
- `ScenarioMapperTest` **observe** l'atmosphère du DTO sauvé, entry construite via la factory :
  l'assertion passe de `NONE` à **`NRLMSISE`** — ce qui en fait la preuve que le défaut d'une
  mission wizard voyage jusqu'au fichier.

Aucun autre test n'est touché : `AtmosphereChoiceTest` (la normalisation reste `NONE`),
`EarthOrbitNonRegressionTest`, `CentralBodyBaselineTest`, et les tests de vol lents montent leur
spec à la main → inchangés.

La propriété « défaut on » est prouvée **sans vol lourd** :

1. un **test factory** (`MissionFactoryTest.wizardMission_defaultsToDragOn`) : une mission
   wizard porte `NRLMSISE` sur le spec **et** sur la `Mission` composée — la preuve directe de
   la bascule, sans optimisation ;
2. les **round-trips REL-22** (§3.5) : une mission drag-on sauvée revient drag-on ;
3. l'**essai manuel runtime** : une mission créée au wizard vole visiblement sous traînée.

### 3.3 Le ×7,5 assumé, mesuré, escalade conditionnelle

`L5` **assume** le ×7,5 comme coût one-shot **par calcul** (et non comme une attente
par-évaluation que l'utilisateur subirait en boucle). Il le **mesure** sur **un** calcul
représentatif — un dimensionnement + vol planner du Falcon Heavy LEO-400 drag-on contre le même
drag-off, chronométré — et **consigne** le chiffre. Ce nombre est le **déclencheur
d'escalade** vers le levier « atmosphère bon marché à l'optim » de `L1` §6 : on n'ouvre ce
design que si la mesure le rend nécessaire. C'est la discipline « mesurer puis escalader » du
chantier, et non une borne que `L5` doit tenir.

### 3.4 `periapsisFloor` — l'énoncé du découpage est inversé par la mesure

`dt1MaxPhysical` reste sans objet (§2.2 : la borne est déjà l'enveloppe d'extinction). Le
**plancher de périapsis du gravity-turn**, lui, a bien été touché — mais dans l'autre sens que le
découpage : il fallait le **retirer sous traînée**, pas le relever. Relevé à 120 km il est
inatteignable (le périapsis ne peut pas dépasser l'altitude du hand-off) ; laissé en place il
facture 1 605,5 au seul hand-off qui sorte de l'atmosphère en tenant la fenêtre d'apogée. Le
plancher qui mord réellement sous traînée est celui d'**altitude**, et il est neuf. §7 porte la
mesure, §7.5 le paquet.

### 3.5 REL-22 : `withAtmosphere` et application au restore

- **`MissionSpec.withAtmosphere(AtmosphereModel)`** est ajouté à l'interface scellée et à ses
  quatre records — symétrique de `withLauncherLoads`, et cohérent avec le save qui lit déjà
  l'atmosphère **depuis le spec**, pas depuis la map de valeurs. L'atmosphère reste hors de
  l'abstraction valeurs-wizard.
- **`ScenarioSession.restoreMission`** fait
  `spec = MissionFactory.specFromWizardValues(...).withAtmosphere(atmosphèreDe(mission))` avant
  `new MissionEntry(spec)`. La valeur sauvée est appliquée **verbatim** (y compris `NONE`),
  écrasant le nouveau défaut — un scénario pré-PHY-2 revole donc en vide, comme il a été volé.
- **`requireFlyableAtmosphere`** ne **refuse plus** une atmosphère non-`NONE` : il **parse et
  valide** seulement (un nom de modèle inconnu reste un refus, la règle que `MissionFactory`
  applique déjà à un type de mission illisible).

### 3.6 L'invariant « au bit près » retiré au niveau du défaut

Après `L5`, une mission **créée au wizard** n'est plus vide : le « drag off ⇒ trajectoire
d'avant au bit près » que PHY-1 et le socle `L1` tenaient est **retiré pour la production**.
Mais il ne disparaît pas partout : les fixtures et les gates, qui montent leur spec à la main ou
construisent la `Mission` en direct (défaut `NONE`, hors de la factory), le **gardent sans
qu'on ait à les épingler** — c'est ce qui laisse la suite drag-off verte à `0.0`. L'opt-out
`NONE` reste possible et vole sans traînée ; ce n'est pas un retour à l'état ante (l'Isp, les
poids et le dimensionnement ont changé en `L2`/`L3`/`L4`), c'est correct, et écrit ici pour
qu'on ne le lise pas comme une régression.

---

## 4. Sorties

**Code.**

- **`MissionFactory.DEFAULT_ATMOSPHERE = NRLMSISE`**, appliqué en sortie de
  `specFromWizardValues` par `spec.withAtmosphere(...)` — l'unique origine de production. La
  normalisation des quatre constructeurs `MissionSpec` et le champ `Mission.atmosphere` restent
  `NONE` ;
- **`MissionSpec.withAtmosphere(AtmosphereModel)`** — interface scellée + quatre records ;
- **`ScenarioSession.restoreMission`** applique l'atmosphère sauvée via `withAtmosphere` ;
  `requireFlyableAtmosphere` devient **`restoredAtmosphere`** — elle ne refuse plus une
  atmosphère connue, elle la **lit** (nom de modèle inconnu = refus, blanc/absent = `NONE`).

**Tests.**

- `MissionFactoryTest.wizardMission_defaultsToDragOn` — la bascule prouvée sur le spec **et** la
  `Mission` composée, sans vol ;
- `ScenarioMapperTest` — l'assertion du DTO sauvé passe de `NONE` à `NRLMSISE` (le défaut voyage
  au fichier) ;
- `ScenarioReplayTest` — spec **épinglé `NONE`** (fidélité du replay, hors ×7,5) ;
- `ScenarioSessionTest` — **round-trips REL-22 neufs** (`requireFlyableAtmosphere` n'était exercé
  par aucun test) : un scénario sauvé `NRLMSISE` se restaure `NRLMSISE`, un sauvé `NONE` se
  restaure `NONE`, un nom de modèle inconnu est refusé.

**Mesure consignée.** `DefaultAtmosphereCostFlightTest` (gaté `orbitlab.slowTests`, lancé par
l'utilisateur) chronométre un calcul planner représentatif — FH LEO-400 monté par la factory,
`FixedLoadPlanner.plan()` en FAST — **drag-on contre le même opté-out**, et journalise le ratio
et les orbites atteintes. Terminaison assertée seule (patron de `AscentDragTerminationTest`) : le
chiffre est l'entrée de la décision d'escalade vers le levier, pas un seuil.

---

## 5. Fermeture

1. **Défaut basculé** — `MissionFactoryTest.wizardMission_defaultsToDragOn` vert (spec **et**
   `Mission` composée à `NRLMSISE`).
2. **Suite drag-off intacte** — les quatre gates et les baselines verts à tolérance `0.0`,
   **sans épinglage** (ils montent leur spec à la main → défaut `NONE` inchangé) ; seul
   `ScenarioReplayTest` est épinglé `NONE` (procédure `cleanTest` + gates isolés,
   [`../bugs.md` BUG-7](../bugs.md)).
3. **« On » prouvé** — test factory vert, round-trips REL-22 verts, **et** un LEO direct drag-on
   qui boucle à `399,5 × 420,3 km` (§7.7). Reste l'essai manuel runtime.
4. **REL-22 opérante** — round-trips verts : `NRLMSISE` restauré, `NONE` honoré verbatim, modèle
   inconnu refusé.
5. **×7,5 mesuré et consigné** — `DefaultAtmosphereCostFlightTest` vert : le ratio de production
   vaut **×2,4**, pas ×7,5. L'escalade vers le levier `L1` §6 **n'est pas déclenchée** (§7.7).

---

## 6. Ce que cette conception corrige au découpage

Quatre points ([`06-decoupage-PHY-2.md`](06-decoupage-PHY-2.md) §5, L5) :

| Le découpage disait | Ce que le code impose |
|---|---|
| relever le `periapsisFloor` (« 100 km n'a plus de sens ») | le plancher visé est celui du **gravity-turn** et non celui de `TransferProblem` (§2.1) — et il fallait le **retirer** sous traînée, pas le relever : relevé il est inatteignable, laissé il bloque la seule solution qui vole (§7.3, §7.4) |
| absorber les pertes dans `dt1MaxPhysical` | la borne est **déjà l'enveloppe maximale** (extinction) ; l'énoncé ne mord pas (§2.2) |
| « suite complète re-baselinée drag-on » | **faux** : les gros gates contournent le défaut et restent drag-off ; la bascule ne touche que le chemin composer, protégé par épinglage (§2.3, §3.2) |
| REL-22 : « le champ existe déjà, rien à inventer » | le champ existe, mais la **couture de restauration est neuve** (`withAtmosphere` + application au restore) (§2.5, §3.5) |

Et un risque, non une correction : le **×7,5** que `L1` §6 avait légué comme condition possible
de la bascule est **assumé et mesuré**, pas levé (§2.4, §3.3).

**Un resserrement décidé à l'implémentation.** La séance de conception avait placé la bascule
dans la normalisation des constructeurs `MissionSpec` (« spec seul »). L'audit du code, au
moment d'implémenter, a mesuré que ce placement force à épingler `NONE` sur ~7 tests de vol
chemin-composer — dont quatre lents et des lunaires — sous peine de les faire payer le ×7,5. Le
placement `MissionFactory.specFromWizardValues` (§3.1), l'unique origine d'un spec de
production, bascule la production à l'identique tout en laissant les spec montés à la main à
`NONE` : les tests réellement touchés retombent à deux (`ScenarioReplayTest` épinglé,
`ScenarioMapperTest` dont l'assertion devient une preuve). Le champ `Mission` reste `NONE` dans
les deux placements — l'intention Q2 est tenue ; seul le rayon côté fixtures se resserre.
Décision prise en conversation avant d'écrire le code.

---

---

## 7. Le vol de mesure : pourquoi il était rouge, et les trois termes qui le rendent vert

`DefaultAtmosphereCostFlightTest` a été lancé quatre fois le 2026-09-12. Rouge sur le code de
§4, rouge avec le plancher de périapsis *relevé* que §5 du découpage demandait, rouge au
chronomètre une fois le vol débloqué, **vert** au quatrième. Cette section consigne les quatre,
dans l'ordre, parce que le découpage se trompait de sens sur son propre livrable et que c'est la
mesure qui l'a retourné.

### 7.1 Ce qui est mesuré

| | drag-off (boucle) | drag-on (jette, avant §7.5) |
|---|---|---|
| MECO | 176,3 s — **burn 2 = 0,0 s** | 183,1 s — **burn 2 = 0,0 s** |
| altitude de hand-off | **30,0 km** | 39,9 km |
| périapsis de hand-off | **−17,7 km** | −75,0 km |
| FPA de hand-off | 1,15° | 1,71° |
| transfert | Δv1 163 m/s, **coast 2 661 s**, Δv2 103 m/s | jette |
| orbite atteinte | 399,6 × 420,4 km | — |
| durée du calcul | 83 → 96 s | — |

`java.lang.IllegalStateException: No apogee found within one transfer half-period`
(`AnalyticHohmannTransferStage.computeBurnPlan`, via `insertThenTransfer`).

**Le hand-off bas n'est pas un symptôme de la traînée.** Le profil de référence, en vide, rend
la main à **30,0 km** — épinglé au genou du garde-fou `alt < 30 000` de §5 de `trajectoryCost`
— sur une orbite **−17,7 × 362,8 km**, et le transfert analytique **coaste une demi-orbite
(2 661 s)** depuis cette altitude jusqu'à son apogée. Sous traînée le hand-off monte à 39,9 km
et le périapsis descend à −75 km : dix kilomètres plus haut, cinquante-sept plus bas. Ce qui
casse n'est donc pas un hand-off anormalement bas, c'est que **ce coast-là n'est pas volable
dans l'air**, quelle que soit sa valeur exacte.

### 7.2 Le mur est un effondrement de coefficient balistique, pas une altitude

L'ascension **survit** à 8 km/s à 40 km d'altitude : la propagation du gravity-turn s'y termine
normalement. Ce qui ne survit pas, c'est le coast qui suit, et la raison est dans le catalogue
([`Launchers.java`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/vehicle/catalog/Launchers.java)) :

- pendant le turn, la pile fait 1,27 × 10⁶ kg pour 10,5 m² à `Cd = 0,4` → `Cd·A/m ≈ 3,3·10⁻⁶` ;
- après la séparation S1, l'étage supérieur fait 23,6 t pour les mêmes 10,5 m² à `Cd = 2,2`
  → `Cd·A/m ≈ 9,8·10⁻⁴`, soit **300 fois plus**.

À 40 km (`ρ ≈ 3·10⁻³ kg/m³`) et 8 km/s, cela met le coast à une décélération de l'ordre de
**80 à 100 m/s², près de 10 g**. La même altitude est bénigne sous poussée avec la pile
entière et un mur pour l'étage seul. C'est pour cela que l'ascension drag-on de `L1` passe et
que le transfert ne passe pas.

### 7.3 La première tentative, et ce qu'elle a révélé

Le plancher a d'abord été relevé, exactement comme le découpage §5 le demandait — terme de coût
dans §6 de `trajectoryCost`, conditionné à `hasDrag()`, drag-off au bit près — et posé à
**120 km**. Vol :

```
Final best cost=14.9126, total evals=8120
Consensus: 4 independent explorations descended to cost=14.9126 (within 1.0E-4 relative)
end-state: alt=43 491 m | peri=−11 724 m | vTan=8 151,6 m/s | FPA=2,00°
```

La recherche a bougé — périapsis −75 → −11,7 km — puis s'est arrêtée, quatre explorations sur le
même point à 10⁻⁴ près. La raison est géométrique :

> **le périapsis d'une orbite ne peut pas dépasser l'altitude du dernier point poussé.**

Le MECO a lieu à ~43 km, donc le périapsis y plafonne à ~43 km. 120 km était inatteignable *à
cette altitude de hand-off*.

**Mais la conclusion qu'on en a tirée sur le moment était trop forte.** « Le gravity-turn ne peut
pas finir plus haut » n'était pas mesuré : `CMAESTrajectoryOptimizer` part de l'exposant 1,0 avec
σ 0,3 et se pose à 0,35, l'extrémité *plate* d'une boîte [0,1 ; 3,0] dont la moitié haute — rester
vertical plus longtemps, lofter — n'est quasiment pas échantillonnée. Il fallait mesurer
l'enveloppe avant de conclure.

### 7.4 La sonde d'enveloppe, et le vrai coupable

[`GravityTurnHandoffEnvelopeProbe`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/GravityTurnHandoffEnvelopeProbe.java)
(gatée `orbitlab.probe`, n'assertant rien) parcourt la chaîne de production jusqu'au gravity-turn
comme `MissionOptimizer`, puis **grade une grille régulière sur la boîte du problème** au lieu de
l'optimiser, et imprime le hand-off de chaque candidat. 288 points, 21 s par balayage, drag-on et
drag-off, boîte entière puis fenêtre fine autour de la complétion d'étagement.

Elle a trouvé, **dans la boîte**, le hand-off qui boucle :

```
tT=175,0  exp=0,564 | alt=103,1 km | peri=−1 663,1 km | apo=396,5 km | vTan=7 263,8 | FPA=7,26° | cost=1 605,82
```

103 km d'altitude — quatre ordres de grandeur de densité sous le mur — et son **apogée à 396,5 km,
dans la fenêtre [358,2 ; 400]**. Le coast qui suit ne redescend jamais : il reste au-dessus de
103 km.

**Et sur ses 1 605,82 de coût, 1 605,5 sont exactement le plancher de périapsis en vide**
(`30·((−200 km − (−1 663 km))/200 km)²`). Le terme que `L5` cherchait à *relever* est celui qui
**bloquait** la solution. Un hand-off assez haut pour sortir de l'air est atteint en loftant sur
un burn de cœur écourté : il est raide, excentrique, et son périapsis est nécessairement profond.

Deux autres faits de la grille : **3 points sur 288** seulement placent le hand-off au-dessus de
90 km avec l'apogée dans la fenêtre — la région viable est étroite, ce qui explique qu'une
recherche ancrée à l'exposant 1,0 et tirée vers le bas par le plancher ne la trouve jamais ; et le
même point vaut `alt 107,0 / apo 437,4 / cost 1 471,8` **en vide** — le plancher étouffe cette
famille dans les deux environnements, le vide ne s'en plaint pas parce que son hand-off bas vole
très bien.

### 7.5 Le paquet livré — les deux planchers

Tout est conditionné à `hasDrag()`, et le drag-off est **bit-identique par construction** : chaque
terme reprend son littéral d'origine quand la traînée est absente.

1. **Le plancher de périapsis devient vide-seulement.** Ce n'est pas une précaution : c'est le
   blocage de §7.4. Il n'a rien à protéger sous traînée non plus — le coast que le transfert vole
   depuis le hand-off est la branche **ascendante**, donc il n'atteint jamais ce périapsis.
2. **Le garde-fou d'altitude de §5 passe de 30 km à 100 km sous traînée.** C'est la grandeur que la
   traînée lit réellement : le coast montant ne descend jamais, donc son altitude minimale *est*
   celle du hand-off. 100 km se place juste sous les 103,1 km du candidat mesuré. Il facture au
   hand-off bas `100·((100 − 39,894)/100)² ≈ 36` contre les 0,022 qu'il coûtait.

`GravityTurnManeuver.hasDrag()` est l'accesseur neuf qui porte l'environnement du `FlightContext`
jusqu'au coût.

### 7.6 Le seuil d'acceptation, et pourquoi ce n'est pas la fenêtre FPA

Les deux planchers font boucler le vol — mais le premier juge après eux **expire** :
`timed out after 1200000 ms`. Le gravity-turn drag-on dépensait **13 514 évaluations contre 1 989**
en vide, douze minutes contre quarante secondes, en journalisant
`Final cost 0.3710739 above acceptable 0.0476` — alors que les quatre explorations s'accordaient
sur la solution retenue à **sept chiffres**. Décomposition du résidu 0,371074 :

| terme | valeur |
|---|---|
| `W_FPA_SOFT · (6,81°)²` | **0,3532** |
| fenêtre FPA au-delà de 2,5° | 0,0113 |
| dépassement d'apogée (425,7 km) | 0,0021 |
| déficit vTan | 0,0007 |

**98 % du résidu est la paire FPA**, et c'est un plancher structurel : un hand-off lofté n'est pas
à plat. C'est exactement la situation que le commentaire d'`ACCEPTABLE_COST` décrit déjà pour le
vide — il dimensionne l'acceptation sur le FPA irréductible du profil de référence (2,1°, marge à
2,5°) — à ceci près que sous traînée ce profil est un autre : **6,81° mesuré, même marge, 8°**.

Donc `getAcceptableCost()` devient `hasDrag() ? W_FPA_SOFT·(8°)² : W_FPA_SOFT·(2,5°)²`. **Seule
l'acceptation bouge, pas le paysage de coût** : aucun candidat ne change de rang, et le hand-off
retenu est inchangé au 7ᵉ chiffre. `W_FPA_SOFT` est laissé intact délibérément — le commentaire
qui le porte consigne que retirer la traction vers un hand-off à plat dégrade la circularité finale
~6× (ecc 1,4e-4 → 8,5e-4) ; l'affaiblir ici aurait changé *qui gagne* au lieu de *quand on
s'arrête*. À 8°, une mission drag-on qui rendrait la main plus haut déclenche toujours le WARN, ce
pour quoi il existe.

### 7.7 Le résultat, et la décision d'escalade

```
[PHY-2 L5 ×cost] NONE run:     80,2 s, achieved 399,6 x 420,4 km, final mass 14 175,5 kg
[PHY-2 L5 ×cost] NRLMSISE run: 192,5 s, achieved 399,5 x 420,3 km, final mass 14 419,8 kg
[PHY-2 L5 ×cost] ... ratio ×2,4
```

Même orbite à 100 m près, dans la marge ±7 % de la cible. Aucun WARN, aucun timeout, 273 s au
total. Le gravity-turn drag-on rend la main à **99 430 m, FPA 6,80°**, et le transfert analytique
y trouve son apogée (`dv1 = 1 029,6 m/s, dtCoast = 2 702 s, dv2 = 78,9, apogee alt = 400,0015 km`).

**Non-régression drag-off** : les quatre gates à tolérance `0.0` sont verts, lancés par `gateTest`
(`forkEvery = 1`) précédé de `cleanTest` — 11 tests, aucun échec, `CentralBodyBaselineTest`
compris. Le juge le montre aussi directement : le hand-off drag-off reste `alt = 29 998 m` pour un
coût de `0.010120768689750793`, chiffre pour chiffre celui d'avant le paquet.

**Le ×7,5 de §3.3 n'existe pas au niveau du calcul : il vaut ×2,4.** `L1` §6 mesurait la
propagation d'ascension seule ; un calcul de production complet — dimensionnement en deux passes
compris — dilue ce facteur par trois. **L'escalade vers le levier « atmosphère bon marché à
l'optim » n'est donc pas déclenchée**, et ce levier reste fermé.

Une observation à ne pas surinterpréter : la masse finale drag-on (14 419,8 kg) est *supérieure* à
la drag-off (14 175,5 kg). Ce n'est pas un gain de performance — le dimensionnement deux passes
charge les étages différemment sur les deux trajectoires, et la masse finale inclut le résidu
ergol. À vérifier avant de s'en servir comme chiffre de performance.

**Ce qui reste ouvert.** La mesure porte sur **un** profil : Falcon Heavy + 10 t, LEO-400, Kourou.
Les profils GEO et elliptique drag-on n'ont pas été volés, et le plancher d'altitude les concerne :
une cible basse resserre la fenêtre d'apogée (un parking GEO à 200 km plafonne `maxApogee` à
200 km) et pourrait rendre un hand-off à 100 km incompatible avec elle. Le défaut secondaire
d'`insertThenTransfer` sous traînée — la branche de secours **abaissait** l'orbite qu'elle voulait
relever, −11,7 × 1 093 km → −32,4 × 532 km — n'est plus atteint mais n'est pas réparé.

---

## 8. Ce que la vérification a trouvé un lot plus haut

Le vol drag-on de `L5` étant vert, la question posée ensuite était : *a-t-on calibré trois termes
sur un seul lanceur ?* Deux sondes ont été écrites pour y répondre, et elles ont démenti le
soupçon tout en sortant un défaut réel — **dans `L4`**, que `L5` ne pouvait pas voir.

### 8.1 Le paquet `L5` ne contraint qu'un lanceur sur deux

[`GravityTurnHandoffEnvelopeProbe`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/GravityTurnHandoffEnvelopeProbe.java)
balaie la boîte du problème par lanceur ;
[`LauncherDragConvergenceProbe`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/LauncherDragConvergenceProbe.java)
vole le calcul de production. Ensemble elles séparent une limite de physique d'une limite de
recherche.

| | hand-off | FPA | coût GT | orbite atteinte |
|---|---|---|---|---|
| Falcon Heavy + 10 t @ 400 | 99,4 km | 6,80° | 0,371 | 399,5 × 420,3 km |
| **Ariane 64 + 10 t @ 400** | **264,7 km** | **0,01°** | **8,7e-5** | 398,9 × 415,1 km |

Le plancher d'altitude de §7.5 **ne mord que sur le Falcon Heavy** : l'Ariane 64 rend la main
165 km au-dessus, quasi-circulaire, sans qu'on le lui demande — son Vinci (180 kN, Isp 457) peut
faire une vraie insertion là où un Falcon Heavy chargé de 10 t reste surpuissant et n'a qu'un
compromis à offrir. Balayage d'altitude complet (550, 800, 1 500 km, les deux lanceurs, les deux
environnements) : **huit vols, huit convergences**, drag-on et drag-off s'accordant au décimètre.

**Une hypothèse tombée en route, à consigner.** `buildInitialGuess()` vaut `burn1Duration + 20`,
soit 150 s sur l'Ariane dont la bonne solution est à 617 s — 15,6 σ d'écart. La conclusion
« l'initialisation est calibrée pour le Falcon Heavy » était fausse : les runs 1-3 de la phase
d'exploration partent de `perturbGlobal`, dispersés dans toute la boîte, et CMA-ES retient bien
617 s (`bound usage: transitionTime = 77 %`). L'écart en σ ne mesurait que le run 0.

### 8.2 Le dimensionnement pouvait rendre un plan qu'il jugeait infaisable

L'imprécision réellement observée sur l'Ariane est ailleurs. `MeasuredLoadPlanner.plan()` sortait
de sa boucle sur `MAX_SIZING_PASSES` en gardant la **dernière** passe, sans la comparer aux
précédentes. Sur l'Ariane 64 à 400 km la boucle **diverge** :

| passe | S2 chargé | résidu | orbite moyenne | e |
|---|---|---|---|---|
| 1 | 18 048 kg | 66,5 % | 409 666 × 409 923 m | 1,9e-5 |
| 2 | 3 804 kg | 92,8 % | 409 672 × 409 919 m | 1,8e-5 |
| **3** | **246 kg** | **0,0 %** | **404 333 × 409 681 m** | **3,9e-4** |

La chute d'un facteur 15 vers la passe 3 est le couplage que la Javadoc de `MAX_SIZING_PASSES`
annonce : les 272 kg mesurés à la passe 2 ont été brûlés sur une trajectoire que le
redimensionnement précédent avait lui-même changée — à ces charges le gravity-turn cesse de
rallumer l'étage supérieur (burn 2 tombe à 0 s, MECO de 620 s à 457 s). L'étage finit à sec, le
trim n'a plus rien à brûler, et l'orbite livrée perd un facteur 21 en circularité. C'est le point 4
que le découpage §6 avait laissé ouvert : « la convergence du dimensionnement — deux passes, ou
itération si le résidu ne se referme pas ».

**Le correctif.** La boucle retient la passe la moins chère dont l'étage dimensionné n'a pas fini à
sec, et ne rend la dernière que si elle est faisable. La préférence est volontairement asymétrique
— un étage sur-chargé coûte de la masse morte, un étage à sec coûte l'orbite, qui est le livrable.
Quand la boucle converge, elle sort sur `next.isEmpty()` avec un résidu dans la bande : le repli ne
se déclenche alors jamais, et le Falcon Heavy est intouché **par construction**.

**Le seuil de rejet est distinct du plancher de redimensionnement**, et cela a été mesuré, pas
supposé. `DEFAULT_RESIDUAL_FLOOR_RATIO` (1 %) répond à « faut-il payer un vol de plus ? » ;
`DRY_STAGE_RESIDUAL_RATIO` (0,1 %) répond à « le trim avait-il de quoi brûler ? ». Réutiliser le
premier pour le second rejetait la passe à **0,37 %** de l'Ariane en vide — 1 kg sur 273, insérée
proprement à e = 1,8e-5 — pour 2 068 kg de propergol mort et une excentricité légèrement pire. Le
seuil est posé dans l'intervalle mesuré (0,0 % à rejeter, 0,37 % à garder) ; une passe qui
atterrirait entre 0,1 % et 1 % est le signal qu'il faut le déplacer.

**Mesuré après correctif** (les trois prédictions tenues au kilo près) :

| | avant | après |
|---|---|---|
| Ariane @ 400 drag-on | 398,9 × 415,1 km, e = 3,9e-4, 16 076,7 kg | **399,3 × 420,3 km, e = 1,8e-5**, 19 608,4 kg |
| Ariane @ 400 drag-off | 399,5 × 420,4 km, 16 077,7 kg | **inchangé** (passe 3 gardée, 273 kg) |
| Falcon Heavy @ 400 | 399,5 × 420,3 / 399,6 × 420,4 | **inchangé au chiffre près**, aucun repli |

Le gain de circularité drag-on se paie **+3 532 kg de propergol mort**, faute d'une passe
intermédiaire que le budget de trois vols n'atteint pas. C'est le prix assumé de l'asymétrie ; le
lever demanderait une quatrième passe ou une interpolation entre les deux dernières, et n'a pas été
mesuré.

### 8.3 Un bruit de journalisation, corrigé au passage

`StageChainRunner.plain()` est le runner de la passe d'optimisation, et sa propre Javadoc dit qu'un
étage qui échoue y est **le contrat de pénalité** : la chaîne avorte, `trajectoryCost` lit
`elapsed < 1 s` et note le candidat comme échoué. Le journaliser en WARN produisait une ligne par
candidat rejeté, depuis chaque thread d'exploration, sérialisées sur un appender —
`Propagation failed for stage 'S1 separation': minimal step size reached`. Passé en `debug` sur
cette passe uniquement ; sur un vol réel (`sampling()`) un étage qui ne propage pas reste une
anomalie et reste en WARN. C'est le partage que `StageSeparationStage#logJettison` faisait déjà
pour la ligne de largage, et dont la séparation avait été oubliée.

*Document rédigé le 2026-09-12, après exploration du code et séance de conception en
conversation.*
