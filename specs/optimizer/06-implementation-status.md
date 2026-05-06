# État des lieux — Optimiseur de mission

Snapshot de l'implémentation à date, croisée avec la roadmap
(`03-robustness-roadmap.md`) et la spec d'exécution Phase 0
(`04-phase0-baseline-analysis.md`). Sert de cadrage pour le **prochain jalon
métier : mission parking → GTO → GEO**.

## Résumé exécutif

- **Phase 0 (alignement géodésique)** + **Phases 1-2 (gravity turn et
  transfert altitude-aware)** : **livrées**.
- **Instrumentation Phase 0.1** complète (saturation des bornes, décomposition
  Δv, état de fin de GT, barrières).
- **Phase 3 (robustesse)** : **partielle** — feasibility check (3.3) OK ;
  auto-widening des bornes (3.1) et warm-start Hohmann explicite (3.2)
  manquants.
- **Phase 4 (assertions de régression)** : **harness en place mais assertions
  désactivées** dans `LEOAltitudeSweepTest`.
- **Phase 5 (anticipation GTO via `OrbitTarget`)** : **non démarrée** — c'est
  le bloqueur principal pour le jalon parking → GTO → GEO.

## Status par Niveau de la roadmap

Légende : ✅ implémenté · ⚠️ partiel · ❌ non implémenté.

| Niveau | Description | Statut | Preuve principale |
|---|---|---|---|
| 0 | Alignement géodésique de la cost (`OneAxisEllipsoid.transform`) | ✅ | `TransferTwoManeuverProblem.java:350-407` (helper `computeGeodeticAltitude`) |
| 1.1 | `transitionTime` adaptatif (`300 + 0.3·sqrt(targetAlt)`) | ✅ | `GravityTurnProblem.java:70-82` |
| 1.2 | `vTanMin` dérivé vis-viva | ✅ | `GravityTurnConstraints.java:66-82` |
| 1.3 | Ratios apogée adaptatifs (0.95 → 0.75) + `apogeeMinSafe` | ✅ | `GravityTurnConstraints.java:51-64` |
| 1.4 | Fenêtre FPA (au lieu d'un point dur 2°) | ✅ | `GravityTurnConstraints.java:84-98` + `GravityTurnProblem.java:144-151` |
| 1.5 | Borne basse `exponent` | ⚠️ | `GravityTurnProblem.java:65-66` — élargie à 0.1 (était 0.3) mais non altitude-adaptative |
| 2.1 | Borne `β1` adaptative (en fonction de `apoDefect`) | ✅ | `TransferTwoManeuverProblem.java:207-215` |
| 2.2 | Sigma initial dynamique `0.3·(upper − lower)` | ✅ | `TransferTwoManeuverProblem.java:294-304` |
| 2.3a | `t1` borné par fraction de période orbitale | ✅ | `TransferTwoManeuverProblem.java:74-102, 221` (`T1_MAX_PERIOD_FRACTION = 0.5`) |
| 2.3b | `dt1` facteur 4 + cap propergol physique | ✅ | `TransferTwoManeuverProblem.java:73, 222` |
| 2.3c | Feasibility check Hohmann (Tsiolkovsky) | ✅ | `TransferTwoManeuverProblem.java:193-205` |
| 2.4a | Termes d'erreur absolus (`ABS_ERR_SCALE`) | ✅ | `TransferTwoManeuverProblem.java:60-62, 362-363` |
| 2.4b | `W_E` adaptatif (plus fort à basse altitude) | ✅ | `TransferTwoManeuverProblem.java:52, 56, 236` |
| 2.4c | `periapsisFloor` adaptatif | ✅ | `TransferTwoManeuverProblem.java:105, 232-235` |
| 3.1 | Auto-élargissement des bornes en cas de saturation | ❌ | détection présente (`OptimizerDiagnostics`), action manquante dans `CMAESTrajectoryOptimizer` |
| 3.2 | Warm-start Hohmann explicite | ⚠️ | `buildInitialGuess` est Hohmann-like (`TransferTwoManeuverProblem:269`) mais pas un seed pur séparé des runs d'exploration |
| 3.3 | Diagnostic d'infaisabilité a priori | ✅ | identique à 2.3c |
| 4.1 | Assertions actives sur `LEOAltitudeSweepTest` | ⚠️ | `LEOAltitudeSweepTest:50-114` mesure et logue ; aucune assertion JUnit |
| 5 | Abstraction `OrbitTarget(rPeri, rApo, inclination)` | ❌ | aucun record `OrbitTarget`, aucune `Burn2Resolver` polymorphe |

## Instrumentation Phase 0.1

Toutes les sondes prévues sont en place et exploitées par `MissionOptimizer` :

| Sonde | Implémentation | Consommateur |
|---|---|---|
| Saturation des bornes | `OptimizerDiagnostics.java` (`evaluateBounds`, `logBoundReport`) | `MissionOptimizer.java:104-107` |
| Décomposition Δv (utile / gaspillé / burn 2) | `TransferTwoManeuverProblem.java:438-466` (`DvBreakdown`) | `MissionOptimizer.java:119-126` |
| État de fin de GT vs handoff Hohmann idéal | `StageEndStateDiagnostic.java` | `MissionOptimizer.java:141-157` |
| Barrières activées (peri / altMin / altMax) | `TransferTwoManeuverProblem.java:481-529` (`BarrierReport`) | `MissionOptimizer.java:127-136` |
| Suite paramétrique de référence | `LEOAltitudeSweepTest.java` (`[150 → 2000]` km, `@Tag("slow")`) | logs `SWEEP_BASELINE` |

## Inventaire mission / stage actuel

État de l'écosystème mission au regard du jalon GTO.

- **Missions** : `LEOMission` uniquement. Aucune `GTOMission`, `ParkingMission`,
  `MultiSegmentMission`.
- **Objective** : `OrbitInsertionObjective(body, altitude, eccentricity)` —
  paramétré par `eccentricity`, mais ce champ **n'est utilisé nulle part dans
  l'optimiseur** (opportunité : déjà à moitié prêt à exprimer une cible
  elliptique).
