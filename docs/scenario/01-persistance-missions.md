# UI-3 — Persistance des missions et format de scénario

> Conception L1. Ce document enregistre les décisions prises en conversation ; il
> n'en introduit aucune. Les faits chiffrés du §1 ont été mesurés dans le code au
> 2026-08-21, juste après la livraison de `PHY-1`.

---

## 1. Ce que le code dit déjà

La fiche `UI-3` de [`docs/roadmap/01-roadmap.md`](../roadmap/01-roadmap.md) §6 annonce que
« `MissionSpec` est immuable et sérialise déjà les paramètres du wizard — le plus dur est
fait ». C'est vrai dans l'esprit et faux dans le détail. Six mesures, dont quatre corrigent
la fiche.

### 1.1 Le portail de sérialisation existe, et ce n'est pas `MissionSpec`

`WizardPrefill.fromEntry(entry)` produit une `Map<String, Object>` **plate** de 15 clés,
toutes `String` ou `Double`, et `MissionFactory.specFromWizardValues(values, type)` en est
l'inverse exact. Cet aller-retour n'est pas théorique : il est exercé à chaque édition de
mission depuis le wizard.

Sérialiser `MissionSpec` directement obligerait au contraire à écrire à la main
`LaunchConfiguration`, `LauncherModel`, `Spacecraft`, `PropulsionSystem` et les charges
ergol par étage — c'est-à-dire un état **dérivé** (§2.3).

### 1.2 La date de lancement n'est pas dans le spec

Elle vit sur `MissionEntry.scheduledDate`. Trois autres données que l'utilisateur possède
sont dans le même cas : `optimizationType`, `color`, `visible`. Un format qui ne persisterait
que `MissionSpec` perdrait la date de lancement, donc la mission.

### 1.3 Le seed CMA-ES n'est pas une variable

`MissionPlanOptimizer.SEED` et `MissionOptimizer.DEFAULT_SEED` valent `42L`, en dur, et rien
dans l'UI ne les expose. Il n'y a donc **rien à persister**, et surtout : le déterminisme est
déjà acquis. Le vecteur solution que le §2 persiste n'achète pas de la reproductibilité —
il achète du **temps**, une propagation au lieu de N.

### 1.4 Aucun chemin de rejeu n'existe

`MissionOrchestratorAppState.submitForComputation()` appelle toujours
`MissionPlanOptimizer.compute()`, qui ré-optimise intégralement. La fiche présente la
persistance du vecteur comme évitant « de rejouer un CMA-ES de plusieurs minutes au
chargement » : ce n'est pas de la sérialisation, c'est un mode d'exécution à écrire (§5).

En revanche il est **à portée**, et c'est la mesure qui a débloqué le chantier : les trois
seuls étages qui consomment un `OptimizationResult` — `GravityTurnFirstBurnStage:147`,
`TransfertManeuverStage:97`, `TransfertTwoManeuverStage:87` — ne lisent que
`bestVariables()`. Les deux composants Orekit non sérialisables du résultat, `bestState` et
`stageEntryState`, ne sont relus par personne dans le chemin de rejeu.

### 1.5 L'atmosphère est dans le spec mais pas dans le wizard

`MissionSpec.atmosphere()` existe depuis `PHY-1`, avec `AtmosphereModel.NONE` par défaut.
Mais `MissionFactory` ne lit aucune clé `ATMOSPHERE` et il n'existe pas de `FormField`
correspondant : côté UI, **toute** mission vaut `NONE` aujourd'hui. La note de la roadmap
tient donc telle quelle — le champ doit exister au format dès la v1 — simplement il n'aura
pas de champ de formulaire en face avant `PHY-2`.

### 1.6 Un trou existant, et qui est sur le chemin

