# Roadmap court terme — OrbitLab

## Contexte (mise à jour 2026-07-27)

Depuis la dernière version de ce document, la **Phase 1 (GEO end-to-end)** a été
livrée, et un chantier plus large que prévu s'est greffé dessus : un **framework
de composition de mission** et **3 modes d'optimisation** pilotables depuis le
panel. Le détail de ce qui a été fait est archivé en
[Annexe A](#annexe-a--phase-1-geo-end-to-end-terminé). Résumé :

- Le wizard crée des missions **LEO et GEO** de bout en bout (carte GEO active,
  `StepParameters` paramétré par type, `MissionWizardAppState.createMission()`
  branché) — `StepMissionType.java:54-101`, `StepParameters.java:45-82`,
  `MissionWizardAppState.java:63-83`.
- **`MissionComposer`** (`simulation/mission/operation/MissionComposer.java`)
  construit une `Mission` à partir d'un `MissionSpec` (LEO/GEO, immuable,
  sérialise les paramètres du wizard) et d'un `OptimizationType`. `MissionFactory`
  est resserré à son rôle de parsing des valeurs brutes du wizard
  (`specFromWizardValues`, `MissionFactory.java:64-109`) ; la construction de
  mission proprement dite est déléguée à `MissionComposer`.
- **3 modes d'optimisation** (`simulation/mission/OptimizationType.java:11-18`) :
  `FAST` (profil analytique, charges fixes — ancien comportement par défaut),
  `BALANCED` (transfert optimisé CMA-ES, charges fixes), `PRECISE` (transfert
  optimisé + minimisation de propergol via `MissionPlanOptimizer`). Câblés de
  bout en bout : `ModeSegmentedControl` sur chaque ligne du panel
  (`MissionRow.java:146-149`) → `MissionPanelWidget.onSetMode`
  (`MissionPanelWidget.java:161-164`) → `MissionEntry.setOptimizationType`
  (`MissionEntry.java:198-209`, recompose la mission, invalide résultat/éphéméride,
  repasse en `DRAFT`) → `MissionPlanOptimizer.planner()` choisit
  `FixedLoadPlanner` ou `MinimizedLoadPlanner` selon le mode au moment du calcul
  (`MissionPlanOptimizer.java:69-74`).
  **Limite connue** : côté GEO, les 3 modes composent actuellement la **même**
  `GEOMission` analytique (`MissionComposer.java:86-99`, commenté explicitement) —
  GEO n'a pas encore d'équivalent CMA-ES pour la composition d'étages ; seul le
  levier propergol (`PRECISE`) agit réellement sur GEO aujourd'hui.
