# PHY-4 / L4 — La bascule de sphère d'influence, sans mission

Lot **L4** du découpage (`01-decoupage.md` §4). Il suit `03-conception-L1.md`, dont il
reprend le `GravitationalContext`, et `05-conception-L3.md`, dont il produit enfin le
second arc. Il se mesure contre `02-baseline-L0.md` par les deux gates que L1 et L3
ont laissés.

**Propriété rendue vraie.** Une propagation qui traverse la SOI lunaire se coupe en
deux arcs continus.

**Ce que ce lot ne fait pas.** Aucun étage de production ne déclare de transition,
donc aucune mission ne produit un second arc et aucun chiffre ne bouge — et pas par
chance : sans transition déclarée, la boucle de tronçons du §4 ne fait qu'un tour et
émet exactement la suite d'appels d'aujourd'hui. Le rendu n'est pas touché : dessiner
un arc lunaire au bon endroit reste L5.

> **Le cœur technique de l'item, et le mieux isolé des six lots**, dit le §6 du
> découpage. La mesure le confirme dans les deux sens : la conversion d'état est
> *exacte* (§1, 0 m), et tout le risque du lot tient dans une extraction de code qui
> n'a le droit de rien changer — l'étape 4 du §8.

---

## 1. Inventaire mesuré

Relevé au commit `6c42990`, après le merge de L3. Les chiffres viennent de trois
sondes jetables lancées sur ce commit, dans le style du §5.2 de L2 : mesurer d'abord,
épingler ensuite.

### 1.1 Ce qui existe déjà, et que le découpage ne sait pas

**Le détecteur de traversée est dans Orekit.** `RelativeDistanceDetector` (13.1.1,
depuis 12.1) porte exactement la fonction de commutation demandée,
`|r − r_corps| − seuil`, avec la position du corps évaluée dans `s.getFrame()` — donc
indifférente au repère de propagation. Il ne suffit pas ici, parce que le seuil retenu
respire (§3.2), mais il donne le patron et il prouve que la forme est la bonne.

**Une propagation lunaire tourne.** `Moon/inertial` est `pseudoInertial = true`, et un
`createOptimizationPropagator` monté sur un contexte lunaire fabriqué à la main a
propagé une heure sans lever.

### 1.2 Quatre mesures qui contredisent ce qui est écrit

**A. Le rayon de SOI n'est pas un nombre.** Sur 400 jours à partir du 2026-03-01 :

| | min | max | à la distance moyenne |
|---|---|---|---|
| distance Terre-Lune | 356 779 km | 406 570 km | 384 400 km |
| rayon de Laplace | **61 427 km** | **70 000 km** | **66 183 km** |

Les 66 200 km du découpage sont la valeur à la distance moyenne. L'écart entre les
extrêmes vaut 8 600 km, soit 14 %.

**B. `moon.getInertiallyOrientedFrame()` n'a pas les axes ICRF.** Mesuré à **22,08°**
de GCRF au 2026-03-01, et l'angle dérive — 22,08° → 22,43° → 22,94° sur deux ans,
c'est le pôle lunaire IAU. Contrôle : `earth.getInertiallyOrientedFrame()` est à
1,568 rad de GCRF, donc ces repères ne sont pas non plus ceux qu'on emploie déjà côté
Terre.

Or la question ouverte n° 1 du découpage recommande « axes ICRF » **et** L3 §9 la
déclare tranchée par `GravitationalContext.inertialFrame()`. Les deux moitiés de cette
réponse se contredisent dès qu'on instancie la Lune, et c'est le §2.2 qui les
réconcilie.

**C. La cible « millimètre / µm/s » est plusieurs ordres trop lâche.** Aller-retour
GCRF → sélénocentrique → GCRF sur un état à 66 000 km de la Lune :

| repère lunaire | Δposition | Δvitesse |
|---|---|---|
| sélénocentrique parallèle ICRF (§2.2) | **0 m exactement** | 8,5 × 10⁻¹⁴ m/s |
| `moon.getInertiallyOrientedFrame()` | 2,4 × 10⁻⁷ m | 7,3 × 10⁻¹³ m/s |

Une translation pure n'a rien à arrondir sur la position. Le §7.3 en tire une exigence
beaucoup plus stricte que celle que le découpage proposait, et sa raison est écrite.

**D. Le trou du cache de gravité n'est plus anticipé, il est mesuré.**
`orekit-data.zip` ne contient qu'un fichier de potentiel, `Potential/eigen-6s.gfc`,
terrestre. Aujourd'hui, `createOptimizationPropagator` sur un contexte lunaire monte
`HolmesFeatherstoneAttractionModel` — **le champ 8×8 terrestre exprimé en ITRF** — le
range sous la clé `MOON` dans le cache de L1 §3.3, et la propagation **ne lève rien**
en produisant des chiffres plausibles. C'est le seul défaut du lot capable de rendre
une trajectoire fausse et crédible ; le §2.3 le ferme et le §7.6 le garde.

