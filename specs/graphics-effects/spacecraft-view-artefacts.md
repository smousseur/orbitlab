# Spec — Artefacts graphiques en vue Spacecraft

> Diagnostic de deux artefacts visuels observés en `ViewMode.SPACECRAFT`,
> et catalogue des correctifs envisageables, des plus simples (one-liner)
> aux plus structurants (refonte du pipeline de profondeur). Aucune
> implémentation engagée à ce stade — ce document sert de base de
> décision.

## 1. Symptômes observés

1. **La ligne de trajectoire du vaisseau scintille.** Pixels qui
   clignotent le long de la `LineStrip` quand la caméra bouge, surtout
   quand l'orbite passe près de la surface de la Terre (LEO basse).
2. **À partir d'une certaine distance, le modèle 3D de la Terre se
   déforme** : la sphère se couvre de motifs hexagonaux avec des
   "trous", comme si des faces internes du maillage perçaient à
   travers la surface visible. Symptôme classique de **Z-fighting**.

Les deux symptômes pointent vers la même famille de problèmes :
**précision insuffisante du depth buffer** dans la near viewport du
mode spacecraft.

## 2. Rappel — architecture viewports

Cf. `specs/camera/01-view-transitions.md` et `CLAUDE.md` §"Rendering:
Dual Viewport".

| | Far viewport | Near viewport |
|---|---|---|
| Échelle | 1 unité = 1e9 m (Mm × 1000) | 1 unité = 1e3 m (km) |
| Cible | Soleil, planètes lointaines, lignes d'orbites héliocentriques | Modèles 3D des corps, vaisseaux, trajectoires de mission |
| Cam | `cam` JME par défaut | Clone post-view (`NearView`) |
| Frustum | Adapté frame par frame (`OrbitCameraAppState.updateFrustum`) | Adapté frame par frame (`NearCameraSyncAppState`) |
| Origine flottante | `solarRoot` translaté de `-planet.position` en PLANET/SPACECRAFT | `nearFrame` translaté de `-spacecraft.position` en SPACECRAFT |

Le modèle 3D de la Terre est attaché à
`Model3dView.modelBucket` → `anchor3d` → `nearBucket` →
`nearBodiesNode` → `nearFrame` → `nearRoot` → near viewport.
La ligne de trajectoire est dans le même graphe via
`nearOrbitsNode` (`MissionTrajectoryRenderer.initialize` ligne 62).

## 3. Diagnostic — ligne de trajectoire

### 3.1 Code en cause

`states/mission/MissionTrajectoryRenderer.java:48-63` :

```java
Mesh mesh = new Mesh();
mesh.setMode(Mesh.Mode.LineStrip);
// …
Material mat = AssetFactory.get().material(color);
mat.setColor("Color", color);
mat.getAdditionalRenderState().setLineWidth(LINE_WIDTH);   // 2 px

lineGeometry = new Geometry("MissionTrajectory-" + missionName, mesh);
lineGeometry.setMaterial(mat);
nearOrbitsNode.attachChild(lineGeometry);
```

`AssetFactory.material(...)` ne configure **ni `setDepthWrite`, ni
`setPolyOffset`** (cf. `engine/AssetFactory.java:65-69`). Comparer avec
`alphaMaterial(...)` qui désactive explicitement le depth write — la
méthode `material` "non alpha" reste sur les défauts JME (depth test
on, depth write on, pas de polygon offset).

### 3.2 Mécanique de l'artefact

Sources cumulables du scintillement :

- **Z-fighting ligne ↔ surface Terre**. Pour une orbite LEO à 400 km,
  la ligne est à r=6778 km alors que la surface est à r=6378 km. La
  différence relative à la profondeur caméra est faible (typiquement
  ≪ 1 %). Avec la précision actuelle du depth buffer dans la near
  viewport (cf. §4), c'est dans la zone de bataille.
- **Z-fighting ligne ↔ ligne**. La trajectoire forme des boucles ;
  des segments distincts se projettent sur les mêmes pixels avec des
  z très proches. Avec `setDepthWrite(true)`, chaque segment écrit
  sa profondeur et le suivant tape sur celle d'avant à 1 ulp près.
