# PHY-1 / L2 — Le vol allumé

Lot `L2` du [découpage](02-decoupage.md), tel qu'amendé le 2026-08-20. Entrées :
[`03-baseline-L0.md`](03-baseline-L0.md) et [`04-conception-L1.md`](04-conception-L1.md).

**Propriété rendue vraie.** Le chemin drag-on a tourné, et ce qu'il produit a été
confronté à une valeur calculée par ailleurs.

**Fermé par** trois mesures (§3), et par trois valeurs consignées sans assertion qui
sont les entrées de `PHY-2` (§4).

**Le lot n'écrit aucune ligne de production.** Tout ce qu'il allume, `L1` l'a câblé :
`StageLegRunner:161` résout `stage.flightContext(stageEntry, mission)` sur chaque leg
et en bâtit le propagateur trente lignes plus bas. Pour qu'une mission traîne, il ne
manque que deux écritures qu'un test peut faire lui-même : un `AtmosphereModel`
non-`NONE` sur la mission, et un étage actif porteur d'aéro.

---

## 1. Ce que ce lot change au découpage

### 1.1 « Un profil existant recomposé » n'était pas une option

Le découpage laissait ouverte, en §6, la question de la mission de test : *un profil
existant recomposé avec une atmosphère, ou une mission dédiée ?* Elle est tranchée, et
pas par préférence — par une propriété du code.

**Toute mission terrestre part du pas de tir.** `EarthMission.getInitialState`
construit l'état initial sur le sol, à partir d'un point géodésique de site de
lancement ; aucune sous-classe de production ne démarre en orbite. Donner une
atmosphère à `LEO-400` ou à `Ariane 62`, ce n'est donc pas ajouter une force à un vol
orbital : c'est **voler une ascension depuis 0 km dans l'atmosphère**. Trois
conséquences, dont deux sont des empêchements :

- **Harris-Priester devient inutilisable** : il lève en dessous de 100 km, pendant
  l'évaluation interne d'un pas d'essai, hors de portée de tout détecteur
  ([L0 §2.1](03-baseline-L0.md)). Le lot perdrait la moitié de la mesure 2, qui est
  précisément la confrontation des deux modèles ;
- **le régime pathologique de L0 §2.2 est traversé** — à 130 km, 982 497 pas et 487 s
  au lieu de 452 pas et 0,2 s — et il l'est sans la borne d'altitude que `PHY-2` doit
  poser ;
- **le verdict passerait par une ré-optimisation CMA-ES**, dont un seul candidat bas
  suffit à faire exploser la durée.

Autrement dit, l'option 1 du §6 faisait entrer dans `L2` le problème non résolu de
`PHY-2`. `L2` vole donc un **fixture orbital dédié**, qui ne descend jamais sous
200 km.

Ce que cela laisse de côté est nommé et assumé au §5 : `L2` ne vole aucune ascension
avec traînée, et n'a donc rien à dire sur les 100–300 m/s de pertes que l'étude
d'impacts lui attribue.

### 1.2 La sanity 800 km gagne une seconde borne

