# MIS-4 / L6 — La poussée finie

Lot **L6** du découpage ([`01-decoupage.md`](01-decoupage.md) §4). Il rend vraie une seule
propriété : **l'injection translunaire brûle vraiment.**

L'impulsion que [`L4`](06-conception-L4.md) a laissée est remplacée par une combustion à poussée
constante, centrée sur le point d'injection, calibrée pour délivrer la même énergie que
l'impulsion qu'elle remplace. Rien du plan ne bouge : `TranslunarInjectionPlan.solve()` reste
impulsionnel, aux mêmes 4,5 s et aux mêmes chiffres épinglés. Ce que le lot ajoute est une couche
d'**exécution**, et la mesure de ce qu'elle coûte.

Le lot se déroule en **deux étapes** : une suppression, puis la substitution. L'ordre n'est pas de
confort — la suppression est ce qui rend la substitution possible en un seul modèle, §3 le montre.

**Cinq énoncés écrits ailleurs sont corrigés ici**, rassemblés au §8. Le cinquième a été mesuré
à l'implémentation et non à la conception ; il est signalé comme tel.

**Le §9 est une révision de ce document lui-même**, écrite après l'implémentation. Quatre énoncés
de la conception ont été démentis par la mesure : le résidu de visée que le §7.1 pariait
négligeable, l'accord des deux étages sur le point d'injection promis au §5.2, la neutralité du
bracket hérité de L1, et l'idée qu'une fenêtre pouvait dater un lancement sans savoir quel
véhicule vole.

---

## 1. Ce que le code dit avant qu'on y touche

Cinq relevés, tous faits avant qu'une ligne bouge. Trois contredisent le découpage.

### 1.1 — Le patron annoncé n'est pas celui du découpage

Le découpage §4 écrit : « combustion centrée sur le point d'injection — le patron de
`AnalyticGtoInjectionStage`, qui centre déjà la sienne sur l'apogée ». Les deux moitiés de la
phrase sont fausses.

`AnalyticGtoInjectionStage` **ne centre pas** et **ne brûle pas à l'apogée** : elle allume à
`state.getDate().shiftedBy(plan.leadCoast() + 1.0e-3)` et brûle au périgée, la combustion
commençant au point visé au lieu de l'encadrer. Le Newton qu'elle porte agit sur le rayon d'apogée
visé, pas sur un centrage.

L'étage qui centre est `AnalyticApogeeCircularizationStage` :

```java
AbsoluteDate burnStart = stateAtApogee.getDate().shiftedBy(-dt / 2.0);
if (burnStart.isBefore(state.getDate())) {
  burnStart = state.getDate().shiftedBy(1.0e-3);
}
```

C'est lui le patron de L6, et il apporte davantage que le centrage : une **sécante sur β**, échelle
de vitesse, sur six itérations, avec un retour vectoriel sur le plan. La conception ci-dessous en
reprend la sécante scalaire et laisse le retour vectoriel en remède nommé (§4.4).

La conséquence pratique est que la garde `isBefore` du patron **ne peut pas être transposée** :
`AnalyticApogeeCircularizationStage` se retrouve avec un allumage antérieur à son propre état
d'entrée parce qu'elle découvre son apogée en cours de route. L6 n'a pas ce problème, et la raison
est la décision **α** (§2) : c'est le coast qui s'arrête à l'heure de l'allumage.

### 1.2 — La démo `PHY-4` ne peut physiquement pas voler une combustion finie

`LunarTransferMission` — la démo derrière `mission.lunarDemo` — vole
`PropulsionSystem.getSpacecraftPropulsion()`, soit `PropulsionSystem.java:51` :

```java
return new PropulsionSystem(300, 3000);
```

300 s d'Isp, 3 kN de poussée, sur un empilement de 500 kg à sec et 1 200 kg d'ergols. La
combustion d'injection y durerait **1 101 s**, soit **74,9° d'arc** sur une révolution de 5 292 s,
pour une perte `1 − sinc(θ/2)` d'environ **221 m/s** contre une marge d'ergols d'environ 419 m/s.

Ce n'est pas un cas dur, c'est un cas **hors modèle** : à 75° d'arc, la combustion n'est plus une
perturbation de l'impulsion et la manette scalaire de §4 n'a plus de sens. Porter L6 sur cette
démo aurait exigé un second modèle d'exécution — donc la coexistence de deux modèles, dans un lot
dont la règle est « un changement de comportement à la fois ».

### 1.3 — Aucun vol lunaire du dépôt ne vole Ariane

Le découpage justifie le lot par un chiffre :

| Étage supérieur | Combustion | Arc | Perte contre l'impulsionnel |
|---|---|---|---|
| Falcon Heavy S2 (348 s, 981 kN) | ~47 s | 3,2° | ~0,4 m/s |
| Ariane 62 ULPM (457 s, 180 kN) | ~275 s | 19° | **~14 m/s** |

