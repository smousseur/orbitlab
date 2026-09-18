# SEL-1 / L1 — « objet suivi = mission + clé », primaire routé dessus

Premier lot de comportement de `SEL-1` ([`01-decoupage.md`](01-decoupage.md) §4,
stratégie S1). Il introduit le type qui désigne l'objet suivi et le résolveur
d'éphéméride, puis **route le primaire à travers, sans aucun changement de
comportement**. Il isole la charnière caméra fragile (floating origin, annulation
bit-exacte de l'offset, seuil de LOD) avant que L2 y branche un second objet.

**Couche.** Tout est render / caméra-UI. Les deux seuls ajouts dans
`simulation.mission` — le type `FollowedObject` et l'accesseur `ephemerisOf` — sont
un type de référence et un accesseur de données, **pas de la physique**. Aucune
propagation touchée : les quatre gates à tolérance zéro et la boucle CMA-ES sont
intacts.

**Périmètre.** Caméra seule. La télémétrie (pointeur + widget), le clic débris et
les retours restent à L2/L3.

---

## 1. Faits mesurés

Blast radius **exhaustif**, tout couche render / caméra-UI :

- **Un seul chemin d'écriture du focus SPACECRAFT.** `FocusView.viewSpacecraft`
  n'a qu'un appelant, `CameraTransitionAppState.applyFocus`
  (`states/camera/CameraTransitionAppState.java:289`) ; `requestSpacecraft` n'en a
  qu'un, `MissionRenderer.onSpacecraftSelected`
  (`states/mission/MissionRenderer.java:347`, le clic primaire).
- **`TransitionTarget.Spacecraft(missionId, parentBody)`**
  (`app/view/TransitionTarget.java:73`) est touché en **7 points**, tous dans
  `CameraTransitionAppState` : construction (114), `cancelIfTargeting` (130),
  `applyFocus` (288), `isCurrentFocus` (320), `targetDistance` (334),
  `pivotSupplier` (375), `spacecraftPivot` (410).
- **Deux résolveurs seulement** lisent l'éphéméride de l'objet suivi, tous deux
  via `entry.getEphemeris()` : `FloatingOriginAppState.displayPoint`
  (`states/camera/FloatingOriginAppState.java:191`) et
  `CameraTransitionAppState.spacecraftPivot:410`. Tout ce qui suit dans le
  pipeline (`renderBodyOf`, globe near, offset de frame) **dérive du point rendu**.
- **Le reset orchestrateur** compare sur la mission suivie
  (`states/mission/MissionOrchestratorAppState.java:177`, `getFocusedMission()`).
- **La clé d'un débris est déjà son identité déclarée** :
  `DebrisTrack(MissionEphemeris, StageRole role, int exemplarIndex)`, dont le
  Javadoc pose que *« role and exemplarIndex are its identity »* (1-based,
  `simulation/mission/ephemeris/DebrisTrack.java:11`).
- `TransitionTarget` est **déjà une sealed interface** (`Solar`/`Planet`/
  `Spacecraft`) — le patron du type ci-dessous existe à côté.

Rien de périmé à corriger dans le découpage : la charnière à deux résolveurs et
le chemin d'écriture unique tiennent.

---

## 2. Conception

### 2.1 Le type `FollowedObject`

Une sealed interface dans `simulation.mission` (à côté de `MissionId`), importée
par `app.view` comme `MissionId` l'est déjà :

```
sealed interface FollowedObject { MissionId mission(); }
  record Primary(MissionId mission)
  record Debris(MissionId mission, StageRole role, int exemplar)
```

`Debris` porte exactement l'identité de `DebrisTrack` — `(role, exemplar)` — et
**non** un index dans `entry.getDebris()`, cette liste étant reconstruite à chaque
recalcul (`setDebris(List.copyOf…)`). L'égalité de records donne gratuitement
l'égalité de `FollowedObject`, utilisée par `isCurrentFocus` (§2.5).

### 2.2 Le résolveur

`MissionEntry.ephemerisOf(FollowedObject) → Optional<MissionEphemeris>`, parce que
`MissionEntry` possède déjà `getEphemeris()` **et** `getDebris()` :

- `Primary` → `getEphemeris()` ;
- `Debris` → cherche dans `getDebris()` par `(role, exemplar)` →
  `track.ephemeris()` ;
- vide si absent (mission en recalcul, ou débris introuvable).

Commodité `MissionContext.ephemerisOf(FollowedObject)` qui enchaîne
`findMission(ref.mission())` puis `entry.ephemerisOf(ref)`, pour que les deux
résolveurs caméra n'aient qu'un appel au lieu du couple actuel
`findMission` + `getEphemeris`.

### 2.3 `FocusView`

- `focusedMission` (`MissionId`) → `focusedObject` (`FollowedObject`, `null` hors
  SPACECRAFT).
- `viewSpacecraft(FollowedObject, SolarSystemBody parentBody)`.
- Ajout `getFocusedObject()`.
- **Conservation** de `getFocusedMission()` =
  `focusedObject == null ? null : focusedObject.mission()`, pour les consommateurs
  mission-level (reset orchestrateur `:177`).

À L1, seul `Primary` est jamais stocké.

### 2.4 `TransitionTarget.Spacecraft` + `CameraTransitionAppState`

- La cible devient `Spacecraft(FollowedObject object, SolarSystemBody parentBody)`
  — le `missionId` est plié dans `object`.
- Les 7 points lisent `object()` / `object().mission()` ; `applyFocus` →
  `viewSpacecraft(object, parentBody)` ; `spacecraftPivot` → `ephemerisOf(object)`.
- `requestSpacecraft(FollowedObject, parentBody)` ; l'unique appelant
  (`onSpacecraftSelected`) passe `new FollowedObject.Primary(entry.id())`.

### 2.5 `FloatingOriginAppState.displayPoint` et `isCurrentFocus`

- `displayPoint` prend le `FollowedObject` et résout via `ephemerisOf`.
- `isCurrentFocus` compare l'**égalité de `FollowedObject`** (records) au lieu du
  seul `missionId` — ce qui laisse L2 distinguer primaire et débris sans y
  revenir.

---

## 3. Non-régression et vérification

À L1, **seul `Primary` existe**, donc `ephemerisOf(Primary)` est exactement
`getEphemeris()` et chaque chemin caméra reste identique bit pour bit. Vérification :

- `FocusViewTest` / `CameraTransitionTest` verts (signatures adaptées, sémantique
  inchangée) ;
- **un vol manuel suivant le spacecraft primaire** : cadrage, absence de jitter et
  seuil de LOD inchangés — c'est la charnière fragile que ce lot déplace ;
- gates à tolérance zéro **saufs par construction** (aucune propagation touchée).

---

## 4. Ce que L1 ne fait pas

- **Aucune télémétrie.** Le pointeur `telemetryFocus` et le widget restent
  mission-level ; leur généralisation à l'objet est L2.
- **Aucun clic débris.** Les débris restent `onClick == null` ; c'est L2 qui les
  rend sélectionnables et branche la règle unifiée (caméra + télémétrie).
- **Aucun retour.** Ligne d'identité/retour et retours automatiques sont L2/L3.
