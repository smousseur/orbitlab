# I7 — Suites (post multi-étages)

> Ce document prend la suite du [bilan I7](10-i7-bilan.md), dont plusieurs conclusions ont été
> **invalidées par la mesure** depuis. Il fait l'état des lieux réel, corrige ce que le bilan 10
> affirmait à tort, et priorise les actions restantes. À lire avant de reprendre le chantier :
> plusieurs « vérités » du bilan 10 ne tiennent plus.

---

## 1. Où on en est

I7 est **vert sur les deux profils**, avec un gain qui compte désormais au niveau du stack :

| Profil | λ* | Étage(s) réduit(s) | Gain |
|---|---|---|---|
| LEO 400 km (mono-λ) | 0,4313 | S2 2844 → 1227 kg | **−57 % de S2** (−0,1 % du stack) |
| LEO 400 km (multi-λ) | [1,0000 ; 0,4312] | S2 seul, S1 épinglé à 1 | identique au mono-λ (§3.1) |
| GEO (multi-λ, sonde corrigée) | [0,9453 ; 0,8141] | inchangé | **confirme le −5,6 %** (§3.1) |
| GEO (mono-λ) | 0,8141 | S2 10 619 → 8645 kg | −18,6 % de S2 |
| **GEO (multi-λ)** | **[0,9453 ; 0,8141]** | **S1 −67 430 kg**, S2 −1974 kg | **−69 404 kg, −5,6 % du stack** |

Livré depuis le bilan 10 :

| Sujet | Contenu |
|---|---|
| Terme propellant-aware (§6) | `TransferProblem.computeCost` pénalise le Δv au-delà de la référence Hohmann |
| Résiduel par-étage (§6) | `vehicle/StagePropellant`, `Vehicle.resolveStagePropellant`, capture au largage |
| Invariant d'étagement | `GravityTurnProblem` pénalise un MECO antérieur à la fin d'étagement |
| 3 garde-fous analytiques | séparation d'étage, convergence Newton GTO, signes du plan de parking |
| Multi-bisection | `runtime/MultiStageLoadOptimizer`, descente par coordonnées, un λ par étage |

---

## 2. Ce que le bilan 10 affirme à tort

Trois corrections, toutes établies par la mesure. **Ne pas repartir des conclusions du bilan 10
sans lire cette section.**

### 2.1 « S1 n'est jamais largué explicitement » (§4.1, §5.3) — FAUX

`GravityTurnManeuver.configure` larguait déjà S1 explicitement, à l'intérieur de la gravity turn :
burn 1 arrêté par `DepletionStopTrigger`, puis un `DateDetector` à `kickDate + burn1Duration` qui
fait un `RESET_STATE` vers `massAfterJettison`, puis coast interétage, puis burn 2 sur S2.

Le vrai défaut était que **rien ne garantissait que la propagation atteigne la date de largage** :
`getLowerBounds()` renvoyait un plancher plat de 30 s alors que `buildInitialGuess()` utilisait
`burn1Duration + 20`. Quand CMA-ES choisissait `transitionTime < burn1Duration`, la propagation
s'arrêtait avant le détecteur — burn 1 tronqué, **pas de largage**, S1 actif pour tout le reste.

Arithmétique de la panne (massFlow S1 ≈ 7855 kg/s, `burn1Duration` ≈ 149,97 s) :

| cas | transitionTime | écart | conséquence |
|---|---|---|---|
| GEO λ=1 | 150,04 s | +0,07 | largage OK |
| GEO λ=0,65 | 149,56 s | **−0,41** | 3284 kg échoués dans S1 |
| LEO λ=1 | 154,09 s | +4,12 | largage + burn 2 de 606 kg |

LEO franchissait la falaise de 4 secondes par chance, GEO la ratait de 0,4 seconde.

### 2.2 « Scaler S1 casse l'ascension, λ* épinglé à 1 » (§4.1) — FAUX sur GEO, VRAI sur LEO

Mesuré : **λ₀ = 0,9453 sur GEO**, soit 67 tonnes récupérées. La conclusion du bilan 10 avait été
tirée sur des runs où le largage était raté — elle est donc bâtie sur des données faussées.

Re-mesurée depuis sur LEO (§3.1) : **λ₀ = 1,0000**, la conclusion tient — mais pour une raison que
le bilan 10 n'énonçait pas, et sur des données cette fois saines. Ce n'est donc pas une affirmation
sur le comportement de S1 en général : c'est une propriété du profil.

### 2.3 « Le gain stack est structurellement masqué par S1 » (§5.2) — CADUC

