# FX-2 — Éclipses / pénombre inter-corps : découpage haut niveau

Item roadmap : `FX-2` (★4 ◆3 M), dernier item ouvert de la phase 4 (`docs/roadmap/01-roadmap-v1.md`
§3), fiche de référence au §6 du même document. Ce document ne conçoit rien : il **découpe**.
Chaque lot y est défini par la propriété qu'il rend vraie et par le test qui la ferme. Le détail de
conception (noms de classes, signatures, formules) viendra dans les documents `02-…` et suivants,
lot par lot.

> **Statut : brouillon de découpage.** Les formules et tolérances citées sont des directions à
> vérifier à l'implémentation, pas des seuils arrêtés.

---

## 1. Périmètre

**Dans `FX-2`** :

- un vaisseau s'assombrit dans l'ombre du corps central de son arc courant (Terre ou Lune selon la
  mission) ;
- la Lune s'assombrit dans l'ombre de la Terre (éclipse lunaire), avec dégradé si l'éclipse est
  partielle ;
- une tache d'ombre — ombre et pénombre dégradée — se déplace sur la surface terrestre visible
  quand la Lune s'interpose entre le Soleil et la Terre (éclipse solaire vue de l'espace). C'est
  l'image qui justifie le chantier.

**Hors `FX-2`**, et à ne pas y laisser glisser :

- l'ombre projetée sur l'atmosphère ou les nuages — dépend d'un modèle qui n'existe qu'à partir de
  `PHY-2` ;
- toute éclipse impliquant une autre planète ou une lune qui n'est pas la Lune terrestre — aucune
  autre n'est modélisée dans le rendu aujourd'hui ;
- le cas interplanétaire (un corps occulté alors qu'il vole sur un arc héliocentrique) — cet arc
  n'existe pas dans le code, `TrajectoryArc` ne connaît que des arcs centrés Terre ou Lune ;
- lens flare et god-rays (`effects-roadmap.md` §6.4) — effet voisin, item distinct.

---

## 2. État des lieux — ce que le code porte déjà

**A. Le point d'injection existe et est unique.** `MatDefs/Light/WrapLighting.frag` est le seul
matériau d'éclairage du dépôt ; son terme diffus tient en une ligne
(`color += DiffuseSum.rgb * lightColor.rgb * diffuseColor.rgb * diff * lightDir.w`) et est
appliqué par [`AssetFactory.applyLambert`](../../src/main/java/com/smousseur/orbitlab/engine/AssetFactory.java)
à deux endroits : les planètes ([`PlanetPoseAppState`](../../src/main/java/com/smousseur/orbitlab/states/scene/PlanetPoseAppState.java),
falloff 0.8) et le vaisseau de chaque mission ([`MissionRenderer`](../../src/main/java/com/smousseur/orbitlab/states/mission/MissionRenderer.java),
falloff 0.3). Un uniform ajouté au `.j3md` couvre donc les deux familles de corps d'un coup.

**B. Aucun code d'éclipse n'existe, mais Orekit — déjà une dépendance — en porte un, inutilisé.**
`org.orekit.propagation.events.EclipseDetector` et son `OccultationEngine` calculent, pour une
position donnée à une date donnée, trois angles réels : la séparation apparente entre les
directions occultant/occulté, le rayon apparent de l'occulteur, et le rayon apparent du corps
occulté (le Soleil). `angles(SpacecraftState)` se construit sans propagation, à partir d'une
position brute (`AbsolutePVCoordinates`). C'est un oracle indépendant pour les tests, et la source
de la formule de pénombre — pas une simulation à réécrire.

**C. Le near viewport n'affiche jamais qu'un seul globe 3D à la fois.**
[`LodView`](../../src/main/java/com/smousseur/orbitlab/engine/scene/body/LodView.java) documente
que `SceneGraph.showBodySpatial` retire le modèle 3D de tout corps qui n'est pas celui sur lequel
la scène est centrée. Ce n'est pas bloquant : les trois cas du périmètre n'ont besoin de l'occulteur
que comme **donnée** (position + rayon, lus sur l'éphéméride), jamais comme modèle affiché — le
corps qui reçoit l'effet visuel est toujours seul en 3D, l'occulteur reste invisible.

**D. Le repère d'un point de trajectoire n'est pas toujours GCRF.** Depuis PHY-4/L4,
[`MissionEphemerisPoint`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/ephemeris/MissionEphemerisPoint.java)
porte un `TrajectoryArc` par point — Terre pour une mission terrestre, **Lune** pour un arc lunaire
(MIS-4/MIS-5, livrés). Le docstring de `SpacecraftPresenter.updatePose` qui décrit la position comme
« geocentric, Earth at origin » est obsolète sur ce point : il ne décrit que le cas terrestre.

**Ce qui joue déjà en notre faveur.** `PlanetRadius.radiusFor` couvre tous les corps, Soleil
compris. `TrajectoryArc.convertPosition` et `EphemerisService.trySampleHelioIcrf` donnent déjà les
conversions de repère nécessaires, en translation pure et exacte (round-trip mesuré à 0 m par L4) —
aucune nouvelle brique de conversion à écrire. `PlanetPoseAppState.update()` et `MissionRenderer`
ont chacun déjà une boucle par-frame et par-corps qui peut porter le nouvel appel ; ni l'un ni
l'autre n'a aujourd'hui de point d'accroche pour pousser un uniform de matériau après le chargement
asynchrone du modèle — c'est la seule vraie plomberie neuve.

---

## 3. Principe du découpage

Deux règles, décidées en amont de ce document et non renégociées lot par lot :

1. **Un seul mécanisme shader, dès le premier lot.** Un test d'occultation par fragment (sphère
   contre le rayon vers le Soleil), alimenté par les uniforms position/rayon de l'occulteur —
   jamais un scalaire CPU pour les petits corps puis un chemin fragment séparé pour la Terre. La
   géométrie que le CPU doit calculer est la même quel que soit l'endroit où le test final
   s'exécute ; ne pas la dupliquer.
2. **Chaque lot ajoute un occulteur à un corps de plus, jamais une nouvelle technique.** L1 pose le
   mécanisme et l'exerce sur le vaisseau ; L2 et L3 ne font que câbler un occulteur supplémentaire
   sur un corps qui a déjà tout le reste.

Chaque lot se ferme sur une comparaison avec l'oracle Orekit (`EclipseDetector`/`OccultationEngine`)
quand une évaluation ponctuelle suffit (L1, L2) ; sur un contrôle visuel plus une fonction Java de
référence testée isolément quand l'effet est surfacique (L3, où rien n'observe un shader depuis
JUnit).

