# MIS-5 / L1 — Le coast borné par la sphère

Lot **L1** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur la baseline de
[`02-baseline-L0.md`](02-baseline-L0.md). Il rend vraie **une** propriété :

> **Une traversée de sphère d'influence termine l'étage qui l'a déclarée, et les deux passes
> s'arrêtent au même endroit.**

Rien d'autre. Le prédicat est faux par défaut, donc tout le reste du dépôt est inchangé **par
construction et non par mesure** — la même clause que `soiTransitions` s'était donnée en
`PHY-4 / L4` §3.1.

C'est le lot de socle du chantier, et le découpage §3 assume l'arbitrage : le premier consommateur
de production du crochet n'arrive qu'en `L5`. Ce que `L1` a et qu'un chantier de socle séparé
n'aurait pas, c'est de se fermer sur un vol de production — trois étages réels, une vraie visée, une
vraie sphère.

**Cinq faits mesurés dans le code contredisent ou complètent le découpage.** Ils sont au §1.2, et
deux d'entre eux changent ce que le lot livre.

---

## 1. Inventaire mesuré

### 1.1 Ce que le lot touche

| Fichier | Taille | Ce qui bouge |
|---|---|---|
| [`MissionStage`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/MissionStage.java) | 251 l. | une méthode de plus |
| [`StageLegRunner`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/StageLegRunner.java) | 294 l. | un `if` dans la boucle, une garde |
| [`StageChainRunner`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/runtime/StageChainRunner.java) | 273 l. | un refus dans la branche filet |
| [`CoastingStage`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/CoastingStage.java) | 70 l. | un accesseur protégé |
| [`TranslunarCoastStage`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/TranslunarCoastStage.java) | 38 l. | un second constructeur, un `propagateStandalone` |
| [`SoiCrossingDetector`](../../src/main/java/com/smousseur/orbitlab/simulation/gravity/SoiCrossingDetector.java) | 116 l. | une fabrique statique |

Le point d'insertion dans la boucle est exactement entre `StageLegRunner:240`
(`legs.add(… crossing.body())`) et `:262` (la bascule de contexte).

### 1.2 Cinq faits que le découpage ne connaît pas

**1. Le coast translunaire non terminal tombe sur le filet de 7 200 s.** `TranslunarCoastStage` est
un `CoastingStage(name, null)` : `configure()` ne pose aucune date et `getConfiguredEndDate()` reste
`null`. En `MIS-4` il est le **dernier** étage, donc `lastStageCoastSeconds` le borne. En `MIS-5` il
est en milieu de chaîne : `endDateResolver` tombe dans la branche filet de `StageChainRunner:264-270`
et le borne à **7 200 s**, quand sa frontière est à ~265 000 s. L'étage s'arrêterait trois jours trop
tôt sans jamais voir la sphère, et se déclarerait complet. Le crochet du découpage ne suffit donc
pas : **le coast doit porter une borne explicite**, et c'est cette borne qui rend le drapeau
`endDateIsStageCutoff` nécessaire. Le §2.3 pt 4 du découpage a raison, mais pour une raison qu'il
n'écrit pas.

**2. Un `propagateStandalone` non gardé casse `LunarFlybyFlightTest` par l'horizon, pas par la
trajectoire.** `MissionOptimizer` lit `mission.getCurrentState()` après la marche d'étages pour
résoudre `finalCoastSeconds` (`MissionOptimizer:261-267`). Aujourd'hui le coast est un no-op, l'état
reste à la coupure du TLI, et `FixedDuration(7 j)` rend ~7 j. S'il volait 3,07 j, l'horizon rendrait
3,93 j et le vol total ferait **3,95 j** contre l'assertion `7 j ± 120 s` de
`LunarFlybyFlightTest:307`. Trois jours d'écart. La garde par le prédicat n'est pas un raffinement :
c'est ce qui tient la non-régression.

**3. La bande de clôture du découpage est celle de l'autre configuration.** Le §4 / `L1` ferme sur
« 3,08–3,16 j » ; c'est la plage de `MIS-4 / L0`, mesurée depuis un parking à 185 km / 30°. `MIS-5`
vole 400 km / 28,562°, et `L0` §4 mesure **3,071 à 3,148 j** sur cette configuration-là — en disant
explicitement que 3,071 sort du plancher de 3,08. Voir §7.