Le découpage écrit la mesure 3 comme un écart drag-on / drag-off « inférieur à
0,1 % ». Mesuré sur le catalogue de `L1`, avec le B = 505 kg/m² d'`EARTH_OBS_SAT` : à
800 km la décroissance est de **0,281 m/jour** sur un demi-grand axe de 7 178 km, soit
**3,9 × 10⁻⁶ %**. Le seuil de 0,1 % est **quatre ordres de grandeur** au-dessus du
signal. (L'estimation faite avant l'écriture annonçait 0,63 m/jour : elle employait la
ρ ponctuelle de la table L0 §1, deux fois supérieure à la moyenne le long de l'orbite
— même cause qu'au §7-b.)

Il n'attrape donc qu'une erreur d'échelle grossière — ce pour quoi le découpage
l'écrit, et c'est légitime. Mais **tel quel, il passe aussi quand aucune traînée n'est
montée du tout**, ce qui est exactement le mode de défaillance que `L1` s'est construit
pour exclure. La mesure 3 est donc écrite à **deux bornes** : l'écart est inférieur à
0,1 % *et* strictement non nul.

### 1.3 La mesure 1 se dédouble en deux états

Le découpage demande la confrontation de l'accélération à l'analytique « à un état
donné », avec la vitesse relative à l'atmosphère en rotation. Un état ne suffit pas :
l'oubli de la co-rotation ne se manifeste pas de la même façon selon la direction du
vol, et le §3.1 le chiffre. Deux états, tous deux sur l'équateur.

---

## 2. Les deux fixtures

### 2.1 Le fixture propagateur, pour la mesure 1

Sur le patron de `ThirdBodyPerturbationTest` (`PHY-4 / L2`), qui a fermé son troisième
corps de la même façon : le contexte est bâti à la main, le propagateur sort des
**factories** d'`OrekitService` — jamais d'un `new DragForce(…)` direct — et la force
montée est ressortie de la liste pour être évaluée à un état imposé. Ce qui est testé
est le câblage, pas Orekit.

### 2.2 Le fixture mission, pour les mesures 2 et 3

Sur le patron de `SoiRoundTripFlightTest.BallisticMission` (`PHY-4 / L4`), avec une
différence qui compte : celui-là passe un véhicule `null`, et
`MissionStage.flightContext` lit `mission.getVehicle().resolveActiveStage(…)`. Le
fixture de `L2` porte donc un **vrai véhicule**, et c'est ce qui le fait passer par la
résolution d'aéro au lieu de passer à côté.

| Élément | Valeur | Pourquoi |
|---|---|---|
| `getInitialState` | orbite circulaire imposée, 250 km, i = 51,6° | la géométrie de [L0 §2.2](03-baseline-L0.md), pour que les chiffres se comparent |
| véhicule | `Spacecraft` porteur de l'aéro d'`EARTH_OBS_SAT` (9,0 m², Cd 2,2, B = 505 kg/m²) | un `Spacecraft` nu suffit : `Vehicle.aerodynamics()` remonte par `resolveActiveStage`, sans `VehicleStack` |
| étages | un `CoastingStage` de 86 400 s | la seule phase dont le lot a besoin |
| atmosphère | `HARRIS_PRIESTER` et `NRLMSISE`, un run chacun, plus un run `NONE` | le run `NONE` est le zéro de la mesure 2 |
| époque | fixée, celle des densités de [L0 §1](03-baseline-L0.md) | NRLMSISE dépend du fichier de météo spatiale embarqué dans `orekit-data.zip` ; la date fixée et la bande large (§3.2) sont ce qui rend le test insensible à un rafraîchissement de l'archive |

**`ReentryGuard` est armé, et c'est sans effet ici.** `StageLegRunner` l'arme sur
chaque leg, et [L0 §2.3](03-baseline-L0.md) a montré qu'il est inopérant avec traînée.
Le fixture ne descend pas : à 250 km, après 24 h, l'altitude mesurée par le prototype
est encore 250,4 km. Le détecteur inopérant reste un legs à `PHY-2`, pas un problème de
`L2`.

---

## 3. Les trois mesures

### 3.1 Mesure 1 — l'accélération contre l'analytique, sur deux états

L'attendu est écrit à la main, `0,5·ρ·v_rel²·Cd·S/m`, dirigé selon `−v_rel`, avec
`v_rel` tiré de la transformation ITRF → GCRF. Ce que cela prouve n'est pas la formule
d'Orekit : c'est **la surface et le Cd de l'étage actif, le bon signe, et surtout que
la vitesse employée est relative à une atmosphère en rotation.**

**Les deux états ont le même `|ω × r|` et ne diffèrent que par la direction du vol.**
Tous deux sont placés sur l'équateur, à 250 km, où `ω·a` vaut 483 m/s pour une vitesse
circulaire de 7 755 m/s :

| État | Vitesse | Ce que coûterait une vitesse inertielle *(mesuré)* |
|---|---|---|
| équatorial prograde | plein est | **+13,7 %** sur le module, **0,00°** sur la direction |
| polaire au nœud | plein nord | **−0,4 %** sur le module, **3,57°** sur la direction |

Un seul état laisserait donc passer la moitié du piège : l'état équatorial pince le
module, l'état polaire pince la direction. La variante en vitesse **inertielle** est
calculée et **loggée** à chaque exécution, pour que le lecteur voie l'écart que le test
attrape — même usage que la marée linéarisée, loggée et jamais assertionnée, de
`ThirdBodyPerturbationTest`.

**La tolérance est réglée sur l'erreur observée, et celle-ci est nulle.** Mesuré :
l'écart relatif entre l'expression écrite à la main et celle d'Orekit vaut exactement
`0,000000e+00`, sur les deux états et sur les deux modèles — les deux calculs se
trouvent faire la même algèbre dans le même ordre. La tolérance reste non nulle
(10⁻¹⁴) comme marge pour une autre époque ou une expression réordonnée, pas comme
marge de mesure : la défaillance qu'elle existe pour attraper, la vitesse inertielle,
est à 13,7 %.

### 3.2 Mesure 2 — la décroissance à 250 km, écrite comme une bande analytique

**L'attendu est calculé dans le test**, et non recopié : la décroissance séculaire
d'une orbite circulaire vaut `Δa = −2π·ρ·a²/B` par révolution, avec ρ **lue sur le
modèle** et B **lu sur le catalogue**. La bande suit donc le catalogue au lieu d'être
un nombre à maintenir — même principe que la mesure 1, qui écrit son accélération
attendue au lieu de reprendre le chiffre du découpage.

**Pourquoi une bande, et pourquoi ±25 %.** Confronté aux mesures du prototype
([`baseline/prototype-L0.log`](baseline/prototype-L0.log), parking 250 km, 24 h,
i = 51,6°, B = 455 kg/m²) :

| Modèle | Analytique brut | Corrigé de la co-rotation (×0,924) | Mesuré par le prototype |
|---|---:|---:|---:|
| NRLMSISE-00 | 943 m | 871 m | **908,2 m** |
| Harris-Priester | 851 m | 786 m | **740,5 m** |

L'analytique corrigé tombe 4,1 % en dessous sous NRLMSISE et 6,2 % au-dessus sous
Harris-Priester. Le reste de l'écart est la variation de ρ le long de l'orbite — les
densités de L0 §1 sont prises en un point, l'orbite en balaie un tour — et il ne se
réduira pas. **±25 % est donc quatre fois la dispersion mesurée**, pas de la prudence.

**Ce que la bande ne voit pas, et pourquoi la mesure 1 existe.** L'oubli de la
co-rotation ne pèse que 7,6 % sur la décroissance à i = 51,6° : il passerait inaperçu
dans cette bande. C'est la mesure 1 qui l'attrape, au bit près. Les deux mesures ne
sont pas redondantes — l'une pince la force instantanée, l'autre l'effet accumulé sur
un jour à travers le chemin de production.

**Les deux modèles sont volés**, un run chacun. C'est ce qui produit au passage la
première des valeurs consignées (§4.1). Un troisième run, `NONE`, tient lieu de zéro :
le même fixture, sans atmosphère, ne doit pas décroître.

Mesuré au catalogue (B = 505 kg/m²), sur éléments moyens : **−716,7 m/jour** sous
NRLMSISE-00 et **−584,5 m/jour** sous Harris-Priester, pour un analytique de 709,4 et
579,3 m — des rapports de **1,010** et **1,009**. Le §7 explique pourquoi ces chiffres
sont plus bas que les ordres de grandeur annoncés ici avant l'écriture, et pourquoi
c'est l'annonce qui était fausse.

### 3.3 Mesure 3 — la sanity 800 km, à deux bornes

Le même fixture à 800 km, drag-on contre drag-off. Deux assertions, pour la raison
donnée au §1.2 :

1. l'écart est **inférieur à 0,1 %** — le filet à erreur d'échelle du découpage ;
2. l'écart est **strictement non nul**, plancher réglé sur la mesure — sans quoi le
   test passe aussi quand aucune traînée n'est montée.

**Mesuré : 0,281 m d'écart sur le demi-grand axe moyen, contre 0,282 m d'analytique**
— 0,4 % d'accord, et 3,9 × 10⁻⁸ du demi-grand axe, soit quatre ordres de grandeur sous
le plafond de 0,1 %. Le demi-grand axe suffit donc et la dérive le long de la trace n'a
pas eu à être employée : lue sur éléments moyens (§7), la grandeur ne porte plus le
bruit qui rendait ce choix incertain. Le plancher est fixé au tiers de la mesure,
0,09 m.

---

## 4. Les trois valeurs consignées sans assertion

Elles sont les entrées de `PHY-2`, pas des propriétés de `PHY-1`. Aucune n'est
assertionnée ; toutes sont loggées et reprises dans le bilan de livraison.

### 4.1 L'écart Harris-Priester / NRLMSISE-00

Il tombe des deux runs de la mesure 2, sans travail supplémentaire. **Mesuré :
NRLMSISE-00 est 22,6 % plus sévère qu'Harris-Priester** (716,7 contre 584,5 m sur
24 h), ce qui confirme au dixième de point les ≈ 22 % du prototype L0 (740,5 contre
908,2 m à B = 455, et 3 424,5 contre 4 219,1 m à B = 101). Harris-Priester est donc le
moins sévère à cette altitude alors qu'il est le plus dense au-dessus de 800 km.

