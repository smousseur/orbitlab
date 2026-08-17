# PHY-4 / L5 — Les deux échelles à l'écran

Lot **L5** du découpage (`01-decoupage.md` §4). Il suit `05-conception-L3.md`, dont il
hérite de trois dettes écrites, et `06-conception-L4.md`, dont il hérite d'une quatrième.
Il se mesure contre `02-baseline-L0.md` par les deux gates que L1 et L3 ont laissés.

**Propriété rendue vraie.** Une trajectoire qui va de 200 km à 400 000 km se lit.

**Ce que ce lot ne fait pas.** Aucune mission de production ne produit encore un second
arc — c'est L6 qui sera le premier déclarant réel de `soiTransitions`. Aucun chiffre de
mission ne bouge, et pas par chance : avec un seul arc, le tableau de positions converti
**est** le tableau existant, par identité de référence. Il n'y a donc pas de capture
d'écran dans ce lot ; le §7.7 dit pourquoi, et ce n'est pas un oubli.

> **Le lot dont le diagnostic coûte plus cher que le correctif.** Trois des quatre
> défauts se ferment en un mot ou une constante (§4, §5) ; ce qui a demandé du travail
> est de savoir *lesquels*, et surtout de mesurer que le troisième viewport n'est pas
> nécessaire (§5.3) alors que deux documents antérieurs l'annonçaient comme inévitable.

---

## 1. Inventaire mesuré

Relevé au commit `5014b68`, après le merge de L4.

### 1.1 Le vrai obstacle n'est pas la profondeur

`LodView.setPositionWorld:61` ne déplace que `farAnchor` ; `nearSpatial()` (le champ
`anchor3d`) **n'est jamais translaté**. Le modèle 3D de chaque corps est donc garé sur
l'origine du viewport near, et `SceneGraph.showBodySpatial:95-100` masque tous les autres.
`PlanetHudMarkersAppState:71` passe `allow3d = (body == focusView.getBody())`, et le
viewport far ne dessine **aucun corps** — seulement des lignes d'orbite (le bilan FX-1 de
la roadmap l'a déjà consigné : « `LodView` coupe un corps en deux : une ancre de position
sans géométrie sous `farBodiesNode`, le modèle GLTF sous `nearBodiesNode` »).

Donc « Terre + Lune dans le même cadre » n'est pas d'abord un problème de budget de
profondeur : c'est une architecture où un second globe n'a **aucun endroit où exister**.
Le §3.2 montre que ce n'est pas un obstacle mais le mécanisme.

### 1.2 Le budget de profondeur, chiffré

Formule de `NearFrustumDepthTest`, `Δz = 2⁻²⁴ · z² · (1/near − 1/far)`, en unités km :

| situation | `near` | pas à 384 400 km | rayon lunaire / pas |
|---|---:|---:|---:|
| vue SPACECRAFT, focus 500 m | 0,1 km | **88 000 km** | 0,02 |
| vue SPACECRAFT, zoom 5 km (plafond) | 1 km | 8 807 km | 0,20 |
| vue PLANET, d = 30 000 km | 15 km | 587 km | 2,96 |
| vue PLANET, d = 400 000 km | 200 km | 44 km | 39,4 |

Le budget n'est donc **pas uniformément cassé**. Il l'est en vue spacecraft — la Terre
entière tient dans 0,14 pas de quantification — et en vue planet zoomée ; il est correct
en vue planet dézoomée. Pour obtenir 10 km de pas à 384 400 km il faudrait `near ≈ 880 km`,
ce qui découpe tout le reste : un seul buffer 24 bits ne peut pas tenir 100 m et
384 400 km, ratio 3,8 × 10⁶.

### 1.3 Avant la profondeur, le clipping

En vue spacecraft, `NearCameraSyncAppState:125` donne
`far = clamp(distToOrigin·10, 100 000, 10⁸)` avec `distToOrigin ≈ 0,5 km` : **far = 100 000 km**,
collé à son plancher. Les trois quarts d'un tracé TLI sont coupés net avant qu'aucune
question de profondeur ne se pose. C'est le premier symptôme qui serait apparu à l'écran,
et c'est une constante.

