# SEL-1 — Découpage : sélection et suivi d'un objet de mission

Chantier de v2, glissé **après `PHY-5`, avant `PHY-6`**
([`../roadmap/02-roadmap-v2.md`](../roadmap/02-roadmap-v2.md) §2 et §4). Il
reprend un livrable que la fiche `PHY-5` annonçait — *« un débris qui garde son
identité dans le breadcrumb et la télémétrie »* — et qui n'a pas été fait, en le
**reconçevant** : sans breadcrumb, par le clic sur l'icône.

---

## 1. Objectif

Aujourd'hui un débris propagé par `PHY-5` est **anonyme** : dessiné sans handler
de clic, absent de tout widget, sans télémétrie. On ne peut donc pas regarder une
rentrée — le cas vedette : suivre un booster qui retombe, voir son impact, puis
(plus tard, `FX-4`) sa traînée plasma.

Cible : **clic sur l'icône d'un objet → focus caméra + télémétrie**, pour
n'importe quel objet d'une mission (le primaire comme un débris), avec un moyen de
**revenir au primaire**.

**La donnée existe déjà.** `DebrisTrack.ephemeris()` renvoie un `MissionEphemeris`
— exactement le type que le widget de télémétrie consomme. Le manque est
d'**interaction et d'adressage, pas de physique** : ce chantier est à 100 % couche
render / UI, aucune propagation neuve, les gates à tolérance zéro sont saufs par
construction.

---

## 2. Faits mesurés — les coutures

### 2.1 Suivi caméra : deux résolveurs, tout le reste en dérive

Le mode `SPACECRAFT` suit **le primaire de la mission** par deux points, et un
seul dans chaque :

- `states/camera/FloatingOriginAppState.java:191` — `displayPoint(missionId)` lit
  `entry.getEphemeris()` et renvoie **un** point ; le corps de rendu, le globe
  near et l'offset de frame en **dérivent tous** (`renderBodyOf(point)`, lignes
  99-122). Rien d'autre du pipeline ne relit la mission.
- `states/camera/CameraTransitionAppState.java:410` — `spacecraftPivot(target)`
  lit lui aussi `entry.getEphemeris()` pour viser la cible ; l'aim et
  l'orientation en dérivent (`point.arc().body()`, lignes 421-426).

Résoudre l'éphéméride de **l'objet nommé par la clé** au lieu du primaire dans ces
deux méthodes, et le suivi marche pour un débris sans toucher au reste du
pipeline. Les autres points qui nomment la mission suivie :

- `app/view/FocusView.java:162` — `focusedMission` (`MissionId`) +
  `viewSpacecraft(missionId, parentBody)` ; `getFocusedMission()`.
- `states/camera/CameraTransitionAppState.java` — `TransitionTarget.Spacecraft`
  (`missionId` + `parentBody`) et ses lecteurs : `applyFocus`→`viewSpacecraft`
  (288), `isCurrentFocus` (318), `cancelIfTargeting` (127), `targetDistance` (334).
- `states/mission/MissionOrchestratorAppState.java:177` — reset si l'objet suivi
  disparaît (suppression de mission).

### 2.2 Télémétrie : le pointeur et le widget ; les panneaux restent mission-level

- `simulation/mission/context/MissionContext.java` — `telemetryFocusMissionId`
  (`getTelemetryFocusMission()`), pointeur **indépendant** du focus caméra.
- `states/mission/TelemetryWidgetAppState.java:38` + `ui/telemetry/TelemetryWidget.java`
  — le widget lit `entry.getEphemeris()` et `entry.mission()` (ce dernier
  **seulement** pour le MET, `TelemetryWidget.java:216`) ; il **n'affiche aucune
  identité d'objet**.
- Restent **mission-level, et le peuvent** — suivre un débris n'a pas à changer
  quelle *mission* est en surbrillance : `MissionDisplayPanelWidget.java:198`,
  `MissionPanelWidget.java:353` (surbrillance), `MissionDisplayPanelRules.java:72`
  (auto-on à `READY`), `MissionTimelineVisibility.java:38` (dispo timeline).