« Quatorze m/s, c'est tout le bénéfice mensuel de la fenêtre de lancement. C'est ce qui fait de ce
lot une livraison et non un raffinement. »

Or `LunarFlybyFlightTest:325`, `:336`, `:347` et `LunarPrecisePathProbeTest:76` construisent tous
`Launchers.FALCON_HEAVY`. **Le profil à 19° qui justifie L6 n'est volé par aucun test du dépôt.**
Le §6.3 comble le trou.

### 1.4 — Deux précisions sur la table ci-dessus

Elles ne l'invalident pas, mais elles disent ce que le log devra confirmer.

**La durée est proportionnelle à la masse à l'injection.** `dt = m₀c/F · (1 − e^(−Δv/c))` : à
Δv = 3 082 m/s et c = 3 413 m/s (Isp 348 s), les 47 s de la table correspondent à
**m₀ ≈ 22,7 t**. Une injection plus lourde allonge la combustion dans le même rapport, et l'arc
avec elle. Ce que la mission emmène réellement au point d'injection, seul le vol le dit — c'est la
première chose que le log de §6.1 produira.

**L'arc est calculé à 185 km.** Les 3,2° et 19° supposent la révolution de 5 292 s de
`TranslunarInjectionPlan.PARKING_ALTITUDE`. `LunarFlybyMission.DEFAULT_PARKING_ALTITUDE` vaut
400 km, soit 5 554 s : à durée égale, l'arc est ~5 % plus petit que la table ne l'annonce.

### 1.5 — `parkingState` est une référence de non-régression, pas une fixture

La suppression de la démo (§3) ôte à `TranslunarInjectionPlan.parkingState` son dernier usage de
production. Elle **reste** néanmoins, et le motif n'est pas la commodité :

- `TranslunarDepartureFlightTest:154` s'en sert comme **référence de non-régression de `L1`** — le
  départ depuis un plan imposé y est comparé au plan fabriqué ;
- `TranslunarInjectionPlanTest:196-213` éprouve la garde de déclinaison à travers elle.

La supprimer casserait le test de clôture de `L1`. Ce qui change est son javadoc : plus rien ne la
vole, elle est une fixture et une référence.

---

## 2. Les six décisions

Prises une par une, dans cet ordre, avant qu'aucune ligne ne soit écrite.

| # | Question | Décision |
|---|---|---|
| **B** | Comment rattraper la sous-délivrance ? | **Deux manettes emboîtées**, l'intérieure ne propageant que la combustion |
| **a** | Sur quoi agit la manette intérieure ? | **Une échelle scalaire du Δv commandé**, cible = l'énergie spécifique de l'état impulsionnel |
| **α** | Qui décide de l'heure d'allumage ? | **Le coast raccourcit, l'étage regarde devant** |
| **i** | Le plan devient-il fini ? | **L'exécution seule** — le ΔV visé reste impulsionnel (révisé au §9 : la bissection, elle, vole le fini) |
| **C** | Que devient la démo `PHY-4` ? | **Elle meurt** |
| **A** | Dans quel ordre ? | **Deux étapes dans L6, la suppression d'abord** |

**Pourquoi B.** L'offset de visée a de l'autorité sur la distance ratée, pas sur l'énergie : un
déficit de 14 m/s déplace le demi-grand axe de 328 000 à 262 000 km, ce qu'aucun réglage de visée
ne rattrape. Il faut deux manettes. L'intérieure ne propage que la combustion — quelques dizaines
de secondes — et non les quatre jours du transfert, ce qui la rend assez bon marché pour vivre
dans `inject()` plutôt que dans l'étage.

**Pourquoi a.** La poussée est inertiellement fixe, donc le Δv délivré est exactement parallèle au
Δv commandé : il ne manque qu'une amplitude, pas une direction. Le centrage annule l'erreur de
direction au premier ordre et laisse θ²/24 — 6,5·10⁻⁴ sur FH, 2,6·10⁻² sur ULPM. Une manette
scalaire suffit tant que ce résidu reste dans le bracket ; le remède si ULPM en sort est nommé
au §4.4.

**Pourquoi α.** Centrer exige de connaître dt̂ *avant* d'allumer. Le faire porter par le coast, qui
s'arrête à `t_inj − dt̂/2`, évite d'introduire un allumage antérieur à l'entrée de l'étage — le cas
que `AnalyticApogeeCircularizationStage` doit garder. Le prix assumé : `ParkingCoastStage` apprend
à lire la propulsion de l'étage actif, et son `configuredEndDate` veut désormais dire « allumage »
et non « point d'injection ».

