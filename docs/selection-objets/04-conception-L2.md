# SEL-1 / L2 — sélectionner un débris : règle unifiée caméra + télémétrie

Deuxième lot de `SEL-1` ([`01-decoupage.md`](01-decoupage.md) §4), et **celui où
la feature apparaît**. `L1` a posé l'adressage « objet suivi » et routé le primaire
dessus sans rien changer ; `L2` rend un débris **sélectionnable** et branche la
**règle unifiée** : un clic sur l'icône d'un objet pilote la caméra *et* la
télémétrie, pour le primaire comme pour un débris.

**Couche.** Render / UI partout, plus un champ de données dans `MissionContext`.
Aucune propagation, aucun planning : les quatre gates à tolérance zéro et la
boucle CMA-ES sont intacts.

**C'est un lot de comportement**, pas un no-op : le clic sur le spacecraft
primaire, qui ne bougeait que la caméra, allume désormais aussi sa télémétrie.

---

## 1. Faits mesurés

- **L'action unifiée a trois sites d'appel** : le clic primaire
  (`MissionRenderer.onSpacecraftSelected:341`, qui résout déjà le corps parent
  depuis l'éphéméride), le clic débris (`MissionRenderer.rebuildDebrisViews:726`,
  aujourd'hui `onClick == null` — le `DebrisTrack` y expose `role` et
  `exemplarIndex`), et la **ligne de retour** (neuve, dans
  `TelemetryWidgetAppState`).
- **`ApplicationContext` est le conteneur DI** : il tient `focusView()`,
  `missionContext()`, `displaySettings()`, `cameraTransition()` (ce dernier
  enregistré tardivement par setter). `DisplaySettings` et `HudSurfaces` y sont de
  simples objets tenus — le patron d'un `FocusController`.
- **Le widget de télémétrie n'affiche aucune identité d'objet** aujourd'hui :
  `entry.mission()` ne lui sert qu'au MET (`TelemetryWidget.java:216`). L'en-tête
  est `[• TELEMETRY] … [• phase]`, hauteur fixe 215 px.
- **`labelFor` est `private static` dans `MissionRenderer`** (`:798`, « Booster
  N » / « Core » / « Upper stage » / « Kick stage »).
- **Le focus télémétrie est mission-level et à un seul point** :
  `MissionContext.telemetryFocusMissionId`. Ses consommateurs keyent tous sur la
  mission : surbrillance panneaux (`MissionDisplayPanelWidget:198`,
  `MissionPanelWidget:353`), règle auto-`READY` (`MissionDisplayPanelRules:72`),
  timeline (`MissionTimelineVisibility:38`), clear-au-hide
  (`MissionOrchestratorAppState:155`), scénario (`ScenarioAppState:295`).

---

## 2. Conception

### 2.1 `FocusController` (neuf, package `app`)

Une petite classe tenue par `ApplicationContext` (comme `DisplaySettings`),
exposée par `context.focusController()`. Elle centralise la règle unifiée :

```
select(FollowedObject object):
  parent = missionContext.ephemerisOf(object)
              .map(e -> e.displayPointAt(clock.now()).arc().body())
              .orElse(focusView.getBody())
  cameraTransition.requestSpacecraft(object, parent)
  missionContext.setTelemetryFocus(object)
```

