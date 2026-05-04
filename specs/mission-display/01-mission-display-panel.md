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
multi-missions** avec couleurs distinctes, et rendre la **sélection télémètrie
explicite** sans passer par la modal.

---

## 2. Vue d'ensemble — séparation des rôles

| Rôle | Avant | Après |
|---|---|---|
| Création / édition / optim / delete | Modal (`MissionPanelWidget`) | Modal — inchangée fonctionnellement, simplifiée visuellement |
| Liste des missions calculées | Modal (toutes statuts) | Modal (toutes statuts) **+** nouveau panneau HUD (statut `READY` uniquement) |
| Toggle visibilité 3D | Icône œil dans la modal, singleton (1 visible max) | Icône œil dans le **HUD**, **multi-affichage** autorisé |
| Sélection télémètrie | Sélection de ligne dans la modal | Indicateur radio dans le HUD, **explicite et séparé** de la visibilité |
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
 │ ●  ◉   Apollo-LEO        LEO 400km           👁         │
 │ ●  ○   Test-GTO          GTO                 👁         │
 │ ●  ○   Lune-direct       TLI                 ◌          │
 └────────────────────────────────────────────────────────┘
```

De gauche à droite :

| # | Élément | Largeur | Rôle |
|---|---|---|---|
| 1 | **Color swatch** | 12×12 px | Couleur attribuée à la mission (cf. §6) ; carré rempli, bord 1 px `FormStyles.BORDER` |
| 2 | **Indicateur focus télémètrie** | 14×14 px | Style radio : disque plein si focus actif, anneau vide sinon |
| 3 | **Nom de la mission** | flex | `UiKit.sora()` 14, tronqué avec ellipse si dépassement |
| 4 | **Type / sous-titre** | flex | `UiKit.sora()` 12, `FormStyles.TEXT_SECONDARY` (ex. `LEO 400km`) |
| 5 | **Toggle visibilité (œil)** | 16×16 px | Icône `eye` allumée si `entry.visible`, `eye-slash` ou icône grisée sinon |

#### Interactions sur une ligne

| Geste | Effet |
|---|---|
| Clic sur la zone nom (1+3+4) | Set focus télémètrie sur cette mission. **Force `visible = true`** si la mission était cachée. |
| Clic sur l'indicateur radio (2) | Identique au clic ligne (set focus + force visible). |
| Clic sur l'icône œil (5) | Toggle `entry.visible` indépendamment du focus. |
| Hover ligne | Surbrillance légère `FormStyles.ROW_HOVER` (à définir si non existant). |
| Ligne focus | Fond `ICE_ROW_SELECTED` (déjà défini dans `AppStyles`). |

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
  mission est visible. Au clic : passe `entry.visible = false` pour
  toutes les missions `READY`. Le focus télémètrie est conservé mais la
  télémètrie sera masquée (cf. §5.3).

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

### 4.2 Ouverture / fermeture

- Ouverture : déclenchée par
  `EventBus.UiNavigationEvent.OpenMissionManagement` publié depuis le
  Display Panel (bouton "Manage").
- Fermeture : bouton de fermeture dans le header, ou clic sur le
  backdrop modal — comportement existant inchangé.

### 4.3 Affichage de la couleur dans la modal

Pour cohérence visuelle, la colonne NAME affiche aussi le **color
swatch** (12×12 px) à gauche du nom, identique au Display Panel.

---

## 5. Couplage avec la télémètrie

### 5.1 Modèle de données

Nouveau champ dans `MissionContext`
(`src/main/java/com/smousseur/orbitlab/simulation/mission/MissionContext.java`) :

```java
private volatile String telemetryFocusMissionName;
public Optional<MissionEntry> getTelemetryFocusMission() { ... }
public void setTelemetryFocusMissionName(String name) { ... }
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

Les conditions d'affichage sont préservées (focus défini + `READY` +
`visible` + ephemeris).

### 5.3 Règles d'auto-cohérence

Ces règles sont implémentées par le composant qui pilote le Display Panel
(`MissionDisplayPanelAppState` à créer) :

| Déclencheur | Action |
|---|---|
| Première mission qui passe `READY` alors qu'aucun focus n'est défini | `entry.visible = true` ; `telemetryFocusMissionName = mission.name` |
| Mission supplémentaire qui passe `READY` (alors qu'au moins une est focus) | `entry.visible = false` (par défaut) ; focus inchangé |
| User set focus sur une mission **cachée** | Forcer `entry.visible = true` (focus implique visible) |
| User cache (œil off) la mission **focus** | `telemetryFocusMissionName` migre vers la première mission `READY` encore visible (ordre de la liste) ; sinon `null` |
| User clique "Hide all" | Toutes `entry.visible = false` ; focus conservé mais télémètrie masquée |
| Mission focus supprimée | Idem ligne "user cache focus" |

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
| `MissionTrajectoryRenderer` | Couleur de la ligne d'orbite 3D |
| `SpacecraftPresenter` | Teinte de l'icône billboard / modèle 3D du spacecraft |

---

## 7. Suppression de la contrainte singleton

`MissionOrchestratorAppState.pollMissionActions()` cache aujourd'hui
toutes les autres missions quand `TOGGLE_VISIBLE` est reçu pour activer
une mission. **Cette logique est supprimée** : `TOGGLE_VISIBLE` ne touche
plus que la mission cible.

L'unicité reste portée par le **focus télémètrie** (`telemetryFocusMissionName`
unique) — c'est un autre axe que la visibilité.