- **Rasterisation de `glLineWidth > 1`**. Sur les drivers modernes
  (Mesa core, NVIDIA core profile, macOS), `setLineWidth(2f)` est
  silencieusement clampé à 1.0 ; sur les drivers qui l'honorent,
  l'expansion en quad introduit un anti-aliasing dont les pixels de
  bord oscillent quand la ligne se déplace sub-pixel à l'écran. Le
  symptôme "scintille en mouvement" peut venir de là indépendamment
  du Z-buffer.

## 4. Diagnostic — déformation de la Terre

### 4.1 Code en cause

`states/camera/NearCameraSyncAppState.java:31-35,70-73` :

```java
private static final float NEAR_MIN = 0.01f;          // 10 m
private static final float NEAR_MAX = 500f;           // 500 km
private static final float FAR_MIN  = 100_000f;       // 100 000 km
private static final float FAR_MAX  = 100_000_000f;   // 100 millions km
// …
float distToOrigin = nearCam.getLocation().length();
float near = FastMath.clamp(distToOrigin * 0.0005f, NEAR_MIN, NEAR_MAX);
float far  = FastMath.clamp(distToOrigin * 10f,    FAR_MIN, FAR_MAX);
nearCam.setFrustumPerspective(fovYDeg, aspect, near, far);
```

### 4.2 Mécanique de l'artefact

Le depth buffer OpenGL standard fait une projection **non linéaire** :
la précision se concentre près du near plane et chute en `z²` quand on
s'éloigne.

Ordre de grandeur réaliste en spacecraft view, vaisseau en LEO, caméra
à `distToOrigin ≈ 20 000 km` :

| Quantité | Valeur |
|---|---|
| `near` | `20 000 × 0.0005 = 10 km` |
| `far`  | clamp `200 000 → max(100 000, ·)` = `200 000 km`, mais en pratique souvent `FAR_MIN = 100 000 km` |
| Ratio `far / near` | ~10 000 à 20 000 |
| Surface Terre vue de la caméra | profondeur dans `[~14 000, ~26 000]` km |
| Résolution depth à 20 000 km (depth 24 bits) | **~300–500 m** |

300 m de résolution Z à la surface d'un GLTF planète dont les triangles
adjacents diffèrent en profondeur de quelques mètres (sphère/icosphère
subdivisée) = **Z-fighting généralisé**. Les "trous hexagonaux"
correspondent aux faces du maillage géodésique du GLTF (typique d'une
sphère subdivisée — icosphère ou Goldberg polyhedron) dont certaines
gagnent localement le test de profondeur sur leurs voisines.

### 4.3 Pourquoi le near est si petit

Le facteur `0.0005f` et `NEAR_MIN = 0.01f` (10 m) sont sur-dimensionnés
pour les détails du vaisseau. En pratique :

- Le vaisseau est rendu à son ancrage `MissionRenderer.getAnchorSpatial()`,
  qui est à l'origine de la `nearFrame` en SPACECRAFT mode (cf.
  `FloatingOriginAppState.update` ligne 87-95). Sa taille effective
  est de l'ordre de la dizaine de mètres → la caméra ne descend
  presque jamais à < 1 km.
- Aucun autre objet n'est aussi proche de l'origine.

Le near plane à 10 m n'est jamais "utile" mais coûte cher en précision
à la profondeur de la Terre.

### 4.4 Pourquoi le far est si grand

`FAR_MIN = 100 000 km` est fixé pour que la Lune (~384 000 km) ou des
satellites lointains restent visibles dans la near viewport. Mais il
est appliqué **inconditionnellement**, même quand le vaisseau est en
LEO sans rien d'autre à voir à grande distance.

### 4.5 Confirmation indirecte

`FloatingOriginAppState.update` ligne 70 commente déjà le risque :

```java
// Ensure the far frustum is large enough to encompass distant orbits and bodies.
orbitCam.setFarFloor(PLANET_MODE_FAR_MIN);   // = 50 000 (solar units)
```

Le constat — depth precision écrasée par un far plane élevé — est
**connu côté far viewport** mais pas répliqué côté near.

## 5. Catalogue de correctifs

Classés du moins invasif au plus structurant.