Elle lit ses dépendances via le context (`cameraTransition()` est nul avant son
enregistrement, lu à l'appel, pas à la construction). C'est aussi le foyer prévu
pour les retours automatiques de `L3`.

### 2.2 Les trois sites

- `MissionRenderer.onSpacecraftSelected` →
  `context.focusController().select(new FollowedObject.Primary(entry.id()))`. La
  résolution du corps parent **quitte le renderer** pour le contrôleur (voir §4).
- `MissionRenderer.rebuildDebrisViews` : le débris est construit avec
  `onClick = () -> context.focusController().select(new FollowedObject.Debris(
  entry.id(), track.role(), track.exemplarIndex()))` (au lieu de `null`).
- Ligne de retour : `TelemetryWidgetAppState` câble un `Consumer<MissionId>` =
  `missionId -> context.focusController().select(new FollowedObject.Primary(
  missionId))`.

### 2.3 Focus télémétrie objet — shim (miroir de `L1`)

`MissionContext` reçoit un champ `telemetryFocus` (`FollowedObject`) à la place de
`telemetryFocusMissionId`. Les méthodes existantes deviennent des shims :

- `getTelemetryFocusMissionId()` → `telemetryFocus == null ? null :
  telemetryFocus.mission()` ;
- `setTelemetryFocusMissionId(MissionId id)` → `telemetryFocus = id == null ?
  null : new FollowedObject.Primary(id)` ;
- `getTelemetryFocusMission()` inchangé (passe par le shim).

Et deux méthodes neuves pour la couche objet : `getTelemetryFocus()` et
`setTelemetryFocus(FollowedObject)`. **Tous les consommateurs mission-level du §1
restent inchangés** : ils continuent d'appeler `getTelemetryFocusMissionId()` et
voient la mission parente, quel que soit l'objet suivi.

### 2.4 `TelemetryWidgetAppState.update`

- `obj = mc.getTelemetryFocus()` ; masque si `null` ;
- `entry = mc.findMission(obj.mission())` ; garde **inchangée** — masque si
  l'entrée manque, n'est pas `READY`, ou n'est pas visible (la mission parente) ;
- `eph = mc.ephemerisOf(obj)` (le résolveur de `L1`) ; masque si vide ;
- `widget.updateFromEphemeris(eph, mc.clock().now(), entry.mission(), obj)` — le
  `mission` reste pour le MET, `obj` sert au libellé et à la ligne d'identité.

### 2.5 `TelemetryWidget` — rangée d'identité et retour

Une rangée en haut du widget, **pleine largeur et de hauteur fixe** (le widget
passe de 215 à ~231 px ; la hauteur ne dépend pas de l'objet suivi, sans quoi le
HUD dessous bougerait) :

```
‹ [Mission]   ·   [Objet]
```

- Libellé objet : le nom de mission quand on suit le primaire, sinon
  `debrisLabel(role, exemplar)`.
- Le segment mission est **cliquable seulement quand l'objet est un `Debris`** →
  il déclenche le `Consumer<MissionId>` de retour (§2.2). Suivre le primaire
  affiche juste le nom de mission, non cliquable — ce qui comble au passage le
  manque actuel (on ne savait pas de quelle mission on lisait la télémétrie).
- Le widget reçoit son handler de retour à la construction, comme
  `BreadcrumbWidget` reçoit les siens.

**Mutualisation du libellé** : un static `FollowedObject.debrisLabel(StageRole
role, int exemplar)` porte la table (« Booster N » / « Core » / « Upper stage » /
« Kick stage ») ; `MissionRenderer.labelFor` et le widget l'appellent tous deux.
C'est un nom de pièce neutre, co-localisé avec l'identité `(role, exemplar)`.

### 2.6 Sélectabilité du débris = liée à l'icône

L'`onClick` du débris est **inerte tant que `displaySettings.isDebrisVisible()`
est OFF** : le handler teste le toggle et ne fait rien sinon. La poignée reste
donc l'icône (elle-même montrée seulement quand le toggle est ON, PHY-5 / L7), et
non le maillage 3D dessiné en permanence. C'est ce qui pose proprement le retour
automatique de `L3` : couper l'affichage retire la poignée.

---

## 3. Vérification

Lot de comportement — la feature change ce qui est visible :

- **Test unitaire (TDD)** du shim `MissionContext` : `setTelemetryFocus(Debris)`
  → `getTelemetryFocusMissionId()` rend la mission ; `setTelemetryFocusMissionId(
  id)` → `getTelemetryFocus()` rend `Primary(id)` ; `null` des deux côtés.
- **Vol manuel** (affichage débris ON) : clic sur un débris → la caméra le suit
  et la télémétrie affiche « Booster 1 » avec la ligne de retour ; clic sur le
  segment mission → retour au primaire ; clic sur le primaire → sa télémétrie
  s'allume ; panneaux toujours en surbrillance sur la **mission**.
- Gates à tolérance zéro **saufs par construction** (aucune propagation touchée).

`FocusController` orchestre `CameraTransitionAppState` (une caméra JME) : sa règle
n'est pas testable hors runtime sans banc, d'où le vol manuel.

---

## 4. Changement d'edge-case assumé

`FocusController.select` résout le corps parent lui-même. Le clic primaire y perd
son repli `renderContext.targetBody()` (l'arc de départ de la mission) au profit de
`focusView.getBody()`, **uniquement dans le cas où l'éphéméride est nulle** — une
mission en cours de recalcul. C'est un cas transitoire où le cadrage caméra est de
toute façon indéfini ; la simplification est assumée pour n'avoir qu'une résolution
du parent, partagée par les trois sites.

---

## 5. Ce que L2 ne fait pas

- **Aucun retour automatique.** Couper l'affichage débris, ou rembobiner avant le
  largage, ne ramène pas encore le focus au primaire : c'est `L3`. `L2` ne livre
  que le retour **manuel** (la ligne d'identité).
- **Aucun effet de rentrée** (`FX-4`), aucun breadcrumb (décision tenue).
