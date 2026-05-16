# Spec — Restructuration de l'interface Missions

## 1. Contexte

Aujourd'hui, toute l'interaction utilisateur autour des missions passe par un
**unique widget modal**, `MissionPanelWidget`
(`src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionPanelWidget.java`),
ouvert via le bouton trigger haut-gauche `MissionPanelTrigger`
(`src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionPanelTrigger.java`).
Ce widget cumule trois rôles distincts :

1. **Management** — création (wizard), édition, optimisation, suppression.
2. **Affichage 3D** — toggle de visibilité par mission (icône œil dans
   `RowActionIcons`), avec contrainte singleton imposée par
   `MissionOrchestratorAppState.pollMissionActions()` : activer une mission
   cache automatiquement toutes les autres.
3. **Sélection télémètrie** — la sélection de ligne écrit
   `MissionContext.selectedMissionName`, que `TelemetryWidgetAppState` lit
   chaque frame pour décider quoi afficher.

### Problèmes UX

- Pour changer la trajectoire suivie par la télémètrie, l'utilisateur doit
  **ouvrir la modal, sélectionner une ligne, refermer la modal** — friction
  importante pour une action fréquente.
- L'utilisateur ne peut afficher **qu'une seule mission à la fois** alors que
  comparer plusieurs trajectoires (variantes d'optimisation, missions
  différentes) est un cas d'usage légitime.
- Les fonctions de management — peu fréquentes — occupent autant d'espace que
  les fonctions d'affichage, plus fréquentes.

### Objectif

Séparer **affichage** (fréquent, doit être à un clic) et **management**
(occasionnel, peut rester dans une modal), introduire l'**affichage
multi-missions** avec couleurs distinctes, et rendre l'**activation de la
télémètrie explicite** via une action dédiée, exclusive (une seule mission
télémétrée à la fois).

### Note d'investigation (multi-affichage)

L'infrastructure de rendu multi-missions est **déjà entièrement opérationnelle**
côté code (`MissionOrchestratorAppState.update()` itère toutes les missions et
appelle `MissionRenderer.updateFromEphemeris` indépendamment ; chaque renderer
a sa propre couleur, son propre mesh trajectoire, son propre spacecraft).
L'unique obstacle est un bloc dans `pollMissionActions()` qui cache
explicitement toutes les autres missions quand `TOGGLE_VISIBLE` active une
mission. Sa suppression suffit à activer le multi-affichage.

---

## 2. Vue d'ensemble — séparation des rôles

| Rôle | Avant | Après |
|---|---|---|
| Création / édition / optim / delete | Modal (`MissionPanelWidget`) | Modal — inchangée fonctionnellement, simplifiée visuellement |
| Liste des missions calculées | Modal (toutes statuts) | Modal (toutes statuts) **+** nouveau panneau HUD (statut `READY` uniquement) |
| Toggle visibilité 3D | Icône œil dans la modal, singleton (1 visible max) | Icône œil dans le **HUD**, **multi-affichage** autorisé, **strictement indépendant** de la télémètrie |
| Activation télémètrie | Effet de bord de la sélection de ligne dans la modal | **Action explicite** (icône dédiée) sur le HUD, **exclusive** (1 mission max), **toggle** (re-clic = off) |
| Couleur trajectoire | Couleur unique fixe | **Couleur par mission** issue d'une palette cyclique |
| Trigger haut-gauche | Ouvre la modal | Ouvre le **HUD** ; un bouton "Manage" du HUD ouvre la modal |

---

## 3. Mission Display Panel (nouveau widget non-modal)

Tous les libellés UI sont en **anglais** (cohérent avec
`specs/wizard/mission-wizard-visual-spec.md`).

### 3.1 Position et comportement

- **Ancrage** : haut-gauche, marge `AppStyles.HUD_MARGIN_PX` (16 px) en X et
  Y, calé via `setLocalTranslation(MARGIN, screenHeight - MARGIN, 0f)` —
  même pattern que `TelemetryWidget.layoutTopRight`.
