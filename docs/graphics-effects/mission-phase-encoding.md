# Spec — Lecture des phases de vol sur la trajectoire d'une mission

Item roadmap : `RND-3`. Ce document **remplace** la description de
[`effects-roadmap.md`](effects-roadmap.md) §9.3.1 et §9.3.2, dont il corrige
deux erreurs de prémisse (§3).

## 1. Contexte

Une trajectoire de mission est aujourd'hui dessinée d'un seul tenant, dans une
couleur uniforme tirée d'une palette cyclique de 8 entrées.

- `ui/mission/MissionColorPalette.java:15` — les 8 couleurs, attribuées par
  mission et partagées entre le panneau d'affichage, la modale de gestion et la
  scène 3D. Elles portent donc **l'identité de la mission**.
- `states/mission/MissionTrajectoryRenderer.java:63` — un unique
  `Material` `Unshaded` avec `setColor("Color", …)`. Aucun `VertexBuffer`
  de couleurs.
- `simulation/mission/ephemeris/TrajectoryPolyline.java:39` — la forme
  dessinable ne transporte que `positions[]` et `times[]`. Le `stageName` que
  porte `MissionEphemerisPoint` est **perdu à la construction de la polyline**.
- `simulation/mission/ephemeris/MissionEphemerisGenerator.java:143` —
  `pointOf(MissionStage stage, SpacecraftState state)` a le `MissionStage` en
  main au moment où il fabrique le point.
- `simulation/mission/MissionStage.java:87` — `isPropulsive()` existe et est
  sémantique : c'est la seule classification de phase qui soit déjà stable.

Les noms d'étapes, eux, sont des chaînes libres décidées mission par mission —
`"Vertical Ascent"`, `"Gravity Turn"`, `"Coasting parking"`, `"GTO injection"`,
`"Trim"`, `"Plane trim"`, `"Coasting"` (`operation/GEOMission.java:145-173`,
`operation/LEOMission.java:129-232`). Une GEO en aligne une dizaine.

Résultat utilisateur : on ne voit pas où finit l'ascension verticale, où
commence la gravity turn, où le transfert s'allume.

## 2. Le fait qui commande tout le design

Les phases propulsées sont **négligeables en longueur d'arc** et **majoritaires
en nombre de sommets**. Les deux à la fois.

