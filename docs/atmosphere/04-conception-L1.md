# PHY-1 / L1 — La brique, éteinte

Lot `L1` du [découpage](02-decoupage.md), tel qu'amendé le 2026-08-20. Entrées :
[`03-baseline-L0.md`](03-baseline-L0.md).

**Propriété rendue vraie.** Tout est là et rien ne l'allume : une mission *peut*
demander une atmosphère, aucune ne le fait, et rien n'a bougé d'un bit.

**Fermé par** les quatre gates inchangés, un test structurel sur la liste de forces,
et une série de tests d'unité (§5).

---

## 1. Ce que ce lot change au découpage

Le découpage prévoyait quatre lots, dont un `L1` de renommage pur
(`GravitationalContext` → `FlightContext`) précédant un `L2` qui étendait le record
renommé par un composant `drag` nullable et une méthode `withDrag(…)`.

**Ce n'est pas la forme retenue.** `FlightContext` **compose** au lieu de renommer :

```java
record FlightContext(GravitationalContext gravity, DragContext drag)
```

`GravitationalContext` survit intact — six composants, ses deux invariants, et toute
la justification de `PHY-4` typée sur ce dont elle parle. Trois conséquences, et les
trois valent d'être écrites parce qu'elles ont décidé la forme :

**a. Le lot de renommage disparaît.** Il était dimensionné à **236 occurrences
d'identifiant dans 49 fichiers** — 206 du type (101 `main`, 105 `test`) et 30 de
l'identifiant `gravitationalContext` (29 `main`, 1 `test`) — plus 21 lignes de
Javadoc en prose. Sous la composition il n'achète rien : le nom qu'il corrigeait
reste juste. Les **45 occurrences** des cinq documents `docs/multi-corps/` restent
vraies elles aussi, et n'ont pas à être reprises.

**b. La composition est bon marché parce que le record est transporté, pas
déréférencé.** Hors du paquet `gravity`, `src/main` ne lit ses accesseurs que
**quatorze fois** :

| Site | Lecture |
|---|---|
| `ReentryGuard:69,96` | `equatorialRadius()` ×2 |
| `OrekitService:269,305,306,327` | `mu()` ×2, `body()`, `perturbers()` |
| `AnalyticGtoInjectionStage:446`, `AnalyticPlaneTrimAtNodeStage:211`, `CoastingStage:52` | `inertialFrame()` ×3 |
| `TrajectoryArc:51` | `body()`, `inertialFrame()` |
| `Mission:81`, `TranslunarInjectionPlan:709` | `shape()` ×2 |
| `StageLegRunner:275` | `body()` |

Les onze autres sont dans `ArcTransition` lui-même, qui garde un paramètre
`GravitationalContext` et **ne change pas d'une ligne**. Tout le reste des ~130
occurrences de `main` est de la déclaration de type et du passage de paramètre.

**c. Le renumérotage.** Le découpage passe à `L0` → `L1` (ce document) → `L2` (le vol
allumé). Un découpage sans `L1` se relit mal, et ces étiquettes seront citées en
Javadoc pour longtemps.

### 1.1 L'argument qui a décidé la forme

`FlightContext = (GravitationalContext, DragContext)` **est la liste de forces du
propagateur.** La moitié gravitationnelle se monte en `setMu` + `nonCentralField` +
`addPerturbers` ; la moitié aéro est exactement le couple qu'Orekit demande :

```java
DragForce(Atmosphere atmosphere, DragSensitive spacecraft)
```

Donc `drag == null` ⇒ aucun `DragForce` monté ⇒ **le « au bit près » est une
propriété du type**, et non d'une convention `withDrag(…)` qu'il faut aller vérifier.
C'est la même démonstration qu'`addPerturbers` documente déjà pour l'ensemble vide
(spec `docs/multi-corps/04-conception-L2.md` §4.1), obtenue ici par la forme du
record.

C'est aussi ce qui rend la non-régression **structurelle** : les quatre gates
deviennent une confirmation, pas la preuve.

### 1.2 Le piège `ArcTransition` du découpage §3.6 n'existe plus

Le découpage admettait un défaut : `ArcTransition.across` reconstruit le contexte de
l'autre côté d'une frontière depuis le contexte statique du nouveau corps central,
donc un composant `drag` y serait **perdu**, et la mitigation était de le journaliser
et de léguer le problème à `MIS-5`.

Ce défaut disparaît, à une condition : que la moitié aéro tienne l'**énumération**
et non un objet Orekit déjà construit. Vérifié dans Orekit 13.1.1 —

