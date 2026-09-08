# PHY-8 / L1 — Le mécanisme, inerte — conception

Lot **L1** du découpage ([`01-decoupage.md`](01-decoupage.md) §5), mesuré contre la baseline
[`02-baseline-L0.md`](02-baseline-L0.md). Ce que le lot rend vrai : **le modèle sait faire brûler
deux étages ensemble, et personne ne s'en sert.**

Aucune entrée de `Launchers` ne déclare de propulseurs à la fin du lot. Tout ce qui est écrit ici
est donc atteignable, et n'est atteint par rien de ce que le catalogue vole — c'est ce qui permet
aux quatre épinglages de fermer le lot **sans avoir été modifiés**.

Le document corrige le découpage sur sept points, dont trois qui touchent à ce que `L2`, `L3` et
`L4` pourront invoquer. Ils sont au §8.

---

## 1. Périmètre

**Dans `L1`** :

- les trois indices de pile écrits en dur, dérivés du rôle ;
- la multiplicité sur `StageModel`, la fraction d'étranglement sur `AscentProfile` ;
- le bloc parallèle dans `VehicleStack` : agrégation exacte, plancher propre au bloc, répartition
  des ergols au prorata des débits, largage groupé ou en deux temps ;
- **l'ascension à cinq phases** quand la pile déclare des propulseurs, et les trois d'aujourd'hui
  quand elle n'en déclare pas.

Ce dernier point n'est dans la liste d'aucun lot du découpage. Le §8, point 2, dit pourquoi il est
ici et nulle part ailleurs.

**Hors `L1`** : le catalogue (`L2`, `L4`), l'étranglement réel (`L3`), les hauteurs (`L5`), les
charges utiles (`L6`). Et `PropellantBudget` n'est pas touché : il rend toujours autant de charges
que la pile a d'étages, et aucune pile de production n'en gagne un ici.

---

## 2. Les mesures qui décident

### 2.1 `depletionFloor()` est la charnière, et presque tout en découle

`ActiveStageInfo` dérive tout de deux champs. `depletionFloor()` vaut `dryMass + massAbove`,
`massAfterJettison()` vaut `massAbove`, et `remainingFuel(m)` vaut littéralement
`m − depletionFloor()` — mêmes deux termes, soustraits dans l'autre sens.

**30 appels à `depletionFloor()`, dans 20 fichiers de `src/main`**, un seul site de calcul. Un
plancher de bloc correct fait donc tomber gratuitement :

- la durée de la première combustion, `GravityTurnManeuver.getBurn1Duration()` étant
  `remainingFuel / ṁ` ;
- les gardes `DepletionGuard` et `DepletionStopTrigger`, qui lisent ce plancher aux 30 sites ;
- le dimensionnement de pas d'intégrateur, `MissionStage.maxStepSeconds` construisant sa `BurnSpec`
  dessus.

Aucun des 30 sites n'est à reprendre.

### 2.2 Ce qui casse n'est pas le plancher, c'est la règle de résolution

Le §3.1 du découpage prévient sur `MassDepletionDetector`. Il ne dit rien de `resolveActiveStage`,
qui casse d'abord. Sur le profil LEO-400 aux charges écrites à la main — celui que volent les deux
épinglages à tolérance zéro (`02-baseline-L0.md` §3) — éclaté en `[propulseurs ×2, corps, S2]` :

| | valeur |
|---|---|
| masse au décollage | 770 150 kg |
| `massAbove[0]`, seuil actuel de l'entrée « propulseurs » | 326 150 kg |
| plancher réel du bloc (propulseurs vides) | 170 150 kg |
| masse brûlée au franchissement du seuil | **444 000 kg sur 600 000, soit 74 % de la combustion** |

Aux trois quarts de la combustion partagée, le balayage par seuils de masse cumulée rendrait le
corps seul alors que les propulseurs poussent encore — et avec lui sa poussée, son Isp et sa
section. Le seuil du bloc doit être la masse **après largage des propulseurs**, pas la masse de
référence de la pile au-dessus.

### 2.3 La pile ne voit pas `StageRole`

`StageModel.toVehicle(load)` construit un `LaunchVehicle(dryMass, capacity, load, propulsion,
aerodynamics)` : le composant `capabilities`, qui porte le rôle, est jeté. Ni `VehicleStack` ni
`ActiveStageInfo` ne peuvent donc dire que ce qui est actif est le corps. Les « recherches par
`StageRole` » du §3.2 du découpage supposent un porteur qu'il ne nomme pas — et c'est le même
porteur qui devra dire à la pile quelles entrées brûlent ensemble.

### 2.4 L'agrégation du Falcon est exacte au bit près

Le §4 du découpage écrit que `Isp_eff` vaut 296 s « au chiffre près ». Mesuré :