### 4.2 Le surcoût compute sur une optimisation — une tentative bornée dont l'issue est la mesure

Le surcoût **par propagation** est déjà mesuré, et il n'est pas ce que le découpage
demande : sur les 446 pas du parking 250 km, Harris-Priester coûte ×1,35 à ×1,92 et
NRLMSISE-00 ×3,73 à ×4,03 par rapport au propagateur nu.

Le surcoût **sur une optimisation** suppose de lancer CMA-ES avec la traînée allumée
sur un profil qui décolle du sol, c'est-à-dire exactement ce que le §1.1 vient
d'écarter du reste du lot. Il est donc traité comme **une tentative unique, bornée en
temps, marquée `@Disabled`, lancée par l'utilisateur** : `LEO-400` recomposé avec
NRLMSISE-00.

**Toutes les issues sont des mesures**, et c'est la raison de cette forme : s'il
converge, on consigne le pourcentage demandé ; s'il ne termine pas, la non-terminaison
chiffre l'absence de borne d'altitude sur le vrai optimiseur. La fermeture de `L2` n'en
dépend dans aucun cas.

**Mesuré le 2026-08-21 — c'est une troisième issue, que ni le découpage ni la
conception n'avaient envisagée.** Le run drag-off converge normalement. Le run drag-on
ne traîne pas et ne converge pas : **il casse, en 3 min 15 s**, dans cet ordre —

