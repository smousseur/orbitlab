# 01 — Éléments moyens : rapporter l'orbite de mission, pas l'osculatrice instantanée

**Date** : 2026-08-05
**Statut** : implémenté ; non-régression des suites longues **à confirmer** (§5.3)
**Voisin** : `mission-stages/03-garde-rentree.md` (même exigence d'invariance numérique)

---

## 1. Le défaut

OrbitLab rapporte des éléments **osculateurs instantanés** là où l'utilisateur a demandé une
**orbite de mission**. Les deux grandeurs sont justes ; elles ne sont simplement pas la même
chose, et c'est la seconde que l'utilisateur a saisie dans l'assistant.

Rien n'est faux dans la trajectoire. Le chantier est **purement additif** : il ne touche
aucune phase, aucune manœuvre, aucun ciblage.

### 1.1 Ce qui est déjà mesuré (2026-08-05) — ne pas re-dériver

Sous J2, le demi-grand axe **et** l'excentricité osculateurs oscillent en phase avec la même
amplitude relative :

```
f = (3/2)·J2·(RE/a)²
```

Au périgée `δa` et `a·δe` s'ajoutent, à l'apogée ils se compensent :

```
r_p = a(1−e)  →  −2af          r_a = a(1+e)  →  0
```

soit `Δr_p ≈ 3·J2·RE²/a`. Vérifié sur trois profils :

| Profil | f | Creux prédit | Creux mesuré | Écart | Apogée mesurée |
|---|---|---|---|---|---|
| FH 400 km | 1,4379e-3 | 19 493 m | 19 282 m | +1,1 % | +57 m |
| 600 km | 1,3567e-3 | 18 934 m | 18 854 m | +0,4 % | +556 m |
| 1000 km | 1,2136e-3 | 17 908 m | 17 700 m | +1,2 % | +455 m |

**L'apogée prédite vaut 0 m dans les trois cas, et c'est la signature qui compte** : un
défaut de ciblage déplacerait l'orbite entière, périgée *et* apogée. Seul le périgée bouge.
La cause est le terme J2 du champ de gravité, pas la mission.

Insertions mesurées, toutes excellentes :

| Cible | Insertion osculatrice | e | Coast |
|---|---|---|---|
| 600 km | 600 000 × 600 088 m | 6,34e-6 | 581,190 → 600,600 km |
| 1000 km | 1 000 000 × 1 000 068 m | 4,66e-6 | 982,334 → 1000,489 km |
| 200 / 1000 km | 200 000 × 1 000 077 m | — | min 183 094 m |

**Ce n'est pas l'aplatissement géométrique.** L'altitude géodésique au-dessus de l'ellipsoïde
ne vaut que ~0,18 km d'écart à 5,23° d'inclinaison (Kourou, `EarthMission.DEFAULT_LATITUDE`).
Deux ordres de grandeur en dessous.

### 1.2 Où la grandeur est effectivement rapportée aujourd'hui

Inventaire fait avant de décider du périmètre :

| Site | Ce qu'il rapporte aujourd'hui |
|---|---|
| `AbstractTrajectoryOptimizerTest` l.108-157 | log « Insertion orbit » + les deux assertions ±7 % — **osculateur** |
| `MissionOptimizer.logReport` | masses, ΔV, ergols. **Aucun élément orbital** |
| `MissionPerformanceReport` | idem — aucun élément orbital |
| `TelemetryWidget` | MET / ALT / VEL / MASS. **Aucun élément orbital** |
| `ui/mission/display` | sous-titre = nom du corps central (`EARTH`) |

**Conséquence sur le périmètre.** L'écart de ~19 km ne se manifeste aujourd'hui que dans les
logs et les assertions de test. Côté UI il n'y a rien à corriger : afficher une orbite y
serait une **fonctionnalité neuve**, pas une correction. Elle est donc hors de ce chantier.

---

## 2. Ce que le chantier ferme, et ce qu'il ne ferme pas

C'est le point le plus important du document, et il a été posé avant l'écriture de la moindre
ligne.

Les étages analytiques visent un périgée **osculateur**, et ils le touchent au mètre (§1.1).
L'insertion tombe apparemment en haut de l'oscillation — le creux de −19 km n'a pas de
remontée symétrique au-dessus de la cible. Le centre de l'oscillation, c'est-à-dire la
moyenne, se lit alors autour de `cible − a·f` :

| Profil | a·f (moitié du creux) |
|---|---|
| 400 km | ≈ 9 750 m |
| 600 km | ≈ 9 470 m |
| 1000 km | ≈ 8 950 m |

Donc :

- **rapporter la moyenne rend le chiffre stable et honnête** — il ne dépend plus de l'instant
  d'échantillonnage, et il cesse d'osciller de 19 km pendant le coast ;
- **rapporter la moyenne ne fait pas coïncider le chiffre affiché avec le chiffre demandé.**
  L'écart résiduel de ~10 km ne se ferme qu'en **visant** en moyenne, ce qui déplacerait les
  trajectoires et sort du périmètre.

Ce chantier produit exactement la grandeur qui manque pour instruire ce futur chantier de
ciblage : l'écart osculateur↔moyen mesuré à l'insertion, profil par profil.

### 2.1 Mesuré le 2026-08-05 — la prédiction tient, et elle en révèle une autre

Sonde `GravityTurnFloorProbeTest#meanVersusOsculatingAtInsertion`, deux profils calibrés, 10,6 s :

```
[M/FH-400]       osculatrice : 400000 x  400114 m (e=8,423e-06)
                 moyenne     : 390612 x  409712 m (e=1,409e-03)
                 osc−cible = 0 m | moy−cible = −9 388 m | a·f = 9 746 m

[M/LEO-200x1000] osculatrice : 200000 x 1000077 m (e=5,733e-02)
                 moyenne     : 192637 x 1009727 m (e=5,854e-02)
                 osc−cible = 0 m | moy−cible = −7 363 m | a·f = 9 467 m
```

**Le signe est négatif sur les deux profils, comme prédit.** L'amplitude vaut 96 % de `a·f` sur
FH-400 et 78 % sur l'elliptique — l'écart du second est cohérent avec le fait que la formule
fermée est une approximation quasi-circulaire (§3.2). Et `osculatrice − cible = 0 m` sur les deux
confirme que le ciblage est parfait au mètre.

### 2.2 Le résultat non anticipé : une orbite circulaire n'a pas de moyenne circulaire

À l'insertion FH-400 l'orbite osculatrice est circulaire — `e = 8,4e-6`. La **moyenne** du même
état porte `e = 1,409e-3`, c'est-à-dire ≈ `f`. Ce n'est pas un artefact : le référé par moyennes
équinoxiales avait rendu 1,4114e-3 sur un cas équivalent, par un chemin indépendant.

**Une orbite instantanément circulaire et une orbite moyennement circulaire sont incompatibles à
l'ordre de `f`.** Conséquence directe pour ce qui est affiché :

| grandeur | ce qu'elle affiche pour « 400 km circulaire » |
|---|---|
| osculatrice à l'insertion | 400,0 × 400,1 km — la demande, au mètre |
| **moyenne** | **390,6 × 409,7 km — ni à l'altitude, ni circulaire** |
| minimum géodésique volé pendant le coast | ~381 km |

Rapporter la moyenne **à la place** de l'osculatrice ferait donc *empirer* la lecture : elle
donnerait l'air d'un raté de 10 km sur une orbite non circulaire, là où l'osculatrice dit
exactement ce qui a été demandé. La décision du §3.1 — les deux, assertions sur l'osculatrice —
protège de ça ; elle avait été prise pour une autre raison et se trouve validée par celle-ci.

Le corollaire pour la Tâche 3 (UI, hors de ce chantier) : **une UI qui n'afficherait que la
moyenne ferait passer un ciblage parfait pour un ratage.** Si une orbite est affichée, elle doit
l'être dans les deux conventions, ou dans l'osculatrice seule.

---

## 3. Décisions de conception

### 3.1 La moyenne **en plus** de l'osculatrice, jamais à la place

Les deux conventions ont un sens et aucune ne subsume l'autre :

- l'**osculatrice au point d'injection** est la convention de précision d'un lanceur. C'est
  elle qui prouve aujourd'hui que le ciblage est bon *au mètre* ;
- la **moyenne** est la convention d'une orbite de mission, celle que l'utilisateur a saisie.

Logs et rapports portent donc les deux. Les assertions d'insertion restent sur
l'osculatrice.

**Écarté — basculer les assertions sur la moyenne.** Cohérent avec l'intention, mais pas
neutre : contre une cible osculatrice, la moyenne introduit un biais systématique de ~10 km
(§2). Sur le profil 200/1000, où ±7 % = 14 000 m, la marge tomberait de 14 km à ~4 km. On
ferait passer un test vert au bord du rouge pour un défaut qui n'est pas dans la trajectoire.
À rouvrir **après** la mesure du §5.1, et seulement en même temps que le ciblage.

**Écarté — la moyenne seule.** Perd la seule grandeur qui démontre la qualité du ciblage.

### 3.2 Eckstein-Hechler en implémentation, formule fermée en oracle de test

**Cette décision a été prise deux fois. La première version était fausse et la mesure l'a
renversée ; elle est conservée en §3.2.1 parce que l'erreur est instructive.**

`EcksteinHechlerTheory` via `FixedPointConverter`, avec un provider de **degré ≥ 6** —
`GravityFieldFactory.getUnnormalizedProvider(6, 0)`. Le degré 5 lève
« no term (6, 0) in a 5x0 spherical harmonics decomposition » : la théorie a besoin du terme
C60.

Mesuré le 2026-08-05 contre un référé sans théorie (moyennes équinoxiales des éléments
osculateurs sur une période, champ 8×8, 720 échantillons) :

| cas | référé | Eckstein-Hechler | écart | Brouwer-Lyddane | écart |
|---|---|---|---|---|---|
| circ-400 | 390 558 m | 390 349 m | −209 m | 398 073 m | +7 515 m |
| circ-600 | 590 822 m | 590 623 m | −199 m | 598 189 m | +7 368 m |
| circ-1000 | 991 310 m | 991 127 m | −183 m | 998 388 m | +7 078 m |
| ellip 200×1000 | 198 896 m | 198 781 m | −115 m | 198 845 m | −51 m |

EH converge 8/8 sur toute la grille en 5 itérations, round-trip sous le micromètre, et rend des
résultats identiques au bit qu'on lui passe du képlérien ou de l'équinoxial. Les ~200 m
résiduels s'expliquent vraisemblablement par la différence de modèle — le référé vole sous 8×8
avec ses tesséraux, EH n'intègre que des zonaux jusqu'à J6. C'est une lecture, pas une mesure.

La formule fermée `f = (3/2)·J2·(RE/a)²` reste l'**oracle** du test unitaire (§5.2), avec une
limite désormais mesurée : le rapport `span mesuré / 2af` vaut 0,983 sur les trois cas
circulaires (accord à 1,7 %) mais 0,898 sur l'elliptique. C'est une approximation d'orbite
quasi-circulaire, et l'oracle ne doit s'en servir que là.

#### 3.2.2 Le résidu — ce que la conversion ne retire pas

**EH retire ~97 % de l'oscillation courte période, pas 100 %.** Mesuré le 2026-08-05 sur une
série osculatrice d'une période : l'oscillation du périgée passe de 19 108 m à **625 m** à
400 km (571 m à 600 km, 482 m à 1000 km).

Ce n'est pas un défaut de convergence : à seuil 1e-15 et 5 000 itérations, le résultat est
identique au bit. C'est le résidu de modélisation propre à Eckstein-Hechler.

**Conséquence à assumer** : le périgée moyen rapporté dépend encore de l'instant
d'échantillonnage, à hauteur de ~600 m. C'est 3 % des 19 km que le chantier corrige — donc un
chiffre à rapporter au kilomètre, pas au mètre. Le seuil du test (§5.2) est posé à 1 000 m sur
cette mesure, et non sur une valeur ronde choisie a priori : la première version du plan portait
50 m, inventés, et c'est la mesure qui a corrigé la barre.

**Écarté — la formule fermée en implémentation.** Elle se trompe de 10 % sur le profil
elliptique 200×1000, qui est un profil de test réel.

#### 3.2.1 Pourquoi Brouwer-Lyddane est disqualifié — et pourquoi je m'étais trompé

La première version de ce paragraphe retenait `BrouwerLyddanePropagator.computeMeanOrbit` au
motif que « c'est précisément l'apport de Lyddane d'avoir levé la singularité `e → 0` de
Brouwer ». **L'affirmation est vraie mais hors sujet** : Lyddane lève la singularité dans le
sens moyen→osculateur. `computeMeanOrbit` fait l'**inversion**, par point fixe sur les éléments
képlériens — or à `e → 0` l'argument du périgée `ω` et l'anomalie moyenne `M` ne sont pas
déterminés séparément, seule leur somme l'est. Le point fixe oscille.

Mesuré, à `a = RE + 400 km`, `i = 5,23°` :

| e | convergence |
|---|---|
| 5e-6 | 0/8 |
| 1e-4 | 2/8 |
| 1e-3 | 5/8 |
| ≥ 5e-3 | 8/8 |

Relâcher le seuil à 1e-6 et le plafond à 20 000 itérations ne change rien : c'est une
divergence franche, pas une convergence lente. L'amortissement (`FixedPointConverter` à 0,5 puis
0,1) ne change rien non plus, et itérer en éléments équinoxiaux — pourtant non singuliers à
`e → 0` — ne rend que 1/8.

