# PHY-4 / L0 — Baseline mesurée

Lot **L0** du découpage (`01-decoupage.md` §4). Ce document ne contient aucune
décision et aucun code : il **consigne des chiffres**, mesurés avant que quoi que
ce soit ne bouge, pour que les lots suivants puissent prouver qu'ils n'ont rien
déplacé.

Les logs bruts dont chaque nombre est extrait sont dans `baseline/`. Ils sont
versionnés avec ce document : sans eux, un chiffre qui bouge en L1 ne dit pas
*où* il a bougé.

---

## 1. Conditions de mesure

| | |
|---|---|
| Date | 2026-08-16, 20 h 46 – 21 h 50 |
| Commit | `8f54206` « Prepare PHY-4 » — arbre propre |
| JDK | GraalVM 21.0.5, assertions actives (`-ea`) |
| Lancement | IntelliJ, un test par JVM (pas de `./gradlew test` global) |
| Graine CMA-ES | 42, explicite, sur les quatre profils optimisés |
| Époque de mission | `2026-01-01T12:00:00.000Z` |
| Budget d'évaluations | 40 000 |

Les mesures viennent de deux sources de nature différente, et la distinction est
la chose la plus importante de ce document :

- **`AscentBaselineN2Test`** (LEO-400, GEO) produit un *snapshot* structuré
  — position et vitesse MECO à 10⁻⁶ m près, sur les deux passes. C'est une
  référence au sens fort.
- **Les autres tests** (MEO, Ariane 62, polaire) ne logguent que des grandeurs
  agrégées : forme d'orbite, masses, ΔV. C'est une référence plus faible, et
  §6 dit ce que ça coûte.

---

## 2. Tableau de référence

| Profil | Lanceur / charge utile | Orbite atteinte (osculatrice) | Inclinaison | Masse finale | ΔV total | Durée |
|---|---|---|---|---|---|---|
| **LEO-400** | Falcon Heavy, `LEGACY` | 400 314,5 × 419 164,8 m | 5,303026° | 35 306,788 kg | 8 074,631 m/s | 7,3 s |
| **GEO** | `GEOMission` 400 km → 35 786 km | 35 786 247,8 × 35 791 193,0 m | 0,000034° | 2 470,370 kg | 11 978,344 m/s | 14,2 s |
| **MEO** | Ariane 62, `GEO_SAT` | 19 641 725 × 20 203 913 m | 55,0020° | 2 000,0 kg ⚠ | 11 734 m/s | 7,4 s |
| **Ariane 62 LEO** | Ariane 62, `EARTH_OBSERVATION_SAT` 5 t | 400 117 × 419 351 m | 5,2909° | 12 464,578 kg | 8 062 m/s | 7,6 s |
| **Polaire** | Falcon Heavy, `LEGACY` | 410 084 × 2 563 706 m † | 89,9999° | 29 438,109 kg | *sans objet* | 0,4 s |

⚠ La masse finale MEO est un **plancher**, pas un résultat : cf. §5.1.
† Le polaire s'arrête à la fin du virage gravitationnel puis du plane trim : il ne
vole ni circularisation ni transfert, son orbite n'a donc aucune raison d'être la
cible à 400 km. Cf. §4 et §5.6. N'ayant pas d'optimiseur, il n'a pas non plus de
ΔV total à rapporter.

Durées = temps d'optimisation seul (hors génération d'éphéméride et hors
démarrage JVM). Elles sont *observées*, jamais assertées — `AscentBaselineN2Test`
les qualifie de N3, « watched, not enforced ».

---

## 3. Profils mesurés au MECO — la vraie référence de L1

Ces deux profils sont les seuls dont l'état de fin d'étage d'ascension est
enregistré. Ce sont eux qui portent le test d'égalité de L1.

### LEO-400 — `baseline/leo-400-n2.txt`

`EarthOrbitMission("Falcon Heavy", LaunchConfiguration(FALCON_HEAVY, {600 000, 100 000}, LEGACY), 400 000)`

```
CMA-ES transitionTime : 307.193166 s      exposant : 0.127161
coût / évaluations    : 8.6e-5 / 2321
MECO                  : t+314.193166 s, masse 36368.082 kg
  position (m)        : -3065580.683126, -5677159.212400, 577919.103944
  vitesse  (m/s)      : 6963.051876, -3780.275135, -187.406132
passe optim ↔ éphém.  : Δpos = 0.000 m, Δvit = 0.00000 m/s, Δmasse = 0.000 kg
éphéméride            : 9 992 points, complète
```

