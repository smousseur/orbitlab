# PHY-4 / L3 — Éphéméride et rendu multi-arcs, avec un seul arc

Lot **L3** du découpage (`01-decoupage.md` §4). Il suit `03-conception-L1.md`, dont
il réutilise le `GravitationalContext` sans y toucher, et se mesure contre
`02-baseline-L0.md`. Il est indépendant de `04-conception-L2.md` : les deux lots
partent de L1 et ne se croisent pas.

**Propriété rendue vraie.** Une trajectoire est une **suite d'arcs**, chacun avec
son corps central et son repère ; le renderer sait en dessiner une. Il n'y en a
toujours qu'un seul en pratique.

**Ce que ce lot ne fait pas.** Rien ne produit un second arc. Aucune force, aucun
propagateur, aucune ligne de physique. Et — c'est la découverte du §1.1-C —
basculer le contexte de rendu sur le corps de l'arc corrige l'**identité** de ce
qui est dessiné, pas sa **place** à l'écran : dessiner un arc lunaire au bon
endroit reste entièrement à faire, et c'est L5.

> **Le lot le plus déséquilibré du chantier.** Sa surface est large — 10 sites de
> construction, 7 fichiers de test, 4 classes de rendu — et son risque numérique
> tient dans **une formule entière**, celle du budget de décimation (§4.1). Tout
> le reste du diff est mécanique et vérifié par le compilateur.

---

## 1. Inventaire mesuré

Relevé au commit `1a8317d`, après le merge de L2.

**Le repère n'est pas absent de l'éphéméride : il est jeté.**
`MissionEphemerisGenerator.Collector.pointOf` (`:143-149`) a `state.getFrame()`
sous la main et ne l'écrit pas. Le rendre explicite ne coûte rien côté
production ; le coût est entièrement aux sites de construction.

| | `src/main` | `src/test` |
|---|---|---|
| `new MissionEphemerisPoint(` | 3 | **7** (5 fichiers) |
| `new MissionEphemeris(` | 1 | **6** (5 fichiers) |

**Une frontière d'arc n'est pas une frontière de phase.**
`TrajectoryPolyline.rawRunStarts` (`:125-136`) partitionne sur les transitions de
`(stageName, propulsive)`. Une traversée de SOI tombe au milieu d'un coast : même
nom d'étage, même drapeau. C'est donc une **seconde partition indépendante**, pas
un cas particulier de run.

**L'interpolation traverse la frontière sans le savoir.**
`MissionEphemeris.interpolate` (`:145-168`) fait un Hermite cubique entre `i0` et
`i1`. Deux repères différents y donnent un résultat sans aucun sens — et
`displayPointAt` est la réponse unique à « où est le vaisseau maintenant » pour
trois consommateurs.

**La fabrique n'impose pas le repère de propagation — l'état initial le fait.**
`createOptimizationPropagator` (`OrekitService:186-200`) fixe `orbitType`, `mu` et
les forces, jamais le repère : Orekit le prend de l'orbite initiale. Donc
`ctx.inertialFrame() == state.getFrame()` tient aujourd'hui **par construction
ailleurs**, et rien ne le vérifie. C'est le risque que L2 §7 lègue à L4, et L3 est
le lot où il devient énonçable dans un type.

**`computeAltitudeMeters` lit le contexte de la *mission*, pas celui de l'étage.**
`Mission:92-96` appelle `gravitationalContext()` sur elle-même, alors que L1 a
installé la surcharge sur `MissionStage`. Un étage qui déclarerait un autre corps
calculerait donc son altitude contre la forme de la mission. C'est la phrase du
découpage « l'altitude portée par le point devient relative au corps de l'arc », et
c'est une ligne.

**Ce qui est hors de portée, et c'est une bonne nouvelle.** La timeline
(`MissionTimelineWidget`, `PhaseBar`, `PhaseMarkers`) ne lit **jamais**
`positionAt` — seulement `runs()`, `timeAt`, `runOf`, `indexUpTo`. Elle est
insensible au repère et ne figure nulle part dans ce lot.

### 1.1 Quatre corrections au découpage, mesurées

