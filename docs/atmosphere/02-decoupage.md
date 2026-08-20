# PHY-1 — Atmosphère : la brique, désactivée par défaut — découpage

Item roadmap : `PHY-1` (★4 ◆3 L), phase 3. Ce document ne conçoit pas en détail :
il **découpe**. Chaque lot y est défini par la propriété qu'il rend vraie, par ce
qu'il consomme, par ce qu'il produit et par le test qui le ferme.

Il ne remplace pas [`01-impacts-fonctionnels-techniques.md`](01-impacts-fonctionnels-techniques.md),
qui reste l'étude d'impacts. Il en **corrige** en revanche six affirmations sur le
code, périmées depuis sa rédaction du 2026-05-05 — antérieure à `MIS-7`, `PHY-4`
et `MIS-2` (§2.2 ci-dessous).

> **Statut : découpage arrêté, conception à écrire lot par lot.** Les valeurs
> numériques citées comme cibles de mesure sont des ordres de grandeur à
> confronter, pas des seuils arrêtés. Elles sont écrites parce qu'un test « on
> vérifiera que c'est cohérent » n'est pas un test.

> **Amendé le 2026-08-20**, après la conception de `L1`
> ([`04-conception-L1.md`](04-conception-L1.md)). Deux changements de fond : le lot
> de renommage est **dissous** et les lots sont renumérotés `L0 → L1 → L2` (§5) ;
> `FlightContext` **compose** `GravitationalContext` au lieu de le renommer (§3.2),
> ce qui supprime le piège `ArcTransition` du §3.6 et le legs à `MIS-5` du §8. Les
> corrections de détail sont récapitulées au §9.

---

## 1. Périmètre

**Dans PHY-1** — la brique de traînée, livrée éteinte :

- propriétés aérodynamiques au catalogue véhicule, agrégées sur l'étage actif ;
- énumération des modèles d'atmosphère et leur construction Orekit, mise en cache ;
- câblage jusqu'aux propagateurs, par extension du contexte que `PHY-4` a installé ;
- choix par mission, porté par le spec de mission et écrit en un seul endroit ;
- **au moins un vol réellement volé avec traînée**, confronté à des valeurs
  connues par ailleurs.

**Hors PHY-1**, et à ne pas y laisser glisser :

- relever le `periapsisFloor`, absorber les pertes dans `dt1MaxPhysical`,
  re-baseliner les tests d'optimisation, basculer le défaut à « atmosphère
  activée » — c'est `PHY-2` en totalité ;
- reprendre les ISP « proxy » du catalogue (§2.3) — `PHY-2` également, mais
  PHY-1 en **chiffre la dette** ;
- `MaxQDetector`, détecteur d'interface atmosphérique, télémétrie Q et traînée
  instantanée, sélecteur de fidélité au wizard — c'est `PHY-3`. PHY-1 crée le
  champ que ce sélecteur pilotera ; il n'expose rien ;
- atmosphère non terrestre (Mars, Vénus, Titan), coefficient de traînée fonction
  du nombre de Mach, portance, échauffement, ablation, pression de radiation
  solaire.

**La contrainte non négociable**, qui est la raison d'être du découpage en deux
items : `AtmosphereModel.NONE` ⇒ propagation **identique au bit près** à
aujourd'hui. C'est ce qui permet de livrer la brique sans toucher aux
calibrations Falcon Heavy et Ariane 62, et donc de la livrer avant les missions
lunaires plutôt qu'après.

---

## 2. État des lieux

### 2.1 Ce que le code contient aujourd'hui

**Aucune ligne d'atmosphère.** Une seule occurrence du mot dans `src/main`, et
c'est un commentaire du catalogue (§2.3). Tous les propagateurs sont purement
gravitationnels.

**Deux factories de propagateur**, et non trois :

| Factory | Champ | Signature |
|---|---|---|
| `createTestPropagator` | newtonien | `(GravitationalContext, double maxStep)` |
| `createOptimizationPropagator` | 8×8 | `(GravitationalContext, double maxStep)` |

`GravityFieldFactory.getNormalizedProvider(8, 8)` est le **seul** appel de champ
harmonique de `src/main`.

**Vingt-et-un sites de construction de propagateur**, dans quatorze fichiers,
répartis en trois familles :

| Famille | Sites | Rôle |
|---|---|---|
| Vol runtime et replay | 1 | `StageLegRunner.fly` — un propagateur par étage **et par leg** |
| Marche de l'optimiseur | 8 | les `propagateStandalone` des étages |
| Prédictions analytiques internes | 12 | détection de nœud, d'apogée, simulation de poussée, planification de transfert |

