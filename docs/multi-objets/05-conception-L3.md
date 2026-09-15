# PHY-5 / L3 — Le primaire maigrit — conception

Lot L3 du découpage [`01-decoupage.md`](01-decoupage.md) §5, sur L1
([`03-conception-L1.md`](03-conception-L1.md)) et L2
([`04-conception-L2.md`](04-conception-L2.md)). L2 a exercé la clé-de-maillage-par-
pièce côté débris ; L3 l'applique au **primaire** : sa silhouette maigrit au fil des
largages, `full → after_boosters → after_s1`, par **échange de maillage sur un
`LodView` vivant** — la capacité que le Javadoc déclarait non supportée.

Décisions prises en conversation (2026-09-15).

---

## 1. Objet du lot

Faire changer la silhouette de l'objet principal à chaque largage de lanceur :

- `full` (le lanceur entier) avant tout largage ;
- `-after_boosters` (cœur + S2 + charge) après le largage booster ;
- `-after_s1` (S2 + charge) après le largage cœur.

L'arrêt est à `after_s1` : la transition vers la **charge utile** (après le largage
S2) est `L4`.

---

## 2. État des lieux *(mesuré)*

- **`Model3dView` charge une fois, pour de bon** : `loadModel()` lit `config.modelPath()`
  et scale par `config.radiusMeters()` (fixes), `onModelLoaded` attache au
  `modelBucket`. Aucun échange n'existe — `MissionRenderer` le disait déjà
  (« swapping the mesh of a live LodView is not supported »).
- **`AssetFactory.loadModel(path, scale)` applique `setLocalScale(scale)` en direct**,
  sans normalisation par bounding-box. Donc les tailles **relatives** des silhouettes
  sont leurs tailles **intrinsèques** dans les gltf.
- **Le catalogue n'a que `LauncherModel.heightMeters`** (le lanceur complet) — pas de
  hauteur par silhouette. La fiche découpage §2.3 était optimiste.
- **La chronologie des largages est déjà dans les débris** : chaque `DebrisTrack`
  porte `(ephemeris.startDate(), role)`. Aucune donnée neuve à faire circuler.

---

## 3. Décisions de conception

### 3.1 C1 — L'échange de maillage sur un `LodView` vivant

- `Model3dView` gagne `loadModel(String path)` (charge un chemin donné à l'échelle
  de `config`) et `replaceModel(Spatial)` (vide le `modelBucket`, rattache). `LodView`
  expose `swapMesh(String path)` qui enchaîne les deux en asynchrone, comme le
  chargement initial.
- **Le clic et l'occulteur d'éclipse survivent** : le clic vit sur l'icône
  (`BillboardIconView`), inchangée ; l'occulteur parcourt le `modelBucket` **à chaque
  frame** (`Model3dView.setOccluder`), donc il ramasse les géométries du maillage
  neuf dès le frame suivant, sans rien de spécial.

### 3.2 C2 — La silhouette du primaire, dérivée des débris

Le renderer construit une chronologie `(date → suffixe)` à partir de
`entry.getDebris()` — une fonction **pure**, testable (`PrimarySilhouette`) :

| role du largage | suffixe de la silhouette qui suit |
|---|---|
| `BOOSTER` | `-after_boosters` |
| `CORE` | `-after_s1` |
| `UPPER` | *ignoré en L3 (→ charge utile, `L4`)* |

À chaque frame : suffixe courant = la dernière transition de date ≤ `now`, ou `""`
(full) si aucune. S'il diffère du dernier appliqué,
`primary.swapMesh(modelPath.replace(".gltf", suffixe + ".gltf"))`. **Réversible au
scrub** : la silhouette est une fonction de `now`, pas un événement, donc reculer
l'horloge repasse à la silhouette antérieure.

### 3.3 C3 — L'échelle : swap à rayon inchangé

Le swap réutilise `config.radiusMeters()`. Le **maigrissement vient des tailles
intrinsèques** des maillages (§2). Il est **gratuit et correct si** `AST-1` a exporté
les `after_*` dans le même repère que le complet (le S2 occupe naturellement le haut
du volume) ; **absent si** chaque maillage a été re-normalisé à une unité (tous
dessinés pleine hauteur). C'est un fait d'asset à **vérifier à l'œil** ; s'il est
faux, c'est un ré-export (comme `DT-18`), pas du code. Le seuil LOD reste au rayon du
lanceur complet (le primaire focalisé est en 3D de toute façon) — écart mineur
assumé.

---

## 4. Ce que L3 s'interdit *(→ L4)* et le trou assumé

- **La charge utile** (`after_s1 → payload`, après le largage S2) : `L4`.
- **Trou assumé, GEO seulement** : `L2` dessine le S2 largué comme débris ; `L3`
  laisse le primaire à `after_s1` (qui **inclut** le S2). Donc **après la séparation
  S2 en GEO**, le S2 est dessiné **deux fois** (primaire + débris) pendant le coast
  final. En LEO il n'y a pas de séparation S2 → pas de trou. `L4` le ferme en faisant
  passer le primaire à la charge utile. Décision du 2026-09-15 : assumé.

---

## 5. L'invariant et la vérification

- **Optimize + quatre gates intacts** : L3 ne touche que le rendu — l'échange de
  maillage, la silhouette dérivée des débris. Aucun chemin de trajectoire. `L3`
  relance `gateTest`.
- **TDD** sur la fonction pure `PrimarySilhouette` : full avant tout largage,
  `-after_boosters` après le booster, `-after_s1` après le cœur, `UPPER` ignoré,
  réversibilité au scrub.
- **Vérif visuelle** (app) : la pile perd ses boosters puis son cœur, et — si les
  maillages sont dans un repère partagé (§3.3) — raccourcit à chaque fois. `DT-18`
  reste. Falcon Heavy en démonstration.

---

## 6. Ce que L3 lègue à L4

L'échange de maillage vivant et la chronologie dérivée des débris. `L4` n'ajoute
qu'une transition de plus (`UPPER → payload`), avec la table de maillage par famille
de charge utile et sa hauteur — et ferme le trou S2 du §4 par la même occasion.
