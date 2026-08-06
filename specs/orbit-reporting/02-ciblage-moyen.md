# 02 — Ciblage en éléments moyens : centrer l'oscillation J2 au lieu de s'y percher

**Date** : 2026-08-05
**Statut** : conception validée ; implémentation à faire
**Précédent** : `orbit-reporting/01-elements-moyens.md` — dont ce chantier est la suite annoncée
(§2 : « l'écart résiduel de ~10 km ne se ferme qu'en **visant** en moyen »)
**Voisins** : `mission-stages/03-garde-rentree.md` (même exigence de non-régression chiffrée)

---

## 1. Le défaut

Les étapes analytiques visent un périgée képlérien deux-corps. La mission insère donc sur une
orbite circulaire **osculatrice**, c'est-à-dire à un **extrême** de l'oscillation J2 courte
période : le périgée volé ne peut plus que descendre, et il descend de toute l'amplitude.

```
ciblage actuel (osculateur), 600 km demandés : 581,2 → 600,6 km volés
ciblage moyen                                : ~590,5 → ~609,5 km volés, centré
```

> **⚠️ Le paragraphe ci-dessous est FAUX. Il est conservé parce qu'il a orienté toute la première
> moitié du chantier, et parce que la mesure qui l'a renversé est le vrai résultat (§5.6).**
>
> ~~**L'amplitude ne change pas.** Aucune orbite n'a d'altitude plate sous J2 : le creux vaut
> `Δr_p ≈ 3·J2·RE²/a` quoi qu'on fasse. Seul le **centrage** est un choix, et c'est le seul objet
> de ce chantier. Il divise l'écart pire-cas par deux.~~

**L'amplitude dépend de l'orbite, et elle s'effondre sur une orbite moyennement circulaire.**
Mesuré le 2026-08-05 : le creux de 19,9 km d'une insertion osculatrice-circulaire tombe à
**1,27 km** quand la même mission insère sur une orbite dont l'excentricité *moyenne* est nulle.

C'est le résultat classique de l'orbite gelée, et le raisonnement qui manquait est celui-ci : le
vecteur excentricité osculateur décrit, sous J2, un petit cercle de rayon ≈ `f` centré sur le
vecteur excentricité **moyen**. Quand ce centre est à l'origine, le module `|e|` reste à `f` et
c'est l'argument du périgée qui tourne — le rayon à une latitude argumentale donnée ne bouge
presque plus. Quand le centre est à `f` de l'origine (cas d'une insertion osculatrice-circulaire),
le cercle **passe par zéro** et `|e|` balaie 0 → 2f, d'où les 2af de creux.

Le centrage n'était donc pas « le seul choix » : c'était le seul choix *visible* tant qu'on
raisonnait sur une amplitude supposée fixe. Le chantier ne divise pas l'écart pire-cas par deux,
il le divise par **vingt**.

### 1.1 Ce qui est déjà mesuré — ne pas re-dériver

Tout le §1.1 du 01 s'applique tel quel : amplitude relative commune à `δa/a` et `δe`,
`f = (3/2)·J2·(RE/a)²`, creux de périgée `Δr_p ≈ 3·J2·RE²/a` vérifié à 1,2 % près sur trois
profils, apogée qui ne bouge que de 57 à 556 m.

S'y ajoute la mesure du 2026-08-05 (`GravityTurnFloorProbeTest#meanVersusOsculatingAtInsertion`)
qui **est** la ligne de base de ce chantier :

| Profil | osculatrice à l'insertion | moyenne | écart moy−cible | `a·f` |
|---|---|---|---|---|
| FH-400 | 400 000 × 400 114 m (e = 8,4e-6) | 390 612 × 409 712 m (e = 1,409e-3) | **−9 388 m** | 9 746 m |
| LEO 200×1000 | 200 000 × 1 000 077 m | 192 637 × 1 009 727 m | **−7 363 m** | 9 467 m |

### 1.2 Le résultat qui fonde toute la conception

**Le périgée moyen est le centre de la bande d'altitude volée.** Ce n'est pas une hypothèse,
c'est une lecture croisée des deux tableaux ci-dessus :

| Profil | excursion du périgée osculateur | son centre | périgée moyen | écart |
|---|---|---|---|---|
| FH-400 | 380 604 → 400 095 m | 390 350 m | 390 612 m | **262 m** |
| LEO 200×1000 | 182 936 → 200 596 m | 191 766 m | 192 637 m | **871 m** |

**La grandeur est l'excursion du périgée osculateur le long du coast, pas la bande d'altitude
volée.** Les deux coïncident sur une cible circulaire et divergent complètement sur une
elliptique : mesurée le 2026-08-05, l'altitude du profil 200×1000 balaie 183 094 → 1 000 573 m,
soit l'ellipse entière, dont le « centre » à 591 833 m ne dit rien de l'oscillation J2. La
première version de la sonde mesurait cela et se trompait de grandeur sur un profil sur deux.

C'est ce qui rend le chantier faisable avec un seul degré de liberté : viser « périgée moyen =
périgée demandé » **est** viser « bande volée centrée sur la demande ». Aucune autre grandeur
mesurée ici n'a cette propriété — ni le demi-grand axe moyen, ni l'apogée moyenne.

L'écart des deux dernières colonnes est **le plancher de précision du critère** : viser le
périgée moyen ne centre la bande qu'à 167 m près sur le circulaire et 1 090 m sur l'elliptique.
C'est le résidu de modélisation d'Eckstein-Hechler (01 §3.2.2), pas une erreur de ciblage — et
c'est la barre contre laquelle tout mécanisme candidat doit être jugé (§3.2.1).

