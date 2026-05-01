# Phase 0 — Baseline diagnostic et plan de correction

Synthèse de l'investigation Phase 0 du roadmap, conduite sur la branche
`claude/cmaes-altitude-parameters-JL1Z1` après l'instrumentation
(saturation des bornes, décomposition Δv, état de fin de gravity turn,
diagnostic des barrières) et l'exécution de la suite paramétrique
`LEOAltitudeSweepTest` sur `[185, 250, 400, 600, 800, 1200, 1500,
2000]` km.

Cette spec technique consolide les pathologies effectivement observées
et **liste, par ordre de priorité, les correctifs à apporter** pour
fiabiliser la convergence sur toute la plage. Elle référence les
documents existants pour les justifications physiques et le plan de
plus haut niveau.

## Documents de référence

| Document | Rôle dans cette spec |
|---|---|
| [`01-convergence-analysis.md`](01-convergence-analysis.md) | Décodage initial des 4 paramètres CMA-ES, structure de la cost function. Source des indices n°1-5 sur le cas 400 km vs 600 km. |
| [`02-altitude-dependent-design.md`](02-altitude-dependent-design.md) | Principes physiques (vis-viva, géométrie ellipse de transfert) pour les bornes adaptatives. **À consulter pour les formules exactes des correctifs.** |
| [`03-robustness-roadmap.md`](03-robustness-roadmap.md) | Plan séquencé Phases 0-5. Cette spec **affine et réordonne** les Phases 1-3 selon les résultats Phase 0. |

## Mesures Phase 0 (baseline)

Suite paramétrique exécutée avec budget 40 000 évaluations CMA-ES par
stage. Mesures extraites des logs `MissionOptimizer` enrichis par
l'instrumentation Phase 0.1.

### Tableau récap

| target | totalCost | maxAlt | apoErr | periErr | dv1 useful | dv1 wasted | dv2 |
|---|---|---|---|---|---|---|---|
| 185 km | 7.2e-4 | 198 033 | **+13 033** | −616 | 3 541 | 171 | 19 |
| 250 km | 3.0e-4 | 262 953 | **+12 953** | −442 | 2 838 | 83 | 15 |
| 400 km | 8.9e-5 | 411 686 | **+11 686** | −323 | 700 | 79 | 33 |
| 600 km | 2.3e-4 | 614 420 | **+14 420** | +2 346 | 102 | 221 | 96 |
| 800 km | 7.8e-4 | 813 389 | **+13 389** | +1 323 | 58 | 198 | 160 |
| 1200 km | 2.1e-3 | 1 216 101 | **+16 101** | +5 050 | 67 | 134 | 274 |
| 1500 km | 1.1e-2 | 1 516 042 | **+16 042** | +4 865 | 91 | 206 | 355 |
| 2000 km | 5.6e-3 | 2 016 095 | **+16 095** | +4 742 | 115 | 159 | 450 |

### Table des saturations

| Paramètre | Bornes actuelles | Saturation observée |
|---|---|---|
| GT `transitionTime` | `[30, 450]` s | **HIGH** à 400, 600, 800, 1200, 1500, 2000 km |
| GT `exponent` | `[0.3, 3.0]` | **LOW** à 600, 800, 1200, 1500, 2000 km |
| Transfer `t1` | `[0, 2·guessT1+120]` s | **LOW** à 400, 600, 800, 1200, 1500, 2000 km |
| Transfer `dt1` | `[0.5·guessDt1, min(2·guessDt1, dt1MaxPhys)]` | **HIGH** à 600 km |
| Transfer `α1` | `[−π/2, +π/2]` (déjà élargi par commit a33af4e) | aucune saturation |
| Transfer `β1` | `[−π/12, +π/12]` | **HIGH** à 185, 400, 1500, 2000 km |

### États de fin de gravity turn

`vTan` réel comparé à la cible vis-viva `v_min = sqrt(μ·(2/r_GT − 1/a_transfer))` :

