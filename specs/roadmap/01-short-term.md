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

**Ce que ce chantier avait laissé ouvert** — les deux régressions/dettes visibles
utilisateur ont été **corrigées le 2026-07-27** (détail en Phase 0, items 0.1 et
0.3, tous deux ✔) :

1. ~~Le panel affiche toujours `"LEO"` pour toutes les missions, y compris GEO.~~
   Corrigé : `MissionTypes.label()` lit `MissionSpec.type()`.
2. ~~`MissionEntry.setOptimizationType` recompose sans filet sur le fil JME.~~
   Corrigé : composition sous `try`, rollback complet, statut `FAILED`.

La **feature demandée par l'utilisateur** — le seek par saisie manuelle de date
sur la timeline (0.2) — a été livrée le même jour. La Phase 0 est donc close ;
la prochaine priorité est la Phase 1 (panel).

Légende : **P0/P1/P2** = priorité (P0 = doit être fait d'abord) ;
**S/M/L** = difficulté (Small / Medium / Large).

---

## Phase 0 — Dette soldée + seek timeline (P0) — **close**

La dette issue du chantier GEO/modes est soldée (0.1 et 0.3 ✔) et la feature
seek timeline est livrée (0.2 ✔). Les trois items sont conservés ci-dessous
pour la traçabilité des écarts d'implémentation.

### 0.1 Réparer l'étiquette de type dans le panel — **✔ FAIT (2026-07-27)**

`MissionTypes.label(entry)` lit désormais le vrai type :
`entry.spec().map(MissionSpec::type).map(MissionType::displayName)`.

Écarts avec le plan initial, à connaître :

- **Fallback = `"—"`**, pas l'ancien `"LEO"`. Le chemin legacy
  `MissionContext.addMission(Mission)` n'a plus aucun appelant en production
  (seul `MissionDisplayPanelRulesTest` l'utilise) : un repli `"LEO"` ne
  couvrait aucun cas réel et reconduisait l'affirmation fausse qu'on
  corrigeait. `"—"` reprend l'idiome déjà présent dans `PanelFooter:75` pour
  un véhicule inconnu.
- **`MissionType.displayName()` a dû être ajouté** — il n'existait pas.
  Implémenté en déléguant à `name()` (pas de champ dupliquant `"LEO"`/`"GEO"`,
  qui ne ferait que dériver) ; l'accesseur existe pour que l'UI ne dépende pas
  de l'orthographe des constantes.
- Corrige d'un coup les deux appelants : colonne Type de la ligne
  (`MissionRow.java:84`) et ligne de détail du footer (`PanelFooter.java:80`).

Fichiers : `ui/mission/panel/MissionTypes.java`,
`simulation/mission/MissionType.java`.

### 0.2 Seek par saisie manuelle d'une date — **✔ FAIT (2026-07-27)**

Le libellé de date est éditable : clic → champ prérempli, `Entrée` valide,
`Échap`/perte de focus annulent, refus explicite hors couverture éphéméride.

**Écart principal — la boîte de date était déjà trop petite pour son propre
texte.** `2030-03-14 09:26:53` mesure **115 px** en `share-tech-mono-12`, dans
une boîte de 86 px : Lemur impose `box = taille du composant`
(`TextComponent.reshape`) et `BitmapText` est en `LineWrapMode.Word`, donc la
date s'affichait **sur deux lignes** avant ce chantier. Un `TextField` au même
endroit aurait été pire (`TextEntryComponent` force `Clip` + défilement
horizontal : 14 caractères visibles sur 19, qui défilent pendant la frappe).

Comme l'édition ne doit rien déplacer, le cluster est donc dimensionné une fois
pour les deux états : **boîte 128 px** (116 px de texte + 2×6 px de padding),
les 42 px pris sur le scrubber (167 → 125 px, piste décorative aujourd'hui, que
2.1 doit refaire de toute façon). `LiveIndicator`, `TransportControls` et les
dividers 1 et 2 ne bougent pas ; la capsule reste 600×52. Les positions du
divider 3, du stepper et du scrubber se recalculent seules
(`TimelineWidget` les dérive de `clockDisplay.leftEdge()`).

Autres écarts, à connaître :

- **Alignement à gauche imposé par Lemur** : `resetCursorPosition()` calcule le
  x du curseur depuis le bord gauche **sans tenir compte du `hAlignment`**. Le
  libellé est donc passé de `Right` à `Left`, et libellé et champ partagent
  x, police et ligne de base (alignement sur la ligne de glyphes, pas sur la
  boîte) — zéro pixel de saut au clic.
- **Le gel du refresh est dans `ClockDisplay.update()`**, pas dans
  `TimelineWidget` : l'état d'édition ne fuit pas hors du composant, et
  `TimelineWidget` garde son appel inconditionnel. Le tick sert alors à
  effacer l'état d'erreur dès que la saisie change. Idem pour `clock.seek(...)`,
  appelé par `ClockDisplay` comme `LiveIndicator` le fait déjà.
- **Style dédié plutôt que `UiKit.newInputField`** : nouveau sélecteur
  `textField` dans le style `timeline` (mono 12, insets 0, sans fond). Le champ
  wizard aurait apporté ibmPlexMono 11, insets 8/12 et le 9-slice wizard, soit
  36 px de haut dans une capsule de 52. La boîte visible (24 px, `btn-hover` au
  survol / `btn-active` en édition) reprend le gabarit des boutons transport.
- **`ÉCHAP` doit être ajouté explicitement** à l'action map du champ : son
  `keyChar` est < 32 donc Lemur ne le consomme pas par défaut. Bonne nouvelle
  en revanche pour les binds globaux de `SimulationClockAppState` (`ESPACE`,
  `-`, `=`, `BACKSPACE`, `←`, `→`) : `KeyInterceptState` +
  `TextEntryComponent.KeyHandler` les consomment tant que le champ a le focus.
- **Parsing STRICT** : `DateTimeFormatter.ofPattern` résout en SMART par défaut
  et ramenait silencieusement `2030-02-31` au 28. Refusé désormais — cohérent
  avec le refus de clamp sur les bornes.
- **Bug préexistant corrigé dans `TimeConverter.fromUtcLocalDateTime`** : le
  passage par `java.util.Date` faisait lever Orekit
  (`out of range seconds number`) pour **toute date antérieure à 1970**, le
  constructeur `AbsoluteDate(Date, TimeScale)` découpant l'epoch en `/` et `%`
  qui tronquent vers zéro. La conversion se fait désormais par composantes
  calendaires ; ces dates sont donc parsées puis refusées normalement par les
  bornes. Le parseur est couvert par un test de totalité : rien de ce qui est
  tapé ne doit remonter dans la boucle clavier de Lemur.
- **Saisie invalide → le champ reste ouvert** en état erreur (au lieu de se
  refermer), pour corriger la faute de frappe sans tout retaper.
- **Message d'erreur hors capsule** : il n'y a pas 224 px à l'intérieur pour un
  intervalle de dates. Bulle posée au-dessus du cluster, donc aucune géométrie
  interne touchée. Bornes affichées en UTC (≈ 37 s d'écart avec le TAI stocké).
- **Bornes exposées par deux méthodes `default`** sur `EphemerisSource`
  (`coverageStart` / `coverageEndExclusive`) : l'interface reste
  `@FunctionalInterface`, les sources analytiques renvoient `Optional.empty()`.

Couvert par des tests : le parseur tolérant (`TimeConverterTest`, 7 cas dont les
dates antérieures à 1970 et la totalité du parseur). **Non vérifié à l'écran** :
les critères d'acceptation 1 à 6 ci-dessous demandent l'application lancée.

**Extension hors périmètre 0.2 — la date de lancement du wizard.** Même geste
utilisateur, même faiblesse : `MissionWizardAppState.createMission` reparsait la
valeur avec `new AbsoluteDate(String, utc)`, **hors du `try`**, donc dans la
boucle de rendu. Aligné sur la timeline : `StepParameters.validateLaunchDate()`
refuse format invalide et date hors couverture, en teignant le champ et en
remplaçant le libellé d'aide (`UTC · Orekit epoch`) par la raison du refus ;
`MissionWizardWidget.goNext()` bloque le passage à l'étape suivante **et** la
création, donc le wizard ne se ferme plus sur une date inutilisable ;
l'`AppState` garde un filet qui log et ne crée rien. La politique de couverture
commune aux deux widgets est dans `ui/EphemerisWindow.java` (`covers`,
`rangeLabel`), pour que les deux refusent la même chose avec les mêmes mots.

Fichiers : `ui/timeline/components/ClockDisplay.java`,
`ui/timeline/TimelineStyles.java`, `app/converters/TimeConverter.java`,
`simulation/source/EphemerisSource.java`,
`simulation/source/DatasetEphemerisSource.java`. Non modifiés comme prévu :
`app/SimulationClock.java`, `states/ephemeris/EphemerisAppState.java` — et
`ui/timeline/TimelineWidget.java` n'a reçu que des commentaires.

#### Spécification d'origine

*Conservée pour la traçabilité.*

**Besoin.** Amener la simulation à une date **précise** — un survol, une date
de lancement, un instant relevé dans un log — sans glisser un curseur et sans
attendre que l'horloge y arrive. La saisie texte est le bon geste ici : elle
est exacte à la seconde, ce qu'un drag sur 300 px de piste ne sera jamais.
Aujourd'hui la timeline ne pilote que la **vitesse** de lecture ; la date
(`ClockDisplay`) est en lecture seule.

*Périmètre resserré par rapport à la version précédente de ce document* : le
glisser-déposer sur la piste n'est plus dans cet item — il est déplacé en 2.2,
avec les marqueurs qui en dépendent. Ce n'est pas le besoin exprimé.

#### Comportement attendu

1. **Point d'entrée** : le libellé de date de la timeline devient éditable —
   click → champ de saisie prérempli avec la date courante. (Alternative
   écartée : un bouton « GOTO » séparé, la capsule fait déjà 600 px pour 5
   clusters.)
2. **Format saisi** : celui qui est affiché, `yyyy-MM-dd HH:mm:ss`, en UTC.
   Accepter aussi l'ISO `yyyy-MM-ddTHH:mm:ssZ` et la date seule `yyyy-MM-dd`
   (→ `00:00:00`).
3. **Validation** : `Entrée` valide, `Échap` annule et restaure la date
   courante, la perte de focus annule (jamais de seek implicite).
4. **Saisie non parsable** : champ en état erreur, pas de saut, la date
   courante reste affichée. Pas de modale, pas de log d'erreur.
5. **Date hors couverture éphéméride** : **refusée**, avec un message donnant
   l'intervalle admissible — pas de clamp silencieux. Atterrir en 2101 quand on
   a demandé 2150 est plus déroutant qu'un refus explicite.
6. **Date acceptée** : `clock.seek(date)`. Vitesse et état lecture/pause
   inchangés — c'est déjà le contrat documenté de `SimulationClock.seek`
   (« Does not change playing state », `SimulationClock.java:190`).
7. **Piège d'implémentation principal** : `TimelineWidget.update()` appelle
   `clockDisplay.update(clock.now())` **à chaque frame** (`TimelineWidget.java:114`).
   L'édition doit suspendre ce rafraîchissement, sinon la saisie est effacée
   pendant la frappe.

#### Bornes admissibles

- **Source de vérité** : `DatasetEphemerisSource`, `[1990-01-01, 2101-01-01[`
  en TAI (`DatasetEphemerisSource.java:33-35`). Hors de cet intervalle,
  `sampleIcrf` lève `OrbitlabException`.
- **Seul vrai manque backend** : ces bornes sont des constantes **privées**.
  Les exposer (accesseurs sur `DatasetEphemerisSource`, plus des méthodes
  `default` sur `EphemerisSource` — l'interface reste `@FunctionalInterface`,
  une seule méthode abstraite), lisibles depuis l'UI via
  `EphemerisSourceRegistry.get()`.
- Bornes en **TAI**, saisie en **UTC** : convertir pour le message d'erreur
  (~37 s d'écart, sans conséquence pratique, mais autant afficher les bornes
  dans l'échelle où l'utilisateur saisit).
- **Aucune source publiée** (`EphemerisSourceRegistry.get()` vide) : pas de
  bornes, accepter toute date parsable — dégradation cohérente avec le repli
  keplerien de `EphemerisSource.sampleIcrfSafe`.

#### Déjà en place (rien à écrire)

- `SimulationClock.seek(AbsoluteDate)` (`SimulationClock.java:191-206`),
  thread-safe, émet `SeekPerformed` + `TimeChanged(USER)`. Déjà appelée par le
  pas-à-pas (`TransportControls.java:43,52`) et le retour au direct
  (`LiveIndicator.java:87`).
- **Le saut arbitraire est déjà géré côté données** : `EphemerisAppState`
  s'abonne à `SeekPerformed` et déclenche `EphemerisWorker.onSeek(newTime)`
  (`EphemerisAppState.java:100-106`), qui reconstruit la fenêtre glissante en
  entier. Un saut de 50 ans n'est pas plus coûteux à câbler qu'un saut de 5
  minutes.
- Champ de saisie stylé : `UiKit.newInputField(...)`, déjà utilisé pour la date
  de lancement du wizard (`StepParameters.java:92`).
- **Attention, deux formats de date coexistent dans l'app** :
  `TimeConverter.formatDate` produit `2030-03-14 09:26:53` (affiché par la
  timeline) tandis que `OrekitTime.formatDate` produit
  `2030-03-14T09:26:53Z` (écrit par le wizard, reparsé par
  `new AbsoluteDate(String, utc)` dans `MissionWizardAppState.java:72`). Le
  parseur du champ doit accepter les deux, sinon un copier-coller d'un bout à
  l'autre de l'app échoue.

#### Critères d'acceptation

1. Saisir `2030-03-14 09:26:53` → la simulation saute à cette date, le libellé
   l'affiche, les corps se repositionnent une fois la fenêtre reconstruite.
2. `2150-01-01` → refus, message indiquant l'intervalle, aucun saut.
3. `hier` → refus, aucun saut, aucune exception dans les logs.
4. `Échap` en cours d'édition → état strictement inchangé.
5. Seek pendant une lecture à 100× → la lecture continue depuis la nouvelle
   date, à la même vitesse.
6. Après un seek accepté, **aucun** `Unexpected error in ephemeris worker tick`
   dans les logs — le worker attrape les `Throwable` par tick
   (`EphemerisWorker.java:103`), donc une borne mal validée se manifesterait
   par un spam d'erreurs et des corps figés, pas par un crash.

Fichiers : `ui/timeline/components/ClockDisplay.java` (édition inline),
`ui/timeline/TimelineWidget.java` (suspendre le refresh pendant l'édition,
câbler le seek), `simulation/source/DatasetEphemerisSource.java` +
`simulation/source/EphemerisSource.java` (exposer les bornes),
`app/converters/TimeConverter.java` (helper de parsing tolérant, à ajouter).
Pas de modif attendue : `app/SimulationClock.java`,
`states/ephemeris/EphemerisAppState.java`.

### 0.3 Filet d'erreur sur le recompose de mode — **✔ FAIT (2026-07-27)**

`MissionEntry.setOptimizationType` compose désormais sous `try` et ne publie
rien tant que la composition n'a pas réussi.

- **Ordre des affectations corrigé au passage** : `this.optimizationType` était
  affecté *avant* `compose(...)`. Même enveloppé, l'ancien code aurait laissé
  l'entrée avec un mode ne correspondant plus à sa mission. Mode, mission,
  invalidation résultat/éphéméride sont maintenant publiés ensemble, après
  succès.
- Sur `RuntimeException` : log `error` (mode visé, mission, mode conservé,
  exception), `mission.setStatus(MissionStatus.FAILED)`, retour — ancienne
  mission, ancien mode, résultat et éphéméride tous intacts.
- **Aucun changement UI nécessaire** : `MissionPanelWidget.onSetMode` appelle
  déjà `refresh()`, et `ModeSegmentedControl` ne garde pas d'état de sélection
  (il lit `entry.getOptimizationType()` au build) — la ligne se reconstruit
  avec l'ancien mode toujours actif et le statut `FAILED`.
- Effet de bord assumé : `FAILED` bloque `TOGGLE_VISIBLE` (gaté sur `READY`,
  `MissionOrchestratorAppState.java:126`). Une mission déjà affichée reste
  rendue mais n'est plus re-togglable avant recalcul.
- **Pas de test unitaire** : `MissionComposer.compose` est aujourd'hui total
  pour tout `MissionSpec` constructible (interface scellée sur `Leo`/`Geo`,
  aucun constructeur traversé ne valide ni ne lève). Forcer un échec
  demanderait un seam d'injection dans `MissionEntry` — plus coûteux que le
  filet lui-même. Le garde-fou vise le jour où GEO gagne un mode CMA-ES.

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
  - Type (déjà disponible via `MissionTypes.label()` depuis 0.1), statut, mode
    d'optimisation courant, scheduled date, launch site.
  - Liste des stages (nom, durée, Δv approx).
  - Pour `READY` : altitude finale, inclinaison finale, écart à la cible —
    lit `entry.getOptimizerResult()`.
  - Pour `FAILED` : message d'erreur lisible. 0.3 pose déjà le statut mais **ne
    conserve pas le message** — l'exception n'est que loguée, ni
    `MissionEntry` ni `Mission` ne la stockent. Cet item doit donc ajouter le
    champ (ex. `MissionEntry.lastError`) et le renseigner aux deux endroits qui
    passent en `FAILED` : `MissionEntry.setOptimizationType` et
    `MissionOrchestratorAppState.java:179`.
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
  pour des charges où la GT ne consomme pas S1 (cf. mémoire I7). Ce cas-là est
  déjà rattrapé côté calcul (`MissionOrchestratorAppState.java:178-181`, statut
  `FAILED`), et 0.3 a fermé le trou symétrique côté toggle de mode. Ce qui
  manque encore est le **message** rendu à l'utilisateur — à traiter avec 1.1.

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

### 2.1 Piste indexée sur le temps + marqueurs d'événements — **P1 / M**

Ne dépend **pas** de 0.2 : `clock.seek(...)` existe déjà et 0.2 ne fait que
l'appeler depuis un champ texte. Ce qui manque ici est autre chose — une piste
qui représente le **temps** et non la vitesse.

- `ScrubberTrack` n'a aucune notion de mission, de stage ni même de date : ni
  le fichier ni `TimelineWidget` ne référencent `MissionEntry`/`MissionStage`,
  et les graduations (`TICK_COUNT = 21`) sont décoratives, indexées sur la
  vitesse. **Premier travail : définir la fenêtre temporelle représentée par la
  piste** (durée de la mission sélectionnée ? fenêtre glissante autour de
  `now()` ?) et la fonction temps ↔ position.
- Sur cette base, poser des marqueurs aux transitions de stages pour la mission
  sélectionnée (ou toutes les missions visibles) : vertical ascent → gravity
  turn, gravity turn → parking/Hohmann, apoapsis/periapsis/trim burn, mass
  depletion.
- Hover marqueur → tooltip nom du stage + timestamp ; click → `clock.seek(...)`
  sur ce timestamp.
- Reste en **lecture + click discret** : le glisser continu est en 2.2.

Fichiers : `ui/timeline/components/ScrubberTrack.java`,
`ui/timeline/TimelineWidget.java`.

### 2.2 Scrub continu (glisser sur la piste) — **P2 / M** *(extrait de 0.2)*

Faisait partie de l'ancien item 0.2, retiré parce que le besoin réel est la
saisie d'une date exacte. Le drag reste utile pour l'exploration approximative
(« qu'est-ce qui se passe vers le milieu de la mission ? »), mais il est
subordonné à 2.1 : sans piste indexée sur le temps, il n'y a rien à parcourir.

- Aujourd'hui le callback de drag de `ScrubberTrack` remonte via
  `TimelineWidget.java:101` vers `applySpeedIndex`
  (`TimelineWidget.java:155-162`), qui n'appelle que `clock.setSpeed(...)` —
  jamais `clock.seek(...)`.
- Trancher la cohabitation des deux sémantiques sur un même widget : piste
  « scrub » dédiée séparée de la piste « vitesse » (recommandé), ou modes
  d'interaction distincts (drag court = vitesse, shift-drag = seek).
- Feedback pendant le drag : tooltip de la date visée avant relâchement —
  sinon l'utilisateur navigue à l'aveugle.
- Attention au débit de seeks : chaque `seek` déclenche une reconstruction
  complète de la fenêtre éphéméride (`EphemerisWorker.onSeek`). Un drag émet
  potentiellement un seek par frame → n'émettre qu'au relâchement, ou étrangler.

Fichiers : `ui/timeline/components/ScrubberTrack.java`,
`ui/timeline/TimelineWidget.java`.

### 2.3 Breadcrumb de navigation 3D — **P2 / M** *(rétrogradé de P1)*

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
3. **Seek pendant lecture** (liée à 0.2) : ~~mettre en pause automatiquement ou
   continuer à jouer ?~~ **Défaut retenu : on ne touche pas à l'état de
   lecture**, c'est le contrat déjà documenté de `SimulationClock.seek` et le
   comportement des boutons pas-à-pas existants. À rouvrir seulement si
   l'usage montre qu'atterrir en pleine lecture à 100× est désagréable.
4. **Bornes hors couverture** (liée à 0.2) : ~~refuser ou clamper une date hors
   dataset ?~~ **Tranché : refus explicite** avec affichage de l'intervalle
   admissible (cf. 0.2, comportement 5).

---

## Récap priorisation

| Item | Priorité | Difficulté |
|---|---|---|
| ~~0.1 Réparer l'étiquette de type (panel)~~ | ✔ FAIT | S |
| ~~0.3 Filet d'erreur sur recompose de mode~~ | ✔ FAIT | S |
| ~~0.2 Seek par saisie manuelle d'une date~~ | ✔ FAIT | S |
| 1.1 Détail mission (panel) | P1 | M |
| 1.2 Action Edit (panel) | P1 | M |
| 1.3 Feedback progression optimisation | P1 | M |
| 1.4 Polish général | P1 | S |
| 2.1 Piste indexée temps + marqueurs | P1 | M |
| 2.2 Scrub continu (drag) | P2 | M |
| 2.3 Breadcrumb 3D | P2 | M |
| 3.1 Rendezvous simulation | P2 | L |
| 3.2 Rendezvous test | P2 | M |
| 3.3 Rendezvous wizard | P2 | M |

Changements de priorité de cette révision : 0.2 passe de **M à S** (le drag et
son feedback en sortent, il ne reste qu'un champ texte, un parseur et
l'exposition des bornes) ; le drag devient **2.2 / P2**, subordonné à 2.1 qui
doit d'abord donner un sens temporel à la piste ; le breadcrumb glisse en 2.3
sans changer de priorité.

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
| `Mission.getType()` / étiquette panel | ✔ autrement — `Mission.getType()` jamais ajouté, le type est porté par `MissionSpec.type()` et lu par `MissionTypes.label()` depuis le 2026-07-27 (Phase 0.1) |

Le dernier item explique pourquoi Phase 0.1 a existé : le plan initial prévoyait
d'ajouter le type directement sur `Mission`, mais le framework de composition
construit à la place porte déjà cette information sur `MissionSpec` — il ne
restait qu'à faire lire `MissionTypes.label()` depuis là. C'est fait.