**Pourquoi i.** `solve()` intact, ce sont les mêmes 4,5 s, les mêmes tests épinglés et la même
bande. La couche finie est une substitution à l'exécution seule, et le résidu qu'elle laisse est
exactement ce que le vol de clôture mesure. Le remède si le résidu est trop grand — une passe de
re-visée volant la combustion, +15 % de coût — est nommé et non pris.

> **Révisé.** Le vol de clôture a mesuré ce résidu à 3 451 km. Le remède a été pris : la bissection
> de `solve()` évalue désormais chaque offset en volant la combustion calibrée. Le ΔV que le plan
> porte reste un équivalent impulsionnel et les cas épinglés de `L1` volent toujours l'impulsion —
> ce que la décision **i** protégeait — mais l'offset, lui, est convergé contre la combustion. §9.

**Pourquoi C.** §1.2. La démo ne peut pas voler ce lot.

**Pourquoi A.** La suppression est la prémisse de la conception, pas sa conséquence : c'est elle
qui autorise un modèle d'exécution unique.

---

## 3. Étape 1 — la suppression

### 3.1 Production

| Site | Ce qui part |
|---|---|
| `operation/LunarTransferMission.java` | la classe entière (133 l.) |
| `window/problem/TranslunarInjectionPlanWindowProblem.java` | la classe entière — 2ᵉ des 3 implémentations de `LaunchWindowProblem`, construite dans la seule branche démo |
| `OrbitLabApplication.java` | `loadLunarDemoIfRequested` (~44 l., `:250-283`), les constantes `LUNAR_DEMO_DAYS` / `LUNAR_DEMO_BUDGET` / `LUNAR_DEMO_MARGIN`, deux imports |
| `resources/application.properties` | `mission.lunarDemo` (`:14`, `:18`) |
| `TranslunarInjectionPlan.keplerianInjectionDeltaV(AbsoluteDate, double)` (`:550`) | la surcharge fermée — son unique appelant est `TranslunarInjectionPlanWindowProblem:97` |
| `stage/TranslunarInjectionStage.java` | la classe entière, remplacée par `TLIBurnStage` (§5.2) |

**La surcharge `(SpacecraftState, AbsoluteDate)` (`:570`) survit** : c'est le terme Lambert de la
fenêtre lunaire, et la décision **α** en dépend directement (§5.1).

Deux javadoc mentionnent la démo et doivent être repris : `TranslunarInjectionPlan:49` et
`LunarLaunchWindowProblem`.

### 3.2 Tests

| Classe | Sort |
|---|---|
| `LunarTransferFlightTest` | supprimée — vol épinglé de la démo |
| `TranslunarInjectionPlanWindowProblemTest` | supprimée — teste une classe supprimée |
| `LunarBaselineProbeTest` | supprimée |
| `TranslunarDepartureFlightTest:60` | repointée sur `LunarFlybyMission` |
| `LunarFlybyMissionTest:122` | repointée sur `LunarFlybyMission` |

**Rien de la couverture n'est perdu au passage multi-arcs** : `LunarFlybyFlightTest` vole
`[EARTH, MOON, EARTH]`, ce que la démo n'a jamais fait.

### 3.3 Ce que l'étape 1 laisse

Un seul étage d'injection, une seule fonction d'injection, deux appelants — l'étage et
`LunarLaunchWindowProblem.confirm()`, exactement les deux que `L4` §7 tenait ensemble. Aucune
coexistence de modèles à aucun moment du lot.

---

## 4. Étape 2 — le plan de combustion et le contrat d'`inject()`

### 4.1 Le nouveau retour

`inject()` garde son nom, sa signature et son rôle de seam unique. Elle change de retour :
`Injected(plan, state)` disparaît — elle ne rend plus ce qu'on obtient mais **ce qu'il faut
allumer**.

```java
public record Burn(TranslunarInjectionPlan plan, Vector3D direction,
                   double duration, double commandedDeltaV, double endMass) {}
```

`direction` est `plan.deltaV().normalize()`, inertiellement fixe : c'est l'hypothèse qui rend la
décision **a** valide.

### 4.2 Ce que fait `inject()`

1. **`solve()`** — inchangé, impulsionnel (décision **i**).
2. **`plan.applyTo(parking, c)`** — qui cesse d'être un état de vol pour devenir la **référence**.
   Son seul rôle après L6 : fournir l'énergie spécifique cible `ε* = v²/2 − µ/r`. C'est la seule
   promotion de sens du lot ; tout le reste ne fait que perdre du travail.
3. **La sécante intérieure sur β**, échelle du Δv commandé. On brûle
   `dt(β) = m₀c/F · (1 − e^(−βΔv/c))` depuis le point d'allumage, on propage **la combustion
   seule**, on lit `ε` en fin de poussée. Six itérations, on garde la meilleure. Tolérance :
   l'équivalent de 0,01 m/s, soit `δε = v·δv ≈ 108 J/kg` à 10,8 km/s.
4. **Le plancher d'extinction**, désormais prononcé sur `commandedDeltaV`.