**Le sillon existe déjà et il est payé.** `PHY-4 / L1` a fait du corps central une
donnée portée par l'étage : `Mission.gravitationalContext()` →
`MissionStage.gravitationalContext(Mission)` → les vingt-et-un sites, qui
**prennent tous déjà le contexte** en paramètre ou en champ, y compris les
helpers privés (`timeToNextNode(SpacecraftState, GravitationalContext)`,
`detectStateAtApogee(…, GravitationalContext)`). Huit des sites écrivent
littéralement `computePlan(state, mission.getVehicle(), gravitationalContext(mission))` :
état, véhicule et contexte y sont déjà réunis.

**L'étage actif est déjà résolu depuis la masse.** `VehicleStack.resolveActiveStage`
porte un invariant écrit : *l'étage actif ne change que par un largage explicite ;
la combustion ne peut pas le changer, si longue soit-elle*. Un largage est un
`StageSeparationStage`, donc un étage à part, donc un propagateur neuf.

**Les gates de non-régression existent.** `CentralBodyBaselineTest` épingle 62
frontières sur quatre profils × deux passes, à tolérance **`0.0`** — comparaison
de records entiers, c'est-à-dire égalité `double` exacte. Plus
`MissionPolylineBaselineTest`, `EarthOrbitNonRegressionTest` et
`AscentBaselineN2Test`.

**Les données solaires sont déjà embarquées.** `src/main/resources/orekit-data.zip`
(20,4 Mo, tracké) contient `CSSI-Space-Weather-Data/SpaceWeather-All-v1.2.txt`,
couvrant **1957-10-01 → 2096-10-01** (observé, prédit, ajustement mensuel), et 308
fichiers MSAFE F10.7 (avr. 1999 → sept. 2024).

### 2.2 Les six affirmations de `01-…` qui sont fausses

| Affirmation (2026-05-05) | Mesuré le 2026-08-20 |
|---|---|
| « Trois propagateurs : Simple, Optim 8×8, Default 50×50 » | Deux. Le 50×50 n'existe plus, `createSimplePropagator` non plus |
| « Les **deux** points de modification critiques sont `GravityTurnManeuver` et `TransfertTwoManeuver` » | Vingt-et-un sites dans quatorze fichiers — mais un sillon commun les traverse déjà |
| « Champ `Optional<AerodynamicProperties>` » | Interdit : `Optional` est un type de retour seulement. Patron maison = composant nullable + prédicat |
| « Piège Orekit : un `IsotropicDrag` construit une fois ne verra pas la nouvelle surface à la séparation » | Le piège n'existe pas dans cette architecture (§3.3) |
| « Vérifier le contenu de la zip embarquée [indices solaires] » | Présents et mesurés (§2.1) |
| « `src/main/resources/` est exclu de git, donc la zip est chargée à part » | Faux : seul `models/**` est exclu ; la zip est trackée |

Ces corrections ne sont pas cosmétiques : la première retire à la spec sa
répartition « Harris-Priester pour l'optimisation, NRLMSISE-00 pour le runtime »,
qui reposait sur une couture 8×8 / 50×50 disparue ; la deuxième transforme un
chantier de deux points en un chantier d'un seul sillon ; la quatrième supprime le
risque technique que la spec présentait comme le plus délicat.

### 2.3 Ce que la spec ne dit pas, et qui pèse le plus

**Le catalogue compense déjà la traînée, dans la mauvaise variable.**
`Launchers.FALCON_HEAVY` déclare son premier étage à un ISP de 296 s, avec ce
commentaire :

> *Mean-trajectory ISP (sea level 282 s / vacuum 311 s): with no atmosphere
> modeled, 296 s is the proxy for real ascent losses.*

`Launchers.ARIANE_62` applique explicitement la même règle (300 s dans un
intervalle [271 s, 331 s]).

Autrement dit, les deux lanceurs du catalogue portent une atmosphère implicite,
encodée dans leur impulsion spécifique. Activer la traînée sans reprendre ces ISP
compterait les pertes **deux fois**. PHY-1 n'y touche pas — ce serait renoncer à
« drag off ⇒ identique au bit près », et donc à la raison même de livrer la brique
tôt — mais il en **chiffre** la dette, et c'est une entrée obligatoire de `PHY-2`.

---

## 3. Décisions de conception

Six décisions, prises avant le découpage parce qu'elles le déterminent.

### 3.1 Le lot se ferme sur deux preuves, pas une

PHY-1 va jusqu'au vol allumé. Le lot produit donc **une égalité** (drag off, rien
n'a bougé) et **une mesure** (drag on, la traînée fait ce qu'elle doit). C'est la
forme du lot `L2` de PHY-4 — troisième corps opt-in, fermé par une accélération
analytique et une dérive GEO.

La forme écartée est « la brique jamais exécutée » : elle laisserait à `PHY-2` un
chemin drag-on n'ayant jamais tourné, dont il découvrirait les défauts en même
temps qu'il recalibre. Deux changements de comportement à la fois, ce que le
principe de découpage interdit (§4).

### 3.2 L'atmosphère voyage dans le contexte de vol

