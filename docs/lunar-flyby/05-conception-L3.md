# MIS-4 / L3 — L'objectif de survol

Lot **L3** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur les chiffres de
[`02-baseline-L0.md`](02-baseline-L0.md). Il rend vraie une seule propriété : **un survol est
notable**.

C'est le seul lot de `L1`–`L3` qui change un comportement. Les deux autres ajoutent des pièces sans
appelant ; celui-ci corrige un objectif faux et donne à la machinerie de faisabilité un prédicat qui
sait lire l'arc lunaire.

**Trois énoncés du découpage sont corrigés ici.**

1. **La faille du §2.3 n'est pas celle qui est décrite.** Le découpage dit que le maximum est
   aberrant ; le vrai défaut est que **le mauvais arc est sélectionné**, et le maximum n'en est
   qu'un symptôme dans le cas où la sélection tombe juste par chance (§1.2).
2. **Le prédicat ne va pas dans `MissionLoadEvaluator`, et ce lot ne le touche pas.** Le §4 place
   « la branche correspondante dans `MissionLoadEvaluator.objectiveMet` » et annonce cette classe
   dans le périmètre ; l'y mettre créerait un cycle de packages, et l'y déléguer depuis
   `mission/objective/` aussi (§1.5, §3).
3. **L'éphéméride de la démo ne peut pas fermer ce lot à elle seule.** Le §4 ferme sur « un test sur
   l'éphéméride de la démo lunaire » ; or la démo ne vole que deux arcs et le cas que la faille rate
   en demande trois. La démo ferme le câblage, une fixture ferme la faille (§5).

---

## 1. Ce que le code dit avant qu'on y touche

### 1.1 — Personne ne lit le type d'un objectif

`MissionObjective` est scellé sur un seul membre depuis toujours :

```java
public sealed interface MissionObjective permits OrbitInsertionObjective {
  SolarSystemBody body();
}
```

`getObjective()` a **quatre** appelants dans tout le dépôt, et l'un d'eux est dans `Mission`
lui-même. Des trois autres :

| appelant | ce qu'il lit | ce qu'il fait d'un `FlybyObjective` |
|---|---|---|
| `MissionDisplayPanelWidget:216` | `body()` seul | rien, il est indifférent au type |
| `MissionOptimizer:573` | `instanceof`, sinon `NaN` | rend `NaN`, et son unique appelant (`:409`) est enfermé dans `if (problem instanceof GravityTurnProblem)` |
| `MissionLoadEvaluator:474` | `instanceof`, sinon jette | jette — voir §6 a |

**Aucun `switch` sur `MissionObjective` n'existe.** Les deux seules lectures typées sont ces deux
`instanceof`, tous les deux avec une branche de repli. Ajouter un membre au `permits` ne casse donc
**aucune unité de compilation** : le compilateur n'a nulle part où se plaindre, ce qui est
exactement le problème que le `switch` exhaustif du §3 vient créer.

### 1.2 — La faille est une erreur de sélection, pas un maximum aberrant

`MissionLoadEvaluator.objectiveMet` (`:379`) mesure sur les points qui satisfont
`FINAL_COAST_STAGE.equals(point.stageName()) && point.arc().body() == finalArcBody`, où
`finalArcBody` est l'arc du **dernier** échantillon du coast terminal.

Le découpage §2.3 en tire que le maximum, à ~60 000 km, rend `objectiveMet` faux par construction.
C'est vrai, et c'est secondaire. Ce que L0 §4 mesure est plus grave :

| jour | entrée sphère | sortie | périlune | max arc lunaire | **dernier arc** |
|---|---|---|---|---|---|
| 0 | 3,08 j | 4,59 j | 100,4 km | 67 348 km | `EARTH` |
| 6 | 3,10 j | 4,58 j | 101,2 km | 67 676 km | `EARTH` |
| 18 | 3,16 j | 4,51 j | 99,4 km | 61 711 km | `EARTH` |
| 24 | 3,12 j | 4,62 j | 100,6 km | 65 972 km | `EARTH` |

