# MIS-4 / L4 — La mission du produit

Lot **L4** du découpage ([`01-decoupage.md`](01-decoupage.md) §4), conçu sur les chiffres de
[`02-baseline-L0.md`](02-baseline-L0.md) et sur ce que [`L1`](03-conception-L1.md),
[`L2`](04-conception-L2.md) et [`L3`](05-conception-L3.md) lèguent. Il rend vraie une seule
propriété : **la mission vole du sol au survol**.

C'est la livraison du chantier. `MissionType.LUNAR_FLYBY`, `MissionSpec.Lunar`, la branche du
`switch` de `MissionComposer`, et une chaîne d'étages qui décolle d'un pas de tir, insère en
parking, coaste jusqu'à son point d'injection, part vers la Lune et la survole. La mission est
construite depuis une spec bâtie **en test** : aucun wizard, qui est le lot suivant.

**Sept énoncés écrits ailleurs sont corrigés ici.** Ils sont rassemblés au §10 ; trois d'entre eux
tombent d'une mesure faite en concevant, quatre d'un raisonnement mené avant d'écrire.

---

## 1. Ce que le code dit avant qu'on y touche

Quatre relevés, tous faits avant qu'une ligne bouge. Les deux premiers ferment chacun une option
avant qu'elle soit posée.

### 1.1 — Huit classes seulement écrasent `propagateStandalone`, et aucune n'est un coast

L1 §6 lègue le piège : le coast de parking **doit** écraser `propagateStandalone`, sinon la marche
d'étages de `MissionOptimizer` résout l'injection depuis l'état à l'insertion en parking — mauvaise
phase, mauvaise date, aucune erreur levée. Le raccourci tentant est de réparer `CoastingStage` une
fois pour toutes.

Relevé : `propagateStandalone` n'est écrasé que par `AnalyticApogeeCircularizationStage`,
`AnalyticGtoInjectionStage`, `AnalyticHohmannTransferStage`, `AnalyticParkingInsertionStage`,
`AnalyticPlaneTrimAtNodeStage`, `AnalyticTrimBurnStage`, `ConstantThrustStage` et
`GravityTurnBurnStage`. **Les huit étages propulsés ou analytiques, et rien d'autre.** Aucun coast,
aucune séparation.

Donc, dans la marche d'étages, **tous les coasts de toutes les missions du dépôt s'effondrent à
durée nulle**. GEO en a déjà un en milieu de chaîne — `CoastingStage("Coasting parking", true)`,
`GEOMission:213` — bénin là-bas parce que l'injection depuis une orbite circulaire ne dépend pas de
la phase. Réparer la classe déplacerait la marche d'étages de **toutes** les missions, y compris
les références que MIS-7 a ré-enregistrées.

**Décidé : sous-classe neuve, `CoastingStage` n'est pas touché.** Ce n'est pas un arbitrage, c'est
ce que la mesure laisse.

Une précision au passage, parce que L1 §6 désigne `TranslunarCoastStage` comme précédent : **cette
classe n'écrase pas `propagateStandalone` non plus.** Elle est le précédent de la *forme* — une
sous-classe qui existe pour une méthode — pas de l'écrasement. Personne ne doit s'attendre à le
trouver déjà là.

### 1.2 — Six sites que le compilateur désignera

Ajouter la constante d'énumération **et** le membre du `permits` fait pointer le compilateur sur six
endroits, dont la moitié n'appartient pas à ce lot :

| Site | À qui il appartient |
|---|---|
| `MissionComposer.compose` | L4 — c'est le lot |
| `MissionHorizon.defaultFor:67` | L4 |
| `MissionTargetOrbit.of` | signalé par L3 §6 d comme « cassera à L4 » |
| `MissionFactory.build:89` | **L5** |
| `WizardPrefill` | **L5** |
| `ScenarioMapper.toScenarioMission:76` | **aucun lot** — voir §10 pt 5 |

Plus trois `instanceof` sans aide du compilateur : `MissionPlanOptimizer:155`,
`MissionWizardAppState:194`, `MissionProfile:302`. Les deux derniers testent
`instanceof MissionSpec.EarthOrbit` avec une branche de repli, donc une spec lunaire les traverse
sans rien casser ; le premier est traité au §5.

### 1.3 — Le chemin λ est fermé par défaut, et s'ouvre d'un clic