*Si cette identité ne tenait pas, la conception entière s'effondrerait ; c'est pourquoi elle est
le premier falsificateur du §5.*

---

## 2. Périmètre

### 2.1 Une seule étape : `AnalyticTrimBurnStage`

C'est la **dernière poussée qui fixe la forme**, sur les deux profils :

```
LEO  … → Transfert (Hohmann | Transfert-2) → Trim → Coasting
GEO  … → Circularisation apogée → Trim → Plane trim → Coasting
```

Sur GEO le `AnalyticPlaneTrimAtNodeStage` qui suit est une rotation **pure** — il préserve la
norme de la vitesse au nœud, donc il ne touche ni l'énergie ni la forme, seulement le plan
(vérifié en lecture : `vTarget = vTargetDir · |vNode|`). La forme finale du GEO est donc posée
par le **même** `AnalyticTrimBurnStage` que celle du LEO. Une classe couvre les deux.

### 2.2 Écarté — basculer aussi les étapes amont

`AnalyticHohmannTransferStage` (burn 2), `AnalyticApogeeCircularizationStage`,
`AnalyticGtoInjectionStage`, `AnalyticParkingInsertionStage` restent en osculateur.

Chacune vise un état **intermédiaire** que le trim re-mesure et corrige en aval : le trim lit
`stateAtApogee` par `ApsideDetector` sous le champ réel, il ne fait aucune hypothèse sur la
qualité de ce que l'amont lui a livré. Leur décaler l'objectif de 9,4 km ne changerait donc que
l'état depuis lequel le trim planifie — sans rien gagner, et en perturbant la chaîne GEO, dont
la référence du 2026-08-04 est ce qu'on doit précisément préserver.

L'orbite de parking et l'apogée GTO sont par ailleurs des **points de passage**, pas l'orbite de
mission. L'utilisateur n'a jamais demandé un parking « moyen ».

---

## 3. Décisions de conception

### 3.1 Le piège de paramétrage, à nommer avant d'écrire la moindre ligne

Le trim appelle aujourd'hui :

```java
computeTargetVelocityAtApogee(rApo, vApo, mu, rPerigeeTarget, r2 = |rApo|, i)
```

où `rPerigeeTarget` n'entre **que** par `a = (rPerigeeTarget + |rApo|)/2`, puis
`|v| = sqrt(mu·(2/|rApo| − 1/a))`.

Dans le cas circulaire `|rApo| ≈ R_cible`. Monter `rPerigeeTarget` à `R + 9,4 km` donne
`a = R + 4,7 km > |rApo|` : le point de poussée devient le **périgée** de l'orbite obtenue, et
l'autre apside part à `R + 9,4 km`. **Le paramètre cesse de désigner le périgée qu'on obtiendra.**

Ce n'est pas un problème numérique — la relation « paramètre → périgée moyen » reste monotone et
l'inversion 1-D fonctionne. C'est un **nom qui ment**, et il doit être documenté sur place plutôt
que redécouvert plus tard. Le javadoc de la nouvelle API le dit explicitement.

### 3.2 Point fixe Eckstein-Hechler, avec repli sur la formule fermée

La visée est résolue par inversion numérique : partir de la visée nominale, construire l'orbite
osculatrice que cette visée produirait, lire son périgée **moyen**, corriger du résidu. Pente ≈ 1,
trois itérations suffisent.

**Aucune propagation n'est ajoutée** : l'orbite visée est entièrement déterminée en fermé par
`(rApo, vTarget)`. Le coût est de 2 à 3 conversions EH de 5 itérations chacune, hors intégration.

**Eckstein-Hechler et non Brouwer-Lyddane** : voir 01 §3.2.1, où BL a été disqualifié **par la
mesure** — 0/8 convergences à `e = 5e-6`, et un périgée moyen qui s'étale sur 8 216 m selon
l'anomalie d'échantillonnage. Le régime quasi-circulaire est exactement celui que ce chantier
vise. Rien à re-mesurer, la question est close.

**Le repli est ce qui rend la méthode totale.** `OrbitElements.mean()` rend un `Optional` par
construction (01 §3.4 : aucune mission ne doit échouer parce qu'un calcul de compte rendu n'a pas
convergé). Un `Optional` vide fait basculer la visée sur `+ (3/2)·J2·RE²/a`. **Zéro nouveau mode
d'échec sur un chemin de mission** — c'était la seule objection sérieuse à faire entrer le
converter dans le ciblage, et le repli la lève.

Résidu assumé : les ~600 m de biais propre à EH (01 §3.2.2). Trois pour cent d'une correction de
9,4 km — à rapporter au kilomètre, pas au mètre.

#### 3.2.1 Écarté — la formule fermée seule, mais de justesse et pour une raison structurelle

**La première rédaction de ce paragraphe comparait les deux mécanismes sur la mauvaise grandeur.
Elle est corrigée ici, et l'erreur est conservée parce qu'elle est instructive.**

Elle opposait leurs **décalages** (9 746 contre 9 388 ; 9 467 contre 7 363) et en tirait « +29 %,
2,1 km, gain dégradé de 9,5 à 11,6 km sur l'elliptique ». C'est faux : la grandeur qui compte est
le **centrage résiduel** que chacun laisse, et pour cela il faut le décalage réellement requis —
`cible − centre de la bande volée`, pas `cible − périgée moyen`.

Décalages requis **mesurés** le 2026-08-05 (`flownBandCentringAndCost`), pas dérivés :

