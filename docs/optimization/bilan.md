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

## Ce qui reste ouvert, par ordre d'intérêt

1. **`t1` saturé à 100 %** sur 5 transferts sur 7, `norm=1.0` exactement. C'est un mur de boîte, et c'est maintenant le seul signal fort restant. Déplacement de borne, donc à traiter isolément avec re-mesure des références.
2. **Voie B** — le `WARN Final cost … above acceptable 0.003` tombe encore sur chaque transfert. Plus urgent pour la propreté du log que pour le temps, maintenant.
3. **L'orbite moyenne livrée est ~8 km au-dessus de la cible** (557 844 × 559 719 pour 550 km visés). Biais préexistant J2/Trim, dans la bande ±7 %, que rien de ce qu'on a fait n'a touché — mais qui saute aux yeux une fois le reste nettoyé.
4. Le `ERROR DepletionGuard - [Trim burn] … upstream mass accounting is wrong` sur les rejets de routine.

Rien n'est commité — les sept fichiers sont dans l'arbre, dont tes deux en cours.