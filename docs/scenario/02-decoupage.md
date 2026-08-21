# UI-3 — Persistance des missions — découpage

Item roadmap : `UI-3` (★4 ◆3 M), phase 3. Ce document ne conçoit pas en détail : il
**découpe**. Chaque lot y est défini par la propriété qu'il rend vraie, par ce qu'il
consomme, par ce qu'il produit et par le test qui le ferme.

Il ne remplace pas [`01-persistance-missions.md`](01-persistance-missions.md), qui reste la
conception. Il en dérive l'ordre d'exécution : **le format d'abord, l'interface en dernier**.

---

## 1. Périmètre

**Dans UI-3** — les cinq lots du §4 :

- le format de fichier v1 et son aller-retour, testé hors application ;
- le mode de rejeu, qui refait voler une mission depuis ses vecteurs sans CMA-ES ;
- le magasin sur disque et la logique de session ;
- les deux entrées de menu et la fenêtre de sélection ;
- la réparation de `MISSION_HORIZON_DAYS`, qui est une pièce porteuse du format et non une
  réparation opportuniste ([`01-…`](01-persistance-missions.md) §4.3).

**Hors UI-3**, et à ne pas y laisser glisser : l'enregistrement et le chargement
automatiques, le sélecteur de fichier système, l'export mono-mission, le champ « atmosphère »
au wizard, et tout fichier de préférences utilisateur. La liste complète et ses raisons sont
au §10 de la conception.

---

## 2. État des lieux

Les six mesures qui fondent le découpage sont au §1 de la conception et ne sont pas
répétées. Trois d'entre elles décident de l'ordre des lots :

| Mesure | Conséquence sur le découpage |
|---|---|
| `WizardPrefill` ↔ `MissionFactory` forment déjà un aller-retour exact (§1.1) | le lot format est **purement additif** : il ne modifie aucun fichier existant |
| Aucun chemin de rejeu n'existe, mais les étages ne relisent que `bestVariables()` (§1.4) | le rejeu est un lot à part entière, et c'est le seul qui touche le cœur d'optimisation |
| `WizardPrefill` n'écrit pas `MISSION_HORIZON_DAYS` (§1.6) | c'est un **changement de comportement**, donc son propre lot, avant le format |

---

## 3. Principe du découpage

Trois règles, reprises de `PHY-1` parce qu'elles y ont tenu du premier au dernier lot :

1. **Un changement de comportement à la fois.** C'est ce qui sort la réparation de l'horizon
   du lot format : elle change ce que voit l'utilisateur qui rouvre une mission, le format
   ne change rien pour personne.
2. **Chaque lot se ferme sur un test exécutable**, pas sur une revue.
3. **Rien n'est branché avant d'être testé seul.** Les lots `L1` à `L3` n'ajoutent aucune
   entrée de menu et ne sont atteignables par aucun geste utilisateur ; `L4` les branche
   d'un coup. C'est ce qui permet de livrer les trois premiers sans risque de régression
   visible.

**Contrainte de méthode**, valable pour tout le chantier : les tests d'optimisation sont
lents et **c'est l'utilisateur qui les lance**. Aucun lot ne se ferme sur une exécution de
`./gradlew test` faite par l'assistant.

---

## 4. Les lots

| Lot | Objet | Change le comportement ? | Test qui le ferme |
|---|---|---|---|
| **L0** | L'horizon revient du préremplissage | oui, une ligne | `WizardPrefillTest` |
| **L1** | Le format et son aller-retour | non (purement additif) | 3 classes de test, hors JME |
| **L2** | Le rejeu | oui, opt-in : aucun appelant avant `L3` | 1 test unitaire + 1 test lent |
| **L3** | Magasin disque et logique de session | non (aucun appelant avant `L4`) | 2 classes de test, sur `@TempDir` |
| **L4** | Les deux entrées de menu et la fenêtre | oui, c'est la livraison | `ScenarioBrowserModelTest` + essai manuel |

### L0 — L'horizon revient du préremplissage

**Propriété rendue vraie.** Rouvrir dans le wizard une mission dont l'horizon a été forcé
retrouve la valeur forcée, et une mission laissée en « auto » revient en « auto ».

**Entrées.** La suite verte au commit de départ.