**Le résultat rédhibitoire est ailleurs.** Balayage de l'anomalie vraie sur 16 valeurs, à `a`,
`e`, `i`, `ω`, `Ω` fixes : BL converge 4 fois, et parmi celles-là le périgée moyen s'étale de
389 857 à 398 073 m — **8 216 m d'écart selon le point de l'orbite où l'instantané a été pris**.
Une orbite moyenne qui dépend du point d'échantillonnage n'est pas une orbite moyenne. BL n'est
pas « peu pratique ici », il est faux ici.

**Piège de mesure à ne pas refaire.** Le premier référé moyennait l'excentricité **scalaire** et
rendait `e = 0,00181` pour une orbite partie à `e = 5e-6`. Ce n'était pas un bug : `e` est une
norme, donc ≥ 0, et la moyenne de `|e|` près du circulaire est structurellement positive même
quand l'excentricité moyenne vraie est nulle. Le facteur mesuré, 0,00181/0,001425 = 1,26, est
celui qu'on attend pour la moyenne d'une norme. Il faut moyenner les composantes
**équinoxiales**, qui sont signées. Le biais valait 2,5 km de périgée sur les cas circulaires —
assez pour faire croire que toutes les théories étaient fausses.

### 3.3 Deux conventions à verrouiller, sinon les deux lignes ne sont pas comparables

