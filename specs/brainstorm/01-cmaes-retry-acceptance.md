# Brainstorm — Refonte de l'optimizer CMA-ES (retry schedule + acceptance multi-critères)

> Synthèse de la discussion. Le **plan d'exécution** correspondant est dans
> `~/.claude/plans/ceci-est-un-thread-hashed-russell.md` (à promouvoir vers
> `specs/optimizer/06-…` au moment de l'implémentation).

## Pourquoi cette discussion

L'optimizer trajectoire actuel (`CMAESTrajectoryOptimizer`) marche pour la
cible 400 km LEO mais avec une mécanique opaque :

- Deux phases hardcodées : exploration (4 runs perturbés, 40% du budget) puis
  cascade de raffinement (3 passes sigma×0.1/0.03/0.01, 60% du budget).
- Acceptance binaire scalaire (`getAcceptableCost()`) — on sait qu'une
  trajectoire est « bonne » ou « pas bonne », jamais *pourquoi*.
- Pas de diagnostic exploitable quand l'optimisation échoue : pas moyen de
  distinguer « altitude OK mais propergol épuisé » de « altitude ratée ».
- Logique de retry / fallback diffuse et difficile à régler par problème.

Trois objectifs sont ressortis de la discussion :

1. **Unifier exploration et polish** dans un seul mécanisme déterministe et
   réglable par problème.
2. **Rendre l'acceptance multi-critères et structurée**, avec un rapport qui
   peut servir aux logs aujourd'hui et à un feedback UI demain.
3. **Garder le meilleur résultat à travers les retries**, même quand le
   dernier régresse.

## Décisions retenues

### D1 — Retry schedule déterministe, plafond fixe par problème

Plutôt que deux phases distinctes, **une seule boucle de retries** dont chaque
index a un comportement défini :

| Index | Seed                                | sigmaMul                  | popFactor |
|-------|--------------------------------------|---------------------------|-----------|
| 0     | PHYSICS (initial guess physique)     | adaptatif (cf. D3)        | 1         |
| 1     | PERTURB_MODERATE autour best-so-far  | 1.5                       | 1         |
| 2     | PERTURB_STRONG autour best-so-far    | 0.3 (resserrage = polish) | 2         |
| 3     | RANDOM_UNIFORM dans bounds           | 3.5 (clamped à box/3)     | 2         |

L'index 2 absorbe le rôle de la cascade actuelle (sigma resserré autour du
meilleur courant) ; l'index 3 fait le saut large pour échapper aux minima
locaux. Plus besoin de cascade séparée.

Le schedule est un **record standalone** (`RetrySchedule`), pas des méthodes
sur `TrajectoryProblem`. Raisons :
- découplage physique / méta-optimiseur,
- réutilisable entre problèmes,
- testable en isolation.

Le plafond (`maxRetries()`) est par problème : 4 pour gravity-turn, 6 pour
transfer-2 (plus de variables, plus de risques de minima locaux).

### D2 — Acceptance multi-critères avec rapport structuré

`AcceptanceCriteria` (interface) décide à la fin d'un retry si on s'arrête,
en consultant **l'état final** + **le coût scalaire**. Elle retourne un
`AcceptanceReport` :

```
Status overall ∈ {ACCEPTED, IMPROVABLE, UNACCEPTABLE}
List<CriterionResult> criteria  // un par critère : nom, status, valeur, seuil, marge, unité
```

Réduction du composite :
- Si **un seul** critère est UNACCEPTABLE → overall UNACCEPTABLE.
- Si **tous** sont ACCEPTED → overall ACCEPTED (on s'arrête).
- Sinon → IMPROVABLE (on continue les retries si budget restant).

Critères concrets pour gravity-turn :

| Critère                    | ACCEPTED si                    | UNACCEPTABLE si           |
|----------------------------|---------------------------------|---------------------------|
| `apogeeError`              | ≤ 7%                           | > 15%                     |
| `eccentricityBound`        | e ≤ 0.05                       | e ≥ 1.0 (hyperbolique)    |
| `tangentialVelocity`       | ≥ minTangentialVelocity        | —                         |
| `minAltitudeNotViolated`   | tracker.minAlt ≥ 30 km         | minAlt < 0                |
| `propellantRemaining`      | mass ≥ vehicleMinMass          | mass < vehicleMinMass     |
| `flightPathAngle`          | FPA ∈ [fpaMin, fpaMax]         | —                         |

Pour transfer-2 (esquissé, à finaliser) : apogée et périgée ≤ 5%, e ≤ 0.001,
ratio vRadial/vCirc ≤ 0.01, barrières dures sur peri/min/max altitude.

> **Pourquoi apogée et pas altitude au handoff** pour gravity-turn : le GT
> sort sub-orbital et passe la main à une circularisation. C'est l'apogée du
> sub-orbital qui qualifie la qualité de l'insertion finale, pas l'altitude
> instantanée à MECO.

### D3 — Sigma adaptatif sur retry 0 uniquement

À retry 0, on fait une propagation à coût zéro de l'initial guess pour estimer
le `seedCost`, puis on dimensionne sigma :

- `seedCost ≤ acceptableCost × 5` → sigma = `getInitialSigma() × 0.5` (proche, on resserre).
- `seedCost ≤ 1.0` → sigma = `getInitialSigma() × 1.0` (médiocre, on garde la valeur du problème).
- sinon → sigma = `getInitialSigma() × 2.0` clamped à box/3 (loin, on ouvre).

Retries ≥ 1 utilisent le multiplicateur fixe du schedule. **Pas d'adaptation
dynamique inter-retries** — la simplicité prime, la séquence déterministe est
plus prévisible et plus testable.

### D4 — Best-so-far conservé à travers les retries

Un `BestTracker` interne conserve `(bestVars, bestCost, bestState, bestReport)`
et est mis à jour après chaque retry si `run.bestCost < bestCost`. Si le
dernier retry régresse, on retourne quand même le meilleur intermédiaire.

`OptimizationResult` gagne un composant `AcceptanceReport bestReport`
(nullable, rétro-compat via constructeur 5-args en shim).

### D5 — `AdaptiveConvergenceChecker` reste scalar-only

Hipparchus ne donne au checker que des coûts scalaires, jamais l'état
spacecraft. Donc :
- Le checker garde son rôle : early-exit basé sur le coût + tolérances
  absolue/relative + kill anti-divergence à 300 itérations si coût > 1.0.
- L'évaluation acceptance se fait **après** la run, dans
  `CMAESTrajectoryOptimizer`, via une propagation finale unique du `bestVars`.
- `getAcceptableCost()` reste utilisé comme hint d'early-exit scalaire (pas
  comme verdict).