**4. La passe optimiseur ne convertit jamais de repère.** Le §2.3 pt 2 du découpage écrit que
« `StageLegRunner.fly` convertit l'état d'entrée de chaque étage » : c'est vrai de la passe éphéméride
seule. `MissionOptimizer:226` appelle `stage.propagateStandalone(getCurrentState(), mission)` sans
passer par le leg runner, et `createOptimizationPropagator` ne fixe aucun repère — il vient de l'état
initial. Un étage lunaire y recevrait un état GCRF et intégrerait en GCRF avec un µ lunaire au
centre, silencieusement. Hors du périmètre de `L1` ; écrit au §8 pt 1 pour que `L4` et `L5` ne le
redécouvrent pas.

**5. La fixture bon marché existe déjà.**
[`SoiRoundTripFlightTest`](../../src/test/java/com/smousseur/orbitlab/simulation/mission/runtime/SoiRoundTripFlightTest.java)
construit `StageLegRunner` directement — il est dans le paquet `runtime` — avec son propre
`EndDateResolver`, sur une trajectoire synthétique Terre → Lune → Terre. Tout le comportement de
coupure est testable là, en secondes, sans ascension CMA-ES. Et `ParkingCoastStageTest:101-114` porte
déjà l'état de parking exact de la baseline : circulaire 400 km, i = 28,562°, époque
`2026-03-31T00:00:00Z`.

### 1.3 Ce qui existe déjà et qu'on n'écrira pas

La détection, l'hystérésis, la conversion de repère, la dérivation du contexte d'arrivée et la
tolérance de lecture de l'arrêt : les cinq sont livrées et volées par `PHY-4 / L4` et `L6`.
`BOUNDARY_STOP_TOLERANCE` en particulier est **déjà** le fruit d'une réparation — 51 ps sur la
fixture synthétique de `L4`, **524 µs** sur le premier vol translunaire réel, une bascule
silencieusement manquée et un coast qui se déclarait complet. Ce lot ajoute une décision au-dessus de
ce socle ; il n'en refait aucune partie.

---

## 2. Le prédicat

### 2.1 La quatrième méthode de la famille

`MissionStage` gagne une méthode, de la forme exacte que `maxStepSeconds`, `gravitationalContext`,
`soiTransitions` et `sampleStepSeconds` ont déjà — *une phase est l'unité qui sait ce qu'elle vole,
donc c'est elle qui le déclare* :

```java
public boolean soiCrossingEndsStage(Mission mission) {
  return false;
}
```

**Faux par défaut**, ce qui rend `MIS-4` et tout le reste du dépôt inchangés par construction. Elle
prend `mission` pour la symétrie avec `soiTransitions(Mission)`, même si le premier implémenteur n'en
lit rien : la famille se lit d'un bloc ou elle ne se lit plus.

### 2.2 La garde de cohérence

Déclarer `soiCrossingEndsStage` sans déclarer aucune frontière est une déclaration qui **ne peut
jamais être honorée** : aucun détecteur n'est armé, l'étage ne s'arrête nulle part de particulier, et
rien ne le dit. `StageLegRunner:164` refuse déjà la contradiction « propulsif **et** frontières
déclarées » ; celle-ci est refusée au même endroit et du même geste, avant la première jambe.

---

## 3. La coupure dans `StageLegRunner`

### 3.1 Le retour anticipé

Un booléen calculé une fois avant la boucle, à côté de `transitions`, et un `if` inséré dans le
corps :

```java
legs.add(new Leg(context, legEntry, exit, crossing.body()));

if (endsAtCrossing) {
  return new StageFlight(legs, endDate.date(), false, null);
}

if (sampler != null) {
  sampler.sample(stage, context, exit);
}
```

La date rendue reste la date résolue : elle sert encore aux journaux et à `StageRun.endDate`. Seul le
drapeau tombe. `MAX_LEGS_PER_STAGE` devient inatteignable pour un étage terminant — il n'a jamais
qu'une jambe.