### 1.4 Le globe est plus faux que l'arc

L3 §1.1-C conclut « la place reste entièrement à faire ». Concrètement : un point
sélénocentrique au périlune (1 837 km du centre lunaire) est dessiné à 1 837 km de
l'origine near — et `showBodySpatial(EARTH)` y met **le globe terrestre**. Ce n'est pas
« l'arc est à 384 400 km de son endroit », c'est « l'arc est juste, le globe est faux ».
Le correctif n'est pas le même, et il est plus petit.

### 1.5 Le ruban a une seule origine, et il soustrait à travers les repères

`MissionTrajectoryRenderer:161-165` prend `origin = tip` puis écrit
`positionAt(i).subtract(origin)` pour chaque sommet. Avec deux arcs, cela soustrait un
vecteur géocentrique d'un vecteur sélénocentrique. Une `Geometry`, une translation : la
classe ne peut structurellement pas porter deux repères.

Trois sites de production lisent `positionAt` : `MissionTrajectoryRenderer:161`, `:165`,
`PhaseNodeMarkers:113`. C'est tout.

### 1.6 Le suivi caméra gèle le corps

`FocusView.viewSpacecraft:98` fixe `body` une fois, rien ne le met à jour en vol.
`CameraTransitionAppState.spacecraftPivot:344` ajoute l'offset corps-relatif du point à
`bodyPivot(target.parentBody())` — le parent capturé au clic. Et
`isMissionVisible(objective.body())` (`MissionOrchestratorAppState:109`) compare
l'objectif au corps focalisé : **une mission lunaire cliquée depuis la Terre serait
masquée sur la frame même où elle devient focalisée.**

### 1.7 La fenêtre glissante ne couvre pas la trace

`EphemerisConfig.defaultSolarSystem()` : pas de 10 min, 200 points en arrière, 400 en
avant — soit **33 h en arrière, 66 h en avant**. Un transfert lunaire dure 3 à 5 jours.

C'est la mesure qui décide de l'architecture du §3.3, et elle **dément une contrainte que
la conversation avait posée comme évidente** : « l'offset inter-corps doit venir de la
même source que le globe, sans quoi le trait ne toucherait pas la Lune ». C'est impossible
pour le gros de la trace — et la raison invoquée s'effondre d'elle-même, parce que le
trait converti et le globe **ne sont jamais à l'écran ensemble** :

- en vue SPACECRAFT le tracé est arc-relatif et le globe est le corps de l'arc garé sur
  l'origine — ils ne peuvent pas diverger, c'est le même vecteur ;
- en vue PLANET centrée sur la Terre, la Lune n'est dessinée qu'en icône de 16 px.

La contrainte réelle est donc de cohérence **interne au tracé** (§6.1), pas de cohérence
avec le globe.

### 1.8 Ce qui joue en notre faveur

`toJmePosition(objet, cible, ctx)` fait déjà la composition « absolu moins cible » pour la
vue far. `spacecraftPivot` compose déjà « position du corps » + « offset corps-relatif » —
il se trompe seulement de corps. `TrajectoryPolyline` garde `times[]`, donc une conversion
par sommet à sa propre date est possible. Et `bindColors` est déjà gardé par une identité
de trace : le patron d'un tableau dérivé, calculé une fois et publié au thread de rendu,
existe déjà dans la classe qu'on modifie.

---

## 2. Ce que L5 décide

Quatre décisions, prises dans cet ordre, chacune conditionnant la suivante.

1. **Un seul globe 3D à l'écran ; le tracé complet est lisible.** L'autre corps reste
   l'icône HUD existante — ce qui est déjà le comportement de la Lune vue depuis la Terre
   aujourd'hui. L'architecture « un corps garé sur l'origine » du §1.1 n'est pas ouverte.