| Profil | décalage requis | `a·f` | son écart | EH-moyen | son écart |
|---|---|---|---|---|---|
| FH-400 | 9 650 m | 9 746 m | **+96 m** | 9 388 m | **−262 m** |
| LEO 200×1000 | 8 234 m | 9 467 m | **+1 233 m** | 7 363 m | **−871 m** |

Les deux **encadrent** la valeur juste, et le classement **s'inverse d'un profil à l'autre** : la
formule fermée est trois fois meilleure sur le circulaire, l'EH une fois et demie meilleure sur
l'elliptique. Sur les deux profils mesurés, **la formule fermée seule serait un choix
défendable** — ce que la première rédaction niait à tort.

Ce qui départage n'est donc pas la mesure actuelle, c'est le **comportement en excentricité** :

- la formule fermée est une approximation quasi-circulaire, et sa dégradation est mesurée et
  monotone — rapport `span/2af` de **0,983** sur les trois cas circulaires à **0,898** à
  `e = 0,057` (01 §3.2). Extrapolée à un profil plus excentrique, elle décroche ;
- Eckstein-Hechler, mesuré contre le référé sans théorie, fait **−200 m** sur le circulaire et
  **−115 m** sur l'elliptique (01 §3.2) : il ne se dégrade pas avec `e`.

Le point fixe EH est donc retenu pour ce qu'il garantit sur les profils **futurs**, pas pour un
gain sur ceux d'aujourd'hui. Et le corollaire donne au repli du §3.2 sa vraie justification :
**il est acceptable précisément parce qu'il est équivalent sur les profils actuels.**

La formule fermée reste par ailleurs l'**oracle** du test unitaire (§6.2).

#### 3.2.2 Écarté — le point fixe strict, sans repli