`MISSION_HORIZON_DAYS` est publié par `StepParameters:658`, lu par `MissionFactory:271`, et
relu au préremplissage par `StepParameters:690` — mais **`WizardPrefill` ne l'écrit jamais**.
Rouvrir dans le wizard une mission à horizon forcé la ramène silencieusement en « auto ».
Le §4 fait passer l'enregistrement par `WizardPrefill` : le trou cesse d'être un défaut
d'édition pour devenir une pièce porteuse du format, et il est réparé dans ce lot.

### 1.7 Outillage disponible

Jackson 3 (`tools.jackson.core:jackson-databind:3.2.1`) est déjà au build, utilisé
uniquement en lecture d'arbre par `SpaceTrackService`. L'application n'écrit **aucun
fichier** : sa seule configuration est `application.properties`, ressource classpath en
lecture seule. Le menu applicatif a un hôte prêt (`MissionDisplayPanelAppState.MENU_ITEMS`,
quatre entrées, `AppMenuModel` couvert par test). Il n'existe **aucun sélecteur de fichier**,
et Lemur n'en fournit pas ; en revanche le motif « fenêtre-liste » est fourni par
`ui/mission/panel/` (1 711 lignes) et `ui/form/` (`ModalBackdrop`, `ConfirmDialog`,
`WindowDragHandler`).

---

## 2. Périmètre : ce que le fichier porte

**Un fichier de scénario décrit une session**, c'est-à-dire la liste des missions ouvertes,
et non une mission isolée. C'est ce que demande la fin de phase (« une mission survit à la
fermeture de l'application ») et c'est la seule forme où le fichier décrit ce qu'on voit.

| Donnée | Où elle vit | Persistée | Pourquoi |
|---|---|:-:|---|
| Nom, type, site, lanceur, payload et sa masse, cibles, horizon | `MissionSpec` | **oui** | l'intention de l'utilisateur ; quelques centaines d'octets, versionnable, diffable |
| Date de lancement | `MissionEntry.scheduledDate` | **oui** | une mission sans sa date ne se rejoue pas (§1.2) |
| Mode d'optimisation | `MissionEntry.optimizationType` | **oui** | il change la composition des étages, donc la trajectoire |
| Modèle d'atmosphère | `MissionSpec.atmosphere()` | **oui**, même à `NONE` | sans lui, un scénario d'avant `PHY-2` se rejoue après avec une autre physique et personne ne le voit passer |
| Couleur, visibilité | `MissionEntry` | **oui** | sans elles, un scénario rouvert ne ressemble pas à celui qu'on a enregistré |
| Vecteurs solution par clé d'étage, et facteurs λ | résultat d'optimisation | **oui** | ce qui permet le rejeu (§5) ; seul composant du résultat que les étages relisent (§1.4) |
| Date de l'horloge de simulation | `SimulationClock` | **oui** | sans elle l'écran est vide à l'ouverture (§2.2) |
| Charges ergol par étage, payload instancié | `LaunchConfiguration` | **non** | **dérivés** : `PropellantBudget` les recalcule. Les figer rejouerait un vieux dimensionnement après un changement de budget |
| `MissionEphemeris` | `MissionEntry` | **non** | dérivé, 14 à 420 Mo par mission (`MIS-8`), périmé dès que le propagateur change |
| Orbite atteinte, rapport de performance, dernière erreur, avancement | `MissionEntry` | **non** | dérivés du calcul ; le rejeu les reconstruit |
| `MissionId` | `MissionEntry` | **non** | identité de session ; deux ouvertures du même fichier ne doivent pas se confondre |
| `MISSION_PROFILE` | valeur de wizard | **non** | déjà dérivé par `MissionProfile.of(spec)` ; le persister créerait une seconde vérité |

### 2.1 La règle qui gouverne le tableau

Est persisté ce que l'utilisateur a **décidé**, plus ce qu'une **optimisation a coûté cher à
trouver**. Tout le reste est recalculé. C'est ce qui garde `UI-3` indépendant de toute
question de mémoire, et ce qui empêche un scénario de figer un état que le code sait mieux
produire aujourd'hui qu'au jour de l'enregistrement.

### 2.2 Pourquoi l'horloge est dedans