**A. Le découpage annonce deux lecteurs de `renderContextFor`. Il y en a trois.**
`MissionOrchestratorAppState:230`, `FloatingOriginAppState:161`, et
**`CameraTransitionAppState:353`**, qui calcule le pivot de la caméra par la même
conversion. Un lot qui n'en basculerait que deux ferait viser la caméra à côté à
la traversée — et le troisième site n'était pas dans le périmètre écrit.

**B. Et ce n'est pas une substitution d'expression : le contexte est figé à la
construction, dans quatre objets.** `createRenderer` l'évalue une fois et le passe
à `MissionRenderer` (champ `final`), à `BodyRenderConfig` → `LodView`, à
`MissionTrajectoryRenderer` (champ `final`) et à `PhaseNodeMarkers` (champ
`final`). Aucun ne le relit jamais. « Lire le corps de l'arc courant » veut dire
transformer une constante de construction en valeur par image dans quatre objets.
C'est le vrai poids du lot côté rendu.

**C. `toJmeBodyRelativePosition` ne lit jamais le corps du contexte — et ça change
la nature du lot.** `JmeVectorAdapter:60-66` n'utilise que `unitsPerMeter()` et
`axisConvention()`, et les deux sont **identiques pour tout `RenderContext.Planet`**
quel que soit le corps (`PLANET_METERS_PER_UNIT = 1e3`,
`ICRF_TO_JME_Y_UP`). Donc `planet(EARTH)` et `planet(MOON)` produisent le **même
triplet de `float`** pour le même `Vector3D`.

Deux conséquences, dans des directions opposées :

- l'invariant d'annulation au bit près que le découpage demande de préserver
  explicitement est **insensible au corps de l'arc**. Il est protégé par quelque
  chose de plus faible que ce que le découpage suppose ;
- et symétriquement, basculer le contexte sur le corps de l'arc ne déplace **rien**
  géométriquement. Sur tout le chemin de rendu d'une mission, un seul site lit
  réellement le corps du contexte : `MissionRenderer.onSpacecraftSelected:160`
  (`targetBody()` → corps parent de la caméra). `LodView:105` et `Model3dView:46`
  ne lisent que l'échelle ; `toJmePosition` reçoit sa cible en argument.

D'où la phrase d'ouverture : L3 pose la couture et corrige l'identité. Il ne rend
pas un arc lunaire dessinable, et le document doit le dire plutôt que de laisser
la lecture optimiste.

**D. L'argument de compacité de la question ouverte n° 4 ne survit pas au chiffre.**
Le découpage préfère le repère par segment parce qu'il est « plus compact (8192
sommets) ». Une référence par sommet sur 8 192 sommets fait 32–64 Ko ; un point
brut pèse déjà ~100 octets (trois `Vector3D` plus une `String`). Le repère par
point coûte **~1 %** de l'empreinte. Le choix ne se joue pas sur la place, et le
§2 le tranche sur un autre critère.

---

## 2. L'objet : un record étroit, pas le contexte gravitationnel

```java
package com.smousseur.orbitlab.simulation.mission.ephemeris;

/** The frame one segment of a trajectory is expressed in: the body it is centred
 *  on, and the inertial frame that centring is realised by. */
public record TrajectoryArc(SolarSystemBody body, Frame frame) {

  /** The pairing, stated once: what a stage declares is what its samples carry. */
  public static TrajectoryArc of(GravitationalContext ctx) {
    return new TrajectoryArc(ctx.body(), ctx.inertialFrame());
  }

  public static TrajectoryArc earth() { return Holder.EARTH; }
}
```

**Pourquoi pas `GravitationalContext` directement**, alors qu'il porte déjà les
deux composants et que le `Collector` pourrait l'écrire sans dérivation. Parce que
**l'égalité de ce record est l'identité d'arc** : c'est elle que le §4 emploie pour
détecter une frontière. Or l'égalité d'un `GravitationalContext` inclut `mu` et
les perturbateurs, qui sont des affaires de propagation. En L6, un même arc
lunaire pourra légitimement enchaîner un coast sans perturbateur et une poussée
avec : groupé par `equals`, cela fabriquerait une frontière d'arc là où il n'y en
a aucune. Il faudrait alors grouper sur `body()` seul, c'est-à-dire porter un
record dont l'égalité ment sur l'usage qu'on en fait.

Le prix est une dérivation d'une ligne. Elle est concentrée dans `of(...)`, et
c'est précisément ce qui rend la paire *corps déclaré ↔ repère réellement utilisé*
énonçable en un seul endroit — le §1 rappelle que rien ne la vérifie aujourd'hui.

