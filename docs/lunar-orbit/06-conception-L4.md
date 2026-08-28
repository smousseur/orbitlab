# MIS-5 / L4 — Les deux étages, testés seuls

Lot **L4** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur la baseline de
[`02-baseline-L0.md`](02-baseline-L0.md), sur la frontière que [`03-conception-L1.md`](03-conception-L1.md)
a livrée et sur l'orbiteur que [`05-conception-L3.md`](05-conception-L3.md) a mis au catalogue. Il
rend vraie **une** propriété :

> **Un coast d'approche sélénocentrique et une combustion d'insertion volent seuls, sur une
> hyperbole fabriquée en test.**

Rien d'autre. Le lot est **additif** : aucune mission ne les enchaîne, aucune trajectoire existante
ne change, et rien du dépôt n'appelle les trois classes qu'il écrit. `L5` les compose.

**Cinq faits mesurés contredisent le découpage**, et trois d'entre eux changent ce que le lot livre :
comment l'allumage se résout, quelle grandeur la calibration fait converger, et sur quelle bande le
lot se ferme. Ils sont au §1.2.

---

## 1. Inventaire mesuré

### 1.1 Ce que le lot touche

| Fichier | Taille | Ce qui bouge |
|---|---|---|
| `maneuver/LunarInsertionPlan` | **neuf** | l'arrivée, l'avance à l'allumage, la calibration, quatre refus |
| `stage/LunarApproachCoastStage` | **neuf** | jumeau de `ParkingCoastStage` (111 l.) |
| `stage/LunarInsertionStage` | **neuf** | jumeau de `TLIBurnStage` (200 l.) |

**Trois fichiers neufs, zéro fichier modifié.** C'est la forme la plus additive qu'un lot puisse
avoir : la non-régression n'est pas mesurée, elle est structurelle — aucune ligne existante n'est
touchée.

### 1.2 Cinq faits que le découpage ne connaît pas

Toutes les mesures ci-dessous sont prises sur une hyperbole sélénocentrique fabriquée
(`v∞ = 828,74 m/s`, la valeur en forme close que `L3` §1.2 pt 1 a établie), démarrée **à la sphère**
au rayon que l'époque `2026-03-31T00:00:00Z` donne — **66 539 km** — et propagée dans
`moon().withPerturbers(EARTH, SUN)`.

**1. « Le temps au périastre se lit sur l'anomalie hyperbolique de son état d'entrée » est faux de
plusieurs centaines de secondes.** La forme close képlérienne, contre le périlune réellement volé :

| départ | forme close | volé | écart | périlune volé (képlérien : 100,000 km) |
|---|---|---|---|---|
| **66 539 km (la sphère)** | 64 169 s | — | — | **impacte** |
| 50 000 km | 46 230 s | — | — | **impacte** |
| 30 000 km | 25 410 s | 25 372 s | +38,8 s | +46,4 km |
| 20 000 km | 15 649 s | 15 640 s | +8,9 s | +87,3 km |
| 10 000 km | 6 738 s | 6 738 s | +0,7 s | +99,3 km |
| 5 000 km | 2 827 s | 2 826 s | +0,1 s | +100,05 km |
| 2 000 km | 434 s | 434 s | +0,0 s | +100,00 km |

**L'écart décroît régulièrement avec le rayon de départ : ce n'est pas un artefact de fixture, c'est
la marée terrestre intégrée sur l'approche.** À la sphère, sur une fixture *visée* pour voler à
100 km, la forme close se trompe de **+722,6 s** ; sur deux autres orientations, de **−402,3** et
**−235,7 s**. La demi-combustion vaut **175 s** : l'allumage tomberait une à quatre demi-combustions
après le périlune.

C'est cohérent avec la définition même de la sphère : à 66 539 km de la Lune, `µM/r² = 1,11·10⁻³`
m/s² et la marée terrestre `2 µE r / d³ = 9,4·10⁻⁴` m/s² sont du même ordre — c'est ce que la sphère
de Laplace *signifie*. Dix-huit heures de cela déplacent le périlune de centaines de kilomètres.