```java
HarrisPriester(ExtendedPositionProvider sun, OneAxisEllipsoid earth)
NRLMSISE00(NRLMSISE00InputParameters params, ExtendedPositionProvider sun, BodyShape earth)
```

— les deux atmosphères sont construites **contre une forme de corps**, et
`HarrisPriester` exige le type concret `OneAxisEllipsoid`, précisément ce que
`GravitationalContext.shape()` rend (`PHY-4 / L1` §2.2 avait choisi le type concret).

`OrekitService` résout donc `(modèle, gravity.shape())` **au moment de construire le
propagateur**, et rend `null` quand le corps central n'a pas d'atmosphère —
exactement la forme de `nonCentralField(body)`, dont le Javadoc dit déjà *« a body
with no harmonic field gets none »*. Déroulé sur le seul cas atteignable :

| Franchissement | Moitié aéro portée | Ce qui est monté |
|---|---|---|
| arc terrestre, drag on | `(NRLMSISE, Cd·S)` | `DragForce` |
| Terre → Lune | inchangée | rien — pas d'atmosphère lunaire |
| Lune → Terre | inchangée | `DragForce` remonté |

`ArcTransition.across` garde sa signature gravité-seule, `StageLegRunner` écrit
`context.withGravity(across(context.gravity(), body))`, et la moitié aéro traverse
sans être touchée — ce qui est le comportement juste, et non un bug encadré.

**Une nuance, pour être honnête** : porter la moitié aéro telle quelle serait *faux*
si l'on tenait un `Atmosphere` déjà construit contre l'ellipsoïde terrestre — on
appliquerait une atmosphère terrestre autour de la Lune. C'est le report de la
résolution à la construction du propagateur qui rend le transport correct, pas la
composition seule.

---

## 2. Les quatre types

Paquet neuf `simulation/flight/` — `simulation/gravity/` garde `GravitationalContext`,
`ArcTransition`, `SphereOfInfluence` et `SoiCrossingDetector`, tous authentiquement
gravitationnels et tous inchangés par le lot. La dépendance va dans un seul sens :
`flight` connaît `gravity`, jamais l'inverse.

```java
public enum AtmosphereModel { NONE, HARRIS_PRIESTER, NRLMSISE }

public record DragContext(AerodynamicProperties aero, AtmosphereModel model) {
  public DragContext {
    Objects.requireNonNull(aero, "aero");
    Objects.requireNonNull(model, "model");
    if (model == AtmosphereModel.NONE) {
      throw new IllegalArgumentException("NONE is the absence of a DragContext, not a DragContext");
    }
  }
}

public record FlightContext(GravitationalContext gravity, DragContext drag) {
  public FlightContext { Objects.requireNonNull(gravity, "gravity"); }   // drag nullable

  public static FlightContext earth() { … }
  public static FlightContext moon()  { … }

  public boolean hasDrag() { return drag != null; }
  public FlightContext withGravity(GravitationalContext gravity) { … }
  public FlightContext withDrag(DragContext drag) { … }
}
```

Paquet `mission/vehicle/model/` — au plus petit ancêtre commun de `StageModel`
(dans `model/stage/`) et de `PayloadModel` (dans `model/`) :

```java
public record AerodynamicProperties(double crossSection, double dragCoefficient) { … }
```

**Quatre points qui ne se lisent pas dans le code, et qui vont en Javadoc.**

1. **`DragContext` rejette `NONE`.** Même forme que `GravitationalContext` rejetant le
   corps central parmi ses propres perturbateurs : « pas de traînée » a alors **une
   seule représentation**, `drag == null`, au lieu de deux qu'il faudrait tenir
   d'accord. C'est ce qui écarte la variante à trois composants plats sur
   `FlightContext`.

2. **Aucune délégation sur `FlightContext`** — pas de `mu()`, pas de
   `equatorialRadius()`, pas de `body()`. Les quatorze lectures du §1-b passent par
   `.gravity()`, donc il existe exactement un chemin vers chaque donnée. Une
   délégation en créerait deux et inviterait la dérive.

3. **Pas de `Holder` sur `FlightContext.earth()`.** La paresse de
   `GravitationalContext.Holder` existe pour une raison précise — les repères Orekit
   ne doivent pas se résoudre avant `OrekitService.initialize()` — et elle est
   **héritée** par la délégation. L'`assertSame` de `SoiRoundTripFlightTest:425` porte
   sur `.gravity()`, donc sur l'instance partagée, qui reste partagée.

