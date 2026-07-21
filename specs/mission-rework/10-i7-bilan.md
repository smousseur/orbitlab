# I7 — Bilan (boucle externe de dimensionnement propergol)

> Ce document rend compte de l'implémentation d'I7 (spec [09](09-i7-preparation.md), design d'origine
> [06 §S5](06-launcher-driven-profile-2.md)) : ce qui a été livré, les résultats du premier run
> d'intégration réel, les limites découvertes, et la suite. Les tâches 0, 1, 2 et 4 du plan §6 de la
> spec 09 sont faites ; la tâche 3 (UI) est un chantier séparé.

---

## 1. Rappel de l'objectif

Fermer la boucle « le lanceur dimensionne le propergol » : au lieu de voler les charges heuristiques
de `PropellantBudget` (marge 10 %), **rechercher le chargement minimal qui atteint encore
l'objectif**, par bisection scalaire sur un facteur λ appliqué aux charges heuristiques.

---

## 2. Ce qui a été livré

| Tâche | Contenu | Fichiers |
|---|---|---|
| **0** | maxStep dynamique câblé sur les 6 stages analytiques (invariant late-ignition) | `simulation/mission/stage/Analytic*Stage.java`, helper `MissionStage.burnLimitedMaxStep(state, vehicle)` |
| **1** | `PropellantLoadOptimizer` — bisection sur λ ∈ [0,3 ; 1], tol 2 %, ≤ 10 évals, warm-start, helpers purs `scaledLoads` / `lambdaScaledMask` | [`runtime/PropellantLoadOptimizer.java`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/PropellantLoadOptimizer.java) + `PropellantLoadOptimizerTest` |
| **2** | `MissionLoadEvaluator` — reconstruit la mission avec `loads(λ)`, lance `MissionOptimizer.optimize()`, applique le prédicat de succès | [`runtime/MissionLoadEvaluator.java`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/MissionLoadEvaluator.java) + `MissionLoadEvaluatorTest` |
| **4** | Test d'intégration I7 (lent, opt-in) | [`runtime/PropellantLoadOptimizerIntegrationTest.java`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/runtime/PropellantLoadOptimizerIntegrationTest.java) |
| 3 | Feedback UI (progressbar boucle externe) | **non fait — chantier séparé** |

**Architecture retenue** : le cœur de bisection (`PropellantLoadOptimizer`) est découplé de la
reconstruction de mission via une interface `Evaluator` injectable. Cela rend le TU de la bisection
rapide (évaluateur monotone synthétique, sans propagation) et isole la logique de recherche de la
logique métier. L'évaluateur de production (`MissionLoadEvaluator`) porte la reconstruction + le
prédicat.

**Lancement du test d'intégration** (lent, ~5 min) :
```
./gradlew test --tests "*PropellantLoadOptimizerIntegrationTest" -Dorbitlab.slowTests=true
```
Il est gaté par `@EnabledIfSystemProperty("orbitlab.slowTests"="true")` (skippé par défaut) ; le flag
est forwardé du JVM Gradle vers le JVM de test dans `build.gradle`.

---

## 3. Résultats du premier run d'intégration (LEO 400 km, Falcon Heavy)

**Configuration** : FH, charge utile `EARTH_OBSERVATION_SAT` **10 t**, transfert optimisé (I6),
cible circulaire 400 km, site par défaut (lat 45,96°). Seed fixe 42, 8 évaluations, ~5 min.

**Sortie** :

| λ | charge S2 | résiduel S2 | faisable | note |
|---|---|---|---|---|
| 1,0 (heuristique) | 2844 kg | 284 kg = **9,98 %** | ✅ | baseline |
| 0,3 (borne basse) | 853 kg | — | ❌ | optim échoue (Hohmann Δv 167 > 133 dispo) |
| 0,65 | 1849 kg | **0 %** | ❌ | **flame-out** (DepletionGuard, burn tronqué) — rejeté |
| 0,825 | 2346 kg | 34 % | ✅ | |
| 0,7375 | 2097 kg | 11,8 % | ✅ | |
| 0,694 | 1973 kg | 15,7 % | ✅ | |
| 0,672 | 1911 kg | 4,9 % | ✅ | |
| **0,6609 (λ\*)** | **1880 kg** | 35 % | ✅ | **retenu** |