> **Amendé le 2026-08-20.** La forme d'origine était un renommage de
> `GravitationalContext` en `FlightContext`, étendu d'un composant `drag` nullable et
> d'une méthode `withDrag(…)` calquée sur `withPerturbers(…)`. La forme retenue est
> une **composition** — le détail et les mesures qui l'ont décidée sont en
> [`04-conception-L1.md`](04-conception-L1.md) §1.

```java
record FlightContext(GravitationalContext gravity, DragContext drag)   // drag nullable
```

`GravitationalContext` survit intact, avec ses six composants et ses deux invariants.
Zéro changement de signature sur les vingt-et-un sites de propagateur : le contexte y
voyage déjà, et il est **transporté plutôt que déréférencé** — hors du paquet
`gravity`, `src/main` ne lit ses accesseurs que quatorze fois.

**C'est ce choix qui rend la non-régression structurelle plutôt que mesurée.**
`FlightContext = (gravité, traînée)` est littéralement la liste de forces du
propagateur : la moitié gravitationnelle se monte en `setMu` + `nonCentralField` +
`addPerturbers`, la moitié aéro est exactement le couple
`DragForce(Atmosphere, DragSensitive)` qu'Orekit demande. Donc `drag == null` ⇒ aucun
`DragForce` monté — pas une force identité, pas un terme nul. Le « au bit près » est
une **propriété du type**, démontrable par lecture ; les gates deviennent une
confirmation, pas la preuve. C'est le mécanisme par lequel `PHY-4 / L2` a rendu sa
propre non-régression structurelle avec un ensemble de perturbateurs vide, obtenu ici
par la forme du record au lieu d'une convention à vérifier.

### 3.3 Le Cd et la surface viennent du catalogue, par étage

`StageModel` et `PayloadModel` gagnent un composant `AerodynamicProperties`
nullable ; l'agrégation sur l'étage actif se fait via `resolveActiveStage(mass)`.

> **Corrigé le 2026-08-20** : *« exactement comme `VehicleStack` le fait déjà pour
> `propulsion()` »* était inexact. `VehicleStack.propulsion()` rend
> `vehicles.getFirst().propulsion()`, le **premier** étage et non l'actif ;
> l'agrégation sur l'étage actif passe par `ActiveStageInfo`, et c'est celle-là qu'on
> imite. `VehicleStack` n'override pas `aerodynamics()`
> ([`04-conception-L1.md`](04-conception-L1.md) §3.1).

**Le record a deux composants, pas trois** : `crossSection` et `dragCoefficient` —
**dans cet ordre**, corrigé le 2026-08-20 pour s'aligner sur
`IsotropicDrag(crossSection, dragCoeff)`, son unique consommateur : deux `double`
adjacents dans l'ordre inverse sont une transposition silencieuse qu'aucun test
d'unité n'attrape.
La spec en proposait un troisième, `liftCoefficient` : il n'a aucun consommateur
avant une rentrée atmosphérique, `IsotropicDrag` ne l'expose pas, et un composant
qu'on ne peut pas brancher est un composant qu'on ne peut pas tester.

**Un étage sans propriétés aéro ne traîne pas.** C'est ce qui rend l'opt-in vrai
*jusqu'à la donnée* et rend prévisible un catalogue partiellement renseigné : si
l'étage actif n'en déclare pas, l'engin ne traîne pas, même si un autre étage du
stack en déclare.

**Deux populations de coefficient, et c'est de la physique, pas une commodité.**
La spec recommandait « une valeur de Cd par véhicule » et proposait 2,2. Or 2,2 est
un coefficient de **régime libre moléculaire** — celui d'un satellite en orbite. Un
lanceur en ascension vole en régime continu, où le Cd rapporté à la section
frontale vaut plutôt 0,3 à 0,5, avec un pic transsonique. Appliquer 2,2 pendant
l'ascension surestimerait la traînée d'un facteur voisin de cinq, c'est-à-dire
exactement là où `PHY-2` ira chercher sa crédibilité. Le découpage du modèle règle
la difficulté sans table Mach : les étages de premier étage portent un Cd de régime
continu, tout le reste un Cd libre moléculaire.

> **Corrigé le 2026-08-20.** La coupure écrite ici — *« les étages lanceur volent
> l'atmosphère, la charge utile vole l'orbite »* — était décalée d'un cran : un étage
> **supérieur** s'allume au-dessus de 70 km, en régime déjà transitionnel-à-libre-
> moléculaire. La coupure juste est **premier étage / tout le reste**. Le choix y est
> numériquement sans conséquence — ρ y est quatre ordres de grandeur sous le régime
> que le premier étage traverse ([`04-conception-L1.md`](04-conception-L1.md) §4.1).

**Le piège Orekit signalé par la spec n'existe pas ici.** À la séparation d'étage,
un `IsotropicDrag` figé ne verrait pas la nouvelle surface — c'est vrai dans une
architecture à propagateur unique. Ce n'en est pas une : `StageLegRunner` construit
un propagateur par étage *et par leg*, la séparation est un étage à part, et
l'invariant de `VehicleStack` garantit que l'étage actif ne change pas autrement.
Une surface figée à l'entrée d'étage est donc **exacte par construction**, pas
approchée.