1. **Altitudes** : `a(1±e) − WGS84_EARTH_EQUATORIAL_RADIUS`, exactement comme aujourd'hui.
   Pas de bascule vers le géodésique au passage — ce serait un second changement de grandeur
   dans un chantier dont tout l'objet est de clarifier laquelle est rapportée.
2. **µ** : l'orbite passée à BL est construite avec le **µ du provider**, pas
   `WGS84_EARTH_MU`. Mélanger les deux introduit un écart parasite de l'ordre du mètre qu'on
   prendrait pour du J2.

### 3.4 Mode dégradé : `Optional`, jamais d'exception qui remonte

La conversion est un point fixe itératif ; elle peut ne pas converger. **Une mission ne doit
jamais échouer parce qu'un log n'a pas pu être calculé.** `mean()` rend donc un
`Optional<OrbitElements>` et l'appelant loggue « unavailable » le cas échéant.

C'est la clause qui garantit qu'aucun chemin de mission ne gagne un mode d'échec — sans elle
le chantier ne serait plus purement additif.

**L'invariant est garanti à la frontière, pas au fond** (correctif issu de la revue finale,
2026-08-05). La première implémentation ne l'appliquait qu'à l'intérieur de `mean()` ;
`AchievedOrbit.of` et `OrbitElements.osculating` restaient hors de toute garde. Or
`MissionLoadEvaluator` traduit **toute** `RuntimeException` sortie d'`optimize()` en
« λ infaisable » : une exception échappée d'une ligne de compte rendu aurait déplacé le λ retenu
par la campagne d'échelonnement, sans qu'aucune erreur ne remonte — précisément le « chiffre de
mission qui bouge » que le §4 interdit.