### 4.3 Deux changements de comportement assumés

**Le plancher se durcit.** Le fini consomme davantage pour la même énergie ; un transfert qui
passait de justesse à l'impulsion peut être refusé ici. C'est précisément la sous-délivrance
annoncée par le javadoc de la classe, payée en ergols plutôt qu'en distance ratée. Le refus reste
lisible : le message cite le Δv commandé, donc le surcoût est visible dans l'exception.

**Le rapport de performance dit enfin vrai.** `isPropulsive()` restant vrai et la masse variant
réellement, le Δv dérivé de l'écart de masse cesse d'être celui d'une impulsion fictive.

### 4.4 Coût, pas d'intégration, et le remède nommé

La sécante propage sur le propagateur de la classe, avec le pas plafonné par l'invariant
d'allumage tardif — `burnLimitedMaxStep`, jamais `COAST_MAX_STEP`. Coût estimé depuis la table du
§1.3 : quelques dizaines de pas d'intégrateur au total, contre les trente propagations de quatre
jours de `solve()`. C'est ce qui permet à `confirm()` de porter le modèle fini sans renchérir la
fenêtre — donc de dater les lancements par la trajectoire que la mission vole, ce que le javadoc
d'`inject()` réclame nommément depuis `L4`.

**Remède nommé, non pris :** si le profil ULPM sort du bracket de la sécante scalaire, on passe au
retour vectoriel complet d'`AnalyticApogeeCircularizationStage`. On **n'élargit pas** les bornes.

### 4.5 La seconde entrée publique

`ignitionLead(parking, departure, active)` : forme fermée sur
`keplerianInjectionDeltaV(SpacecraftState, AbsoluteDate)` puis Tsiolkovsky, **aucune
propagation**. Elle évalue son Δv sur l'état de parking avancé képlériennement jusqu'au point
d'injection — `departureFrom` construit déjà ce `KeplerianOrbit` pour la même raison.

Le décentrage résiduel qu'elle laisse est l'écart entre Δv fermé et Δv résolu, de l'ordre de
0,05 % de dt : **0,02 s sur FH, 0,14 s sur ULPM**.

---

## 5. Les deux étages

### 5.1 `ParkingCoastStage` — il s'arrête plus tôt

```java
Departure departure = TranslunarInjectionPlan.departureFrom(previousState);
ActiveStageInfo active = mission.getVehicle().resolveActiveStage(previousState.getMass());
this.ignitionDate = departure.injectionDate()
    .shiftedBy(-TranslunarInjectionPlan.ignitionLead(previousState, departure, active));
```

Le champ `injectionDate` devient `ignitionDate`, et le javadoc doit dire que `configuredEndDate`
signifie désormais « allumage ». Le log annonce les deux dates. `configure` et
`propagateStandalone` s'arrêtent là.

### 5.2 `TLIBurnStage` — il remplace `TranslunarInjectionStage`

**`enter`.** L'étage entre à l'allumage et doit retrouver le point d'injection devant lui. Il
rappelle `departureFrom` sur son **propre** état d'entrée — jamais en lisant un champ du coast :
les deux étages s'accordent parce qu'ils appellent la même forme fermée, pas parce qu'ils se
passent une valeur.

*Mesure qui autorise ce choix :* depuis un état en avance de dt̂/2, le `travel` du premier passage
vaut +1,6° sur FH et +9,3° sur ULPM, tous deux très au-dessus de
`DEPARTURE_TOLERANCE_RADIANS = 1e-9`. Le signe étant positif, l'enroulement en `[0, 2π)` du
premier passage ne s'applique pas : c'est bien le même point d'injection qui est retrouvé, pas
celui de la révolution suivante.

Puis : propagation balistique jusqu'à `departure.injectionDate()` (8×8, comme le
`propagateStandalone` du coast et pour la même raison), `inject()` **à ce point-là** — qui reçoit
donc exactement l'état géométrique que l'impulsionnel recevait, ce qui est ce qui garde `solve()`
intact —, on retient le `Burn`, et **on rend l'état d'entrée inchangé**. Aucune masse ne bouge
dans `enter` : la combustion est volée par `configure`.

**`configure`.** `FrameAlignedProvider` sur `burn.direction()`, `ConstantThrustManeuver` à
`entrée + 1e-3` pour `burn.duration()`, puis le `SETTLING_COAST_SECONDS = 60` existant.
`configuredEndDate = entrée + durée + 60 s`. L'argument de troncature du javadoc actuel survit tel
quel : c'est toujours le seul endroit de cette mission où le contrôle de
`MissionEphemerisGenerator` a prise.

**`propagateStandalone` — surchargé, et c'est le coût structurel du lot.** L'ancien étage héritait
du défaut parce qu'une impulsion est de l'arithmétique d'état ; une poussée finie n'en est pas,
donc les passes optimiseur et éphéméride doivent voler la même combustion sur leur propre
propagateur. Gabarit repris ligne pour ligne d'`AnalyticGtoInjectionStage:139`.