Ça borne le changement télémétrie **au widget + au pointeur**.

### 2.3 Clic, poignée, identité du débris

- `states/mission/MissionRenderer.java:726` — un débris est construit avec
  `onClick == null` (*« a debris is built with no click handler … not focusable »*,
  `TrackedObjectView.java:32`). Le rendre cliquable = passer un handler.
- `app/DisplaySettings.java:16` — `isDebrisVisible()` (OFF par défaut) ne pilote
  que l'**affichage secondaire** du débris (icône lointaine + ground track), **pas
  le maillage 3D** (`MissionRenderer.java:493-525`). La poignée cliquable est donc
  l'icône, présente **seulement toggle ON** — ce qui fonde le retour auto (§3).
- `simulation/mission/ephemeris/DebrisTrack.java:11` — record
  `(MissionEphemeris, StageRole role, int exemplarIndex)`, dont le Javadoc pose
  que **`role` + `exemplarIndex` *sont* son identité** (1-based). C'est la clé.

---

## 3. Décisions de conception

Tranchées en amont, une par une (résumé ; le détail par lot suit) :

- **Règle unifiée.** Clic sur l'icône de *tout* objet (primaire ou débris) →
  **focus caméra + focus télémétrie** d'un seul geste. Pour le primaire c'est un
  progrès : aujourd'hui le clic ne bouge que la caméra, la télémétrie étant pilotée
  à part (auto-`READY`, panneau).
- **Adressage.** Objet suivi = `MissionId` + **clé** `(role, exemplarIndex)`
  (l'identité déjà déclarée par `DebrisTrack`), **pas** l'index dans
  `entry.getDebris()` — cette liste est reconstruite à chaque recalcul
  (`setDebris(List.copyOf…)`), un index n'y est pas stable. Le primaire est un cas
  particulier de la même clé.
- **Deux pointeurs**, capables d'objet, **écrits ensemble par le clic** : focus
  caméra (`FocusView`) et focus télémétrie (`MissionContext`). Les chemins
  télémétrie-seuls existants (auto-`READY`, panneau de détail) continuent d'écrire
  « primaire » → on peut toujours lire une télémétrie sans y voler la caméra.
- **Un résolveur unique** rend à la caméra et à la télémétrie l'éphéméride du bon
  objet (primaire → `entry.getEphemeris()`, débris → `track.ephemeris()`).
- **Retour au primaire.** Une **ligne d'identité/retour dans la télémétrie** (le
  widget n'affiche aucune identité aujourd'hui) : nom de l'objet suivi, et quand
  c'est un débris, nom du primaire cliquable = y revenir. Plus un **retour
  automatique** sur les deux cas où le débris n'a plus de poignée : toggle
  « afficher débris » coupé, ou horloge rembobinée **avant** le largage (débris
  masqué). **L'impact ne renvoie pas** — la pièce reste suivie jusqu'au sol et au
  repos, c'est le but.

**Hors périmètre.** Le breadcrumb (décision tenue de
[`../navigation/01-breadcrumb.md`](../navigation/01-breadcrumb.md), abandon
explicite au brainstorm) ; l'effet plasma (`FX-4`) ; toute physique.

---

## 4. Découpage — stratégie S1 (généraliser d'abord, sans changer le comportement)

La charnière caméra (§2.1) est le point fragile — floating origin, annulation
bit-exacte de l'offset, chute de LOD à ×300 sont des bugs subtils. On l'isole dans
un lot **sans changement de comportement** avant d'ajouter la feature, comme
`OPT-1 / L1` (« résultats inchangés »).

### L0 — baseline : capture des comportements actuels *(aucun `src/main`)*

Épingler, comme référence de non-régression, ce que les lots suivants vont
délibérément changer ou devront préserver :