Rien ne pouvait lever aujourd'hui, c'était donc latent. Mais c'est le seul endroit du chantier où
du code de compte rendu a un fil vers une décision d'optimiseur, et un invariant qui repose sur
« aujourd'hui rien ne lève » n'est pas un invariant. `AchievedOrbit.of` est donc **total** : il
attrape et rend `AchievedOrbit.UNAVAILABLE`, dont les deux conventions sont vides.

### 3.5 Emplacement : `simulation/`, à côté de `Physics` et `OrekitService`

```
simulation/OrbitElements.java   record (a, e, i, perigeeAltitude, apogeeAltitude)
                                + osculating(Orbit)
                                + mean(Orbit) → Optional<OrbitElements>
                                + format()
```

Et, pour que l'UI puisse un jour lire le résultat sans le recalculer, le compte rendu est **porté
par le résultat de calcul**, pas seulement logué :

```
mission/runtime/AchievedOrbit.java   record (OrbitElements osculating,
                                             Optional<OrbitElements> mean)
                                    + of(SpacecraftState)
                                    + formatMean()
```

`MissionComputeResult` gagne un cinquième composant `achievedOrbit`. Le record n'est construit
qu'à un seul endroit (`MissionOptimizer`), vérifié avant modification ; partout ailleurs il n'est
que lu. C'est le point d'accès public demandé pour la Tâche 3 (UI).