**`maxStepSeconds`** — `burnLimitedMaxStep(entryState, mission.getVehicle())`, et conformément à
l'avertissement de `MissionStage:215-218`, **la même valeur est passée aux trois propagateurs** :
celui de `propagateStandalone`, celui de la sécante intérieure, et le pas déclaré à la chaîne.

**Inchangé.** Aucune transition de sphère d'influence n'est déclarée — l'étage étant désormais
réellement propulsif, ce n'est plus seulement correct mais obligatoire, `StageLegRunner` la
refuserait. `sampleStepSeconds` reste à 1 s : la combustion prend pour la première fois de la
substance dans la trace, là où l'impulsion ne laissait que son coast de 60 s.

---

## 6. Ce qui est mesuré

### 6.1 Ce que l'étage journalise

Une ligne à `enter`, qui est le relevé de la poussée finie et n'existait pas :

```
[TLI burn] dt=47 s (3.2° d'arc), Δv commandé 3 094 m/s pour 3 082 m/s impulsionnels (+12),
           masse 44 700 → 21 900 kg, périlune visée 100 km
```

L'écart `commandé − impulsionnel` **est** la perte de poussée finie, mesurée et non estimée. C'est
la première fois que le dépôt le produit, et il tranche deux points d'un coup : la masse réelle à
l'injection (donc la durée réelle de la combustion, §1.4) et la valeur de la perte elle-même, que
le découpage estimait à ~0,4 m/s sur FH et ~14 m/s sur ULPM. **Mesurée le 2026-08-27 par le test du
§6.3, à 22,7 t : +2,18 m/s sur FH et +23,5 m/s sur ULPM** — correction 5 du §8.

Une seconde ligne à la convergence de la sécante : résidu d'énergie en équivalent m/s, et nombre
d'itérations. Une sécante qui sort sans converger doit se voir dans le log avant de se voir dans
le périlune.

### 6.2 Ce que mesure la clôture

Le même vol que `L4` — `LunarFlybyFlightTest` sur Falcon Heavy, **assertion inchangée** : périlune
dans les 10 km de la cible, contre la valeur volée le 2026-08-27, **101,0 km pour 100 km visés**.

La livraison du lot est que ce nombre bouge peu ou pas, et que l'écart au plan impulsionnel soit
désormais lisible dans le log au lieu d'être absorbé en silence.

Ce que le lot croyait suffisant pour l'obtenir — le décentrage résiduel de 0,02 s et la calibration
en énergie — ne l'était pas : §9.

**Rien n'est épinglé de plus.** `solve()` étant intact, les tests épinglés de `L1` et `L4` doivent
rendre les mêmes chiffres au chiffre près. S'ils bougent, c'est un défaut, pas une nouvelle
référence.

### 6.3 Le profil à 19°

Le trou du §1.3 est comblé par un **test au niveau de la combustion seule** : même état de
parking, un étage actif de caractéristiques ULPM (457 s, 180 kN), on appelle `inject()`, **on re-vole
la combustion indépendamment de la calibration** et on vérifie que l'énergie atteinte égale `ε*` à
0,05 m/s près, et que la surcharge tombe dans la bande où elle a été mesurée.

Vérifier en re-volant plutôt qu'en lisant le résidu que la sécante journalise est délibéré :
l'assertion doit pouvoir échouer quand la sécante sort de son bracket, et c'est cet échec-là qui
déclenche le remède vectoriel du §4.4.

**Mesuré le 2026-08-27**, à 22,7 t à l'injection, sur les deux profils :

| Profil | Combustion | Arc | Surcharge mesurée | Estimée au découpage |
|---|---|---|---|---|
| Falcon Heavy S2 | 47,9 s | 3,3° | **+2,18 m/s** | ~0,4 |
| Ariane 62 ULPM | 288,5 s | 19,6° | **+23,5 m/s** | ~14 |

Trois itérations de sécante sur chacun, résidu d'énergie sous 0,001 m/s. Les bandes du test sont
posées sur ces mesures et non sur l'estimation qu'elles démentent.

Quelques secondes, et il vise exactement ce que L6 livre. Les deux alternatives sont écartées :

- **un vol lunaire Ariane 62 complet** couvrirait tout, mais c'est un second vol de quatre jours,
  et rien ne dit que `loadsForLunar` boucle sur un ULPM de 31 t — rapport de masse ~1,99 pour
  3 082 m/s à 457 s. On risquerait de découvrir un refus de plancher d'extinction et de le
  confondre avec un défaut de L6, ce qui est une question de dimensionnement et non de poussée
  finie ;