Corollaire, à écrire pour qu'on ne le réintroduise pas : un `DragSensitive` maison
qui re-résoudrait l'étage actif à chaque pas serait du code défensif inatteignable,
affirmant faussement que le modèle supporte un étagement intra-phase. C'est
précisément l'erreur que `VehicleStack` documente avoir déjà commise avec les
étages analytiques à deux poussées.

### 3.4 Le choix vit sur `MissionSpec`

`MissionSpec` gagne un composant `AtmosphereModel`, normalisé à `NONE` dans le
constructeur compact quand il est nul — comme `MissionHorizon` et `NodeBranch` y
font déjà. `MissionComposer` en devient l'**unique écrivain**, exactement comme
pour l'horizon.

Trois conséquences, et les trois comptent :

1. l'intention utilisateur survit aux recompositions de `MissionEntry` (bascule de
   mode d'optimisation, édition depuis le wizard), qui remplacent la `Mission` mais
   pas le spec — c'est le défaut documenté qui avait fait migrer `MissionHorizon`
   vers le spec ;
2. `UI-3` aura un champ à sérialiser sans en inventer un. La roadmap l'exige
   explicitement : *« le champ modèle d'atmosphère du format doit exister dès
   `UI-3`, même s'il ne vaut que `NONE` — sinon les scénarios d'avant deviennent
   silencieusement faux au moment du basculement »* ;
3. `PHY-3` n'aura qu'à brancher un sélecteur sur un champ existant.

Coût mesuré : cinq sites de construction dans `main`, treize dans les tests. Les
constructeurs de commodité en absorbent la majorité, comme lors de `MIS-2` pour
`targetRaan`.

### 3.5 Les trois modèles sont livrés ensemble

`NONE`, `HARRIS_PRIESTER`, `NRLMSISE`. Les deux modèles sont des constructions
Orekit pures et leurs données sont déjà dans la zip (§2.1).

La raison de ne pas s'arrêter à Harris-Priester est la même qu'au §3.1 : livrer un
modèle jamais exécuté ferait découvrir à `PHY-2` le câblage des indices solaires au
moment même où il recalibre. Livrer les deux permet en outre la mesure la plus
informative pour `PHY-2` — l'écart entre les deux modèles à 250 km — dès le lot du
vol allumé.

Ce que cela oblige à écrire : NRLMSISE-00 lit des indices datés. Le fichier CSSI
est **statique** et couvre jusqu'en 2096, donc les résultats sont déterministes *à
zip constante* ; une mise à jour d'`orekit-data.zip` les déplacerait sans que rien
ne le signale. C'est une dépendance, pas un détail.

### 3.6 Un seul résolveur, sur `MissionStage`

Le mécanisme qui relie §3.2, §3.3 et §3.4 :

```
flightContext(entryState, mission)
  = new FlightContext(
        gravitationalContext(mission),
        modèle == NONE || aéro absente ? null
            : new DragContext(aéro de resolveActiveStage(entryState.getMass()),
                              mission.getAtmosphere()))
```

De forme identique à `maxStepSeconds(entryState, mission)` : quatrième déclaration
du même principe — *une phase est l'unité qui sait ce qu'elle vole, donc c'est
l'unité qui le déclare*.

- `StageLegRunner.fly` l'appelle pour le vol ; les huit `propagateStandalone`
  l'appellent pour la marche de l'optimiseur.
- **Quand le modèle vaut `NONE`, le résolveur rend un contexte dont la moitié aéro
  est `null`**, et dont la moitié gravitationnelle est l'instance partagée elle-même,
  pas une copie. C'est l'ancrage concret du §3.2.
- Les douze prédictions analytiques internes héritent du choix sans le savoir,
  puisqu'elles reçoivent le contexte de leur appelant. Un détecteur de nœud verra
  donc la traînée, et c'est le bon comportement : une date de nœud décalée par la
  traînée est plus juste, pas moins.

**~~Le piège `ArcTransition`, borné et journalisé.~~ Supprimé le 2026-08-20.** Le
découpage prévoyait ici que `ArcTransition.across`, qui reconstruit le contexte de
l'autre côté d'une frontière depuis le contexte **statique** du nouveau corps
central, perde un composant `drag` — perte bornée à l'étage en cours, journalisée
plutôt qu'absorbée, et léguée à `MIS-5`.

**Sous la composition du §3.2, ce défaut n'existe pas.** `across()` garde une
signature gravité-seule et `StageLegRunner` écrit
`context.withGravity(across(context.gravity(), body))` : la moitié aéro traverse
sans être touchée. Ce qui rend ce transport *correct* et non seulement possible,
c'est que `DragContext` tient l'**énumération** et non un `Atmosphere` déjà construit
— les deux modèles Orekit se construisent contre une forme de corps, donc la
résolution est reportée à la construction du propagateur, où un corps sans
atmosphère n'en reçoit aucune. Un franchissement Terre → Lune cesse de traîner, un
retour Lune → Terre remonte son `DragForce`
([`04-conception-L1.md`](04-conception-L1.md) §1.2, fermé par deux tests §5.4).

