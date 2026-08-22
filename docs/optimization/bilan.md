**23 min 47 s.** La cible est tenue.

## Le run

12:13:02 → 12:36:49, contre 35 min 60 s au run précédent : **−34 %**. Ma projection disait 21 min 35 s, l'écart de deux minutes tient à de la variance machine (la même passe de raffinement de 1 598 évals a pris 103 s ici contre 69 s au run d'avant).

| λ | évals transfert | tentatives | verdict |
|---|---|---|---|
| 1,0 | 7 998 | 1 — *converged in its box* | feasible, 891 kg (25,1 %) |
| **0,65** | 14 773 | **3 — retries conservés** | feasible, 222 kg (9,6 %) |
| 0,5625 | 5 009 | 1 | feasible, 96 kg (4,8 %) |
| 0,51875 | 7 998 | 1 | infeasible, 1 kg |
| 0,540625 | 4 985 | 1 | feasible, 51 kg (2,6 %) |
| 0,5296875 | 5 001 | 1 | **feasible, 27 kg (1,5 %) → λ\*** |
| 0,5096875 (passe 2) | 6 291 | 1 | infeasible, 0 kg |

Total : **52 055 évals contre 89 244** — −42 %.

## La règle a fait exactement ce pour quoi elle est écrite

Elle a coupé six retries stériles, et **elle ne s'est pas déclenchée à λ=0,65** — le seul λ dont les retries étaient productifs, et le seul dont la cascade d'attempt 0 était plate :

```
Refinement 1/3: cost=0.034874966798933836   ← identique à l'exploration, plateau
Retry 1/2 triggered …
Refinement 1/3: cost=0.03157182081226329    ← plate à nouveau, plateau
Retry 2/2 triggered …
Refinement 1/3: 0.006065769306782445        ← ça descend enfin
Attempt 3 converged in its box
```

Trois tentatives complètes là où il en fallait trois, une seule partout ailleurs. Le discriminant mesuré sur le run précédent s'est vérifié sur celui-ci sans exception.

## Bonus non attendu

`λ* = 0,5297` → **1 883 kg, soit −47,0 %** de la charge de l'étage, contre 1 922 kg (−45,9 %) au run précédent. **39 kg de mieux.** En supprimant le retry, l'évaluation à λ=0,5296875 retient la solution d'attempt 0 (résidu 27 kg, 1,5 %) là où le run précédent la remplaçait par une variante à 4 kg qui tombait sous le plancher. Un retry stérile n'était donc pas seulement du temps perdu : il pouvait dégrader le verdict.

Cumul depuis le point de départ : **~2 600 kg → 1 883 kg** sur S2, soit ~700 kg récupérés par la barrière et 39 de plus par la règle.

## Bilan des quatre changements

| | validé par |
|---|---|
| barrière d'extinction | test unitaire + 3 runs applicatifs + FH 400 km (coût mesuré : −38 kg, −0,26 %) |
| bornes par tentative | test mock + disparition des `evals=0` au runtime |
| `refine()` retiré | 2 runs applicatifs |
| règle du retry | test mock + **ce run**, 5 déclenchements corrects et 1 abstention correcte |

Suites rapides vertes, plus `LEOMissionOptimizationTest` (9), `GEOMissionOptimizationTest`, `AscentBaselineN2Test`, `Ariane62`, `Meo`, et `LEOMissionOptimizedTransferTest` (3, derrière `-Dorbitlab.slowTests=true`).

---

# Suite : décomposition du plancher orbital

## L'instrument

`TransferProblem.CostBreakdown` expose les onze contributions du coût, poids déjà appliqués. Elle
n'est **pas** une copie des formules : le calcul a été déplacé dans `breakdown(SpacecraftState)`
et `computeCost` délègue.

```java
public double computeCost(SpacecraftState state) {
  …
  return breakdown(state).total();
}
```

`diagnoseBarriers` recopie déjà ses formules à côté de celles de `computeCost` — deux exemplaires
qui peuvent diverger, exactement ce qui rend un diagnostic douteux au moment où on en a besoin. Le
test `TransferProblemCostTest.theCostBreakdownSumsToTheCostItDecomposes` verrouille l'invariant
(somme des termes = coût, à 1e-12 relatif), et `MissionOptimizer` journalise la ligne à côté des
diagnostics Δv et barrières.

## Ce que la décomposition montre

Run 15:05, λ=1,0, cible 553 138 m. Objectif = 2,6388e-3 :