### 2.1 `earth()` ne peut pas passer par `GravitationalContext.earth()`

Quatre des cinq fichiers de test qui construisent des points **n'initialisent
jamais `OrekitService`** : `TrajectoryPolylineTest`, `MissionTimelineVisibilityTest`,
`MissionLoadEvaluatorTest`, `MissionEphemerisDisplayPointTest`. Seul
`PhaseNodeMarkersTest` a un `@BeforeAll`.

`GravitationalContext.earth()` résout ITRF et l'ellipsoïde WGS84 ; l'appeler
depuis ces tests les casserait. `TrajectoryArc.earth()` résout donc **GCRF seul**,
par un Holder, sur le modèle du §2.1 de L1.

Le prix est une **seconde énonciation de la paire Terre**, et il se paye par un
test d'une ligne :

```java
assertEquals(TrajectoryArc.of(GravitationalContext.earth()), TrajectoryArc.earth());
```

Les deux ne peuvent donc pas dériver l'une de l'autre sans que la suite le dise.

### 2.2 Le composant est ajouté en dernier, et il n'y a pas de surcharge

```java
public record MissionEphemerisPoint(
    AbsoluteDate time, Vector3D position, Vector3D velocity,
    String stageName, boolean propulsive, double mass,
    double altitudeMeters, TrajectoryArc arc) { … }
```

En dernier, pour que les 7 sites de test soient un ajout mécanique.

**Aucune surcharge Terre-implicite**, et c'est le §3.2 de L1 mot pour mot : tant
qu'elle existe, un site oublié compile et porte la Terre en silence ; une fois
absente, le compilateur énumère les sites à votre place.

### 2.3 Le choix de la granularité, tranché

Question ouverte n° 4 du découpage : par point. Le §1.1-D a retiré le seul
argument que le découpage donnait pour le segment. Ce qui décide à la place :

**quatre consommateurs tiennent un point nu** — `FloatingOriginAppState`, l'ancre
du vaisseau, `CameraTransitionAppState`, la télémétrie. S'ils lisent l'arc **dans
le point**, ils lisent le même champ du même objet : l'invariant du §1.1-C cesse
d'être une coïncidence et devient impossible à violer sans lire deux points
différents, ce qui casserait déjà tout aujourd'hui. Avec une table d'arcs séparée,
chacun devrait interroger l'éphéméride en plus — et c'est exactement l'endroit où
deux lecteurs divergent d'une image.

Second effet, gratuit : `interpolate` voit les deux repères et peut refuser la
frontière au lieu de la moyenner (§3.3).

---

## 3. Le câblage

### 3.1 Le contexte de rendu se dérive du point, plus de l'entrée

```java
// MissionRenderer — the MissionEntry overload is deleted, not kept beside it.
public static RenderContext renderContextFor(MissionEphemerisPoint point) {
  return RenderContext.planet(point.arc().body());
}
```

`FloatingOriginAppState:156` et `CameraTransitionAppState:350` ont déjà le point en
main : deux lignes chacun. Les trois lecteurs appellent déjà `displayPointAt(now)`
à la même date — **même point ⇒ même arc ⇒ même contexte**, par le mécanisme exact
qui leur donne déjà le même `p`.

C'est le point de conception qui compte le plus côté rendu. Le découpage demande
que les lecteurs « ne cessent jamais de lire la même chose l'un que l'autre » ;
la seule façon d'en faire une propriété plutôt qu'une consigne est de leur faire
lire **le même objet**, et cet objet existe déjà.

`createRenderer` (`MissionOrchestratorAppState:230`) a l'éphéméride mais pas de
point : il prend `eph.firstPoint()`, l'arc dans lequel la mission commence. Ce
contexte figé ne sert qu'à l'échelle (§1.1-C) ; son javadoc le dit et renvoie à L5.

**Une alternative écartée pour une raison mesurée.** Faire calculer le contexte une
fois par image par l'orchestrateur et le publier pour les deux autres ne marche
pas : `FloatingOriginAppState` tourne **délibérément avant** l'orchestrateur (cf.
le javadoc de `nearFrameOffset`), donc il lirait la valeur de l'image précédente.
C'est le défaut même que cet ordre d'exécution existe pour empêcher.