Ergols : étage 0 — 600 000 kg chargés, 600 000 consommés, 0 résiduel ; étage 1 —
100 000 chargés, 68 843,2 consommés, **31 156,8 résiduels**.

### GEO — `baseline/geo-n2.txt`

`GEOMission("GTO mission", 400 000, 35 786 000)`

```
CMA-ES transitionTime : 329.124209 s      exposant : 0.177424
coût / évaluations    : 8.6e-5 / 3258
MECO                  : t+336.124209 s, masse 64579.867 kg
  position (m)        : -2971529.637513, -5650537.034819, 569924.139212
  vitesse  (m/s)      : 7056.046315, -3730.610941, -197.768295
passe optim ↔ éphém.  : Δpos = 0.000 m, Δvit = 0.00000 m/s, Δmasse = 0.000 kg
éphéméride            : 120 317 points, complète
```

Ergols : étage 0 — 1 233 000 / 1 233 000 / 0 ; étage 1 — 107 500 / 84 648,5 /
**22 851,5** ; étage 2 — 2 000 / 1 530,2 / 469,8.

**Le `Δpos = 0.000 m` entre les deux passes est un invariant à surveiller.** Il
dit que rejouer la mission pour l'éphéméride refait exactement le vol optimisé.
MEO ne l'a pas (§5.2).

---

## 4. Profils mesurés en agrégat

### MEO — `baseline/MeoMissionTest.txt`

Ariane 62 + `Payloads.GEO_SAT`, Kourou (5,23° N, −52,77° E), parking 400 km,
cible 20 200 km / 55,0°. Chargements budgétés : S1 434 000 kg, S2 18 338,5 kg,
ergol charge utile 1 241,1 kg.

Douze étages : `Vertical Ascent, Gravity turn (S1), S1 separation, Gravity turn
(S2), Parking, Coasting parking, GTO injection, S2 separation, Circularization,
Trim, Plane trim, Coasting`.

```
CMA-ES                : [378.66310715556153, 0.13199471634075782], coût 8.670592244682683E-5
orbite osculatrice    : 19 641 725 × 20 203 913 m (e = 1,069e-02, i = 55,0020°)
orbite moyenne        : 19 640 192 × 20 203 760 m (e = 1,071e-02, i = 55,0013°)
bande survolée        : 19 637,2 – 20 202,4 km, i = 55,0027°
éphéméride            : 88 524 points, complète, coast final 2 037 556 s (48 révolutions)
```

Masses par étage (kg) : 497 580 → 477 270 → 63 580 → *(largage)* 27 580 →
17 480 → 17 097 → 10 886 → *(largage)* 3 241 → 2 024 → **2 000**.

Ergols : étage 0 — 434 000 / 434 000 / 0 ; étage 1 — 18 338 / 16 693 /
**1 645 largués avec l'étage** (9,0 %) ; étage 2 — 1 241 / 1 241 / 0.

### Ariane 62 LEO-400 — `baseline/Ariane62MissionTest.txt`

Ariane 62 + `EARTH_OBSERVATION_SAT` (5 000 kg, sans ergol), Kourou, cible 400 km.

```
CMA-ES                : [241.1858272116893, 0.14051992433342997], coût 5.684e-03
orbite osculatrice    : 400 117 × 419 351 m (e = 1,417e-03, i = 5,2909°)
orbite moyenne        : 409 693 × 409 917 m (e = 1,652e-05, i = 5,2943°)
extinction S1         : T+128,198 s
horizon de restitution: 1 jour → 86 164 s de coast
```

Ergols : étage 0 — 434 000 chargés, 7,3 × 10⁻¹² kg restants ; étage 1 — 6 698,77
chargés, 5 234 consommés, **1 464,578 résiduels (21,9 %)**.

Masse finale 12 464,578 kg : il n'y a **pas d'étage de séparation S2** dans cette
chaîne, la masse porte donc la structure S2 + le résiduel + les 5 t de charge
utile.

**Le coût CMA-ES retenu vaut 5,7 × 10⁻³, soit 66 fois celui des trois autres
profils** (8,7 × 10⁻⁵). C'est le seul des quatre où l'optimiseur ne déclenche pas
« Target reached during exploration ». À consigner comme état des lieux, pas
comme défaut : l'orbite atteinte est correcte.

### Polaire — `baseline/PolarCoverageTest.txt`

Falcon Heavy pleinement chargé + `LEGACY`, Kourou, cible 400 km, plan 90°.
**Aucun CMA-ES** : burn2 = 250 s et exposant = 0,32 sont figés dans le test.
C'est, en l'état, le profil le mieux armé conceptuellement pour un test de
non-régression L1 — et le moins bien instrumenté.