4. **La surface avant le coefficient**, contre le découpage §3.3 qui écrivait l'ordre
   inverse. Le constructeur Orekit est `IsotropicDrag(crossSection, dragCoeff)` :
   deux `double` adjacents dans l'ordre inverse de leur unique consommateur, c'est une
   transposition silencieuse qui ne se voit dans aucun test d'unité et qui fausse la
   traînée d'un facteur voisin de 30 sur un étage de lanceur. Aligner le record la
   rend impossible à commettre.

---

## 3. La couture

### 3.1 L'aéro remonte par l'étage actif

```java
// Vehicle.java — ne déclare rien ⇒ ne traîne pas
default AerodynamicProperties aerodynamics() { return null; }

// LaunchVehicle / Spacecraft : override, depuis StageModel / PayloadModel
// ActiveStageInfo : accesseur délégant, juste à côté de propulsion()
public AerodynamicProperties aerodynamics() { return vehicle.aerodynamics(); }
```

**`VehicleStack` n'override pas**, délibérément. Il hérite du `null`, parce qu'un
empilement n'a pas de section frontale unique et que la production ne le lit jamais :
la résolution passe par `resolveActiveStage(mass)`. Écrire un override que rien
n'atteint serait exactement ce que le Javadoc de `VehicleStack` se reproche déjà
d'avoir fait avec les étages analytiques à deux poussées — *« unreachable defensive
code writing a false claim about the model »*.

> **Correction au découpage §3.3**, qui écrit que `VehicleStack` agrège l'aéro
> « exactement comme il le fait déjà pour `propulsion()` ». C'est inexact :
> `VehicleStack.propulsion()` rend `vehicles.getFirst().propulsion()`, le **premier**
> étage, pas l'actif. L'agrégation sur l'étage actif se fait par `ActiveStageInfo`, et
> c'est celle-là qu'on imite.

**L'aéro vit sur le matériel, jamais sur la phase.** Une `MissionStage` ne déclare
aucune aérodynamique ; elle la *résout* depuis le matériel actif au moment de
construire son propagateur, comme elle ne déclare pas de moteur mais vole celui de
l'étage actif.

### 3.2 Le choix, sur `MissionSpec` puis sur `Mission`

Décalque exact de `MissionHorizon`, qui a déjà fait ce chemin pour la raison qu'on
veut ici — l'intention utilisateur doit survivre aux recompositions que
`MissionEntry` opère sur une bascule de mode ou une édition depuis le wizard.

```java
// MissionSpec.EarthOrbit et MissionSpec.Geo — constructeur compact
if (atmosphere == null) { atmosphere = AtmosphereModel.NONE; }

// Mission.java, à côté de horizon
private AtmosphereModel atmosphere = AtmosphereModel.NONE;
public AtmosphereModel getAtmosphere() { … }
public void setAtmosphere(AtmosphereModel a) { this.atmosphere = Objects.requireNonNull(a); }

// MissionComposer:106, ligne suivante — seul écrivain
mission.setAtmosphere(spec.atmosphere());
```

**Le défaut est écrit deux fois, et c'est nécessaire** : `Mission` initialisé à
`NONE` couvre les missions bâties sans passer par `MissionComposer` —
`AbstractTrajectoryOptimizerTest.TestMission` et les fixtures — qui porteraient
autrement `null`.

Coût vérifié, et le découpage §3.4 est juste au site près : **5 sites de construction
dans `main`** (`MissionFactory:105` et `:132`, plus les 3 copies de
`withLauncherLoads` dans `MissionSpec` lui-même) et **13 dans les tests**.

### 3.3 Le résolveur

```java
// MissionStage.java, juste après gravitationalContext(Mission) — qui ne bouge pas
public FlightContext flightContext(SpacecraftState entryState, Mission mission) {
  GravitationalContext gravity = gravitationalContext(mission);
  AtmosphereModel model = mission.getAtmosphere();
  if (model == AtmosphereModel.NONE) {
    return new FlightContext(gravity, null);
  }
  AerodynamicProperties aero =
      mission.getVehicle().resolveActiveStage(entryState.getMass()).aerodynamics();
  return new FlightContext(gravity, aero == null ? null : new DragContext(aero, model));
}
```

**Il faut deux « oui » pour qu'il y ait traînée**, et les deux niveaux sont
indépendants :

