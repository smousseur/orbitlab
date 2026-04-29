# Conception altitude-aware — `GravityTurnConstraints` et `TransferTwoManeuverProblem`

Principes physiques pour rendre les contraintes et bornes des deux
stages d'optimisation dépendantes de `targetAltitude`, plutôt que
constantes.

## Pourquoi un bornage en `targetAltitude` est nécessaire

À altitude fixée, le besoin réel en `α1` (et en autorité du burn 1)
dépend de :

- **où** dans la fenêtre `[apogeeTarget, apogeeMax]` du gravity turn
  on est tombé,
- **avec quel FPA**,
- **avec quelle vitesse tangentielle** au point de fin de GT.

Trois effets cumulatifs poussent vers un `α1` plus grand quand la cible
monte :

1. **Δa augmente → Δv tangentiel augmente → durée de burn augmente**.
   Pendant un burn long, le vecteur vitesse "tourne" sous l'effet de la
   gravité ; la direction optimale dans le repère TNW *au début* du
   burn s'écarte de la pure tangentielle (problème classique du
   *finite burn loss*).
2. **Le FPA résiduel à compenser est constant (~2°) mais l'effet
   relatif change**. À 400 km, la composante radiale à corriger pèse
   peu devant la grosse impulsion tangentielle ; à 600 km, le burn
   tangentiel utile est plus long, donc la fraction radiale à injecter
   via α n'est pas proportionnelle à `Δv`.
3. **L'apogée de fin de gravity turn (≤ 0.875·targetAlt) est plus loin
   de la cible en valeur absolue** (75 km à 600 km contre 50 km à
   400 km), donc le burn 1 doit lever davantage l'apogée, demandant un
   meilleur "timing × direction".

### Limites d'un bornage purement en `targetAltitude`

Un bornage `α_max = f(targetAlt)` reste un proxy : il masque la vraie
cause (la passation gravity-turn → transfert) plutôt que de la
résoudre. Deux runs CMA-ES sur le gravity turn peuvent atterrir à
450 km comme à 525 km pour une cible 600 km : ces deux cas demandent
des `α1` très différents.

Le **vrai principe directeur** : à la fin du gravity turn, l'état
`(h, v_tan, FPA)` doit être proche du début d'un arc de Hohmann allant
à l'orbite circulaire cible. Tout dérive alors de la vis-viva.

## 1. `GravityTurnConstraints` — refonte physics-derived

### 1.1 `vTanMin` — dériver de la cible via vis-viva

À la fin du GT (altitude `h_end ≈ apogeeTarget`), pour atteindre un
apogée à `r_apo = R⊕ + targetAlt` sur un arc balistique, la vitesse
minimale (tangentielle, FPA≈0) vaut :

```
r_GT_end   = R⊕ + apogeeTarget
r_target   = R⊕ + targetAlt
a_transfer = (r_GT_end + r_target) / 2
v_min      = sqrt( μ · (2/r_GT_end − 1/a_transfer) )
vTanMin    = v_min · 0.95          (marge -5%)
```

Ordres de grandeur :

| `targetAlt` | `vTanMin` calculé | Constante actuelle |
|---|---|---|
| 185 km | ~7 100 m/s | 2 000 m/s |
| 400 km | ~7 200 m/s | 2 000 m/s |
| 600 km | ~7 250 m/s | 2 000 m/s |

⚠️ Le **2 000 m/s actuel n'a aucun sens physique** comme contrainte
d'orbite — c'est un seuil qui ne contraint quasiment jamais. Les
valeurs vraies sont 3-4× plus grandes. C'est pour ça que la fin de GT
peut tomber dans un état "physiquement faiblard" sans déclencher la
barrière.

### 1.2 `apogeeTarget` / `apogeeMax` — ratios adaptatifs

Le bon ratio dépend du Δv que le burn 1 peut/doit fournir et de la
marge atmosphérique.

```
apogeeMin_safe = max(140 km, 0.6 · targetAlt)         // sortie atmosphère

ratio_apo(targetAlt):
  - si targetAlt ≤ 250 km   : 0.95   (GT presque jusqu'à la cible)
  - si targetAlt ∈ [250, 800] km : interpoler 0.95 → 0.75
  - si targetAlt > 800 km   : 0.75   (laisser plus de travail au burn 1)

apogeeTarget = ratio_apo(targetAlt) · targetAlt
apogeeMax    = min(targetAlt, apogeeTarget · 1.15)
```

Le seuil bas (`apogeeMin_safe`) garantit que le GT à 185 km finit
au-dessus de l'atmosphère exploitable.

### 1.3 `fpaTarget` — fenêtre, pas un point

Plutôt qu'un FPA fixe à 2°, en faire une **fenêtre**
`[fpa_min, fpa_max]` dérivée de la géométrie de l'ellipse de
transfert :

```
e_transfer    = (r_target − r_GT_end) / (r_target + r_GT_end)
fpa_at_GT_end = atan2( e·sin(ν), 1 + e·cos(ν) )    // ν = anomalie vraie à h_GT_end
fpaTarget     ∈ [0°, fpa_at_GT_end + marge]
```