2. **Les sommets d'un arc étranger sont convertis dans le repère du corps de rendu**,
   par sommet, à la date du sommet. Pas de visibilité par arc, pas de `Geometry` rigide
   par arc — un arc n'est rigide que si son corps ne bouge pas pendant sa durée, et L4 a
   mesuré **54 h de séjour** lunaire, pendant lesquelles la Lune parcourt ~55 000 km.
3. **Le corps affiché bascule à la frontière d'arc, dérivé du point affiché.** Aucun
   nouvel état, aucun détecteur, aucun événement : la bascule est atomique par
   construction et réversible en marche arrière, ce qu'un événement ne serait pas.
4. **Une mission est visible si le corps affiché figure parmi les corps de ses arcs.**
   C'est la règle « par arc » que L3 §5.1 avait laissée à ce lot.

Les alternatives écartées et leurs raisons sont au §8.

---

## 3. Le corps de rendu et les deux conversions

### 3.1 Une seule dérivation, faite sur le point

```java
// MissionRenderer — renderContextFor en devient un dérivé
public static SolarSystemBody renderBodyOf(MissionEphemerisPoint point, FocusView view) {
  return view.getMode() == ViewMode.SPACECRAFT ? point.arc().body() : view.getBody();
}
```

Chaque lecteur la dérive du point qu'il lit déjà, **jamais d'une valeur publiée**.
L'ordre d'attache (`CameraTransitionAppState:114` → `FloatingOriginAppState:120` →
`MissionOrchestratorAppState:145`) interdit la publication : le premier lirait la valeur
de l'image précédente. C'est mot pour mot l'alternative que L3 §3.1 avait déjà écartée,
et pour la même raison.

Les trois lecteurs appellent déjà `displayPointAt(now)` à la même date — même point ⇒ même
arc ⇒ même corps de rendu, par le mécanisme exact qui leur donne déjà la même position.

### 3.2 `FocusView.body` n'est jamais réécrit

C'est un renoncement délibéré à la solution la plus courte. Faire écrire
`focusView.setBody(point.arc().body())` par la boucle de rendu ferait suivre d'un coup les
six lecteurs de ce champ — mais détruirait la seule chose qu'il dit : **quel corps
l'utilisateur a choisi de regarder**. Un champ d'intention réécrit soixante fois par
seconde par un autre acteur n'est plus une intention.

Ce sont donc les deux lignes du cas `SPACECRAFT` de `FloatingOriginAppState` (`:93`,
`:98`) qui passent de `view.getBody()` au corps de rendu :

- `showBodySpatial(corps de rendu)` gare le bon globe ;
- le far root se recentre sur lui, pour que les icônes des autres corps s'ordonnent autour
  de la Lune quand on y est — ce que le javadoc de ce bloc demande déjà, en nommant le
  parent plutôt que le corps de rendu parce qu'à l'époque les deux ne pouvaient pas
  différer.

Et **l'architecture du §1.1 devient le mécanisme au lieu de l'obstacle**. En vue
spacecraft, `nearFrame` est translaté de −p et le globe garé à l'origine de
`nearBodiesNode` se dessine donc à |p| du vaisseau. Si p est sélénocentrique *et* que le
corps garé est la Lune, la Lune apparaît exactement à la bonne distance. Rien à délier,
rien à ajouter.

Vérifié sur les autres lecteurs de `FocusView.body` : `isSatelliteVisible(MOON)` reste
vrai avec `body = EARTH` (`EARTH == MOON.parent()`) ; `bodyPivot` ne sert que pendant une
transition, où le §5.2 lui donne le bon corps explicitement ; `isMissionVisible` change de
signature au §5.3. Aucun ne demande que le champ bouge.

### 3.3 Deux conversions, et elles ne partagent que leur formule