Vrai tant que S1 restait hors λ. Avec S1 sous λ : −5,6 % du stack.

---

## 3. Actions, par priorité

### 3.1 — Multi-étages sur LEO ✔ FAIT, question tranchée

Test : `PropellantLoadOptimizerIntegrationTest.leoMultiStage_shrinksEveryVariableLoadStage`.
16 évaluations, 1 passe, ~10 min.

**Résultat : λ* = [1,0000 ; 0,4312].** S1 reste épinglé à 1 ; λ(S2) reproduit le mono-λ (0,4313) à
la 4ᵉ décimale, ce qui valide au passage la machinerie multi-étages contre la scalaire et confirme
la quasi-séparabilité des coordonnées déjà observée sur GEO.

**Pourquoi S1 ne bouge pas — le mécanisme, pas l'intuition.** La sonde qui l'explique est
`λ = [0,9891 ; 0,43125]` : **−1,1 % sur S1 seul, S2 laissé à sa valeur gagnante**, et le résiduel de
S2 tombe de 127 kg à **0**. Les 13,5 t retirées à S1 sont intégralement repayées par S2, qui est
déjà au bord de sa falaise (voir §3.2). Ce n'est donc pas « l'ascension casse » : c'est un transfert
de charge vers un étage sans marge, qui sature immédiatement.

**L'asymétrie LEO/GEO est structurelle, pas accidentelle.** Sur GEO, S2 injecte 10,6 t en GTO et
laisse la GT collée à son plancher d'étagement — du gras en bas. Sur LEO, S2 pèse 1227 kg et S1 fait
presque tout le travail — rien à récupérer en bas. **Corollaire : `allVariableLoadMask` reste un
opt-in profil par profil, et ne doit pas devenir le défaut.**

**Défaut d'outillage trouvé au passage, corrigé depuis : la sonde diagonale.** Elle avait testé
`[0,98 ; 0,4226]` et renvoyé `feasible=false`, ce qui se lisait « on est sur la frontière ». Le
verdict était vide de sens : `tolerance` était utilisée en **absolu** comme critère d'arrêt de la
bissection (`feasible − infeasible > tolerance`) et en **relatif** comme pas de la sonde
(`λ × (1 − step)`). Dès que λ < 1 le pas devient plus petit que la résolution de la bissection —
ici 0,43125 × 0,02 = 0,0086 contre un bracket non résolu de [0,4203 ; 0,43125] — donc la sonde
sondait **à l'intérieur du bracket**, une zone sur laquelle la bissection venait de renoncer à
conclure.

Trois correctifs posés dans `MultiStageLoadOptimizer` :

| # | Correctif |
|---|---|
| 1 | pas **absolu** sur l'axe λ (`λ − step`), commensurable avec le critère d'arrêt |
| 2 | seules les coordonnées strictement dans (λmin, λmax) sont steppées ; sous 2 coordonnées mobiles la sonde est **sautée** (pas de coin possible) |
| 3 | un refus se loggue « aucun gras diagonal au-delà de la résolution », plus jamais « on est sur la frontière » |

**Vérifié sur les deux profils après correctif** :

- **LEO** — sonde sautée (1 coordonnée mobile sur 2), **15 évaluations au lieu de 16**, λ* inchangé.
- **GEO** — sonde exécutée avec le pas absolu, `[0,9253 ; 0,7941]` → infaisable, λ* inchangé à
  [0,9453 ; 0,8141]. **Le −5,6 % de GEO n'est donc pas un artefact de coin** à l'échelle de 0,02 :
  le chiffre du tableau §1 tient, cette fois sur une sonde qui teste réellement quelque chose.

### 3.2 — Marge mesurée pour les étages brûlés à épuisement ★ priorité haute

**Le problème** : le plancher de résiduel ne garde que l'étage scalé du haut. On ne peut pas
l'étendre à S1, car S1 affiche **toujours 0 % de résiduel** (la GT dimensionne `burn1Duration` pour
tout consommer). Donc **λ₀ = 0,9453 est faisable mais sans marge mesurée**, là où S2 en a une (1,4 %).

**Le piège** : on ne peut pas s'appuyer sur `StageCapabilities.shutdownMode()` pour distinguer les
deux régimes — **FH S1 déclare `COMMANDED` alors qu'il vole en épuisement**. Le modèle ment.

**Ce n'est pas propre à S1 : c'est un basculement de mode, mesuré sur S2 (LEO multi-λ, §3.1).**
Le résiduel ne décroît pas continûment vers le plancher quand on serre la charge — il tombe d'un
coup :