**Sorties.** Une écriture conditionnelle dans
`ui/mission/wizard/WizardPrefill.java`, symétrique de celle de `StepParameters:658` : la clé
`MISSION_HORIZON_DAYS` est écrite **si et seulement si** le spec porte un
`MissionHorizon.FixedDuration`, jamais quand il porte le défaut dérivé de son type.

**Le piège à ne pas rater.** `MissionHorizon.defaultFor(type)` peut rendre une valeur
*égale* à celle qu'un utilisateur aurait tapée. Le prédicat porte donc sur le **type** de
l'horizon, pas sur sa valeur en secondes : comparer des durées ferait revenir en « forcé »
une mission qui était en « auto », et l'absence de la clé cesserait de signifier « auto »
comme `FormField.MISSION_HORIZON_DAYS` le documente.

**Fermeture.** `WizardPrefillTest`, nouvelle classe : un cas horizon forcé (la clé est
présente, à la bonne valeur en jours), un cas horizon auto (la clé est **absente**), et un
cas où le défaut dérivé vaut numériquement la même chose que le forcé (la clé reste absente).

### L1 — Le format et son aller-retour

**Propriété rendue vraie.** Une mission traverse le JSON et en revient identique, **charges
ergol comprises**. Rien dans l'application ne sait encore que le format existe.

**Entrées.** `L0` (sans quoi l'aller-retour perd l'horizon).

**Sorties.** Un paquet neuf, `simulation/mission/scenario/`, et rien d'autre — aucun fichier
existant n'est modifié :

- les records du §3 de la conception : `ScenarioFile`, `ScenarioMission` (interface scellée,
  `EarthOrbit` et `Geo`), `ScenarioSite`, `ScenarioVehicle`, `ScenarioSolution` ;
- `ScenarioMapper`, qui traduit entre le DTO et la **map de valeurs de wizard** — jamais
  entre le DTO et le spec (conception §4.2), et sans dépendre de `ui.mission.wizard` : il
  emploie les mêmes littéraux de clés que `MissionFactory` ;
- `ScenarioCodec`, mince enveloppe Jackson : `String write(ScenarioFile)` /
  `ScenarioFile read(String)`, configurée pour **omettre les nuls** — c'est ce qui rend
  l'absence signifiante lisible dans le fichier.

**Fermeture.** Trois classes, toutes hors JME et sans propagation :

- `ScenarioMapperTest` — aller-retour `map ↔ DTO` sur les deux types, avec **et sans** les
  valeurs absentes : inclinaison non commandée, RAAN, horizon.
- `ScenarioCodecTest` — aller-retour JSON, champs omis compris ; une `formatVersion`
  supérieure à la version connue est refusée avec son numéro dans le message.
- `ScenarioRoundTripTest` — le test qui compte : `MissionEntry` → `WizardPrefill` →
  `ScenarioMapper` → JSON → `ScenarioMapper` → `MissionFactory.specFromWizardValues` →
  `MissionSpec`, comparé au spec d'origine **y compris `configuration().propellantLoads()`**.
  C'est lui qui interdit une dérive silencieuse du dimensionnement.

### L2 — Le rejeu

**Propriété rendue vraie.** Une mission dont on connaît les vecteurs vole sans CMA-ES et
atteint la même orbite que l'optimisation qui les a produits. Aucun appelant : `L3` en
fournira un.

**Entrées.** Rien de `L1` — le rejeu ignore le format. C'est délibéré : le cœur
d'optimisation ne connaît pas les fichiers.

**Sorties.**

| Fichier | Rôle |
|---|---|
| `simulation/mission/runtime/MissionSolutions.java` *(créé)* | record `(Map<String,double[]> vectors, double[] lambdas)`, plus `boolean covers(Mission)` — vrai quand chaque `OptimizableMissionStage` de la composition a sa clé |
| `simulation/mission/planner/ReplayPlanner.java` *(créé)* | `MissionPlanner` qui applique les λ puis délègue à un `MissionOptimizer` alimenté par les vecteurs |
| `simulation/mission/runtime/MissionOptimizer.java` *(modifié)* | au point qui produit le `OptimizationResult` (l. 165-167), une solution fournie remplace le `CMAESTrajectoryOptimizer` |
| `simulation/mission/planner/MissionPlanOptimizer.java` *(modifié)* | sélectionne `ReplayPlanner` quand l'entrée porte des solutions en attente |
| `simulation/mission/context/MissionEntry.java` *(modifié)* | champ `volatile MissionSolutions pendingSolutions`, posé au chargement, **effacé par `publish()`** |

