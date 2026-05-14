# Roadmap court terme — OrbitLab

## Contexte

`GEOMission` est implémentée et validée par `GEOMissionOptimizationTest`
(±50 km sur l'altitude cible, ±0.1° d'inclinaison sur un scénario GTO 400 km
parking → 35 786 km). Mais :

- La carte GTO du wizard est `DISABLED` (`StepMissionType.java:69-80`).
- Le wizard ne sait créer qu'une `LEOMission`
  (`MissionWizardAppState.createMission()` ligne 63).
- Le panel affiche `"LEO"` en dur pour toutes les missions
  (`MissionTypes.java:8-15`, TODO actif).

Il faut donc débloquer GEO end-to-end côté UI, puis peaufiner l'expérience
(breadcrumb 3D déjà spécifié, détails mission, timeline), avant d'ouvrir un
nouveau type (Rendezvous / Phasing).

Légende : **P0/P1/P2** = priorité (P0 = doit être fait d'abord) ;
**S/M/L** = difficulté (Small / Medium / Large).

---

## Phase 1 — Débloquer GEO end-to-end (P0)

Objectif : pouvoir créer une mission GEO depuis le wizard, l'optimiser, la voir
correctement étiquetée dans le panel, et la rendre en 3D — comme LEO aujourd'hui.

### 1.1 Stocker le type sur `Mission` — **P0 / S**

- Ajouter `MissionType` (enum `LEO`, `GEO`) et l'exposer via
  `Mission.getType()` (implémentation par `LEOMission` et `GEOMission`).
- `ui/mission/panel/MissionTypes.label(entry)` : renvoyer
  `entry.mission().getType().displayName()` au lieu du `DEFAULT_MISSION_TYPE`
  codé en dur. Supprimer le TODO ligne 13.

Fichiers : `simulation/mission/Mission.java`,
`simulation/mission/LEOMission.java`, `simulation/mission/GEOMission.java`,
`ui/mission/panel/MissionTypes.java`.

### 1.2 Activer la carte GEO dans `StepMissionType` — **P0 / S**

- Renommer la carte `GTO` → `GEO` (cohérence avec `GEOMission`, à confirmer ;
  cf. *Question ouverte 1*). Mettre à jour le badge en
  `AVAILABLE` / `SUCCESS`.
- État initial : `IDLE` (sélection mutuellement exclusive avec LEO).
- Brancher le `MouseEventControl` pour mettre à jour `selectedMissionType`
  et l'état visuel des deux cartes (réutiliser exactement le pattern LEO
  ligne 82-90).

Fichier : `ui/mission/wizard/step/StepMissionType.java`.

### 1.3 Branche GEO dans `StepParameters` — **P0 / M**

- `StepParameters` actuel est codé en dur "LEO" (titre, slider 160–2000 km).
- Le rendre paramétré par le type sélectionné à l'étape précédente :
  - **LEO** : un slider d'altitude cible (existant, conservé).
  - **GEO** : deux champs — parking altitude (200–1000 km, défaut 400 km) et
    altitude cible (lecture seule ou éditable autour de 35 786 km).
- Émettre les valeurs avec des clés distinctes : `GEO_PARKING_ALT`,
  `GEO_TARGET_ALT` (en plus de `LEO_TARGET_ALT` déjà présent).
- Mettre à jour le titre dynamiquement (`"PARAMETERS — GEO"` / `"— LEO"`).

Fichiers : `ui/mission/wizard/step/StepParameters.java`,
`ui/mission/wizard/FormField.java` (nouvelles clés).

### 1.4 Branche GEO dans `MissionWizardAppState.createMission()` — **P0 / S**

- Lire `MISSION_TYPE` depuis `values` et instancier `GEOMission` ou
  `LEOMission` selon. Le constructeur GEO existe déjà :
  `new GEOMission(name, parkingAlt*1000, targetAlt*1000)`.
- Passer le `scheduledDate`, la latitude/longitude/altitude du launch site
  (existants).

Fichier : `states/mission/MissionWizardAppState.java:63-77`.

### 1.5 Retirer le seed GEO codé en dur — **P0 / S**