### 3.2 L'échantillon sortant est sauté

C'est le point non évident du lot. L'échantillon de `StageLegRunner:248` existe parce que la jambe
suivante rouvre l'arc dans l'autre repère : sans lui, le dernier point de cet arc-ci serait un pas
d'échantillonnage trop tôt — des centaines de kilomètres à vitesse de transfert.

Quand l'étage se **termine**, il n'y a pas de jambe suivante, et `Collector.onStageEnd` écrit déjà
l'état final dans ce même contexte au même instant. Le garder produirait un point strictement en
double.

La propriété que `L4` §5 a écrite — *la frontière est un instant écrit deux fois, une fois par
repère* — survit intacte : elle passe simplement de la couture entre deux **jambes** à la couture
entre deux **étages**. C'est l'étage suivant, déclarant le contexte lunaire, qui écrit le second
exemplaire, et `ArcTransition.convert` en tête de `fly` le lui donne dans le bon repère.

### 3.3 Le drapeau tombe sur cette branche seulement

`endDateIsStageCutoff` est mis à `false` **sur le retour terminant**, jamais sur l'étage entier. La
chaîne de conséquence est celle que le découpage §5 identifie comme le seul endroit du chantier où un
défaut peut sortir loin de sa cause :

```
endDateIsStageCutoff → shortfallSeconds() → Collector.complete → isComplete() → MissionLoadEvaluator
                                                               → « aucune charge d'ergols faisable »
```

Le placer sur la branche terminante préserve exactement ce que le drapeau sert à voir. Si le coast
s'arrête plus tôt pour **une autre raison** — la garde de rentrée est armée sur chaque jambe de
chaque étage — alors `crossed.get()` est nul, `stoppedOnBoundary` est faux, on repart par le retour
ordinaire de `StageLegRunner:235`, le drapeau reste vrai et la troncature est rapportée. Un `false`
inconditionnel rendrait le coast aveugle à toute troncature autre que la sienne.

---

## 4. La borne

### 4.1 Un `maxTime` ordinaire, dont la valeur est à `L5`

Le fait §1.2 pt 1 l'impose : un étage qui se termine à la frontière doit quand même dire jusqu'où il
va si la frontière ne vient pas. C'est un `maxTime` de `CoastingStage`, rien de plus — pas un
horizon, pas un filet, pas une constante translunaire. **Sa valeur est une décision de chaîne**, donc
de `L5` ; `L1` livre le mécanisme et le refus.

### 4.2 Le refus qui rend l'oubli impossible

Dans la branche filet de `StageChainRunner:264-270`, un étage qui déclare `soiCrossingEndsStage` et
n'a configuré aucune coupure **lève** au lieu d'avertir. Le resolver a déjà `stage` en main ;
`mission` est capturé depuis `run()`.

Le refus est placé là et non dans `StageLegRunner` parce que c'est le seul endroit où les trois
branches sont distinguées : le dernier étage borné par `lastStageCoastSeconds` rend légitimement
`isStageCutoff = false`, et une garde écrite sur ce booléen seul confondrait le cas légitime avec le
filet.

C'est une erreur de câblage, pas une issue de vol : elle lève, comme la contradiction
propulsif + frontières lève déjà. Sans ce refus, l'étage serait borné à 7 200 s quand sa frontière
est à 265 000 s, et se déclarerait complet — mot pour mot le mode de défaillance que
`BOUNDARY_STOP_TOLERANCE` a déjà dû réparer une fois.

### 4.3 Le prix, écrit plutôt que découvert

`MissionLoadEvaluator` attrape toute `RuntimeException` par évaluation de λ et la traduit en
« λ infaisable » — c'est le signal dont la bissection a besoin, pas une erreur à propager. Une erreur
de câblage sortira donc comme une **absence de charge faisable**, avec sa vraie cause dans le journal
et non dans le verdict.

---

## 5. `TranslunarCoastStage`

### 5.1 Deux constructeurs, aucun booléen au site d'appel