| Niveau | Correctif | Cible | Difficulté | Effet attendu |
|---|---|---|---|---|
| 1 | `setDepthWrite(false)` + `setPolyOffset(-1, -1)` sur le matériau ligne | Ligne | ★ | Tue le Z-fighting ligne ↔ surface |
| 1 | Bucket `Transparent` pour la ligne | Ligne | ★ | Dessine après les opaques, depth-test seul |
| 1 | Resserrer `NEAR_MIN`, durcir le facteur near (`0.0005 → 0.005`), descendre `FAR_MIN` | Terre | ★ | Récupère 1–2 ordres de grandeur de précision |
| 2 | Ribbon billboardé à la place des GL lines | Ligne | ★★★ | Élimine l'aliasing `glLineWidth` + AA explicite |
| 2 | Near plane adaptatif par contenu (closest object) au lieu de `distToOrigin` | Terre | ★★ | Précision optimale frame par frame |
| 3 | Reverse-Z + depth buffer `D32F` | Terre + ligne | ★★★★ | Précision quasi linéaire ; supprime quasi tout le Z-fighting |
| 3 | Logarithmic depth buffer (shader patch sur tous les matériaux near) | Terre + ligne | ★★★★ | Idem ; intrusif côté matériaux |
| 3 | Troisième viewport "spacecraft proche" (cascade 3 niveaux) | Terre + ligne | ★★★★ | Plage de profondeur réduite par viewport |
| 4 | Refonte rendu trajectoire en mesh procédural body-relative | Ligne | ★★★★ | Re-centre les calculs sur le vaisseau (FP precision) |

### 5.1 Tier 1 — Quick wins

#### 5.1.1 Patch matériau ligne

`MissionTrajectoryRenderer.initialize` :

```java
mat.getAdditionalRenderState().setDepthTest(true);
mat.getAdditionalRenderState().setDepthWrite(false);
mat.getAdditionalRenderState().setPolyOffset(-1f, -1f);   // bias vers la caméra
lineGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
```

- `DepthWrite(false)` : la ligne ne pollue pas le depth buffer, donc
  pas de bataille ligne ↔ ligne ni ligne ↔ Terre en self-occlusion.
- `PolyOffset(-1, -1)` : décale le z des fragments d'une fraction
  d'ulp vers l'avant. Standard pour les "lignes sur surface".
- `Bucket.Transparent` : trie en arrière → avant et dessine après les
  opaques.

Coût : 4 lignes, aucune incidence sur les autres rendus.

Limitation : la ligne reste visible **à travers** la Terre quand elle
passe derrière (depth test on, depth write off). Pour le masquer, il
faudrait soit garder `DepthWrite(true)` et accepter le bias seul, soit
faire deux passes (visible + occulté à alpha bas) → cf. `specs/
graphics-effects/effects-roadmap.md` §9.3.2. Premier choix simple :
laisser la ligne traverser, débugger ensuite.

#### 5.1.2 Resserrer le frustum near

`NearCameraSyncAppState` — proposition de valeurs :

```java
private static final float NEAR_MIN = 1.0f;          // 1 km (au lieu de 10 m)
private static final float NEAR_MAX = 500f;          // inchangé
private static final float FAR_MIN  = 50_000f;       // 50 000 km (au lieu de 100 000)
private static final float FAR_MAX  = 100_000_000f;  // inchangé
// …
float near = FastMath.clamp(distToOrigin * 0.005f,  NEAR_MIN, NEAR_MAX);   // ×10
float far  = FastMath.clamp(distToOrigin * 10f,     FAR_MIN, FAR_MAX);
```

Impact attendu sur un cas LEO (`distToOrigin = 20 000 km`) :

| Avant | Après |
|---|---|
| `near = 10 km`, `far = 100 000 km` | `near = 100 km`, `far = 100 000 km` |
| Ratio ~10 000 | Ratio ~1 000 |
| Résolution Z à la Terre ~300–500 m | Résolution Z à la Terre ~30–50 m |

C'est suffisant pour battre les triangles GLTF dans la grande majorité
des cas. Trade-off : on ne peut plus zoomer sous ~1 km du vaisseau ;
acceptable tant qu'on ne fait pas de "fly-by caméra rapprochée" du
vaisseau lui-même.

Si la limite `1 km` est trop grossière pour des futurs zooms vaisseau,
on peut conditionner `NEAR_MIN` à l'échelle du vaisseau focalisé
plutôt qu'utiliser une constante (cf. §5.2.2).