**Le résultat de rejeu n'est pas bricolé.** `problem.propagate(variables)` puis
`problem.computeCost(state)` sont tous deux au contrat de `TrajectoryProblem` ; le résultat
est un `OptimizationResult` complet avec `evaluations = 0`, qui se lit comme « pas
optimisé ». Les blocs de diagnostic de `MissionOptimizer` — saturation de bornes,
décomposition Δv, barrières, état de fin de gravity turn — sont **sautés** : ils re-propagent
pour écrire des journaux qui n'ont de sens que face à une optimisation.

**L'invariant à ne pas perdre.** `publish()` efface déjà tout ce qui dérive de la composition
précédente ; les solutions en attente en font partie. Une bascule de mode ou une édition qui
les conserverait ferait rejouer les vecteurs d'une composition qui n'existe plus — et le
`covers()` du tout-ou-rien ne le rattraperait pas toujours, deux compositions pouvant
partager leurs clés d'étage.

**Fermeture.**

- `MissionSolutionsTest` — le tout-ou-rien, sans propagation : clé manquante, clé en trop,
  recouvrement exact. C'est une fonction pure, elle se teste comme telle.
- `ScenarioReplayTest`, **opt-in sous `-Dorbitlab.slowTests=true`** : optimiser une mission
  LEO, capturer ses vecteurs, rejouer, et vérifier que l'orbite atteinte est identique à la
  tolérance du propagateur. Étend `AbstractTrajectoryOptimizerTest`. **Lancé par
  l'utilisateur**, pas par l'assistant.

### L3 — Magasin disque et logique de session

**Propriété rendue vraie.** Une session complète s'écrit dans un fichier et s'en relit, avec
son refus par mission ; rien de tout cela n'est encore atteignable depuis l'écran.

**Entrées.** `L1` (le format) et `L2` (le rejeu, pour peupler `pendingSolutions`).

**Sorties.**

| Fichier | Rôle |
|---|---|
| `simulation/mission/scenario/ScenarioStore.java` | le disque, et lui seul : `list()`, `read(name)`, `write(name, file)`, `exists(name)`, sur `~/.orbitlab/scenarios/<nom>.json`. Le nom est restreint à `[A-Za-z0-9 _-]` et **refusé** hors de ce jeu, jamais assaini |
| `simulation/mission/scenario/ScenarioSession.java` | la logique, sans disque et sans JME : `ScenarioFile capture(List<MissionEntry>, AbsoluteDate clockDate)` et `ScenarioLoadReport restore(ScenarioFile)` |
| `simulation/mission/scenario/ScenarioLoadReport.java` | ce que `restore` rend : les `MissionEntry` reconstruites, la date d'horloge, et les rejets — un `(nom, motif)` par mission écartée |

**C'est ici, et pas dans `L1`, que `ScenarioSolution` devient `MissionSolutions`.** Les deux
records portent la même paire `(vectors, lambdas)` et ce n'est pas une redondance : l'un est
un format de fichier qu'on ne casse pas, l'autre est un type de domaine que le cœur
d'optimisation possède. `ScenarioSession` est le seul point qui les met en regard, parce
qu'il est le seul lot qui dépende à la fois de `L1` et de `L2`.

**Ce que `ScenarioSession` ne fait pas**, et c'est ce qui la rend testable : elle ne touche ni
`MissionContext`, ni les renderers, ni l'horloge. Elle **rend** des entrées ; c'est
l'`AppState` de `L4` qui procède à l'échange. L'invariant du §6.3 de la conception — le
fichier est entièrement converti avant que la moindre mission courante ne soit détruite —
découle alors de la seule signature : `restore` a fini avant que l'appelant ne détruise quoi
que ce soit.

**Fermeture.**

- `ScenarioStoreTest`, sur `@TempDir` : écriture puis relecture, `exists`, liste triée, et le
  refus d'un nom hors du jeu autorisé.
- `ScenarioSessionTest` : capture d'une session de deux missions ; restitution avec une
  mission dont le `launcherId` est inconnu — le rapport porte une entrée valide et un rejet
  motivé, pas une exception ; restitution d'un fichier de `formatVersion` future — refus en
  bloc.

### L4 — Les deux entrées de menu et la fenêtre

**Propriété rendue vraie.** La fin de phase : une mission survit à la fermeture de
l'application.

**Entrées.** `L3`.