---

## 4. Principe du découpage

Trois règles, reprises de `PHY-4` parce qu'elles y ont tenu du premier au dernier
lot :

1. **Un changement de comportement à la fois.** Un lot qui renomme ne change pas la
   physique ; un lot qui change la physique ne renomme pas. Sinon une dérive
   constatée n'est attribuable à rien.
2. **Chaque lot se ferme sur un test exécutable**, pas sur une revue. Pour les lots
   de refactor, le test est une **égalité** ; pour le lot de physique, c'est une
   **mesure** confrontée à une valeur connue par ailleurs.
3. **Le nouveau comportement est opt-in, et il l'est encore à la fin de l'item.**
   C'est la différence avec PHY-4, dont le dernier lot allumait pour de bon. Ici, le
   défaut reste `NONE` jusqu'à `PHY-2`.

---

## 5. Les lots

| Lot | Objet | Change le comportement ? | Test qui le ferme |
|---|---|---|---|
| **L0** | Baseline mesurée + prototype isolé | non (aucun code de production) | un document chiffré |
| **L1** | La brique, éteinte | non (structurellement) | les quatre gates + liste de forces + tests d'unité |
| **L2** | Le vol allumé | oui, opt-in | trois mesures |

> **Renumérotés le 2026-08-20.** Le lot de renommage `GravitationalContext` →
> `FlightContext` est **dissous** : sous la composition du §3.2 il n'y a rien à
> renommer, et le nom qu'il corrigeait reste juste. Il était dimensionné à 236
> occurrences d'identifiant dans 49 fichiers Java, plus 45 dans cinq documents
> `docs/multi-corps/` — tout cela reste vrai et n'a pas à être repris. L'ancien `L2`
> devient `L1`, l'ancien `L3` devient `L2`.

### L0 — Baseline mesurée et prototype isolé

**Propriété rendue vraie.** Il existe une référence chiffrée écrite *avant*, et on
sait ce que coûte réellement la traînée avant de s'y engager.

**Entrées.** La suite verte au commit de départ. Rien d'autre.

**Sorties.** `docs/atmosphere/03-baseline-L0.md`, contenant :

- les profils existants — orbites atteintes, masses restantes, durées de calcul —
  pour LEO-400, GEO, MEO, Ariane 62 et polaire ;
- la procédure d'exécution qui donne un vert fiable malgré [`BUG-7`](../bugs.md) :
  exécution isolée des gates et `cleanTest` systématique (§7) ;
- les mesures du prototype jetable : ρ retournée par Harris-Priester et par
  NRLMSISE-00 à cinq altitudes, confrontée à des tables publiées ; coût par pas
  d'intégration des deux modèles rapporté au propagateur nu ; **et le nombre de pas
  d'intégration d'un parking 250 km sur 24 h, avec et sans traînée**.

**Pourquoi ce troisième chiffre est le vrai but du prototype.** L'étude d'impacts
signale en §6.2 qu'à 100 km d'altitude, ρ varie de quatre ordres de grandeur sur
50 km, et que le pas adaptatif de `DormandPrince853` « doit gérer — mais peut
nécessiter de durcir les tolérances ». **Personne ne l'a mesuré.** Si le nombre de
pas explose, tout le budget compute annoncé pour `PHY-2` (+5 % à +50 %) est faux,
et il vaut mieux l'apprendre sur quarante lignes jetables que sur une optimisation
CMA-ES.

**Fermeture.** Le document. Aucun code de production ; le prototype ne rejoint pas
`src/main`.

**Contrainte de méthode.** Les tests d'optimisation sont lents et c'est
l'utilisateur qui les lance.

### ~~L1 — `GravitationalContext` devient `FlightContext`~~ — dissous le 2026-08-20

Ce lot renommait `GravitationalContext` en `FlightContext` avant que le lot suivant ne
l'étende, pour que le diff de celui-ci ne mélange pas un renommage et un changement de
composants.

**Il n'a plus d'objet.** `FlightContext` compose `GravitationalContext` au lieu de le
remplacer (§3.2), donc le nom qu'il corrigeait reste juste — dans le code comme dans
les cinq documents `docs/multi-corps/`.

Deux comptes du lot dissous étaient d'ailleurs faux, et sont consignés corrigés pour
qu'on ne les reprenne pas ailleurs : **236 occurrences d'identifiant** dans 49
fichiers Java (206 du type — 101 `main`, 105 `test` — et 30 de l'identifiant
`gravitationalContext`), et non 204 ; **45 occurrences** dans les cinq documents, et
non 61.

### L1 — La brique, éteinte