Refuser le plan sur non-convergence serait cohérent avec les refus déjà en place
(`DV_SIGN_TOLERANCE`, `requireDeliverable`, la garde de capacité de l'injection GTO) — sauf que
ceux-là signalent une **incapacité du véhicule**. Ici l'échec serait celui d'un calcul de confort :
la mission volerait parfaitement avec l'ancienne visée. Cela donnerait à `MissionLoadEvaluator` un
nouveau chemin « λ infaisable » pour une raison qui n'est pas physique.

### 3.3 Emplacement et API

Nouveau fichier `simulation/MeanPerigeeAim.java`, à côté d'`OrbitElements`. Mêmes raisons qu'au
01 §3.5 : `simulation/orbit/` est le rendu des orbites de corps célestes, et `Physics` rend des
scalaires et des vecteurs, pas des inversions itératives.

```java
/**
 * The value to hand computeTargetVelocityAtApogee as its shaping parameter so the resulting
 * orbit's MEAN perigee lands on the requested altitude.
 *
 * NOT a prediction of the achieved osculating perigee: near-circular the burn point is the
 * perigee and this aim is the far apside (see spec 02 section 3.1).
 *
 * @param targetMeanPerigeeRadius the mean perigee radius the mission asks for (m)
 * @param semiMajorAxisHint semi-major axis for the closed-form fallback (m)
 * @param aimedOrbit maps an aimed shaping radius to the orbit that aim would produce
 */
public static double resolve(double targetMeanPerigeeRadius,
                             double semiMajorAxisHint,
                             DoubleFunction<Orbit> aimedOrbit)
```

`aimedOrbit` est fourni par l'étape appelante : « pour cette visée, voici l'orbite osculatrice que
je volerais ». La géométrie reste dans l'étape, l'inversion moyenne reste dans le helper, et le
helper se teste **sans mission et sans propagation**.

`AnalyticTrimBurnStage` ne gagne qu'un appel : `rPerigeeTarget` devient
`MeanPerigeeAim.resolve(...)`. Aucune autre étape n'est touchée.

### 3.4 Les quatre couplages, et pourquoi trois d'entre eux ne bougent pas

| Site | Décision | Motif |
|---|---|---|
| `AbstractTrajectoryOptimizerTest` — assertions | ~~moyen/moyen~~ → **bande volée** (§5.6.1) | La décision « moyen/moyen » de ce tableau a été prise puis **renversée par la mesure** : sous le centrage complet, la moyenne porte un biais systématique de 9,8 km. Toute convention d'éléments en porte un ; la bande volée n'en porte aucun. |
| `FLOWN_PERIGEE_FLOOR_MARGIN_M` = 40 000 | **mesurer, puis poser** | Prédiction ~20 000 (2× la bande de 9,9 km), mais elle n'est pas écrite avant la sonde du §5. Le 01 s'est déjà fait mordre : « la première version du plan portait 50 m, inventés, et c'est la mesure qui a corrigé la barre ». |
| `MissionLoadEvaluator.objectiveMet` | **inchangé** | Garde de **sécurité et de faisabilité sur la trajectoire volée**, jumelle de `FLOWN_PERIGEE_FLOOR` — pas une mesure de ciblage. Le recentrage ne peut que lui **donner** de la marge (déficit 19,3 → 9,9 km contre une bande de ±7 %). Et le 01 §3.4 a été écrit précisément pour qu'aucun code de compte rendu n'ait de fil vers une décision d'optimiseur : y brancher EH ouvrirait ce fil dans la campagne λ. |
| `OrbitInsertionObjective` | **inchangé** | Il enregistre ce que l'utilisateur a demandé. C'est son *interprétation* qui bascule, et elle vit dans le trim. Une ligne de javadoc le dit. |

**Aucune borne CMA-ES n'est touchée** — il n'y en a aucune dans ce chantier. Rappel de la leçon
enregistrée : Hipparchus normalise l'espace par la largeur de la boîte, donc déplacer une borne
rééchelonne le sigma effectif et change la trajectoire de recherche à graine identique, **même
là où la borne ne mord jamais**. Tout passe par le coût.

---

## 4. Non-régression

**Ce chantier n'est pas neutre en trajectoire, et il ne prétend pas l'être.** Contrairement au 01,
qui était purement additif, celui-ci change ce que la dernière poussée vise. Les orbites volées
changent par construction. « Non-régression » signifie ici : *les références restent atteintes et
les prédictions du §5 tiennent* — pas l'identité au bit.

Toute affirmation de neutralité est formulée comme une **prédiction falsifiable avant le run**,
jamais comme un constat après coup, et vérifiée sur plusieurs profils.

### 4.1 Séquencement — la baseline LEO doit précéder le chantier

La référence λ\* LEO de I7 (0,475) est **périmée** depuis le correctif de latitude du 2026-08-05.
Elle doit être reprise **avant** que ce chantier n'atterrisse, sinon la non-régression LEO n'a
rien contre quoi se mesurer. C'est le seul des quatre longs runs dont le résultat n'est pas
prédit ci-dessous. La référence GEO du 2026-08-04, elle, est fraîche — d'où P3.

---

## 5. Prédictions falsifiables

Posées avant le run, chiffrées, sur deux profils.

### 5.0 Ligne de base mesurée — avant le changement

`GravityTurnFloorProbeTest#flownBandCentringAndCost`, 2026-08-05, `skipped="0"`, ~20 s.

| Profil | excursion périgée osc. | centre | offset requis | pire-cas | min alt. volée | résidu étage dimensionné |
|---|---|---|---|---|---|---|
| FH-400 | 380 604 → 400 095 m | 390 350 m | **9 650 m** | 19 396 m | 380 775 m (−19 225) | 1 044 kg / 2 844 (0,3670) |
| LEO 200×1000 | 182 936 → 200 596 m | 191 766 m | **8 234 m** | 17 064 m | 183 094 m (−16 906) | 55 202 kg / 107 500 (0,5135) |

Tous les chiffres du §5 ci-dessous en découlent. Ceux de la première rédaction étaient **dérivés**
des tableaux du 01 ; ceux-ci sont **mesurés**, et ils diffèrent de quelques centaines de mètres.

### P1 — Centrage

| Profil | excursion aujourd'hui | prédite | pire-cas | min alt. volée prédite |
|---|---|---|---|---|
| FH-400 | 380 604 → 400 095 (centre 390 350) | ~389 992 → ~409 483 (centre ~399 738) | 19,4 → **10,0 km** | ~390 163 (−9 837) |
| LEO 200×1000 | 182 936 → 200 596 (centre 191 766) | ~190 299 → ~207 959 (centre ~199 129) | 17,1 → **9,7 km** | ~190 457 (−9 543) |

**Falsificateur** : si le centre de bande ne tombe pas à ±1,5 km de la cible **sur les deux
profils**, l'identité du §1.2 est fausse et le chantier s'arrête là. Ne pas généraliser depuis un
seul profil : c'est justement l'elliptique qui écarte la formule fermée.

### P2 — Le coût n'est pas nul

Le recentrage **monte l'énergie**. La visée monte de ~9,4 km, mais elle n'entre que par
`a = (visée + |rApo|)/2` et `|rApo|` ne bouge pas : **une seule apside se déplace**, donc
`Δa ≈ +4,7 km` et

```
Δv ≈ (v/2)·(Δa/a) = (7 669/2)·(4 700/6 778 137) ≈ 2,7 m/s   à 400 km
```

C'est ~0,03 % du ΔV total : « quasi nul » est juste, **zéro ne l'est pas**, et les campagnes λ se
décident sur des ratios de résidu. Chiffré depuis la ligne de base du §5.0 :

| Profil | Δa | Δv | masse au trim | baisse de résidu prédite | résidu de base |
|---|---|---|---|---|---|
| FH-400 | +4 825 m | 2,7 m/s | ~15 100 kg | **~12 kg** | 1 044 kg |
| LEO 200×1000 | +4 117 m | 2,4 m/s | ~59 400 kg | **~41 kg** | 55 202 kg |

**Falsificateur** : une baisse supérieure à **3× la prédiction** (36 kg / 123 kg) signifierait que
le mécanisme fait autre chose qu'un recentrage. Noter que les deux résidus de base sont larges —
0,37 et 0,51 du chargement — donc P2 ne menace aucun seuil de faisabilité ; il n'est là que pour
attraper un mécanisme qui déraperait.

### P3 — Le GEO ne bouge pas

`f` varie en `(RE/a)²` : à `a = 42 164 km`, `f = 3,72e-5` et `a·f =` **1 567 m**, contre 9 746 m à
400 km. Le recentrage GEO vaut donc **1,6 km**, soit 4,4e-5 de la cible de 35 786 km, contre une
bande de faisabilité de 7 %.

**Prédiction** : `geoMultiStage` rend **λ\* = [0,934375 ; 0,8140625]**, 28 évaluations, 2 passes,
1 243 619 → 1 160 729 kg, à l'identique.

**Falsificateur** : si λ\* bouge, c'est que la faisabilité était au bord. Cela se regarde — cela ne
se rationalise pas après coup.

### P4 — L'osculatrice à l'insertion cesse d'être circulaire

Par construction : ~`R × R+9,4 km`, `e ≈ 7e-4` au lieu de `8,4e-6`. **Attendu, pas une
régression** — et c'est le corollaire direct du 01 §2.2 : une orbite instantanément circulaire et
une orbite moyennement circulaire sont incompatibles à l'ordre de `f`, il faut choisir laquelle on
veut.

**Point de surveillance** : l'assertion `±7 %` sur l'**apogée moyenne** est celle qui se resserre,
l'apogée moyenne montant de `2·a·e_moyen`. Le chiffre est logué avant qu'une quelconque
modification de tolérance ne soit proposée.

### 5.5 Mesuré après le changement — 2026-08-05

`flownBandCentringAndCost`, `skipped="0"`, ~17 s. Chiffres tels que sortis.

| Profil | excursion périgée osc. | centre | pire-cas | min alt. volée | résidu |
|---|---|---|---|---|---|
| FH-400 | 389 996 → 400 490 m | 395 243 m | **10 004 m** | 390 165 m (−9 835) | 1 035 kg (−9) |
| LEO 200×1000 | 190 306 → 207 971 m | 199 139 m | **9 694 m** | 190 464 m (−9 536) | 55 186 kg (−16) |

Décalages effectivement résolus par le point fixe : **9 405 m** (FH-400) et **7 376 m**
(200×1000), contre 9 388 et 7 363 mesurés en autonome au §1.1 — à 17 et 13 m près.

**Le décalage visé se transmet 1:1 au relèvement du minimum volé** : 9 390 m de gain pour 9 405 m
visés, 7 370 pour 7 376. C'est le contrôle le plus net que le mécanisme fait exactement ce qu'il
dit.

#### 5.5.1 Verdict des prédictions

| Prédiction | Mesuré | Verdict |
|---|---|---|
| P1 pire-cas FH-400 : 10,0 km | 10 004 m | ✅ à 4 m |
| P1 pire-cas 200×1000 : 9,7 km | 9 694 m | ✅ à 7 m |
| P1 min volé FH-400 : 390 163 (−9 837) | 390 165 (−9 835) | ✅ à 2 m |
| P1 min volé 200×1000 : 190 457 (−9 543) | 190 464 (−9 536) | ✅ à 7 m |
| P2 résidu : −12 kg / −41 kg, seuils 36 / 123 kg | −9 kg / −16 kg | ✅ |
| **P1 centre à ±1 500 m** | −4 757 m (FH-400), −861 m (200×1000) | ❌ **déclenché sur FH-400** |

#### 5.5.2 Le falsificateur déclenché — la métrique était fausse, pas le mécanisme

**Cette section existe pour que la conclusion soit justifiée et non affirmée.** « Le
falsificateur s'est déclenché mais ce n'est pas grave » est exactement la rationalisation
a posteriori que le §4 interdit ; voici pourquoi ce n'en est pas une.

Le critère « centre d'excursion à ±1 500 m » supposait une **translation rigide** de l'excursion.
Mesuré, elle ne se translate pas, elle **se resserre** :

| Profil | largeur avant | largeur après |
|---|---|---|
| FH-400 | 19 491 m | **10 494 m** |
| LEO 200×1000 | 17 660 m | 17 665 m |

Sur FH-400 le bord bas monte de 9 392 m — prédit 9 388, soit **4 m d'écart** — tandis que le bord
haut ne bouge que de 395 m. Les quatre prédictions portant sur le bord bas tiennent toutes à moins
de 10 m ; seule celle portant sur le bord haut tombe, et elle entraîne le centre avec elle.

Or **le bord bas est le seul qui compte** : c'est celui qui plonge vers le sol, celui que
`FLOWN_PERIGEE_FLOOR_MARGIN_M` surveille, celui dont l'écart pire-cas est l'objet du chantier. Le
resserrement est un résultat *meilleur* que prédit, pas moins bon.

**Ce qui reste non expliqué, et qui ne doit pas être deviné** : pourquoi l'excursion se resserre
sur le profil circulaire et pas sur l'elliptique. La formule fermée `2af` décrivait l'excursion de
l'orbite d'**avant** (osculatrice circulaire, `e_osc ≈ 0`) ; elle ne décrit plus celle d'après
(`e_osc ≈ 6,9e-4`). Une lecture plausible existe — l'excentricité moyenne n'est plus du même ordre
que `f`, donc le balayage du vecteur excentricité ne passe plus par zéro — mais **elle n'est pas
mesurée**, et elle ne sera pas écrite ici comme si elle l'était.

**Correctif retenu** : le critère de centrage est remplacé, pour les chantiers à venir, par
l'**écart pire-cas du minimum d'altitude volée**. C'est la grandeur que le chantier vise, elle est
définie identiquement sur les deux profils, et elle a été prédite juste à 7 m près.

#### 5.5.3 L'orbite d'insertion après le changement — et l'explication du resserrement

`meanVersusOsculatingAtInsertion`, même run :

| Profil | osculatrice | moyenne | moy − cible |
|---|---|---|---|
| FH-400 | 400 114 × 409 405 m (e = 6,849e-4) | **400 000** × 409 702 m (e = 7,152e-4) | **0 m** |
| LEO 200×1000 | 207 376 × 1 000 077 m | **200 000** × 1 009 718 m | **0 m** |

**Le périgée moyen tombe exactement sur la cible, au mètre, sur les deux profils.** Le point fixe
fait ce qu'il annonce.

**Le resserrement du §5.5.2 s'explique, et il est maintenant mesuré.** L'excentricité **moyenne**
de FH-400 passe de 1,409e-3 (§1.1) à 7,152e-4 : elle est divisée par deux. L'apogée moyenne, elle,
ne bouge pas — 409 712 → 409 702 m. Le recentrage a donc relevé le périgée moyen sans toucher à
l'apogée moyenne, ce qui rend l'orbite moyenne *moins* excentrique qu'avant, d'où une excursion
osculatrice plus étroite. Ce n'était pas prédit ; c'est un bonus, et il est désormais chiffré au
lieu d'être supposé.

#### 5.5.4 Conséquence à assumer : plus aucune convention n'affiche « circulaire »

Pour une demande « 400 km circulaire », après ce chantier :

| convention | ce qu'elle lit |
|---|---|
| osculatrice à l'insertion | 400 114 × 409 405 m — pas circulaire |
| moyenne à l'insertion | 400 000 × 409 702 m — périgée exact, apogée +9,7 km |
| bande d'altitude volée | ~390 → ~410 km, **centrée sur 400** |

Le 01 §2.2 avertissait qu'« une UI qui n'afficherait que la moyenne ferait passer un ciblage
parfait pour un ratage ». La situation est maintenant **symétrique** : aucune des deux conventions
ne rend « 400 × 400 ». C'était le corollaire assumé du chantier — une orbite ne peut pas être
circulaire osculatrice et circulaire moyenne à la fois — mais son ampleur, 9,7 km d'excès
d'apogée moyenne, n'avait pas été chiffrée.

**Conséquence directe pour la Tâche 3 (UI, hors périmètre)** : la grandeur à afficher pour
démontrer que le ciblage est bon n'est plus une orbite d'insertion, c'est la **bande d'altitude
volée et son centre**. C'est la seule des trois lignes ci-dessus qui rende la demande.

**Écarté — viser aussi l'apogée moyenne.** Le trim n'a qu'un degré de liberté (la norme de la
vitesse au point de poussée) ; il ne peut pas poser le périgée *et* l'apogée moyens. Fermer les
9,7 km demanderait une seconde poussée à l'apside opposée, c'est-à-dire un chantier de manœuvre,
pas un chantier de ciblage.