### 3.2 Les deux champs figés deviennent des paramètres par image

`MissionRenderer.updateFromEphemeris` dérive `renderContextFor(point)` et le passe
à `presenter.updatePose` et à `trajectoryRenderer.update` ; les champs `final` de
`MissionTrajectoryRenderer` et `PhaseNodeMarkers` disparaissent.

**C'est un changement purement structurel, sans risque numérique**, et le §1.1-C
en est la garantie : la conversion étant aveugle au corps, aucun chiffre ne peut
bouger. Ce qu'il achète est qu'il ne reste plus une seule source capable de
devenir périmée. Les laisser en place marcherait aussi — mais **par accident**, et
L5 devrait rouvrir exactement ces deux classes.

`BodyRenderConfig` reste tel quel : il ne sert qu'à l'échelle.

### 3.3 `interpolate` applique une sémantique de plancher à la frontière

Arcs différents entre `i0` et `i1` ⇒ retourner `pointAt(i0)` tel quel, sans
Hermite. C'est déjà la sémantique que la même méthode applique à `stageName` et à
`mass` (« floor semantics ») ; le coût est une comparaison.

**L'effet de bord est recherché.** Combiné au §3.1, le contexte de rendu bascule
**atomiquement** au point entrant : il n'existe aucune image où les trois lecteurs
pourraient tenir des arcs différents. La discontinuité est bornée par un pas
d'échantillonnage.

Les deux alternatives, et pourquoi elles tombent. Lever une exception est l'état
le plus honnête — un Hermite entre deux repères n'a pas de sens — mais
`displayPointAt` est appelé par trois `AppState` à chaque image : il lèverait à
60 Hz pendant toute la traversée. Convertir puis interpoler est physiquement juste
et continu, mais c'est exactement le travail que L4 conçoit, fait ici en avance,
sans son test, et dans un chemin appelé trois fois par image.

### 3.4 L'altitude passe sur le contexte de l'étage

`Collector.pointOf` écrit `TrajectoryArc.of(stage.gravitationalContext(mission))`,
et l'altitude est calculée contre la forme de ce contexte-là, plus contre celle de
la mission (§1). Aujourd'hui strictement identique — aucun étage ne surcharge —
donc aucun chiffre ne bouge ; demain juste.

---

## 4. Le polyline : une seconde partition

```java
public record ArcRun(TrajectoryArc arc, int firstVertex, int count) {}

private final short[] arcOf;
private final List<ArcRun> arcs;
public int arcOf(int index);
public List<ArcRun> arcs();
```

`of(...)` prend un cinquième tableau parallèle `TrajectoryArc[]`. `rawArcStarts`
calque `rawRunStarts` en comparant par `equals` — l'égalité de `TrajectoryArc`
**est** l'identité d'arc, c'est ce pour quoi le §2 l'a fait étroit. `build()` gagne
une seconde passe identique à celle des runs. Aucun concept nouveau n'entre dans la
classe : c'est sa forme existante, appliquée une fois de plus.

**Pourquoi pas raffiner la partition existante**, en faisant simplement qu'un
changement d'arc ouvre un nouveau run — ce qui n'aurait coûté ni structure ni
budget. Parce qu'un run est un objet d'**affichage** : nom d'étage, couleur de
phase, marqueur de transition. Une traversée de SOI au milieu d'un coast couperait
ce coast en deux runs homonymes, donc **deux marqueurs de phase** dessinés, et un
compte de phases faux dans la timeline (`MissionTimelineWidget:303` affiche
`trail.runs().size() + " phases"`). Ce serait faire porter une frontière physique
par un concept graphique.

**Pourquoi l'arc doit descendre jusqu'ici**, plutôt que d'être résolu par le
renderer en interrogeant l'éphéméride : le polyline est décimé et **ne conserve
pas `srcOf`** — `build()` (`:153-184`) l'utilise puis le jette. Il n'existe aucun
chemin d'un index de sommet vers l'index brut correspondant.

Et le javadoc de `positionAt` (`:212`, « in GCRF meters ») devient « in the frame
of `arcOf(index)` ». C'est la seule hypothèse Terre-en-dur écrite noir sur blanc
dans ce fichier.

### 4.1 L'union, pas la somme — et c'est tout le risque du lot

Le budget de décimation réserve aujourd'hui de la marge pour les sommets forcés :

