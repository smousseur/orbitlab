# PHY-5 / L5 — La charge utile comme objet dessiné — conception

Dernier lot du chantier, sur L1–L4. C'est l'**ex-L4** du découpage
[`01-decoupage.md`](01-decoupage.md), scindé le 2026-09-15 : `L4` a fait la
**physique** (une LEO propulsée largue son S2 et la charge utile trimme —
[`06-conception-L4.md`](06-conception-L4.md)), `L5` fait le **rendu**. Il absorbe
`PHY-6`.

Décisions déjà prises en conversation (2026-09-15), une à une ; taille de la
charge utile = **taille vraie du catalogue** (choix utilisateur).

---

## 1. Objet du lot

Faire maigrir le primaire d'un cran de plus, jusqu'à son **maillage de charge
utile** : `after_s1 → payload`, au largage du S2. Effet :

- une mission `GEO_SAT` dessine un **satellite** en GEO, pas un Falcon Heavy — le
  livrable de `PHY-6` ;
- **grâce à L4**, une LEO propulsée (le largage S2 produit désormais un débris
  `UPPER`) fait de même — une charge d'observation dessinée en LEO ;
- le **double-dessin S2** (L3 §4) se ferme partout : dès que le primaire passe à
  la charge utile, il ne montre plus le S2 ; seul le débris S2 le montre.

---

## 2. État des lieux *(mesuré)*

- **`PrimarySilhouette` renvoie un suffixe** (`""` / `-after_boosters` /
  `-after_s1`) que `MissionRenderer` colle sur `modelPath`. La charge utile
  n'est **pas** un suffixe : un chemin d'asset distinct (`payloads/…`), **à une
  autre échelle**.
- **Les maillages de charge utile ne sont PAS normalisés** (contrairement aux
  lanceurs, « 1 unité de haut »). Mesuré : `goes` scale racine ×7,46 (coords
  ~0,13), `landsat8` coords ~10⁵ sans scale, `lro` ×0,038 (coords ~33). Aucune
  convention partagée.
- **`AssetFactory.loadModel(path, scale)` fait `setLocalScale(scale)` sans
  normalisation par bounding-box** ; `swapMesh(path)` réutilise l'échelle du
  lanceur (`config.radiusMeters`). Échanger vers un maillage payload à l'échelle
  lanceur donne une taille **fausse et imprévisible**.
- **La taille vraie est au catalogue** : `PayloadModel.dimensionMeters`
  (`GEO_SAT` 2,5 m, `EARTH_OBS_SAT` 3,0 m, `LUNAR_*` 2,0 m ; `CARGO` 4,5 m). Son
  Javadoc l'annonçait — *« turning it into a drawn size needs the
  metres-per-mesh-unit of a given asset, which is PHY-6's to write »*. L5 le
  **mesure** (bbox) au lieu de le cataloguer → aucune dépendance d'asset neuve.
- **Le `payloadId` est en main** : `entry.spec().configuration().payloadId()`
  (nullable ; le renderer lit déjà `…launcher().id()` juste à côté).

---

## 3. Décisions de conception

### 3.1 D1 — `PrimarySilhouette` renvoie une **phase**, pas un suffixe

`phaseAt(now)` renvoie une `SilhouettePhase` :

| largage | phase qui suit |
|---|---|
| `BOOSTER` | `AFTER_BOOSTERS` |
| `CORE` | `AFTER_S1` |
| `UPPER` | **`PAYLOAD`** *(était ignoré en L3)* |
| `KICK` | *ignoré* (moteur de la charge utile ; elle continue, pas de changement) |

`FULL` avant tout largage. Fonction pure de `now` → réversible au scrub, comme
L3. C'est l'unité TDD (l'API passe de `String` à l'enum).

### 3.2 D2 — Résolution phase → maillage, dans le renderer

Le renderer (seul à connaître le `payloadId`) résout la phase en une cible
`(chemin, taille)` :

- `FULL` / `AFTER_BOOSTERS` / `AFTER_S1` → `modelPath` (± suffixe), **échelle
  lanceur** (inchangé).