| λ(S2) | charge | résiduel | |
|---|---|---|---|
| 1,0 | 2844 kg | 47,1 % | ✓ |
| 0,65 | 1849 kg | 34,2 % | ✓ |
| 0,475 | 1351 kg | 12,3 % | ✓ |
| **0,43125** | **1227 kg** | **10,3 %** | **✓ ← λ\*** |
| 0,4203 | 1195 kg | **0,0 %** | ✗ |
| 0,40937 | 1164 kg | 0,0 % | ✗ |
| 0,3875 | 1102 kg | 0,0 % | ✗ |

32 kg de moins (−2,6 % de charge) font passer 127 kg de résiduel à **exactement zéro** :
sous un seuil critique, la solution bascule d'une coupure commandée à une coupure par épuisement.
`objectiveMet=true` **des deux côtés** de la falaise — l'orbite est atteinte dans les deux modes,
seule la façon de terminer la combustion change.

Conséquence pour cette section : n'importe quel étage peut basculer en épuisement selon sa charge,
donc « lire la marge dans le résiduel » n'est pas seulement inapplicable à S1, c'est inapplicable à
tout étage serré. La marge cherchée ici doit se lire ailleurs que dans le résiduel.

**Pistes** :
- soit corriger la déclaration du catalogue et faire porter le plancher par le mode réel ;
- soit définir une marge propre aux étages en épuisement — p. ex. l'écart entre `transitionTime` et
  le plancher d'étagement, qui mesure exactement « de combien la GT aurait pu couper plus tôt » ;
- soit accepter et documenter que ces étages sont gardés par l'objectif et les garde-fous seuls.

La deuxième piste est la plus prometteuse : c'est ce signal qui a permis de prédire correctement
que S1 avait du gras sur GEO.

### 3.3 — Découpler la métrique de test de la géométrie terrestre ★ prérequis des tolérances

**Le constat**, établi sur le sweep LEO : `min/max coast altitude` sont des altitudes **géodésiques**,
donc elles mélangent la qualité d'insertion et la forme de la Terre. À i = 45,9°, l'aplatissement
(21,4 km entre rayons équatorial et polaire) impose à lui seul :

```
ΔR = 21,39 × sin²(45,87°) = 11,0 km d'écart min/max, sur une orbite PARFAITEMENT circulaire
```

Confirmation empirique : à 800 km, `e = 1,4e-4` (quasi parfait) et l'écart mesuré vaut **10,96 km**.
Contre-épreuve : sur GEO, `i ≈ 0` donc le terme s'annule — l'écart tombe à **1,78 km**.

Le décentrage (±5 km) vient de la latitude où tombent les apsides : l'altitude géodésique moyenne
d'une orbite circulaire vaut `r − R_eq + 21,39 × sin²i / 2`, soit +5,5 km à i = 46°.

**Conséquence** : à 300 km, l'excursion mesurée (13,6 km) consomme **4,5 % des 7 %** de tolérance
avec de la géométrie pure. Resserrer à 5 % ne laisse presque rien ; à 4 % le test échoue sur la
forme de la Terre, pas sur le vol.

**Action** : asserter sur ce que l'optimiseur contrôle — `a` et `e`, ou l'altitude géodésique aux
apsides osculatrices — plutôt que sur des extrêmes d'altitude sur tout le coast.

### 3.4 — Resserrer les tolérances ▸ priorité moyenne, après 3.3

> **Corrigé par la mesure (§3.1).** Ce paragraphe affirmait que le −57 % de LEO était « en partie
> acheté » par la tolérance ±7 %. **C'est faux sur LEO** : sur les 8 évaluations du run multi-λ,
> `objectiveMet=true` **partout**, y compris sur les points déclarés infaisables. L'objectif n'est
> jamais la contrainte active ; c'est le plancher de résiduel qui ferme le bracket, à chaque fois.

| Bouton | Valeur | Sensibilité mesurée sur LEO |
|---|---|---|
| `ORBIT_MARGIN_RATIO` | ±7 % | **nulle** — jamais la contrainte active ; plafonné par 3.3 en basse altitude |
| plancher de résiduel | 1 % | **nulle** — voir ci-dessous |
| `W_PROPELLANT` | 5e-3 | non re-mesurée ; calibré à ~27 % d'`acceptableCost` (3e-3) |