### 5.2 Tier 2 — Adaptations

#### 5.2.1 Ribbon billboardé

Déjà documenté dans `effects-roadmap.md` §9.4.1. Résout
indépendamment :
- Le scintillement résiduel dû à l'aliasing `glLineWidth` après le
  patch §5.1.1.
- L'épaisseur constante à grande distance.
- L'AA gratuit via alpha-fade des bords.

#### 5.2.2 Near plane piloté par le contenu

Au lieu d'utiliser `distToOrigin` comme proxy, calculer chaque frame
le plus proche objet de la caméra parmi un petit ensemble pertinent
(vaisseau focalisé + planète parent) :

```java
float dClosestObject = min(
    distance(nearCam, spacecraftAnchor),
    distance(nearCam, planetSurfaceTangent)   // distance - radius
);
float near = max(NEAR_MIN, dClosestObject * 0.5f);
```

Avantage : on n'a plus à choisir un compromis global `NEAR_MIN`. Le
near plane suit ce que la caméra regarde vraiment. Coût : une boucle
de quelques distances par frame, négligeable.

Le `far` peut suivre la même logique en cherchant l'objet **le plus
loin** intéressant à voir dans la near viewport (Lune ? GEO ?), ce
qui rend `FAR_MIN` lui aussi inutile.

### 5.3 Tier 3 — Refonte du pipeline de profondeur

Les solutions ci-dessus repoussent le mur mais ne le suppriment pas :
dès qu'on aura besoin d'à la fois voir le vaisseau de près **et** la
Terre nettement **et** la Lune au loin, le ratio far/near explosera
de nouveau.

#### 5.3.1 Reverse-Z + depth buffer flottant

Convention "reverse-Z" : on inverse la signification du depth buffer
(near → 1.0, far → 0.0) et on utilise un format **D32F** au lieu de
D24/D24S8. L'association des deux donne une précision **quasi
constante en world space** sur toute la plage du frustum — c'est
mathématiquement la combinaison optimale pour le rendu à grande
dynamique de profondeur (cf. articles de Nathan Reed, Eric Lengyel).

Côté JME3 :
- Setter `glDepthFunc(GL_GEQUAL)` + `glClearDepth(0.0)` côté renderer.
- Demander un FrameBuffer avec un attachement depth `Image.Format.Depth32F`.
- Patcher la projection (`Camera.getProjectionMatrix`) ou injecter une
  matrice de projection reverse-Z (swap des deux dernières lignes).
- JME ne supporte pas reverse-Z out-of-the-box → il faut surcharger
  le `Renderer` et écrire un petit pipeline custom. Plusieurs jours
  de R&D, mais le résultat est définitif.

Compatibilité : OpenGL 4.5+ ou ARB_clip_control. Tous les GPU desktop
modernes l'ont depuis 2015.

#### 5.3.2 Logarithmic depth buffer

Alternative reverse-Z, plus simple à câbler mais avec des artefacts
au polygon-clipping (les arêtes de gros polygones peuvent passer
"derrière" le near plane en interpolation linéaire alors qu'elles
devraient être visibles). Mitigation : géométrie suffisamment
tessellée OU calculer le log-z par fragment.

Implémentation : dans chaque vertex shader, après projection :

```glsl
gl_Position.z = (log(C * gl_Position.w + 1.0) / log(C * far + 1.0)) * 2.0 - 1.0;
gl_Position.z *= gl_Position.w;
```

Avantage vs reverse-Z : un seul patch dans les shaders, pas besoin de
modifier le pipeline depth côté Renderer. Inconvénient : doit toucher
**tous** les matériaux utilisés dans la near viewport. Avec
`Unshaded.j3md` partout, c'est un fork de ce j3md + un nouveau
include GLSL. Acceptable.

C'est la solution adoptée par la plupart des moteurs de simulation
"galactique" (Outerra, SpaceEngine) avant qu'ils migrent vers
reverse-Z.

#### 5.3.3 Cascade de viewports

Au lieu de 2 viewports (far + near), en avoir 3 :