**Propriété rendue vraie.** Tout est là et rien ne l'allume : une mission *peut*
demander une atmosphère, aucune ne le fait, et rien n'a bougé d'un bit.

**Entrées.** `L0`, et les décisions du §3. Conception détaillée :
[`04-conception-L1.md`](04-conception-L1.md).

**Sorties.**

- `AerodynamicProperties` — record à deux composants, nullable sur `StageModel` et
  `PayloadModel`, agrégé sur l'étage actif par `ActiveStageInfo` ;
- `AtmosphereModel` — énumération `NONE` / `HARRIS_PRIESTER` / `NRLMSISE`, avec
  construction Orekit et mise en cache sur le patron des modèles de gravité déjà en
  place (`computeIfAbsent`, instance partagée, et cela reste un invariant et non une
  optimisation) ;
- `FlightContext` et `DragContext`, dans un paquet neuf `simulation/flight/` ;
- `DragForce` conditionnel dans **les deux** factories de propagateur ;
- composant `AtmosphereModel` sur `MissionSpec`, normalisé à `NONE`, écrit par le
  seul `MissionComposer`, puis porté par `Mission` ;
- `MissionStage.flightContext(entryState, mission)` (§3.6) ;
- `FlightContext` porté par `Leg`, `StageRun.exitContext` et `StepSampler.sample` ;
- **sept valeurs de catalogue** — Falcon Heavy S1 et S2, Ariane 62 S1 et S2,
  `CARGO_MODULE`, `EARTH_OBS_SAT`, `GEO_SAT` — chacune avec sa provenance en
  commentaire, comme le catalogue le fait déjà pour ses ISP. Règle de dérivation :
  la surface est la section frontale **du bloc tel qu'il est agrégé** (les deux
  lanceurs agrègent des corps parallèles, donc c'est la somme des sections, pas
  celle du corps principal) ; le Cd suit les deux populations du §3.3.

**Fermeture — trois preuves.**

1. **Les quatre gates inchangés**, à leurs tolérances actuelles, `0.0` comprise.
2. **Un test structurel** : un propagateur bâti sur `FlightContext.earth()` expose
   la même liste de force models — même compte, mêmes types, même ordre — qu'un
   propagateur drag-on privé de son `DragForce`. C'est ce test, et non les gates,
   qui *démontre* le « au bit près » ; les gates le confirment.
3. **Tests d'unité** : agrégation aéro sur l'étage actif, dans `VehicleTest` ; un
   étage sans propriétés aéro ne traîne pas, même quand un autre étage du stack en
   déclare ; défaut `NONE` du spec quand le composant est nul ; `MissionComposer`
   unique écrivain ; `DragContext` refuse `NONE` ; et les **deux tests qui suppriment
   le legs à `MIS-5`** — un contexte lunaire ne monte aucun `DragForce`,
   `withGravity(across(…))` conserve la moitié aéro
   ([`04-conception-L1.md`](04-conception-L1.md) §5.4).

### L2 — Le vol allumé

> **Conçu le 2026-08-20** : [`05-conception-L2.md`](05-conception-L2.md). Deux points
> de ce bloc y sont amendés — la sanity 800 km gagne une seconde borne, « non nul »,
> faute de quoi elle passerait aussi sans traînée montée (§1.2), et la mesure
> analytique est volée sur **deux** états au lieu d'un, l'équatorial pinçant le module
> et le polaire la direction (§1.3).

**Propriété rendue vraie.** Le chemin drag-on a tourné, et ce qu'il produit a été
confronté à des valeurs connues par ailleurs.

**Entrées.** `L1`, et une mission de test déclarant explicitement son modèle
d'atmosphère.

**Sorties.** Le chemin drag-on exécuté, plus le bilan de livraison (§8).

**Fermeture — trois mesures.**

1. **Accélération de traînée, contre l'analytique.** À un état donné, l'accélération
   que le `DragForce` contribue, confrontée à `0,5·ρ·v²·Cd·S/m` calculée à la main.
   La vitesse à employer est la **vitesse relative à l'atmosphère en rotation**, pas
   la vitesse inertielle : c'est précisément le piège que ce test existe pour
   attraper, et il ne se voit pas dans un résultat d'orbite. Test serré,
   déterministe, rapide — l'équivalent ici de la vérification analytique par
   laquelle `PHY-4 / L2` a fermé son troisième corps.
2. **Décroissance d'un parking 250 km sur 24 h**, confrontée à l'ordre de grandeur
   publié. Écrite comme une **bande**, pas comme un point : elle dépend du
   coefficient balistique retenu au catalogue, donc un point serait un chiffre
   auto-réalisateur.
3. **Sanity haute altitude.** La même mission à 800 km, drag-on contre drag-off :
   écart inférieur à 0,1 %. Attrape un modèle appliqué à la mauvaise échelle, qui
   est le mode de défaillance qu'aucune des deux mesures précédentes ne verrait.