### 1.3 Trois contraintes tirées du code, chacune d'une ligne

**Une poussée ne peut pas enjamber une frontière.**
`ConstantThrustStage.configure:58-85` reconstruit son `ConstantThrustManeuver` sur
`mission.getCurrentState().getDate()` avec sa durée pleine, et son attitude est
`new LofOffset(state.getFrame(), TNW)` — liée au repère. Un `configure()` rejoué sur un
second tronçon revolerait la poussée depuis son début, réorientée. D'où l'interdiction
du §3.3, qui n'est pas une privation : un transfert lunaire traverse en balistique.

**Un coast rejoué doit garder son horloge.** `CoastingStage:55` lit
`mission.getCurrentState().getDate().shiftedBy(maxTime)`. D'où la règle du §3.4 :
`setCurrentState` ne bouge qu'aux frontières d'étage, jamais aux frontières de tronçon.

**L1 §3.4 annonçait trois `NodeDetector(gcrf())` basculés ; il y en a deux.**
`CoastingStage:48` code encore GCRF en dur — et le coast est exactement l'étage pendant
lequel une traversée a lieu. Corrigé ici (§3.7), à risque nul et vérifiable.

### 1.4 Deux trouvailles de passage

`createTestPropagator` n'appelle pas `setOrbitType(CARTESIAN)`, contrairement à
`createOptimizationPropagator`. Une approche lunaire étant hyperbolique par rapport à
la Lune, la fabrique newtonienne lève
`hyperbolic orbits cannot be handled as EquinoctialOrbit`. Elle n'a aucun appelant dans
`main` (L2 §1) et les tests de ce lot volent la fabrique de production ; la ligne n'est
pas touchée, le fait est écrit (§6).

Le rayon lunaire existe déjà : `PlanetRadius:31`, 1 737 400 m.

---

## 2. Les objets

### 2.1 Le contexte lunaire, sur le patron exact de `earth()`

```java
public static GravitationalContext moon() { return Holder.MOON; }
```

| composant | valeur | source |
|---|---|---|
| `body` | `MOON` | — |
| `mu` | 4,902800 × 10¹² | `CelestialBodyFactory.getMoon().getGM()` |
| `inertialFrame` | sélénocentrique parallèle ICRF | §2.2 |
| `bodyFixedFrame` | `Moon/rotating` | `moon.getBodyOrientedFrame()` |
| `shape` | sphère 1 737 400 m, aplatissement 0 | `PlanetRadius:31` |
| `perturbers` | vide | c'est le déclarant qui ajoute |

Résolution paresseuse par Holder, pour la raison de L1 §2.1. `moon()` reste l'instance
**sans perturbateur** et `withPerturbers(EARTH, SUN)` en dérive : règle de L2 §2.3
appliquée telle quelle.

`PlanetRadius` vit dans `engine.scene` alors qu'il ne dépend que de `SolarSystemBody` et
des `Constants` d'Orekit. La couche simulation le lit **là où il est** : le déplacer est
un renommage, et L1 §7 interdit au diff d'en contenir.

### 2.2 Le repère de l'arc lunaire : une translation pure, pas celui d'Orekit

Le repère est sélénocentrique **à axes ICRF**, construit sur GCRF par une translation
pure vers le centre de la Lune, et non `moon.getInertiallyOrientedFrame()`. Trois
raisons, toutes mesurées.

**1. Le rendu.** `JmeVectorAdapter.toJmeBodyRelativePosition:60` applique
`axisConvention().icrfToJme(...)` **inconditionnellement** et documente son entrée comme
« position in ICRF axes ». L3 §1.1-C a montré que cette conversion est aveugle au
*corps* ; elle ne l'est pas aux *axes*. Un arc en axes pôle lunaire serait dessiné 22°
de travers, en silence, et L5 en hériterait.

**2. La conversion devient exacte.** §1.2-C : 0 m contre 2,4 × 10⁻⁷ m.

**3. Et le repère d'Orekit est dynamiquement faux pour nous.** Le même état, les mêmes
forces, propagés 6 h de deux façons — Terre centrale + Lune + Soleil d'un côté, Lune
centrale + Terre + Soleil de l'autre — puis ramenés en GCRF :

| repère de l'arc lunaire | écart après 6 h |
|---|---|
| sélénocentrique parallèle ICRF | **0,246 m** |
| `moon.getInertiallyOrientedFrame()` | **2 974,5 m** |