| target | GT alt | vTan réel | Δ vs ideal | vRad | FPA |
|---|---|---|---|---|---|
| 185 km | 138 km | 4 280 | **−3 555** | +150 | +2.0° |
| 250 km | 204 km | 4 950 | **−2 845** | +171 | +2.0° |
| 400 km | 284 km | 7 062 | −707 | +255 | +2.1° |
| 600 km | 268 km | 7 729 | −109 | +234 | +1.7° |
| 800 km | 230 km | 7 861 | −64 | +130 | +1.0° |
| 1200 km | 193 km | 7 986 | −75 | +28 | +0.2° |
| 1500 km | **107 km** | 8 117 | −94 | **−212** | **−1.5°** |
| 2000 km | **128 km** | 8 187 | −117 | **−144** | **−1.0°** |

## Diagnostic — quatre pathologies indépendantes

### P1. Biais constant `apoErr ≈ +13 km` (artefact de mesure)

**Origine** : mismatch géocentrique vs géodésique.

`TransferTwoManeuverProblem.computeCost` (ligne 227) :

```java
double apoAlt = apoapsis - EARTH_RADIUS;  // EARTH_RADIUS = WGS84 équatorial = 6 378 137 m
```

C'est l'altitude **géocentrique** mesurée par rapport au **rayon
équatorial**. Mais l'application mesure l'altitude **géodésique**
au-dessus de l'ellipsoïde WGS84 (`Mission.computeAltitudeMeters` →
`OneAxisEllipsoid.transform`), utilisée par `MissionEphemerisGenerator`
(et donc par `LEOMissionOptimizationTest`,
`LEOAltitudeSweepTest`).

À latitude φ = 45° sur l'ellipsoïde WGS84 (`f = 1/298.257223563`) :

```
e² = 2f − f² ≈ 0.006694
R(45°) = a · sqrt((1 − e²(2−e²)·sin²φ) / (1 − e²·sin²φ))
       ≈ 6 378 137 · 0.998331
       ≈ 6 367 485 m
ΔR    = R_eq − R(45°) ≈ +10 652 m
```

Pour une orbite `i ≈ 45.5°`, l'apoapsis tombe à des latitudes proches
de l'inclinaison ; on observe en effet un biais de **+11.7 à +16.1 km**,
cohérent avec ce ~+10.7 km plus quelques km dus à la latitude exacte
d'apoapsis et à l'osculation.

**Vérification croisée** : doc `01-convergence-analysis.md` affichait
déjà `Max coast altitude = 411 778 m` pour la cible 400 km
(« convergence excellente »), soit **+11.7 km** — exactement la valeur
mesurée aujourd'hui. Le biais existait avant les modifications
récentes, simplement masqué par la marge de 7 % du test
`LEOMissionOptimizationTest`.

### P2. Effondrement de la passation GT → Transfert en altitude

Trois régimes pathologiques distincts visibles dans la table « États de
fin de gravity turn » :

**P2.a — Cibles basses (185-250 km)** : `vTan` réel à 4 280 / 4 950 m/s,
**Δ vs ideal = −3 555 / −2 845 m/s**. Le burn 1 du transfert doit alors
fournir 2 800-3 500 m/s de Δv utile (quasi-mise en orbite seul). La
contrainte `minTangentialVelocity = 2000 m/s` (constante) **n'est jamais
active** car la valeur physique réelle attendue est ~7 100 m/s. Voir
`02-altitude-dependent-design.md` §1.1 (« le 2000 m/s actuel n'a aucun
sens physique »).

**P2.b — Cibles moyennes (400-800 km)** : passation acceptable à 400 km
(seul cas où `useful > wasted`). Dès 600 km, `transitionTime` plafonne
à 450 s, `exponent` plonge vers la borne basse 0.3, **`dt1` sature à
99 % de `2·guessDt1`** → α₁ pousse à 1.25 rad ≈ 71° pour compenser
radialement → **dv1 wasted (221 m/s) > useful (102 m/s)**. Le burn 2
absorbe la dérive (96 m/s).

**P2.c — Cibles hautes (1200-2000 km)** : régime catastrophique.
`transitionTime = 450 s` (cap dur), `exponent ≈ 0.32` (cap bas) → le
pitch program plonge. À 1500-2000 km, **la trajectoire balistique
post-MECO a déjà passé son apogée et redescend** lorsque le transfert
prend la main : `alt = 107 / 128 km`, `vRad < 0`, `FPA < 0°`. Le
burn 1 fait alors un steering radial massif (α₁ ≈ −1.25 rad) tandis
que **le burn 2 absorbe la totalité de la mise en orbite** (dv2 = 450
m/s à 2000 km).

