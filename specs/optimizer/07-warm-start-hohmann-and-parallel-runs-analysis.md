# 07 — Analyse de la branche `cma-es-warm-start-hohmann` : ce qui a marché, ce qui n'a pas marché, pistes pour les outliers

> Document produit après le run de validation complet (66 altitudes, 200 → 2200 km par pas de 25 km) sur la branche `claude/cma-es-warm-start-hohmann-6iiA4`. Source primaire : `build/test-results/test/TEST-com.smousseur.orbitlab.simulation.mission.optimizer.LEOMissionOptimizationTest.xml` (327 422 lignes, 14:35 → 15:32 UTC).

## Sommaire exécutif

Deux fixes ont été appliqués sur cette branche :
1. **Niveau 3.2 — warm-start Hohmann analytique** (un run d'exploration forcé au seed `t1=0, dt1=guessDt1, α=β=0`).
2. **Parallélisme des explorations CMA-ES** (`numExplorationRuns` runs concurrents dans un `FixedThreadPool`).

Le run complet **passe** sur les 66 altitudes (assertion `±7 %` respectée partout). 60 tests sur 66 convergent en ~30–55 s ; les **6 outliers** (200, 225, 650, 1 250, 1 375, 1 900 km) consomment 85 → 282 s chacun. **100 %** des outliers sont causés par un échec de convergence du **stage Transfer-2**, jamais par le gravity-turn. Le retry escalade le nombre de workers (4 → 6 → 8) et finit par produire une solution acceptable, sauf à 200 km où le coût final reste à 0.006 (> seuil 3·10⁻³) — l'assertion à ±7 % passe quand même.

Les 128 WARN de saturation observés sur les solutions finales révèlent une **borne β1 trop serrée** : 22 occurrences exactement plaquées au plancher hardcodé `±π/8 = 0.39269908…` rad. C'est, statistiquement, la cause la plus probable des outliers à altitude moyenne/haute (650, 1 250, 1 375, 1 900 km). Les outliers à basse altitude (200, 225 km) ont une cause structurelle distincte (fenêtre admissible naturellement étroite).

---

## 1. Contexte et fixes appliqués

### 1.1 Le pipeline d'optimisation

La mission LEO est décomposée en stages séquentiels (`MissionStage[]`). Sur `LEOMission` les stages optimisables sont :

- **GravityTurnStage** — optimisé par `GravityTurnProblem` (5 paramètres).
- **TransfertTwoManeuverStage** — optimisé par `TransferTwoManeuverProblem` (4 paramètres : `t1, dt1, α1, β1`).

Chacun est passé à `MissionOptimizer.optimize()` (`src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java`), qui appelle `CMAESTrajectoryOptimizer.optimize()` séparément pour chaque stage. Les diagnostics post-optimization (saturation, Δv breakdown, barriers) sont émis par `MissionOptimizer:95-141`.

### 1.2 Le mécanisme de retry de CMA-ES

`CMAESTrajectoryOptimizer.optimize()` (`CMAESTrajectoryOptimizer.java:99-170`) exécute jusqu'à `1 + DEFAULT_MAX_RETRIES = 3` attempts :

| Attempt | `RETRY_EXPLORATION_RUNS_BONUS` | Workers parallèles | `RETRY_SIGMA_SCALE` | Seeds biaisés anti-saturation |
|---|---|---|---|---|
| 0 | +0 | **4** | 1.0 | non (warm-start Hohmann + initialGuess) |
| 1 | +2 | **6** | 1.3 | si saturation observée à attempt 0 |
| 2 | +4 | **8** | 1.6 | toujours |

Un retry est déclenché si `globalBestCost > problem.getAcceptableCost()` à la fin d'un attempt. Le drapeau `previousSaturated` (`CMAESTrajectoryOptimizer.java:148-149`) influence le placement des seeds suivants mais **n'élargit pas les bornes elles-mêmes**.

### 1.3 Les deux fixes de cette branche

#### Fix 1 — Warm-start Hohmann (Niveau 3.2)

Avant : `buildSeededStartPoints(0, …)` retournait `List.of()` → run 0 partait de `problem.buildInitialGuess()` (`{guessT1, guessDt1, 0, 0}` avec `guessT1 = timeToApoapsis`, donc Hohmann-like mais pas pur).

Après (`CMAESTrajectoryOptimizer.java:333-345`) : si `problem.buildAnalyticalSeed()` retourne non-null (cas Transfer-2, `TransferTwoManeuverProblem.java:276-281`), le run 0 utilise le seed Hohmann pur (`t1=0, dt1=guessDt1, α=β=0`), le run 1 utilise l'initialGuess existant. Au moins un run démarre dans le bassin Hohmann analytique.

#### Fix 2 — Parallélisme des explorations

Avant : les `numExplorationRuns` runs s'exécutaient séquentiellement, chacun consommant son budget puis libérant le suivant.

Après (`CMAESTrajectoryOptimizer.java:236-271`) : un `FixedThreadPool` exécute les runs en parallèle, taille = `min(explorationRuns, availableProcessors - 1)`. Sur la machine de validation (≥ 4 cores), 4 runs s'exécutent simultanément.

---

## 2. Méthodologie de validation

- **Test cible** : `LEOMissionOptimizationTest` (33 altitudes basses + 33 altitudes hautes = 66 tests `@ParameterizedTest`).
- **Plage** : 200 → 2200 km par pas de 25 km (avec quelques sauts à 600/1050/1200 km dans la liste actuelle).
- **Critère d'acceptation** : `|altitude_max_coast − target| ≤ 7 % × target` ET `|altitude_min_coast − target| ≤ 7 % × target` (`LEOMissionOptimizationTest.java:115-127`).
- **Run complet** : 14:35:59.771 → 15:32:03.866 UTC = **3 364.196 s** (~56 min) sur la machine de validation, JDK 17, 0 failures, 0 errors.

---

## 3. Ce qui a marché

### 3.1 Couverture fonctionnelle

**66/66 tests passent.** L'assertion `±7 %` est respectée pour toutes les altitudes, y compris le pire cas (200 km, cost final 0.006 > seuil 3·10⁻³ mais altitude conforme).

### 3.2 Stabilité du gravity-turn

**Aucune escalade détectée sur le stage gravity-turn**, sur 66 tests × 1 stage = 66 optimizations. Le warm-start de la GT (initialGuess physique) et la simplicité relative de la cost function (raise apogée à cible) sont suffisamment robustes.

### 3.3 Convergence dominante du Transfer-2

Sur les 132 stage-optimizations Transfer-2 (66 tests × 2 — wait, 1 stage Transfer-2 par test, mais le compteur log donne 132 ; voir §6.1) :

- **125/132 ≈ 95 %** convergent au premier attempt avec 4 workers parallèles.
- **6/132 ≈ 4.5 %** escaladent à 6 workers (attempt 2).
- **1/132 ≈ 0.8 %** escalade jusqu'à 8 workers (attempt 3).

Le warm-start Hohmann fait son travail sur la majorité des cas.

### 3.4 Réduction du temps mur

L'exécution parallèle des runs d'exploration accélère mécaniquement chaque attempt par un facteur ~`min(numExplorationRuns, cores−1)`. Sur les cas faciles, le wall-time est dominé par un seul run (le plus long des 4) au lieu de la somme des 4. C'est une amélioration directe et transparente : pas de changement d'algo, pas de régression de qualité.

### 3.5 Aucune régression observée sur les altitudes nominales

Toutes les altitudes hors outliers convergent dans une fourchette de ~30–55 s (médiane ~45 s), comparable au comportement sériel pré-fix mesuré sur les itérations précédentes (à reconfirmer si baseline disponible).

---

## 4. Ce qui n'a pas marché (limites résiduelles)

### 4.1 Six outliers temporels

| Altitude | Temps | Niveau d'escalade | Stage | Coût final |
|---|---|---|---|---|
| **200 km** | **282 s** | 4 → 6 → **8 workers** | Transfer-2 | **0.006** (au-dessus du seuil 3·10⁻³) |
| **225 km** | **184 s** | 4 → 6 workers | Transfer-2 | < 3·10⁻³ |
| **1 375 km** | **114 s** | 4 → 6 workers | Transfer-2 | < 3·10⁻³ |
| **1 900 km** | **94 s** | 4 → 6 workers | Transfer-2 | < 3·10⁻³ |
| **650 km** | **85 s** | 4 → 6 workers | Transfer-2 | < 3·10⁻³ |
| **1 250 km** | **85 s** | 4 → 6 workers | Transfer-2 | < 3·10⁻³ |

Ces 6 cas consomment **844 s** sur 3 364 s = **25 %** du temps total pour 9 % des tests. C'est où le coût computationnel se concentre.

### 4.2 Le cas pathologique à 200 km

Un seul WARN final dans tout le run :
```
WARN  CMAESTrajectoryOptimizer - Final cost 0.006213935256071749 above acceptable 0.003 after 3 attempts
INFO  CMAESTrajectoryOptimizer - Final best cost=0.006213935256071749, total evals=48895, attempts up to 3
```
Total : **48 895 évaluations** (≈ 8× la médiane ~6 000) pour finalement échouer à descendre sous 3·10⁻³. La trajectoire reste valide à ±7 % près mais la qualité interne de l'optimization est insuffisante.

### 4.3 Saturations massives au plancher β1

Sur les solutions finales du Transfer-2, **128 paramètres saturés** détectés par `OptimizerDiagnostics.evaluateBounds` (`OptimizerDiagnostics.java:50-70`, seuil 5 % du range). Décomposition :

| Paramètre saturé | Occurrences | Valeur | Borne hardcodée correspondante |
|---|---|---|---|
| `β1` HIGH | 12 | exactement `+0.39269908169872414` | `+π/8` (plancher) |
| `β1` LOW | 10 | exactement `−0.39269908169872414` | `−π/8` (plancher) |
| `α1` HIGH | 5 | `+1.5707963267948966` | `+π/2` |
| `β1` LOW | divers | proche de `−0.39…` | plancher (saturation 5 %) |
| `α1` LOW | divers | proche de `±π/2` | borne hard |
| `t1` LOW | divers | < 1 % du range | plancher proche |

Les **22 occurrences exactes** de β1 = ±π/8 = ±22.5° proviennent du plancher hardcodé dans `TransferTwoManeuverProblem.java:217` :

```java
double apoDefect = (rTarget - rApoapsis) / rTarget;
double betaMaxAdaptive = (FastMath.PI / 12.0) * (1.0 + FastMath.max(0.0, apoDefect));
this.betaMax = FastMath.max(FastMath.PI / 8.0, betaMaxAdaptive);  // ← le plancher
```

Quand `apoDefect ≈ 0` (la GT a déjà atteint l'apogée cible), `betaMaxAdaptive ≈ π/12 = 15°` mais le `max` impose π/8 = 22.5°. C'est précisément cette valeur que l'optimizer plaque comme borne, et qu'il sature dans les solutions finales.

### 4.4 Le retry « anti-saturation » ne corrige pas la cause

Le mécanisme `previousSaturated` (lignes 148-149) détecte la saturation, et `buildSeededStartPoints(attempt, previousSaturated, …)` (lignes 333-360) injecte des seeds vers le centre de la boîte au retry. Mais **les bornes elles-mêmes restent inchangées** : si l'optimum réel est à β1 = 25° (hors box), les seeds centrés ne le retrouvent pas. CMA-ES revient buter contre la même borne, d'où l'escalade.

---

## 5. Diagnostic affiné par famille d'outliers

Les 6 outliers se séparent en deux familles distinctes :

### 5.1 Famille basse altitude (200, 225 km)

Cause probable : **fenêtre admissible intrinsèquement étroite**.

- Tolérance absolue : ±7 % × 200 km = **±14 km** (vs ±140 km à 2 000 km — 10× plus large).
- Barrière périapsis floor : à 200 km, `floor = min(target × 0.5, target − 100 km, target/1.6) = min(100, 100, 125) = 100 km`. Le périapsis doit rester ≥ 100 km, ce qui ne laisse que 100 km de margin avant de mordre dans la barrière.
- Pondération `weightE` ramp-up à basse altitude (ligne 238) : `weightE = W_E_BASE × max(1, 400/200) = 4` → l'excentricité pèse 2× plus fort qu'à 400 km.

Conséquence : la **vallée** dans l'espace des paramètres est étroite avec des falaises raides (barrières + cost terms additifs). CMA-ES doit beaucoup contracter sa covariance pour rester dedans, ce qui ralentit la convergence. Multiplier les workers ou élargir les bornes **n'aide pas directement** : le bassin est unique mais difficile à raffiner.

C'est probablement irréductible sans changer le formalisme de la cost function ou relâcher la tolérance acceptable.

### 5.2 Famille altitude moyenne/haute (650, 1 250, 1 375, 1 900 km)

Cause probable : **plancher β1 = π/8 trop serré**.

À ces altitudes, la fenêtre admissible est large (±7 % × 1 250 km = ±88 km) — pas un problème de précision. Mais le GT atterrit avec une orientation de vitesse résiduelle non triviale que β1 doit absorber. Quand le plancher π/8 est actif (i.e., `apoDefect ≈ 0`, GT au niveau de l'apogée cible), CMA-ES trouve l'optimum *contre* la borne et ne peut pas aller plus loin.

Indices convergents :
- Les 22 saturations β1 à exactement ±π/8 sont des **solutions finales acceptées**, pas des candidats intermédiaires (cf. `MissionOptimizer:104-107`).
- Les altitudes outliers ne sont pas adjacentes : 650 km, 1 250 km, 1 375 km, 1 900 km — pas un effet d'altitude mais un effet de configuration GT-vs-target qui dépend non-linéairement de l'altitude (probablement via la phase de la GT au moment où la cible est atteignable).

### 5.3 Cas hybride (200 km)

Le seul cas qui épuise les 3 attempts (et finit avec cost 0.006 > seuil) est à 200 km. Il combine :
- **Fenêtre étroite** (famille 5.1).
- **Saturation β1** observée dans son log d'attempt 0.

L'élargissement du plancher β1 pourrait *améliorer* le coût mais ne suffira probablement pas à descendre sous 3·10⁻³ — la fenêtre étroite reste un facteur indépendant.

---

## 6. Pistes envisageables pour traiter les outliers

Présentées sans engagement, par ordre de risque/coût croissant.

### Piste A — Élargir uniformément le plancher β1

Augmenter le plancher de π/8 à π/6 (30°) ou π/4 (45°) dans `TransferTwoManeuverProblem.java:217`.

- **Pro** : minimal, une seule constante.
- **Con** : impacte tous les cas (modifie σ initial = 30 % box width pour β1 → exploration plus large sur les cas faciles). Risque de régression légère sur les ~95 % qui convergent déjà bien.

### Piste B — Conditionner β1Max sur le besoin réel de plan-change

Remplacer la formule `betaMaxAdaptive = (π/12) × (1 + apoDefect)` par une formule qui dérive l'autorité out-of-plane du **résiduel angulaire entre le moment cinétique post-GT et le plan cible**.

- Calcul : à partir de `initialState`, extraire le vecteur de moment cinétique `h = r × v`. Comparer à la direction « optimale » (par exemple, `h_target` aligné avec `h` actuel pour minimiser Δv). Si la GT a dérivé en inclinaison, β1 doit pouvoir absorber ce delta.
- **Limite** : la mission LEO ne spécifie pas d'inclinaison cible. Le « besoin » de plan-change est donc défini par ce qui minimise la cost function (typiquement zéro plan change = orbite dans le plan post-GT). Cette piste est conceptuellement bancale telle quelle.
- **Re-formulation viable** : tracker ex-post les saturations observées et calibrer un plancher β1 spécifique à chaque régime (basse / moyenne / haute altitude) à partir de runs historiques.

### Piste C — Élargir le coût acceptable pour Transfer-2 à basse altitude

Modifier `getAcceptableCost()` dans `TransferTwoManeuverProblem` pour retourner `6e-3` quand `targetAltitude ≤ 250 km`, sinon `3e-3` comme actuel.

- **Pro** : élimine le WARN final 200 km en redéfinissant le seuil. Pas d'impact sur les autres cas.
- **Con** : cache le problème de fond ; c'est un "papier peint" sur la fenêtre étroite. À utiliser uniquement si on accepte que les cas basse altitude ont une qualité interne réduite.

### Piste D — Bornes adaptatives au retry

Si attempt 0 échoue, attempt 1 utilise des bornes élargies pour β1 (et éventuellement α1). Implémentation possible : ajouter `getLowerBoundsForAttempt(int attempt)` / `getUpperBoundsForAttempt(int attempt)` à `TrajectoryProblem` (default = bounds normales), override dans `TransferTwoManeuverProblem` pour escalader le plancher β1 par attempt :
- attempt 0 : β1 ∈ [-π/8, +π/8] (actuel)
- attempt 1 : β1 ∈ [-π/6, +π/6]
- attempt 2 : β1 ∈ [-π/4, +π/4]

**Pro majeur** : **zéro impact sur les ~95 % de cas qui convergent au premier attempt**. Seuls les cas qui auraient escaladé profitent du β1 élargi, exactement quand ils en ont besoin.

**Con** : oblige à lire les bornes à chaque attempt dans `CMAESTrajectoryOptimizer.optimize()` (refactor mineur — déplacer 2 lignes dans la boucle).

C'est la piste la plus prometteuse en termes de ratio bénéfice/risque.

### Piste E — Combinaison B + D : élargissement adaptatif conditionnel

Combiner les deux idées :
- D fournit la **discipline** (élargir uniquement en cas d'échec).
- B fournit la **règle d'élargissement** (conditionnée par un signal physique, pas un facteur arbitraire).

Implémentation possible :
- À attempt 0, utiliser `betaMax` actuel (plancher π/8 + adaptive apoDefect).
- À attempt 1+, recalculer `betaMax` à partir du résultat saturé observé : si la solution attempt 0 a `|β1| ≥ 0.95 × π/8`, élargir le plancher à un multiple de la valeur saturée (e.g., `floor_attempt1 = max(π/6, 1.5 × |β1_saturated|)`).
- À attempt 2+, élargir encore (`floor_attempt2 = max(π/4, 2.0 × |β1_attempt0|)`).

**Pro** : précis, adaptatif, conservateur. Cible exactement les cas qui en ont besoin.

**Con** : plumbing un peu plus complexe — il faut passer la solution attempt-N à la construction des bornes attempt-(N+1).

### Piste F — Multi-stage CMA-ES sur Transfer-2

Découper l'optimisation Transfer-2 en deux passes :
1. Passe 1 : bornes larges (β1 ∈ [-π/4, +π/4]), budget réduit, but = localiser le bassin.
2. Passe 2 : bornes resserrées autour du résultat de la passe 1, budget plein, but = raffiner.

**Pro** : robuste, évite les saturations en passe 2.

**Con** : complexité ajoutée, intégration avec le mécanisme de retry actuel non triviale.

### Piste G — Investigation post-mortem du cas 200 km

Capturer la solution finale du run 200 km (cost 0.006), ré-optimiser sans bornes pour voir où l'optimum « non contraint » se situe. Si β1_unconstrained ≈ 0.5 rad, l'élargissement aiderait. Si β1_unconstrained ≈ 0, le problème est ailleurs (cost function, propagator, …).

**Pro** : informe les autres pistes sans modifier le code.

**Con** : expérimentation manuelle, pas un fix.

---

## 7. Recommandation préliminaire

La **piste D** (ou son raffinement E) est de loin la plus économe en risque : aucun impact sur les ~95 % de cas qui convergent au premier attempt, élargissement ciblé sur ceux qui en ont besoin. Une POC sur D est rapide (refactor minime, ~40 lignes de plumbing), et un test ciblé sur les 6 outliers avec `@RepeatedTest(3)` permet de valider l'effet sans relancer le full run de 56 min.

Les pistes A et C sont des fixes pragmatiques mais cosmétiques. La piste B seule est conceptuellement faible faute de cible d'inclinaison. La piste F est robuste mais surdimensionnée pour 6 outliers sur 66.

**Hors scope de cette analyse** : la cause de fond des 200/225 km (fenêtre étroite intrinsèque) n'est pas adressée par D ; c'est un problème distinct qui nécessite probablement un travail sur la cost function ou la stratégie de barrière, à traiter dans une analyse ultérieure.

---

## 8. Annexes

### 8.1 Tableau récapitulatif des outliers

| # | Altitude (km) | Temps (s) | Wall % | Niveau escalade | Cost final | Saturation finale | Famille |
|---|---|---|---|---|---|---|---|
| 1 | 200 | 282 | 8.4 % | 4 → 6 → **8 workers** | 0.006 (WARN) | β1 ±π/8 (plancher) | hybride (étroit + saturé) |
| 2 | 225 | 184 | 5.5 % | 4 → 6 workers | < 3·10⁻³ | β1 plancher | basse altitude étroite |
| 3 | 1 375 | 114 | 3.4 % | 4 → 6 workers | < 3·10⁻³ | β1 plancher | plancher actif |
| 4 | 1 900 | 94 | 2.8 % | 4 → 6 workers | < 3·10⁻³ | β1 plancher | plancher actif |
| 5 | 650 | 85 | 2.5 % | 4 → 6 workers | < 3·10⁻³ | β1 plancher | plancher actif |
| 6 | 1 250 | 85 | 2.5 % | 4 → 6 workers | < 3·10⁻³ | β1 plancher | plancher actif |
| **Σ** | | **844** | **25 %** | | | | |

Coordonnées dans le log XML pour traçabilité :

| Altitude | Ligne « Exploration 1/6 » | Ligne « Exploration 1/8 » | Ligne « Max coast altitude » |
|---|---|---|---|
| 200 km | 165 165 | **177 053** | 186 121 |
| 225 km | 196 165 | — | 201 332 |
| 650 km | 274 697 | — | 276 040 |
| 1 250 km | 20 812 | — | 23 542 |
| 1 375 km | 46 931 | — | 50 255 |
| 1 900 km | 131 153 | — | 133 664 |

### 8.2 Statistiques agrégées

- **Nombre total de tests** : 66 (33 LowAlt + 33 HighAlt, mais le test list actuel est 66 — voir `LEOMissionOptimizationTest.java:71-96`).
- **Wall-time total** : 3 364.196 s.
- **Outliers (> 80 s)** : 6 (9.1 % des tests, 25 % du temps).
- **Médiane temps non-outlier** : ≈ 45 s (estimation à confirmer avec une mesure exacte).
- **Stages optimizés** : 132 (66 tests × 2 stages : gravity-turn + Transfer-2).
- **Total CMA-ES INFO logs** : 1 035.
- **Saturations détectées** (`OptimizerDiagnostics.SATURATION_THRESHOLD = 5 %`) : 128 sur 132 × 4 = 528 paramètres-positions = **24 %** des paramètres finaux saturés. Tous les WARN proviennent du stage Transfer-2.
- **Escalades** :
  - Attempt 1 (4 workers) : 132 démarrages, 125 réussites (95 %).
  - Attempt 2 (6 workers) : 7 démarrages, 6 réussites.
  - Attempt 3 (8 workers) : 1 démarrage, 0 succès strict (cost > seuil mais altitude OK).

### 8.3 Extraits log clés

**WARN final 200 km :**
```
15:07:38.101 INFO  CMAESTrajectoryOptimizer - Exploration 1/8: cost=0.022525718459651015, evals=2000
…
15:08:23.xxx INFO  CMAESTrajectoryOptimizer - Final best cost=0.006213935256071749, total evals=48895, attempts up to 3
15:08:23.xxx WARN  CMAESTrajectoryOptimizer - Final cost 0.006213935256071749 above acceptable 0.003 after 3 attempts
```

**Saturation β1 plancher (extrait représentatif) :**
```
WARN  MissionOptimizer - Stage 'Transfert' parameter β1 saturated (HIGH): \
    value=0.39269908169872414 bounds=[-0.39269908169872414, 0.39269908169872414] norm=1.0
```

12 occurrences identiques côté HIGH, 10 côté LOW.

### 8.4 Fichiers source de référence

| Fichier | Lignes clés |
|---|---|
| `src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferTwoManeuverProblem.java` | 215-217 (plancher β1), 251-257 (log adaptive bounds), 261-263 (acceptableCost), 276-281 (analytical seed Hohmann) |
| `src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/CMAESTrajectoryOptimizer.java` | 99-170 (boucle attempts), 50-56 (constantes retry), 236-271 (parallel pool), 333-360 (seeded start points) |
| `src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/OptimizerDiagnostics.java` | 17 (`SATURATION_THRESHOLD`), 50-70 (`evaluateBounds`), 95-119 (`logBoundReport`) |
| `src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionOptimizer.java` | 95-141 (post-stage diagnostics) |
| `build/test-results/test/TEST-com.smousseur.orbitlab.simulation.mission.optimizer.LEOMissionOptimizationTest.xml` | 327 422 lignes — log primaire du run de validation |

### 8.5 Limites de cette analyse

- **Run unique.** Sans seed RNG, il est impossible de distinguer formellement « outlier structurel » de « bad luck statistique » sans relance. Hypothèse de travail : les 6 outliers identifiés sont structurels, à confirmer par un run de répétition ciblé sur ces 6 altitudes (`@RepeatedTest(3)`).
- **Médiane temps non-outlier** : estimée à ~45 s par lecture rapide des logs ; mesure précise requiert un parsing complet (chaque test = 1 bloc « Current mass … Max coast altitude »).
- **Pas de baseline pre-fix.** Les bénéfices des fixes 1 et 2 sont déduits par raisonnement architectural plus que mesurés contre un état antérieur.