### 5.6 État final — centrage complet, 2026-08-05

**Cette section supersede les §3.2, §3.3 et §5.5 sur le critère visé.** Celles-ci sont conservées
parce que le chemin est instructif : le chantier a changé trois fois de grandeur cible, et les deux
premières étaient fausses pour la même raison.

#### Le critère retenu

`FlownBandAim` (ex-`MeanPerigeeAim`) résout la visée pour que le **demi-grand axe moyen** vaille
`a demandé + a·f`. Justification mesurée sur les suites longues du 2026-08-05 : le centre de la
bande volée vaut `demi-grand axe moyen − 9,1 km` à 230 m près sur deux profils, et ce 9,1 km est
`a·f`. C'est aussi la grandeur la mieux posée pour une poussée à un seul degré de liberté — la
vitesse au point de poussée fixe l'énergie, et l'énergie **est** le demi-grand axe.

Effet de bord non anticipé et décisif : viser ce demi-grand axe rend l'orbite **moyennement
circulaire** (`e_moyen = 1,6e-5` mesuré), donc quasi gelée, donc plate en altitude (§1).

#### Mesuré

| Profil | bande volée avant | après périgée moyen | **après centrage complet** |
|---|---|---|---|
| FH-400 | 380 775 → 400 658 m (19,9 km) | 390 165 → ~400 600 (10,4 km) | **399 680 → 400 953 m (1,27 km)** |
| LEO 200×1000, min volé | 183 094 m (−16 906) | 190 464 m (−9 536) | **199 776 m (−224 m)** |

