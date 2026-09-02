# Spec — Artefacts visuels en vue Spacecraft

> **Diagnostic refait le 2026-08-09.** Il remplace intégralement la version du
> commit `6d0676e`, devenue fausse sur trois points (§7). Les symptômes
> observés aujourd'hui ne sont plus ceux qui y étaient décrits, et la cause
> dominante n'est pas celle qui y était retenue.
>
> **État des correctifs.** Causes A, B et C **corrigées le 2026-08-09**
> (§9.1, §9.2, §9.3), et **vérifiées à l'écran** par le protocole du §8.
> Restent ouverts le raccord terminal (§4.3), le `setDepthWrite(false)` de §6,
> et les niveaux 3-4 du catalogue. L'item `RND-1` de la roadmap, que ce document
> instruisait, est clos.

## 1. Symptômes observés

En `ViewMode.SPACECRAFT`, caméra posée sur le vaisseau (clic sur l'icône) :

1. **La ligne de trajectoire saute.** Elle ne reste pas accrochée au vaisseau :
   elle danse autour de lui d'une frame à l'autre, et scintille par endroits.
2. **Le modèle 3D du vaisseau disparaît à haute vitesse d'horloge.** À vitesse
   réelle il est là ; en accéléré il s'éclipse (l'icône 2D prend sa place).

Le symptôme « la Terre se couvre de motifs hexagonaux troués » décrit par
l'ancienne version n'est plus rapporté. Il n'est pas traité ici ; s'il
réapparaît, il relève du §5.3 (plancher de précision de profondeur), dont les
chiffres ci-dessous donnent la mesure réelle.

**Trois causes racines indépendantes** ont été identifiées, une par mécanisme.
Elles n'ont ni la même origine, ni le même correctif :

| # | Cause | Symptôme produit | Confiance | État |
|---|---|---|:-:|:-:|
| A | Offset de floating origin en retard d'une frame, lu par le calcul de LOD | Modèle 3D qui disparaît en accéléré | Établie par lecture de code | **Corrigée** |
| B | Sommets de la ligne en coordonnées GCRF absolues, annulation catastrophique en `float32` | Ligne qui saute / danse autour du vaisseau | Dérivée, quantifiée, **mesurée en test** | **Corrigée** |
| C | Plan near à 10 m ⇒ ~270 km de résolution de profondeur à la distance de la Terre | Ligne qui scintille **là où elle croise le disque terrestre** | Dérivée, quantifiée, **mesurée en test** | **Corrigée** |

Le §8 donne le protocole d'observation qui sépare B de C en trente secondes.

## 2. Rappel — architecture concernée

### 2.1 Graphe near

```
nearRoot
└── nearFrame                    ← translaté de −p_vaisseau en SPACECRAFT
    ├── nearOrbitsNode
    │   └── MissionTrajectory-<id>   (LineStrip, sommets GCRF absolus en km)
    └── nearBodiesNode
        ├── PlanetsBucket → BodyAnchor-EARTH → ModelBucket   (local = 0)
        └── Anchor-mission-<id>          ← local = +p_vaisseau (km)
            └── BodyAnchor-mission-<id>  → ModelBucket → heavy_falcon.gltf
```

`SceneGraph:58-62`, `MissionRenderer:88-90`, `MissionTrajectoryRenderer:69`.

Échelle near : 1 unité = 1 km (`RenderContext.PLANET_METERS_PER_UNIT`). En LEO
à 400 km, `|p| ≈ 6778` unités.

### 2.2 Ordre des AppState dans la frame

L'ordre d'attachement **est** l'ordre de mise à jour
(`AppStateManager.update` itère le tableau des états dans l'ordre d'insertion),
et il est décidé dans `OrbitLabApplication.simpleInitApp` :