```
F_eff   = 15 200 000 + 7 600 000 == 22 800 000.0        exact
Isp_eff = ΣF / Σ(F/Isp)          == 296.0               exact
ṁ       = F_eff/(Isp_eff·g₀)     == 7 854,570829694853  identique au bit près
```

Ce n'est pas une coïncidence numérique mais une propriété des chiffres : les poussées sont des
entiers exactement représentables, `F_propulseurs = 2 · F_corps`, et les deux Isp sont égales, donc
`ΣF/Σ(F/Isp)` retombe sur la valeur commune. La division par une même quantité étant invariante par
mise à l'échelle en puissance de deux, la somme des deux débits est exactement le débit agrégé.

Le reste de la chaîne suit, et pour la même raison : masse sèche du bloc `44 000 + 22 000 = 66 000`,
`massAbove` `104 000 + 150 = 104 150`, donc `depletionFloor()` **identique au S1 actuel**, donc
`remainingFuel`, donc `burn1Duration`, donc `jettisonDate`, donc `massAfterJettison`. Toutes les
grandeurs dont l'ascension dépend sont des entiers exacts, et leur somme l'est quel que soit
l'ordre.

**Conséquence** : l'iso-trajectoire du Falcon éclaté à `f = 1` ne s'espère pas, elle se démontre —
et `L1` peut la prouver avant que le catalogue ne bouge (§6.2).

### 2.5 Les deux lanceurs volent à `f = 1` avec des structures opposées

Le §2.3 du découpage écrit que *« l'Ariane s'étage d'elle-même et n'a besoin d'aucun
étranglement »*, et le §5 `L4` ne mentionne aucun `f`. L'Ariane 64 vole donc à `f = 1` — et c'est
elle qui a la plus longue phase corps seul de l'item :

| | Falcon `L2` | Ariane `L4` |
|---|---|---|
| `f` | 1 | 1 |
| débits (propulseurs / corps) | 5 236 / 2 618 kg/s | 4 338 / 317 kg/s |
| ergols du corps à l'extinction des propulseurs | **0 kg** | **110 800 kg** |
| phase corps seul | aucune | **~350 s** |
| largage | groupé | deux largages |

130 s d'extinction des propulseurs plus 350 s de corps seul redonnent les ~8 min de Vulcain que le
§2.3 cite. Même `f`, structures opposées : `f` ne peut pas être le critère qui décide entre un
largage groupé et deux (§3.3).

### 2.6 Deux nouveaux composants de record, zéro churn de test

| Record | Constructions | Comment elles survivent |
|---|---|---|
| `StageModel` | 19, dans 5 fichiers | constructeur secondaire d'arité 6, à côté de celui d'arité 5 qui existe déjà |
| `AscentProfile` | 17, dans 7 fichiers | constructeur secondaire d'arité 3 |
| `VehicleStack` | 29, dont 28 de test | constructeur secondaire à un argument |
| `StageSeparationStage` | 9, dans 4 fichiers | reprises : le paramètre change de type (§3.7) |

Les constructions de record étant positionnelles, renommer les composants de `StageModel` en
`unit*` (§3.4) ne coûte rien non plus.

### 2.7 Deux accesseurs d'`ActiveStageInfo` sont morts

`stageMass()` et `propellantCapacity()` ne sont lus **nulle part** dans `src/main`. Rien n'y
trébuchera donc quand ils décriront un véhicule synthétique dont ces deux grandeurs n'ont pas de
sens évident.

---

## 3. Décisions de conception

### 3.1 Le bloc entre dans `ActiveStageInfo` sans une seule méthode nouvelle

**Décision : le bloc est un `ActiveStageInfo` ordinaire, dont on choisit les deux champs.**

| champ | valeur pour le bloc | ce que ça rend |
|---|---|---|
| `vehicle` | véhicule synthétique : `N·F_p + f·F_c`, `Isp_eff = ΣF/Σ(F/Isp)`, sections sommées, masse sèche des **propulseurs seuls** | poussée, Isp et section du bloc, sans écriture à la main |
| `massAbove` | masse **après largage des propulseurs** : sec corps + ergols corps restants + pile au-dessus | `massAfterJettison()` largue exactement les propulseurs |
| | | `depletionFloor()` = masse à l'extinction des propulseurs |
| `dryMassAbove` | sec corps + sec au-dessus | `remainingDryMass()` inchangé |

Le plancher du §3.1 du découpage — *« le bloc doit s'éteindre quand les propulseurs sont vides,
alors que le corps a encore des ergols au-dessus de ce plancher »* — tombe alors de la formule
existante, sans exception à écrire nulle part.

**Ce que ce choix de découpe préserve, nommément.** `MissionOptimizer` calcule la masse sèche
larguée par une phase comme `remainingDryMass(entrée) − remainingDryMass(sortie)` : au largage des
propulseurs, `(sec_p + sec_c + sec_au-dessus) − (sec_c + sec_au-dessus)` = la masse sèche des
propulseurs, juste par construction. Et `getBurn1Duration()`, qui divise `remainingFuel` par le
débit, rend la durée jusqu'à l'extinction des **propulseurs** parce que `remainingFuel` est
`m − depletionFloor()` et que le plancher est celui du bloc.