Le vaisseau **ressort** de la sphère lunaire entre 4,51 j et 4,62 j. À l'horizon de `L4` (~7 j) le
dernier arc est donc `EARTH`, `finalArcBody` rend `EARTH`, et **l'arc lunaire n'est jamais regardé
du tout**. Le prédicat ne mesure pas mal le survol : il mesure une autre trajectoire.

Sur la démo d'aujourd'hui l'horizon est de 4,5 j, donc le vol s'arrête dans l'arc lunaire et la
sélection tombe juste — **par accident, avec une marge de 14 min à 2,9 h** selon l'époque. C'est le
genre de justesse qui disparaît sans que rien ne le signale le jour où l'horizon change, et `L4`
change l'horizon.

### 1.3 — Le prédicat existe déjà, écrit deux fois, en test

L3 n'invente pas la mesure. Elle est écrite à l'identique à deux endroits :

```java
double perilune = Double.POSITIVE_INFINITY;
for (MissionEphemerisPoint point : ephemeris.allPoints()) {
  if (point.arc().body() == SolarSystemBody.MOON) {
    perilune = FastMath.min(perilune, point.altitudeMeters());
  }
}
```

— `LunarTransferFlightTest:103-106` et `LunarBaselineProbeTest:257-261`. Deux copies, aucune en
production, et le seul endroit du dépôt qui *devrait* la porter fait autre chose. Le lot extrait un
idiome, il ne conçoit pas une mesure.

Noter ce que les deux copies **ne** filtrent **pas** : ni `stageName`, ni le rang de l'arc. Elles
prennent tous les points du corps. C'est la forme que le §3.2 reprend.

### 1.4 — `"Coasting"` est un piège déjà nommé

`FINAL_COAST_STAGE` vaut la chaîne `"Coasting"`, et `LunarTransferMission.stages()` porte un
commentaire disant que son coast s'appelle ainsi **parce que** `MissionLoadEvaluator` filtre sur ce
nom. Le couplage est documenté, ce qui vaut mieux que caché, mais il reste un couplage par chaîne de
caractères entre une classe de dimensionnement et le nom d'affichage d'un étage.

Un survol n'a de toute façon aucune raison de tomber pendant un coast terminal — à `L4` il tombe au
milieu du vol, deux jours et demi avant la fin. Le §3.2 ne filtre donc pas sur l'étage, et ne
réplique pas le piège.

### 1.5 — `objective` et `runtime`, et le sens de la flèche

`simulation.mission.runtime` dépend déjà de `simulation.mission.objective` :
`MissionLoadEvaluator` et `MissionOptimizer` importent tous deux `OrbitInsertionObjective`.

Poser l'évaluateur dans `objective/` et lui faire déléguer sa branche insertion à
`MissionLoadEvaluator.objectiveMet` — la seule façon de ne pas dupliquer cette branche — ferait
repartir la dépendance en sens inverse et **créerait un cycle de packages**. L'alternative, déplacer
`objectiveMet` dans `objective/`, déplace une API `public static` et réécrit ses **dix** appels dans
`MissionLoadEvaluatorTest`, pour un lot dont l'apport tient en un record.

D'où le §3 : l'évaluateur vit dans `runtime/`, à côté de la classe dont il réutilise la branche.

### 1.6 — Le rayon d'explosion de la démo est nul

Changer l'objectif de `LunarTransferMission` (§4) ne peut rien casser aujourd'hui, et chacune des
raisons est vérifiable :

- `MissionPlanOptimizer:95` fait `entry.spec().map(this::minimizedLoadPlanner)
  .orElseGet(this::fixedLoadPlanner)` ; la démo est une entrée *legacy* sans spec, elle prend donc
  `fixedLoadPlanner` et **n'atteint jamais `MissionLoadEvaluator`** ;
- aucun étage de cette mission n'est optimisable — `LunarTransferFlightTest` le dit dans le javadoc
  de `MAX_EVALUATIONS = 1` —, donc le `NaN` de `resolveTargetAltitude` n'est jamais lu ;