| Rang | État | Ce qu'il fait du couple (ancre, nearFrame) |
|---:|---|---|
| … | `PlanetPoseAppState` | pose les planètes |
| 7 | **`MissionOrchestratorAppState`** | écrit `ancre.local = p(t)`, **puis lit `ancre.getWorldTranslation()`** pour le LOD |
| 8 | **`FloatingOriginAppState`** | lit `ancre.local` et écrit `nearFrame.local = −p(t)` |
| 9 | `PlanetHudMarkersAppState` | lit les positions monde des planètes (**après** l'origine flottante) |
| … | `OrbitCameraAppState`, `NearCameraSyncAppState` | posent les caméras |
| — | `rootNode.updateGeometricState()` | fige les transformées monde pour le rendu |

Les planètes font donc la lecture **dans le bon ordre** (rang 9 > 8) ; la
mission la fait **dans le mauvais** (rang 7 < 8). C'est toute la cause A.

## 3. Cause A — le modèle 3D disparaît en accéléré

> **Corrigée le 2026-08-09.** Le §3 décrit l'état d'avant correctif ; ce qui a
> été fait, et ce qui a été fait *en plus* de ce qui était prévu, est en §9.1.

### 3.1 Chaîne de causalité

`MissionRenderer.updateFromEphemeris` (`:153-155`) fait, dans cet ordre :

```java
presenter.updatePose(point.position(), …);   // ancre.local ← p(t)
view.updateScreen(cam);                      // LOD, lit la position MONDE
trajectoryRenderer.update(trail, upTo, point.position());
```

et `LodView.updateScreen` (`:92-107`) décide du LOD ainsi :

```java
Vector3f bodyPos = farAnchor.getWorldTranslation();   // ← ligne 93
float distance = cam.getLocation().distance(bodyPos);
…
float projectedRadiusPx = (float) (radiusUnits / distance) * (cam.getHeight() * 0.5f) / tanHalfFov;
float threshold = lastShow3d ? 6f : 10f;
```

`getWorldTranslation()` déclenche `checkDoTransformUpdate()` : la transformée
monde est recalculée depuis l'ancêtre le plus haut **marqué sale**. Or
`nearFrame` n'est pas sale à cet instant — sa translation date de la frame
précédente, `FloatingOriginAppState` ne passera qu'au rang 8. D'où :

```
ancre.world = nearFrame.local(t−1) + ancre.local(t)
            = −p(t−1) + p(t)
            = Δ   (le déplacement du vaisseau pendant une frame simulée)
```

Au **rendu**, en revanche, tout est juste : `updateGeometricState()` s'exécute
après le rang 8, donc `ancre.world = −p(t) + p(t) = 0` exactement (annulation
bit à bit de deux `float` opposés). Le vaisseau est bien dessiné à l'origine.
**Seule la décision de LOD est fausse** — elle croit le vaisseau à `Δ` de
l'origine alors qu'il y est posé.

### 3.2 Chiffres

Vitesse orbitale LEO ≈ 7,7 km/s, 60 fps ⇒ `Δ = 7,7 × (vitesse d'horloge / 60)` km.

Seuil de bascule : `radiusUnits = 0,05` (50 m), `height = 720`, FoV ≈ 21° à la
distance de focus (500 m ⇒ `normalizedZoom01 ≈ 0,11`), donc
`projectedRadiusPx ≈ 1915 × 0,05 / distance`. Le modèle s'éteint sous 6 px,
soit **distance > 16 km**.

| Vitesse d'horloge | Δ (km) | Rayon projeté | Affichage |
|---:|---:|---:|---|
| ×0 (pause) | 0 | 191 px (d = 0,5 km) | modèle 3D |
| ×1 | 0,13 | 149 px | modèle 3D |
| ×10 | 1,3 | 74 px | modèle 3D |
| ×60 | 7,7 | 12 px | modèle 3D (marginal) |
| ×300 | 38 | 2,5 px | **icône** |
| ×3600 | 462 | 0,2 px | **icône** |

**Prédictions falsifiables** (cf. §8) :

- Le basculement se produit entre ×60 et ×300, pas avant.
- **Mettre l'horloge en pause fait revenir le modèle instantanément**, sans
  bouger la caméra : `Δ` tombe à zéro. C'est le test décisif — aucun mécanisme
  de profondeur ou de précision ne réagit à la pause.
- Le seuil se déplace avec le framerate : moins de fps ⇒ `Δ` plus grand ⇒
  disparition plus tôt.
- Autour du seuil, l'hystérésis 6↔10 px s'applique à une grandeur bruitée (Δ
  varie avec le jitter de `tpf`) : on doit voir un **clignotement** modèle ↔
  icône plutôt qu'une bascule franche.

### 3.3 Effet de bord

`BillboardIconView.updateScreenPosition` (`:87-102`) projette la **même**
position monde fausse. L'icône censée remplacer le modèle est donc placée à
l'écran comme si le vaisseau était à `Δ` : elle atterrit n'importe où, ou est
rejetée par le test `screen.z ∉ [0,1]`. D'où l'impression que le vaisseau
« disparaît » plutôt que « repasse en icône ».

## 4. Cause B — la ligne saute

> **Corrigée le 2026-08-09**, sauf §4.3. Le §4 décrit l'état d'avant correctif ;
> ce qui a été fait est en §9.2.

### 4.1 Le mécanisme

`MissionTrajectoryRenderer.putVertex` (`:117-122`) écrit les sommets en
**GCRF absolu** converti en km :

```java
Vector3D scaled = RenderTransform.scaleMetersToUnits(positionGcrf, renderContext);
Vector3D jme = renderContext.axisConvention().icrfToJme(scaled);
fb.put((float) jme.getX())…
```

Un sommet proche du vaisseau vaut donc ~6778 en `float32`, et la matrice monde
de la géométrie porte la translation `−p ≈ −6778` (héritée de `nearFrame`). Le
GPU calcule `M · v` avec deux opérandes de l'ordre de 6778 dont la somme vaut
~0 : **annulation catastrophique**.

- `ulp(6778) = 2⁻¹¹ = 4,9·10⁻⁴` unité = **0,49 m**. Le seul stockage du sommet
  le quantifie déjà à un demi-mètre.
- Après produit matriciel (facteur de projection `cot(FoV/2) ≈ 5,3`), l'erreur
  résiduelle est de l'ordre de **1 m**, et elle **change à chaque frame**
  puisque la translation `−p` change à chaque frame (≈ 128 m/frame à ×1, soit
  ~260 ulp : le motif d'arrondi est entièrement renouvelé).
- Angulairement, à 500 m de distance : `1 m / 500 m = 0,115°`. Avec 720 px pour
  21° de FoV (33,8 px/°) ⇒ **≈ 4 px de tremblement par frame**.

### 4.2 Pourquoi la ligne et pas le vaisseau

C'est la signature qui identifie cette cause. Le modèle 3D est immobile parce
que sa géométrie a des sommets **petits** (±50 m) et une translation monde
**exactement nulle** (§3.1) : aucune annulation dans sa matrice. La Terre a une
grosse translation mais elle est à 6778 km de la caméra, donc son erreur de
0,5 m sous-tend 0,005 px. La ligne est le seul objet qui cumule **coordonnées
énormes** et **géométrie à quelques centaines de mètres de la caméra**.

En vue `PLANET` le même défaut existe mais la caméra est à des milliers de km :
invisible. L'artefact est intrinsèquement spécifique à la vue spacecraft.

### 4.3 Contributeur secondaire — le raccord terminal

`MissionTrajectoryRenderer.update` (`:103-109`) dessine `trail[0..upTo]` puis le
point interpolé `tip`. La dernière corde relie donc un sommet **échantillonné**
au point courant. Pas de coast = 60 s (`MissionStage.sampleStepSeconds`), soit
en LEO une corde de 460 km, dont la direction s'écarte de la tangente réelle de
jusqu'à **1,9°**. À chaque incrément de `upTo`, cette direction se recale d'un
coup : la ligne pivote autour du vaisseau d'environ 2°, soit ~12 px au bord de
l'écran, **une fois par pas d'échantillonnage simulé** (toutes les 0,2 s de
temps réel à ×300).

C'est un vrai « saut », périodique et distinct du tremblement continu de §4.1.
Il s'aggrave si l'horizon de mission force la décimation de
`TrajectoryPolyline` (stride > 1 au-delà de 8192 points) : la corde terminale
passe alors à plusieurs milliers de km.

## 5. Cause C — la ligne scintille sur le disque terrestre

> **Corrigée le 2026-08-09.** Le §5 décrit l'état d'avant correctif — les
> facteurs cités dans les extraits de code ci-dessous sont les anciens ; ce qui
> a été fait est en §9.3.

### 5.1 Chiffres réels du frustum near

`NearCameraSyncAppState:70-73`, caméra posée sur le vaisseau
(`SPACECRAFT_FOCUS_DISTANCE_SOLAR_UNITS = 5e-7` ⇒ `distToOrigin = 0,5` unité) :

```java
float near = FastMath.clamp(0.5f * 0.0005f, 0.01f, 500f);      // → 0,01 km = 10 m (plancher)
float far  = FastMath.clamp(0.5f * 10f,  100_000f, 1e8f);      // → 100 000 km (plancher)
```

Les deux valeurs sont **collées à leur plancher** : les facteurs adaptatifs ne
jouent plus du tout. C'est précisément en vue spacecraft que le frustum est le
plus mauvais.

### 5.2 Résolution de profondeur

Buffer de profondeur 24 bits, `Δd = 2⁻²⁴` :

```
Δz = 2⁻²⁴ · z² · (1/near − 1/far)
```

avec `z = 6778 km` (distance caméra → surface terrestre), `near = 0,01 km`,
`far = 10⁵ km` :

```
Δz = 5,96·10⁻⁸ × 4,59·10⁷ × (100 − 10⁻⁵) ≈ 274 km
```

**274 km de résolution de profondeur.** La trajectoire LEO passe à 400 km
au-dessus de la surface : elle n'est séparée de la Terre que d'**environ 1,5
pas de quantification**. C'est exactement la zone de bataille — la ligne gagne
ou perd le test de profondeur selon le pixel et selon l'angle de vue, et le
motif change dès que la caméra ou le vaisseau bouge : **scintillement**.

Corollaire : la face arrière de l'orbite, qui devrait être occultée par la
Terre, ne l'est pas de façon fiable non plus.

### 5.3 Ce qui pilote réellement cette résolution

`Δz ∝ z² / near` dès que `far ≫ near`. Ici `1/near = 100` contre
`1/far = 10⁻⁵` : **le plan far n'a aucun effet mesurable.**

| `near` | `far` | Δz à 6778 km |
|---:|---:|---:|
| 10 m | 100 000 km | 274 km |
| 10 m | 50 000 km | 274 km *(inchangé)* |
| 100 m | 100 000 km | 27 km |
| 1 km | 100 000 km | 2,7 km |

Descendre `FAR_MIN` — ce que recommandaient l'ancienne spec et la roadmap — ne
sert **à rien**. Tout le levier est sur `near`, et il est gratuit : en vue
spacecraft l'objet le plus proche est le vaisseau lui-même, à l'origine, donc
`distToOrigin` **est** la distance au contenu le plus proche. Un facteur de
0,2 (au lieu de 0,0005) laisse encore une marge confortable devant un modèle
de ~100 m d'envergure.

## 6. Contributeurs mineurs

- **`setLineWidth(2f)`** (`MissionTrajectoryRenderer:64`) : plafonné à 1 px sur
  les drivers en profil core, et sur ceux qui l'honorent l'expansion en quad
  produit un aliasing de bord qui oscille en mouvement sub-pixel. Ne suffit pas
  à expliquer les symptômes, mais s'ajoute à eux. Réponse : `RND-4` (ribbon).
- **Aucun réglage de profondeur sur le matériau ligne** : ni `setDepthWrite`,
  ni bucket transparent. Un `setDepthWrite(false)` supprimerait au moins les
  batailles ligne ↔ ligne aux croisements de boucles.
- **Sommet terminal dupliqué** quand la mission est finie (`upTo = size−1` et
  `tip = dernier point`) : sans effet visuel, signalé pour mémoire.

## 7. Ce que l'ancienne version disait de faux

À corriger aussi dans la roadmap (`RND-1`) :

1. **« Résolution de profondeur ~300–500 m à la surface de la Terre. »** Faux
   d'un facteur ~600. Le calcul supposait `distToOrigin ≈ 20 000 km` donc
   `near = 10 km` ; en vue spacecraft réelle la caméra est à 500 m de l'origine
   et `near` est cloué à son plancher de **10 m**. La valeur est ~274 km (§5.2).
2. **« Descendre `FAR_MIN` de 100 000 à 50 000 km. »** Sans effet (§5.3). Le
   correctif à trois constantes annoncé par la roadmap en contient donc une
   inutile.
3. **« `setPolyOffset(-1,-1)` sur le matériau de ligne. »** Sans effet : JME
   n'active que `GL_POLYGON_OFFSET_FILL` (`GLRenderer:871-892`), jamais
   `GL_POLYGON_OFFSET_LINE`. Le polygon offset ne s'applique pas à une
   géométrie en `Mesh.Mode.LineStrip`.

Restent valables : le diagnostic de z-fighting sur la ligne (mais bien plus
sévère qu'estimé), l'idée de resserrer le near, et le §5.4 de l'ancienne
version (rendu de trajectoire relatif au vaisseau) — qui n'était classé
« pas urgent » que faute d'avoir été chiffré, et qui est en réalité le
correctif de la cause dominante B.

## 8. Protocole d'observation (à faire avant tout correctif)

Quatre manipulations, aucune modification de code. Elles confirment ou
infirment chaque cause séparément.

| # | Manipulation | Si A est vraie | Si B est vraie | Si C est vraie |
|---|---|---|---|---|
| 1 | Passer de ×1 à ×60 puis ×300, caméra immobile | modèle présent jusqu'à ×60, absent à ×300 | sans effet sur le modèle | sans effet sur le modèle |
| 2 | Mettre l'horloge **en pause** | le modèle revient immédiatement | la ligne se fige (mais reste décalée) | le scintillement persiste si on bouge la caméra |
| 3 | Regarder la ligne **là où elle croise le ciel noir**, hors du disque terrestre | — | elle tremble aussi | elle est stable (rien contre quoi battre) |
| 4 | Dézoomer à ~50 km du vaisseau | — | le tremblement disparaît (erreur constante en mètres, angle qui s'écrase) | le scintillement persiste |

La manipulation 3 est la discriminante : **B tremble partout, C ne scintille
que sur la Terre.**

## 9. Catalogue de correctifs

| Niveau | Correctif | Cause | Difficulté | Effet |
|---|---|:-:|:-:|---|
| 1 | ~~`FloatingOriginAppState` devient propriétaire de l'offset et passe **avant** l'orchestrateur~~ **fait** | A | ★ | Supprime la disparition du modèle |
| 1 | ~~`NEAR_MIN` / facteur near resserrés~~ **fait** | C | ★ | ×10 de résolution de profondeur (274 → 27 km) |
| 1 | `setDepthWrite(false)` sur le matériau ligne | C | ★ | Supprime les batailles ligne ↔ ligne |
| 2 | ~~Sommets de trajectoire relatifs au vaisseau~~ **fait** | B | ★★ | Supprime le tremblement |
| 2 | Densifier le raccord terminal de la ligne | B (§4.3) | ★★ | Supprime le pivotement périodique |
| 3 | Near/far pilotés par le contenu réel de la near viewport | C | ★★ | Rend les constantes inutiles |
| 3 | Ribbon billboardé (`RND-4`) | §6 | ★★★ | Épaisseur stable + AA explicite |
| 4 | Reverse-Z ou logarithmic depth | C | ★★★★ | Précision quasi constante, en réserve |

### 9.1 Cause A — rendre l'offset frais

Le défaut n'est pas dans `LodView` : il est dans le fait qu'un consommateur de
position monde tourne **avant** le producteur de l'origine flottante. Deux
options, une seule recommandée.

**Recommandée.** `FloatingOriginAppState` ne lit plus l'ancre de la scène : il
calcule `p(t)` lui-même depuis l'éphéméride de la mission focalisée à
`clock.now()`, écrit `nearFrame`, et est **attaché avant**
`MissionOrchestratorAppState`. L'orchestrateur pose ensuite l'ancre à partir de
la même donnée, via la même conversion (`RenderTransform` + `AxisConvention`) :
les deux `float` restent bit à bit opposés, l'annulation exacte du §3.1 est
préservée, et `getWorldTranslation()` redevient juste dès le rang 7. C'est
aussi ce qui aligne la mission sur le fonctionnement déjà correct des planètes
(rang 9 > 8).

**Écartée.** Faire écrire `nearFrame` par l'orchestrateur entre `updatePose` et
`updateScreen` : plus court, mais éclate la propriété de l'origine flottante
entre deux états, contre la règle « une donnée partagée, un propriétaire ».

#### Fait le 2026-08-09

L'option recommandée, telle quelle :

- `FloatingOriginAppState.nearFrameOffset(MissionId)` lit l'éphéméride de la
  mission focalisée à `clock.now()` ; il ne touche plus au registre des
  renderers. `MissionRenderer.getAnchorSpatial()` n'avait plus d'appelant : il
  est supprimé.
- L'ordre d'attachement est inversé dans `OrbitLabApplication`, avec le
  commentaire qui dit pourquoi il ne doit pas être reperdu.
- Deux points de conversion partagés garantissent l'annulation exacte :
  `MissionEphemeris.displayPointAt(date)` (même point pour les deux états) et
  `JmeVectorAdapter.toJmeBodyRelativePosition(position, ctx)` (même triplet
  `float`). `SpacecraftPresenter` passe par le second au lieu d'inliner la
  conversion. `MissionRenderer.renderContextFor(entry)` porte le contexte de
  rendu, jusque-là construit à un seul endroit et désormais à deux.

**Écarts avec ce qui était prévu ici**, tous deux dans le sens de la prudence :

1. **Repli sur le dernier offset connu.** Une mission focalisée perd son
   éphéméride le temps d'un recalcul. Retomber à zéro pour ces frames poserait
   l'origine near au centre de la Terre — caméra à l'intérieur de la planète.
   `FloatingOriginAppState` conserve donc le dernier offset calculé et le
   réutilise tant qu'il ne peut pas en produire un nouveau. Le comportement
   d'avant correctif (garder la position périmée de l'ancre) était déjà celui-là
   par accident ; il est maintenant explicite.
2. **Le test ne verrouille pas l'ordre.** Ce paragraphe annonçait qu'un test
   sans OpenGL suffirait à verrouiller la non-régression : c'est faux.
   L'ordre vit dans la séquence d'attachement de `OrbitLabApplication`, dont
   `simpleInitApp` exige un contexte GL. `NearFrameOriginTest` verrouille ce
   qui l'est : l'annulation **exacte** (sans tolérance) de l'offset et de
   l'ancre, propriété fragile puisqu'elle repose désormais sur deux
   producteurs indépendants ; et il mesure, sur le graphe JME, ce que coûte une
   frame dans le mauvais ordre (~38 km à ×300, sous le seuil de LOD de 16 km).
   Contre un retour en arrière sur l'ordre lui-même, il n'y a que le
   commentaire à l'endroit de l'attachement.

### 9.2 Cause B — trajectoire relative au vaisseau

Soustraire `p` **en `double`** avant le cast `float`, et compenser par une
translation locale de la géométrie :

```java
// dans update(…), tip = position GCRF du vaisseau à l'instant courant
Vector3D origin = tip;                                   // double, exact
… putVertex(fb, trail.positionAt(i).subtract(origin));   // sommets petits près du vaisseau
lineGeometry.setLocalTranslation(sameVector3fAsAnchorLocal);   // + p, annule le −p de nearFrame
```

Propriétés :

- `nearFrame(−p) + ligne(+p) = 0` **exactement**, à condition d'utiliser le
  même triplet `float` que l'ancre — donc de le produire par le même chemin de
  conversion (à factoriser).
- L'erreur d'un sommet devient proportionnelle à sa distance au vaisseau, donc
  **son erreur angulaire vue de la caméra reste bornée** : c'est la propriété
  standard du rendu camera-relative.
- Coût nul : le buffer entier est déjà réécrit à chaque frame
  (`MissionTrajectoryRenderer:95-115`).
- Fonctionne sans branche en vue `PLANET` (`nearFrame = 0`, la ligne est posée
  à `+p`, position absolue correcte).

Pour §4.3, ajouter au raccord terminal quelques points interpolés entre
`trail[upTo]` et `tip` (l'éphéméride brute les a déjà, seule la polyline
d'affichage est décimée), ou simplement faire commencer le raccord au dernier
sommet **de l'éphéméride** plutôt que de la polyline.

#### Fait le 2026-08-09

Le §9.2 tel quel, dans `MissionTrajectoryRenderer.update` : soustraction de
`tip` en `double` avant le cast, sommet terminal écrit à `Vector3D.ZERO`, et
`lineGeometry.setLocalTranslation(toJmeBodyRelativePosition(tip, ctx))` — le
même chemin de conversion que l'ancre, donc l'annulation reste exacte. Repli
sur `trail[upTo]` quand `tip` est absent : prendre le géocentre pour origine
rendrait la précision gagnée.

`MissionTrajectoryOriginTest` verrouille les trois propriétés — sommets bornés
par l'étendue du tracé, sommet voisin placé au millimètre, sommet terminal
exactement sur l'origine near.

**Le §4.3 (raccord terminal) n'est pas fait** : c'est un défaut distinct, un
pivotement périodique et non un tremblement continu, et il survivra à ce
correctif-ci.

**Mesure obtenue en écrivant le test.** Avant correctif, un sommet à 1,5 km du
vaisseau est dessiné 0,19 m à côté de sa vraie place — dans la bande du
demi-mètre annoncée au §4.1. Un détail non prévu : un décalage de *exactement*
1 km ne montre rien du tout. À 6778 unités le pas du `float` vaut 2⁻¹¹ unité,
dont 1 unité est un multiple exact, si bien que les deux quantifications se
compensent. Il faut un décalage hors réseau — ce que sont les vrais
échantillons — pour que le défaut apparaisse. Toute mesure future de cet
artefact doit en tenir compte.

### 9.3 Cause C — resserrer le near

```java
private static final float NEAR_MIN = 0.005f;   // 5 m, plancher de sécurité
// …
float near = FastMath.clamp(distToOrigin * 0.2f, NEAR_MIN, NEAR_MAX);
float far  = FastMath.clamp(distToOrigin * 10f, FAR_MIN, FAR_MAX);   // inchangé (§5.3)
```

À 500 m de distance : `near = 100 m`, `Δz ≈ 27 km` à la Terre, la ligne LEO est
à 400 km au-dessus de la surface soit ~15 pas de quantification. La bataille
cesse. Le modèle du vaisseau (~100 m d'envergure, centré à l'origine) reste
entièrement devant le plan near tant que le facteur reste ≤ 0,3.

Limite connue : cette formule suppose que l'objet le plus proche est à
l'origine. C'est vrai en `SPACECRAFT`, pas en `PLANET` où l'origine est le
centre de la Terre et où le contenu le plus proche est sa surface, 6378 km plus
près que l'origine. Un near piloté par le contenu (niveau 3) lève l'hypothèse ;
tant qu'on ne l'écrit pas, le facteur doit rester conditionné au mode de vue.

#### Fait le 2026-08-09

Le facteur est bien conditionné au mode : `SPACECRAFT_NEAR_FACTOR = 0.2f`
contre `DEFAULT_NEAR_FACTOR = 0.0005f` ailleurs, `NEAR_MIN = 0.005f`, `FAR_MIN`
inchangé (§5.3). Le calcul est sorti dans `NearCameraSyncAppState.nearPlane`,
qui prend le `ViewMode` en paramètre ; l'état reçoit désormais
l'`ApplicationContext` pour le lire.

`NearFrustumDepthTest` verrouille ce que le plan achète, pas sa valeur : le
budget de profondeur au niveau de la Terre, la marge devant le modèle, et la
non-régression de la limite ci-dessus (en `PLANET`, le plan reste devant la
surface). Un quatrième test consigne §7.2 — diviser le far par deux ne déplace
pas le pas de profondeur d'un millième.

**Le chiffre du §5.2 est confirmé par la mesure** : avec l'ancien facteur, le
test rapporte 273,8 km par pas et 1,46 pas de dégagement pour une orbite à
400 km. Le diagnostic refait tenait.

**Écart avec ce qui était prévu ici, dans le sens de la prudence.** Le §9.3
plafonne le facteur (« le facteur doit rester ≤ 0,3 ») mais pas son produit, et
raisonne à la distance de focus de 500 m. La limite connue mord plus tôt qu'à
la Terre : en dézoomant de `d` km vers le nadir depuis un vaisseau à 400 km, le
sol sous la caméra n'est plus qu'à `400 − d`, qui décroît pendant que `0,2·d`
croît. Le test les fait se croiser à **334 km** — au-delà, le plan near
creuserait un trou dans la planète sous la caméra, soit un artefact échangé
contre un pire. D'où un `SPACECRAFT_NEAR_MAX = 1f` : inactif en deçà de 5 km de
dézoom, donc tout le bénéfice est conservé, et même quand il s'engage le pas de
profondeur à la Terre vaut ~2,7 km contre les 274 km visés. Le sweep est
verrouillé par `NearFrustumDepthTest`.

## 10. Recommandation

1. **Le §8 a été passé en recette, et il est concluant.** Il ne l'a pas été
   *avant* de coder, faute de pouvoir l'être : il demande l'application en
   marche et un œil humain. Les correctifs ont donc été écrits sur la foi des
   symptômes rapportés et de deux mesures obtenues sans OpenGL (§9.2, §9.3),
   qui reproduisent exactement les chiffres dérivés ici — puis vérifiés à
   l'écran. L'ordre était inconfortable ; le résultat tient.
2. **Ensuite, dans l'ordre** : ~~§9.1 (cause A)~~, ~~§9.3 (cause C)~~,
   ~~§9.2 (cause B)~~ — **les trois faites le 2026-08-09**. Reste, par ordre de
   rapport : `setDepthWrite(false)` (§9, niveau 1), le raccord terminal (§4.3),
   puis les niveaux 3-4.
3. **Conséquence sur la roadmap** : `RND-1` était coté ◆1 / taille S sur la
   base d'un diagnostic qui ne couvrait qu'une des trois causes, et dont deux
   des trois correctifs sont sans effet (§7). Le périmètre réel est
   ◆2 / taille M, et il faut y ajouter explicitement la disparition du modèle
   en accéléré, qui n'est pas un problème de z-fighting.
4. Reverse-Z, logarithmic depth et troisième viewport restent en réserve
   (ancienne version §5.3, toujours valable dans son analyse) : ils
   deviendront nécessaires le jour où la near viewport devra tenir Terre +
   Lune + vaisseau dans le même cadre.

## 11. Liens

- [`../roadmap/01-roadmap-v1.md`](../roadmap/01-roadmap-v1.md) — item `RND-1`, à
  recoter (§10.3).
- [`effects-roadmap.md`](effects-roadmap.md) §9 — backlog rendu trajectoires ;
  §9.4.1 (ribbon) reste la réponse au §6.
- [`../camera/01-view-transitions.md`](../camera/01-view-transitions.md) —
  sémantique des trois `ViewMode`.
- [`../atmosphere/01-impacts-fonctionnels-techniques.md`](../atmosphere/01-impacts-fonctionnels-techniques.md)
  — la future couche atmosphérique durcira les exigences de précision Z autour
  de la planète.
- Code : `states/mission/MissionRenderer.java`,
  `states/mission/MissionTrajectoryRenderer.java`,
  `engine/scene/body/LodView.java`,
  `engine/scene/body/lod/BillboardIconView.java`,
  `states/camera/FloatingOriginAppState.java`,
  `states/camera/NearCameraSyncAppState.java`,
  `engine/scene/graph/SceneGraph.java`,
  `OrbitLabApplication.java` (ordre d'attachement des états).
