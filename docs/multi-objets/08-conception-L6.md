# PHY-5 — L6 — Les pièces collent : sièges de rendu *(conception)*

Lot ajouté après la vérification visuelle de L5 : les séparations se dessinent mal
(pièces empilées à l'origine, silhouette qui se téléporte). Ce lot **ne touche pas
la propagation** — elle est juste. Il corrige le seul rendu pour que les pièces du
puzzle collent : les débris **et** les rubans.

> **Décidé en conversation (2026-09-16).** On ne modifie pas la propagation ; le
> `(0,5,0)` où s'allume un S2 est une info **rigide** que la masse ponctuelle jette,
> et qu'on **réintroduit au dessin**, pas dans la trajectoire. Pour un débris c'est
> sa place physique de départ ; pour le primaire c'est une couche d'affichage sur
> une trajectoire **partagée et gatée**.

---

## 1. Le défaut, mesuré

`AssetFactory.loadModel` ne fait qu'un `setLocalScale` — **aucun recentrage**. La
place d'une pièce dans la pile est donc **gravée dans le glTF**. Mesuré (repère
unité, pile pleine = 1.0, chaque axe transformé par la hiérarchie de nœuds) :

| pièce | `heavy_falcon` Y | `ariane_64` Y | siège réel |
|---|---|---|---|
| full | 0 → 1.000 | 0 → 1.000 | — |
| `-core` | **0** → 0.687 | **0** → 0.572 | bas |
| `-after_boosters` | **0** → 1.000 | **0** → 1.000 | pleine hauteur (boosters latéraux) |
| `-after_s1` | **0** → 0.383 | **0** → 0.514 | haut |
| `-S2` | **0** → 0.213 | **0** → 0.214 | haut |
| `-booster1` | **0** → 0.653, Z ±0.06 | **0** → 0.362, Z ±0.05 | bas, latéral ±~4 m |

**Toutes les pièces sont recentrées base = 0, centrées sur l'axe.** Donc
base-à-l'ancre dessine chaque pièce avec sa **base au point propagé P**. Au largage
S1, le `core` (débris) et le reste `after_s1` (primaire) démarrent **tous deux à P**
→ ils se chevauchent, et le S2 « se téléporte » de sa place haute vers P. Défaut
confirmé au chiffre près, et identique au largage S2.