```java
int budget = Math.max(1, MAX_POINTS - runStart.length - 1);
```

Les débuts d'arc sont forcés eux aussi. La tentation est d'en soustraire le
compte. **C'est l'erreur à ne pas commettre.** L'écriture juste est l'**union
dédupliquée** des deux jeux de frontières :

```java
int[] forced = union(runStart, arcStart);   // both sorted, both starting at 0
int budget = Math.max(1, MAX_POINTS - forced.length - 1);
```

Avec un seul arc, `arcStart = {0}`, qui est déjà `runStart[0]` : l'union est
identique, donc le budget est identique au bit, donc le stride est identique, donc
**l'ensemble des sommets gardés est identique par construction**. L'égalité
géométrique que le découpage demande devient structurelle au lieu d'être espérée.

Écrite en somme, la même ligne fait perdre un slot de budget. Le stride étant
entier, le plus souvent rien ne bouge — et c'est précisément ce qui rend le défaut
mauvais : il ne se manifeste qu'au voisinage d'un multiple de 8 192, c'est-à-dire
sur les horizons longs que peu de tests exercent, et jamais sur les fixtures
courtes.

---

## 5. Ce que L3 ne touche pas

| Site | Raison |
|---|---|
| La règle de visibilité (`MissionOrchestratorAppState:109`, `FocusView.isMissionVisible`) | §5.1 |
| La **conversion** d'arc | §5.2 |
| La géométrie à l'écran | §1.1-C : l'identité est corrigée, la place ne l'est pas. C'est L5. |
| `MissionLoadEvaluator.objectiveMet:296` | Prend le min/max d'altitude du coast final. Tient tant que ce coast est d'un seul arc — hypothèse écrite, pas corrigée. L6 la rouvrira. |
| La timeline | Insensible au repère (§1). |
| Les 20 sites de propagation, les étages, les forces | Rien du tout. |

### 5.1 La règle de visibilité reste sur l'objectif, et c'est une décision

`isMissionVisible` gate le renderer **entier** : `setVisible(false)` cache le
vaisseau *et* la ligne. Pour une mission multi-arcs, la règle juste est presque
sûrement **par arc** — on regarde la Terre, l'arc terrestre reste légitime à
l'écran même si le vaisseau est déjà dans la SOI lunaire.

La basculer maintenant sur `point.arc().body()` serait strictement équivalent
aujourd'hui, donc tentant pour clore la couture. Mais cela installerait une règle
**fausse pour ce qu'elle prépare** : elle cacherait la mission entière, arc
terrestre compris. C'est une question de conception L5, et elle est écrite ici
comme telle — pas comme un oubli. Le javadoc de `FocusView.isMissionVisible`, qui
énonce aujourd'hui l'invariant « une mission est dessinée dans le contexte du
corps que vise son objectif », le dit.

### 5.2 La conversion d'arc est L4, pas L3

Le découpage annonce en fermeture des « tests d'unité sur la concaténation **et la
conversion** d'arcs ». La conversion — ramener un état d'un repère dans l'autre —
est le cœur technique de L4, avec sa propre cible au millimètre. **L3 teste la
concaténation et le cas dégénéré**, et le §3.3 explique pourquoi il refuse de
convertir. Correction au découpage.

---

## 6. Les tests

### 6.1 Unité : la concaténation et le cas dégénéré

Dans `TrajectoryPolylineTest`, qui possède déjà une fixture à 40 runs et plus de
8 192 points servant de patron :

- **un seul arc** ⇒ `arcs().size() == 1`, `arcOf(i) == 0` partout,
  `firstVertex == 0`, `count == size()`. C'est le cas dégénéré que le découpage
  demande explicitement, et c'est le seul que la production produira en L3 ;
- **deux arcs synthétiques** ⇒ la frontière est un sommet gardé **même au-delà du
  budget**. Sans cela, la décimation joindrait les deux arcs par un segment qui
  traverse un changement de repère ;
- **une frontière d'arc qui ne coïncide pas avec une frontière de run** ⇒
  `runs().size()` inchangé. C'est le test qui prouve que les deux partitions sont
  indépendantes, donc que le choix du §4 n'a pas été fait pour rien ;
- `TrajectoryArc.earth().equals(TrajectoryArc.of(GravitationalContext.earth()))`
  (§2.1) ;