**Écarté — `simulation/orbit/`.** Ce package est celui du rendu des orbites de corps célestes
(`OrbitPath`, `OrbitPolicy`, `OrbitPathCache`, `OrbitSnapshot`), pas de la mécanique.

**Écarté — une méthode de plus dans `Physics`.** `Physics` rend des scalaires et des
vecteurs ; ici on introduit un type de données, qui mérite son fichier.

### 3.6 Deux points d'ancrage

| Site | État lu | Loggué |
|---|---|---|
| `AbstractTrajectoryOptimizerTest` | insertion = 1er échantillon du coast (**inchangé**) | osculatrice **et** moyenne |
| `MissionOptimizer` l.238 | `mission.getCurrentState()` = état final | osculatrice **et** moyenne |

Le second est gratuit : à la l.237 l'état final est déjà en main, aucune plomberie, aucun
appariement de nom de phase.

**Et il fournit un contrôle croisé.** Entre l'insertion et la fin du coast, l'osculatrice doit
avoir oscillé de ~19 km pendant que la moyenne ne bouge que de la dérive séculaire. Si la
moyenne bouge de 19 km elle aussi, le converter est faux et on le voit sans rien mesurer de
plus.

---

## 4. Non-régression

**Contrainte ferme** : aucune trajectoire ne change. Si un chiffre de mission bouge, c'est un
bug du chantier.

Le chantier n'ajoute que des lectures : un record immuable, une conversion hors intégration,
des lignes de log. Rien n'est armé sur un propagateur, rien n'entre dans une fonction de coût,
aucune borne CMA-ES n'est touchée — **rappel de la leçon enregistrée** : déplacer une borne
renormalise l'espace de recherche Hipparchus et change la trajectoire à graine identique, même
là où la borne ne mord pas. Ici il n'y a pas de borne du tout.

`OrbitInsertionObjective` reste intact : il demeure la cible **osculatrice** des étages
analytiques.

Résultats attendus : **bit-identiques**, pas « proches ».

Références à préserver (à relancer côté utilisateur, ces tests sont longs) :

| Profil | Référence |
|---|---|
| `geoMultiStage` | λ*=[0,9344 ; 0,8141] / 28 évals / 2 passes / −82 890 kg / ~6 min |
| LEO simple-λ | λ*≈0,4313 |
| LEO FH-400, 600, 1000, 200×1000 | insertions du §1.1, au mètre |

---

## 5. Vérification

### 5.1 Étape 0 — la sonde, avant toute autre écriture