**Résultat** : `λ* = 0,6609` → **S2 = 1880 kg** au lieu de 2844 kg heuristique, soit **−964 kg
(−34 % de la charge de l'étage dimensionné)**, mission faisable avec 35 % de résiduel de marge, et
**aucune erreur DepletionGuard sur la solution retenue**. Critère de sortie I7 (`Σ load(λ*) <
Σ load_heuristique`) satisfait.

> Au niveau du stack complet le gain n'est que **−0,1 %** (964 kg sur 1,236 M kg) : c'est structurel,
> S1 (1,233 M kg) écrase tout. **Le gain pertinent est celui de l'étage réellement dimensionné (S2),
> −34 %.**

---

## 4. Décisions actées (déviations de la spec 09, assumées)

### 4.1 Masque λ = étage haut dimensionné seulement

**Spec 09 §1** met tous les étages liquides sous λ (hors λ = SOLID + AKM). **Constat du run** : sur
le profil LEO, **S1 n'est jamais largué explicitement** — il est traîné (66 t à vide) jusqu'à ce que
son propergol s'épuise, puis largué implicitement par le calcul de masse. Son plein est déjà « juste
ce qu'il faut » pour hisser sa propre structure. Le scaler abaisserait S1 → l'ascension casse
immédiatement → λ* épinglé à 1, rien récupéré.

**Décision** : `lambdaScaledMask` ne scale que **le dernier étage variable-load** (le seul que
`PropellantBudget` dimensionne avec marge). C'est le seul endroit où il y a du propergol récupérable
sur un stack non-étagé.

### 4.2 Plancher de résiduel = 1 % de la charge de l'étage dimensionné

Itération en deux temps :
1. **Première tentative — plancher sur `residualRatio()` (résiduel / total chargé)** : cassé, le
   dénominateur est écrasé par S1 → ne peut jamais atteindre 1 % (0,28 % à λ=1). La spec §5 (« ≥ 1 %
   par étage, s'appuie sur `totalPropellantResidual()` déjà par-étage ») était **inexacte** : le
   rapport n'expose qu'un résiduel total (celui de l'étage actif final).
2. **Deuxième tentative — supprimer le plancher (objectif seul)** : le run a montré que ça laisse
   l'optimiseur interne pousser S2 à la **vidange exacte** (résiduel 0, `DepletionGuard` ERROR
   « upstream mass accounting is wrong », burn tronqué au replay) tout en « atteignant l'objectif ».
   Solution au bord du couteau, contraire au but réalisme d'I7.
3. **Retenu** : faisabilité = **objectif atteint ET `résiduel ≥ 1 % × charge de l'étage dimensionné`**
   (`scaledLoads[top]`). Le bon dénominateur. À λ=1 : 284 / 2844 = 10 % (sain) ; les solutions
   flame-out (résiduel 0) sont proprement rejetées.

### 4.3 Warm-start cross-λ

Non fait : chaque mission reconstruite warm-starte déjà son CMA-ES interne sur un seed analytique
fiable (spec 09 §5, commit `a8fb39e`), recalculé pour la nouvelle charge. Réinjecter le
`bestVariables` de l'évaluation λ précédente exigerait un hook de seed dans l'optimiseur FH-neutre —
gain marginal, risque non justifié.

---

## 5. Limites connues

### 5.1 Faisabilité seulement quasi-monotone (limite principale)

La bisection suppose la faisabilité **monotone en λ**. La fermeture physique l'est, **mais le plancher
de résiduel ne l'est pas** : le CMA-ES interne optimise la précision d'orbite, **pas la sobriété
propergol**. À un λ donné il peut tomber, de façon stochastique, sur une solution gaspilleuse qui
vide l'étage dimensionné (résiduel 0, sous le plancher) alors qu'une solution plus sobre existe.

Preuve dans le run : λ=0,65 → résiduel 0 (`wasted1` = 257 m/s gâchés) → **infaisable**, tandis que
λ=0,66 → résiduel 35 % (`wasted1` = 7 m/s) → **faisable**. Le résiduel des points faisables saute
(35 %, 4,9 %, 15,7 %, 34 %…).

**Conséquences** :
- λ* reste une charge **réellement faisable avec marge**, mais possiblement **conservatrice** (un
  minimum plus bas peut exister) et **dépendante du seed**.
- Bénin pour le but I7 (« charge réduite qui marche, avec marge »), mais pas un vrai minimum garanti.

### 5.2 Gain « stack » masqué par S1

Le profil non-étagé fait que S1 domine la masse. Le gain sur l'étage dimensionné (−34 %) est réel
mais invisible au niveau du stack (−0,1 %). Tant qu'il n'y a pas de largage de S1, I7 ne peut agir
que sur les étages hauts.

### 5.3 Profil LEO non-étagé

Découvert en cours de route : ni le profil LEO analytique ni le LEO optimisé n'ont de
`StageSeparationStage`. S1 est traîné jusqu'à épuisement puis largué implicitement par la
comptabilité de masse. Ce n'est pas propre à I7 mais ça conditionne fortement ce qu'I7 peut
récupérer.

### 5.4 Bruit dans le budget CMA-ES interne

Certaines évals consomment jusqu'à 40 000 évaluations internes (refinement, consensus non atteint),
d'autres 2 500. Le temps par éval externe varie de ~5 s à ~2 min. Le run total (~5 min) est bien en
deçà des ~15 min anticipés, mais très variable.

---

## 6. Points à améliorer

| Sujet | Piste | Priorité |
|---|---|---|
| **Non-monotonicité (§5.1)** | rendre l'optimiseur interne **propellant-aware** (petit terme de coût pénalisant le `wasted1` / le résiduel nul) → faisabilité monotone, λ* = vrai minimum | haute si on veut un λ* robuste/reproductible |
| **Résiduel par-étage** | exposer un vrai résiduel par-étage dans `MissionPerformanceReport` (au lieu du seul total) → prédicat exact même si l'étage dimensionné n'est pas l'étage actif final | moyenne |
| **Largage de S1 (§5.3)** | ajouter une séparation S1 explicite au profil LEO → S1 dimensionnable, gain stack visible, physique plus juste | moyenne (chantier profil, pas I7) |
| **Robustesse bisection** | tolérer la non-monotonicité (re-éval d'un λ borderline, ou retenir le meilleur faisable sur tous les λ vus) | basse (patch si §5.1 pas traité) |
| **Warm-start cross-λ** | hook de seed dans le CMA-ES pour réinjecter le `bestVariables` de l'éval précédente → moins d'évals internes reparties de loin | basse |
| **Bascule v2** | CMA-ES externe (au lieu de bisection scalaire) si le résiduel post-bisection dépasse 5 % sur un étage (spec 09 §1) | conditionnelle |

---

## 7. Suite immédiate

1. **Tâche 3 — Feedback UI** : progressbar sur la boucle externe (≤ 10 évals × ~5–30 s). L'utilisateur
   doit être prévenu du coût. Chantier séparé, déjà acté.
2. **Décider du sort de la non-monotonicité (§5.1)** : soit on l'accepte (documentée, λ* conservateur
   suffisant), soit on rend l'optimiseur interne propellant-aware. C'est le point le plus structurant
   pour la qualité du résultat I7.
3. **Étendre les scénarios de test** : GEO (AKM hors λ, S2 fait le GTO), autres charges utiles /
   altitudes, pour vérifier que le masque « étage haut seulement » et le plancher tiennent.
4. **Durcissement des tolérances** : `ORBIT_MARGIN_RATIO` (±7 %) conditionne le λ* atteignable — à ne
   resserrer qu'après stabilisation d'I7 (spec 09 §5).

---

## 8. Validation

- TU rapides verts : `PropellantLoadOptimizerTest` (bisection, budget, warm-start, masque),
  `MissionLoadEvaluatorTest` (prédicats objectif + résiduel par-étage-dimensionné), sans propagation.
- Test d'intégration LEO 400 km vert : λ* = 0,6609, S2 −34 %, faisable avec marge, pas de DepletionGuard
  sur la solution retenue.
- FH-neutralité de la tâche 0 confirmée par l'utilisateur (sweeps LEO/GEO inchangés).