- **la sélection de sommets elle-même, épinglée** —
  `overTheBudget_theKeptVerticesArePinned`. Les autres tests au-delà du budget
  vérifient des bornes et des extrémités, qu'un stride décalé d'un cran
  satisferait encore. Celui-ci épingle le stride, sur une fixture dimensionnée
  pour rendre un slot de budget perdu impossible à manquer : `n` vaut exactement
  deux fois le budget d'une trace mono-run, donc perdre un slot fait passer
  `ceil(n/budget)` de 2 à 3 et emporte un tiers des sommets. **C'est le filet du
  §4.1 au niveau unité, et il est instantané.**

### 6.2 L'interpolation à la frontière

`MissionEphemerisDisplayPointTest` gagne le seul cas de L3 qui exerce réellement
le multi-arc : deux points d'arcs différents, `displayPointAt` au milieu rend
`pointAt(i0)` **exactement** — position égale au point sortant, pas une moyenne —
et porte l'arc sortant.

### 6.3 L'annulation, et le §1.1-C promu en test

`NearFrameOriginTest` reste vert tel quel. On lui ajoute un cas qui dérive le
contexte de `renderContextFor(point)` pour un arc **lunaire** et exige la même
annulation exacte : par le §1.1-C elle doit tomber au même bit que pour l'arc
terrestre.

Le test cesse alors d'être seulement l'invariant d'annulation. Il devient la
**preuve écrite et exécutable** que la conversion est aveugle au corps — c'est-à-dire
l'énoncé de ce que L5 aura à changer, épinglé au lieu d'être raconté.

### 6.4 Le vol minimal

Un test qui vole la fixture LEO-400 du gate L1 via `MissionEphemerisGenerator` et
épingle : nombre de points bruts, nombre de sommets du polyline, nombre de runs,
`arcs().size() == 1`, premier et dernier sommet, indices de début de run. Valeurs
**mesurées sur le commit actuel avant tout changement**, comme le §5.3 de L1 —
elles ne se recopient pas depuis la baseline.

**Pourquoi un vol malgré l'argument structurel du §4.1.** L2 avait le droit de
s'en tenir à une non-régression structurelle, parce que son code de production
était inchangé. Ici le code est réécrit : l'argument porte sur ce qu'on modifie.
Et le §5.2 de L1 est la leçon exacte — un raisonnement structurel s'y est révélé
faux (« une chaîne complète couvre les douze sites analytiques »), et seule la
mesure l'a montré.

> **Corrigé à l'implémentation.** Ce paragraphe disait « la fixture est **extraite**
> de `CentralBodyBaselineTest`, pas recopiée », au motif que les 62 frontières du
> gate à tolérance `0.0` prouveraient l'innocuité de l'extraction. C'est vrai au
> moment de l'extraction, et c'est hors sujet : L1 a écrit la règle inverse dans le
> javadoc du gate, et sa raison porte sur l'avenir, pas sur l'instant.
>
> > *The fixtures are copied, not shared. […] a gate must not rest on another
> > test's fixture: a change over there would move the reference over here with
> > nobody seeing it.* — `CentralBodyBaselineTest:96-98`
>
> Elle s'applique symétriquement au gate L3. La fixture LEO-400 est donc
> **recopiée**, et les deux sont libres de diverger : ce qui est épinglé ici est
> que *cette* trajectoire-là ne bouge pas, pas qu'elle soit la même que celle du
> gate L1.

**Et la fixture décime — ce n'était pas acquis.** Mesuré le 2026-08-17 au commit
`1a8317d` : **9 992 points bruts pour 5 000 sommets**, contre un budget de 8 192.
Le gate volé n'atteste donc pas seulement qu'une trace sous le budget est recopiée
telle quelle : il exerce le chemin du stride, celui que le §4.1 désigne comme
l'unique risque numérique du lot, sur une trajectoire réelle et non synthétique.
Le §8 en tient compte.

---

## 7. Ordre d'exécution

1. **Extraire la fixture et épingler le polyline LEO-400 sur le commit actuel.**
   Aucun code de production. Le gate L1 vert prouve l'extraction. Commit séparé :
   c'est le filet, il doit exister avant la chute.