**La valeur numérique du plancher ne porte aucune information.** À cause de la falaise (§3.2), le
résiduel de l'étage dimensionné vaut soit ≥ 10,3 %, soit exactement 0 — jamais entre les deux.
N'importe quelle valeur dans (0 ; 10,3 %] donne le même λ*. Ce n'est pas un bouton de réglage, c'est
un **détecteur binaire de flame-out**. Il fait bien son travail (rejeter les solutions en équilibre
sur le fil), mais le régler est sans effet.

**Sur GEO le plancher se comporte à l'inverse — il mord, mais sa course est courte.** À
λ* = [0,9453 ; 0,8141], S2 termine à **118 kg sur 8645 kg chargés, soit 1,4 % contre un plancher à
1 %** : collé au plancher, pas à 10 points au-dessus comme sur LEO. La descente y est continue —
λ = 0,803 → 0,81 % · 0,814 → 1,39 % · 0,825 → 1,95 % · 1,0 → 11,34 % — donc le 1,4 % est une marge
réelle et **la valeur du plancher fixe directement λ\***.

Mais sous λ(S2) ≈ 0,79 la mission **ne vole plus** : l'injection GTO refuse le plan. Le mur est
entre 0,782 et 0,798. Baisser le plancher de 1 % à 0,5 % déplacerait donc λ* de 0,814 à ~0,798, soit
**~140 kg sur S2**, puis mur. **C'est un bouton à ~150 kg de course, pas un levier sur le −5,6 %** —
et ce qui borne réellement GEO n'est ni la tolérance ni le plancher, mais le garde-fou d'injection
GTO (§3.7).

| Profil | résiduel de l'étage dimensionné à λ* | plancher | régime | ce qui borne λ* |
|---|---|---|---|---|
| LEO | 10,3 % | 1 % | falaise — valeur sans effet | plancher, en détecteur binaire |
| GEO | **1,4 %** | 1 % | continu — valeur active | **garde-fou GTO** (§3.7) |

**Ce que ça change pour cette action** : la prémisse « trois boutons qui interagissent » n'est vraie
sur aucun des deux profils, mais pour des raisons opposées. Sur LEO il ne reste qu'un bouton à
mesurer (`W_PROPELLANT`), les deux autres étant sans effet. Sur GEO c'est le plancher de résiduel
qui pilote, et `ORBIT_MARGIN_RATIO` n'y est même pas le paramètre en jeu (GEO est asserté à ±50 km
via un objectif de faisabilité explicite, pas via `ORBIT_MARGIN_RATIO`). **Resserrer les tolérances
d'insertion n'est donc pas le levier qu'on croyait sur λ\*, sur aucun des deux profils.**

Si `acceptableCost` bouge, la calibration de `W_PROPELLANT` est à revérifier.

**Note sur la précision de λ\*** : le bracket final LEO vaut 0,4203–0,43125, soit une largeur de
0,011 sous la tolérance de 0,02 — λ* est donc précis à ~12 kg près, et **n'est pas conservateur**.
La non-monotonicité stochastique documentée dans `PropellantLoadOptimizer` ne s'est pas manifestée
sur ce run : la séquence des résiduels est propre et monotone jusqu'à la falaise.

### 3.5 — Tension de la GT sur GEO ✔ FAIT, le sujet s'est refermé tout seul

Sur le run GEO mono-λ, à tous les λ < 1 la GT se figeait **exactement** sur son plancher d'étagement
(`transitionTime = 151,9796595263…` à la 11ᵉ décimale, `burn2 = 0`), avec pour conséquences : coût
au-dessus de l'acceptable → **WARN à chaque évaluation**, **~12 000 évaluations CMA-ES au lieu de
2065**, hand-off à FPA 2,8–4,0° au lieu de 1,17°.

**Lu dans le run multi-étages : dès que S1 passe sous λ, la GT se décolle.**

| λ(S1) | `transitionTime` | plancher | coût (acceptable 0,0476) | évals CMA-ES | FPA |
|---|---|---|---|---|---|
| 1,0 | **151,9797** | 151,98 — `burn2 = 0` | 0,13 à 5,40 ⚠ | 10 000–13 500 | 2,80–4,01° |
| 0,9453 (λ*) | **144,3130** | 143,39 — `burn2 = 0,9 s` | **0,0133** ✓ | **2 356** | **1,32°** |

Budget interne revenu au normal, coût sous l'acceptable, hand-off à 1,32°. C'était bien le symptôme
du gras S1.