`MissionOrchestratorAppState:98` cache toute mission dont l'éphéméride commence après
l'instant courant. Un scénario dont le lancement est dans six mois, rouvert avec l'horloge
restée sur « maintenant », donnerait donc une liste de missions `READY` et un écran noir.
Restaurer la date d'horloge est la seule façon que « le fichier décrit ce qu'on voit » soit
vrai.

### 2.3 Pourquoi les λ sont dans la solution et non dans le véhicule

En `PRECISE`, `MinimizedLoadPlanner` résout des facteurs d'échelle par étage
(`PropellantSizing.lambdas`) : la mission qui a volé n'est pas celle composée aux charges
budgétées. Les λ ne décrivent pas le véhicule — que le tableau ci-dessus exclut — ils sont le
**résultat d'une optimisation**, au même titre que les vecteurs de trajectoire, et c'est à ce
titre qu'ils sont persistés. Rejouer devient : recomposer aux charges budgétées, appliquer
`spec.withLauncherLoads(charges × λ)` qui existe déjà, injecter les vecteurs, propager une
fois. Cinq nombres pour éviter le balayage complet, dans le mode où recalculer coûte le plus
cher.

---

## 3. Le format v1

Un `ScenarioFile` sérialisé par Jackson, dont le `ScenarioMission` est **scellé en miroir
exact de `MissionSpec`** (`EarthOrbit` / `Geo`), discriminé par le `type` déjà présent. Le
miroir n'est pas une coquetterie : c'est ce qui rend le mapper du §4 évident à relire.

```json
{
  "formatVersion": 1,
  "savedAt": "2026-08-21T14:32:10Z",
  "clockDate": "2026-09-01T05:30:00Z",
  "missions": [
    {
      "type": "LEO",
      "name": "LEO 400",
      "launchDate": "2026-09-01T06:00:00Z",
      "site": {
        "name": "Kourou - French Guiana",
        "latitudeDeg": 5.236, "longitudeDeg": -52.768, "altitudeM": 15.0
      },
      "vehicle": {
        "launcherId": "FALCON_HEAVY",
        "payloadId": "EARTH_OBS_SAT",
        "payloadDryMassKg": 1200.0
      },
      "perigeeKm": 400.0,
      "apogeeKm": 400.0,
      "inclinationDeg": 51.6,
      "horizonDays": null,
      "atmosphere": "NONE",
      "optimizationMode": "BALANCED",
      "color": "#4FC3F7",
      "visible": true,
      "solution": {
        "vectors": { "Gravity turn (S1)": [0.31, 12.4, 148.0] },
        "lambdas": null
      }
    }
  ]
}
```

Records, dans `simulation/mission/scenario/` :