Insertions correspondantes :

```
[FH-400]       osculatrice 400 114 x 419 337 m (e=1,416e-03)
               moyenne     409 692 x 409 915 m (e=1,645e-05)   ← circulaire en moyen
[200x1000]     osculatrice 216 697 x 1 000 077 m
               moyenne     209 305 x 1 009 707 m
```

Levées de visée : 19 337 m (FH-400) et 16 697 m (200×1000). **Le relèvement du minimum volé suit
la levée 1:1** sur les trois points de mesure du chantier — 9 405 → +9 390, puis 19 337 → +18 905.
C'est le contrôle le plus net que le mécanisme est linéaire et fait ce qu'il dit.

Coût, contre la ligne de base du §5.0 : résidu **−21 kg** (FH-400) et **−55 kg** (200×1000), soit
2 % et 0,1 % des résidus. P2 tenait à 3 m/s ; le centrage complet en coûte le double, et reste
négligeable.

#### 5.6.1 La leçon : arrêter de courir après une convention

Les assertions de `AbstractTrajectoryOptimizerTest` ont changé **trois fois** :

| version | grandeur assertée | pourquoi elle était fausse |
|---|---|---|
| ≤ 2026-08-05 | osculatrice à l'insertion | mesure un instantané, pas l'orbite de mission |
| pendant ce chantier | moyenne à l'insertion | biais systématique de 9,8 km une fois le centrage complet en place |
| **retenue** | **bande d'altitude volée** | — |

Les deux premières partagent le même défaut : **elles assertent un jeu d'éléments contre une
demande exprimée en altitude volée.** Toute convention d'éléments porte un décalage par rapport à
la trajectoire, et changer de convention ne fait que changer le décalage.

La bande volée n'a pas ce défaut : elle ne dépend d'aucune théorie moyenne, elle est définie
identiquement sur toutes les formes d'orbite, et c'est littéralement ce que l'utilisateur a
demandé. **Bonus** : `MissionLoadEvaluator.objectiveMet` lisait déjà exactement cette grandeur —
la barre de précision des tests et la porte de faisabilité des campagnes λ mesurent enfin la même
chose, ce que le §3.4 avait manqué en décidant de ne pas y toucher.