**Trois mesures consignées sans assertion**, parce qu'elles sont les entrées de
`PHY-2` et non des propriétés de `PHY-1` :

- écart Harris-Priester / NRLMSISE-00 sur la décroissance à 250 km ;
- surcoût compute mesuré sur une optimisation, à confronter au +5 % / +50 % annoncé
  et au nombre de pas mesuré en `L0` ;
- **dette des ISP proxy chiffrée** (§2.3) : combien de m/s de pertes le catalogue
  compense-t-il aujourd'hui par son ISP moyen, et donc combien `PHY-2` devra rendre
  en reprenant les 296 s et les 300 s.

---

## 6. Ce qui reste à trancher au raffinement

1. ~~**La mission de test de `L2`** : un profil existant recomposé avec une
   atmosphère, ou une mission dédiée ?~~ **Tranché en `L2`**
   ([`05-conception-L2.md`](05-conception-L2.md) §1.1), et pas par préférence : toute
   mission terrestre part du pas de tir, donc « recomposer un profil » signifie voler
   une ascension depuis 0 km — ce qui rend Harris-Priester inutilisable, traverse le
   régime pathologique de [L0 §2.1 et §2.2](03-baseline-L0.md) et fait dépendre le
   verdict d'une ré-optimisation CMA-ES. `L2` vole un fixture orbital dédié, plus un
   fixture propagateur pour la mesure analytique.
2. ~~**Le plancher d'application du modèle.**~~ **Tranché par mesure en `L0`**
   ([`03-baseline-L0.md`](03-baseline-L0.md) §2.1) : Harris-Priester est valide sur
   **[100 km, 1000 km]**, lève une exception en dessous et rend 0,0 en silence
   au-dessus ; NRLMSISE-00 couvre 0 → 1500 km. Ce qui reste ouvert, et qui est plus
   grave que la question posée, c'est **le choix de modèle pour l'ascension** : une
   ascension part de 0 km, donc Harris-Priester ne peut pas la voler du tout, et la
   répartition « HP pour l'optimisation, NRLMSISE pour le playback » recommandée par
   l'étude d'impacts est inapplicable là où le chantier se justifie.
3. ~~**Les valeurs de Cd à retenir**, une fois les sources rassemblées.~~ **Tranché
   en `L1`** ([`04-conception-L1.md`](04-conception-L1.md) §4) : Cd 0,4 en régime
   continu pour les premiers étages, 2,2 en libre moléculaire pour tout le reste ;
   quatre surfaces dérivées de diamètres publiés, trois surfaces de charge utile
   déclarées comme conventions faute de matériel derrière.
4. ~~**Faut-il exposer le modèle dans le rapport de mission** dès `L2`, ou attendre
   `PHY-3` ?~~ **Rendu bon marché par `L1`** : la leg enregistre désormais le
   `FlightContext` qu'elle a réellement volé (§3.5 de la conception), donc l'exposer
   devient une lecture de champ, à faire quand `PHY-3` en aura besoin.

---

## 7. Ordonnancement et risques

`L0 → L1 → L2`, strictement séquentiel : `L1` construit ce que `L2` allume, et `L0`
mesure ce à quoi les deux se comparent.

**Le lot le plus large est `L1`**, qui absorbe le lot de renommage dissous sans en
hériter la surface — la composition laisse `GravitationalContext` et ses 236
occurrences intacts.

**Le lot le plus incertain en effort est `L2`**, parce que son verdict dépend de
sources externes (les ordres de grandeur publiés de décroissance) et non du seul
code.

**Le risque transverse est [`BUG-7`](../bugs.md#bug-7--les-gates-de-non-régression-tombent-quand-un-test-lunaire-les-précède-dans-le-même-jvm).**
Les gates sur lesquels repose `L1` tombent au dernier bit quand un test
lunaire les précède dans le même JVM. PHY-1 ne le résout pas ; `L0` en consigne la
procédure de contournement, faute de quoi chaque lot débattra d'un rouge qui n'est
pas le sien. Piège associé, à connaître avant toute mesure : relancer le même filtre
`--tests` après un succès rend la tâche `UP-TO-DATE` et n'exécute rien, en affichant
un vert. Toute mesure passe par `cleanTest`.

---

## 8. Ce que PHY-1 lègue

**À `PHY-2`** — quatre entrées chiffrées, produites par `L0` et `L2` : le surcoût
compute réel, l'écart entre les deux modèles d'atmosphère, la dette des ISP proxy,
et le nombre de pas d'intégration à basse altitude. Plus la liste de ce que `PHY-2`
doit faire et que `PHY-1` s'est interdit : relever le `periapsisFloor`, absorber les
pertes dans `dt1MaxPhysical`, reprendre les 296 s et 300 s du catalogue,
re-baseliner la suite d'optimisation, basculer le défaut.

**Et trois approximations que `L1` fait entrer au catalogue** — à corriger ou à
assumer explicitement avant de basculer le défaut
([`04-conception-L1.md`](04-conception-L1.md) §6) :

- **aucun pic transsonique** : un Cd unique par étage ne représente pas Mach 1, là
  où la traînée d'ascension est maximale ;
- **trois géométries de bus conventionnelles** pour les charges utiles, sans matériel
  publié derrière, et sans panneaux solaires déployés ;
- **le Cd libre-moléculaire du S2 est appliqué en régime continu** — le catalogue le
  justifie par « un étage supérieur s'allume au-dessus de 70 km », et le seul profil
  qui ait été volé avec traînée l'allume à **58 km** ([`05-conception-L2.md`](05-conception-L2.md)
  §4.2, mesuré le 2026-08-21).