Nouveau `@Test` read-only dans `GravityTurnFloorProbeTest` (opt-in `-Dorbitlab.probe=true`,
quelques secondes). Sur les deux profils dont les variables retenues sont déjà enregistrées —
`FH_400_RETAINED` (circulaire) et `ELLIPTIC_RETAINED` (200×1000) — il imprime, à l'insertion :

- l'osculatrice ;
- la moyenne BL ;
- la prédiction fermée `a·f` ;
- l'écart signé osculateur↔moyen.

C'est la mesure qui valide ou infirme le §2, **avant** qu'une décision sur les assertions ne
soit rouverte. Coût : quelques secondes, contre plusieurs minutes par run d'optimisation.

### 5.2 L'oracle — test unitaire rapide et permanent

Sur des orbites **synthétiques** circulaires à 400 / 600 / 1000 km, i = 5,23°, e = 5e-6 : pas de
mission, pas de propagation numérique, quelques dizaines de millisecondes.

La série osculatrice est engendrée par un `EcksteinHechlerPropagator` : on ne peut pas fabriquer
l'oscillation J2 en changeant l'anomalie d'un jeu d'éléments figés, elle naît de la propagation.

**Le flag doit être `PropagationType.OSCULATING`.** Mesuré : avec `MEAN` la série sort quasiment
plate — 150 m d'amplitude contre 19 493 m prédits — et l'oracle serait vide de sens. La première
version du plan portait `MEAN` ; c'est la mesure qui a corrigé la consigne.

Deux propriétés se mesurent alors sans deviner aucune phase :

1. l'amplitude crête-à-crête du périgée **osculateur** vaut `2·a·f` — accord mesuré à 2 % sur les
   cas circulaires (rapport 0,980) ;
2. le périgée **moyen** rendu par le converter reste sous le plafond de résidu de 1 000 m établi
   au §3.2.2.

La seconde est celle qui casse bruyamment si le converter rend de l'osculateur déguisé : elle
laisse passer les ~600 m de résidu réel tout en restant 19 fois sous l'oscillation de 19 km
qu'elle doit attraper. La première ne vaut que sur le quasi-circulaire (§3.2) : ne pas l'étendre
au profil elliptique, où la formule fermée se trompe de 10 %.

`Assumptions` sur `orekit-data.zip` comme les autres suites, et vérification que `skipped="0"`
dans `build/test-results/test/` — sans quoi un vert ne prouve rien.

### 5.3 Ce qu'il reste à faire, côté utilisateur

Relancer les suites longues pour confirmer l'invariance du §4 : `LEOMissionOptimizationTest`,
`GEOMissionOptimizationTest`, `PropellantLoadOptimizerIntegrationTest`. **C'est la seule
vérification qui manque**, et c'est la seule qui exerce `MissionOptimizer.optimize()` de bout en
bout.

Références à retrouver à l'identique : λ*=[0,9344 ; 0,8141] / 28 évals / 2 passes / −82 890 kg sur
`geoMultiStage`, λ*≈0,4313 sur le LEO simple-λ, et les insertions osculatrices du §1.1 au mètre.

### 5.4 Ce qui a été vérifié

| Suite | Tests | Résultat |
|---|---|---|
| `OrbitElementsTest` | 4 | vert, `skipped="0"` |
| `AchievedOrbitTest` | 2 | vert, `skipped="0"` |
| Revue finale indépendante | — | conformité au §4 vérifiée par lecture, bytecode et exécution |
| `GravityTurnFloorProbeTest#meanVersusOsculatingAtInsertion` | 1 | vert, `skipped="0"`, 10,6 s |
| `StageSeparationStageTest` / `DepletionGuardTest` / `ReentryGuardTest` | 12 | verts, `skipped="0"` |

`AchievedOrbitTest` existe pour une raison précise : `AchievedOrbit.of` n'est appelé que depuis
`MissionOptimizer.optimize()`, donc **aucune suite courte ne l'atteignait**. Il rejoue la même
lecture sur un état construit à la main, en une seconde, et verrouille au passage le piège du
§2.2 sur une orbite synthétique — indépendamment de la mesure sur mission réelle.