- `MissionDisplayPanelWidget:216` ne lit que `body()` ;
- `LunarTransferFlightTest` n'assertait **rien** sur l'objectif avant ce lot.

C'est ce qui permet de faire basculer la démo dans le même lot que le type, plutôt que d'attendre
`L4`.

---

## 2. `FlybyObjective`

```java
public record FlybyObjective(
    SolarSystemBody body, double closestApproachAltitude, double toleranceMeters)
    implements MissionObjective {
```

Constructeur compact : `body` non nul, les deux réels finis et **strictement positifs**. Une visée
sous la surface n'est pas une cible mal réglée, c'est un impact — et L0 a montré que la voie d'échec
réelle de ce chantier n'est pas une altitude négative demandée mais un **plancher** de périlune
atteignable (1 873 km au jour 12, contre 100 km visés), qui se solde par un refus de la visée.

### 2.1 — Le composant s'appelle `closestApproachAltitude`

Pas `perileneAltitude`, et pas non plus `perilune`. Trois raisons :

1. **`perilene` est un francisme**, déjà présent dans `LunarTransferMission` (`perileneAltitude`,
   `getPerileneAltitude()`), à côté d'une constante qui s'écrit `DEFAULT_PERILUNE_ALTITUDE` : le
   fichier se contredit lui-même. Un type neuf n'a pas à propager cela.
2. **Le record n'est pas lunaire.** `FlybyObjective(MARS, …)` n'a pas de périlune, et la hiérarchie
   est scellée sur des objectifs, pas sur des corps.
3. C'est le nom de la grandeur mesurée au §3.2 : le minimum d'altitude sur l'arc, qui est
   littéralement l'altitude de l'approche au plus près.

### 2.2 — La tolérance est absolue, et portée par le record

C'est l'asymétrie assumée du lot : `OrbitInsertionObjective` ne porte **pas** sa tolérance — elle
est un paramètre du prédicat, en ratio — et `FlybyObjective` porte la sienne, en mètres.

**Pourquoi l'insertion a raison de ne pas la porter.** Elle a deux cibles, périgée et apogée, qu'un
ratio couvre ensemble, et des appelants qui choisissent réellement leur bande : 7 % pour LEO
(`DEFAULT_OBJECTIVE_TOLERANCE_RATIO`), ±50 km pour GEO, où le javadoc de
`MissionPlanOptimizer:52` explique que le défaut LEO à ±7 % accepterait une orbite à des milliers de
km du GEO. Ce site **divise déjà** ses 50 km par l'altitude cible (`:170`) pour rentrer dans une API
qui ne sait faire que du ratio — le contournement est écrit, il n'est pas hypothétique.

**Pourquoi le survol a raison de la porter.** Il a **une** cible, et sa bande n'est pas un choix
d'appelant, elle est dictée par la mesure : 0,9 km de sur-lecture de l'approche au pas
d'échantillonnage de 60 s, ~1 km de convergence de la visée. Ces deux erreurs sont **absolues**. Un
ratio de 7 % donnerait 7 km sur une visée à 100 km et 7 000 km sur une visée à 100 000 km — la même
bande nominale décrirait deux exigences sans aucun rapport, et la seconde n'exigerait plus rien.

**Ce que l'asymétrie coûte, localement :** la signature du `switch` exhaustif du §3.1 porte un
paramètre de tolérance que la branche survol ignore. C'est payé au nom du paramètre, pas à une note
de javadoc.

---

## 3. `ObjectiveEvaluator`, dans `runtime/`

### 3.1 — La signature

```java
public static boolean met(
    MissionEphemeris ephemeris, MissionObjective objective, double insertionToleranceRatio) {
  return switch (objective) {
    case OrbitInsertionObjective insertion ->
        MissionLoadEvaluator.objectiveMet(ephemeris, insertion, insertionToleranceRatio);
    case FlybyObjective flyby -> flybyMet(ephemeris, flyby);
  };
}
```