Ordres de grandeur :

| `targetAlt` | `fpa_at_GT_end` |
|---|---|
| 185 km | ~0.3° |
| 400 km | ~1.5° |
| 600 km | ~2.2° |
| 1500 km | ~5° |

### 1.4 Bornes du `GravityTurnProblem`

- `transitionTime` upper bound : aujourd'hui 450 s. À haute altitude
  (1500 km+), il faut ~600-800 s. Borne supérieure à rendre dépendante
  de `targetAlt`, p.ex. :
  ```
  transitionTime_max = 300 + 0.3 · sqrt(targetAlt en m)
  ```
- `exponent` (profil de pitch, actuellement 0.3-3.0) : à vérifier
  empiriquement, peut probablement être conservé.

## 2. `TransferTwoManeuverProblem` — bornes adaptatives

### 2.1 Bornes `α1` / `β1` dérivées du contexte

Plutôt que `±π/4` fixe, dériver des bornes physiques :

```
// défaut FPA estimé à l'instant t1 (mesurable depuis l'état initial du transfert)
fpa_defect = |fpa_at_burn_start − fpa_optimal_for_transfer|

// déficit d'apogée
apo_defect = (target − apo_current) / target

α_max = min( π/2, max( π/4, fpa_defect + asin(min(1, apo_defect)) + marge ) )
β_max = π/12 · (1 + apo_defect)
```

À défaut, un fallback **scalé sur `targetAlt`** (cohérent avec ce qui
a été validé empiriquement) :

```
α_max = min( π/2, π/4 · max(1, targetAlt / 400e3) )
```

### 2.2 Sigma initial cohérent avec les bornes

`getInitialSigma()` doit toujours valoir ~0.3·(upper−lower) pour que
CMA-ES explore correctement la boîte. Aujourd'hui le sigma de `α1` est
codé à `π/8` — devient inadapté si on bouge `α_max`. À recalculer
dynamiquement à partir des bornes.

### 2.3 Bornes `t1`, `dt1`

- **`dt1MaxPhysical`** (déjà dépend du propergol restant) : OK, mais
  ajouter un **assert de feasibility** au démarrage :
  ```
  Δv_Hohmann_estimé ≤ ISP · g · ln(m0 / (m0 − propergol))
  ```
  Sinon → exception explicite "altitude infaisable avec ce stack".
- **`t1` upper bound** : `2·guessT1 + 120` peut être insuffisant à
  très haute altitude, où la phase de coast pré-burn1 est plus longue.
  Borner par une fraction de la période orbitale courante.

### 2.4 Cost function — normalisation et pondération

#### Erreur normalisée trop lâche aux basses altitudes

L'erreur actuelle est `(Δh / targetAlt)²`. À 185 km, 5 km d'erreur
fait `(5/185)² = 7.3e-4` — proche du seuil `acceptableCost = 8e-4`.
**Le seuil devient le critère, pas la qualité réelle.**

→ Découpler : seuil acceptable en valeur **absolue** (ex : 5 km) ET
relative.

#### Pondération de l'excentricité

À 1500 km, `e = 0.01` représente 30 km de variation — tolérable. À
200 km, c'est inacceptable car proche de l'atmosphère. Pondération
`W_E = f(targetAlt)` pour pénaliser plus fort à basse altitude.

#### Barrière periapsis floor

100 km est OK pour 400+, dangereux pour 185 km cible (un periapsis à
100 km est en atmosphère dense). À faire dépendre de `targetAlt` :

```
periapsisFloor = max(120 km, targetAlt − 100 km)
```

## 3. Plage de `targetAltitude` à supporter

| Régime | Particularités physiques | Risques de convergence |
|---|---|---|
| **Très bas (185-250 km)** | Densité atmosphérique non négligeable jusqu'à ~150 km. Vitesse circulaire ≈ 7 790 m/s. | Les ratios actuels (`apogeeMax = 0.875·target`) imposent fin de GT à ~162 km → encore dans l'atmosphère. Δv burn 1 minuscule → mauvais conditionnement. Barrière `altMax=1.05·target` très étroite (~9 km de marge à 185 km). |
| **LEO standard (300-800 km)** | Plage validée. Drag négligeable. | OK avec α1 élargi. |
| **LEO haute (800-2000 km)** | Δv Hohmann grandit. Période orbitale grandit. | `dt1MaxPhysical` (propergol) devient saturant. `transitionTime` du GT (borne actuelle 450 s) peut saturer. Burn 2 plus long → l'approximation `dt2` quasi-impulsionnelle peut diverger. |
| **GTO prep (parking 200-400 km, puis injection elliptique)** | Cible non circulaire (rp ~200 km, ra ~35 786 km). | Le coût actuel **suppose une cible circulaire** (`apoAlt`/`periAlt` comparés à `targetAlt`) → incompatible tel quel. Voir `03-robustness-roadmap.md` §7. |