**Point-1 (essai annulé).** Un premier correctif poussait l'offset dans la
*propagation* des débris (`SeparationImpulse.positionOffset` appliqué dans
`DebrisGenerator`). Il empirait : base perpendiculaire **arbitraire** (boosters
orientés n'importe comment) et offset **nul pour le core et le S2** (multiplicité 1)
→ toujours empilés. Annulé intégralement.

---

## 2. Principe

- **Propagation intacte.** Le CoM ponctuel est la donnée juste de la mission ; on
  n'y touche pas.
- **Siège 100 % rendu, en repère corps** (attitude = vitesse) : un décalage ajouté à
  la **position dessinée**, donc au **maillage ET à la pointe du ruban ensemble**.
  Rien de stocké n'est touché → les quatre gates sont saufs **par construction**.
- **Règle « on aligne les nez ».** Le sommet reste fixe à `P + H` ; la pile maigrit
  **par le bas**. `siège_primaire = H − hauteur_dessinée(silhouette)`. Comme chaque
  reste occupe exactement la tranche qu'il occupait déjà dans la pile pleine, le
  **swap de silhouette ne fait aucun saut de maillage**.

---

## 3. Décisions

### D1 — Siège du primaire

`siège_axial = H − hauteur_dessinée(silhouette)`, `H = launcher.heightMeters()`.

| phase | hauteur dessinée | siège axial | Falcon (H=70) | Ariane (H=63) |
|---|---|---|---|---|
| `FULL` / `AFTER_BOOSTERS` | `H` | 0 | 0 | 0 |
| `AFTER_S1` | `frac·H` | `(1−frac)·H` | ~43 m | ~31 m |
| `PAYLOAD` | taille catalogue | `H − taille` | ~67 m | ~60 m |

`frac_after_s1` par lanceur, **mesuré (§1)** : `heavy_falcon` 0.383, `ariane_64`
0.514. Une charge utile **sans maillage** (cargo, custom) retombe sur `after_s1` —
mesh et siège compris.

### D2 — Sièges des débris *(à leur place de largage)*

| rôle | siège axial | siège latéral |
|---|---|---|
| `CORE` | 0 (il part de la base) | 0 |
| `BOOSTER` | 0 | ~4 m, **fanné** par exemplaire |
| `UPPER` (S2) | `(1−frac_after_s1)·H` (= base de `after_s1`, là où le S2 siégeait) | 0 |
| `KICK` | 0 | 0 |

Le ~4 m latéral vient du montage mesuré (±0.06·H ≈ ±4 m sur l'axe Z du mesh). Sa
**direction** est une base perpendiculaire stable, **pas le vrai roulis** (inconnu)
— cosmétique assumé (D4 du découpage).

### D3 — Application (rendu)

- `StackSeat.offset(vitesse, position, axial, latéral, i, M)` (fonction pure) =
  `axial·v̂ + latéral·éventail`, en mètres, dans le repère de l'arc. L'**éventail
  latéral ouvre dans le repère orbital local** (cross-range à azimut 0), réutilisé de
  `SeparationImpulse.fanDirection` **pour que le siège et le coup de vitesse ouvrent
  dans le même plan** — sinon les boosters partent d'un flanc et dérivent vers un
  autre (défaut « pas dans le bon axe » vu à la vérif).
- **Le siège n'est PAS ajouté à la position absolue de l'ancre.** L'ancre reste sur le
  point propagé (ce que le floating-origin annule *exactement*) ; sinon, loin de la
  Terre, le petit siège se perd dans l'annulation float et l'objet **tremble** (défaut
  vu à la vérif : satellite + S2). Le siège est appliqué comme **petit offset en repère
  proche** sur le **maillage lui-même** (`BodyView.setModelOffset` → translation locale
  du `modelBucket`) **et** sur la **pointe du ruban** (`MissionTrajectoryRenderer`).
  Les sommets historiques du ruban restent sur le vrai chemin (seule la pointe suit) —
  d'où la « fourche » assumée (D4).
- Le siège sur le maillage (pas sur l'ancre) a un corollaire : un débris **hérite** de
  la translation de son ancre parente (le primaire) mais **pas** du siège du primaire
  → chaque objet porte son propre siège, sans couplage. Le débris reste positionné
  **relatif au primaire (non seaté)** : gros termes annulés en double → correctif
  « tremblement » de L5 **préservé**.
- `displayPointAt` / floating-origin / caméra / télémétrie / **gates** lisent le CoM
  **vrai** (non seaté).

### D4 — Artefacts assumés

- **Fourche du ruban primaire au largage** : la base dessinée monte d'un cran, le
  débris file par le bas. Local, une fois, informatif.
- **Charge utile dessinée au sommet** (~H au-dessus du point de focus caméra) —
  cohérent avec le cadrage de la pile pleine, dont le nez était déjà là. Recentrer la
  caméra sur l'objet seaté est **hors périmètre**.
- **Roulis inconnu** → direction latérale des flancs cosmétique.

---

## 4. Invariant préservé

Conforme au §4 du découpage : rien de la trajectoire volée n'est touché. Les quatre
gates volent `LEGACY` (0 séparation → silhouette `FULL` → siège 0), et de toute
façon le siège ne vit qu'au moment du dessin. **Aucune re-baseline.**

---

## 5. Tests

- `StackSeat` (pur, TDD) : axial le long de la vitesse ; latéral perpendiculaire ;
  éventail cross-range (repère orbital) ; fan opposé pour deux exemplaires ; zéro quand
  tout est nul ou à l'arrêt.
- `SeparationImpulse` (TDD) : rétro + éventail cross-range partagé.
- `LauncherStackGeometry` : les deux fractions épinglées (provenance = §1).
- Le rendu lui-même se juge **à l'œil** (non testable ici).

> **Deux défauts corrigés après la première vérif visuelle (2026-09-16).** (1) Les
> boosters ne se séparaient pas « dans le bon axe » : l'éventail ouvrait dans un plan
> référencé sur le pôle → passé au repère orbital local (cross-range), partagé
> siège/impulsion. (2) Le satellite et le S2 « tremblaient » en GEO : le siège était
> ajouté à la position **absolue** de l'ancre et se perdait dans l'annulation float du
> floating-origin → déplacé sur le maillage (`modelBucket`) et la pointe du ruban,
> l'ancre restant sur le point propagé.