**Le corollaire porte sur les tests.** Sur une fixture **non perturbée**, la forme close est exacte :
100,000000 km et une vitesse radiale de 2,3·10⁻⁸ m/s à la date qu'elle prédit. Un test écrit sur une
telle fixture **passerait avec l'implémentation fausse**. La fixture de `L4` doit être perturbée, et
le §6 lui donne des dents pour que cela reste vrai.

**2. `new ApsideDetector(orbit)` est inutilisable sur une hyperbole.** Le constructeur lit
`orbit.getKeplerianPeriod()`, qui vaut `Infinity` quand `a < 0`, et en tire
`maxCheck = Infinity / 3` **et** `threshold = 1e-13 × Infinity` — les deux infinis. C'est aussi
pourquoi le patron de `AnalyticTrimBurnStage.detectStateAtApogee`, qui propage sur `period × 1,1`,
ne se recopie pas ici. Avec des réglages explicites :

| réglages | écart à la référence | coût |
|---|---|---|
| `maxCheck 60 s, threshold 1e-3` | **−0,0008 s** | 70 ms |
| `maxCheck 300 s, threshold 1e-3` | −0,0006 s | 60 ms |
| `maxCheck 60 s, threshold 1e-6` | −0,0000 s | 64 ms |

La référence est une recherche ternaire sur le rayon, convergée à 10⁻⁴ s.

**3. Le patron d'`AnalyticApogeeCircularizationStage` transfère, mais pas la grandeur visée.** Cet
étage brûle à l'apogée et fait converger l'**apside lointaine** ; le découpage demande de l'imiter.
À une cible circulaire le critère est dégénéré, et mesuré il diverge en creusant :

| itération | apolune | périlune |
|---|---|---|
| 0 | 117,364 km | 97,674 km |
| 1 | 102,038 | 78,711 |
| 2 | 101,720 | 74,528 |
| 3 | 100,944 | **51,241** |

L'apolune « converge » pendant que le périlune tombe de 46 km. La grandeur qui a de l'autorité est
le **demi-grand axe**, et la deuxième est la **vitesse radiale à la coupure** : une apside à un rayon
`r` avec `a = r` *est* un cercle.

**4. Les deux pentes ont une forme close exacte : c'est un Newton, pas une sécante.**

| | forme close | mesuré | |
|---|---|---|---|
| ∂a/∂β | **−2·r** | −3 661,9 contre −3 674,8 km | 99,65 % |
| ∂v_r/∂ζ | **+Δv** | +818,90 contre +823,76 m/s/rad | 99,41 % |
| ∂a/∂ζ | — | −8,3 km/rad | négligeable |
| ∂v_r/∂β | — | −473,7 m/s par unité de β | 14 %, absorbé à l'itération suivante |

β est une échelle sur la vitesse circulaire visée, ζ une rotation de la poussée **dans le plan**,
autour de `h = r × v`. Le découpage annonçait une sécante ; deux pentes connues à 0,4 % près valent
mieux, et le signe de la seconde n'est **pas** celui qu'on devine — `∂v_r/∂ζ = +Δv`, mesuré, et
l'écrire `−Δv` fait diverger la boucle jusqu'à `e = 0,66`.

**5. L'avertissement de `L0` sur la perte de poussée finie ne se reproduit pas.** `L0` §3 prévenait
que « la perte de poussée finie vaut 1,7 à 5 fois l'estimation en sinc » et que `L4` la mesurerait.
Mesuré : le Δv commandé dépasse l'impulsionnel de **+3,45 m/s sur 823,76, soit 0,41 %** et **2,2 kg**
— là où le sinc prédit +3,29 m/s, donc **juste à 1,3 % près**. Les 2,2 kg consomment **3,7 %** de la
marge de 10 % que `L3` a posée. La combustion mesure **350,1 s, soit 4,95 % d'une révolution et
17,83° d'arc** — exactement ce que `L3` a dimensionné.

### 1.3 Ce qui existe déjà et qu'on n'écrira pas

- **La conversion de repère.** `ArcTransition.convert` est exacte (les deux repères sont orientés
  ICRF, le transport est une pure translation) et **idempotente par égalité de référence** : mesuré,
  `moon().inertialFrame()` rend la même instance à chaque appel et à travers `withPerturbers`.