- `mission.getAtmosphere() != NONE` — l'**interrupteur**, intention utilisateur, un
  seul champ écrit en un seul endroit, à `NONE` pour toute mission jusqu'à `PHY-2` ;
- l'étage actif déclare une aéro — l'**existence physique**, portée par le catalogue.
  Un catalogue partiellement renseigné est ainsi prévisible : si un étage ne déclare
  rien, l'engin ne traîne pas pendant sa phase. Pas d'exception, pas de valeur
  inventée, pas de `NaN`.

`entryState` plutôt que l'état courant, pour la raison écrite dans `VehicleStack` :
l'étage actif ne change que par un largage explicite, donc n'importe quel état de la
phase résout le même étage. Même forme et même justification que
`maxStepSeconds(entryState, mission)` — quatrième déclaration du principe *une phase
est l'unité qui sait ce qu'elle vole, donc c'est l'unité qui le déclare*.

**Aucune collision de nom.** `gravitationalContext(Mission)` reste honnête — l'étage
déclare bien la moitié gravitationnelle — et `flightContext(entryState, mission)`
compose. Le couple de surcharges différant par la seule arité, dont la courte aurait
omis la traînée en silence sur 29 sites d'appel, n'existe pas sous cette forme.

### 3.4 Les factories et les sites de propagateur

```java
public NumericalPropagator createOptimizationPropagator(FlightContext context, double maxStep)
public NumericalPropagator createTestPropagator(FlightContext context, double maxStep)

private void addDrag(NumericalPropagator propagator, FlightContext context)          // à côté d'addPerturbers
private Atmosphere atmosphereFor(AtmosphereModel model, GravitationalContext gravity) // computeIfAbsent
```

**Les deux factories montent le `DragForce`**, par un helper commun.
`createTestPropagator` a zéro appelant dans `src/main` — il n'y est que déclaré, et
ses cinq sites d'appel sont dans les tests — mais lui laisser ignorer un
`DragContext` rouvrirait le mode de défaillance « la traînée a été demandée et n'a pas
été volée ». Son Javadoc annonce « newtonien seul », ce qui est périmé depuis que
`PHY-4 / L2` y a ajouté `addPerturbers` : à corriger dans le même lot.

`atmosphereFor` met en cache sur le patron des modèles de gravité déjà en place
(`computeIfAbsent`, instance partagée) — et cela reste un invariant, pas une
optimisation.

Le paramètre passe de `body` à `context` : **34 déclarations `GravitationalContext
body`** portent ce nom dans `main` aujourd'hui, et un `FlightContext body` qui tient
Cd·S serait un mensonge sur deux niveaux.

Les 8 `propagateStandalone` et les 12 prédictions analytiques internes passent de
`gravitationalContext(mission)` à `flightContext(state, mission)` — elles ont déjà
l'état et le véhicule sous la main aux huit sites qui écrivent
`computePlan(state, mission.getVehicle(), …)`. Les trois sites qui lisent
`.inertialFrame()` gagnent un `.gravity()`. Les prédictions héritent donc du choix
sans le savoir, et un détecteur de nœud verra la traînée : une date de nœud décalée
par la traînée est plus juste, pas moins.

Restent en gravité seule, sans une ligne de changement : `ArcTransition`,
`SphereOfInfluence`, `SoiCrossingDetector`, `ReentryGuard` (appelé avec
`context.gravity()`), `TrajectoryArc`.

### 3.5 La leg enregistre ce qu'elle a volé

`StageLegRunner.Leg.context`, `StageChainRunner.StageRun.exitContext` et
`StepSampler.sample(stage, context, state)` passent à `FlightContext`. Quatre
signatures touchées, chacune ajoutant un `.gravity()` à son unique point de
consommation — `exitContext` n'a qu'un lecteur dans `main`,
`MissionEphemerisGenerator:136`, qui n'en tire qu'un `TrajectoryArc`.

En échange, la question §6-4 du découpage — *« faut-il exposer le modèle d'atmosphère
dans le rapport de mission ? »* — devient une lecture de champ pour `PHY-3` au lieu
d'un rebranchement de quatre signatures.

---

## 4. Les sept valeurs du catalogue

**Le catalogue ne déclare aujourd'hui aucune géométrie** — ni diamètre, ni longueur.
`StageModel` porte des masses, une propulsion et des capacités ; `PayloadModel` porte
`(id, nom, masse sèche, ergol AKM, propulsion)`. Les sept surfaces sont donc sept
nombres neufs, sans figure existante dont les dériver. Chacune porte sa provenance en
commentaire, comme le catalogue le fait déjà pour ses ISP.

