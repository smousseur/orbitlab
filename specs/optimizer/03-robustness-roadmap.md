# Roadmap de robustesse — 185 km à ~2000 km, anticipation GTO

Plan d'action séquencé pour rendre la convergence de l'optimiseur LEO
fiable sur toute la plage `targetAltitude` ciblée, puis préparer le
support des cibles non circulaires (GTO).

## Phase 0 — Diagnostic & instrumentation (à faire en premier)

Avant de toucher au modèle, instrumenter pour mesurer les pathologies.

### 0.1 Instrumentation des runs

À ajouter dans `MissionOptimizer` / `CMAESTrajectoryOptimizer` (logs de
fin de run) :

- Pour chaque paramètre : distance relative aux bornes
  `(x − lower) / (upper − lower)`. Flag WARN si < 5 % ou > 95 %.
- Δv décomposé : burn 1 utile, burn 1 "gaspillé" (composante
  non-tangentielle), burn 2.
- État de fin de GT : altitude, FPA, v_tan, v_rad. Comparé à l'état
  "Hohmann idéal" pour la cible.
- Nombre de barrières activées (peri, alt min/max) — actuellement
  opaque.

### 0.2 Suite paramétrique de référence

Créer une suite balayant
`[185, 250, 400, 600, 800, 1200, 1500, 2000]` km. Capturer cost final,
nombre d'évaluations, paramètres convergés, Δv. Sert de **baseline
avant chaque modification**.

## Phase 1 — Refonte `GravityTurnConstraints` (physics-derived)

Référence détaillée : `02-altitude-dependent-design.md` §1.

1. **`vTanMin`** dérivé de la vis-viva (cible = atteindre `targetAlt`
   avec marge -5 %).
2. **`apogeeTarget` / `apogeeMax`** : ratios adaptatifs, avec floor
   atmosphérique pour la basse altitude.
3. **`fpaTarget`** : fenêtre `[0°, fpa_at_GT_end + marge]` au lieu d'un
   point dur, dérivable de la géométrie de l'ellipse de transfert.
4. **`transitionTime` upper bound** : `300 + 0.3 · sqrt(targetAlt)` ou
   formule équivalente, pour ne pas saturer à haute altitude.

**Gain attendu** : majeur pour 185 km et > 800 km. C'est la priorité.

## Phase 2 — Refonte `TransferTwoManeuverProblem`

Référence détaillée : `02-altitude-dependent-design.md` §2.

1. **Bornes `α1` / `β1` adaptatives** (de préférence physics-derived,
   à défaut scalées sur `targetAlt`).
2. **Sigma initial cohérent** : `≈ 0.3 · (upper − lower)` calculé
   dynamiquement, plus de constantes `π/8`.
3. **Bornes `t1`, `dt1`** : assert de feasibility, borne `t1` adaptée
   à la période orbitale.
4. **Cost function** : seuil acceptable absolu+relatif, `W_E` adaptatif,
   `periapsisFloor` adaptatif.

**Gain attendu** : consolide le cas 600 km (validé empiriquement) et
débloque la convergence à 185 km.

## Phase 3 — Robustesse de l'optimiseur

### 3.1 Détection automatique des bords

Dans `CMAESTrajectoryOptimizer`, après chaque run :

- Si un paramètre est à < 2 % de sa borne, **élargir la borne d'un
  facteur 1.5 et relancer** (max 2 fois).
- Logger en WARN avec le paramètre concerné.

C'est le filet de sécurité : garantit qu'on ne convergera pas à un
faux optimum contraint, même si on rate un cas dans la conception
adaptative.

### 3.2 Multi-restart adaptatif — warm start Hohmann

Pour les régimes difficiles (très bas, très haut), ajouter une
stratégie "warm start depuis Hohmann analytique" :

- Un seed = solution Hohmann pure
  (`α1=0, β1=0, dt1=Hohmann_dv/massflow, t1≈0`).
- Force au moins un run près de la solution physique, en complément
  des 4 runs d'exploration aléatoires.

### 3.3 Diagnostic d'infaisabilité a priori

Avant de lancer CMA-ES :

- Δv total nécessaire (Hohmann + perte gravité estimée + drag)
  ≤ Δv disponible ?
- L'apogée GT atteignable est-elle compatible avec le burn 1 max ?
- Si non : exception claire avec message "stage X manque de
  propergol" ou "altitude infaisable".

## Phase 4 — Suite de tests de régression

Avant de fusionner quoi que ce soit dans la branche.

### 4.1 Test paramétrique