---

## 8. Évènements EventBus

### 8.1 À ajouter

- `EventBus.UiNavigationEvent.OpenMissionManagement` (sealed record sans
  payload) — Display Panel → ouverture de la modal.
- `EventBus.MissionTelemetryFocusChanged(String missionName)` — utile
  pour observer le focus depuis d'autres widgets ou pour des futures
  extensions (caméra qui suit le focus, par ex.). **Optionnel** : la
  télémètrie pollant déjà `MissionContext`, l'event peut être différé à
  un besoin réel.

### 8.2 Inchangés

- `MissionActionRequest(name, action)` — `TOGGLE_VISIBLE` fonctionne
  toujours, sa sémantique singleton est juste retirée côté orchestrateur.
- `OpenMissionWizard`, `CreateMission`, `OrbitPathReady`.

---

## 9. Scénarios utilisateur

| # | Scénario | Comportement attendu |
|---|---|---|
| **S1** | Premier lancement, aucune mission | Display Panel affiche l'état vide (§3.5). Clic "+ Create mission" → wizard s'ouvre. |
| **S2** | Première mission calculée (`READY`) | Auto-focus + auto-visible. Trajectoire 3D apparaît, télémètrie s'affiche en haut-droite. |
| **S3** | Deuxième mission calculée | Ajoutée masquée par défaut (visible=false) ; focus reste sur la première. User clique l'œil de la deuxième → trajectoires A et B visibles avec couleurs distinctes ; télémètrie continue d'afficher A. |
| **S4** | User clique sur la ligne B | Focus migre sur B, télémètrie s'actualise, A reste visible (deux trajectoires affichées). |
| **S5** | User cache B (œil) alors que B est focus | Focus migre automatiquement vers la première mission `READY` encore visible (A) ; télémètrie repasse sur A. |
| **S6** | User clique "Hide all" | Toutes les missions deviennent invisibles ; focus conservé mais télémètrie masquée. Réafficher la mission focus → télémètrie réapparaît immédiatement. |
| **S7** | User clique "Manage" puis lance optim sur A | Modal s'ouvre. Optim démarre → A passe en `COMPUTING`. La ligne de A **disparaît** du Display Panel (filtré sur `READY`) le temps du calcul. À la fin, A revient avec sa couleur d'origine et reste visible/focus si elle l'était avant. |
| **S8** | User supprime une mission depuis la modal | La ligne disparaît du Display Panel ; sa couleur redevient libre pour la prochaine création. Si c'était la mission focus, focus migre comme S5. |
| **S9** | User ouvre la modal pour créer une nouvelle mission | Modal liste toutes les missions (tous statuts). Clic "+ New mission" → wizard. Création → modal reste ouverte, le wizard se ferme, la nouvelle mission apparaît en `DRAFT` dans la modal et **n'apparaît pas** dans le Display Panel tant qu'elle n'est pas `READY`. |
| **S10** | User ferme le Display Panel via le trigger | Le HUD se masque. La trajectoire 3D et la télémètrie restent visibles tant que les états `entry.visible` / focus ne changent pas (le HUD est juste un panneau de contrôle, pas la source de vérité de l'affichage). |

---

## 10. Hors-scope

- **Tabs télémètrie** : afficher la télémètrie de plusieurs missions en
  parallèle (la télémètrie reste mono-mission ; on change juste sa
  source via le focus).
- **Drag & drop** pour réordonner les missions.
- **Persistance** des couleurs / de la sélection de focus entre sessions
  (sera traitée avec la persistance globale des missions).
- **Implémentation Java** : la spec décrit comportements et structure
  visuelle ; l'implémentation sera planifiée dans une livraison
  séparée.
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
| Télémètrie | `src/main/java/com/smousseur/orbitlab/ui/telemetry/TelemetryWidget.java` |
| AppState télémètrie | `src/main/java/com/smousseur/orbitlab/states/mission/TelemetryWidgetAppState.java` |
| Modèle de mission | `src/main/java/com/smousseur/orbitlab/simulation/mission/MissionContext.java`, `MissionEntry.java`, `MissionStatus.java` |
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
les **scénarios S1 à S10** (§9) sont reproductibles à la main dans
l'application :

1. Lancer `./gradlew run` (ou la cible d'exécution équivalente).
2. Dérouler S1 → S6 dans l'ordre, en utilisant uniquement le bouton
   trigger haut-gauche, le Display Panel et le bouton "Manage". Aucun
   passage par la modal ne doit être nécessaire pour changer la mission
   suivie par la télémètrie.
3. Vérifier que deux missions `READY` peuvent être affichées
   simultanément avec deux couleurs distinctes (S3).
4. Vérifier S7 : pendant un calcul, la ligne disparaît du HUD mais
   reste dans la modal avec status `COMPUTING`.
5. Vérifier que les tests unitaires existants
   (`./gradlew test`) passent toujours, en particulier
   `LEOMissionOptimizationTest` qui exerce le pipeline de calcul de
   mission.