### 4.1 Deux populations de Cd, et la coupure n'est pas où le découpage la met

Le découpage §3.3 écrit *« les étages lanceur volent l'atmosphère, la charge utile
vole l'orbite »*. La coupure est mal placée d'un cran : **un étage supérieur s'allume
au-dessus de 70 km**, où l'écoulement est déjà transitionnel-à-libre-moléculaire, pas
continu. Falcon Heavy S2 et Ariane 62 S2 ne volent pas le régime que 0,4 décrit.

Le choix y est **numériquement sans conséquence** : la table L0 §1 donne
ρ ≈ 3,3 × 10⁻⁴ kg/m³ à 60 km et 3,8 × 10⁻⁶ à 90 km, soit quatre ordres de grandeur
sous le régime que S1 traverse. La coupure juste est donc **premier étage / tout le
reste** :

| | Cd | Régime | Provenance |
|---|---:|---|---|
| S1 des deux lanceurs | **0,4** | continu, rapporté à la section frontale | fourchette usuelle 0,3–0,5 pour un lanceur élancé ; milieu de fourchette, **aucun pic transsonique modélisé** |
| S2 des deux lanceurs, et les trois charges utiles | **2,2** | libre moléculaire | valeur standard du calcul de traînée satellite |

### 4.2 Les quatre surfaces de lanceur

Règle : la section frontale **du bloc tel qu'il est agrégé**. Les deux S1 agrègent des
corps parallèles, donc somme des sections — pas celle du corps principal.

| Étage | Composition | Surface |
|---|---|---:|
| FH S1 « 3 cores aggregated » | 3 × ⌀ 3,66 m | **31,6 m²** |
| FH S2 « Merlin Vacuum » | ⌀ 3,66 m | **10,5 m²** |
| A62 S1 « 2 P120C + LLPM aggregated » | ⌀ 5,4 m + 2 × ⌀ 3,4 m | **41,1 m²** |
| A62 S2 « ULPM, Vinci » | ⌀ 5,4 m | **22,9 m²** |