- `PAYLOAD` → **`PayloadAssets`** (`GEO_SAT→goes`, `EARTH_OBS_SAT→landsat8`,
  `LUNAR_PROBE`/`LUNAR_ORBITER→lro`), **taille vraie** = `dimensionMeters`.
- **Repli** : pas de `payloadId` (legacy), pas de maillage (cargo), ou
  `dimensionMeters = 0` → la cible `PAYLOAD` **retombe sur `AFTER_S1`** (le
  primaire reste la pile lanceur). En pratique inatteignable : une LEO inerte ne
  largue pas son S2 (pas de phase `PAYLOAD`), le GEO exige `GEO_SAT`. Défense
  pure.

On ne compare pas la phase mais la **cible** appliquée : le repli `PAYLOAD→AFTER_S1`
vaut la cible `AFTER_S1`, donc scruber entre les deux ne rejoue aucun échange.

### 3.3 D3 — Chargement normalisé par bounding-box, à la taille vraie

Voie neuve : `AssetFactory.loadModelNormalized(path, tailleUnités)` — charge,
mesure la bbox monde (`updateGeometricState` + `getWorldBound`), met
`setLocalScale` pour que **le plus grand axe = la taille cible**. `Model3dView`
convertit mètres→unités (`dimensionMeters / PLANET_METERS_PER_UNIT`) ;
`TrackedObjectView` gagne `swapMesh(path, tailleMètres)` normalisé. Les phases
lanceur gardent `swapMesh(path)` (échelle config). Même chaîne async +
`applyLambert`.

- Le plus grand axe = `dimensionMeters` (arrays déployés compris — approximation
  cosmétique : `dimensionMeters` est le bus arrays repliés, le maillage montre les
  arrays).
- **Écart assumé, repris de L3 §3.3** : le seuil LOD reste au rayon lanceur
  (`config.radiusMeters` inchangé), donc le satellite dessiné petit reste en 3D
  plus longtemps qu'un vrai objet de 2,5 m — mineur, et plutôt souhaitable (on
  voit le satellite). À la distance de focus (cadrée sur le lanceur), le satellite
  vrai (~2,5 m) est petit : un modèle 3D quand on est proche, son icône quand on
  dézoome — comme tout corps, et comme la réalité.

### 3.4 D4 — Trous assumés

- **Orientation arbitraire** du satellite (nose +Y aligné vitesse) : un satellite
  n'a pas de nez. Cosmétique.
- **Repli cargo / legacy** (§3.2) : reste à `after_s1`, réintroduit le
  double-dessin S2 pour ce seul cas — inatteignable dans le wizard.
- **Maigrissement lanceur** (`after_*`) toujours tributaire de la convention
  d'export d'`AST-1` (L3 §3.3, à l'œil). `DT-18` (booster Ariane) reste.

---

## 4. L'invariant et la vérification

- **Rendu seul** : L5 ne touche que le maillage dessiné — aucun chemin de
  trajectoire. Les quatre gates sont saufs par construction ; `L5` relance
  `gateTest`.
- **TDD (pur)** : `PrimarySilhouette` (phases `FULL`/`AFTER_BOOSTERS`/`AFTER_S1`/
  `PAYLOAD`, `UPPER→PAYLOAD`, `KICK` ignoré, réversibilité) ; `PayloadAssets`
  (mapping des trois maillages, repli cargo/null/inconnu, taille catalogue).
- **Échelle / bbox / rendu** → vérif visuelle app (comme L3) : le primaire maigrit
  jusqu'au satellite en GEO **et** en LEO propulsée ; le S2 n'est plus dessiné
  deux fois.

---

## 5. Clôture du chantier

L5 est le dernier lot de PHY-5. Le chantier livre la machinerie multi-objets (L1),
la séparation complète (L2), le maigrissement lanceur du primaire (L3), la
livraison physique de la charge utile en LEO (L4) et son rendu distinct jusqu'au
satellite (L5). `PHY-6` est absorbé. Reste hors périmètre : `DT-18` (asset),
l'impact au sol (`MIS-10`), la télémétrie/sélection des débris (différées).