```
ascension              : burn1 150 s, largage 177 650 → 111 650 kg, MECO à 402 s
après ascension seule  : i = 86,7603°, trace au sol jusqu'à 86,888°
                         orbite −131 589 × 3 111 118 m (e = 2,061e-01), masse 39 787,241 kg
plane trim au nœud     : erreur de plan 3,2410882290795384° → ΔV 1 028 m/s, dt 36 s
après plane trim       : i = 89,9999°, trace au sol jusqu'à 89,891°
                         orbite 410 084 × 2 563 706 m (e = 1,369e-01), masse 29 438,109 kg
                         ergol dépensé au trim : 10 349,1 kg ‡
plein est (contrôle)   : trace au sol 5,230°
```

‡ **Ce n'est pas le coût d'une mission polaire réelle** — la fixture applique le
trim à une géométrie qu'aucune mission ne vole. Cf. §5.6 et `bugs.md` BUG-6.

**Le périgée négatif après l'ascension est normal** : `flyAscent` s'arrête à la
fin de la séquence de virage gravitationnel, sans circularisation ni transfert.
L'état de MECO est sur un arc suborbital, comme sur tout autre profil au même
instant. Ce qui est mesuré ici est un **plan**, pas une orbite d'insertion.

**Ce profil est le seul reproductible au bit près.** Les deux campagnes (21 h 36
et 21 h 50) donnent des chiffres identiques jusqu'aux seize décimales de l'erreur
de plan. Aucun CMA-ES, aucune exploration parallèle : c'est ce qui en fait le
meilleur candidat pour porter un test d'égalité en L1, malgré son instrumentation
plus pauvre.

---

## 5. Anomalies héritées — à ne pas attribuer à PHY-4

Elles sont dans la baseline. Si elles disparaissent en L1 ou L2, **c'est un
signal**, pas une amélioration gratuite.

### 5.1 MEO : le trim tape le plancher d'ergol

```
ERROR DepletionGuard - [Trim burn] Propellant depleted before scheduled cutoff
  at 2026-01-02T03:00:33.474…Z (floor 2000.0 kg): stopping propagation,
  upstream mass accounting is wrong
```

La masse finale MEO n'est pas 2 000,0 kg parce que le vol s'y arrête, mais parce
que la garde y coupe. L'orbite MEO est donc **limitée par l'ergol**, et le
message accuse explicitement la comptabilité de masse amont. Le test passe (les
assertions portent sur la bande survolée). Ce point n'est pas diagnostiqué ici,
et il ne doit pas l'être en L0 — mais il doit être *connu* avant que L1 ne touche
au corps central, sinon la première dérive MEO lui sera attribuée à tort.

### 5.2 MEO : la passe d'optimisation et la passe d'éphéméride ne volent pas pareil

Là où LEO et GEO affichent `Δpos = 0.000 m` entre les deux passes, MEO diverge —
et **diverge de façon reproductible**, chiffre pour chiffre, sur deux exécutions
indépendantes :

| | passe d'optimisation | passe d'éphéméride |
|---|---|---|
| Ciblage de nœud GTO, itér. 0 | coast 2 385 s, latitude d'apogée −1,9074° | coast 2 774 s, +1,9042° |
| Ciblage de nœud GTO, itér. 1 | coast 2 348 s, −0,00173° | coast 2 737 s, −0,00238° |
| Circularisation | converge en **5** itérations, β = −0,016745, Δv 1 478,04 m/s | **6** itérations, β = −0,015841, Δv 1 475,49 m/s |
| Trim | Δv 57,828 m/s, dt 185,99 s | Δv 60,603 m/s, dt 198,85 s |
| Plane trim | résidu 0,002587° → sauté | résidu 0,001238° → sauté |

L'écart naît en amont, au ciblage de nœud de l'injection GTO, et se propage.
Les étages analytiques **replanifient** au lieu de rejouer. Cause non
diagnostiquée en L0.

> **Diagnostiquée depuis, en L1** — voir `03-conception-L1.md` §5.7. En deux mots :
> `CoastingStage` et `StageSeparationStage` ne surchargent pas
> `propagateStandalone`, dont le défaut renvoie `enter()` et **n'avance pas le
> temps**. La passe d'optimisation ne vole donc pas les coasts, et l'étage
> analytique en aval replanifie depuis l'état d'avant-coast. La divergence existe
> **aussi sur GEO**, que ce paragraphe ne mentionnait pas. Aucun chiffre de ce
> document n'est modifié : seule la cause est désormais connue.