| Viewport | Échelle | Contenu |
|---|---|---|
| Far | 1 unité = 1e9 m | Soleil, orbites héliocentriques, planètes lointaines |
| Mid (nouveau) | 1 unité = 1e6 m | Système Terre-Lune, satellites éloignés |
| Near | 1 unité = 1e3 m | Vaisseau, Terre proche, atmosphère, trajectoires de mission |

Chaque viewport a son frustum borné aux objets qu'il contient → le
ratio far/near de chacun reste sain. Coût : un troisième render pass,
un troisième `Camera` à sync, un troisième `Frame` à translater pour
la floating origin. C'est l'option la plus "physique" : on n'enrichit
pas la précision, on segmente les échelles. Synergie avec `specs/camera/
01-view-transitions.md` qui définit déjà le saut SOLAR ↔ PLANET ↔
SPACECRAFT — un viewport "mid" cadrerait bien avec la transition
PLANET.

Inconvénient : aujourd'hui le code couple chaque viewport à un seul
`RenderContext` (`SOLAR_METERS_PER_UNIT` vs `PLANET_METERS_PER_UNIT`).
Ajouter un troisième context signifie compléter `RenderContext`
(`Solar`, `Planet`, `Spacecraft`?), `RenderTransform`, et tous les
`Presenter` qui scalent les positions. Plusieurs jours, mais
extensible "naturellement" plus tard.

### 5.4 Tier 4 — Rendu trajectoire body-relative

Aujourd'hui les positions de la trajectoire sont stockées en GCRF
absolu (mètres → km via `RenderTransform.scaleMetersToUnits` dans
`MissionTrajectoryRenderer.update` ligne 98), puis la `nearFrame` est
translatée pour ramener le vaisseau à l'origine. La précision
`float32` du buffer GPU à ~7 000 km est ~1 m — suffisante pour la
géométrie mais marginale pour la profondeur après projection.

Stocker la trajectoire **directement en spacecraft-relative** (déjà
décalé côté CPU avant cast `float`) supprime cette source d'erreur.
Côté CPU on garde le `double` Vector3D ; on soustrait `spacecraft.position`
en double avant de caster. Pratique : adapté quand on aura déjà la
souplesse pour décorréler l'origine de la `LineStrip` du `nearFrame`.

Pas urgent — utile surtout après §5.3 (sinon dominé par le bruit
depth).

## 6. Solution robuste recommandée

À moyen terme, la combinaison la plus solide et la moins risquée :

1. **Maintenant** — appliquer §5.1.1 (patch matériau ligne) et
   §5.1.2 (frustum near plus serré). Suffisant pour 90 % des cas
   d'usage actuels. Code minimal, isolé, réversible.
2. **Quand on attaque le ribbon** (effects-roadmap §9.4.1) —
   intégrer §5.2.2 (near plane piloté par le contenu) en même temps,
   parce que les deux touchent les mêmes fichiers (frustum sync +
   factory ligne).
3. **Si on commence à voir des artefacts persistants** (vue ultra-large
   sur Terre + Lune + Mars dans le même cadre, ou zooms vaisseau
   serrés) — passer à §5.3.2 (logarithmic depth). C'est le plus petit
   pas vers une précision "définitive" sans toucher au pipeline JME.

§5.3.1 (reverse-Z) et §5.3.3 (3 viewports) restent en réserve pour
le jour où le contenu de la near viewport gagne en richesse
(atmosphère, ombres, gros zoom sol-vaisseau).

## 7. Liens

- `specs/camera/01-view-transitions.md` — sémantique des trois
  `ViewMode` (SOLAR/PLANET/SPACECRAFT) et de la cascade actuelle de
  caméras.
- `specs/graphics-effects/effects-roadmap.md` §9 — backlog rendu
  trajectoires (ribbon, vertex colors, passé/futur). §5.1.1 ci-dessus
  est un prérequis hygiène avant tout enrichissement de la ligne.
- `specs/atmosphere/01-impacts-fonctionnels-techniques.md` — la
  future couche atmosphérique (halo Fresnel, scattering) imposera des
  exigences supplémentaires sur la précision Z autour de la planète ;
  donne du poids à §5.3.
- Code : `states/mission/MissionTrajectoryRenderer.java`,
  `engine/AssetFactory.java`, `states/camera/NearCameraSyncAppState.java`,
  `states/camera/FloatingOriginAppState.java`,
  `engine/scene/body/lod/Model3dView.java`.