- **Le contexte lunaire.** `ArcTransition.across(earth().withPerturbers(MOON, SUN), MOON)` rend
  exactement `moon().withPerturbers(EARTH, SUN)` — mesuré égal, même instance de repère. Rien à
  écrire.
- **Le garde de rentrée sur l'arc lunaire.** `ReentryGuard` lit `body.equatorialRadius()` depuis
  `PHY-4`, donc son plancher vaut **1 687,4 km** de rayon sur la Lune. Mesuré : il arrête une
  hyperbole percutante à −50,0 km d'altitude en **98 ms**.
- **La forme close de l'avance à l'allumage.** `Physics.computeBurnDurationCapped` et le patron de
  `TranslunarInjectionPlan.ignitionLead`.
- **Le pas d'intégration.** `MissionStage.burnLimitedMaxStep` rend **30 s** pour l'orbiteur — le
  plafond `SAFE_MAX_STEP` : la charge n'est pas assez légère pour resserrer.

---

## 2. `maneuver/LunarInsertionPlan`

### 2.1 Trois entrées, dans l'ordre où la chaîne les appelle

```java
public record Arrival(SpacecraftState atPerilune, double periluneAltitude) {}
public record Burn(Vector3D direction, double duration,
                   double commandedDeltaV, double impulsiveDeltaV,
                   double endMass, double periluneAltitude) {}

public static Arrival arrivalFrom(SpacecraftState selenocentric, FlightContext context);
public static double  ignitionLead(Arrival arrival, ActiveStageInfo active);
public static Burn    insert(SpacecraftState ignitionState, ActiveStageInfo active,
                             FlightContext context);
```

**C'est le précédent exact du dépôt.** `ParkingCoastStage` et `TLIBurnStage` appellent tous deux
`TranslunarInjectionPlan.departureFrom` et `ignitionLead`, et aucun des deux ne connaît l'autre. La
propriété que cela protège est la même ici : les **quatre** lectures — deux étages × deux passes —
doivent tomber sur la même date, sinon la combustion n'est pas calibrée sur ce qui est volé. C'est la
règle d'une seule arithmétique que `L1` §5.3 a écrite pour `CoastingStage.cutoffFrom`.

`insert` **redétecte son arrivée** au lieu de recevoir celle du coast, comme `TLIBurnStage.plan`
rappelle `departureFrom` sur son propre état d'entrée. Coût : 70 ms. Bénéfice : les deux étages
restent sans lien, et l'un peut être testé sans l'autre — ce que le titre du lot demande.

La classe fait ~200 lignes, pas les 1 370 de `TranslunarInjectionPlan` : il n'y a ici ni Lambert, ni
visée, ni bissection sur le périlune. L'arrivée n'est pas à *choisir*, elle est à *lire*.

### 2.2 `arrivalFrom` : le périlune se détecte, il ne se calcule pas

C'est le fait §1.2 pt 1. Un `ApsideDetector` armé sur le contexte réel, en `STOP` sur le passage
croissant de `r·v`, donne le périlune volé à **8·10⁻⁴ s** près en 70 ms.

Deux choses sont écrites plutôt qu'héritées :

- **Les réglages du détecteur** (fait §1.2 pt 2), avec la provenance des deux valeurs en commentaire.
- **L'horizon de recherche**, et c'est le **seul usage légitime de la forme close** : elle se trompe
  de ≤ 723 s sur 64 169, soit **1,1 %**, donc `estimation × 1,3` borne largement des deux côtés (le
  pire écart *court* mesuré est −402 s, contre 19 250 s de mou). Elle est mauvaise comme réponse et
  bonne comme majorant, et le javadoc le dit dans ces termes — pour que personne ne la promeuve.

### 2.3 `ignitionLead` : la seule chose qui reste en forme close

Demi-durée de combustion sur le Δv impulsionnel au périlune, plafonnée par l'ergol restant — mot pour
mot `TranslunarInjectionPlan.ignitionLead`.

