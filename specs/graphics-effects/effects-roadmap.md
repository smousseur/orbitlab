# Spec — Roadmap effets graphiques

## 1. Contexte

Le rendu actuel d'OrbitLab est fonctionnel mais minimal :

- **Éclairage** : un seul `LightningAppState`
  (`states/fx/LightningAppState.java`) qui applique une `AmbientLight` à
  0,3× et une `DirectionalLight` à 1,2× orientée chaque frame depuis le
  Soleil vers le corps focalisé.
- **Matériaux** : tout passe par `Common/MatDefs/Misc/Unshaded.j3md`
  (`engine/AssetFactory.java`, `engine/scene/body/lod/Model3dView.java`).
  Aucune réponse à la lumière, pas de normal map, pas de specular, pas
  de fall-off d'ombre.
- **Pas de skybox** — le fond est noir uni.
- **Pas de post-processing** (ni bloom, ni tone-mapping, ni god-rays).
- **Pas de shaders custom** — aucun fichier `.j3md` / `.frag` / `.vert`
  dans le dépôt.
- **Pas de particules** (tuyère, traînée de rentrée, exhaust).
- **Double viewport** (far 1–50 000 / near 0,1–20 000) géré par
  `FloatingOriginAppState` — toute FX doit cohabiter avec les deux
  caméras et le re-centrage flottant.

Ce document liste les effets candidats pour améliorer le rendu, classés
en tiers par rapport **wow / difficulté**, avec pour chacun un moyen
technique succinct (mécanisme JME3, shader, ou asset) et les zones du
code touchées. Il sert de **backlog** dans lequel piocher — il ne
décide pas d'un ordre d'implémentation engagé.

**Légende**

- ★ Difficulté : 1 (trivial, < 1 j) → 5 (gros chantier, plusieurs jours
  + R&D shader).