**Cas groupé.** Quand propulseurs et corps s'éteignent ensemble — le Falcon de `L2` —, le largage
doit retirer **les deux entrées**, sinon les 22 t de masse sèche du corps restent à bord et `L2`
perd son iso-trajectoire. Même paramétrage, autre découpe : `dryMass` = sec propulseurs + sec corps,
`massAbove` = pile au-dessus. Le plancher est identique dans les deux découpes ; seul
`massAfterJettison()` diffère, ce qui **est** la différence entre les deux cas.

**La règle de résolution** devient : *si le plan d'étagement déclare un bloc et que la masse
courante dépasse strictement le seuil du bloc, rendre le bloc ; sinon, balayage ordinaire à partir
de l'entrée qui suit le bloc.* Le largage laisse la masse exactement au seuil, donc le `>` strict
fait tomber sur l'entrée suivante sans ambiguïté — le mécanisme d'aujourd'hui, au même endroit.

L'invariant que `VehicleStack` documente — *« the active stage changes only by an explicit
jettison »* — est donc préservé, mais **sa démonstration change**. Elle ne repose plus sur
« le plancher de déplétion est strictement au-dessus du seuil » (§2.2 montre que c'est faux pour un
bloc) mais sur « le seuil du bloc **est** la masse d'après largage ». Le Javadoc de
`resolveActiveStage` doit être réécrit en conséquence, pas seulement complété.

### 3.2 Le plan d'étagement vit sur la pile, calculé une fois

**Décision : `LauncherModel.instantiate` calcule un `StagingPlan` que `VehicleStack` porte en
second composant.**

Trois choses doivent arriver jusqu'au vol : quelles entrées brûlent ensemble, avec quelle fraction
d'étranglement, et quel rôle porte chaque entrée. Aucune n'est visible depuis `Vehicle` (§2.3), et
`LauncherModel` est le **seul** point du dépôt qui tient les trois à la fois : il a `stages()`,
donc les rôles et les débits, et `ascentProfile()`, donc `f`.

Le plan porte donc : le rôle de chaque entrée de pile, l'index de l'entrée basse du bloc, si le bloc
se largue groupé, son seuil, et la fraction d'étranglement.

**Pourquoi pas le rôle sur `LaunchVehicle`.** C'est le modèle le plus honnête — un étage sait ce
qu'il est — mais `f` n'est pas une propriété du véhicule : le §3.3 du découpage l'a placé sur
`AscentProfile`, comme programme de vol. Il faudrait donc un second canal de toute façon, et 21
fixtures `new LaunchVehicle(` à couvrir par surcharges pour n'en résoudre qu'une moitié.

**Pourquoi pas une résolution chez l'appelant.** Une vue construite à côté de la pile laisserait les
30 sites qui appellent `vehicle.resolveActiveStage(masse)` ne rien voir du bloc. La pile est
l'endroit où tout le dépôt pose déjà la question.

**Ce que `LauncherModel` refuse à la construction** : un étranglement déclaré sans bloc parallèle —
un `f < 1` sur un lanceur sans propulseurs serait silencieusement ignoré, ce qui est une erreur de
catalogue et non un réglage inerte — et deux entrées portant le même rôle, qui rendraient la
résolution par rôle ambiguë (§3.7).

### 3.3 Groupé ou non : mesuré sur les charges, pas déclaré par `f`

**Décision : le plan calcule les ergols que le corps garde à l'extinction des propulseurs, et c'est
ce nombre qui décide.**

```
coreLeft = charge_corps − charge_propulseurs · ṁ_corps / ṁ_propulseurs
```

| `coreLeft` | ce que le plan produit |
|---|---|
| `> ε` | deux largages, phase corps seul, cinq phases d'ascension |
| `\|coreLeft\| ≤ ε` | largage groupé, trois phases |
| `< −ε` | **refus à l'instanciation** |

Le refus n'est pas une précaution : un `coreLeft` négatif veut dire que le corps s'éteint **avant**
les propulseurs, donc une perte de poussée en cours de combustion partagée. Le modèle n'a pas de
forme pour l'exprimer — un `ConstantThrustManeuver` par phase — et l'agrégat mentirait en silence
sur la poussée d'une partie de la phase.

**`ε` = ce que le corps brûle pendant une milliseconde**, soit 2,6 kg sur le Falcon. La
milliseconde n'est pas choisie : c'est le quantum de temps du modèle, celui que
`StageSeparationStage` impose déjà à son coast et qu'`AscentPlan` place devant chaque allumage. Une
phase corps seul plus courte ne serait pas ordonnançable.