**Le découpage avait raison sur l'avance et tort sur la date** ; les deux se lisent dans la même
mesure. À 175 s de portée du périlune, le véhicule est à ~430 km de lui, et le tableau du §1.2 pt 1
donne un écart nul dès 2 000 km. Mesuré : **le périlune redétecté depuis l'allumage tombe à 8·10⁻⁴ s
de ce que l'avance annonçait** (175,0478 contre 175,0486).

### 2.4 `insert` : Newton à deux boutons, jacobienne en forme close

**Deux cibles** — `a = r_périlune` et `v_r(coupure) = 0`, qui ensemble disent `e = 0`. **Deux
boutons** — β et ζ, dont les pentes sont celles du §1.2 pt 4. La boucle est un Newton diagonal : les
deux couplages croisés sont soit négligeables (∂a/∂ζ), soit une contraction de 14 % que l'itération
suivante absorbe (∂v_r/∂β).

Tolérances : `|a − r| ≤ 200 m`, `|v_r| ≤ 0,1 m/s`, six itérations au plafond. Mesuré sur cinq
configurations — trois orientations × trois altitudes :

| cas | périlune volé | atteint | `e` | Δv commandé | surcoût |
|---|---|---|---|---|---|
| raan 20°, 100 km | 100,00 | 99,837 × 100,094 | 7,0e−5 | 827,206 | +3,451 |
| raan 90° | 229,90 | 229,787 × 229,970 | 4,7e−5 | 805,913 | +2,615 |
| raan 270° | 268,60 | 268,493 × 268,663 | 4,2e−5 | 802,660 | +2,439 |
| raan 20°, 200 km | 200,00 | 199,873 × 200,073 | 5,2e−5 | 812,757 | +2,802 |
| raan 20°, 50 km | 50,00 | 49,808 × 50,102 | 8,2e−5 | 835,010 | +3,849 |

**Trois évaluations, 0,8 à 1,1 s, bande ≤ 0,30 km partout.** À comparer aux deux alternatives
mesurées : un seul bouton sur `a` donne 93,59 × 106,50 km (`e = 0,0035`) en deux évaluations, et
aucun bouton donne 97,67 × 117,36 km (`e = 0,0053`).

**La combustion s'allume à l'entrée d'étage, pas recentrée sur le périlune** — la forme de
`TLIBurnStage.addBurn`. Mesuré, les deux donnent le même résultat **au mètre**
(99,866 × 100,101 km sur les deux branches, `e = 6,38e−5`), et la branche recentrée **écrête** dans quatre cas sur six :
la demi-combustion calibrée (175,7 s) dépasse l'avance en forme close (175,05 s) de 0,65 s, donc une
combustion centrée commencerait avant son propre étage. Recentrer serait de la machinerie qui
n'existerait que pour être écrêtée.

### 2.5 Quatre refus, tous en `OrbitlabException`

1. **Le périlune est derrière** — l'estimation en forme close est négative ou nulle. Refusé **avant**
   de propager : c'est le seul des quatre qui ne coûte rien.
2. **Aucun périlune dans l'horizon** — le `ReentryGuard` a arrêté la recherche, donc l'hyperbole
   percute. Le détecteur ne tire jamais et l'absence est le verdict.
3. **Un périlune sous la surface** — entre 0 et −50 km, la bande que le plancher du garde laisse
   passer. C'est le refus que le découpage nomme, et il ne se réduit **pas** au précédent.
4. **La combustion passe sous le plancher d'extinction**, jugée sur le Δv **commandé** et non sur
   l'impulsionnel, en citant les deux chiffres — `TranslunarInjectionPlan.refuseOrReturn` mot pour
   mot.

**Le refus n° 3 se juge sur le périlune volé, jamais sur le képlérien.** La fixture des mesures a un
périlune képlérien de **767,364 km** pour un vol à **99,999 km** : juger le képlérien accepterait des
trajectoires qui percutent et refuserait des trajectoires qui passent.

---

## 3. `stage/LunarApproachCoastStage`