## Alternatives considérées et écartées

### A1 — Garder la cascade de refinement comme phase séparée

**Écarté.** Le retry schedule peut absorber le polish via une étape à sigma
réduit autour du best-so-far (notre index 2). Ajouter une cascade post-retries
serait un deuxième mécanisme à régler et logger. Si la régression de qualité
le justifie, on ajoutera un step 4 à sigma×0.05 dans le schedule plutôt que
de réintroduire une phase distincte.

### A2 — Méthodes `seedForRetry(int)` / `sigmaForRetry(int)` sur `TrajectoryProblem`

**Écarté.** Mélange physique du problème et stratégie méta-optimiseur. Un
record `RetrySchedule` standalone est plus testable, plus réutilisable, et
laisse `TrajectoryProblem` focalisé sur la mécanique.

### A3 — Rendre `acceptanceCriteria()` abstrait (forcer l'override)

**Écarté pour l'instant.** Forcerait à migrer tous les problèmes en même
temps. La factory `AcceptanceCriteria.scalarFallback(...)` permet une
migration progressive : un problème non migré utilise automatiquement
l'ancien seuil scalaire emballé en rapport mono-critère.

### A4 — Sigma adaptatif inter-retries (recalculé à chaque étape)

**Écarté.** Trop dynamique, dur à débugger, dur à tester de manière
reproductible. La séquence déterministe est plus prévisible et la valeur
adaptative au retry 0 suffit pour l'essentiel du gain.

### A5 — Acceptance binaire (ACCEPTED / NOT_ACCEPTED)

**Écarté.** Le tier IMPROVABLE est utile pour distinguer « on peut faire
mieux mais c'est exploitable » de « inacceptable » (mass < 0, altitude
violée). Pour les logs aujourd'hui et le feedback UI demain.

## Hors scope — à reprendre dans un autre chantier

- **Feedback UI temps-réel** : progress bar, bouton cancel, événements
  EventBus de progression d'optimisation.
- **Migration de `getAcceptableCost()` → suppression définitive** : reste
  déprécié. À retirer quand tous les problèmes auront migré.
- **Critères transfer-2 finalisés** : esquissés ici, à affiner avec les
  bornes physiques de la spec `02-altitude-dependent-design.md`.
- **Couplage avec la roadmap altitude-dépendante** (specs/optimizer/03 et 04) :
  certaines bornes (apogeeError, periapsisError) gagneraient à varier avec
  `targetAltitude` plutôt qu'à être fixées en %.
- **Tuning des bandes de sigma adaptatif** (D3 : seuils 5×acceptable / 1.0) :
  empirique pour l'instant ; à valider sur la suite paramétrique 185 km →
  2000 km.

## Risques identifiés

| Risque                                    | Mitigation                                                  |
|-------------------------------------------|-------------------------------------------------------------|
| Régression qualité après drop cascade     | LEO test ±7% comme canary ; ajouter step 4 sigma×0.05 si nécessaire. |
| Sigma adaptatif retry 0 surprenant        | Log DEBUG seedCost + sigma calculé pour diagnostic.         |
| Reliquat de budget mal calculé            | Test unitaire dédié sur le calcul de budget par retry.      |
| Migration douce trop permissive           | `@Deprecated` + javadoc explicite ; revue à la prochaine release. |

## Références code

- `simulation/mission/optimizer/CMAESTrajectoryOptimizer.java` — fichier le
  plus impacté, à découper.
- `simulation/mission/optimizer/CMAESRunExecutor.java` — wrapper Hipparchus,
  signature `execute()` à préserver.
- `simulation/mission/optimizer/AdaptiveConvergenceChecker.java` — à laisser
  intact.
- `simulation/mission/optimizer/TrajectoryProblem.java` — ajouts non breaking.
- `simulation/mission/optimizer/problems/GravityTurnProblem.java` —
  bénéficiaire principal.
- `simulation/mission/optimizer/problems/TransferTwoManeuverProblem.java` —
  bénéficiaire secondaire.
- `simulation/mission/runtime/OptimizationResult.java` — +composant
  `bestReport`.
- `simulation/mission/runtime/MissionOptimizer.java` — log du rapport par
  stage.
- `test/.../optimizer/LEOMissionOptimizationTest.java` — canary de
  non-régression.

## Liens

- Plan d'exécution : `~/.claude/plans/ceci-est-un-thread-hashed-russell.md`
- Contexte altitude-dépendant : `specs/optimizer/02-altitude-dependent-design.md`
- Roadmap robustesse : `specs/optimizer/03-robustness-roadmap.md`, `04-phase0-baseline-analysis.md`