Diamètres vérifiés le 2026-08-20 : Falcon Heavy cœur 3,66 m
([Wikipedia](https://en.wikipedia.org/wiki/Falcon_Heavy)) ; LLPM et ULPM 5,4 m, P120C
3,4 m ([ESA — Ariane 6 overview](https://www.esa.int/Enabling_Support/Space_Transportation/Launch_vehicles/Ariane_6_overview)).
Contrôle croisé au passage : l'ULPM est donné pour ~32 t d'ergols et 6 t sèches, ce
que le catalogue déclare déjà (31 000 / 6 000 kg).

**La coiffe n'est pas modélisée** : elle ne dépasse la section agrégée sur aucun des
deux lanceurs.

**Contrôle de vraisemblance** sur FH S1 au max Q : `a = ½·ρ·v²·Cd·S/m` ≈ 0,33 m/s² à
11 km, soit quelques dizaines de m/s intégrés sur la phase — même ordre que les
100–300 m/s que l'étude d'impacts annonce pour un lanceur lourd. Le catalogue ne se
donne donc pas une traînée de fantaisie.

### 4.3 Les trois charges utiles n'ont aucune géométrie publiée

Ce sont des entrées **génériques** — « module cargo », « satellite d'observation »,
« satellite GEO ». Il n'existe pas de matériel derrière, donc aucune surface ne peut
avoir de provenance réelle. La valeur est une **convention**, et son commentaire doit
le dire au lieu de se donner une source.

| Charge utile | Masse | Géométrie de bus supposée | Surface | B = m/(Cd·S) |
|---|---:|---|---:|---:|
| `CARGO_MODULE` | 15 000 kg | ⌀ 4,5 m | **15,9 m²** | 429 kg/m² |
| `EARTH_OBS_SAT` | 10 000 kg | bus 3,0 × 3,0 m | **9,0 m²** | 505 kg/m² |
| `GEO_SAT` | 4 000 kg au départ (2 t sec + 2 t AKM) | bus 2,5 × 2,5 m | **6,25 m²** | 291 kg/m² |

Les trois coefficients balistiques **encadrent** le 455 kg/m² de la table L0 §2.2 sans
avoir été calés dessus. C'est délibéré : caler S pour retomber sur 455 rendrait la
mesure de décroissance du lot suivant auto-réalisatrice, ce que le découpage écarte
déjà en demandant cette mesure « comme une bande, pas comme un point ».

**Les panneaux solaires déployés ne sont pas modélisés.** À l'altitude où la traînée
compte, un satellite les a repliés ; l'approximation cesse d'être vraie pour une
rentrée de fin de vie, hors `PHY-1` comme hors `PHY-2`.

---

## 5. La fermeture

### 5.1 Les quatre gates, tolérances inchangées

`CentralBodyBaselineTest` (62 frontières à `0.0`), `MissionPolylineBaselineTest`,
`EarthOrbitNonRegressionTest`, `AscentBaselineN2Test`, selon la procédure du
[L0 §3](03-baseline-L0.md) — isolement contre [`BUG-7`](../bugs.md), `cleanTest`
systématique, et attente de la fin effective du run précédent sous Windows.

**Ils ne sont plus la preuve, seulement la confirmation** (§1.1).

### 5.2 Le test structurel, en deux assertions

1. un propagateur bâti sur `FlightContext.earth()` monte **exactement** la liste de
   force models d'avant le lot — même compte, mêmes types, même ordre ;
2. un contexte drag-on monte la même liste, **plus un `DragForce`**.

La liste littérale attendue sera **mesurée au moment d'écrire le test**, pas devinée :
`setMu` monte une `NewtonianAttraction` de son cru quand aucun modèle d'attraction
n'est présent, ce que le Javadoc d'`OrekitService` documente déjà.

### 5.3 Les tests d'unité

Dans `VehicleTest`, à côté des trois `vehicleStack_resolveActiveStage_*` existants :

- l'aéro remonte par l'étage actif, et **change au largage** — le basculement continu
  → libre moléculaire du §4.1 est une assertion, pas une intention ;
- un étage sans propriétés aéro ne traîne pas, **même quand un autre étage du stack
  en déclare** ;
- `VehicleStack.aerodynamics()` rend `null`, avec le test qui dit pourquoi.

Ailleurs :

- `MissionSpec.EarthOrbit` et `.Geo` normalisent `null` en `NONE` ; une `Mission`
  neuve porte `NONE` sans passer par `MissionComposer` ; `MissionComposer` est le seul
  écrivain ;
- `DragContext` refuse `NONE` ;
- `FlightContext.earth().hasDrag()` est faux, et `.gravity()` est **la même instance**
  que `GravitationalContext.earth()` (`assertSame`).

### 5.4 Les deux tests qui suppriment un legs

Ceux-là ne sont pas de l'hygiène : ils sont ce qui autorise à effacer le §3.6 du
découpage et son entrée `MIS-5` au §8.

- `atmosphereFor(NRLMSISE, contexte lunaire)` rend `null` ⇒ un `FlightContext`
  lunaire porteur d'un `DragContext` **ne monte aucun `DragForce`** ;
- `withGravity(ArcTransition.across(…))` **conserve** la moitié aéro à travers un
  franchissement — l'aller-retour Terre → Lune → Terre vérifié sans propager.

---

## 6. Ce que le lot s'interdit, et ce qu'il lègue

**Il ne prouve rien sur la traînée.** Aucun vol allumé, aucune confrontation à une
valeur publiée. Le lot livre la brique **éteinte** ; les trois mesures du lot suivant
la ferment — accélération contre l'analytique avec la vitesse **relative à
l'atmosphère en rotation**, décroissance à 250 km écrite en bande, sanity 800 km sous
0,1 %.

**Deux approximations neuves entrent au catalogue avec ce lot**, à ajouter aux
entrées déjà léguées à `PHY-2` :

- **aucun pic transsonique** — un Cd unique par étage ne représente pas Mach 1 (§4.1) ;
- **trois géométries de bus conventionnelles**, sans matériel derrière (§4.3).

Les entrées déjà prévues par le découpage §8 restent : le surcoût compute réel,
l'écart entre les deux modèles, la dette des ISP proxy, le nombre de pas
d'intégration à basse altitude, plus les deux contraintes dures mesurées en `L0` — la
borne d'altitude nécessaire à la terminaison de l'optimisation, et `ReentryGuard`
inopérant avec traînée.

**Le legs à `MIS-5` disparaît** (§1.2). Celui à `PHY-3` et à `UI-3` est inchangé, et
`PHY-3` gagne en plus le contexte de vol enregistré par leg (§3.5).

---

*Conception arrêtée en conversation puis rédigée le 2026-08-20.*