Jumeau de `ParkingCoastStage` : `extends CoastingStage`, `super(name, null)`, un `ignitionDate`
**absolu** résolu dans `enter` et lu par `configure` et par `propagateStandalone`, un `DateDetector`
qui `STOP` et transitionne. Il redéfinit `propagateStandalone` pour la raison qui fait exister son
jumeau : sans cela, la marche d'étages le réduirait à zéro seconde et l'insertion serait planifiée
**à la frontière de la sphère**, dix-huit heures et 66 000 km trop tôt.

### 3.1 Une différence : `enter` convertit, et rend l'état converti

C'est le trou que `L1` §8 pt 1 a écrit avant de le rencontrer. **Sur les deux passes, l'état arrive
géocentrique** : `StageChainRunner` appelle `enter` *avant* le `ArcTransition.convert` que
`StageLegRunner.fly` fait en tête, et la passe optimiseur ne convertit jamais du tout.

Rendre l'état **non** converti — ce que fait `ParkingCoastStage`, qui rend `previousState` intact —
laisserait `mission.getCurrentState()` géocentrique pendant que `fly` propage du sélénocentrique :
deux vérités sur le même instant, et l'étage suivant lirait la mauvaise. En rendant l'état converti,
le `convert` de `fly` devient l'identité par égalité de référence (§1.3), et les deux passes
publient la même chose.

**C'est le seul endroit du lot où une erreur ne lève pas** : `createOptimizationPropagator` prend son
repère de l'état initial, donc un état GCRF intégré avec un µ lunaire au centre ne produit ni
exception ni avertissement — seulement une trajectoire fausse. Le §6 l'asserte directement.

### 3.2 Le contexte lunaire est dérivé, pas écrit

```java
gravitationalContext(mission) → ArcTransition.across(mission.gravitationalContext(), MOON)
```

Mesuré égal à `moon().withPerturbers(EARTH, SUN)`, même instance de repère. Écrire la liste en dur
serait un second endroit disant ce qu'`ArcTransition.across` dit déjà, et il divergerait le jour où
la mission déclare un troisième perturbateur. La règle « le corps quitté reste perturbateur du corps
désormais volé » est écrite une fois, dans `ArcTransition`, et `PHY-4 / L4` l'a mesurée à 0,246 m sur
six heures.

Les deux étages portent la même redéfinition d'une ligne. Une classe de base lunaire commune serait
un troisième fichier pour deux lignes, et masquerait quelle déclaration appartient à quel étage.

### 3.3 Les cinq déclarations

| déclaration | valeur | conséquence |
|---|---|---|
| `isPropulsive` | `false` (hérité) | échantillonnage 60 s → **~1 070 points** pour 17,8 h ; `L0` mesure 3 annonçait ~1 100 |
| `maxStepSeconds` | `COAST_MAX_STEP` = 300 s (hérité) | aucune combustion à héberger |
| `gravitationalContext` | dérivé (§3.2) | |
| `soiTransitions` | **vide** | il part *sur* la sphère ; y réarmer une frontière le couperait au premier pas |
| `soiCrossingEndsStage` | `false` (hérité) | c'est le coast de `L1` qui porte la traversée, une fois |

---

## 4. `stage/LunarInsertionStage`

Jumeau de `TLIBurnStage` : `enter` planifie et **ne bouge aucune masse**, `configure` vole la
combustion et pose la coupure, `propagateStandalone` replanifie et vole sur son propre propagateur.
`ArcTransition.convert` en tête de `enter` là aussi — idempotent par référence donc gratuit, et
l'étage cesse de supposer qui le précède.

### 4.1 Il ne prend pas d'altitude en paramètre

C'est plus mince que le découpage ne le laisse croire, et c'est une décision. L'altitude d'orbite
lunaire est visée **une seule fois**, par `TLIBurnStage(name, targetPeriluneAltitude)` ; l'insertion
circularise le périlune qu'elle **atteint**. Un second paramètre serait une seconde vérité sur la
même cible, et rien dans l'étage ne saurait quoi faire du désaccord — le rattraper coûterait un
changement de plan hors périmètre, l'ignorer en ferait un ornement.

C'est l'objectif de `L5` (`OrbitInsertionObjective.circular(MOON, altitude, i)`) qui vérifie que les
deux coïncident, et c'est sa place : un objectif juge, un étage vole.