**Pourquoi pas `f` comme critère**, qui serait plus lisible et n'aurait aucune tolérance à
justifier : le §2.5 le démolit sur le deuxième lanceur de l'item, pas sur un cas tordu. L'Ariane 64
vole `f = 1` avec 350 s de corps seul.

**Et le calcul n'est pas ajouté pour le critère** : `coreLeft` **est** le seuil du bloc. Dès que les
deux ne s'éteignent pas ensemble, le `massAbove` du bloc vaut `sec corps + coreLeft + pile
au-dessus`. Le plan doit produire ce nombre pour exister ; le critère ne fait que le lire. Trois
multiplications, une fois par instanciation de pile, et zéro quand aucun propulseur n'est déclaré —
c'est-à-dire dans tout `L1`.

Sur le Falcon éclaté au prorata 2/3 – 1/3, `coreLeft` vaut **exactement 0 en binaire**, y compris
sur les charges écrites à la main `{400 000, 200 000, 100 000}` des deux épinglages : les débits
sont dans un rapport de puissance de deux et l'arrondi de la division est invariant d'échelle.

### 3.4 La multiplicité : composants par exemplaire, accesseurs agrégés

**Décision : les composants de `StageModel` se nomment `unitDryMass`, `unitPropellantCapacity`,
`unitPropulsion`, `unitAerodynamics` ; les accesseurs `dryMass()`, `propellantCapacity()`,
`propulsion()` et `aerodynamics()` gardent leurs noms et rendent le produit par `N`.**

Le catalogue écrit alors ce que les sources donnent — 141 t et 9,08 m² par P120C, 22 t et 10,5 m²
par bloc Falcon — et la couche qui vole ne voit que des agrégats. **Aucun accesseur au nom de total
ne rend une valeur unitaire**, et aucun site existant ne change de nom ni de sens :
`PropellantBudget`, `LaunchConfiguration`, `StageModel.toVehicle` et `StepLauncher` continuent de
lire ce qu'ils lisaient, agrégé.

C'est le point où le découpage se contredit lui-même, et il fallait trancher : le §5 `L2` écrit la
ligne Falcon en agrégat (44 t / 822 t / 15,2 MN / 21,0 m², soit deux fois l'unitaire) et le §5 `L4`
écrit la ligne Ariane dans les deux conventions à la fois — 564 t d'ergols agrégés, mais des
sections « 9,08 / 22,9 / 22,9 m² » où 9,08 est la section d'**un** P120C (4 × 9,08 + 22,9 = les
59,2 m² au décollage que le §2.3 calcule). C'est exactement le genre de facteur qu'aucun test
unitaire ne voit, et c'est la raison pour laquelle `AerodynamicProperties` documente déjà, dans le
dépôt, pourquoi l'ordre de ses deux composants a été choisi contre une transposition invisible.

`N` multiplie la masse sèche, la capacité, la poussée et la section. Il ne multiplie **ni l'Isp ni
le coefficient de traînée**, qui sont intensifs. `N ≥ 1`, défaut 1.

**Pourquoi pas l'agrégat déclaré avec un accesseur unitaire dérivé** : la multiplicité ne toucherait
jamais le chemin masse/ΔV, ce qui est plus sûr encore — mais le catalogue afficherait des nombres
qu'aucune source ne donne et la provenance ne vivrait plus que dans un commentaire. Le §8 du
découpage promet à `PHY-5` « un propulseur … sa section (9,08 m² sur l'Ariane, 10,5 m² sur le
Falcon) » : c'est cette valeur-là que le catalogue doit porter.

### 3.5 L'ascension à cinq phases, ou trois

**Décision : la fabrique de chaîne émet cinq phases quand la pile déclare un bloc qui ne se largue
pas groupé, et les trois d'aujourd'hui sinon.**

```
Vertical Ascent → GT (bloc) → largage propulseurs → GT (corps) → S1 separation → GT (S2)
```

**`AscentPlan` gagne une troisième combustion.** Il est aujourd'hui structurellement à deux —
`burn1Duration`, une `jettisonDate`, `burn2Duration`, `firstStage`, `secondStage`. Il lui faut
`coreBurnDuration`, le `coreStage`, et les dates dérivées `boosterJettisonDate()`,
`coreIgnitionDate()`, `coreJettisonDate()`.

Son invariant le plus fragile est préservé par construction. Le record documente que *« date
arithmetic is deliberately literal »* : `a.shiftedBy(x).shiftedBy(y)` et `a.shiftedBy(x + y)` ne
sont pas les mêmes bits, et collapser ces chaînes déplacerait les frontières de combustion de
femtosecondes. Sur le chemin sans bloc, les nouvelles dates **ne s'insèrent pas** dans la chaîne :
`jettisonDate()` reste `firstIgnitionDate().shiftedBy(burn1Duration).shiftedBy(ε)`, epsilon par
epsilon. C'est ce qui laisse les quatre épinglages verts sans être touchés.

**La phase corps seul est une troisième sous-classe de `GravityTurnBurnStage`**, structurellement
identique à la première : `DepletionStopTrigger` sur le plancher du corps — sémantique de flame-out,
donc une charge qui varie ne demande aucun recalcul de fenêtre —, fin sur une date de largage, et
loi de tangage ancrée sur `kickDate`/`transitionTime` du plan. L'attitude reste une fonction pure de
la date à travers les cinq phases comme à travers les trois, ce qui est la raison d'être de cet
ancrage.

**La pénalité d'étagement suit sans changer de forme.** `stagingCompleteTime` devient
`combustion bloc + coast de largage + combustion corps + coast interétage`. `GravityTurnProblem`
et le contrôle de réplication de `GravityTurnFirstBurnStage` lisent ce seul nombre.

**Le pas d'intégrateur aussi.** `OrekitService.burnLimitedMaxStep` est variadique : la combustion du
corps, qui s'allume après le coast de largage, ajoute sa `BurnSpec` à celle de la deuxième
combustion. Sans bloc, aucune `BurnSpec` n'est ajoutée et le pas est inchangé au bit près ; avec un
bloc largué groupé, il n'y a pas de phase corps seul, donc pas de spec non plus — le vol de fixture
du §6.2 garde donc le pas du Falcon d'aujourd'hui.

**Un seul point de fabrication.** La chaîne est écrite en dur à **deux** endroits qui doivent rester
d'accord : `AscentSequence.gravityTurn` et `GravityTurnFirstBurnStage.optimizationChain`, qui n'en
diffèrent que par le silence des logs. Passer de « toujours 3 » à « 3 ou 5 » les ferait diverger si
on les laissait séparées. `AscentSequence` porte donc la fabrique unique, les deux appels ne se
distinguant plus que par un drapeau. Les quatre missions passent leur `Vehicle` à `buildStages` — le
plan d'étagement y est, puisqu'il vit sur la pile.

### 3.6 Le largage des propulseurs est une séparation ordinaire

**Décision : une `StageSeparationStage` comme les deux autres, avec un coast de 0 s.**

Elle hérite alors de tout ce que la classe porte déjà : la garde du rôle attendu, la capture de
résidu de `MissionOptimizer` — qui teste `stage instanceof StageSeparationStage` —, la ligne de
télémétrie et la présence dans la liste des phases.

**Le prix, et il est réel** : la classe clampe son coast à 1 ms, donc le corps cesse de pousser
pendant une milliseconde là où un vrai lanceur ne coupe rien. Cela ne perd pas d'ergols — les 2,6 kg
sont brûlés une milliseconde plus tard — mais décale la combustion, dont le coût est la perte
gravitationnelle du décalage, de l'ordre du centième de mètre par seconde. À écrire dans les
limitations, pas à corriger ici.

**Pourquoi pas un largage fondu dans l'entrée de la phase corps seul**, qui ne coûterait rien du
tout : `MissionStage.enter` peut baisser la masse, c'est exactement ce que `StageSeparationStage`
fait. Mais le largage disparaîtrait de la liste des phases, de la télémétrie et de
`captureJettisonedResidual`, et la classe de bug que « séparations explicites » a fermée — un
largage implicite dont personne ne vérifie qu'il tombe sur le bon étage — se rouvrirait à moitié.

### 3.7 Les gardes de largage attendent un rôle, pas un index

**Décision : `StageSeparationStage.expectedStageIndex` devient `expectedRole`, et
`ActiveStageInfo` expose `role()` depuis le plan d'étagement.**

`AscentSequence.FIRST_STAGE_INDEX`, `GEOMission.UPPER_STAGE_INDEX` et
`LunarOrbitMission.UPPER_STAGE_INDEX` disparaissent avec l'assertion d'`AscentSequenceTest` qui
épingle le premier à 0 : **plus aucun index de pile n'est écrit à la main dans l'item.** Décaler les
constantes d'un cran, ce que `L2` aurait imposé, déplacerait la classe de bug ; les dériver du rôle
la referme.

Le bloc rend le rôle de son entrée basse, `BOOSTER`. Le largage groupé attend donc `BOOSTER`, les
deux largages en attendent `BOOSTER` puis `CORE`, et une ascension sans propulseurs attend `CORE` —
la fabrique connaît la forme et pose le bon rôle. Le message d'erreur nomme désormais des rôles là
où le défaut en est un.

La fabrique vérifie à la construction que le rôle attendu existe dans la pile, donc l'échec reste
aussi précoce qu'avec un index résolu au catalogue.

### 3.8 Ce qui se dérive sans choix

**Prorata des débits.** Rien ne brûle sous le bloc, donc `consommé = pile.getMass() − masse
courante`, réparti entre `ṁ_propulseurs = N·F_p/(Isp_p·g₀)` et `ṁ_corps = f·F_c/(Isp_c·g₀)`. Les
deux débits étant constants et les deux réservoirs coulant ensemble sur toute la phase partagée, la
répartition ne dépend que du total consommé : `resolveStagePropellant` reste **sans état**, comme
aujourd'hui. Le bloc rendant l'index de son entrée basse, `captureJettisonedResidual` enregistre le
résidu des propulseurs au premier largage et celui du corps au second, sans rien changer à
`MissionOptimizer`.

**Aérodynamique du bloc.** Section = somme des sections des entrées qui en déclarent une ;
coefficient de traînée = `Σ(Cd_i·S_i) / ΣS_i`, la seule combinaison qui conserve la force ; `null`
seulement si aucune entrée n'en déclare. C'est l'application littérale, entrée par entrée, du
contrat de `PHY-1` : déclarer rien, c'est ne pas traîner. Sur les deux lanceurs tous les `Cd` valent
0,4 en régime continu, donc la pondération est neutre aujourd'hui ; la formule est celle qui reste
juste quand ils diffèrent, ce que `PHY-2` fera.

**`f`** vit sur `AscentProfile`, domaine `0 < f ≤ 1`, défaut `1.0`, et n'est lu que par l'agrégation
du bloc. Au largage, le corps retrouve sa poussée pleine sans que rien ne l'écrive : la phase corps
seul lit la propulsion de l'entrée de pile, non étranglée.

**Le rôle de la charge utile** dans le plan est `KICK`, ce que le Javadoc de `StageRole.KICK` dit
déjà — *« Payload-integrated apogee motor »*.

---

## 4. Le chemin mono-propagateur refuse le bloc

Le virage gravitationnel a encore sa forme historique : `GravityTurnManeuver.configure`, un seul
propagateur portant les deux combustions et le largage en `DateDetector`/`RESET_STATE` au milieu.

**Mesuré : plus aucun appel de `src/main` ne l'atteint.** `GravityTurnFirstBurnStage` construit le
problème à quatre arguments, donc `AscentChainPropagation` ; le constructeur à trois arguments, qui
retombe sur `maneuver.asPropagation()`, n'est appelé que par `GravityTurnProblemTest`, et
`configure` directement que par `GravityTurnReplayConsistencyTest`.

Il ne peut pas apprendre le bloc sans dupliquer la logique des cinq phases dans un second endroit —
exactement ce que `AscentChainPropagation` existe pour empêcher, et la raison pour laquelle les deux
passes ne peuvent plus diverger. Il **refuse** donc une pile déclarant un bloc parallèle, en trois
lignes, plutôt que de voler en silence une ascension dont la poussée est fausse après l'extinction
des propulseurs.

---

## 5. Ce que le lot touche

**`src/main`, quatorze fichiers.**

| Fichier | Ce qui change |
|---|---|
| `model/stage/StageModel` | composants `unit*`, accesseurs agrégés, `multiplicity` (§3.4) |
| `model/AscentProfile` | `coreThrottle`, domaine `(0, 1]` (§3.8) |
| `model/LauncherModel` | construit le `StagingPlan`, refuse un étranglement sans bloc et deux entrées de même rôle (§3.2) |
| `vehicle/StagingPlan` | **nouveau** : rôles, index du bloc, groupé ou non, seuil, étranglement |
| `vehicle/VehicleStack` | second composant, résolution du bloc, prorata (§3.1, §3.8) |
| `vehicle/ActiveStageInfo` | `role()`, et rien d'autre |
| `vehicle/Vehicle` | `stagingPlan()`, défaut « rien de déclaré » — c'est par là que la fabrique d'ascension lit la forme de la pile sans `instanceof` |
| `stage/StageSeparationStage` | `expectedRole` (§3.7) |
| `stage/ascent/AscentPlan` | troisième combustion et ses trois dates (§3.5) |
| `stage/ascent/AscentSequence` | fabrique unique, 3 ou 5 phases |
| `stage/ascent/GravityTurnCoreBurnStage` | **nouveau** : la phase corps seul |
| `stage/ascent/GravityTurnFirstBurnStage` | `optimizationChain` délègue à la fabrique |
| `maneuver/GravityTurnManeuver` | `plan()`, `getStagingCompleteTime()`, `maxStepSeconds()`, refus du bloc (§4) |
| `operation/` × 4 | `buildStages` reçoit le véhicule ; les trois constantes d'index disparaissent |

---

## 6. Ce qui ferme le lot

### 6.1 Les quatre épinglages, non modifiés

```bash
JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.5" ./gradlew gateTest
```

C'est la tâche que `L0` a créée (`02-baseline-L0.md` §2.4), à `forkEvery = 1`, et la commande que le
découpage désigne pour fermer `L1` et `L2`. Les quatre classes doivent rendre exactement ce que
`L0` a re-capturé, sans qu'aucune ligne de test n'ait bougé.

### 6.2 Le vol éclaté de fixture, à tolérance zéro

Un lanceur de **test** déclarant `[propulseurs ×2, corps, S2]` à `f = 1`, volé sur le profil LEO-400
aux charges écrites à la main, comparé **dans le même run** au Falcon du catalogue.

C'est la forme d'`EarthOrbitNonRegressionTest`, dont `L0` §2.2 rappelle qu'il compare deux
compositions de la même mission dans un seul run à tolérance 0, et qu'il survit pour cette raison à
n'importe quel catalogue. Les charges du profil étant écrites à la main, `PropellantBudget` n'est
pas nécessaire : `{600 000, 100 000}` devient `{400 000, 200 000, 100 000}`, prorata exact.

Ce test déplace la preuve d'iso-trajectoire du §4 du découpage **de `L2` vers `L1`**. `L2` cesse
alors d'être un pari : il déplace dans `Launchers` des nombres déjà prouvés, et apprend le prorata à
`PropellantBudget`.

### 6.3 Les contrôles physiques du chemin à cinq phases

Sur une seconde fixture, à `f < 1`, et non iso-trajectoire par nature : date d'extinction des
propulseurs, durée de la phase corps seul, masse au largage, répartition des ergols entre les deux
entrées. Les valeurs se calculent hors JVM depuis le catalogue, comme `L0` §3 l'a fait pour les
durées de combustion.

### 6.4 Mesuré à la clôture

```
gateTest, 3 min 08 s
  EarthOrbitNonRegressionTest   tests=4 skipped=0 failures=0 errors=0
  MissionPolylineBaselineTest   tests=1 skipped=0 failures=0 errors=0
  CentralBodyBaselineTest       tests=4 skipped=0 failures=0 errors=0
  AscentBaselineN2Test          tests=2 skipped=0 failures=0 errors=0