**Le `switch` est exhaustif et sans `default`.** C'est lui, et rien d'autre dans le dépôt, qui fait
travailler le `sealed` : le §1.1 a montré qu'aucun site existant ne se plaindrait d'un troisième
membre. Le jour où `L5` en ajoute un, le compilateur désigne cette ligne.

**Le paramètre s'appelle `insertionToleranceRatio`, pas `toleranceRatio`.** C'est le prix de
l'asymétrie du §2.2, payé au nom plutôt qu'au commentaire : un lecteur qui voit la branche survol
l'ignorer n'a pas à chercher pourquoi.

**La branche insertion délègue.** `MissionLoadEvaluator.objectiveMet` reste où elle est, avec sa
signature, son javadoc et ses dix appels de test intacts. L3 n'édite pas cette classe (§1.5).

### 3.2 — La branche survol

Le cœur est l'idiome du §1.3 : minimum de `altitudeMeters` sur **tous** les points dont
`arc().body()` est le corps visé, quel que soit l'étage et quel que soit le rang de l'arc dans la
séquence. Puis comparaison à `closestApproachAltitude` dans `toleranceMeters`.

Sélection **par corps seul**, donc. C'est ce que L0 §4 impose : à `L4` la séquence est
`[EARTH, MOON, EARTH]` et l'arc à mesurer est celui du milieu, ni le premier ni le dernier. Une
sélection par rang aurait à décider lequel, et la réponse dépendrait de l'horizon.

**Le maximum n'est jamais lu.** C'est la propriété que le découpage demande, et la seule différence
de fond avec la branche insertion : sur l'arc lunaire de la démo le maximum vaut 67 348 km, ce qui
est l'altitude à l'entrée de la sphère et ne décrit aucune intention.

### 3.3 — Les trois refus

Trois cas se décident avant toute comparaison.

**a. Aucun point sur l'arc visé → `false`.** Le vol n'a pas atteint le corps. C'est le cas que `L4`
doit voir — sa géométrie peut manquer la Lune — et il est aujourd'hui indiscernable d'un succès.

**b. Le minimum est ≤ 0 → `false`.** Un impact n'est pas un objectif atteint, quelle que soit la
tolérance déclarée. Le test est redondant tant que `toleranceMeters < closestApproachAltitude`, et
il est écrit quand même : la propriété doit tenir sans dépendre de ce rapport. Le javadoc de
`LunarTransferFlightTest.theAimConvergesOrRefusesAcrossALunarMonth` rapporte une visée à
**−53 km**, calculée et volée comme si c'était un plan.

**c. Le corps visé est celui du premier point de l'éphéméride → jette.** Un survol se note sur un arc
qu'on n'a pas commencé. Sans cette garde, `FlybyObjective(EARTH, …)` sur une mission qui décolle
lirait `min = 0` — le pas de tir — et un objectif à 200 km serait manqué de 200 km sans que rien
n'explique pourquoi.

**Pourquoi (c) jette là où (a) et (b) rendent `false`.** (a) et (b) sont des faits de vol : le
vaisseau n'est pas arrivé, ou il s'est écrasé. (c) est un **objectif mal formé**, ce qui est la
définition d'`IllegalArgumentException` — et c'est déjà ce que `MissionLoadEvaluator:474` fait, dans
le même package, pour un objectif du mauvais type.

L'argument décisif est le diagnostic. Rendu en `false`, (c) traverse le ET de faisabilité et
ressort en « aucune charge faisable » : l'auteur cherche des ergols pendant des heures pour un
objectif qui ne pouvait pas être satisfait. Jeté, il dit ce qu'il est. Le risque habituel du jet —
faire tomber une bissection en cours de route — ne s'applique pas : la condition ne dépend que de
l'objectif et du **premier** point, jamais de λ, donc elle tombe à la première évaluation, avant
qu'aucune recherche n'ait brûlé de temps.

---

## 4. La démo bascule

`LunarTransferMission:88` :