| | |
|---|---|
| Mode par défaut d'une entrée | `FAST` (`MissionEntry:42`) |
| Ce qui ouvre le chemin λ | `PRECISE` **et** une spec présente (`MissionPlanOptimizer:95`) |
| Ce qui casse alors | `MissionLoadEvaluator:326` appelle `orbitInsertionObjective(...)`, qui jette (`:473`) |
| Où le jet tombe | **hors** du `try` qui absorbe les échecs d'optimisation — celui-ci n'enveloppe que `new MissionOptimizer(...).optimize()` — donc jusqu'au `catch (Exception)` de `MissionOrchestratorAppState:232` |
| Ce que l'utilisateur voit | `FAILED` et un message interne, **après** une première évaluation λ complète : une ascension CMA-ES et un vol de sept jours |

C'est la limitation (a) de L3 §6, et L3 §7 en a explicitement laissé la décision à ce lot.

### 1.4 — La date de tir ne vit pas sur la spec

Ni `MissionSpec.EarthOrbit` ni `MissionSpec.Geo` ne portent de date. Elle vit sur
`MissionEntry.getScheduledDate()`, posée par l'étape de planification du wizard, persistée à part
sous `LAUNCH_DATE`, et l'orchestrateur retombe sur l'horloge quand elle est absente
(`MissionOrchestratorAppState:203`).

**Conséquence pour L4 : c'est l'appelant qui date le tir.** Le test résout la fenêtre de L2, en
prend la date, et la donne comme époque de vol — il joue le rôle que l'étape de planification jouera
en L5. `MissionSpec.Lunar` n'a pas de composant de date à inventer.

---

## 2. L'altitude de parking devient un paramètre, à 400 km

Le découpage §4 écrit la chaîne de L4 avec `AnalyticParkingInsertionStage(185 km, i = φ)`. L0 §7
pt 5 avait déjà noté que ce 185 km est posé en dur dans le découpage lui-même. Ce lot ne le
reconduit pas.

**Le 185 km n'est déjà plus dans le chemin lunaire.** Après L1, `PARKING_ALTITUDE` n'est lu qu'à
`TranslunarInjectionPlan:181`, c'est-à-dire dans `parkingState` — la démo. `departureFrom` et `solve`
sont agnostiques à l'altitude : ils lisent l'état qu'on leur passe. Et L2 en a **déjà** fait un
paramètre de constructeur (`LunarLaunchWindowProblem:119`). Figer 185 km à L4 ne serait pas conserver
un couplage, ce serait en réintroduire un dans une chaîne neuve, à l'endroit exact où les deux
briques amont viennent de s'en libérer.

**Quatre faits décident de la valeur.**

1. **La visée ne fait pas de différence.** L0 §2 : 185 / 250 / 300 / 400 km convergent en un nombre
   de bissections identique à une unité près, périlune au kilomètre partout.
2. **Le coût va dans l'autre sens.** Le TLI vaut **3 178 m/s à 185 km contre 3 124 m/s à 400 km**,
   −54 m/s en faveur du plus haut. La contrepartie — le coût d'ascension jusqu'à 400 km — n'est pas
   dans ce tableau et joue en sens inverse.
3. **Aucune ascension du dépôt n'est jamais montée à 185 km.** Toutes les cibles de parking volées
   sont à 400 km (`GEOMissionOptimizationTest`, `MeoMissionTest`, `AnalyticParkingInsertionStageTest`,
   `AscentBaselineN2Test`, `MissionAscentWiringTest`) ; la plus basse jamais volée est le 200 km de la
   ligne elliptique `200_000, 1_000_000` de `LEOMissionOptimizationTest`. Et
   `GravityTurnConstraints.getFpaWindowDeg` place son coude **à 185 km exactement** : la fenêtre
   d'angle de pente y vaut `[−0,5°, 1,3°]` contre `[0°, 2,5°]` à 400 km. C'est le bord le plus serré
   de la calibration CMA-ES, et il n'a jamais été exercé.
4. **Le wizard plancherait à 200 km de toute façon** : `StepParameters:251` construit le curseur de
   parking GEO sur `(200, 2 000)` avec un défaut à 400. Une chaîne qui figerait 185 km volerait une
   valeur que l'UI de L5 serait incapable de proposer.