### 4.2 Les déclarations, et le journal

| déclaration | valeur |
|---|---|
| `isPropulsive` | `true` → échantillonnage 1 s → **351 points** |
| `maxStepSeconds` | `burnLimitedMaxStep` → mesuré **30 s**, le plafond `SAFE_MAX_STEP` |
| `gravitationalContext` | dérivé (§3.2) |
| `soiTransitions` | vide, et **ne pourrait pas** : `StageLegRunner` refuse une transition sur un étage propulsif |
| coast de stabilisation | **60 s**, la constante et le motif structurel de `TLIBurnStage` — donner à l'étage une coupure à juger, et séparer la combustion du coast qui suit dans la trace |

Le journal porte ce que le découpage demande — « le Δv commandé contre l'impulsionnel, et l'écart
journalisé » : durée, arc balayé, commandé, impulsionnel, surcoût, masses, périlune volé, apsides
atteintes.

---

## 5. Une correction au critère de fermeture du découpage

Le découpage demande « les apsides atteintes dans la bande que la **mesure 5** de `L0` fixe ». Cette
mesure vaut **0,08 km** — mais c'est une **respiration** sous perturbation tierce sur douze
révolutions, une figure de tenue d'orbite et non de précision d'insertion. Aucune insertion ne
l'atteint : le meilleur mesuré au §2.4 est **0,30 km**, et l'excentricité résiduelle de 7·10⁻⁵ vaut à
elle seule 0,13 km sur un rayon de 1 837 km.

**Bande retenue : ±0,5 km sur les deux apsides, et `e ≤ 5·10⁻⁴`.** Soit 40 % de marge sur le pire
cas mesuré, et près d'un ordre de grandeur sous ce qu'un seul bouton donnerait.

---

## 6. Les tests

Trois classes, sur **une hyperbole sélénocentrique fabriquée** — aucune mission neuve, aucun vol de
quatre jours, comme le découpage l'exige. Onze cas, quelques secondes de paroi.

**La fixture doit être perturbée, et c'est une condition de validité, pas un détail** (§1.2 pt 1).
Son périlune képlérien est bissecté une fois hors ligne — **767,364 km pour un vol à 99,999 km à
l'époque `2026-03-31T00:00:00Z`** — et la constante porte sa provenance en commentaire, ce qui est
exactement l'un des quatre cas où `CLAUDE.md` autorise un commentaire de corps.

### 6.1 `LunarInsertionPlanTest` — 5 cas

- **l'arrivée détectée est le périlune réellement volé**, contre une recherche ternaire fine, à
  10⁻³ s ;
- **les dents de la fixture** : le périlune volé s'écarte du képlérien de plus de 500 km et de plus
  de 300 s. Sans ce cas, une fixture qui cesserait d'être perturbée rendrait les quatre autres
  vacants — et c'est le seul défaut de ce lot qui produirait onze tests verts sur du code faux ;
- **l'avance à l'allumage** est une demi-combustion avant le périlune volé, et le périlune redétecté
  depuis l'allumage tombe à 10⁻³ s de ce qu'elle annonçait (mesuré 8·10⁻⁴) ;
- **les apsides atteintes** dans ±0,5 km avec `e ≤ 5·10⁻⁴`, et le Δv commandé au-dessus de
  l'impulsionnel de 1 à 10 m/s — les deux bornes encadrant les +3,45 mesurés, la basse disant que la
  perte finie existe et la haute qu'elle reste une correction ;
- **les quatre refus**, un cas paramétré.

### 6.2 `LunarApproachCoastStageTest` — 3 cas

- **les deux passes s'arrêtent au même allumage** — jumeau de
  `TranslunarCoastStageTest.bothPassesStopOnTheSphere`, et la propriété que `L1` a payée ;
- **l'état d'entrée géocentrique ressort sélénocentrique**, sur les deux passes. C'est le §3.1, et
  c'est le défaut qui ne lève pas ;
- **l'étage déclare une fin et ne déclare aucune frontière** : `getConfiguredEndDate() != null` après
  `configure`, `soiTransitions` vide.