- **rien**, qui laisserait la clôture n'exercer que les 3,2° où la perte est de 0,4 m/s,
  c'est-à-dire le seul cas où le lot ne sert presque à rien.

C'est aussi ce test qui déclenchera le remède vectoriel du §4.4 si le bracket lâche.

---

## 7. Ce que ce lot ne fait pas

1. **Il ne rend pas le plan fini.** Le ΔV que `solve()` cherche reste l'increment impulsionnel
   d'un seed de Lambert ; ce qui a changé au §9, c'est la manière dont un candidat est *évalué*, pas
   la nature de ce qui est cherché. Aucune combustion n'entre dans le seed.
2. **Il ne touche pas au dimensionnement lunaire d'Ariane 62.** §6.3.
3. **Il ne répare pas `CoastingStage`.** Le refus de `L4` §1.1 tient : tous les coasts du dépôt
   s'effondrent de la même manière en passe optimiseur, et les déplacer déplacerait les références
   d'ascension réenregistrées par `MIS-7`.
4. **Il ne crée pas les deux contraintes d'étage**, qui tiennent par ailleurs : le coast de parking
   vaut au plus une révolution, sous les 7 200 s du S2 comme sous les 21 600 s de l'ULPM, et les
   `restartCount` de 2 et 4 laissent la place au second allumage.

---

## 8. Corrections apportées à des énoncés écrits ailleurs

1. **`01-decoupage.md` §4, « le patron de `AnalyticGtoInjectionStage`, qui centre déjà la sienne
   sur l'apogée »** — faux deux fois. Cette classe n'entoure pas son point visé et brûle au
   périgée. Le patron réel est `AnalyticApogeeCircularizationStage:187`. §1.1.
2. **`01-decoupage.md` §4, table des pertes, colonne « Combustion »** — les 47 s ne sont pas une
   propriété de l'étage mais du couple étage/masse : elles correspondent à ~22,7 t à l'injection.
   §1.4.
3. **`01-decoupage.md` §4, colonne « Arc »** — 3,2° et 19° sont calculés sur la révolution de
   5 292 s à 185 km, quand `LunarFlybyMission` vole une parking à 400 km, soit 5 554 s : les arcs
   réels sont ~5 % plus petits. §1.4.
4. **Le javadoc de `TranslunarInjectionPlan.inject`** annonce que L6 remplacera l'étage
   impulsionnel « some 14 m/s on Ariane 62 ». L'énoncé reste vrai, mais il décrivait un bénéfice
   qu'aucun test du dépôt n'exerce : les quatre vols lunaires volent Falcon Heavy. §1.3, comblé
   au §6.3.
