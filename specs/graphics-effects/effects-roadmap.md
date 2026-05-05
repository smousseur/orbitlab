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