**Et deux contraintes dures, découvertes par la mesure en `L0`** — celles-là ne sont
pas des entrées à consulter mais des obstacles à lever avant de basculer le défaut
([`03-baseline-L0.md`](03-baseline-L0.md) §2.2 et §2.3) :

- **une borne d'altitude est nécessaire à la terminaison de l'optimisation.**
  Au-dessus de 200 km la traînée ne coûte aucun pas d'intégration ; à 130 km la même
  propagation demande 982 497 pas et 487 s au lieu de 452 pas et 0,2 s, puis échoue.
  Un seul candidat CMA-ES qui pique bas suffit à rendre une évaluation 2 000 fois
  plus longue ;
- **`ReentryGuard` est inopérant avec traînée.** Rejoué armé, il ne change ni un pas
  ni une milliseconde : son plancher est à −50 km et l'intégrateur cède à −9 km. Il
  faut relever ce plancher, ou lui adjoindre la borne haute que le point précédent
  réclame déjà.

**À `PHY-3`** — un champ `AtmosphereModel` sur `MissionSpec` qu'un sélecteur n'a
plus qu'à piloter, un modèle d'atmosphère interrogeable pour calculer Q, et le
`FlightContext` que chaque leg enregistre.

**À `UI-3`** — le champ que le format de scénario doit sérialiser dès sa première
version, même quand il ne vaut que `NONE`.

**~~À `MIS-5`~~ — plus rien.** Le legs prévu ici — la perte du composant `drag` au
retour Lune → Terre à l'intérieur d'un étage — a été **supprimé le 2026-08-20** par
la forme composée du §3.2, et deux tests le vérifient
([`04-conception-L1.md`](04-conception-L1.md) §5.4).

---

## 9. Amendements du 2026-08-20

Consignés après la conception de `L1`, dans l'ordre du document. Les deux premiers
sont des changements de fond ; les suivants sont des corrections de mesure ou de
formulation.

| § | Ce que disait le découpage | Ce qui a été retenu |
|---|---|---|
| §3.2 | `FlightContext` = `GravitationalContext` renommé + composant `drag` + `withDrag(…)` | `FlightContext` **compose** `(GravitationalContext, DragContext)` ; le « au bit près » devient une propriété du type |
| §3.6, §8 | le piège `ArcTransition` est borné, journalisé, légué à `MIS-5` | le piège **n'existe pas** sous la forme composée ; le legs à `MIS-5` est supprimé |
| §5, §7 | quatre lots `L0`–`L3`, dont un `L1` de renommage pur | trois lots `L0`–`L2` ; le lot de renommage est **dissous** |
| §3.3 | `AerodynamicProperties(dragCoefficient, crossSection)` | ordre inversé, pour s'aligner sur `IsotropicDrag(crossSection, dragCoeff)` |
| §3.3 | « les étages lanceur volent l'atmosphère, la charge utile vole l'orbite » | coupure décalée d'un cran : **premier étage / tout le reste** |
| §3.3 | `VehicleStack` agrège l'aéro comme il le fait pour `propulsion()` | `propulsion()` rend le **premier** étage, pas l'actif ; l'agrégation passe par `ActiveStageInfo` |
| §5 (brique éteinte) | `DragForce` conditionnel dans `createOptimizationPropagator` | dans **les deux** factories, par un helper commun |
| §5 (renommage) | 204 occurrences dans 49 fichiers ; 61 dans cinq documents | **236** occurrences d'identifiant ; **45** dans les cinq documents |
| §6-3 | les valeurs de Cd restent à trancher | tranchées : 0,4 continu / 2,2 libre moléculaire, et les sept surfaces |
| §6-4 | exposer le modèle au rapport de mission reste ouvert | rendu bon marché : la leg enregistre le `FlightContext` volé |

Ce qui **n'a pas** bougé : les six corrections du §2.2, la dette des ISP proxy du
§2.3, les décisions §3.1 / §3.4 / §3.5, les trois règles du §4, la fermeture en trois
mesures du lot du vol allumé, et les deux contraintes dures mesurées en `L0`.

---

*Document rédigé le 2026-08-20, amendé le même jour après la conception de `L1`
([`04-conception-L1.md`](04-conception-L1.md)).*