```java
/** MIS-4's terminal coast: open-ended, horizon-bounded, cut into legs at the sphere. */
public TranslunarCoastStage(String name)

/** A bounded translunar coast, and therefore one that ENDS at the lunar sphere. */
public TranslunarCoastStage(String name, double boundSeconds)
```

Un champ privé `endsAtTheSphere` que `soiCrossingEndsStage` rend. `LunarFlybyMission` n'est pas
touché — pas même son site d'appel. Le booléen n'apparaît nulle part chez l'appelant : c'est la
présence d'une borne qui distingue les deux usages, et la borne est exactement ce que le §4 rend
obligatoire.

La classe passe de 38 à ~110 lignes, et son javadoc actuel — « *the class exists for one line* » —
cesse d'être vrai. Il faut le réécrire : elle en portera trois, et la troisième est la seule
implémentation de `propagateStandalone` du dépôt qui s'arrête sur autre chose qu'une date.

### 5.2 `propagateStandalone`, gardé

```java
if (!endsAtTheSphere) {
  return super.propagateStandalone(currentState, mission);
}
SpacecraftState entry = enter(currentState, mission);
FlightContext context = flightContext(entry, mission);
NumericalPropagator propagator =
    OrekitService.get().createOptimizationPropagator(context, maxStepSeconds(entry, mission));
propagator.setInitialState(entry);
ReentryGuard.armQuiet(propagator, context.gravity());
armTheSphere(propagator, context.gravity(), mission);
return propagator.propagate(cutoffFrom(entry));
```

C'est le patron de
[`ParkingCoastStage`](../../src/main/java/com/smousseur/orbitlab/simulation/mission/stage/ParkingCoastStage.java)
ligne pour ligne, `armQuiet` compris, et le passage par `createOptimizationPropagator` n'est pas une
commodité : la passe optimiseur doit voler le **même champ 8×8** que la passe éphéméride, sans quoi
les deux dates de traversée ne peuvent pas coïncider.

**La garde n'est pas défensive, elle est structurelle** : le fait §1.2 pt 2 chiffre ce que son
absence coûte, et c'est trois jours sur le vol de `MIS-4`.

### 5.3 Une seule arithmétique de date

`CoastingStage` gagne un `protected AbsoluteDate cutoffFrom(SpacecraftState entry)`, que `configure()`
et le nouveau `propagateStandalone` appellent tous deux, plutôt que `shiftedBy(maxTime)` écrit à deux
endroits. C'est l'invariant qui rend « les deux passes s'arrêtent au même endroit » vrai plutôt
qu'espéré, et il tient parce que `StageChainRunner` pose `mission.setCurrentState(stageEntry)` juste
avant `fly` et que `MissionOptimizer` passe ce même état à `propagateStandalone` : les deux passes
ancrent la coupure sur la même date. C'est la leçon que `ParkingCoastStage` a déjà écrite pour son
propre `ignitionDate` — *absolu plutôt qu'une durée, pour que les deux passes ne puissent pas être en
désaccord sur l'arithmétique*.

**Ce n'est pas la réparation de `CoastingStage`** que le découpage §6 pt 6 refuse : aucun coast du
dépôt ne change de comportement, un accesseur protégé apparaît.

### 5.4 Le même détecteur, une seule règle de direction