Les 3 km ne sont pas de la physique : ce sont les mêmes équations dans un repère qui
déclare `rotationRate = 0` alors que ses axes bougent avec la date. La propagation
intègre à axes fixes, la conversion de sortie emploie l'orientation dérivée. Le repère
retenu n'a pas ce mode de panne du tout.

Le repère vit dans un **troisième cache** d'`OrekitService`, à côté de `gravityModels` et
`thirdBodyModels` :

```java
private final Map<SolarSystemBody, Frame> bodyCentredFrames = new ConcurrentHashMap<>();

public Frame bodyCentredIcrfFrame(SolarSystemBody body)   // EARTH -> gcrf()
```

La Terre y retombe sur `gcrf()`, qui **est** la réalisation géocentrique d'ICRF : la même
méthode sert les deux corps et l'arc terrestre ne change pas d'instance de repère, ce
qui garde atteignable l'égalité stricte du gate L1. La branche Terre est dans la
fonction de mapping et **ne réentre pas dans la carte** — c'est la mise en garde de
L1 §7, et elle ne doit pas être « améliorée ».

### 2.3 Le champ de gravité d'un corps qui n'en a pas

```java
// createOptimizationPropagator, à la place de l'addForceModel inconditionnel
ForceModel field = nonCentralField(body.body());
if (field != null) propagator.addForceModel(field);
```

Le terme central reste entier : `NumericalPropagator.setMu` monte lui-même une
`NewtonianAttraction` s'il n'y en a pas, mesurée présente en fin de liste dans les trois
configurations de L2 §3.2. La carte ne contient donc que de vrais champs, et le `null`
est confiné à une méthode privée dont le javadoc nomme le fichier absent.

### 2.4 Trois classes nouvelles dans `simulation/gravity/`

- **`SphereOfInfluence`** — `record(SolarSystemBody body, SolarSystemBody primary)`, avec
  `radiusAt(AbsoluteDate)` = `d(t)·k`. Le `k` de Laplace est **calculé** depuis les GM
  d'Orekit, jamais écrit en dur (mesuré 0,17217202). Le primaire se lit sur
  `SolarSystemBody.parent()`, qui existe déjà.
- **`SoiCrossingDetector`** — patron de `ReentryDetector`, y compris son constructeur de
  copie qui fait voyager les champs à travers `create` : le javadoc de `ReentryDetector`
  explique qu'un champ oublié là se perd en silence et se manifeste comme une trajectoire
  fausse, jamais comme une erreur. `g = |r − r_autre| − seuil(date)`, positif **hors** de
  la SOI, l'autre corps évalué dans `state.getFrame()` — la même classe sert donc les deux
  sens de traversée, puisque sur l'arc lunaire la Lune est à l'origine et c'est la Terre
  qui donne `d`.
- **`ArcTransition`** — la conversion d'un état d'un contexte vers l'autre, le
  franchissement d'ensemble (§4.2), et rien d'autre.

Et une quatrième dans `simulation/mission/runtime/`, à côté de `StageChainRunner` : la
boucle de tronçons du §4.

---

## 3. La couture

### 3.1 Comment un étage déclare qu'il peut basculer

```java
// MissionStage — troisième déclaration de la forme de maxStepSeconds et gravitationalContext
public Set<SolarSystemBody> soiTransitions(Mission mission) { return Set.of(); }
```

L'opt-in est ce qui protège le gate L1 : sans lui, un tronçon unique ne produirait plus
les appels d'aujourd'hui, et les 62 frontières ne tiendraient plus par construction mais
par chance.

Deux formes écartées. **La dériver des perturbateurs** — surveiller la SOI de tout
perturbateur satellite du corps central — n'ajoute aucune déclaration, mais arme un
détecteur sur une mission GEO qui voudrait seulement la perturbation lunaire, et fait dire
deux choses à `withPerturbers(MOON)`. **La porter dans le contexte**, comme L2 y a porté
les perturbateurs, élargirait le record une seconde fois pour une raison plus faible :
« quels corps j'écoute » est une politique d'orchestration, pas un environnement
gravitationnel.

### 3.2 Le seuil respire

Le rayon est la formule de Laplace à la **distance instantanée**, pas une constante. Le
§1.2-A en donne la raison : une constante reviendrait à écrire qu'on ignore une variation
de 14 %. Le coût marginal est nul — l'interpolation d'éphéméride du corps est déjà faite
pour évaluer `|r − r_autre|`.

C'est la **question ouverte n° 2 du découpage, tranchée** : sphère de Laplace géométrique.
Le rapport des forces resterait plus juste, mais avec le corps opposé en perturbateur des
deux côtés (§4.2) il ne corrigerait qu'une comptabilité.