- **Largeur** : 320 px (préliminaire ; à ajuster lors de l'implémentation).
- **Hauteur** : auto, contenue par un scroll vertical au-delà de 8 missions
  visibles.
- **Non-modal** : ne bloque pas la souris sur la scène 3D, pas de backdrop.
- **État initial** : **ouvert** par défaut au démarrage (le HUD est le hub
  principal post-création ; le rendre visible par défaut garantit la
  découvrabilité du flux "Manage").
- **Toggle** : le bouton trigger haut-gauche existant
  (`MissionPanelTrigger`) ouvre/ferme **le Display Panel** au lieu de la
  modal.

### 3.2 Layout d'une ligne (par mission `READY`)

```
 ┌────────────────────────────────────────────────────────┐
 │ ●  📡  Apollo-LEO        LEO 400km            👁        │
 │ ●  📡̶  Test-GTO          GTO                  👁        │
 │ ●  📡̶  Lune-direct       TLI                  ◌         │
 └────────────────────────────────────────────────────────┘
```

(📡 = télémètrie active ; 📡̶ = télémètrie inactive ; 👁 = visible ; ◌ = caché. Icônes exactes à choisir lors de l'implémentation.)

De gauche à droite :

| # | Élément | Largeur | Rôle |
|---|---|---|---|
| 1 | **Color swatch** | 12×12 px | Couleur attribuée à la mission (cf. §6) ; carré rempli, bord 1 px `FormStyles.BORDER` |
| 2 | **Icône télémètrie** | 16×16 px | Action exclusive (cf. §3.2 interactions). Affichée "remplie/accentuée" si télémètrie active sur cette mission, "vide/grisée" sinon. |
| 3 | **Nom de la mission** | flex | `UiKit.sora()` 14, tronqué avec ellipse si dépassement |
| 4 | **Type / sous-titre** | flex | `UiKit.sora()` 12, `FormStyles.TEXT_SECONDARY` (ex. `LEO 400km`) |
| 5 | **Toggle visibilité (œil)** | 16×16 px | Icône `eye` allumée si `entry.visible`, `eye-slash` ou icône grisée sinon |

#### Interactions sur une ligne

| Geste | Effet |
|---|---|
| Clic sur l'icône télémètrie (2) — mission non télémétrée | Active la télémètrie sur cette mission. **Désactive** la télémètrie sur la mission précédemment télémétrée (s'il y en avait une). **Force `entry.visible = true`** si la mission était cachée. |
| Clic sur l'icône télémètrie (2) — mission déjà télémétrée | Désactive la télémètrie (état "aucune mission télémétrée"). `entry.visible` est inchangé. |
| Clic sur l'icône œil (5) | Toggle `entry.visible` **indépendamment** de la télémètrie. Voir §5.3 pour les règles de cohérence (cas où la mission télémétrée devient cachée). |
| Clic sur la zone nom (1+3+4) | **Aucun effet fonctionnel.** La ligne n'est pas "sélectionnable" dans le HUD. Seule la surbrillance hover s'applique. |
| Hover ligne | Surbrillance légère `FormStyles.ROW_HOVER` (à définir si non existant). |
| Ligne télémétrée | Fond `ICE_ROW_SELECTED` (déjà défini dans `AppStyles`) pour souligner l'état actif. |

> **Camera focus** : la mise au point caméra sur un spacecraft reste pilotée
> par le clic 3D sur le spacecraft (`MissionRenderer.onSpacecraftSelected`)
> et est **orthogonale** à l'activation télémètrie. Aucun changement de
> comportement caméra dans cette livraison.

### 3.3 Header

```
 ┌────────────────────────────────────────────────────────┐
 │  MISSIONS                              [⚙ Manage]      │
 ├────────────────────────────────────────────────────────┤
```

- Titre `MISSIONS` à gauche (`UiKit.sora()` 14, `FormStyles.TEXT_PRIMARY`).
- Bouton **"Manage"** à droite (icône engrenage + label).
  - Au clic : publie `EventBus.UiNavigationEvent.OpenMissionManagement`
    (nouvel event).
  - Style : bouton tertiaire, `FormStyles.shellBg()` léger, accent
    `ICE_ACCENT` au hover.

### 3.4 Footer

```
 ├────────────────────────────────────────────────────────┤
 │  2 visible / 3 total                       [Hide all]  │
 └────────────────────────────────────────────────────────┘
```

- Compteur "**N** visible / **M** total" à gauche, `UiKit.sora()` 12,
  `FormStyles.TEXT_SECONDARY`.
- Bouton **"Hide all"** à droite, visible uniquement si au moins une
  mission est visible. Au clic :
  - Passe `entry.visible = false` pour toutes les missions `READY`.
  - **Désactive la télémètrie** (`telemetryFocusMissionName = null`) si
    elle était active (la mission télémétrée fait partie de celles
    cachées). Voir §5.3.

### 3.5 État vide

Si aucune mission `READY` n'existe, le panneau remplace la liste par :

```
 ┌────────────────────────────────────────────────────────┐
 │  MISSIONS                              [⚙ Manage]      │
 ├────────────────────────────────────────────────────────┤
 │                                                        │
 │      No mission computed yet.                          │
 │                                                        │
 │              [+ Create mission]                        │
 │                                                        │
 └────────────────────────────────────────────────────────┘
```

- "No mission computed yet." en `FormStyles.TEXT_SECONDARY`.
- Bouton **"+ Create mission"** publie directement
  `EventBus.UiNavigationEvent.OpenMissionWizard` (réutilise l'event
  existant). Pas besoin d'ouvrir la modal d'abord.
- Le footer disparaît en état vide (pas de compteur, pas de "Hide all").

### 3.6 Style global

- Conteneur racine : `FormStyles.shellBg()` (texture 9-slice existante).
- Bordure : 1 px `FormStyles.BORDER`.
- Fonts : `UiKit.sora()` (14 pour les noms, 12 pour les labels secondaires).
- Pas de nouveau token couleur sauf la **palette de couleurs par mission**
  (cf. §6).
- Marge interne : 12 px horizontal, 10 px vertical (cohérent avec
  `TelemetryWidget`).

---

## 4. Modal de management (existante, simplifiée)

### 4.1 Différences par rapport à l'existant

**Conservé :**
- Header avec titre et bouton de fermeture.
- Liste avec colonnes NAME / TYPE / STATUS.
- Actions par ligne : edit, compute, delete.
- Footer : détails de la mission sélectionnée (status, vehicle, altitude,
  launch date).
- Bouton "+ New mission" qui ouvre le wizard.

**Supprimé :**
- L'icône **œil** (toggle visibilité) dans la colonne ACTIONS — cette
  fonction migre dans le Display Panel.
- Le couplage entre la sélection de ligne dans la modal et
  `MissionContext.selectedMissionName` (qui pilotait la télémètrie). La
  modal a désormais une sélection **locale** (pour le footer details
  uniquement), qui ne pilote plus la télémètrie.

**Pas d'action télémètrie dans la modal.** L'activation télémètrie est une
action de visualisation, exclusivement exposée par le Display Panel.

### 4.2 Ouverture / fermeture

- Ouverture : déclenchée par
  `EventBus.UiNavigationEvent.OpenMissionManagement` publié depuis le
  Display Panel (bouton "Manage").
- Fermeture : bouton de fermeture dans le header, ou clic sur le
  backdrop modal — comportement existant inchangé.

### 4.3 Affichage des indicateurs dans la modal

Pour cohérence visuelle avec le Display Panel :

- **Color swatch** (12×12 px) à gauche du nom dans la colonne NAME,
  identique au Display Panel.
- **Badge "telemetry active"** (petite icône 📡 ou point coloré) à côté du
  swatch sur la ligne dont la télémètrie est active. Read-only ici :
  cliquer dessus dans la modal n'a aucun effet (on revient sur le Display
  Panel pour changer l'activation).

---

## 5. Couplage avec la télémètrie

### 5.1 Modèle de données

Nouveau champ dans `MissionContext`
(`src/main/java/com/smousseur/orbitlab/simulation/mission/MissionContext.java`) :

```java
private volatile String telemetryFocusMissionName;  // null si aucune mission télémétrée
public Optional<MissionEntry> getTelemetryFocusMission() { ... }
public void setTelemetryFocusMissionName(String name) { ... }
public void clearTelemetryFocus() { setTelemetryFocusMissionName(null); }
```

`selectedMissionName` reste pour la sélection interne à la modal (footer
details). Si après refactor il n'a plus d'usage hors modal, il peut être
remplacé par un état local dans `MissionPanelWidget` — décision laissée à
l'implémentation.

### 5.2 Comportement de `TelemetryWidgetAppState`

Inchangé sauf la source de la mission cible :

```java
Optional<MissionEntry> focus = context.missionContext().getTelemetryFocusMission();
if (focus.isEmpty()
    || entry.status() != READY
    || !entry.isVisible()
    || entry.ephemeris().isEmpty()) {
  widget.setVisible(false);
  return;
}
widget.updateFromEphemeris(eph, clock.now(), mission);
```

Conditions d'affichage du widget télémètrie :
- `telemetryFocusMissionName` non `null` **et**
- la mission cible existe **et**
- son statut est `READY` **et**
- `entry.visible` est `true` **et**
- l'éphéméride est disponible.

> Sémantique forte : si la mission télémétrée n'est plus visible, le widget
> télémètrie disparaît. La règle de cohérence §5.3 garantit qu'on ne peut pas
> arriver dans cet état autrement que par un toggle œil → la télémètrie y
> est automatiquement désactivée, donc en pratique la condition
> `entry.visible` ne masque le widget que pendant la frame de transition.

### 5.3 Règles d'auto-cohérence

Implémentées par le composant qui pilote le Display Panel
(`MissionDisplayPanelAppState` à créer). L'état télémètrie et l'état
visibilité sont indépendants, sauf aux transitions ci-dessous :

| # | Déclencheur | Action |
|---|---|---|
| R1 | Mission passe `READY` **et** aucune mission n'a la télémètrie active | `entry.visible = true` ; `telemetryFocusMissionName = mission.name` |
| R2 | Mission passe `READY` **et** une autre mission a déjà la télémètrie | `entry.visible = false` (par défaut) ; télémètrie inchangée |
| R3 | User clique l'icône télémètrie sur une mission **non télémétrée** | `telemetryFocusMissionName = mission.name` ; si mission cachée, `entry.visible = true` (force visible) |
| R4 | User clique l'icône télémètrie sur la mission **télémétrée** | `telemetryFocusMissionName = null` ; visibilité inchangée |
| R5 | User clique l'œil pour **cacher** la mission télémétrée | `entry.visible = false` ; `telemetryFocusMissionName = null` |
| R6 | User clique l'œil pour cacher une mission **non télémétrée** | `entry.visible = false` ; télémètrie inchangée |
| R7 | User clique l'œil pour **afficher** une mission cachée | `entry.visible = true` ; télémètrie inchangée |
| R8 | User clique "Hide all" | Toutes `entry.visible = false` ; `telemetryFocusMissionName = null` si une mission était télémétrée |
| R9 | Mission télémétrée passe `READY → COMPUTING` ou `READY → FAILED` | `telemetryFocusMissionName = null`. **R1 sera ré-évaluée** quand la mission reviendra `READY` (auto-activation si plus rien n'est télémétré) |
| R10 | Mission télémétrée supprimée | `telemetryFocusMissionName = null` ; sa couleur redevient libre (cf. §6.2) |

Note : **aucune migration automatique** vers une autre mission `READY` lors
de la désactivation. Si l'utilisateur veut suivre une autre mission, il
clique son icône télémètrie.

---

## 6. Couleur par mission

### 6.1 Palette

Palette cyclique de **8 teintes** distinctives, pensées pour rester
lisibles sur un fond espace sombre. Les codes sont indicatifs ; à valider
visuellement lors de l'implémentation.

| # | Nom | RGB approx. |
|---|---|---|
| 1 | Cyan (accent existant) | (0.30, 0.65, 0.90) |
| 2 | Magenta | (0.85, 0.35, 0.75) |
| 3 | Lime | (0.55, 0.85, 0.30) |
| 4 | Orange | (0.95, 0.60, 0.25) |
| 5 | Yellow | (0.95, 0.85, 0.30) |
| 6 | Violet | (0.65, 0.45, 0.95) |
| 7 | Salmon | (0.95, 0.55, 0.55) |
| 8 | Teal | (0.25, 0.80, 0.75) |

Constantes regroupées dans `ui/AppStyles.java` ou un nouveau
`MissionColorPalette`.

> **Remplace** la palette `TRAJECTORY_PALETTE` actuellement codée dans
> `MissionOrchestratorAppState` (6 teintes, attribuée via compteur monotone
> `colorIndex++`).

### 6.2 Affectation

- Nouveau champ `MissionEntry.color` (record / classe immutable côté
  `MissionEntry`).
- Affectation au moment de la création (`MissionContext.addMission`) via
  un round-robin :
  - Garder la liste des couleurs actuellement utilisées par les missions
    encore présentes.
  - Choisir la première couleur de la palette qui n'est pas en cours
    d'usage.
  - Si toutes les couleurs sont prises (>8 missions actives), recycler
    en round-robin sur la mission la plus ancienne — collision visuelle
    acceptée.
- À la suppression d'une mission, sa couleur redevient automatiquement
  disponible (puisque l'algo ré-évalue à chaque création).

### 6.3 Réutilisation de la couleur

| Composant | Usage |
|---|---|
| Display Panel | Color swatch en début de ligne (§3.2) |
| Modal de management | Color swatch dans la colonne NAME (§4.3) |
| `MissionTrajectoryRenderer` | Couleur de la ligne d'orbite 3D (déjà supporté, juste sourcer depuis `entry.color`) |
| `SpacecraftPresenter` | Teinte de l'icône billboard / modèle 3D du spacecraft |

---

## 7. Suppression de la contrainte singleton de visibilité

`MissionOrchestratorAppState.pollMissionActions()` cache aujourd'hui
toutes les autres missions quand `TOGGLE_VISIBLE` est reçu pour activer
une mission (lignes 140-148). **Ce bloc est supprimé** : `TOGGLE_VISIBLE`
ne touche plus que la mission cible.

L'investigation a confirmé que :
- La boucle `update()` itère déjà toutes les missions indépendamment.
- Chaque `MissionRenderer` a son propre mesh trajectoire, son propre
  spacecraft, sa propre couleur — aucun état partagé.
- Aucune autre logique ne dépend de l'invariant "une seule mission visible".

L'unicité reste portée par la **télémètrie** (`telemetryFocusMissionName`
unique) — c'est un autre axe que la visibilité.

---

## 8. Évènements EventBus

### 8.1 À ajouter

- `EventBus.UiNavigationEvent.OpenMissionManagement` (sealed record sans
  payload) — Display Panel → ouverture de la modal.
- Nouveau type d'action dans `MissionActionRequest` : `SET_TELEMETRY` et
  `CLEAR_TELEMETRY` (ou un nouvel event dédié
  `TelemetryFocusRequest(String missionName)`, le nom `null` signifiant
  "désactiver"). Choix à faire en phase technique.
- `MissionTelemetryFocusChanged(String missionName)` — utile pour observer
  le focus depuis d'autres widgets ou pour des futures extensions (caméra
  qui suit le focus, par ex.). **Optionnel** : la télémètrie pollant déjà
  `MissionContext`, l'event peut être différé à un besoin réel.

### 8.2 Inchangés

- `MissionActionRequest(name, TOGGLE_VISIBLE)` — fonctionne toujours, sa
  sémantique singleton est juste retirée côté orchestrateur.
- `MissionActionRequest(name, OPTIMIZE)` et `(name, DELETE)`.
- `OpenMissionWizard`, `CreateMission`, `OrbitPathReady`.

---

## 9. Scénarios utilisateur

| # | Scénario | Comportement attendu |
|---|---|---|
| **S1** | Premier lancement, aucune mission | Display Panel affiche l'état vide (§3.5). Clic "+ Create mission" → wizard s'ouvre. |
| **S2** | Première mission `READY` (Apollo-LEO) | Auto-visible + auto-télémétrée (R1). Trajectoire 3D apparaît en cyan, widget télémètrie en haut-droite affiche Apollo-LEO. L'icône télémètrie sur la ligne Apollo-LEO est en état "actif". |
| **S3** | Deuxième mission `READY` (Test-GTO) | Ajoutée cachée par défaut (R2). Télémètrie reste sur Apollo-LEO. User clique l'œil sur Test-GTO → les deux trajectoires sont rendues en cyan et magenta. Télémètrie continue d'afficher Apollo-LEO. |
| **S4** | User clique l'icône télémètrie de Test-GTO | Télémètrie migre sur Test-GTO (R3). Apollo-LEO reste visible (icône télémètrie d'Apollo-LEO repasse "inactive"). Widget télémètrie se met à jour avec les données de Test-GTO. |
| **S5** | User clique l'œil pour cacher Test-GTO (qui est télémétrée) | Test-GTO devient cachée **et** la télémètrie se désactive (R5). Widget télémètrie disparaît. Apollo-LEO reste visible mais non télémétrée. **Pas de migration automatique.** L'utilisateur doit cliquer l'icône télémètrie d'Apollo-LEO s'il veut la suivre. |
| **S6** | User clique "Hide all" | Toutes les missions deviennent cachées ; télémètrie désactivée (R8) ; widget télémètrie disparaît. Réafficher une mission via l'œil → la trajectoire 3D revient ; le widget télémètrie **ne réapparaît pas tant que l'utilisateur ne réactive pas explicitement la télémètrie**. |
| **S7** | User clique "Manage" puis relance optim sur Apollo-LEO (qui est télémétrée) | Modal s'ouvre. Optim démarre → Apollo-LEO passe en `COMPUTING`. Télémètrie désactivée (R9), ligne disparaît du Display Panel (filtré sur `READY`). À la fin du calcul, Apollo-LEO revient avec sa couleur d'origine ; si aucune autre mission n'est télémétrée, R1 ré-active la télémètrie automatiquement (la mission "redevient READY"). |
| **S8** | User supprime une mission depuis la modal | Ligne disparaît du Display Panel ; sa couleur redevient libre pour la prochaine création. Si c'était la mission télémétrée, télémètrie désactivée (R10). |
| **S9** | User ouvre la modal pour créer une nouvelle mission | Modal liste toutes les missions (tous statuts). Clic "+ New mission" → wizard. Création → modal reste ouverte, le wizard se ferme, la nouvelle mission apparaît en `DRAFT` dans la modal et **n'apparaît pas** dans le Display Panel tant qu'elle n'est pas `READY`. |
| **S10** | User ferme le Display Panel via le trigger | Le HUD se masque. La trajectoire 3D et la télémètrie restent visibles tant que les états `entry.visible` / `telemetryFocusMissionName` ne changent pas (le HUD est juste un panneau de contrôle, pas la source de vérité de l'affichage). |
| **S11** | User active la télémètrie sur une mission cachée | Force `entry.visible = true` + télémètrie activée (R3). La trajectoire 3D apparaît et le widget télémètrie se peuple. La mission précédemment télémétrée (si elle existait) repasse "non télémétrée" mais reste visible. |

---

## 10. Hors-scope

- **Tabs télémètrie** : afficher la télémètrie de plusieurs missions en
  parallèle (la télémètrie reste mono-mission ; on change juste sa
  source via l'action télémètrie).
- **Drag & drop** pour réordonner les missions.
- **Persistance** des couleurs / de l'état télémètrie entre sessions
  (sera traitée avec la persistance globale des missions).
- **Migration automatique** de la télémètrie lors de la désactivation
  (cacher la mission télémétrée, suppression, recompute) — choix
  délibéré : désactivation simple, sans heuristique.
- **Gestion du conflit de couleurs** quand >8 missions sont actives :
  recyclage simple, pas d'algo de "couleur la plus distincte".

---

## 11. Fichiers concernés (référence pour l'implémentation future)

| Rôle | Fichier |
|---|---|
| Modal management actuelle | `src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionPanelWidget.java` |
| Lignes / actions modal | `src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionRow.java`, `RowActionIcons.java` |
| Trigger haut-gauche | `src/main/java/com/smousseur/orbitlab/ui/mission/panel/MissionPanelTrigger.java` |
| AppState modal | `src/main/java/com/smousseur/orbitlab/states/mission/MissionPanelWidgetAppState.java` |
| Orchestrateur (singleton actuel à retirer) | `src/main/java/com/smousseur/orbitlab/states/mission/MissionOrchestratorAppState.java` |
| Télémètrie widget | `src/main/java/com/smousseur/orbitlab/ui/telemetry/TelemetryWidget.java` |
| AppState télémètrie | `src/main/java/com/smousseur/orbitlab/states/mission/TelemetryWidgetAppState.java` |
| Modèle de mission | `src/main/java/com/smousseur/orbitlab/simulation/mission/context/MissionContext.java`, `MissionEntry.java`, `MissionStatus.java` |
| Renderer trajectoire | `src/main/java/com/smousseur/orbitlab/states/mission/MissionTrajectoryRenderer.java` |
| Renderer spacecraft | `src/main/java/com/smousseur/orbitlab/engine/scene/spacecraft/` |
| EventBus | `src/main/java/com/smousseur/orbitlab/engine/events/EventBus.java` |
| Style HUD réutilisable | `ui/AppStyles.java`, `ui/form/FormStyles.java`, `ui/UiKit.java` |
| Graphe GUI | `src/main/java/com/smousseur/orbitlab/engine/scene/graph/GuiGraph.java` (ajout possible d'un `missionDisplayNode`) |

### Nouveaux fichiers attendus à l'implémentation

| Rôle | Emplacement proposé |
|---|---|
| Widget Display Panel | `src/main/java/com/smousseur/orbitlab/ui/mission/display/MissionDisplayPanelWidget.java` |
| AppState Display Panel | `src/main/java/com/smousseur/orbitlab/states/mission/MissionDisplayPanelAppState.java` |
| Palette couleurs | `src/main/java/com/smousseur/orbitlab/ui/mission/MissionColorPalette.java` (ou dans `AppStyles`) |

---

## 12. Vérification end-to-end

Une fois implémenté, la spec sera considérée correctement réalisée si
les **scénarios S1 à S11** (§9) sont reproductibles à la main dans
l'application :

1. Lancer `./gradlew run` (ou la cible d'exécution équivalente).
2. Dérouler S1 → S6 dans l'ordre, en utilisant uniquement le bouton
   trigger haut-gauche, le Display Panel et le bouton "Manage". Aucun
   passage par la modal ne doit être nécessaire pour changer la mission
   suivie par la télémètrie.
3. Vérifier que deux missions `READY` peuvent être affichées
   simultanément avec deux couleurs distinctes (S3).
4. Vérifier S5 : cacher la mission télémétrée désactive bien la
   télémètrie, sans migration.
5. Vérifier S7 : pendant un calcul, la ligne disparaît du HUD mais
   reste dans la modal avec status `COMPUTING` ; la télémètrie se
   ré-arme automatiquement à la fin si elle était active.
6. Vérifier S11 : activer la télémètrie sur une mission cachée la rend
   automatiquement visible.
7. Vérifier que les tests unitaires existants
   (`./gradlew test`) passent toujours, en particulier
   `LEOMissionOptimizationTest` qui exerce le pipeline de calcul de
   mission.