| Record | Composants |
|---|---|
| `ScenarioFile` | `formatVersion`, `savedAt`, `clockDate`, `missions` |
| `ScenarioMission` (scellé) | commun : `type`, `name`, `launchDate`, `site`, `vehicle`, `horizonDays`, `atmosphere`, `optimizationMode`, `color`, `visible`, `solution` |
| `ScenarioMission.EarthOrbit` | `perigeeKm`, `apogeeKm`, `inclinationDeg`, `raanDeg` |
| `ScenarioMission.Geo` | `parkingKm` |
| `ScenarioSite` | `name`, `latitudeDeg`, `longitudeDeg`, `altitudeM` |
| `ScenarioVehicle` | `launcherId`, `payloadId`, `payloadDryMassKg` |
| `ScenarioSolution` | `vectors` (clé d'étage → `double[]`), `lambdas` |

Les composants absents sont des **champs nullables**, jamais des `Optional` : la règle du
projet est qu'`Optional` est un type de retour et rien d'autre.

### 3.1 Trois règles de format

1. **L'absence est signifiante et le reste.** `inclinationDeg`, `raanDeg` et `horizonDays`
   sont **omis** quand ils n'ont pas été commandés, jamais écrits à leur valeur dérivée.
   C'est le joint de non-régression de `WizardPrefill.putInclinationIfCommanded` : publier
   l'inclinaison dérivée déplacerait l'azimut de quelques millièmes de degré, donc
   l'assistance de rotation signée, donc toutes les charges ergol — une dérive de trajectoire
   qu'aucune assertion sur l'inclinaison ne verrait passer, puisque le plan, lui, resterait
   juste.
2. **Les unités du fichier sont celles du wizard** — kilomètres, degrés, jours — et non
   celles du spec (mètres, radians). Le fichier est fait pour être lu et diffé par un humain,
   et c'est de toute façon la forme que le mapper doit produire.
3. **Les dates sont en ISO UTC.** `TimeConverter.parseUtcDate` accepte déjà le format
   `uuuu-MM-dd'T'HH:mm:ss'Z'` en plus du format d'affichage : le joint existe, il n'y a rien
   à écrire.

---

## 4. Le chemin, dans les deux sens

**Écriture** — `MissionEntry` → `WizardPrefill.fromEntry()` → map de valeurs →
`ScenarioMapper.toDto(values, entry)` → `ScenarioMission` → Jackson.

**Lecture** — Jackson → `ScenarioMission` → `ScenarioMapper.toWizardValues()` → map →
`MissionFactory.specFromWizardValues()` → `MissionSpec` → `new MissionEntry(spec)`, puis
`setOptimizationType(mode)`, `setScheduledDate`, `setColor`, `setVisible`.

### 4.1 Pourquoi le chargement repasse obligatoirement par `MissionFactory`

C'est là que vivent le dimensionnement ergol (`PropellantBudget`, `directConfiguration` /
`highOrbitConfiguration`) et la subtilité « inclinaison absente = plein est dérivé de la
latitude en `double`, pas des degrés arrondis » (`MissionFactory:213`). Reconstruire un
`MissionSpec` directement depuis le fichier dupliquerait environ 150 lignes délicates et
ferait dériver les trajectoires sans qu'aucune assertion ne le voie.

### 4.2 Ce que le mapper traduit, et ce qu'il ne traduit pas

`ScenarioMapper` traduit entre le **DTO** et la **map de valeurs** — jamais entre le DTO et
le spec. Les deux sens sont exactement symétriques, ce qu'un unique test aller-retour
vérifie. Conséquence voulue : la règle de l'absence signifiante n'a qu'une implémentation,
celle de `WizardPrefill`, et le format en hérite au lieu de la redire.

Le mapper reste **hors de la couche UI** : il emploie les mêmes littéraux de clés que
`MissionFactory`, qui documente déjà que ses clés « reflètent celles de
`ui.mission.wizard.FormField` sans dépendre de la couche UI ». C'est l'`AppState` du §6.3,
dans `states/`, qui appelle `WizardPrefill` à l'enregistrement.

### 4.3 La réparation de `MISSION_HORIZON_DAYS`

Tant que `WizardPrefill` n'écrit pas la clé (§1.6), **tout enregistrement perd l'horizon
forcé**. La réparation — une écriture conditionnelle dans `WizardPrefill`, plus son test —
fait donc partie de ce lot, et corrige au passage l'édition de mission depuis le wizard.

---

## 5. Le rejeu

`MissionPlanner` est une `@FunctionalInterface` rendant un `MissionPlan` : le rejeu est un
**troisième planner**, à côté de `FixedLoadPlanner` et `MinimizedLoadPlanner`.

Dans `MissionOptimizer`, la boucle d'étages ne change pas ; seul le point qui produit le
`OptimizationResult` change. Quand une solution est fournie, au lieu de construire un
`CMAESTrajectoryOptimizer` :

```java
SpacecraftState best = problem.propagate(variables);
result = new OptimizationResult(variables, problem.computeCost(best), best, 0, entryState);
```

`propagate` et `computeCost` sont tous deux au contrat de `TrajectoryProblem` : rien n'est
approché, et `evaluations = 0` se lit honnêtement comme « pas optimisé ». Les blocs de
diagnostic — saturation de bornes, décomposition Δv, barrières, état de fin de gravity turn —
sont **sautés** en rejeu : ils re-propagent plusieurs fois pour écrire des journaux qui n'ont
de sens que face à une optimisation.