### 3.3 La bascule est interdite sur un étage propulsif

Décision écrite, pas oubli, et sa raison est mesurée au §1.3. `soiTransitions` non vide
sur un étage `isPropulsive()` lève à la construction du tronçon.

### 3.4 `setCurrentState` ne bouge qu'aux frontières d'étage

Jamais aux frontières de tronçon (§1.3). Avec cette règle, un `configure()` rejoué sur le
second tronçon d'un coast reconstruit **la même date absolue**.

### 3.5 Le repère de l'état d'entrée devient un contrat

L3 §1 note que `ctx.inertialFrame() == state.getFrame()` tient aujourd'hui par
construction ailleurs et que rien ne le vérifie. La boucle de tronçons est l'endroit où
ça devient vrai : si les deux diffèrent, elle convertit ; **si c'est la même instance,
elle ne touche à rien**. La comparaison est une **égalité de référence** — les deux
viennent du même cache — de sorte qu'aucun état existant ne traverse un `Transform`
identité et que le gate L1 garde son `0.0` par construction.

### 3.6 Le sampler porte le contexte

`StepSampler.sample(stage, state)` devient `sample(stage, context, state)`, et
`MissionEphemerisGenerator.Collector.pointOf` lit ce contexte-là au lieu d'interroger
l'étage. Sans ça, après une bascule, le collecteur étiquetterait les points du second
tronçon avec le corps que l'étage **déclare** au lieu de celui qu'il **vole** — c'est-à-dire
qu'il écrirait un arc faux dans le champ que L3 vient d'ajouter précisément pour ça.
`StageRun` gagne symétriquement le contexte de sortie.

### 3.7 La garde de rentrée, et une correction de L1

La garde est armée par tronçon, avec le contexte du tronçon. Le plancher reste `−50 km` :
L1 §4.2 l'avait renvoyé à L4 en notant qu'il serait trop généreux sur un corps quasi
sphérique. Il l'est — sur une Lune d'aplatissement nul il vaut 50 km sous la surface — et
ça ne coûte rien, parce que le rôle de la garde est d'arrêter avant que le pas s'effondre
à `r → 0`, pas de dater l'impact.

`CoastingStage:48` passe de `OrekitService.get().gcrf()` à
`gravitationalContext(mission).inertialFrame()`. Pour un étage terrestre c'est la même
instance de repère, et le gate L1 le prouve.

---

## 4. La boucle de tronçons

### 4.1 La boucle

Elle remplace les lignes 192-255 de `StageChainRunner`, qui garde sa boucle d'étages, son
`StageRun`, son listener et son contrat d'apatridie. Le runner **délègue toujours**, pas
seulement quand une transition est déclarée : un tronçon unique est le cas dégénéré du
même code, et c'est ce qui rend l'égalité structurelle au lieu de conditionnelle.

```
contexte  = stage.gravitationalContext(mission)
état      = aligné sur contexte.inertialFrame()   (no-op si même instance, §3.5)
maxStep   = stage.maxStepSeconds(entrée d'étage, mission)   -- calculé une fois
tant que vrai :
    propagateur = createOptimizationPropagator(contexte, maxStep)
    ReentryGuard armée avec contexte
    stage.configure(propagateur, mission)
    multiplexeur -> sampler.sample(stage, contexte, ...)
    un SoiCrossingDetector par corps déclaré, en STOP
    fin = propagateur.propagate(endDate)
    tronçons += (contexte, état, fin)
    si une traversée a coupé avant endDate :
        contexte = franchir(contexte, corps traversé)
        état     = ArcTransition.convert(fin, contexte)
    sinon : sortir
```

`maxStep` est calculé une fois, sur l'état d'entrée d'étage : la bascule n'existe que sur
un étage non propulsif (§3.3), où il vaut `COAST_MAX_STEP` quel que soit l'état.

### 4.2 Le franchissement est une seule règle

```
central(nouveau)       = corps traversé, ou son parent si c'est le central qu'on quitte
perturbateurs(nouveau) = perturbateurs(ancien) − {nouveau central} + {ancien central}
```

Un arc terrestre déclaré `withPerturbers(MOON, SUN)` donne donc
`moon().withPerturbers(EARTH, SUN)` : le Soleil traverse sans qu'on ait à le nommer, et la
règle « corps opposé des deux côtés » devient une **conséquence** de ce que l'étage a
déclaré, pas une seconde règle à tenir. Le retrait du nouveau central n'est pas
cosmétique : le constructeur compact de `GravitationalContext` **lève** si le central
figure dans ses perturbateurs (L2 §2.2), et c'est exactement le bug que ce lot était
prévu pour commettre.