Les deux jeux d'éléments restent **logués**, jamais assertés. Ils diagnostiquent une insertion
propre ; ils ne mesurent pas l'atteinte de l'objectif.

#### 5.6.2 Ce qui n'est pas vérifié

- **Le GEO n'est pas mesuré sous le centrage complet.** `a·f` y vaut 1 567 m, donc l'effet est 12×
  plus petit qu'en LEO, mais aucune sonde ne l'a volé. C'est `GEOMissionOptimizationTest` qui le
  dira.
- **La platitude n'est établie que sur un coast d'un jour sidéral.** Une orbite quasi gelée le
  reste tant que la condition de gel tient ; sur des mois l'argument du périgée dérive et
  l'amplitude revient. Hors périmètre d'une mission, mais à ne pas généraliser.
- **P3 reste falsifiée, avec attribution close côté N2 et ouverte côté λ.** Voir §5.7.

### 5.7 P3 — attribution

λ\* GEO a bougé : [0,9344 ; 0,8141] → [0,9563 ; 0,7922], gain −82 890 → −56 151 kg. La prédiction
était ferme et elle est fausse.

**Mais `AscentBaselineN2Test` exonère le chantier.** Lancé à `HEAD` avec les modifications
remisées (`git stash`), il rend :

```
leo-400 : MECO 36 186,452 kg, transitionTime −0,186530 s
geo     : MECO 64 486,789 kg, transitionTime −0,111935 s
```

**bit-identiques au run avec les modifications**, et tous deux en échec contre un instantané qui
date du 2026-08-03. Le MECO est en amont du trim et `GravityTurnProblem` ne voit jamais le trim :
le chantier ne peut pas l'avoir déplacé, et la mesure le confirme.

Puisque la masse MECO du profil GEO avait **déjà** bougé de 32 kg à `HEAD`, tout le budget
d'ergols en aval différait déjà de la référence du 2026-08-04.

#### 5.7.1 Le chantier ne peut pas déplacer λ\* au GEO — argument structurel

**C'est l'argument qui ferme la question, et il ne coûte aucun run.** La chaîne GEO est :

```
… → GTO injection → S2 separation → Circularization → Trim → Plane trim → Coasting
```

La faisabilité d'un λ repose sur trois lectures (`MissionLoadEvaluator.evaluate`) :

1. **le résidu de l'étage dimensionné** — S2, index 1, lu **au largage**, donc **deux phases avant
   que le trim ne s'exécute**. Aucun réglage du trim ne peut le modifier ;
2. **`objectiveMet`** — min/max d'altitude du coast final contre la cible, avec la tolérance de
   ~50 km que le test GEO passe en `feasibilityObjective`. Le chantier déplace ce minimum de
   **0,78 km** (§5.8.1) : deux ordres de grandeur sous le seuil ;
3. **`ephemerisComplete`** — le trim consomme 140,9 kg d'un AKM qui en garde 470 sur 2 000.

**Confirmé empiriquement, deux fois.** λ\*=[0,95625 ; 0,7921875] / 29 évals / −56 151 kg / résidu
S2 204 kg, **chiffre pour chiffre identique** sous le ciblage périgée-moyen (levée ~1,6 km au GEO)
et sous le centrage complet (levée ~3,1 km). Deux visées différentes, un même λ\*.

**Conclusion : P3 est falsifiée par une cause extérieure au chantier.** L'erreur retenue n'est pas
d'avoir déplacé λ\*, c'est d'avoir **posé une prédiction ferme sur une référence dont je n'avais
pas vérifié la fraîcheur**, alors que la péremption de la référence LEO jumelle était écrite noir
sur blanc dans le brief. La règle à en tirer : avant de prédire l'invariance d'une référence, en
dater la dernière validation et la comparer aux commits qui ont suivi.

### 5.8 Suites longues sous centrage complet — 2026-08-06

Lancées par l'utilisateur. **Les trois prédictions du §5.6 tiennent** : 600/600 min prédit
~599,7 km → mesuré 599,690 (10 m) ; 600/800 min prédit ~600,000 → mesuré 600,005 (5 m) ; GEO
déplacement prédit +0,55 km → mesuré +0,78 km (230 m).

| Profil | déficit de périgée volé avant | après |
|---|---|---|
| `LEOMissionOptimizationTest` 600/600 | 9 664 m | **310 m** |
| `LEOMissionOptimizationTest` 600/800 | 9 424 m | **−5 m** (excès) |
| `LEOMissionOptimizedTransferTest` 600/600 | 9 591 m | **4 m** |
| `LEOMissionOptimizedTransferTest` 600/800 | 9 405 m | **−31 m** |

Insertions moyennes correspondantes : `609 392 × 609 631 m (e = 1,707e-05)` et
`609 642 × 610 151 m (e = 3,642e-05)` — moyennement circulaires, donc quasi gelées, ce qui est le
mécanisme du §1. Coût sur `leo-400` (snapshot N2) : trim 37,7 → 62,6 kg, ΔV 3,7 → 6,1 m/s.

**`leoMultiStage` est bit-identique** : λ\*=[0,9125 ; 0,6828], 28 évals, 1 234 963 → 1 126 453 kg
(−108 510 kg, −8,8 %). Seuls le résidu S2 (52 → 49 kg) et l'orbite finale (a 6 783,3 → 6 788,3 km,
e 0,00224 → 0,00151) bougent, dans le sens attendu. **La campagne λ LEO est insensible au
chantier.**

#### 5.8.1 Régression assumée : le GEO recule de 783 m