### 6.3 `LunarInsertionStageTest` — 3 cas

- **l'insertion depuis l'allumage tombe dans la bande**, sur deux orientations ;
- **les deux passes volent la même combustion** ;
- **le refus d'une hyperbole qui percute**, remonté depuis l'étage et non depuis le plan.

---

## 7. Ce que `L4` ne fait pas

1. **Aucune mission neuve.** `MissionSpec.LunarOrbit`, la branche de `MissionComposer.compose` et
   `LunarOrbitMission` sont à `L5`. Les trois classes de ce lot ne sont appelées par rien.
2. **Le coast terminal lunaire n'est pas écrit.** `CoastingStage` ne déclare pas de contexte ; c'est
   `L5` qui le lui fera déclarer, et c'est le troisième des « trois derniers étages » du découpage
   §2.3 pt 2.
3. **Aucune inclinaison n'est visée.** Elle reste subie (`L0` mesure 1 : dispersion 22,3°), et `L5`
   la journalise. ζ tourne la poussée **dans le plan** : il ne touche pas au plan, délibérément.
4. **La LOI reste unique.** Le découpage §6 pt 4 tient, et la mesure le confirme : 4,95 % d'une
   révolution.
5. **`MissionOptimizer.resolveTargetAltitude` n'est pas réparé** — le §6 pt 11 du découpage
   n'apparaît qu'avec l'objectif que `L5` porte.
6. **Rien n'est mesuré sur une vraie arrivée.** Toutes les mesures de ce document sont sur une
   hyperbole fabriquée. Le §1.2 pt 1 argumente que la marée ne dépend que de la géométrie et non de
   la provenance de l'état, mais c'est `L5` qui le vérifiera sur un vol du sol.

---

## 8. Risques

**Le seul risque réel est le repère** (§3.1). Un état géocentrique intégré avec un µ lunaire au
centre ne lève rien : `createOptimizationPropagator` prend son repère de l'état initial. Trois choses
le bornent, dans cet ordre — la conversion est en tête de `enter` sur les **deux** étages ; elle est
idempotente par égalité de référence, donc sans effet sur un état déjà lunaire ; et le deuxième cas
de `LunarApproachCoastStageTest` l'asserte sur les deux passes.

**Le second est la fixture qui perdrait ses dents** (§6.1). Il est borné par un test qui ne teste
rien d'autre que cela.

**Ce qui n'est pas un risque** : la détection d'apside, la conversion de repère, le garde de rentrée
sur l'arc lunaire, le pas d'intégration et la combustion à poussée constante. Les cinq sont livrés
par `PHY-4`, `MIS-4` et `L1`, et mesurés ici même.

---

## 9. Ce que `L4` lègue

- **À `L5`** : deux étages volés, une classe de plan qui refuse quatre fois, et la certitude que le
  contexte lunaire se dérive au lieu de s'écrire. Il reste à composer la chaîne, à faire déclarer le
  contexte au coast terminal, et à porter l'objectif.
- **À `MIS-11`** : une combustion sélénocentrique calibrée sur ce qu'elle vole, dont le retour aura
  besoin dans l'autre sens — et la jacobienne en forme close qui la rend à trois évaluations.
- **Au socle** : la mesure que la forme close képlérienne n'a aucune valeur à l'échelle d'une sphère
  d'influence, et le seul usage qui lui reste — un majorant d'horizon de recherche.
- **À la clôture du chantier** : le §5, si `L5` trouve une bande plus juste que ±0,5 km sur une vraie
  arrivée.

---

## 10. Fermeture — `L4` est implémenté

**Verdict : la propriété du §1 est vraie, et les onze cas la tiennent.** Trois classes de test,
**11 cas, 0 échec**, quelques secondes de paroi. Sur les paquets voisins — `gravity`,
`mission.stage`, `mission.runtime`, `mission.vehicle`, `OrbitElements` — **263 cas, un seul rouge,
préexistant** (§10.3).

### 10.1 Les chiffres du document, relevés sur le code livré