- `MissionPanelWidgetAppState` (ligne 28) seed `new GEOMission("GTO mission",
  400_000, 35_786_000)` au démarrage. Une fois le wizard fonctionnel, le
  supprimer (laisser le panel vide à l'ouverture, ou ne garder qu'un seul
  exemple LEO selon préférence).

Fichier : `states/mission/MissionPanelWidgetAppState.java`.

### 1.6 Vérification end-to-end Phase 1

1. Lancer l'app → ouvrir panel → cliquer `+ New mission`.
2. Sélectionner GEO → renseigner parking + target → choisir launcher + site
   → créer.
3. Panel : la nouvelle mission apparaît avec type `GEO`.
4. Cliquer l'action "compute" → status passe à `COMPUTING` puis `READY`.
5. Activer la visibilité → la trajectoire s'affiche en 3D.
6. `./gradlew test` toujours vert (en particulier `GEOMissionOptimizationTest`).

---

## Phase 2 — Polish UI (P1)

### 2.1 Breadcrumb de navigation 3D — **P1 / M**

Suit intégralement la spec `specs/navigation/01-breadcrumb.md`. Pas de
re-spec ici, juste l'inscription dans la roadmap.

À créer :
- `ui/breadcrumb/BreadcrumbWidget.java`
- `states/scene/BreadcrumbWidgetAppState.java`

À modifier :
- `core/SolarSystemBody.java` (ajouter `children()`)
- `engine/scene/graph/GuiGraph.java` (ajouter `breadcrumbNode`)
- `OrbitLabApplication.java` (enregistrer le state)
- `states/scene/PlanetPoseAppState.java` (exposer `onSelectPlanet`)
- `states/mission/MissionRenderer.java` (exposer focus mission)

Réutiliser `ui/mission/wizard/component/PopupList.java` pour le dropdown.

Vérification : scénarios 1–9 de la spec, section 6.

### 2.2 Vue détail mission dans le panel — **P1 / M**

Aujourd'hui `PanelFooter` n'affiche que des métadonnées sommaires.
Décision utilisateur : panel = lecture seule + actions + inspection détaillée.

- Ajouter une zone de détails (extension du footer ou sous-panel à droite)
  qui s'affiche sur sélection d'une ligne :
  - Type, statut, scheduled date, launch site (lat/lon/alt).
  - Liste des stages (nom, durée, Δv approx).
  - Pour les missions `READY` : altitude finale, inclinaison finale, écart
    à la cible (lit le résultat `MissionOptimizerResult` exposé via
    `OptimizableMissionStage`).
  - Pour `FAILED` : message d'erreur lisible.
- Réutiliser le style `FormStyles` / `UiKit` déjà en place.

Fichiers : `ui/mission/panel/PanelFooter.java` (ou nouveau
`MissionDetailsView.java`), `ui/mission/panel/MissionPanelWidget.java`
(layout).

### 2.3 Implémenter l'action "Edit" du panel — **P1 / M**

L'icône existe (`RowActionIcons`) mais l'action n'est pas câblée.

- Click "Edit" sur une ligne → rouvrir le wizard pré-rempli avec les
  valeurs de la mission, type non modifiable.