Le §2.2 chiffre ce que ce choix achète : 0,246 m entre les deux côtés au lieu de 7 249 m.
La bascule cesse d'être un changement de **physique** pour devenir un changement de
**comptabilité** — repère, corps de référence pour l'altitude, corps central du rapport
d'orbite.

### 4.3 Ce que la boucle rend, et pourquoi c'est peu

Une liste de tronçons `(contexte, état d'entrée, état de sortie)`. L'éphéméride ne la lit
pas : elle reçoit ses arcs par le sampler, un point à la fois, avec le contexte du tronçon
qui l'a produit. **La liste existe pour le test** — c'est elle qui permet d'assert « deux
arcs, la frontière à telle date, la continuité au franchissement » sans mission, sans
rendu et sans éphéméride, ce que le découpage appelle le cœur testable en isolation.

### 4.4 L'hystérésis

Le mode de panne est précis : un STOP survient à `g = 0`, le tronçon suivant démarre **sur**
la sphère, et un détecteur réarmé sur la même sphère y voit un `g` dont le signe n'est plus
décidé que par l'arrondi. Il peut re-déclencher immédiatement et produire une suite de
tronçons de longueur nulle.

**Bande morte sur le rayon** : entrer se décide à `R(t)`, ressortir à `R(t)·(1 + ε)`. La
trajectoire doit s'être éloignée d'une marge avant que la traversée inverse compte. La
marge est une distance là où la physique en est une — un temps de séjour minimal serait
trop lourd pour une traversée rapide et trop léger pour une trajectoire rasante lente.
`ε` est **calibré par le test §7.5**, ordre de grandeur proposé : 10 km, soit
ε ≈ 1,5 × 10⁻⁴ à R = 64 500 km.

**Plus un plafond de tronçons par étage**, avec un WARN quand il sert, sur le modèle de
`FALLBACK_DURATION_SECONDS`. Un filet silencieux se découvre des mois plus tard.

---

## 5. L'éphéméride : la frontière est un instant écrit deux fois

C'est la question que L3 §9 lègue explicitement à ce lot. La réponse est **deux points à
date égale**, un par repère : l'état sortant dans l'ancien contexte, l'état converti dans
le nouveau.

**Pourquoi pas un seul.** Sans le point sortant, le dernier échantillon de l'arc terrestre
serait celui du pas d'échantillonnage précédent — 60 s de coast, des centaines de
kilomètres à vitesse de transfert. L'arc s'arrêterait visiblement avant la frontière
pendant que l'arc lunaire y démarrerait pile.

**Ce que ça fait à `interpolate`**, avec `times = [… t_{b−1}, t_b, t_b, t_{b+1} …]` aux
indices `k−1, k, k+1, k+2` :

| date demandée | `findInterval` | conséquence |
|---|---|---|
| dans `(t_{b−1}, t_b)` | `i0 = k−1, i1 = k` | même arc, Hermite dans l'ancien repère |
| dans `(t_b, t_{b+1})` | `i0 = k+1, i1 = k+2` | même arc, Hermite dans le nouveau |
| exactement `t_b` | `i0 = i1 = k` **ou** `k+1` | non spécifié par contrat |

Seule la troisième ligne demande quelque chose : **normaliser sur le plus petit indice
portant cette date**. C'est la sémantique de plancher que L3 §3.3 applique déjà au nom
d'étage, à la masse et à l'arc, étendue au cas dupliqué — à l'instant exact de la frontière
on rend le point **sortant**, et le basculement se fait au point suivant, ce qui est mot
pour mot l'atomicité que L3 a écrite.

Deux remarques qui évitent de croire le problème plus gros qu'il est. L'invariant
d'annulation n'a jamais été en danger : les trois lecteurs interrogent le même tableau à la
même date, donc `Arrays.binarySearch` leur rend le même indice, fût-il non spécifié. Ce qui
se ferme ici est le **contrat**, pas le comportement. Et la branche `dt = 0` du Hermite
reste inatteignable : à travers la paire dupliquée les arcs diffèrent, donc la garde d'arc
de L3 rend le point sortant avant tout calcul.

**Ce que L5 hérite, mesuré pour ne pas être redécouvert.**
`TrajectoryPolyline.rawArcStarts:179` force le **premier** sommet de chaque arc, pas
`arcStart − 1`, le dernier du précédent. Sur une trace décimée, l'arc sortant peut donc
s'arrêter jusqu'à un `stride` avant la frontière alors que l'arc entrant y démarre pile.
Ce n'est pas un sujet L4 — aucune mission n'y produit deux arcs — mais c'est une ligne à
ajouter à l'union du budget le jour où L5 dessine.

---