| terme | valeur | part |
|---|---|---|
| **apoAbs** | 1,2615e-3 | **47,8 %** |
| **apoRel** | 6,1845e-4 | **23,4 %** |
| periAbs | 4,8776e-4 | 18,5 % |
| periRel | 2,6570e-4 | 10,1 % |
| incl | 4,24e-6 | 0,16 % |
| ecc | 1,21e-6 | 0,05 % |
| **vRad** | **8,95e-13** | ~0 |

**Apogée 71 %, périgée 29 %, tout le reste 0,2 %.** Trois hypothèses meurent d'un coup : ce n'est
ni l'excentricité, ni l'inclinaison, ni la vitesse radiale — 9e-13, autrement dit la
circularisation déterministe à l'apoapside est *exacte*. Le plancher n'a rien à voir avec la forme
de l'orbite.

En inversant les poids, les deux échelles (relative et absolue) retombent au mètre près sur le même
écart, ce qui valide aussi l'instrument :

```
apoAbs  → Δapogée = 50 000 · √(1,261478e-3 / 0,05)   = 7 941,9 m
apoRel  → Δapogée = 553 138 · √(6,184485e-4 / 3)     = 7 941,9 m
periAbs → Δpérigée = 50 000 · √(4,877641e-4 / 0,15)  = 2 851,2 m
periRel → Δpérigée = 553 138 · √(2,656997e-4 / 10)   = 2 851,2 m
```

Second constat, indépendant : **`apoAbs` est le premier terme (47,8 %) alors que l'erreur relative
n'est que de 1,4 %.** `ABS_ERR_SCALE = 50 km` transforme 7,9 km en 16 % d'échelle contre 1,4 % en
relatif. Le terme « absolu », ajouté *« to preserve sensitivity at high altitudes »*, est ce qui
porte le plancher **en LEO** — l'inverse de son intention.

## L'hypothèse de la cible — testée et démentie

**L'idée.** `TransferProblem` appliquait sa propre compensation J2 (`J2·RE²/2r·(1−1,5sin²i)` =
3 138 m) là où `FlownBandAim.closedFormOffset(a)` — la règle que `AnalyticTrimBurnStage` vole
réellement — donne `3·J2·RE²/2a` = 9 535 m. Un tiers de l'offset, plus un facteur d'inclinaison que
la règle de référence n'a pas. Le transfert semblait donc pénalisé pour atterrir là où l'étage
suivant l'emmenait de toute façon.

**Le test.** Cible relevée à 559 535 m, run 15:23 (log joint) :

| | cible 553 138 | cible 559 535 |
|---|---|---|
| objectif | 2,6388e-3 | **2,6951e-3** — pire |
| total | 4,0627e-3 | **4,3588e-3** — pire |
| Δapogée | 7 941,9 m | **8 055,8 m** |
| Δpérigée | 2 851,2 m | **2 889,5 m** |
| orbite moyenne livrée | 559 649 × 559 722 (e=5,3e-6) | **559 712 × 566 006 (e=4,5e-4)** |

**Le fait qui compte : les écarts apsidaux suivent la cible un pour un.** Un déplacement de cible de
+6 397 m a déplacé l'apogée atteint de +6 511 m et le périgée de +6 436 m. En relatif l'écart est
**constant à 0,3 % près** — 1,4358 % → 1,4397 % sur l'apogée, 0,5155 % → 0,5164 % sur le périgée.

Le plancher est donc un **biais relatif fixe, pas un défaut de visée** : l'optimiseur vise juste, la
trajectoire suit la cible, et le résidu reste. Aucune cible ne peut l'absorber. Le raisonnement « à
trajectoire figée » qui prédisait −53 % était faux par construction.

Pire, le déplacement a créé la double compensation qu'il prétendait supprimer :
`AnalyticTrimBurnStage` centre sur la requête **brute**, donc pré-lever la cible du transfert de
`a·f` empile `a·f` deux fois — l'orbite moyenne livrée y a perdu sa circularité.

**Changement annulé.** `TransferProblem` a retrouvé sa règle locale, avec un commentaire qui
enregistre la tentative. Le test qui encodait le contrat réfuté a été supprimé : un test vert qui
affirme une conception fausse est pire que pas de test. `CostBreakdown` reste — c'est l'instrument,
et c'est lui qui a permis de tuer l'hypothèse en un run.

## Correction au bilan précédent

La liste « ce qui reste ouvert » du premier bilan comptait comme défaut : *« l'orbite moyenne livrée
est ~8 km au-dessus de la cible »*. **C'est faux.** Ces ~8 km sont `a·f`, le critère de centrage de
`FlownBandAim` : sous J2 aucune orbite n'est plate, et pour que la bande *volée* soit centrée sur la
requête, l'orbite **moyenne** doit siéger `a·f` au-dessus. Vérification :

