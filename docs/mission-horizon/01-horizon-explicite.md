# MIS-8 — Horizon de mission explicite

> Statut : spec validée, implémentation en cours. 2026-08-08.
> Fiche roadmap : [`docs/roadmap/01-roadmap.md`](../roadmap/01-roadmap.md) §6, `MIS-8` — ★5 ◆2 M.

---

## 1. Le problème

La date de fin d'une mission est une constante, arbitraire à deux endroits.

| Constante | Valeur | Rôle |
|---|---|---|
| `MissionEphemerisGenerator.DEFAULT_COAST_DURATION_SECONDS` | `86_164.0` | coast final, appliqué au dernier stage de la chaîne |
| `StageChainRunner.FALLBACK_DURATION_SECONDS` | `7200.0` | filet pour un stage sans cutoff configuré |

Trois défauts, du plus bénin au plus bloquant.

**Le commentaire ment.** `86_164.0` est annoté `// 90 min (one LEO orbit)` : c'est un jour
sidéral, seize fois la valeur commentée.

**Le symptôme est visible.** Passé cet horizon, `MissionOrchestratorAppState` bascule sur la
branche « clock after ephemeris » : le vaisseau est figé sur `lastPoint()` et `TelemetryWidget`
affiche `COMPLETE`. Un satellite inséré en LEO s'arrête de tourner au bout de ~23 h 56 de temps
simulé — quelques secondes de temps réel à ×10⁵.

**C'est un blocage dur pour les phases 4 et 5.** Un coast TLI vers la Lune dure ~3 jours, tronqué
avant l'arrivée par un horizon d'un jour. Un phasing de rendez-vous sur N révolutions bute sur le
même mur.

S'y ajoutent deux dépenses inutiles :

- l'échantillonnage est à pas fixe (`DEFAULT_STEP_SECONDS = 1.0`) et tout est gardé en mémoire dans
  des tableaux parallèles (~160 o par point) : 86 k points ≈ 14 Mo pour un jour, 2,6 M points
  ≈ 420 Mo pour trente ;
- `MissionOrchestratorAppState:94` appelle `eph.positionsUpTo(now)` **à chaque frame et par mission
  visible**, ce qui alloue une `ArrayList` neuve de cette taille à chaque fois.

**Le défaut de conception sous-jacent : un seul tableau sert deux consommateurs aux besoins
incompatibles.** L'enregistreur de vol (télémétrie, verdict de complétude) veut de la fidélité là
où la dynamique est rapide ; la polyligne d'affichage veut au plus quelques milliers de points,
l'écran faisant ~2000 px de large. La tension est déjà visible :
`MissionTrajectoryRenderer.MAX_POINTS = 8192` et `update()` parcourt le tableau **à rebours depuis
la fin** (`currentPositions.get(size - i)`). La traînée dessinée est donc les 8192 *derniers*
échantillons, soit ≈ 2 h 17 de temps mission au pas actuel : sur toute mission plus longue,
l'ascension disparaît silencieusement de la ligne. Ce n'est pas une décimation, c'est une
troncature par le début, et personne ne l'a décidée.

---

## 2. Décisions

| Question | Décision |
|---|---|
| Périmètre | Tout le « À faire » de la fiche roadmap, wizard compris |
| Forme du réglage | Un champ « durée de mission » unique, prérempli et réversible (bouton `AUTO`) |
| Défaut dérivé | Généreux, ~3 jours : LEO `Revolutions(48)`, GEO `Revolutions(3)` |
| Pas d'échantillonnage | Constantes burn / coast portées par l'étage : 1 s si propulsif, 60 s sinon |
| Polyligne d'affichage | Produit dédié construit **une fois** à la génération, décimé à pas constant |
| `FALLBACK_DURATION_SECONDS` | **Valeur inchangée**, branche rendue bruyante et couverte par un test |

### Deux horizons à ne pas confondre

Celui de cette spec est l'**horizon de restitution** : jusqu'où on échantillonne et affiche. Il ne
doit changer aucune trajectoire optimisée. Voir §8, où l'on montre que c'est structurel et non
seulement souhaité.

---

## 3. Le modèle : `MissionHorizon`

Interface scellée dans `simulation/mission/`, aux côtés de `MissionType` et `MissionStatus` — c'est
du vocabulaire de mission, pas de la plomberie d'éphéméride.

```java
sealed interface MissionHorizon permits Revolutions, FixedDuration, TrailingCoast
  record Revolutions(int count)          // N révolutions après insertion
  record FixedDuration(double seconds)   // durée totale depuis le lancement
  record TrailingCoast(double seconds)   // coast de longueur fixe après insertion
```