**Conséquence directe pour L1 et L3 :** tout test d'égalité portant sur MEO doit
déclarer *de quelle passe* il parle. Comparer une passe d'optimisation d'avant à
une passe d'éphéméride d'après produira un écart de 3 m/s sans qu'aucun code de
corps central n'y soit pour rien.

### 5.3 MEO : l'orbite atteinte n'est pas circulaire

19 641,7 × 20 203,9 km, soit **562 km d'écart apogée-périgée** pour une cible
circulaire à 20 200 km (e = 1,07 × 10⁻²). Le test passe parce qu'il assertionne
la bande survolée à ±7 %, ce que 562 km respecte. Chiffre de référence, pas
verdict.

### 5.4 Les quatre profils optimisés saturent l'exposant de virage gravitationnel

`WARN … parameter exponent saturated (LOW)` sur LEO (0,127), GEO (0,177), MEO
(0,132) et A62 (0,141), pour des bornes `[0,1 ; 3,0]`. Attendu : l'épinglage au
plancher a déjà été diagnostiqué comme le véritable optimum, pas comme un défaut
de recherche. Ne pas « corriger » en déplaçant la borne — déplacer une borne
CMA-ES renormalise la recherche et perturbe tous les profils.

### 5.5 Un libellé de log faux, dans `PolarCoverageTest` — corrigé

Le log de la campagne écrit `(Δv spent: {} kg)` en y passant une **différence de
masse**. Les 10 349,1 kg sont des kilogrammes d'ergol, pas des m/s. Sans
conséquence numérique — aucune assertion ne lisait ce nombre. Corrigé depuis
(`propellant spent`), en même temps que l'instrumentation manquante du §4 : les
logs archivés sous `baseline/` portent donc l'ancien libellé, c'est normal.

### 5.6 Le plane trim n'est une rotation pure qu'à une apside

C'est la mesure du polaire qui l'a révélé, et c'est la seule anomalie de ce
document que la campagne du 2026-08-16 ne pouvait pas voir : il fallait la forme
d'orbite des deux côtés du trim, qui n'était pas logguée.

Le javadoc d'`AnalyticPlaneTrimAtNodeStage` affirme que la manœuvre « ne change
ni l'énergie ni la forme de l'orbite, seulement le plan ». Mesuré :

| | avant trim | après trim |
|---|---|---|
| demi-grand axe | 7 867 901 m | 7 865 032 m — **préservé à 0,036 %** ✅ |
| excentricité | 0,2061 | **0,1369** ❌ |
| périgée × apogée | −131 589 × 3 111 118 m | 410 084 × 2 563 706 m |

**L'énergie est bien préservée ; la forme ne l'est pas.** La raison est dans la
construction du ΔV : `vTargetDir = (nIdeal × rNode).normalize()` est perpendiculaire
au rayon, donc la vitesse visée est **purement transverse**. Faire tourner une
vitesse qui possède une composante radiale vers une direction purement transverse
de même module conserve `|v|` — donc `a` — mais augmente `|h| = r·v_transverse`,
donc abaisse `e`. La manœuvre circularise en même temps qu'elle tourne. Le
javadoc n'est exact **qu'à une apside**, où la vitesse est déjà transverse.

Le coût le confirme. Une rotation pure de 3,24° vaut `2·v·sin(1,62°)`, soit 350
à 462 m/s selon l'endroit du nœud (vitesse comprise entre 6,20 km/s à l'apogée et
8,17 km/s au périgée). Le ΔV dépensé est de **1 028 m/s**, deux à trois fois
plus. La rotation totale déduite de `2·v·sin(Ψ/2) = 1028` vaut Ψ ≈ 7,4°, dont
3,24° de changement de plan : le reste, ≈ 6,6° en quadrature, est l'angle de pente
au nœud que la manœuvre aplatit au passage. Contrôle de cohérence : les 10 349,1
kg dépensés donnent, par Tsiolkovsky, une Isp implicite de 347,8 s — la valeur
attendue pour l'étage. Le ΔV logué est donc réel, pas un plan non tenu.

**Ce n'est pas un défaut de l'étage, c'est la fixture qui est hors enveloppe.**
En production, `EarthOrbitMission.ascentThen` insère le plane trim **après** les
phases orbitales (`EarthOrbitMission.java:349-359`) : il s'exécute donc sur une
orbite déjà circularisée, où la vitesse *est* transverse. Les autres profils de
cette baseline le confirment — le trim coûte **3,3 kg et 4,2 m/s** sur GEO, et
MEO le saute. `PolarCoverageTest.trimPlane`, lui, l'appelle directement sur
l'état de fin d'ascension, en sautant les phases orbitales que la mission réelle
vole avant.