## 6. Ce que L4 ne touche pas

| Site | Raison |
|---|---|
| Les vingt sites de construction, les étages, les manœuvres | Aucun ne bascule ; le contexte voyage déjà par la couture de L1 |
| **Aucun étage de production ne déclare `soiTransitions`** | Opt-in jusqu'au dernier lot (règle 3 du §3 du découpage). Les deux gates restent verts **structurellement**, comme l'ensemble vide de perturbateurs en L2 §4.1 |
| Le rendu — les quatre objets à contexte figé de L3 §1.1-B, la règle de visibilité | L5, et L3 §5.1 l'a déjà écrit comme une décision |
| `MissionLoadEvaluator.objectiveMet:296` | Suppose un coast final d'un seul arc. L6 |
| `AchievedOrbit`, `PropellantBudget`, `MissionHorizon`, `Physics`, `LaunchPlane`, `EarthMission` | Terre-en-dur, réveillés quand un arc non terrestre en aura besoin (L1 §4.1) |
| Les plans analytiques képlériens | L2 §4.2 : l'écart plan/vol est à mesurer en L6, et aucun étage analytique ne bascule |
| L'optimisation multi-arcs | Hors PHY-4, §1 du découpage |
| `createTestPropagator` et son `OrbitType` par défaut | §1.4 : aucun appelant dans `main`, et les tests de L4 volent la fabrique de production (argument de L2 §5.2) |

---

## 7. Les tests

**7.1 `SphereOfInfluenceTest`** — `k` calculé sur les GM d'Orekit (0,17217202),
`radiusAt(t)` à une date figée, et l'encadrement 61 427 – 70 000 km consigné à côté des
66 183 km du découpage. Comme L2 §5.1 a gardé les 7,3 × 10⁻⁶ en repère logué, le chiffre du
découpage reste écrit avec son écart, pour qu'un lecteur qui compare sache pourquoi ça ne
colle pas.

**7.2 `SoiCrossingDetectorTest`** — la traversée détectée au bon rayon et à la bonne date.
L'assertion n'est **pas** « la date vaut X », ce serait recopier l'implémentation : c'est la
condition géométrique à la date détectée — `|r − r_L| / d = k` à la convergence près — qui
est un énoncé indépendant de la formule testée. Les deux sens par la même classe.

**7.3 `ArcTransitionTest`** — la continuité, exacte. Aller-retour de conversion à **0 m** et
8,5 × 10⁻¹⁴ m/s : la cible « millimètre / µm/s » du découpage est remplacée par une exigence
de plusieurs ordres plus stricte, avec sa raison écrite (une translation pure n'a rien à
arrondir sur la position). Plus le franchissement d'ensemble du §4.2, et le contrôle qu'un
contexte gardant `MOON` en perturbateur **lève** au lieu d'être toléré.

**7.4 `SoiRoundTripFlightTest`** — le test du lot. Terre → Lune → Terre à travers la boucle
de tronçons, contre la même trajectoire volée d'un bout à l'autre en géocentrique avec les
mêmes forces. Mesuré sur 6 h : **0,246 m**. Et le contre-cas qui lui donne son sens, logué
et non asserté : sans le Soleil des deux côtés, **7 249 m**, qui sont de la marée solaire
(`2·µ_S·d/D³ ≈ 3,0 × 10⁻⁵ m/s²`, soit 7,0 km en ½at²) et non un défaut. C'est la forme des
quatre propagations de L2 §5.2 — séparer les contributions rend visible un câblage à moitié
faux qu'une tolérance unique avalerait.

**7.5 L'hystérésis** — une fixture rasante, le nombre de tronçons borné. C'est ce test qui
**calibre** ε (§4.4).

**7.6 Le bug silencieux** — `createOptimizationPropagator(moon(), …).getAllForceModels()` ne
contient aucun `HolmesFeatherstoneAttractionModel` et contient bien la `NewtonianAttraction`.
Instantané, et c'est le seul défaut du lot qui rendrait une trajectoire plausible et fausse.

**7.7 La non-régression** — `CentralBodyBaselineTest` à `0.0` sur ses 62 frontières et
`MissionPolylineBaselineTest` à l'identique, à chaque étape.

Aucun compte d'évaluations, aucun temps, aucun CMA-ES — règle du §6 de la baseline.

---

## 8. Ordre d'exécution

1. **Le contexte lunaire** : `moon()`, `bodyCentredIcrfFrame`, la résolution du champ absent,
   test 7.6. Aucun appelant. C'est le commit qui ferme le bug silencieux du §1.2-D.
2. **`SphereOfInfluence` + `SoiCrossingDetector`**, tests 7.1 et 7.2. Aucun appelant en
   production.