**Ce qu'il faut retenir quand même** : la pathologie n'a pas disparu, elle n'est plus *déclenchée* à
l'optimum. La sonde `λ=[0,9453 ; 0,3]` re-épingle la GT sur le nouveau plancher (143,3949), WARN et
10 208 évals. Le mécanisme réel est « l'étage du dessus est trop léger pour le profil que la GT
vise » — le gras de S1 n'en était qu'une cause parmi d'autres. **Conséquence pratique : l'épinglage
reste un indicateur fiable de « ce λ est loin du faisable », et il coûte 5× le temps d'une
évaluation saine** (voir §3.6).

### 3.6 — Tâche 3 : feedback UI ▸ chantier séparé, différé

Progressbar sur la boucle externe. Trois éléments nouveaux à intégrer :

- le coût est maintenant de **~29 évaluations** (multi-étages) et non ≤ 10 ;
- **le chemin GEO de `MissionFactory` peut désormais lever une exception** pour des masses de charge
  utile où la GT n'épuise pas S1 — là où il produisait silencieusement une mission fausse. Le wizard
  n'a aucune gestion d'erreur pour ça ;
- **le coût d'une évaluation varie d'un facteur 5** et n'est pas prévisible à l'avance. Mesuré sur le
  run GEO : ~5–8 s quand la GT converge normalement (~2 200 évals CMA-ES), **~25–33 s quand elle
  s'épingle sur son plancher d'étagement** (~12 000 évals, §3.5). Une barre de progression linéaire
  en nombre d'évaluations sera donc très fausse par moments. Le signal existe et est lisible en
  cours de route — l'épinglage se voit au WARN `cost above acceptable` —, mais il arrive *après*
  coup.

### 3.7 — Garde-fou d'injection GTO ✔ FAIT, question tranchée (mur physique, diagnostic corrigé)

**Le constat**, lu sur la séquence d'évaluations GEO. Le refus de `AnalyticGtoInjectionStage` se
déclenche à `AIM_CONVERGENCE_TOLERANCE_RATIO` = 1 % de r₂, soit **422 km** pour GEO. Le refus le
plus serré du run manque de **472 km**, avec **7481 kg encore à bord** et une combustion de 26,0 s.
Ce n'est pas un étage affamé.

Le javadoc de la constante énonce pourtant que le seuil « n'a qu'à séparer *à quelques itérations
près* de *n'a jamais bougé* », le second cas laissant « des dizaines de milliers de km ». Le run
fournit le contraste exact : `λ=[0,3 ; 0,814]` manque de **35 614 km avec 0 kg et 0,000 s de
combustion** — ça, c'est la starvation. 472 km, c'est la bande ambiguë que le design supposait
jamais visitée. **La bissection s'y installe, parce que c'est précisément là qu'est la frontière.**

**Tranché : c'est de la physique, et sans rien relancer.** Le rapport propergol/durée est constant
sur **tous** les refus GEO — 2832/9,852 · 5379/18,713 · 6649/23,130 · 7206/25,067 · 7481/26,027 =
**287,4–287,5 kg/s**, le débit du Merlin Vacuum. `dt1` vaut donc exactement
`propergol restant / débit` dans chaque refus : la combustion est **plafonnée par le carburant**.

Or `Physics.computeBurnDurationCapped` renvoie `min(requis, épuisement)`. Une fois le plafond
atteint, **`dt1` ne dépend plus de `dv1`, donc plus de `r2Aim`** : chaque itération resimule la même
combustion et relit le même `bias`, pendant que `r2Aim` accumule dans le vide. L'itération de Newton
n'a plus aucun degré de liberté. **Aucun budget d'itérations ne peut refermer ces 472 km**, et le
test proposé plus haut était voué à ne rien montrer.

Le mur à λ(S2) ≈ 0,79 est donc réel : GEO ne récupère pas de masse de ce côté. **Résultat négatif,
mais définitif** — et §3.4 tient tel quel (plancher à ~150 kg de course, puis mur physique).

**Ce qui restait vraiment défectueux, corrigé** : le diagnostic, pas la borne. Les deux pannes
arrivaient sous le même message « did not converge », ce qui a fait passer une limite du véhicule
pour un artefact de solveur méritant plus d'itérations. `AnalyticGtoInjectionStage` distingue
désormais :

| Panne | Message | Vraie question |
|---|---|---|
| combustion plafonnée, toujours court | `injection out of reach: burning all N kg …` | le véhicule ne peut pas, point |
| marge de propergol, itérations épuisées | `did not converge … the burn was not propellant-limited` | là, `AIM_ITERATIONS` mérite d'être augmenté |

