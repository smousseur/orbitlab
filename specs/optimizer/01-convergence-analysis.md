# Analyse de convergence — 400 km vs 600 km

Diagnostic du comportement observé dans `LEOMissionOptimizationTest`
pour deux altitudes cibles différentes.

## Observations brutes (logs)

### Cas 400 km — convergence excellente

```
Final best cost = 6.71e-7, total evals = 13074
Stage 'Transfert' optimized: values = [0.186, 14.44, 0.502, 0.118]
Post burn1 orbit: a=6713857 m, e=0.0091, i=45.52°
Transfert burn 2: dtCoast=3142.2 s, dt2=0.80 s, dvNeeded=34.98 m/s
[400 km] Max coast altitude = 411 778 m
[400 km] Min coast altitude = 399 729 m
```

### Cas 600 km — convergence dégradée

```
Stage 'Transfert' optimized: cost=0.0946, values=[0.976, 6.37, 0.7854, 0.225]
Post burn1 orbit: a=6833877 m, e=0.0285, i=45.53°
Transfert burn 2: dtCoast=2247.7 s, dt2=2.37 s, dvNeeded=108.13 m/s
[600 km] Max coast altitude = 664 642 m
[600 km] Min coast altitude = 653 237 m
```

## Décodage des 4 paramètres optimisés

Les `values` correspondent au **burn 1** du `TransfertTwoManeuverStage`,
définis dans `TransferTwoManeuverProblem.java` :

| Idx | Param | Sens physique | 400 km | 600 km | Bornes |
|---|---|---|---|---|---|
| 0 | `t1` | offset début burn 1 (s) | 0.186 | 0.976 | `[0, 2·guessT1+120]` |
| 1 | `dt1` | durée burn 1 (s) | 14.44 | 6.37 | `[0.5·guessDt1, min(2·guessDt1, dt1MaxPhys)]` |
| 2 | `α1` | angle in-plane TNW (rad) | 0.502 | **0.7854** | **`[−π/4, +π/4]`** |
| 3 | `β1` | angle out-of-plane TNW (rad) | 0.118 | 0.225 | `[−π/12, +π/12]` |

## Indices d'échec à 600 km

### Indice n°1 — `α1` saturé à sa borne supérieure

`0.7853981633974483` est **exactement** π/4, c'est-à-dire la borne
supérieure définie dans `getUpperBounds()`. Quand un paramètre CMA-ES
finit pile sur sa borne, c'est presque toujours qu'il **voudrait aller
au-delà** mais qu'il est cliqué par la contrainte. L'optimum réel se
trouve hors de la boîte de recherche → la convergence à `cost=0.094`
reflète un minimum *contraint*, pas un vrai optimum.

### Indice n°2 — orbite post-burn1 mal placée, pas mal circularisée

Avec `a = 6 833 877 m, e = 0.0285` (cas 600 km) :

- apoapsis ≈ **650 km**
- periapsis ≈ **261 km**

Le burn 2 analytique (108 m/s, ~3.7× le cas 400 km) circularise près de
l'apoapsis courante, donc l'orbite finale tombe à **653-664 km** :
**~55 km au-dessus de la cible**. Le coût est dominé par le terme
apoapsis (`W_APO=3`) et le terme periapsis (`W_PERI=10`), tous deux
incapables de se résoudre puisque les altitudes de coast sont
décalées vers le haut.

Conclusion : **le burn 1 n'arrive pas à descendre l'apoapsis à 600 km**
parce que la fin du gravity turn l'a déjà poussée plus haut, et α1 est
plafonné côté positif (impossible de "tirer" davantage radialement
pour recadrer).

### Indice n°3 — gravity turn non altitude-aware

`GravityTurnConstraints.forTarget` produit :

```java
apogeeTarget = 0.75  · targetAlt
apogeeMax    = 0.875 · targetAlt
vTanMin      = 2000 m/s   // FIXE
fpaTarget    = 2°          // FIXE
```

- 400 km → fenêtre apogée [300, 350] km
- 600 km → fenêtre apogée [450, 525] km

Mais `vTanMin` et `fpaTarget` sont **constants** alors que la physique
change : pour atteindre 600 km à fin de gravity turn, il faut soit y
entrer plus vite, soit avec un FPA différent. La "passation" entre le
gravity turn et le burn de transfert se dégrade quand la cible monte.

### Indice n°4 — `dt1` plus court que prévu

`guessDt1` est issu d'une estimation Hohmann
(`a_transfer = (rApo + rTarget)/2`). À 600 km, `guessDt1` est plus grand
que à 400 km (Δv Hohmann plus grand). Pourtant l'optimiseur converge à
`dt1 = 6.37 s` (plus court que les 14.44 s à 400 km !).

Interprétation : CMA-ES a trouvé que **brûler longtemps coûte plus**
que brûler peu et "gérer" avec un grand burn 2 (108 m/s), probablement
parce qu'au-delà de quelques secondes l'apoapsis explose et déclenche
la barrière `altitudeMax = 1.05·targetAlt`. Il est coincé entre deux
contraintes.

### Indice n°5 — `t1` non négligeable à 600 km

`t1 = 0.976 s` (vs 0.186 s à 400 km) suggère que l'optimiseur essaie
de retarder le burn pour le placer ailleurs sur l'orbite balistique
post-gravity-turn — symptôme d'une géométrie d'arrivée mal centrée
sur la cible.

## Synthèse — pourquoi 600 km dégrade

1. **`α1` plafonne à π/4** : l'espace de recherche est trop étroit pour
   cette altitude. Signal le plus net.
2. **Le gravity turn livre une fin d'arc inadaptée** : `vTanMin` et
   `fpaTarget` ne dépendent pas de `targetAlt`, donc plus la cible
   monte, plus la passation entre stages se dégrade.
3. **Conséquence** : le burn 1 n'a ni assez d'autorité (durée bornée)
   ni assez de directionnalité (α1 borné) pour ramener l'apoapsis à
   600 km ET réduire l'excentricité simultanément. CMA-ES fait un
   compromis qui laisse l'orbite trop excentrique (e=0.029 vs 0.009)
   et trop haute (~654 km).
4. **Le seuil `acceptableCost = 8e-4`** n'est jamais atteint, donc le
   `MissionOptimizer` épuise son budget multi-try sans converger.

## Validation expérimentale

L'élargissement empirique de la borne supérieure de `α1` (au-delà de
π/4) suffit à débloquer la convergence à 600 km. Cela confirme l'indice
n°1 comme cause directe, et motive les évolutions décrites dans le
document `02-altitude-dependent-design.md`.

## Fonction de coût (`TransferTwoManeuverProblem`)

Pour référence, la cost combine 4 termes + barrières :

| Terme | Poids | Formule |
|---|---|---|
| Apoapsis error | `W_APO = 3.0` | `((apoAlt − targetAlt) / targetAlt)²` |
| Periapsis error | `W_PERI = 10.0` | `((periAlt − targetAlt) / targetAlt)²` |
| Eccentricity penalty | `W_E = 2.0` | `e²` |
| Radial velocity penalty | `W_V = 1.0` | `(vRadial / vCircTarget)²` |

Barrières (déclenchent un coût élevé) :

- Periapsis floor : 100 km
- Altitude min en vol : 80 km
- Altitude max : 1.05 · targetAlt
- Hyperbolique (énergie ≥ 0)

`acceptableCost = 8e-4` (très exigeant, normalisé par `targetAlt`).