| | document | livré |
|---|---|---|
| détection du périlune contre une recherche ternaire | 8·10⁻⁴ s | **63 966,9853 contre 63 966,9861 s** |
| forme close contre périlune volé | +722,6 s | **+722,5 s** (64 689,5 contre 63 967,0) |
| képlérien contre volé, en altitude | 767,4 → 100 km | **767,4 → 100,000 km**, 667,4 km d'écart |
| avance à l'allumage, redétectée | 8·10⁻⁴ s | **175,0478 contre 175,0486 s** |
| durée de combustion | 350,1 s | **351,38 s** commandée |
| Δv commandé / impulsionnel | 827,2 / 823,8 (+3,45) | **827,215 / 823,755 (+3,460)** |
| apsides atteintes, plan seul | 99,84 × 100,09 km | **99,864 × 100,098 km**, `e = 6,38·10⁻⁵` |
| apsides atteintes, chaîne complète | — | **99,860 × 100,098 km**, `e = 6,49·10⁻⁵`, `i = 149,945°` |
| durée de l'approche | 16,3 – 18,3 h (`L0`) | **17,7200 h** |

**Les deux passes ne s'accordent pas à une tolérance près : elles sont identiques.** Écart de date,
de position et de masse **exactement nul**, sur le coast comme sur la combustion. C'est plus fort que
l'accord de `TranslunarCoastStageTest`, qui compare deux interpolations indépendantes d'une même
racine ; ici les deux passes construisent le même propagateur depuis le même état avec les mêmes
réglages, et la seule chose qui pouvait diverger — la date d'allumage — est un champ lu deux fois.

### 10.2 Trois écarts au plan

1. **Le refus d'ergols est prononcé deux fois, et la première avant toute propagation.** Le §2.5
   n'en prévoyait qu'un, après la boucle, sur le Δv commandé. Deux mesures l'ont déplacé : d'abord
   `Physics.computeBurnDurationCapped` **arrête la combustion au plancher d'extinction**, donc une
   masse finale sous le plancher est inatteignable et le test du §2.5 tel qu'écrit ne serait jamais
   vrai — le verdict est la troncature, pas la masse. Ensuite, sur un réservoir d'un ordre de
   grandeur trop petit, la combustion écrêtée laisse une trajectoire que l'intégrateur ne suit pas :
   il lève `minimal step size reached` **depuis la boucle**, ce qui est une façon illisible de dire
   que le véhicule ne peut pas. La garde sur le Δv **impulsionnel** est donc posée avant la première
   propagation, et celle sur le **commandé** reste après la boucle pour la bande étroite où
   l'impulsion passe et la combustion qui la remplace ne passe pas.
2. **`addBurn` n'arme pas la garde d'extinction.** Le plan assemble la manœuvre, l'étage arme la
   garde — parce que `DepletionGuard.arm` veut un nom à journaliser, et qu'un nom d'étage n'a rien à
   faire dans une classe de manœuvre. `TLIBurnStage` fait les deux dans sa méthode privée ; ici la
   méthode est partagée, et la coupure tombe là.
3. **`logBurn` prend le contexte de vol.** Il lui faut le rayon de l'orbite atteinte pour dire l'arc
   balayé, et `Burn` porte une altitude. Passer le contexte évite d'ajouter au record une seconde
   forme de la même grandeur.

### 10.3 Un rouge préexistant, non attribuable au lot

`ParkingCoastStageTest > The parking coast advances the stage walk by the coast it resolved` échoue.
**Vérifié préexistant** : les sept fichiers du lot retirés de l'arbre, `cleanTest` puis relance, il
échoue à l'identique (`ParkingCoastStageTest:75`). C'est le rouge que `MIS-4 / L6` a laissé.

`spotlessApply` reformate de nouveau **huit** fichiers de `L1` sans rapport avec ce lot
(`SoiCrossingDetector`, `MissionStage`, `StageLegRunner`, `CoastingStage`, `TranslunarCoastStage`,
`TranslunarBoundaryFlightTest`, `SoiTerminatingStageTest`, `TranslunarCoastStageTest`) ; ils ont été
rendus à leur état commité, comme aux lots précédents. Aucun des sept fichiers de `L4` n'est en
violation.