Une seule opération : `finalCoastSeconds(launchDate, insertionState)`, la durée du coast final.
Elle prend l'**état** d'insertion et non une date plus une orbite : c'est ce que l'appelant a
réellement en main (`mission.getCurrentState()`), la date s'en déduit, et `Revolutions` y lit
lui-même sa période sans dépendre de la forme d'`AchievedOrbit`.

| Cas | Résolution |
|---|---|
| `Revolutions(n)` | `n × période képlérienne de l'orbite atteinte` |
| `FixedDuration(s)` | `s − (dateInsertion − launchDate)` |
| `TrailingCoast(s)` | `s`, indépendant de l'insertion |

> **Pourquoi trois cas et pas deux.** `TrailingCoast` est la primitive que `StageChainRunner`
> implémente déjà, et c'est exactement la sémantique de la constante remplacée. Il existe pour que
> le défaut de `Mission` soit *démontrablement* l'ancien comportement. Exprimer ce défaut en
> `FixedDuration(86_164)` aurait été subtilement différent : une durée totale retranche l'ascension,
> raccourcissant le coast d'environ 600 s — assez pour déplacer ce que mesure
> `GravityTurnFloorProbeTest`. Le wizard n'écrit jamais ce cas.

Les trois garde-fous :

1. **Clamp à zéro.** Un `FixedDuration` plus court que l'ascension donne un coast nul, loggé en
   `warn` : l'utilisateur obtient l'ascension, pas une erreur.