- ✨ Wow : 1 (à peine perceptible) → 5 (change radicalement le ressenti
  de l'app).

---

## 2. Vue d'ensemble — tableau ranké

| Tier | Effet | ★ | ✨ | Mécanisme principal |
|---|---|---|---|---|
| 1 | Skybox étoilée | 1 | 4 | `SkyFactory` + cubemap |
| 1 | Lambert sur planètes (fall-off Soleil) | 1 | 4 | `Lighting.j3md` (built-in) |
| 1 | Bloom sur le Soleil | 1 | 3 | `BloomFilter` (built-in) |
| 1 | MSAA + filtrage anisotrope | 1 | 2 | `AppSettings` |
| 1 | Particules de tuyère | 2 | 4 | `ParticleEmitter` (built-in) |
| 2 | God-rays / rayons solaires | 2 | 4 | `LightScatteringFilter` |
| 2 | Halo atmosphérique (Fresnel) | 3 | 5 | Shader custom (sphère + alpha Fresnel) |
| 2 | Lumières de villes côté nuit | 2 | 4 | Texture émissive masquée par `1 - N·L` |
| 2 | Normal maps planètes | 2 | 3 | Slot `NormalMap` de `Lighting.j3md` |
| 2 | HDR + tone mapping | 2 | 3 | `ToneMapFilter` |
| 3 | Ombres portées (shadow map) | 4 | 3 | `DirectionalLightShadowRenderer` |
| 3 | Diffusion atmosphérique (Rayleigh+Mie) | 5 | 5 | Shader GLSL custom (raymarch shell) |
| 3 | Anneaux de Saturne | 3 | 4 | Disque + texture alpha + ombre projetée |
| 3 | Plume volumétrique des moteurs | 4 | 4 | Shader noise / billboards animés |
| 3 | Traînée plasma de rentrée | 4 | 5 | Particules émissives + heat-haze |
| 4 | Nuages volumétriques sur Terre | 5 | 5 | Raymarch 3D-noise (coûteux) |
| 4 | Specular océanique | 4 | 4 | Shader Fresnel + sun-disk highlight |
| 4 | Éclipses / pénombre inter-corps | 4 | 4 | Occlusion géométrique + atténuation shader |
| 4 | Lens flare avec occlusion | 3 | 3 | Post-FX screen-space, fadé sur visibilité Soleil |
| 4 | Terrain procédural en approche | 5 | 4 | Heightmap + LOD swap |

---

## 3. Tier 1 — Quick wins (gros impact, faible coût)

### 3.1 Skybox étoilée

**Pourquoi** — Aujourd'hui le fond est noir uni : impossible de juger
les rotations de la scène ni de "sentir" l'échelle. Une skybox
résout les deux d'un coup.

**Comment** — `com.jme3.util.SkyFactory.createSky(...)` avec une
cubemap (6 textures ou DDS). On enveloppe le viewport `far`. Asset à
fournir (Tycho, ESO Milky Way, ou cubemap procédurale offline).

**Code à toucher** — Nouveau `SkyboxAppState` dans `states/fx/`,
enregistré dans `OrbitLabApplication`. Asset sous
`assets/textures/sky/`.

---

### 3.2 Lambert sur planètes (fall-off du Soleil)

**Pourquoi** — C'est l'effet "fall-off d'ombre par rapport à
l'éclairage du Soleil" mentionné dans la demande. Aujourd'hui les
planètes sont éclairées uniformément ; la ligne terminator
(jour/nuit) n'apparaît pas.

**Comment** — Remplacer `Unshaded.j3md` par
`Common/MatDefs/Light/Lighting.j3md` dans `Model3dView` (et la
factory de matériaux des planètes). La `DirectionalLight` du
Soleil déjà gérée par `LightningAppState` produit alors le
fall-off Lambert (`max(0, dot(N, L))`) gratuitement, plus la
contribution ambient.

**Code à toucher** — `engine/AssetFactory.java`,
`engine/scene/body/lod/Model3dView.java`. Vérifier que les normales
des GLTF planètes sont correctes.

---

### 3.3 Bloom (glow du Soleil)

**Pourquoi** — Le Soleil est aujourd'hui un disque mat. Un bloom le
rend lumineux et donne une cohérence visuelle "spatiale".

**Comment** — `FilterPostProcessor` + `BloomFilter` (built-in JME3).
Seuil élevé pour ne capter que le Soleil. À appliquer sur le viewport
`far`.

**Code à toucher** — Nouveau `PostFxAppState` dans `states/fx/`
(centralisera ensuite tone-mapping, god-rays, etc.).

---

### 3.4 MSAA + filtrage anisotrope

**Pourquoi** — Les lignes d'orbite et les bords de planètes
crénèlent ; les textures vues en biseau bavent.

**Comment** — `AppSettings.setSamples(4)` pour MSAA ;
`Material.setFloat("AnisotropicFilter", 8)` ou réglage global via
texture state. Une ligne dans `EngineConfig` / au boot.

**Code à toucher** — `engine/EngineConfig.java`,
`OrbitLabApplication` (settings).

---

### 3.5 Particules de tuyère

**Pourquoi** — Les vaisseaux glissent silencieusement. Une flamme
animée transforme la perception du moteur poussé/coasté.

**Comment** — `com.jme3.effect.ParticleEmitter` attaché au node du
vaisseau, blending `Additive`, sprite radial. Activation pilotée par
la phase de mission (poussée active vs coast). On peut moduler le
débit par la magnitude de la poussée.

**Code à toucher** — `engine/scene/spacecraft/SpacecraftPresenter.java`
(point d'attache), nouvelle classe `EngineExhaustView` dans
`engine/scene/spacecraft/`. Hook sur l'état de la phase courante via
`MissionContext`.

---

## 4. Tier 2 — Polish moyen

### 4.1 God-rays / rayons solaires

`com.jme3.post.filters.LightScatteringFilter`, ancré sur la position
écran du Soleil. Built-in, peu coûteux. Combiné au bloom = effet
"soleil qui perce".

### 4.2 Halo atmosphérique (Fresnel)

Sphère légèrement plus grande que la planète, matériau additif, alpha
= `pow(1 - dot(N, V), k)` où `V` est la direction caméra. Couleur
modulée par `dot(N, L)` pour que le halo s'estompe côté nuit. Premier
shader custom à écrire — fragment court (~20 lignes). Cible :
Terre, Vénus, Mars, Titan.

**Assets à créer** — `assets/shaders/atmosphere/Atmosphere.j3md` +
`.frag` + `.vert`.

### 4.3 Lumières de villes côté nuit

Sample d'une texture émissive multiplée par `1 - max(0, dot(N, L))`
pour ne briller que côté nuit. `Lighting.j3md` expose déjà
`GlowMap`/`EmissiveMap` — utilisable directement avec un masque.
Pour la Terre : asset NASA Black Marble.

### 4.4 Normal maps planètes

Slot `NormalMap` de `Lighting.j3md`. Asset par planète (Earth,
Mars, Mercure, Lune). Effet sensible surtout en approche rapprochée
(viewport near).

### 4.5 HDR + tone mapping

`ToneMapFilter` (built-in). Permet de pousser l'intensité du Soleil
au-delà de 1.0 sans cramer les planètes. Synergie forte avec bloom.

---

## 5. Tier 3 — Avancé

### 5.1 Ombres portées (shadow map directionnelle)

`DirectionalLightShadowRenderer` avec PSSM (Parallel-Split Shadow
Map). Permet à un vaisseau de projeter une ombre sur la planète et
réciproquement. Difficulté principale : **les deux viewports + la
floating origin** rendent le frustum-fitting non trivial. Nécessite
de forcer le shadow renderer sur le viewport `near` uniquement et
de gérer les distances en world-units après re-centrage
(`FloatingOriginAppState`).

### 5.2 Diffusion atmosphérique (Rayleigh + Mie)

L'effet "haut de gamme" : ciel bleu vu de l'espace, transition
crépuscule rose-orange à la terminator. Shader GLSL custom à
raymarcher dans une coque atmosphérique (réf. *GPU Gems 2*, ch. 16,
Sean O'Neil ; ou implémentation Bruneton plus moderne). Coût GPU
modéré si raymarch peu d'étapes (16) et tables précalculées.

**Assets à créer** — `assets/shaders/atmosphere/Scattering.j3md`,
LUT précalculée (transmittance + scattering).

### 5.3 Anneaux de Saturne

Disque (mesh `Quad` ou anneau triangulé) + texture alpha (gradient
radial). Ombre du corps **sur** les anneaux : projeter la position
du Soleil dans l'UV du disque et atténuer où la planète occulte.
Ombre **des** anneaux sur Saturne : projection inverse dans le
shader planète. Asset texture libre dispo (Cassini).

### 5.4 Plume volumétrique des moteurs

Remplace les particules simples par des billboards animés en bruit
3D, ou un raymarch court d'un volume cylindrique. Beaucoup plus
crédible en vue rapprochée du vaisseau. Coût modéré ; shader custom.

### 5.5 Traînée plasma de rentrée

Activée pendant la phase de rentrée (altitude < ~120 km, vitesse
> ~5 km/s). Particules émissives orange/blanc dans le sillage +
léger heat-haze (post-FX de distorsion screen-space autour du
vaisseau). Hook sur la phase de mission.

---

## 6. Tier 4 — Ambitieux / showcase

### 6.1 Nuages volumétriques sur Terre

Raymarch 3D-noise dans une coque entre 0 et ~12 km d'altitude.
Coûteux (jusqu'à plusieurs ms GPU) mais iconique. À ne calculer
que sur le viewport `near` quand la caméra est proche de la Terre.

### 6.2 Specular océanique

Shader Fresnel + reflet du Soleil (sun-disk highlight) sur le
masque eau de la Terre. La tache spéculaire suit la position du
Soleil et donne immédiatement l'impression d'un océan vivant.

### 6.3 Éclipses / pénombre inter-corps

Lorsqu'un corps passe dans le cône d'ombre d'un autre (vaisseau dans
l'ombre de la Terre, Lune dans l'ombre de la Terre…), atténuer
géométriquement la `DirectionalLight` reçue. Calcul analytique
sphère-cône en CPU, valeur passée comme uniform au shader.

### 6.4 Lens flare avec occlusion

Ghosts/halos screen-space autour du Soleil, fadés en fonction d'un
test d'occlusion (raycast vers Soleil). Effet "cinématique".

### 6.5 Terrain procédural en approche

Quand la caméra passe sous un seuil d'altitude, swap LOD vers un
mesh tessellé à partir d'un heightmap (Earth/Mars). Gros chantier
(LOD continu, streaming) mais débloque les vues "atterrissage".

---

## 7. Notes transverses

### 7.1 Double viewport et floating origin

- Tout `FilterPostProcessor` doit être attaché au **bon** viewport.
  Bloom et god-rays vont sur `far` ; shadow map et nuages vont sur
  `near`.
- Les shaders qui dépendent de la position monde doivent prendre en
  compte le re-centrage opéré par `FloatingOriginAppState` — passer
  systématiquement en uniforms les positions corrigées (relatives
  à l'origine flottante), pas les positions Orekit brutes.

### 7.2 Pipeline d'assets

- `src/main/resources/` est `.gitignore`'d → tout asset (cubemap,
  normal maps, émissive, sprite d'exhaust, textures de halo) doit
  être documenté hors-dépôt et chargé via `AssetFactory`.
- Prévoir une convention `assets/textures/{sky,planets,fx}/...` et
  `assets/shaders/{atmosphere,fx}/...`.

### 7.3 Convention AppState

Chaque effet ayant une logique runtime → un `AppState` dans
`states/fx/`, enregistré dans `OrbitLabApplication`, qui suit la
règle **pas de `getState()`** : toute donnée partagée passe par
`ApplicationContext` (cf. `CLAUDE.md`).

---

## 8. Suggestion d'ordre d'attaque

Ordre suggéré pour maximiser l'impact visuel par jour-homme investi
(non engageant) :

1. **Skybox étoilée** (3.1) — change immédiatement le ressenti.
2. **Lambert sur planètes** (3.2) — fait apparaître les terminators.
3. **Bloom + tone-mapping** (3.3 + 4.5) — donne du HDR au Soleil.
4. **MSAA + anisotrope** (3.4) — propre les bords/orbites.
5. **Particules de tuyère** (3.5) — anime les vaisseaux.
6. **God-rays** (4.1) — synergie immédiate avec bloom.
7. **Halo atmosphérique Fresnel** (4.2) — premier shader custom,
   marche sur Terre/Vénus/Mars.
8. **Normal maps + lumières de villes** (4.3 + 4.4) — détail des
   planètes en approche.
9. **Ombres portées** (5.1) — le pas vers le rendu "scientifique".
10. **Diffusion atmosphérique complète** (5.2) — pic d'effet wow.

Au-delà (tier 4), ce sont des chantiers à isoler dans des specs
dédiées.

---

## 9. Rendu des trajectoires & orbites

### 9.1 État actuel

Le rendu des chemins (orbites planétaires + trajectoires de mission)
est aujourd'hui réduit au strict minimum :

- **Géométrie** : `Mesh.Mode.LineStrip` (ou `LineLoop` pour les
  orbites fermées body-relative), construite par
  `engine/scene/OrbitLineFactory.java`. Trajectoires de mission
  buildées par `states/mission/MissionTrajectoryRenderer.java` à
  partir des `MissionEphemerisPoint`
  (`simulation/mission/ephemeris/MissionEphemerisPoint.java`).
- **Matériau** : `Common/MatDefs/Misc/Unshaded.j3md`, couleur
  uniforme via `setColor("Color", ...)`. Largeur en **pixels**
  (`RenderState.setLineWidth`) — donc constante à l'écran, pas de
  taper en biseau, drivers modernes plafonnent souvent à 1 px.
- **Couleur** : par planète via `engine/scene/PlanetColors.java`,
  par mission via une `ColorRGBA` unique passée au
  `MissionRenderer` — **uniforme sur toute la trajectoire**, alors
  que `MissionEphemerisPoint` porte déjà un `stageName` exploitable.
- **Pas d'antialiasing**, pas d'alpha, pas de fade, pas de marqueur
  de direction, pas de distinction passé / futur, pas de marqueur
  d'apoapse / périapse, pas de trace au sol.

### 9.2 Tableau ranké

| Tier | Effet | ★ | ✨ | Mécanisme principal |
|---|---|---|---|---|
| 1 | Couleur par stage de mission | 1 | 4 | Vertex colors + map `stageName → RGBA` |
| 1 | Distinction passé / futur | 1 | 4 | Vertex colors / alpha modulés par t vs `clock.now()` |
| 1 | Marqueur "now" (curseur temps) | 1 | 3 | `Sphere` ou billboard à la position courante |
| 1 | Marqueurs apoapse / périapse | 2 | 3 | Billboards aux extrema, calculés depuis l'`OrbitPath` |
| 2 | Ribbon billboardé (épaisseur stable) | 3 | 4 | Mesh tri-strip face-camera, remplace les GL lines |
| 2 | Tirets animés (flow-along) | 2 | 4 | Texture 1D + offset UV piloté par le temps |
| 2 | Halo additif sous la ligne | 2 | 4 | Ribbon plus large, additif, faible alpha |
| 2 | Trace au sol (ground track) | 3 | 4 | Projection sur la sphère planète + ligne secondaire |
| 2 | Flèches directionnelles périodiques | 2 | 3 | Mesh triangle billboardé tous les N points |
| 3 | Code couleur thrust / coast | 2 | 4 | Vertex colors selon phase (`stage.thrust > 0`) |
| 3 | Gradient vitesse / altitude | 2 | 3 | Vertex colors via colormap (viridis / heat) |
| 3 | Mise en avant mission sélectionnée | 2 | 4 | Boost largeur+glow sur mission active, dim sur les autres |
| 3 | Marqueurs Δv aux nœuds de manœuvre | 3 | 4 | Billboard 3D fléché à chaque transition de stage |
| 3 | Fenêtrage temporel (±N h autour de now) | 2 | 3 | Sous-échantillonner + alpha-fade aux bords |
| 4 | Enveloppe d'incertitude (sweep optimizer) | 4 | 5 | Tube semi-transparent autour du nominal |
| 4 | Traînée vapeur derrière le vaisseau live | 4 | 4 | Particules + ribbon décroissant en alpha |
| 4 | Heat-haze sur les segments atmosphériques | 4 | 3 | Distorsion screen-space localisée |

### 9.3 Tier 1 — Quick wins trajectoires

#### 9.3.1 Couleur par stage de mission

**Pourquoi** — La donnée existe déjà (`MissionEphemerisPoint.stageName`)
mais n'est pas exploitée visuellement. Le user ne voit pas où finit
l'ascension verticale, où commence la gravity turn, où se déclenche
le transfert. C'est la modification "plus de signal pour zéro coût
GPU".

**Comment** — Étendre `OrbitLineFactory.buildHeliocentricLineStrip`
(et l'équivalent body-relative) pour accepter un buffer de couleurs
par sommet (`VertexBuffer.Type.Color`). Côté
`MissionTrajectoryRenderer`, mapper chaque `stageName` → `ColorRGBA`
via une table (à coder dans une `MissionStageColors` similaire à
`PlanetColors`). Activer `mat.setBoolean("VertexColor", true)` sur
le matériau Unshaded.

**Touche** — `engine/scene/OrbitLineFactory.java`,
`states/mission/MissionTrajectoryRenderer.java`, nouveau
`engine/scene/MissionStageColors.java`.

#### 9.3.2 Distinction passé / futur

**Pourquoi** — Aujourd'hui rien ne distingue le segment déjà parcouru
du segment à venir. C'est l'info la plus lue par l'utilisateur sur
une trajectoire en cours.

**Comment** — Toujours via le `VertexBuffer.Type.Color` (combinable
avec 9.3.1 — multiplier les RGB du stage par un alpha) : alpha
plein pour `t <= clock.now()`, alpha à 0,3–0,4 pour `t > clock.now()`.
Mise à jour : soit re-uploader le color buffer à chaque tick (les
trajectoires de mission sont bornées en taille — 8192 points max,
acceptable), soit envoyer `now` en uniform et discriminer dans un
shader Unshaded patché. Le buffer-update est plus simple à câbler.

**Touche** — `MissionTrajectoryRenderer` (subscribe au
`SimulationClock`, recolore à la volée).

#### 9.3.3 Marqueur "now" (curseur de temps)

**Pourquoi** — Lit en un coup d'œil la position courante du vaisseau
sur sa propre trajectoire — utile à grande vitesse de simulation.

**Comment** — Petit billboard additif (sprite cercle ou losange)
placé à la position interpolée correspondant à `clock.now()` dans
l'ephemeris. Réutilise le pattern de `BillboardIconView` déjà
présent.

#### 9.3.4 Marqueurs apoapse / périapse

**Pourquoi** — Lecture immédiate de la forme de l'orbite ; utile
pour les missions LEO et GTO.

**Comment** — Lors de la construction de l'`OrbitPath`, calculer
les indices argmin/argmax de `‖r‖` (heliocentrique pour une orbite
solaire, body-relative pour LEO). Placer deux billboards (icônes
`Pe` / `Ap`) aux positions correspondantes.

**Touche** — `simulation/orbit/OrbitPath.java` (helper),
`OrbitInitAppState` / `OrbitRuntimeAppState` (placement des
billboards).

### 9.4 Tier 2 — Ribbon, trace au sol, animation

#### 9.4.1 Ribbon billboardé (épaisseur stable)

**Pourquoi** — Les `glLineWidth` > 1 sont **non garantis** sur les
drivers modernes (Mesa, NVIDIA core profile). Résultat : nos lignes
sont très fines et crénelées. Un ribbon (quad-strip face-caméra)
règle :
- l'épaisseur (configurable en pixels ou en degrés visuels),
- l'antialiasing (alpha-fade des bords du ruban),
- la lisibilité à grande distance.

**Comment** — Soit CPU : pour chaque segment, générer 2 vertices
décalés par `cross(tangent, view)`. Recalcul à chaque frame ou
géométrie en world-space + correction par geometry shader. Soit
GPU : geometry shader / vertex shader qui expand la ligne en quad.
JME3 supporte les geometry shaders mais c'est plus simple côté CPU
pour un nombre de points limité (orbite ~256 pts, mission ~8 192
pts).

**Touche** — Nouveau `engine/scene/RibbonLineFactory.java`,
matériau custom `Common/MatDefs/Misc/Unshaded.j3md` patché ou
`assets/shaders/ribbon/Ribbon.j3md`.

#### 9.4.2 Tirets animés (flow-along)

**Pourquoi** — Indique le sens de parcours sans flèches discrètes.
Effet "missile en route" très lisible.

**Comment** — Sur le ribbon : texture 1D dash/gap, UV.x = abscisse
curviligne (longueur cumulée), offset = `time * speed`. Quasi
gratuit GPU.

#### 9.4.3 Halo additif sous la ligne

**Pourquoi** — Donne du corps visuel sans bouffer la lisibilité.

**Comment** — Doubler le ribbon : un brin "core" opaque + un brin
plus large en blending additif, alpha décroissant transversalement.

#### 9.4.4 Trace au sol (ground track)

**Pourquoi** — Standard de l'industrie pour les orbites LEO ; permet
de voir survol des sites, créneaux de visibilité.

**Comment** — Pour chaque point de l'orbite/mission, projeter
`(lat, lon)` calculés en frame ITRF (déjà disponible via
`OrekitService`) sur la sphère planète à `R + ε`. Construit un
deuxième ribbon collé au sol. Couleur dérivée de la trajectoire
parente, alpha plus faible.

**Touche** — Nouveau `engine/scene/GroundTrackFactory.java`,
nouveau `states/mission/GroundTrackRenderer.java` (ou intégrer dans
`MissionTrajectoryRenderer`).

#### 9.4.5 Flèches directionnelles périodiques

**Pourquoi** — Complément discret au flow des tirets pour les vues
statiques / captures d'écran.

**Comment** — Tous les N points (ou tous les Δt simulation), placer
un mesh triangle billboardé orienté selon la tangente. Couleur du
stage.

### 9.5 Tier 3 — Sémantique avancée

#### 9.5.1 Code couleur thrust / coast

Vertex colors : si la phase courante a une poussée non nulle →
couleur saturée (jaune/orange) ; sinon → couleur du stage atténuée.
Lecture immédiate des phases motorisées vs balistiques.

#### 9.5.2 Gradient vitesse / altitude

Vertex colors via colormap (viridis, plasma, heat). Utile en debug
mission / tuning optimizer ; à exposer derrière un toggle UI.

#### 9.5.3 Mise en avant de la mission sélectionnée

Boost largeur + glow + alpha 1 sur la mission active dans le
panneau ; les autres descendent à largeur réduite + alpha 0,3.
Hook sur la sélection courante exposée par
`MissionContext`.

#### 9.5.4 Marqueurs Δv aux nœuds de manœuvre

Aux frontières de stages où un `ImpulsiveManeuver` ou un changement
de poussée a lieu, billboard 3D fléché orienté selon le Δv local.
Taille proportionnelle à `‖Δv‖`.

#### 9.5.5 Fenêtrage temporel ±N heures

Au lieu de dessiner toute l'ephemeris, ne rendre que les points
dans `[now − N, now + M]`, avec alpha-fade aux deux extrémités.
Allège la scène, met l'accent sur le moment courant.

### 9.6 Tier 4 — Ambitieux

#### 9.6.1 Enveloppe d'incertitude

L'optimizer de
`simulation/mission/optimizer/` produit déjà des sweeps de
robustesse (cf. `specs/optimizer/03-robustness-roadmap.md`). Render
l'enveloppe min/max comme un tube semi-transparent autour du
trajectoire nominale → effet "couloir de vol" très parlant.

#### 9.6.2 Traînée vapeur derrière le vaisseau live

En complément (ou alternative) à la trajectoire prédite : émettre
des particules à la position du vaisseau, fade-out sur ~30 s sim.
Donne une impression de "où il vient de passer" même quand la
trajectoire calculée n'est pas affichée.

#### 9.6.3 Heat-haze sur segments atmosphériques

Pour les segments où l'altitude est < ~80 km (rentrée, ascent
basse), distorsion screen-space localisée autour du segment.
Synergie avec l'effet "plasma de rentrée" (5.5).

### 9.7 Refactoring suggéré

Les sections 9.3 et 9.4 supposent une factorisation côté
`OrbitLineFactory` :

1. Extraire un `LineMeshBuilder` qui accepte `(positions, colors,
   widths)` par sommet — versions ribbon et raw-line héritent.
2. Pousser les couleurs vers un `VertexBuffer.Type.Color` au lieu
   de `setColor("Color", ...)` global.
3. Permettre à `MissionTrajectoryRenderer` de faire un
   "color refresh" sans rebuild complet du mesh (juste l'update du
   color buffer) → coût négligeable pour le marqueur passé/futur.

### 9.8 Suggestion d'ordre d'attaque (trajectoires)

1. **Couleur par stage** (9.3.1) — débloque tout le reste.
2. **Passé / futur** (9.3.2) — trivial une fois 9.3.1 en place.
3. **Marqueur "now"** (9.3.3) — complète le triptyque temporel.
4. **Apoapse / périapse** (9.3.4) — utile pour LEO et GTO.
5. **Ribbon** (9.4.1) — saute le mur des `glLineWidth`.
6. **Tirets animés + halo** (9.4.2 + 9.4.3) — exploitent le ribbon.
7. **Trace au sol** (9.4.4) — gros impact pour les missions LEO.
8. **Sélection / highlight** (9.5.3) — lisibilité multi-mission.
9. **Marqueurs Δv** (9.5.4) — enrichit la lecture des manœuvres.
10. **Enveloppe d'incertitude** (9.6.1) — synergie avec le travail
    optimizer.