**Sorties.**

| Fichier | Rôle |
|---|---|
| `engine/events/EventBus.java` *(modifié)* | un `UiNavigationEvent.OpenScenarioBrowser(Mode mode)` et son `pollOpenScenarioBrowser()`, sur le motif exact d'`OpenMissionManagement` |
| `states/mission/MissionDisplayPanelAppState.java` *(modifié)* | deux `AppMenuItem.action` après *New mission…*, derrière un séparateur, qui publient l'événement |
| `states/mission/ScenarioBrowserModel.java` *(créé)* | la logique pure : mode, sélection, validité du nom, existence, activation des boutons. Testée hors JME comme `AppMenuModel` |
| `ui/mission/scenario/ScenarioBrowserWidget.java` *(créé)* | la modale à deux modes, sur `ModalBackdrop`, `WindowDragHandler`, `ConfirmDialog`, `UiLayers` |
| `states/mission/ScenarioAppState.java` *(créé)* | draine l'événement, possède la fenêtre, l'inscrit dans `HudSurfaces`, procède à l'échange de session, pose l'horloge par `clock.seek(...)` et soumet les rejeux |
| `OrbitLabApplication.java` *(modifié)* | enregistre le nouvel `AppState` |

**La règle de communication.** L'`AppState` du menu et celui du scénario ne se connaissent
pas : tout passe par l'`EventBus` et l'`ApplicationContext`. La règle « pas de `getState()` »
n'admet pas d'exception ici.

**Dépendance d'actif.** `AppMenuItem` impose une icône par entrée et il n'existe sous
`interface/` ni icône « ouvrir » ni icône « enregistrer ». Deux PNG sont à produire ; le lot
est livrable sans les attendre, sur le repli `wizard/lbl-box` et
`missions/icon-action-manage`.

**Fermeture.**

- `ScenarioBrowserModelTest` : sélection, nom invalide, nom existant, activation des boutons
  dans les deux modes.
- `AppMenuModelTest` étendu aux deux nouvelles entrées.
- Essai manuel, qui est le seul juge de la fin de phase : créer une mission, l'optimiser,
  l'enregistrer, quitter, relancer, ouvrir — la trajectoire est dessinée sans qu'aucun CMA-ES
  n'ait tourné.

---

## 5. Ordonnancement et risques

`L0 → L1 → L2 → L3 → L4`, sans réordonnancement possible : chaque lot consomme le précédent,
sauf `L2` qui est indépendant de `L1` et pourrait être mené en parallèle. Il ne le sera pas —
la règle 1 du §3 vaut aussi entre lots menés de front.

| Risque | Où | Parade |
|---|---|---|
| Le rejeu ne redonne pas la même trajectoire | `L2` | `ScenarioReplayTest` compare les orbites atteintes, pas les vecteurs ; un écart est visible tout de suite |
| Un scénario écrit avant un changement de composition rejoue des vecteurs périmés | `L2`, `L3` | le tout-ou-rien de `MissionSolutions.covers()` ; en cas de doute, la mission arrive en `DRAFT` |
| L'aller-retour perd une subtilité du wizard | `L1` | `ScenarioRoundTripTest` compare les charges ergol, qui sont l'aval de toutes ces subtilités |
| La fenêtre déborde le lot | `L4` | la logique est dans `ScenarioBrowserModel`, testée seule ; le widget n'est que du placement |

**Le risque qui n'est pas dans le tableau, parce qu'il n'est pas technique** : `UI-6`
(fenêtres déplaçables, empilement par focus) désigne `UI-3` comme « la prochaine fenêtre »
qui rendra son coût plus élevé. `L4` ajoute bien une fenêtre de plus à reprendre le jour où
`UI-6` sera fait. C'est assumé et signalé, pas résolu ici.

---

## 6. Ce que UI-3 lègue

- Un format versionné auquel les phases 4 et 5 ajouteront des types de mission sans changer
  de schéma : `ScenarioMission` est scellé, un nouveau type y est un nouveau record.
- Un mode de rejeu qui vaut au-delà de la persistance — c'est le socle d'un éventuel mode
  batch, et le seul moyen de refaire voler une trajectoire sans la re-chercher.
- Le premier fichier que l'application écrit, donc le premier dossier utilisateur :
  `~/.orbitlab/`. Un futur fichier de préférences (`UI-4`) y prendra place **à côté**, jamais
  dedans (conception §10).