---

## 4. Les lots

| Lot | Objet | Nouveau mécanisme ? | Test qui le ferme |
|---|---|---|---|
| **L1** | Le vaisseau s'assombrit dans l'ombre de son corps central | oui — le seul de tout le chantier | signe accordé avec `EclipseDetector.g` |
| **L2** | La Lune s'assombrit dans l'ombre de la Terre | non — un occulteur de plus | signe accordé avec `EclipseDetector.g`, au centre de la Lune |
| **L3** | La Terre montre la tache d'ombre de la Lune | non — câblage seul | contrôle visuel + fonction Java de référence pour la formule de recouvrement |

### L1 — Le vaisseau s'assombrit dans l'ombre de son corps central

**Propriété rendue vraie.** Un vaisseau qui traverse le cône d'ombre du corps autour duquel il vole
en ce moment (Terre ou Lune, selon `TrajectoryArc.body()` du point courant) s'assombrit
visuellement, et se rallume en sortant.

**Contenu.** `WrapLighting.j3md`/`.frag` : trois nouveaux uniforms (`m_OccluderPosition`,
`m_OccluderRadius`, `m_SunApparentRadius`) et un test d'occultation par fragment dans la boucle
d'éclairage existante, réutilisant `lightDir` déjà calculé — la formule de recouvrement des deux
disques (occulteur, Soleil), paramétrée par ces trois valeurs, pas un simple bord dur. Côté Java :
dans `MissionRenderer`, chaque frame, dérive occulteur = `point.arc().body()`, sa position relative
au vaisseau (`-point.position()`, déjà en main), son rayon (`PlanetRadius.radiusFor`), pousse les
trois uniforms sur les géométries du `Model3dView` du vaisseau via un point d'accroche ajouté à
`BodyView` (aux côtés de `setPositionWorld`/`setRotationWorld`), implémenté par un parcours des
géométries à chaque frame — pas de nouvel état mis en cache.

