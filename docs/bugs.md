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