Le coût total reste « bas » (0.005-0.011) parce que la cost function
pondère ratios apo/peri *finaux* — pas le réalisme physique du
chemin. C'est exactement la situation décrite par
`02-altitude-dependent-design.md` §1.4 (`transitionTime_max`
saturant à haute altitude) et le doc 02 §1.1 (vis-viva).

### P3. Bornes du burn 1 (transfert) trop étroites ou mal centrées

- **`β1` ∈ [−π/12, +π/12]** : sature à 185, 400, 1500, 2000 km.
  L'inclinaison post-burn 1 (45.5°) montre que la composante
  out-of-plane est consommée — bornes trop étroites pour que CMA-ES
  reste « à l'intérieur ».
- **`dt1` HIGH-SAT à 600 km** : la borne `min(2·guessDt1, dt1MaxPhys)`
  vaut `2·guessDt1 = 7.50 s` (et non le cap propergol). Le facteur 2
  est insuffisant quand le GT livre un état déficient.
- **`t1` LOW-SAT (~0 %) sur 6 altitudes / 8** : l'initial guess
  `timeToApoapsis` est inadapté car le GT ne livre pas un état proche
  de l'apoapsis (vRad > 0, donc encore en montée). CMA-ES « tire »
  systématiquement vers `t1 = 0` (burn immédiat), ce qui suggère que
  la borne basse est elle-même sous-optimale ou que le guess est
  mal positionné dans la fenêtre. Voir `01-convergence-analysis.md`
  Indice n°5.

### P4. Bornes du gravity turn non adaptatives

- **`transitionTime ∈ [30, 450]` s** : sature HIGH à toutes les altitudes
  ≥ 400 km. À 1500 km, la valeur physique requise est 600-800 s. Voir
  `02-altitude-dependent-design.md` §1.4.
- **`exponent ∈ [0.3, 3.0]`** : sature LOW à toutes les altitudes
  ≥ 600 km. La borne basse force un pitch program agressif qui pousse
  la trajectoire à passer son apogée avant la fin du `transitionTime`,
  ce qui produit la pathologie P2.c.
- **`apogeeTarget = 0.75·target`, `apogeeMax = 0.875·target`** :
  cohérent à 400 km mais inadapté en haute et basse altitude. Voir
  `02-altitude-dependent-design.md` §1.2 pour les ratios proposés.
- **`vTanMin = 2000 m/s` (constant)** : voir P2.a. Voir
  `02-altitude-dependent-design.md` §1.1.
- **`fpaTarget = 2.0°` (constant)** : devrait être une fenêtre dérivée
  de la géométrie de transfert. Voir
  `02-altitude-dependent-design.md` §1.3.

## Plan de correctifs ordonnés par priorité

Le séquencement diffère du roadmap §03 parce que les mesures Phase 0
ont mis en évidence que :

1. **P1 (biais géodésique) doit être corrigé en premier** : sans cela,
   tous les seuils `acceptableCost` et toutes les mesures de qualité
   sont biaisés de ~13 km, ce qui empêche d'évaluer correctement
   l'effet des correctifs suivants.
2. **P2.c (GT en chute libre) est plus grave que P2.a (vTanMin)** :
   à 1500-2000 km la trajectoire est physiquement absurde.
3. **P3 et P4 se traitent en parallèle** une fois P1 et P2 réglés.

### Niveau 0 — Pré-requis : aligner la métrique de coût (P1)

**Pourquoi en premier** : tant que la cost function est en géocentrique
et le test en géodésique, le seuil `acceptableCost = 8e-4` ne signifie
rien d'absolu et chaque correctif sera évalué dans le bruit du biais.

**Fichier** : `TransferTwoManeuverProblem.java`

**Modification** : dans le constructeur, après le calcul existant de
`altitudeOffset` (J2 short-period), ajouter une compensation
géodésique :

