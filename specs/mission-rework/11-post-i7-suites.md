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

### 2.2 « Scaler S1 casse l'ascension, λ* épinglé à 1 » (§4.1) — FAUX sur GEO

Mesuré : **λ₀ = 0,9453 sur GEO**, soit 67 tonnes récupérées. La conclusion du bilan 10 avait été
tirée sur des runs où le largage était raté — elle est donc bâtie sur des données faussées.

**Elle n'a jamais été re-testée sur LEO depuis le correctif.** C'est l'action n°1 ci-dessous.

### 2.3 « Le gain stack est structurellement masqué par S1 » (§5.2) — CADUC

Vrai tant que S1 restait hors λ. Avec S1 sous λ : −5,6 % du stack.

---

## 3. Actions, par priorité

### 3.1 — Relancer le multi-étages sur LEO ★ priorité haute

**Pourquoi** : §2.2. La conclusion « S1 n'a pas de gras sur LEO » repose sur des runs antérieurs au
correctif d'étagement. GEO vient de démontrer l'inverse de la même intuition.

**Comment** : dupliquer `geoMultiStage_shrinksEveryVariableLoadStage` sur le profil LEO transfert
optimisé, masque `allVariableLoadMask`, référence à battre λ* = 0,4313 sur S2 seul.

**Coût** : ~27 évaluations × 64 s ≈ 30 min. **Tranche une question ouverte sur données.**

### 3.2 — Marge mesurée pour les étages brûlés à épuisement ★ priorité haute

**Le problème** : le plancher de résiduel ne garde que l'étage scalé du haut. On ne peut pas
l'étendre à S1, car S1 affiche **toujours 0 % de résiduel** (la GT dimensionne `burn1Duration` pour
tout consommer). Donc **λ₀ = 0,9453 est faisable mais sans marge mesurée**, là où S2 en a une (1,4 %).

**Le piège** : on ne peut pas s'appuyer sur `StageCapabilities.shutdownMode()` pour distinguer les
deux régimes — **FH S1 déclare `COMMANDED` alors qu'il vole en épuisement**. Le modèle ment.

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

`ORBIT_MARGIN_RATIO` (±7 %) conditionne le λ* atteignable : le −57 % de LEO est en partie « acheté »
par cette tolérance. Trois boutons interagissent et doivent être re-réglés **ensemble** :

| Bouton | Valeur | Remarque |
|---|---|---|
| `ORBIT_MARGIN_RATIO` | ±7 % | plafonné par 3.3 en basse altitude |
| plancher de résiduel | 1 % | de la charge de l'étage dimensionné |
| `W_PROPELLANT` | 5e-3 | calibré à ~27 % d'`acceptableCost` (3e-3) |

Si `acceptableCost` bouge, la calibration de `W_PROPELLANT` est à revérifier.

### 3.5 — Tension de la GT sur GEO ▸ à qualifier

Sur le run GEO mono-λ, à tous les λ < 1 la GT se figeait **exactement** sur son plancher d'étagement
(`transitionTime = 151,9796595263…` à la 11ᵉ décimale, `burn2 = 0`), avec pour conséquences : coût
au-dessus de l'acceptable → **WARN à chaque évaluation**, **~12 000 évaluations CMA-ES au lieu de
2065**, hand-off à FPA 2,8–4,0° au lieu de 1,17°.

**Question ouverte** : maintenant que S1 est sous λ, la GT est-elle encore collée au plancher ? Si
oui, le budget interne reste 6× trop cher ; si non, c'était bien le symptôme du gras S1 et le sujet
se referme tout seul. **À lire dans le log du run multi-étages GEO avant d'ouvrir quoi que ce soit.**

### 3.6 — Tâche 3 : feedback UI ▸ chantier séparé, différé

Progressbar sur la boucle externe. Deux éléments nouveaux à intégrer :

- le coût est maintenant de **~29 évaluations** (multi-étages) et non ≤ 10 ;
- **le chemin GEO de `MissionFactory` peut désormais lever une exception** pour des masses de charge
  utile où la GT n'épuise pas S1 — là où il produisait silencieusement une mission fausse. Le wizard
  n'a aucune gestion d'erreur pour ça.

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