La boucle sort aussi dès le plafond détecté, au lieu de dépenser trois propagations de plus à
re-dériver le même nombre. `AIM_CONVERGENCE_TOLERANCE_RATIO` est **inchangé** : ce n'est pas lui qui
décidait de la faisabilité, et le toucher aurait couplé deux changements de comportement (§4).

### 3.8 — `minimizeBelow` re-sonde `lambdaMin` à chaque passe ▸ priorité basse

Sur le run GEO, `λ=[0,3 ; 0,8140625]` est évalué **deux fois à l'identique** (passe 1 et passe 2),
avec la même exception. La bissection ouvre systématiquement son bracket sur `lambdaMin` alors que
la passe précédente l'a déjà prouvé infaisable pour cette coordonnée. ~26 s sur 29 évaluations.
Corriger en mémorisant, par coordonnée, le dernier point infaisable connu et en ouvrant le bracket
là plutôt qu'à `lambdaMin`.

### 3.9 — La passe optimize et la réplique éphéméride ne volent pas la même trajectoire ★★ BLOQUANT avant l'UI

**Le constat.** Chaque évaluation propage deux fois : `MissionOptimizer.optimize()`, puis
`MissionEphemerisGenerator` qui re-planifie les étages analytiques depuis son propre état. Les deux
divergent. Mesuré sur le run GEO multi-étages à λ=[1 ; 1], même mission, même graine :

| | passe optimize | passe éphéméride |
|---|---|---|
| Injection GTO Δv / durée | 2410,40 m/s / 30,4215 s | 2360,31 m/s / 29,9833 s |
| résiduel S2 au largage | **1204 kg** | **1330 kg** |
| inclinaison à l'entrée AKM | **5,2955°** | **0,1501°** |
| aim tilt de circularisation | 31,77° | 0,80° |
| plane trim au nœud | **283 m/s demandés pour 68 kg** | 7 m/s |

L'écart de masse ferme exactement (0,438 s × 287,4 kg/s = 126 kg), donc ce n'est pas du bruit
d'intégration : les deux passes entrent dans l'injection GTO avec des états différents.

**Trois symptômes, dont deux étaient déjà visibles dans les logs sans être lus :**

1. `DepletionGuard` tire en **ERROR** — *« Propellant depleted before scheduled cutoff … upstream
   mass accounting is wrong »* — **sur une solution retenue** (λ=[1 ; 0,8141], le λ₁\* de la passe 1).
   Le code signale lui-même une incohérence de comptabilité, au niveau ERROR, et rien ne la lit.
2. Dans la passe optimize, le plane trim est **systématiquement affamé** : 283 m/s demandés, 68 kg
   disponibles (~105 m/s), combustion jusqu'à la masse sèche exacte, 5,28° d'inclinaison laissés.
   Dans la réplique le même trim demande 6 m/s et finit à 0,12°.
3. **Les deux moitiés du prédicat de faisabilité portent sur deux trajectoires différentes** :
   `MissionLoadEvaluator` lit `objectiveMet` dans `result.ephemeris()` (la réplique) et le plancher
   de résiduel dans `result.performanceReport()` (la passe optimize). D'où le 1204 kg loggué par
   l'évaluateur contre 1330 kg loggué au largage dans l'éphéméride, pour la même évaluation.

**Pourquoi c'est bloquant pour la Tâche 3.** Le rendu et le panneau de trajectoire affichent
l'éphéméride. L'utilisateur verrait donc la réplique — ni la mission qui a été optimisée, ni celle
dont le résiduel par étage a été validé. Tant que les deux divergent, « la mission affichée » et
« la mission retenue » ne sont pas le même objet, et aucun affichage ne peut être juste.

**Diagnostiqué — incohérence de modèle gravitationnel, étage par étage.** Le générateur
d'éphéméride propage **tout** en `createOptimizationPropagator` (8×8 Holmes-Featherstone, avec J2 et
l'aplatissement). La passe optimize propage chaque étage analytique via son `propagateStandalone`,
et ceux-ci **ne sont pas cohérents entre eux** :

| Étage | `propagateStandalone` (optimize) | générateur (éphéméride) | |
|---|---|---|---|
| Parking insertion | `createSimplePropagator` — point-masse | 8×8 | ✗ |
| GTO injection | `createSimplePropagator` — point-masse | 8×8 | ✗ |
| Trim burn | `createSimplePropagator` — point-masse | 8×8 | ✗ |
| Hohmann (profil LEO) | `createSimplePropagator` — point-masse | 8×8 | ✗ |
| AKM circularization | `createOptimizationPropagator` — 8×8 | 8×8 | ✓ |
| Plane trim (nœud) | `createOptimizationPropagator` — 8×8 | 8×8 | ✓ |