**Décidé : `parkingAltitude` est un composant de `MissionSpec.Lunar`, à 400 km, et il n'est pas
exposé au wizard.** Une source unique pour la fenêtre, la chaîne et le budget d'ergols ; les tests
peuvent balayer les quatre altitudes que L0 a mesurées ; et L5 garde le champ **unique** que le
découpage lui donne — l'altitude de périlune. La troisième voie, un curseur de plus dans le wizard,
est écartée : elle élargit le périmètre agréé de L5 pour quelques dizaines de m/s que l'utilisateur
n'a aucun moyen d'arbitrer.

**Un chiffre du découpage bouge avec ce choix.** L'argument de rallumage du §4, lot `L6`, est calculé
à 185 km : une révolution vaut 5 291,5 s, et L1 §2.2 a mesuré 5 302 s en comptant la dérive du point
d'injection. À 400 km la révolution vaut **5 553,6 s**, plus la même dérive d'une douzaine de
secondes. Toujours très en deçà des 7 200 s du Falcon Heavy S2 et des 21 600 s de l'ULPM : la
conclusion tient, la ligne de calcul non.

---

## 3. La chaîne

### 3.1 — Le squelette

`LunarFlybyMission extends EarthMission` — les trois accesseurs de site, comme `GEOMission` :

```
VerticalAscentStage
AscentSequence.gravityTurn(profile, GravityTurnConstraints.forTarget(400 km), plane, latitude)
AnalyticParkingInsertionStage("Parking", 400 km)
ParkingCoastStage                       ← la seule pièce neuve (§3.2)
TranslunarInjectionStage                ← réutilisé tel quel
TranslunarCoastStage
```

**Le préfixe d'ascension est déjà écrit deux fois** : à la main dans `GEOMission:194`, et derrière le
helper privé `ascentThen` dans `EarthOrbitMission:341`. Le lunaire en fait une troisième copie — de
**deux lignes**, `AscentSequence` possédant déjà la partie difficile (les trois phases explicites et
leur plan partagé).

**Pas d'extraction, et c'est délibéré.** `ascentThen` n'est pas réutilisable : il *ferme* la chaîne
par `CoastingStage("Coasting", null)` et un trim de plan conditionnel. Factoriser les deux lignes
restantes ferait toucher les deux missions qui portent les références recalibrées par MIS-7, pour un
gain de deux lignes. Le rapport est mauvais dans le lot qui vole cette chaîne pour la première fois.

### 3.2 — `ParkingCoastStage`

La durée **ne peut pas être un argument de constructeur** : `departureFrom` a besoin de l'état à
l'insertion en parking, qui dépend de la date de tir et de l'ascension réellement volée. Cela ferme
aussi la réutilisation de `CoastingStage(name, maxTime)`, dont le `maxTime` est final et lu à
`configure`.

L'étage résout donc sa propre durée à `enter`, en appelant `TranslunarInjectionPlan.departureFrom`,
plante son `DateDetector` dessus, et **écrase `propagateStandalone`** pour propager réellement cette
durée. C'est la seule raison d'être de la classe, et le §1.1 dit pourquoi elle ne peut pas être une
réparation de `CoastingStage`.

`departureFrom` est en forme close — aucune propagation, seule l'éphéméride lunaire est évaluée
(L1 §2.2) — donc le fait qu'elle soit appelée deux fois par vol, une par la marche d'étages et une
par la passe d'éphéméride comme L0 §6 l'a mesuré pour `solve()`, ne coûte rien.

### 3.3 — Pas de largage de S2 après le TLI

GEO porte son `StageSeparationStage` parce qu'il a un second moteur à activer : la séparation existe
pour que `resolveActiveStage` bascule sur l'AKM de la charge utile. Le survol lunaire n'a rien à
activer — la charge utile de L5 est **inerte** (découpage §6 pt 8) et le TLI est la dernière
combustion de la chaîne.