```
a       = 6 378 137 + 558 781           = 6 936 918 m
f       = 1,5 × 1,08263e-3 × (RE/a)²    = 1,37277e-3
a·f                                     = 9 523 m   (mesuré : 8 781 m)
lift de l'aim (= 2·a·f)                 = 19 046 m  (journalisé : 19 211 m)
```

Sous les ~600 m de résidu propre d'Eckstein-Hechler que la javadoc de `FlownBandAim` cite
elle-même. `MissionOptimizer.logAchievedOrbit` le dit d'ailleurs explicitement : *« the mean line of
an insertion aimed circular is not circular, and the gap is not a targeting miss »*.

## Pistes d'amélioration

1. **Osculateur contre moyen — l'hypothèse survivante.** Le coût grade des apsides *osculatrices*
   contre une cible qui est implicitement une altitude en éléments *moyens*. Sous J2 l'écart entre
   les deux est structurel et proportionnel, ce qui collerait avec un biais relatif constant
   (1,4358 % / 0,5155 %, rapport apogée/périgée = 2,79, lui aussi constant — une signature de
   *forme*, pas d'altitude). Si c'est ça, ça ne se corrige ni en bougeant la cible (démontré
   ci-dessus) ni en changeant les poids : il faut grader les éléments moyens. Coût probable : une
   conversion Eckstein-Hechler par évaluation, à chiffrer avant de s'engager.

2. **Recaler `acceptableCost` sur le plancher mesuré.** Variante mieux fondée de la « voie B ». Le
   seuil de 3e-3 a été calibré avant le terme I7, sur un coût purement orbital ; le plancher réel
   est de ~2,64e-3 orbital + ~1,4e-3 propergol. Tant qu'il n'est pas atteignable, le `WARN Final
   cost … above acceptable` tombe sur chaque transfert et aucun arrêt « Target reached » ne peut
   jouer. Attention : relever le seuil sans discernement laisserait repasser l'extinction sèche —
   c'est le bug que la barrière vient de corriger. La forme sûre est de comparer la *partie
   orbitale* au seuil, pas le total.

3. **`ABS_ERR_SCALE = 50 km` est mal dimensionné pour la LEO.** Il fait de `apoAbs` le premier terme
   du coût sur une erreur qui ne vaut que 1,4 % en relatif. À réexaminer en même temps que la piste
   1, dont il est le multiplicateur.

4. **`t1` saturé à 100 %** sur cinq transferts sur sept (`norm=1.0` exactement). Mur de boîte :
   l'optimiseur veut allumer plus tard qu'une demi-période et `t1Max = 0,5 × période` l'en empêche.
   Déplacement de borne, donc à traiter isolément et avec re-mesure des références — les bornes
   CMA-ES ne sont pas des contraintes, les déplacer renormalise toute la recherche.

5. **Exemption « boîte élargie » de la règle du retry.** Trou connu, non couvert : si la cascade
   descend *et* que le problème élargit la boîte au retry (saturation β1 réelle), la règle saute un
   retry qui avait du terrain neuf. La combinaison ne s'est produite sur aucun des sept λ mesurés —
   les cinq `anti-saturation=true` étaient du `t1` saturé, pas du β1, et `t1` n'a pas de relaxation.

6. **`ERROR DepletionGuard - [Trim burn] … upstream mass accounting is wrong`** se déclenche sur les
   λ normalement rejetés. Rien n'est *wrong* : la charge est trop petite, c'est le verdict attendu.
   Un ERROR avec ce message sur un rejet de routine fait chercher un bug qui n'existe pas.

## Ce qu'il ne faut pas refaire

- **Déplacer la cible du transfert pour réduire le plancher.** Mesuré, démenti, annulé (§ ci-dessus).
- **Remplacer le transfert CMA-ES par l'analytique dans le sweep λ.** Le prédicat de faisabilité est
  le plancher de résidu de l'étage dimensionné, et c'est précisément ce que les deux transferts ne
  partagent pas (5,7 % contre 43,6 % à charge égale) : le sweep rendrait λ ≈ 0,95 et PRECISE
  livrerait les charges de BALANCED.

## État du dépôt

Commit `b1c3542 "Payload optimizer fixes"` porte les quatre changements du premier bilan. Restent
non commités : `TransferProblem` (la décomposition seule, la cible étant revenue à sa règle locale),
`MissionOptimizer` (la ligne de log), et `TransferProblemDepletionCostTest` renommé en
`TransferProblemCostTest` (trois cas : somme de la décomposition, rejet de l'extinction, non
régression de la fixture).