En `PRECISE`, le planner de rejeu applique d'abord `spec.withLauncherLoads(charges × λ)`
avant d'injecter les vecteurs (§2.3).

### 5.1 Le rejeu est tout ou rien

Si une clé d'étage manque, ou si les clés ne recouvrent pas la composition — mode changé,
étage renommé, composition modifiée par un lot ultérieur — le rejeu est **abandonné en bloc**
et la mission arrive en `DRAFT`, à recalculer par une action `OPTIMIZE` ordinaire. Rejouer la
moitié des étages et optimiser l'autre moitié produirait une trajectoire que personne n'a
demandée et que rien ne signalerait.

### 5.2 Où il s'exécute

Sur l'exécuteur mono-thread `mission-optimizer` que `MissionOrchestratorAppState` possède
déjà, par le même chemin que `submitForComputation` : le rejeu reste une propagation et une
génération d'éphéméride, jamais du travail sur le thread de rendu.

---

## 6. L'interface

### 6.1 Deux entrées de menu

Dans `MissionDisplayPanelAppState.MENU_ITEMS`, après *New mission…* et derrière un
séparateur : ***Open scenario…*** et ***Save scenario…***. Elles publient un
`UiNavigationEvent` sur l'`EventBus`, drainé par l'`AppState` du §6.3 — la règle « pas de
`getState()` » interdit tout autre couplage.

**Dépendance d'actif** : `AppMenuItem` impose une icône par entrée et il n'existe sous
`interface/` ni icône « ouvrir » ni icône « enregistrer ». Deux PNG sont à produire ; d'ici
là le lot est livrable avec un repli temporaire sur `wizard/lbl-box` et
`missions/icon-action-manage`.

### 6.2 Une modale à deux modes

`ScenarioBrowserWidget` (`ui/mission/scenario/`), doublé d'un `ScenarioBrowserModel` pur —
sélection, validité du nom, écrasement, activation des boutons — testé hors JME comme
`AppMenuModel` l'est. La fenêtre réutilise l'existant : `ModalBackdrop`, `WindowDragHandler`,
`ConfirmDialog`, `UiLayers`, et l'inscription dans `HudSurfaces` qui lui donne `ESC`
gratuitement.

- **Mode OPEN** : la liste des scénarios du dossier — nom, date d'enregistrement, nombre de
  missions, lus dans l'en-tête de chaque fichier. `ConfirmDialog` si la session courante
  n'est pas vide.
- **Mode SAVE** : la même liste, plus un champ nom ; cliquer une ligne remplit le champ.
  `ConfirmDialog` si le nom existe déjà.

### 6.3 Ouvrir remplace la session

Ouvrir un scénario, c'est changer de session : les missions courantes sont supprimées —
renderers détruits, focus caméra réinitialisé — et remplacées par celles du fichier, puis
l'horloge est posée sur `clockDate`. Un `ConfirmDialog` prévient quand la liste courante
n'est pas vide, sur le motif déjà en place pour *Quit*.

**Invariant** : le fichier est entièrement lu et converti en specs **avant** que la moindre
mission courante ne soit détruite. Un fichier corrompu ne coûte donc jamais la session en
cours.

---

## 7. Refus et versions

La règle du projet est de **refuser, jamais de rabattre en silence** — celle que
`MissionFactory` applique déjà à un RAAN illisible.

- **Refus par mission.** Une mission dont le lanceur a quitté le catalogue, dont
  l'inclinaison est devenue inatteignable depuis son site, ou dont une valeur est illisible,
  est écartée avec son motif brut (`MissionEntry.describeFailure` : l'exception telle quelle,
  jamais une table de messages amicaux). Les autres se chargent : un scénario de six missions
  dont une est cassée en ramène cinq, pas zéro.