3. **`ArcTransition`**, test 7.3. Aucun appelant.
4. **L'extraction de la boucle hors de `StageChainRunner`, sans qu'aucune bascule soit
   possible** — la boucle ne fait qu'un tour, le sampler gagne son paramètre de contexte.
   **Tout le risque de non-régression du lot est ici**, et l'étape est seule dans son commit
   pour ça : les deux gates doivent rester verts à l'identique.
5. **La bascule** : `soiTransitions`, le franchissement, la bande morte, le plafond, la double
   émission de points, la normalisation de `findInterval`. Tests 7.4 et 7.5.
6. **La correction de `CoastingStage:48`**, séparée : elle appartient à L1 et un lecteur doit
   pouvoir la lire comme telle.
7. Suite complète.

L'étape 4 est séparée de la 5 pour la raison exacte qui séparait l'étape 2 de l'étape 3 en L1,
et l'étape 3 de l'étape 2 en L3 : si un chiffre bouge, savoir déjà que ce n'est pas
l'extraction.

---

## 9. Risques identifiés

**L'extraction (étape 4) est le seul risque de non-régression**, sur trois points nommés :
l'ordre `create` → garde → `configure` → multiplexeur → `propagate`, qui décide de l'ordre
des détecteurs ; la non-conversion quand le repère est identique, par **égalité de référence**
et non `equals` ; et `setCurrentState`, qui ne doit pas changer de rythme. Les deux gates
l'attrapent.

**Un trou de couverture, du même genre que celui de L1 §5.7.** La boucle ne couvre que
`StageChainRunner.run`. Les douze sites de `propagateStandalone` construisent leurs propres
propagateurs et ne basculeront pas — donc **la passe d'optimisation ne traverse aucune SOI**.
Sans effet en L4, où rien ne bascule, ni en L6, dont le TLI est injecté analytiquement sans
optimiseur. Ça comptera pour `MIS-4`, et c'est écrit ici pour que ce ne soit pas une
découverte.

**`configure()` rejoué.** La règle du §3.4 le rend sûr pour un coast, et l'interdiction du
§3.3 ferme le reste. Le risque résiduel est un étage futur, non propulsif, dont le
`configure()` lirait un état plutôt qu'une date — à vérifier au moment de l'écrire.

**La bande morte non calibrée** : trop petite, bavardage ; trop grande, la sortie de SOI est
datée tard, ce qui ne coûte que de la comptabilité (§4.2). Le risque est asymétrique, il faut
se tromper du côté grand.

**`computeIfAbsent` et le troisième cache** : la branche Terre de `bodyCentredIcrfFrame` rend
`gcrf()` sans réentrer dans la carte — mise en garde de L1 §7, à ne pas « améliorer ».

**Ce que L4 n'a pas comme risque** : aucun chiffre de mission ne peut bouger, puisque rien ne
déclare de transition. Comme L2, la non-régression est structurelle et non mesurée.

---

## 10. Ce que L4 laisse ouvert

- **L5** hérite de la géométrie (L3 §1.1-C la laisse entièrement à faire), de la règle de
  visibilité (L3 §5.1), des quatre contextes figés (L3 §3.1), et de `arcStart − 1` à ajouter
  à l'union du budget (§5).
- **L6** sera le premier déclarant réel de `soiTransitions` et de perturbateurs. Il devra
  mesurer l'écart entre les plans analytiques képlériens et le vol perturbé (L2 §4.2), rouvrir
  `MissionLoadEvaluator.objectiveMet`, et réveiller les sites Terre-en-dur de L1 §4.1 —
  `AchievedOrbit` en premier, puisqu'une orbite lunaire atteinte s'y rapporterait au µ
  terrestre.
- **`MIS-4`** hérite du trou de `propagateStandalone` (§9) et du facteur ×1,4 de temps de paroi
  mesuré en L2 §5.3.
- **La question ouverte n° 1 du découpage est tranchée pour de bon** : le repère d'un arc est
  l'inertiel centré sur son corps **à axes ICRF**, ce que L3 §9 croyait acquis et que le §1.2-B
  a démenti pour le repère d'Orekit.
- **La question ouverte n° 2 est tranchée** : sphère de Laplace géométrique, à la distance
  instantanée (§3.2).
- **La question ouverte n° 5 est tranchée** : l'orchestration vit dans un objet **sous** le
  runner, pas dans le runner ni au-dessus des étages (§4).

---

## 11. Fermeture — L4 est implémenté

Mesuré le **2026-08-17**, branche `feature_phy4_l4_soi`, GraalVM 21.0.5. Les six étapes du
§8 ont été livrées en six commits, chacun compilant et laissant les deux gates verts.

