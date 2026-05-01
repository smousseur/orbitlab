# LEO Mission Optimizer — Specs & Roadmap

Ce dossier rassemble l'analyse, la conception et le plan d'évolution du
système d'optimisation de mission LEO (CMA-ES + `MissionOptimizer`), avec
pour objectif de rendre la convergence fiable sur une large plage de
`targetAltitude` (185 km → ~2000 km), et de préparer le terrain pour une
cible GTO.

## Contexte

À l'heure où ces documents sont rédigés :

- La convergence est satisfaisante autour de 400 km.
- À 600 km, la convergence est dégradée : le paramètre `α1` (angle
  in-plane TNW du burn 1) sature à sa borne supérieure π/4.
- Élargir empiriquement la borne `α1` (au-delà de π/4) suffit à débloquer
  600 km, ce qui confirme que le problème est en grande partie un
  bornage trop étroit.
- L'objectif suivant est de couvrir 185 km (LEO basse) à plusieurs
  milliers de km, avec une suite de tests de régression, et préparer le
  passage à des cibles non circulaires (GTO simple depuis une latitude
  basse, type 5°).

## Documents

| Fichier | Contenu |
|---|---|
| `01-convergence-analysis.md` | Diagnostic du cas 400 km (succès) vs 600 km (échec). Décodage des 4 paramètres optimisés, fonction de coût, indices physiques de saturation. |
| `02-altitude-dependent-design.md` | Principes physiques pour rendre `GravityTurnConstraints` et `TransferTwoManeuverProblem` adaptatifs en `targetAltitude` (vis-viva, géométrie de transfert). |
| `03-robustness-roadmap.md` | Plan d'action séquencé (Phase 0 → Phase 5) pour couvrir 185 km → 2000 km, avec instrumentation, refonte des contraintes, robustesse de l'optimiseur, suite de tests, et anticipation GTO. |
| `04-phase0-baseline-analysis.md` | **Spec technique d'exécution.** Synthèse de l'investigation Phase 0 (instrumentation + suite paramétrique). Identifie quatre pathologies (P1 biais géodésique, P2 passation GT→Transfert, P3 bornes transfert, P4 bornes GT non adaptatives), classe les correctifs par niveau de priorité (0 → 5) et référence les formules détaillées des docs 02/03. **Document à suivre pour l'implémentation.** |

## Fichiers de code concernés

- `src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/TransferTwoManeuverProblem.java`
- `src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/GravityTurnProblem.java`
- `src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/problems/GravityTurnConstraints.java`
- `src/main/java/com/smousseur/orbitlab/simulation/mission/optimizer/CMAESTrajectoryOptimizer.java`
- `src/main/java/com/smousseur/orbitlab/simulation/mission/LEOMission.java`
- `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/LEOMissionOptimizationTest.java`
- `src/test/java/com/smousseur/orbitlab/simulation/mission/optimizer/AbstractTrajectoryOptimizerTest.java`