5. **`01-decoupage.md` §4, colonne « Perte contre l'impulsionnel » — mesurée à l'implémentation,
   pas à la conception.** Les ~0,4 m/s et ~14 m/s sont l'application de `1 − sinc(θ/2)`, qui ne
   compte que le balayage de la direction de poussée. La perte réelle y ajoute ce que la combustion
   paie à s'écarter de la trajectoire impulsionnelle pendant qu'elle pousse : **+2,18 m/s sur FH
   (5× l'estimation) et +23,5 m/s sur ULPM (1,7×)**. `1 − sinc(θ/2)` est donc une borne inférieure,
   et non la perte. Cela ne change aucune décision du lot — l'argument du découpage (« quatorze m/s,
   c'est tout le bénéfice mensuel de la fenêtre ») en sort renforcé, pas affaibli. §6.3.

---

## 9. Révision après mesure — la visée devait voler la combustion

Écrit après l'implémentation, contre une prédiction de ce document.

### 9.1 Ce que le premier vol a donné

La couche finie du §4 a été implémentée telle qu'elle est décrite, et `LunarFlybyFlightTest` a
rendu un **périlune à 3 551,6 km pour 100 km visés** — 345 fois la bande. La calibration, elle,
avait parfaitement fonctionné : résidu d'énergie 0,0001 m/s, surcharge +6,9 m/s sur une combustion
de 130 s à 61,4 t.

### 9.2 Le diagnostic, mesuré et non supposé

En sondant l'état de départ à la masse du vol, en comparant l'état à la coupure de la combustion à
l'état impulsionnel propagé à la même date :

| Masse | dt | Arc | Écart de position | Écart de vitesse | Écart de direction |
|---|---|---|---|---|---|
| 61,4 t | 129,6 s | 8,8° | 31,3 km (dont −30,8 le long de la trace) | 7,25 m/s | 0,11 mrad → 42 km |
| 22,7 t | 47,9 s | 3,3° | 11,6 km | 2,12 m/s | 0,03 mrad → 11 km |

**La direction est excellente.** Le centrage fait exactement ce que le §2 lui promettait, et le
remède vectoriel tenu en réserve au §4.4 visait donc le mauvais défaut — il est retiré.

Ce qui rate est ailleurs : la combustion se termine **31 km plus loin, 8,4 km plus bas**, et
l'énergie y est conservée *parce que* le rayon est plus petit — µ/r augmente de 7,7·10⁴ J/kg,
v·δv vaut 7,8·10⁴, les deux termes s'égalisent au chiffre près. **Calibrer ε cale le demi-grand
axe, pas le transfert.** La bissection avait convergé son `aimOffset` pour un départ en
(r₀, v₀+Δv) à t₀ ; on lui remettait un état à 31 km de là, le moment cinétique bougeait de 0,06 %,
et quatre jours plus tard la Lune amplifiait.

### 9.3 Le remède, pris

La manette extérieure vole désormais ce que la mission vole. `solve()` reçoit une **exécution** —
comment un ΔV candidat devient l'état d'où part le transfert — et `attempt()` l'appelle au lieu
d'appliquer une impulsion :

- l'entrée publique `solve(parking, cible, exhaustVelocity, context)` garde l'exécution
  impulsionnelle : les cas épinglés de `L1` et le rapport plan-contre-vol ne bougent pas ;
- `inject()` passe l'exécution finie — calibrer, puis voler — de sorte que le périlune que la
  bissection converge est celui que cette combustion atteint.

Les deux manettes de la décision **B** voient enfin le même départ, ce qui est ce que la décision
disait et que l'implémentation n'avait pas fait.

**Coût réel** : la sécante intérieure tourne à chaque évaluation de la visée, soit ~28 × 3
propagations de 130 s contre 28 propagations de quatre jours. En dessous des +15 % annoncés.

### 9.4 Ce qui l'atteste

Le test du §6.3 porte une seconde assertion : le périlune que le plan rapporte doit être la cible à
2 km près. Elle est nouvelle et c'est celle qui manquait — la version qui a raté la Lune de 3 451 km
passait l'assertion d'énergie sans broncher. Un vol qui délivre la bonne énergie n'est pas un vol
qui arrive.

---

### 9.5 Deuxième révision — la combustion doit partir de l'allumage réel

La visée corrigée, le vol a rendu **3 551 km**, puis, une fois la visée finie en place, il a
**percuté la Lune**. Les deux fois, la calibration était irréprochable : résidu d'énergie
0,0001 m/s.

Le diagnostic, par élimination puis par datation. L'état de coupure calibré est daté
`23:57:44,870`, la chaîne coupe à `23:57:47,33` : **2,46 s d'écart**, à masse identique
(24 529,093 kg) donc à durée de combustion identique. Même durée et date différente ne laissent
qu'une cause : l'allumage n'est pas au même instant.

D'où il venait : `ParkingCoastStage` résout `departureFrom(étatInsertion)` et s'arrête sur le point
d'injection qu'il en tire ; `TLIBurnStage` rappelle `departureFrom(étatAllumage)`, 64 s plus loin
sur la trajectoire, et obtient un point d'injection **2,46 s plus tôt**. La combustion était donc
calibrée centrée sur une date et volée depuis l'autre — 3,5 m/s à la coupure, 1 150 km au périlune
(sensibilité mesurée : ~333 km par m/s).

Le §5.2 affirmait : « les deux étages s'accordent parce qu'ils appellent la même forme fermée, pas
parce qu'ils se passent une valeur ». **C'est faux**, et la mesure qui l'autorisait ne vérifiait que
le signe du `travel`, jamais l'égalité des dates. `departureFrom` est un point fixe sur l'état
qu'on lui donne.

**Le remède, pris.** `inject()` reçoit l'**état d'allumage** et calibre la combustion qui s'allume
là, sans jamais reconstruire l'instant. Le désaccord des deux `departureFrom` devient alors
inoffensif : quel que soit le décentrage résiduel, la visée converge sur le départ que la mission
vole. C'est la même leçon qu'au §9.3, un cran plus bas.

### 9.6 Troisième révision — la fenêtre doit screener avec le lanceur qui vole

La visée finie a fait apparaître un défaut que l'impulsionnel masquait : **un départ fini n'atteint
pas seulement moins loin, il atteint moins de périlunes.** À l'époque la moins chère de la fenêtre,
le périlune volé en fonction de l'offset a un **minimum de 132 km** — il remonte des deux côtés —
contre 100 km visés. Impulsivement une racine existait (offset 6 940 km) ; finiment il n'y en a
aucune. La cause est structurelle : l'offset ne déplace la cible que le long d'**une** direction,
alors que la distance ratée en a deux.

Une fenêtre qui screene en impulsionnel rend donc des dates que la mission ne peut pas honorer —
exactement le défaut que `L4` §7 annonçait. Mais la faire screener en fini n'a de sens que si elle
screene avec le bon véhicule : les deux fixtures de fenêtre du dépôt portaient encore
`PropulsionSystem.getSpacecraftPropulsion()`, le moteur 3 kN de la démo `PHY-4` que le §1.2 a
mesuré à 75° d'arc. La surcharge finie qu'il produit — **+264 à +309 m/s** — n'est pas un coût de la
mission mais un artefact de fixture, assez gros pour réordonner la fenêtre et, dans un essai, pour
la vider entièrement.

**Les deux fixtures reçoivent donc le lanceur réel** (Falcon Heavy, 61 400 kg à l'injection), et
`confirm()` reprend le verdict fini. Le §7.2 disait que le lot ne toucherait pas au dimensionnement
lunaire ; il ne le fait pas — il corrige *quel véhicule la fenêtre pèse*, ce qui est autre chose.
Mesure après : la fenêtre rend `2026-03-31T22:31:30`, écart screen→confirm **2,8 m/s**, périlune
volé **99,8 km**, et l'intention du test de fenêtre est préservée puisque l'étage réel coupe lui
aussi à quelques kilogrammes de son plancher.

### 9.7 Le bracket de visée n'était pas neutre

Deux hypothèses tacites de `L1` sont tombées avec le départ fini :

- **le plancher au rayon lunaire.** Il disait « en dessous, le point de visée est dans la Lune et la
  lecture du périlune n'a plus de sens », ce qui confond la cible avec la lecture : le point de
  visée est une condition aux limites de Lambert que la trajectoire volée manque déjà de 26 000 km,
  et le périlune est lu sur le vol. Retiré ;
- **la monotonie croissante.** « Viser plus loin du centre passe plus loin » vaut pour les départs
  impulsionnels que `L1` a mesurés et pas ailleurs : volée finiment, une géométrie donne 132 km à
  1 837 km d'offset et 259 km à 230 km — décroissante, la racine est vers l'intérieur.

Le bracket marche donc **vers l'extérieur d'abord, à l'identique de `L1`, puis vers l'intérieur si
rien n'a été encadré**. Tout cas qui convergeait converge par la même suite d'essais ; c'est un
surensemble strict, pas un remplacement.

### 9.8 Ce que le vol de clôture fait désormais

Le biais entre la fenêtre et la chaîne, que `MEASURE 2` chiffre depuis `L4` (β planifié 0,0005°
contre β réel 0,0465°), peut maintenant décider de la faisabilité et pas seulement du coût : la
fenêtre confirme sur l'état d'injection qu'un pas de tir *atteindrait*, la chaîne arrive avec celui
que son ascension lui a réellement livré.

Le vol essaie donc les époques **dans l'ordre du coût** et vole la première que la chaîne sait
planifier. Il n'esquive pas la fenêtre — toutes les dates essayées viennent d'elle — et il journalise
le refus. Ce qu'il cesse d'affirmer : que la meilleure époque de la fenêtre est volable.

### 9.9 Ce que le vol rend

**Profil pleinement chargé** (Falcon Heavy, 61 398 kg à l'allumage) :

```
Epoch 2026-03-31T16:25:25 cannot be planned by the chain: best is 132 km
Launch window: 2026-03-31T22:31:30 at 3131 m/s, β planned = 0,0005°
finite injection: dt=128 s (8,3° d'arc), commanded 3132 m/s pour 3124 impulsionnels (+8,1),
                  masse 61 398 → 24 520 kg
Arcs flown: [EARTH, MOON, EARTH]
MEASURE 1 — flown perilune: 100,2 km (aimed 100,0 km, band ±10,0 km)
```

**Profil dimensionné par le budget** (sonde de 2 t, 17 342 kg à l'allumage) :

```
Budget sizing: loads [1 233 000, 12 518], mass at injection 15 684 kg
Launch window: 2026-03-31T16:25:25 at 3124 m/s
finite injection: dt=37 s (2,4° d'arc), commanded 3221 m/s pour 3218 impulsionnels (+3,1),
                  masse 17 342 → 6 748 kg
Arcs flown: [EARTH, MOON, EARTH]
MEASURE 1 — flown perilune: 99,8 km (aimed 100,0 km, band ±10,0 km)
```

**Les +8,1 et +3,1 m/s sont la livraison du lot** : la perte de poussée finie de deux vols réels,
mesurée et loguée pour la première fois, sur deux arcs (8,3° et 2,4°) et deux masses. Le périlune
passe de 101,0 km (`L4`, impulsionnel) à **100,2 km** — la substitution ne dégrade pas la visée,
elle l'améliore d'un kilomètre.

**Le profil léger vole l'époque que le lourd refuse.** À `16:25` la chaîne dimensionnée planifie
sans peine ce que la chaîne pleine ne peut pas atteindre : l'infaisabilité du §9.6 n'est pas une
propriété de la date, c'est le couple date/état d'arrivée. C'est aussi ce qui montre que le repli
d'époque du §9.8 fait un vrai travail et ne masque pas un défaut systématique.