Le point-masse (`NewtonianAttraction`) est sphérique — pas de J2. Parking, GTO et Trim volent donc
en champ sphérique dans la passe optimize et en champ oblate dans l'éphéméride ; les deux
trajectoires divergent dès la première combustion analytique et l'écart s'accumule sur le transfert
GTO. La GT est en 8×8 des **deux** côtés (`propagateForOptimization` et le replay via `configure`),
d'où l'écart minuscule à l'entrée du parking (dv1 20,335 vs 20,341) : la divergence naît *après*, pas
dans le hand-off de la GT.

**Tracé — le mécanisme complet, et quelle passe est juste.** L'AKM (`finalInclination = 0`) vise
l'équateur : il fait **tout** le changement de plan (~5,2° depuis Kourou → 0°) à l'apogée, combiné à
la circularisation ; le plane-trim-at-node ne nettoie que le résidu **~0,25°** qu'il laisse (commentaire
`GEOMission`). Or un changement de plan à l'apogée n'a d'autorité que si l'apogée tombe **près d'un
nœud** (commentaire `AnalyticApogeeCircularizationStage` : hors-nœud, seul ~0,03° est récupérable).

Les deux propagateurs de l'AKM sont en 8×8, donc son *plan* est calculé pareil dans les deux passes —
seul son **état d'entrée** diffère. L'inclinaison est invariante sous J2 (les deux passes arrivent à
~5,2°) ; ce qui diffère, c'est la position apogée-vs-nœud, que **J2 fait précesser**. Passe 8×8 :
apogée au nœud → AKM retire le plan → résidu 0,15°, plane-trim 6 m/s (comportement de conception).
Passe point-masse : apogée hors-nœud → AKM impuissant → 5,30° restants → le plane-trim, dimensionné
pour 0,25°, se voit demander 283 m/s qu'il n'a pas → famine, trip du garde.

**Verdict : la passe 8×8 (éphéméride) est la physiquement juste** — c'est le modèle de conception, et
elle reproduit l'intention. La passe point-masse casse la géométrie apogée-nœud dont l'AKM dépend.
Les 283 m/s « physiquement exacts » sont un leurre : la mission n'était jamais censée porter 5,28° au
plane-trim. **Le trip du `DepletionGuard` en passe optimize est donc un faux positif du mauvais
modèle** — ce qui confirme rétroactivement qu'il fallait bien **différer** son passage en infaisable
(le faire lever aurait rejeté des missions faisables). **Corollaire : l'option 1 ci-dessous est la
bonne, et suffit.**

Réserve : le *pourquoi* exact de « point-masse hors-nœud vs 8×8 au-nœud » (précession J2 inférée) n'a
pas été tracé jusqu'au ciblage de nœud de l'injection GTO. Sans effet sur le verdict — la conception
vise apogée-au-nœud (résidu ~0,25°) et seule la passe 8×8 l'atteint. Question de robustesse ouverte
**au-delà** de §3.9 : si pour certains époques/charges le 8×8 lui-même plaçait l'apogée hors-nœud, la
mission serait réellement sous-budgétée au plane-trim (le node-trim ne porte que ~0,25°).

**Deux directions de correction :**

1. **Aligner `propagateStandalone` sur le modèle de l'éphéméride** ★ **validée par le tracé AKM** —
   remplacer `createSimplePropagator` par `createOptimizationPropagator` dans les quatre étages mal
   appariés (parking, GTO, trim, Hohmann LEO). Un mot par étage, les deux passes volent alors le même
   champ 8×8 — et c'est le champ physiquement juste (l'AKM y retrouve sa géométrie apogée-nœud, plus
   de famine du plane-trim). Le 8×8 est déjà la référence (toute la pile d'optim et le générateur
   l'utilisent). **Ça change la trajectoire que voit l'optimiseur → λ* GEO/LEO et calibrations à
   re-mesurer (non-régression, cf. §4)**, mais dans le sens correct. Le point-masse avait sans doute
   été choisi pour la vitesse ; ces étages étant analytiques et propagés une fois par évaluation
   (hors boucle CMA-ES), le surcoût est borné — l'éphéméride fait déjà ce travail en 8×8.
2. **Supprimer la double planification** — les étages analytiques re-planifient à neuf dans chaque
   passe (`propagateStandalone` puis `configure`). Les faire persister le plan calculé en passe
   optimize et le rejouer, comme `OptimizableMissionStage`, rend les deux passes identiques par
   construction et indépendantes du modèle. Plus de travail, mais ferme la classe de bugs entière.

**Prérequis transverse.** À l'analyse, ce prérequis s'est révélé **couplé au choix de modèle** et
non indépendant comme annoncé :

- Faire lever le `DepletionGuard` de la passe **optimize** rejetterait des solutions sur la foi du
  modèle point-masse — or c'est justement le modèle suspect. Pire, à λ* GEO le plane trim demande
  283 m/s pour retirer 5,28°, ce qui est **physiquement exact** (5,28° × 3075 × π/180 ≈ 283) : on ne
  peut pas savoir si c'est le point-masse (pessimiste correct) ou le 8×8 (0,12°, plan déjà retiré en
  amont) qui dit vrai **sans tracer la logique de plan de l'AKM**. Tant que ce n'est pas fait, faire
  lever le garde optimize figerait peut-être le mauvais modèle et déplacerait λ*.