1. le gravity turn S1 s'établit à un coût de **0,582** contre 0,0476 acceptable, trois
   explorations CMA-ES indépendantes tombant d'accord dessus, et se termine à **58 km**
   d'altitude avec une pente de 3,65° là où le profil drag-off finit à plat ;
2. `Gravity turn (S2)` touche alors son `DepletionGuard` avant sa coupure prévue — les
   pleins ont été dimensionnés par `PropellantBudget` pour une ascension **sans**
   atmosphère, donc l'empilage n'atteint plus l'orbite ;
3. `AnalyticHohmannTransferStage` finit par lever `minimal step size reached` sur une
   trajectoire qui n'est jamais arrivée en orbite.

**Ce que `PHY-2` reçoit n'est donc pas un pourcentage mais un enchaînement** : allumer
la traînée sans redimensionner le budget d'ergols ne rend pas l'optimisation lente,
elle la rend **infaisable**. Le surcoût compute demandé par le découpage n'existe pas
encore comme grandeur mesurable — il n'y a rien à chronométrer tant qu'il n'y a pas de
solution.

**Et le run expose une approximation de `L1` hors de son domaine déclaré.** Le
catalogue donne au S2 le Cd libre-moléculaire de 2,2 « parce qu'un étage supérieur
s'allume au-dessus de 70 km » ; ce profil l'allume à **58 km**, en régime continu. À
corriger ou à assumer avant de basculer le défaut, au même titre que les deux
approximations déjà léguées par `L1`.

