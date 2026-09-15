# PHY-5 / L2 — La séparation complète : impulsion, M objets, identité — conception

Lot L2 du découpage [`01-decoupage.md`](01-decoupage.md) §5, sur la machinerie de
L1 ([`03-conception-L1.md`](03-conception-L1.md)). L1 a livré la structure — une
`List<DebrisTrack>`, la capture, le `DebrisGenerator`, le fan-out du renderer — avec
**un** débris inerte. L2 la remplit : **tous** les largages, un bloc **éclaté en M**,
l'**impulsion** d'écartement, le **maillage par pièce** et l'**identité**. Le delta
est de la donnée dans les cadres de L1, pas de la structure.

Décisions prises en conversation (2026-09-15).

---

## 1. Objet du lot

- Émettre un débris à **chaque** séparation, plus **M** pour le bloc booster.
- Faire diverger les débris par une **impulsion** (rétro + éventail).
- Dessiner chaque pièce avec **son** maillage (`booster{i}`, `core`, `S2`) et son
  **label**.

À la fin de L2, une séparation Falcon Heavy montre deux boosters qui reculent et
s'écartent, puis un cœur qui se détache, chacun avec sa silhouette.

---

## 2. Décisions de conception

### 2.1 C1 — La source de M : `ParallelBlock.boosterCount`

La multiplicité est **dissoute au runtime** : `StageModel.toVehicle` agrège
`multiplicity × unit` dans un `LaunchVehicle` sans compte, et threader M dans ce
record toucherait ses **40 sites d'appel**. Mais M est connu à la composition, et
`StagingPlan.forLauncher(List<StageModel>, …)` a les `StageModel` sous la main.

- `ParallelBlock` gagne un composant `int boosterCount`.
- `StagingPlan.block(…)` (son unique site de construction) le peuple avec
  `stages.get(bottomIndex).multiplicity()`.
- Au runtime : `mission.getVehicle().stagingPlan().parallelBlock().boosterCount()`.

1 record + 1 site touché, aucun churn ailleurs.

### 2.2 C2 — Tous les largages, un événement par séparation

Le `Collector` (L1 §2.2) perd la garde *« premier seulement »* et émet à **chaque**
`StageSeparationStage`. Chaque `JettisonEvent` porte désormais ce qu'il faut pour
éclater et dessiner :

```
JettisonEvent(SpacecraftState state,   // position/vitesse/date pré-largage
              AerodynamicProperties aero,  // agrégat (M × unit)
              double jettisonedMass,       // agrégat (M exemplaires)
              int multiplicity,            // M — 1 hors bloc booster
              StageRole role)              // BOOSTER / CORE / UPPER
```

M vient de C1 quand `role == BOOSTER` et que le stack a un bloc parallèle ; **1**
sinon. `StageSeparationStage` reste inchangé (la capture lit de l'extérieur).

### 2.3 C3 — Le `DebrisGenerator` éclate et applique l'impulsion

Le split et l'impulsion sont la **physique d'affichage** des débris, donc leur
place est le `DebrisGenerator`, pas la capture. Pour chaque événement, pour chaque
exemplaire `i` de `1..M` :

- masse = `jettisonedMass / M` ; section = `aero.crossSection() / M` (même Cd) ;
- vitesse initiale = `state.velocity()` **+ l'impulsion** `(i, M)` (C4) ;
- propagation traînée-seule (comme L1) → `MissionEphemeris` →
  `DebrisTrack(ephemeris, role, exemplarIndex = i)`.

`DebrisTrack` gagne `(StageRole role, int exemplarIndex)`. L'invariant tient : tout
cela ne tourne qu'au replay, hors CMA-ES.

### 2.4 C4 — L'impulsion : rétro + éventail

Une **fonction pure** `SeparationImpulse.of(velocity, index, multiplicity)`,
testable en TDD, bakée dans la vitesse initiale par le générateur :

- **rétro** : une composante `−v̂ · RETRO_MAG` commune à tous — le débris recule
  derrière le primaire qui continue sur sa trajectoire optimisée (sinon il en
  diverge trop lentement) ;
- **éventail** (si `M > 1`) : `+fanDir(index, M) · FAN_MAG`, où `fanDir` tourne en
  azimut `2π(index−1)/M` dans le plan perpendiculaire à la vitesse (base
  orthonormale `perp1 = v × ref`, `perp2 = v × perp1`, `ref` fixe non colinéaire).
  Les M boosters s'ouvrent en éventail symétrique.
- **M = 1** (cœur, S2) : rétro seul, pas d'éventail.

`RETRO_MAG` et `FAN_MAG` ~1 m/s (ordre confirmé en L0 §5.2), constantes tunables ;
jamais une prétention physique (D4).

### 2.5 C5 — Maillage et identité, côté rendu

`DebrisTrack` porte `(role, exemplarIndex)` ; le **renderer** les mappe (le sim ne
connaît pas les chemins d'asset) :

| role | maillage (suffixe) | label |
|---|---|---|
| `BOOSTER` (indice i) | `-booster{i}.gltf` | « Booster i » |
| `CORE` | `-core.gltf` | « Core » |
| `UPPER` | `-S2.gltf` | « Upper stage » |

Le suffixe se colle au chemin du lanceur comme en L1 (`modelPath.replace(".gltf",
suffixe)`). Couleur : le gris neutre de L1 (une teinte par rôle est possible mais
non retenue). Le label est le nom du `BodyRenderConfig`, montré par l'icône.

---

## 3. Ce que L2 s'interdit *(→ lots suivants)*

- **Le maigrissement du primaire** (`after_boosters`/`after_s1`) : `L3`.
- **La charge utile comme objet distinct** : `L4`.
- **La sélection / le focus** d'un débris : hors chantier.
- **Une impulsion sourcée** : les magnitudes restent cosmétiques (D4).

---

## 4. L'invariant et la vérification

- **Optimize + quatre gates intacts** : la capture reste une accumulation au
  replay, le split et l'impulsion vivent dans le `DebrisGenerator` (hors CMA-ES),
  `StageSeparationStage` est inchangé, et `ParallelBlock.boosterCount` n'entre dans
  aucun calcul de trajectoire. `L2` relance `gateTest`.
- **TDD** sur les fonctions pures neuves : `SeparationImpulse.of` (rétro + éventail,
  déterministe) et le split du `DebrisGenerator` (un événement M=2 → 2 tracks, masse
  et section ÷2, rôles et indices corrects).
- **Vérif visuelle** (app) : les deux boosters s'écartent, le cœur se détache, les
  silhouettes sont les bonnes. `DT-18` reste (booster Ariane large) — Falcon Heavy
  en démonstration.

---

## 5. Ce que L2 lègue à L3

Le maigrissement du primaire (`L3`) réutilise la clé-de-maillage-par-phase : L2
l'a déjà exercée côté débris (role → suffixe de maillage). L3 l'applique au
**primaire** au fil des largages (`full → after_boosters → after_s1`), avec
l'échange de maillage sur `LodView` vivant.