```java
double sinPhi = FastMath.sin(initialOrbit.getI()); // φ_apo ≈ inclination
double f = Constants.WGS84_EARTH_FLATTENING;
double e2 = 2 * f - f * f;
double rEllipsoid = EARTH_RADIUS *
    FastMath.sqrt((1 - e2 * (2 - e2) * sinPhi * sinPhi)
                / (1 - e2 * sinPhi * sinPhi));
double geodeticOffset = EARTH_RADIUS - rEllipsoid; // ~+10.7 km à i=45°

double effectiveTargetAlt = targetAltitude + altitudeOffset + geodeticOffset;
```

**Critère de succès** : sur `LEOAltitudeSweepTest`, `apoErr` médian
< 2 km à 400 km (au lieu de +11.7 km), `apoErr` constant disparaît à
toutes les altitudes.

**Effort** : ~5 lignes. Risque : nul (la valeur ne dépend que de
l'inclinaison initiale, déjà disponible).

---

### Niveau 1 — CRITIQUE : refonte gravity turn altitude-aware

Sans cela, P2.b et P2.c persistent indépendamment des correctifs
amont sur le transfert.

#### Niveau 1.1 — `transitionTime` adaptatif (résout P2.c, P4)

**Fichier** : `GravityTurnProblem.java`, méthode `getUpperBounds()`.

**Formule** (cf. `02-altitude-dependent-design.md` §1.4) :

```java
double targetAltMeters = constraints.targetApogee() / 0.75; // remonter à targetAlt
double transitionTimeMax = 300.0 + 0.3 * FastMath.sqrt(targetAltMeters);
return new double[] {transitionTimeMax, 3.0};
```

Donne ~600 s à 1500 km, ~720 s à 2000 km. Gain ciblé : élimine la
saturation HIGH de `transitionTime` à toutes les altitudes ≥ 400 km.

**Effet attendu** : `vRad > 0` et `FPA > 0°` au handoff pour 1500 et
2000 km.

#### Niveau 1.2 — `vTanMin` dérivé vis-viva (résout P2.a, P4)

**Fichier** : `GravityTurnConstraints.java`, méthode `forTarget(...)`.

**Formule** (cf. `02-altitude-dependent-design.md` §1.1) :

```java
double rGtEnd   = EARTH_RADIUS + targetAltitude * 0.75; // = apogeeTarget
double rTarget  = EARTH_RADIUS + targetAltitude;
double aTransfer = (rGtEnd + rTarget) / 2.0;
double vMin     = FastMath.sqrt(MU * (2.0 / rGtEnd - 1.0 / aTransfer));
double vTanMin  = vMin * 0.95; // marge -5%
```

Gain ciblé : à 185-250 km, l'optimiseur GT « voit » qu'il doit livrer
~7 100 m/s tangentiel, ce qui doit faire chuter spectaculairement le
`dv1 useful` du transfert (de ~3 500 à quelques centaines de m/s).

#### Niveau 1.3 — Ratios apogée adaptatifs (résout P4)

**Fichier** : `GravityTurnConstraints.java`, méthode `forTarget(...)`.

**Formule** (cf. `02-altitude-dependent-design.md` §1.2) :

```java
double targetKm = targetAltitude / 1000.0;
double ratioApo;
if (targetKm <= 250) ratioApo = 0.95;
else if (targetKm <= 800) ratioApo = 0.95 + (0.75 - 0.95) * (targetKm - 250) / (800 - 250);
else ratioApo = 0.75;

double apogeeTarget = ratioApo * targetAltitude;
double apogeeMax    = FastMath.min(targetAltitude, apogeeTarget * 1.15);
double apogeeMinSafe = FastMath.max(140_000, 0.6 * targetAltitude);
apogeeTarget = FastMath.max(apogeeTarget, apogeeMinSafe);
```

#### Niveau 1.4 — `fpaTarget` en fenêtre (résout P4)

**Fichier** : `GravityTurnConstraints.java` + `GravityTurnProblem.java`.

Étendre le record pour exposer une fenêtre `[fpaMin, fpaMax]` au lieu
d'un point dur. Voir `02-altitude-dependent-design.md` §1.3 pour la
formule (`fpa_at_GT_end` dérivée de l'ellipse de transfert).

Le coût (`GravityTurnProblem.computeCost` ligne 124-125) doit pénaliser
seulement les FPA hors fenêtre.

#### Niveau 1.5 — Borne basse `exponent` (résout P4)

**Fichier** : `GravityTurnProblem.java`, méthode `getLowerBounds()`.

`exponent` sature LOW à 0.3 dès 600 km. Élargir la borne basse à 0.1
ou la rendre adaptative — à valider empiriquement après les niveaux
1.1-1.4 (la pression sur `exponent` peut disparaître une fois le
gravity turn correctement contraint).

---

### Niveau 2 — MAJEUR : bornes du transfert adaptatives

À traiter **après** Niveau 1, parce que plusieurs des saturations
observées au transfert (en particulier `t1 = 0`, `α1` proche de ±π/2,
`β1` saturé) sont des **conséquences** de la mauvaise passation GT, pas
des causes propres. Une partie disparaîtra naturellement après
Niveau 1.

#### Niveau 2.1 — Borne `β1` adaptative (résout P3)

**Fichier** : `TransferTwoManeuverProblem.java`, méthodes
`getLowerBounds()` / `getUpperBounds()`.

Voir `02-altitude-dependent-design.md` §2.1 :

```java
double apoDefect = (rTarget - rApoapsis) / rTarget; // peut être négatif
double betaMax = FastMath.PI / 12.0 * (1.0 + FastMath.max(0.0, apoDefect));
```

À défaut, élargir directement à `±π/8` ou `±π/6` et observer si la
saturation persiste après Niveau 1.

#### Niveau 2.2 — Sigma initial cohérent (résout P3)

**Fichier** : `TransferTwoManeuverProblem.java`, méthode
`getInitialSigma()`.

Toujours utiliser `~0.3·(upper − lower)` calculé dynamiquement, plutôt
que les constantes `π/8`, `π/24`. Voir
`02-altitude-dependent-design.md` §2.2.

```java
@Override
public double[] getInitialSigma() {
    double[] lo = getLowerBounds();
    double[] hi = getUpperBounds();
    double[] sigma = new double[lo.length];
    for (int i = 0; i < sigma.length; i++) sigma[i] = 0.3 * (hi[i] - lo[i]);
    return sigma;
}
```

#### Niveau 2.3 — Bornes `t1` et `dt1` (résout P3)

**Fichier** : `TransferTwoManeuverProblem.java`.

- **`t1`** : aujourd'hui `[0, 2·guessT1+120]`. La saturation à 0 sur
  6/8 altitudes indique que `guessT1 = timeToApoapsis` n'est pas le
  bon point de référence (le GT ne livre pas un état proche de
  l'apoapsis). Deux pistes :
  1. Calculer `guessT1` à partir de l'état réel du GT (trouver le
     temps où `vRad = 0`, qui peut être négatif si le GT a passé son
     apogée). Si le GT est en montée (vRad > 0), `guessT1 ≈ 0`.
  2. Garder `[0, fraction · période_orbitale]` mais ne pas pré-biaiser
     l'initial guess.
- **`dt1`** : remplacer le facteur `2·guessDt1` par
  `min(K · guessDt1, dt1MaxPhysical)` avec `K = 3` ou `4`. Voir
  `02-altitude-dependent-design.md` §2.3 + assert de feasibility
  (`Δv_Hohmann ≤ ISP·g·ln(m0 / (m0 − propellant))`).

#### Niveau 2.4 — Cost function : seuils absolus + W_E adaptatif (résout marges)

**Fichier** : `TransferTwoManeuverProblem.java`.

Voir `02-altitude-dependent-design.md` §2.4. À 185 km, l'erreur
relative `(5/185)² ≈ 7.3e-4` est presque égale au seuil
`acceptableCost = 8e-4` — le seuil devient le critère, pas la qualité
réelle. Découpler en seuil absolu (5 km) ET relatif. Pondérer
`W_E = f(targetAlt)` (plus fort à basse altitude).

`periapsisFloor` à dériver de `targetAlt` (`max(120 km, target − 100 km)`).

---

### Niveau 3 — Robustesse de l'optimiseur (filets de sécurité)

À traiter **après** Niveau 2, pour gérer les cas résiduels.

#### Niveau 3.1 — Détection automatique des bords (P3 résiduel)

**Fichier** : `CMAESTrajectoryOptimizer.java`, méthode `optimize()`.

Voir `03-robustness-roadmap.md` §3.1. Après chaque run :

- L'instrumentation Phase 0.1 (`OptimizerDiagnostics.evaluateBounds`)
  détecte déjà la saturation et émet un WARN.
- Ajouter le **comportement actif** : si un paramètre est à < 2 % de
  sa borne, élargir la borne d'un facteur 1.5 et relancer (max 2
  fois).

#### Niveau 3.2 — Warm-start Hohmann (résout cas résiduels)

Voir `03-robustness-roadmap.md` §3.2. Ajouter aux 4 runs d'exploration
de `CMAESTrajectoryOptimizer` un seed = solution Hohmann pure
(`α1=0, β1=0, dt1=Hohmann_dv/massflow, t1≈0`) pour garantir au moins
un run près de la solution physique.

#### Niveau 3.3 — Diagnostic d'infaisabilité a priori

Voir `03-robustness-roadmap.md` §3.3. Avant CMA-ES, vérifier
`Δv_Hohmann ≤ Δv_disponible`. Lever une exception explicite plutôt
que de laisser l'optimiseur tâtonner.

---

### Niveau 4 — Validation

Validation par la suite paramétrique déjà mise en place.

#### Niveau 4.1 — Critères de succès Phase 4 du roadmap

Une fois Niveaux 0-3 implémentés, **réintroduire les assertions** dans
`LEOAltitudeSweepTest` selon les critères de
`03-robustness-roadmap.md` §4.2 :

- `cost < acceptableCost(targetAlt)` (relatif et absolu)
- `|maxAlt − targetAlt| < max(2 km, 0.01 · targetAlt)`
- `|maxAlt − minAlt| < max(5 km, 0.02 · targetAlt)` (circularité)
- Aucun paramètre saturé aux bornes (via les flags Phase 0.1).

#### Niveau 4.2 — Tests latitude (optionnel)

Voir `03-robustness-roadmap.md` §4.3. Tests à 5°, 28°, 45°, 51° pour
valider l'indépendance latitude / altitude — particulièrement utile
**après le correctif P1** pour confirmer que le compensateur géodésique
fonctionne à toute latitude.

---

### Niveau 5 — Anticipation GTO (sans implémenter)

Référence : `03-robustness-roadmap.md` §5. Pendant les Niveaux 0-3,
paramétrer `TransferTwoManeuverProblem` et `GravityTurnConstraints`
par un `OrbitTarget` (record `rPeri, rApo, inclination`) plutôt que
par `double targetAltitude`, **sans encore implémenter** le cas
elliptique. Évite un second refactor lourd.

## Résumé exécutif

| Niveau | Effort | Risque | Pathologies résolues |
|---|---|---|---|
| **0** — Compensation géodésique | 5 lignes | Nul | P1 entier |
| **1.1** — `transitionTime` adaptatif | 5 lignes | Faible | P2.c, P4 partiel |
| **1.2** — `vTanMin` vis-viva | 10 lignes | Faible | P2.a, P4 partiel |
| **1.3** — Ratios apogée adaptatifs | 10 lignes | Faible | P4 partiel |
| **1.4** — Fenêtre FPA | 30 lignes | Moyen | P4 partiel |
| **1.5** — Borne `exponent` | 1 ligne | Faible | P4 partiel |
| **2.1-2.3** — Bornes transfert | ~50 lignes | Moyen | P3 |
| **2.4** — Cost normalisée | ~30 lignes | Moyen | Marges 185 km |
| **3.1** — Auto-élargissement bords | ~40 lignes | Faible | Filet de sécurité |
| **3.2** — Warm start Hohmann | ~30 lignes | Faible | Robustesse |
| **3.3** — Feasibility check | ~20 lignes | Faible | Erreurs claires |
| **4.1** — Réintroduire assertions | Tests | Nul | Validation |
| **5** — `OrbitTarget` (anticipation GTO) | ~80 lignes | Moyen | Préparation |

**Ordre d'attaque recommandé** : 0 → 1.1 → 1.2 → 1.3 → 4.1 (mesurer)
→ 2.1 → 2.3 → 2.2 → 2.4 → 1.4, 1.5 → 3.1 → 3.2, 3.3 → 5.

Mesurer après chaque étape via `LEOAltitudeSweepTest` et comparer à la
baseline tabulée ci-dessus.
