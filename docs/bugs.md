# docs/bugs.md — registre des bugs ouverts

Bugs constatés à l'usage, hors du périmètre d'un chantier en cours. Un item
sort d'ici soit corrigé, soit promu en item de roadmap quand il s'avère être un
chantier à part entière.

**Convention.** `BUG-n` dans l'ordre de découverte, jamais réattribué. Chaque
fiche dit ce qu'on observe, ce qu'on croit savoir, et ce qui reste à vérifier —
la frontière entre les deux derniers doit rester lisible.

| ID | Titre | Constaté | Statut |
|---|---|---|---|
| [`BUG-1`](#bug-1--jitter-du-billboard-et-de-lorbite-de-pluton) | Jitter du billboard et de l'orbite de Pluton | 2026-08-10 | Ouvert, non diagnostiqué |
| [`BUG-2`](#bug-2--sauts-de-la-skybox-au-zoom) | Sauts de la skybox au zoom | 2026-08-15 | Ouvert, piste identifiée |
| [`BUG-3`](#bug-3--orientation-des-modèles-3d-des-planètes) | Orientation des modèles 3D des planètes | 2026-08-15 | Ouvert, diagnostic à faire |
| [`BUG-4`](#bug-4--hover-des-widgets-non-uniforme) | Hover des widgets non uniforme | 2026-08-15 | **Promu** en `UI-7` le 2026-08-16 |
| [`BUG-5`](#bug-5--pop-du-modèle-3d-au-changement-de-focus) | Pop du modèle 3D au changement de focus | 2026-08-15 | Ouvert, mécanisme identifié |
| [`BUG-6`](#bug-6--plane-trim-employé-hors-de-son-enveloppe-par-lascension-polaire) | Plane trim employé hors de son enveloppe par l'ascension polaire | 2026-08-16 | Ouvert, mécanisme mesuré — **importance : mineure côté code, à trancher côté physique** |
| [`BUG-7`](#bug-7--les-gates-de-non-régression-tombent-quand-un-test-lunaire-les-précède-dans-le-même-jvm) | Les gates de non-régression tombent quand un test lunaire les précède dans le même JVM | 2026-08-18 | Ouvert, reproductible, piste identifiée — **fiabilité de l'instrument, pas de la physique** |
| [`BUG-8`](#bug-8--inclinaison-figée-invalidée-en-silence-par-un-changement-de-site) | Inclinaison figée invalidée en silence par un changement de site | 2026-08-20 | Ouvert, mécanisme identifié — **ergonomie, le modèle est sain** |
| [`BUG-9`](#bug-9--parkingcoaststagetest-teste-la-sémantique-davant-mis-4l6) | `ParkingCoastStageTest` teste la sémantique d'avant MIS-4/L6 | 2026-08-28 | Ouvert, reproductible — **régression de test, pas de production** |
| [`BUG-10`](#bug-10--reentryguard-inopérant-en-présence-de-traînée) | `ReentryGuard` inopérant en présence de traînée | 2026-08-30 | Ouvert — **sans impact avant PHY-2/MIS-10, aucun vol de production ne l'exerce aujourd'hui** |
| [`BUG-11`](#bug-11--loptimiseur-saute-les-coasts-que-le-vol-rejoue) | L'optimiseur saute les coasts que le vol rejoue | 2026-08-30 | Ouvert, mécanisme identifié — **traverse PHY-4 → MIS-4 → MIS-5 sans jamais être refermé** |
| [`BUG-12`](#bug-12--bande-morte-ε-de-franchissement-de-soi-jamais-calibrée) | Bande morte ε de franchissement de SOI jamais calibrée | 2026-08-30 | Ouvert, acceptée par verdict — **redevient un risque actif pour MIS-11** |
| [`BUG-13`](#bug-13--fenêtre-de-lancement-lunaire-refusée-sans-signal-à-lécran) | Fenêtre de lancement lunaire refusée sans signal à l'écran | 2026-08-30 | Ouvert — famille de `BUG-8`, sous-système distinct |
| [`BUG-14`](#bug-14--deux-portées-de-recherche-de-fenêtre-divergentes) | Deux portées de recherche de fenêtre divergentes | 2026-08-30 | Ouvert, non mesuré |
| [`BUG-15`](#bug-15--log-error-trompeur-de-depletionguard-sur-un-rejet-correct) | Log `ERROR` trompeur de `DepletionGuard` sur un rejet correct | 2026-08-30 | Ouvert — **cosmétique** |
| [`BUG-16`](#bug-16--t1-saturé-contre-sa-borne-sur-la-majorité-des-transferts-mesurés) | `t1` saturé contre sa borne sur la majorité des transferts mesurés | 2026-08-30 | Ouvert, mesuré — artefact de mur de boîte |
| [`BUG-17`](#bug-17--acceptablecost-mal-calé-depuis-lajout-du-terme-ergols-i7) | `acceptableCost` mal calé depuis l'ajout du terme ergols I7 | 2026-08-30 | Ouvert, correctif proposé non fait |
| [`BUG-18`](#bug-18--rejets-de-scénario-au-chargement-seulement-journalisés) | Rejets de scénario au chargement, seulement journalisés | 2026-08-30 | Ouvert — **trou connu de `UI-3`** |

---

## BUG-1 — Jitter du billboard et de l'orbite de Pluton

**Constaté.** Depuis la vue solaire, cliquer sur Pluton. Pendant l'approche puis
une fois arrivé, son icône et sa ligne d'orbite tremblent d'une frame à l'autre.

**Ce n'est pas un bug du chantier caméra** (`NAV-1`, résolu). La transition ne
fait que maintenir la vue assez longtemps sur une configuration où le problème
est visible ; le même défaut doit être atteignable à la molette, et le tremblement
persiste après l'arrivée. À vérifier en premier, justement : reproduire **sans**
transition, en zoomant manuellement sur Pluton depuis la vue solaire. Si le
tremblement est là aussi, la piste ci-dessous tient ; sinon elle est fausse et
il faut repartir de zéro.

### Piste principale : précision du `float` dans le repère far

Le repère far stocke tout en unités solaires (1 unité = 1e9 m), en `float`. Le
quantum de représentation dépend donc de l'éloignement au Soleil, et Pluton est
le corps où il est le plus grossier :

| Corps | Distance au Soleil | ulp du `float` | Cadrage à l'arrivée (5 rayons) | Quantum / cadrage |
|---|---|---|---|---|
| Terre | ~150 unités | 2⁻¹⁶ ≈ 15 km | 31 855 km | 0,05 % |
| Pluton | 5900 à 7400 unités | 2⁻¹¹ ≈ 488 km | 5 942 km | **~8 %** |

Même code, même chemin de conversion : c'est un problème de **magnitude**, pas
de logique. Pluton cumule les deux extrêmes — le corps le plus lointain et l'un
des plus petits rayons du catalogue (1188 km, cf. `PlanetRadius`), donc le
cadrage le plus serré.

**Pourquoi ça tremble au lieu d'être un simple décalage fixe.** La position
rendue vaut `anchor.localTranslation + farRoot.localTranslation`, et les deux
sont réécrites à chaque frame depuis des valeurs qui bougent
(`PlanetPoseAppState`, `FloatingOriginAppState`). L'arrondi des deux termes
change donc à chaque frame, et le résidu se déplace. C'est aussi pour ça que le
modèle 3D de Pluton, lui, ne tremble pas une fois focalisé :
`FloatingOriginAppState` pose exactement l'opposé du `localTranslation` du corps
focus, donc la somme retombe à zéro **exactement**. Tout le reste hérite du
bruit.

**Pour l'orbite en particulier**, le recentrage n'y peut rien : les sommets sont
figés en héliocentrique au moment de la construction —
`OrbitLineFactory` les convertit par
`RenderTransform.toRenderUnitsJmeAxes(pHelio, null, RenderContext.solar())`, donc
un sommet d'orbite plutonienne est un `float` de magnitude ~6000 qui a déjà perdu
ses bits de poids faible. Translater le nœud parent ensuite ne les rend pas.

### Deux directions à explorer

1. **Stocker la géométrie far relative au corps focus** plutôt qu'héliocentrique,
   et la reconstruire au changement de focus. C'est ce que le near viewport fait
   déjà pour les trajectoires de mission via `RenderContext.planet(body)`. Coûteux
   (rebuild à chaque changement de focus), mais c'est la seule qui règle le
   problème à la racine plutôt que de le repousser d'une décade.
2. **Garder la double précision jusqu'au décalage** et ne convertir en `float`
   qu'après soustraction de l'origine courante. `RenderTransform` travaille déjà
   en `Vector3D` ; c'est la conversion finale qui arrive trop tôt pour les sommets
   d'orbite. Moins invasif, mais ne résout rien pour un corps qu'on regarde sans
   l'avoir focalisé.

### À regarder

- `engine/scene/OrbitLineFactory.java` — construction des `FloatBuffer` de sommets
  (lignes ~66, ~130, ~188 : les trois chemins de conversion).
- `engine/scene/body/lod/BillboardIconView.updateScreenPosition` — projection de
  l'icône, qui lit `anchor.getWorldTranslation()`.
- `states/camera/FloatingOriginAppState` — la négation exacte qui explique
  pourquoi le corps focus est le seul épargné.

### Non vérifié

Tout ce qui précède est un raisonnement sur les magnitudes, pas une mesure. Rien
n'a été instrumenté. Avant de coder quoi que ce soit : loguer sur quelques frames
la position monde de l'ancre de Pluton et celle d'un sommet de son orbite, et
confirmer que l'amplitude du tremblement est bien de l'ordre de 500 km et non de
plusieurs milliers — auquel cas la cause serait ailleurs.

---

## BUG-2 — Sauts de la skybox au zoom

**Constaté.** À la molette, le fond étoilé saute — il change d'échelle sur une
frame puis revient. La scène, elle, ne bouge pas anormalement.

### Piste principale : un invariant de frustum cassé, et un ordre d'update qui l'expose

`SkyboxAppState` n'a pas de champ de vision à lui : il le **recalcule** à chaque
frame depuis le couple `(frustumTop, frustumNear)` de la caméra far
(`SkyboxAppState.java:75-78`). Ce calcul n'est juste que si les deux membres du
couple ont été écrits ensemble, ce que seul `setFrustumPerspective` garantit.

Or `OrbitCameraAppState.updateFrustum()` (`:393-423`) n'écrit **que** `near` et
`far`, par `setFrustumNear` / `setFrustumFar`, et laisse `frustumTop` à sa valeur
précédente. Entre cet appel et le `setFrustumPerspective` de `updateAdaptiveFov()`
(`:426-433`), la caméra far porte un couple incohérent — sa FoV réelle vaut
`2·atan(top_ancien / near_nouveau)`.

Le zoom est justement le seul chemin qui laisse cette fenêtre ouverte au-delà de
l'appel : dans `onAnalog`, la branche molette (`:266-271`) appelle
`updateFrustum()` **sans** `updateAdaptiveFov()`. Et les événements d'entrée sont
distribués avant tout `AppState.update()` de la frame.

**Ordre d'attache = ordre d'update**, et c'est là que ça se joue :

| État | Attaché | Lit le couple |
|---|---|---|
| `SkyboxAppState` | `OrbitLabApplication:104` | **avant** réparation |
| `OrbitCameraAppState` | `:143` | c'est lui qui répare |
| `NearCameraSyncAppState` | `:214` | après réparation |

D'où le symptôme : le ciel est le **seul** consommateur situé du mauvais côté, et
donc le seul à sauter. La caméra far, elle, est réparée avant le rendu.

**Amplitude attendue.** `near = d · nearFactor`, et un cran de molette multiplie
`d` par `exp(±0,12)` (`OrbitCameraConfig:68`). À `top` constant, `tan(halfFov)`
est donc multiplié par ~1,13 : à 15° de FoV, environ 1,7° de trop sur une frame.
Un scroll rapide compose — plusieurs événements tombent dans la même frame, cinq
crans donnent `×0,55` sur `near`, soit une FoV presque doublée. Ce n'est plus une
respiration, c'est un saut.

### Confirmer en cinq minutes

Loguer le `fovYDeg` calculé dans `SkyboxAppState.update` et celui appliqué dans
`updateAdaptiveFov`. Sur une frame où la molette a tourné, les deux doivent
diverger ; hors molette, ils doivent être égaux. **S'ils sont toujours égaux, la
piste est fausse** et il faut repartir de la projection du mesh de ciel.

### Corrections, par ordre de préférence

1. **La racine** : fusionner `updateFrustum` et `updateAdaptiveFov` en une seule
   méthode qui se termine toujours par `setFrustumPerspective`. L'invariant à
   poser est : *la caméra far n'est jamais observable avec un `near` sans son
   `top`.* Toute correction qui ne le pose pas laisse le prochain consommateur
   retomber dedans.
2. **Le rattrapage** : appeler `updateAdaptiveFov()` dans la branche molette de
   `onAnalog`. Une ligne, mais l'invariant reste implicite.
3. **En complément** : attacher `SkyboxAppState` après `OrbitCameraAppState`,
   pour la raison déjà écrite en commentaire pour `MissionOrchestratorAppState`
   et `PlanetHudMarkersAppState` (`OrbitLabApplication:122-132`). Supprime aussi
   la latence d'une frame que le ciel traîne en permanence.

### Deuxième question, indépendante du bug

**Le ciel doit-il suivre la FoV du tout ?** C'est un choix délibéré
(`SkyboxAppState.java:25-27`), et il a un coût : la FoV adaptative va de 15° à
60°, donc les étoiles grossissent d'un facteur 4 sur la plage de zoom. *Eyes on
the Solar System* garde un fond fixe. Si le défaut est encore visible avec un
zoom lent et régulier — donc sans le saut d'une frame décrit plus haut — alors
c'est de ça qu'il s'agit, et aucune des trois corrections ci-dessus n'y changera
quoi que ce soit. À départager **avant** de coder.

### Non vérifié

Rien n'a été mesuré : tout ce qui précède est une lecture de l'ordre d'appel. En
particulier, la valeur d'un cran de molette est supposée valoir ~1 après le
snapping (`OrbitCameraAppState:334-337`) ; si le pilote envoie de petits deltas
qui passent sous ce seuil, l'amplitude réelle est plus faible que l'estimation.

---

## BUG-3 — Orientation des modèles 3D des planètes

**Constaté.** Sauf la Terre, les planètes ne sont pas correctement orientées.
Référence visuelle : *Eyes on the Solar System* (NASA).

### Ce qui est déjà juste, et donc n'est pas la cause

La rotation appliquée vient de `icrf.getTransformTo(bodyFrame, t)`
(`ChunkComputerV1.java:156`) : c'est l'orientation IAU réelle — pôle et méridien
origine — pas une approximation à taux constant. L'obliquité, le sens de rotation
et la précession sont **dans la donnée**. Inutile de les chercher ailleurs.

### Ce qui est suspect : une correction de maillage unique pour onze modèles

Toute la chaîne n'applique qu'une seule correction, globale :
`RenderTransform.meshCorrectionQ` (`:21-22`), +90° autour de X, composée à droite
dans `toRenderQuaternion` (`:43-44`). `AssetFactory.loadModel` (`:59-63`) ne fait
qu'une mise à l'échelle, et `BodyRenderConfig` n'a aucun champ d'orientation :
**il n'existe aucun endroit où un corps peut corriger son propre maillage.**

Une correction unique n'est juste que si les onze GLTF partagent exactement la
même convention d'axes *et* le même méridien origine. Cette hypothèse n'a jamais
été vérifiée, et elle tombe dès qu'un export Blender a coché ou décoché « +Y up ».
La Terre est probablement simplement le modèle sur lequel la constante a été
réglée.

### Diagnostic à faire avant d'ouvrir Blender

Deux défauts distincts, qui ne se corrigent pas au même endroit :

- **(a) axe de rotation faux** — le corps tourne autour d'un axe qui n'est pas son
  pôle. Témoins immédiats : le plan des anneaux de Saturne, et les bandes de
  Jupiter, qui doivent être perpendiculaires à l'axe. Uranus (98°) et Vénus
  (rétrograde, 177°) confirment ou infirment le cas limite. C'est un problème
  d'axe *up* du maillage.
- **(b) axe juste, longitude fausse** — le corps tourne bien mais la texture est
  décalée en longitude. **Test sans ambiguïté : la Lune est en rotation
  synchrone.** Si sa face visible n'est pas tournée vers la Terre, l'offset de
  méridien du maillage est faux.

Établir la liste « corps → défaut (a) ou (b) » est la première chose à faire ;
elle décide de tout le reste.

### Tips

- **La boucle d'itération.** Ré-exporter depuis Blender à chaque essai coûte trop
  cher pour chercher onze valeurs. Ajouter d'abord un champ de correction
  optionnel par corps (un `Quaternion` dans `BodyRenderConfig`, défaut = la
  constante actuelle), trouver les valeurs empiriquement, **puis** décider : les
  figer dans le code, ou les cuire dans les `.gltf` et remettre les surcharges à
  l'identité. Les deux fins se valent ; ce qui ne va pas, c'est de chercher les
  valeurs par ré-export.
- **Attention au dépôt.** `src/main/resources/models/` est gitignored (cf.
  `CLAUDE.md`). Une correction faite *uniquement* dans Blender n'est pas
  versionnée : elle sera perdue au prochain poste, et le prochain modèle ajouté
  réintroduira le bug en silence. Si c'est la voie retenue, documenter la
  convention attendue (en repère body-fixed, avant `meshCorrectionQ` : +Z = pôle
  nord, +X = méridien origine) est le minimum.
- **Sanity check côté code, à faire en premier parce qu'il coûte dix minutes.**
  La rotation stockée est `ICRF → body`, et elle est appliquée telle quelle au
  maillage (`PlanetPresenter:63`). Orienter un objet dans le monde demande la
  transformation inverse. Si la convention Hipparchus/JME ne compense pas ce sens
  quelque part, **toutes** les planètes tournent à l'envers — indétectable sur
  une sphère texturée sauf à regarder le sens de défilement. Vérifier que la
  Terre tourne bien d'ouest en est avant de conclure que le défaut est
  exclusivement dans les assets.

### Non vérifié

Aucune observation corps par corps n'a été faite. « Sauf la Terre » est le
constat de l'utilisateur, pas un relevé : il se peut qu'un ou deux autres corps
soient corrects, et cela renseignerait directement sur les exports.

---

## BUG-4 — Hover des widgets non uniforme

> **Promu en item de roadmap le 2026-08-16** — [`UI-7`](roadmap/01-roadmap.md#ui-7--tooltips-sur-les-contrôles-et-le-socle-de-survol-qui-les-porte--3-2-m-ajout),
> qui livre le socle de survol partagé réclamé ci-dessous et les infobulles comme
> premier client de ce socle. La fiche reste ici parce que c'est elle qui porte
> l'état des lieux ; le « à faire » est désormais dans la roadmap. Conformément à
> la convention en tête de ce document, cet item ne sortira du registre qu'une
> fois `UI-7` livré.

**Constaté.** Les labels et boutons ne réagissent pas de la même façon au survol
— couleurs, éléments affectés, présence même d'un retour visuel.

> **À ne pas confondre** avec `docs/graphics-effects/hover-effects.md`, qui traite
> du survol des *planètes et de leurs orbites* dans la scène 3D. Ici il s'agit
> uniquement des widgets Lemur de `ui/`.

### État des lieux

Vingt-deux fichiers de `ui/` posent leurs propres listeners
(`MouseEventControl` / `CursorEventControl.addListenersToSpatial`), pour au moins
quatre mécaniques différentes :

| Mécanique | Exemples |
|---|---|
| Échange de texture de fond | `DisplayRowIcons:53-78`, `ModeSegmentedControl:110-130`, `PaginationBar:130`, `ConfirmDialog:163-170` |
| Teinte d'un `TbtQuad` existant | `AppMenu:344-345`, `MissionRow:107-113`, `PopupList:170-176` |
| Remplacement par le fond d'un *autre* état | `PopupList:98-102` — le trigger prend `inputFocusBg` au survol |
| `highlightColor` du style Lemur | `FormStyles:152` (`ACCENT_BRIGHT`), `BreadcrumbStyles:88` (`TEXT_PRIMARY`), `TimelineStyles:174` (`TL_CYAN`) |

Et les valeurs divergent là où elles devraient coïncider : le blanc à 0,18 d'alpha
est recopié dans trois fichiers (`AppMenu:82`, `DisplayRow:29`, `MissionRow:36`),
la même idée vaut 0,33 dans `PopupList:171`, et trois `highlightColor`
différentes cohabitent. La règle des trois copies
(`docs/dette-technique.md` §6.3) est déjà franchie.

### Les divergences qui se voient à l'usage ne sont pas les couleurs

- **L'état désactivé.** `AppMenu` recalcule tout par `refresh()` et refuse le
  hover quand `enabled` est faux (`:344`) ; `ModeSegmentedControl` sort avant même
  de poser le listener (`:105-108`), donc un segment désactivé n'a *aucun* retour.
  Deux réponses différentes à la même situation.
- **Le texte.** Certains widgets changent la couleur du texte au survol, d'autres
  seulement le fond.
- **L'allocation.** Plusieurs `mouseEntered` construisent un composant de fond
  neuf à chaque événement (`UiKit.missionsFlat(...)`, `new ColorRGBA(...)`).
  Au-delà du déchet, c'est ce qui fait perdre les réglages posés au build (margin,
  insets) — d'où les micro-décalages d'un pixel au survol.
- **Le curseur.** Aucun widget n'en change. Rien ne distingue un label cliquable
  d'un label décoratif tant qu'on ne l'a pas survolé.

### Direction

Ce n'est pas une série de bugs à corriger un par un : c'est un composant qui
manque. Un helper unique (`UiKit.hoverable(...)`, ou un `HoverSkin` encapsulant
les trois mécaniques) plus des jetons dans `FormStyles` (`HOVER_TINT`,
`SELECT_TINT`, `HOVER_TEXT`), puis migration des sites.

**Poser le contrat avant de coder** : quels états existent (idle / hover /
sélectionné / désactivé / focus), lesquels s'excluent, et ce que chacun modifie
(fond, texte, icône, curseur). Sans ce tableau écrit, l'uniformisation reproduira
les mêmes divergences avec de nouvelles valeurs.

Indicateur de fin de chantier commode : le nombre de fichiers posant leur propre
listener de hover doit tomber de 22 à 1.

### Non vérifié

La liste ci-dessus vient de la lecture du code, pas d'un passage en revue à
l'écran. Une capture de chaque widget dans ses cinq états manque — c'est elle qui
dira lesquelles de ces divergences se voient réellement, et donc lesquelles
méritent d'être traitées en premier.

---

## BUG-5 — Pop du modèle 3D au changement de focus

**Constaté.** En changeant d'objet focus, le modèle 3D du corps visé apparaît
d'un coup au moment où il passe dans la near view, au lieu de grossir
continûment.

### Ce n'est pas un bug de LOD : c'est la conséquence d'un verrou

L'hystérésis de `LodView` (`:114`, bande 6↔10 px) ne joue aucun rôle ici — elle
est court-circuitée par le veto `allow3d`. Deux mécanismes se conjuguent :

1. `PlanetHudMarkersAppState:71` passe `allow3d = (body == focusView.getBody())`,
   c'est-à-dire le corps **source** pendant toute la transition. Le corps visé
   reste sur son icône de 16 px (`BillboardIconView:27`) jusqu'à la dernière
   frame.
2. `FloatingOriginAppState:82` appelle `showBodySpatial(view.getBody())`, qui cule
   tous les ancres near sauf un (`SceneGraph:95-100`). Même si le LOD
   l'autorisait, le modèle ne serait pas dessiné.

Et le basculement du focus est atomique, sur la dernière frame de la transition
(`CameraTransitionAppState.finish`, `:220-234`). À cet instant la caméra est déjà
à 5 rayons du corps (`:52`) : on passe donc, **en une frame**, d'une icône de
16 px à un disque de plusieurs centaines de pixels. C'est exactement le pop.

**Pourquoi on ne peut pas simplement lever le verrou.** Les ancres near ne sont
jamais translatés — `LodView.setPositionWorld` (`:61-63`) ne pose la position que
sur `farAnchor`, et le modèle 3D vit à l'origine de la near view. Ce viewport ne
peut donc afficher qu'un seul corps : celui sur lequel le repère est centré.
Autoriser la destination à s'y afficher pendant l'approche la dessinerait *à la
place* de la source. C'est la même contrainte qui fait que
`FocusView.isMissionVisible` ignore délibérément la destination alors que
`isSatelliteVisible` la prend en compte (`FocusView:205-216`).

### Trois directions, du moins au plus cher

1. **Cosmétique.** Garder l'icône et faire monter le modèle en échelle et en alpha
   sur ~150 ms après le basculement, au lieu d'un cull binaire. Ne corrige pas la
   cause — le modèle apparaît toujours trop tard — mais supprime la
   discontinuité. Le plus rapide, et suffisant si le reproche est esthétique.
2. **Basculer le repère à mi-parcours** plutôt qu'à la fin. `FocusView` connaît
   déjà la destination (`pendingBody`, `:35-37`). Il faut passer la propriété de
   la near view à la destination une fois la caméra plus proche de l'arrivée que
   du départ, **et** ré-exprimer le pivot `from` de la transition dans le nouveau
   repère (translation de la différence des deux positions), faute de quoi
   l'interpolation saute de toute la distance entre les deux corps. Corrige la
   cause, mais touche le cœur de la transition : même prudence que sur `NAV-1`.
3. **Donner une position aux ancres near**, pour que plusieurs corps du même
   système cohabitent dans la near view. Tenable pour la Lune (3,84e5 unités km),
   pas pour une autre planète — le far plane near est plafonné à 1e8
   (`NearCameraSyncAppState:42`). Réglerait au passage « la Lune n'a pas de modèle
   3D quand on regarde la Terre », mais c'est un chantier, pas un correctif.

### À vérifier d'abord — deux mesures, dix minutes

- **Le pop est-il exactement synchrone avec la fin de la transition ?** Un décalage
  de quelques frames désignerait un second effet, indépendant : l'ancre near est
  culé (`CullHint.Always`) depuis le démarrage, donc les textures du modèle ne
  sont téléversées sur le GPU qu'à la première frame où il est dessiné. Ça se
  corrige seul, par un `renderManager.preloadScene(...)` au chargement
  (`PlanetPoseAppState:145-158`).
- **Le pop existe-t-il aussi sans transition ?** À la molette, sur le corps déjà
  focus, le veto est vrai en permanence et seule l'hystérésis joue. Si ça pope
  quand même, le seuil 6↔10 px est trop haut — troisième cause, indépendante des
  deux autres.

### Non vérifié

Aucune instrumentation. Ce qui précède est une lecture de l'ordre d'exécution,
pas une observation frame par frame.

---

## BUG-6 — Plane trim employé hors de son enveloppe par l'ascension polaire

**Constaté.** En mesurant la baseline L0 de PHY-4
([`multi-corps/02-baseline-L0.md`](multi-corps/02-baseline-L0.md) §5.6), le
`AnalyticPlaneTrimAtNodeStage` de `PolarCoverageTest` dépense **1 028 m/s et
10 349 kg** — 26 % de la masse — pour un changement de plan de 3,24°, et fait
tomber l'excentricité de 0,206 à 0,137 au passage.

### Le mécanisme, mesuré

La vitesse visée par l'étage est construite comme `nIdeal × rNode`, donc
**perpendiculaire au rayon** : purement transverse. Faire tourner vers elle une
vitesse qui possède une composante radiale conserve `|v|` — donc le demi-grand
axe — mais augmente `|h| = r·v_transverse`, donc abaisse `e`. La manœuvre
circularise en même temps qu'elle tourne, et paie les deux.

| | avant trim | après trim |
|---|---|---|
| demi-grand axe | 7 867 901 m | 7 865 032 m (préservé à 0,036 %) |
| excentricité | 0,2061 | 0,1369 |
| périgée × apogée | −131 589 × 3 111 118 m | 410 084 × 2 563 706 m |

Une rotation pure de 3,24° coûterait `2·v·sin(1,62°)`, soit 350 à 462 m/s. La
rotation totale déduite de `2·v·sin(Ψ/2) = 1028` vaut Ψ ≈ 7,4° : 3,24° de plan,
et ≈ 6,6° en quadrature — l'angle de pente que la manœuvre aplatit. Les 10 349 kg
donnent une Isp implicite de 347,8 s : le ΔV logué est réel, pas un plan non tenu.

Le javadoc de la classe affirmait l'inverse (« changes neither the orbit's energy
nor its shape »). **Corrigé le 2026-08-16** — la classe dit maintenant ce qu'elle
fait. C'est la seule partie de cet item qui soit close.

### Importance : mineure côté production, moyenne côté fidélité du test

**Aucun chemin de production n'est concerné, et c'est le cœur de l'évaluation.**
`EarthOrbitMission.ascentThen` insère le plane trim **après** les phases
orbitales (`EarthOrbitMission.java:349-359`) : en vol réel, il s'exécute sur une
orbite déjà circularisée, où la vitesse *est* transverse et où l'effet
disparaît. Les mesures le confirment sur les deux profils qui l'exercent
réellement :

| Profil | Contexte du trim | Coût |
|---|---|---|
| GEO | après circularisation d'apogée | **3,3 kg, 4,2 m/s** |
| MEO | résidu 0,0026° | **sauté** |
| Polaire (`PolarCoverageTest`) | après l'ascension seule, e = 0,21 | **10 349 kg, 1 028 m/s** |

C'est donc la **fixture** qui est hors enveloppe, pas l'étage :
`PolarCoverageTest.trimPlane` appelle `propagateStandalone` directement sur
l'état de fin d'ascension, en sautant les phases orbitales que la mission réelle
vole avant. La démonstration qualitative de T5 reste valide — le trim fait bien
passer la couverture de 86,9° à 89,9°, et c'est ce que MIS-7 voulait montrer —
mais **le coût qu'elle affiche n'est pas celui d'une mission polaire réelle**.

Le risque concret, et la raison de tracer l'item : quelqu'un lit les 10 349 kg de
la ligne polaire de la baseline comme un coût de mission. C'est faux d'un facteur
qui reste à établir.

### Correction, et pourquoi elle ne doit pas être faite maintenant

Faire voler les phases orbitales à la fixture avant le trim la remettrait dans
l'enveloppe de production. C'est un effort faible.

**Mais cela déplacerait les chiffres polaires de la baseline L0**, qui vient
d'être enregistrée et sur laquelle les lots L1 à L6 de PHY-4 vont s'appuyer
pendant des semaines. Corriger maintenant, c'est bouger la référence en cours de
chantier — exactement ce que le §3 du découpage interdit. À faire après PHY-4,
ou pendant, mais alors avec un ré-enregistrement explicite et daté de la
baseline.

### Non vérifié

- **Le coût réel d'un plane trim polaire en production n'a pas été mesuré.** Il
  faudrait faire tourner une mission polaire complète via `EarthOrbitMission` et
  lire la ligne « Plane trim » de son rapport. Tant que ce n'est pas fait, on
  sait que 10 349 kg est faux, pas ce qui est juste.
- **L'angle de pente au nœud n'a pas été lu directement**, il est déduit de la
  décomposition en quadrature du ΔV. La déduction est cohérente avec `e = 0,21`,
  elle n'est pas une mesure.
- **Le seuil `SKIP_PLANE_ERROR_RAD` (0,03°) n'a pas été réexaminé** à la lumière
  de ce qui précède : rien ne dit qu'un seuil pensé pour un résidu de
  circularisation convienne à un résidu d'ascension.

---

## BUG-7 — Les gates de non-régression tombent quand un test lunaire les précède dans le même JVM

**Constaté le 2026-08-18**, pendant PHY-4 / L6. `CentralBodyBaselineTest` et
`MissionPolylineBaselineTest` échouent **de façon déterministe** — trois
assertions rouges — quand `SoiCrossingDetectorTest` s'exécute avant eux dans le
même JVM :

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew cleanTest test --tests "*SoiCrossingDetectorTest*" --tests "*CentralBodyBaselineTest*" --tests "*MissionPolylineBaselineTest*"
```

3 échecs sur 3 exécutions. Les écarts sont de dernier bit, là où L1 §5.5 exige
`0.0` :

| Grandeur | Attendu | Obtenu | Écart |
|---|---|---|---|
| `x` (LEO-400, étage `Trim`) | 3 129 196,368 272 180 m | 3 129 196,368 271 845 m | 3,3 × 10⁻⁷ m |
| `t` (même frontière) | 8 546,404 567 668 282 s | 8 546,404 567 668 333 s | 5,1 × 10⁻¹⁴ s |

**Chacun des deux gates passe seul, et la suite complète passe** (`./gradlew
test` : 808 tests verts), parce qu'elle fork plusieurs JVM et sépare les
protagonistes. Le défaut n'est donc visible que sur une sélection partielle —
c'est-à-dire exactement dans la boucle de travail quotidienne.

**Ce n'est pas PHY-4 / L6 qui l'introduit.** Reproduit à l'identique sur `main`
au commit `08ac325`, antérieur au lot. C'est L4 qui a amené le test lunaire ; le
défaut dormait depuis.

### Piste principale : les caches temporels partagés d'Orekit

`SoiCrossingDetectorTest` propage jusqu'à six jours avec la Lune en
perturbateur, sur une plage de dates qu'aucun autre test ne visite. Orekit sert
l'éphéméride JPL et l'historique EOP à travers des `GenericTimeStampedCache`,
qui choisissent un créneau et interpolent depuis les N échantillons voisins. Une
propagation terrestre ultérieure peut alors interpoler **depuis un voisinage
différent**, ce qui déplace le résultat du dernier bit — puis l'intégration
amplifie ce bit sur la durée du vol.

C'est cohérent avec l'amplitude observée (5 × 10⁻¹⁴ s sur 8 546 s, soit 6 ×
10⁻¹⁸ en relatif) et avec le fait que **seul l'ordre d'exécution** change quoi
que ce soit. Ce n'est pas vérifié.

### Ce qu'il faut trancher

Trois issues, et le choix n'est pas évident :

1. **Isoler les gates** — `@Isolated` de JUnit, ou un `forkEvery` / une tâche
   Gradle dédiée. Ne touche à aucune assertion, mais rend la propreté du gate
   dépendante d'une configuration de build.
2. **Donner aux gates une tolérance minuscule et justifiée.** L1 §5.5 a choisi
   `0.0` **délibérément** et a écrit que toute exception devait être écrite
   plutôt qu'absorbée : cette option demande donc sa propre entrée dans
   `docs/multi-corps/`, pas seulement un littéral changé.
3. **Réinitialiser les caches concernés entre tests**, s'il existe un moyen
   supporté de le faire.

### Non vérifié

- **Le mécanisme.** La piste ci-dessus est cohérente, elle n'est pas démontrée.
  Le confirmer demanderait d'instrumenter le cache ou de reproduire l'effet avec
  deux propagations nues, sans le reste du harnais.
- **L'étendue.** Seul `SoiCrossingDetectorTest` a été identifié comme
  contaminant. `SoiRoundTripFlightTest` et `LunarTransferFlightTest` n'ont pas
  fait tomber les gates dans les sélections essayées, mais l'échantillon est
  petit et l'absence n'est pas une preuve.
- **Si d'autres tests à tolérance serrée sont touchés.** Seuls les deux gates
  ont été regardés.

> **Piège de mesure, à connaître avant de reproduire.** Relancer `./gradlew test
> --tests …` avec le même filtre après un succès rend `> Task :test UP-TO-DATE`
> et n'exécute **rien**, en affichant un BUILD SUCCESSFUL vert. Une séquence
> « rouge puis vert puis vert » est en général « exécuté-échec, exécuté-succès,
> sauté ». Toute mesure passe par `cleanTest`.

---

## BUG-8 — Inclinaison figée invalidée en silence par un changement de site

**Constaté.** Wizard de création de mission, carte `LEO`, pas de tir Cape
Canaveral, nœud cible 50°. La page `PLANNING` n'affiche aucune opportunité et
donne pour motif `target plane unreadable, or out of this site's reach`. Rien,
avant cela, n'a signalé que quoi que ce soit était invalide.

**Le modèle n'est pas en cause, c'est mesuré.** Sondé directement,
`EarthLaunchWindowPlanner.nextOpportunities` depuis Canaveral (28,562°,
−80,577°, 3 m) vers un nœud à 50°, cible 400 km :

| Plan visé | Résultat |
|---|---|
| plein est (i = 28,562°) | **3 créneaux**, un par jour sidéral, 6,73 m/s au creux, 7 min 01 s de large |
| i = 28,6° explicite | 3 créneaux, 7,46 m/s |
| i = 51,6° explicite | 3 créneaux, 11,53 m/s |
| i = 5,24° — celle de Kourou | **refusé** : `Inclination 5.240° is unreachable from latitude 28.562°` |

Une seule entrée fait échouer la chaîne, et c'est une inclinaison **inférieure à
la latitude du pas de tir**.

### Mécanisme (identifié dans le code, non reproduit pas à pas dans l'IHM)

`MissionProfile.LEO` est en `InclinationMode.AUTO` : l'inclinaison vaut la
latitude du site et la suit — **jusqu'à la première frappe de l'utilisateur**,
qui pose `inclinationAuto = false` et fige la valeur. À partir de là,
`refreshDerivedInclination()` ne la rafraîchit plus.

Il suffit donc de saisir une inclinaison pendant que Kourou est sélectionné —
c'est le **site par défaut**, à 5,236° — puis de passer le site à Canaveral :
la valeur figée sort de la bande `[28,562° ; 151,438°]` que ce pas de tir
atteint sans changement de plan. `LaunchPlane.requireReachableFrom` la refuse,
`EarthOrbitDynamicParameters.targetOrbit` rend alors du vide, et la page
planning affiche le motif ci-dessus.

**Le défaut n'est pas le refus, il est son silence.** L'étape ne marque le champ
en rouge que dans `validateTargetPlane()`, qui ne tourne qu'à la pression de
`Next`. Entre le changement de site et cette pression, le formulaire porte une
valeur invalide sans rien en dire, et la seule chose qui parle est une page
secondaire que rien n'oblige à ouvrir.

### Pistes, aucune tranchée

1. **Réarmer l'auto au changement de site.** Le plus simple, mais il écrase une
   saisie explicite de l'utilisateur — exactement ce que le mode AUTO existe
   pour respecter.
2. **Ramener la valeur figée dans la bande atteignable** au changement de site.
   Ne perd pas l'intention, la déforme.
3. **Marquer le champ en direct** plutôt qu'à `Next`, sur le motif de
   `rejectedLaunchDate`/`rejectedRaan` qui effacent leur rouge à la frappe. Ne
   corrige rien mais rend le problème visible là où il naît — probablement le
   minimum à faire quelle que soit la suite.

### Non vérifié

- **La séquence exacte dans l'IHM.** Le mécanisme est lu dans le code et le
  refus est mesuré côté modèle ; la suite de clics qui y mène n'a pas été rejouée
  pas à pas. Il reste possible qu'un autre chemin produise le même état.
- **Si la ligne d'aide sous le champ dit déjà la bande atteignable** pendant que
  la valeur en est sortie. `refreshReachableHelper` existe, mais on n'a pas
  vérifié qu'il tourne encore quand l'auto est désarmé.
- **L'étendue aux autres cartes.** `POLAR` (90°) et le profil héliosynchrone
  (~98°) restent atteignables depuis n'importe quelle latitude, donc `LEO`
  paraît seule exposée — non vérifié.
- **La réouverture d'une mission** dont la spec porte une inclinaison basse,
  suivie d'un changement de site, devrait produire le même état par un autre
  chemin. Non essayé.

---

## BUG-9 — `ParkingCoastStageTest` teste la sémantique d'avant MIS-4/L6

**Constaté.** `ParkingCoastStageTest:75` compare la durée volée à
`departure.coastDuration()` à 1 s près. Rouge depuis le 2026-08-28, toujours
rouge au 2026-08-30 — vérifié directement dans le code, la ligne n'a pas
bougé.

**Mécanisme, mesuré.** `MIS-4 / L6` (décision α) fait désormais s'arrêter ce
coast à **l'allumage**, pas à la durée totale résolue par `departure`. L'écart
est exactement `ignitionLead` : le journal dit « coasting 690 s to ignition,
19,6 s ahead ». Le test asserte la sémantique d'avant `L6` et n'a jamais été
mis à jour.

**Ce n'est pas une régression de production.** `ParkingCoastStage` lui-même
est correct — c'est la sémantique voulue par `L6` — seule l'assertion du test
est restée sur l'ancien contrat.

### Correction

Comparer la durée volée à `departure.coastDuration() − ignitionLead` (ou à
l'accesseur équivalent que la classe expose déjà pour l'allumage), pas à
`coastDuration()` seul.

### Non vérifié

Aucune recherche systématique d'un autre test qui encoderait la même
sémantique pré-`L6`.

---

## BUG-10 — `ReentryGuard` inopérant en présence de traînée

**Constaté.** `ReentryGuard.SUBSURFACE_FLOOR = −50 km`, armé partout comme en
vol (`armQuiet`, `StageLegRunner`). Mesuré dans
[`atmosphere/03-baseline-L0.md`](atmosphere/03-baseline-L0.md) §2.3 : avec la
traînée active, l'intégrateur meurt à −9 à −30 km — **au-dessus** du plancher.
Le garde ne se déclenche donc jamais dans son seul régime où une rentrée
réaliste (et non une chute libre pathologique) est en jeu.

**Pourquoi le plancher est profond, et pourquoi ça ne suffit pas ici.** Le
plancher a été dimensionné pour arrêter un effondrement `r → 0` en chute
libre — cf. le javadoc de la classe et [[reentry-guard]] : sans traînée, une
trajectoire qui plonge le fait vite, et **−50 km** suffit très largement à
intercepter la chute avant que le pas d'intégration ne s'effondre. Avec la
traînée, la descente est ralentie et l'intégrateur échoue **plus tôt, à une
profondeur moindre**, pour une raison numérique distincte (probablement liée
au modèle atmosphérique lui-même sous 30 km, non instrumenté ici).

**Importance.** Nul aujourd'hui : la traînée est **off** par défaut
(`PHY-1`), aucun vol de production ne l'exerce. Concerne directement `PHY-2`
et `MIS-10` (rentrée contrôlée), phases 6-7.

### Non vérifié

- La cause exacte de l'échec de l'intégrateur à −9/−30 km sous traînée.
- Le plancher ou le mécanisme de garde approprié pour ce régime — probablement
  distinct de `SUBSURFACE_FLOOR`, pas un simple décalage de sa valeur.

---

## BUG-11 — L'optimiseur saute les coasts que le vol rejoue

**Constaté.** `CoastingStage` et `StageSeparationStage` ne surchargent pas
`propagateStandalone` : mesuré dans
[`multi-corps/03-conception-L1.md`](multi-corps/03-conception-L1.md) §5.2, un
écart d'environ **2 770 s** (près d'une demi-orbite) apparaît entre la
physique interne du CMA-ES et le vol effectivement rejoué, dès qu'une étape
analytique de ciblage de nœud suit un coast dans la chaîne.

**Importance.** C'est la racine identifiée d'une anomalie de baseline
jusque-là inexpliquée en `PHY-4`. Le défaut **traverse `PHY-4` → `MIS-4` →
`MIS-5`** sans jamais être refermé — chaque chantier l'a mesuré à nouveau sans
le corriger, faute d'appartenir clairement à son périmètre.

**Pourquoi c'est le candidat le plus sérieux avant Phase 5.** `MIS-6`
(rendezvous/phasing) et `MIS-11` (retour) sont les deux premiers chantiers où
le **timing** de la trajectoire rejouée est l'enjeu central plutôt qu'un
sous-produit — un optimiseur qui raisonne sur une physique décalée d'une
demi-orbite y devient un défaut de premier ordre, pas une curiosité de
baseline.

### Piste

Donner à `CoastingStage` et `StageSeparationStage` une implémentation de
`propagateStandalone` qui propage réellement le coast, au lieu de le sauter —
alignant la passe d'optimisation sur le vol rejoué.

### Non vérifié

Le coût de calcul d'un coast réellement propagé à chaque évaluation CMA-ES
n'a pas été mesuré ; il peut être significatif, un coast pouvant durer des
milliers de secondes et être évalué des milliers de fois par optimisation.

---

## BUG-12 — Bande morte ε de franchissement de SOI jamais calibrée

**Constaté.** La bande morte ε qui décide du franchissement de la sphère
d'influence a été acceptée **« par verdict »** — un jugement, pas une mesure
— à travers `PHY-4` → `MIS-4` → `MIS-5`
([`multi-corps/08-conception-L6.md`](multi-corps/08-conception-L6.md)
§5.5/§12.4, [`lunar-orbit/09-conception-L7.md`](lunar-orbit/09-conception-L7.md)
§9). Aucune trajectoire réellement « capturée » (franchissant la frontière
plus d'une fois) n'a permis de mesurer la valeur empirique attendue.

**Pourquoi c'est encore ouvert.** Aucun vol mesuré jusqu'ici n'a sollicité
plus d'un franchissement. `MIS-11` (survol lunaire et retour) sera le premier
à le faire pour de vrai, et c'est là que la valeur actuelle de ε — jamais
stressée — devient un risque concret plutôt qu'un choix confortable.

### Non vérifié

Aucune mesure empirique n'existe. Le calibrage demande une trajectoire de
test qui franchisse la frontière SOI dans les deux sens.

---

## BUG-13 — Fenêtre de lancement lunaire refusée sans signal à l'écran

**Constaté.** Pour un site hors de la bande de déclinaison lunaire atteignable
(exemple mesuré : Kourou, ~87,5 % d'une lunaison hors bande),
`LunarLaunchWindowProblem` / `LunarOrbitWindowProblem` ne renvoient aucune
opportunité — seulement un warning loggé. Rien à l'écran ne le signale.
Nommé comme dernier trou ouvert connu à la clôture de `MIS-5`
([`lunar-orbit/09-conception-L7.md`](lunar-orbit/09-conception-L7.md) §7/§9).

**Distinct de `BUG-8`.** Sous-système différent (recherche de fenêtre
lunaire, pas gel d'inclinaison figée), et le défaut touche identiquement
toutes les cartes lunaires par construction géométrique — il ne dépend pas
d'un ordre d'interaction utilisateur comme `BUG-8`.

### Piste

Même famille de correctif que `BUG-8` — marquer le champ ou remonter le refus
visuellement — mais sur un sous-système distinct, à traiter séparément.

### Non vérifié

La séquence exacte dans l'IHM n'a pas été rejouée pas à pas.

---

## BUG-14 — Deux portées de recherche de fenêtre divergentes

**Constaté.** `EarthLaunchWindowPlanner.SEARCH_SPAN` est un littéral en dur
(`Duration.ofHours(26)`, `EarthLaunchWindowPlanner.java:37`), de même que
`LunarLaunchWindowPlanner.SEARCH_SPAN` (48 h), alors que `LaunchWindowSearch`
dérive ailleurs sa portée de `problem.recurrence()`. Signalé dans
[`mission-window/02-timeline-wizard.md`](mission-window/02-timeline-wizard.md)
§8 comme « une mesure à faire, pas un nettoyage à trancher » — jamais faite.

### Non vérifié

L'impact concret (une opportunité manquée en bord de portée, pour un site ou
une inclinaison particulière) n'a jamais été mesuré.

---

## BUG-15 — Log `ERROR` trompeur de `DepletionGuard` sur un rejet correct

**Constaté.** `DepletionGuard` émet `… upstream mass accounting is wrong` en
routine sur un λ correctement rejeté pour charge sous-dimensionnée
([`optimization/bilan.md`](optimization/bilan.md), piste 6). Rien n'est
« wrong » : c'est le verdict attendu de l'algorithme de recherche de charge.

**Importance.** Cosmétique, mais un `ERROR` sur un comportement normal fait
chercher un bug qui n'existe pas à chaque lecture de log — coût de debug
récurrent plutôt qu'un défaut fonctionnel.

### Piste

Distinguer, dans `DepletionGuard`, le message pour ce chemin de rejet de
routine (charge insuffisante détectée pendant la recherche) du message pour
une véritable incohérence de comptage de masse — et abaisser le premier en
`INFO`/`WARN`.

---

## BUG-16 — `t1` saturé contre sa borne sur la majorité des transferts mesurés

**Constaté.** Sur 5 des 7 scénarios de transfert mesurés, `t1` sature
exactement à sa borne supérieure (`norm = 1,0`,
[`optimization/bilan.md`](optimization/bilan.md), piste 4). C'est un mur de
boîte (`t1Max = 0,5 × période`) : l'optimiseur voudrait allumer plus tard
qu'une demi-période et la borne l'en empêche — ce n'est pas un vrai optimum.

**Attention avant de corriger.** Cf. [[cmaes-bounds-are-not-constraints]] :
déplacer une borne CMA-ES renormalise toute la recherche et perturbe toutes
les missions, pas seulement celles qui saturent. Toute correction exige une
re-mesure complète des références, pas un simple changement de littéral.

### Non vérifié

La vraie valeur optimale de `t1` au-delà de la moitié de période n'a jamais
été mesurée.

---

## BUG-17 — `acceptableCost` mal calé depuis l'ajout du terme ergols I7

**Constaté.** Le seuil `acceptableCost = 3e-3` a été calibré avant l'ajout du
terme propergol (I7). Le plancher de coût réel mesuré vaut désormais
~2,64e-3 (partie orbitale) + ~1,4e-3 (partie propergol) —
[`optimization/bilan.md`](optimization/bilan.md), piste 2. Un `WARN Final
cost … above acceptable` tombe donc sur pratiquement chaque transfert, et
aucun arrêt anticipé « Target reached » ne peut s'enclencher.

### Piste, et pourquoi la version naïve est dangereuse

Comparer seulement la **partie orbitale** du coût au seuil plutôt que le
total. Une simple élévation du seuil au niveau du plancher mesuré laisserait
repasser l'extinction sèche — exactement le défaut que la barrière propergol
vient de corriger.

---

## BUG-18 — Rejets de scénario au chargement, seulement journalisés

**Constaté.** À l'ouverture d'un scénario, `ScenarioAppState.openScenario`
(`:311-318`) journalise chaque rejet (`logger.info` sur le compte, `logger.warn`
par mission rejetée) mais ne remonte **rien** à l'écran. `ScenarioLoadReport`
porte l'information ; rien ne la lit côté IHM. Nommé « trou connu » dans
[`scenario/01-persistance-missions.md`](scenario/01-persistance-missions.md).

**Vérifié au 2026-08-30** directement dans `ScenarioAppState.java` — le
comportement n'a pas changé depuis la clôture d'`UI-3` (2026-08-21).

### Piste

Un résumé minimal (toast, ligne dans le panel) au retour de
`ScenarioSession.restore`, listant les missions rejetées et leur motif —
même contenu que les lignes `WARN` déjà produites, juste remonté à l'écran.