2. **Repli.** Orbite atteinte indisponible ou non elliptique (pas de période képlérienne, cas d'une
   trajectoire d'échappement) → `FixedDuration(3 j)`, loggé en `warn`.
3. **Plafond dur à 30 jours.** Au-delà, la réponse est `MIS-9` (éphéméride hors mémoire), pas un
   tableau plus gros.

Les défauts sont exposés par `MissionHorizon.defaultFor(MissionType)` :

| Type | Défaut | Durée équivalente |
|---|---|---|
| LEO | `Revolutions(48)` | ≈ 3,2 j à 550 km (période 95,6 min) |
| GEO | `Revolutions(3)` | = 3,0 j (période 23 h 56) |

---

## 4. Le chemin de la donnée

```
MissionSpec.Leo/.Geo  ──(composant `horizon`)──▶  MissionComposer
                                                       │ setHorizon()
                                                       ▼
                                                    Mission
                                                       │ getHorizon()
                                                       ▼
                              MissionOptimizer ──(finalCoastSeconds)──▶ MissionEphemerisGenerator
                                                                              │
                                                                              ▼
                                                              StageChainRunner.sampling(...)
```

**`MissionSpec`** gagne un composant `MissionHorizon horizon`. Deux appelants de production
seulement, tous deux dans `MissionFactory`, plus les deux `withLauncherLoads` internes — que le
balayage ergols appelle, et qui doivent donc le propager.

**`Mission`** expose `getHorizon()` / `setHorizon()`, `MissionComposer` étant l'unique écrivain.

> **Pourquoi un setter et pas un paramètre de constructeur.** La chaîne est profonde — quatre
> constructeurs `LEOMission`, quatre `GEOMission`, `EarthMission`, plus les stubs de test — et
> chaque mission de test devrait fournir un horizon dont elle n'a que faire. L'horizon a exactement
> le statut d'`initialDate` : décidé dehors, appliqué à la mission. `Mission` est déjà un porteur
> mutable assumé (`setInitialDate`, `setStatus`, `setCurrentState`).

**La valeur par défaut du champ est `TrailingCoast(86_164.0)`** — la constante actuelle, à la
seconde près. Ce n'est pas de la nostalgie : `GravityTurnFloorProbeTest` appelle `generate()` sur des
missions construites hors composer. Ce défaut leur garantit un comportement identique, et confine
l'écart mesuré du chantier aux missions que l'application construit.

**La résolution produit une durée, pas une date.** `MissionOptimizer` calcule déjà
`AchievedOrbit.of(mission.getCurrentState())` juste avant de générer, et à ce point
`getCurrentState()` *est* l'état d'insertion : le `CoastingStage` final n'override pas
`propagateStandalone`, il est donc un no-op sur la passe d'optimisation. L'optimiseur résout
l'horizon en secondes de coast et les passe à `generate(mission, initialState, finalCoastSeconds)`.
`StageChainRunner` conserve la sémantique `lastStageCoastSeconds` qu'il a déjà : **seule la valeur
cesse d'être une constante.** Une surcharge à deux arguments résout depuis `mission.getHorizon()`
pour les appels directs.

Passer une durée plutôt qu'une date absolue rend le résultat insensible à un éventuel écart de
quelques secondes entre la date d'insertion de la passe d'optimisation et celle du replay.

---

## 5. Le pas d'échantillonnage variable

`MissionStage.sampleStepSeconds(entryState, mission)`, calque exact de `maxStepSeconds` :

```java
protected static final double BURN_SAMPLE_STEP  = 1.0;
protected static final double COAST_SAMPLE_STEP = 60.0;

public double sampleStepSeconds(SpacecraftState entryState, Mission mission) {
  return isPropulsive() ? BURN_SAMPLE_STEP : COAST_SAMPLE_STEP;
}
```

`StageChainRunner` perd son champ `sampleStepSeconds` et interroge chaque étage au moment de
brancher le multiplexeur ; la fabrique `sampling(...)` perd son premier paramètre et
`DEFAULT_STEP_SECONDS` disparaît.

**Le coast final est couvert sans cas particulier** : le dernier étage est toujours un
`CoastingStage("Coasting", null)`, non propulsif, donc échantillonné à 60 s.

| Horizon | Pas fixe 1 s (avant) | Pas variable (après) |
|---|---|---|
| LEO nominal, 1 j → 3,2 j | 86 400 pts ≈ 14 Mo | ≈ 5 200 pts ≈ 0,8 Mo |
| GEO nominal, 3 j | — | ≈ 5 000 pts ≈ 0,8 Mo |
| 30 j réglés à la main | 2,6 M pts ≈ 420 Mo | ≈ 43 000 pts ≈ 7 Mo |

C'est ce 17× — obtenu *pendant* que l'horizon triple — qui paie tout le reste du chantier.

---

## 6. Les deux produits

`MissionEphemeris` garde sa pleine résolution : c'est l'**enregistreur de vol** — télémétrie,
`isComplete()`, `allPoints()` pour les tests. Il construit une fois, dans son constructeur, un
produit dérivé :

```java
final class TrajectoryPolyline {
  static final int MAX_POINTS = 8192;            // seule source de vérité du budget
  Vector3D[] positions;  AbsoluteDate[] times;   // parallèles
  int indexUpTo(AbsoluteDate date);              // recherche binaire, zéro allocation
}
```

Décimation à pas constant, `stride = ceil(n / MAX_POINTS)`, premier et dernier points toujours
conservés, **et seulement si `n > MAX_POINTS`** — donc jamais sur le défaut retenu. Elle ne sert que
les horizons longs réglés à la main.

> **Réserve assumée.** À 30 jours (stride 6) l'ascension n'est plus dessinée qu'avec ~100 points au
> lieu de 600. À ce niveau de dézoom c'est un point à l'écran. La décimation géométrique
> (angle / corde), écartée ici pour deux constantes à calibrer sans usage qui les exige, reste
> disponible si l'artefact devient visible.

Le contrat par frame dans `MissionOrchestratorAppState` :

```java
MissionEphemerisPoint pt = eph.interpolate(now);        // déjà calculé pour la pose
TrajectoryPolyline trail = eph.displayTrail();          // même instance à chaque frame
renderer.updateFromEphemeris(pt, trail, trail.indexUpTo(now), cam, tpf);
```

Zéro allocation. `positionsUpTo` et `allPositions` disparaissent avec leurs deux appelants.

`MissionTrajectoryRenderer.update()` écrit `[0..upTo]` **vers l'avant** puis la pointe interpolée
(`pt.position()`, déjà en main), et dimensionne son `FloatBuffer` depuis
`TrajectoryPolyline.MAX_POINTS + 1`. Le parcours à rebours disparaît — et avec lui la troncature par
le début. Les deux branches de visibilité de l'orchestrateur convergent sur le même appel, la
branche « clock after ephemeris » n'étant plus qu'un `upTo = size - 1`.

---

## 7. Le wizard

Une ligne `MISSION DURATION` dans `StepParameters`, après `LAUNCH DATE` : un `TextField` en jours
(décimales acceptées) et un bouton `AUTO`. La validation reprend le motif de `validateLaunchDate()`
— champ coloré, helper remplacé, entrée refusée mémorisée pour que l'erreur s'efface à la première
frappe. Bornes `[1 min, 30 j]`.

L'estimation affichée en mode auto vient du type courant : `DynamicParameters.defaultHorizonDays()`,
une méthode par type qui calcule `N × 2π√(a³/µ)` depuis ses propres altitudes. `StepParameters`
l'interroge dans son `update(tpf)`, qui tourne déjà chaque frame ; le préremplissage suit donc
l'altitude cible tant que l'utilisateur n'a pas touché au champ. Helper :
`auto · 48 révolutions ≈ 3,2 j`.

**Ce qui fait tenir « réversible » sans état supplémentaire** : en mode auto, `getValues()` **n'émet
pas** la clé `MISSION_HORIZON_DAYS`. `MissionFactory` retombe alors sur
`MissionHorizon.defaultFor(type)`, et `applyValues` — le chemin de réédition d'une mission — voit
une clé absente et rouvre en auto. Pas de booléen à sérialiser, et le comportement survit au
round-trip par la map de valeurs brutes.

---

## 8. Pourquoi l'optimiseur ne peut pas bouger

La fiche roadmap demande de vérifier par un test de non-régression que l'horizon ne touche aucune
baseline d'optimiseur. La vérification a été faite sur le code, et le résultat est plus fort qu'un
test : **c'est structurel.**

- `MissionEphemerisGenerator` est le **seul** appelant de production de `StageChainRunner.sampling(...)`
  avec un `lastStageCoastSeconds` non nul.
- La passe d'optimisation emprunte `StageChainRunner.plain()`, construit avec
  `lastStageCoastSeconds = 0`, et `MissionOptimizer` avance les étages par `propagateStandalone`.
- Le `CoastingStage` final n'override pas `propagateStandalone` : il est un no-op sur cette passe.

Le coast final n'est donc **jamais volé pendant l'optimisation**. L'horizon est postérieur au dernier
étage optimisé par construction.

Le test qui verrouille l'invariant appelle `generate()` **deux fois** avec des horizons éloignés et
compare. Il n'a besoin d'aucune optimisation : ce qui est testé est le câblage — quelle phase reçoit
quel pas, ce qui borne une phase sans cutoff, et jusqu'où l'horizon peut atteindre. Des phases
inertes suffisent, et ne pas avoir besoin d'un modèle de poussée garde ces tests à la seconde plutôt
qu'à la minute.

Il affirme **deux choses à deux niveaux d'exigence différents**, et la distinction est mesurée, pas
supposée :

- **Tout ce qui précède le coast final est identique au bit.** Ces phases sont propagées jusqu'à
  leurs propres cutoffs, que l'horizon ne touche pas : aucune tolérance n'est accordée. C'est
  l'assertion qui attraperait une fuite de l'horizon dans un étage optimisé.
- **Le coast final lui-même s'accorde au millimètre, pas exactement.** Mesuré : **1,3 µm** sur les
  premiers échantillons communs. Ce n'est pas l'horizon qui perturbe la trajectoire, c'est
  l'intégrateur adaptatif qui choisit une séquence de pas différente quand on lui donne une date
  cible différente — attendu, et sans conséquence pour une phase qui est par construction postérieure
  à tout ce que l'optimiseur a produit.

---

## 9. Le filet `FALLBACK_DURATION_SECONDS`

Contrairement à ce que suggère son commentaire (« every stage but the last sets
`getConfiguredEndDate()` »), cette branche est **atteignable**. `CoastingStage` ne fixe
`configuredEndDate` que dans la branche `maxTime != null` : un coast construit avec
`stopAtNode = true` s'arrête sur un `NodeDetector`, sans date configurée. Si le nœud n'arrive pas,
ce sont les 7200 s qui le bornent.

Le filet est donc réel et justifié, et la prudence de la fiche roadmap à son endroit est fondée.
**On ne change pas la valeur** : on ajoute un `logger.warn` quand la branche sert, et un test qui
confirme le bornage. On documente le filet au lieu de le déplacer.

---

## 10. Tests

Livrés, 22 tests, ~12 s au total.

| Classe | Ce qu'elle prouve |
|---|---|
| `MissionHorizonTest` | Les trois cas ; le défaut LEO tombe bien vers 3,2 j ; clamp à zéro sous l'ascension ; repli sur orbite non liée ; plafond 30 j sur les trois cas ; le défaut de `Mission` est le `TrailingCoast` historique |
| `TrajectoryPolylineTest` | Identité sous le budget et à la limite ; au-delà, taille bornée, **extrémités conservées** et temps strictement croissants ; `indexUpTo` au plancher, entre deux échantillons, et clampé aux deux bouts ; `displayTrail()` rend la même instance |
| `MissionHorizonSamplingTest` | La règle 1 s / 60 s lue sur les étages **et** telle qu'elle atterrit dans une éphéméride générée ; l'horizon n'atteint rien avant le coast final (§8) ; une phase sans cutoff est bornée par le filet, et ce n'est pas compté comme un shortfall |

Le filet est testé via un `CoastingStage` sans `maxTime` ni nœud plutôt que via
`stopAtNode = true` sur une orbite équatoriale : le `NodeDetector` d'une orbite dont la fonction de
commutation est identiquement nulle a un comportement numérique douteux, et ce n'est pas ce qu'on
cherche à mesurer. La branche exercée est la même.

---

## 11. Hors périmètre

- **`NAV-2`** (piste temporelle indexée sur le temps) — consommera cet horizon, ne le définit pas.
- **Le libellé `COMPLETE`** de `TelemetryWidget` — il reste exact, et le code lisant `eph.endDate()`
  ne bouge pas.
- **`MIS-9`** (éphéméride hors mémoire) — le pas variable rend tenable tout horizon réaliste ; les
  conditions de déclenchement de `MIS-9` restent non remplies.