2. **`TrajectoryArc`, le composant sur le point, la colonne dans l'éphéméride**,
   l'altitude prise sur l'étage (§3.4). Le polyline ignore encore l'arc. Les deux
   tests restent verts.
3. **La partition d'arc dans le polyline, budget en union** (§4.1). Le test
   épinglé doit rester vert **à l'identique** : c'est le §4.1 vérifié plutôt que
   raisonné, et c'est l'étape où le seul risque numérique du lot se joue.
4. **Le câblage de rendu** (§3.1, §3.2) : `renderContextFor(point)`, suppression de
   la surcharge sur `MissionEntry`, les deux champs devenus paramètres.
   `NearFrameOriginTest` vert.
5. **Les tests d'unité multi-arcs** (§6.1 à §6.3). Comme en L2, ils ne peuvent pas
   précéder l'API : ils testeraient du code qui ne compile pas.
6. **Suite complète.**

L'étape 3 est séparée de l'étape 2 pour la même raison que l'étape 2 de L1 l'était
de son étape 3 : si un sommet bouge, on veut déjà savoir que ce n'est ni le record
ni la colonne.

---

## 8. Risques identifiés

**Le seul risque numérique du lot est la formule du budget** (§4.1). Écrite en
somme au lieu d'union, elle déplace le stride sur les missions au-delà de 8 192
points. L'étape 3 de l'ordonnancement existe pour ça, et **deux** tests épinglés à
l'étape 1 l'attrapent : celui d'unité, dimensionné pour qu'un slot perdu change un
tiers des sommets (§6.1), et le gate volé, dont la mesure a montré qu'il décime
réellement (§6.4).

> La rédaction initiale ajoutait ici « silencieusement, et seulement sur les
> horizons longs que peu de tests exercent ». La seconde moitié est démentie par la
> mesure : l'horizon **par défaut** d'une mission LEO produit déjà 9 992 points et
> décime. Le risque est donc plus facile à attraper que le document ne le craignait
> — et il l'est effectivement.

**`TrajectoryArc.earth()` et l'initialisation Orekit.** `FramesFactory.getGCRF()`
est réputé ne demander aucune donnée EOP. **À vérifier à l'implémentation, pas à
supposer** : si c'est faux, les quatre tests sans `@BeforeAll` du §2.1 cassent, et
il faut soit un `@BeforeAll`, soit un arc de test construit explicitement.

**La surface du diff.** 10 sites de construction, 7 fichiers de test, 4 classes de
rendu. Mécanique et relisable, mais il ne doit contenir *que* la bascule : aucun
renommage, aucun reformatage, aucune correction de javadoc non liée. Le §7 de L1
s'applique mot pour mot.

**Ce que L3 n'a pas comme risque.** Aucune force, aucun propagateur, aucune
physique. Contrairement à L1, la non-régression numérique se réduit à une formule
entière et à la sélection de sommets qu'elle commande.

---

## 9. Ce que L3 laisse ouvert

- **L4** produira le second arc, et hérite d'une contrainte précise : `interpolate`
  rend un plancher à la frontière (§3.3), donc L4 doit trancher si la frontière est
  un point **dupliqué à date égale** — `EphemerisInterpolator.findInterval` sur deux
  temps identiques est un cas à traiter, pas une évidence. Il hérite aussi du §1 :
  le repère de propagation vient de l'état initial, pas de la fabrique.
- **L5** hérite de trois choses : la règle de visibilité, qui doit devenir par arc
  (§5.1) ; la géométrie, que le §1.1-C laisse entièrement à faire ; et le contexte
  figé de `createRenderer` (§3.1).
- **L6** : `MissionLoadEvaluator.objectiveMet` suppose un coast final d'un seul arc.
- **La question ouverte n° 1 du découpage est tranchée, et sans coût.** Le repère
  d'un arc est l'inertiel centré sur son corps, tel que
  `GravitationalContext.inertialFrame()` le porte déjà. `RenderFrame` n'a **pas**
  besoin d'un troisième cas : `PLANETOCENTRIC_RELATIVE_ICRF` est déjà paramétré par
  un corps quelconque via `RenderContext.Planet`, et le §1.1-C montre qu'il n'est
  consulté que pour décider s'il faut soustraire une cible.
- **La question ouverte n° 4 est tranchée** : par point (§2.3), sur un critère qui
  n'est pas celui que le découpage anticipait (§1.1-D).