- Validation → remplace la mission dans `MissionContext` (ou met à jour
  l'entrée existante et repasse status à `DRAFT` pour ré-optimisation).
- Étendre `MissionWizardWidget` pour accepter des valeurs initiales et un
  mode "edit" (titre différent, bouton "Update" au lieu de "Create").

Fichiers : `ui/mission/wizard/MissionWizardWidget.java`,
`states/mission/MissionWizardAppState.java`, `ui/mission/panel/MissionRow.java`.

### 2.4 Marqueurs d'événements sur la timeline — **P1 / M**

`ScrubberTrack` est numérique [0–10] aujourd'hui, sans marqueurs.

- Pour la mission sélectionnée (ou toutes les missions visibles), poser des
  marqueurs sur la timeline aux transitions de stages :
  - Vertical ascent → gravity turn
  - Gravity turn → parking insertion (GEO) ou Hohmann (LEO)
  - Apoapsis burn, periapsis burn, trim burn
  - Mass depletion (si détecteur déclenché)
- Hover marqueur → tooltip avec nom du stage + timestamp.

Fichiers : `ui/timeline/components/ScrubberTrack.java`,
`ui/timeline/TimelineWidget.java`.

### 2.5 Polish général — **P1 / S** (à grouper)

- Affichage clair du statut `FAILED` (couleur + tooltip d'erreur).
- Confirmation avant suppression d'une mission depuis le panel.
- Auto-trigger optimisation après création (décision utilisateur :
  cf. *Question ouverte 2*).
- Cohérence des fonts et couleurs entre wizard et panel.

---

## Phase 3 — Nouveau type de mission : Rendezvous / Phasing (P2)

Objectif : mission qui amène le spacecraft à intercepter une cible existante
(autre satellite, ISS-like) en orbite donnée.

### 3.1 Modèle simulation — **P2 / L**

- `RendezvousMission extends Mission` :
  paramètres = cible (orbite Keplerian ou TLE), tolérance de phasing
  (distance + Δv relatif).
- Stages : ascent + gravity turn (réutilisés) + transfer (Hohmann ou
  bi-elliptic) + **phasing burn(s)** pour caler l'anomalie vraie.
- Nouveau `TrajectoryProblem` : `RendezvousProblem` qui ajoute la
  contrainte de phasing au coût de transfer.
- `RendezvousObjective` (sous `objective/`) : minimise distance finale au
  point de rendez-vous + Δv total.

Fichiers : `simulation/mission/RendezvousMission.java`,
`simulation/mission/optimizer/problems/RendezvousProblem.java`,
`simulation/mission/objective/RendezvousObjective.java`,
`simulation/mission/stage/PhasingStage.java`.

### 3.2 Test unitaire — **P2 / M**

- `RendezvousMissionOptimizationTest extends AbstractTrajectoryOptimizerTest`.
- Scénario : cible à 400 km LEO, anomalie offset 30° → vérifier que
  l'optimiseur converge avec distance finale < seuil (ex : 10 km).

Fichier :
`test/java/.../simulation/mission/optimizer/RendezvousMissionOptimizationTest.java`.

### 3.3 Intégration wizard — **P2 / M**

- Carte `RDV` dans `StepMissionType` (badge `AVAILABLE`).
- Nouvelle variante de `StepParameters` (ou step dédié) pour la cible :
  altitude, inclinaison, anomalie vraie initiale (ou choix d'une mission
  active existante comme cible).
- Branche dans `MissionWizardAppState.createMission()`.
- `MissionType.RENDEZVOUS` ajouté à l'enum.

---

## Questions ouvertes

1. **Naming** : on garde `GTO` (transfer orbit, vocabulaire métier) ou
   on renomme en `GEO` (cohérence avec `GEOMission`) sur la carte wizard et
   dans le panel ?
2. **Auto-optimisation** : après création depuis le wizard, on déclenche
   automatiquement l'optimisation, ou on laisse l'utilisateur cliquer "compute"
   depuis le panel comme aujourd'hui ?

---

## Récap priorisation

| Item | Priorité | Difficulté |
|---|---|---|
| 1.1 `Mission.getType()` + panel label | P0 | S |
| 1.2 Activer carte GEO `StepMissionType` | P0 | S |
| 1.3 Branche GEO `StepParameters` | P0 | M |
| 1.4 Branche GEO `createMission()` | P0 | S |
| 1.5 Retirer seed GEO codé en dur | P0 | S |
| 2.1 Breadcrumb 3D | P1 | M |
| 2.2 Détail mission (panel) | P1 | M |
| 2.3 Action Edit (panel) | P1 | M |
| 2.4 Marqueurs timeline | P1 | M |
| 2.5 Polish général | P1 | S |
| 3.1 Rendezvous simulation | P2 | L |
| 3.2 Rendezvous test | P2 | M |
| 3.3 Rendezvous wizard | P2 | M |

Hors scope court terme (à backlogger) : persistance des missions
(save/load), command palette, vue multi-mission comparée, télémétrie
enrichie par mission.