**Ce que ça ne fait pas.** Un seul occulteur par corps (celui de l'arc courant) ; pas de cas
interplanétaire ; les autres corps (Mars, Jupiter…) ne reçoivent aucun occulteur.

**Fermeture.** Sur une mission dont l'éphéméride croise l'ombre de son corps central, le signe du
facteur évalué au centre du vaisseau doit s'accorder avec `EclipseDetector.g(state)` — construit via
`AbsolutePVCoordinates` à partir du même point d'éphéméride — aux instants d'entrée/sortie, à la
tolérance de convergence de l'algorithme près.

### L2 — La Lune s'assombrit dans l'ombre de la Terre

**Propriété rendue vraie.** La Lune, en éclipse totale ou partielle par la Terre, s'assombrit — et
parce que le mécanisme est par-fragment depuis L1, une éclipse partielle montre déjà un dégradé sur
le disque lunaire, pas un tout-ou-rien, sans travail supplémentaire.

**Contenu.** Dans `PlanetPoseAppState.update()`, pour `MOON` (seul satellite existant), dérive
l'occulteur = `body.parent()` (Terre), sa position relative à la Lune via deux appels à
`trySampleHelioIcrf` (Terre et Lune — la pose de la Lune en fait déjà un), son rayon via
`PlanetRadius.radiusFor(EARTH)`, pousse les mêmes trois uniforms que L1 par la même méthode
`BodyView`. Un petit helper partagé (`EclipseGeometry.sunApparentRadius(sunDistanceMeters)`, une
ligne : `asin(SUN_RADIUS / distance)`) factorise le calcul introduit en L1 dans `MissionRenderer`,
pour ne pas le dupliquer au deuxième site d'appel.

**Ce que ça ne fait pas.** Les autres planètes (Mercure…Pluton) ne reçoivent toujours aucun
occulteur — aucune n'a de corps proche qui l'éclipse dans cette application. Ne couvre pas encore
la tache d'ombre sur la Terre (L3).

**Fermeture.** À une géométrie Terre-Lune-Soleil alignée (synthétique en test, ou une date
d'éclipse lunaire réelle), le signe du facteur évalué au centre de la Lune doit s'accorder avec
`EclipseDetector.g`, comme L1.

### L3 — La Terre montre la tache d'ombre de la Lune

**Propriété rendue vraie.** Quand la Lune s'interpose entre le Soleil et la Terre, une tache
d'ombre — ombre et pénombre dégradée — apparaît et se déplace sur la surface terrestre visible, à
la bonne position et à la bonne taille.

**Contenu.** Dans `PlanetPoseAppState.update()`, pour `EARTH` — un cas explicite, pas dérivé de la
relation parent/satellite générique de L2, puisque la Terre n'est satellite de personne — occulteur
= `MOON`, réutilisant les deux mêmes appels `trySampleHelioIcrf` que L2 calcule déjà pour poser la
Lune. **Aucun nouveau code shader** : le test par-fragment et la formule de recouvrement des deux
disques existent depuis L1 — L3 n'est que du câblage CPU. C'est la conséquence directe d'avoir
construit la formule pleine dès L1 plutôt qu'un bord dur : la Terre n'est jamais qu'un corps de
plus, seulement assez grand pour que le dégradé se voie sur sa propre surface au lieu de se
moyenner sur un disque de quelques pixels.

**Ce que ça ne fait pas.** Pas d'ombre projetée sur les nuages/atmosphère (dépend de `PHY-2`, hors
périmètre). Pas de cas Terre-occultant-le-Soleil-vu-d'un-vaisseau-en-orbite-lunaire (aucun besoin
identifié). La distinction éclipse totale/annulaire n'est *a priori* pas un cas à part — la formule
de recouvrement la capture déjà par construction, le rayon apparent relatif de la Lune et du Soleil
la détermine — mais reste à **confirmer à l'écran**, pas supposée acquise.

**Fermeture.** À une date d'éclipse solaire connue (ou géométrie synthétique alignée), la tache
sombre doit apparaître centrée là où la droite Soleil-Lune perce la surface terrestre — contrôle
visuel, puisque la logique vit en shader. Pour un test automatisé indépendant de l'écran : extraire
la formule de recouvrement en une fonction Java pure (le double de référence de la formule GLSL),
testable en JUnit à des séparations angulaires connues et vérifiée une fois contre le shader — le
même principe que `RibbonMeshBuilderTest` pour une propriété géométrique sans contexte OpenGL.

---

## 5. Ce qui reste à trancher au raffinement

Aucune de ces questions ne bloque le début de L1.

1. **Forme exacte de la formule de recouvrement des deux disques** (aire d'intersection de deux
   cercles à partir des trois angles d'`OccultationEngine`) — la forme mathématique est connue et
   standard, mais son implémentation GLSL précise (branches numériques aux cas limites : disques
   disjoints, l'un contenu dans l'autre) est à écrire et à tester en L1, pas encore posée.
2. **Où vit `sunApparentRadius` au long cours.** L2 propose un helper partagé pour ne pas dupliquer
   la formule ; à décider si ce calcul devrait plutôt être centralisé une fois par frame (par
   exemple aux côtés de `LightningAppState`, qui calcule déjà la direction du Soleil) plutôt que
   recalculé par consommateur.
3. **Tolérance de la comparaison avec `EclipseDetector.g`** en L1/L2 — numérique, à calibrer à
   l'implémentation contre le seuil de convergence par défaut de l'algorithme Orekit.
4. **Distinction totale/annulaire à l'écran** (L3) — à vérifier visuellement une fois la formule en
   place ; pas garantie tant qu'elle n'est pas observée.

---

## 6. Ordonnancement

Strictement séquentiel — `L1 → L2 → L3` — puisque chaque lot après L1 ne fait qu'étendre le
précédent à un corps de plus ; il n'y a pas d'intercalage utile ici, contrairement à `PHY-4`.

Le lot le plus risqué est **L1** : c'est la seule fois où le mécanisme shader et le point
d'accroche `BodyView` sont neufs, et une erreur dans la formule de recouvrement s'y propage
silencieusement à L2 et L3. **L3** porte la valeur du chantier — c'est l'image que `FX-2` existe
pour produire — mais n'est presque que du câblage une fois L1 posé correctement.