### 11.1 Le verdict

| | |
|---|---|
| Suite par défaut | **787 tests, 0 échec, 0 erreur**, 17 sautés, 112 classes |
| Diff de production | **11 fichiers, +873 / −93** |
| Diff de test | 6 fichiers, +1 123 |
| Gate L1 `CentralBodyBaselineTest` | vert à chaque étape, 62 frontières à `0.0` |
| Gate L3 `MissionPolylineBaselineTest` | vert à l'identique à chaque étape |
| Étages de production déclarant une transition | **0**, comme prévu au §6 |

### 11.2 La physique, mesurée

| | mesuré |
|---|---|
| Facteur de Laplace, sur les GM d'Orekit | **0,17217202** |
| Rayon de SOI lunaire sur 400 jours | **61 427 → 70 000 km** (66 183 km à la distance moyenne) |
| Aller-retour de conversion d'arc | **0,0 m** et 8,53 × 10⁻¹⁴ m/s |
| Traversée entrante | 66 654 km, sphère à 66 654 km |
| Traversée sortante | 68 281 km, seuil avec bande morte à 68 270 km |
| Terre → Lune → Terre, écart à la traversée sortante | **9,552 m** |
| … et après 3 jours, survol compris | **10,372 m** |
| Séjour dans la sphère lunaire | **54 h**, un seul tronçon |

L'égalité de conversion **exacte** que le §7.3 réclamait est atteinte : 0,0 m, pas une
tolérance.

### 11.3 Sept écarts au plan

1. **L'état du handler n'est pas l'état rendu.** Mesuré : Orekit remet au handler un état
   daté **51 ps** avant celui que `propagate()` retourne, ce dernier étant ré-interpolé à la
   racine localisée. La boucle convertit donc l'état **rendu**, qui est aussi celui que
   `StageChainRunner` a toujours enfilé. Le plan ne distinguait pas les deux.
2. **La date de fin a dû devenir un callback.** Le pseudo-code du §4.1 la prenait en
   paramètre ; un étage ne publie sa coupure que depuis `configure()`, donc elle ne peut pas
   être connue avant que le premier tronçon soit configuré. `EndDateResolver` porte la
   contrainte explicitement, la politique restant dans le runner.
3. **La conversion doit ré-exprimer l'attitude.** `SpacecraftState` refuse une attitude dont
   le repère de référence diffère de celui de son orbite. Le plan n'en parlait pas ; la
   porter plutôt que la laisser retomber sur une attitude par défaut évite d'en perdre une
   silencieusement.
4. **La normalisation du §5 ne vaut qu'en correspondance exacte.** Écrite d'abord sur
   l'indice bas quel qu'il soit, elle faisait bracketer *à travers* la frontière pour une
   date située dans l'arc entrant, et rendait le point sortant. Attrapé par le test du §5,
   corrigé dans le corps du document.
5. **La fixture du §7.4 ne peut pas viser la Lune.** Un tir géocentrique met un jour et demi
   à monter, pendant lesquels la Lune parcourt 130 000 km : la première version manquait la
   sphère entièrement. La vitesse initiale est celle de la Lune plus une approche relative,
   ce qu'un vrai TLI fait pour la même raison.
6. **Une lecture fausse pendant l'implémentation, et elle vaut d'être écrite.** Le premier
   écart Terre → Lune → Terre mesurait **4 032 m** et se lisait comme de la physique. C'était
   un décalage de **3,6 s** entre l'échantillon comparé et la référence — à vitesse de
   transfert, 3,6 s *sont* 3,6 km. Comparées à la même date, les deux trajectoires
   coïncident à une dizaine de mètres.
7. **Le §7.5 n'a pas calibré ε, et c'est un manque à écrire.** La fixture quasi tangentielle
   produit **un seul** changement d'arc : la géométrie rasante se résout proprement et ne met
   jamais la bande morte à l'épreuve. Ce qui prouve réellement l'anti-bavardage est
   l'assertion de séjour sur la traversée réelle — 54 h dans la sphère au lieu d'un
   redémarrage immédiat à `g = 0`. **ε reste donc à la valeur proposée (1,5 × 10⁻⁴, ≈ 10 km)
   sans avoir été mesuré contre un cas qui l'exige.** À rouvrir si L6 rencontre une
   trajectoire capturée.

### 11.4 Ce que L4 n'a pas eu besoin de faire

Aucun étage de production, aucune force, aucun des vingt sites de construction. Le rendu
n'a pas bougé d'une ligne (§6) et la règle de visibilité non plus. La correction de
`CoastingStage:48` est le seul changement qui touche un étage, et elle appartient à L1.

L5 et L6 peuvent démarrer.