Ordre de grandeur pour une GEO — durées de phase plausibles, *non mesurées sur
un tir réel* (à confirmer par un dump d'éphéméride, cf. §7) :

| Phase | Durée (s) | Part |
|---|---|---|
| Ascension propulsée (vertical + gravity turn + insertion parking) | ~575 | 0,9 % |
| Coast parking | ~1 200 | 2,0 % |
| Injection GTO | ~300 | 0,5 % |
| Coast de transfert | ~19 000 | 31 % |
| Circularisation + trims | ~320 | 0,5 % |
| Coast final | ~40 000 | 65 % |

**Les poussées font ~2 % de la durée et moins de 1 % de la longueur d'arc
dessinée.** La conclusion est robuste à une large marge : même en multipliant
les durées de poussée par cinq, elles resteraient sous les 10 %.

Simultanément, l'échantillonnage est par phase — `BURN_SAMPLE_STEP = 1.0` et
`COAST_SAMPLE_STEP = 60.0` (`MissionStage.java:26` et `:35`). Le même profil
produit donc ~1 200 sommets de poussée contre ~1 000 de coast : **les poussées
occupent la majorité des sommets et une poignée de pixels.** Une GEO reste sous
`TrajectoryPolyline.MAX_POINTS`, donc rien n'est décimé et la couleur serait
parfaitement résolue — et pourtant invisible.

**Conséquence de design.** La couleur ne peut porter que ce qui est long : les
coasts. Tout ce qui est évènementiel — l'instant d'une transition, l'existence
d'une poussée — doit passer par un **marqueur discret**. Le marqueur n'est pas
un complément décoratif, c'est le canal principal.

## 3. Deux prémisses de `effects-roadmap.md` §9.3 à corriger

**§9.3.1 (« couleur par stage ») suppose une table `stageName → ColorRGBA`.**
Les `stageName` sont des chaînes libres, propres à chaque mission : une telle
table serait à étendre à chaque nouveau type de mission et casserait
silencieusement (retour à la couleur par défaut) sur une faute de frappe. §5.2
la remplace par une règle dérivée de la structure de la mission.

**§9.3.2 (« distinction passé / futur par l'alpha ») n'a rien à moduler.** Le
futur n'est pas dessiné. `MissionOrchestratorAppState.java:121` calcule
`upTo = trail.indexUpTo(now)` et `MissionTrajectoryRenderer.java:129` boucle
`for (i = 0; i <= last; i++)` : le trait est une traîne qui pousse derrière le
vaisseau, il n'y a rien devant lui à estomper. Dessiner la trajectoire planifiée
en avant du vaisseau est une fonctionnalité à part entière — probablement
précieuse sur un outil de planification — mais c'est un autre chantier. **Tant
qu'elle n'est pas prise, le canal alpha est libre**, ce qui laisse la
saturation et la luminosité entièrement disponibles pour les phases.

## 4. Objectif

Permettre deux lectures simultanées sur le trait, sans compromettre
l'identification de la mission :

1. **Évènementielle** — où commence chaque phase : un marqueur par transition.
2. **Régime** — poussée contre balistique, et progression du vol : une
   modulation continue de saturation / luminosité.

### Contrainte cardinale — l'identité de mission prime

Les 8 couleurs de `MissionColorPalette` servent à distinguer les missions entre
elles. Le codage de phase ne doit jamais rendre deux missions confusables.
D'où la règle : **la modulation ne fait que *monter* vers la couleur de
palette.** Le coast final — le segment le plus long, ~65 % du trait — vaut
exactement la couleur de la mission ; les coasts antérieurs sont progressivement
plus sourds. Une mission finit donc toujours par ressembler à son entrée de
palette, et c'est le segment dominant qui le fait.

### Critères de succès

- Sur deux missions de teintes voisines affichées ensemble, l'appartenance de
  chaque trait à sa mission reste immédiate.
- On distingue à l'œil le coast de parking du coast final d'une même mission.
- Chaque transition de phase franchie porte un marqueur, y compris celles des
  phases très courtes.
- Aucune régression de coût par frame (cf. §5.3).

## 5. Design

### 5.1 Plomberie — faire descendre la phase jusqu'au sommet

`TrajectoryPolyline` gagne deux membres :

- `short[] runOf` — index de *run* par sommet ;
- `List<PhaseRun> runs`, avec
  `record PhaseRun(String stageName, boolean propulsive, int firstVertex)`.

**Des runs, pas des noms distincts.** Un run est un segment *contigu* de même
étape. Deux étapes peuvent porter le même nom sans être le même segment de vol ;
une clé par nom les fusionnerait à tort. Le run est aussi exactement la
structure dont les marqueurs ont besoin : un marqueur par frontière de run.

Pour les construire il faut `isPropulsive` à la maille du point :
`MissionEphemerisPoint` gagne un composant `boolean propulsive`, rempli par
`MissionEphemerisGenerator.pointOf(…)` qui a déjà le `MissionStage`.
`MissionEphemeris` dérive les runs de ses tableaux parallèles et les passe à
`TrajectoryPolyline.of(…)`.

Six sites de construction du record sont à mettre à jour : `MissionEphemeris`,
`MissionEphemerisGenerator`, et quatre tests
(`MissionEphemerisDisplayPointTest`, `TrajectoryPolylineTest`,
`MissionLoadEvaluatorTest`, `MissionTrajectoryOriginTest`).

**Piège à traiter : la décimation peut avaler un run entier.** Une ascension
verticale de 15 s échantillonnée à 1 s fait 15 sommets ; sur un horizon long où
le stride monte à 20, elle disparaît du tracé — et son marqueur avec.
`TrajectoryPolyline.of(…)` doit garantir qu'**un run conserve au moins son
premier sommet**, quelle que soit la décimation. C'est le même genre de
troncature silencieuse que celle documentée dans le Javadoc de la classe.

### 5.2 La règle de couleur

Un objet pur, `ui/mission/MissionPhaseShading`, voisin de `MissionColorPalette`
(qui a déjà son test) :

```java
static ColorRGBA[] shade(ColorRGBA missionColor, List<PhaseRun> runs)
```

Une couleur par run, calculée une fois :

- **tout run dessinable, de rang *k*** : `lerp(missionColor, ANCHOR_MUTED, w)`
  avec `w = 0,22 × niveau(k)`, le niveau étant compté **à rebours depuis la fin
  du vol** et parcouru en **onde triangulaire** sur `0…3` : 0, 1, 2, 3, 2, 1, 0,
  1, … Le dernier run vaut donc `w = 0`, soit **exactement** `missionColor`.
- **run propulsé** : en plus, `lerp(base, ColorRGBA.White, 0,20)`.
- **run de moins de 2 sommets** : prend la nuance de son voisinage **sans
  consommer de rang**. `StageSeparationStage` est instantané ; le laisser prendre
  un rang décalerait toutes les phases suivantes d'un cran.

**Pourquoi tous les runs et pas les seuls coasts.** Une première version ne
classait que les coasts et donnait à toutes les poussées une nuance unique. Or
seuls `CoastingStage` et `StageSeparationStage` retournent `isPropulsive() ==
false` dans tout le codebase : **une LEO n'a qu'un seul vrai coast**, la règle y
produisait exactement deux couleurs, et l'ascension — la partie du vol qu'on
regarde le plus — était quatre ou cinq poussées consécutives dans un seul ton
plat. Constaté à l'écran, pas en test.

**Pourquoi un pas fixe et pas une rampe.** Une rampe étalée sur le nombre de
runs rétrécit le pas à mesure qu'une mission gagne des phases : une GEO à onze
runs deviendrait illisible précisément parce qu'elle a plus à dire. Un pas fixe
compté à rebours donne un contraste entre voisins **indépendant de la longueur
de la mission**. L'onde triangulaire plutôt que la dent de scie évite le saut du
ton nominal au plus sombre en plein milieu de la séquence — sur une LEO réelle il
tombe entre les deux gravity turns et se lit comme un artefact de rendu.

**Ce que ça coûte entre missions, et l'arbitrage retenu.** L'atténuation est une
contraction vers une ancre commune : élargir le pas achète de la lisibilité
*dans* une mission et dépense de la séparation *entre* missions. La paire la plus
serrée de la palette est `PALETTE[0]` (0,30 0,65 0,90) et `PALETTE[7]`
(0,25 0,80 0,75), distantes de 0,218 en RGB. L'arbitrage est explicite : le run
**final** de deux missions reste à la pleine séparation de palette — c'est lui
qui porte l'identité, et il n'est jamais modulé — tandis que leurs runs les plus
atténués, courts et précoces, peuvent descendre à 0,05. Le §6 épingle les deux
bouts de cet arbitrage.

Le rang du coast est dérivé de sa position dans la liste des runs — aucune
métadonnée nouvelle, aucun enum, aucune table de chaînes. La règle vaut telle
quelle pour une LEO (2 coasts) comme pour une GEO (3), et vaudra pour la
prochaine mission sans qu'on y touche.

**Pas de conversion HSL.** Un lerp vers un gris sombre désature *et* assombrit
d'un seul geste, ce qui est précisément le « sourd » recherché, en trois lignes
au lieu de deux conversions d'espace colorimétrique. Si le rendu ne tient pas à
l'œil sur les huit couleurs de la palette, on basculera sur une modulation
HSL — mais on commence par le simple, et le test du §6 est écrit pour arbitrer.

Le `stageName` reste porté par le run, mais pour **étiqueter** — pas pour
choisir une couleur.

### 5.3 Le buffer de couleurs

Sur `MissionTrajectoryRenderer` :

- un `VertexBuffer.Type.Color`, 4 composantes, `MAX_VERTICES * 4` flottants ;
- `mat.setBoolean("VertexColor", true)`, et `Color` mis à **blanc**. `Unshaded`
  multiplie les deux : garder la couleur de mission dans `Color` ferait du
  buffer un multiplicateur, incapable d'éclaircir au-dessus d'elle, alors qu'une
  poussée doit précisément le faire.

**Le buffer est écrit une seule fois, pas par frame.** Il ne dépend que de la
polyline, qui est immuable et partagée — « the same instance is handed out on
every call » (`MissionEphemeris.displayTrail()`). Un garde d'identité
`if (trail != boundTrail) { rebuildColors(trail); }` suffit, et se redéclenche
naturellement quand un edit du wizard régénère l'éphéméride.

Seul le sommet de tête est réécrit à chaque frame — un `float4`, puisque son
index varie avec `upTo`. **Le coût par frame reste celui d'aujourd'hui.**

### 5.4 Les marqueurs de transition

Une seconde géométrie par mission, `Mesh.Mode.Points`, une dizaine de sommets.

- **Seules les frontières franchies sont dessinées** (`firstVertex <= upTo`) :
  annoncer une phase que le vaisseau n'a pas atteinte contredirait la sémantique
  de traîne du trait. Le buffer de points est donc réécrit chaque frame — dix
  sommets, négligeable.
- **Même origine que la ligne.** Les marqueurs doivent emprunter exactement le
  chemin de conversion de `MissionTrajectoryRenderer.putVertex(…)` et porter la
  même translation de géométrie, sinon ils scintillent à côté du trait au lieu
  d'être dessus — c'est tout l'objet du §4 de
  [`spacecraft-view-artefacts.md`](spacecraft-view-artefacts.md).

Cette contrainte partagée fait des marqueurs un **collaborateur possédé par**
`MissionTrajectoryRenderer`, à qui celui-ci passe l'`origin` déjà calculée —
pas une classe sœur qui recalculerait la sienne.

#### Pourquoi `Points` et pas un billboard

**La taille de point est acquise, sans shader custom.** Vérifié dans le jar
`jme3-core-3.9.0-beta1` : `Common/MatDefs/Misc/Unshaded.j3md` déclare
`Float PointSize : 1.0` et le define `HAS_POINTSIZE`, `Unshaded.vert:37-38`
écrit `gl_PointSize = m_PointSize`, et `GLRenderer.java:720` active
`GL_VERTEX_PROGRAM_POINT_SIZE` **inconditionnellement** au démarrage sur
desktop. Ce n'est donc pas la loterie de pilote qu'est `glLineWidth`
([`effects-roadmap.md`](effects-roadmap.md) §9.1).

**L'argument décisif contre le billboard est la profondeur.**
`engine/scene/body/lod/BillboardIconView` est un `Container` Lemur attaché au
nœud **GUI**, projeté depuis la 3D et donc non depth-testé contre la scène. Un
marqueur situé de l'autre côté du corps central se dessinerait par-dessus la
planète, alors que le trait auquel il appartient disparaît correctement
derrière. Les nœuds d'une trajectoire passent derrière le corps en
permanence — c'est le cas nominal. Une géométrie `Points` dans `nearOrbitsNode`
hérite du depth-test de la ligne sans rien faire. Le profil de charge va dans le
même sens : `BillboardIconView` sert **une** icône par corps, une douzaine en
tout ; ici ce serait une dizaine de marqueurs par mission, reprojetés par frame.

**Ce que `Points` coûte.** Un point est un carré plat : `PointSprite` n'existe
plus dans le `Unshaded` de la 3.9, et `GL_POINT_SPRITE` n'est activé qu'en
profil non-core (`GLRenderer.java:722`) — donc pas de texture, pas de dot rond,
pas d'antialiasing des bords. Et `PointSize` étant un paramètre de matériau, la
géométrie n'a qu'**une seule taille de marqueur** ; distinguer visuellement la
phase courante demanderait une seconde géométrie.

**Repli si le carré déplaît à l'œil** : non pas le billboard GUI, mais un mesh
de quads dans le même nœud 3D — deux triangles et un `BillboardControl` par
marqueur, le depth-test étant préservé.

### 5.5 Hors périmètre

- **Étiquetage des marqueurs.** Un marqueur muet ne dit pas *quelle* phase
  commence. Le nom de la phase courante est déjà lisible dans le widget
  télémétrie (`ui/telemetry/TelemetryWidget.java:217` affiche `stageName`), ce
  qui suffit pour ce lot. Le survol 3D n'existe pas encore (item `NAV-5`,
  [`hover-effects.md`](hover-effects.md)) et une légende dans le panneau
  d'affichage est un chantier UI distinct.
- **Trajectoire future.** Cf. §3.
- **Largeur de trait variable par segment.** Impossible sans le ribbon
  billboardé (item `RND-4`) : `glLineWidth` est un état de géométrie, pas de
  sommet, et il est plafonné.

## 6. Tests

- **`MissionPhaseShadingTest`** — pur, sans scène JME (`ColorRGBA` est une
  classe de valeurs) :
  - le dernier run vaut exactement la couleur de mission ;
  - **contraste entre voisins ≥ 0,10**, vérifié sur une séquence d'ascension LEO
    *et* sur une GEO à onze runs. C'est la garantie qui manquait à la première
    version : elle n'avait qu'un test de lisibilité écrit sur un profil à trois
    coasts qu'une LEO ne produit jamais, donc il passait pendant que le rendu
    réel était plat ;
  - un run de moins de 2 sommets ne consomme pas de rang ;
  - à rang égal, la poussée est plus claire que le coast ;
  - **identité** : le run final de deux missions quelconques reste à ≥ 0,20 ;
  - **anti-confusion** : deux missions au même rang restent à ≥ 0,05. Volontairement
    plus faible que la forme « toutes paires » initiale, qui est ce qui avait forcé
    l'atténuation jusqu'à l'illisibilité.
- **`TrajectoryPolylineTest`** (existe déjà) — étendu :
  - les runs sont reconstruits à l'identique en l'absence de décimation ;
  - un run plus court que le stride conserve au moins un sommet ;
  - `runOf` reste parallèle à `positions` après décimation.

## 7. Reste ouvert

**Les durées de phase du §2 n'ont pas été mesurées.** Ce sont toujours des ordres
de grandeur. Un dump des `stageName` et des durées sur un run réel de
`GEOMissionOptimizationTest` les confirmerait. Le design ne change pas si elles
bougent d'un facteur 2 — mais si les poussées dépassaient 10 % de la longueur
d'arc, la priorité relative du marqueur et de la couleur mériterait d'être
rediscutée. Le rendu observé en application est cohérent avec l'estimation
(les segments de poussée sont bien minuscules), sans la valider pour autant.

**Le réglage fin du contraste attend le ribbon (`RND-4`).** Sur une ligne GL de
2 px la différence entre phases reste modeste, et une ligne fine et claire se
désature perceptivement quelle que soit sa couleur. Le paramètre à bouger est
`MUTING_STEP` ; le test de contraste entre voisins du §6 dira immédiatement si on
va trop loin dans l'autre sens.

**Étiquetage des marqueurs** — cf. §5.5, toujours hors périmètre.