- **Stages existants** : `VerticalAscentStage`, `GravityTurnStage`,
  `TransfertTwoManeuverStage`, `CoastingStage`, `BallisticCoastingStage`,
  `ConstantThrustStage`. Aucun stage *apogee raise* ni *plane change*.
- **Maneuvers / resolvers** : `Burn2Resolver` (package-private, `final`,
  hardcodé sur la circularisation à l'apoapsis suivante).
- **Problems d'optimisation** : `GravityTurnProblem`, `TransferTwoManeuverProblem`
  paramétrés par `double targetAltitude` partout — incompatibles avec une
  cible elliptique sans refactor.

## Gap parking → GTO → GEO

### Séquence physique attendue

1. Ascent (`VerticalAscentStage` + `GravityTurnStage`) → parking circulaire
   ~200-400 km, faible inclinaison (Kourou ≈ 5.2°).
2. Coast en parking jusqu'à un point favorable.
3. **Burn d'élévation d'apogée** au périgée → GTO
   (`r_apo ≈ 42 164 km`, `r_peri ≈ parking`).
4. Coast jusqu'à l'apogée GTO.
5. **Burn d'apogée combiné circularisation + plane change** → GEO
   (`a = 42 164 km`, `e ≈ 0`, `i ≈ 0°`).

### Must have

| Item | Difficulté | Notes |
|---|---|---|
| Abstraction `OrbitTarget(rPeri, rApo, inclination)` | Moyenne | Niveau 5 du roadmap. Ripple sur `TransferTwoManeuverProblem`, `GravityTurnConstraints`, `OrbitInsertionObjective` (champ `eccentricity` déjà là). |
| Cost function généralisée pour cible elliptique | Moyenne | Comparer apo/peri à `rApoTarget`/`rPeriTarget` distincts ; conserver le cas circulaire dégénéré. |
| `Burn2Resolver` polymorphe | Moyenne | Transformer en interface : `CircularizationResolver` (existant) + `ApogeeRaiseResolver` (nouveau). Aujourd'hui `final` et package-private. |
| Stage *apogee raise* (parking → GTO) | Moyenne | Réutilise probablement `TransfertTwoManeuverStage` une fois `OrbitTarget` et resolver polymorphes en place. |
| Stage *circularisation + plane change à l'apogée* (GTO → GEO) | Moyenne-haute | Optimisation couplée Δv tangentiel + composante out-of-plane (~5° à 28° selon site). Nouveau problem CMA-ES (3-4 paramètres). |
| `GTOMission` (ou `MultiSegmentMission` configurable) | Moyenne | Au choix : nouvelle classe concrète ou mission générique paramétrée par une liste de `(stage, OrbitTarget)`. |
| Test d'intégration parking → GTO → GEO | Haute (compute) | 3 optimisations en cascade ; `@Tag("slow")` obligatoire. |

### Nice to have

| Item | Difficulté | Bénéfice |
|---|---|---|
| Niveau 3.1 — auto-widening des bornes | Faible | Filet de sécurité contre cas pathologiques GTO non anticipés. |
| Niveau 3.2 — warm-start Hohmann explicite | Faible | Garantit un run partant de la solution analytique pure ; utile car la géométrie GTO est tendue. |
| Niveau 4.1 — assertions actives sur `LEOAltitudeSweepTest` | Faible | Verrouille les acquis Phases 0-2 **avant** de toucher au code partagé pour GTO. |
| Plane change combiné optimal à l'apogée GEO | Moyenne | Économie ~150-300 m/s vs. plane change pur. Demande un objectif Δv minimisé, pas seulement géométrique. |
| Phasing GEO (longitude cible / slot) | Haute | Hors périmètre court terme. |
| Drag à parking 200 km | Moyenne | Vérifier si `OrekitService.optimizationPropagator` modélise l'atmosphère (question ouverte du roadmap). |
| Alignement géodésique du `MinAltitudeTracker` | Faible | Cohérence complète avec Niveau 0 (signalé hors périmètre par doc 05 §2.3). |

### Pré-requis recommandés avant d'attaquer GTO

Ordre suggéré pour limiter le risque :

1. **Niveau 4.1** — activer les assertions de la suite paramétrique LEO
   (verrouille l'existant avant tout refactor partagé).
2. **Niveau 3.2** — warm-start Hohmann (faible coût, gros gain robustesse pour
   les cas tendus GTO).
3. **Niveau 5** — abstraction `OrbitTarget` (refactor non fonctionnel : LEO
   continue de passer en cas dégénéré `rPeri == rApo`).
4. `Burn2Resolver` interface + extraction `CircularizationResolver`.
5. `ApogeeRaiseResolver` + premier test parking → GTO (sans plane change,
   inclinaison de parking conservée).
6. Stage circularisation + plane change → GEO et test bout-en-bout.
7. **Niveau 3.1** — auto-widening (en dernier, une fois la boîte stabilisée).

## Questions ouvertes à trancher

1. **Drag à 200 km** — l'`optimizationPropagator` modélise-t-il l'atmosphère ?
   À vérifier avant le premier run parking. (Question reprise du roadmap.)
2. **Site de lancement** pour la démo GTO/GEO : Kourou (5.2°) minimise le
   plane change. À confirmer.
3. **Stratégie plane change** : tout à l'apogée GEO (classique) ou réparti
   (un peu au lancement, le reste à l'apogée) ?
4. **Réutilisation du champ `eccentricity` de `OrbitInsertionObjective`** —
   l'absorber dans `OrbitTarget` ou le déprécier ?
5. **Compute budget** — un test parking → GTO → GEO complet va prendre
   plusieurs minutes (3 optimisations en cascade). Smoke test rapide + suite
   complète nightly ?

## Références

- `01-convergence-analysis.md` — décodage initial des 4 paramètres CMA-ES.
- `02-altitude-dependent-design.md` — formules physiques (vis-viva, géométrie
  de transfert).
- `03-robustness-roadmap.md` — plan séquencé Phases 0-5.
- `04-phase0-baseline-analysis.md` — spec d'exécution Niveaux 0-5.
- `05-phase0-level0-cost-metric-alignment.md` — détail Niveau 0.