- **Refus du fichier entier** dans un seul cas : une `formatVersion` supérieure à la version
  connue, refusée avec son numéro — on ne sait pas ce qu'on lit.
- **Nom de fichier** restreint à `[A-Za-z0-9 _-]`, refusé hors de ce jeu, jamais assaini.

---

## 8. Stockage

`~/.orbitlab/scenarios/<nom>.json`, créé à la demande : c'est le premier fichier que
l'application écrit (§1.7). Un `ScenarioStore` mince — `list` / `read` / `write` / `exists` —
isole le disque du reste.

**Rien n'est automatique.** Pas d'enregistrement à la fermeture, pas de rechargement au
démarrage : deux entrées de menu, deux gestes. La fin de phase est tenue par un
enregistrement volontaire, et l'auto-chargement reste ajoutable plus tard sans changer le
format.

---

## 9. Tests

Cinq tests, tous hors JME et hors propagation :

1. **`ScenarioMapperTest`** — aller-retour `map ↔ DTO` sur les deux types, **avec et sans**
   les valeurs absentes : inclinaison non commandée, RAAN, horizon.
2. **`ScenarioCodecTest`** — aller-retour JSON, champs omis compris, et refus d'une
   `formatVersion` future.
3. **`ScenarioBrowserModelTest`** — sélection, nom invalide, écrasement, activation des
   boutons.
4. **`WizardPrefillTest`** — l'horizon forcé revient (§4.3).
5. **Bout en bout `entry → JSON → spec`** — le spec reconstruit est comparé à l'original,
   **charges ergol comprises**. C'est ce test qui interdit une dérive silencieuse du
   dimensionnement.

Le rejeu d'une vraie trajectoire coûte des minutes : test opt-in sous
`-Dorbitlab.slowTests=true`, comme les boucles d'optimisation existantes.

---

## 10. Hors périmètre, explicitement

- Pas d'enregistrement ni de chargement automatique (§8).
- Pas de sélecteur de fichier système : aucune dépendance AWT/Swing n'entre dans
  l'application.
- Pas d'export ni d'import mono-mission. Le format porte déjà un tableau : il pourra être
  ajouté sans changer de schéma.
- Pas de champ « atmosphère » dans le wizard — c'est `PHY-2`. Le champ existe au format et
  vaut `NONE` (§1.5).
- **Aucun fichier de préférences utilisateur.** Cela tranche la question ouverte n° 6 de la
  roadmap : les bascules d'affichage d'`UI-4` restent **volatiles**, et le jour où elles
  seront conservées ce sera dans un second fichier, jamais dans le scénario — sans quoi
  rejouer le scénario d'un tiers reconfigurerait l'écran de celui qui l'ouvre.

---

## 11. Écarts à la fiche roadmap

Quatre affirmations de la fiche `UI-3` sont corrigées par les mesures du §1. Elles ne
changent pas la décision de faire `UI-3`, ni son placement en phase 3 ; elles déplacent son
coût.

| Fiche | Mesure |
|---|---|
| « `MissionSpec` sérialise déjà les paramètres du wizard (… date …) » | la date de lancement est sur `MissionEntry`, pas sur le spec (§1.2) |
| « … seed CMA-ES » parmi les données à persister | le seed vaut `42L` en dur et n'est pas exposé ; rien à persister (§1.3) |
| « Manquent le schéma v1, les (dé)sérialiseurs et deux entrées de menu » | il manque aussi **un mode de rejeu** et **une fenêtre de sélection** ; c'est là qu'est le vrai coût du lot (§1.4, §6.2) |
| Persister le résultat « évite de rejouer un CMA-ES de plusieurs minutes » | exact, mais ce n'est pas la sérialisation qui l'évite : c'est le planner de rejeu du §5, qui n'existe pas encore |

En sens inverse, une mesure **abaisse** le coût annoncé : les étages ne relisent que
`bestVariables()` (§1.4), donc rien de ce que porte `OptimizationResult` n'est difficile à
sérialiser.