Étendre `LEOMissionOptimizationTest` (ou créer un nouveau
`LEOAltitudeSweepTest`) en `@ParameterizedTest` sur :

```
[185, 250, 300, 400, 500, 600, 800, 1000, 1200, 1500, 2000] km
```

### 4.2 Critères de succès

- `cost < acceptableCost(targetAlt)` (relatif **et** absolu)
- `|maxAlt − targetAlt| < max(2 km, 0.01 · targetAlt)`
- `|maxAlt − minAlt| < max(5 km, 0.02 · targetAlt)` (circularité)
- **Aucun paramètre saturé aux bornes** (vérifier via les flags
  ajoutés en Phase 0.1).

### 4.3 Tests latitude

Optionnellement : test à différentes latitudes (5°, 28°, 45°, 51°)
pour valider l'indépendance latitude / altitude.

### 4.4 Budget temps

Ces tests peuvent être lents → tag `@Tag("slow")` et exclus du
`./gradlew test` rapide. Lancement nightly ou avant merge.

## Phase 5 — Préparer la voie GTO (sans implémenter)

Identifier les abstractions à introduire pendant les Phases 1-3 sans
casser l'existant :

### 5.1 `OrbitTarget` — généraliser la cible

Nouvelle interface ou record :

```java
record OrbitTarget(double rPeri, double rApo, double inclination) { }
```

Une cible circulaire devient un cas dégénéré (`rPeri == rApo`). Le
`TransferTwoManeuverProblem` calcule alors sa cost contre n'importe
quel target elliptique.

### 5.2 `Burn2Resolver` polymorphique

Actuellement la résolution analytique du burn 2 suppose une
circularisation. Pour une GTO, le burn 2 vise une apogée à 35 786 km,
géométrie totalement différente. Découpler la stratégie :

```
interface Burn2Resolver {
    ResolvedBurn2 resolve(SpacecraftState postBurn1, OrbitTarget target);
}
```

Implémentations : `CircularizationResolver` (existant),
`ApogeeRaiseResolver` (futur GTO).

### 5.3 Nouveau stage `GTOInjectionStage` (futur)

Éventuellement remplacer la circularisation par un single-burn
d'élévation d'apogée. Pas dans ce cycle de travail, mais à anticiper.

### 5.4 Anticipation dans le refactor des Phases 1-3

Paramétrer les classes touchées par `OrbitTarget` plutôt que par
`double targetAltitude`, **sans encore implémenter** le cas
elliptique. Évite un second refactor lourd plus tard.

## Ordre d'exécution recommandé

| Étape | Phase | Risque | Gain attendu |
|---|---|---|---|
| 1 | Phase 0 (instrumentation + baseline) | Aucun | Visibilité immédiate |
| 2 | Phase 1.1 + 1.2 (`vTanMin` + ratios apogée) | Faible | Majeur (185 km, > 800 km) |
| 3 | Phase 2.1 + 2.2 (bornes α1/β1 + sigma) | Faible | Consolide 600 km validé |
| 4 | Phase 2.4 (cost normalization) | Moyen | Débloque 185 km |
| 5 | Phase 3.1 (détection auto bords) | Faible | Filet de sécurité |
| 6 | Phase 1.3 + 1.4 (FPA window, bornes GT) | Faible | Raffinement |
| 7 | Phase 3.2 + 3.3 (warm start, feasibility) | Faible | Robustesse |
| 8 | Phase 4 (suite de tests) | Aucun | Validation |
| 9 | Phase 5 (abstraction `OrbitTarget`) | Moyen | Anticipation GTO |

## Questions ouvertes à trancher avant de coder

1. **Drag** : est-ce que le propagateur d'optimisation
   (`OrekitService.optimizationPropagator`) modélise l'atmosphère ?
   - Si oui à 185 km c'est OK.
   - Sinon il faut soit l'ajouter, soit fixer un floor d'altitude
     minimal de coast plus haut (~200 km).
2. **Critère de "convergence acceptable"** : objectifs de précision
   souhaités ? ±1 % d'altitude, ±0.001 d'eccentricité ? Les seuils
   impactent directement la cost function.
3. **Budget compute** : la suite paramétrique multipliera le temps de
   test par ~10×. Acceptable ou doit-on segmenter (smoke test rapide
   + suite complète optionnelle) ?
4. **GTO targets effectifs** : forme exacte des cibles GTO visées
   (parking + injection, ou single shot) ? Définit l'ampleur du
   refactor `OrbitTarget`.