### 4.3 La dette des ISP proxy

Le catalogue compense aujourd'hui les pertes d'ascension par un ISP « moyen
trajectoire », et le dit en commentaire à ses deux premiers étages : Falcon Heavy S1
porte **296 s** dans un intervalle [282 s au sol, 311 s au vide], Ariane 62 S1 porte
**300 s** dans [271 s, 331 s].

La dette est un calcul de catalogue, sans propagation : `Δv(ISP vide) − Δv(ISP moyen)`
= `g₀·ΔIsp·ln R`, soit **147,1·ln R** m/s pour Falcon Heavy S1 et **304,0·ln R** m/s
pour Ariane 62 S1. Le rapport de masse `R` est lu sur le stack **réellement dimensionné
par `PropellantBudget`**, pas sur la capacité pleine — aucune mission ne vole ses
1 233 t.

**L'intérêt du chiffre est sa comparaison** aux 100–300 m/s de pertes de traînée
qu'annonce l'étude d'impacts pour un lanceur lourd. **Mesuré : 408 m/s sur Falcon
Heavy S1 et 671 m/s sur Ariane 62 S1**, sur les stacks dimensionnés pour un LEO 400 km
(rapports de masse 16,04 et 9,08). Les deux sont **au-dessus** de la fourchette, et
Ariane 62 en double la borne haute — plus l'intervalle sol-vide de l'étage est large,
plus la convention « moyen trajectoire » absorbe.

**Ce que ce résultat dit à `PHY-2`**, et qui n'était pas l'hypothèse implicite du
découpage : reprendre les 296 s et les 300 s ne rendra pas seulement ce que la traînée
coûtera. Sur ces deux entrées, le proxy paie davantage que la seule traînée, et la
différence devra être attribuée — pertes de pilotage, pertes gravitationnelles déjà
modélisées ailleurs, ou simple marge — avant de basculer le défaut.

---

## 5. Ce que le lot s'interdit

Quatre choses, et [L0](03-baseline-L0.md) a montré qu'elles sont liées entre elles —
les traiter séparément serait les traiter mal :

- **aucune ascension volée avec traînée** (§1.1) ;
- **aucune borne d'altitude**, celle que L0 §2.2 réclame pour que l'optimisation reste
  terminante ;
- **aucun réglage de `ReentryGuard`**, dont le plancher à −50 km est franchi par le bas
  avant déclenchement (L0 §2.3) ;
- **aucun changement de défaut** : `AtmosphereModel.NONE` reste le défaut à la fin de
  `PHY-1`, comme le découpage §4 l'exige.

Les quatre gates — `CentralBodyBaselineTest`, `MissionPolylineBaselineTest`,
`EarthOrbitNonRegressionTest`, `AscentBaselineN2Test` — sont repassés par hygiène,
selon la procédure de [L0 §3](03-baseline-L0.md) : isolement contre
[`BUG-7`](../bugs.md), `cleanTest` systématique, et attente de la fin effective du run
précédent sous Windows. **Ils ne prouvent rien ici** — le lot n'ajoute aucun code que
les missions drag-off traversent.

---

## 6. Une référence fausse dans `L1`, corrigée

[`04-conception-L1.md`](04-conception-L1.md) §4.3 écrit que les trois coefficients
balistiques du catalogue encadrent « le 455 kg/m² de la table L0 §2.2 ». La table de
[L0 §2.2](03-baseline-L0.md) est le balayage de pas, mesuré à **B = 101 kg/m²** ; le
455 n'apparaît que dans le bloc décroissance du
[log du prototype](baseline/prototype-L0.log). L'encadrement lui-même est juste — 291,
429 et 505 entourent 455 — c'est la référence qui est fausse.

---

---

## 7. Ce que l'écriture a mesuré, et ce qu'elle corrige