|  | le gros (polyline) | le point (tête, ancre, origine near, pivot) |
|---|---|---|
| quand | à la construction de l'éphéméride, hors thread de rendu | par image |
| combien | ≤ 8 192 × (corps d'arc distincts) | 1 |
| source | Orekit `CelestialBodyFactory` | Orekit, **la même** |

La formule, écrite une fois :

```
p_rendu(i) = p(i) + (corps_arc(i) − corps_rendu)(times[i])      // identité si égaux
```

**Pourquoi Orekit et pas `EphemerisService`** : §1.7. La fenêtre glissante couvre 33 h en
arrière, une trace lunaire en demande 120. Une conversion par sommet passant par le buffer
échouerait silencieusement sur les sommets les plus anciens — c'est-à-dire dessinerait une
trace fausse sans lever.

**Pourquoi la même source pour les deux conversions** : la tête est l'origine à laquelle
tous les sommets sont soustraits. Deux sources donneraient un **coude** entre le dernier
sommet et la tête du vaisseau, à l'endroit précis où l'œil regarde. C'est le mode de panne
que le §7.3 attrape.

### 3.4 Un tableau de positions par corps d'arc distinct

`TrajectoryPolyline` gagne un tableau de positions **par corps d'arc distinct**, calculé à
la construction.

Pas un tableau « canonique » unique plus une seconde conversion à l'affichage : cette
seconde conversion aurait lieu sur le thread de rendu, à des dates réparties sur toute la
trace, et retomberait exactement dans le trou de fenêtre du §1.7.

**Avec un seul arc il y a un tableau, et il *est* `positions`.** La non-régression est
donc une **identité de référence**, pas une égalité mesurée — le même argument structurel
que l'ensemble vide de perturbateurs en L2 §4.1 et que le tronçon unique en L4 §6.

Coût : ~400 Ko par corps supplémentaire et par mission, chiffre que RND-5 §4.1 a déjà
accepté pour son propre second tableau — et c'est le même endroit,
`TrajectoryPolyline` restant « le seul qui connaît les repères » (RND-5 §5).

L'accesseur devient `positionAt(int index, SolarSystemBody renderBody)`, **remplacé et non
surchargé** : règle de L3 §2.2, tant qu'une surcharge Terre-implicite existe, un site
oublié compile et dessine faux en silence. Un corps de rendu qui n'est celui d'aucun arc
**lève**, plutôt que de rendre silencieusement le mauvais tableau.

---

## 4. Le ruban ne change pas de structure

C'est le résultat le plus agréable du lot. Une fois le bon tableau choisi **et** la tête
convertie par la même formule, tous les sommets et la tête sont dans le repère du corps de
rendu : la soustraction du §1.5 redevient légale.

Une `Geometry`, une translation, **aucun découpage par arc**. Et la propriété de précision
de `spacecraft-view-artefacts.md` §4 est conservée mot pour mot — les sommets restent
exprimés relativement à la tête, leur erreur reste bornée par leur distance au vaisseau et
non par leur distance au corps central.

### 4.1 `arcStart − 1` : la dette de L4, avec une raison neuve

L4 §5 lègue « `arcStart − 1` à ajouter à l'union du budget le jour où L5 dessine », au
motif qu'un segment droit joindrait sinon deux sommets exprimés autour de corps différents.

**Cette raison est périmée.** Avec la conversion du §3.3, les deux côtés de la frontière
sont dans le même repère et le segment est géométriquement juste — seulement plus
grossier, d'au plus un `stride`.

La ligne est donc **facultative**, et elle est ajoutée quand même : elle coûte un slot de
budget, elle fait que `ArcRun.vertexCount()` de l'arc sortant contienne réellement sa
frontière, et une dette écrite qu'on choisit de ne pas payer se repaie deux fois. Ce qui
change est la raison, et le document doit le dire plutôt que de recopier celle de L4.

---

## 5. Le frustum, la caméra, la visibilité

### 5.1 Une constante, et c'est tout

`FAR_MIN` passe de `100_000f` à `500_000f` (§1.3).

**Et ça ne coûte rien, c'est mesuré et déjà testé.** `Δz ∝ z²·(1/near − 1/far)` avec
`1/near = 10` contre `1/far = 2 × 10⁻⁶` : monter le plan far de 100 000 à 500 000 km
déplace la résolution de profondeur de **8 × 10⁻⁷ %**. Le test
`loweringTheFarPlaneChangesNothing` existe déjà et dit exactement cela dans l'autre sens —
il devient la **licence** du correctif au lieu d'une curiosité.

`nearPlane` n'est pas touché. Sa licence — « en vue spacecraft le contenu le plus proche
*est* le vaisseau, à l'origine » — reste vraie : une Lune lointaine ne rapproche rien.

### 5.2 La caméra

- `CameraTransitionAppState.spacecraftPivot:344` : `bodyPivot(target.parentBody())` devient
  `bodyPivot(point.arc().body())`. La méthode compose déjà « position du corps » + « offset
  arc-relatif » ; elle se trompait seulement de corps.
- `MissionRenderer.onSpacecraftSelected:184` : le corps parent vient du point courant, plus
  du contexte figé à la construction, avec repli sur ce contexte quand l'éphéméride n'est
  pas disponible — la même dégradation que `FloatingOriginAppState` accepte déjà.
- Le champ `MissionRenderer.renderContext` ne garde alors que son rôle d'**échelle**, et le
  javadoc que L3 §3.1 y avait laissé (« L5 est là où ce champ doit être revisité ») est
  honoré et retiré.

### 5.3 Le troisième viewport est tranché : non

Le pas de profondeur à 384 400 km reste de 88 000 km, **et on l'accepte**, parce que rien
n'est là pour être occulté : un seul globe est dessiné, dans la région de l'origine, où le
pas vaut 27 km. Le bout lointain du trait ne dispute la profondeur qu'à lui-même.

C'est la **question ouverte n° 4 de la roadmap tranchée sur une mesure**, et le §10.4 de
`spacecraft-view-artefacts.md` — « [reverse-Z, depth log et troisième viewport]
deviendront nécessaires le jour où la near viewport devra tenir Terre + Lune + vaisseau
dans le même cadre » — reçoit sa réponse : ce jour est arrivé, et il ne les demande pas,
parce qu'on ne tient pas *deux globes*, on en tient un et un trait. La question rouvrira
le jour où deux globes devront coexister (rendez-vous lunaire, `MIS-6`), et pas avant.

### 5.4 La visibilité

`FocusView.isMissionVisible` prend l'ensemble des corps d'arc de la trajectoire au lieu de
`objective.body()`. Un transfert lunaire est alors visible depuis la Terre (arc 1) comme
depuis la Lune (arc 2), et invisible depuis Mars.

Garder la règle actuelle la rendrait **activement fausse** et non seulement incomplète :
avec la bascule du §3.1, une mission lunaire (objectif `MOON`, arc `EARTH`) serait masquée
pendant toute son ascension terrestre et n'apparaîtrait qu'à la traversée de SOI.

---

## 6. Ce que L5 ne touche pas

| Site | Raison |
|---|---|
| `nearPlane`, le facteur spacecraft, le viewport far | §5.1, §5.3 |
| L'architecture « un corps garé sur l'origine » | §3.2 : elle devient le mécanisme |
| Le découpage du ruban, `RibbonMeshBuilder`, les couleurs de phase | §4 : rien à découper |
| La timeline | Insensible au repère depuis L3 §1 |
| Les étages, les forces, les propagateurs, les vingt sites de construction | Aucune physique dans ce lot |
| `MissionLoadEvaluator.objectiveMet:296` | Suppose un coast final d'un seul arc. L6 |
| `AchievedOrbit`, `PropellantBudget`, `MissionHorizon`, `Physics` | Terre-en-dur, réveillés en L6 (L1 §4.1) |
| RND-5 (repère d'affichage tournant) | §9 : la structure se rouvrira à ce moment-là |

### 6.1 La cohérence qu'on garde, et celle qu'on abandonne

**Gardée** : à l'intérieur du tracé — tableau et tête par la même source (§3.3), une seule
conversion, un seul corps de rendu par image.

**Abandonnée, explicitement** : la coïncidence au mètre entre le trait converti et la
position du globe, qui viennent de deux sources (Orekit contre le dataset interpolé). Le
§1.7 montre qu'elles ne sont jamais confrontées à l'écran : l'écart n'a aucun observateur.
C'est écrit comme une décision pour qu'un lecteur qui trouverait l'écart plus tard sache
qu'il a été vu.

---

## 7. Les tests

**7.1 `NearFrustumDepthTest`** — trois cas et un verdict dans le javadoc de la classe :
- *le plancher far relevé ne change rien* : le pas à 6 778 km est identique à 0,1 % près
  entre 100 000 et 500 000 km. Symétrique exact de `loweringTheFarPlaneChangesNothing`, et
  c'est la licence du §5.1 ;
- *la distance lunaire est couverte* : 384 400 < `FAR_MIN`. C'est l'assertion du défaut
  réellement corrigé ;
- *le pas à 384 400 km vaut 88 000 km, et c'est accepté* : épinglé avec sa raison, sur la
  discipline de L2 §5.1 qui a gardé les 7,3 × 10⁻⁶ en repère logué plutôt que de les taire.

**7.2 `TrajectoryPolylineTest`** — à un arc, `positionAt(i, EARTH)` rend le tableau
d'origine **par identité** (`assertSame`), pas par égalité : la preuve qu'aucun arrondi ne
peut entrer. À deux arcs synthétiques, l'aller-retour de conversion est exact. Un corps de
rendu étranger lève. Et `ArcRun.vertexCount()` de l'arc sortant contient sa frontière
(§4.1).

**7.3 `NearFrameOriginTest`** — L3 §6.3 y avait ajouté un cas d'arc lunaire prouvant que
l'annulation est aveugle au corps, en écrivant que ce test deviendrait « l'énoncé de ce que
L5 aura à changer, épinglé au lieu d'être raconté ». L'échéance tombe : le test doit
maintenant prouver que l'annulation tient **avec une tête convertie**.

**7.4 `FocusViewTest`** — la règle par ensemble d'arcs (§5.4).

**7.5 La conversion elle-même** — un point à l'origine sélénocentrique converti en
géocentrique rend la position de la Lune à cette date, au mètre ; et l'aller-retour est
exact, l'argument de L4 §7.3 s'appliquant mot pour mot (une translation pure n'a rien à
arrondir sur la position).

**7.6 Les deux gates** — `CentralBodyBaselineTest` à `0.0` sur ses 62 frontières et
`MissionPolylineBaselineTest` à l'identique, à chaque étape.

**7.7 Aucune capture d'écran, et c'est une décision.** Le découpage annonce en fermeture
« une capture d'écran comparée ». Mais comme L3 et L4, L5 ne produit aucun second arc :
il n'existe, à la fin de ce lot, aucune mission chargeable dans l'application qui exerce
quoi que ce soit du multi-arc à l'écran. Embarquer une mission de démonstration
déclarant `soiTransitions` donnerait la capture tout de suite — au prix de rompre la
règle 3 du découpage (« le nouveau comportement est opt-in jusqu'au dernier lot ») et de
mettre les deux gates en danger pour une raison d'affichage. **Le verdict visuel devient
donc un critère d'acceptation de L6**, où une vraie trajectoire lunaire existe.

---

## 8. Les alternatives écartées

**Deux globes 3D dans le même cadre.** Imposerait de délier `anchor3d` de l'origine near,
de retirer le masquage de `showBodySpatial`, et un troisième viewport ou un depth log, le
ratio far/near mesuré étant 3,8 × 10⁶ (§1.2). Écarté parce que le §5.3 montre qu'on peut
livrer la propriété visée sans rien de tout cela.

**Aucune conversion : visibilité par arc.** On ne dessine que les arcs du corps affiché.
Coût nul, risque nul — mais depuis la Terre il manque les 66 200 km finaux, soit 17 % du
trait et précisément l'extrémité qu'on veut voir.

**Une `Geometry` par arc, placée à la position courante de son corps.** Pas de conversion
par sommet, mais un arc n'est rigide que si son corps ne bouge pas pendant sa durée : faux
de jusqu'à 55 000 km sur les 54 h mesurées en L4 §11.2.

**Bascule du corps affiché par transition caméra animée.** Plus doux à l'œil, mais une
transition gèle délibérément mode et corps pendant ses 2,5 s : le tracé resterait dans
l'ancien repère 2,5 s après la traversée, et le scrubbing arrière la rejouerait mal.

**Bascule sur critère d'angle apparent**, indépendamment de l'arc. Découple ce qu'on voit
de ce qui est propagé, mais introduit un second seuil à calibrer et un instant où le globe
et le repère du tracé ne sont plus le même corps.

**Réécrire `FocusView.body` depuis la boucle de rendu.** §3.2.

---

## 9. Ordre d'exécution

1. **`FAR_MIN` et les trois tests de profondeur** (§7.1). Aucun autre code de production.
2. **Les N tableaux et `positionAt(int, SolarSystemBody)`**, trois sites appelants. Les
   consommateurs passent encore le corps de l'arc, donc rien ne change à l'écran. Gate L3
   identique **par identité du tableau unique**.
3. **`renderBodyOf`, les deux lignes de `FloatingOriginAppState`, la tête convertie, le
   ruban lisant le tableau du corps de rendu.** C'est là que la couture bouge réellement.
4. **La règle de visibilité** (§5.4).
5. **Le pivot caméra et `onSpacecraftSelected`** (§5.2).
6. **Les tests d'unité multi-arcs** (§7.2, §7.3, §7.5). Comme en L2 et L3, ils ne peuvent
   pas précéder l'API : ils testeraient du code qui ne compile pas.
7. **Suite complète.**

L'étape 2 est séparée de la 3 pour la raison qui a tenu en L1, L3 et L4 : si un sommet
bouge, savoir déjà que ce n'est pas le tableau.

---

## 10. Risques identifiés

**Le seul risque numérique est l'étape 3.** Si la tête et le tableau ne passent pas par la
même fonction de conversion, le premier sommet du ruban fait un coude avec la tête du
vaisseau — à l'endroit exact où l'œil regarde. `NearFrameOriginTest` (§7.3) l'attrape.

**Le coût de la conversion à la construction** : ≤ 8 192 × N évaluations Orekit par
mission, sur le thread de calcul de mission. `OrekitService` y est déjà initialisé, mais
le coût est **à mesurer, pas à supposer**.

**La mémoire** : ~400 Ko par corps d'arc supplémentaire et par mission.

**L'accesseur qui lève** sur un corps étranger est délibéré, et c'est un risque assumé :
un chemin de rendu qui demanderait un corps hors de la trajectoire échouerait bruyamment
au lieu de dessiner faux. La règle de visibilité du §5.4 est ce qui garantit que le cas ne
se produit pas ; si elle est contournée un jour, on veut l'exception.

**Ce que L5 n'a pas comme risque** : aucune physique, aucun propagateur, aucun étage.
Comme L2 et L4, la non-régression est structurelle et non mesurée — ici, l'identité du
tableau unique.

---

## 11. Ce que L5 laisse ouvert

- **L6** hérite du verdict visuel (§7.7), de `MissionLoadEvaluator.objectiveMet`, et des
  sites Terre-en-dur de L1 §4.1.
- **RND-5** (`trajectory-display-frame.md`) veut son propre second tableau de positions,
  en repère lié au corps. La forme naturelle devient alors « un tableau par (corps de
  rendu, repère d'affichage) », soit quatre tableaux à deux corps. La structure se rouvrira
  à ce moment-là ; elle n'est **pas** tranchée ici, et il ne faut pas construire
  l'abstraction par anticipation.
- **Le troisième viewport** reste écarté *pour ce cas* (§5.3), pas dans l'absolu.
- **La cohérence trait / globe** est abandonnée sciemment (§6.1).
- **`arcStart − 1`** est ajouté avec une raison neuve ; celle de L4 §5 est périmée (§4.1).

---

## 12. Fermeture — L5 est implémenté

Mesuré le **2026-08-17**, branche `feature_phy4_l5_biscale`, GraalVM 21.0.5.

### 12.1 Le verdict

| | |
|---|---|
| Suite par défaut | **799 tests, 0 échec, 0 erreur**, 17 sautés, 112 classes |
| Diff de production | **10 fichiers, +409 / −100** |
| Diff de test | 7 fichiers, +320 / −38 |
| Gate L1 `CentralBodyBaselineTest` | vert à chaque étape, 62 frontières à `0.0` |
| Gate L3 `MissionPolylineBaselineTest` | vert **à l'identique** à chaque étape |
| Étages de production déclarant une transition | **0**, donc un seul arc partout, donc un seul tableau |

Les sept étapes du §9 ont été livrées en **cinq** commits (cf. écart 1).

### 12.2 Cinq écarts au plan

1. **L'étape 5 a été livrée dans l'étape 3.** Le pivot caméra et `onSpacecraftSelected`
   appliquent la même substitution que le reste de l'étape 3 — le corps de l'arc à la place
   d'un corps figé — et les séparer aurait laissé, le temps d'un commit, un pivot caméra
   incohérent avec la scène near qu'il vise. Le plan les distinguait par prudence, pas par
   nécessité.

2. **Le §7.3 s'est trompé, et l'erreur remonte à L3.** L3 §6.3 avait écrit que son cas
   d'arc lunaire serait « ce que L5 aura à changer » ; le §7.3 de ce document l'a repris tel
   quel. **Il n'a pas eu à changer.** L5 ne touche pas à `toJmeBodyRelativePosition` : il
   convertit son *entrée*. L'aveuglement de l'adaptateur au corps reste donc vrai, reste
   utile — c'est lui qui rend l'annulation robuste au corps que le contexte nomme — et
   l'assertion L5 est venue **s'ajouter** au lieu de remplacer. Les deux tests cohabitent, et
   le second vérifie d'abord que la conversion déplace réellement le point, sans quoi il ne
   prouverait que l'identité que le premier couvre déjà.

3. **La vitesse n'est pas convertie, et le plan ne le disait pas.** Deux repères
   body-centrés à axes ICRF partagent leurs axes mais pas leur mouvement : une vitesse
   convertie pointe ailleurs. Or ce qu'elle pilote est l'attitude du modèle, qui appartient
   au repère dans lequel le véhicule vole, pas à celui dans lequel on choisit de le dessiner.
   Décidé à l'implémentation, écrit dans le code.

4. **La règle de visibilité est évaluée après la récupération de la trace**, plus avant :
   elle a besoin de `trail.arcBodies()`. Deux blocs de `MissionOrchestratorAppState.update`
   ont donc échangé leur ordre. Sans effet — la trace est un champ déjà construit — mais
   c'est une ligne de diff que le plan n'annonçait pas.

5. **Le coût de la conversion à la construction n'a pas été mesuré**, contrairement à ce
   que le §10 demandait, et c'est un manque à écrire. Il n'y avait rien à mesurer : aucun
   étage ne déclare de transition, le chemin rapide du §3.4 rend le tableau unique sans un
   seul appel Orekit, et une mesure prise sur une trace synthétique n'aurait rien dit du cas
   réel. **À faire en L6**, sur la première trajectoire à deux arcs.

### 12.3 Ce que L5 n'a pas eu besoin de faire

Aucune physique, aucun propagateur, aucun étage, aucun des vingt sites de construction de
L1. Aucun découpage du ruban (§4), aucun troisième viewport, aucun reverse-Z, aucun depth
log (§5.3), et pas une ligne touchée à `nearPlane` ni à l'architecture « un corps garé sur
l'origine » — laquelle s'est révélée être le mécanisme du lot plutôt que son obstacle.

Le seul risque numérique annoncé (§10) ne s'est jamais manifesté : la conversion de la tête
et celle du tableau passent par `TrajectoryArc.convertPosition` depuis le premier jet.

L6 peut démarrer, et il hérite du verdict visuel.