```java
new OrbitInsertionObjective(SolarSystemBody.MOON, perileneAltitude, perileneAltitude, 0.0)
```

devient

```java
new FlybyObjective(SolarSystemBody.MOON, perileneAltitude, DEFAULT_PERILUNE_TOLERANCE)
```

L'ancienne ligne décrivait une **orbite circulaire lunaire à 100 km** — périgée = apogée = 100 km,
inclinaison 0. Cette mission ne fait aucune tentative de s'y insérer et ne s'y insère jamais : elle
passe. Ce n'était donc pas seulement un objectif mal noté, c'était un objectif **faux**, qui
décrivait le vol de quelqu'un d'autre.

### 4.1 — ±10 km, et le nombre n'existe qu'une fois

La bande est déjà écrite et déjà volée : `LunarTransferFlightTest.PERILUNE_BAND = 10_000.0`, avec sa
justification — un ordre de grandeur au-dessus des 0,9 km que l'échantillonnage à 60 s du coast peut
sur-lire sur l'approche, et au-dessus du 1 km vers lequel la sécante de visée converge.

L3 la déplace en production, javadoc comprise :

```java
public static final double DEFAULT_PERILUNE_TOLERANCE = 10_000.0;
```

à côté de `DEFAULT_PERILUNE_ALTITUDE`, et le test lit la constante de production au lieu de la
sienne. Il lit **déjà** `DEFAULT_PERILUNE_ALTITUDE` pour la cible (`:112`) ; garder les deux nombres
de part et d'autre de la frontière, dont un dupliqué, serait le seul endroit du fichier où la cible
et sa bande ne viendraient pas du même endroit.

### 4.2 — `getPerileneAltitude()` est supprimé

Il a **zéro appelant**, et il devient doublement mort une fois que `getObjective()` rend un
`FlybyObjective` qui porte l'altitude de façon typée. Le champ `perileneAltitude` reste — il sert au
constructeur — mais l'accesseur part avec le lot qui ouvre le fichier.

---

## 5. Les tests de fermeture

### 5.1 — La fixture, dans `ObjectiveEvaluatorTest`

Sur le patron de `MissionLoadEvaluatorTest` : éphémérides synthétiques, aucune propagation,
millisecondes. Les altitudes sont celles que L0 §4 a mesurées au jour 0 — périlune **100,4 km**,
maximum de l'arc lunaire **67 348 km** — pour que la fixture décrive un vol qui a eu lieu et non un
vol inventé.

**L'éphéméride pivot est `[EARTH, MOON, EARTH]`, et ce n'est pas celle de la démo.** À 4,5 j la
démo finit dans l'arc lunaire ; c'est `L4`, à ~7 j, qui fera revenir l'arc géocentrique. La fixture
épingle donc exactement le cas que `finalCoastArcBody` rate (§1.2), et qui n'existera nulle part
ailleurs avant `L4`. C'est la raison d'être du lot, et c'est la correction 3 du préambule : la
démo ne peut pas fermer cela.

| test | ce qu'il ferme |
|---|---|
| trois arcs, périlune 100,4 km → `true` | la sélection par corps, où que l'arc tombe dans la séquence |
| le même vol jugé par `OrbitInsertionObjective(MOON, 100 km, 100 km)` → `false` | le contraste : ce que l'ancien objectif disait du même vol |
| corps jamais atteint → `false` | §3.3 a — le vol qui n'arrive pas, aujourd'hui indiscernable d'un succès |
| minimum ≤ 0 → `false` | §3.3 b — l'impact, indépendamment de la tolérance |
| objectif sur le corps du premier point → jette | §3.3 c |
| une insertion rend ce que `MissionLoadEvaluator.objectiveMet` rend | la délégation du §3.1 |

Le deuxième mérite d'être un test et non un commentaire : c'est la **seule trace exécutable** de ce
que L3 corrige. Sans lui, la faille du §2.3 disparaît du dépôt sans laisser de témoin.