```
avant : min 35 785 448  max 35 787 231   centre 35 786 340   pire-cas 1 231 m
après : min 35 786 230  max 35 788 014   centre 35 787 120   pire-cas 2 014 m
```

Le GEO était **déjà bien centré** (340 m) et la correction l'a poussé à 1 120 m.

**Cause : un régime différent, pas un défaut d'implémentation.** En LEO la bande volée valait
19,9 km pour un `a·f` de 9,7 km — la correction est petite devant ce qu'elle corrige (rapport 2).
Au GEO la bande vaut 1,78 km pour un `a·f` de 1,57 km (rapport 1,1) : la correction est de l'ordre
de la bande entière, et le développement au premier ordre qui la justifie n'y a plus de marge.

**Laissé en l'état, délibérément.** 783 m sur 35 786 km valent 2,2e-5 de la cible, contre une
tolérance de 7 % ; la masse finale bouge de 20 g (2 470,391 → 2 470,371 kg) et l'inclinaison de
2e-9 degré. Corriger demanderait de modéliser le décalage moyen↔volé au-delà du premier ordre,
pour un gain invisible — mais **ce n'est pas du bruit, c'est une dégradation, et elle est
consignée comme telle.** Si un chantier futur resserre les tolérances GEO, c'est ici qu'il faut
revenir.

#### 5.8.2 Non vérifié à cette date

- **`geoMultiStage` relancé, identique** : λ\*=[0,95625 ; 0,7921875], 29 évals, 2 passes,
  −56 151 kg, résidu S2 204 kg (2,4 %) — les mêmes chiffres que sous le ciblage périgée-moyen.
  Voir §5.7.1 : c'est ce que la structure de la chaîne imposait.
- **`AscentBaselineN2Test` échoue à l'identique**, MECO au bit près — conforme au §5.7. Son
  `final perigee` sur `leo-400` passe de 390 446,7 à **400 314,7 m**, ce qui vérifie le chantier
  sur un cinquième profil au passage.

---

## 6. Vérification

1. **Sonde, avant toute implémentation.** Étendre `meanVersusOsculatingAtInsertion`
   (`GravityTurnFloorProbeTest`, opt-in `-Dorbitlab.probe=true`) pour imprimer aussi le **centre
   de la bande volée** et le résidu d'ergols. Fixe la ligne de base de P1 et P2 en quelques
   secondes, contre plusieurs minutes par run d'optimisation.
2. **Test unitaire `MeanPerigeeAim`** — orbites synthétiques, ni mission ni propagation :
   convergence sous 1 000 m (le plancher de résidu EH du 01 §3.2.2), **déclenchement effectif du
   repli** sur un `mean()` indisponible, monotonie de la visée.
3. **Sonde après implémentation** — mêmes profils, mêmes variables retenues
   (`FH_400_RETAINED`, `ELLIPTIC_RETAINED`) : bande volée et résidu confrontés à P1 et P2.
   **C'est l'étape de falsification**, et elle coûte des secondes.
4. **Poser `FLOWN_PERIGEE_FLOOR_MARGIN_M`** depuis la mesure de l'étape 3. Pas avant.
5. **Suites courtes** : `OrbitElementsTest`, `AchievedOrbitTest`, `MeanPerigeeAimTest`,
   `StageSeparationStageTest`, `DepletionGuardTest`, `ReentryGuardTest`. Vérifier `skipped="0"`
   dans `build/test-results/test/` — sans quoi un vert ne prouve rien.
6. **Côté utilisateur** (runs longs, non lancés par l'assistant) : `LEOMissionOptimizationTest`,
   `LEOMissionOptimizedTransferTest`, `GEOMissionOptimizationTest`,
   `PropellantLoadOptimizerIntegrationTest`. S'y ajoute `AscentBaselineN2Test`, opt-in derrière
   `-Dorbitlab.slowTests=true`.

### 6.1 Ce qui a été vérifié — 2026-08-05

| Suite | Tests | Résultat |
|---|---|---|
| `MeanPerigeeAimTest` | 5 | vert, `skipped="0"` |
| `OrbitElementsTest` | 4 | vert, `skipped="0"` |
| `AchievedOrbitTest` | 2 | vert, `skipped="0"` |
| `StageSeparationStageTest` | 5 | vert, `skipped="0"` |
| `DepletionGuardTest` | 2 | vert, `skipped="0"` |
| `ReentryGuardTest` | 5 | vert, `skipped="0"` |
| `GravityTurnReplayConsistencyTest` | 7 | vert, `skipped="0"` |
| `MissionAscentWiringTest` | 8 | vert, `skipped="0"` |
| `MissionFactoryTest` | 6 | vert, `skipped="0"` |
| `MissionLoadEvaluatorTest` | 13 | vert, `skipped="0"` |
| `AnalyticGtoInjectionStageTest` | 3 | vert, `skipped="0"` |
| `GravityTurnFloorProbeTest` (2 sondes) | 2 | vert, `skipped="0"` |

`MeanPerigeeAimTest` porte cinq propriétés et non quatre : la cinquième,
`theFixedPointImprovesOnItsClosedFormSeed`, existe parce que rien d'autre ne distinguait « le
point fixe a convergé » de « il est silencieusement retombé sur sa graine ». Sans elle le
raffinement Eckstein-Hechler aurait pu être du code mort sans qu'aucun test ne rougisse.

### 6.2 Ce qui reste à faire, côté utilisateur

Les quatre suites longues, plus la reprise de la baseline λ\* LEO exigée au §4.1. La prédiction à
confronter est P3 : `geoMultiStage` doit rendre λ\* = [0,934375 ; 0,8140625] à l'identique.
