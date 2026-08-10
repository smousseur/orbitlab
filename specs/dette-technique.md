# specs/dette-technique.md — état de la dette technique

Photographie de la qualité du code au **2026-08-10**, commit `b027d1d`. Ce
document est un état des lieux mesuré, pas une roadmap : les items qui méritent
d'être planifiés doivent être promus dans `roadmap/01-roadmap.md`.

**Convention.** `DT-n` dans l'ordre de priorité décroissante à la date de
rédaction, jamais réattribué. Un item sort d'ici corrigé, ou requalifié en
« accepté » avec la raison. Comme dans `bugs.md`, chaque fiche sépare ce qui est
**mesuré** de ce qui est **inféré**.

---

## 1. Périmètre et méthode

| Grandeur | Valeur |
|---|---|
| Classes `src/main` | 266 |
| SLOC `src/main` (hors lignes vides) | 32 920 |
| SLOC `src/test` | 12 738 |
| Ratio test/main | 0,39 |

**Aucun analyseur statique n'est branché sur le build** — `build.gradle` ne
déclare que le plugin `java`, et il n'y a pas de CI. Les chiffres ci-dessous ont
donc été reconstruits à la main :

- longueur de méthodes et de classes par comptage d'accolades ;
- complexité cognitive **approximée** (mots-clés de branchement pondérés par la
  profondeur d'indentation) — l'ordre de grandeur est fiable, la valeur absolue
  ne l'est pas ;
- code mort par référencement croisé sur `src/main` **et** `src/test`, en
  incluant les références de méthode `::` ;
- familles de règles Sonar classiques par recherche ciblée.

La conséquence pratique : ces mesures ne sont pas reproductibles automatiquement
aujourd'hui. C'est le sujet de [`DT-1`](#dt-1--aucune-analyse-statique-dans-le-build).

---

## 2. Synthèse

| ID | Item | Sévérité | Effort | Risque de régression |
|---|---|---|---|---|
| [`DT-1`](#dt-1--aucune-analyse-statique-dans-le-build) | Aucune analyse statique dans le build | Critique (structurel) | Faible | Nul |
| [`DT-2`](#dt-2--squelette-dupliqué-sur-les-six-analyticstage) | Squelette dupliqué sur les six `Analytic*Stage` | Majeur | Moyen | **Élevé** |
| [`DT-3`](#dt-3--complexité-cognitive-de-multistageloadoptimizerminimize) | Complexité de `MultiStageLoadOptimizer.minimize()` | Critique | Moyen | **Élevé** |
| [`DT-4`](#dt-4--singleton-statique-mutable-orbitlabapplicationapp) | Singleton statique mutable `OrbitLabApplication.app` | Critique | Faible | Faible |
| [`DT-5`](#dt-5--classes-et-méthodes-trop-longues) | Classes et méthodes trop longues | Majeur | Élevé | Variable |
| [`DT-6`](#dt-6--gestion-dexception-trop-large) | Gestion d'exception trop large | Majeur | Faible | Faible |
| [`DT-7`](#dt-7--trou-de-couverture-sur-simulationmissionplanner) | Trou de couverture sur `simulation/mission/planner` | Majeur | Moyen | Nul |
| [`DT-8`](#dt-8--violations-de-la-règle-de-langue) | Violations de la règle de langue (français dans le code) | Mineur | Trivial | Nul |
| [`DT-9`](#dt-9--code-mort) | Code mort | Mineur | Trivial | Nul |
| [`DT-10`](#dt-10--commentaires-redondants) | Commentaires redondants | Mineur | Faible | Nul |
| [`DT-11`](#dt-11--littéraux-dupliqués-et-todo-non-tracés) | Littéraux dupliqués et TODO non tracés | Mineur | Faible | Nul |

Aucun item de sévérité **Bloquant** n'a été trouvé.

---

## 3. Fiches

### DT-1 — Aucune analyse statique dans le build

**Mesuré.** `build.gradle` déclare `plugins { id 'java' }` et rien d'autre. Pas
de Spotless, pas d'ErrorProne, pas de PMD, pas de SonarQube. Pas de
`.github/workflows` non plus (`CLAUDE.md` le confirme : les tests sont lancés à
la main).

**Conséquence mesurée.** 208 lignes dépassent 100 caractères alors que le style
google-java-format est suivi partout ailleurs (indentation 2, enveloppement des
paramètres) : le style est respecté par discipline, pas par outil, donc il
dérive. La moitié des items **Mineur** de ce document seraient interdits à la
compilation par un outil coûtant vingt lignes de configuration.

**Inféré.** C'est l'item le plus rentable du document, parce qu'il est le seul
qui empêche les autres de revenir. Tous les autres corrigent le passé ; celui-ci
corrige le futur.

Détail du correctif proposé en [§6](#6-conseils--ne-pas-augmenter-la-dette).

---

### DT-2 — Squelette dupliqué sur les six `Analytic*Stage`

**Mesuré.** 2 002 lignes cumulées sur six classes de
`simulation/mission/stage/` : `AnalyticGtoInjectionStage` (469),
`AnalyticHohmannTransferStage` (423), `AnalyticParkingInsertionStage` (312),
`AnalyticApogeeCircularizationStage` (289), `AnalyticTrimBurnStage` (272),
`AnalyticPlaneTrimAtNodeStage` (237).

Les six exposent le même squelette, dans le même ordre :

```
configure(propagator, mission)
  → computeXxxPlan(state, vehicle)      // le seul vrai point de variation
  → addBurn(s)(propagator, state, plan, vehicle)
  → DateDetector(endDate) + handler { transitionToNextStage(s); STOP }

propagateStandalone(currentState, mission)
  → computeXxxPlan(...)                 // recalculé à l'identique
  → createOptimizationPropagator(burnLimitedMaxStep(...))
  → setInitialState + ReentryGuard.armQuiet
  → addBurn(s)(...)
  → propagate(fin du plan)
```

Les corps de `configure()` d'`AnalyticApogeeCircularizationStage`,
`AnalyticParkingInsertionStage` et `AnalyticTrimBurnStage` sont **identiques à un
nom de record près**. Les commentaires de `propagateStandalone()` sont
eux-mêmes recopiés (« 8×8 gravity, matching the ephemeris generator (bilan 11
§3.9)… », deux fois mot pour mot).

**Inféré.** Le correctif est un *template method* : une base
`AbstractAnalyticBurnStage<P>` portant `configure`, `propagateStandalone` et
l'installation du `DateDetector`, avec deux points d'extension — `computePlan()`
et `addBurns()` — plus la tolérance d'un `plan == null` signifiant « étape
sautée », cas que `AnalyticTrimBurnStage` et `AnalyticPlaneTrimAtNodeStage`
gèrent déjà chacun de leur côté. Gain estimé : 250 à 350 lignes.

**Le vrai gain n'est pas le volume.** C'est que l'invariant de pas d'intégration
documenté dans `CLAUDE.md` (« ne jamais passer un grand max step brut à un
propagateur qui hébergera une poussée ») est aujourd'hui réimplémenté six fois.
Il n'y a aujourd'hui aucune garantie mécanique que les six copies restent
d'accord.

**Risque.** Élevé : ces classes sont le cœur des trajectoires GEO. À ne pas
entreprendre sans baseline de non-régression préalable, conformément à la règle
« un changement de comportement à la fois ».

---

### DT-3 — Complexité cognitive de `MultiStageLoadOptimizer.minimize()`

**Mesuré.** `simulation/mission/runtime/MultiStageLoadOptimizer.java:204` —
225 lignes, complexité cognitive approximée à ~99 (le seuil Sonar par défaut est
15). C'est le pire point du dépôt, avec un facteur ~2 sur le suivant.

La méthode enchaîne : validation des arguments, sonde du point heuristique,
journalisation de démarrage, boucle de passes, balayage top-down des
coordonnées, critère d'arrêt sur tolérance, et construction du résultat.

**Les autres dépassements notables**, par complexité approximée décroissante :

| Méthode | Complexité ≈ | Lignes |
|---|---|---|
| `MultiStageLoadOptimizer.minimize()` | 99 | 225 |
| `MissionOrchestratorAppState.pollMissionActions()` | 35 | 33 |
| `CMAESTrajectoryOptimizer:297` | 38 | 197 |
| `MissionOptimizer.optimize()` | 26 | 199 |
| `StepLauncher` (constructeur) | 30 | 108 |

`pollMissionActions()` mérite d'être noté à part : 35 pour 33 lignes, c'est la
densité de branchement la plus forte du dépôt, dans une méthode appelée à chaque
frame.

**Inféré.** Découpage naturel de `minimize()` en `probeHeuristic()` /
`sweepPass()` / `refineCoordinate()`, sans changement de comportement. Le
découpage est purement structurel, mais la méthode pilote le dimensionnement des
ergols : baseline obligatoire.

---

### DT-4 — Singleton statique mutable `OrbitLabApplication.app`

**Mesuré.** `OrbitLabApplication.java:49` déclare `public static
OrbitLabApplication app;`, affecté ligne 77. Un seul consommateur dans tout le
dépôt : `engine/scene/body/lod/Model3dView.java:57`, qui s'en sert uniquement
pour `enqueue(...)`.

**Pourquoi ça compte ici.** `CLAUDE.md` pose une règle explicite — toute
communication inter-états passe par `ApplicationContext`, jamais par
`getState()`. Ce champ est la même faille par une autre porte : un accès global
mutable qui court-circuite le conteneur. Il est d'autant moins justifiable qu'il
sert un seul besoin, très étroit.

**Correctif.** Exposer un point d'`enqueue` (ou un `Executor` du thread JME) sur
`ApplicationContext`, injecter dans `Model3dView`, supprimer le champ. Un seul
appelant à reprendre, risque faible.

---

### DT-5 — Classes et méthodes trop longues

**Mesuré.** 25 méthodes dépassent 70 lignes. Les classes les plus lourdes, par
SLOC hors commentaires :

| Classe | SLOC | Méthodes | Champs | Imports | Diagnostic |
|---|---|---|---|---|---|
| `optimizer/problems/TransferProblem` | 427 | 34 | **44** | 18 | Mélange configuration, bornes et fonction de coût pondérée. Extraire les poids dans un record, isoler le calcul de coût. |
| `optimizer/CMAESTrajectoryOptimizer` | 398 | 35 | 16 | 12 | Deux méthodes de 197 et 100 lignes. Les phases exploration/raffinement sont déjà nommées en bannières de commentaires — signe qu'elles veulent être des méthodes. |
| `ui/…/step/StepParameters` | 393 | **44** | 34 | 30 | Classe la plus couplée de l'UI. Le patron `params/DynamicParameters` existe déjà : y pousser davantage. |
| `runtime/MultiStageLoadOptimizer` | 336 | 35 | 13 | 6 | Voir [`DT-3`](#dt-3--complexité-cognitive-de-multistageloadoptimizerminimize). |
| `states/camera/OrbitCameraAppState` | 324 | 39 | 25 | 20 | Bindings d'entrée + machine d'état caméra + application de pose dans une seule classe. Sortir le mapping input. |

**Cas à part : `OrbitLabApplication.simpleInitApp()`**, 129 lignes. Ce n'est pas
une classe trop longue mais une séquence de câblage dont **l'ordre est
critique** — la contrainte est aujourd'hui portée par un commentaire (ligne 105 :
« Before FloatingOriginAppState, and that is a requirement, not a preference »).
Un découpage en `initViewports()` / `initStates()` / `initGui()` rendrait cet
ordre lisible dans le code au lieu de le confier à un commentaire que rien ne
protège.

**Couplage efférent** — plafond à 35 imports (`OrbitLabApplication`, légitime
pour un *composition root*), puis `MissionOptimizer` et `OrbitRuntimeAppState` à
34, ce qui l'est moins. Globalement sain : la discipline `ApplicationContext`
tient.

---

### DT-6 — Gestion d'exception trop large

**Mesuré.** 16 `catch (Exception e)` et 1 `catch (Throwable t)`. Trois sites
méritent une action, les autres sont des gardes de tâche asynchrone
défendables :

| Site | Problème |
|---|---|
| `source/BodyFile.java:85` | `catch (Exception e)` suivi de `if (e instanceof IOException ioe) throw ioe; throw (RuntimeException) e;`. **Correct aujourd'hui** — `EphemerisV1Parser` ne déclare que `IOException` — mais le jour où le parser déclare une autre exception vérifiée, une erreur de format se transforme en `ClassCastException`. Le pattern idiomatique est un drapeau `boolean ok` + `finally`. |
| `source/DatasetEphemerisSource.java:176` | `catch (Exception ignored) { // ignore }` dans `close()`. Un `logger.debug` suffit à rendre l'échec observable. |
| `ephemeris/EphemerisWorker.java:103` | `catch (Throwable t)` — **justifié**, c'est la garde de la boucle du worker, et elle journalise. À conserver, noté pour éviter qu'un futur analyseur ne le signale sans contexte. |

**Également mesuré, sévérité faible** : 12 `throws Exception` génériques,
intégralement concentrés dans `tools/ephemerisgen` et `tools/orbitgen` — code
outil hors application, hors du chemin de rendu et d'optimisation. Deux
`System.err.println` dans les `main` de ces mêmes outils, qui sont des messages
d'usage CLI et non de la journalisation détournée.

---

### DT-7 — Trou de couverture sur `simulation/mission/planner`

**Mesuré.** Ratio test/main global de 0,39, correct pour ce type de projet. Mais
la répartition est très inégale :

- **`simulation/mission/planner` — 6 classes, aucun test.** C'est de la logique
  métier pure et récente.
- Toute l'UI Lemur est non testée (`ui/mission/panel` 9 classes,
  `ui/timeline/components` 5, `ui/mission/display` 5).

**Inféré.** Le second point est un arbitrage défendable — tester du rendu Lemur
coûte cher pour peu de valeur, et les règles métier de l'UI qui *méritaient*
d'être testées l'ont été (`MissionDisplayPanelRules`, `MissionPhaseShading`).
Le premier ne l'est pas : `planner` est exactement le genre de code que la suite
de tests existante sait couvrir, puisqu'elle couvre déjà `runtime`, `stage` et
`optimizer`.

---

### DT-8 — Violations de la règle de langue

`CLAUDE.md` : tout commentaire, toute Javadoc et toute chaîne de code sont en
anglais, sans exception ; seul `specs/` est en français.

| Site | Contenu |
|---|---|
| `engine/scene/planet/PlanetPresenter.java:56` | `// Convertir en JME units/axes selon le contexte SOLAR` |
| `ui/mission/wizard/WizardStepper.java:137` | `// Spacer du haut ≈ (CIRCLE_SIZE - CONNECTOR_HEIGHT) / 2 pour centrer verticalement` |
| `runtime/MultiStageLoadOptimizer.java:234` | message de log : `"…infeasible — mission under-dotée, nothing to shrink; aborting"` |
| `runtime/PropellantLoadOptimizer.java:169` | même message, dupliqué |

Les deux derniers sont aussi un cas de [`DT-11`](#dt-11--littéraux-dupliqués-et-todo-non-tracés).

---

### DT-9 — Code mort

**Mesuré**, par référencement croisé sur `src/main` + `src/test`, références de
méthode `::` incluses :

- `states/mission/MissionDisplayPanelAppState.java:97` — `publishOpenWizard()`,
  méthode privée jamais appelée ni référencée.
- `engine/scene/graph/SceneGraph.java:52` — champ `nearOrbitLayer`, initialisé
  et jamais lu.

Aucune constante privée inutilisée. Huit autres candidats détectés
automatiquement se sont révélés être des références de méthode `::` — écartés
après vérification.

---

### DT-10 — Commentaires redondants

**Mesuré, et c'est le point rassurant du document.** Sur 1 051 lignes de
commentaire situées dans des corps de méthode, **42 seulement** paraphrasent la
ligne de code qui suit. La densité globale est élevée — jusqu'à 66 % sur
`Vehicle.java`, 64 % sur `MissionStage.java` — mais elle porte du *rationale*,
exactement ce que `CLAUDE.md` demande. **Il ne faut pas y toucher.**

Les 42 se répartissent en deux familles :

1. **Étiquettes de section dans de longs constructeurs UI** (~30). Exemples :
   `// Playhead` devant `playhead = new Panel(...)` (`ScrubberTrack:89`),
   `// Color swatch` devant `Container swatch = ...` (`DisplayRow:46`),
   `// Divider 1/2/3` (`TimelineWidget:73,81,89`), `// Content pane` /
   `// Footer strip` (`MissionWizardWidget:130,136`).
   **Le correctif n'est pas de les effacer** : dans `ScrubberTrack` (99 lignes)
   et `ClockDisplay` (116 lignes) ils servent de repères de navigation. C'est
   d'extraire les blocs en méthodes nommées, le nom de méthode remplaçant le
   commentaire.
2. **Paraphrases pures**, supprimables telles quelles :
   `// Apply initial pose immediately` devant `applyCameraPose();`
   (`OrbitCameraAppState:205`), `// Add the final state of this stage as a
   sample point` devant `points.add(pointOf(...))`
   (`MissionEphemerisGenerator:133`), `// Reset speed` devant
   `addMapping(ACTION_SPEED_RESET, ...)` (`SimulationClockAppState:123`).

---

### DT-11 — Littéraux dupliqués et TODO non tracés

**Littéraux dupliqués** (règle S1192) :

| Littéral | Occurrences | Remarque |
|---|---|---|
| `"background"` | 8 | Style Lemur |
| `"stepSeconds must be finite and > 0"` | 7 | Message de validation |
| `"Coasting"` | 7 | **Nom d'étape** |
| `"btn-primary"` | 6 | Style Lemur |
| `"S2 separation"` | 5 | **Nom d'étape** |

Les noms d'étape sont les seuls qui portent un vrai risque : ils servent à
l'appariement d'étapes, et une faute de frappe y échouerait silencieusement.
Ils devraient être des constantes.

**TODO non tracés** (3) — aucun ne porte de référence de roadmap :

- `engine/scene/OrbitLineFactory.java:125` — « call sampleIcrfSafe instead of sampleIcrf »
- `simulation/orbit/OrbitPathCache.java:103` — « only propagate from the last ephemeris point » (gain de performance potentiel)
- `states/camera/FloatingOriginAppState.java:65` — « Get camera from context » (dette `ApplicationContext`, même famille que [`DT-4`](#dt-4--singleton-statique-mutable-orbitlabapplicationapp))

---

## 4. Ce qui est sain

À consigner autant que le reste, pour ne pas dégrader ce qui tient :

- **La discipline `ApplicationContext` tient.** Aucun `getState(Class)` de
  communication inter-états. Le couplage efférent plafonne à 34 imports hors
  *composition root*.
- **La règle `Optional` de `CLAUDE.md` est respectée sans exception.** Les deux
  seules occurrences du dépôt sont des types de retour.
- **La densité de commentaires est un actif, pas une dette** (cf. `DT-10`).
- **Aucun item bloquant** : pas de fuite de ressource, pas de `catch` vide, pas
  de comparaison de `String` par `==`, pas de ternaire imbriqué, pas de champ
  public mutable hors le cas unique de `DT-4`.
- **Les décisions non évidentes sont documentées avec leur mesure** (références
  « bilan 08 §3.1 », « bilan 11 §3.9»…). C'est ce qui rend ce dépôt reprenable.

**Faux positifs écartés après vérification**, à ne pas re-signaler : le seul
`catch (InterruptedException)` restaure bien le flag d'interruption ; les
`ExecutorService` de `MissionRenderer` et `PlanetPoseAppState` sont *empruntés*
à `AssetFactory` et non possédés, donc leur absence de `shutdown` est correcte ;
les 3 comparaisons `double ==` sont des tests « exactement zéro » sur des
sentinelles.

---

## 5. Ordre d'attaque proposé

Par ratio impact/risque décroissant :

1. **`DT-1`** — outillage. Aucun risque, et fige immédiatement `DT-8`, `DT-9`,
   une partie de `DT-11` et les 208 dépassements de largeur.
2. **`DT-9`, `DT-8`, `DT-4`** — nettoyages triviaux, un seul appelant à reprendre
   pour `DT-4`.
3. **`DT-6`** — trois sites, faible risque.
4. **`DT-7`** — tests sur `planner`, aucun risque de régression par
   construction, et prérequis utile aux deux suivants.
5. **`DT-2`** puis **`DT-3`** — les deux gros chantiers, à faire **après** avoir
   établi une baseline de non-régression, un changement de comportement à la
   fois.

`DT-5`, `DT-10` et le reste de `DT-11` se traitent opportunément, au fil des
passages dans les fichiers concernés (cf. règle de la trace en
[§6](#6-conseils--ne-pas-augmenter-la-dette)).

---

## 6. Conseils : ne pas augmenter la dette

Cette section est la plus importante du document. Les sections précédentes
décrivent un passé rattrapable ; celle-ci décide si le même état sera à
réécrire dans six mois.

### 6.1 Rendre les règles mécaniques plutôt qu'intentionnelles

Le constat central de `DT-1` : **ce dépôt suit déjà de bonnes conventions, mais
aucune n'est vérifiée par une machine.** Le style google-java-format est
respecté partout — et dérive quand même sur 208 lignes. Toute règle qui repose
sur la seule vigilance humaine finit par produire exactement ce type de résidu.

Trois paliers, du moins cher au plus complet :

**Palier 1 — le formatage (quinze minutes, aucun risque).** Ajouter Spotless
avec `googleJavaFormat()` à `build.gradle`, lancer `spotlessApply` une fois, et
commiter le résultat en un commit isolé « formatage seul » pour qu'il ne pollue
aucune revue. Ensuite `spotlessCheck` échoue sur toute dérive.

**Palier 2 — les bugs réels (une heure).** ErrorProne, en mode avertissement
d'abord pour mesurer le bruit, puis erreur sur les catégories qui font
consensus. Il attrape des choses qu'aucune relecture ne voit de façon fiable.

**Palier 3 — la dette structurelle.** SonarQube ou PMD, avec des seuils fixés à
ce que le code fait *aujourd'hui* plutôt qu'à l'idéal — un seuil qu'on ne peut
pas atteindre est un seuil qu'on désactive. Le but n'est pas d'atteindre zéro,
c'est d'interdire la progression.

Vérifier les versions au moment de l'ajout ; ce document ne les fige pas
volontairement.

### 6.2 Le seuil qui compte : le ratio, pas la valeur absolue

Un plafond dur du type « pas plus de 300 lignes par classe » se contourne en
coupant arbitrairement, ce qui produit deux classes incohérentes au lieu d'une
longue. La règle utile est différente :

> Quand une classe dépasse **300 SLOC** ou **25 méthodes**, ce n'est pas une
> erreur — c'est une **question** : cette classe a-t-elle encore une seule
> responsabilité ? Si la réponse est oui, on documente pourquoi. Si elle est
> non, on découpe **avant** d'ajouter la fonctionnalité qui a déclenché la
> question.

`TransferProblem` avec ses 44 champs et `StepParameters` avec ses 44 méthodes
sont passées par ce seuil sans que personne pose la question.

### 6.3 La règle des trois copies

`DT-2` n'est pas né d'une décision : il est né de six décisions raisonnables
prises isolément. Copier `AnalyticParkingInsertionStage` pour écrire
`AnalyticTrimBurnStage` était le bon choix la première fois. La sixième, non.

> À la **troisième** copie d'un squelette, on extrait la base avant d'écrire la
> troisième. Pas après.

Le signal concret à surveiller : quand on se surprend à **copier aussi le
commentaire** — comme le bloc « 8×8 gravity, matching the ephemeris generator
(bilan 11 §3.9) » recopié mot pour mot — c'est que l'abstraction manquante est
déjà identifiée, elle n'est simplement pas encore écrite.

### 6.4 Le commentaire qui dit *quoi* est un nom de méthode qui manque

Le dépôt écrit d'excellents commentaires de *rationale* — le **pourquoi**, ce
qu'aucun nom ne peut porter. C'est un actif, il faut le préserver tel quel. La
distinction opérationnelle :

| Le commentaire dit… | Alors… |
|---|---|
| **pourquoi** (contrainte physique, décision mesurée, piège Orekit) | on l'écrit, on le développe, on cite la mesure. C'est ce que fait déjà ce dépôt. |
| **quoi** (« Apply initial pose », « Color swatch », « Reset speed ») | c'est un nom de méthode ou de variable qui manque. On extrait au lieu de commenter. |

Corollaire pour les constructeurs UI : une bannière de section dans un
constructeur de 100 lignes signale une méthode privée `buildXxx()` en attente.

### 6.5 Un accès global n'entre jamais « juste pour ce cas »

`DT-4` est un champ `public static` justifié par un seul besoin étroit —
`enqueue` depuis `Model3dView`. C'est toujours ainsi que ça commence, et c'est
pourquoi l'exception ne doit pas être accordée : le coût d'ajouter le point
d'entrée sur `ApplicationContext` est de quelques lignes, le coût de retirer un
accès global qui a essaimé se compte en jours.

> Aucun nouveau `static` mutable, aucun nouveau `getState(Class)`. Si
> `ApplicationContext` n'expose pas ce dont on a besoin, on l'ajoute à
> `ApplicationContext`.

### 6.6 Un TODO sans identifiant est un TODO qui ne sera pas fait

Les trois TODO du dépôt sont anonymes et non datés. Deux décrivent un vrai
travail (`OrbitPathCache:103` est un gain de performance identifié).

> Un `TODO` porte une référence de roadmap ou de bug (`// TODO(RND-4): …`), ou
> il n'existe pas. S'il ne vaut pas un item, il ne vaut pas une ligne.

### 6.7 Tester ce qui décide, pas ce qui affiche

L'arbitrage implicite du dépôt est bon et mérite d'être explicite :

> La logique métier pure — `simulation/**`, et les règles extraites de l'UI
> comme `MissionDisplayPanelRules` — arrive **avec** ses tests. Le rendu Lemur
> et JME n'en a pas ; en contrepartie, toute règle de décision qui s'y glisse
> est extraite dans une classe testable.

`simulation/mission/planner` (`DT-7`) est le contre-exemple : six classes de
décision pure livrées sans test. C'est le seul endroit où la règle a été
enfreinte, ce qui prouve qu'elle est tenable.

### 6.8 La règle de la trace

Les items `DT-5`, `DT-10` et une partie de `DT-11` ne justifient pas de chantier
dédié. Ils se traitent ainsi :

> Quand on modifie un fichier pour une autre raison, on corrige au passage la
> dette **mineure** qu'il contient — paraphrase, littéral à extraire, ligne trop
> longue — et **rien d'autre**. Une correction structurelle, elle, mérite son
> propre commit.

C'est ce qui empêche un document de dette de rester un inventaire.

### 6.9 Baseline avant tout refactoring de trajectoire

Règle déjà appliquée dans ce dépôt, consignée ici parce qu'elle conditionne
`DT-2` et `DT-3` : sur le chemin optimisation/propagation, **on mesure avant, on
change une chose, on remesure**. Un refactoring « purement structurel » sur ce
chemin ne l'est jamais tout à fait — un ordre d'opérations flottantes qui change
suffit à déplacer un optimum.

---

## 7. Entretien de ce document

Ce document est daté et le restera : il photographie un commit. Le relancer
demande de rejouer les mesures de [§1](#1-périmètre-et-méthode), ce qui n'est
pas automatisable en l'état — encore une raison de traiter `DT-1` en premier.
Une fois l'outillage en place, la majorité de ce document devient un tableau de
bord généré, et il ne restera ici que ce qu'aucun outil ne sait dire : `DT-2`,
`DT-7` et la [§6](#6-conseils--ne-pas-augmenter-la-dette).