### 5.2 — L'assertion volée, dans `LunarTransferFlightTest`

Une ligne, après la lecture de la périlune, pour zéro seconde de mur — l'éphéméride est déjà
calculée :

```java
assertTrue(
    ObjectiveEvaluator.met(ephemeris, mission.getObjective(), Double.NaN),
    "the flown flyby must satisfy the mission's own objective");
```

**`Double.NaN` pour le ratio d'insertion est délibéré et fait partie de l'assertion.** La branche
survol ne doit pas le lire ; s'il finissait dans une comparaison, `NaN` la rendrait fausse et le
test tomberait. Passer `DEFAULT_OBJECTIVE_TOLERANCE_RATIO` aurait masqué exactement ce défaut-là.

Ce que cette ligne prouve et que la fixture ne peut pas : que la ligne de production changée au §4
est réellement câblée, que la sélection par corps tient sur une **vraie** éphéméride, et que la
garde du §3.3 c ne se déclenche pas sur une mission qui part de la Terre.

---

## 6. Limitations assumées

**a. Le chemin λ ne sait toujours pas noter un survol — et L3 le rend bruyant.**
`MissionLoadEvaluator:326` appelle `orbitInsertionObjective(mission.getObjective())`, qui **jette**
sur un `FlybyObjective` (`:474`). Avant L3 la mission lunaire portait une insertion : ce chemin
l'aurait acceptée et mal notée en silence. Après L3 il jette. Aucun chemin ne le prend aujourd'hui
(§1.6), mais **`L4` le rencontrera à sa première exécution s'il donne une spec à la mission
lunaire**. C'est un mur laissé debout volontairement : le trancher demande de savoir si
`feasibilityObjective` s'élargit, et cette question appartient au lot qui a la mission produit sous
les yeux.

**b. `insertionToleranceRatio` traverse une API pour une seule des deux branches** (§3.1). Payé au
nom du paramètre, pas résorbé.

**c. La garde du §3.3 c refuse un cas légitime.** Une mission Terre→Lune→Terre avec un
`FlybyObjective(EARTH, …)` sur l'arc de **retour** est un survol parfaitement formé, et la garde le
refuse parce que le vol commence lui aussi sur Terre. Faux refus assumé : la garde protège du pas de
tir, qui est le cas réel aujourd'hui, et distinguer les deux demanderait de sélectionner un arc par
son rang **et** son corps — ce que le §3.2 a écarté pour une raison qui, elle, ne bouge pas.
`MIS-11` devra y revenir.

**d. `MissionTargetOrbit` cassera à `L4`.** Son `switch` exhaustif porte sur `MissionSpec` et non
sur l'objectif, il est donc indifférent à L3 ; mais une mission lunaire produit lui ajoutera une
branche. Signalé, pas corrigé : hors périmètre.

**e. Aucune exécution réelle ne couvre les trois arcs avant `L4`.** La démo en vole deux ; le cas
pivot n'est épinglé que par la fixture (§5.1).

**f. Le couplage par la chaîne `"Coasting"` reste entier** dans `MissionLoadEvaluator` (§1.4). L3 ne
le réplique pas ; il ne le supprime pas non plus, puisqu'il ne touche pas cette classe.

---

## 7. Ce que `L3` lègue

**À `L4`** — un objectif qui décrit enfin son vol, et la décision (a) à trancher : élargir
`feasibilityObjective` à `MissionObjective`, ou garder la mission lunaire hors du chemin λ. Plus la
fixture du §5.1, qui décrit d'avance la séquence d'arcs que `L4` doit produire.

**À `L5`** — le `switch` exhaustif du §3.1 : un troisième objectif fera désigner par le compilateur
les points à traiter, ce qu'aucun site du dépôt ne faisait avant ce lot (§1.1).

**À `MIS-11`** — la garde du §3.3 c et son faux refus (c).

**À la clôture du chantier** — `LunarBaselineProbeTest` et sa copie de l'idiome (§1.3) peuvent
disparaître avec le probe ; L3 ne les touche pas.