Deux choses ont dû être mesurées avant que la mesure 2 puisse assertionner quoi que ce
soit. Aucune n'était visible dans la conception ; les deux ont été trouvées en balayant
la durée du coast — 1 révolution, 3 h, 6 h, 12 h, 24 h — contre la même expression
analytique, sur un test jetable supprimé après mesure.

| durée | écart osculateur | écart moyen | analytique | osc/analytique | moyen/analytique |
|---|---:|---:|---:|---:|---:|
| 1 rév | 36,01 m | 36,03 m | 30,71 m | 1,173 | 1,173 |
| 3 h | 72,82 m | 72,62 m | 61,94 m | 1,176 | 1,172 |
| 6 h | 147,41 m | 145,56 m | 123,87 m | 1,190 | 1,175 |
| 12 h | 305,76 m | 291,54 m | 247,73 m | 1,234 | 1,177 |
| 24 h | 665,90 m | 584,46 m | 495,33 m | **1,344** | 1,180 |

**a. La décroissance se lit sur éléments moyens, pas osculateurs.** Le rapport
osculateur dérive avec la durée quand le rapport moyen ne dérive pas, et cette
signature nomme la cause : le vol drag-on prend de l'avance sur son jumeau drag-off —
des dizaines de kilomètres le long de la trace après un jour — de sorte que les deux
échantillonnent le terme court-période J2 du demi-grand axe à des arguments de latitude
différents. Ce terme a ici une amplitude de 6 km, dix fois le signal ; il gonfle
l'écart osculateur de **14 % à 24 h** en laissant une révolution intacte.
`OrbitElements.mean` l'enlève.

**b. ρ est moyennée le long de la trajectoire réellement volée**, échantillonnée sur le
jumeau drag-off, et non le long d'une orbite képlérienne. Un échantillon képlérien lit
**17 % trop bas**, à peu près uniformément : l'historique d'altitude de l'orbite réelle
sous J2 n'est pas le cercle dont elle part, et la hauteur d'échelle de la densité n'est
ici que d'une quarantaine de kilomètres.

Les deux corrections appliquées, l'analytique tombe à **1 % du vol** à toutes les
durées du balayage — 1,009 sous Harris-Priester, 1,010 sous NRLMSISE-00.

### 7.1 Une correction à `L0`

**Les `da` du [log du prototype](baseline/prototype-L0.log) sont des écarts
osculateurs**, calculés exactement comme la première version de ce fixture : différence
du demi-grand axe final contre le run sans traînée. Ils portent donc la même inflation
de 14 % à 24 h. Vérifié : à B = 455 le prototype donne 908,2 m sous NRLMSISE, et ce
fixture retrouve 816,7 m en osculateur à B = 505 — soit 908,2 × 455/505 = 818,3 m, à
0,2 % près — mais **716,7 m sur éléments moyens**.

Les chiffres de `L0` restent valables pour ce qu'ils servaient à établir : le coût en
pas d'intégration, les modes de défaillance aux bords, et le rapport entre les deux
modèles, qui est un quotient et où l'inflation se simplifie (les 22 % de L0 sont
confirmés à 22,6 %). Ils sont en revanche **environ 14 % trop élevés comme mesure de
décroissance sur 24 h**, et `PHY-2` ne doit pas les reprendre tels quels comme
référence de décroissance.

### 7.2 Ce que le lot livre

Quatre classes de test et un utilitaire, **aucune ligne de production** :

| Fichier | Rôle |
|---|---|
| `simulation/flight/DragAccelerationTest` | mesure 1 — quatre cas (deux états × deux modèles) |
| `simulation/flight/OrbitalDecayFlightTest` | mesures 2 et 3, plus l'écart entre modèles du §4.1 |
| `simulation/flight/AtmosphereProbe` | atteint l'`Atmosphere` que le propagateur a réellement montée, partagé par les deux |
| `mission/vehicle/IspProxyDebtTest` | la dette du §4.3, arithmétique de catalogue |
| `mission/optimizer/DragOptimizationCostTest` | le §4.2, `@Disabled`, lancé à la main |

---

*Conçu le 2026-08-20, implémenté et mesuré le 2026-08-21.*