- Lire les deux moitiés du prédicat sur la même passe change forcément la source du résiduel **ou**
  de `objectiveMet`, donc λ* → re-mesure. C'est du ressort de l'option 1/2, pas un préalable neutre.

**✔ Fait — le sous-ensemble réellement sûr et découplé.** L'échec de la passe **éphéméride** (le
8×8, la trajectoire affichée) est désormais visible : `MissionEphemeris` porte un drapeau
`isComplete()`, le générateur le baisse quand un étage lève **ou** s'arrête avant son cutoff
planifié (un `DepletionGuard` qui tronque une combustion à sec), et `MissionLoadEvaluator` refuse
comme infaisable toute évaluation dont la trajectoire volée est tronquée — même si le coast terminal
paraît sur cible. Ne rejette pas le λ* actuel (son éphéméride atteint tous les cutoffs), ne biaise
aucun modèle. C'est ce qui empêche l'UI d'afficher une trajectoire cassée comme une mission valide.

**Reste couplé à §3.9 (option 1/2), à faire ensemble** : aligner le modèle des étages analytiques
sur 8×8, faire lever le garde optimize (désormais sûr, une fois les modèles alignés), et lire les
deux moitiés du prédicat sur la même passe — le tout suivi d'une re-mesure GEO/LEO. **Le préalable
qui bloquait — tracer l'AKM pour savoir quelle passe est juste — est fait : c'est la passe 8×8, donc
l'option 1 est la bonne. Le chantier est débloqué.**

---

## 4. Points de méthode à ne pas perdre

Trois leçons payées cher pendant ce chantier.

**Une borne CMA-ES fait partie du système de coordonnées.** `CMAESRunExecutor` passe `SimpleBounds`
à Hipparchus, qui **normalise l'espace de recherche par la largeur de la boîte**. Déplacer une borne
ré-encode tous les candidats *et* change le sigma effectif, donc perturbe la recherche **y compris
sur les missions où la borne n'est jamais active** (mesuré : l'apogée LEO 300 km partie à +10,6 km
alors que le reste du sweep tenait à +0,1…+4,7 km). Toute contrainte nouvelle passe par un **terme
de coût dominant**, jamais par une borne.

**Ne pas coupler deux changements de comportement.** Le correctif d'étagement a d'abord été posé en
borne *et* en invariant ; il a fallu les séparer. Capturer la baseline **avant** de toucher à quoi
que ce soit.

**Les solveurs analytiques doivent refuser, pas produire n'importe quoi.** Les trois pannes trouvées
suivaient le même patron : un solveur qui sort une valeur aberrante sans le signaler (aim à
177 000 km pour une cible à 35 786 ; largage du mauvais étage ; durée de poussée négative). Le
correctif est toujours le même — lever une exception portant les chiffres, que
`MissionLoadEvaluator` lit comme une infaisabilité propre.

---

## 5. Ce qui reste hors périmètre

| Sujet | Statut |
|---|---|
| Warm-start cross-λ (bilan 10 §4.3) | toujours non fait, gain marginal |
| Bascule v2 / surrogate bayésien | **non nécessaire a priori** : sur GEO les deux coordonnées se sont révélées quasi séparables (λ₁ inchangé au 16ᵉ chiffre après réduction de S1 de 67 t), donc la descente par coordonnées est proche de l'optimum global. À rouvrir seulement si un sweep laisse de la masse sur la table |
| CMA-ES en boucle externe | **écarté** : n ≤ 3, évaluations à 25–65 s, et le réemploi réel se limiterait à ~20 lignes |