- **Profil de vol dépendant du lanceur** : `AscentProfile`
  (`simulation/mission/vehicle/model/AscentProfile.java:12-29` — durée
  d'ascension verticale, angle de pitch kick, coast inter-étage) est un champ de
  `LauncherModel` (`LauncherModel.java:20-21`), renseigné par catalogue
  (`Launchers.java:50`) et consommé par `LEOMission` et `GEOMission` pour
  construire `VerticalAscentStage`/`GravityTurnStage`
  (`LEOMission.java:122-130`, `GEOMission.java:81,93,139`). Un seul lanceur est
  catalogué (`FALCON_HEAVY`) donc la variété inter-lanceurs n'est pas encore
  démontrée en pratique — à garder en tête avant d'ajouter un 2ᵉ lanceur.

**Ce que ce chantier a laissé ouvert** — deux régressions/dettes visibles utilisateur,
traitées en priorité ci-dessous (Phase 0) :

1. Le panel affiche toujours `"LEO"` pour **toutes** les missions, y compris GEO
   — `ui/mission/panel/MissionTypes.java:8-15`, TODO jamais résolu. Avant, ce
   TODO était inoffensif (GEO n'était pas créable) ; maintenant qu'il l'est,
   c'est un bug visible dès qu'on crée une mission GEO depuis le wizard.
2. `MissionEntry.setOptimizationType` (recompose côté UI, sur le fil JME) et la
   création de mission (`MissionWizardAppState.createMission()`, qui catch déjà
   les `RuntimeException` de composition) n'ont pas le même filet — un mode
   toggle qui ferait échouer `MissionComposer.compose(...)` planterait sans
   retour utilisateur visible autre qu'un log. Peu probable aujourd'hui (GEO ne
   varie pas par mode), mais à surveiller si GEO gagne un mode CMA-ES.

Légende : **P0/P1/P2** = priorité (P0 = doit être fait d'abord) ;
**S/M/L** = difficulté (Small / Medium / Large).

---

## Phase 0 — Corrections rapides (P0)

Dette directement issue du chantier GEO/modes, plus une nouvelle feature
demandée par l'utilisateur (seek timeline) dont l'infrastructure existe déjà
à moitié.

### 0.1 Réparer l'étiquette de type dans le panel — **P0 / S**

- `MissionTypes.label(entry)` doit lire le vrai type au lieu du
  `DEFAULT_MISSION_TYPE` codé en dur.
- Le type existe déjà : `MissionSpec.type()` (`MissionSpec.java:48`, implémenté
  ligne 87 et 132) renvoie un `MissionType`. `MissionEntry.spec()` l'expose en
  `Optional` (vide seulement pour le chemin legacy `MissionEntry(Mission)`,
  utilisé par `MissionContext.java:32`).
- Donc : `entry.spec().map(MissionSpec::type).map(MissionType::displayName)`
  avec fallback sur l'ancien comportement (ou un label `"—"`) pour les entrées
  legacy sans spec. Pas besoin d'ajouter `Mission.getType()` comme prévu dans
  l'ancienne version de ce document — l'info est déjà portée par `MissionSpec`.

Fichier : `ui/mission/panel/MissionTypes.java`.

### 0.2 Seek timeline (édition de la date) — **P0 / M** — *nouvelle feature*

Aujourd'hui la timeline ne fait que piloter la **vitesse** de lecture. On veut
pouvoir cliquer/glisser sur la piste pour sauter directement à une date/heure
de simulation arbitraire.

- L'infrastructure existe déjà côté horloge :
  `SimulationClock.seek(AbsoluteDate)` (`app/SimulationClock.java:191-206`) est
  thread-safe et émet `SeekPerformed`/`TimeChanged(cause=USER)`. Elle est déjà
  appelée par les boutons pas-à-pas (`TransportControls.java:43,52`, saut de
  `±STEP_SECONDS`) et par le retour au direct (`LiveIndicator.java:87`).
- Ce qui manque : `ScrubberTrack` (`ui/timeline/components/ScrubberTrack.java`)
  n'est câblé qu'à la vitesse. Son callback de drag remonte via
  `TimelineWidget.java:101` vers `applySpeedIndex`
  (`TimelineWidget.java:155-162`), qui appelle uniquement `clock.setSpeed(...)` —
  jamais `clock.seek(...)`.
- Reste à faire :
  - Décider de la représentation : soit une piste dédiée "scrub" séparée de la
    piste "vitesse" actuelle (recommandé — éviter de surcharger un seul widget
    avec deux sémantiques), soit un mode d'interaction distinct sur
    `ScrubberTrack` (drag court = vitesse, drag long / shift-drag = seek).
  - Mapper la position de drag vers une `AbsoluteDate` (borne réaliste : plage
    couverte par l'éphéméride chargée, ou fenêtre glissante autour de `now()`)
    et appeler `clock.seek(date)`.
  - Feedback visuel pendant le drag (tooltip date, comme les marqueurs prévus
    en 2.1) pour que l'utilisateur sache où il va atterrir avant de relâcher.
  - Vérifier l'interaction avec la vitesse courante : un seek pendant lecture
    doit-il mettre en pause, ou continuer à jouer depuis la nouvelle date ?
    (à trancher avec l'utilisateur — cf. *Question ouverte 3*).

Fichiers : `ui/timeline/components/ScrubberTrack.java`,
`ui/timeline/TimelineWidget.java`, `app/SimulationClock.java` (déjà prêt, pas
de modif attendue sauf besoin de bornes).

### 0.3 Filet d'erreur sur le recompose de mode — **P0 / S**

- `MissionEntry.setOptimizationType` (`MissionEntry.java:198-209`) appelle
  `MissionComposer.compose(...)` sans try/catch, contrairement à
  `MissionWizardAppState.createMission()` qui catch déjà les
  `RuntimeException` de composition.
- Envelopper l'appel, garder l'ancienne mission + l'ancien mode si la
  recomposition échoue, logger et remonter un statut visible (rejoint 1.1 —
  affichage `FAILED`/erreur dans le panel).

Fichier : `simulation/mission/context/MissionEntry.java`.

---

## Phase 1 — Panel : détail, édition, retour d'erreur (P1)

Le panel reste la plus grosse dette UI : lecture seule, métadonnées
sommaires, aucune action câblée au-delà de compute/delete. Avec 3 modes et
des résultats d'optimiseur réellement disponibles (`MissionEntry.
getOptimizerResult()`), l'absence de vue détail est plus visible qu'avant.

### 1.1 Vue détail mission dans le panel — **P1 / M**

- `PanelFooter` (`ui/mission/panel/PanelFooter.java`) affiche encore une
  `DUMMY_ALTITUDE = "380 km"` codée en dur (ligne 24, commentée comme
  placeholder) et une seule ligne résumé (type / véhicule / alt / launch,
  lignes 78-86) — jamais de lecture de `MissionOptimizerResult`.
- Ajouter une zone de détails (extension du footer ou sous-panel) affichée sur
  sélection d'une ligne :
  - Type (une fois 0.1 fait), statut, mode d'optimisation courant, scheduled
    date, launch site.
  - Liste des stages (nom, durée, Δv approx).
  - Pour `READY` : altitude finale, inclinaison finale, écart à la cible —
    lit `entry.getOptimizerResult()`.
  - Pour `FAILED` : message d'erreur lisible (rejoint 0.3).
- Réutiliser `FormStyles` / `UiKit`.

Fichiers : `ui/mission/panel/PanelFooter.java` (ou nouveau
`MissionDetailsView.java`), `ui/mission/panel/MissionPanelWidget.java`.

### 1.2 Implémenter l'action "Edit" du panel — **P1 / M**

- L'icône est déjà câblée jusqu'au handler : `RowActionIcons` →
  `MissionRow.java:142` → `MissionPanelWidget.onEdit`
  (`MissionPanelWidget.java:151-153`) — mais le handler ne fait qu'un
  `logger.info("Edit not yet implemented ...")`.
- Click "Edit" → rouvrir le wizard pré-rempli avec les valeurs du
  `MissionSpec` de l'entrée (type non modifiable — cohérent avec le fait que
  seules les entrées avec `spec()` non vide sont éditables ; les entrées
  legacy n'en ont pas).
- Validation → recompose via `MissionComposer` (remplace `entry.mission()`,
  invalide résultat/éphéméride comme le fait déjà `setOptimizationType`).
- Étendre `MissionWizardWidget` pour accepter des valeurs initiales et un
  mode "edit" (titre différent, bouton "Update").

Fichiers : `ui/mission/wizard/MissionWizardWidget.java`,
`states/mission/MissionWizardAppState.java`, `ui/mission/panel/MissionRow.java`,
`ui/mission/panel/MissionPanelWidget.java`.

### 1.3 Feedback de progression pendant l'optimisation — **P1 / M**

Reporté depuis `specs/mission-rework/11-post-i7-suites.md` §3.6 (Tâche 3, différée
à l'époque faute d'UI mission stable — elle l'est maintenant). Contraintes
mesurées à respecter :

- Le coût d'une évaluation varie d'un facteur ~5 (GT qui converge normalement
  vs. GT épinglée sur son plancher d'étagement) et n'est pas prévisible à
  l'avance : une barre linéaire en nombre d'évaluations sera par moments très
  fausse. Préférer un indicateur indéterminé (spinner) avec le nombre
  d'évaluations écoulées en texte, plutôt qu'une vraie barre de progression.
- Le mode `PRECISE` (GEO en particulier) peut désormais **lever une exception**
  pour des charges où la GT ne consomme pas S1 (cf. mémoire I7) — le wizard/panel
  n'a aucune gestion d'erreur pour ce cas. À couvrir en même temps que 1.1
  (affichage `FAILED`) et 0.3 (filet d'erreur).

Fichiers : `states/mission/*` (état de calcul déjà suivi via `MissionStatus`),
`ui/mission/panel/MissionRow.java`, `ui/mission/panel/PanelFooter.java`.

### 1.4 Polish général — **P1 / S** (à grouper)

- Confirmation avant suppression d'une mission depuis le panel.
- Cohérence des fonts et couleurs entre wizard et panel.
- Auto-optimisation après création — toujours en question (cf. *Question
  ouverte 2*) ; aujourd'hui `createMission()` ajoute l'entrée en `DRAFT` sans
  déclencher de calcul (`MissionWizardAppState.java:63-83`), l'utilisateur doit
  cliquer "compute" depuis le panel.

---

## Phase 2 — Timeline & navigation 3D (P1/P2)

### 2.1 Marqueurs d'événements sur la timeline — **P1 / M**

Dépend de 0.2 (seek) : les marqueurs n'ont de sens que si cliquer dessus peut
effectivement sauter à cette date.

- `ScrubberTrack` n'a aucune notion de mission ou de stage aujourd'hui — ni le
  fichier ni `TimelineWidget` ne référencent `MissionEntry`/`MissionStage`.
  Les graduations actuelles (`TICK_COUNT = 21`) sont décoratives, indexées sur
  la vitesse, pas sur le temps de simulation.
- Pour la mission sélectionnée (ou toutes les missions visibles), poser des
  marqueurs aux transitions de stages : vertical ascent → gravity turn,
  gravity turn → parking/Hohmann, apoapsis/periapsis/trim burn, mass
  depletion.
- Hover marqueur → tooltip nom du stage + timestamp ; click → `clock.seek(...)`
  sur ce timestamp (réutilise 0.2).

Fichiers : `ui/timeline/components/ScrubberTrack.java`,
`ui/timeline/TimelineWidget.java`.

### 2.2 Breadcrumb de navigation 3D — **P2 / M** *(rétrogradé de P1)*

Suit intégralement la spec `specs/navigation/01-breadcrumb.md`. Aucun fichier
n'existe encore (`ui/breadcrumb/`, `states/scene/BreadcrumbWidgetAppState.java`
absents) — le chantier n'a pas commencé. Rétrogradé de P1 à P2 : c'est de la
navigation générale, indépendante du travail mission/optimisation en cours ;
les items 0.x/1.x/2.1 ci-dessus ont plus de valeur immédiate pour exploiter ce
qui vient d'être livré (GEO, modes, profils lanceur).

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

---

## Phase 3 — Nouveau type de mission : Rendezvous / Phasing (P2)

Inchangé depuis la version précédente — toujours après le polish panel/timeline
et le breadcrumb, vu la taille (L) et le fait que GEO vient tout juste d'être
stabilisé (cf. `specs/mission-rework/11-post-i7-suites.md` — le −5,6 % GEO et
l'injection node-aware ont été validés le 2026-07-25, encore frais).

### 3.1 Modèle simulation — **P2 / L**

- `RendezvousMission extends Mission` (ou un `MissionSpec.Rendezvous` +
  `MissionComposer.composeRendezvous`, pour rester cohérent avec le nouveau
  framework de composition plutôt que de repartir sur l'ancien pattern
  `Mission` monolithique) : paramètres = cible (orbite Keplerian ou TLE),
  tolérance de phasing (distance + Δv relatif).
- Stages : ascent + gravity turn (réutilisés) + transfer (Hohmann ou
  bi-elliptic) + **phasing burn(s)** pour caler l'anomalie vraie.
- Nouveau `TrajectoryProblem` : `RendezvousProblem` qui ajoute la contrainte
  de phasing au coût de transfer.
- `RendezvousObjective` (sous `objective/`) : minimise distance finale au
  point de rendez-vous + Δv total.

Fichiers : `simulation/mission/operation/MissionSpec.java` (nouvelle variante),
`simulation/mission/operation/MissionComposer.java`,
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

- Carte `RDV` dans `StepMissionType` (badge `AVAILABLE`), suit le pattern LEO/GEO
  déjà en place (`StepMissionType.java:54-101`).
- Nouvelle variante de `StepParameters` pour la cible : altitude, inclinaison,
  anomalie vraie initiale (ou choix d'une mission active existante comme cible).
- `MissionType.RENDEZVOUS` ajouté à l'enum ; branché dans
  `MissionFactory.specFromWizardValues` et `MissionComposer.compose`.

---

## Questions ouvertes

1. ~~**Naming** : GTO ou GEO sur la carte wizard ?~~ **Tranché** : la carte
   affiche `"GEO"` (`StepMissionType.java:70`), cohérent avec `GEOMission` et
   `MissionType.GEO`.
2. **Auto-optimisation** : après création depuis le wizard, on déclenche
   automatiquement l'optimisation, ou on laisse l'utilisateur cliquer "compute"
   depuis le panel comme aujourd'hui (comportement actuel confirmé, voir 1.4) ?
3. **Seek pendant lecture** (nouvelle, liée à 0.2) : un seek déclenché pendant
   que la simulation joue doit-il mettre en pause automatiquement, ou continuer
   à jouer depuis la nouvelle date ?

---

## Récap priorisation

| Item | Priorité | Difficulté |
|---|---|---|
| 0.1 Réparer l'étiquette de type (panel) | P0 | S |
| 0.2 Seek timeline (édition de date) | P0 | M |
| 0.3 Filet d'erreur sur recompose de mode | P0 | S |
| 1.1 Détail mission (panel) | P1 | M |
| 1.2 Action Edit (panel) | P1 | M |
| 1.3 Feedback progression optimisation | P1 | M |
| 1.4 Polish général | P1 | S |
| 2.1 Marqueurs timeline | P1 | M |
| 2.2 Breadcrumb 3D | P2 | M |
| 3.1 Rendezvous simulation | P2 | L |
| 3.2 Rendezvous test | P2 | M |
| 3.3 Rendezvous wizard | P2 | M |

Hors scope court terme (à backlogger) : persistance des missions
(save/load), command palette, vue multi-mission comparée, télémétrie
enrichie par mission, 2ᵉ lanceur au catalogue (pour éprouver la variété
`AscentProfile`), mode CMA-ES pour la composition GEO (aujourd'hui figée en
analytique quel que soit le mode). Suivi optimiseur/backend (tolérances,
warm-start cross-λ, etc.) reste tracké dans
`specs/mission-rework/11-post-i7-suites.md`, pas ici.

---

## Annexe A — Phase 1 GEO end-to-end (terminé)

Historique, conservé pour traçabilité. Tous les items ci-dessous sont **✔ FAIT** :

| Item | Statut |
|---|---|
| Carte GEO active dans `StepMissionType` | ✔ `StepMissionType.java:54-101` |
| `StepParameters` paramétré par type (LEO/GEO) | ✔ `StepParameters.java:45-82,133-137` |
| `MissionWizardAppState.createMission()` branché GEO | ✔ `MissionWizardAppState.java:63-83`, via `MissionFactory.specFromWizardValues` + `MissionComposer.compose` |
| Seed GEO codé en dur retiré | ✔ `MissionPanelWidgetAppState.java` ne contient plus de `GEOMission(...)` |
| `Mission.getType()` / étiquette panel | ✘ jamais fait tel quel — remplacé par `MissionSpec.type()`, mais `MissionTypes.label()` ne le lit pas encore (→ Phase 0.1) |

Le dernier item explique pourquoi Phase 0.1 existe : le plan initial prévoyait
d'ajouter le type directement sur `Mission`, mais le framework de composition
qui a été construit à la place porte déjà cette information sur `MissionSpec` —
il ne restait qu'à faire lire `MissionTypes.label()` depuis là, ce qui n'a pas
été fait.