Une séparation ne changerait donc aucune trajectoire, ajouterait un étage qui sait **refuser** (le
garde-fou d'index de pile, posé après un défaut réel sur le profil GEO), et son coast de tassement
creuserait un écart de plus entre la marche d'étages et le vol, puisque `StageSeparationStage`
n'écrase pas non plus `propagateStandalone` (§1.1).

**Le prix assumé** est que la masse rapportée en fin de mission inclut l'étage vide. L6, qui vide
vraiment le S2, ou `MIS-11`, qui aura une charge utile propulsée, sont les lots où une raison
apparaîtra.

### 3.4 — L'horizon, et qui s'aperçoit qu'un survol a été tronqué

L'horizon est `FixedDuration(7 j)`, le nombre du découpage. L0 §4 mesure la sortie de sphère à
**4,51–4,62 j**, donc sept jours laissent ~2,4 j d'arc géocentrique de retour et produisent la
séquence `[EARTH, MOON, EARTH]` que la fixture de L3 §5.1 décrit d'avance. Le plafond de
`MissionHorizon` est à 30 j : il ne mord pas.

Le découpage §4 exige que la capture accidentelle soit traitée explicitement — « lever ou étendre,
jamais rendre un survol silencieusement tronqué ». Or `ObjectiveEvaluator` sélectionne **par corps
seul** et prend le minimum : un vol coupé en pleine descente vers le périlune rend un minimum
parfaitement plausible et **passe**.

**Décidé : la branche survol refuse un minimum atteint au dernier point de l'arc lunaire.** Si le
minimum est le dernier échantillon, l'approche descendait encore : le périlune n'a pas été passé, et
ce qu'on mesure n'est pas une approche au plus près mais l'endroit où le vol s'est arrêté.

Deux options ont été écartées.

- **Un horizon nu, la troncature n'étant vue que par un test.** Cela ne protège pas le produit : la
  propriété doit tenir sur n'importe quelle géométrie, pas seulement sur celle du test.
- **Exiger que l'arc lunaire soit refermé** — un point ultérieur revenu sur Terre. Même propriété,
  mais elle **casse l'assertion que L3 vient d'ajouter à la démo** : à 4,5 j la séquence est
  `[EARTH, MOON]`, l'arc n'est jamais refermé. Il faudrait remonter l'horizon de la démo et lui faire
  perdre sa trace non décimée, la seule du dépôt. Le critère retenu la laisse passer inchangée : son
  périlune tombe à 4,0 j sous un horizon de 4,5 j.

**Et c'est un `false`, pas un jet**, conformément à la règle que L3 §3.3 s'est donnée : un vol tronqué
est un **fait de vol**, comme un corps jamais atteint ou un impact ; le jet reste réservé à l'objectif
mal formé. Le découpage disait « lever ou étendre » ; la règle de L3 est plus récente et plus précise,
et c'est elle qui prime.

---

## 4. `MissionSpec.Lunar`

```java
record Lunar(
    String name,
    LaunchConfiguration configuration,
    double parkingAltitude,        // 400 km, non exposé (§2)
    double periluneAltitude,       // le seul paramètre utilisateur (découpage §6 pt 1)
    String siteName,
    double latitude, double longitude, double altitude,
    MissionHorizon horizon,        // null → FixedDuration(7 j)
    AtmosphereModel atmosphere)    // null → NONE
```

Le constructeur compact normalise `horizon` et `atmosphere` comme les deux autres records le font,
pour qu'une spec assemblée à la main n'ait pas à connaître les défauts.

**Pas de composant d'inclinaison.** `i = φ`, plein est (découpage §6 pt 2), et L2 §6 pt 4 l'a déjà
rendu visible en ne prenant pas de `LaunchPlane` : à `i = φ` les deux azimuts que
`LaunchPlane.launchAzimuth` distingue fusionnent. `EarthOrbit` porte `targetInclination` et
`nodeBranch`, `Geo` porte `finalInclination`, parce que ces deux-là ont un choix à offrir. Le lunaire
n'en a pas avant le lot d'inclinaison adaptative.

**`periluneAltitude`, pas `perileneAltitude`.** L3 §2.1 a établi que `perilene` est un francisme, déjà
présent dans `LunarTransferMission` à côté d'un `DEFAULT_PERILUNE_ALTITUDE` qui le contredit dans le
même fichier. Un record neuf ne le propage pas. Et contrairement à `FlybyObjective`, nommé
`closestApproachAltitude` parce qu'il n'est pas lunaire, `MissionSpec.Lunar` l'est : `perilune` y est
le mot juste.

### 4.1 — La tolérance n'est pas un composant de spec

`FlybyObjective` porte sa tolérance (L3 §2.2). La chaîne du produit doit donc la fournir, et elle
n'existe aujourd'hui que comme `LunarTransferMission.DEFAULT_PERILUNE_TOLERANCE = 10 km` — une
constante que L3 vient de déplacer en production **dans la classe de la démo**.

En faire un composant de spec contredirait l'argument qui lui a donné sa forme : L3 §2.2 a justifié la
tolérance absolue précisément parce que « sa bande n'est pas un choix d'appelant, elle est dictée par
la mesure » — l'échantillonnage à 60 s et la convergence de la visée.

**Décidé : la constante monte sur `LunarFlybyMission`, et la démo la lit de là.** C'est l'inversion de
dépendance dans le bon sens : la mission du produit survit au chantier, la démo est celle qui est
censée mourir. La garder sur `LunarTransferMission` ferait partir la bande du produit avec le lot qui
supprimera la démo. La loger sur `TranslunarInjectionPlan`, qui possède déjà les constantes
translunaires, mélangerait une bande d'**évaluation du vol** avec les réglages d'un plan dont la
convergence propre est ±1 km.

**Et les 10 km sont provisoires.** Ils sont justifiés pour un vol qui part déjà en orbite de parking.
Un vol depuis le sol ajoute la dispersion de l'ascension et les ~3 min de biais de date que L2 §6 a
chiffrées sans les voler — donc une erreur de β, donc une erreur de périlune, d'ampleur inconnue.
**Le vol de fermeture (§8.3) mesure le périlune atteint avant que la bande soit figée.**

### 4.2 — Pas de garde sur l'horizon

`MissionHorizon.Revolutions` sur un vol lunaire compterait les révolutions de l'ellipse translunaire.
L'état d'insertion est encore géocentrique et lié — L0 §8 relève `169 528 × 433 293 919 m`,
`e = 0,9707` — donc le µ terrestre de `keplerianPeriodOf` est ici **juste**, et une révolution vaut
**12,1 j** (calculé, non mesuré). C'est dénué de sens comme intention utilisateur, mais ça ne casse
rien, et un horizon trop court est de toute façon attrapé par la garde du §3.4.

`defaultFor(LUNAR_FLYBY)` rend `FixedDuration(7 j)` et L5 n'offrira qu'une durée. La contrainte que
L0 §7 pt 4 lègue à L5 — `DynamicParameters:135` reste hors chemin *tant que* le profil lunaire garde
un horizon `FixedDuration` — est donc tenue par le défaut, pas par un refus.

---

## 5. Le chemin λ s'élargit à l'objectif

**Décidé : `feasibilityObjective` passe de `OrbitInsertionObjective` à `MissionObjective`, et
`MissionLoadEvaluator` route par `ObjectiveEvaluator.met`.**

Trois raisons.

1. **La pièce existe et n'a aucun consommateur de production.**
   `ObjectiveEvaluator.met(ephemeris, objective, insertionToleranceRatio)` a été écrit par L3 dans
   `runtime/`, avec son `switch` exhaustif, et n'est consommé que par sa fixture et l'assertion volée
   de `LunarTransferFlightTest`. C'est exactement la signature qu'il faut ici. La laisser sans
   appelant un lot de plus, c'est avoir livré une pièce morte.
2. **L'alternative fait diverger un contrôle utilisateur générique.** Faire retomber une
   `MissionSpec.Lunar` sur `fixedLoadPlanner` coûterait trois lignes, mais `PRECISE` voudrait alors
   dire deux choses selon le type de mission, sans que la commande segmentée puisse le dire.
3. **Le coût est contenu à une classe** : le champ `:129`, le paramètre des deux constructeurs
   publics `:217` et `:251`, et le site d'appel `:324-327`. La branche insertion continue de déléguer
   au `objectiveMet` statique, dont les dix appels de test ne bougent pas — GEO et LEO gardent leur
   comportement au bit près, et `MissionPlanOptimizer:155` n'a rien à changer : une spec lunaire passe
   par la branche qui donne `null` et laisse la mission fournir son propre objectif.

**Ce que cela active n'est pas mesuré, et c'est écrit ici plutôt que découvert.** Un balayage λ vaut
jusqu'à 10 évaluations par étage mis à l'échelle (`PropellantLoadOptimizer.DEFAULT_MAX_EVALUATIONS`),
coordonnées entre étages, **chacune un vol complet** : ascension CMA-ES, deux `solve()` à 4,5 s, sept
jours de propagation. Le §8.4 achète une sonde pour ce chiffre, et il peut se révéler assez cher pour
rouvrir la question — auquel cas il vaut mieux le savoir ici qu'en L5.

---

## 6. Les six sites du compilateur

### 6.1 — `MissionTargetOrbit` se règle mieux qu'annoncé

L3 §6 d l'annonce comme « cassera à L4. Signalé, pas corrigé ». Un survol n'a pas de triplet (périgée,
apogée, inclinaison), et en fabriquer un dégénéré ferait afficher une cible géocentrique fausse à côté
de l'orbite atteinte — exactement le genre de silence que ce chantier retire.

Mais `forEntry` **rend déjà un `Optional`**, et ses deux consommateurs traitent déjà l'absence, pour
les entrées *legacy* sans spec : `MissionDetailView:166` omet la ligne TARGET, `PanelFooter:290` omet
l'écart à la cible. Faire rendre `Optional` à `of` et enchaîner par `flatMap` coûte trois lignes et
**aucun cas d'UI neuf**.

### 6.2 — Trois refus explicites

`MissionFactory`, `WizardPrefill` et `ScenarioMapper` appartiennent à des lots ultérieurs, ou à aucun.
L4 y pose une branche qui **lève en nommant le lot qui la remplira**.

Les deux autres formes ont été écartées. Livrer les trois branches pour de vrai absorberait la
fabrique et le préremplissage de L5, et inventerait une `ScenarioMission.Lunar` dont personne n'a
besoin avant qu'une mission lunaire puisse être créée — la règle 1 du découpage §3, un changement de
comportement à la fois, sauterait. Poser un `default` sur ces `switch` donnerait le même résultat
qu'un refus, en **retirant au compilateur** le pouvoir de désigner ces points au type suivant : or
c'est précisément la propriété que L3 §7 comptait comme un legs.

---

## 7. La confirmation de la fenêtre cesse d'être une recopie

L2 §6 pt 6 nomme sa limitation et désigne L4 pour la fermer : les quatre lignes de
`LunarLaunchWindowProblem.confirm` — `resolveActiveStage`, `solve`, `applyTo`, plancher d'épuisement —
sont le corps de `TranslunarInjectionStage.enter` moins son journal.

**Ce qui rend l'échéance réelle, c'est L6.** Il remplace `TranslunarInjectionStage` par
`TLIBurnStage`. Si la recopie survit, la fenêtre continuera de confirmer ses dates contre un modèle
**impulsionnel** que la mission ne vole plus — et l'écart est de ~14 m/s sur Ariane 62, c'est-à-dire
exactement le bénéfice mensuel que la fenêtre existe pour capter. La dérive que le javadoc de
`LaunchWindowProblem#confirm` redoute deviendrait mesurable et invisible en même temps.

**Décidé : on extrait le corps partagé**, une fonction prenant `(état, périlune, ActiveStageInfo,
contexte)` et rendant l'état injecté ou refusant sur le plancher, consommée par l'étage **et** par
`confirm()`. L'étage devient une enveloppe mince — son journal et sa `configuredEndDate` — et il n'y a
plus qu'un endroit à changer à L6.

**C'est une correction à L2, pas son exécution.** La bonne fermeture n'est pas de *rendre la
confirmation à l'étage* : `enter` ne demande à la `Mission` que le véhicule et le contexte
gravitationnel, que `confirm()` a déjà tous les deux sous la main. Lui faire composer une mission au
sol pour réutiliser un `enter` — ce que la lettre de L2 demande, et que rien n'interdit puisque
`window/problem` importe déjà `operation.MissionSpec` pour `EarthLaunchWindowRequest.from` — serait
fabriquer une mission entière pour l'appeler avec un état qu'elle n'a pas produit.

---

## 8. Les tests de fermeture

Le dépôt sépare déjà trois régimes ; L4 se range dedans plutôt que d'en créer un.

### 8.1 — Fermés, en millisecondes

| | Ce qui est fermé |
|---|---|
| Normalisations de `MissionSpec.Lunar` | horizon nul → `FixedDuration(7 j)`, atmosphère nulle → `NONE` |
| `MissionTargetOrbit.of` sur un survol | rend vide, et les deux vues n'ont pas de cas neuf (§6.1) |
| Les trois refus | chacun nomme son lot (§6.2) |
| Troncature, dans `ObjectiveEvaluatorTest` | minimum au dernier point de l'arc lunaire → `false` (§3.4) |
| Routage d'un `FlybyObjective` par `MissionLoadEvaluator` | l'élargissement du §5, sans vol |

### 8.2 — Le piège de L1, à quelques secondes

Que `propagateStandalone` de `ParkingCoastStage` **avance réellement** de la durée que `departureFrom`
a calculée, contre l'état inchangé que rendrait `MissionStage.enter`. C'est la seule assertion qui
rende visible le défaut que L1 §6 a nommé, et elle n'a besoin que d'un état de parking synthétique.

Sa famille existe : `GravityTurnReplayConsistencyTest` garde exactement cette cohérence marche
d'étages / éphéméride sur l'ascension, après un défaut réel qui avait fini en ~5° d'inclinaison sur la
chaîne GEO.

### 8.3 — Un vol, lent

Sol → survol, arcs `[EARTH, MOON, EARTH]`, périlune dans la bande, `isComplete()`, `FlybyObjective`
satisfait. La date de tir vient de la fenêtre de L2 (§1.4), et le vol porte trois mesures que rien
d'autre ne peut faire :

- le **périlune réellement atteint**, avant que les 10 km du §4.1 soient figés ;
- les **deux biais de L2** — 68 s d'ascension hors modèle, 115 s de régression nodale — lus comme
  l'écart entre le β planifié à la date de la fenêtre et le β réel à l'injection ;
- le **demi-degré J2** que L1 §5 pt 1 a calculé sans le voler.

**L'ascension est optimisée pour de vrai**, au budget d'intégration de
`AbstractTrajectoryOptimizerTest` — 40 000 évaluations, graine 42. `LunarTransferFlightTest` vole à
`MAX_EVALUATIONS = 1` parce qu'aucun de ses étages n'est optimisable ; la chaîne de L4 en a un, et le
lot se ferme sur « un vol du sol au survol ». Une ascension **rejouée** depuis un vecteur épinglé
serait rapide et déterministe, mais elle figerait un nombre dans le lot qui vole cette chaîne pour la
première fois — avant que quiconque sache ce qu'il devrait valoir, et alors que les références
d'ascension ont déjà dû être ré-enregistrées une fois après MIS-7. Un budget réduit cumulerait les
deux défauts : une ascension non convergée ferait rater le survol pour une raison qui n'est pas la
sienne.

### 8.4 — Une sonde opt-in, jetable

Le temps de paroi d'un `PRECISE` lunaire — la mesure promise au §5. Elle coûte un balayage entier et
ne peut donc pas être un test. Le patron est `LunarBaselineProbeTest` et son `orbitlab.probe`, que L0
a déjà posé et qui doit disparaître à la clôture du chantier.

### 8.5 — Cinq classes qui passent sans édition

`LunarTransferFlightTest`, `TranslunarInjectionPlanTest`, `TranslunarDepartureFlightTest`,
`LunarLaunchWindowProblemTest`, `LunarLaunchWindowFlightTest`. C'est la garde de l'extraction du §7 :
si elle a dérivé, la fenêtre bouge. Et c'est aussi ce qui dit que la garde du §3.4 laisse la démo
inchangée.

**Contrainte de méthode**, rappelée du découpage §3 : c'est l'utilisateur qui lance ces tests.

---

## 9. Limitations assumées

**Ce que le lot élargit sciemment**

1. **Le périmètre du découpage §4 déborde de quatre packages.** Il écrit `MissionType`,
   `MissionSpec`, `MissionComposer`, `operation/`, `stage/`. S'y ajoutent `runtime/` (§5 et §3.4),
   `window/problem` et `maneuver/` (§7), `ui/mission/` (§6.1), plus les trois refus du §6.2. Chacun a
   sa raison écrite ; l'ensemble est plus large que ce qui était annoncé.
2. **Le chemin λ est ouvert sans que son temps de paroi soit connu** (§5), mesuré par la sonde du
   §8.4.
3. **Les 10 km de tolérance sont provisoires** jusqu'à la mesure du §8.3.

**Ce qui reste faux ou grossier et ne bouge pas ici**

4. **`MissionHorizon.Revolutions` reste offert sur une mission lunaire** et y compte des révolutions
   de 12,1 j (§4.2). Sans effet : le défaut est `FixedDuration`, et L5 n'offrira qu'une durée.
5. **Aucun largage de S2** (§3.3) : la masse rapportée en fin de mission inclut l'étage vide.
6. **La troisième copie du préfixe d'ascension** (§3.1).
7. **La trace redevient décimée** — ~10 000 points à sept jours contre le budget de 8 192 de
   `TrajectoryPolyline`, découpage §6 pt 12, confirmé par L0 §4 à 1 447 points par jour.
8. **Deux façons de produire une orbite de parking coexistent** — L1 §5 pt 5, inchangé : la démo
   `mission.lunarDemo` survit à ce lot, avec son `parkingState` fabriqué et son 185 km.

**Héritées, non touchées**

9. **ToF à 4 j et angle de transfert à 170° restent des constantes couplées** — découpage §6 pt 1.
10. **Le seed de Lambert reste enfermé**, à `nRev = 0` — découpage §6 pt 7.
11. **Rien n'est optimisé sur la trajectoire translunaire** — découpage §6 pt 5. Ce que L4 optimise
    est son ascension, comme toute mission terrestre.
12. **Le faux refus de la garde de L3 §3.3 c** reste entier, et `MIS-11` devra y revenir.

---

## 10. Ce que cette conception corrige

**1. « `AnalyticParkingInsertionStage(185 km)` » — découpage §4.** L'altitude devient un composant de
spec, à **400 km** (§2). Le motif n'est pas le coût — les 54 m/s du TLI vont dans ce sens mais le coût
d'ascension va dans l'autre — c'est que 400 km est la seule altitude de parking que l'ascension du
dépôt vole réellement, et que 185 km est le bord le plus serré de la calibration du virage
gravitationnel, jamais exercé.

**2. « Le coast de parking vaut au plus une révolution (5 292 s) » — découpage §4, lot `L6`.** Déjà
corrigé par L1 §2.2 sur la dérive ; corrigé ici sur l'altitude. À 400 km la révolution vaut
**5 553,6 s**. Les deux contraintes d'étage tiennent toujours.

**3. « Lever ou étendre » sur la capture accidentelle — découpage §4.** Le lot rend **`false`** (§3.4),
parce que la règle que L3 §3.3 s'est donnée est plus précise que l'énoncé du découpage : un vol tronqué
est un fait de vol, pas un objectif mal formé. Et le critère n'est pas « l'arc n'est pas refermé » mais
« le minimum est le dernier point de l'arc », qui dit la même chose sans coûter à la démo son horizon.

**4. « L4 rend la confirmation à l'étage » — L2 §6 pt 6.** Corrigé en **extraction du corps partagé**
(§7) : l'étage n'a jamais eu besoin d'une `Mission` pour ce calcul, et lui en fabriquer une pour
appeler son `enter` aurait été composer une mission au sol pour réutiliser quatre lignes.

**5. La persistance de scénarios est un trou du découpage.** `ScenarioMission`, `ScenarioMapper` et
leur round-trip ne sont mentionnés par **aucun lot**, alors que `ScenarioMapper.toScenarioMission:76`
est l'un des six sites que le compilateur désigne (§1.2). L4 y pose un refus ; il faudra bien que
quelqu'un le remplisse pour qu'une mission lunaire créée au wizard survive à une sauvegarde. Signalé,
pas comblé.

**6. « `MissionTargetOrbit` cassera à L4 » — L3 §6 d.** Vrai, mais moins cher qu'annoncé : trois lignes
et aucun cas d'UI neuf (§6.1).

**7. « Le précédent est `TranslunarCoastStage` » — L1 §6.** Cette classe **n'écrase pas**
`propagateStandalone`. Elle est le précédent de la forme, pas de l'écrasement (§1.1).

---

## 11. Ce que `L4` lègue

**À `L5`** — une mission composable, un `MissionSpec.Lunar` à remplir depuis le wizard, et **trois
refus nommés** à remplacer par du code : `MissionFactory`, `WizardPrefill`, et le trou de
`ScenarioMapper`. Plus la contrainte du §4.2 : le profil lunaire garde un horizon `FixedDuration`,
faute de quoi `DynamicParameters:135` revient sur le chemin avec son µ terrestre.

**À `L6`** — un seul endroit où l'injection est calculée (§7), et la référence impulsionnelle contre
laquelle mesurer la poussée finie : le vol du §8.3, son Δv et son périlune.

**À `MIS-5`** — la chaîne complète du sol au périlune. Et le rappel que `OrbitElements:130` soustrait
le rayon **terrestre** aux apsides : juste tant que le dernier étage propulsé sort géocentrique, faux
dès qu'un étage propulsé lunaire existe (L0 §7 pt 4).

**À la clôture du chantier** — la démo `LunarTransferMission`, son `parkingState` et son 185 km, qui
survivent à ce lot sans avoir de consommateur de production.