Le découpage demande « le même détecteur ». La règle de direction — `scale = 1 + ε` quand on sort de
sa propre sphère, `1` quand on y entre — est aujourd'hui enfermée dans le `private static
armBoundaries` d'une classe elle-même package-private. Elle est donc inatteignable depuis
`mission/stage/`.

`SoiCrossingDetector` gagne une fabrique statique qui porte la règle :

```java
public static SoiCrossingDetector crossingFrom(SphereOfInfluence soi, GravitationalContext from)
```

`StageLegRunner.armBoundaries` et `TranslunarCoastStage` la construisent tous deux. Dix lignes dans
`gravity/`, la règle à un seul endroit, et les deux passes ne peuvent plus dériver l'une de l'autre
sur la seule chose qui décide **où** elles s'arrêtent.

L'orchestration reste dans `StageLegRunner` : la fabrique rend un détecteur, elle ne l'arme pas et ne
porte pas le handler. C'est `PHY-4 / L4` §4 qui a confié la boucle au leg runner, et ce lot ne la lui
reprend pas.

### 5.5 L'état rendu n'est pas converti

`propagateStandalone` rend l'état **du côté terrestre de la frontière**, dans le repère terrestre —
exactement ce que rend le leg runner, dont le dernier `Leg` porte le contexte sortant. Les deux
passes rendent donc la même chose, ce qui est la propriété du lot.

Et c'est là que le fait §1.2 pt 4 se paiera plus tard : la passe optimiseur n'appelle jamais
`ArcTransition.convert`, donc l'étage lunaire qui suivra devra se convertir lui-même. `L1` ne le
répare pas ; il l'écrit (§8 pt 1).

---

## 6. Les tests

Trois classes neuves, et une existante relancée inchangée.

### 6.1 `SoiTerminatingStageTest`

`src/test/.../simulation/mission/runtime/`, voisin de `SoiRoundTripFlightTest`. Classe neuve avec sa
propre fixture — ~60 lignes — plutôt que des cas ajoutés à l'existante, dont le javadoc est
« *PHY-4 / L4 §7.4 — the test of the lot* » et qui vise un aller-retour là où celle-ci veut une seule
entrée. Le `StageLegRunner` y est construit directement avec son `EndDateResolver`, comme la voisine
le fait déjà.

| Cas | Ce qu'il pince |
|---|---|
| une jambe au lieu de trois arcs | `legs().size() == 1`, `crossedBoundary() == MOON`, contexte de sortie `EARTH`, date de sortie = date de traversée |
| le drapeau tombe | `endDateIsStageCutoff()` faux ; via `StageChainRunner.sampling`, `shortfallSeconds() == 0` alors que l'étage s'arrête ~2 j avant sa coupure |
| **le drapeau ne tombe que là** | le même coast, plus un `DateDetector` STOP posé par le test avant la frontière : `crossed` reste nul, le drapeau reste **vrai**, `shortfallSeconds() > 0` |
| pas de point en double | un seul échantillon à la date de traversée, un seul arc |
| refus sans coupure | étage terminant, non dernier, sans `maxTime` → `IllegalStateException` nommant l'étage |
| refus sans frontière | `soiCrossingEndsStage` vrai et `soiTransitions` vide → refus (§2.2) |

**Le troisième cas est celui qui vaut le lot.** C'est lui qui sépare « le drapeau tombe sur la branche
terminante » de « le drapeau tombe », et donc la seule assertion qui protège la chaîne du §3.3.

### 6.2 `TranslunarCoastStageTest`

`src/test/.../simulation/mission/stage/`, jumeau de `ParkingCoastStageTest`. Deux cas :

- **le constructeur à un argument ne change rien** : `soiCrossingEndsStage` faux, et
  `propagateStandalone` rend l'état à la même date. La non-régression `MIS-4` **assertée et non
  supposée**, exactement comme `ParkingCoastStageTest` asserte qu'un `CoastingStage` ordinaire
  n'avance rien ;
- **les deux passes s'arrêtent au même endroit**, depuis un même état d'entrée : dates égales à
  `BOUNDARY_STOP_TOLERANCE`.

L'écart en position est **journalisé et non asserté** : mêmes réglages d'intégrateur, même détecteur,
même champ, il devrait être nul, et une borne devinée avant la mesure ne dirait rien.

**Mesuré à l'implémentation** : la traversée tombe à 14,172 h, et les deux passes sont d'accord à
**0,000e+00 s et 0,000e+00 m** — identiques au bit près, et non « à la tolérance près ». La
prédiction du §5.2 se vérifie donc exactement : deux propagations de mêmes réglages, avec les mêmes
détecteurs sur le même champ, ne peuvent pas diverger. La tolérance reste écrite parce que c'est elle
qui borne la lecture d'une racine, pas parce que quelque chose l'occupe.

### 6.3 `TranslunarBoundaryFlightTest`

`src/test/.../simulation/mission/operation/`, le vol du lot. Trois étages de production —
`ParkingCoastStage → TLIBurnStage → TranslunarCoastStage(borné à 5 j)`. La mission de test déclare
`earth().withPerturbers(MOON, SUN)`, sans quoi la sphère n'est pas traversée dans la bonne physique.

**La géométrie de parking est `TranslunarInjectionPlan.parkingState`, et c'est une correction à ce
qui était prévu ici.** La conception annonçait 400 km / 28,562° repris de `ParkingCoastStageTest` — la
configuration de `MIS-5`. À l'écriture, cela s'est révélé impossible sans duplication : le plan de la
sonde de `L0` contient la direction d'arrivée, et le construire demande `transferPlaneNormal`, qui est
package-private dans `maneuver`. Un plan arbitraire (RAAN nul) n'a aucune raison de converger. La
sortie est `parkingState`, **publique et de production**, gardée précisément comme référence
(`MIS-4 / L6` §1.5) : elle vole 185 km / 30°. Le prix est que la date de traversée appartient à la
bande de `MIS-4 / L0` — 3,08–3,16 j — et non à celle de `MIS-5 / L0`. Les deux sont journalisées, et
le lot ne mesure de toute façon pas une date : il mesure une frontière.

**Ce qu'il asserte** : la traversée dans une bande large — **3,0 à 3,2 j**, assez serrée pour exclure
les deux mauvaises façons de s'arrêter (0,083 j serait le filet de 7 200 s, 5 j sa propre borne),
assez large pour ne pas dépendre du plan ; l'arrêt **sur** la sphère à 1 km ; `isComplete()` vrai ; un
seul arc, `EARTH` ; le dernier point appartenant au coast ; et les deux passes d'accord à
`BOUNDARY_STOP_TOLERANCE`.

**Mesuré à l'implémentation** : coast de **3,0865 j**, dans la bande de `MIS-4 / L0` comme la
configuration le veut ; les deux passes à **8,963e-08 s** l'une de l'autre — 90 ns, quatre ordres sous
la tolérance. Ce n'est pas le zéro exact du §6.2 parce que la chaîne replanifie le TLI de part et
d'autre, mais l'écart est du bruit de dernier bit propagé, pas une divergence.

**Coût mesuré : 29,2 s**, contre les ~15 s estimés — l'estimation était basse d'un facteur deux, et
c'est la propagation de 3,1 j qui coûte plus que les 9,2–11,8 s pour dix jours de `MIS-4 / L0` ne le
laissaient prévoir. Gaté derrière `orbitlab.slowTests` comme `LunarFlybyFlightTest`, et c'est
l'utilisateur qui le lance (découpage §3, contrainte de méthode).

**Pourquoi depuis le parking et non depuis le sol.** Aucune mission ne déclare le prédicat avant `L5`,
et `LunarFlybyMission.buildStages` est `private static` avec des étages figés par `List.copyOf` : un
vol depuis le sol obligerait à recopier la chaîne d'ascension dans le test, définitivement, pour une
chaîne que `L5` ne construira pas de cette façon. Depuis le parking, la copie est de trois étages, la
visée et la sphère sont réelles, et le prix reste sous la demi-minute au lieu d'ajouter une ascension
CMA-ES.

### 6.4 Et un test relancé inchangé

`LunarFlybyFlightTest`, **au chiffre près**. Il traverse la sphère pour de vrai, trois jours après le
décollage, et prouve qu'une traversée **non déclarée** coupe toujours en jambes et produit toujours
`[EARTH, MOON, EARTH]`. C'est la moitié « vol réel » de la clôture, et elle est gratuite.

---

## 7. Deux corrections au découpage

**1. La bande de clôture de `L1`.** Le §4 écrit « 3,08–3,16 j » ; c'est la plage de `MIS-4 / L0`,
mesurée depuis un parking à 185 km / 30°. `MIS-5` vole 400 km / 28,562°, et `MIS-5 / L0` §4 mesure
**3,071 à 3,148 j** — en disant explicitement que 3,071 sort du plancher de 3,08, et que la plage est
celle d'**une** configuration de parking et non une propriété du transfert. Le test asserte 3,0–3,2 j
et journalise la valeur contre celle de `L0` (§6.3).

**2. La portée du §2.3 pt 2.** Il écrit que « `StageLegRunner.fly` convertit l'état d'entrée de chaque
étage », ce qui est vrai de la passe éphéméride et faux de la passe optimiseur (§1.2 pt 4). La
conséquence — « les trois derniers étages déclarent le contexte lunaire, ce qui suffit » — reste vraie
pour l'éphéméride et devra être complétée en `L4`/`L5` pour la marche d'étages.

---

## 8. Ce que `L1` ne fait pas

1. **La passe optimiseur ne convertit toujours pas de repère.** `createOptimizationPropagator` ne fixe
   aucun repère : il vient de l'état initial. Un étage lunaire y recevrait un état GCRF et intégrerait
   en GCRF avec un µ lunaire au centre, **silencieusement**. `L1` rend l'état côté terrestre, comme le
   leg runner ; c'est `L4`/`L5` qui devront convertir.
2. **`CoastingStage` n'est pas réparé.** Le refus du découpage §6 pt 6 tient : un accesseur protégé
   apparaît, aucun coast du dépôt ne change de comportement.
3. **La valeur de la borne est à `L5`.** `L1` livre le refus qui rend son oubli impossible, pas le
   nombre.
4. **Le coût que `L1` introduit à `L5`** : le coast translunaire est désormais volé sur les **deux**
   passes, donc une fois par évaluation de λ. À ~3 s les 3,1 j, un balayage de dix λ paie ~30 s de
   plus qu'aujourd'hui. Chiffré depuis `MIS-4 / L0`, **non mesuré**.
5. **Rien sur l'arc lunaire.** Aucun étage de ce lot ne vole après la frontière ; la conversion, le
   contexte lunaire et l'échantillonnage de l'approche sont à `L4`.

---

## 9. Risques

**Le seul risque réel est celui que le découpage §5 nomme en troisième** : le crochet traverse le socle
partagé, et `endDateIsStageCutoff` alimente `shortfallSeconds()`, qui décide de `isComplete()`, qui
décide de la faisabilité dans `MissionLoadEvaluator`. Une erreur là se manifesterait comme « aucune
charge d'ergols faisable » sur une mission qui n'a rien à voir avec la Lune.

Trois choses le bornent, dans cet ordre :

- le prédicat est **faux par défaut**, donc la non-régression de trajectoire est structurelle ;
- le drapeau ne tombe que sur la branche terminante (§3.3), donc aucun autre arrêt anticipé ne devient
  invisible ;
- le troisième cas de `SoiTerminatingStageTest` (§6.1) asserte précisément cette distinction.

**Ce qui n'est pas un risque** : la détection, l'hystérésis, la conversion de repère et la lecture de
l'arrêt. Les quatre sont livrées, volées et épinglées par `PHY-4 / L4` et `L6`.

---

## 10. Ce que `L1` lègue

- **À `L4` et `L5`** : un coast qui s'arrête à la sphère sur les deux passes, une borne dont l'oubli
  lève, et le trou de conversion de la marche d'étages, écrit avant d'être rencontré.
- **À `MIS-11`** : une bascule de sphère qui **termine** un étage, dont le retour aura besoin dans
  l'autre sens.
- **Au socle** : la règle de direction du détecteur à un seul endroit (§5.4), et une famille de
  déclarations d'étage passée de trois à quatre membres — toutes de la même forme, ce qui reste ce qui
  la rend lisible.

---

## 11. Fermeture — `L1` est implémenté

**Verdict : la propriété du §1 est vraie, et les trois tests neufs la tiennent.** Neuf cas, tous
verts : six dans `SoiTerminatingStageTest`, deux dans `TranslunarCoastStageTest`, un vol dans
`TranslunarBoundaryFlightTest`. Le socle a bougé de six fichiers, dont cinq d'une poignée de lignes.

### 11.1 Ce que la mesure confirme

| Prédiction | Mesuré |
|---|---|
| Les deux passes s'arrêtent au même endroit (§5.2, §6.2) | **0,000e+00 s et 0,000e+00 m** sur un étage seul |
| … et sur une chaîne de trois étages (§6.3) | **8,963e-08 s** — 90 ns, quatre ordres sous la tolérance |
| Le coast s'arrête à la sphère et non au filet ni à sa borne | **3,0865 j** d'un plafond de 5 j |
| L'échantillon sortant sauté évite un point en double (§3.2) | aucun instant écrit deux fois dans l'éphéméride |
| Coût du vol | **29,2 s** contre ~15 s estimés — **bas d'un facteur deux** |

Le point double du §3.2 méritait d'être vérifié plutôt que raisonné : la conclusion supposait que le
normaliseur de pas d'Orekit n'émet pas l'état final, ce qui est vrai mais n'avait pas été mesuré. Il
l'est maintenant, et par une assertion permanente.

### 11.2 Deux écarts au plan

1. **La géométrie de parking du vol** (§6.3) : `TranslunarInjectionPlan.parkingState` à 185 km / 30°
   au lieu des 400 km / 28,562° annoncés, parce que reproduire ces derniers demandait de recopier
   `transferPlaneNormal`, package-private, dans un test. La conception refusait la duplication
   permanente ; l'implémentation a tenu ce refus et payé le prix en changeant de configuration.
2. **Le coût du vol** est le double de l'estimation. L'extrapolation depuis les 9,2–11,8 s pour dix
   jours de `MIS-4 / L0` ne tenait pas : cette chaîne planifie deux fois le TLI et propage deux fois
   3,1 j, et la propagation translunaire coûte plus par jour que la moyenne d'un vol de dix jours.

### 11.3 Un rouge préexistant, trouvé en passant

La suite rapide a **cinq échecs au commit `8c56bbf`**, avant toute ligne de `L1` — établis par
remisage, relance, restauration, relance. Ils sont de deux natures très différentes.

**Un défaut franc, et il est daté.**
`ParkingCoastStageTest > propagateStandalone_advancesToTheInjectionPoint` échoue partout : en
isolation, en suite, avec le lot et sans lui. Il compare la durée volée à
`departure.coastDuration()` à **1 s près**, alors que `MIS-4 / L6` (décision α) a fait s'arrêter ce
coast à **l'allumage**, une demi-combustion avant le point d'injection. Son propre journal donne les
deux nombres : « *coasting 690 s to ignition, 19,6 s ahead* » — l'assertion porte sur 709,6 s et le
vol en fait 690. **Le test asserte la sémantique d'avant `L6`**, et l'écart est exactement
`ignitionLead`. C'est un test à réparer, pas un code : il suffit de retrancher le délai d'allumage,
ce qui est aussi ce qu'il devrait alors pincer.

**Deux épinglages sensibles à l'ordre.** `CentralBodyBaselineTest` (ses quatre profils) et
`MissionPolylineBaselineTest.leo400` sont des comparaisons **bit à bit à tolérance zéro**, et leur
verdict change avec le jeu de classes exécuté — dans les deux sens :

| Jeu exécuté | `CentralBodyBaselineTest` | `MissionPolylineBaselineTest` |
|---|---|---|
| suite complète, arbre propre | **rouge** ×4 | vert |
| suite complète, avec `L1` | **rouge** ×4 | vert |
| classe seule, avec `L1` | vert | — |
| sous-ensemble de 18 tests, arbre propre | — | **rouge** |
| le même sous-ensemble, avec `L1` | — | **vert** |

Le javadoc de `CentralBodyBaselineTest` nomme lui-même la cause sans la traiter comme un risque :
l'égalité stricte tient « *les mêmes instances de repère en cache et le même modèle de gravité 8×8
partagé* ». Ces caches sont des singletons de JVM, donc partagés entre classes de test, donc
dépendants de qui a tourné avant. Un épinglage à tolérance zéro sur un état global partagé n'est pas
un épinglage : il est vert ou rouge selon le filtre `--tests`.

**`L1` n'introduit aucun de ces échecs, et cela est mesuré et non affirmé** : 1 140 tests / 5 échecs
sur l'arbre propre, 1 149 / les 5 **mêmes** avec le lot ; et sur un jeu de classes identique des deux
côtés, `L1` en fait passer un de plus qu'il n'en fait échouer. Ce n'est pas le travail de ce lot de
les réparer, et ils sont écrits ici pour ne pas lui être attribués.