```

Aucun des quatre fichiers n'a été touché. La suite rapide rend **1 388 tests, 30 sautés, 0 échec,
0 erreur** sur 203 classes, en 3 min 01 s — contre 1 318 sur 197 classes au relevé de `L0` ;
`pmdMain`, `pmdTest` et `spotlessCheck` passent.

Le vol de fixture, mesuré :

```
L1 split vs catalog Falcon Heavy at MECO: Δpos 0.000e+00 m, Δvel 0.000e+00 m/s, Δmass 0.000e+00 kg
Throttled Falcon Heavy: block burn 157.0 s, core-only 29.8 s, core total 186.8 s
```

Le zéro est un zéro exact, pas un arrondi d'affichage : les trois comparaisons sont à tolérance
`0.0`. Et le profil étranglé rend les 157 s, 30 s et 187 s que le §2.3 du découpage dérive de
`f ≈ 0,81`, sans qu'aucun de ces trois nombres n'ait été écrit dans le code.

### 6.5 Les unitaires

Résolution du bloc de part et d'autre de son seuil · plancher du bloc contre le calcul à la main ·
refus d'un `coreLeft` négatif · refus d'un étranglement sans bloc · refus de deux entrées de même
rôle · largage groupé contre deux largages sur les mêmes chiffres · et la chaîne de dates
d'`AscentPlan` inchangée epsilon par epsilon quand aucun bloc n'est déclaré.

---

## 7. Limitations assumées

- **1 ms non propulsé au largage des propulseurs** (§3.6). Les ergols ne sont pas perdus, la
  combustion est décalée ; le coût est la perte gravitationnelle du décalage, de l'ordre du
  centième de mètre par seconde.
- **L'Isp est tenue constante sous étranglement.** Un moteur réel en perd quand il descend sous sa
  poussée nominale. Le modèle n'a qu'une Isp par entrée de catalogue, et le §3.4 du découpage a déjà
  décidé que les étages atmosphériques gardent un proxy de trajectoire moyenne.
- **L'étranglement est constant** pendant toute la phase partagée (§1, point 5 du découpage). Le
  vrai Falcon Heavy ré-accélère son corps central après le largage ; ici il passe de `f` à 1 d'un
  coup, au largage.
- **Le chemin mono-propagateur du virage refuse un bloc** au lieu de l'apprendre (§4).
- **Tout ce que le lot livre est inerte.** Aucune entrée de `Launchers` ne déclare de propulseurs,
  donc aucune trajectoire de production ne passe par une seule des lignes écrites ici. C'est le
  critère de clôture, pas un regret.

---

## 8. Ce que la conception corrige au découpage

1. **`Isp_eff` n'est pas juste « au chiffre près », elle est exacte au bit** (§2.4), et toute la
   chaîne dont l'ascension dépend avec elle. Le §4 du découpage présente la seconde preuve
   d'iso-trajectoire comme une propriété heureuse des trois corps identiques ; c'est une propriété
   démontrable des chiffres, ce qui permet de la prouver dès `L1` (§6.2).
2. **La phase corps seul n'était attribuée à aucun lot.** Le §3.1 du découpage écrit qu'*« au
   largage, le bloc se dissout et le corps continue avec ses propres chiffres »*, mais le §5 la
   donne ni à `L1` (qui ne liste que le largage groupé) ni à `L3` (qui ne liste que `f ≈ 0,81`). Ce
   n'est pas un réglage : la poussée change à l'extinction des propulseurs, un
   `ConstantThrustManeuver` ne change pas de poussée, donc il faut une phase. Elle est ici, inerte,
   pour que le re-baseline de `L3` reste attribuable à une cause unique.
3. **`f` ne peut pas décider du largage groupé** (§2.5) : l'Ariane 64 vole `f = 1` avec ~350 s de
   phase corps seul, le Falcon `f = 1` sans aucune. Le critère est la charge, pas l'étranglement.
4. **Le §5 écrit ses deux lignes de catalogue dans deux conventions opposées** (§3.4) : `L2` en
   agrégat, `L4` avec des ergols agrégés et des sections par exemplaire dans la même phrase.
   Tranché : le catalogue déclare par exemplaire, les accesseurs agrègent.
5. **Ce qui casse en premier, c'est `resolveActiveStage`, pas `MassDepletionDetector`** (§2.2). Le
   §3.1 ne prévient que sur le second. Le seuil est franchi aux 74 % de la combustion, sur le profil
   même des deux épinglages à tolérance zéro.
6. **La pile ne voit pas `StageRole`** (§2.3) : `StageModel.toVehicle()` jette `capabilities`. Les
   « recherches par `StageRole` » du §3.2 supposaient un porteur que le découpage ne nomme pas.
7. **Le §3.1 dit « la section la somme » et ne dit rien du `Cd`** (§3.8). Sommer les sections sans
   pondérer les coefficients donnerait une force fausse dès que deux entrées d'un bloc n'ont pas le
   même régime — ce qui n'arrive pas sur les deux lanceurs d'aujourd'hui, et arrivera chez `PHY-2`.

**Ce que l'implémentation a trouvé en plus.**

- **Une fixture déclarait deux étages `CORE`.** `PropellantBudgetTest.liquidStage` donnait le rôle
  `CORE` à tous les étages qu'elle construit, y compris l'étage supérieur d'un lanceur à deux
  étages. L'unicité des rôles (§3.2) l'a refusée — la fixture disait quelque chose de faux sur son
  propre lanceur, et personne ne pouvait s'en apercevoir tant que le rôle ne servait à rien.
- **La section du Falcon éclaté vaut 31,5 m², pas 31,6.** Le catalogue arrondit son agrégat à 31,6 ;
  trois exemplaires de 10,5 en donnent 31,5, et la valeur exacte est 3 × π·1,83² = 31,56. Rien ne la
  lit — aucune mission de production ne déclare d'atmosphère — donc la trajectoire n'en dépend pas,
  et c'est à `L2` de choisir lequel des deux arrondis le catalogue garde.
- **`AscentSequenceTest` épinglait `FIRST_STAGE_INDEX == 0`.** L'assertion disparaît avec la
  constante ; ce qu'elle gardait est désormais couvert par comportement dans
  `StageSeparationStageTest`, qui vérifie que la garde refuse le mauvais rôle.

**Correction d'un chiffre avancé en conversation** : la perte du largage à 1 ms y avait été estimée
à ~0,015 m/s, sur une masse fausse et en confondant l'impulsion non délivrée à cet instant avec une
perte. L'impulsion est délivrée une milliseconde plus tard ; ce qui est réellement perdu est la
gravité de ce décalage, de l'ordre de 0,01 m/s (§3.6).

---

## 9. Ce que `L1` lègue

- À **`L2`** : le vol éclaté déjà prouvé iso-trajectoire (§6.2). Il ne reste qu'à écrire les mêmes
  nombres dans `Launchers` et à apprendre à `PropellantBudget` le prorata 2/3 – 1/3 — plus la carte
  du wizard, qui affiche `"S1 thrust"` depuis `stages().getFirst()` et annoncerait 15,2 MN au lieu
  de 22,8 dès l'éclatement.
- À **`L3`** : un seul nombre à poser. Le passage de trois à cinq phases est déjà écrit, testé et
  inerte ; le re-baseline reste attribuable à `f`.
- À **`L4`** : le même mécanisme sans étranglement, l'Ariane 64 se groupant d'elle-même par ses
  débits (§2.5), et un `variableLoad()` qui rendra enfin `false` sur un étage qui l'est.
- À **`PHY-5`** : une masse sèche et une section **par exemplaire** dans le catalogue (§3.4), et un
  largage de propulseurs qui est un événement de pile ordinaire auquel accrocher `N` débris.