**Conséquence pour la lecture du §4 : les 10 349,1 kg ne sont pas le coût d'une
mission polaire.** C'est le coût d'un trim appliqué à une géométrie qu'aucune
mission ne vole. La démonstration de couverture reste valide (86,9° → 89,9°) ;
son prix affiché, non.

Le javadoc de l'étage affirmait l'invariant de forme ; il a été corrigé le
2026-08-16 pour dire ce que le code fait. Le reste — l'emploi hors enveloppe dans
la fixture, et le coût réel d'un plane trim polaire, qui n'a jamais été mesuré —
est suivi dans [`bugs.md` BUG-6](../bugs.md#bug-6--plane-trim-employé-hors-de-son-enveloppe-par-lascension-polaire).

**Pourquoi cet item ne doit pas être corrigé pendant PHY-4.** Remettre la fixture
dans l'enveloppe déplacerait les chiffres polaires de cette baseline, sur
laquelle L1 à L6 vont s'appuyer pendant des semaines. Ce serait bouger la
référence en cours de chantier, ce que le §3 du découpage interdit. Et pour L1
en particulier, il faut savoir dès maintenant que la forme d'orbite du polaire
est **déjà** gouvernée par cette construction-là, et non par le corps central.

---

## 6. Reproductibilité mesurée

MEO a été exécuté deux fois (20 h 51 et 21 h 35), même graine, même commit.

| | exécution A | exécution B |
|---|---|---|
| Exploration 1/4 | coût 8.670592244682683E-5, 511 éval. | **identique**, 511 éval. |
| Exploration 2/4 | coût 2.7563347023279607E-4, 524 éval. | même coût, **525** éval. |
| Exploration 3/4 | coût **1.419481679516491E-4**, 552 éval. | coût **4.1248031742326585E-3**, **538** éval. |
| Exploration 4/4 | coût 0.09686472306409878, 524 éval. | même coût, **521** éval. |
| Total évaluations | **2 115** | **2 099** |
| Optimum retenu | `[378.66310715556153, 0.13199471634075782]` | **identique** |
| Orbite atteinte | 19 641 725 × 20 203 913 m, i 55,0020° | **identique** |

Les explorations tournent en parallèle
(`CMAESTrajectoryOptimizer.java:314`) et se partagent un budget découpé à
l'exécution : **le nombre d'évaluations et le coût des explorations perdantes ne
sont pas reproductibles**. L'exploration gagnante, l'optimum retenu et l'orbite
atteinte le sont, au dernier chiffre.

**Règle pour les lots suivants :** un test de non-régression peut épingler un
résultat retenu ; il ne doit **jamais** épingler un compte d'évaluations, un coût
d'exploration perdante, ni un temps.

---

## 7. Trous connus de cette baseline

À combler avant que le lot concerné n'en ait besoin, pas avant.

1. **Aucun état MECO pour MEO, Ariane 62 et le polaire.** Seuls LEO-400 et GEO en
   ont un, parce que seuls eux passent par `AscentBaselineN2Test`. Leur test
   d'égalité L1 sera donc plus faible : forme d'orbite et masses, pas position et
   vitesse. Le remède, si L1 le demande, est d'ajouter un profil à
   `AscentBaselineN2Test` plutôt que d'inventer un second mécanisme.
2. ~~Le polaire n'a ni forme d'orbite ni masse absolue.~~ **Comblé** le
   2026-08-16 : les lignes manquantes ont été ajoutées à `PolarCoverageTest` et
   le test rejoué (§4). Ce trou aura payé son comblement à lui seul — c'est lui
   qui a fait apparaître §5.6.
3. **Aucune mesure de `PropellantLoadOptimizerIntegrationTest`**, dont le temps
   d'exécution est un point de surveillance ouvert depuis I7.
4. **Les durées sont des temps de paroi sur une seule machine.** Elles ne servent
   qu'à détecter un ordre de grandeur perdu (le blocage de 4 h de `geoMultiStage`
   corrigé en I7 est le précédent), jamais à comparer deux machines.

---

## 8. Le plancher de bruit, rappelé

Le bilan MIS-7 mesure un étalement de **19,2 km** de l'ensemble acceptable de
CMA-ES sur le périgée LEO : deux candidats que la fonction de coût juge
équivalents peuvent être distants de 19 km.

**Une dérive de quelques kilomètres constatée en L1 ou L2 ne prouve donc rien.**
C'est la raison d'être du §3 de ce document : les tests de non-régression des
lots suivants portent sur des **états de fin d'étage à variables figées**, pas
sur des sorties d'optimiseur.