- clic sur le spacecraft primaire = **caméra seule** (la télémétrie ne bouge pas) ;
- un débris **n'est pas cliquable** ;
- la télémétrie **n'affiche aucune identité d'objet** ;
- couper « afficher débris » **laisse le maillage** du débris dessiné (n'ôte
  qu'icône + ground track).

Livrable : `02-baseline-L0.md` + éventuelles caractérisations sur `FocusView` /
`CameraTransition` déjà couvertes par `FocusViewTest` / `CameraTransitionTest`.

### L1 — « objet suivi = mission + clé », primaire routé dessus, **rien ne change**

Introduire le type de référence d'objet (forme : `MissionId` + clé
`PRIMARY | (role, exemplarIndex)`) et le **résolveur unique** d'éphéméride, puis
router **le primaire** à travers, clé = `PRIMARY` partout.

- `FocusView` : `focusedMission` → objet suivi ; `viewSpacecraft(...)` prend la clé.
- `TransitionTarget.Spacecraft` : porte la clé ; `spacecraftPivot`,
  `FloatingOriginAppState.displayPoint`, `applyFocus`, `isCurrentFocus`,
  `cancelIfTargeting` passent par le résolveur.
- `MissionOrchestratorAppState:177` : reset comparé sur l'objet.

**Où vit l'action « sélectionner un objet »** (elle écrira les deux pointeurs au
L2) est à trancher ici : un petit contrôleur, ou une méthode de l'orchestrateur —
sans `getState`, via `ApplicationContext` (cf. la piste `FocusController` de
[`../navigation/01-breadcrumb.md`](../navigation/01-breadcrumb.md) §5.2).

**Vérification.** Suivre le primaire est **visuellement identique** (même
cadrage, même absence de jitter, LOD inchangé) ; `FocusViewTest` /
`CameraTransitionTest` verts ; gates saufs (aucune propagation touchée).

### L2 — sélectionner un débris (la feature, règle A)

- `MissionRenderer.rebuildDebrisViews` : le débris reçoit un `onClick` qui appelle
  l'action de sélection unifiée avec sa clé `(role, exemplarIndex)`.
- Le clic **primaire** (`onSpacecraftSelected`) passe aussi par l'action unifiée →
  il pilote désormais la télémétrie (**c'est le changement (A)**, contre le pin L0).
- `MissionContext.telemetryFocus*` : capable d'objet ; le résolveur du L1 rend
  l'éphéméride du bon objet à `TelemetryWidgetAppState`.
- `ui/telemetry/TelemetryWidget` : résout le **libellé** (« Booster 1 » —
  `labelFor` est aujourd'hui `private static` dans `MissionRenderer`, à mutualiser)
  et ajoute la **ligne d'identité/retour** (nom du primaire cliquable quand un
  débris est suivi).

**Vérification.** Clic sur un débris → caméra + télémétrie sur lui ; ligne de
retour ramène au primaire ; clic primaire allume sa télémétrie ; panneaux
toujours en surbrillance sur la **mission** parente.

### L3 — retours automatiques

Un contrôle par frame : si l'objet suivi est un débris et qu'il **n'a plus de
poignée**, re-sélectionner le primaire — sur les deux déclencheurs seuls :

1. `DisplaySettings` « afficher débris » repassé à OFF (l'icône disparaît) ;
2. horloge rembobinée avant le largage du débris (`now < debris.startDate`, le
   débris est masqué, `MissionRenderer.java:499`).

**L'impact ne déclenche rien** (la pièce reste dessinée au repos). Chemin de
retour identique à celui du L2 (sélectionner le primaire).

---

## 5. Questions ouvertes

1. **Nom et emplacement du type de référence d'objet** (`TrackedObjectRef` ?
   `FocusTarget` ?) et de l'action de sélection unifiée — à fixer au L1.
2. **Mutualisation de `labelFor`** (aujourd'hui `private static` dans
   `MissionRenderer`) entre le renderer et le widget de télémétrie — cosmétique,
   au L2.
3. **Fiche `PHY-6` à corriger** en aval : elle mentionne encore le breadcrumb pour
   le suivi de l'objet actif (`../roadmap/02-roadmap-v2.md` §4, `PHY-6`) — périmé,
   c'est le substrat de `SEL-1` qu'elle réutilisera, hors breadcrumb.
